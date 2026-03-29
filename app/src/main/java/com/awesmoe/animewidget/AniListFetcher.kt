package com.awesmoe.animewidget

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal val sharedJson = Json { ignoreUnknownKeys = true }

class AniListFetcher(private val client: OkHttpClient) {

    suspend fun getMultipleAiringSchedules(malIds: List<Int>): Map<Int, AiringNode?> = withContext(Dispatchers.IO) {
        if (malIds.isEmpty()) return@withContext emptyMap()

        try {
            val queries = malIds.mapIndexed { index, malId ->
                """
                anime$index: Media(idMal: $malId, type: ANIME) {
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
                """.trimIndent()
            }.joinToString("\n")

            val query = "query { $queries }"

            val requestBody = buildJsonObject {
                put("query", JsonPrimitive(query))
            }.toString()

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build()

            val responseBody = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AniListFetcher", "HTTP Error: ${response.code}")
                }
                response.body?.string()
            } ?: return@withContext malIds.associateWith { null }
            val jsonResponse = sharedJson.parseToJsonElement(responseBody).jsonObject
            val data = jsonResponse["data"] as? JsonObject
            if (data == null) {
                val errorMessage = jsonResponse["errors"]
                    ?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("message")?.jsonPrimitive?.content
                    ?: "AniList returned no data"
                throw IOException(errorMessage)
            }

            val results = mutableMapOf<Int, AiringNode?>()
            malIds.forEachIndexed { index, malId ->
                try {
                    val animeData = data["anime$index"]?.jsonObject
                    val nodes = animeData
                        ?.get("airingSchedule")?.jsonObject
                        ?.get("nodes")?.let { nodesElement ->
                            sharedJson.decodeFromJsonElement<List<AiringNode>>(nodesElement)
                        }

                    if (nodes != null && nodes.isNotEmpty()) {
                        results[malId] = nodes[0]
                        Log.d("AniListFetcher", "Found schedule for MAL $malId: Ep ${nodes[0].episode}")
                    } else {
                        results[malId] = null
                        Log.d("AniListFetcher", "No schedule found for MAL $malId")
                    }
                } catch (e: Exception) {
                    Log.e("AniListFetcher", "Error parsing data for MAL $malId", e)
                    results[malId] = null
                }
            }

            return@withContext results

        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            Log.e("AniListFetcher", "Error fetching batch AniList data", e)
            return@withContext malIds.associateWith { null }
        }
    }
}