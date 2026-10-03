package com.awesmoe.animewidget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.firstOrNull

class AnimeWidget : GlanceAppWidget() {

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
        val useEnglishTitle = getUseEnglishTitle(context).firstOrNull() ?: true
        val state = getCachedWidgetState(context, username)

        if (state == null) {
            // No cached data yet — kick off a fetch and show a loading state.
            // The worker calls updateAll() when it finishes.
            enqueueOneTimeRefresh(context)
            provideContent {
                GlanceTheme {
                    LoadingContent()
                }
            }
            return
        }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    animeList = state.animeList,
                    useEnglishTitle = useEnglishTitle,
                    hasMoeList = hasMoeList,
                    aniListError = state.aniListError,
                    lastUpdated = state.lastUpdated
                )
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
    private fun LoadingContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Loading\u2026",
                style = TextStyle(color = GlanceTheme.colors.onSurface)
            )
        }
    }

    @Composable
    private fun AniListErrorBanner(error: String) {
        Text(
            text = "AniList: $error",
            style = TextStyle(
                color = GlanceTheme.colors.error,
                fontSize = TextUnit(11f, TextUnitType.Sp)
            ),
            maxLines = 2,
        )
    }

    @Composable
    private fun WidgetContent(
        animeList: List<AnimeWithSchedule>,
        useEnglishTitle: Boolean,
        hasMoeList: Boolean,
        aniListError: String? = null,
        lastUpdated: Long = 0L,
    ) {

        if (animeList.isEmpty()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (aniListError != null) {
                    AniListErrorBanner(aniListError)
                } else {
                    Text(
                        text = "No airing anime",
                        style = TextStyle(color = GlanceTheme.colors.onSurface)
                    )
                }
                Spacer(modifier = GlanceModifier.height(16.dp))
                RefreshFooter(lastUpdated)
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                if (aniListError != null) {
                    item {
                        AniListErrorBanner(aniListError)
                        Spacer(modifier = GlanceModifier.height(8.dp))
                    }
                }
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
                            text = "Ep ${item.episode ?: "-"} (${item.anime.num_watched_episodes ?: "?"}) - $timeStr",
                            style = TextStyle(color = GlanceTheme.colors.onTertiaryContainer),
                            maxLines = 1
                        )
                    }
                }

                item {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    RefreshFooter(lastUpdated)
                }
            }
        }
    }

    @Composable
    private fun RefreshFooter(lastUpdated: Long = 0L) {
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

            if (lastUpdated > 0) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                    text = formatLastUpdated(lastUpdated),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = TextUnit(11f, TextUnitType.Sp)
                    )
                )
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

    private fun formatLastUpdated(millis: Long): String {
        if (millis <= 0) return ""
        val minutes = (System.currentTimeMillis() - millis) / 60_000
        return when {
            minutes < 1 -> "Updated just now"
            minutes < 60 -> "Updated ${minutes}m ago"
            minutes < 1440 -> "Updated ${minutes / 60}h ago"
            else -> "Updated ${minutes / 1440}d ago"
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
        enqueueOneTimeRefresh(context)
    }
}
