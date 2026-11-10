package com.parceltracker.app.data

import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.OrTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SearchTerm
import javax.mail.search.SubjectTerm

/**
 * Gmail IMAP client used by the email + app-password sign-in path.
 * No Google Cloud project or OAuth client is involved.
 */
class ImapGmailClient {

    data class MessageRef(
        val uid: Long,
        val id: String,
        val subject: String,
        val from: String,
        val receivedDate: LocalDate,
        val receivedEpochMillis: Long,
        val threadId: String,
    )

    suspend fun verifyCredentials(email: String, appPassword: String) =
        withContext(Dispatchers.IO) {
            withStore(email, appPassword) { store ->
                val folder = store.getFolder("INBOX")
                folder.open(Folder.READ_ONLY)
                folder.close(false)
            }
        }

    suspend fun listMessages(
        email: String,
        appPassword: String,
        scanDays: Int,
        maxRefs: Int = 500,
    ): List<MessageRef> = withContext(Dispatchers.IO) {
        withStore(email, appPassword) { store ->
            val folder = openMailFolder(store)
            try {
                val messages = searchMessages(folder, scanDays)
                if (messages.isEmpty()) return@withStore emptyList()

                val profile = FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE)
                }
                folder.fetch(messages, profile)

                val uidFolder = folder as? UIDFolder ?: return@withStore emptyList()

                messages.mapNotNull { message ->
                    runCatching {
                        val uid = uidFolder.getUID(message)
                        if (uid <= 0L) return@mapNotNull null

                        val sent = message.sentDate
                            ?: message.receivedDate
                            ?: Date()
                        val localDate = sent.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val subject = message.subject?.takeIf { it.isNotBlank() }
                            ?: "(no subject)"
                        val from = runCatching {
                            message.from?.firstOrNull()?.toString().orEmpty()
                        }.getOrDefault("")

                        MessageRef(
                            uid = uid,
                            id = "imap-$uid",
                            subject = subject,
                            from = from,
                            receivedDate = localDate,
                            receivedEpochMillis = sent.time,
                            threadId = "imap-thread-$uid",
                        )
                    }.getOrNull()
                }
                    .sortedByDescending { it.receivedEpochMillis }
                    .take(maxRefs)
            } finally {
                runCatching { folder.close(false) }
            }
        }
    }

    suspend fun fetchMessages(
        email: String,
        appPassword: String,
        refs: List<MessageRef>,
        onProgress: (read: Int, total: Int) -> Unit = { _, _ -> },
    ): List<GmailMessage> = withContext(Dispatchers.IO) {
        if (refs.isEmpty()) return@withContext emptyList()

        withStore(email, appPassword) { store ->
            val folder = openMailFolder(store)
            try {
                val uidFolder = folder as? UIDFolder ?: return@withStore emptyList()
                val result = mutableListOf<GmailMessage>()
                var processed = 0
                val bodyProfile = FetchProfile().apply { add("BODY.PEEK[]") }

                refs.chunked(20).forEach { chunk ->
                    val messages = chunk.mapNotNull { ref ->
                        runCatching {
                            uidFolder.getMessageByUID(ref.uid)?.let { ref to it }
                        }.getOrNull()
                    }
                    if (messages.isNotEmpty()) {
                        // One IMAP round-trip for each batch of bodies, instead of one per email.
                        runCatching {
                            folder.fetch(messages.map { it.second }.toTypedArray(), bodyProfile)
                        }
                    }

                    messages.forEach { (ref, message) ->
                        val body = runCatching { extractBody(message) }.getOrDefault("").take(60_000)
                        result += GmailMessage(
                            id = ref.id,
                            threadId = ref.threadId,
                            subject = ref.subject,
                            from = ref.from,
                            receivedDate = ref.receivedDate,
                            receivedEpochMillis = ref.receivedEpochMillis,
                            snippet = HtmlText.normalizeSpace(body).take(220),
                            bodyText = body,
                        )
                    }

                    processed += chunk.size
                    onProgress(processed.coerceAtMost(refs.size), refs.size)
                }
                result
            } finally {
                runCatching { folder.close(false) }
            }
        }
    }

    private fun openMailFolder(store: Store): Folder {
        val candidates = listOf("[Gmail]/All Mail", "[Google Mail]/All Mail")
        for (path in candidates) {
            val folder = runCatching { store.getFolder(path) }.getOrNull() ?: continue
            if (runCatching { folder.exists() }.getOrDefault(false)) {
                folder.open(Folder.READ_ONLY)
                return folder
            }
        }

        val allMail = runCatching {
            store.defaultFolder.list("*")
                .firstOrNull { it.name.contains("All Mail", ignoreCase = true) }
        }.getOrNull()
        if (allMail != null) {
            allMail.open(Folder.READ_ONLY)
            return allMail
        }

        return store.getFolder("INBOX").apply { open(Folder.READ_ONLY) }
    }

    private fun searchMessages(folder: Folder, scanDays: Int): Array<Message> {
        val imapFolder = folder as? IMAPFolder
        if (imapFolder != null) {
            val uids = runCatching { gmailRawSearch(imapFolder, scanDays) }.getOrNull()
            if (uids != null) {
                if (uids.isEmpty()) return emptyArray()
                return (imapFolder as UIDFolder).getMessagesByUID(uids)
            }
        }
        return folder.search(buildSearchTerm(scanDays))
    }

    /**
     * Gmail's X-GM-RAW search runs Gmail's native query engine on the server,
     * avoiding a full client-side scan of the All Mail folder.
     */
    private fun gmailRawSearch(folder: IMAPFolder, scanDays: Int): LongArray {
        val days = scanDays.coerceIn(30, 365)
        val query = "(category:purchases OR subject:order OR subject:delivery " +
            "OR subject:shipped OR subject:arriving OR subject:dispatched " +
            "OR ${OrderEmailParser.HOBBY_STORE_QUERY}) " +
            "${OrderEmailParser.NON_DELIVERY_QUERY} " +
            "newer_than:${days}d -category:promotions -category:social -category:forums"
        val escaped = query.replace("\\", "\\\\").replace("\"", "\\\"")
        var found = LongArray(0)

        folder.doCommand { protocol ->
            val responses = protocol.command("UID SEARCH X-GM-RAW \"$escaped\"", null)
            val uids = mutableListOf<Long>()
            for (response in responses) {
                val text = response.toString()
                val idx = text.indexOf("SEARCH", ignoreCase = true)
                if (idx < 0) continue
                text.substring(idx + "SEARCH".length)
                    .trim()
                    .split(Regex("\\s+"))
                    .mapNotNullTo(uids) { it.toLongOrNull() }
            }
            found = uids.takeLast(500).toLongArray()
            responses
        }
        return found
    }

    private fun buildSearchTerm(scanDays: Int): SearchTerm {
        val since = Date.from(
            LocalDate.now()
                .minusDays(scanDays.toLong())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant(),
        )
        val keywords = listOf(
            "order",
            "delivery",
            "shipped",
            "arriving",
            "dispatched",
            "delivered",
            "cancelled",
            "package",
            "parcel",
        )
        var subjectTerm: SearchTerm = SubjectTerm(keywords.first())
        for (keyword in keywords.drop(1)) {
            subjectTerm = OrTerm(subjectTerm, SubjectTerm(keyword))
        }
        return AndTerm(
            subjectTerm,
            ReceivedDateTerm(ComparisonTerm.GE, since),
        )
    }

    private fun extractBody(part: Part): String {
        if (part.disposition?.equals(Part.ATTACHMENT, ignoreCase = true) == true) return ""
        if (part.fileName != null) return ""

        return runCatching {
            when (val content = part.content) {
                is String -> {
                    if (part.isMimeType("text/html")) HtmlText.toPlain(content) else content
                }

                is Multipart -> {
                    val plain = StringBuilder()
                    val html = StringBuilder()
                    for (index in 0 until content.count) {
                        val child = content.getBodyPart(index)
                        val childText = extractBody(child)
                        if (childText.isBlank()) continue
                        if (child.isMimeType("text/html")) {
                            html.append(childText).append('\n')
                        } else {
                            plain.append(childText).append('\n')
                        }
                    }
                    when {
                        plain.isNotBlank() -> plain.toString()
                        html.isNotBlank() -> html.toString()
                        else -> ""
                    }
                }

                is Message -> extractBody(content)
                else -> ""
            }
        }.getOrDefault("")
    }

    private fun <T> withStore(
        email: String,
        appPassword: String,
        block: (Store) -> T,
    ): T {
        val session = Session.getInstance(properties())
        var store: Store? = null
        try {
            store = session.getStore("imaps")
            store.connect(HOST, PORT, email, appPassword)
            return block(store)
        } finally {
            runCatching { store?.close() }
        }
    }

    private fun properties(): Properties = Properties().apply {
        put("mail.store.protocol", "imaps")
        put("mail.imaps.host", HOST)
        put("mail.imaps.port", PORT.toString())
        put("mail.imaps.ssl.enable", "true")
        put("mail.imaps.ssl.checkserveridentity", "true")
        put("mail.imaps.connectiontimeout", "20000")
        put("mail.imaps.timeout", "30000")
        put("mail.imaps.writetimeout", "30000")
        put("mail.imaps.fetchsize", "1048576")
    }

    companion object {
        private const val HOST = "imap.gmail.com"
        private const val PORT = 993
    }
}
