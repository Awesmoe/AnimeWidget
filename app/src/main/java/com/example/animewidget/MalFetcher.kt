package com.example.animewidget

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.URLEncoder

class MalFetcher(private val client: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAnimeList(username: String): List<MalAnime> = withContext(Dispatchers.IO) {
        val watchingDeferred = async { fetchAnimeByStatus(username, 1) }
        val planToWatchDeferred = async { fetchAnimeByStatus(username, 6) }

        val watchingAnime = watchingDeferred.await()
        Log.d("MalFetcher", "Found ${watchingAnime.size} watching anime")

        val planToWatch = planToWatchDeferred.await()
        Log.d("MalFetcher", "Found ${planToWatch.size} plan to watch anime")

        val allAnime = watchingAnime + planToWatch
        Log.d("MalFetcher", "Total anime: ${allAnime.size}")
        allAnime
    }

    suspend fun fetchAnimeByStatus(username: String, status: Int): List<MalAnime> = withContext(Dispatchers.IO) {
        val encodedUsername = URLEncoder.encode(username, "UTF-8")
        val url = "https://myanimelist.net/animelist/$encodedUsername?status=$status"
        Log.d("Fetcher", url)
        val request = Request.Builder().url(url).build()

        val html = client.newCall(request).execute().use { response ->
            Log.d("MalFetcher", "Response code for status $status: ${response.code}")
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} fetching MAL list (status=$status)")
            }
            response.body?.string()
        } ?: throw IOException("Empty response body for status $status")

        Log.d("MalFetcher", "HTML length for status $status: ${html.length}")

        val doc = Jsoup.parse(html, Parser.htmlParser())
        val listTable = doc.select("table.list-table").first()
            ?: throw IOException("Could not find list table for status $status")

        val dataItems = listTable.attr("data-items")
        if (dataItems.isEmpty()) {
            Log.e("MalFetcher", "data-items attribute is empty for status $status")
            return@withContext emptyList()
        }

        json.decodeFromString<List<MalAnime>>(dataItems)
    }
}
