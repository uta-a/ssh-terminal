package com.uta.terminal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** タグ名と、それを付けているプロファイル数（フィルタチップの表示・掃除用）。 */
data class TagWithCount(val id: String, val name: String, val count: Int)

@Dao
interface TagDao {
    @Query(
        """
        SELECT t.id AS id, t.name AS name, COUNT(pt.profileId) AS count
        FROM tags t LEFT JOIN profile_tags pt ON pt.tagId = t.id
        GROUP BY t.id, t.name
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun observeTagsWithCount(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id IN (:ids)")
    suspend fun tagsByIds(ids: List<String>): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: ProfileTagCrossRef)

    @Query("DELETE FROM profile_tags WHERE profileId = :profileId")
    suspend fun clearProfileTags(profileId: String)

    @Query("SELECT tagId FROM profile_tags WHERE profileId = :profileId")
    suspend fun tagIdsFor(profileId: String): List<String>

    /** どのプロファイルにも使われていないタグを削除する（掃除）。 */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM profile_tags)")
    suspend fun deleteOrphanTags()

    /**
     * プロファイルのタグを与えられた名前集合に設定する。既存タグは名前で再利用し、
     * 無ければ作る。設定後、孤立タグを掃除する。1 トランザクションで整合させる。
     */
    @Transaction
    suspend fun setProfileTags(profileId: String, tagNames: List<String>, newId: () -> String) {
        clearProfileTags(profileId)
        for (raw in tagNames) {
            val name = raw.trim()
            if (name.isEmpty()) continue
            val existing = findByName(name)
            val tagId = if (existing != null) {
                existing.id
            } else {
                val id = newId()
                insertTag(TagEntity(id = id, name = name))
                // IGNORE で衝突した場合に備え、再取得で正しい id を得る。
                findByName(name)?.id ?: id
            }
            insertCrossRef(ProfileTagCrossRef(profileId = profileId, tagId = tagId))
        }
        deleteOrphanTags()
    }
}
