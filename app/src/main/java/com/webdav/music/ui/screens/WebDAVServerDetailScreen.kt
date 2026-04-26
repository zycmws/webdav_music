package com.webdav.music.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.webdav.music.data.model.WebDAVConfig
import com.webdav.music.data.repository.MusicRepository
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "WebDAVServerDetailScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDAVServerDetailScreen(
    repository: MusicRepository,
    existingConfig: WebDAVConfig? = null,
    onBack: () -> Unit
) {
    val isEditing = existingConfig != null
    var displayName by remember { mutableStateOf(existingConfig?.displayName ?: "") }
    var serverUrl by remember { mutableStateOf(existingConfig?.serverUrl ?: "") }
    var username by remember { mutableStateOf(existingConfig?.username ?: "") }
    var password by remember { mutableStateOf(existingConfig?.password ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑服务器" else "添加服务器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isEditing) {
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("删除")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it; errorMessage = null },
                label = { Text("名称 *") },
                placeholder = { Text("如：家里 NAS") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it; errorMessage = null },
                label = { Text("服务器地址") },
                placeholder = { Text("http://192.168.1.5:5005/") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMessage = null },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = !isLoading
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (displayName.isBlank()) {
                        errorMessage = "请输入服务器名称"
                        return@Button
                    }
                    if (serverUrl.isBlank()) {
                        errorMessage = "请输入服务器地址"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val config = WebDAVConfig(
                            id = existingConfig?.id ?: UUID.randomUUID().toString(),
                            displayName = displayName.trim(),
                            serverUrl = serverUrl.trim(),
                            username = username.trim(),
                            password = password
                        )
                        Log.d(TAG, "测试连接: ${config.serverUrl}")
                        val success = repository.testWebDAVConnection(config)
                        if (success) {
                            if (isEditing) {
                                repository.updateWebDAVService(config)
                                Log.d(TAG, "更新服务器: ${config.displayName}")
                            } else {
                                repository.addWebDAVService(config)
                                repository.setCurrentWebDAVService(config.id)
                                Log.d(TAG, "添加服务器: ${config.displayName}")
                            }
                            onBack()
                        } else {
                            errorMessage = "连接测试失败，请检查地址和凭据"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("保存")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除服务器") },
            text = { Text("确定要删除「${existingConfig?.displayName}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            existingConfig?.id?.let {
                                repository.deleteWebDAVService(it)
                                Log.d(TAG, "删除服务器: $it")
                            }
                            showDeleteDialog = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
