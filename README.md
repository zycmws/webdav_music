# WebDAV Music Player

一个支持本地音乐与 WebDAV 服务器的 Android 音乐播放器，采用 Jetpack Compose 构建的 Material Design 3 界面。

## 功能特性

- **本地音乐播放**：通过存储访问框架（SAF）浏览并播放设备本地音乐文件
- **WebDAV 流媒体**：连接 WebDAV 服务器，直接在线流媒体播放，无需下载完整文件
- **后台播放**：基于前台服务的持久播放，支持系统媒体控件和通知栏控制
- **播放模式**：
  - 顺序 / 随机播放
  - 单曲循环 / 列表循环 / 不循环
  - 播放进度自动保存与恢复
- **自动暂停**：蓝牙耳机断开或耳机拔出时自动暂停播放
- **深色主题**：跟随系统主题自动切换浅色 / 深色模式
- **中文界面**：完整的中文用户界面

## 技术栈

- **语言**：Kotlin 1.9.22
- **UI**：Jetpack Compose (BOM 2024.02.00) + Material3
- **媒体播放**：Media3 ExoPlayer 1.2.1 + MediaSession
- **网络**：OkHttp 4.12.0（WebDAV PROPFIND / Basic Auth）
- **数据持久化**：DataStore Preferences 1.0.0
- **架构**：MVVM + 手动依赖注入
- **最低 SDK**：26 (Android 8.0) | **目标 SDK**：34

## 构建与运行

```bash
# 构建调试 APK
./gradlew assembleDebug

# 构建并安装到已连接设备
./gradlew installDebug

# 仅编译 Kotlin（快速验证）
./gradlew compileDebugKotlin

# 运行单元测试
./gradlew test
```

## 项目结构

```
com.webdav.music
├── data
│   ├── model/          # 数据类：MusicItem, PlayerState, WebDAVConfig, RepeatMode
│   ├── local/          # PreferencesManager（DataStore 偏好设置）
│   ├── source/         # LocalMusicDataSource / WebDAVDataSource
│   └── repository/     # MusicRepository（统一数据访问）
├── player/             # AudioPlayerService + CustomForwardingPlayer
├── ui
│   ├── components/     # 共享组件：PlayerControls, TrackList, SearchBar, MiniPlayer...
│   └── screens/        # OnboardingScreen, SettingsScreen
├── MainViewModel.kt    # 核心状态与播放协调
└── MainScreen.kt       # 主界面（底部导航 + 迷你/展开播放器）
```

## 播放架构

- **MainViewModel** 通过 `ServiceConnection` 绑定 **AudioPlayerService**，直接通过 Binder 调用播放控制
- **CustomForwardingPlayer** 包装 ExoPlayer，强制启用上/下一曲命令并处理切歌期间的 MediaSession 状态
- 播放完成由 ExoPlayer `STATE_ENDED` 触发，经 StateFlow 通知 ViewModel 决定下一曲逻辑
- WebDAV 流媒体通过 ExoPlayer 的 `DefaultHttpDataSource` 携带 Basic Auth Header 实现

## 开源协议

MIT License
