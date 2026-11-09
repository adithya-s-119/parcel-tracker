package com.parceltracker.app

import android.content.Context
import com.parceltracker.app.data.AppDatabase
import com.parceltracker.app.data.GmailApi
import com.parceltracker.app.data.GmailAuthManager
import com.parceltracker.app.data.GmailSyncManager
import com.parceltracker.app.data.OrderRepository
import com.parceltracker.app.data.SettingsStore
import com.parceltracker.app.widget.DeliveryWidget

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.get(appContext)
    val settingsStore = SettingsStore(appContext)
    val authManager = GmailAuthManager(appContext)
    val gmailApi = GmailApi()
    val repository = OrderRepository(database.orderDao())

    val syncManager = GmailSyncManager(
        auth = authManager,
        api = gmailApi,
        repository = repository,
        dao = database.orderDao(),
        settingsStore = settingsStore,
        onDataChanged = { DeliveryWidget.refresh(appContext) },
    )
}
