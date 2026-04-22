package com.webdav.music.data.source

import android.content.Context
import android.util.Log
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import com.webdav.music.data.model.WebDAVConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.URLDecoder

class WebDAVDataSource(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val cacheDir: File
        get() = File(context.cacheDir, "music").also { it.mkdirs() }

    suspend fun testConnection(config: WebDAVConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "testConnection: 测试连接 ${config.serverUrl}")
            Log.d(TAG, "testConnection: 用户名 ${config.username}")

            val request = Request.Builder()
                .url(config.serverUrl)
                .method("PROPFIND", "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><resourcetype/></prop></propfind>".toRequestBody("application/xml".toMediaType()))
                .header("Authorization", Credentials.basic(config.username, config.password))
                .header("Depth", "0")
                .header("Content-Type", "application/xml")
                .build()

            Log.d(TAG, "testConnection: 发送请求")
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "testConnection: 响应码 ${response.code}")
                response.isSuccessful || response.code == 207
            }
        } catch (e: Exception) {
            Log.e(TAG, "testConnection: 连接失败 ${e.message}")
            e.printStackTrace()
            false
        }
    }

    suspend fun listMusic(config: WebDAVConfig): List<MusicItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "listMusic: 开始获取音乐列表")
            Log.d(TAG, "listMusic: 服务器地址 ${config.serverUrl}")

            val items = mutableListOf<MusicItem>()

            // First, get items from root
            Log.d(TAG, "listMusic: 从根目录获取")
            val rootItems = fetchMusicFromUrl(config.serverUrl, config, "根目录")
            Log.d(TAG, "listMusic: 根目录获取到 ${rootItems.size} 首歌曲")
            items.addAll(rootItems)

            // If root returned a "music" subdirectory, also fetch from there
            val musicDirUrl = if (config.serverUrl.endsWith("/")) {
                config.serverUrl.removeSuffix("/") + "/music"
            } else {
                config.serverUrl + "/music"
            }
            Log.d(TAG, "listMusic: /music/ 目录地址 $musicDirUrl")

            if (musicDirUrl != config.serverUrl) {
                Log.d(TAG, "listMusic: 从 /music/ 目录获取")
                val musicDirItems = fetchMusicFromUrl(musicDirUrl, config, "music目录")
                Log.d(TAG, "listMusic: /music/ 目录获取到 ${musicDirItems.size} 首歌曲")
                // Avoid duplicates by checking URL
                val existingUrls = items.map { it.path }.toSet()
                items.addAll(musicDirItems.filter { it.path !in existingUrls })
            }

            Log.d(TAG, "listMusic: 总共获取到 ${items.size} 首歌曲")
            items
        } catch (e: Exception) {
            Log.e(TAG, "listMusic: 失败 ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun fetchMusicFromUrl(url: String, config: WebDAVConfig, debugName: String): List<MusicItem> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "fetchMusicFromUrl [$debugName]: 请求 URL: $url")

            val request = Request.Builder()
                .url(url)
                .method("PROPFIND", null)
                .header("Authorization", Credentials.basic(config.username, config.password))
                .header("Depth", "1")
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "fetchMusicFromUrl [$debugName]: 响应码 ${response.code}")

                if (!response.isSuccessful) {
                    Log.e(TAG, "fetchMusicFromUrl [$debugName]: 请求失败，状态码 ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: ""
                Log.d(TAG, "fetchMusicFromUrl [$debugName]: 响应体长度 ${body.length}")

                parseWebDAVResponse(body, config, debugName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchMusicFromUrl [$debugName]: 异常 ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseWebDAVResponse(xml: String, config: WebDAVConfig, debugName: String): List<MusicItem> {
        val musicItems = mutableListOf<MusicItem>()
        val audioExtensions = listOf("mp3", "flac", "aac", "ogg", "wav", "m4a")
        val hrefPattern = "<d:href>([^<]+)</d:href>".toRegex(RegexOption.IGNORE_CASE)

        Log.d(TAG, "parseWebDAVResponse [$debugName]: 解析 ${xml.length} 字符")

        val matches = hrefPattern.findAll(xml).toList()
        Log.d(TAG, "parseWebDAVResponse [$debugName]: 找到 ${matches.count()} 个 href")

        // Also log all hrefs for debugging
        matches.forEach { match ->
            val href = match.groupValues[1]
            Log.d(TAG, "parseWebDAVResponse [$debugName]: href=$href")
        }

        hrefPattern.findAll(xml).forEach { hrefMatch ->
            val href = hrefMatch.groupValues[1]
            // URL decode the full href
            val decodedHref = URLDecoder.decode(href, "UTF-8")
            val fileName = decodedHref.substringAfterLast("/")
            val extension = fileName.substringAfterLast(".").lowercase()

            if (extension in audioExtensions) {
                // Build full URL correctly
                val baseUrl = config.serverUrl.trimEnd('/')
                val fullPath = decodedHref.trimStart('/')
                val url = "$baseUrl/$fullPath"

                Log.d(TAG, "parseWebDAVResponse [$debugName]: 找到音频文件: $fileName -> $url")

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
        Log.d(TAG, "parseWebDAVResponse [$debugName]: 解析到 ${musicItems.size} 首歌曲")
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
                val tempFile = File(cacheDir, "$fileName.tmp")
                response.body?.byteStream()?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                // Atomic rename
                tempFile.renameTo(file)
                musicItem.copy(
                    path = file.absolutePath,
                    isDownloaded = true
                )
            }
        } catch (e: Exception) {
            // Clean up temp file if it exists
            val fileName = cacheFileName(musicItem.path)
            val tempFile = File(cacheDir, "$fileName.tmp")
            tempFile.delete()
            null
        }
    }

    suspend fun getCachedMusic(): List<MusicItem> = withContext(Dispatchers.IO) {
        cacheDir.listFiles()
            ?.filter { it.isFile && it.extension == "audio" }
            ?.mapNotNull { file ->
                // Filename is the URL hash from cacheFileName()
                val urlHash = file.nameWithoutExtension
                MusicItem(
                    id = "webdav_$urlHash",
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

    private fun String.toRequestBody(mediaType: MediaType?) = object : RequestBody() {
        override fun contentType() = mediaType
        override fun writeTo(sink: okio.BufferedSink) {
            sink.writeUtf8(this@toRequestBody)
        }
    }

    companion object {
        private const val TAG = "WebDAVDataSource"
    }
}