package com.socialitefree.app.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.socialitefree.app.data.PreferencesManager
import kotlinx.coroutines.flow.first

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val update = UpdateChecker.checkForUpdate() ?: return Result.success()
        val prefs = PreferencesManager.getInstance(applicationContext)
        val lastNotified = prefs.getInt(PreferencesManager.LAST_NOTIFIED_UPDATE_CODE, 0).first()

        if (update.buildCode > lastNotified) {
            UpdateChecker.showUpdateNotification(applicationContext, update)
            prefs.setInt(PreferencesManager.LAST_NOTIFIED_UPDATE_CODE, update.buildCode)
        }
        return Result.success()
    }
}
