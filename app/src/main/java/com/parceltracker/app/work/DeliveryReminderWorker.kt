package com.parceltracker.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parceltracker.app.ParcelApp
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.notifications.Notifications
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class DeliveryReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ParcelApp ?: return Result.success()
        val settings = app.container.settingsStore.settings.first()
        if (!settings.notificationsEnabled) return Result.success()

        val today = LocalDate.now()
        val orders = app.container.database.orderDao().deliveriesBetween(
            today.toEpochDay(),
            today.plusDays(1).toEpochDay(),
        )

        val todayOrders = orders.filter { it.promisedEpochDay == today.toEpochDay() }
        val tomorrowOrders = orders.filter {
            it.promisedEpochDay == today.plusDays(1).toEpochDay()
        }

        Notifications.showDeliveryReminder(applicationContext, todayOrders, tomorrowOrders)
        return Result.success()
    }
}
