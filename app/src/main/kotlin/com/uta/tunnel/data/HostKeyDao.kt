package com.uta.tunnel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 既知ホスト鍵の DAO。
 *
 * `*Sync` は [com.uta.tunnel.core.ssh.HostKeyStore] 実装から**接続スレッド上で**同期的に
 * 呼ばれる（TOFU 照合が接続処理の途中にあり suspend にできないため）。メインスレッドから
 * 呼ぶと Room が例外を投げる（`allowMainThreadQueries` は有効にしない）。
 * UI からは [observeAll] と suspend 版の [deleteSync] ではなく [delete] を使うこと。
 */
@Dao
interface HostKeyDao {
    @Query("SELECT * FROM host_keys ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HostKeyEntity>>

    @Query("SELECT * FROM host_keys WHERE host = :host AND port = :port")
    fun findSync(host: String, port: Int): HostKeyEntity?

    @Query("SELECT * FROM host_keys ORDER BY createdAt DESC")
    fun listSync(): List<HostKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSync(entry: HostKeyEntity)

    @Query("DELETE FROM host_keys WHERE host = :host AND port = :port")
    fun deleteSync(host: String, port: Int)

    @Query("DELETE FROM host_keys WHERE host = :host AND port = :port")
    suspend fun delete(host: String, port: Int)
}
