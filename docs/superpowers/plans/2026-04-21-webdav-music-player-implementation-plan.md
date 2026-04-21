# WebDAV Music Player - Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete Android music player supporting local music and WebDAV streaming with download caching.

**Architecture:** Clean layered architecture (UI → ViewModel → Repository → DataSource) with Media3 ExoPlayer for audio playback and foreground service for background playback.

**Tech Stack:** Kotlin, Jetpack Compose, Media3 ExoPlayer, OkHttp, DataStore

---

## File Structure

```
app/src/main/
├── java/com/webdav/music/
│   ├── MainActivity.kt                    # Single Activity, Compose entry point
│   ├── MainViewModel.kt                   # Central ViewModel for all UI state
│   ├── data/
│   │   ├── model/
│   │   │   ├── MusicItem.kt              # Music track data class
│   │   │   ├── MusicSource.kt            # Enum: LOCAL, WEBDAV
│   │   │   ├── WebDAVConfig.kt          # WebDAV server configuration
│   │   │   ├── PlayerState.kt           # Playback state (playing, progress, shuffle, repeat)
│   │   │   └── Playlist.kt              # Playlist data class
│   │   ├── source/
│   │   │   ├── LocalMusicDataSource.kt  # MediaStore scanner
│   │   │   └── WebDAVDataSource.kt       # WebDAV client (OkHttp-based)
│   │   ├── repository/
│   │   │   └── MusicRepository.kt       # Unified data access
│   │   └── local/
│   │       └── PreferencesManager.kt    # DataStore wrapper
│   ├── player/
│   │   ├── AudioPlayerService.kt        # Foreground service + MediaSession
│   │   └── DownloadManager.kt            # WebDAV download manager
│   └── ui/
│       ├── theme/
│       │   └── Theme.kt                  # Light theme + Material3 colors
│       ├── components/
│       │   ├── PlayerControls.kt         # Play/pause, prev/next, progress
│       │   ├── TrackList.kt             # Scrollable song list
│       │   └── SearchBar.kt              # Search input
│       └── screens/
│           ├── MainScreen.kt             # Main player + tabbed list
│           └── OnboardingScreen.kt       # First-run WebDAV setup
├── res/
│   ├── values/strings.xml
│   └── drawable/ic_music_placeholder.xml
└── AndroidManifest.xml
```

---

## Milestone 1: Project Setup and Basic UI

### Task 1: Initialize Gradle Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `app/build.gradle.kts`
- Create: `gradle.properties`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "WebDAVMusicPlayer"
include(":app")
```

- [ ] **Step 2: Create root build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

- [ ] **Step 3: Create app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.webdav.music"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")

    // OkHttp for WebDAV
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
```

- [ ] **Step 4: Create gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 5: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".MusicApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.WebDAVMusicPlayer">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.WebDAVMusicPlayer">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name=".player.AudioPlayerService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 6: Create res/values/strings.xml**

```xml
<resources>
    <string name="app_name">WebDAV Music</string>
</resources>
```

- [ ] **Step 7: Create res/values/themes.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.WebDAVMusicPlayer" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "M1: Initialize Gradle project structure"
```

---

### Task 2: Create Data Models

**Files:**
- Create: `app/src/main/java/com/webdav/music/data/model/MusicItem.kt`
- Create: `app/src/main/java/com/webdav/music/data/model/MusicSource.kt`
- Create: `app/src/main/java/com/webdav/music/data/model/WebDAVConfig.kt`
- Create: `app/src/main/java/com/webdav/music/data/model/PlayerState.kt`
- Create: `app/src/main/java/com/webdav/music/data/model/Playlist.kt`

- [ ] **Step 1: Create MusicSource.kt**

```kotlin
package com.webdav.music.data.model

enum class MusicSource {
    LOCAL,
    WEBDAV
}
```

- [ ] **Step 2: Create MusicItem.kt**

```kotlin
package com.webdav.music.data.model

