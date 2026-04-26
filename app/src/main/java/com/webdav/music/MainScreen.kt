package com.webdav.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webdav.music.data.repository.MusicRepository
import com.webdav.music.ui.OnboardingScreen
import com.webdav.music.ui.components.MiniPlayer
import com.webdav.music.ui.components.SearchBar
import com.webdav.music.ui.components.TrackList
import com.webdav.music.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val playerState by viewModel.playerState.collectAsState()
    val localMusic by viewModel.localMusic.collectAsState()
    val webDAVMusic by viewModel.webDAVMusic.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentWebDAVName by viewModel.currentWebDAVName.collectAsState()

    var showOnboarding by remember { mutableStateOf(!hasCompletedOnboarding) }
    var showSettings by remember { mutableStateOf(false) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val repository = remember { MusicRepository(context) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(hasCompletedOnboarding) {
        showOnboarding = !hasCompletedOnboarding
    }

    if (showOnboarding) {
        OnboardingScreen(
            onComplete = { showOnboarding = false },
            onSkip = { showOnboarding = false }
        )
        return
    }

    if (showSettings) {
        SettingsScreen(
            repository = repository,
            onBack = { showSettings = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的音乐") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("本地音乐") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("WebDAV") },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val tracks by remember {
                        derivedStateOf {
                            if (selectedTab == 0) {
                                viewModel.getFilteredLocalMusic()
                            } else {
                                viewModel.getFilteredWebDAVMusic()
                            }
                        }
                    }

                    if (tracks.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (selectedTab == 1 && currentWebDAVName == null) {
                                Text(
                                    text = "请前往设置添加并选择 WebDAV 服务器",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(
                                    text = if (selectedTab == 0) "未找到本地音乐" else "未找到 WebDAV 音乐",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        if (selectedTab == 1 && currentWebDAVName != null) {
                            Text(
                                text = "当前：$currentWebDAVName",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        TrackList(
                            tracks = tracks,
                            currentTrackId = playerState.currentMusic?.id,
                            onTrackClick = { track ->
                                isPlayerExpanded = false
                                viewModel.playMusic(track, tracks)
                            }
                        )
                    }
                }
            }

            playerState.currentMusic?.let { currentMusic ->
                MiniPlayer(
                    musicItem = currentMusic,
                    isPlaying = playerState.isPlaying,
                    isExpanded = isPlayerExpanded,
                    onExpandChange = { isPlayerExpanded = it },
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNext,
                    onPrevious = viewModel::playPrevious,
                    onSeek = viewModel::seekTo,
                    progress = playerState.progress,
                    duration = playerState.duration,
                    onShuffleToggle = viewModel::toggleShuffle,
                    onRepeatToggle = viewModel::toggleRepeat,
                    shuffleMode = playerState.shuffleMode,
                    repeatMode = playerState.repeatMode
                )
            }
        }
    }
}
