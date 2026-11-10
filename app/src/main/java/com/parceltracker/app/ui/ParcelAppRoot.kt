package com.parceltracker.app.ui

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.google.android.gms.auth.api.identity.Identity
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.ui.components.OrderEditorSheet
import com.parceltracker.app.ui.screens.CalendarScreen
import com.parceltracker.app.ui.screens.OnboardingScreen
import com.parceltracker.app.ui.screens.OrdersScreen
import com.parceltracker.app.ui.screens.SettingsScreen
import java.time.LocalDate

private data class EditorTarget(
    val order: DeliveryOrder?,
    val initialDate: LocalDate,
)

@Composable
fun ParcelAppRoot(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val parsed = result.data?.let { data ->
            runCatching {
                Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            }
        }
        when {
            parsed?.isSuccess == true -> viewModel.handleAuthorizationResult(parsed.getOrNull())
            parsed?.isFailure == true -> viewModel.handleAuthorizationFailure(parsed.exceptionOrNull())
            result.resultCode == Activity.RESULT_OK -> viewModel.handleAuthorizationFailure(null)
            else -> viewModel.handleAuthorizationResult(null)
        }
    }

    val connect: () -> Unit = {
        viewModel.connect { intentSender: IntentSender ->
            authLauncher.launch(
                IntentSenderRequest.Builder(intentSender).build(),
            )
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
            onConnect = connect,
            onSkip = viewModel::continueWithoutGmail,
        )
    } else {
        MainScaffold(
            viewModel = viewModel,
            settings = settings,
            orders = orders,
            syncState = syncState,
            snackbarHostState = snackbarHostState,
            connect = connect,
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
    connect: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }

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
                    accountEmail = settings.accountEmail,
                    authNeeded = settings.authNeeded,
                    onConnect = connect,
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
                    onConnect = connect,
                    onDisconnect = viewModel::signOutGmail,
                    onAutoSync = viewModel::setAutoSync,
                    onNotifications = viewModel::setNotificationsEnabled,
                    onScanDays = viewModel::setScanDays,
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
