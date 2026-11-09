package com.parceltracker.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class GmailApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun getProfileEmail(token: String): String = withContext(Dispatchers.IO) {
        val json = getJson(token, "$BASE/profile")
        json.optString("emailAddress").takeIf { it.contains("@") }
            ?: throw GmailApiException(500, "Gmail did not return an account address.")
    }

    suspend fun listMessageIds(
        token: String,
        query: String,
        maxResults: Int = 200,
    ): List<String> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<String>()
        var pageToken: String? = null

        while (ids.size < maxResults) {
            val builder = "$BASE/messages".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("maxResults", "100")
            if (!pageToken.isNullOrBlank()) {
                builder.addQueryParameter("pageToken", pageToken)
            }

            val json = getJson(token, builder.build().toString())
            val messages = json.optJSONArray("messages")
            if (messages != null) {
                for (index in 0 until messages.length()) {
                    val id = messages.optJSONObject(index)?.optString("id").orEmpty()
                    if (id.isNotBlank()) ids += id
                }
            }

            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            if (pageToken == null) break
        }

        ids.take(maxResults)
    }

    suspend fun getMessage(token: String, messageId: String): GmailMessage =
        withContext(Dispatchers.IO) {
            val url = "$BASE/messages/$messageId".toHttpUrl().newBuilder()
                .addQueryParameter("format", "full")
                .build()
                .toString()

            val json = getJson(token, url)
            val payload = json.optJSONObject("payload")
            val headers = payload?.optJSONArray("headers")
            val subject = header(headers, "Subject") ?: "(no subject)"
            val from = header(headers, "From") ?: ""
            val epochMillis = json.optString("internalDate").toLongOrNull()
                ?: System.currentTimeMillis()
            val snippet = json.optString("snippet").orEmpty()
            val bodyText = extractBody(payload, snippet)

            GmailMessage(
                id = messageId,
                threadId = json.optString("threadId").orEmpty(),
                subject = subject,
                from = from,
                receivedDate = Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate(),
                receivedEpochMillis = epochMillis,
                snippet = snippet,
                bodyText = bodyText,
            )
        }

    private fun getJson(token: String, url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw GmailApiException(response.code, extractError(body, response.message))
            }
            return try {
                JSONObject(body)
            } catch (error: Exception) {
                throw GmailApiException(response.code, "Gmail returned an unreadable response.")
            }
        }
    }

    private fun extractBody(payload: JSONObject?, snippet: String): String {
        if (payload == null) return snippet

        val plain = StringBuilder()
        val html = StringBuilder()
        collectBodies(payload, plain, html)

        val plainText = plain.toString().trim()
        val htmlText = if (html.isNotBlank()) HtmlText.toPlain(html.toString()) else ""

        val best = when {
            plainText.length >= 40 -> plainText
            htmlText.length > plainText.length -> htmlText
            else -> plainText
        }

        return best.ifBlank { snippet }.take(60_000)
    }

    private fun collectBodies(part: JSONObject, plain: StringBuilder, html: StringBuilder) {
        val mimeType = part.optString("mimeType").lowercase()
        val data = part.optJSONObject("body")?.optString("data").orEmpty()

        if (data.isNotBlank()) {
            when (mimeType) {
                "text/plain" -> plain.append(decodeBase64Url(data)).append('\n')
                "text/html" -> html.append(decodeBase64Url(data)).append('\n')
                "" -> plain.append(decodeBase64Url(data)).append('\n')
            }
        }

        val parts: JSONArray? = part.optJSONArray("parts")
        if (parts != null) {
            for (index in 0 until parts.length()) {
                parts.optJSONObject(index)?.let { collectBodies(it, plain, html) }
            }
        }
    }

    private fun decodeBase64Url(value: String): String = try {
        val bytes = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP)
        String(bytes, Charsets.UTF_8)
    } catch (_: Exception) {
        ""
    }

    private fun header(headers: JSONArray?, name: String): String? {
        if (headers == null) return null
        for (index in 0 until headers.length()) {
            val header = headers.optJSONObject(index) ?: continue
            if (header.optString("name").equals(name, ignoreCase = true)) {
                return header.optString("value").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun extractError(body: String, fallback: String): String {
        val message = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return message?.takeIf { it.isNotBlank() } ?: fallback.ifBlank { "Gmail request failed." }
    }

    companion object {
        private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me"
    }
}
