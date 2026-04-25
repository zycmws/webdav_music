package com.webdav.music.data.local

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.webdav.music.data.model.WebDAVConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "music_prefs")

private const val TAG = "PreferencesManager"

class PreferencesManager(private val context: Context) {

    companion object {
        // Old keys (kept for migration)
        private val OLD_WEBDAV_SERVER_URL = stringPreferencesKey("webdaV_server_url")
        private val OLD_WEBDAV_USERNAME = stringPreferencesKey("webdaV_username")
        private val OLD_WEBDAV_PASSWORD = stringPreferencesKey("webdaV_password")

        // New keys
        private val WEBDAV_SERVICES = stringPreferencesKey("webdav_services")
        private val CURRENT_WEBDAV_ID = stringPreferencesKey("current_webdav_id")

        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val LAST_PLAYED_MUSIC_ID = stringPreferencesKey("last_played_music_id")
        private val LAST_PLAYED_PROGRESS = longPreferencesKey("last_played_progress")
        private val SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        private val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        private val LOCAL_MUSIC_DIR = stringPreferencesKey("local_music_dir")
    }

    private val migrationMutex = Mutex()
    private var migrationDone = false

    private suspend fun runMigration() {
        migrationMutex.withLock {
            if (migrationDone) return
            migrationDone = true

            context.dataStore.edit { prefs ->
                val oldUrl = prefs[OLD_WEBDAV_SERVER_URL]
                if (!oldUrl.isNullOrBlank()) {
                    val oldUser = prefs[OLD_WEBDAV_USERNAME] ?: ""
                    val oldPass = prefs[OLD_WEBDAV_PASSWORD] ?: ""

                    val id = UUID.randomUUID().toString()
                    val config = WebDAVConfig(
                        id = id,
                        displayName = "服务器 1",
                        serverUrl = oldUrl,
                        username = oldUser,
                        password = oldPass
                    )

                    val services = listOf(config)
                    prefs[WEBDAV_SERVICES] = servicesToJson(services)
                    prefs[CURRENT_WEBDAV_ID] = id

                    prefs.remove(OLD_WEBDAV_SERVER_URL)
                    prefs.remove(OLD_WEBDAV_USERNAME)
                    prefs.remove(OLD_WEBDAV_PASSWORD)

                    Log.i(TAG, "runMigration: 已将旧版单服务器配置迁移到多服务器格式")
                }
            }
        }
    }

    // region New multi-server API

    val webDAVServices: Flow<List<WebDAVConfig>> = flow {
        runMigration()
        emitAll(context.dataStore.data.map { parseServicesJson(it[WEBDAV_SERVICES] ?: "") })
    }

    val currentWebDAVId: Flow<String?> = context.dataStore.data.map { it[CURRENT_WEBDAV_ID] }

    suspend fun addWebDAVService(config: WebDAVConfig) {
        context.dataStore.edit { prefs ->
            val currentList = parseServicesJson(prefs[WEBDAV_SERVICES] ?: "").toMutableList()
            currentList.add(config)
            prefs[WEBDAV_SERVICES] = servicesToJson(currentList)
        }
    }

    suspend fun updateWebDAVService(config: WebDAVConfig) {
        context.dataStore.edit { prefs ->
            val currentList = parseServicesJson(prefs[WEBDAV_SERVICES] ?: "").toMutableList()
            val index = currentList.indexOfFirst { it.id == config.id }
            if (index != -1) {
                currentList[index] = config
                prefs[WEBDAV_SERVICES] = servicesToJson(currentList)
            }
        }
    }

    suspend fun deleteWebDAVService(id: String) {
        context.dataStore.edit { prefs ->
            val currentList = parseServicesJson(prefs[WEBDAV_SERVICES] ?: "").toMutableList()
            currentList.removeAll { it.id == id }
            prefs[WEBDAV_SERVICES] = servicesToJson(currentList)

            if (prefs[CURRENT_WEBDAV_ID] == id) {
                prefs.remove(CURRENT_WEBDAV_ID)
            }
        }
    }

