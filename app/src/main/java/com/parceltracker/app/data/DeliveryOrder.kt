package com.parceltracker.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class OrderStatus {
    UPCOMING,
    SHIPPED,
    DELAYED,
    READY_FOR_PICKUP,
    OUT_FOR_DELIVERY,
    DELIVERY_FAILED,
    DELIVERED,
    CANCELLED,
    RETURNED,
}

@Entity(
    tableName = "orders",
    indices = [
        Index("promisedEpochDay"),
        Index("orderNumber"),
        Index("threadId"),
    ],
)
data class DeliveryOrder(
    @PrimaryKey val id: String,
    val threadId: String = "",
    val merchant: String,
    val itemName: String,
    val orderNumber: String? = null,
    val trackingNumber: String? = null,
    val carrier: String? = null,
    val promisedEpochDay: Long? = null,
    val status: OrderStatus = OrderStatus.UPCOMING,
    val emailEpochMillis: Long = System.currentTimeMillis(),
    val emailSubject: String = "",
    val snippet: String = "",
    val notes: String? = null,
    val source: String = "GMAIL",
    val isManual: Boolean = false,
    val isEdited: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val promisedDate: LocalDate?
        get() = promisedEpochDay?.let(LocalDate::ofEpochDay)

    val isActive: Boolean
        get() = status != OrderStatus.DELIVERED &&
            status != OrderStatus.CANCELLED &&
            status != OrderStatus.RETURNED
}

@Entity(tableName = "scanned_emails")
data class ScannedEmail(
    @PrimaryKey val messageId: String,
    val scannedAt: Long = System.currentTimeMillis(),
)

data class OrderDraft(
    val merchant: String,
    val itemName: String,
    val promisedDate: LocalDate?,
    val status: OrderStatus = OrderStatus.UPCOMING,
    val orderNumber: String? = null,
    val notes: String? = null,
)
