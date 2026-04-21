package com.webdav.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.webdav.music.MainActivity
import com.webdav.music.R
import com.webdav.music.data.model.MusicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioPlayerService : Service() {

    private val binder = AudioPlayerBinder()
    private var player: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentMusic = MutableStateFlow<MusicItem?>(null)
    val currentMusic: StateFlow<MusicItem?> = _currentMusic

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    inner class AudioPlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        _duration.value = player?.duration ?: 0L
                    }
                }
            })
        }
    }

    fun playMusic(musicItem: MusicItem) {
        _currentMusic.value = musicItem
        player?.apply {
            setMediaItem(MediaItem.fromUri(musicItem.path))
            prepare()
            play()
        }
        startForeground(NOTIFICATION_ID, createNotification(musicItem))
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        if (player?.isPlaying == true) pause() else play()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun updateProgress() {
        _progress.value = player?.currentPosition ?: 0L
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(musicItem: MusicItem): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(musicItem.title)
            .setContentText(musicItem.artist)
            .setSmallIcon(R.drawable.ic_music_placeholder)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
    }
}