package com.jacksonkasi.cliplex.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class], version = 2, exportSchema = true)
abstract class ClipLexDatabase : RoomDatabase() {
	abstract fun sessionDao(): SessionDao
}
