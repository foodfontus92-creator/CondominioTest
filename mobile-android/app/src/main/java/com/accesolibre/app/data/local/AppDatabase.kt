package com.accesolibre.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PassEntity::class, AccessLogEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun passDao(): PassDao
    abstract fun accessLogDao(): AccessLogDao

    companion object {
        fun build(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "accesolibre.db",
        ).fallbackToDestructiveMigration().build()
    }
}
