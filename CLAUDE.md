# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供本仓库的工作指引。

## 构建与运行

```bash
./gradlew assembleDebug          # 构建调试 APK
./gradlew installDebug           # 构建并安装到设备
./gradlew compileDebugKotlin     # 仅编译（快速迭代）
./gradlew test                   # 运行单元测试
./gradlew test --tests "com.webdav.music.data.WebDAVUrlParsingTest"  # 运行单个测试类
```

ADB 路径：`/home/zyc/android-sdk/platform-tools/adb`
手动安装：`adb install -r app/build/outputs/apk/debug/app-debug.apk`

Gradle 仓库使用阿里云镜像（配置在 `settings.gradle.kts` 中）。

App UI 使用中文。所有用户界面字符串直接在 Compose 代码中使用中文（无 string resources）。

## 架构

MVVM + 手动依赖注入。单 Activity Compose 应用，无导航库。

```
Compose UI (MainScreen + screens/components)
    ↓ collectAsState()
MainViewModel (AndroidViewModel, StateFlow<PlayerState>)
    ↓ 直接 Binder 调用
AudioPlayerService (前台服务, Media3 ExoPlayer)
    ↑ BroadcastReceiver (ACTION_NEXT/PREVIOUS/PLAY_PAUSE)
MusicRepository → LocalMusicDataSource / WebDAVDataSource
PreferencesManager (DataStore)
```

**播放核心数据流：**
- UI 调用 ViewModel 方法 → ViewModel 通过绑定服务调用 `audioService.playMusic()`
- `AudioPlayerService` 用 `CustomForwardingPlayer` 包装 ExoPlayer，其作用：
  - 强制启用 `COMMAND_SEEK_TO_NEXT/PREVIOUS`（供 Fluid Cloud / 系统媒体控件显示上/下一曲按钮）
  - 切歌期间覆写 `isPlaying()`/`getPlaybackState()` 防止 MediaSession 中断
  - 将 `seekToNext()`/`seekToPrevious()` 委托给广播 → ViewModel 的 `mediaActionReceiver`
- 播放完成：ExoPlayer `STATE_ENDED` → `playbackEnded` StateFlow → ViewModel `onPlaybackEnded()` → `playNext()`
- 随机播放：`PlayerState.shuffledPlaylist` 持久化；随机模式下上/下一曲使用 `playMusicDirect()` 以避免重新生成乱序列表
- 循环模式：仅 `RepeatMode.ONE` 使用 ExoPlayer 内部循环；`OFF`/`ALL` 由 ViewModel 在播放结束时处理

**WebDAV 凭证：** `setWebDAVCredentials()` 在凭证未变时跳过重新初始化——**不要删除此守卫**，否则每次切歌都会销毁/重建 ExoPlayer 和 MediaSession。

**`playMusic`/`playMusicDirect` 中使用 `runBlocking` 获取 WebDAV 凭证**——这是故意的，因为 ExoPlayer 需要在发起 HTTP 请求前设置 auth header。不要替换为协程作用域。

## 测试

- 框架：JUnit 4 + MockK 1.13.9 + kotlinx-coroutines-test 1.7.3 + arch-core-testing 2.2.0
- 测试位置：`app/src/test/java/com/webdav/music/data/`
- 模拟协程时使用 `runTest`（来自 kotlinx-coroutines-test）

## 项目结构

- `data/model/` — 数据类（MusicItem, PlayerState, WebDAVConfig, RepeatMode）
- `data/source/` — LocalMusicDataSource（MediaStore/SAF）、WebDAVDataSource（OkHttp）
- `data/repository/` — MusicRepository（统一数据访问）
- `data/local/` — PreferencesManager（DataStore）
- `player/` — AudioPlayerService + CustomForwardingPlayer
- `ui/components/` — 共享 Compose 组件（PlayerControls, TrackList, SearchBar）
- `ui/screens/` — OnboardingScreen, SettingsScreen
- `MainViewModel.kt` — 核心状态与播放协调
- `MainScreen.kt` — 主界面（迷你/展开播放器）

## 关键模式

- **MusicSource 枚举**（`LOCAL`/`WEBDAV`）决定播放路径：本地文件通过 URI 播放，WebDAV 文件通过 ExoPlayer 的 `DefaultHttpDataSource` 带 auth header 流式播放
- **本地音乐**使用 SAF（存储访问框架）——用户通过 `ACTION_OPEN_DOCUMENT_TREE` 选择目录，URI 持久化在 DataStore 中
- **无 DI 框架**——`MusicRepository` 在 `MainViewModel` 中直接实例化；`AudioPlayerService` 通过 Android `ServiceConnection` 绑定
- **服务绑定**——`MainViewModel.bindService()` 在 `init{}` 中调用；`syncServiceState()` 在连接后收集服务的 StateFlow
- **进度持久化**——ViewModel 每 ~10 秒通过 `repository.savePlaybackProgress()` 保存播放位置

## 技术栈

- Kotlin 1.9.22, Compose BOM 2024.02.00, Material3
- Media3 ExoPlayer 1.2.1 + MediaSession
- OkHttp 4.12.0（WebDAV）
- DataStore Preferences 1.0.0
- compileSdk/targetSdk 34, minSdk 26
