package com.parceltracker.app.data

import android.content.Context
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads delivery-related SMS from the device inbox and parses promising dates.
 * Everything stays on the phone. Requires READ_SMS/RECEIVE_SMS.
 */
class SmsSyncManager(
    private val context: Context,
    private val repository: OrderRepository,
    private val dao: OrderDao,
    private val settingsStore: SettingsStore,
    private val onDataChanged: suspend () -> Unit,
) {

    suspend fun sync(
        fullRescan: Boolean = false,
        progress: (String) -> Unit = {},
    ): SyncResult {
        val settings = settingsStore.settings.first()
        if (!settings.smsEnabled) {
            return SyncResult(
                SyncResult.Outcome.SUCCESS,
                message = "Delivery texts are turned off.",
            )
        }
        if (!SmsPermission.hasReadAccess(context)) {
            return SyncResult(
                SyncResult.Outcome.FAILED,
                message = "Text message permission isn't granted. Enable it in Settings.",
            )
        }

        progress("Reading delivery texts…")
        val rows = try {
            withContext(Dispatchers.IO) { readInbox(settings.scanDays) }
        } catch (error: Exception) {
            return SyncResult(
                SyncResult.Outcome.FAILED,
                message = "Couldn't read text messages: ${error.message ?: "permission or provider error"}",
            )
        }

        val scanned = dao.scannedIds(rows.map { it.id }).toSet()
        val unseen = rows.filterNot { it.id in scanned }
        var tracked = 0
        var updated = 0

        unseen.forEachIndexed { index, row ->
            val parsed = runCatching {
                OrderEmailParser.parseSms(row.address, row.body, row.date)
            }.getOrNull()

            if (parsed != null) {
                val merchant = merchantFor(parsed, row)
                val itemName = parsed.itemName ?: row.body.take(90)
                val order = parsed.copy(merchant = merchant, itemName = itemName)
                when (repository.mergeGmail(order, row.id, row.threadId, row.epochMillis)) {
                    OrderRepository.MergeResult.INSERTED -> tracked++
                    OrderRepository.MergeResult.UPDATED -> updated++
                    OrderRepository.MergeResult.IGNORED -> Unit
                }
            }

            dao.markScanned(listOf(ScannedEmail(row.id)))
            if (index % 20 == 0) {
                progress("Reading texts ${index + 1}/${unseen.size}…")
            }
        }

        val summary = when {
            tracked == 0 && updated == 0 -> "Checked texts — no new deliveries."
            tracked > 0 && updated > 0 -> "Added $tracked deliveries · updated $updated from texts."
            tracked > 0 -> "Added $tracked ${if (tracked == 1) "delivery" else "deliveries"} from texts."
            else -> "Updated $updated ${if (updated == 1) "delivery" else "deliveries"} from texts."
        }

        onDataChanged()
        return SyncResult(
            outcome = SyncResult.Outcome.SUCCESS,
            scanned = rows.size,
            tracked = tracked,
            updated = updated,
            message = summary,
        )
    }

    /** Called by the SMS_RECEIVED broadcast for a new message. */
    suspend fun ingest(address: String, body: String, timestampMillis: Long) {
        if (body.isBlank()) return
        val settings = settingsStore.settings.first()
        if (!settings.smsEnabled) return

        val date = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val id = smsId(address, timestampMillis, body)
        if (dao.scannedIds(listOf(id)).isNotEmpty()) return

        val parsed = runCatching {
            OrderEmailParser.parseSms(address, body, date)
        }.getOrNull() ?: run {
            dao.markScanned(listOf(ScannedEmail(id)))
            return
        }

        val row = SmsRow(
            id = id,
            address = address,
            body = body,
            date = date,
            epochMillis = timestampMillis,
            threadId = "sms-thread-$id",
        )
        val merchant = merchantFor(parsed, row)
        val itemName = parsed.itemName ?: body.take(90)
        repository.mergeGmail(
            parsed.copy(merchant = merchant, itemName = itemName),
            row.id,
            row.threadId,
            row.epochMillis,
        )
        dao.markScanned(listOf(ScannedEmail(row.id)))
        onDataChanged()
    }

    private fun readInbox(scanDays: Int): List<SmsRow> = runCatching {
        val since = LocalDate.now()
            .minusDays(scanDays.toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val keywordClauses = listOf(
            "body LIKE ?",
            "body LIKE ?",
            "body LIKE ?",
            "body LIKE ?",
            "body LIKE ?",
            "body LIKE ?",
            "body LIKE ?",
            "body LIKE ?",
        )
        val senders = listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa",
            "delhivery", "bluedart", "ekart", "dtdc", "fedex", "dhl",
            "ups", "usps", "indiapost", "xpressbees", "shadowfax", "courier",
        )
        val senderClauses = senders.map { "address LIKE ?" }
        val selection = "date >= ? AND (" +
            (keywordClauses + senderClauses).joinToString(" OR ") + ")"

        val args = buildList {
            add(since.toString())
            listOf(
                "%deliver%", "%order%", "%shipped%", "%arriving%",
                "%package%", "%parcel%", "%courier%", "%dispatch%",
            ).forEach { add(it) }
            senders.forEach { add("%$it%") }
        }.toTypedArray()

        val rows = mutableListOf<SmsRow>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            ),
            selection,
            args,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext() && rows.size < MAX_SMS) {
                val address = cursor.getString(addressIndex).orEmpty()
                val body = cursor.getString(bodyIndex).orEmpty()
                if (body.isBlank()) continue
                val epochMillis = cursor.getLong(dateIndex)
                val date = Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                val id = smsId(address, epochMillis, body)
                rows += SmsRow(
                    id = id,
                    address = address,
                    body = body,
                    date = date,
                    epochMillis = epochMillis,
                    threadId = "sms-thread-$id",
                )
            }
        }
        rows
    }.getOrDefault(emptyList())

    private fun merchantFor(
        parsed: OrderEmailParser.ParsedOrder,
        row: SmsRow,
    ): String {
        val current = parsed.merchant
        if (current !in CARRIERS && current != "Unknown store") return current
        return OrderEmailParser.guessMerchant(row.body)
            ?: OrderEmailParser.guessMerchant(row.address)
            ?: current
    }

    private fun smsId(address: String, epochMillis: Long, body: String): String =
        "sms-" + sha1("$address|$epochMillis|$body").take(18)

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private data class SmsRow(
        val id: String,
        val address: String,
        val body: String,
        val date: LocalDate,
        val epochMillis: Long,
        val threadId: String,
    )

    companion object {
        private const val MAX_SMS = 600
        private val CARRIERS = setOf(
            "Amazon Logistics", "Delhivery", "Blue Dart", "Ekart", "DTDC",
            "India Post", "FedEx", "UPS", "USPS", "DHL",
        )
    }
}
