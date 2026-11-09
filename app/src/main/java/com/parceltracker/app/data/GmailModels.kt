package com.parceltracker.app.data

import java.time.LocalDate

data class GmailMessage(
    val id: String,
    val threadId: String,
    val subject: String,
    val from: String,
    val receivedDate: LocalDate,
    val receivedEpochMillis: Long,
    val snippet: String,
    val bodyText: String,
)

class GmailApiException(
    val statusCode: Int,
    message: String,
) : Exception(message)
