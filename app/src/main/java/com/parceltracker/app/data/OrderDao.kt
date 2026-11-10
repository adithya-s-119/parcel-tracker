package com.parceltracker.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders ORDER BY (promisedEpochDay IS NULL), promisedEpochDay ASC, emailEpochMillis DESC")
    fun observeAll(): Flow<List<DeliveryOrder>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun findById(id: String): DeliveryOrder?

    @Query("SELECT * FROM orders WHERE orderNumber = :orderNumber AND isManual = 0 LIMIT 1")
    suspend fun findByOrderNumber(orderNumber: String): DeliveryOrder?

    @Query(
        "SELECT * FROM orders WHERE isManual = 0 " +
            "AND (orderNumber = :reference OR trackingNumber = :reference) LIMIT 1",
    )
    suspend fun findByReference(reference: String): DeliveryOrder?

    @Query("SELECT * FROM orders WHERE threadId = :threadId AND isManual = 0 LIMIT 1")
    suspend fun findByThreadId(threadId: String): DeliveryOrder?

    @Query("SELECT * FROM orders WHERE isManual = 0 AND source IN (:sources)")
    suspend fun findBySources(sources: List<String>): List<DeliveryOrder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: DeliveryOrder)

    @Update
    suspend fun update(order: DeliveryOrder)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM orders WHERE isManual = 0 AND source IN ('GMAIL', 'IMAP')")
    suspend fun deleteAllGmailOrders()

    @Query("DELETE FROM orders WHERE isManual = 0")
    suspend fun deleteAllImportedOrders()

    @Query(
        "DELETE FROM orders WHERE isManual = 0 AND (" +
            "lower(merchant) LIKE '%pvr%' OR lower(merchant) LIKE '%bookmyshow%' OR " +
            "lower(merchant) LIKE '%book my show%' OR lower(merchant) LIKE '%inox%' OR " +
            "lower(merchant) LIKE '%cinepolis%' OR lower(merchant) LIKE '%ticketnew%')",
    )
    suspend fun deleteNonDeliveryOrders()

    @Query(
        "UPDATE orders SET promisedEpochDay = (emailEpochMillis / 86400000) " +
            "WHERE status = 'DELIVERED' AND promisedEpochDay IS NOT NULL " +
            "AND promisedEpochDay > (emailEpochMillis / 86400000) + 1",
    )
    suspend fun clampFutureDeliveredDates()

    @Query(
        "DELETE FROM orders WHERE isManual = 0 " +
            "AND lower(emailSubject) LIKE '%invoice%' " +
            "AND status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED')",
    )
    suspend fun deleteInvoiceOrders()

    @Query(
        "DELETE FROM orders WHERE isManual = 0 AND status != 'DELIVERED' " +
            "AND (lower(emailSubject) LIKE '%your return for%' OR " +
            "lower(emailSubject) LIKE '%return request%')",
    )
    suspend fun deleteReturnRequests()

    @Query(
        "DELETE FROM orders WHERE isManual = 0 " +
            "AND status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED') " +
            "AND (lower(emailSubject) LIKE '%refund%' OR lower(emailSubject) LIKE '%credit note%')",
    )
    suspend fun deleteRefundOrders()

    @Query(
        "UPDATE orders SET status = 'CANCELLED' WHERE isManual = 0 " +
            "AND lower(emailSubject) LIKE '%cancel%' " +
            "AND status NOT IN ('DELIVERED', 'RETURNED', 'CANCELLED')",
    )
    suspend fun markCancelledOrders()

    @Query(
        "UPDATE orders SET status = 'DELIVERY_FAILED' WHERE isManual = 0 " +
            "AND lower(emailSubject) LIKE '%problem during delivery%' " +
            "AND status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED')",
    )
    suspend fun markFailedDeliveries()

    @Query(
        "DELETE FROM orders WHERE source = 'SMS' AND isManual = 0 " +
            "AND promisedEpochDay IS NOT NULL AND promisedEpochDay < :cutoffDay " +
            "AND status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED')",
    )
    suspend fun deleteStaleSmsOrders(cutoffDay: Long)

    @Query(
        "DELETE FROM orders WHERE source = 'SMS' AND isManual = 0 " +
            "AND orderNumber IS NULL AND merchant LIKE '%-P' " +
            "AND status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED')",
    )
    suspend fun deletePromotionalSmsOrders()

    @Query("DELETE FROM orders")
    suspend fun deleteAll()

    @Query(
        "SELECT * FROM orders WHERE status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED') " +
            "AND promisedEpochDay IS NOT NULL AND promisedEpochDay >= :fromDay " +
            "ORDER BY promisedEpochDay ASC, emailEpochMillis DESC LIMIT :limit",
    )
    suspend fun nextDeliveries(fromDay: Long, limit: Int): List<DeliveryOrder>

    @Query(
        "SELECT * FROM orders WHERE promisedEpochDay BETWEEN :startDay AND :endDay " +
            "AND status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED') ORDER BY promisedEpochDay ASC",
    )
    suspend fun deliveriesBetween(startDay: Long, endDay: Long): List<DeliveryOrder>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markScanned(emails: List<ScannedEmail>)

    @Query("SELECT messageId FROM scanned_emails WHERE messageId IN (:ids)")
    suspend fun scannedIds(ids: List<String>): List<String>

    @Query("DELETE FROM scanned_emails")
    suspend fun clearScannedEmails()
}
