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
        val config = getCurrentWebDAVConfig()
        if (config == null) {
            Log.w(TAG, "getWebDAVMusic: 没有选中的 WebDAV 服务器配置")
            return emptyList()
        }
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
        return getCurrentWebDAVConfig()
            ?: WebDAVConfig(serverUrl = "", username = "", password = "")
    }

    suspend fun getWebDAVServices(): List<WebDAVConfig> {
        Log.d(TAG, "getWebDAVServices: 获取所有 WebDAV 服务器")
        return preferencesManager.webDAVServices.first()
    }

    suspend fun getCurrentWebDAVConfig(): WebDAVConfig? {
        Log.d(TAG, "getCurrentWebDAVConfig: 获取当前选中的 WebDAV 配置")
        return preferencesManager.getCurrentWebDAVConfig()
    }

    suspend fun addWebDAVService(config: WebDAVConfig) {
        Log.d(TAG, "addWebDAVService: 添加服务器 displayName=${config.displayName}")
        preferencesManager.addWebDAVService(config)
    }

    suspend fun updateWebDAVService(config: WebDAVConfig) {
        Log.d(TAG, "updateWebDAVService: 更新服务器 id=${config.id}")
        preferencesManager.updateWebDAVService(config)
    }

    suspend fun deleteWebDAVService(id: String) {
        Log.d(TAG, "deleteWebDAVService: 删除服务器 id=$id")
        preferencesManager.deleteWebDAVService(id)
    }

    suspend fun setCurrentWebDAVService(id: String) {
        Log.d(TAG, "setCurrentWebDAVService: 设置当前服务器 id=$id")
        preferencesManager.setCurrentWebDAVService(id)
    }

    suspend fun saveWebDAVConfig(config: WebDAVConfig) {
        Log.d(TAG, "saveWebDAVConfig: 保存配置 serverUrl=${config.serverUrl}")
        val services = getWebDAVServices()
        val existing = services.find { it.id == config.id }
        if (existing != null) {
            updateWebDAVService(config)
        } else {
            addWebDAVService(config)
            setCurrentWebDAVService(config.id)
        }
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