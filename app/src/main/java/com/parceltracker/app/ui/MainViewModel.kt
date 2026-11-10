package com.parceltracker.app.ui

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.parceltracker.app.ParcelApp
import com.parceltracker.app.data.AppSettings
import com.parceltracker.app.data.AuthDiagnostics
import com.parceltracker.app.data.AuthError
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.data.GmailAuthManager
import com.parceltracker.app.data.GmailSyncManager
import com.parceltracker.app.data.OrderDraft
import com.parceltracker.app.notifications.Notifications
import com.parceltracker.app.widget.DeliveryWidget
import com.parceltracker.app.work.WorkScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data object Connecting : SyncUiState
    data class Syncing(val message: String) : SyncUiState
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ParcelApp).container

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val settings: StateFlow<AppSettings> = container.settingsStore.settings
        .onEach { _ready.value = true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val orders: StateFlow<List<DeliveryOrder>> = container.repository.observeOrders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _syncState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    fun connect(
        uiContext: Context,
        onResolution: (IntentSender) -> Unit,
    ) {
        if (_syncState.value != SyncUiState.Idle) return
        if (!AuthDiagnostics.playServicesAvailable(getApplication())) {
            _authError.value = "Google Play services is not available on this device: " +
                "${AuthDiagnostics.playServicesStatus(getApplication())}. " +
                "Gmail sign-in needs a device or emulator with Google Play services."
            return
        }
        viewModelScope.launch {
            _authError.value = null
            _syncState.value = SyncUiState.Connecting
            try {
                val result = requestToken(uiContext, settings.value.accountEmail)
                when (result) {
                    is GmailAuthManager.TokenResult.Success -> {
                        _syncState.value = SyncUiState.Idle
                        finishConnect(result.token)
                    }

                    is GmailAuthManager.TokenResult.Resolution -> {
                        _syncState.value = SyncUiState.Idle
                        onResolution(result.intentSender)
                    }

                    GmailAuthManager.TokenResult.Unavailable -> {
                        _syncState.value = SyncUiState.Idle
                        _authError.value = "Google did not return an account. Please try again."
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Gmail authorization request failed", error)
                _syncState.value = SyncUiState.Idle
                _authError.value = AuthError.message(error)
            }
        }
    }

    private suspend fun requestToken(
        uiContext: Context,
        accountEmail: String?,
    ): GmailAuthManager.TokenResult {
        return try {
            container.authManager.getToken(accountEmail, uiContext)
        } catch (error: ApiException) {
            // Play services occasionally reports a transient internal error; retry once.
            if (error.statusCode == 8) {
                Log.w(TAG, "Play services internal error, retrying authorization once", error)
                delay(800)
                container.authManager.getToken(accountEmail, uiContext)
            } else {
                throw error
            }
        }
    }

    fun handleAuthorizationResult(result: AuthorizationResult?) {
        if (result == null) {
            Log.w(TAG, "Authorization returned no result (cancelled or blocked)")
            _authError.value = "Google sign-in was cancelled. If you didn't cancel it, " +
                "open Sign-in help to check Play services and the OAuth client."
            return
        }
        if (!container.authManager.acceptResult(result)) {
            Log.w(TAG, "Authorization result has no access token")
            _authError.value = "Google sign-in didn't return an access token. " +
                "Check that the Gmail API is enabled and your account is a test user, then try again."
            return
        }
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            _authError.value = "Google did not return an access token. Please try again."
            return
        }
        viewModelScope.launch { finishConnect(token) }
    }

    fun handleAuthorizationFailure(error: Throwable?) {
        Log.e(TAG, "Google authorization failed", error)
        _authError.value = error?.let { AuthError.message(it) }
            ?: "Google returned an empty sign-in result. Check Google Play services and the OAuth client setup, then try again."
    }

    private suspend fun finishConnect(token: String) {
        _syncState.value = SyncUiState.Connecting
        try {
            val email = container.gmailApi.getProfileEmail(token)
            container.settingsStore.setAccount(email)
            container.settingsStore.setOnboardingComplete(true)
            container.settingsStore.setAuthNeeded(false)
            _authError.value = null
            _syncState.value = SyncUiState.Idle
            syncNow(announce = true)
        } catch (error: Exception) {
            _syncState.value = SyncUiState.Idle
            _authError.value = AuthError.message(error)
        }
    }

    fun syncNow(fullRescan: Boolean = false, announce: Boolean = true) {
        if (_syncState.value != SyncUiState.Idle) return
        viewModelScope.launch {
            _syncState.value = SyncUiState.Syncing("Starting…")
            val result = runCatching {
                container.syncManager.sync(fullRescan) { message ->
                    _syncState.value = SyncUiState.Syncing(message)
                }
            }

            result.onSuccess { syncResult ->
                _syncState.value = SyncUiState.Idle
                if (syncResult.outcome == GmailSyncManager.SyncResult.Outcome.NEEDS_AUTH) {
                    _authError.value = syncResult.message
                }
                if (announce || syncResult.tracked > 0 || syncResult.updated > 0) {
                    _events.emit(UiEvent.Message(syncResult.message))
                }
            }.onFailure { error ->
                _syncState.value = SyncUiState.Idle
                _events.emit(UiEvent.Message(error.message ?: "Sync failed."))
            }

            WorkScheduler.apply(getApplication(), container.settingsStore.settings.first())
        }
    }

    fun continueWithoutGmail() {
        viewModelScope.launch {
            container.settingsStore.setOnboardingComplete(true)
        }
    }

    fun onAppStart() {
        val current = settings.value
        if (!current.onboardingComplete || current.accountEmail == null) return
        if (!current.autoSync || current.authNeeded) return
        if (System.currentTimeMillis() - current.lastSyncAt < 15 * 60 * 1000L) return
        syncNow(announce = false)
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setAutoSync(enabled)
            WorkScheduler.apply(getApplication(), container.settingsStore.settings.first())
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setNotificationsEnabled(enabled)
            if (enabled) Notifications.ensureChannel(getApplication())
            WorkScheduler.apply(getApplication(), container.settingsStore.settings.first())
        }
    }

    fun setScanDays(days: Int) {
        viewModelScope.launch {
            container.settingsStore.setScanDays(days)
        }
    }

    fun signOutGmail() {
        container.authManager.signOut()
        viewModelScope.launch {
            container.settingsStore.clearAccount()
            WorkScheduler.cancelSync(getApplication())
            _events.emit(
                UiEvent.Message("Gmail disconnected. Your saved deliveries are still here."),
            )
        }
    }

    fun deleteAllData() {
        container.authManager.signOut()
        viewModelScope.launch {
            container.database.orderDao().deleteAll()
            container.database.orderDao().clearScannedEmails()
            container.settingsStore.clearAll()
            WorkScheduler.cancelSync(getApplication())
            DeliveryWidget.refresh(getApplication())
            _events.emit(UiEvent.Message("All deliveries and account data were deleted."))
        }
    }

    fun saveOrder(existing: DeliveryOrder?, draft: OrderDraft) {
        viewModelScope.launch {
            runCatching {
                if (existing == null) {
                    container.repository.addManual(draft)
                } else {
                    container.repository.updateFromEditor(existing, draft)
                }
            }.onSuccess {
                DeliveryWidget.refresh(getApplication())
                _events.emit(
                    UiEvent.Message(if (existing == null) "Delivery added." else "Delivery updated."),
                )
            }.onFailure {
                _events.emit(UiEvent.Message("Couldn't save the delivery. Please try again."))
            }
        }
    }

    fun markDelivered(order: DeliveryOrder) {
        viewModelScope.launch {
            runCatching { container.repository.markDelivered(order) }
                .onSuccess {
                    DeliveryWidget.refresh(getApplication())
                    _events.emit(UiEvent.Message("Marked as delivered."))
                }
                .onFailure {
                    _events.emit(UiEvent.Message("Couldn't update the delivery."))
                }
        }
    }

    fun deleteOrder(order: DeliveryOrder) {
        viewModelScope.launch {
            runCatching { container.repository.delete(order) }
                .onSuccess {
                    DeliveryWidget.refresh(getApplication())
                    _events.emit(UiEvent.Message("Delivery removed."))
                }
                .onFailure {
                    _events.emit(UiEvent.Message("Couldn't remove the delivery."))
                }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    companion object {
        private const val TAG = "ParcelAuth"
    }
}
