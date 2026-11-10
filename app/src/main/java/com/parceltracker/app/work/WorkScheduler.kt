package com.parceltracker.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.parceltracker.app.data.AppSettings
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val SYNC_WORK = "gmail_sync"
    private const val REMINDER_WORK = "delivery_reminders"

    fun apply(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)

        if (settings.autoSync &&
            (settings.accountEmail != null || settings.smsEnabled)
        ) {
            val request = PeriodicWorkRequestBuilder<GmailSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                SYNC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            workManager.cancelUniqueWork(SYNC_WORK)
        }

        if (settings.notificationsEnabled) {
            val now = ZonedDateTime.now()
            var next = now.withHour(9).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val initialDelayMinutes = Duration.between(now, next).toMinutes().coerceAtLeast(1)

            val request = PeriodicWorkRequestBuilder<DeliveryReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                REMINDER_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            workManager.cancelUniqueWork(REMINDER_WORK)
        }
    }

    fun cancelSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK)
    }
}
