package com.uta.tunnel.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** プロファイル本体＋付与タグ（多対多を [ProfileTagCrossRef] 経由で解決）。 */
data class ProfileWithTags(
    @androidx.room.Embedded val profile: ProfileEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ProfileTagCrossRef::class,
            parentColumn = "profileId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

@Dao
interface ProfileDao {
    // ピン留め優先、次に手動並び替えの sortOrder 昇順。同順は新しい順（createdAt DESC）で安定させる。
    @Transaction
    @Query("SELECT * FROM profiles ORDER BY pinned DESC, sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<ProfileWithTags>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Query("SELECT MIN(sortOrder) FROM profiles")
    suspend fun minSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("UPDATE profiles SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: String, order: Int)

    @Query("UPDATE profiles SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    /** 渡した id 順に sortOrder=0,1,2… を 1 トランザクションで振り直す（途中失敗で不整合にしない）。 */
    @Transaction
    suspend fun applyOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)

    /** 鍵ストアの各鍵を使っているプロファイル数（鍵管理画面の「使用中」表示・削除ガード用）。 */
    @Query("SELECT keyId, COUNT(*) AS count FROM profiles WHERE keyId IS NOT NULL GROUP BY keyId")
    fun observeKeyUsage(): Flow<List<KeyUsage>>

    @Query("SELECT COUNT(*) FROM profiles WHERE keyId = :keyId")
    suspend fun keyUsageCount(keyId: String): Int

    /** 鍵ストア未昇格のインライン鍵プロファイル（起動時マイグレーション対象）。 */
    @Query("SELECT * FROM profiles WHERE authKind = 'KEY' AND keyId IS NULL")
    suspend fun inlineKeyProfiles(): List<ProfileEntity>
}

/** [ProfileDao.observeKeyUsage] の集計行。 */
data class KeyUsage(val keyId: String, val count: Int)
