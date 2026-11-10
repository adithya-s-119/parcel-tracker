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
        fallbackItem: String? = null,
    ): MergeResult {
        var existing = parsed.orderNumber
            ?.let { dao.findByReference(it) }
            ?: parsed.trackingNumber?.let { dao.findByReference(it) }
            ?: threadId.takeIf { it.isNotBlank() }?.let { dao.findByThreadId(it) }

        if (existing == null) {
            existing = findCrossSourceDuplicate(parsed, source)
        }

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
                itemName = parsed.itemName ?: fallbackItem ?: fallbackItemName(parsed),
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

        val newerEmail = receivedEpochMillis >= existing.emailEpochMillis
        val mergedStatus = mergeStatus(existing.status, parsed.status, newerEmail)

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

    /**
     * Gmail and SMS often describe the same order. If the order number or
     * tracking ID is missing from one of them, match on the product name and
     * a nearby delivery date instead of inserting a second card.
     */
    private suspend fun findCrossSourceDuplicate(
        parsed: OrderEmailParser.ParsedOrder,
        source: String,
    ): DeliveryOrder? {
        val otherSources = if (source == "SMS") {
            listOf("GMAIL", "IMAP")
        } else {
            listOf("SMS")
        }
        val candidates = dao.findBySources(otherSources)
        if (candidates.isEmpty()) return null

        parsed.trackingNumber?.takeIf { it.isNotBlank() }?.let { tracking ->
            candidates.firstOrNull {
                it.trackingNumber?.equals(tracking, ignoreCase = true) == true
            }?.let { return it }
        }

        val item = parsed.itemName?.takeIf { it.isNotBlank() } ?: return null
        val itemTokens = itemTokens(item)
        if (itemTokens.size < 2) return null

        return candidates.firstOrNull { candidate ->
            candidate.merchant.equals(parsed.merchant, ignoreCase = true) &&
                sameProduct(itemTokens, itemTokens(candidate.itemName)) &&
                datesAreClose(parsed.promisedDate?.toEpochDay(), candidate.promisedEpochDay)
        }
    }

    private fun datesAreClose(first: Long?, second: Long?): Boolean {
        if (first == null || second == null) return false
        return kotlin.math.abs(first - second) <= 2L
    }

    private fun sameProduct(first: Set<String>, second: Set<String>): Boolean {
        if (first.isEmpty() || second.isEmpty()) return false
        val overlap = first.intersect(second).size
        return overlap >= 2 && overlap.toDouble() / minOf(first.size, second.size) >= 0.6
    }

    private fun itemTokens(value: String?): Set<String> =
        value.orEmpty()
            .lowercase(Locale.ENGLISH)
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(' ')
            .filter { it.length >= 3 && it !in GENERIC_TOKENS }
            .toSet()

    private fun mergeStatus(
        current: OrderStatus,
        incoming: OrderStatus,
        newerEmail: Boolean,
    ): OrderStatus {
        // Terminal states stick. A cancellation/return can override delivery.
        if (current == OrderStatus.CANCELLED) return OrderStatus.CANCELLED
        if (current == OrderStatus.RETURNED) return OrderStatus.RETURNED
        if (incoming == OrderStatus.CANCELLED || incoming == OrderStatus.RETURNED) {
            return incoming
        }
        if (incoming == OrderStatus.DELIVERED) return OrderStatus.DELIVERED
        if (current == OrderStatus.DELIVERED) return OrderStatus.DELIVERED

        // For live states, the latest email wins: out for delivery -> failed ->
        // ready for pickup -> out for delivery again all need to be reflected.
        return if (newerEmail) incoming else current
    }

    private val GENERIC_TOKENS = setOf(
        "your", "the", "and", "for", "with", "from", "has", "have", "been",
        "order", "item", "items", "product", "package", "parcel", "shipment",
        "delivery", "delivered", "shipping", "shipped", "arriving", "arrive",
        "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "store",
        "this", "that", "will", "today", "tomorrow", "status", "update",
    )

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
            .replace(Regex("(?i)^(?:ordered|order\\s+placed)\\s*[:#\\-–]?\\s*"), "")
            .replace(Regex("(?i)^order\\s*[:#\\-–]\\s*"), "")
            .trim()

        if (value.lowercase(Locale.ENGLISH) in setOf("your order", "order", "order confirmed", "confirmation")) {
            value = ""
        }
        return value.ifBlank { "Order from ${parsed.merchant}" }.take(100)
    }
}
