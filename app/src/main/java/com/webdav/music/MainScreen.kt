package com.webdav.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webdav.music.ui.components.PlayerControls
import com.webdav.music.ui.components.SearchBar
import com.webdav.music.ui.components.TrackList

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

    var showOnboarding by remember { mutableStateOf(!hasCompletedOnboarding) }

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
            onComplete = { serverUrl, username, password ->
                viewModel.testAndSaveWebDAVConfig(serverUrl, username, password) { success ->
                    if (success) {
                        viewModel.setOnboardingCompleted()
                        showOnboarding = false
                    }
                }
            },
            onSkip = {
                viewModel.setOnboardingCompleted()
                showOnboarding = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Music") },
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

            playerState.currentMusic?.let { currentMusic ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(160.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = currentMusic.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = currentMusic.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PlayerControls(
                        isPlaying = playerState.isPlaying,
                        shuffleMode = playerState.shuffleMode,
                        repeatMode = playerState.repeatMode,
                        progress = playerState.progress,
                        duration = playerState.duration,
                        onPlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::playNext,
                        onPrevious = viewModel::playPrevious,
                        onSeek = viewModel::seekTo,
                        onShuffleToggle = viewModel::toggleShuffle,
                        onRepeatToggle = viewModel::toggleRepeat
                    )
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Local") },
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
                    val tracks = if (selectedTab == 0) {
                        viewModel.getFilteredLocalMusic()
                    } else {
                        viewModel.getFilteredWebDAVMusic()
                    }

                    if (tracks.isEmpty()) {
                        Text(
                            text = if (selectedTab == 0) "No local music found" else "No WebDAV music found",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        TrackList(
                            tracks = tracks,
                            currentTrackId = playerState.currentMusic?.id,
                            onTrackClick = { track ->
                                viewModel.playMusic(track, tracks)
                            },
                            onDownload = { track ->
                                viewModel.downloadTrack(track)
                            }
                        )
                    }
                }
            }
        }
    }
}