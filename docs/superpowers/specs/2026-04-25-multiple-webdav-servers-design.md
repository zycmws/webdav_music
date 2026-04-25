# 多 WebDAV 服务器配置与切换设计文档

## 背景

当前应用仅支持配置单个 WebDAV 服务器，用户有多台服务器需要按需切换浏览和播放。

## 目标

1. 支持添加、编辑、删除多个 WebDAV 服务器配置
2. 在设置页面统一管理服务器列表，按需切换当前激活的服务器
3. 主界面 WebDAV Tab 仅展示当前选中服务器的音乐列表
4. 账号密码使用 Android Keystore 加密存储

## 非目标

- 不合并多个服务器的音乐列表（当前选中哪个就展示哪个）
- 不在播放页面提供服务器切换控制
- 不引入 Room/SQLite（DataStore + JSON 足够覆盖此场景）

## 方案概述

采用 **DataStore + JSON 序列化 + AES-256/GCM 加密** 方案。

理由：WebDAV 服务器数量通常极少（< 10），JSON 序列化性能完全可接受；与现有 DataStore 架构无缝衔接；无需引入 Room 等重型依赖。

---

## 1. 数据模型

### 1.1 WebDAVConfig

扩展现有数据类，新增 `id` 和 `displayName`：

```kotlin
data class WebDAVConfig(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val password: String
) {
    fun isValid(): Boolean = serverUrl.isNotBlank() && username.isNotBlank()
}
```

- `id`：唯一标识，UUID 生成，用于列表区分和当前选中标记
- `displayName`：用户自定义名称，如"家里 NAS"、"公司服务器"
- 其他字段保持现有语义

---

## 2. 数据持久层（PreferencesManager）

### 2.1 DataStore Key 设计

| Key | 类型 | 用途 |
|-----|------|------|
| `webdav_services` | String | 所有服务器配置的 JSON 数组，密码字段加密 |
| `current_webdav_id` | String | 当前选中的服务器 ID（可为空） |
| 旧 key（`webdav_server_url` 等） | — | 保留用于首次启动迁移 |

### 2.2 加密方案

使用 Android Keystore 生成 AES-256/GCM 密钥：

```
序列化：List<WebDAVConfig> → JSON → 对每个元素的 password 字段 AES-GCM 加密 → base64 编码 → 最终 JSON 字符串
反序列化：JSON 字符串 → 解析 → 对每个 password 字段 base64 解码 → AES-GCM 解密 → 还原对象
```

密钥生成参数：
- `KeyProperties.KEY_ALGORITHM_AES`
- `KeyProperties.BLOCK_MODE_GCM`
- `KeyProperties.ENCRYPTION_PADDING_NONE`
- `KeyGenParameterSpec.Builder.setRandomizedEncryptionRequired(true)`

### 2.3 新增 API

```kotlin
val webDAVServices: Flow<List<WebDAVConfig>>      // 自动解密
val currentWebDAVId: Flow<String?>

suspend fun addWebDAVService(config: WebDAVConfig)
suspend fun updateWebDAVService(config: WebDAVConfig)
suspend fun deleteWebDAVService(id: String)
suspend fun setCurrentWebDAVService(id: String)
suspend fun getCurrentWebDAVConfig(): WebDAVConfig?  // 从 services 中按 id 查找
```

### 2.4 向后兼容迁移

首次启动时检测到旧 key（`webdav_server_url`）存在：

1. 读取旧配置（url、username、password）
2. 生成 UUID 作为 id
3. displayName 设为 "服务器 1"
4. 组装为单个元素的列表，写入新 JSON 格式
5. 将旧配置的 id 设为 `current_webdav_id`
6. 清除所有旧 key（`webdav_server_url`、`webdav_username`、`webdav_password`）

---

## 3. Repository 层（MusicRepository）

### 3.1 新增方法

```kotlin
suspend fun getWebDAVServices(): List<WebDAVConfig>
suspend fun getCurrentWebDAVConfig(): WebDAVConfig?
suspend fun addWebDAVService(config: WebDAVConfig)
suspend fun updateWebDAVService(config: WebDAVConfig)
suspend fun deleteWebDAVService(id: String)
suspend fun setCurrentWebDAVService(id: String)
```

### 3.2 改造现有方法

`getWebDAVMusic()` 改为：

```kotlin
suspend fun getWebDAVMusic(): List<MusicItem> {
    val config = getCurrentWebDAVConfig()
    return if (config != null && config.isValid()) {
        webDAVDataSource.listMusic(config)
    } else {
        emptyList()
    }
}
```

---

## 4. ViewModel 层（MainViewModel）

### 4.1 新增 StateFlow

