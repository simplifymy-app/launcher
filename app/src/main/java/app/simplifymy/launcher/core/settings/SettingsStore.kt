package app.simplifymy.launcher.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("settings")

val SettingsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    coerceInputValues = true
}

class SettingsStore(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    val settings: StateFlow<Settings> = store.data
        .map { prefs -> prefs[KEY]?.let(::decode) ?: Settings() }
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    suspend fun update(edit: (Settings) -> Settings) {
        store.edit { prefs ->
            val current = prefs[KEY]?.let(::decode) ?: Settings()
            prefs[KEY] = SettingsJson.encodeToString(StoredSettings, edit(current))
        }
    }

    suspend fun replace(settings: Settings) = update { settings }

    private fun decode(raw: String): Settings =
        runCatching { SettingsJson.decodeFromString(StoredSettings, raw) }.getOrDefault(Settings())

    private companion object {
        val KEY = stringPreferencesKey("settings")
    }
}
