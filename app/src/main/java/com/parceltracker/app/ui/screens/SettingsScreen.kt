package com.parceltracker.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.parceltracker.app.data.AppSettings
import com.parceltracker.app.data.AuthMode
import com.parceltracker.app.data.ParcelAccent
import com.parceltracker.app.data.SmsPermission
import com.parceltracker.app.ui.Dates

@Composable
fun SettingsScreen(
    settings: AppSettings,
    syncing: Boolean,
    syncMessage: String?,
    onSync: () -> Unit,
    onUseAppPassword: () -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onSmsToggle: (Boolean) -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onScanDays: (Int) -> Unit,
    onAccent: (ParcelAccent) -> Unit,
    onRebuild: () -> Unit,
    onDeleteAll: () -> Unit,
    onAdd: () -> Unit,
) {
    var showRebuildDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShareHelp by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.READ_SMS] == true &&
            result[Manifest.permission.RECEIVE_SMS] == true
        onSmsToggle(granted)
    }
    val toggleSms: (Boolean) -> Unit = { enabled ->
        when {
            !enabled -> onSmsToggle(false)
            SmsPermission.hasAll(context) -> onSmsToggle(true)
            else -> smsPermissionLauncher.launch(SmsPermission.permissions)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    text = "PARCEL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.2.sp),
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tune how Parcel watches your inboxes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            AccountCard(
                settings = settings,
                syncing = syncing,
                syncMessage = syncMessage,
                onSync = onSync,
                onUseAppPassword = onUseAppPassword,
                onAddAccount = onAddAccount,
                onRemoveAccount = onRemoveAccount,
            )
        }

        item {
            SectionTitle("Syncing")
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    SwitchRow(
                        icon = Icons.Outlined.Sync,
                        title = "Automatic sync",
                        subtitle = "Check all inboxes every 6 hours",
                        checked = settings.autoSync,
                        onCheckedChange = onAutoSync,
                    )
                    SettingsDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBox(icon = Icons.Outlined.MailOutline)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan window",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "How far back to look for orders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(90 to "3 months", 180 to "6 months", 365 to "1 year").forEach { (days, label) ->
                                FilterChip(
                                    selected = settings.scanDays == days,
                                    onClick = { onScanDays(days) },
                                    label = {
                                        Text(label, style = MaterialTheme.typography.labelLarge)
                                    },
                                    shape = CircleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    ActionRow(
                        icon = Icons.Outlined.Refresh,
                        title = "Rebuild from Gmail",
                        subtitle = "Clear matches and scan again",
                        onClick = { showRebuildDialog = true },
                    )
                    if (settings.lastSyncSummary != null || settings.lastSyncAt > 0L) {
                        Text(
                            text = buildString {
                                settings.lastSyncSummary?.let { append(it) }
                                if (settings.lastSyncAt > 0L) {
                                    if (isNotEmpty()) append(" · ")
                                    append(Dates.syncSubtitle(settings.lastSyncAt))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionTitle("Delivery texts")
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    SwitchRow(
                        icon = Icons.Outlined.Message,
                        title = "Read delivery texts",
                        subtitle = "Find dates in SMS from stores and couriers",
                        checked = settings.smsEnabled,
                        onCheckedChange = toggleSms,
                    )
                    if (settings.smsEnabled) {
                        SettingsDivider()
                        ActionRow(
                            icon = Icons.Outlined.Refresh,
                            title = "Scan texts now",
                            subtitle = "Check the SMS inbox for new deliveries",
                            onClick = onSync,
                        )
                    }
                    Text(
                        text = "SMS is read on this device only. Google Play restricts SMS access; this build is for personal use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )
                }
            }
        }

        item {
            SectionTitle("Appearance")
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        IconBox(icon = Icons.Outlined.Palette)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Theme",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Pick the accent used across Parcel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ParcelAccent.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { accent ->
                                    ThemeSwatch(
                                        accent = accent,
                                        selected = settings.accent == accent,
                                        onClick = { onAccent(accent) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Reminders")
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                SwitchRow(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "Delivery reminders",
                    subtitle = "A 9 AM nudge for today and tomorrow",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onNotifications,
                )
            }
        }

        item {
            SectionTitle("Deliveries")
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    ActionRow(
                        icon = Icons.Outlined.Add,
                        title = "Add a delivery manually",
                        subtitle = "For orders Gmail didn't catch",
                        onClick = onAdd,
                    )
                    SettingsDivider()
                    ActionRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Delete all data",
                        subtitle = "Remove deliveries, emails matches and account link",
                        onClick = { showDeleteDialog = true },
                        destructive = true,
                    )
                }
            }
        }

        item {
            SectionTitle("Help")
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                ActionRow(
                    icon = Icons.Outlined.MailOutline,
                    title = "Add from Gmail",
                    subtitle = "Open an email → Share → Parcel",
                    onClick = { showShareHelp = true },
                )
            }
        }

        item {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    IconBox(icon = Icons.Outlined.Lock)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Private by design",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Parcel uses read-only Gmail access when connected, and can read delivery " +
                                "texts on this device when enabled. Nothing is uploaded to a server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Read-only IMAP · SMS stays on-device",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Parcel 1.0 · made for calm arrivals",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }

    if (showRebuildDialog) {
        AlertDialog(
            onDismissRequest = { showRebuildDialog = false },
            title = { Text("Rebuild from Gmail?") },
            text = {
                Text(
                    "This clears imported deliveries from Gmail and texts, then scans both again. " +
                        "Manual deliveries and edits are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebuildDialog = false
                        onRebuild()
                    },
                ) { Text("Rebuild") }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showShareHelp) {
        AlertDialog(
            onDismissRequest = { showShareHelp = false },
            title = { Text("Add from Gmail") },
            text = {
                Column {
                    Text("1. Open Gmail and choose an order or shipping email.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("2. Tap the three-dot menu, then Share.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("3. Choose Parcel.")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("4. Check the store, item and delivery date, then tap Add to calendar.")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "No Google Cloud, password or account connection is needed for this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareHelp = false }) { Text("Got it") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete all data?") },
            text = {
                Text(
                    "This removes every delivery, the Gmail connection and all app settings. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAll()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ThemeSwatch(
    accent: ParcelAccent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(accent.swatch))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = accent.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccountCard(
    settings: AppSettings,
    syncing: Boolean,
    syncMessage: String?,
    onSync: () -> Unit,
    onUseAppPassword: () -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
) {
    val accounts = settings.accountEmails
    var removeTarget by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (accounts.isEmpty()) {
                Text(
                    text = "Connect your Gmail",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Parcel reads order and shipping emails to fill your calendar. " +
                        "It never sends, edits or deletes anything.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onAddAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Connect Gmail with app password",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = "Free, read-only access with a Gmail app password. You can add more " +
                        "accounts later and enable delivery texts below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Text(
                    text = if (accounts.size == 1) "Connected account" else "Connected accounts",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Parcel scans ${if (accounts.size == 1) "this inbox" else "all ${accounts.size} inboxes"} " +
                        "and merges duplicate deliveries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(10.dp))
                accounts.forEach { email ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = email.firstOrNull()?.uppercase() ?: "G",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "App password · read-only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { removeTarget = email }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Remove $email",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                if (settings.authNeeded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "An account needs reconnecting. Saved deliveries are still here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onSync,
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = syncMessage ?: "Syncing…",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync now")
                        }
                    }
                    OutlinedButton(
                        onClick = onAddAccount,
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text("Add account")
                    }
                }

                if (settings.authNeeded) {
                    TextButton(
                        onClick = onUseAppPassword,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reconnect an account")
                    }
                }
            }
        }
    }

    removeTarget?.let { email ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove account?") },
            text = {
                Text(
                    "Stop scanning $email? Saved deliveries will stay on this phone, " +
                        "but new updates from this account will not be checked.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveAccount(email)
                        removeTarget = null
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun IconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon = icon)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (destructive) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(11.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (destructive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 62.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
    )
}
