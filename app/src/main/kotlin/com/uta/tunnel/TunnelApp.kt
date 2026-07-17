package com.uta.tunnel

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.uta.tunnel.core.session.SessionManager
import com.uta.tunnel.core.ssh.HostKeyStore
import com.uta.tunnel.core.ssh.InMemoryHostKeyStore
import com.uta.tunnel.data.ProfileRepository
import com.uta.tunnel.data.SettingsStore
import com.uta.tunnel.data.SshKeyRepository
import com.uta.tunnel.data.TunnelDatabase
import com.uta.tunnel.session.SessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手動 DI コンテナ。依存を最小化するため Hilt は使わない（IR Tool と同じ流儀）。 */
class AppContainer(app: Application) {
    val appContext: Context = app.applicationContext

    // 開いているセッションのレジストリ。UI（ドロワー）とサービスが共有する。
    val sessionManager = SessionManager()

    // 既知ホスト鍵ストア。MVP はインメモリ（起動ごとに TOFU がリセット）。
    // TODO: Room 実装へ差し替える（永続 TOFU）。
    val hostKeyStore: HostKeyStore = InMemoryHostKeyStore()

    // アクティブな SSH セッションのライフサイクル管理（EmulatorHost を保持）。
    val sessionController = SessionController(appContext, sessionManager, hostKeyStore)

    // 接続プロファイル・鍵ストアの永続化（Room）。秘密は Keystore で暗号化して保存する。
    private val database = Room.databaseBuilder(appContext, TunnelDatabase::class.java, "tunnel.db")
        .addMigrations(
            TunnelDatabase.MIGRATION_1_2,
            TunnelDatabase.MIGRATION_2_3,
            TunnelDatabase.MIGRATION_3_4,
        )
        .build()
    val sshKeyRepository = SshKeyRepository(database.sshKeyDao(), database.profileDao())
    val profileRepository =
        ProfileRepository(database.profileDao(), database.tagDao(), sshKeyRepository)

    // アプリ設定（生体認証 ON/OFF 等）。
    val settingsStore = SettingsStore(appContext)

    // TODO: settings（DataStore）・HostKeyEntry の永続 TOFU をセッション実装フェーズで追加する。
}

class TunnelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // sshj が参照する "BC" プロバイダを本物の BouncyCastle へ差し替える（Android 対処）。
        com.uta.tunnel.ssh.SshSecurity.ensureBouncyCastle()
        container = AppContainer(this)
        // 旧形式（行内暗号化）の鍵プロファイルを鍵ストアへ昇格する（失敗はスキップ）。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.profileRepository.promoteInlineKeys() }
        }
    }
}
