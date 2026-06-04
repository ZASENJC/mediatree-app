#!/usr/bin/env bash
set -euo pipefail

file_limit_bytes="${TELEGRAM_FILE_LIMIT_BYTES:-50000000}"

required_vars=(
  TG_BOT_TOKEN
  TG_CHAT_ID
  VERSION
  APK_PATH
  APK_NAME
  CHECKSUM_PATH
  GITHUB_REPOSITORY
)

for var_name in "${required_vars[@]}"; do
  if [ -z "${!var_name:-}" ]; then
    echo "$var_name is required for Telegram release notifications." >&2
    exit 1
  fi
done

if [ ! -f "$APK_PATH" ]; then
  echo "APK not found: $APK_PATH" >&2
  exit 1
fi

if [ ! -f "$CHECKSUM_PATH" ]; then
  echo "Checksum file not found: $CHECKSUM_PATH" >&2
  exit 1
fi

api_url="https://api.telegram.org/bot${TG_BOT_TOKEN}"
github_server_url="${GITHUB_SERVER_URL:-https://github.com}"
release_url="${github_server_url}/${GITHUB_REPOSITORY}/releases/tag/${VERSION}"
asset_url="${github_server_url}/${GITHUB_REPOSITORY}/releases/download/${VERSION}/${APK_NAME}"
checksum_name="$(basename "$CHECKSUM_PATH")"
checksum_url="${github_server_url}/${GITHUB_REPOSITORY}/releases/download/${VERSION}/${checksum_name}"
checksum_value="$(awk '{ print $1; exit }' "$CHECKSUM_PATH")"
apk_size_bytes="$(wc -c < "$APK_PATH" | tr -d '[:space:]')"
apk_size_mb="$(awk -v bytes="$apk_size_bytes" 'BEGIN { printf "%.1f", bytes / 1000000 }')"

send_message() {
  local text="$1"

  curl --fail --silent --show-error --retry 3 --retry-delay 2 \
    --request POST "$api_url/sendMessage" \
    --data-urlencode "chat_id=$TG_CHAT_ID" \
    --data-urlencode "text=$text" \
    --data-urlencode "disable_web_page_preview=true" \
    >/dev/null
}

send_release_link() {
  local reason="$1"
  local text

  text="$(cat <<EOF
MediaTree App ${VERSION}
${reason}

APK: ${asset_url}
SHA-256: ${checksum_value}
Checksum: ${checksum_url}
Release: ${release_url}
EOF
)"

  send_message "$text"
}

if [ "$apk_size_bytes" -gt "$file_limit_bytes" ]; then
  send_release_link "APK is ${apk_size_mb} MB, over Telegram Bot API cloud upload limit; sending release links instead."
  exit 0
fi

caption="$(cat <<EOF
MediaTree App ${VERSION}
SHA-256: ${checksum_value}
Release: ${release_url}
EOF
)"

if curl --fail --silent --show-error --retry 3 --retry-delay 2 \
  --request POST "$api_url/sendDocument" \
  --form-string "chat_id=$TG_CHAT_ID" \
  --form "document=@${APK_PATH};filename=${APK_NAME}" \
  --form-string "caption=$caption" \
  >/dev/null; then
  exit 0
fi

send_release_link "APK upload to Telegram failed; sending release links instead."
