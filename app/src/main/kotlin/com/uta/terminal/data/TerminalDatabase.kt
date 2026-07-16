package com.uta.terminal.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, SshKeyEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class TerminalDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sshKeyDao(): SshKeyDao

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
    }
}
