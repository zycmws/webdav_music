# 多 WebDAV 服务器配置与加密存储实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将单 WebDAV 服务器配置扩展为多服务器管理，支持添加/编辑/删除/切换，并使用 Android Keystore AES-256/GCM 加密存储密码。

**Architecture:** DataStore + JSON 序列化服务器列表，密码字段单独 AES-GCM 加密后 base64 编码存入 JSON；设置页改为服务器列表 + 详情页结构；主界面 WebDAV Tab 按当前选中服务器加载。

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, Android Keystore, kotlinx.serialization.json

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `data/local/PasswordEncryptor.kt` | 创建 | Android Keystore AES-256/GCM 加密/解密工具 |
| `data/model/WebDAVConfig.kt` | 修改 | 添加 `id` 和 `displayName` 字段 |
| `data/local/PreferencesManager.kt` | 修改 | 多服务器 JSON 存储、密码加密、向后兼容迁移 |
| `data/repository/MusicRepository.kt` | 修改 | 新增多服务器 CRUD，改造 `getWebDAVMusic()` |
| `MainViewModel.kt` | 修改 | 新增服务器列表 StateFlow，改造播放凭证获取 |
| `ui/screens/WebDAVServerDetailScreen.kt` | 创建 | 服务器新增/编辑详情页 |
| `ui/screens/SettingsScreen.kt` | 修改 | 改造为服务器列表页（RadioButton 切换 + 编辑入口） |
| `MainScreen.kt` | 修改 | WebDAV Tab 显示当前服务器名称和空状态提示 |

---

## Task 1: PasswordEncryptor（加密工具）

**Files:**
- Create: `app/src/main/java/com/webdav/music/data/local/PasswordEncryptor.kt`
- Test: `app/src/test/java/com/webdav/music/data/PasswordEncryptorTest.kt`

- [ ] **Step 1: 编写加密工具类**

```kotlin
package com.webdav.music.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PasswordEncryptor {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "webdav_password_key"
    private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        // iv + ciphertext 拼接后 base64
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encrypted: String): String {
        val combined = Base64.decode(encrypted, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }
}
```

- [ ] **Step 2: 编写单元测试**

```kotlin
package com.webdav.music.data

import com.webdav.music.data.local.PasswordEncryptor
import org.junit.Test
import kotlin.test.assertEquals

class PasswordEncryptorTest {

    @Test
    fun `encrypt and decrypt should return original plaintext`() {
        val original = "mySecretPassword123!"
        val encrypted = PasswordEncryptor.encrypt(original)
        val decrypted = PasswordEncryptor.decrypt(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun `encrypt should produce different ciphertext for same plaintext`() {
        val original = "samePassword"
        val encrypted1 = PasswordEncryptor.encrypt(original)
        val encrypted2 = PasswordEncryptor.encrypt(original)
        assert(encrypted1 != encrypted2) { "Same plaintext should produce different ciphertext due to random IV" }
    }

    @Test
    fun `encrypt and decrypt with empty string`() {
        val original = ""
        val encrypted = PasswordEncryptor.encrypt(original)
        val decrypted = PasswordEncryptor.decrypt(encrypted)
        assertEquals(original, decrypted)
    }
}
```

- [ ] **Step 3: 运行测试确认通过**

Run: `./gradlew test --tests "com.webdav.music.data.PasswordEncryptorTest"`
Expected: 3 tests PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/webdav/music/data/local/PasswordEncryptor.kt app/src/test/java/com/webdav/music/data/PasswordEncryptorTest.kt
git commit -m "feat: add PasswordEncryptor with Android Keystore AES-256/GCM"
```

---

## Task 2: 扩展 WebDAVConfig 数据类

**Files:**
- Modify: `app/src/main/java/com/webdav/music/data/model/WebDAVConfig.kt`

- [ ] **Step 1: 修改 WebDAVConfig，添加 id 和 displayName**

```kotlin
package com.webdav.music.data.model

import java.util.UUID