data class MusicItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val source: MusicSource,
    val isDownloaded: Boolean = false
)
```

- [ ] **Step 3: Create WebDAVConfig.kt**

```kotlin
package com.webdav.music.data.model

data class WebDAVConfig(
    val serverUrl: String,
    val username: String,
    val password: String
) {
    fun isValid(): Boolean = serverUrl.isNotBlank() && username.isNotBlank()
}
```

- [ ] **Step 4: Create PlayerState.kt**

```kotlin
package com.webdav.music.data.model

data class PlayerState(
    val isPlaying: Boolean = false,
    val currentMusic: MusicItem? = null,
    val playlist: List<MusicItem> = emptyList(),
    val currentIndex: Int = 0,
    val progress: Long = 0,
    val duration: Long = 0,
    val shuffleMode: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

enum class RepeatMode {
    OFF,
    ONE,
    ALL
}
```

- [ ] **Step 5: Create Playlist.kt**

```kotlin
package com.webdav.music.data.model

data class Playlist(
    val id: String,
    val name: String,
    val items: List<MusicItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "M1: Add data models (MusicItem, PlayerState, Playlist, WebDAVConfig)"
```

---

### Task 3: Create Theme and Basic UI Structure

**Files:**
- Create: `app/src/main/java/com/webdav/music/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/webdav/music/MainActivity.kt`
- Create: `app/src/main/java/com/webdav/music/MusicApplication.kt`

- [ ] **Step 1: Create Theme.kt**

```kotlin
package com.webdav.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF1976D2),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFBBDEFB),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001E30),
    secondary = androidx.compose.ui.graphics.Color(0xFF545F70),
    onSecondary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color(0xFF1C1B1F),
)

@Composable
fun WebDAVMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColorScheme, content = content)
}
```

- [ ] **Step 2: Create MusicApplication.kt**

```kotlin
package com.webdav.music

import android.app.Application

class MusicApplication : Application()
```

- [ ] **Step 3: Create MainActivity.kt**

```kotlin
package com.webdav.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.webdav.music.ui.theme.WebDAVMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebDAVMusicTheme {
                MainScreen()
            }
        }
    }
}
```

- [ ] **Step 4: Create stub MainScreen.kt (placeholder for now)**

```kotlin
package com.webdav.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun MainScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("WebDAV Music Player")
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "M1: Add theme and basic MainActivity with Compose setup"
```

---

## Milestone 2: Local Music Scanning and Playback

### Task 4: PreferencesManager (DataStore)

**Files:**
- Create: `app/src/main/java/com/webdav/music/data/local/PreferencesManager.kt`

- [ ] **Step 1: Create PreferencesManager.kt**

```kotlin
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
        private val WEBDAV_SERVER_URL = stringPreferencesKey("webdav_server_url")
        private val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        private val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
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
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "M1: Add PreferencesManager for DataStore persistence"
```

---

### Task 5: LocalMusicDataSource

**Files:**
- Create: `app/src/main/java/com/webdav/music/data/source/LocalMusicDataSource.kt`

- [ ] **Step 1: Create LocalMusicDataSource.kt**

```kotlin
package com.webdav.music.data.source

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicDataSource(private val context: Context) {

    suspend fun scanMusic(): List<MusicItem> = withContext(Dispatchers.IO) {
        val musicItems = mutableListOf<MusicItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)
                val path = cursor.getString(dataColumn) ?: ""

                if (duration > 0) {
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    musicItems.add(
                        MusicItem(
                            id = "local_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            path = contentUri.toString(),
                            source = MusicSource.LOCAL,
                            isDownloaded = false
                        )
                    )
                }
            }
        }
        musicItems
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "M2: Add LocalMusicDataSource with MediaStore scanning"
```

---

### Task 6: AudioPlayerService

**Files:**
- Create: `app/src/main/java/com/webdav/music/player/AudioPlayerService.kt`

- [ ] **Step 1: Create AudioPlayerService.kt**

```kotlin
package com.webdav.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.webdav.music.MainActivity
import com.webdav.music.R
import com.webdav.music.data.model.MusicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioPlayerService : Service() {

    private val binder = AudioPlayerBinder()
    private var player: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentMusic = MutableStateFlow<MusicItem?>(null)
    val currentMusic: StateFlow<MusicItem?> = _currentMusic

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    inner class AudioPlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _duration.value = player?.duration ?: 0L
                    }
                }
            })
        }
    }

    fun playMusic(musicItem: MusicItem) {
        _currentMusic.value = musicItem
        player?.apply {
            setMediaItem(MediaItem.fromUri(musicItem.path))
            prepare()
            play()
        }
        startForeground(NOTIFICATION_ID, createNotification(musicItem))
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        if (player?.isPlaying == true) pause() else play()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun updateProgress() {
        _progress.value = player?.currentPosition ?: 0L
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(musicItem: MusicItem): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(musicItem.title)
            .setContentText(musicItem.artist)
            .setSmallIcon(R.drawable.ic_music_placeholder)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
    }
}
```

- [ ] **Step 2: Create ic_music_placeholder.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#757575"
        android:pathData="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z"/>
</vector>
```

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "M2: Add AudioPlayerService with ExoPlayer and foreground notification"
```

---

### Task 7: MusicRepository

**Files:**
- Create: `app/src/main/java/com/webdav/music/data/repository/MusicRepository.kt`

- [ ] **Step 1: Create MusicRepository.kt**

```kotlin
package com.webdav.music.data.repository

