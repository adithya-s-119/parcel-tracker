package com.parceltracker.app.data

import kotlinx.coroutines.flow.first

class ImapSyncManager(
    private val client: ImapGmailClient,
    private val credentialStore: SecureCredentialStore,
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
        val email = settings.accountEmail
            ?: return SyncResult(
                SyncResult.Outcome.NEEDS_AUTH,
                message = "Connect Gmail to start syncing.",
            )
        val appPassword = credentialStore.getAppPassword()
            ?: run {
                settingsStore.setAuthNeeded(true)
                return SyncResult(
                    SyncResult.Outcome.NEEDS_AUTH,
                    message = "The Gmail app password is missing. Sign in again.",
                )
            }

        if (fullRescan) {
            progress("Clearing previous results…")
            dao.clearScannedEmails()
            dao.deleteAllGmailOrders()
        }

        progress("Connecting to Gmail…")
        val refs = try {
            client.listMessages(
                email = email,
                appPassword = appPassword,
                scanDays = settings.scanDays,
            )
        } catch (error: Exception) {
            return failure(error)
        }

        if (refs.isEmpty()) {
            settingsStore.setAuthNeeded(false)
            val summary = "No order emails found in the last ${settings.scanDays} days."
            settingsStore.setLastSync(System.currentTimeMillis(), summary)
            onDataChanged()
            return SyncResult(SyncResult.Outcome.SUCCESS, message = summary)
        }

        val alreadyScanned = dao.scannedIds(refs.map { it.id }).toSet()
        val pending = refs.filterNot { it.id in alreadyScanned }
        val toFetch = pending.take(MAX_MESSAGES_PER_SYNC)

        if (toFetch.isEmpty()) {
            settingsStore.setAuthNeeded(false)
            val summary = "No new order emails since the last sync."
            settingsStore.setLastSync(System.currentTimeMillis(), summary)
            onDataChanged()
            return SyncResult(SyncResult.Outcome.SUCCESS, message = summary)
        }

        progress("Reading ${toFetch.size} new emails…")
        val messages = try {
            client.fetchMessages(email, appPassword, toFetch)
        } catch (error: Exception) {
            return failure(error)
        }

        var tracked = 0
        var updated = 0

        messages.forEach { message ->
            val parsed = runCatching { OrderEmailParser.parse(message) }.getOrNull()
            if (parsed != null) {
                when (repository.mergeGmail(parsed, message.id, message.threadId, message.receivedEpochMillis)) {
                    OrderRepository.MergeResult.INSERTED -> tracked++
                    OrderRepository.MergeResult.UPDATED -> updated++
                    OrderRepository.MergeResult.IGNORED -> Unit
                }
            }
        }

        dao.markScanned(messages.map { ScannedEmail(it.id) })

        val summary = when {
            tracked == 0 && updated == 0 -> {
                "Checked ${refs.size} emails — no new deliveries."
            }

            tracked > 0 && updated > 0 -> "Added $tracked deliveries · updated $updated."
            tracked > 0 -> "Added $tracked new ${if (tracked == 1) "delivery" else "deliveries"}."
            else -> "Updated $updated ${if (updated == 1) "delivery" else "deliveries"}."
        } + if (pending.size > toFetch.size) {
            " More emails will be checked next sync."
        } else {
            ""
        }

        settingsStore.setAuthNeeded(false)
        settingsStore.setLastSync(System.currentTimeMillis(), summary)
        onDataChanged()

        return SyncResult(
            outcome = SyncResult.Outcome.SUCCESS,
            scanned = refs.size,
            tracked = tracked,
            updated = updated,
            message = summary,
        )
    }

    private suspend fun failure(error: Exception): SyncResult {
        return if (ImapError.isAuthenticationFailure(error)) {
            settingsStore.setAuthNeeded(true)
            SyncResult(
                SyncResult.Outcome.NEEDS_AUTH,
                message = ImapError.message(error),
            )
        } else {
            SyncResult(
                SyncResult.Outcome.FAILED,
                message = ImapError.message(error),
            )
        }
    }

    companion object {
        private const val MAX_MESSAGES_PER_SYNC = 100
    }
}
