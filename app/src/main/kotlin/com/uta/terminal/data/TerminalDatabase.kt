package com.uta.terminal.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProfileEntity::class], version = 2, exportSchema = false)
abstract class TerminalDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        /** v1→v2：手動並び替え用の sortOrder 列を追加（既存行は 0）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE profiles ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
