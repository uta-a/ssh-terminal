package com.uta.terminal.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProfileEntity::class], version = 1, exportSchema = false)
abstract class TerminalDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
