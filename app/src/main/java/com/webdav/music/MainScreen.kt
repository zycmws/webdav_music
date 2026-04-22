package com.webdav.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.webdav.music.data.repository.MusicRepository
import com.webdav.music.ui.components.SearchBar
import com.webdav.music.ui.components.TrackList
import com.webdav.music.ui.OnboardingScreen
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

            // 音乐列表
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
                            text = if (selectedTab == 0) "未找到本地音乐" else "未找到 WebDAV 音乐",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
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

            // Mini Player (当有音乐播放时显示)
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

@Composable
private fun MiniPlayer(
    musicItem: com.webdav.music.data.model.MusicItem,
    isPlaying: Boolean,
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    progress: Long,
    duration: Long,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    shuffleMode: Boolean,
    repeatMode: com.webdav.music.data.model.RepeatMode
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onExpandChange(!isExpanded) }
    ) {
        // 迷你播放器条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面占位
            Surface(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = musicItem.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
            }

            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放"
                )
            }

            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "收起" else "展开",
                modifier = Modifier.padding(8.dp)
            )
        }

        // 展开的完整播放器
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            ExpandedPlayer(
                musicItem = musicItem,
                isPlaying = isPlaying,
                progress = progress,
                duration = duration,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onPrevious = onPrevious,
                onNext = onNext,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                shuffleMode = shuffleMode,
                repeatMode = repeatMode
            )
        }
    }
}

@Composable
private fun ExpandedPlayer(
    musicItem: com.webdav.music.data.model.MusicItem,
    isPlaying: Boolean,
    progress: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    shuffleMode: Boolean,
    repeatMode: com.webdav.music.data.model.RepeatMode
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 大专辑封面
        Surface(
            modifier = Modifier.size(200.dp),
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

        Spacer(modifier = Modifier.height(24.dp))

        // 歌曲信息
        Text(
            text = musicItem.title,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = musicItem.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 进度条
        if (duration > 0) {
            Slider(
                value = progress.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(progress),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 播放控制
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffleToggle) {
                Text(
                    text = "随机",
                    color = if (shuffleMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(36.dp))
            }

            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(36.dp))
            }

            IconButton(onClick = onRepeatToggle) {
                Text(
                    text = when (repeatMode) {
                        com.webdav.music.data.model.RepeatMode.OFF -> "关闭"
                        com.webdav.music.data.model.RepeatMode.ALL -> "全部"
                        com.webdav.music.data.model.RepeatMode.ONE -> "单曲"
                    },
                    color = if (repeatMode != com.webdav.music.data.model.RepeatMode.OFF)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}