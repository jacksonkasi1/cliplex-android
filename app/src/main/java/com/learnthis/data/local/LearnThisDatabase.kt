package com.learnthis.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
abstract class LearnThisDatabase : RoomDatabase() {
 abstract fun sessionDao(): SessionDao
}
