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
