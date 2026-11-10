package com.parceltracker.app.data

import com.google.android.gms.common.api.ApiException
import com.parceltracker.app.BuildConfig

object AuthError {

    fun message(error: Throwable): String {
        val api = error as? ApiException
        if (api != null) {
            return when (api.statusCode) {
                10 -> "Google sign-in isn't configured for this build. Create an Android OAuth client for ${BuildConfig.APPLICATION_ID} with this app's signing SHA-1, enable the Gmail API, add the gmail.readonly scope, then reinstall."
                7 -> "No network connection. Check your connection and try again."
                8 -> "Google Play services had an internal error. Update Google Play services, make sure a Google account is added to the device, reboot, then try again. Emulators must use a Google Play system image."
                16 -> "Google Play services is unavailable right now. Try again in a moment."
                12500 -> "Google sign-in failed. Check that Google Play services is available, the Gmail API is enabled, and your account is a test user on the OAuth consent screen."
                12501 -> "Google sign-in was cancelled. If you didn't cancel it, open Sign-in help to check Play services, the package name and SHA-1."
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
