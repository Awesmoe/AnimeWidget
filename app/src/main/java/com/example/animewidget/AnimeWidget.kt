package com.example.animewidget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
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
import getIncludePlanToWatch
import getUseEnglishTitle
import getUsername
import kotlinx.coroutines.flow.firstOrNull

data class AnimeWithSchedule(
    val anime: MalAnime,
    val episode: Int?,
    val airingAt: Long?,
    val timeUntilAiring: Int?
)

private sealed class ContentState {
    data class Success(val animeList: List<AnimeWithSchedule>, val useEnglishTitle: Boolean) : ContentState()
    data class Error(val message: String) : ContentState()
}

class AnimeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Single
/*    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(180.dp, 110.dp),
            DpSize(180.dp, 180.dp),
            DpSize(120.dp, 300.dp)
        )
    )
 */

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

            // Filter airing anime first
            val airingAnime = animeList.filter { anime ->
                anime.anime_airing_status == 1 || anime.anime_airing_status == 3
            }

            // Batch fetch all schedules at once
            val malIds = airingAnime.map { it.anime_id }
            val schedules = aniListFetcher.getMultipleAiringSchedules(malIds)

            // Combine anime with their schedules
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

            // Return success content
            ContentState.Success(sortedAnime, useEnglishTitle)

        } catch (e: Exception) {
            Log.e("AnimeWidget", "Error loading widget data", e)
            e.printStackTrace()
            // Return error content
            ContentState.Error(e.message ?: "Unknown error")
        }

        // Single provideContent call based on state
        provideContent {
            GlanceTheme {
                when (content) {
                    is ContentState.Success -> WidgetContent(content.animeList, content.useEnglishTitle)
                    is ContentState.Error -> ErrorContent(content.message)
                }
            }
        }
    }

    @Composable
    private fun LoadingContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Loading...",
                style = TextStyle(color = GlanceTheme.colors.onSurface)
            )
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
    private fun WidgetContent(animeList: List<AnimeWithSchedule>, useEnglishTitle: Boolean) {

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

                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
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
            }
        }
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