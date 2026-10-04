#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "== FocusFlow debug APK build (JDK bootstrap) =="
echo "Started: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "ERROR: This bootstrap script currently supports Linux only." >&2
  exit 1
fi

case "$(uname -m)" in
  x86_64|amd64) ADOPTIUM_ARCH="x64" ;;
  aarch64|arm64) ADOPTIUM_ARCH="aarch64" ;;
  *)
    echo "ERROR: Unsupported CPU architecture: $(uname -m)" >&2
    exit 1
    ;;
esac

for tool in curl tar readlink; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "ERROR: Required tool '$tool' was not found in PATH." >&2
    exit 1
  fi
done

java_major_version() {
  local java_bin="$1"
  "$java_bin" -XshowSettings:properties -version 2>&1 \
    | sed -nE 's/^[[:space:]]*java.specification.version = ([0-9]+).*$/\1/p' \
    | head -n 1
}

java_home_for() {
  local resolved_java
  resolved_java="$(readlink -f "$1")"
  cd -P "$(dirname "$resolved_java")/.." && pwd
}

JAVA_BIN=""
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
fi
if [[ -z "$JAVA_BIN" ]] || [[ "$(java_major_version "$JAVA_BIN")" != "17" ]]; then
  if command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
  fi
fi

if [[ -n "$JAVA_BIN" ]] && [[ "$(java_major_version "$JAVA_BIN")" == "17" ]]; then
  JAVA_HOME="$(java_home_for "$JAVA_BIN")"
  echo "Using existing JDK 17 at $JAVA_HOME"
else
  CACHE_BASE="${XDG_CACHE_HOME:-${HOME:-}}"
  if [[ -z "$CACHE_BASE" ]]; then
    echo "ERROR: HOME and XDG_CACHE_HOME are both unset; cannot cache JDK 17." >&2
    exit 1
  fi

  CACHE_DIR="$CACHE_BASE/focusflow/android-build"
  CACHED_JDK="$CACHE_DIR/jdk-17"
  mkdir -p "$CACHE_DIR"

  if [[ -x "$CACHED_JDK/bin/java" ]] && [[ "$(java_major_version "$CACHED_JDK/bin/java")" == "17" ]]; then
    JAVA_HOME="$CACHED_JDK"
    echo "Using cached JDK 17 at $JAVA_HOME"
  else
    for tool in mktemp find; do
      if ! command -v "$tool" >/dev/null 2>&1; then
        echo "ERROR: Required tool '$tool' was not found in PATH." >&2
        exit 1
      fi
    done

    DOWNLOAD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/focusflow-jdk17.XXXXXX")"
    trap 'rm -rf "$DOWNLOAD_DIR"' EXIT
    mkdir -p "$DOWNLOAD_DIR/extracted"

    JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/${ADOPTIUM_ARCH}/jdk/hotspot/normal/eclipse"
    echo "No JDK 17 found; downloading one from Adoptium..."
    curl --fail --location --retry 3 --connect-timeout 20 \
      "$JDK_URL" --output "$DOWNLOAD_DIR/jdk-17.tar.gz"
    tar -xzf "$DOWNLOAD_DIR/jdk-17.tar.gz" -C "$DOWNLOAD_DIR/extracted"

    EXTRACTED_JDK="$(find "$DOWNLOAD_DIR/extracted" -mindepth 1 -maxdepth 1 -type d -print -quit)"
    if [[ -z "$EXTRACTED_JDK" || ! -x "$EXTRACTED_JDK/bin/java" ]]; then
      echo "ERROR: The downloaded archive did not contain a usable JDK." >&2
      exit 1
    fi
    if [[ "$(java_major_version "$EXTRACTED_JDK/bin/java")" != "17" ]]; then
      echo "ERROR: The downloaded Java runtime is not JDK 17." >&2
      exit 1
    fi

    STAGING_JDK="$CACHE_DIR/.jdk-17-install-$$"
    rm -rf "$STAGING_JDK"
    mv "$EXTRACTED_JDK" "$STAGING_JDK"
    if [[ -e "$CACHED_JDK" ]]; then
      rm -rf "$CACHED_JDK"
    fi
    mv "$STAGING_JDK" "$CACHED_JDK"
    JAVA_HOME="$CACHED_JDK"
  fi
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f local.properties ]]; then
  echo "NOTE: Android SDK was not detected. Gradle still requires SDK Platform 35 to build the APK." >&2
fi

./gradlew :app:assembleDebug --no-daemon --console=plain --stacktrace "$@"

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo "ERROR: Expected APK was not produced at $APK_PATH" >&2
  exit 1
fi

echo "APK: $APK_PATH"
sha256sum "$APK_PATH"
echo "Finished: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"