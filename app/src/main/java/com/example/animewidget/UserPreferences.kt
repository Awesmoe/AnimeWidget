package com.example.animewidget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

val Context.dataStore by preferencesDataStore(name = "user_prefs")
val USERNAME_KEY = stringPreferencesKey("mal_username")

private val USE_ENGLISH_TITLE = booleanPreferencesKey("use_english_title")
private val INCLUDE_PLAN_TO_WATCH = booleanPreferencesKey("include_plan_to_watch")

suspend fun saveUsername(context: Context, username: String) {
    context.dataStore.edit { prefs ->
        prefs[USERNAME_KEY] = username
    }
}

fun getUsername(context: Context): Flow<String?> {
    return context.dataStore.data.map { prefs ->
        prefs[USERNAME_KEY]
    }
}

suspend fun saveUseEnglishTitle(context: Context, useEnglish: Boolean) {
    context.dataStore.edit { prefs ->
        prefs[USE_ENGLISH_TITLE] = useEnglish
    }
}

fun getUseEnglishTitle(context: Context): Flow<Boolean> =
    context.dataStore.data.map { prefs ->
        prefs[USE_ENGLISH_TITLE] ?: true // default to true
    }

suspend fun saveIncludePlanToWatch(context: Context, include: Boolean) {
    context.dataStore.edit { prefs ->
        prefs[INCLUDE_PLAN_TO_WATCH] = include
    }
}

fun getIncludePlanToWatch(context: Context): Flow<Boolean> =
    context.dataStore.data.map { prefs ->
        prefs[INCLUDE_PLAN_TO_WATCH] ?: true // default to true (include plan to watch)
    }

private const val CACHE_PREFS = "anime_widget_cache"
private const val CACHE_KEY_DATA = "cached_anime_list"
private const val CACHE_KEY_TIMESTAMP = "cached_timestamp"

fun saveCachedAnimeList(context: Context, animeList: List<AnimeWithSchedule>) {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    val jsonStr = Json.encodeToString(animeList)
    prefs.edit()
        .putString(CACHE_KEY_DATA, jsonStr)
        .putLong(CACHE_KEY_TIMESTAMP, System.currentTimeMillis())
        .apply()
}

fun getCachedAnimeList(context: Context): List<AnimeWithSchedule>? {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(CACHE_KEY_DATA, null) ?: return null
    return try {
        Json.decodeFromString<List<AnimeWithSchedule>>(jsonStr)
    } catch (e: Exception) {
        null
    }
}
