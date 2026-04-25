package com.webdav.music

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webdav.music.data.model.MusicItem
import com.webdav.music.data.model.PlayerState
import com.webdav.music.data.model.RepeatMode
import com.webdav.music.data.model.WebDAVConfig
import com.webdav.music.data.repository.MusicRepository
import com.webdav.music.player.AudioPlayerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "MainViewModel"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _localMusic = MutableStateFlow<List<MusicItem>>(emptyList())
    val localMusic: StateFlow<List<MusicItem>> = _localMusic.asStateFlow()

    private val _webDAVMusic = MutableStateFlow<List<MusicItem>>(emptyList())
    val webDAVMusic: StateFlow<List<MusicItem>> = _webDAVMusic.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(false)
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private var audioService: AudioPlayerService? = null
    private var serviceBound = false
    private var progressUpdateJob: Job? = null

    private val mediaActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioPlayerService.ACTION_NEXT -> playNext()
                AudioPlayerService.ACTION_PREVIOUS -> playPrevious()
                AudioPlayerService.ACTION_PLAY_PAUSE -> togglePlayPause()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected")
            val binder = service as AudioPlayerService.AudioPlayerBinder
            audioService = binder.getService()
            serviceBound = true
            syncServiceState()
            startProgressUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
            audioService = null
            serviceBound = false
        }
    }

    init {
        Log.d(TAG, "init")
        bindService()
        registerMediaActionReceiver()
        checkOnboarding()
        loadLocalMusic()
    }

    private fun bindService() {
        Log.d(TAG, "bindService")
        val intent = Intent(getApplication(), AudioPlayerService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun registerMediaActionReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioPlayerService.ACTION_NEXT)
            addAction(AudioPlayerService.ACTION_PREVIOUS)
            addAction(AudioPlayerService.ACTION_PLAY_PAUSE)
        }
        getApplication<Application>().registerReceiver(mediaActionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun checkOnboarding() {
        Log.d(TAG, "checkOnboarding")
        viewModelScope.launch {
            repository.hasCompletedOnboarding().collect {
                Log.d(TAG, "hasCompletedOnboarding: $it")
                _hasCompletedOnboarding.value = it
            }
        }
    }

    fun loadLocalMusic() {
        Log.d(TAG, "loadLocalMusic")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dir = repository.preferencesManager.localMusicDir.first()
                Log.d(TAG, "loadLocalMusic: 使用目录 $dir")
                _localMusic.value = repository.getLocalMusic(dir)
                Log.d(TAG, "loadLocalMusic: 加载了 ${_localMusic.value.size} 首")
            } catch (e: Exception) {
                Log.e(TAG, "loadLocalMusic: 失败 ${e.message}")
                _errorMessage.value = "加载本地音乐失败"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadWebDAVMusic() {
        Log.d(TAG, "loadWebDAVMusic")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "loadWebDAVMusic: 开始获取缓存")
                val cached = repository.getCachedMusic()
                Log.d(TAG, "loadWebDAVMusic: 缓存数量 ${cached.size}")

                Log.d(TAG, "loadWebDAVMusic: 开始获取远程音乐")
                val remote = repository.getWebDAVMusic()
                Log.d(TAG, "loadWebDAVMusic: 远程音乐数量 ${remote.size}")

                _webDAVMusic.value = remote.map { remoteItem ->
                    cached.find { it.id == remoteItem.id } ?: remoteItem
                }
                Log.d(TAG, "loadWebDAVMusic: 最终列表 ${_webDAVMusic.value.size} 首")
            } catch (e: Exception) {
                Log.e(TAG, "loadWebDAVMusic: 失败 ${e.message}")
                _errorMessage.value = "加载 WebDAV 音乐失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playMusic(musicItem: MusicItem, playlist: List<MusicItem>) {
        Log.d(TAG, "playMusic: ${musicItem.title}")

        // Set WebDAV credentials if playing WebDAV music (synchronous for immediate use)
        if (musicItem.source == com.webdav.music.data.model.MusicSource.WEBDAV) {
            runBlocking {
                val config = repository.getWebDAVConfig()
                Log.d(TAG, "playMusic: 设置 WebDAV 认证 ${config.username}")
                audioService?.setWebDAVCredentials(config.username, config.password)
            }
        }

        val originalIndex = playlist.indexOf(musicItem)

        if (_playerState.value.shuffleMode) {
            // Check if we can reuse the existing shuffled playlist (same source playlist & contains the item)
            val existingShuffled = _playerState.value.shuffledPlaylist
            val canReuse = existingShuffled.size == playlist.size &&
                existingShuffled.containsAll(playlist) &&
                existingShuffled.contains(musicItem)

            val shuffled = if (canReuse) existingShuffled else playlist.shuffled()
            val sIndex = shuffled.indexOf(musicItem)
            _playerState.update {
                it.copy(
                    currentMusic = musicItem,
                    playlist = playlist,
                    currentIndex = originalIndex,
                    isPlaying = true,
                    progress = 0,
                    duration = musicItem.duration,
                    shuffledPlaylist = shuffled,
                    shuffledIndex = sIndex
                )
            }
        } else {
            _playerState.update {
                it.copy(
                    currentMusic = musicItem,
                    playlist = playlist,
                    currentIndex = originalIndex,
                    isPlaying = true,
                    progress = 0,
                    duration = musicItem.duration,
                    shuffledPlaylist = emptyList(),
                    shuffledIndex = 0
                )
            }
        }
        audioService?.playMusic(musicItem, _playerState.value.repeatMode)
    }

    fun togglePlayPause() {
        Log.d(TAG, "togglePlayPause")
        if (_playerState.value.currentMusic == null) return

        val currentlyPlaying = _playerState.value.isPlaying
        if (currentlyPlaying) {
            audioService?.pause()
        } else {
            audioService?.play()
        }
        _playerState.update { it.copy(isPlaying = !currentlyPlaying) }
    }

    fun playNext() {
        val state = _playerState.value
        if (state.playlist.isEmpty()) return
        if (state.shuffleMode && state.shuffledPlaylist.isNotEmpty()) {
            val nextIndex = (state.shuffledIndex + 1) % state.shuffledPlaylist.size
            _playerState.update { it.copy(shuffledIndex = nextIndex) }
            val nextItem = state.shuffledPlaylist[nextIndex]
            Log.d(TAG, "playNext (shuffle): shuffledIndex=$nextIndex, nextTrack=${nextItem.title}")
            playMusicDirect(nextItem, state)
        } else {
            val nextIndex = (state.currentIndex + 1) % state.playlist.size
            Log.d(TAG, "playNext: nextIndex=$nextIndex, nextTrack=${state.playlist[nextIndex].title}")
            playMusic(state.playlist[nextIndex], state.playlist)
        }
    }

    fun playPrevious() {
        val state = _playerState.value
        if (state.playlist.isEmpty()) return
        if (state.shuffleMode && state.shuffledPlaylist.isNotEmpty()) {
            val prevIndex = if (state.shuffledIndex > 0) state.shuffledIndex - 1 else state.shuffledPlaylist.size - 1
            _playerState.update { it.copy(shuffledIndex = prevIndex) }
            val prevItem = state.shuffledPlaylist[prevIndex]
            Log.d(TAG, "playPrevious (shuffle): shuffledIndex=$prevIndex, prevTrack=${prevItem.title}")
            playMusicDirect(prevItem, state)
        } else {
            val prevIndex = if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.size - 1
            Log.d(TAG, "playPrevious: prevIndex=$prevIndex, prevTrack=${state.playlist[prevIndex].title}")
            playMusic(state.playlist[prevIndex], state.playlist)
        }
    }

    /**
     * Directly play a music item without regenerating the shuffled playlist.
     * Used when navigating next/previous in shuffle mode.
     */
    private fun playMusicDirect(musicItem: MusicItem, state: PlayerState) {
        if (musicItem.source == com.webdav.music.data.model.MusicSource.WEBDAV) {
            runBlocking {
                val config = repository.getWebDAVConfig()
                audioService?.setWebDAVCredentials(config.username, config.password)
            }
        }

        val originalIndex = state.playlist.indexOf(musicItem)
        _playerState.update {
            it.copy(
                currentMusic = musicItem,
                currentIndex = originalIndex,
                isPlaying = true,
                progress = 0,
                duration = musicItem.duration
            )
        }
        audioService?.playMusic(musicItem, state.repeatMode)
    }

    fun seekTo(position: Long) {
        audioService?.seekTo(position)
        _playerState.update { it.copy(progress = position) }
    }

    fun toggleShuffle() {
        val state = _playerState.value
        val newMode = !state.shuffleMode
        if (newMode && state.playlist.isNotEmpty()) {
            // Generate shuffled playlist, ensure current song is first
            val remaining = state.playlist.filter { it != state.currentMusic }.shuffled()
            val shuffled = if (state.currentMusic != null) {
                listOf(state.currentMusic!!) + remaining
            } else {
                remaining
            }
            _playerState.update {
                it.copy(
                    shuffleMode = newMode,
                    shuffledPlaylist = shuffled,
                    shuffledIndex = 0
                )
            }
        } else {
            _playerState.update {
                it.copy(
                    shuffleMode = newMode,
                    shuffledPlaylist = emptyList(),
                    shuffledIndex = 0
                )
            }
        }
        viewModelScope.launch {
            repository.preferencesManager.saveShuffleMode(newMode)
        }
    }

    fun toggleRepeat() {
        val currentMode = _playerState.value.repeatMode
        val newMode = when (currentMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playerState.update { it.copy(repeatMode = newMode) }
        audioService?.setRepeatMode(newMode)
        viewModelScope.launch {
            repository.preferencesManager.saveRepeatMode(newMode.name)
        }
    }

    fun selectTab(index: Int) {
        Log.d(TAG, "selectTab: $index")
        _selectedTab.value = index
        if (index == 1) {
            Log.d(TAG, "selectTab: WebDAV 标签被选中，加载音乐")
            loadWebDAVMusic()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getFilteredLocalMusic(): List<MusicItem> {
        val query = _searchQuery.value.lowercase()
        return if (query.isEmpty()) _localMusic.value
        else _localMusic.value.filter {
            it.title.lowercase().contains(query) ||
            it.artist.lowercase().contains(query) ||
            it.album.lowercase().contains(query)
        }
    }

    fun getFilteredWebDAVMusic(): List<MusicItem> {
        val query = _searchQuery.value.lowercase()
        return if (query.isEmpty()) _webDAVMusic.value
        else _webDAVMusic.value.filter {
            it.title.lowercase().contains(query) ||
            it.artist.lowercase().contains(query) ||
            it.album.lowercase().contains(query)
        }
    }

    fun setOnboardingCompleted() {
        viewModelScope.launch {
            repository.setOnboardingCompleted()
            _hasCompletedOnboarding.value = true
        }
    }

    fun saveWebDAVConfig(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            repository.saveWebDAVConfig(WebDAVConfig(serverUrl, username, password))
        }
    }

    fun testAndSaveWebDAVConfig(serverUrl: String, username: String, password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val config = WebDAVConfig(serverUrl, username, password)
            val success = repository.testWebDAVConnection(config)
            if (success) {
                repository.saveWebDAVConfig(config)
            }
            onResult(success)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun onPlaybackEnded() {
        val state = _playerState.value
        Log.d(TAG, "onPlaybackEnded: repeatMode=${state.repeatMode}, shuffleMode=${state.shuffleMode}")
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                // RepeatMode.ONE is handled by ExoPlayer internally, no action needed
                // But if it somehow gets here, replay the current track
                state.currentMusic?.let { playMusicDirect(it, state) }
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                // Check if we're at the last track
                val isLastTrack = if (state.shuffleMode && state.shuffledPlaylist.isNotEmpty()) {
                    state.shuffledIndex >= state.shuffledPlaylist.size - 1
                } else {
                    state.currentIndex >= state.playlist.size - 1
                }
                if (isLastTrack) {
                    // Stop playback at the end of the playlist
                    _playerState.update { it.copy(isPlaying = false) }
                } else {
                    playNext()
                }
            }
        }
    }

    private fun syncServiceState() {
        viewModelScope.launch {
            audioService?.isPlaying?.collect { playing ->
                _playerState.update { it.copy(isPlaying = playing) }
            }
        }
        viewModelScope.launch {
            audioService?.currentMusic?.collect { music ->
                if (music != null && music != _playerState.value.currentMusic) {
                    _playerState.update { it.copy(currentMusic = music) }
                }
            }
        }
        viewModelScope.launch {
            audioService?.playbackEnded?.collect { ended ->
                if (ended) {
                    onPlaybackEnded()
                }
            }
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        var saveCounter = 0
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                audioService?.updateProgress()
                val progress = audioService?.progress?.value ?: 0L
                val duration = audioService?.duration?.value ?: 0L
                _playerState.update { it.copy(progress = progress, duration = duration) }

                // Save progress every 10 seconds to avoid excessive writes
                saveCounter++
                if (saveCounter >= 10) {
                    saveCounter = 0
                    val current = _playerState.value.currentMusic
                    if (current != null) {
                        repository.savePlaybackProgress(current.id, progress)
                    }
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        progressUpdateJob?.cancel()
        try {
            getApplication<Application>().unregisterReceiver(mediaActionReceiver)
        } catch (_: Exception) {}
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        super.onCleared()
    }
}