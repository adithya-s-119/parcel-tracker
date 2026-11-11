package com.parceltracker.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "parcel_settings")

data class AppSettings(
    val accountEmails: List<String> = emptyList(),
    val authMode: AuthMode = AuthMode.NONE,
    val onboardingComplete: Boolean = false,
    val autoSync: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val smsEnabled: Boolean = false,
    val scanDays: Int = 180,
    val lastSyncAt: Long = 0L,
    val lastSyncSummary: String? = null,
    val authNeeded: Boolean = false,
    val accent: ParcelAccent = ParcelAccent.FOREST,
) {
    val accountEmail: String? get() = accountEmails.firstOrNull()
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val ACCOUNT_EMAILS = stringSetPreferencesKey("account_emails")
        val AUTH_MODE = stringPreferencesKey("auth_mode")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val SMS_ENABLED = booleanPreferencesKey("sms_enabled")
        val SCAN_DAYS = intPreferencesKey("scan_days")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_SUMMARY = stringPreferencesKey("last_sync_summary")
        val AUTH_NEEDED = booleanPreferencesKey("auth_needed")
        val ACCENT = stringPreferencesKey("accent_theme")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            val legacyEmail = prefs[Keys.ACCOUNT_EMAIL]
            val accountEmails = (prefs[Keys.ACCOUNT_EMAILS].orEmpty() + listOfNotNull(legacyEmail))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ENGLISH) }
                .sortedBy { it.lowercase(Locale.ENGLISH) }

            AppSettings(
                accountEmails = accountEmails,
                authMode = if (accountEmails.isEmpty()) AuthMode.NONE else AuthMode.IMAP,
                onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
                autoSync = prefs[Keys.AUTO_SYNC] ?: true,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
                smsEnabled = prefs[Keys.SMS_ENABLED] ?: false,
                scanDays = prefs[Keys.SCAN_DAYS] ?: 180,
                lastSyncAt = prefs[Keys.LAST_SYNC_AT] ?: 0L,
                lastSyncSummary = prefs[Keys.LAST_SYNC_SUMMARY],
                authNeeded = prefs[Keys.AUTH_NEEDED] ?: false,
                accent = ParcelAccent.from(prefs[Keys.ACCENT]),
            )
        }
        .distinctUntilChanged()

    suspend fun addAccount(email: String) {
        context.dataStore.edit { prefs ->
            val accounts = (prefs[Keys.ACCOUNT_EMAILS].orEmpty() +
                listOfNotNull(prefs[Keys.ACCOUNT_EMAIL], email))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ENGLISH) }
            prefs[Keys.ACCOUNT_EMAILS] = accounts.toSet()
            prefs.remove(Keys.ACCOUNT_EMAIL)
            prefs[Keys.AUTH_MODE] = AuthMode.IMAP.name
            prefs[Keys.AUTH_NEEDED] = false
        }
    }

    suspend fun removeAccount(email: String) {
        context.dataStore.edit { prefs ->
            val accounts = (prefs[Keys.ACCOUNT_EMAILS].orEmpty() +
                listOfNotNull(prefs[Keys.ACCOUNT_EMAIL]))
                .filterNot { it.equals(email, ignoreCase = true) }
            if (accounts.isEmpty()) {
                prefs.remove(Keys.ACCOUNT_EMAILS)
                prefs.remove(Keys.AUTH_MODE)
                prefs[Keys.AUTH_NEEDED] = false
            } else {
                prefs[Keys.ACCOUNT_EMAILS] = accounts.toSet()
            }
            prefs.remove(Keys.ACCOUNT_EMAIL)
        }
    }

    suspend fun setAuthMode(mode: AuthMode) {
        context.dataStore.edit { prefs ->
            if (mode == AuthMode.NONE) prefs.remove(Keys.AUTH_MODE)
            else prefs[Keys.AUTH_MODE] = mode.name
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SYNC] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    suspend fun setSmsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMS_ENABLED] = enabled }
    }

    suspend fun setScanDays(days: Int) {
        context.dataStore.edit { it[Keys.SCAN_DAYS] = days }
    }

    suspend fun setAccent(accent: ParcelAccent) {
        context.dataStore.edit { it[Keys.ACCENT] = accent.name }
    }

    suspend fun setLastSync(at: Long, summary: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_AT] = at
            if (summary == null) prefs.remove(Keys.LAST_SYNC_SUMMARY)
            else prefs[Keys.LAST_SYNC_SUMMARY] = summary
        }
    }

    suspend fun setAuthNeeded(needed: Boolean) {
        context.dataStore.edit { it[Keys.AUTH_NEEDED] = needed }
    }

    suspend fun clearAccount() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCOUNT_EMAIL)
            prefs.remove(Keys.ACCOUNT_EMAILS)
            prefs.remove(Keys.AUTH_MODE)
            prefs[Keys.AUTH_NEEDED] = false
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
