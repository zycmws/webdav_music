package com.webdav.music.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "music_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val WEBDAV_SERVER_URL = stringPreferencesKey("webdaV_server_url")
        private val WEBDAV_USERNAME = stringPreferencesKey("webdaV_username")
        private val WEBDAV_PASSWORD = stringPreferencesKey("webdaV_password")
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val LAST_PLAYED_MUSIC_ID = stringPreferencesKey("last_played_music_id")
        private val LAST_PLAYED_PROGRESS = longPreferencesKey("last_played_progress")
        private val SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        private val REPEAT_MODE = stringPreferencesKey("repeat_mode")
    }

    val webDAVServerUrl: Flow<String> = context.dataStore.data.map { it[WEBDAV_SERVER_URL] ?: "" }
    val webDAVUsername: Flow<String> = context.dataStore.data.map { it[WEBDAV_USERNAME] ?: "" }
    val webDAVPassword: Flow<String> = context.dataStore.data.map { it[WEBDAV_PASSWORD] ?: "" }
    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { it[HAS_COMPLETED_ONBOARDING] ?: false }
    val lastPlayedMusicId: Flow<String?> = context.dataStore.data.map { it[LAST_PLAYED_MUSIC_ID] }
    val lastPlayedProgress: Flow<Long> = context.dataStore.data.map { it[LAST_PLAYED_PROGRESS] ?: 0L }
    val shuffleMode: Flow<Boolean> = context.dataStore.data.map { it[SHUFFLE_MODE] ?: false }
    val repeatMode: Flow<String> = context.dataStore.data.map { it[REPEAT_MODE] ?: "OFF" }

    suspend fun saveWebDAVConfig(serverUrl: String, username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[WEBDAV_SERVER_URL] = serverUrl
            prefs[WEBDAV_USERNAME] = username
            prefs[WEBDAV_PASSWORD] = password
        }
    }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[HAS_COMPLETED_ONBOARDING] = true }
    }

    suspend fun savePlaybackProgress(musicId: String, progress: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_PLAYED_MUSIC_ID] = musicId
            prefs[LAST_PLAYED_PROGRESS] = progress
        }
    }

    suspend fun saveShuffleMode(enabled: Boolean) {
        context.dataStore.edit { it[SHUFFLE_MODE] = enabled }
    }

    suspend fun saveRepeatMode(mode: String) {
        context.dataStore.edit { it[REPEAT_MODE] = mode }
    }
}