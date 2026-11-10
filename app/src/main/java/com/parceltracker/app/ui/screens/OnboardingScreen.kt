package com.parceltracker.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parceltracker.app.ui.components.GoogleGMark
import com.parceltracker.app.ui.components.ParcelWordmark
import com.parceltracker.app.ui.components.SignInHelpDialog
import com.parceltracker.app.ui.theme.Forest
import com.parceltracker.app.ui.theme.PaperBright
import com.parceltracker.app.ui.theme.Sage
import com.parceltracker.app.ui.theme.Terracotta

@Composable
fun OnboardingScreen(
    syncing: Boolean,
    syncMessage: String?,
    authError: String?,
    onConnect: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showSignInHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ParcelWordmark()
                Text(
                    text = "1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HeroIllustration()
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Know when\nit's coming.",
                style = MaterialTheme.typography.displayLarge,
                color = colors.onBackground,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Parcel reads your order emails and turns promised delivery dates into one calm calendar.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(26.dp))
            FeatureRow(
                icon = Icons.Outlined.MailOutline,
                title = "Gmail, read-only",
                subtitle = "Only order and shipping emails are scanned.",
            )
            Spacer(modifier = Modifier.height(16.dp))
            FeatureRow(
                icon = Icons.Outlined.CalendarMonth,
                title = "One honest calendar",
                subtitle = "Promised dates, updated when plans change.",
            )
            Spacer(modifier = Modifier.height(16.dp))
            FeatureRow(
                icon = Icons.Outlined.Widgets,
                title = "Glanceable widget",
                subtitle = "Your next deliveries on the home screen.",
            )

            Spacer(modifier = Modifier.height(30.dp))

            Button(
                onClick = onConnect,
                enabled = !syncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Forest,
                    contentColor = PaperBright,
                ),
            ) {
                if (syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        color = PaperBright,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = syncMessage ?: "Connecting…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                } else {
                    GoogleGMark()
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onSkip,
                    enabled = !syncing,
                ) {
                    Text(
                        text = "Browse without Gmail",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                )
                TextButton(onClick = { showSignInHelp = true }) {
                    Text(
                        text = "Sign-in help",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            if (!authError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = colors.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = colors.onErrorContainer,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Couldn't connect",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onErrorContainer,
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = authError,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Your email is processed on this device. Nothing is sent to a server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showSignInHelp) {
        SignInHelpDialog(onDismiss = { showSignInHelp = false })
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(13.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(272.dp),
    ) {
        Box(
            modifier = Modifier
                .size(248.dp)
                .align(Alignment.Center)
                .offset(y = (-2).dp)
                .rotate(-7f)
                .background(Sage.copy(alpha = 0.18f), RoundedCornerShape(54.dp)),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(size.width * 0.11f, size.height * 0.76f)
                cubicTo(
                    size.width * 0.28f,
                    size.height * 0.96f,
                    size.width * 0.62f,
                    size.height * 0.94f,
                    size.width * 0.82f,
                    size.height * 0.78f,
                )
            }
            drawPath(
                path = path,
                color = Terracotta.copy(alpha = 0.35f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 7.dp.toPx()),
                    ),
                ),
            )
            drawCircle(
                color = Terracotta.copy(alpha = 0.45f),
                radius = 3.dp.toPx(),
                center = Offset(size.width * 0.11f, size.height * 0.76f),
            )
        }

        Surface(
            modifier = Modifier
                .width(238.dp)
                .align(Alignment.TopCenter)
                .offset(y = 14.dp)
                .rotate(-2.5f),
            shape = RoundedCornerShape(26.dp),
            color = PaperBright,
            shadowElevation = 12.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "NOVEMBER",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "2026",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                repeat(4) { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val day = row * 7 + column + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (day == 12) {
                                    Box(
                                        modifier = Modifier
                                            .size(23.dp)
                                            .background(Terracotta, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "12",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                        )
                                    }
                                } else {
                                    Text(
                                        text = day.toString(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Terracotta, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "2 deliveries this week",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp)
                .offset(y = (-10).dp)
                .size(120.dp)
                .rotate(4f)
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .background(Forest, RoundedCornerShape(24.dp)),
        ) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center)
                    .background(Terracotta.copy(alpha = 0.9f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(PaperBright.copy(alpha = 0.94f), RoundedCornerShape(7.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(3.dp)
                            .background(Forest.copy(alpha = 0.4f), CircleShape),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(3.dp)
                            .background(Forest.copy(alpha = 0.25f), CircleShape),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 62.dp, y = (-34).dp)
                .size(48.dp)
                .shadow(10.dp, CircleShape)
                .background(Terracotta, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
