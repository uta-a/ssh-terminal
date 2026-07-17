package com.uta.tunnel.data

import com.uta.tunnel.core.model.HostAddress
import com.uta.tunnel.core.model.HostKeyEntry
import com.uta.tunnel.core.ssh.HostKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [HostKeyStore] の Room 実装（永続 TOFU）。`:core` のモデル ⇄ [HostKeyEntity] の変換をここに閉じる。
 *
 * find/save/list/delete は接続スレッドから同期的に呼ばれる。UI 向けには [observeAll] と
 * suspend の [deleteAsync] を使う。
 */
class RoomHostKeyStore(private val dao: HostKeyDao) : HostKeyStore {

    override fun find(address: HostAddress): HostKeyEntry? =
        dao.findSync(address.host, address.port)?.toEntry()

    override fun save(entry: HostKeyEntry) {
        dao.upsertSync(
            HostKeyEntity(
                host = entry.address.host,
                port = entry.address.port,
                keyType = entry.keyType,
                fingerprint = entry.fingerprint,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun list(): List<HostKeyEntry> = dao.listSync().map { it.toEntry() }

    override fun delete(address: HostAddress) = dao.deleteSync(address.host, address.port)

    /** 設定の既知ホスト一覧が購読する。 */
    fun observeAll(): Flow<List<HostKeyEntry>> =
        dao.observeAll().map { rows -> rows.map { it.toEntry() } }

    /** UI（メインスレッド）からの削除。 */
    suspend fun deleteAsync(address: HostAddress) = dao.delete(address.host, address.port)

    private fun HostKeyEntity.toEntry() =
        HostKeyEntry(HostAddress(host, port), keyType, fingerprint)
}
