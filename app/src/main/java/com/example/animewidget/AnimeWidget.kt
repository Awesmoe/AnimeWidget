package com.example.animewidget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.firstOrNull
import androidx.glance.appwidget.action.actionStartActivity
import android.content.Intent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.core.net.toUri
import androidx.glance.action.clickable
import java.time.Instant
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.glance.ColorFilter
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.layout.height
import androidx.glance.layout.size

data class AnimeWithSchedule(
    val anime: MalAnime,
    val episode: Int?,
    val airingAt: Long?,
    val timeUntilAiring: Int?
)

private sealed class ContentState {
    data class Success(
        val animeList: List<AnimeWithSchedule>,
        val useEnglishTitle: Boolean,
        val hasMoeList: Boolean
    ) : ContentState()
    data class Error(val message: String) : ContentState()
}

class AnimeWidget : GlanceAppWidget() {

 //   override val sizeMode = SizeMode.Single
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 120.dp),
            DpSize(120.dp, 240.dp),
            DpSize(120.dp, 300.dp)
        )
    )


    override suspend fun provideGlance(context: Context, id: GlanceId) {

        val username = getUsername(context).firstOrNull()

        if (username.isNullOrBlank()) {
            provideContent {
                GlanceTheme {
                    SetupRequiredContent()
                }
            }
            return
        }

        val hasMoeList = isMoeListInstalled(context)

        val content = try {
            val useEnglishTitle = getUseEnglishTitle(context).firstOrNull() ?: true
            val includePlanToWatch = getIncludePlanToWatch(context).firstOrNull() ?: true
            val malFetcher = MalFetcher()
            val aniListFetcher = AniListFetcher()

            val animeList = if (includePlanToWatch) {
                malFetcher.getAnimeList(username)
            } else {
                malFetcher.fetchAnimeByStatus(username, 1)
            }

            val airingAnime = animeList.filter { anime ->
                anime.anime_airing_status == 1 || anime.anime_airing_status == 3
            }

            val malIds = airingAnime.map { it.anime_id }
            val schedules = aniListFetcher.getMultipleAiringSchedules(malIds)

            val animeWithSchedules = airingAnime.mapNotNull { anime ->
                val schedule = schedules[anime.anime_id]
                if (anime.anime_airing_status == 1 && schedule == null) {
                    null
                } else {
                    AnimeWithSchedule(
                        anime = anime,
                        episode = schedule?.episode,
                        airingAt = schedule?.airingAt,
                        timeUntilAiring = schedule?.timeUntilAiring
                    )
                }
            }

            val sortedAnime = animeWithSchedules.sortedBy { it.airingAt ?: Long.MAX_VALUE }

            ContentState.Success(sortedAnime, useEnglishTitle, hasMoeList)  // Pass timestamp

        } catch (e: Exception) {
            Log.e("AnimeWidget", "Error loading widget data", e)
            e.printStackTrace()
            ContentState.Error(e.message ?: "Unknown error")
        }

        provideContent {
            GlanceTheme {
                when (content) {
                    is ContentState.Success -> WidgetContent(
                        content.animeList,
                        content.useEnglishTitle,
                        content.hasMoeList
                    )
                    is ContentState.Error -> ErrorContent(content.message)
                }
            }
        }
    }

    @Composable
    private fun SetupRequiredContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Setup Required",
                style = TextStyle(color = GlanceTheme.colors.onSurface)
            )
            Text(
                text = "Open the app to configure",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }

    @Composable
    private fun ErrorContent(message: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error",
                style = TextStyle(color = GlanceTheme.colors.error)
            )
            Text(
                text = message,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 3
            )
        }
    }

    @Composable
    private fun WidgetContent(
        animeList: List<AnimeWithSchedule>,
        useEnglishTitle: Boolean,
        hasMoeList: Boolean,
    ) {

        if (animeList.isEmpty()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No airing anime",
                    style = TextStyle(color = GlanceTheme.colors.onSurface)
                )
                Spacer(modifier = GlanceModifier.height(16.dp))
                RefreshFooter()
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                items(animeList) { item ->
                    val title = if (useEnglishTitle) {
                        item.anime.anime_title_eng?.takeIf { it.isNotBlank() } ?: item.anime.anime_title
                    } else {
                        item.anime.anime_title
                    }
                    val timeStr = formatTimeUntil(item.timeUntilAiring ?: 0)

                    val clickIntent = if (hasMoeList) {
                        createMoeListIntent(item.anime.anime_id)
                    } else {
                        createMalWebIntent(item.anime.anime_id)
                    }

                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable(actionStartActivity(clickIntent))
                    ) {
                        Text(
                            text = title,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                            maxLines = 1
                        )
                        Text(
                            text = "Ep ${item.episode ?: "-"} (${item.anime.num_watched_episodes}) - $timeStr",
                            style = TextStyle(color = GlanceTheme.colors.onTertiaryContainer),
                            maxLines = 1
                        )
                    }
                }

                item {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    RefreshFooter()
                }
            }
        }
    }

    @Composable
    private fun RefreshFooter() {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .clickable(actionRunCallback<RefreshCallback>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tap to refresh",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = TextUnit(12f, TextUnitType.Sp)
                )
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            Image(
                provider = ImageProvider(android.R.drawable.ic_popup_sync),
                contentDescription = "Refresh",
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )
        }
    }

    fun createMoeListIntent(animeId: Int): Intent {
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

    fun createMalWebIntent(animeId: Int): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = "https://myanimelist.net/anime/$animeId".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isMoeListInstalled(context: Context): Boolean {
        return context.packageManager.getLaunchIntentForPackage("com.axiel7.moelist") != null
    }

    private fun formatTimeUntil(seconds: Int): String {
        if (seconds <= 0) return "Aired"

        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}

class AnimeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AnimeWidget()
}

class RefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d("AnimeWidget", "Manual refresh triggered")
        AnimeWidget().update(context, glanceId)
    }
}