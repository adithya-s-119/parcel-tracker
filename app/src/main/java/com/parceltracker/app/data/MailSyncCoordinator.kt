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
    private val oauthSync: GmailSyncManager,
    private val imapSync: ImapSyncManager,
) {

    private val mutex = Mutex()

    suspend fun sync(
        fullRescan: Boolean = false,
        progress: (String) -> Unit = {},
    ): SyncResult = mutex.withLock {
        val settings = settingsStore.settings.first()
        when (settings.authMode) {
            AuthMode.OAUTH -> oauthSync.sync(fullRescan, progress)
            AuthMode.IMAP -> imapSync.sync(fullRescan, progress)
            AuthMode.NONE -> SyncResult(
                SyncResult.Outcome.NEEDS_AUTH,
                message = "Connect Gmail to start syncing.",
            )
        }
    }
}
