package com.parceltracker.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.parceltracker.app.MainActivity
import com.parceltracker.app.R
import com.parceltracker.app.data.DeliveryOrder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Notifications {

    const val CHANNEL_ID = "delivery_reminders"
    private const val NOTIFICATION_ID = 2001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Delivery reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A quiet nudge when an order is arriving today or tomorrow."
        }
        manager.createNotificationChannel(channel)
    }

    fun showDeliveryReminder(
        context: Context,
        todayOrders: List<DeliveryOrder>,
        tomorrowOrders: List<DeliveryOrder>,
    ) {
        if (todayOrders.isEmpty() && tomorrowOrders.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        ensureChannel(context)

        val title = when {
            todayOrders.isNotEmpty() -> "Arriving today"
            else -> "Arriving tomorrow"
        }

        val lines = buildList {
            todayOrders.take(2).forEach { add("Today · ${it.merchant} · ${it.itemName}") }
            tomorrowOrders.take(3).forEach { add("Tomorrow · ${it.merchant} · ${it.itemName}") }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "calendar")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    fun formattedDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
}
