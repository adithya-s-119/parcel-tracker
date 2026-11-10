package com.parceltracker.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.parceltracker.app.MainActivity
import com.parceltracker.app.ParcelApp
import com.parceltracker.app.data.DeliveryOrder
import com.parceltracker.app.data.ParcelAccent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class DeliveryWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 120.dp),
            DpSize(250.dp, 180.dp),
            DpSize(300.dp, 240.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? ParcelApp
        val orders = runCatching {
            app?.container?.database?.orderDao()
                ?.nextDeliveries(LocalDate.now().toEpochDay(), 6)
                .orEmpty()
        }.getOrDefault(emptyList())
        val theme = runCatching {
            app?.container?.settingsStore?.settings?.first()?.accent
        }.getOrNull() ?: ParcelAccent.FOREST

        provideContent {
            val dark = (LocalContext.current.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            WidgetContent(orders = orders, dark = dark, theme = theme)
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            DeliveryWidget().updateAll(context)
        }
    }
}

@Composable
private fun WidgetContent(orders: List<DeliveryOrder>, dark: Boolean, theme: ParcelAccent) {
    val accent = Color(if (dark) theme.darkPrimary else theme.lightPrimary)
    val tint = Color(if (dark) theme.darkContainer else theme.lightContainer)
    val background = lerp(
        if (dark) Color(0xFF1D201B) else Color(0xFFFFFCF5),
        tint,
        if (dark) 0.24f else 0.30f,
    )
    val ink = if (dark) Color(0xFFF2EFE6) else Color(0xFF211F1A)
    val muted = if (dark) Color(0xFFC6C0B3) else Color(0xFF6F695D)
    val line = lerp(background, ink, if (dark) 0.18f else 0.14f)
    val dateChip = lerp(background, accent, if (dark) 0.20f else 0.14f)

    val today = LocalDate.now()
    val maxRows = when {
        LocalSize.current.height < 150.dp -> 2
        LocalSize.current.height < 200.dp -> 3
        else -> 3
    }
    val visible = orders.take(maxRows)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .cornerRadius(26.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NEXT DELIVERIES",
                    style = TextStyle(
                        color = ColorProvider(accent),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "PARCEL",
                    style = TextStyle(
                        color = ColorProvider(muted),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.height(7.dp))
            Spacer(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(line),
            )
            Spacer(modifier = GlanceModifier.height(5.dp))

            if (visible.isEmpty()) {
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "Nothing on the way",
                    style = TextStyle(
                        color = ColorProvider(ink),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                Text(
                    text = "Your next deliveries will appear here.",
                    style = TextStyle(color = ColorProvider(muted), fontSize = 13.sp),
                    maxLines = 2,
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
            } else {
                Spacer(modifier = GlanceModifier.defaultWeight())
                visible.forEachIndexed { index, order ->
                    WidgetRow(
                        order = order,
                        today = today,
                        ink = ink,
                        muted = muted,
                        accent = accent,
                        dateChip = dateChip,
                    )
                    if (index < visible.lastIndex) {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun WidgetRow(
    order: DeliveryOrder,
    today: LocalDate,
    ink: Color,
    muted: Color,
    accent: Color,
    dateChip: Color,
) {
    val date = order.promisedDate
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(50.dp)
                .height(40.dp)
                .background(dateChip)
                .cornerRadius(11.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (date != null) {
                Column {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = TextStyle(
                            color = ColorProvider(ink),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = date.month.name.take(3),
                        style = TextStyle(color = ColorProvider(muted), fontSize = 11.sp),
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.width(10.dp))

        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = order.merchant.uppercase(Locale.getDefault()),
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            Text(
                text = order.itemName,
                style = TextStyle(
                    color = ColorProvider(ink),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }

        Spacer(modifier = GlanceModifier.width(6.dp))

        Text(
            text = relativeLabel(date, today),
            style = TextStyle(color = ColorProvider(muted), fontSize = 13.sp),
            maxLines = 1,
        )
    }
}

private fun relativeLabel(date: LocalDate?, today: LocalDate): String {
    if (date == null) return ""
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days in 2..6 -> "in $days days"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}
