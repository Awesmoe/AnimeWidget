package com.awesmoe.animewidget

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.OkHttpClient

/**
 * Single shared HTTP client. Explicit timeouts so a hung connection can't
 * stall a worker (or, previously, the widget's provideGlance) indefinitely,
 * plus a browser User-Agent to reduce the odds of MAL/Cloudflare flagging us
 * as a bot (OkHttp's default UA is `okhttp/<version>`).
 */
val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

/**
 * Fetches the user's airing anime (MAL list + AniList schedules), persists the
 * result as a [CachedWidgetState], and returns it.
 *
 * AniList failures degrade gracefully (schedules become null, an error banner
 * flag is set). MAL failures throw [IOException] so the caller can retry.
 */
suspend fun refreshAnimeData(context: Context): CachedWidgetState {
    val username = getUsername(context).firstOrNull()
        ?: return CachedWidgetState()

    val includePlanToWatch = getIncludePlanToWatch(context).firstOrNull() ?: true

    val malFetcher = MalFetcher(sharedHttpClient)
    val aniListFetcher = AniListFetcher(sharedHttpClient)

    val animeList = if (includePlanToWatch) {
        malFetcher.getAnimeList(username)
    } else {
        malFetcher.fetchAnimeByStatus(username, 1)
    }

    val airingAnime = animeList.filter { anime ->
        anime.anime_airing_status == 1 || anime.anime_airing_status == 3
    }

    val malIds = airingAnime.map { it.anime_id }

    var aniListError: String? = null
    val schedules = try {
        aniListFetcher.getMultipleAiringSchedules(malIds)
    } catch (e: IOException) {
        aniListError = e.message
        malIds.associateWith { null }
    }

    val animeWithSchedules = if (aniListError != null) {
        airingAnime.map { anime ->
            val schedule = schedules[anime.anime_id]
            AnimeWithSchedule(
                anime = anime,
                episode = schedule?.episode,
                airingAt = schedule?.airingAt,
                timeUntilAiring = schedule?.timeUntilAiring
            )
        }
    } else {
        airingAnime.mapNotNull { anime ->
            val schedule = schedules[anime.anime_id]
            if (anime.anime_airing_status == 1 && schedule == null) null
            else AnimeWithSchedule(
                anime = anime,
                episode = schedule?.episode,
                airingAt = schedule?.airingAt,
                timeUntilAiring = schedule?.timeUntilAiring
            )
        }
    }

    val sortedAnime = animeWithSchedules.sortedBy { it.airingAt ?: Long.MAX_VALUE }

    val state = CachedWidgetState(
        animeList = sortedAnime,
        aniListError = aniListError,
        lastUpdated = System.currentTimeMillis()
    )

    saveCachedWidgetState(context, username, state)
    return state
}
