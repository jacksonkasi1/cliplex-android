package com.learnthis.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class], version = 2, exportSchema = true)
abstract class LearnThisDatabase : RoomDatabase() {
	abstract fun sessionDao(): SessionDao
}
