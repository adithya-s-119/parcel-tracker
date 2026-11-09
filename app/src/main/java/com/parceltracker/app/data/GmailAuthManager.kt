package com.parceltracker.app.data

import android.accounts.Account
import android.content.Context
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

class GmailAuthManager(private val context: Context) {

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var expiresAtMillis: Long = 0L

    sealed interface TokenResult {
        data class Success(val token: String) : TokenResult
        data class Resolution(val intentSender: IntentSender) : TokenResult
        data object Unavailable : TokenResult
    }

    suspend fun getToken(accountEmail: String?): TokenResult {
        val current = cachedToken
        if (!current.isNullOrBlank() && System.currentTimeMillis() < expiresAtMillis - 60_000L) {
            return TokenResult.Success(current)
        }

        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(SCOPES)

        if (!accountEmail.isNullOrBlank()) {
            builder.setAccount(Account(accountEmail, "com.google"))
        }

        val result = Identity.getAuthorizationClient(context).authorize(builder.build()).await()

        return when {
            result.hasResolution() -> {
                result.pendingIntent?.let { TokenResult.Resolution(it.intentSender) }
                    ?: TokenResult.Unavailable
            }

            !result.accessToken.isNullOrBlank() -> {
                val token = result.accessToken!!
                acceptToken(token, result.expirationTime)
                TokenResult.Success(token)
            }

            else -> TokenResult.Unavailable
        }
    }

    fun acceptResult(result: AuthorizationResult): Boolean {
        val token = result.accessToken ?: return false
        acceptToken(token, result.expirationTime)
        return true
    }

    fun hasCachedToken(): Boolean =
        !cachedToken.isNullOrBlank() && System.currentTimeMillis() < expiresAtMillis - 60_000L

    fun signOut() {
        cachedToken = null
        expiresAtMillis = 0L
    }

    private fun acceptToken(token: String, expirationTime: Long) {
        cachedToken = token
        expiresAtMillis = if (expirationTime > System.currentTimeMillis()) {
            expirationTime
        } else {
            System.currentTimeMillis() + 50 * 60 * 1000L
        }
    }

    companion object {
        val SCOPES: List<Scope> = listOf(
            Scope("https://www.googleapis.com/auth/gmail.readonly"),
        )
    }
}
