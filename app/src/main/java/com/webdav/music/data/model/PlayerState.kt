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