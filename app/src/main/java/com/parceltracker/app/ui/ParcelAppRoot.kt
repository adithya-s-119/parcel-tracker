package com.parceltracker.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.ui.components.GmailPasswordSheet
import com.parceltracker.app.ui.components.OrderEditorSheet
import com.parceltracker.app.ui.screens.CalendarScreen
import com.parceltracker.app.ui.screens.OnboardingScreen
import com.parceltracker.app.ui.screens.OrdersScreen
import com.parceltracker.app.ui.screens.SettingsScreen
import java.time.LocalDate

private data class EditorTarget(
    val order: DeliveryOrder?,
    val initialDate: LocalDate,
    val initialDraft: com.parceltracker.app.data.OrderDraft? = null,
)

@Composable
fun ParcelAppRoot(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val sharedImport by viewModel.sharedImport.collectAsStateWithLifecycle()
    val imapAuthState by viewModel.imapAuthState.collectAsStateWithLifecycle()

    var showImapSheet by rememberSaveable { mutableStateOf(false) }
    var imapInitialEmail by rememberSaveable { mutableStateOf("") }
    val openImapSheet: (String) -> Unit = { initialEmail ->
        viewModel.resetImapAuthState()
        imapInitialEmail = initialEmail
        showImapSheet = true
    }
    val addAccount: () -> Unit = { openImapSheet("") }
    val reconnectAccount: () -> Unit = { openImapSheet(settings.accountEmail.orEmpty()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setNotificationsEnabled(granted)
    }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.onboardingComplete, settings.notificationsEnabled) {
        if (settings.onboardingComplete &&
            settings.notificationsEnabled &&
            !askedForNotifications &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.onAppStart()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!settings.onboardingComplete) {
        OnboardingScreen(
            syncing = syncState is SyncUiState.Connecting || syncState is SyncUiState.Syncing,
            syncMessage = when (val state = syncState) {
                SyncUiState.Connecting -> "Connecting…"
                is SyncUiState.Syncing -> state.message
                else -> null
            },
            authError = authError,
            onUseAppPassword = addAccount,
            onSkip = viewModel::continueWithoutGmail,
        )
    } else {
        MainScaffold(
            viewModel = viewModel,
            settings = settings,
            orders = orders,
            syncState = syncState,
            snackbarHostState = snackbarHostState,
            onUseAppPassword = reconnectAccount,
            onAddAccount = addAccount,
            onRemoveAccount = viewModel::removeGmailAccount,
            onSmsToggle = viewModel::setSmsEnabled,
            sharedImport = sharedImport,
            onConsumeSharedImport = viewModel::consumeSharedImport,
        )
    }

    if (showImapSheet) {
        GmailPasswordSheet(
            initialEmail = imapInitialEmail,
            state = imapAuthState,
            onSubmit = viewModel::signInWithAppPassword,
            onDismiss = {
                showImapSheet = false
                viewModel.resetImapAuthState()
            },
        )
    }
}

@Composable
private fun MainScaffold(
    viewModel: MainViewModel,
    settings: com.parceltracker.app.data.AppSettings,
    orders: List<DeliveryOrder>,
    syncState: SyncUiState,
    snackbarHostState: SnackbarHostState,
    onUseAppPassword: () -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onSmsToggle: (Boolean) -> Unit,
    sharedImport: SharedEmailImport?,
    onConsumeSharedImport: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }

    LaunchedEffect(sharedImport) {
        val incoming = sharedImport ?: return@LaunchedEffect
        tab = 0
        editorTarget = EditorTarget(
            order = null,
            initialDate = incoming.draft.promisedDate ?: LocalDate.now(),
            initialDraft = incoming.draft,
        )
        onConsumeSharedImport()
    }

    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    val syncing = syncState is SyncUiState.Syncing || syncState is SyncUiState.Connecting
    val syncMessage = when (val state = syncState) {
        SyncUiState.Connecting -> "Connecting…"
        is SyncUiState.Syncing -> state.message
        else -> null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            ParcelBottomBar(
                selectedTab = tab,
                onSelect = { tab = it },
            )
        },
        floatingActionButton = {
            if (tab != 2) {
                FloatingActionButton(
                    onClick = {
                        editorTarget = EditorTarget(
                            order = null,
                            initialDate = if (tab == 0) selectedDate else LocalDate.now(),
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add a delivery",
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tab) {
                0 -> CalendarScreen(
                    orders = orders,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedEpochDay = it.toEpochDay() },
                    onOrderClick = { order ->
                        editorTarget = EditorTarget(
                            order = order,
                            initialDate = order.promisedDate ?: selectedDate,
                        )
                    },
                    onSync = { viewModel.syncNow(announce = true) },
                    syncing = syncing,
                    accountEmail = if (settings.accountEmails.size > 1) {
                        "${settings.accountEmails.size} Gmail accounts"
                    } else {
                        settings.accountEmail
                    },
                    authNeeded = settings.authNeeded,
                    onConnect = onUseAppPassword,
                    onAdd = { editorTarget = EditorTarget(null, selectedDate) },
                    lastSyncAt = settings.lastSyncAt,
                )

                1 -> OrdersScreen(
                    orders = orders,
                    onOrderClick = { order ->
                        editorTarget = EditorTarget(
                            order = order,
                            initialDate = order.promisedDate ?: LocalDate.now(),
                        )
                    },
                    onAdd = { editorTarget = EditorTarget(null, LocalDate.now()) },
                )

                else -> SettingsScreen(
                    settings = settings,
                    syncing = syncing,
                    syncMessage = syncMessage,
                    onSync = { viewModel.syncNow(announce = true) },
                    onUseAppPassword = onUseAppPassword,
                    onAddAccount = onAddAccount,
                    onRemoveAccount = onRemoveAccount,
                    onSmsToggle = onSmsToggle,
                    onAutoSync = viewModel::setAutoSync,
                    onNotifications = viewModel::setNotificationsEnabled,
                    onScanDays = viewModel::setScanDays,
                    onAccent = viewModel::setAccent,
                    onRebuild = { viewModel.syncNow(fullRescan = true, announce = true) },
                    onDeleteAll = viewModel::deleteAllData,
                    onAdd = { editorTarget = EditorTarget(null, LocalDate.now()) },
                )
            }
        }
    }

    editorTarget?.let { target ->
        OrderEditorSheet(
            order = target.order,
            initialDate = target.initialDate,
            initialDraft = target.initialDraft,
            onDismiss = { editorTarget = null },
            onSave = { draft -> viewModel.saveOrder(target.order, draft) },
            onDelete = target.order?.let { order -> { viewModel.deleteOrder(order) } },
            onMarkDelivered = target.order?.let { order -> { viewModel.markDelivered(order) } },
        )
    }
}

@Composable
private fun ParcelBottomBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { onSelect(0) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                    )
                },
                label = { Text("Calendar") },
                colors = bottomBarColors(),
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { onSelect(1) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                    )
                },
                label = { Text("Orders") },
                colors = bottomBarColors(),
            )
            NavigationBarItem(
                selected = selectedTab == 2,
                onClick = { onSelect(2) },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                    )
                },
                label = { Text("Settings") },
                colors = bottomBarColors(),
            )
        }
    }
}

@Composable
private fun bottomBarColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
