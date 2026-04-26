package com.webdav.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.webdav.music.data.model.WebDAVConfig
import com.webdav.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SettingsScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: MusicRepository,
    onBack: () -> Unit
) {
    var localMusicDir by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showDetailScreen by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<WebDAVConfig?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var services by remember { mutableStateOf<List<WebDAVConfig>>(emptyList()) }
    var currentId by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistable permission: $e")
            }
            Log.d(TAG, "Selected folder URI: $it")
            localMusicDir = it.toString()
        }
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "加载配置")
        localMusicDir = repository.preferencesManager.localMusicDir.first()
        services = repository.getWebDAVServices()
        currentId = repository.preferencesManager.currentWebDAVId.first()
        isLoading = false
        Log.d(TAG, "加载完成: services=${services.size}")
    }

    LaunchedEffect(showDetailScreen) {
        if (!showDetailScreen) {
            services = repository.getWebDAVServices()
            currentId = repository.preferencesManager.currentWebDAVId.first()
        }
    }

    if (showDetailScreen) {
        WebDAVServerDetailScreen(
            repository = repository,
            existingConfig = editingConfig,
            onBack = {
                showDetailScreen = false
                editingConfig = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editingConfig = null
                        showDetailScreen = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加服务器")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Text(
                    text = "本地音乐",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { folderPickerLauncher.launch(null) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "音乐目录",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (localMusicDir.startsWith("content://")) {
                                    try {
                                        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(localMusicDir))
                                        docId.substringAfter(":").substringAfterLast("/")
                                    } catch (e: Exception) { "已选择文件夹" }
                                } else if (localMusicDir.isNotEmpty()) {
                                    localMusicDir.substringAfterLast("/")
                                } else { "点击选择目录" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "WebDAV 服务器",
                    style = MaterialTheme.typography.titleMedium
                )

                if (services.isEmpty()) {
                    Text(
                        text = "暂无服务器，点击右上角 + 添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    services.forEach { service ->
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = service.id == currentId,
                                    onClick = {
                                        scope.launch {
                                            repository.setCurrentWebDAVService(service.id)
                                            currentId = service.id
                                            Log.d(TAG, "切换到服务器: ${service.displayName}")
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = service.displayName,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = service.serverUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    editingConfig = service
                                    showDetailScreen = true
                                }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "编辑"
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            if (localMusicDir.isNotEmpty()) {
                                repository.preferencesManager.saveLocalMusicDir(localMusicDir)
                                Log.d(TAG, "保存本地音乐目录: $localMusicDir")
                            }
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("完成")
                }
            }
        }
    }
}
