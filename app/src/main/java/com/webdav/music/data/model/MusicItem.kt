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