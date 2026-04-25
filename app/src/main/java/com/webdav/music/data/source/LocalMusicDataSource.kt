package com.webdav.music.data.source

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalMusicDataSource(private val context: Context) {

    companion object {
        private const val TAG = "LocalMusicDataSource"
        private val DEFAULT_MUSIC_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
    }

    suspend fun scanMusic(dirPath: String): List<MusicItem> = withContext(Dispatchers.IO) {
        val musicItems = mutableListOf<MusicItem>()
        val audioExtensions = listOf("mp3", "flac", "aac", "ogg", "wav", "m4a")

        Log.d(TAG, "scanMusic: 扫描目录 $dirPath")

        // 如果是 content:// URI，使用 DocumentFile
        if (dirPath.startsWith("content://")) {
            Log.d(TAG, "scanMusic: 使用 DocumentFile API 扫描")
            return@withContext scanMusicViaSAF(Uri.parse(dirPath), audioExtensions)
        }

        // 如果是标准的文件系统路径
        val musicDir = File(dirPath)
        if (!musicDir.exists() || !musicDir.isDirectory) {
            Log.w(TAG, "scanMusic: 目录不存在 $dirPath")
            // 尝试使用 SAF 解析
            return@withContext musicItems
        }

        scanDirectory(musicDir, audioExtensions, musicItems)

        Log.d(TAG, "scanMusic: 扫描到 ${musicItems.size} 首音乐")
        musicItems
    }

    private fun scanMusicViaSAF(treeUri: Uri, audioExtensions: List<String>): List<MusicItem> {
        val musicItems = mutableListOf<MusicItem>()

        try {
            val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            if (documentFile == null || !documentFile.exists()) {
                Log.w(TAG, "scanMusicViaSAF: DocumentFile 不存在")
                return musicItems
            }

            Log.d(TAG, "scanMusicViaSAF: 扫描 ${documentFile.name}")

            scanDocumentFile(documentFile, audioExtensions, musicItems, documentFile.name ?: "Unknown")

        } catch (e: Exception) {
            Log.e(TAG, "scanMusicViaSAF: 错误 ${e.message}", e)
        }

        Log.d(TAG, "scanMusicViaSAF: 扫描到 ${musicItems.size} 首音乐")
        return musicItems
    }

    private fun scanDocumentFile(
        dir: DocumentFile,
        audioExtensions: List<String>,
        musicItems: MutableList<MusicItem>,
        parentName: String
    ) {
        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                // 递归扫描子目录
                scanDocumentFile(file, audioExtensions, musicItems, file.name ?: dir.name ?: parentName)
            } else if (file.isFile) {
                val fileName = file.name ?: ""
                val extension = fileName.substringAfterLast(".", "").lowercase()
                if (extension in audioExtensions) {
                    val title = fileName.substringBeforeLast(".")
                    val artist = parentName

                    // 获取文件路径（如果可以获取的话）
                    val path = file.uri.toString()

                    musicItems.add(
                        MusicItem(
                            id = "local_${path.hashCode()}",
                            title = title,
                            artist = artist,
                            album = dir.name ?: parentName,
                            duration = 0,
                            path = path,
                            source = MusicSource.LOCAL,
                            isDownloaded = false
                        )
                    )
                    Log.d(TAG, "scanMusicViaSAF: 找到音频 ${file.name}")
                }
            }
        }
    }

    private fun scanDirectory(dir: File, audioExtensions: List<String>, musicItems: MutableList<MusicItem>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                scanDirectory(file, audioExtensions, musicItems)
            } else if (file.isFile) {
                val extension = file.extension.lowercase()
                if (extension in audioExtensions) {
                    val title = file.nameWithoutExtension
                    val artist = dir.name

                    musicItems.add(
                        MusicItem(
                            id = "local_${file.absolutePath.hashCode()}",
                            title = title,
                            artist = artist,
                            album = dir.name,
                            duration = 0,
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