import android.content.Context
import com.webdav.music.data.local.PreferencesManager
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import com.webdav.music.data.model.WebDAVConfig
import com.webdav.music.data.source.LocalMusicDataSource
import com.webdav.music.data.source.WebDAVDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class MusicRepository(context: Context) {

    private val localDataSource = LocalMusicDataSource(context)
    private val webDAVDataSource = WebDAVDataSource(context)
    val preferencesManager = PreferencesManager(context)

    suspend fun getLocalMusic(): List<MusicItem> {
        return localDataSource.scanMusic()
    }

    suspend fun getWebDAVMusic(): List<MusicItem> {
        val config = getWebDAVConfig()
        return if (config.isValid()) {
            webDAVDataSource.listMusic(config)
        } else {
            emptyList()
        }
    }

    suspend fun getAllMusic(): List<MusicItem> {
        return getLocalMusic() + getWebDAVMusic()
    }

    suspend fun getWebDAVConfig(): WebDAVConfig {
        return WebDAVConfig(
            serverUrl = preferencesManager.webDAVServerUrl.first(),
            username = preferencesManager.webDAVUsername.first(),
            password = preferencesManager.webDAVPassword.first()
        )
    }

    suspend fun saveWebDAVConfig(config: WebDAVConfig) {
        preferencesManager.saveWebDAVConfig(config.serverUrl, config.username, config.password)
    }

    suspend fun testWebDAVConnection(config: WebDAVConfig): Boolean {
        return webDAVDataSource.testConnection(config)
    }

    fun hasCompletedOnboarding(): Flow<Boolean> = preferencesManager.hasCompletedOnboarding

    suspend fun setOnboardingCompleted() {
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

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "M2: Add MusicRepository as unified data access layer"
```

---

### Task 8: WebDAVDataSource (Basic)

**Files:**
- Create: `app/src/main/java/com/webdav/music/data/source/WebDAVDataSource.kt`

- [ ] **Step 1: Create WebDAVDataSource.kt**

```kotlin
package com.webdav.music.data.source

import android.content.Context
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import com.webdav.music.data.model.WebDAVConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest

class WebDAVDataSource(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    private val cacheDir: File
        get() = File(context.cacheDir, "music").also { it.mkdirs() }

    suspend fun testConnection(config: WebDAVConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(config.serverUrl)
                .method("PROPFIND", "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><resourcetype/></prop></propfind>".toRequestBody("application/xml".toMediaType()))
                .header("Authorization", Credentials.basic(config.username, config.password))
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun listMusic(config: WebDAVConfig): List<MusicItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(config.serverUrl)
                .method("PROPFIND", "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><displayname getcontentlength getcontenttype getlastmodified/></prop></propfind>".toRequestBody("application/xml".toMediaType()))
                .header("Authorization", Credentials.basic(config.username, config.password))
                .header("Depth", "1")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                parseWebDAVResponse(response.body?.string() ?: "", config)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseWebDAVResponse(xml: String, config: WebDAVConfig): List<MusicItem> {
        val musicItems = mutableListOf<MusicItem>()
        val audioExtensions = listOf("mp3", "flac", "aac", "ogg", "wav", "m4a")
        val hrefPattern = "<d:href>([^<]+)</d:href>".toRegex()
        val displayNamePattern = "<d:displayname>([^<]*)</d:displayname>".toRegex()

        hrefPattern.findAll(xml).forEach { hrefMatch ->
            val href = hrefMatch.groupValues[1]
            val fileName = href.substringAfterLast("/").substringAfterLast("%2F")
            val extension = fileName.substringAfterLast(".").lowercase()
            if (extension in audioExtensions) {
                val url = config.serverUrl.trimEnd('/') + "/" + href.substringAfterLast("/")
                musicItems.add(
                    MusicItem(
                        id = "webdav_${url.hashCode()}",
                        title = fileName.substringBeforeLast("."),
                        artist = "WebDAV",
                        album = "WebDAV",
                        duration = 0,
                        path = url,
                        source = MusicSource.WEBDAV,
                        isDownloaded = false
                    )
                )
            }
        }
        return musicItems
    }

    suspend fun downloadMusic(musicItem: MusicItem, config: WebDAVConfig): MusicItem? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(musicItem.path)
                .header("Authorization", Credentials.basic(config.username, config.password))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val fileName = cacheFileName(musicItem.path)
                val file = File(cacheDir, fileName)
                response.body?.byteStream()?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                musicItem.copy(
                    path = file.absolutePath,
                    isDownloaded = true
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCachedMusic(): List<MusicItem> = withContext(Dispatchers.IO) {
        cacheDir.listFiles()
            ?.filter { it.isFile && it.extension in listOf("mp3", "flac", "aac", "ogg", "wav", "m4a") }
            ?.map { file ->
                MusicItem(
                    id = "cached_${file.absolutePath.hashCode()}",
                    title = file.name.substringBeforeLast("."),
                    artist = "Cached",
                    album = "Cached",
                    duration = 0,
                    path = file.absolutePath,
                    source = MusicSource.WEBDAV,
                    isDownloaded = true
                )
            } ?: emptyList()
    }

    private fun cacheFileName(url: String): String {
        return url.hashCode().toString() + ".audio"
    }

    private fun String.toRequestBody(mediaType: MediaType) = object : RequestBody() {
        override fun contentType() = mediaType
        override fun writeTo(sink: okio.BufferedSink) {
            sink.writeUtf8(this@toRequestBody)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "M2: Add WebDAVDataSource with basic WebDAV client"
```

---

### Task 9: MainViewModel

**Files:**
- Create: `app/src/main/java/com/webdav/music/MainViewModel.kt`

- [ ] **Step 1: Create MainViewModel.kt**

```kotlin
package com.webdav.music

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import com.webdav.music.data.model.PlayerState
import com.webdav.music.data.model.RepeatMode
import com.webdav.music.data.model.WebDAVConfig
import com.webdav.music.data.repository.MusicRepository
import com.webdav.music.player.AudioPlayerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _localMusic = MutableStateFlow<List<MusicItem>>(emptyList())
    val localMusic: StateFlow<List<MusicItem>> = _localMusic.asStateFlow()

    private val _webDAVMusic = MutableStateFlow<List<MusicItem>>(emptyList())
    val webDAVMusic: StateFlow<List<MusicItem>> = _webDAVMusic.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(false)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private var audioService: AudioPlayerService? = null
    private var serviceBound = false
    private var progressUpdateJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.AudioPlayerBinder
            audioService = binder.getService()
            serviceBound = true
            syncServiceState()
            startProgressUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            serviceBound = false
        }
    }

    init {
        bindService()
        checkOnboarding()
        loadLocalMusic()
    }

    private fun bindService() {
        val intent = Intent(getApplication(), AudioPlayerService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkOnboarding() {
        viewModelScope.launch {
            repository.hasCompletedOnboarding().collect {
                _hasCompletedOnboarding.value = it
            }
        }
    }

    fun loadLocalMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _localMusic.value = repository.getLocalMusic()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load local music"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadWebDAVMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val cached = repository.getCachedMusic()
                val remote = repository.getWebDAVMusic()
                _webDAVMusic.value = remote.map { remoteItem ->
                    cached.find { it.id == remoteItem.id } ?: remoteItem
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load WebDAV music"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playMusic(musicItem: MusicItem, playlist: List<MusicItem>) {
        val index = playlist.indexOf(musicItem)
        _playerState.update {
            it.copy(
                currentMusic = musicItem,
                playlist = playlist,
                currentIndex = index,
                isPlaying = true,
                progress = 0,
                duration = musicItem.duration
            )
        }
        audioService?.playMusic(musicItem)
    }

    fun togglePlayPause() {
        val current = _playerState.value
        if (current.currentMusic == null) return

        if (current.isPlaying) {
            audioService?.pause()
        } else {
            audioService?.play()
        }
        _playerState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun playNext() {
        val state = _playerState.value
        if (state.playlist.isEmpty()) return
        val nextIndex = (state.currentIndex + 1) % state.playlist.size
        playMusic(state.playlist[nextIndex], state.playlist)
    }

    fun playPrevious() {
        val state = _playerState.value
        if (state.playlist.isEmpty()) return
        val prevIndex = if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.size - 1
        playMusic(state.playlist[prevIndex], state.playlist)
    }

    fun seekTo(position: Long) {
        audioService?.seekTo(position)
        _playerState.update { it.copy(progress = position) }
    }

    fun toggleShuffle() {
        val newMode = !_playerState.value.shuffleMode
        _playerState.update { it.copy(shuffleMode = newMode) }
        viewModelScope.launch {
            repository.preferencesManager.saveShuffleMode(newMode)
        }
    }

    fun toggleRepeat() {
        val currentMode = _playerState.value.repeatMode
        val newMode = when (currentMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playerState.update { it.copy(repeatMode = newMode) }
        viewModelScope.launch {
            repository.preferencesManager.saveRepeatMode(newMode.name)
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        if (index == 1) {
            loadWebDAVMusic()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredLocalMusic(): List<MusicItem> {
        val query = _searchQuery.value.lowercase()
        return if (query.isEmpty()) _localMusic.value
        else _localMusic.value.filter {
            it.title.lowercase().contains(query) ||
            it.artist.lowercase().contains(query) ||
            it.album.lowercase().contains(query)
        }
    }

    fun getFilteredWebDAVMusic(): List<MusicItem> {
        val query = _searchQuery.value.lowercase()
        return if (query.isEmpty()) _webDAVMusic.value
        else _webDAVMusic.value.filter {
            it.title.lowercase().contains(query) ||
            it.artist.lowercase().contains(query) ||
            it.album.lowercase().contains(query)
        }
    }

    fun setOnboardingCompleted() {
        viewModelScope.launch {
            repository.setOnboardingCompleted()
            _hasCompletedOnboarding.value = true
        }
    }

    fun saveWebDAVConfig(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            repository.saveWebDAVConfig(WebDAVConfig(serverUrl, username, password))
        }
    }

    fun testAndSaveWebDAVConfig(serverUrl: String, username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val config = WebDAVConfig(serverUrl, username, password)
            val success = repository.testWebDAVConnection(config)
            if (success) {
                repository.saveWebDAVConfig(config)
            }
            onResult(success)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun syncServiceState() {
        viewModelScope.launch {
            audioService?.isPlaying?.collect { playing ->
                _playerState.update { it.copy(isPlaying = playing) }
            }
        }
        viewModelScope.launch {
            audioService?.currentMusic?.collect { music ->
                if (music != null && music != _playerState.value.currentMusic) {
                    _playerState.update { it.copy(currentMusic = music) }
                }
            }
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                audioService?.updateProgress()
                val progress = audioService?.progress?.value ?: 0L
                val duration = audioService?.duration?.value ?: 0L
                _playerState.update { it.copy(progress = progress, duration = duration) }

                val current = _playerState.value.currentMusic
                if (current != null) {
                    repository.savePlaybackProgress(current.id, progress)
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        progressUpdateJob?.cancel()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        super.onCleared()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "M2: Add MainViewModel with playback state management"
```

---

## Milestone 3: UI Components and Screens

### Task 10: UI Components

**Files:**
- Create: `app/src/main/java/com/webdav/music/ui/components/PlayerControls.kt`
- Create: `app/src/main/java/com/webdav/music/ui/components/TrackList.kt`
- Create: `app/src/main/java/com/webdav/music/ui/components/SearchBar.kt`

- [ ] **Step 1: Create PlayerControls.kt**

```kotlin
package com.webdav.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.webdav.music.data.model.RepeatMode

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    shuffleMode: Boolean,
    repeatMode: RepeatMode,
    progress: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Progress Slider
        Slider(
            value = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f,
            onValueChange = { onSeek((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )

        // Time Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(progress),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffleToggle) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(onClick = onRepeatToggle) {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = when (repeatMode) {
                        RepeatMode.OFF -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
```

- [ ] **Step 2: Create TrackList.kt**

```kotlin
package com.webdav.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webdav.music.data.model.MusicItem

@Composable
fun TrackList(
    tracks: List<MusicItem>,
    currentTrackId: String?,
    onTrackClick: (MusicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        items(tracks, key = { it.id }) { track ->
            TrackItem(
                track = track,
                isPlaying = track.id == currentTrackId,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

@Composable
private fun TrackItem(
    track: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (track.isDownloaded) {
            Text(
                text = "Cached",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

- [ ] **Step 3: Create SearchBar.kt**

```kotlin
package com.webdav.music.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search music..."
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search"
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear"
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors()
    )
}
```

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "M3: Add UI components (PlayerControls, TrackList, SearchBar)"
```

---

### Task 11: MainScreen

**Files:**
- Modify: `app/src/main/java/com/webdav/music/MainScreen.kt` (replace stub)

- [ ] **Step 1: Replace MainScreen.kt**

```kotlin
package com.webdav.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webdav.music.ui.components.PlayerControls
import com.webdav.music.ui.components.SearchBar
import com.webdav.music.ui.components.TrackList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val localMusic by viewModel.localMusic.collectAsState()
    val webDAVMusic by viewModel.webDAVMusic.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()

    var showOnboarding by remember { mutableStateOf(!hasCompletedOnboarding) }

    LaunchedEffect(hasCompletedOnboarding) {
        showOnboarding = !hasCompletedOnboarding
    }

    if (showOnboarding) {
        OnboardingScreen(
            onComplete = { serverUrl, username, password ->
                viewModel.testAndSaveWebDAVConfig(serverUrl, username, password) { success ->
                    if (success) {
                        viewModel.setOnboardingCompleted()
                        showOnboarding = false
                    }
                }
            },
            onSkip = {
                viewModel.setOnboardingCompleted()
                showOnboarding = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Music") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Now Playing Section
            playerState.currentMusic?.let { currentMusic ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Album Art Placeholder
                    Surface(
                        modifier = Modifier.size(160.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = currentMusic.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = currentMusic.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PlayerControls(
                        isPlaying = playerState.isPlaying,
                        shuffleMode = playerState.shuffleMode,
                        repeatMode = playerState.repeatMode,
                        progress = playerState.progress,
                        duration = playerState.duration,
                        onPlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::playNext,
                        onPrevious = viewModel::playPrevious,
                        onSeek = viewModel::seekTo,
                        onShuffleToggle = viewModel::toggleShuffle,
                        onRepeatToggle = viewModel::toggleRepeat
                    )
                }
            }

            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Local") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("WebDAV") },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) }
                )
            }

            // Track List
            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val tracks = if (selectedTab == 0) {
                        viewModel.getFilteredLocalMusic()
                    } else {
                        viewModel.getFilteredWebDAVMusic()
                    }

                    if (tracks.isEmpty()) {
                        Text(
                            text = if (selectedTab == 0) "No local music found" else "No WebDAV music found",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        TrackList(
                            tracks = tracks,
                            currentTrackId = playerState.currentMusic?.id,
                            onTrackClick = { track ->
                                viewModel.playMusic(track, tracks)
                            }
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "M3: Implement MainScreen with player and track list"
```

---

### Task 12: OnboardingScreen

**Files:**
- Create: `app/src/main/java/com/webdav/music/ui/screens/OnboardingScreen.kt`

- [ ] **Step 1: Create OnboardingScreen.kt**

```kotlin
package com.webdav.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    onComplete: (serverUrl: String, username: String, password: String) -> Unit,
    onSkip: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WebDAV Music",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configure your WebDAV server",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it; errorMessage = null },
            label = { Text("Server URL") },
            placeholder = { Text("https://example.com/webdav/") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorMessage = null },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (serverUrl.isBlank()) {
                    errorMessage = "Server URL is required"
                    return@Button
                }
                isLoading = true
                onComplete(serverUrl, username, password)
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
                Text("Connect & Start")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onSkip,
            enabled = !isLoading
        ) {
            Text("Skip, use local music only")
        }
    }
}
```

- [ ] **Step 2: Fix the typo in OnboardingScreen.kt**

```kotlin
    var isLoading by remember { mutableStateOf(false) }  // was: false }
```

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "M3: Add OnboardingScreen for WebDAV configuration"
```

---

## Milestone 4: Polish and Error Handling

### Task 13: Error Handling and Edge Cases

**Files:**
- Modify: `app/src/main/java/com/webdav/music/MainScreen.kt` (add error handling)
- Modify: `app/src/main/java/com/webdav/music/MainViewModel.kt` (improve error handling)

- [ ] **Step 1: Add error snackbar to MainScreen.kt**

```kotlin
// Add to MainScreen composable, inside Scaffold:
val snackbarHostState = remember { SnackbarHostState() }
val errorMessage by viewModel.errorMessage.collectAsState()

LaunchedEffect(errorMessage) {
    errorMessage?.let {
        snackbarHostState.showSnackbar(it)
        viewModel.clearError()
    }
}

Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    // ... rest
)
```

- [ ] **Step 2: Add download functionality to track item**

```kotlin
// In TrackList.kt, add download button:
@Composable
private fun TrackItem(
    track: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null  // Add this parameter
) {
    // Add download icon button in the trailing section
    if (track.source == MusicSource.WEBDAV && !track.isDownloaded && onDownload != null) {
        IconButton(onClick = onDownload) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "M4: Add error handling and download functionality"
```

---

## Verification

After all milestones complete, verify the app builds:

```bash
cd /home/zyc/workspace/webdav_music
./gradlew assembleDebug
```

Expected: `app/build/outputs/apk/debug/app-debug.apk` generated successfully.

---

## Notes for Engineer

- Media3 ExoPlayer handles both local files and remote URLs transparently
- WebDAV authentication uses Basic Auth via OkHttp Credentials.basic()
- Foreground Service with notification keeps playback alive in background
- DataStore persists playback progress across app restarts
- Search is case-insensitive and searches title, artist, and album fields

---

*Plan created: 2026-04-21*
*Total tasks: 13*