data class WebDAVConfig(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String = "服务器",
    val serverUrl: String,
    val username: String,
    val password: String
) {
    fun isValid(): Boolean = serverUrl.isNotBlank() && username.isNotBlank()
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/webdav/music/data/model/WebDAVConfig.kt
git commit -m "feat: add id and displayName to WebDAVConfig"
```

---

## Task 3: 改造 PreferencesManager

**Files:**
- Modify: `app/src/main/java/com/webdav/music/data/local/PreferencesManager.kt`
- Test: `app/src/test/java/com/webdav/music/data/PreferencesManagerTest.kt`（已有测试需更新或新增）

- [ ] **Step 1: 重写 PreferencesManager，支持多服务器 + 加密 + 迁移**

完整替换文件内容：

```kotlin
package com.webdav.music.data.local

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.webdav.music.data.model.WebDAVConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "music_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val TAG = "PreferencesManager"

        // Old keys (for migration)
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

    val webDAVServices: Flow<List<WebDAVConfig>> = context.dataStore.data.map { prefs ->
        migrateIfNeeded(prefs)
        val jsonStr = prefs[WEBDAV_SERVICES] ?: "[]"
        parseServicesJson(jsonStr)
    }

    val currentWebDAVId: Flow<String?> = context.dataStore.data.map { it[CURRENT_WEBDAV_ID] }

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { it[HAS_COMPLETED_ONBOARDING] ?: false }
    val lastPlayedMusicId: Flow<String?> = context.dataStore.data.map { it[LAST_PLAYED_MUSIC_ID] }
    val lastPlayedProgress: Flow<Long> = context.dataStore.data.map { it[LAST_PLAYED_PROGRESS] ?: 0L }
    val shuffleMode: Flow<Boolean> = context.dataStore.data.map { it[SHUFFLE_MODE] ?: false }
    val repeatMode: Flow<String> = context.dataStore.data.map { it[REPEAT_MODE] ?: "OFF" }
    val localMusicDir: Flow<String> = context.dataStore.data.map {
        it[LOCAL_MUSIC_DIR] ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
    }

    suspend fun addWebDAVService(config: WebDAVConfig) {
        context.dataStore.edit { prefs ->
            val services = parseServicesJson(prefs[WEBDAV_SERVICES] ?: "[]").toMutableList()
            services.add(config)
            prefs[WEBDAV_SERVICES] = servicesToJson(services)
        }
    }

    suspend fun updateWebDAVService(config: WebDAVConfig) {
        context.dataStore.edit { prefs ->
            val services = parseServicesJson(prefs[WEBDAV_SERVICES] ?: "[]").toMutableList()
            val index = services.indexOfFirst { it.id == config.id }
            if (index != -1) {
                services[index] = config
                prefs[WEBDAV_SERVICES] = servicesToJson(services)
            }
        }
    }

    suspend fun deleteWebDAVService(id: String) {
        context.dataStore.edit { prefs ->
            val services = parseServicesJson(prefs[WEBDAV_SERVICES] ?: "[]").toMutableList()
            services.removeAll { it.id == id }
            prefs[WEBDAV_SERVICES] = servicesToJson(services)
            // If deleted service was current, clear current id
            if (prefs[CURRENT_WEBDAV_ID] == id) {
                prefs.remove(CURRENT_WEBDAV_ID)
            }
        }
    }

    suspend fun setCurrentWebDAVService(id: String) {
        context.dataStore.edit { it[CURRENT_WEBDAV_ID] = id }
    }

    suspend fun getCurrentWebDAVConfig(): WebDAVConfig? {
        val prefs = context.dataStore.data.map { it }.first()
        val currentId = prefs[CURRENT_WEBDAV_ID] ?: return null
        val services = parseServicesJson(prefs[WEBDAV_SERVICES] ?: "[]")
        return services.find { it.id == currentId }
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

    suspend fun saveLocalMusicDir(dirPath: String) {
        context.dataStore.edit { prefs ->
            prefs[LOCAL_MUSIC_DIR] = dirPath
        }
    }

    // ---- Migration ----

    private suspend fun migrateIfNeeded(prefs: Preferences) {
        val oldUrl = prefs[OLD_WEBDAV_SERVER_URL]
        if (!oldUrl.isNullOrBlank()) {
            Log.d(TAG, "Migrating old WebDAV config to new format")
            val oldUsername = prefs[OLD_WEBDAV_USERNAME] ?: ""
            val oldPassword = prefs[OLD_WEBDAV_PASSWORD] ?: ""
            val newConfig = WebDAVConfig(
                id = UUID.randomUUID().toString(),
                displayName = "服务器 1",
                serverUrl = oldUrl,
                username = oldUsername,
                password = oldPassword
            )
            context.dataStore.edit { editPrefs ->
                val services = listOf(newConfig)
                editPrefs[WEBDAV_SERVICES] = servicesToJson(services)
                editPrefs[CURRENT_WEBDAV_ID] = newConfig.id
                // Clear old keys
                editPrefs.remove(OLD_WEBDAV_SERVER_URL)
                editPrefs.remove(OLD_WEBDAV_USERNAME)
                editPrefs.remove(OLD_WEBDAV_PASSWORD)
            }
            Log.d(TAG, "Migration completed")
        }
    }

    // ---- JSON Serialization ----

    private fun servicesToJson(services: List<WebDAVConfig>): String {
        val array = JSONArray()
        for (service in services) {
            val obj = JSONObject().apply {
                put("id", service.id)
                put("displayName", service.displayName)
                put("serverUrl", service.serverUrl)
                put("username", service.username)
                try {
                    put("password", PasswordEncryptor.encrypt(service.password))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to encrypt password for ${service.id}", e)
                    put("password", "")
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseServicesJson(jsonStr: String): List<WebDAVConfig> {
        val list = mutableListOf<WebDAVConfig>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val encryptedPassword = obj.optString("password", "")
                val decryptedPassword = if (encryptedPassword.isNotBlank()) {
                    try {
                        PasswordEncryptor.decrypt(encryptedPassword)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decrypt password for ${obj.optString("id")}, treating as plaintext", e)
                        encryptedPassword
                    }
                } else ""
                list.add(
                    WebDAVConfig(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        displayName = obj.optString("displayName", "服务器"),
                        serverUrl = obj.optString("serverUrl", ""),
                        username = obj.optString("username", ""),
                        password = decryptedPassword
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse services JSON", e)
        }
        return list
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/webdav/music/data/local/PreferencesManager.kt
git commit -m "feat: PreferencesManager supports multiple WebDAV servers with AES encryption and migration"
```

---

## Task 4: 改造 MusicRepository

**Files:**
- Modify: `app/src/main/java/com/webdav/music/data/repository/MusicRepository.kt`

- [ ] **Step 1: 重写 MusicRepository，添加多服务器 API**

```kotlin
package com.webdav.music.data.repository

import android.content.Context
import android.util.Log
import com.webdav.music.data.local.PreferencesManager
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.WebDAVConfig
import com.webdav.music.data.source.LocalMusicDataSource
import com.webdav.music.data.source.WebDAVDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val TAG = "MusicRepository"

class MusicRepository(context: Context) {

    private val localDataSource = LocalMusicDataSource(context)
    private val webDAVDataSource = WebDAVDataSource(context)
    val preferencesManager = PreferencesManager(context)

    suspend fun getLocalMusic(dirPath: String): List<MusicItem> {
        Log.d(TAG, "getLocalMusic: 开始扫描本地音乐目录 $dirPath")
        return localDataSource.scanMusic(dirPath)
    }

    suspend fun getWebDAVMusic(): List<MusicItem> {
        Log.d(TAG, "getWebDAVMusic: 开始获取 WebDAV 音乐")
        val config = getCurrentWebDAVConfig()
        Log.d(TAG, "getWebDAVMusic: currentConfig=${config?.displayName}")
        return if (config != null && config.isValid()) {
            Log.d(TAG, "getWebDAVMusic: 配置有效，开始 listMusic")
            webDAVDataSource.listMusic(config)
        } else {
            Log.w(TAG, "getWebDAVMusic: 无有效配置")
            emptyList()
        }
    }

    suspend fun getAllMusic(dirPath: String): List<MusicItem> {
        return getLocalMusic(dirPath) + getWebDAVMusic()
    }

    // ---- Multi-server WebDAV API ----

    suspend fun getWebDAVServices(): List<WebDAVConfig> {
        return preferencesManager.webDAVServices.first()
    }

    suspend fun getCurrentWebDAVConfig(): WebDAVConfig? {
        return preferencesManager.getCurrentWebDAVConfig()
    }

    suspend fun addWebDAVService(config: WebDAVConfig) {
        preferencesManager.addWebDAVService(config)
    }

    suspend fun updateWebDAVService(config: WebDAVConfig) {
        preferencesManager.updateWebDAVService(config)
    }

    suspend fun deleteWebDAVService(id: String) {
        preferencesManager.deleteWebDAVService(id)
    }

    suspend fun setCurrentWebDAVService(id: String) {
        preferencesManager.setCurrentWebDAVService(id)
    }

    suspend fun testWebDAVConnection(config: WebDAVConfig): Boolean {
        Log.d(TAG, "testWebDAVConnection: 测试连接 ${config.serverUrl}")
        return webDAVDataSource.testConnection(config)
    }

    // ---- Onboarding / Playback / Cache ----

    fun hasCompletedOnboarding(): Flow<Boolean> = preferencesManager.hasCompletedOnboarding

    suspend fun setOnboardingCompleted() {
        Log.d(TAG, "setOnboardingCompleted")
        preferencesManager.setOnboardingCompleted()
    }

    suspend fun savePlaybackProgress(musicId: String, progress: Long) {
        preferencesManager.savePlaybackProgress(musicId, progress)
    }

    fun getLastPlayedMusicId(): Flow<String?> = preferencesManager.lastPlayedMusicId
    fun getLastPlayedProgress(): Flow<Long> = preferencesManager.lastPlayedProgress

    suspend fun getCachedMusic(): List<MusicItem> {
        return webDAVDataSource.getCachedMusic()
    }

    suspend fun downloadMusic(musicItem: MusicItem, config: WebDAVConfig): MusicItem? {
        return webDAVDataSource.downloadMusic(musicItem, config)
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/webdav/music/data/repository/MusicRepository.kt
git commit -m "feat: MusicRepository supports multiple WebDAV servers CRUD"
```

---

## Task 5: 改造 MainViewModel

**Files:**
- Modify: `app/src/main/java/com/webdav/music/MainViewModel.kt`

- [ ] **Step 1: 修改 MainViewModel，添加服务器状态收集和改造播放凭证**

在文件中做以下修改（保留原有代码，仅在指定位置添加/修改）：

**A. 新增 StateFlow 字段（在 `_searchQuery` 之后添加）：**

```kotlin
    private val _webDAVServices = MutableStateFlow<List<WebDAVConfig>>(emptyList())
    val webDAVServices: StateFlow<List<WebDAVConfig>> = _webDAVServices.asStateFlow()

    private val _currentWebDAVId = MutableStateFlow<String?>(null)
    val currentWebDAVId: StateFlow<String?> = _currentWebDAVId.asStateFlow()

    private val _currentWebDAVName = MutableStateFlow<String?>(null)
    val currentWebDAVName: StateFlow<String?> = _currentWebDAVName.asStateFlow()
```

**B. 修改 `init()`，在现有收集之后添加新收集：**

```kotlin
    init {
        Log.d(TAG, "init")
        bindService()
        registerMediaActionReceiver()
        checkOnboarding()
        loadLocalMusic()
        collectWebDAVServices()
    }

    private fun collectWebDAVServices() {
        viewModelScope.launch {
            repository.preferencesManager.webDAVServices.collect {
                _webDAVServices.value = it
            }
        }
        viewModelScope.launch {
            repository.preferencesManager.currentWebDAVId.collect {
                _currentWebDAVId.value = it
            }
        }
        viewModelScope.launch {
            repository.preferencesManager.webDAVServices.combine(
                repository.preferencesManager.currentWebDAVId
            ) { services, currentId ->
                services.find { it.id == currentId }?.displayName
            }.collect {
                _currentWebDAVName.value = it
            }
        }
    }
```

**C. 修改 `playMusic()` 中 WebDAV 凭证设置部分：**

将原有：
```kotlin
        if (musicItem.source == com.webdav.music.data.model.MusicSource.WEBDAV) {
            runBlocking {
                val config = repository.getWebDAVConfig()
                Log.d(TAG, "playMusic: 设置 WebDAV 认证 ${config.username}")
                audioService?.setWebDAVCredentials(config.username, config.password)
            }
        }
```

替换为：
```kotlin
        if (musicItem.source == com.webdav.music.data.model.MusicSource.WEBDAV) {
            runBlocking {
                val config = repository.getCurrentWebDAVConfig()
                if (config != null) {
                    Log.d(TAG, "playMusic: 设置 WebDAV 认证 ${config.username}")
                    audioService?.setWebDAVCredentials(config.username, config.password)
                } else {
                    Log.w(TAG, "playMusic: 无当前 WebDAV 配置")
                }
            }
        }
```

**D. 修改 `playMusicDirect()` 中 WebDAV 凭证设置部分：**

将原有：
```kotlin
        if (musicItem.source == com.webdav.music.data.model.MusicSource.WEBDAV) {
            runBlocking {
                val config = repository.getWebDAVConfig()
                audioService?.setWebDAVCredentials(config.username, config.password)
            }
        }
```

替换为：
```kotlin
        if (musicItem.source == com.webdav.music.data.model.MusicSource.WEBDAV) {
            runBlocking {
                val config = repository.getCurrentWebDAVConfig()
                config?.let {
                    audioService?.setWebDAVCredentials(it.username, it.password)
                }
            }
        }
```

**E. 删除废弃的旧方法：** 删除 `saveWebDAVConfig()` 和 `testAndSaveWebDAVConfig()`（或保留但标记为废弃，因为 OnboardingScreen 可能仍在使用）。

如果 OnboardingScreen 仍调用这些方法，暂时保留但改为委托到新的多服务器 API：

```kotlin
    // 保留给 OnboardingScreen 使用，添加为当前选中的第一个服务器
    fun saveWebDAVConfig(config: WebDAVConfig) {
        viewModelScope.launch {
            repository.addWebDAVService(config)
            repository.setCurrentWebDAVService(config.id)
        }
    }

    fun testAndSaveWebDAVConfig(config: WebDAVConfig, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.testWebDAVConnection(config)
            if (success) {
                repository.addWebDAVService(config)
                repository.setCurrentWebDAVService(config.id)
            }
            onResult(success)
        }
    }
```

同时修改 OnboardingScreen 中对 `testAndSaveWebDAVConfig` 的调用，传入完整 WebDAVConfig（包含 displayName）。

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/webdav/music/MainViewModel.kt
git commit -m "feat: MainViewModel supports multiple WebDAV servers and current config lookup"
```

---

## Task 6: 新增 WebDAVServerDetailScreen

**Files:**
- Create: `app/src/main/java/com/webdav/music/ui/screens/WebDAVServerDetailScreen.kt`

- [ ] **Step 1: 编写详情页 Composable**

```kotlin
package com.webdav.music.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.webdav.music.data.model.WebDAVConfig
import com.webdav.music.data.repository.MusicRepository
import kotlinx.coroutines.launch

private const val TAG = "WebDAVServerDetailScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDAVServerDetailScreen(
    repository: MusicRepository,
    existingConfig: WebDAVConfig? = null,
    onBack: () -> Unit
) {
    val isEditing = existingConfig != null
    var displayName by remember { mutableStateOf(existingConfig?.displayName ?: "") }
    var serverUrl by remember { mutableStateOf(existingConfig?.serverUrl ?: "") }
    var username by remember { mutableStateOf(existingConfig?.username ?: "") }
    var password by remember { mutableStateOf(existingConfig?.password ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑服务器" else "添加服务器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isEditing) {
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; errorMessage = null },
                label = { Text("名称 *") },
                placeholder = { Text("如：家里 NAS") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; errorMessage = null },
                label = { Text("服务器地址") },
                placeholder = { Text("http://192.168.1.5:5005/") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (displayName.isBlank()) {
                        errorMessage = "请输入服务器名称"
                        return@Button
                    }
                    if (serverUrl.isBlank()) {
                        errorMessage = "请输入服务器地址"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val config = WebDAVConfig(
                            id = existingConfig?.id ?: java.util.UUID.randomUUID().toString(),
                            displayName = displayName.trim(),
                            serverUrl = serverUrl.trim(),
                            username = username.trim(),
                            password = password
                        )
                        Log.d(TAG, "测试连接: ${config.serverUrl}")
                        val success = repository.testWebDAVConnection(config)
                        if (success) {
                            if (isEditing) {
                                repository.updateWebDAVService(config)
                                Log.d(TAG, "更新服务器: ${config.displayName}")
                            } else {
                                repository.addWebDAVService(config)
                                Log.d(TAG, "添加服务器: ${config.displayName}")
                            }
                            onBack()
                        } else {
                            errorMessage = "连接测试失败，请检查地址和凭据"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("保存")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除服务器") },
            text = { Text("确定要删除「${existingConfig?.displayName}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            existingConfig?.id?.let {
                                repository.deleteWebDAVService(it)
                                Log.d(TAG, "删除服务器: $it")
                            }
                            showDeleteDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/webdav/music/ui/screens/WebDAVServerDetailScreen.kt
git commit -m "feat: add WebDAVServerDetailScreen for adding and editing servers"
```

---

## Task 7: 改造 SettingsScreen 为服务器列表页

**Files:**
- Modify: `app/src/main/java/com/webdav/music/ui/screens/SettingsScreen.kt`

- [ ] **Step 1: 重写 SettingsScreen 为列表页**

```kotlin
package com.webdav.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webdav.music.data.model.WebDAVConfig
import com.webdav.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SettingsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: MusicRepository,
    onBack: () -> Unit
) {
    var localMusicDir by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showDetailScreen by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<WebDAVConfig?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Collect WebDAV services and current ID from ViewModel or Repository
    var services by remember { mutableStateOf<List<WebDAVConfig>>(emptyList()) }
    var currentId by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistable permission: $e")
            }
            Log.d(TAG, "Selected folder URI: $it")
            localMusicDir = it.toString()
        }
    }

    // Load data
    LaunchedEffect(Unit) {
        Log.d(TAG, "加载配置")
        localMusicDir = repository.preferencesManager.localMusicDir.first()
        services = repository.getWebDAVServices()
        currentId = repository.preferencesManager.currentWebDAVId.first()
        isLoading = false
        Log.d(TAG, "加载完成: services=${services.size}")
    }

    // Refresh services when returning from detail
    LaunchedEffect(showDetailScreen) {
        if (!showDetailScreen) {
            services = repository.getWebDAVServices()
            currentId = repository.preferencesManager.currentWebDAVId.first()
        }
    }

    if (showDetailScreen) {
        WebDAVServerDetailScreen(
            repository = repository,
            existingConfig = editingConfig,
            onBack = {
                showDetailScreen = false
                editingConfig = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingConfig = null
                        showDetailScreen = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加服务器")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                // 本地音乐目录设置
                Text(
                    text = "本地音乐",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { folderPickerLauncher.launch(null) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "音乐目录",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (localMusicDir.startsWith("content://")) {
                                    try {
                                        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(localMusicDir))
                                        docId.substringAfter(":").substringAfterLast("/")
                                    } catch (e: Exception) { "已选择文件夹" }
                                } else if (localMusicDir.isNotEmpty()) {
                                    localMusicDir.substringAfterLast("/")
                                } else { "点击选择目录" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                // WebDAV 服务器列表
                Text(
                    text = "WebDAV 服务器",
                    style = MaterialTheme.typography.titleMedium
                )

                if (services.isEmpty()) {
                    Text(
                        text = "暂无服务器，点击右上角 + 添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    services.forEach { service ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = service.id == currentId,
                                    onClick = {
                                        scope.launch {
                                            repository.setCurrentWebDAVService(service.id)
                                            currentId = service.id
                                            Log.d(TAG, "切换到服务器: ${service.displayName}")
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = service.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = service.serverUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    editingConfig = service
                                    showDetailScreen = true
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑"
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            if (localMusicDir.isNotEmpty()) {
                                repository.preferencesManager.saveLocalMusicDir(localMusicDir)
                                Log.d(TAG, "保存本地音乐目录: $localMusicDir")
                            }
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("完成")
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/webdav/music/ui/screens/SettingsScreen.kt
git commit -m "feat: SettingsScreen redesigned as WebDAV server list with selection"
```

---

## Task 8: 改造 MainScreen WebDAV Tab

**Files:**
- Modify: `app/src/main/java/com/webdav/music/MainScreen.kt`

- [ ] **Step 1: 修改 WebDAV Tab 内容区域**

在 `MainScreen.kt` 中，将 WebDAV Tab 的内容区域替换为带当前服务器名称和空状态提示的版本。

找到这一段：
```kotlin
                    if (tracks.isEmpty()) {
                        Text(
                            text = if (selectedTab == 0) "未找到本地音乐" else "未找到 WebDAV 音乐",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
```

替换为：
```kotlin
                    if (tracks.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (selectedTab == 1 && currentWebDAVName == null) {
                                Text(
                                    text = "请前往设置添加并选择 WebDAV 服务器",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = if (selectedTab == 0) "未找到本地音乐" else "未找到 WebDAV 音乐",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
```

同时在 `val currentWebDAVName` 收集处添加（在 MainScreen composable 中）：

```kotlin
    val currentWebDAVName by viewModel.currentWebDAVName.collectAsState()
```

在 WebDAV Tab 列表顶部显示当前服务器名称，在 `TrackList` 之前添加：

```kotlin
                    if (selectedTab == 1 && currentWebDAVName != null) {
                        Text(
                            text = "当前：$currentWebDAVName",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/webdav/music/MainScreen.kt
git commit -m "feat: WebDAV Tab shows current server name and empty state hint"
```

---

## Task 9: 更新 OnboardingScreen 适配新 API

**Files:**
- Modify: `app/src/main/java/com/webdav/music/ui/OnboardingScreen.kt`

- [ ] **Step 1: 修改 OnboardingScreen 中的保存逻辑**

在 `OnboardingScreen.kt` 中，将测试成功后的保存逻辑改为使用新的多服务器 API：

找到这一段：
```kotlin
                        if (success) {
                            Log.d(TAG, "连接成功，保存配置")
                            repository.saveWebDAVConfig(config)
                            repository.setOnboardingCompleted()
                            Log.d(TAG, "跳转主页面")
                            onComplete()
```

替换为：
```kotlin
                        if (success) {
                            Log.d(TAG, "连接成功，保存配置")
                            val configWithName = config.copy(displayName = "服务器 1")
                            repository.addWebDAVService(configWithName)
                            repository.setCurrentWebDAVService(configWithName.id)
                            repository.setOnboardingCompleted()
                            Log.d(TAG, "跳转主页面")
                            onComplete()
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/webdav/music/ui/OnboardingScreen.kt
git commit -m "feat: OnboardingScreen adapted to multi-server API with displayName"
```

---

## Task 10: 最终集成编译与安装验证

**Files:** 全部

- [ ] **Step 1: 全量编译**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 安装到设备**

Run: `./gradlew installDebug`
Expected: BUILD SUCCESSFUL + 安装成功

- [ ] **Step 3: 手动验证清单**

1. 打开设置页，确认「WebDAV 服务器」区域显示为空状态提示
2. 点击右上角 +，添加第一个服务器（名称+地址+用户名+密码），保存成功
3. 返回设置页，确认服务器出现在列表中且 RadioButton 已选中
4. 点击 + 添加第二个服务器
5. 在列表中点击第二个服务器的 RadioButton，切换当前选中
6. 返回主界面 WebDAV Tab，确认列表顶部显示「当前：xxx」且歌曲列表已刷新
7. 点击编辑按钮修改服务器名称，保存后确认列表更新
8. 删除一个服务器（当前选中的），确认列表清空且 Tab 显示提示
9. 杀掉 App 重新打开，确认服务器列表和当前选中状态已持久化
10. 旧版本升级验证：先安装旧版单服务器版本 → 配置服务器 → 覆盖安装新版 → 确认配置已迁移且可正常播放

- [ ] **Step 4: 最终 Commit**

```bash
git log --oneline -5
```

确认所有 commit 整洁后，可选择推送：
```bash
git push origin master
```

---

## 自检清单

**Spec 覆盖检查：**

| Spec 需求 | 对应 Task |
|-----------|-----------|
| WebDAVConfig 扩展 id/displayName | Task 2 |
| DataStore JSON 多服务器存储 | Task 3 |
| AES-256/GCM 密码加密 | Task 1 + Task 3 |
| 向后兼容迁移旧配置 | Task 3 |
| Repository 多服务器 CRUD | Task 4 |
| ViewModel 新增 StateFlow | Task 5 |
| 播放凭证使用当前服务器 | Task 5 |
| 设置页列表 + 详情页 | Task 6 + Task 7 |
| 主界面 WebDAV Tab 显示 | Task 8 |
| Onboarding 适配 | Task 9 |

**Placeholder 扫描：** 无 TBD、TODO、"implement later" 等占位符。

**类型一致性检查：**
- `WebDAVConfig.id` 为 String，在所有任务中一致使用
- `PasswordEncryptor.encrypt/decrypt` 签名一致
- `PreferencesManager.webDAVServices` 返回 `Flow<List<WebDAVConfig>>` 在所有消费者中一致
