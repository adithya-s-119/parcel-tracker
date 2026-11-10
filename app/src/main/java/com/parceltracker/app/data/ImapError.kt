package com.parceltracker.app.data

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException

object ImapError {

    fun message(error: Throwable): String = when (error) {
        is AuthenticationFailedException -> {
            "Gmail rejected the email or app password. Use a 16-character Google app " +
                "password, not your normal Gmail password. Turn on 2-Step Verification, " +
                "create one at myaccount.google.com/apppasswords, then try again."
        }

        is UnknownHostException, is SocketTimeoutException -> {
            "Couldn't reach Gmail. Check your internet connection and try again."
        }

        is MessagingException -> {
            val detail = error.message.orEmpty()
            when {
                detail.contains("IMAP", ignoreCase = true) &&
                    detail.contains("disabled", ignoreCase = true) -> {
                    "IMAP access is disabled for this account. If this is a work or school " +
                        "account, ask your admin to enable IMAP, or use Google sign-in instead."
                }

                detail.contains("Application-specific password required", ignoreCase = true) -> {
                    "Google requires an app password for this account. Turn on 2-Step " +
                        "Verification and create one at myaccount.google.com/apppasswords."
                }

                detail.contains("Invalid credentials", ignoreCase = true) -> {
                    "Gmail rejected the email or app password. Generate a new app password " +
                        "and make sure 2-Step Verification is turned on."
                }

                else -> "Couldn't connect to Gmail: $detail"
            }
        }

        else -> error.message?.takeIf { it.isNotBlank() }
            ?: "Couldn't connect to Gmail. Check your connection and try again."
    }

    fun isAuthenticationFailure(error: Throwable): Boolean =
        error is AuthenticationFailedException
}