```kotlin
private val _webDAVServices = MutableStateFlow<List<WebDAVConfig>>(emptyList())
val webDAVServices: StateFlow<List<WebDAVConfig>> = _webDAVServices.asStateFlow()

private val _currentWebDAVId = MutableStateFlow<String?>(null)
val currentWebDAVId: StateFlow<String?> = _currentWebDAVId.asStateFlow()
```

### 4.2 初始化逻辑

在 `init` 中新增收集：

```kotlin
viewModelScope.launch {
    repository.preferencesManager.webDAVServices.collect {
        _webDAVServices.value = it
    }
}
viewModelScope.launch {
    repository.preferencesManager.currentWebDAVId.collect {
        _currentWebDAVId.value = it
    }
}
```

### 4.3 播放凭证

`playMusic()` 和 `playMusicDirect()` 中：

```kotlin
if (musicItem.source == MusicSource.WEBDAV) {
    runBlocking {
        val config = repository.getCurrentWebDAVConfig()
        config?.let {
            audioService?.setWebDAVCredentials(it.username, it.password)
        }
    }
}
```

若当前无选中服务器但播放 WebDAV 歌曲（异常边界），静默跳过（ExoPlayer 会因无凭证而报错，已有错误监听）。

---

## 5. UI 层

### 5.1 SettingsScreen 改造为服务器列表页

页面结构：

```
┌─────────────────────────┐
│  ←  设置              + │
├─────────────────────────┤
│ 本地音乐               │
│ ┌─────────────────────┐ │
│ │ 📁 音乐目录          │ │
│ └─────────────────────┘ │
├─────────────────────────┤
│ WebDAV 服务器           │
│ ┌─────────────────────┐ │
│ │ ◉ 家里 NAS          │ │
│ │   http://192.168...  │ │
│ │              [编辑]  │ │
│ ├─────────────────────┤ │
│ │ ○ 公司服务器        │ │
│ │   http://office...   │ │
│ │              [编辑]  │ │
│ ├─────────────────────┤ │
│ │ ○ 阿里云盘          │ │
│ │   https://dav...     │ │
│ │              [编辑]  │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

交互：
- 点击 RadioButton：切换当前选中服务器，返回主界面后列表自动刷新
- 点击编辑图标：进入详情页
- 点击右上角「+」：进入新增详情页

### 5.2 新增 WebDAVServerDetailScreen

字段：
- 名称（displayName，必填，默认"服务器"）
- 服务器地址（serverUrl）
- 用户名
- 密码（PasswordVisualTransformation）

操作：
- 保存：先调用 `testWebDAVConnection()`，成功后再保存
- 删除：编辑模式下显示删除按钮，确认后删除并返回列表
- 取消：返回列表页不保存

### 5.3 MainScreen WebDAV Tab

- Tab 标题保持「WebDAV」
- 当 `currentWebDAVId` 不为空时，列表顶部显示当前服务器名称（如「当前：家里 NAS」）
- 未选中任何服务器时，列表区域显示「请前往设置添加并选择 WebDAV 服务器」
- 选中服务器后正常加载并展示该服务器的音乐列表

---

## 6. 数据流

```
用户操作（设置页）
    ↓
SettingsScreen → ViewModel 方法 → MusicRepository → PreferencesManager
    ↓                                      ↓
    保存到 DataStore（JSON + 加密）          读取当前配置
    ↓                                      ↓
StateFlow 更新 ←──────────────────────────┘
    ↓
MainScreen 重新收集 → loadWebDAVMusic() → WebDAVDataSource.listMusic()
    ↓
刷新 WebDAV Tab 音乐列表
```

---

## 7. 边界情况

| 场景 | 处理 |
|------|------|
| 删除当前选中的服务器 | 删除后 `current_webdav_id` 设为 null，主界面列表清空 |
| 唯一服务器被删除 | 同上，列表显示提示引导用户添加 |
| 旧版本升级 | 自动迁移旧配置到新格式 |
| 播放时当前服务器被删除 | 保持当前播放不中断（凭证已设置），下一首时重新检查 |
| Keystore 密钥丢失 | 首次生成新密钥，旧加密数据无法解密，用户需重新配置 |
| 服务器地址重复 | 允许重复，以 displayName 区分 |

---

## 8. 测试要点

1. 旧配置迁移：安装旧版本 → 配置服务器 → 升级 → 验证配置保留且可正常播放
2. 加密/解密：添加服务器 → 杀掉进程重启 → 验证密码可正确解密使用
3. 切换服务器：添加 2 个服务器 → 切换当前选中 → 验证列表仅展示当前服务器音乐
4. 删除当前服务器：删除当前选中 → 验证列表清空且 current_webdav_id 为 null
5. 连接测试失败：输入错误密码 → 验证保存失败且不写入 DataStore
