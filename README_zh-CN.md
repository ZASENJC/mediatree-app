<p align="center">
  <img src="frontend/public/icon.svg" alt="MediaTree App" width="80" />
</p>

<h1 align="center">MediaTree App</h1>

<p align="center">
  <a href="README.md">English</a> | <strong>简体中文</strong>
</p>

<p align="center">
  <em>MediaTree 的 Android 原生客户端。<br>只构建 app，只连接已有 MediaTree 后端，不在 APK 内打包后端服务。</em>
</p>

<p align="center">
  <a href="https://github.com/ZASENJC/mediatree-app/blob/main/CHANGELOG_zh-CN.md"><img src="https://img.shields.io/badge/版本-0.1.00-blue?style=flat-square" alt="Version"></a>
  <a href="https://github.com/ZASENJC/mediatree-app/blob/main/LICENSE"><img src="https://img.shields.io/badge/许可证-MIT-green?style=flat-square" alt="License"></a>
  <img src="https://img.shields.io/badge/android-native-3DDC84?style=flat-square&logo=android" alt="Android">
  <img src="https://img.shields.io/badge/kotlin-compose-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin">
</p>

---

## 项目定位

`mediatree-app` 是客户端 app 仓库，目标是构建 Android 原生客户端并适配 MediaTree 后端 API。

- app 通过用户填写的服务器地址连接已有 MediaTree 后端
- APK 不内置 Python、FastAPI、SQLite 数据库、Docker 镜像或媒体扫描服务
- 后端部署、媒体库扫描、刮削、转码、字幕发现和 Jellyfin 兼容接口仍由独立 MediaTree 服务提供
- app 侧只负责登录、浏览、播放、收藏、进度同步、媒体库切换和移动端体验

---

## 当前功能

- 原生 Kotlin + Jetpack Compose UI
- Material 3 界面，半透明 overlay 顶栏和底栏会随滚动渐隐
- 服务器地址配置、认证状态检测、登录和 token 持久化
- 首页、文件夹浏览、混排收藏、详情和设置页
- 多媒体库读取与当前媒体库切换
- 搜索、排序、最近观看和文件夹入口
- Media3 ExoPlayer 播放，支持 Bearer token 请求头
- 外挂字幕轨道读取和选择
- 播放进度定时上报、完播标记和收藏标签切换
- 横屏沉浸播放、亮度/音量手势和基础播放控制
- 设置页包含后端连接、扫描触发、媒体库显示和本地 SMB 服务器草稿输入
- bundled native playback libraries 放在 `frontend/android/app/src/main/jniLibs/`

---

## 后端要求

请先单独部署或运行 MediaTree 后端服务，然后在 app 登录页填写服务器地址，例如：

```text
http://192.168.1.10:27580
```

app 当前依赖的主要 API 包括：

- `/api/auth/status`
- `/api/auth/login`
- `/api/media-roots`
- `/api/folders`
- `/api/movies`
- `/api/recent-watched`
- `/api/favorites`
- `/api/detail/{id}`
- `/api/progress/{id}`
- `/api/subtitle-tracks/{id}`
- `/api/media-info/{id}`
- `/api/movies/{id}/tags`
- `/api/scan`
- `/api/cover/{id}`
- `/api/episode-still/{id}`
- `/api/stream/{id}`
- `/api/subtitle/{id}/{trackIndex}`

---

## 构建

```bash
git clone https://github.com/ZASENJC/mediatree-app.git
cd mediatree-app/frontend
npm run android:build
```

构建成功后 APK 位于：

```text
frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

也可以直接进入 Android 工程构建：

```bash
cd frontend/android
./gradlew assembleDebug
```

---

## Android 环境

构建脚本会尝试自动发现 `JAVA_HOME` 和 `ANDROID_HOME`。如果本机没有配置，请手动设置：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

安装到已连接设备：

```bash
adb install -r frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 目录说明

```text
frontend/android/                                  Android 原生工程
frontend/android/app/src/main/java/.../data/       API、Session 和数据模型
frontend/android/app/src/main/java/.../ui/         Material 3 Compose UI、screen 和 navigation
frontend/android/app/src/main/java/.../player/     原生播放器层
frontend/android/app/src/main/jniLibs/             native playback libraries
frontend/scripts/build-android.sh                  Android debug APK 构建脚本
backend/                                          后端接口参考，不打包进 app
```

---

## 技术栈

**Android** — Kotlin · Jetpack Compose · Material 3 · Navigation Compose

**播放** — AndroidX Media3 ExoPlayer · bundled native playback libraries

**网络** — OkHttp · kotlinx.serialization

**图片** — Coil

**本地状态** — DataStore Preferences · AndroidX Security Crypto

---

## 开发原则

- 只构建客户端 app，不把后端运行时、数据库或 Docker 相关内容放进 APK
- 以后端现有 API 为合同，优先在 app 侧做兼容和降级
- Android 构建入口是 `npm run android:build` 或 `frontend/android/gradlew assembleDebug`
- 原生 Android 构建不需要 `npm install` 或 `node_modules`
- 不再使用 `cap sync android` 作为 app 构建步骤

---

## 文档

| 文档 | 说明 |
|---|---|
| [README.md](README.md) | English README |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
| [CHANGELOG_zh-CN.md](CHANGELOG_zh-CN.md) | 中文版本历史 |
| [AGENTS.md](AGENTS.md) | AI 辅助开发约束 |

---

## 许可证

MIT © [ZASENJC](https://github.com/ZASENJC)
