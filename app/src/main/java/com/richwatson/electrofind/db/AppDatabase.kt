package com.richwatson.electrofind.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CachedChargerEntity::class, CachedOcmEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chargerDao(): ChargerDao
    abstract fun ocmDao(): OcmDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "electrofind.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
