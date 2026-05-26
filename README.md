<p align="center">
  <img src="frontend/public/icon.svg" alt="MediaTree App" width="80" />
</p>

<h1 align="center">MediaTree App</h1>

<p align="center">
  <strong>English</strong> | <a href="README_zh-CN.md">简体中文</a>
</p>

<p align="center">
  <em>Native Android client for MediaTree.<br>Builds the app only, connects to an existing MediaTree backend, and does not bundle backend services into the APK.</em>
</p>

<p align="center">
  <a href="https://github.com/ZASENJC/mediatree-app/blob/main/CHANGELOG.md"><img src="https://img.shields.io/badge/version-0.1.00-blue?style=flat-square" alt="Version"></a>
  <a href="https://github.com/ZASENJC/mediatree-app/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="License"></a>
  <img src="https://img.shields.io/badge/android-native-3DDC84?style=flat-square&logo=android" alt="Android">
  <img src="https://img.shields.io/badge/kotlin-compose-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin">
</p>

---

## Scope

`mediatree-app` is the client app repository. Its job is to build the Android app and adapt to the MediaTree backend API.

- The app connects to an existing MediaTree backend by server URL
- The APK does not bundle Python, FastAPI, SQLite databases, Docker images, scanners, or scraper services
- Backend deployment, scanning, scraping, transcoding, subtitle discovery, and Jellyfin-compatible APIs remain in the standalone MediaTree service
- The app focuses on login, browsing, playback, favorites, progress sync, library switching, and mobile UX

---

## Current Features

- Native Kotlin + Jetpack Compose UI
- Material 3 interface with translucent overlay top and bottom bars that fade with scroll
- Server URL setup, auth status detection, login, and persisted token storage
- Home, folder browsing, mixed favorites, detail, and settings screens
- Multi-library loading and active library switching
- Search, sort, recent watching, and folder entry points
- Media3 ExoPlayer playback with Bearer token headers
- External subtitle track loading and selection
- Periodic playback progress reporting, completion marking, and favorite tag toggling
- Landscape immersive playback, brightness/volume gestures, and basic controls
- Settings sections for backend connection, scan trigger, library display, and local SMB server draft input
- Bundled native playback libraries under `frontend/android/app/src/main/jniLibs/`

---

## Backend Requirement

Deploy or run the MediaTree backend separately, then enter its server URL in the app login screen, for example:

```text
http://192.168.1.10:27580
```

Main API endpoints currently used by the app:

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

## Build

```bash
git clone https://github.com/ZASENJC/mediatree-app.git
cd mediatree-app/frontend
npm run android:build
```

The debug APK is generated at:

```text
frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

You can also build directly from the Android project:

```bash
cd frontend/android
./gradlew assembleDebug
```

---

## Android Environment

The build script tries to discover `JAVA_HOME` and `ANDROID_HOME`. If needed, set them manually:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

Install to a connected device:

```bash
adb install -r frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Layout

```text
frontend/android/                                  Native Android project
frontend/android/app/src/main/java/.../data/       API client, session, and DTOs
frontend/android/app/src/main/java/.../ui/         Material 3 Compose UI, screens, and navigation
frontend/android/app/src/main/java/.../player/     Native playback layer
frontend/android/app/src/main/jniLibs/             Native playback libraries
frontend/scripts/build-android.sh                  Debug APK build script
backend/                                           Backend API reference only; not bundled into the app
```

---

## Tech Stack

**Android** — Kotlin · Jetpack Compose · Material 3 · Navigation Compose

**Playback** — AndroidX Media3 ExoPlayer · bundled native playback libraries

**Networking** — OkHttp · kotlinx.serialization

**Images** — Coil

**Local State** — DataStore Preferences · AndroidX Security Crypto

---

## Development Rules

- Build the client app only; do not place backend runtime, databases, or Docker assets into the APK
- Treat the existing backend API as the contract and prefer compatibility work on the app side
- Use `npm run android:build` or `frontend/android/gradlew assembleDebug` as the Android build entry point
- Native Android builds do not require `npm install` or `node_modules`
- Do not use `cap sync android` as part of the app build

---

## Documentation

| Document | Description |
|---|---|
| [README_zh-CN.md](README_zh-CN.md) | Chinese README |
| [CHANGELOG.md](CHANGELOG.md) | Version history |
| [CHANGELOG_zh-CN.md](CHANGELOG_zh-CN.md) | Chinese version history |
| [AGENTS.md](AGENTS.md) | AI-assisted development guide |

---

## License

MIT © [ZASENJC](https://github.com/ZASENJC)