    suspend fun setCurrentWebDAVService(id: String) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_WEBDAV_ID] = id
        }
    }

    suspend fun getCurrentWebDAVConfig(): WebDAVConfig? {
        val services = webDAVServices.first()
        val currentId = currentWebDAVId.first()
        return services.find { it.id == currentId }
    }

    // endregion

    // region Backward-compatible single-server wrappers (delegate to new API)

    val webDAVServerUrl: Flow<String> = combine(webDAVServices, currentWebDAVId) { services, id ->
        services.find { it.id == id }?.serverUrl ?: ""
    }

    val webDAVUsername: Flow<String> = combine(webDAVServices, currentWebDAVId) { services, id ->
        services.find { it.id == id }?.username ?: ""
    }

    val webDAVPassword: Flow<String> = combine(webDAVServices, currentWebDAVId) { services, id ->
        services.find { it.id == id }?.password ?: ""
    }

    suspend fun saveWebDAVConfig(serverUrl: String, username: String, password: String) {
        val current = getCurrentWebDAVConfig()
        if (current != null) {
            updateWebDAVService(
                current.copy(serverUrl = serverUrl, username = username, password = password)
            )
        } else {
            addWebDAVService(
                WebDAVConfig(
                    serverUrl = serverUrl,
                    username = username,
                    password = password,
                    displayName = "服务器 1"
                )
            )
        }
    }

    // endregion

    // region Non-WebDAV flows (unchanged)

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { it[HAS_COMPLETED_ONBOARDING] ?: false }
    val lastPlayedMusicId: Flow<String?> = context.dataStore.data.map { it[LAST_PLAYED_MUSIC_ID] }
    val lastPlayedProgress: Flow<Long> = context.dataStore.data.map { it[LAST_PLAYED_PROGRESS] ?: 0L }
    val shuffleMode: Flow<Boolean> = context.dataStore.data.map { it[SHUFFLE_MODE] ?: false }
    val repeatMode: Flow<String> = context.dataStore.data.map { it[REPEAT_MODE] ?: "OFF" }
    val localMusicDir: Flow<String> = context.dataStore.data.map {
        it[LOCAL_MUSIC_DIR] ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
    }

    // endregion

    // region Non-WebDAV save methods (unchanged)

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

    suspend fun saveLocalMusicDir(dirPath: String) {
        context.dataStore.edit { prefs ->
            prefs[LOCAL_MUSIC_DIR] = dirPath
        }
    }

    // endregion

    // region JSON Serialization

    private fun servicesToJson(services: List<WebDAVConfig>): String {
        val array = JSONArray()
        services.forEach { config ->
            val obj = JSONObject()
            obj.put("id", config.id)
            obj.put("displayName", config.displayName)
            obj.put("serverUrl", config.serverUrl)
            obj.put("username", config.username)
            val passwordToStore = if (config.password.isBlank()) {
                ""
            } else {
                try {
                    PasswordEncryptor.encrypt(config.password)
                } catch (e: Exception) {
                    Log.e(TAG, "加密密码失败", e)
                    ""
                }
            }
            obj.put("password", passwordToStore)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseServicesJson(jsonStr: String): List<WebDAVConfig> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val result = mutableListOf<WebDAVConfig>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val encryptedPassword = obj.optString("password", "")
                val password = if (encryptedPassword.isBlank()) {
                    ""
                } else {
                    try {
                        PasswordEncryptor.decrypt(encryptedPassword)
                    } catch (e: Exception) {
                        Log.e(TAG, "解密密码失败，当作明文处理", e)
                        encryptedPassword
                    }
                }
                result.add(
                    WebDAVConfig(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        displayName = obj.optString("displayName", "服务器"),
                        serverUrl = obj.optString("serverUrl", ""),
                        username = obj.optString("username", ""),
                        password = password
                    )
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "解析 WebDAV 服务 JSON 失败", e)
            emptyList()
        }
    }

    // endregion
}
