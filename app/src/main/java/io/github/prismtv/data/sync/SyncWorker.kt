package io.github.prismtv.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.prismtv.AppGraph
import java.util.concurrent.TimeUnit

/** Periodic background refresh of the channel database. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = AppGraph.catalog
        if (!repo.isStale()) return Result.success()
        return if (repo.refreshNow(userInitiated = false)) Result.success()
        else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

object SyncScheduler {
    private const val NAME = "catalog-sync"

    fun schedule(context: Context, hours: Int, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(hours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInitialDelay(hours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
