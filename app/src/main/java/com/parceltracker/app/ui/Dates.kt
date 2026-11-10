package com.parceltracker.app.ui

import com.parceltracker.app.data.OrderStatus
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {

    fun fullDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

    fun dayMonth(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    fun monthShort(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(Locale.getDefault())

    fun monthTitle(month: YearMonth): String =
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    fun relative(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days == -1L -> "Yesterday"
            days in 2..6 -> "In $days days"
            days in -6..-2 -> "${-days} days ago"
            date.year == today.year -> dayMonth(date)
            else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        }
    }

    fun syncSubtitle(lastSyncAt: Long): String {
        if (lastSyncAt <= 0L) return "Not synced yet"
        val minutes = (System.currentTimeMillis() - lastSyncAt) / 60_000L
        return when {
            minutes < 1 -> "Synced just now"
            minutes < 60 -> "Synced $minutes min ago"
            minutes < 24 * 60 -> "Synced ${minutes / 60}h ago"
            else -> "Synced ${minutes / (24 * 60)}d ago"
        }
    }

    fun statusLabel(status: OrderStatus): String = when (status) {
        OrderStatus.UPCOMING -> "Scheduled"
        OrderStatus.SHIPPED -> "Shipped"
        OrderStatus.DELAYED -> "Delayed"
        OrderStatus.READY_FOR_PICKUP -> "Ready for pickup"
        OrderStatus.OUT_FOR_DELIVERY -> "Out for delivery"
        OrderStatus.DELIVERY_FAILED -> "Delivery failed"
        OrderStatus.DELIVERED -> "Delivered"
        OrderStatus.CANCELLED -> "Cancelled"
        OrderStatus.RETURNED -> "Returned to sender"
    }
}
