package com.example.animewidget

import android.util.Log
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.fillMaxWidth
import getUseEnglishTitle
import getUsername
import kotlinx.coroutines.flow.firstOrNull

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AnimeWithSchedule(
    val anime: MalAnime,
    val episode: Int?,
    val airingAt: Long?,
    val timeUntilAiring: Int?
)

class AnimeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
//            DpSize(180.dp, 110.dp),
//            DpSize(180.dp, 180.dp),
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

        try {
            val useEnglishTitle = getUseEnglishTitle(context).firstOrNull() ?: true
            val malFetcher = MalFetcher()
            val aniListFetcher = AniListFetcher()
            val animeList = malFetcher.getAnimeList(username)

            val animeWithSchedules = animeList
                .filter { anime ->
                    anime.anime_airing_status == 1 || anime.anime_airing_status == 3
                }
                .mapNotNull { anime ->
                    val schedule = aniListFetcher.getAiringSchedule(anime.anime_id)
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

            provideContent {
                GlanceTheme {
                    WidgetContent(sortedAnime, useEnglishTitle)
                }
            }
        } catch (e: Exception) {
            Log.e("AnimeWidget", "Error loading widget data", e)
            provideContent {
                GlanceTheme {
                    ErrorContent(e.message ?: "Unknown error")
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
        val scope = rememberCoroutineScope()

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
                // optional refresh button
//            RefreshButton(onClick = { scope.launch { update(context, id) } })
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                items(animeList.size) { index ->
                    val item = animeList[index]
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
                            text = title ?: "Untitled",
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