package com.uta.tunnel.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        SshKeyEntity::class,
        TagEntity::class,
        ProfileTagCrossRef::class,
        HostKeyEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class TunnelDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sshKeyDao(): SshKeyDao
    abstract fun tagDao(): TagDao
    abstract fun hostKeyDao(): HostKeyDao

    companion object {
        /** v1→v2：手動並び替え用の sortOrder 列を追加（既存行は 0）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v2→v3：鍵ストア（ssh_keys）テーブルと profiles.keyId 参照を追加。
         * DDL のみ。既存インライン鍵の昇格はアプリ層（ProfileRepository.promoteInlineKeys）で
         * 起動時に行う（Keystore 復号と公開鍵導出が必要で、失敗を許容したいため）。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ssh_keys` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `secretIv` BLOB NOT NULL,
                        `secretCipher` BLOB NOT NULL,
                        `passIv` BLOB,
                        `passCipher` BLOB,
                        `keyType` TEXT,
                        `publicKey` TEXT,
                        `fingerprint` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE profiles ADD COLUMN keyId TEXT")
            }
        }

        /**
         * v3→v4：ピン留め列 profiles.pinned と、タグの正規化テーブル
         * （tags / profile_tags 中間）を追加。profile_tags は CASCADE 削除。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profile_tags` (
                        `profileId` TEXT NOT NULL,
                        `tagId` TEXT NOT NULL,
                        PRIMARY KEY(`profileId`, `tagId`),
                        FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_profile_tags_tagId` ON `profile_tags` (`tagId`)",
                )
            }
        }

        /**
         * v4→v5：既知ホスト鍵（host_keys）テーブルを追加し、TOFU をインメモリから永続化へ移す。
         * DDL のみ。これ以前はプロセス終了ごとに既知ホストが消えていたため、移行するデータは無い
         * （初回接続扱いで各ホストが改めて登録される）。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `host_keys` (
                        `host` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `keyType` TEXT NOT NULL,
                        `fingerprint` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`host`, `port`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
