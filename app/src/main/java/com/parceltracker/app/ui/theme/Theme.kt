package com.parceltracker.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.parceltracker.app.data.OrderStatus
import com.parceltracker.app.data.ParcelAccent

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = PaperBright,
    primaryContainer = Color(0xFFDCE8DD),
    onPrimaryContainer = ForestDeep,
    secondary = Terracotta,
    onSecondary = PaperBright,
    secondaryContainer = Color(0xFFF4DDD1),
    onSecondaryContainer = Color(0xFF3B1608),
    tertiary = Color(0xFF6F7D5A),
    onTertiary = PaperBright,
    tertiaryContainer = Color(0xFFE4EAD8),
    onTertiaryContainer = Color(0xFF202912),
    background = Paper,
    onBackground = Ink,
    surface = PaperBright,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE8DA),
    onSurfaceVariant = Color(0xFF625C50),
    outline = Color(0xFFC9C1B1),
    outlineVariant = Color(0xFFE3DCCD),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceContainerLowest = PaperBright,
    surfaceContainerLow = Color(0xFFFBF7EF),
    surfaceContainer = Color(0xFFF3EEE4),
    surfaceContainerHigh = Color(0xFFEDE7DB),
    surfaceContainerHighest = Color(0xFFE7E0D2),
)

private val DarkColors = darkColorScheme(
    primary = NightForest,
    onPrimary = Color(0xFF10281C),
    primaryContainer = Color(0xFF2C4636),
    onPrimaryContainer = Color(0xFFD6E8DA),
    secondary = NightTerracotta,
    onSecondary = Color(0xFF3B1608),
    secondaryContainer = Color(0xFF5A3423),
    onSecondaryContainer = Color(0xFFF8DCCB),
    tertiary = Color(0xFFB8C7A0),
    onTertiary = Color(0xFF253016),
    tertiaryContainer = Color(0xFF3A452A),
    onTertiaryContainer = Color(0xFFDDE8C9),
    background = NightBackground,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightMuted,
    outline = Color(0xFF5B6157),
    outlineVariant = Color(0xFF343830),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surfaceContainerLowest = Color(0xFF0F110E),
    surfaceContainerLow = Color(0xFF191C17),
    surfaceContainer = Color(0xFF1F231D),
    surfaceContainerHigh = Color(0xFF292D26),
    surfaceContainerHighest = Color(0xFF33382F),
)

val ParcelShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp),
)

private fun ColorScheme.withAccent(accent: ParcelAccent, dark: Boolean): ColorScheme {
    val themed = copy(
        primary = Color(if (dark) accent.darkPrimary else accent.lightPrimary),
        onPrimary = Color(if (dark) accent.darkOnPrimary else accent.lightOnPrimary),
        primaryContainer = Color(if (dark) accent.darkContainer else accent.lightContainer),
        onPrimaryContainer = Color(if (dark) accent.darkOnContainer else accent.lightOnContainer),
    )
    if (accent == ParcelAccent.FOREST) return themed

    // Tint the paper/night neutrals toward the chosen theme so the whole app
    // picks up the accent instead of keeping the default green cast.
    val tint = Color(if (dark) accent.darkContainer else accent.lightContainer)
    fun blend(color: Color, amount: Float): Color = lerp(color, tint, amount)

    return themed.copy(
        background = blend(themed.background, if (dark) 0.20f else 0.32f),
        surface = blend(themed.surface, if (dark) 0.24f else 0.20f),
        surfaceVariant = blend(themed.surfaceVariant, if (dark) 0.32f else 0.36f),
        outline = blend(themed.outline, if (dark) 0.22f else 0.20f),
        outlineVariant = blend(themed.outlineVariant, if (dark) 0.28f else 0.30f),
        surfaceContainerLowest = blend(themed.surfaceContainerLowest, if (dark) 0.18f else 0.16f),
        surfaceContainerLow = blend(themed.surfaceContainerLow, if (dark) 0.22f else 0.20f),
        surfaceContainer = blend(themed.surfaceContainer, if (dark) 0.26f else 0.24f),
        surfaceContainerHigh = blend(themed.surfaceContainerHigh, if (dark) 0.30f else 0.28f),
        surfaceContainerHighest = blend(themed.surfaceContainerHighest, if (dark) 0.34f else 0.32f),
    )
}

@Composable
fun ParcelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: ParcelAccent = ParcelAccent.FOREST,
    content: @Composable () -> Unit,
) {
    val colors = (if (darkTheme) DarkColors else LightColors).withAccent(accent, darkTheme)
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ParcelTypography,
        shapes = ParcelShapes,
        content = content,
    )
}

@Composable
fun statusColor(status: OrderStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (status) {
        OrderStatus.UPCOMING -> if (dark) UpcomingDark else UpcomingLight
        OrderStatus.SHIPPED -> if (dark) ShippedDark else ShippedLight
        OrderStatus.DELAYED -> if (dark) DelayedDark else DelayedLight
        OrderStatus.READY_FOR_PICKUP -> if (dark) PickupDark else PickupLight
        OrderStatus.OUT_FOR_DELIVERY -> if (dark) OutForDeliveryDark else OutForDeliveryLight
        OrderStatus.DELIVERY_FAILED -> if (dark) DeliveryFailedDark else DeliveryFailedLight
        OrderStatus.DELIVERED -> if (dark) DeliveredDark else DeliveredLight
        OrderStatus.CANCELLED -> if (dark) CancelledDark else CancelledLight
        OrderStatus.RETURNED -> if (dark) ReturnedDark else ReturnedLight
    }
}

@Composable
fun statusContainer(status: OrderStatus): Color {
    val dark = isSystemInDarkTheme()
    return statusColor(status).copy(alpha = if (dark) 0.18f else 0.13f)
}
