package com.webdav.music

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.AudioPlayerBinder
            audioService = binder.getService()
            serviceBound = true
            syncServiceState()
            startProgressUpdates()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            serviceBound = false
        }
    }

    init {
        bindService()
        checkOnboarding()
        loadLocalMusic()
    }

    private fun bindService() {
        val intent = Intent(getApplication(), AudioPlayerService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun checkOnboarding() {
        viewModelScope.launch {
            repository.hasCompletedOnboarding().collect {
                _hasCompletedOnboarding.value = it
            }
        }
    }

    fun loadLocalMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _localMusic.value = repository.getLocalMusic()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load local music"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadWebDAVMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val cached = repository.getCachedMusic()
                val remote = repository.getWebDAVMusic()
                _webDAVMusic.value = remote.map { remoteItem ->
                    cached.find { it.id == remoteItem.id } ?: remoteItem
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load WebDAV music"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playMusic(musicItem: MusicItem, playlist: List<MusicItem>) {
        val index = playlist.indexOf(musicItem)
        _playerState.update {
            it.copy(
                currentMusic = musicItem,
                playlist = playlist,
                currentIndex = index,
                isPlaying = true,
                progress = 0,
                duration = musicItem.duration
            )
        }
        audioService?.playMusic(musicItem)
    }

    fun togglePlayPause() {
        val current = _playerState.value
        if (current.currentMusic == null) return

        if (current.isPlaying) {
            audioService?.pause()
        } else {
            audioService?.play()
        }
        _playerState.update { it.copy(isPlaying = !current.isPlaying) }
    }

    fun playNext() {
        val state = _playerState.value
        if (state.playlist.isEmpty()) return
        val nextIndex = (state.currentIndex + 1) % state.playlist.size
        playMusic(state.playlist[nextIndex], state.playlist)
    }

    fun playPrevious() {
        val state = _playerState.value
        if (state.playlist.isEmpty()) return
        val prevIndex = if (state.currentIndex > 0) state.currentIndex - 1 else state.playlist.size - 1
        playMusic(state.playlist[prevIndex], state.playlist)
    }

    fun seekTo(position: Long) {
        audioService?.seekTo(position)
        _playerState.update { it.copy(progress = position) }
    }

    fun toggleShuffle() {
        val newMode = !_playerState.value.shuffleMode
        _playerState.update { it.copy(shuffleMode = newMode) }
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
        viewModelScope.launch {
            repository.preferencesManager.saveRepeatMode(newMode.name)
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
        if (index == 1) {
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
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = viewModelScope.launch {
            while (isActive) {
                audioService?.updateProgress()
                val progress = audioService?.progress?.value ?: 0L
                val duration = audioService?.duration?.value ?: 0L
                _playerState.update { it.copy(progress = progress, duration = duration) }

                val current = _playerState.value.currentMusic
                if (current != null) {
                    repository.savePlaybackProgress(current.id, progress)
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        progressUpdateJob?.cancel()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        super.onCleared()
    }
}