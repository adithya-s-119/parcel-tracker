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
