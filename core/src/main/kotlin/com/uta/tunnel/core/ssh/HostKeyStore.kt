package com.uta.tunnel.core.ssh

import com.uta.tunnel.core.model.HostAddress
import com.uta.tunnel.core.model.HostKeyEntry

/**
 * 既知ホスト鍵の永続化を抽象化するインターフェース。
 * `:core` は Android 非依存を保つため、実体（Room）は `:app` が実装する。
 *
 * 実装は接続スレッドから同期的に呼ばれる（[TofuHostKeyVerifier.verify] 経由）。
 * メインスレッドからは呼ばないこと。
 */
interface HostKeyStore {
    /** 未登録なら null。 */
    fun find(address: HostAddress): HostKeyEntry?

    /** 新規登録（TOFU 初回）または明示的な上書き（ユーザー承認後）。 */
    fun save(entry: HostKeyEntry)

    /** 登録済みの全エントリ（設定の既知ホスト一覧用）。 */
    fun list(): List<HostKeyEntry>

    /**
     * 登録を削除する。削除後はそのホストへの次回接続が再び TOFU 初回扱い
     * （＝無条件受理）になる点に注意。
     */
    fun delete(address: HostAddress)
}

/** インメモリ実装。テストや一時利用向け。 */
class InMemoryHostKeyStore : HostKeyStore {
    private val map = mutableMapOf<HostAddress, HostKeyEntry>()

    override fun find(address: HostAddress): HostKeyEntry? = map[address]

    override fun save(entry: HostKeyEntry) {
        map[entry.address] = entry
    }

    override fun list(): List<HostKeyEntry> = map.values.toList()

    override fun delete(address: HostAddress) {
        map.remove(address)
    }
}
