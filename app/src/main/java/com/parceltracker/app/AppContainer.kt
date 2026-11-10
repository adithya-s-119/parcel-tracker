package com.parceltracker.app

import android.content.Context
import com.parceltracker.app.data.AppDatabase
import com.parceltracker.app.data.GmailApi
import com.parceltracker.app.data.GmailAuthManager
import com.parceltracker.app.data.GmailSyncManager
import com.parceltracker.app.data.ImapGmailClient
import com.parceltracker.app.data.ImapSyncManager
import com.parceltracker.app.data.MailSyncCoordinator
import com.parceltracker.app.data.OrderRepository
import com.parceltracker.app.data.SecureCredentialStore
import com.parceltracker.app.data.SettingsStore
import com.parceltracker.app.data.SmsSyncManager
import com.parceltracker.app.widget.DeliveryWidget

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.get(appContext)
    val settingsStore = SettingsStore(appContext)
    val authManager = GmailAuthManager(appContext)
    val credentialStore = SecureCredentialStore(appContext)
    val gmailApi = GmailApi()
    val imapClient = ImapGmailClient()
    val repository = OrderRepository(database.orderDao())

    private val oauthSyncManager = GmailSyncManager(
        auth = authManager,
        api = gmailApi,
        repository = repository,
        dao = database.orderDao(),
        settingsStore = settingsStore,
        onDataChanged = { DeliveryWidget.refresh(appContext) },
    )

    private val imapSyncManager = ImapSyncManager(
        client = imapClient,
        credentialStore = credentialStore,
        repository = repository,
        dao = database.orderDao(),
        settingsStore = settingsStore,
        onDataChanged = { DeliveryWidget.refresh(appContext) },
    )

    val smsSyncManager = SmsSyncManager(
        context = appContext,
        repository = repository,
        dao = database.orderDao(),
        settingsStore = settingsStore,
        onDataChanged = { DeliveryWidget.refresh(appContext) },
    )

    val syncManager = MailSyncCoordinator(
        settingsStore = settingsStore,
        dao = database.orderDao(),
        oauthSync = oauthSyncManager,
        imapSync = imapSyncManager,
        smsSync = smsSyncManager,
    )
}
