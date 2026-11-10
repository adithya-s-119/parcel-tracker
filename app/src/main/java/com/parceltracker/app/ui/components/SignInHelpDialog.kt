package com.parceltracker.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.parceltracker.app.data.AuthDiagnostics

@Composable
fun SignInHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sha1 = remember { AuthDiagnostics.signingSha1(context) }
    val playServices = remember { AuthDiagnostics.playServicesStatus(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gmail sign-in help") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "If sign-in is cancelled or fails, check these in order:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(10.dp))
                HelpItem("1. Google Play services", playServices)
                HelpItem("2. Package name", context.packageName)
                HelpItem("3. Signing SHA-1", sha1 ?: "Unavailable")
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "In Google Cloud Console, create an Android OAuth client with this " +
                        "package name and SHA-1. Enable the Gmail API, add the " +
                        "gmail.readonly scope to the OAuth consent screen, and add your " +
                        "Google account as a test user while the app is in Testing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "If you see a Play services internal error (code 8): update Google " +
                        "Play services from the Play Store, add a Google account in Android " +
                        "Settings → Accounts, reboot the device, and try again. Emulators must " +
                        "use a Google Play system image, not a plain AOSP or Google APIs image.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reinstall the APK after changing the OAuth client. If it still fails, " +
                        "capture the log with: adb logcat -s ParcelAuth",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun HelpItem(label: String, value: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SelectionContainer {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}
