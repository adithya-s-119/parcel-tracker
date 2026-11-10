package com.parceltracker.app.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

class OrderRepository(private val dao: OrderDao) {

    enum class MergeResult { IGNORED, INSERTED, UPDATED }

    fun observeOrders(): Flow<List<DeliveryOrder>> = dao.observeAll()

    suspend fun addManual(draft: OrderDraft): DeliveryOrder {
        val now = System.currentTimeMillis()
        val order = DeliveryOrder(
            id = "manual-${UUID.randomUUID()}",
            merchant = draft.merchant.trim().ifBlank { "Unknown store" },
            itemName = draft.itemName.trim().ifBlank { "Delivery" },
            orderNumber = draft.orderNumber?.trim()?.takeIf { it.isNotBlank() },
            promisedEpochDay = draft.promisedDate?.toEpochDay(),
            status = draft.status,
            emailEpochMillis = now,
            emailSubject = "Added manually",
            snippet = "",
            notes = draft.notes?.trim()?.takeIf { it.isNotBlank() },
            source = "MANUAL",
            isManual = true,
            isEdited = true,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(order)
        return order
    }

    suspend fun updateFromEditor(order: DeliveryOrder, draft: OrderDraft): DeliveryOrder {
        val updated = order.copy(
            merchant = draft.merchant.trim().ifBlank { order.merchant },
            itemName = draft.itemName.trim().ifBlank { order.itemName },
            orderNumber = draft.orderNumber?.trim()?.takeIf { it.isNotBlank() },
            promisedEpochDay = draft.promisedDate?.toEpochDay(),
            status = draft.status,
            notes = draft.notes?.trim()?.takeIf { it.isNotBlank() },
            isEdited = true,
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
        return updated
    }

    suspend fun markDelivered(order: DeliveryOrder) {
        dao.update(
            order.copy(
                status = OrderStatus.DELIVERED,
                promisedEpochDay = LocalDate.now().toEpochDay(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(order: DeliveryOrder) {
        dao.deleteById(order.id)
    }

    suspend fun mergeGmail(
        parsed: OrderEmailParser.ParsedOrder,
        messageId: String,
        threadId: String,
        receivedEpochMillis: Long,
        source: String = "GMAIL",
    ): MergeResult {
        val existing = parsed.orderNumber
            ?.let { dao.findByOrderNumber(it) }
            ?: threadId.takeIf { it.isNotBlank() }?.let { dao.findByThreadId(it) }

        val now = System.currentTimeMillis()

        if (existing == null) {
            if (parsed.promisedDate == null &&
                parsed.status != OrderStatus.DELIVERED &&
                parsed.status != OrderStatus.CANCELLED
            ) {
                return MergeResult.IGNORED
            }

            val date = when {
                parsed.status == OrderStatus.DELIVERED -> parsed.promisedDate ?: parsed.emailDate
                else -> parsed.promisedDate
            }

            val order = DeliveryOrder(
                id = messageId,
                threadId = threadId,
                merchant = parsed.merchant,
                itemName = parsed.itemName ?: fallbackItemName(parsed),
                orderNumber = parsed.orderNumber,
                trackingNumber = parsed.trackingNumber,
                carrier = parsed.carrier,
                promisedEpochDay = date?.toEpochDay(),
                status = parsed.status,
                emailEpochMillis = receivedEpochMillis,
                emailSubject = parsed.emailSubject,
                snippet = parsed.snippet,
                source = source,
                createdAt = now,
                updatedAt = now,
            )
            dao.upsert(order)
            return MergeResult.INSERTED
        }

        val mergedStatus = mergeStatus(existing.status, parsed.status)
        val newerEmail = receivedEpochMillis >= existing.emailEpochMillis

        val mergedDate = when {
            existing.isEdited -> existing.promisedEpochDay
            mergedStatus == OrderStatus.DELIVERED ->
                (parsed.promisedDate ?: parsed.emailDate).toEpochDay()
            parsed.promisedDate != null -> parsed.promisedDate.toEpochDay()
            else -> existing.promisedEpochDay
        }

        val updated = existing.copy(
            merchant = existing.merchant,
            itemName = if (existing.isEdited) {
                existing.itemName
            } else {
                parsed.itemName ?: existing.itemName
            },
            orderNumber = existing.orderNumber ?: parsed.orderNumber,
            trackingNumber = parsed.trackingNumber ?: existing.trackingNumber,
            carrier = parsed.carrier ?: existing.carrier,
            promisedEpochDay = mergedDate,
            status = mergedStatus,
            emailEpochMillis = maxOf(existing.emailEpochMillis, receivedEpochMillis),
            emailSubject = if (newerEmail) parsed.emailSubject else existing.emailSubject,
            snippet = if (newerEmail) parsed.snippet else existing.snippet,
            updatedAt = now,
        )

        if (updated == existing) return MergeResult.IGNORED
        dao.update(updated)
        return MergeResult.UPDATED
    }

    private fun mergeStatus(current: OrderStatus, incoming: OrderStatus): OrderStatus {
        val currentRank = statusRank(current)
        val incomingRank = statusRank(incoming)
        return if (incomingRank >= currentRank) incoming else current
    }

    private fun statusRank(status: OrderStatus): Int = when (status) {
        OrderStatus.UPCOMING -> 1
        OrderStatus.SHIPPED -> 2
        OrderStatus.OUT_FOR_DELIVERY -> 3
        OrderStatus.DELIVERED -> 4
        OrderStatus.CANCELLED -> 5
    }

    private fun fallbackItemName(parsed: OrderEmailParser.ParsedOrder): String {
        var value = parsed.emailSubject
            .replace(Regex("(?i)^\\s*(re|fwd?):\\s*"), "")
            .replace(
                Regex("(?i)\\b(?:has been|was|is|will be) (?:shipped|delivered|dispatched)\\b.*$"),
                "",
            )
            .replace(
                Regex("(?i)\\b(?:shipped|delivered|dispatched|out for delivery)\\s*[:\\-–]?\\s*"),
                "",
            )
            .replace(
                Regex("(?i)^(?:your\\s+)?(?:amazon(?:\\.[a-z]+)?\\s+)?order\\s+(?:confirmation|confirmed|update|details|number)\\s*[:\\-–]?\\s*"),
                "",
            )
            .replace(Regex("(?i)^order\\s*[:\\-–]?\\s*"), "")
            .trim()

        if (value.lowercase(Locale.ENGLISH) in setOf("your order", "order", "order confirmed", "confirmation")) {
            value = ""
        }
        return value.ifBlank { "Order from ${parsed.merchant}" }.take(100)
    }
}
