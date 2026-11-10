package com.parceltracker.app.data

import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class GmailSyncManager(
    private val auth: GmailAuthManager,
    private val api: GmailApi,
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
        val account = settings.accountEmail
            ?: return SyncResult(
                SyncResult.Outcome.NEEDS_AUTH,
                message = "Connect Gmail to start syncing.",
            )

        if (fullRescan) {
            progress("Clearing previous results…")
            dao.clearScannedEmails()
            dao.deleteAllGmailOrders()
        }

        val token = try {
            when (val result = auth.getToken(account)) {
                is GmailAuthManager.TokenResult.Success -> result.token
                is GmailAuthManager.TokenResult.Resolution,
                GmailAuthManager.TokenResult.Unavailable -> {
                    settingsStore.setAuthNeeded(true)
                    return SyncResult(
                        SyncResult.Outcome.NEEDS_AUTH,
                        message = "Gmail needs reconnecting.",
                    )
                }
            }
        } catch (error: Exception) {
            if (AuthError.isConfigurationError(error)) {
                return SyncResult(
                    SyncResult.Outcome.FAILED,
                    message = AuthError.message(error),
                )
            }
            val apiError = error as? ApiException
            if (apiError != null && apiError.statusCode in setOf(7, 8, 16)) {
                return SyncResult(
                    SyncResult.Outcome.FAILED,
                    message = AuthError.message(error),
                )
            }
            settingsStore.setAuthNeeded(true)
            return SyncResult(
                SyncResult.Outcome.NEEDS_AUTH,
                message = AuthError.message(error),
            )
        }

        progress("Searching for order emails…")
        val query = buildQuery(settings.scanDays)
        val messageIds = try {
            api.listMessageIds(token, query, maxResults = 250)
        } catch (error: GmailApiException) {
            return apiFailure(error)
        } catch (error: Exception) {
            return networkFailure(error)
        }

        if (messageIds.isEmpty()) {
            settingsStore.setAuthNeeded(false)
            val summary = "No order emails found in the last ${settings.scanDays} days."
            settingsStore.setLastSync(System.currentTimeMillis(), summary)
            onDataChanged()
            return SyncResult(SyncResult.Outcome.SUCCESS, message = summary)
        }

        val alreadyScanned = try {
            dao.scannedIds(messageIds).toSet()
        } catch (_: Exception) {
            emptySet()
        }
        val newIds = messageIds.filterNot { it in alreadyScanned }

        var tracked = 0
        var updated = 0
        var read = 0

        progress("Reading ${newIds.size} new emails…")

        try {
            newIds.chunked(4).forEach { chunk ->
                val results = coroutineScope {
                    chunk.map { id ->
                        async { runCatching { api.getMessage(token, id) } }
                    }.awaitAll()
                }

                for (result in results) {
                    val error = result.exceptionOrNull()
                    if (error is GmailApiException && error.statusCode == 401) {
                        throw error
                    }
                    val message = result.getOrNull() ?: continue

                    val parsed = runCatching { OrderEmailParser.parse(message) }.getOrNull()
                    if (parsed != null) {
                        when (repository.mergeGmail(parsed, message.id, message.threadId, message.receivedEpochMillis)) {
                            OrderRepository.MergeResult.INSERTED -> tracked++
                            OrderRepository.MergeResult.UPDATED -> updated++
                            OrderRepository.MergeResult.IGNORED -> Unit
                        }
                    }

                    dao.markScanned(listOf(ScannedEmail(message.id)))
                    read++
                }

                progress("Reading emails $read/${newIds.size}…")
            }
        } catch (error: GmailApiException) {
            return apiFailure(error)
        } catch (error: Exception) {
            return networkFailure(error)
        }

        val summary = when {
            tracked == 0 && updated == 0 -> "Checked ${messageIds.size} emails — no new deliveries."
            tracked > 0 && updated > 0 -> "Added $tracked deliveries · updated $updated."
            tracked > 0 -> "Added $tracked new ${if (tracked == 1) "delivery" else "deliveries"}."
            else -> "Updated $updated ${if (updated == 1) "delivery" else "deliveries"}."
        }

        settingsStore.setAuthNeeded(false)
        settingsStore.setLastSync(System.currentTimeMillis(), summary)
        onDataChanged()

        return SyncResult(
            outcome = SyncResult.Outcome.SUCCESS,
            scanned = messageIds.size,
            tracked = tracked,
            updated = updated,
            message = summary,
        )
    }

    private suspend fun apiFailure(error: GmailApiException): SyncResult {
        val lower = error.message.orEmpty().lowercase()
        return when {
            error.statusCode == 401 ||
                (error.statusCode == 403 && !lower.contains("quota") && !lower.contains("rate")) -> {
                auth.signOut()
                settingsStore.setAuthNeeded(true)
                SyncResult(
                    SyncResult.Outcome.NEEDS_AUTH,
                    message = "Gmail access expired or was not granted. Reconnect and allow read-only access.",
                )
            }

            error.statusCode == 429 || lower.contains("quota") -> SyncResult(
                SyncResult.Outcome.FAILED,
                message = "Gmail is rate limiting requests. Try again in a few minutes.",
            )

            else -> SyncResult(
                SyncResult.Outcome.FAILED,
                message = "Gmail error ${error.statusCode}: ${error.message}",
            )
        }
    }

    private fun networkFailure(error: Exception): SyncResult {
        val detail = error.message?.takeIf { it.isNotBlank() }
        return SyncResult(
            SyncResult.Outcome.FAILED,
            message = detail?.let { "Couldn't reach Gmail: $it" }
                ?: "Couldn't reach Gmail. Check your connection.",
        )
    }

    private fun buildQuery(days: Int): String {
        val window = days.coerceIn(30, 365)
        return "newer_than:${window}d -in:spam -in:trash " +
            "(category:purchases OR subject:order OR subject:delivery " +
            "OR subject:arriving OR subject:shipped OR subject:dispatched)"
    }
}
