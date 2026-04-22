package com.webdav.music.data.repository

import android.content.Context
import android.util.Log
import com.webdav.music.data.local.PreferencesManager
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
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
        val config = getWebDAVConfig()
        Log.d(TAG, "getWebDAVMusic: config.serverUrl=${config.serverUrl}")
        return if (config.isValid()) {
            Log.d(TAG, "getWebDAVMusic: 配置有效，开始 listMusic")
            webDAVDataSource.listMusic(config)
        } else {
            Log.w(TAG, "getWebDAVMusic: 配置无效")
            emptyList()
        }
    }

    suspend fun getAllMusic(dirPath: String): List<MusicItem> {
        return getLocalMusic(dirPath) + getWebDAVMusic()
    }

    suspend fun getWebDAVConfig(): WebDAVConfig {
        return WebDAVConfig(
            serverUrl = preferencesManager.webDAVServerUrl.first(),
            username = preferencesManager.webDAVUsername.first(),
            password = preferencesManager.webDAVPassword.first()
        )
    }

    suspend fun saveWebDAVConfig(config: WebDAVConfig) {
        Log.d(TAG, "saveWebDAVConfig: 保存配置 serverUrl=${config.serverUrl}")
        preferencesManager.saveWebDAVConfig(config.serverUrl, config.username, config.password)
    }

    suspend fun testWebDAVConnection(config: WebDAVConfig): Boolean {
        Log.d(TAG, "testWebDAVConnection: 测试连接 ${config.serverUrl}")
        return webDAVDataSource.testConnection(config)
    }

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