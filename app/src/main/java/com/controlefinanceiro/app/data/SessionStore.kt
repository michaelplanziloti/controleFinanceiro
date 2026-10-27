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

/** Keeps the logged-in user and the theme choice on the device. */
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
}
