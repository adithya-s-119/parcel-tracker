package com.parceltracker.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.data.OrderStatus
import com.parceltracker.app.ui.components.EmptyState
import com.parceltracker.app.ui.components.OrderCard
import com.parceltracker.app.ui.components.SectionLabel
import java.time.LocalDate
import java.util.Locale

/**
 * A dated order stays in Upcoming through its promised day. Undated orders
 * only stay for two weeks: after that they are almost always stale
 * confirmations for something that already arrived.
 */
private fun DeliveryOrder.isUpcoming(todayEpochDay: Long): Boolean {
    if (!isActive) return false
    val date = promisedEpochDay
    if (date != null) return date >= todayEpochDay
    val twoWeeksAgo = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
    return emailEpochMillis >= twoWeeksAgo
}

private enum class OrderFilter(val label: String) {
    UPCOMING("Upcoming"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
    ALL("All"),
}

@Composable
fun OrdersScreen(
    orders: List<DeliveryOrder>,
    onOrderClick: (DeliveryOrder) -> Unit,
    onAdd: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(OrderFilter.UPCOMING) }
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }

    val filtered = remember(orders, query, filter, todayEpochDay) {
        val needle = query.trim().lowercase(Locale.getDefault())
        orders
            .filter { order ->
                needle.isBlank() ||
                    order.merchant.lowercase(Locale.getDefault()).contains(needle) ||
                    order.itemName.lowercase(Locale.getDefault()).contains(needle) ||
                    order.orderNumber?.lowercase(Locale.getDefault())?.contains(needle) == true
            }
            .filter { order ->
                when (filter) {
                    OrderFilter.UPCOMING -> order.isUpcoming(todayEpochDay)
                    OrderFilter.DELIVERED -> order.status == OrderStatus.DELIVERED
                    OrderFilter.CANCELLED -> order.status == OrderStatus.CANCELLED
                    OrderFilter.ALL -> true
                }
            }
    }

    val active = remember(filtered) {
        filtered.filter { it.isActive }
            .sortedWith(
                compareBy<DeliveryOrder> { it.promisedEpochDay ?: Long.MAX_VALUE }
                    .thenBy { it.merchant.lowercase(Locale.getDefault()) },
            )
    }
    val history = remember(filtered) {
        filtered.filter { !it.isActive }
            .sortedWith(
                compareByDescending<DeliveryOrder> { it.promisedEpochDay ?: Long.MIN_VALUE }
                    .thenBy { it.merchant.lowercase(Locale.getDefault()) },
            )
    }

    val activeCount = remember(orders, todayEpochDay) {
        orders.count { it.isUpcoming(todayEpochDay) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                    text = "Orders",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        orders.isEmpty() -> "Nothing tracked yet."
                        activeCount == 1 -> "1 on the way · ${orders.size} tracked"
                        else -> "$activeCount on the way · ${orders.size} tracked"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search merchant, item or order #",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(OrderFilter.entries) { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = when {
                        query.isNotBlank() -> "No matches"
                        filter == OrderFilter.UPCOMING -> "Nothing on the way"
                        filter == OrderFilter.DELIVERED -> "No delivered orders yet"
                        filter == OrderFilter.CANCELLED -> "No cancelled orders"
                        else -> "No deliveries tracked"
                    },
                    message = when {
                        query.isNotBlank() -> "Try a different merchant, item or order number."
                        filter == OrderFilter.UPCOMING ->
                            "Add a delivery by hand, or connect Gmail to find order dates automatically."
                        else -> "Deliveries will appear here once there's something to show."
                    },
                    actionLabel = if (query.isNotBlank()) null else "Add a delivery",
                    onAction = if (query.isNotBlank()) null else onAdd,
                    modifier = Modifier.padding(vertical = 38.dp),
                )
            }
        }

        if (active.isNotEmpty() && filter != OrderFilter.DELIVERED && filter != OrderFilter.CANCELLED) {
            item {
                SectionLabel(
                    text = if (filter == OrderFilter.ALL) "On the way" else "Upcoming",
                    modifier = Modifier.padding(top = 6.dp),
                    trailing = {
                        Text(
                            text = active.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            items(active, key = { it.id }) { order ->
                OrderCard(order = order, onClick = { onOrderClick(order) })
            }
        }

        if (history.isNotEmpty() && filter != OrderFilter.UPCOMING) {
            item {
                SectionLabel(
                    text = if (filter == OrderFilter.ALL) "History" else filter.label,
                    modifier = Modifier.padding(top = 6.dp),
                    trailing = {
                        Text(
                            text = history.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            items(history, key = { it.id }) { order ->
                OrderCard(order = order, onClick = { onOrderClick(order) })
            }
        }
    }
}
