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
import androidx.media3.common.ForwardingPlayer as BaseForwardingPlayer
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
    private var exoPlayer: ExoPlayer? = null
    private var player: CustomForwardingPlayer? = null
    private var mediaSession: MediaSession? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentMusic = MutableStateFlow<MusicItem?>(null)
    val currentMusic: StateFlow<MusicItem?> = _currentMusic

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _playbackEnded = MutableStateFlow(false)
    val playbackEnded: StateFlow<Boolean> = _playbackEnded

    // WebDAV credentials
    private var webDavUsername: String = ""
    private var webDavPassword: String = ""

    inner class AudioPlayerBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    /**
     * Wraps ExoPlayer to:
     * 1. Force COMMAND_SEEK_TO_NEXT/PREVIOUS to be available, so Fluid Cloud and system
     *    media controls show next/previous buttons.
     * 2. Smooth over track-switching transitions so the session appears continuously
     *    playing (prevents Fluid Cloud from dismissing during the gap).
     * Delegates actual seekToNext/seekToPrevious to broadcast for ViewModel handling.
     */
    @OptIn(UnstableApi::class)
    inner class CustomForwardingPlayer(basePlayer: ExoPlayer) : BaseForwardingPlayer(basePlayer) {

        /** True while we are between tracks (set before setMediaItem, cleared on STATE_READY). */
        var switchingTrack = false

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun getAvailableCommands(): Player.Commands {
            return Player.Commands.Builder()
                .addAll(super.getAvailableCommands())
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
        }

        override fun isPlaying(): Boolean {
            // During track switching, keep reporting as playing to prevent Fluid Cloud from dismissing
            if (switchingTrack && playWhenReady) return true
            return super.isPlaying()
        }

        override fun getPlaybackState(): Int {
            // During track switching, report BUFFERING instead of ENDED
            if (switchingTrack && super.getPlaybackState() == Player.STATE_ENDED) {
                return Player.STATE_BUFFERING
            }
            return super.getPlaybackState()
        }

        override fun seekToNext() {
            sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
        }

        override fun seekToNextMediaItem() {
            sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
        }

        override fun seekToPrevious() {
            sendBroadcast(Intent(ACTION_PREVIOUS).setPackage(packageName))
        }

        override fun seekToPreviousMediaItem() {
            sendBroadcast(Intent(ACTION_PREVIOUS).setPackage(packageName))
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializePlayer()
    }

    fun setWebDAVCredentials(username: String, password: String) {
        // Skip reinitialization if credentials haven't changed
        if (username == webDavUsername && password == webDavPassword) return
        webDavUsername = username
        webDavPassword = password
        // Release existing player and reinitialize with new credentials
        releasePlayer()
        initializePlayer()
    }

    private fun releasePlayer() {
        mediaSession?.run {
            release()
        }
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        player = null
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayer() {
        val dataSourceFactory = createDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _duration.value = exoPlayer?.duration ?: 0L
                            _playbackEnded.value = false
                            player?.switchingTrack = false
                        } else if (state == Player.STATE_ENDED && exoPlayer?.playWhenReady == true && player?.switchingTrack != true) {
                            _playbackEnded.value = true
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error: ${error.message}")
                    }
                })
            }

        player = CustomForwardingPlayer(exoPlayer!!)

        // Create MediaSession with CustomForwardingPlayer so system controls (Fluid Cloud, lock screen) see available commands
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
        _playbackEnded.value = false
        player?.switchingTrack = true

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

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            // RepeatMode.ONE is handled by ExoPlayer; all other modes by ViewModel
            this.repeatMode = when (repeatMode) {
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            prepare()
            play()
        }

        startForeground(NOTIFICATION_ID, createNotification(musicItem))
    }

    fun setRepeatMode(mode: RepeatMode) {
        // RepeatMode.ONE is handled by ExoPlayer; all other modes by ViewModel
        exoPlayer?.repeatMode = when (mode) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer?.isPlaying == true) pause() else play()
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun updateProgress() {
        _progress.value = exoPlayer?.currentPosition ?: 0L
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
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        player = null
        super.onDestroy()
    }
}