package com.parceltracker.app.data

import com.google.android.gms.common.api.ApiException

object AuthError {

    fun message(error: Throwable): String {
        val api = error as? ApiException
        if (api != null) {
            return when (api.statusCode) {
                10 -> "Google sign-in is not configured for this build yet. Register this app's SHA-1 fingerprint as an Android OAuth client in Google Cloud, then reinstall."
                7 -> "No network connection. Check your connection and try again."
                8 -> "Google Play services had an internal error. Please try again."
                16 -> "Google Play services is unavailable right now. Try again in a moment."
                12501 -> "Sign-in was cancelled."
                12502 -> "A sign-in is already in progress."
                else -> api.message?.takeIf { it.isNotBlank() }
                    ?: "Google sign-in failed (code ${api.statusCode})."
            }
        }
        return error.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
    }

    fun isConfigurationError(error: Throwable): Boolean =
        error is ApiException && error.statusCode == 10
}
