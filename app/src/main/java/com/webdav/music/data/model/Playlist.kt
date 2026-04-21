package com.webdav.music.data.model

data class Playlist(
    val id: String,
    val name: String,
    val items: List<MusicItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)