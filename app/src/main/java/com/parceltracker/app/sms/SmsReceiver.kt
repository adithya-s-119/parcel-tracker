package com.parceltracker.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.parceltracker.app.ParcelApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext as? ParcelApp ?: return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isEmpty()) return

        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val address = parts.firstOrNull()?.originatingAddress.orEmpty()
        val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        ParcelApp.appScope.launch {
            val settings = app.container.settingsStore.settings.first()
            if (!settings.smsEnabled) return@launch
            app.container.smsSyncManager.ingest(address, body, timestamp)
        }
    }
}
