#!/usr/bin/env sh

resolve_android_java_home() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    case "$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n '1p')" in
      *'"26.'*)
        ;;
      *)
        printf '%s\n' "$JAVA_HOME"
        return 0
        ;;
    esac
  fi

  for candidate in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/Volumes/STU/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    /opt/homebrew/opt/openjdk@21 \
    /usr/local/opt/openjdk@21
  do
    if [ -x "$candidate/bin/java" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    candidate=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    if [ -n "$candidate" ] && [ -x "$candidate/bin/java" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi

  return 1
}
