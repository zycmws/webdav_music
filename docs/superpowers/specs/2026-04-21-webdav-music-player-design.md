# WebDAV 音乐播放器 - 设计文档

## 1. 项目概述

**项目名称**: WebDAV Music Player
**类型**: Android 原生音乐播放器
**核心功能**: 支持从本地存储和 WebDAV 服务器播放音乐，提供混合播放模式（在线播放/下载缓存）

---

## 2. 技术栈

| 组件 | 技术选型 |
|------|----------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose |
| 最低 SDK | 26 (Android 8.0) |
| 目标 SDK | 34 (Android 14) |
| 音频引擎 | Media3 (ExoPlayer) |
| WebDAV 客户端 | [WebDAV-Client-Kotlin](https://github.com/nicksprince/webdav-client-kotlin) 或自行封装 |
| 架构 | 简洁分层 (UI → ViewModel → Repository → DataSource) |
| 依赖注入 | 手动（避免过度设计） |

---

## 3. 架构设计

### 3.1 分层结构

```
┌─────────────────────────────────────────────┐
│                   UI 层                      │
│    Composable 函数 + ViewModel (StateFlow)   │
├─────────────────────────────────────────────┤
│              Repository 层                    │
│     MusicRepository (统一数据入口)            │
├──────────────────┬──────────────────────────┤
│  LocalDataSource  │   WebDAVDataSource       │
│  (MediaStore)     │   (WebDAV Client)        │
├──────────────────┴──────────────────────────┤
│              AudioPlayer Service             │
│           (Media3 ExoPlayer)                 │
└─────────────────────────────────────────────┘
```

### 3.2 核心模块职责

| 模块 | 职责 |
|------|------|
| `MainViewModel` | 管理播放状态、当前播放列表、界面 UI 状态 |
| `MusicRepository` | 统一管理本地音乐和 WebDAV 音乐的数据访问 |
| `LocalMusicDataSource` | 扫描本地存储的音频文件，读取元数据 |
| `WebDAVDataSource` | 连接 WebDAV 服务器，列出目录、下载文件 |
| `AudioPlayerService` | 后台播放服务，管理 Media3 Player |
| `DownloadManager` | 管理 WebDAV 音乐的本地缓存 |

---

## 4. 数据模型

### 4.1 音乐条目

```kotlin
data class MusicItem(
    val id: String,           // 唯一标识
    val title: String,        // 歌曲名
    val artist: String,       // 艺术家
    val album: String,        // 专辑
    val duration: Long,       // 时长 (毫秒)
    val path: String,         // 文件路径 (本地) 或 URL (WebDAV)
    val source: MusicSource,  // 来源: LOCAL / WEBDAV
    val isDownloaded: Boolean // 是否已下载缓存
)

enum class MusicSource { LOCAL, WEBDAV }
```

### 4.2 WebDAV 配置

```kotlin
data class WebDAVConfig(
    val serverUrl: String,    // 例如: https://example.com/webdav/
    val username: String,
    val password: String
)
```

### 4.3 播放状态

```kotlin
data class PlayerState(
    val isPlaying: Boolean = false,
    val currentMusic: MusicItem? = null,
    val playlist: List<MusicItem> = emptyList(),
    val currentIndex: Int = 0,
    val progress: Long = 0,        // 当前播放位置 (毫秒)
    val duration: Long = 0,         // 总时长 (毫秒)
    val shuffleMode: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF
)

enum class RepeatMode { OFF, ONE, ALL }
```

### 4.4 播放列表

```kotlin
data class Playlist(
    val id: String,
    val name: String,
    val items: List<MusicItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
```

---

## 5. UI 设计

### 5.1 整体风格

- **主题**: 浅色主题 (Light Theme)
- **设计语言**: Material Design 3 简化版
- **风格**: 极简主义，专注播放核心功能

### 5.2 屏幕结构

#### 5.2.1 首次使用引导 (Onboarding)

首次启动 App 时，引导用户配置 WebDAV：

```
┌─────────────────────────────────────┐
│                                     │
│         WebDAV 音乐播放器            │
│                                     │
│   请配置您的 WebDAV 服务器           │
│                                     │
│   服务器地址:                        │
│   ┌─────────────────────────────┐  │
│   │ https://example.com/webdav/ │  │
│   └─────────────────────────────┘  │
│                                     │
│   用户名:                           │
│   ┌─────────────────────────────┐  │
│   │                             │  │
│   └─────────────────────────────┘  │
│                                     │
│   密码:                             │
│   ┌─────────────────────────────┐  │
│   │                             │  │
│   └─────────────────────────────┘  │
│                                     │
│   [        连接并开始        ]       │
│                                     │
│   [跳过，直接使用本地音乐]           │
└─────────────────────────────────────┘
```

#### 5.2.2 主播放界面

```
┌─────────────────────────────────────┐
│  ☰  我的音乐            [设置图标]  │
├─────────────────────────────────────┤
│                                     │
│         ┌───────────────┐           │
│         │               │           │
│         │   专辑封面     │           │
│         │   (占位图)    │           │
│         │               │           │
│         └───────────────┘           │
│                                     │
│         歌曲名称                     │
│         艺术家名称                   │
│                                     │
├─────────────────────────────────────┤
│   🔀  🔁    ◀◀   ▶/❚❚   ▶▶   🔀  🔁  │
├─────────────────────────────────────┤
│  ─────────●────────────────────    │
│  1:23            3:45               │
├─────────────────────────────────────┤
│  [📁 本地]  [☁️ WebDAV]  [🔍 搜索]    │
├─────────────────────────────────────┤
│  ▶ 歌曲 A - 艺术家 A                  │
│  ▶ 歌曲 B - 艺术家 B                  │
│  ▶ 歌曲 C - 艺术家 C                  │
│  ...                                 │
└─────────────────────────────────────┘
```

### 5.3 页面导航

- **主页**: 播放界面 + 歌曲列表 + 底部 Tab 切换 (本地/WebDAV)
- **侧边抽屉**: 设置、清除缓存、关于
- **WebDAV 配置**: 通过设置页面修改

---

## 6. 功能模块

### 6.1 本地音乐扫描

- 扫描设备上的音频文件 (MediaStore API)
- 读取 ID3 元数据 (标题、艺术家、专辑、时长)
- 过滤支持格式: MP3, FLAC, AAC, OGG, WAV
- 后台扫描，扫描完成后刷新列表

### 6.2 WebDAV 连接

- 支持 Basic Auth (用户名+密码)
- 验证连接: 尝试访问服务器根目录
- 保存配置到 SharedPreferences (加密存储密码)
- 支持 Self-signed 证书 (开发阶段)

### 6.3 WebDAV 浏览与播放

- 列出服务器根目录下的音频文件
- 支持子目录浏览
- **在线播放**: 获取文件 InputStream，交给 Media3 直接播放
- **下载缓存**: 下载到应用私有目录，下次直接播放本地文件

### 6.4 音频播放

- 使用 Media3 ExoPlayer
- 后台播放 (Foreground Service)
- 播放控制: 播放/暂停、上一首/下一首
- 进度同步: 实时更新播放进度
- 耳机线控支持
- **随机播放 (Shuffle)**: 随机打乱播放列表顺序
- **循环模式 (Repeat)**: 关闭 / 单曲循环 / 列表循环

### 6.5 后台播放与通知栏

- Foreground Service 保持后台运行
- 通知栏显示: 歌曲名、艺术家、专辑封面(占位图)
- 通知栏控制按钮: 上一首 / 播放暂停 / 下一首
- 锁屏界面控制: 系统 MediaSession 提供锁屏、耳机线控支持

### 6.6 播放进度记忆

- 保存当前播放歌曲 ID 和进度到 SharedPreferences
- App 重启时自动恢复播放位置
- 跨 Session 续播体验

### 6.7 下载管理

- 下载队列管理
- 下载进度显示
- 缓存文件路径: `{app_data}/cache/music/`
- 缓存文件命名: `{hash_of_url}.audio`

### 6.8 播放列表管理

- 创建自定义播放列表 (命名)
- 向播放列表添加/移除歌曲
- 拖拽重排播放列表顺序
- 删除播放列表
- 本地持久化存储 (SharedPreferences 或 Room)

### 6.9 搜索功能

- 实时搜索本地音乐库 (标题、艺术家、专辑)
- 搜索结果高亮显示匹配文字
- 点击搜索结果直接播放

---

## 7. 数据流

### 7.1 本地音乐播放流程

```
用户选择本地歌曲
    ↓
LocalMusicDataSource 扫描/获取歌曲信息
    ↓
MusicRepository 返回 MusicItem
    ↓
MainViewModel 更新播放列表和当前歌曲
    ↓
AudioPlayerService 播放本地文件路径
```

### 7.2 WebDAV 在线播放流程

```
用户选择 WebDAV 歌曲
    ↓
WebDAVDataSource 获取文件 InputStream
    ↓
MusicRepository 返回带有 URL 的 MusicItem
    ↓
MainViewModel 更新播放列表和当前歌曲
    ↓
AudioPlayerService 使用 ExoPlayer 播放 URL
```

### 7.3 WebDAV 下载后播放流程

```
用户点击"下载"按钮
    ↓
DownloadManager 下载文件到缓存目录
    ↓
更新 MusicItem.isDownloaded = true
    ↓
AudioPlayerService 播放本地缓存文件
```

---

## 8. 错误处理

| 场景 | 处理方式 |
|------|----------|
| WebDAV 连接失败 | Toast 提示 "连接失败，请检查服务器地址和账号密码"，显示重试按钮 |
| WebDAV 认证失败 | Toast 提示 "用户名或密码错误"，跳转配置页面 |
| 播放失败 (文件损坏) | 自动跳至下一首，Toast 提示 "播放失败，已跳过" |
| 网络断开 | 检测到网络断开时，Toast 提示 "网络已断开"，暂停播放 |
| 文件未找到 | Toast 提示 "文件不存在"，从列表中移除 |
| 无本地音乐 | 显示空状态 "未找到本地音乐，请先导入音乐文件" |
| 无 WebDAV 配置 | 首次引导或跳转引导页面 |

---

## 9. 项目结构

```
app/
├── src/main/
│   ├── java/com/webdav/music/
│   │   ├── MainActivity.kt
│   │   ├── MainViewModel.kt
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   ├── MusicItem.kt
│   │   │   │   ├── WebDAVConfig.kt
│   │   │   │   ├── PlayerState.kt
│   │   │   │   └── Playlist.kt
│   │   │   ├── source/
│   │   │   │   ├── LocalMusicDataSource.kt
│   │   │   │   └── WebDAVDataSource.kt
│   │   │   ├── repository/
│   │   │   │   └── MusicRepository.kt
│   │   │   └── local/
│   │   │       └── PreferencesManager.kt  // DataStore 存储
│   │   ├── player/
│   │   │   ├── AudioPlayerService.kt      // 后台播放 + MediaSession
│   │   │   └── DownloadManager.kt
│   │   └── ui/
│   │       ├── theme/
│   │       │   └── Theme.kt
│   │       ├── components/
│   │       │   ├── PlayerControls.kt
│   │       │   ├── TrackList.kt
│   │       │   └── SearchBar.kt
│   │       ├── screens/
│   │       │   ├── MainScreen.kt
│   │       │   ├── OnboardingScreen.kt
│   │       │   └── PlaylistScreen.kt
│   │       └── navigation/
│   │           └── AppNavigation.kt
│   ├── res/
│   │   ├── values/
│   │   │   └── strings.xml
│   │   └── drawable/
│   │       └── ic_music_placeholder.xml
│   └── AndroidManifest.xml
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 10. 关键依赖

```kotlin
// Media3 ExoPlayer
implementation("androidx.media3:media3-exoplayer:1.2.1")
implementation("androidx.media3:media3-session:1.2.1")
implementation("androidx.media3:media3-ui:1.2.1") // 通知栏样式

// Compose BOM
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")

// ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

// OkHttp (WebDAV 底层依赖)
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// DataStore (用于保存播放进度和配置)
implementation("androidx.datastore:datastore-preferences:1.0.0")
```

---

## 11. 里程碑规划

| 阶段 | 功能 |
|------|------|
| M1 | 项目搭建、基础 UI、浅色主题 |
| M2 | 本地音乐扫描与播放 + MediaSession 后台播放 |
| M3 | WebDAV 连接配置 (首次引导) |
| M4 | WebDAV 浏览与在线播放 + 下载缓存 |
| M5 | Shuffle/Repeat + 播放进度记忆 |
| M6 | 播放列表管理 |
| M7 | 搜索功能 |
| M8 | 细节打磨、错误处理 |

---

*设计文档版本: 2.0*
*创建日期: 2026-04-21*
*更新日期: 2026-04-21*
