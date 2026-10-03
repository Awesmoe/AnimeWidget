package com.awesmoe.animewidget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val AIRING_NOTIFICATION_CHANNEL_ID = "airing_notifications"
private const val AIRING_NOTIFICATION_CHANNEL_NAME = "Airing notifications"
private const val REFRESH_WORK_NAME = "anime-refresh-worker"
private const val REFRESH_BOOTSTRAP_WORK_NAME = "anime-refresh-bootstrap"

// Old work names from before the worker was unified — cancelled so the
// now-removed AiringNotificationWorker doesn't fire after an update.
private const val LEGACY_NOTIFICATION_WORK_NAME = "airing-notification-worker"
private const val LEGACY_NOTIFICATION_BOOTSTRAP_WORK_NAME = "airing-notification-bootstrap"

/**
 * Schedules the periodic refresh worker (whenever a username is set) plus a
 * one-time bootstrap run for an immediate first load. The widget depends on
 * this worker for fresh data even when notifications are disabled.
 */
suspend fun syncRefreshWork(context: Context) {
    val workManager = WorkManager.getInstance(context)

    workManager.cancelUniqueWork(LEGACY_NOTIFICATION_WORK_NAME)
    workManager.cancelUniqueWork(LEGACY_NOTIFICATION_BOOTSTRAP_WORK_NAME)

    val hasUsername = !getUsername(context).firstOrNull().isNullOrBlank()

    if (!hasUsername) {
        workManager.cancelUniqueWork(REFRESH_WORK_NAME)
        workManager.cancelUniqueWork(REFRESH_BOOTSTRAP_WORK_NAME)
        return
    }

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val bootstrapRequest = OneTimeWorkRequestBuilder<RefreshWorker>()
        .setConstraints(constraints)
        .build()

    val periodicRequest = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .build()

    workManager.enqueueUniqueWork(
        REFRESH_BOOTSTRAP_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        bootstrapRequest
    )

    workManager.enqueueUniquePeriodicWork(
        REFRESH_WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        periodicRequest
    )
}

/** Queues a single immediate refresh (manual "tap to refresh" / first load). */
fun enqueueOneTimeRefresh(context: Context) {
    val workManager = WorkManager.getInstance(context)

    workManager.cancelUniqueWork(LEGACY_NOTIFICATION_WORK_NAME)
    workManager.cancelUniqueWork(LEGACY_NOTIFICATION_BOOTSTRAP_WORK_NAME)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = OneTimeWorkRequestBuilder<RefreshWorker>()
        .setConstraints(constraints)
        .build()

    workManager.enqueueUniqueWork(
        REFRESH_BOOTSTRAP_WORK_NAME,
        ExistingWorkPolicy.KEEP,
        request
    )
}

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val username = getUsername(context).firstOrNull()

        if (username.isNullOrBlank()) return Result.success()

        return try {
            val state = refreshAnimeData(context)

            if (getAiringNotificationsEnabled(context).firstOrNull() == true) {
                processAiringNotifications(context, username, state.animeList)
            }

            AnimeWidget().updateAll(context)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        } catch (e: Exception) {
            // Non-network failure — retrying won't help; keep the last cache.
            Log.w("RefreshWorker", "Unexpected failure; giving up", e)
            Result.success()
        }
    }
}

private suspend fun processAiringNotifications(
    context: Context,
    username: String,
    animeList: List<AnimeWithSchedule>
) {
    val useEnglishTitle = getUseEnglishTitle(context).firstOrNull() ?: true

    val currentStates = animeList.map { item ->
        AiringNotificationState(
            animeId = item.anime.anime_id,
            title = if (useEnglishTitle) {
                item.anime.anime_title_eng?.takeIf { it.isNotBlank() } ?: item.anime.anime_title
            } else {
                item.anime.anime_title
            },
            episode = item.episode,
            airingAt = item.airingAt
        )
    }

    val previousStates = getAiringNotificationStates(context, username)
    val nowEpochSeconds = System.currentTimeMillis() / 1000

    if (previousStates.isNotEmpty()) {
        currentStates.forEach { current ->
            val previous = previousStates[current.animeId] ?: return@forEach
            val previousAired = previous.airingAt != null && previous.airingAt <= nowEpochSeconds
            val scheduleAdvanced =
                current.episode == null ||
                    (previous.episode != null &&
                        (current.episode ?: previous.episode) > previous.episode)

            if (previousAired && scheduleAdvanced && previous.episode != null) {
                postAiringNotification(context, previous, current.animeId)
            }
        }
    }

    saveAiringNotificationStates(context, username, currentStates)
}

private fun postAiringNotification(
    context: Context,
    state: AiringNotificationState,
    animeId: Int
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    ensureNotificationChannel(context)

    val tapIntent = if (isMoeListInstalled(context)) {
        createMoeListIntent(animeId)
    } else {
        createMalWebIntent(animeId)
    }

    val contentIntent = PendingIntent.getActivity(
        context,
        animeId,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, AIRING_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle("${state.title} aired")
        .setContentText("Episode ${state.episode} is now out.")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText("${state.title}, Episode ${state.episode} has aired.")
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()

    NotificationManagerCompat.from(context).notify("${animeId}:${state.episode}".hashCode(), notification)
}

private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val existing = manager.getNotificationChannel(AIRING_NOTIFICATION_CHANNEL_ID)
    if (existing != null) return

    val channel = NotificationChannel(
        AIRING_NOTIFICATION_CHANNEL_ID,
        AIRING_NOTIFICATION_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = "Notifications when tracked anime episodes air"
    }

    manager.createNotificationChannel(channel)
}
