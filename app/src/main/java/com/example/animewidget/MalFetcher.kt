package com.example.animewidget

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

class MalFetcher {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAnimeList(username: String): List<MalAnime> = withContext(Dispatchers.IO) {
        try {
            val allAnime = mutableListOf<MalAnime>()

            // Fetch watching (status=1)
            val watchingAnime = fetchAnimeByStatus(username, 1)
            allAnime.addAll(watchingAnime)
            Log.d("MalFetcher", "Found ${watchingAnime.size} watching anime")

            // Fetch plan to watch (status=6)
            val planToWatch = fetchAnimeByStatus(username, 6)
            allAnime.addAll(planToWatch)
            Log.d("MalFetcher", "Found ${planToWatch.size} plan to watch anime")

            Log.d("MalFetcher", "Total anime: ${allAnime.size}")
            return@withContext allAnime

        } catch (e: Exception) {
            Log.e("MalFetcher", "Error fetching MAL data", e)
            return@withContext emptyList()
        }
    }

    suspend fun fetchAnimeByStatus(username: String, status: Int): List<MalAnime> = withContext(Dispatchers.IO) {
        try {
            val url = "https://myanimelist.net/animelist/$username?status=$status"
            Log.d("Fetcher", url)
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            Log.d("MalFetcher", "Response code for status $status: ${response.code}")

            if (!response.isSuccessful) {
                Log.e("MalFetcher", "HTTP Error: ${response.code}")
                return@withContext emptyList()
            }

            val html = response.body?.string() ?: return@withContext emptyList()

            Log.d("MalFetcher", "HTML length for status $status: ${html.length}")

            // Parse HTML to find the data-items attribute
            val doc = Jsoup.parse(html, Parser.htmlParser())
            val listTable = doc.select("table.list-table").first()

            if (listTable == null) {
                Log.e("MalFetcher", "Could not find list table for status $status")
                return@withContext emptyList()
            }

            val dataItems = listTable.attr("data-items")

            if (dataItems.isEmpty()) {
                Log.e("MalFetcher", "data-items attribute is empty for status $status")
                return@withContext emptyList()
            }

            // Parse JSON
            val animeList = json.decodeFromString<List<MalAnime>>(dataItems)

            return@withContext animeList

        } catch (e: Exception) {
            Log.e("MalFetcher", "Error fetching status $status", e)
            return@withContext emptyList()
        }
    }
}
