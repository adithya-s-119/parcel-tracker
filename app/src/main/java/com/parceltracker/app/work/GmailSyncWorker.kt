package com.parceltracker.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parceltracker.app.ParcelApp
import com.parceltracker.app.data.SyncResult
import kotlinx.coroutines.flow.first

class GmailSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ParcelApp ?: return Result.success()
        val settings = app.container.settingsStore.settings.first()
        if (!settings.autoSync || settings.accountEmail == null) return Result.success()

        return when (app.container.syncManager.sync().outcome) {
            SyncResult.Outcome.SUCCESS,
            SyncResult.Outcome.NEEDS_AUTH,
            -> Result.success()

            SyncResult.Outcome.FAILED ->
                if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }
}
