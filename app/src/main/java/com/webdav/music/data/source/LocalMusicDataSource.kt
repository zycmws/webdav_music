package com.webdav.music.data.source

import android.content.Context
import android.os.Environment
import android.util.Log
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMusicDataSource(private val context: Context) {

    companion object {
        private const val TAG = "LocalMusicDataSource"
        // 默认扫描 /storage/emulated/0/Music 目录
        private val DEFAULT_MUSIC_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
    }

    suspend fun scanMusic(dirPath: String = DEFAULT_MUSIC_DIR): List<MusicItem> = withContext(Dispatchers.IO) {
        val musicItems = mutableListOf<MusicItem>()
        val audioExtensions = listOf("mp3", "flac", "aac", "ogg", "wav", "m4a")

        Log.d(TAG, "scanMusic: 扫描目录 $dirPath")

        val musicDir = File(dirPath)
        if (!musicDir.exists() || !musicDir.isDirectory) {
            Log.w(TAG, "scanMusic: 目录不存在 $dirPath")
            return@withContext musicItems
        }

        scanDirectory(musicDir, audioExtensions, musicItems)

        Log.d(TAG, "scanMusic: 扫描到 ${musicItems.size} 首音乐")
        musicItems
    }

    private fun scanDirectory(dir: File, audioExtensions: List<String>, musicItems: MutableList<MusicItem>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                // 递归扫描子目录
                scanDirectory(file, audioExtensions, musicItems)
            } else if (file.isFile) {
                val extension = file.extension.lowercase()
                if (extension in audioExtensions) {
                    val title = file.nameWithoutExtension
                    val artist = "本地音乐"
                    val album = dir.name

                    musicItems.add(
                        MusicItem(
                            id = "local_${file.absolutePath.hashCode()}",
                            title = title,
                            artist = artist,
                            album = album,
                            duration = 0, // 本地文件暂不获取时长
                            path = file.absolutePath,
                            source = MusicSource.LOCAL,
                            isDownloaded = false
                        )
                    )
                }
            }
        }
    }
}