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