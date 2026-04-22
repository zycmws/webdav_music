package com.webdav.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localMusicDir by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistent permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not take persistable permission: $e")
            }

            // 保存原始 URI（content://...）
            Log.d(TAG, "Selected folder URI: $it")
            localMusicDir = it.toString()
        }
    }

    // 加载当前配置
    LaunchedEffect(Unit) {
        Log.d(TAG, "加载配置")
        val config = repository.getWebDAVConfig()
        serverUrl = config.serverUrl
        username = config.username
        password = config.password
        localMusicDir = repository.preferencesManager.localMusicDir.first()
        isLoading = false
        Log.d(TAG, "加载完成: serverUrl=$serverUrl")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                CircularProgressIndicator()
            } else {
                // 本地音乐目录设置
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
                                    // Try to get folder name from URI
                                    try {
                                        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(localMusicDir))
                                        docId.substringAfter(":").substringAfterLast("/")
                                    } catch (e: Exception) {
                                        "已选择文件夹"
                                    }
                                } else if (localMusicDir.isNotEmpty()) {
                                    localMusicDir.substringAfterLast("/")
                                } else {
                                    "点击选择目录"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                // WebDAV 设置
                Text(
                    text = "WebDAV 服务器",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.5:5005/") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    enabled = !isSaving
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !isSaving
                )

                if (message != null) {
                    Text(
                        text = message!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        isSaving = true
                        message = null
                        scope.launch {
                            // 保存本地音乐目录
                            if (localMusicDir.isNotEmpty()) {
                                repository.preferencesManager.saveLocalMusicDir(localMusicDir)
                                Log.d(TAG, "保存本地音乐目录: $localMusicDir")
                            }

                            // 保存 WebDAV 配置
                            if (serverUrl.isNotBlank()) {
                                Log.d(TAG, "保存 WebDAV 配置: serverUrl=$serverUrl")
                                val config = WebDAVConfig(serverUrl, username, password)
                                val success = repository.testWebDAVConnection(config)
                                if (success) {
                                    repository.saveWebDAVConfig(config)
                                    message = "保存成功"
                                    Log.d(TAG, "保存成功")
                                } else {
                                    message = "WebDAV 连接测试失败"
                                    Log.e(TAG, "连接测试失败")
                                }
                            } else {
                                message = "请输入服务器地址"
                            }
                            isSaving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("保存设置")
                    }
                }
            }
        }
    }
}