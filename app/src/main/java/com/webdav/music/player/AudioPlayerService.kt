package com.webdav.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.webdav.music.MainActivity
import com.webdav.music.R
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.MusicSource
import com.webdav.music.data.model.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioPlayerService : Service() {

    private val binder = AudioPlayerBinder()
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentMusic = MutableStateFlow<MusicItem?>(null)
    val currentMusic: StateFlow<MusicItem?> = _currentMusic

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    // WebDAV credentials
    private var webDavUsername: String = ""
    private var webDavPassword: String = ""

    inner class AudioPlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
        // Register media button receiver
        val filter = android.content.IntentFilter().apply {
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
        }
        registerReceiver(mediaReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    fun setWebDAVCredentials(username: String, password: String) {
        webDavUsername = username
        webDavPassword = password
        // Release existing player and reinitialize with new credentials
        releasePlayer()
        initializePlayer()
    }

    private fun releasePlayer() {
        mediaSession?.run {
            player?.release()
            release()
        }
        player?.release()
        player = null
        mediaSession = null
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        val dataSourceFactory = createDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _duration.value = player?.duration ?: 0L
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error: ${error.message}")
                    }
                })
            }

        // Create MediaSession for lock screen controls and system integration
        mediaSession = MediaSession.Builder(this, player!!)
            .build()
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("WebDAVMusicPlayer")
            .setAllowCrossProtocolRedirects(true)

        if (webDavUsername.isNotEmpty()) {
            val auth = "Basic ${Base64.encodeToString("${webDavUsername}:${webDavPassword}".toByteArray(), Base64.NO_WRAP)}"
            httpDataSourceFactory.setDefaultRequestProperties(mapOf("Authorization" to auth))
        }

        return DefaultDataSource.Factory(this, httpDataSourceFactory)
    }

    fun playMusic(musicItem: MusicItem, repeatMode: RepeatMode = RepeatMode.OFF) {
        Log.d(TAG, "playMusic: ${musicItem.title}, source=${musicItem.source}")

        _currentMusic.value = musicItem

        // Create MediaItem with metadata for lock screen display
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(musicItem.path))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(musicItem.title)
                    .setArtist(musicItem.artist)
                    .setAlbumTitle(musicItem.album)
                    .build()
            )
            .build()

        player?.apply {
            setMediaItem(mediaItem)
            this.repeatMode = when (repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            }
            prepare()
            play()
        }

        startForeground(NOTIFICATION_ID, createNotification(musicItem))
    }

    fun setRepeatMode(mode: RepeatMode) {
        player?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
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
                "音乐播放",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "音乐播放控制"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
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

        // Action buttons for media notification
        val prevIntent = Intent(ACTION_PREVIOUS).apply { setPackage(packageName) }
        val prevPendingIntent = PendingIntent.getBroadcast(this, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE)
        val playPauseIntent = Intent(ACTION_PLAY_PAUSE).apply { setPackage(packageName) }
        val playPausePendingIntent = PendingIntent.getBroadcast(this, 1, playPauseIntent, PendingIntent.FLAG_IMMUTABLE)
        val nextIntent = Intent(ACTION_NEXT).apply { setPackage(packageName) }
        val nextPendingIntent = PendingIntent.getBroadcast(this, 2, nextIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(musicItem.title)
            .setContentText(musicItem.artist)
            .setSmallIcon(R.drawable.ic_music_placeholder)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionCompatToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(R.drawable.ic_music_placeholder, "Previous", prevPendingIntent)
            .addAction(R.drawable.ic_music_placeholder, "Play/Pause", playPausePendingIntent)
            .addAction(R.drawable.ic_music_placeholder, "Next", nextPendingIntent)
            .build()
    }

    private val mediaReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PREVIOUS -> {
                    Log.d(TAG, "mediaReceiver: ACTION_PREVIOUS")
                    sendBroadcast(Intent(ACTION_PREVIOUS).setPackage(packageName))
                }
                ACTION_PLAY_PAUSE -> {
                    Log.d(TAG, "mediaReceiver: ACTION_PLAY_PAUSE")
                    sendBroadcast(Intent(ACTION_PLAY_PAUSE).setPackage(packageName))
                }
                ACTION_NEXT -> {
                    Log.d(TAG, "mediaReceiver: ACTION_NEXT")
                    sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
                }
            }
        }
    }

    companion object {
        const val ACTION_PREVIOUS = "com.webdav.music.ACTION_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.webdav.music.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.webdav.music.ACTION_NEXT"
        private const val TAG = "AudioPlayerService"
        private const val CHANNEL_ID = "music_playback_channel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(mediaReceiver)
        } catch (e: Exception) {}
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player?.release()
        player = null
        super.onDestroy()
    }
}