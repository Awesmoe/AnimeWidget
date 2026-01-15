package com.example.animewidget

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class MalAnime(
    val anime_id: Int,
    val anime_title: String,
    val anime_title_eng: String? = null,
    val anime_airing_status: Int,  // 1 = airing, 2 = finished, 3 = not yet aired
    val status: Int,  // user's watch status: 1 = watching, 6 = plan to watch,
    val num_watched_episodes: Int? = null
)

@Serializable
data class AniListResponse(
    val data: AniListData? = null
)

@Serializable
data class AniListData(
    val Media: AniListMediaSimple? = null
)

@Serializable
data class AniListMediaSimple(
    val id: Int? = null,
    val title: AniListTitle? = null,
    val nextAiringEpisode: AiringNode? = null,
    val airingSchedule: AiringSchedule? = null,  // Add this
    val status: String? = null
)

@Serializable
data class AiringSchedule(
    val nodes: List<AiringNode> = emptyList()
)

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null
)

@Serializable
data class AiringNode(
    val episode: Int? = null,
    val airingAt: Long? = null,
    val timeUntilAiring: Int? = null
)