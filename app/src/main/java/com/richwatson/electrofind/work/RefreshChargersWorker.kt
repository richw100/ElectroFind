package com.richwatson.electrofind.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.richwatson.electrofind.ElectroFindApp

class RefreshChargersWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val pks = inputData.getLongArray(KEY_PKS) ?: return Result.success()
        val app = applicationContext as ElectroFindApp
        return try {
            app.repository.refreshChargersByPks(pks.toSet())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_PKS = "pks"

        fun enqueue(context: Context, pks: Set<Long>) {
            if (pks.isEmpty()) return
            val req = OneTimeWorkRequestBuilder<RefreshChargersWorker>()
                .setInputData(workDataOf(KEY_PKS to pks.toLongArray()))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("refresh-chargers", ExistingWorkPolicy.KEEP, req)
        }
    }
}
