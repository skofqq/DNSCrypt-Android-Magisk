package io.github.skofqq.dnscryptmanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.skofqq.dnscryptmanager.appContainer
import io.github.skofqq.dnscryptmanager.data.BlocklistSettings
import java.util.concurrent.TimeUnit

/**
 * Daily blocklist refresh. A running proxy is restarted to pick the lists up; a
 * stopped one is left stopped and gets them at its next start.
 */
class BlocklistWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val blocklists = applicationContext.appContainer.blocklists
        if (blocklists.settings.value.enabledCount == 0) return Result.success()
        return if (blocklists.updateAll(restart = true).isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "blocklists"

        /** Matches the schedule to [settings]: daily when auto-update is on, nothing otherwise. */
        fun schedule(context: Context, settings: BlocklistSettings) {
            val manager = WorkManager.getInstance(context)
            if (!settings.autoUpdate) {
                manager.cancelUniqueWork(NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (settings.unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .setRequiresCharging(settings.chargingOnly)
                .build()
            val request = PeriodicWorkRequestBuilder<BlocklistWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
