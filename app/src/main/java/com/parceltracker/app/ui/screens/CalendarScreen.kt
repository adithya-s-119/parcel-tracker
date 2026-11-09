package com.parceltracker.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.data.OrderStatus
import com.parceltracker.app.ui.Dates
import com.parceltracker.app.ui.components.EmptyState
import com.parceltracker.app.ui.components.OrderCard
import com.parceltracker.app.ui.components.SectionLabel
import com.parceltracker.app.ui.theme.statusColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    orders: List<DeliveryOrder>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onOrderClick: (DeliveryOrder) -> Unit,
    onSync: () -> Unit,
    syncing: Boolean,
    accountEmail: String?,
    authNeeded: Boolean,
    onConnect: () -> Unit,
    onAdd: () -> Unit,
    lastSyncAt: Long,
) {
    val today = remember { LocalDate.now() }
    val ordersByDay = remember(orders) {
        orders.filter { it.promisedDate != null }.groupBy { it.promisedDate!! }
    }
    val selectedOrders = remember(ordersByDay, selectedDate) {
        ordersByDay[selectedDate].orEmpty().sortedWith(
            compareBy<DeliveryOrder> { if (it.isActive) 0 else 1 }
                .thenBy { it.merchant.lowercase(Locale.getDefault()) },
        )
    }
    val upcoming = remember(orders, selectedDate, today) {
        orders.filter { order ->
            order.isActive &&
                order.promisedDate != null &&
                !order.promisedDate!!.isBefore(today) &&
                order.promisedDate != selectedDate
        }.sortedBy { it.promisedEpochDay }.take(4)
    }

    val subtitle = when {
        authNeeded -> "Gmail needs a quick reconnect."
        syncing -> "Checking your inbox…"
        accountEmail == null -> "Add deliveries by hand, or connect Gmail."
        lastSyncAt > 0L -> Dates.syncSubtitle(lastSyncAt)
        else -> "Your calendar, minus the noise."
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PARCEL",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.2.sp),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Your deliveries",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                SyncButton(syncing = syncing, onSync = onSync)
            }
        }

        if (accountEmail == null || authNeeded) {
            item {
                ConnectBanner(
                    authNeeded = authNeeded,
                    onConnect = onConnect,
                    syncing = syncing,
                )
            }
        }

        item {
            MonthCalendarCard(
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
                ordersByDay = ordersByDay,
                today = today,
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedDate == today) "TODAY" else "SELECTED DAY",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = Dates.fullDate(selectedDate),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                if (selectedOrders.isNotEmpty()) {
                    Text(
                        text = "${selectedOrders.size} ${if (selectedOrders.size == 1) "delivery" else "deliveries"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (orders.isEmpty()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    EmptyState(
                        icon = Icons.Outlined.Inventory2,
                        title = "Your calendar is quiet",
                        message = "Connect Gmail to find order dates automatically, or add a delivery by hand.",
                        actionLabel = "Add a delivery",
                        onAction = onAdd,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                    )
                }
            }
        } else if (selectedOrders.isEmpty()) {
            item {
                QuietDayCard(onAdd = onAdd)
            }
        } else {
            items(selectedOrders, key = { it.id }) { order ->
                OrderCard(order = order, onClick = { onOrderClick(order) })
            }
        }

        if (upcoming.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                SectionLabel(text = "Coming up")
            }
            items(upcoming, key = { "upcoming-${it.id}" }) { order ->
                OrderCard(order = order, onClick = { onOrderClick(order) })
            }
        }
    }
}

@Composable
private fun SyncButton(syncing: Boolean, onSync: () -> Unit) {
    Surface(
        onClick = onSync,
        enabled = !syncing,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(46.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Sync Gmail",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ConnectBanner(
    authNeeded: Boolean,
    onConnect: () -> Unit,
    syncing: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.MailOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (authNeeded) "Gmail needs reconnecting" else "Gmail isn't connected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = if (authNeeded) {
                        "Your saved deliveries are safe. Reconnect for updates."
                    } else {
                        "Connect to fill the calendar automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            TextButton(onClick = onConnect, enabled = !syncing) {
                Text(
                    text = if (authNeeded) "Reconnect" else "Connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MonthCalendarCard(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    ordersByDay: Map<LocalDate, List<DeliveryOrder>>,
    today: LocalDate,
) {
    var displayedMonthText by rememberSaveable {
        mutableStateOf(YearMonth.from(selectedDate).toString())
    }
    val displayedMonth = YearMonth.parse(displayedMonthText)

    LaunchedEffect(selectedDate) {
        val selectedMonth = YearMonth.from(selectedDate)
        if (selectedMonth != displayedMonth) {
            displayedMonthText = selectedMonth.toString()
        }
    }

    val monthCount = ordersByDay
        .filterKeys { YearMonth.from(it) == displayedMonth }
        .values
        .sumOf { it.size }

    val first = displayedMonth.atDay(1)
    val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val gridStart = first.minusDays(offset.toLong())
    val cells = remember(displayedMonth) {
        (0 until 42).map { gridStart.plusDays(it.toLong()) }
    }
    val weekdayLabels = remember {
        DayOfWeek.entries.map {
            it.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        }
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Dates.monthTitle(displayedMonth),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (monthCount == 0) {
                            "No deliveries this month"
                        } else {
                            "$monthCount ${if (monthCount == 1) "delivery" else "deliveries"} this month"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (displayedMonth != YearMonth.now() || selectedDate != today) {
                    TextButton(
                        onClick = {
                            displayedMonthText = YearMonth.now().toString()
                            onDateSelected(today)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                MonthArrow(
                    icon = Icons.Outlined.ChevronLeft,
                    contentDescription = "Previous month",
                    onClick = {
                        displayedMonthText = displayedMonth.minusMonths(1).toString()
                    },
                )
                Spacer(modifier = Modifier.width(6.dp))
                MonthArrow(
                    icon = Icons.Outlined.ChevronRight,
                    contentDescription = "Next month",
                    onClick = {
                        displayedMonthText = displayedMonth.plusMonths(1).toString()
                    },
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.4.sp,
                            fontSize = 10.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            repeat(6) { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(7) { column ->
                        val date = cells[week * 7 + column]
                        val dayOrders = ordersByDay[date].orEmpty()
                        CalendarDay(
                            date = date,
                            inMonth = YearMonth.from(date) == displayedMonth,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            orders = dayOrders,
                            onClick = {
                                if (YearMonth.from(date) != displayedMonth) {
                                    displayedMonthText = YearMonth.from(date).toString()
                                }
                                onDateSelected(date)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    orders: List<DeliveryOrder>,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .height(45.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp,
                ),
                color = textColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                orders.take(3).forEach { order ->
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else statusColor(order.status).copy(alpha = if (inMonth) 1f else 0.4f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuietDayCard(onAdd: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Nothing arriving",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "A quiet day. Add a delivery if plans change.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAdd) {
                Text("Add", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
