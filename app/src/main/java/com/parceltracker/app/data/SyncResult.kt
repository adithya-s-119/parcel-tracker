package com.parceltracker.app.data

data class SyncResult(
    val outcome: Outcome,
    val scanned: Int = 0,
    val tracked: Int = 0,
    val updated: Int = 0,
    val message: String = "",
) {
    enum class Outcome { SUCCESS, NEEDS_AUTH, FAILED }
}
