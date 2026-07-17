package com.uta.tunnel.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * タグ（正規化）。名前でユニーク。改名・使用数集計・未使用掃除を素直に行うため、
 * プロファイルとは多対多の中間テーブル [ProfileTagCrossRef] で結ぶ。
 */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

/**
 * プロファイル⇔タグの中間テーブル。プロファイル/タグ削除で該当行も消える（CASCADE）。
 */
@Entity(
    tableName = "profile_tags",
    primaryKeys = ["profileId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class ProfileTagCrossRef(
    val profileId: String,
    val tagId: String,
)
