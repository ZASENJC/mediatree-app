# MediaTree Android

Native Android client for MediaTree.

This project builds the Android app only. It connects to an existing MediaTree backend by server URL and does not bundle backend services, Python runtime, SQLite data, Docker assets, scanners, or scrapers into the APK.

## Build

From the repository root:

```bash
sh frontend/scripts/build-android.sh
```

Or directly from this Android project:

```bash
./gradlew assembleDebug
```

If needed, configure Android tooling first:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

The project build entrypoints prefer Android Studio 自带 JBR 21 automatically. If you need to set Java manually, point `JAVA_HOME` at Android Studio's bundled runtime or another JDK 21 install:

```bash
export JAVA_HOME="/Volumes/STU/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Runtime

On first launch, enter the MediaTree backend URL, for example:

```text
http://192.168.1.10:27580
```

The app uses the backend API for auth, library browsing, metadata, subtitles, streaming, and playback progress.

## Player

Playback is implemented with the mpv native bridge. Native playback libraries are bundled under `app/src/main/jniLibs/<abi>/` and should stay limited to client playback dependencies.
