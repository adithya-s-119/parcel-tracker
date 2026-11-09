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
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "parcel_settings")

data class AppSettings(
    val accountEmail: String? = null,
    val onboardingComplete: Boolean = false,
    val autoSync: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val scanDays: Int = 180,
    val lastSyncAt: Long = 0L,
    val lastSyncSummary: String? = null,
    val authNeeded: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val SCAN_DAYS = intPreferencesKey("scan_days")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_SUMMARY = stringPreferencesKey("last_sync_summary")
        val AUTH_NEEDED = booleanPreferencesKey("auth_needed")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                accountEmail = prefs[Keys.ACCOUNT_EMAIL],
                onboardingComplete = prefs[Keys.ONBOARDING_COMPLETE] ?: false,
                autoSync = prefs[Keys.AUTO_SYNC] ?: true,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
                scanDays = prefs[Keys.SCAN_DAYS] ?: 180,
                lastSyncAt = prefs[Keys.LAST_SYNC_AT] ?: 0L,
                lastSyncSummary = prefs[Keys.LAST_SYNC_SUMMARY],
                authNeeded = prefs[Keys.AUTH_NEEDED] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun setAccount(email: String?) {
        context.dataStore.edit { prefs ->
            if (email == null) prefs.remove(Keys.ACCOUNT_EMAIL) else prefs[Keys.ACCOUNT_EMAIL] = email
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

    suspend fun setScanDays(days: Int) {
        context.dataStore.edit { it[Keys.SCAN_DAYS] = days }
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
            prefs[Keys.AUTH_NEEDED] = false
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
