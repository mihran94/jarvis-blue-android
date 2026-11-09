package com.jarvisblue.mobile.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jarvisblue.mobile.MainActivity
import com.jarvisblue.mobile.R
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Checks for a newer release only. Never downloads or installs an APK in the background. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val updater = AppUpdateManager(applicationContext)
        if (!updater.autoCheckEnabled || updater.feedUrl.isBlank()) return Result.success()
        return try {
            val candidate = updater.check() // validates HTTPS, package, digest field and version
            if (candidate.versionCode > updater.currentVersionCode) {
                updater.saveCheckStatus("Update ${candidate.versionName} available · ${Instant.now()}", candidate.versionName)
                showNotification(candidate.versionName)
            } else {
                updater.saveCheckStatus("No newer release · ${Instant.now()}")
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            // No repeated network requests; next daily check may try again.
            updater.saveCheckStatus("Last check failed · ${exception.message.orEmpty()}")
            Result.success()
        }
    }

    private fun showNotification(version: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return // The result remains in Settings if notifications are disabled.

        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "JARVIS BLUE updates", NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "New signed app releases; installation is always manual" })
        }
        val activity = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openApp = PendingIntent.getActivity(
            applicationContext, 1199, activity,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("JARVIS BLUE update available")
            .setContentText("Version $version · Open Settings to review and verify the APK")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "jarvis_update_notices"
        private const val NOTIFICATION_ID = 1199
    }
}

/** User-controlled periodic work; never runs if disabled or without the configured network. */
internal object UpdateCheckScheduler {
    private const val WORK_NAME = "jarvis_release_update_check"

    fun configure(context: Context) {
        val updater = AppUpdateManager(context.applicationContext)
        val work = WorkManager.getInstance(context.applicationContext)
        if (!updater.autoCheckEnabled || updater.feedUrl.isBlank()) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(
            if (updater.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
        ).build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
