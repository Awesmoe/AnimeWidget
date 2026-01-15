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
            DpSize(120.dp, 200.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val username = "Awesmoe"  // Replace with your username

        val malFetcher = MalFetcher()
        val aniListFetcher = AniListFetcher()

        val animeList = malFetcher.getAnimeList(username)

        // Fetch AniList data for each anime
        val animeWithSchedules = animeList
            .filter { anime ->
                anime.anime_airing_status == 1 || anime.anime_airing_status == 3
            }
            .mapNotNull { anime ->
                val schedule = aniListFetcher.getAiringSchedule(anime.anime_id)

                // discard anime with no upcoming episode info if AniList says it's finished
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

Log.d("MalFetcher","status 1 or 3: ${animeWithSchedules.size}")
        // Sort by airing time (soonest first)
        val sortedAnime = animeWithSchedules.sortedBy { it.airingAt ?: Long.MAX_VALUE }

        provideContent {
            GlanceTheme {
                WidgetContent(sortedAnime)
            }
        }
    }

    @Composable
    private fun WidgetContent(animeList: List<AnimeWithSchedule>) {
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
                    val title = item.anime.anime_title_eng ?: item.anime.anime_title
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