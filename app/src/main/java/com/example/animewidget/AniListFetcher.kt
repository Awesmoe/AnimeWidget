package com.example.animewidget

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AniListFetcher {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAiringSchedule(malId: Int): AiringNode? = withContext(Dispatchers.IO) {
        try {
            val query = """
            query (${'$'}idMal: Int) {
                Media(idMal: ${'$'}idMal, type: ANIME) {
                    id
                    title {
                        romaji
                        english
                    }
                    airingSchedule(notYetAired: true, perPage: 1) {
                        nodes {
                            episode
                            airingAt
                            timeUntilAiring
                        }
                    }
                    status
                }
            }
        """.trimIndent()

            val requestBody = buildJsonObject {
                put("query", JsonPrimitive(query))
                put("variables", buildJsonObject {
                    put("idMal", JsonPrimitive(malId))
                })
            }.toString()

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("AniListFetcher", "HTTP Error for MAL $malId: ${response.code}")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null

            val aniListResponse = json.decodeFromString<AniListResponse>(responseBody)
            val nodes = aniListResponse.data?.Media?.airingSchedule?.nodes

            if (nodes != null && nodes.isNotEmpty()) {
                Log.d("AniListFetcher", "Found schedule for MAL $malId: Ep ${nodes[0].episode}")
                return@withContext nodes[0]
            }

            Log.d("AniListFetcher", "No schedule found for MAL $malId")
            return@withContext null

        } catch (e: Exception) {
            Log.e("AniListFetcher", "Error fetching AniList data for MAL $malId", e)
            return@withContext null
        }
    }
}