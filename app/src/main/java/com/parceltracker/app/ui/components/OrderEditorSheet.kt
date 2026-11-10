package com.parceltracker.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.data.OrderDraft
import com.parceltracker.app.data.OrderStatus
import com.parceltracker.app.ui.Dates
import com.parceltracker.app.ui.theme.statusColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderEditorSheet(
    order: DeliveryOrder?,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (OrderDraft) -> Unit,
    initialDraft: OrderDraft? = null,
    onDelete: (() -> Unit)? = null,
    onMarkDelivered: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var merchant by remember(order, initialDraft) {
        mutableStateOf(order?.merchant ?: initialDraft?.merchant.orEmpty())
    }
    var itemName by remember(order, initialDraft) {
        mutableStateOf(order?.itemName ?: initialDraft?.itemName.orEmpty())
    }
    var orderNumber by remember(order, initialDraft) {
        mutableStateOf(order?.orderNumber ?: initialDraft?.orderNumber.orEmpty())
    }
    var notes by remember(order, initialDraft) {
        mutableStateOf(order?.notes ?: initialDraft?.notes.orEmpty())
    }
    var status by remember(order, initialDraft) {
        mutableStateOf(order?.status ?: initialDraft?.status ?: OrderStatus.UPCOMING)
    }
    var date by remember(order, initialDraft) {
        mutableStateOf(order?.promisedDate ?: initialDraft?.promisedDate ?: initialDate)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            order != null -> "Edit delivery"
                            initialDraft != null -> "Check this delivery"
                            else -> "Add a delivery"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (initialDraft != null) {
                            "We read the shared email. Check the details before adding it."
                        } else {
                            "Keep the promised date honest."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Store") },
                placeholder = { Text("Amazon, Flipkart, a local shop…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(modifier = Modifier.height(11.dp))

            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = { Text("What's arriving") },
                placeholder = { Text("Running shoes, coffee beans…") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(modifier = Modifier.height(11.dp))

            Surface(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PROMISED DELIVERY",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Dates.fullDate(date),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(11.dp))

            ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = !statusExpanded },
            ) {
                OutlinedTextField(
                    value = Dates.statusLabel(status),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Status") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                )
                ExposedDropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false },
                ) {
                    OrderStatus.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(Dates.statusLabel(option)) },
                            onClick = {
                                status = option
                                statusExpanded = false
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(statusColor(option), CircleShape),
                                )
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(11.dp))

            OutlinedTextField(
                value = orderNumber,
                onValueChange = { orderNumber = it },
                label = { Text("Order number (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(modifier = Modifier.height(11.dp))

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
            )

            val emailSnippet = order?.snippet
            if (!emailSnippet.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Column {
                            Text(
                                text = "From the email",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = emailSnippet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (validationError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    if (merchant.isBlank()) {
                        validationError = "Add the store name."
                        return@Button
                    }
                    if (itemName.isBlank()) {
                        validationError = "Add what's arriving."
                        return@Button
                    }
                    onSave(
                        OrderDraft(
                            merchant = merchant,
                            itemName = itemName,
                            promisedDate = date,
                            status = status,
                            orderNumber = orderNumber.takeIf { it.isNotBlank() },
                            notes = notes.takeIf { it.isNotBlank() },
                        ),
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                Text(
                    text = if (order == null) "Add to calendar" else "Save changes",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (order != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (order.isActive && onMarkDelivered != null) {
                        OutlinedButton(
                            onClick = {
                                onMarkDelivered()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delivered")
                        }
                    }
                    if (onDelete != null) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) { Text("Set date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
            )
        }
    }

    if (showDeleteDialog && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this delivery?") },
            text = { Text("It will be removed from your calendar and widget.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                        onDismiss()
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
