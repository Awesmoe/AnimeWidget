package com.awesmoe.animewidget

import android.content.Context
import android.util.Log
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
private val AIRING_NOTIFICATIONS_ENABLED = booleanPreferencesKey("airing_notifications_enabled")

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

suspend fun saveAiringNotificationsEnabled(context: Context, enabled: Boolean) {
    context.dataStore.edit { prefs ->
        prefs[AIRING_NOTIFICATIONS_ENABLED] = enabled
    }
}

fun getAiringNotificationsEnabled(context: Context): Flow<Boolean> =
    context.dataStore.data.map { prefs ->
        prefs[AIRING_NOTIFICATIONS_ENABLED] ?: false
    }

private const val CACHE_PREFS = "anime_widget_cache"
private const val CACHE_KEY_PREFIX = "cached_anime_list_"
private const val NOTIFICATION_STATE_KEY_PREFIX = "airing_notification_state_"

private fun cacheKeyFor(username: String) = "$CACHE_KEY_PREFIX${username.lowercase()}"
private fun notificationStateKeyFor(username: String) =
    "$NOTIFICATION_STATE_KEY_PREFIX${username.lowercase()}"

@kotlinx.serialization.Serializable
data class AiringNotificationState(
    val animeId: Int,
    val title: String,
    val episode: Int? = null,
    val airingAt: Long? = null
)

fun saveCachedAnimeList(context: Context, username: String, animeList: List<AnimeWithSchedule>) {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    val jsonStr = Json.encodeToString(animeList)
    prefs.edit()
        .putString(cacheKeyFor(username), jsonStr)
        .apply()
}

fun getCachedAnimeList(context: Context, username: String): List<AnimeWithSchedule>? {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(cacheKeyFor(username), null) ?: return null
    return try {
        Json.decodeFromString<List<AnimeWithSchedule>>(jsonStr)
    } catch (e: Exception) {
        Log.e("UserPreferences", "Failed to decode cached anime list", e)
        null
    }
}

fun clearAnimeCache(context: Context) {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    prefs.edit().clear().apply()
}

fun saveAiringNotificationStates(
    context: Context,
    username: String,
    states: List<AiringNotificationState>
) {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    val jsonStr = Json.encodeToString(states)
    prefs.edit()
        .putString(notificationStateKeyFor(username), jsonStr)
        .apply()
}

fun getAiringNotificationStates(
    context: Context,
    username: String
): Map<Int, AiringNotificationState> {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(notificationStateKeyFor(username), null) ?: return emptyMap()
    return try {
        Json.decodeFromString<List<AiringNotificationState>>(jsonStr).associateBy { it.animeId }
    } catch (e: Exception) {
        Log.e("UserPreferences", "Failed to decode notification state", e)
        emptyMap()
    }
}

fun clearAiringNotificationStates(context: Context) {
    val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
    val keysToRemove = prefs.all.keys.filter { it.startsWith(NOTIFICATION_STATE_KEY_PREFIX) }
    prefs.edit().apply {
        keysToRemove.forEach(::remove)
    }.apply()
}
