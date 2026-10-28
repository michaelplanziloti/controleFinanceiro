package com.controlefinanceiro.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.dataStore by preferencesDataStore(name = "session")

/** Link and key of the Google Sheets script (see sheets/Codigo.gs); a blank link turns syncing off. */
data class SheetsConfig(val url: String = "", val key: String = "") {
    val enabled: Boolean get() = url.isNotBlank()
}

/** Keeps the logged-in user, the theme choice and each user's Google Sheets settings on the device. */
class SessionStore(private val context: Context) {
    private val userIdKey = longPreferencesKey("user_id")
    private val themeKey = stringPreferencesKey("theme_mode")

    val userId: Flow<Long?> = context.dataStore.data.map { it[userIdKey] }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun signIn(userId: Long) {
        context.dataStore.edit { it[userIdKey] = userId }
    }

    suspend fun signOut() {
        context.dataStore.edit { it.remove(userIdKey) }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    fun sheetsConfig(userId: Long): Flow<SheetsConfig> = context.dataStore.data.map {
        SheetsConfig(it[stringPreferencesKey("sheets_url_$userId")].orEmpty(), it[stringPreferencesKey("sheets_key_$userId")].orEmpty())
    }

    suspend fun setSheetsConfig(userId: Long, config: SheetsConfig) {
        context.dataStore.edit {
            it[stringPreferencesKey("sheets_url_$userId")] = config.url.trim()
            it[stringPreferencesKey("sheets_key_$userId")] = config.key.trim()
        }
    }

    /** Last sync result shown in Ajustes, e.g. "Out26 enviado às 14:32". */
    fun sheetsStatus(userId: Long): Flow<String?> = context.dataStore.data.map { it[stringPreferencesKey("sheets_status_$userId")] }

    suspend fun setSheetsStatus(userId: Long, status: String) {
        context.dataStore.edit { it[stringPreferencesKey("sheets_status_$userId")] = status }
    }
}
