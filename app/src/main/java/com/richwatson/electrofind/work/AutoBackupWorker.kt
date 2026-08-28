package com.richwatson.electrofind.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.richwatson.electrofind.db.AppDatabase
import com.richwatson.electrofind.model.BackupFile
import com.richwatson.electrofind.model.CustomCharger
import com.richwatson.electrofind.model.Trip
import com.richwatson.electrofind.model.TripLogBackup
import com.richwatson.electrofind.model.TripLogSettingsBackup
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.toBackup
import com.richwatson.electrofind.util.AutoBackupWriter
import java.util.concurrent.TimeUnit

class AutoBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = AppPreferences(applicationContext)
        val gson = Gson()

        val customChargers: List<CustomCharger> = try {
            gson.fromJson(prefs.rawCustomChargers, object : TypeToken<List<CustomCharger>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }
        val trips: List<Trip> = try {
            gson.fromJson(prefs.rawTrips, object : TypeToken<List<Trip>>() {}.type) ?: emptyList()
        } catch (e: Exception) { emptyList() }

        val tripLog: TripLogBackup? = try {
            val dao = AppDatabase.getInstance(applicationContext).tripLogDao()
            TripLogBackup(
                sessions = dao.allSessions().map { it.toBackup() },
                customCharges = dao.allCustomCharges().map { it.toBackup() },
                settings = TripLogSettingsBackup(
                    eurToGbpRate = prefs.tripEurToGbpRate,
                    iceMpg = prefs.tripIceMpg,
                    petrolPricePerLitre = prefs.tripPetrolPricePerLitre,
                    evMilesPerKwh = prefs.tripEvMilesPerKwh,
                    milesTravelled = prefs.tripMilesTravelled,
                    folderUri = prefs.tripFolderUri
                )
            )
        } catch (e: Exception) { null }

        val json = gson.toJson(
            BackupFile(
                customChargers = customChargers,
                favouritePks = prefs.favouritePks.toList(),
                excludedPks = prefs.excludedPks.toList(),
                trips = trips,
                tripLog = tripLog
            )
        )

        val wrote = AutoBackupWriter.write(applicationContext, json)
        if (wrote) {
            prefs.lastAutoBackupAt = System.currentTimeMillis()
            return Result.success()
        }
        return if (runAttemptCount < 2) Result.retry() else Result.failure()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "auto-backup-periodic"
        private const val DEBOUNCED_WORK_NAME = "auto-backup-debounced"

        // Safety net in case the app is closed before a debounced backup fires.
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        // Called after trips/favourites/excluded/custom-chargers change. Debounced so a burst
        // of edits (e.g. reordering several stops) collapses into a single write.
        fun scheduleSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInitialDelay(30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(DEBOUNCED_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
