package com.parceltracker.app.data

import kotlinx.coroutines.flow.first
import java.util.Locale

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
        val accounts = settings.accountEmails
            .distinctBy { it.lowercase(Locale.ENGLISH) }

        if (accounts.isEmpty()) {
            return SyncResult(
                SyncResult.Outcome.NEEDS_AUTH,
                message = "Connect Gmail to start syncing.",
            )
        }

        if (fullRescan) {
            progress("Clearing previous results…")
            dao.clearScannedEmails()
            dao.deleteAllGmailOrders()
        }

        // Move a password saved by a single-account build onto its account key.
        settings.accountEmail?.let { credentialStore.migrateLegacy(it) }

        var totalScanned = 0
        var totalTracked = 0
        var totalUpdated = 0
        var successfulAccounts = 0
        val failedAccounts = mutableListOf<String>()
        var allFailedForAuth = false

        accounts.forEachIndexed { index, email ->
            progress(
                if (accounts.size == 1) "Connecting to Gmail…"
                else "Connecting to $email (${index + 1}/${accounts.size})…",
            )

            val appPassword = credentialStore.getAppPassword(email)
            if (appPassword == null) {
                failedAccounts += email
                allFailedForAuth = true
                return@forEachIndexed
            }

            try {
                val refs = client.listMessages(
                    email = email,
                    appPassword = appPassword,
                    scanDays = settings.scanDays,
                ).map { ref ->
                    // UID space is per mailbox. Scope IDs so two Gmail accounts
                    // cannot collide in the scanned table or order IDs.
                    ref.copy(
                        id = scopedId(email, ref.uid),
                        threadId = scopedThreadId(email, ref.uid),
                    )
                }

                totalScanned += refs.size
                if (refs.isEmpty()) {
                    successfulAccounts++
                    return@forEachIndexed
                }

                val alreadyScanned = dao.scannedIds(refs.map { it.id }).toSet()
                // New orders matter most. Work through the backlog newest-first
                // so a fresh Flipkart/Amazon email is never stuck behind old mail.
                val pending = refs
                    .filterNot { it.id in alreadyScanned }
                    .sortedWith(
                        compareByDescending<ImapGmailClient.MessageRef> {
                            isPriorityStore(it.from)
                        }.thenByDescending { it.receivedEpochMillis },
                    )
                val toFetch = pending.take(MAX_MESSAGES_PER_SYNC)

                if (toFetch.isEmpty()) {
                    successfulAccounts++
                    return@forEachIndexed
                }

                progress(
                    if (accounts.size == 1) "Reading ${toFetch.size} new emails…"
                    else "$email: reading ${toFetch.size} emails…",
                )
                val messages = try {
                    client.fetchMessages(email, appPassword, toFetch) { read, total ->
                        progress(
                            if (accounts.size == 1) "Reading emails $read/$total…"
                            else "$email: reading $read/$total…",
                        )
                    }
                } catch (error: Exception) {
                    failedAccounts += email
                    if (ImapError.isAuthenticationFailure(error)) allFailedForAuth = true
                    return@forEachIndexed
                }

                messages.forEach { message ->
                    val parsed = runCatching { OrderEmailParser.parse(message) }.getOrNull()
                    if (parsed != null) {
                        when (
                            repository.mergeGmail(
                                parsed,
                                message.id,
                                message.threadId,
                                message.receivedEpochMillis,
                                source = "IMAP",
                            )
                        ) {
                            OrderRepository.MergeResult.INSERTED -> totalTracked++
                            OrderRepository.MergeResult.UPDATED -> totalUpdated++
                            OrderRepository.MergeResult.IGNORED -> Unit
                        }
                    }
                }

                dao.markScanned(messages.map { ScannedEmail(it.id) })
                successfulAccounts++
            } catch (error: Exception) {
                failedAccounts += email
                if (ImapError.isAuthenticationFailure(error)) allFailedForAuth = true
            }
        }

        if (successfulAccounts == 0) {
            settingsStore.setAuthNeeded(allFailedForAuth)
            return if (allFailedForAuth) {
                SyncResult(
                    SyncResult.Outcome.NEEDS_AUTH,
                    message = "Some Gmail accounts need reconnecting.",
                )
            } else {
                SyncResult(
                    SyncResult.Outcome.FAILED,
                    message = "Couldn't reach Gmail. Check your connection.",
                )
            }
        }

        val summary = buildString {
            when {
                totalTracked == 0 && totalUpdated == 0 -> append("Checked Gmail — no new deliveries.")
                totalTracked > 0 && totalUpdated > 0 ->
                    append("Added $totalTracked deliveries · updated $totalUpdated.")
                totalTracked > 0 ->
                    append("Added $totalTracked new ${if (totalTracked == 1) "delivery" else "deliveries"}.")
                else ->
                    append("Updated $totalUpdated ${if (totalUpdated == 1) "delivery" else "deliveries"}.")
            }
            if (accounts.size > 1) append(" Scanned ${accounts.size} accounts.")
            if (failedAccounts.isNotEmpty()) {
                append(
                    " ${failedAccounts.size} ${if (failedAccounts.size == 1) "account needs" else "accounts need"} attention.",
                )
            }
        }

        settingsStore.setAuthNeeded(failedAccounts.isNotEmpty())
        settingsStore.setLastSync(System.currentTimeMillis(), summary)
        onDataChanged()

        return SyncResult(
            outcome = SyncResult.Outcome.SUCCESS,
            scanned = totalScanned,
            tracked = totalTracked,
            updated = totalUpdated,
            message = summary,
        )
    }

    private fun isPriorityStore(from: String): Boolean =
        OrderEmailParser.PRIORITY_STORE_DOMAINS.any {
            from.contains(it, ignoreCase = true)
        }

    private fun scopedId(email: String, uid: Long): String =
        "imap-${accountKey(email)}-$uid"

    private fun scopedThreadId(email: String, uid: Long): String =
        "imap-thread-${accountKey(email)}-$uid"

    private fun accountKey(email: String): String =
        email.trim().lowercase(Locale.ENGLISH).hashCode().toUInt().toString(16)

    companion object {
        private const val MAX_MESSAGES_PER_SYNC = 100
    }
}
