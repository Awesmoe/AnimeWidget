import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "user_prefs")
val USERNAME_KEY = stringPreferencesKey("mal_username")

private val USE_ENGLISH_TITLE = booleanPreferencesKey("use_english_title")

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
