#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ANDROID_DIR="$SCRIPT_DIR/../android"

if [ -f "$SCRIPT_DIR/android-java-home.sh" ]; then
  # shellcheck disable=SC1091
  . "$SCRIPT_DIR/android-java-home.sh"
  JAVA_HOME=$(resolve_android_java_home || true)
  if [ -n "$JAVA_HOME" ]; then
    export JAVA_HOME
  fi
fi

if [ -z "${JAVA_HOME:-}" ]; then
  echo "JAVA_HOME is required. Install Android Studio JBR 21 or OpenJDK 21 before building Android." >&2
  exit 1
fi

if [ -z "${ANDROID_HOME:-}" ]; then
  for candidate in \
    "$HOME/Library/Android/sdk" \
    /opt/homebrew/share/android-commandlinetools \
    /usr/local/share/android-commandlinetools
  do
    if [ -d "$candidate/platforms" ] || [ -d "$candidate/cmdline-tools" ]; then
      export ANDROID_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${ANDROID_HOME:-}" ]; then
  echo "ANDROID_HOME is required. Install Android SDK command line tools first." >&2
  exit 1
fi

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

VARIANT="${1:-debug}"
case "$VARIANT" in
  debug)
    GRADLE_TASK=assembleDebug
    ;;
  release)
    GRADLE_TASK=assembleRelease
    ;;
  *)
    echo "Usage: $0 [debug|release]" >&2
    exit 1
    ;;
esac

if [ ! -f "$ANDROID_DIR/local.properties" ]; then
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ANDROID_DIR/local.properties"
fi

cd "$ANDROID_DIR"
exec ./gradlew "$GRADLE_TASK"
