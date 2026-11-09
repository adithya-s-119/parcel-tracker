package com.parceltracker.app

import android.app.Application
import com.parceltracker.app.notifications.Notifications
import com.parceltracker.app.widget.DeliveryWidget
import com.parceltracker.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ParcelApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)

        appScope.launch {
            val settings = container.settingsStore.settings.first()
            WorkScheduler.apply(this@ParcelApp, settings)
            DeliveryWidget.refresh(this@ParcelApp)
        }
    }

    companion object {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
