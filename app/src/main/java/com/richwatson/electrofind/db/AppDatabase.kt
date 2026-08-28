package com.richwatson.electrofind.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedChargerEntity::class,
        ReceiptSessionEntity::class,
        CustomChargeEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chargerDao(): ChargerDao
    abstract fun tripLogDao(): TripLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // v4 -> v5 adds the Trip-tab tables. Hand-written (CREATE only) so parsed receipt
        // sessions and manual charges survive the upgrade — fallbackToDestructiveMigration
        // still covers pre-4 installs where there is no user data worth keeping.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `receipt_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`receiptNumber` TEXT NOT NULL, " +
                        "`sourceFileName` TEXT NOT NULL, " +
                        "`sourceFileId` TEXT NOT NULL, " +
                        "`startEpochMillis` INTEGER NOT NULL, " +
                        "`endEpochMillis` INTEGER NOT NULL, " +
                        "`evse` TEXT, " +
                        "`provider` TEXT, " +
                        "`address` TEXT, " +
                        "`currency` TEXT NOT NULL, " +
                        "`kwh` REAL NOT NULL, " +
                        "`energyCostGross` REAL NOT NULL, " +
                        "`idleCostGross` REAL NOT NULL, " +
                        "`totalGross` REAL NOT NULL, " +
                        "`co2SavedKg` REAL, " +
                        "`excluded` INTEGER NOT NULL, " +
                        "`importedAtEpochMillis` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_receipt_sessions_receiptNumber` ON `receipt_sessions` (`receiptNumber`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_receipt_sessions_sourceFileName` ON `receipt_sessions` (`sourceFileName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_receipt_sessions_startEpochMillis` ON `receipt_sessions` (`startEpochMillis`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_charges` (" +
                        "`id` TEXT NOT NULL, " +
                        "`dateEpochMillis` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`kwh` REAL NOT NULL, " +
                        "`cost` REAL NOT NULL, " +
                        "`idleCost` REAL NOT NULL, " +
                        "`currency` TEXT NOT NULL, " +
                        "`excluded` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_charges_dateEpochMillis` ON `custom_charges` (`dateEpochMillis`)")
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "electrofind.db"
            )
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
