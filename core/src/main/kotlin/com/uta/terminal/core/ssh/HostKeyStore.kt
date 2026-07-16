package com.uta.terminal.core.ssh

import com.uta.terminal.core.model.HostAddress
import com.uta.terminal.core.model.HostKeyEntry

/**
 * 既知ホスト鍵の永続化を抽象化するインターフェース。
 * `:core` は Android 非依存を保つため、実体（Room）は `:app` が実装する。
 */
interface HostKeyStore {
    /** 未登録なら null。 */
    fun find(address: HostAddress): HostKeyEntry?

    /** 新規登録（TOFU 初回）または明示的な上書き（ユーザー承認後）。 */
    fun save(entry: HostKeyEntry)
}

/** インメモリ実装。テストや一時利用向け。 */
class InMemoryHostKeyStore : HostKeyStore {
    private val map = mutableMapOf<HostAddress, HostKeyEntry>()

    override fun find(address: HostAddress): HostKeyEntry? = map[address]

    override fun save(entry: HostKeyEntry) {
        map[entry.address] = entry
    }
}
