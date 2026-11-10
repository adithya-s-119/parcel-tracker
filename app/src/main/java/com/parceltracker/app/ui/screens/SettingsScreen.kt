package com.parceltracker.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsNone
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parceltracker.app.data.AppSettings
import com.parceltracker.app.ui.Dates
import com.parceltracker.app.ui.components.GoogleGMark
import com.parceltracker.app.ui.components.SignInHelpDialog
import com.parceltracker.app.ui.theme.Forest
import com.parceltracker.app.ui.theme.PaperBright

@Composable
fun SettingsScreen(
    settings: AppSettings,
    syncing: Boolean,
    syncMessage: String?,
    onSync: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onScanDays: (Int) -> Unit,
    onRebuild: () -> Unit,
    onDeleteAll: () -> Unit,
    onAdd: () -> Unit,
) {
    var showRebuildDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSignInHelp by remember { mutableStateOf(false) }

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
                    text = "Tune how Parcel watches your inbox.",
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
                onConnect = onConnect,
                onDisconnect = onDisconnect,
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
                        subtitle = "Check Gmail every 6 hours",
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
                    icon = Icons.Outlined.Info,
                    title = "Gmail sign-in help",
                    subtitle = "Package, SHA-1 and Play services status",
                    onClick = { showSignInHelp = true },
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
                            text = "Parcel uses read-only Gmail access. Emails are parsed on this device. " +
                                "No email content or account data is uploaded to a server.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scope: gmail.readonly",
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
                    "This clears Gmail-sourced deliveries and scans your inbox again. " +
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

    if (showSignInHelp) {
        SignInHelpDialog(onDismiss = { showSignInHelp = false })
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
private fun AccountCard(
    settings: AppSettings,
    syncing: Boolean,
    syncMessage: String?,
    onSync: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val email = settings.accountEmail
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            if (email != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = email.firstOrNull()?.uppercase() ?: "G",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        if (settings.authNeeded) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        CircleShape,
                                    ),
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (settings.authNeeded) {
                                    "Reconnect needed"
                                } else {
                                    "Connected · Gmail read-only"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings.authNeeded) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                if (settings.authNeeded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your saved deliveries are still here. Reconnect to check for updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
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
                    if (settings.authNeeded) {
                        Button(
                            onClick = onConnect,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Reconnect")
                        }
                    } else {
                        TextButton(onClick = onDisconnect) {
                            Text(
                                text = "Disconnect",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
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
                    onClick = onConnect,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Forest,
                        contentColor = PaperBright,
                    ),
                ) {
                    GoogleGMark()
                    Spacer(modifier = Modifier.width(9.dp))
                    Text("Connect Gmail", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
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
