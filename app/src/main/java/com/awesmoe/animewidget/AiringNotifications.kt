package com.awesmoe.animewidget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val AIRING_NOTIFICATION_CHANNEL_ID = "airing_notifications"
private const val AIRING_NOTIFICATION_CHANNEL_NAME = "Airing notifications"
private const val AIRING_NOTIFICATION_WORK_NAME = "airing-notification-worker"
private const val AIRING_NOTIFICATION_BOOTSTRAP_WORK_NAME = "airing-notification-bootstrap"

suspend fun syncAiringNotificationWork(context: Context) {
    val workManager = WorkManager.getInstance(context)
    val enabled = getAiringNotificationsEnabled(context).firstOrNull() ?: false
    val hasUsername = !getUsername(context).firstOrNull().isNullOrBlank()

    if (!enabled || !hasUsername) {
        workManager.cancelUniqueWork(AIRING_NOTIFICATION_WORK_NAME)
        workManager.cancelUniqueWork(AIRING_NOTIFICATION_BOOTSTRAP_WORK_NAME)
        return
    }

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val bootstrapRequest = OneTimeWorkRequestBuilder<AiringNotificationWorker>()
        .setConstraints(constraints)
        .build()

    val request = PeriodicWorkRequestBuilder<AiringNotificationWorker>(1, TimeUnit.HOURS)
        .setConstraints(constraints)
        .build()

    workManager.enqueueUniqueWork(
        AIRING_NOTIFICATION_BOOTSTRAP_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        bootstrapRequest
    )

    workManager.enqueueUniquePeriodicWork(
        AIRING_NOTIFICATION_WORK_NAME,
        ExistingPeriodicWorkPolicy.UPDATE,
        request
    )
}

class AiringNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result {
        val context = applicationContext
        val username = getUsername(context).firstOrNull()

        if (username.isNullOrBlank()) return Result.success()
        if (getAiringNotificationsEnabled(context).firstOrNull() != true) return Result.success()

        return try {
            val includePlanToWatch = getIncludePlanToWatch(context).firstOrNull() ?: true
            val useEnglishTitle = getUseEnglishTitle(context).firstOrNull() ?: true

            val malFetcher = MalFetcher(httpClient)
            val aniListFetcher = AniListFetcher(httpClient)

            val animeList = if (includePlanToWatch) {
                malFetcher.getAnimeList(username)
            } else {
                malFetcher.fetchAnimeByStatus(username, 1)
            }

            val trackedAnime = animeList.filter { anime ->
                anime.anime_airing_status == 1 || anime.anime_airing_status == 3
            }

            val schedules = aniListFetcher.getMultipleAiringSchedules(trackedAnime.map { it.anime_id })

            val currentStates = trackedAnime.map { anime ->
                val schedule = schedules[anime.anime_id]
                val title = if (useEnglishTitle) {
                    anime.anime_title_eng?.takeIf { it.isNotBlank() } ?: anime.anime_title
                } else {
                    anime.anime_title
                }

                AiringNotificationState(
                    animeId = anime.anime_id,
                    title = title,
                    episode = schedule?.episode,
                    airingAt = schedule?.airingAt
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
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
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

private fun createMoeListIntent(animeId: Int): Intent {
    return Intent().apply {
        setClassName(
            "com.axiel7.moelist",
            "com.axiel7.moelist.ui.main.MainActivity"
        )
        action = "details"
        putExtra("media_id", animeId)
        putExtra("media_type", "anime")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        addCategory(animeId.toString())
    }
}

private fun createMalWebIntent(animeId: Int): Intent {
    return Intent(Intent.ACTION_VIEW).apply {
        data = "https://myanimelist.net/anime/$animeId".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private fun isMoeListInstalled(context: Context): Boolean {
    return context.packageManager.getLaunchIntentForPackage("com.axiel7.moelist") != null
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
