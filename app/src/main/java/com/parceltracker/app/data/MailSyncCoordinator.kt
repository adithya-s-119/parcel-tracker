package com.parceltracker.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Chooses the active mail source and serializes syncs so two IMAP connections
 * never run at the same time.
 */
class MailSyncCoordinator(
    private val settingsStore: SettingsStore,
    private val dao: OrderDao,
    private val oauthSync: GmailSyncManager,
    private val imapSync: ImapSyncManager,
    private val smsSync: SmsSyncManager,
) {

    private val mutex = Mutex()

    suspend fun sync(
        fullRescan: Boolean = false,
        progress: (String) -> Unit = {},
    ): SyncResult = mutex.withLock {
        val settings = settingsStore.settings.first()

        if (fullRescan) {
            // A rebuild must start from a clean slate for every source, so an
            // old SMS update can't be merged into a stale duplicate row.
            progress("Clearing previous results.")
            dao.deleteAllImportedOrders()
            dao.clearScannedEmails()
        }

        val mailResult = when (settings.authMode) {
            AuthMode.OAUTH -> oauthSync.sync(fullRescan, progress)
            AuthMode.IMAP -> imapSync.sync(fullRescan, progress)
            AuthMode.NONE -> null
        }
        val smsResult = if (settings.smsEnabled) {
            smsSync.sync(fullRescan, progress)
        } else {
            null
        }

        val results = listOfNotNull(mailResult, smsResult)
        if (results.isEmpty()) {
            return@withLock SyncResult(
                SyncResult.Outcome.NEEDS_AUTH,
                message = "Connect Gmail or turn on delivery texts to start syncing.",
            )
        }

        val outcome = when {
            results.any { it.outcome == SyncResult.Outcome.SUCCESS } ->
                SyncResult.Outcome.SUCCESS
            results.any { it.outcome == SyncResult.Outcome.NEEDS_AUTH } ->
                SyncResult.Outcome.NEEDS_AUTH
            else -> SyncResult.Outcome.FAILED
        }
        val message = results
            .map { it.message }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        val combined = SyncResult(
            outcome = outcome,
            scanned = results.sumOf { it.scanned },
            tracked = results.sumOf { it.tracked },
            updated = results.sumOf { it.updated },
            message = message,
        )

        if (outcome == SyncResult.Outcome.SUCCESS) {
            settingsStore.setLastSync(System.currentTimeMillis(), message)
        }
        combined
    }
}
