package com.webdav.music.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import kotlinx.coroutines.launch

private const val TAG = "OnboardingScreen"

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("http://192.168.1.5:5005/") }
    var username by remember { mutableStateOf("zyc") }
    var password by remember { mutableStateOf("zcgy1011") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MusicRepository(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WebDAV 音乐播放器",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "配置您的 WebDAV 服务器",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(48.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it; errorMessage = null },
            label = { Text("用户名") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                Log.d(TAG, "点击连接按钮")
                Log.d(TAG, "服务器: $serverUrl")
                Log.d(TAG, "用户名: $username")

                if (serverUrl.isBlank()) {
                    errorMessage = "请输入服务器地址"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    Log.d(TAG, "开始测试连接")
                    val config = WebDAVConfig(serverUrl, username, password)
                    val success = repository.testWebDAVConnection(config)
                    Log.d(TAG, "连接测试结果: $success")

                    if (success) {
                        Log.d(TAG, "连接成功，保存配置")
                        repository.saveWebDAVConfig(config)
                        repository.setOnboardingCompleted()
                        Log.d(TAG, "跳转主页面")
                        onComplete()
                    } else {
                        Log.e(TAG, "连接失败")
                        errorMessage = "连接失败，请检查服务器地址和凭据"
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
                Text("连接并开始")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = {
                scope.launch {
                    repository.setOnboardingCompleted()
                }
                onSkip()
            },
            enabled = !isLoading
        ) {
            Text("跳过，仅使用本地音乐")
        }
    }
}