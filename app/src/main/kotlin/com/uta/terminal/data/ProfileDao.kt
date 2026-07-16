package com.uta.terminal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    // 手動並び替えの sortOrder 昇順。同順は新しい順（createdAt DESC）で安定させる。
    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT MIN(sortOrder) FROM profiles")
    suspend fun minSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("UPDATE profiles SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)
}
