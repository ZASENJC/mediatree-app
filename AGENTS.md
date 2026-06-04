# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Scope

- This repository is for `mediatree-app`, the Android client app for MediaTree.
- Build the app only. Do not package, embed, start, or ship the backend inside the APK.
- The app adapts to existing media-server APIs by server URL, starting with MediaTree backend compatibility. Backend deployment, media scanning, scraping, transcoding, database storage, and Jellyfin compatibility remain server-side.
- The Git tree should contain app code only. Local backend/upstream reference files belong under ignored `_reference/` directories, and must not be added back to Git or modified unless the user explicitly asks for backend changes.
- Avoid adding Docker image build/push workflows for app releases. App release artifacts should be APK/AAB or app-specific assets.

## Push Workflow

- Before each push, sync `AGENTS.md`, `CHANGELOG.md`, `CHANGELOG_zh-CN.md`, `README.md`, and `README_zh-CN.md` to reflect the current app state. Treat this as a mandatory pre-push gate, not an optional documentation pass.
- Include documentation updates in the same commit; do not commit them separately.
- README files should present MediaTree App as an independent Android media product, use the repository logo at `docs/assets/mediatree-logo.png`, keep screenshot placeholders current, include a prominent APK download entry, and describe only app-side capabilities that exist in the current Android code.
- Version rule: use `0.0.00` three-level format without `v` prefix where this repo controls release versions. Increment sequentially and do not skip major/minor version numbers.
- Android app versions live in `frontend/android/app/build.gradle` (`versionCode` and `versionName`). Keep release notes synced with the app version that is actually built.
- Telegram release notifications must use GitHub Actions secrets (`TG_BOT_TOKEN` and `TG_CHAT_ID`). Do not hardcode bot tokens, chat IDs, or credentials in tracked files.

## Interaction Language Rules

- All user-facing explanations, plans, summaries, question inquiries, and change reports must use Chinese.
- Keep code identifiers, file names, paths, commands, config keys, API paths, class names, function names, and error logs in their original English.
- Do not translate English API names, function names, class names, module names, or Android/Gradle terms.

## Commands

### Android App

```bash
# Build native Android debug APK
sh frontend/scripts/build-android.sh

# Build directly from Gradle
cd frontend/android && ./gradlew assembleDebug

# Run Android unit tests
cd frontend/android && ./gradlew testDebugUnitTest
```

The debug APK is generated at:

```text
frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

### Backend Reference Checks

The backend is not part of the app package or Git tree. If a local reference checkout exists under `_reference/`, run backend tests only when a task explicitly touches backend compatibility assumptions or backend reference code:

```bash
cd backend && PYTHONPATH=. python -m unittest discover -s tests -p 'test_*.py'
```

This project expects Python 3.12 if backend tests are run.

## Architecture

### Android native client

- `frontend/android/` — native Android project.
- `frontend/android/app/src/main/java/com/zasenjc/mediatree/MainActivity.kt` — Compose entrypoint and deep-link handling.
- `frontend/android/app/src/main/java/com/zasenjc/mediatree/data/` — API client, DTOs, session persistence, and ViewModel factory helpers.
- `frontend/android/app/src/main/java/com/zasenjc/mediatree/ui/` — Compose shell, theme, components, navigation, and screens.
- `frontend/android/app/src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt` — mpv playback UI layer, subtitle selection, gestures, progress callbacks.
- `frontend/android/app/src/main/java/com/zasenjc/mediatree/util/UrlUtils.kt` — server URL normalization and API URL helpers.
- `frontend/android/app/src/main/jniLibs/` — bundled native playback libraries. Keep ABI contents intentional and documented.

### Backend API contract

The app connects to an already-running MediaTree backend. Primary API usage is implemented in `MediaTreeApi.kt` and currently includes:

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

Prefer app-side compatibility for response shape differences. If an API mismatch requires backend changes, call that out clearly before editing backend files.

### App screens

- `HomeScreen.kt` — MD3 home feed, recent watching, poster grid, search, sort, and scroll-aware overlay chrome.
- `BrowseScreen.kt` — folder browsing, movie lists, breadcrumbs, search, sort, and scroll-aware overlay chrome.
- `FavoritesScreen.kt` — favorite-tagged media with mixed episode/poster grid and scroll-aware overlay chrome.
- `DetailScreen.kt` — player, subtitle selector, metadata, favorite/watched actions, cast, episodes, stills, and staff.
- `SettingsScreen.kt` — backend connection, logout, SMB draft UI, active library, and scan trigger.
- `LoginScreen.kt` — server URL and credential login flow.

## Packaging Boundaries

- Android builds must not include backend source, Python dependencies, SQLite data, Docker compose files, or server runtime artifacts.
- `frontend/scripts/build-android.sh` is the app build entry point and should run Gradle only.
- Do not use `cap sync android` as part of the app build. This native app does not require copying Vite web assets into the Android package.
- Keep backend, Docker, deployment, database, and legacy web frontend files out of Git tracking. If present locally, keep them under `_reference/` and use them only as reference.
- Keep generated build output ignored: `frontend/android/build/`, `frontend/android/app/build/`, `.gradle/`, `local.properties`, and copied web assets.

## Where To Modify For Common Tasks

- New backend endpoint consumption: `frontend/android/app/src/main/java/com/zasenjc/mediatree/data/MediaTreeApi.kt` + `Models.kt`
- Login/session behavior: `SessionStore.kt`, `LoginScreen.kt`, `MediaTreeApp.kt`
- Server URL handling: `UrlUtils.kt`
- Player/subtitles/progress: `MediaTreePlayer.kt`, `DetailScreen.kt`
- App navigation: `MediaTreeApp.kt`, `TopDestinations.kt`
- Visual design: `Theme.kt`, `SharedComponents.kt`, screen files under `ui/screens/`
- Android release/build settings: `frontend/android/app/build.gradle`
- Native libraries: `frontend/android/app/src/main/jniLibs/`

## Testing Expectations

- For app UI/API changes, at minimum run:

```bash
cd frontend/android && ./gradlew assembleDebug
```

- For model, URL, or utility changes, run the relevant Android unit tests:

```bash
cd frontend/android && ./gradlew testDebugUnitTest
```

## Notes

- The app currently allows cleartext HTTP for LAN servers via `android:usesCleartextTraffic="true"` and `server` URL normalization.
- Treat local untracked files and user edits as user-owned. Do not revert or delete them unless explicitly requested.
- Use `rg` / `rg --files` for searching whenever possible.
