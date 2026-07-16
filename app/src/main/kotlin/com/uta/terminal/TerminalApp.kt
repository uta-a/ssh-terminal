package com.uta.terminal

import android.app.Application
import android.content.Context
import com.uta.terminal.core.session.SessionManager

/** 手動 DI コンテナ。依存を最小化するため Hilt は使わない（IR Tool と同じ流儀）。 */
class AppContainer(app: Application) {
    val appContext: Context = app.applicationContext

    // 開いているセッションのレジストリ。UI（ドロワー）とサービスが共有する。
    val sessionManager = SessionManager()

    // TODO: repository（Room: ConnectionProfile / HostKeyEntry）・secretStore（Keystore+生体認証）・
    //       settings（DataStore）をセッション実装フェーズで追加する。
}

class TerminalApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
