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
import com.parceltracker.app.data.AuthMode
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.data.GmailAuthManager
import com.parceltracker.app.data.GmailMessage
import com.parceltracker.app.data.ImapError
import com.parceltracker.app.data.OrderDraft
import com.parceltracker.app.data.OrderEmailParser
import com.parceltracker.app.data.OrderStatus
import com.parceltracker.app.data.ParcelAccent
import com.parceltracker.app.data.SmsPermission
import com.parceltracker.app.data.SyncResult
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
import java.time.LocalDate

sealed interface SyncUiState {
    data object Idle : SyncUiState
    data object Connecting : SyncUiState
    data class Syncing(val message: String) : SyncUiState
}

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class UndoDelivered(val text: String) : UiEvent
}

data class SharedEmailImport(
    val draft: OrderDraft,
    val rawText: String,
)

data class ImapAuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ParcelApp).container
    private var lastDeliveredUndo: DeliveryOrder? = null

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

    private val _imapAuthState = MutableStateFlow(ImapAuthUiState())
    val imapAuthState: StateFlow<ImapAuthUiState> = _imapAuthState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _sharedImport = MutableStateFlow<SharedEmailImport?>(null)
    val sharedImport: StateFlow<SharedEmailImport?> = _sharedImport.asStateFlow()

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
                _authError.value = AuthError.message(
                    error,
                    AuthDiagnostics.signingSha1(getApplication()),
                )
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
        _authError.value = error?.let {
            AuthError.message(it, AuthDiagnostics.signingSha1(getApplication()))
        } ?: "Google returned an empty sign-in result. Check Google Play services and the OAuth client setup, then try again."
    }

    private suspend fun finishConnect(token: String) {
        _syncState.value = SyncUiState.Connecting
        try {
            val email = container.gmailApi.getProfileEmail(token)
            container.credentialStore.clearAllAppPasswords()
            container.settingsStore.setAuthMode(AuthMode.OAUTH)
            container.settingsStore.addAccount(email)
            container.settingsStore.setOnboardingComplete(true)
            container.settingsStore.setAuthNeeded(false)
            _authError.value = null
            _syncState.value = SyncUiState.Idle
            syncNow(announce = true)
        } catch (error: Exception) {
            _syncState.value = SyncUiState.Idle
            _authError.value = AuthError.message(
                error,
                AuthDiagnostics.signingSha1(getApplication()),
            )
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
                if (syncResult.outcome == SyncResult.Outcome.NEEDS_AUTH) {
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

    fun handleSharedText(subject: String, text: String) {
        val raw = text.trim()
        if (raw.isBlank()) return

        val fallbackSubject = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(120)
            ?: "Shared email"
        val shareSubject = subject.trim().ifBlank { fallbackSubject }

        val message = GmailMessage(
            id = "shared-${System.currentTimeMillis()}",
            threadId = "",
            subject = shareSubject,
            from = "",
            receivedDate = LocalDate.now(),
            receivedEpochMillis = System.currentTimeMillis(),
            snippet = raw.take(220),
            bodyText = raw,
        )
        val parsed = runCatching { OrderEmailParser.parse(message) }.getOrNull()
        val merchant = parsed?.merchant?.takeIf { it != "Unknown store" }
            ?: OrderEmailParser.guessMerchant("$shareSubject\n$raw")
            ?: "Shared email"

        _sharedImport.value = SharedEmailImport(
            draft = OrderDraft(
                merchant = merchant,
                itemName = parsed?.itemName ?: shareSubject,
                promisedDate = parsed?.promisedDate ?: LocalDate.now(),
                status = parsed?.status ?: OrderStatus.UPCOMING,
                orderNumber = parsed?.orderNumber,
                notes = "Imported from a shared email.",
            ),
            rawText = raw,
        )

        viewModelScope.launch {
            container.settingsStore.setOnboardingComplete(true)
        }
    }

    fun consumeSharedImport() {
        _sharedImport.value = null
    }

    fun onAppStart() {
        val current = settings.value
        if (!current.onboardingComplete) return
        if (!current.autoSync) return

        val hasMail = current.accountEmail != null
        val hasSms = current.smsEnabled && SmsPermission.hasReadAccess(getApplication())
        if (!hasMail && !hasSms) return
        if (hasMail && current.authNeeded && !hasSms && current.accountEmails.size <= 1) return
        if (System.currentTimeMillis() - current.lastSyncAt < 15 * 60 * 1000L) return
        syncNow(announce = false)
    }

    fun setSmsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.setSmsEnabled(enabled)
            WorkScheduler.apply(getApplication(), container.settingsStore.settings.first())
            if (enabled) syncNow(announce = true)
        }
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

    fun setAccent(accent: ParcelAccent) {
        viewModelScope.launch {
            container.settingsStore.setAccent(accent)
            DeliveryWidget.refresh(getApplication())
        }
    }

    fun signInWithAppPassword(rawEmail: String, rawPassword: String) {
        if (_imapAuthState.value.loading) return

        val email = rawEmail.trim()
        val password = rawPassword.replace(" ", "").trim()
        if (!email.contains("@")) {
            _imapAuthState.value = ImapAuthUiState(error = "Enter your full Gmail address.")
            return
        }
        if (password.length < 8) {
            _imapAuthState.value = ImapAuthUiState(
                error = "Enter the 16-character Google app password.",
            )
            return
        }

        viewModelScope.launch {
            _imapAuthState.value = ImapAuthUiState(loading = true)
            try {
                container.imapClient.verifyCredentials(email, password)
                container.authManager.signOut()
                // Before the first multi-account add, move the old single-account
                // password onto the existing account's key.
                container.settingsStore.settings.first().accountEmail
                    ?.let { container.credentialStore.migrateLegacy(it) }
                container.credentialStore.saveAppPassword(email, password)
                container.settingsStore.setAuthMode(AuthMode.IMAP)
                container.settingsStore.addAccount(email)
                container.settingsStore.setOnboardingComplete(true)
                container.settingsStore.setAuthNeeded(false)
                WorkScheduler.apply(getApplication(), container.settingsStore.settings.first())
                _imapAuthState.value = ImapAuthUiState(success = true)
                syncNow(announce = true)
            } catch (error: Exception) {
                Log.e(TAG, "Gmail app-password sign-in failed", error)
                _imapAuthState.value = ImapAuthUiState(error = ImapError.message(error))
            }
        }
    }

    fun resetImapAuthState() {
        if (!_imapAuthState.value.loading) _imapAuthState.value = ImapAuthUiState()
    }

    fun removeGmailAccount(email: String) {
        viewModelScope.launch {
            container.credentialStore.removeAppPassword(email)
            container.settingsStore.removeAccount(email)
            if (container.settingsStore.settings.first().accountEmails.isEmpty()) {
                WorkScheduler.cancelSync(getApplication())
            }
            DeliveryWidget.refresh(getApplication())
            _events.emit(
                UiEvent.Message("$email disconnected. Saved deliveries are still here."),
            )
        }
    }

    fun deleteAllData() {
        container.authManager.signOut()
        container.credentialStore.clearAllAppPasswords()
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
                    lastDeliveredUndo = order
                    DeliveryWidget.refresh(getApplication())
                    _events.emit(UiEvent.UndoDelivered("Marked as delivered."))
                }
                .onFailure {
                    lastDeliveredUndo = null
                    _events.emit(UiEvent.Message("Couldn't update the delivery."))
                }
        }
    }

    fun undoMarkDelivered() {
        val previous = lastDeliveredUndo ?: return
        lastDeliveredUndo = null
        viewModelScope.launch {
            runCatching { container.repository.restore(previous) }
                .onSuccess {
                    DeliveryWidget.refresh(getApplication())
                    _events.emit(UiEvent.Message("Delivery restored."))
                }
                .onFailure {
                    _events.emit(UiEvent.Message("Couldn't undo the delivery update."))
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
