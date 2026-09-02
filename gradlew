#!/usr/bin/env sh
set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=8.9
BOOTSTRAP_DIR="$APP_HOME/.gradle-bootstrap"
GRADLE_HOME="$BOOTSTRAP_DIR/gradle-$GRADLE_VERSION"
ARCHIVE="$BOOTSTRAP_DIR/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    mkdir -p "$BOOTSTRAP_DIR"
    if [ ! -f "$ARCHIVE" ]; then
        curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ARCHIVE"
    fi
    unzip -q -o "$ARCHIVE" -d "$BOOTSTRAP_DIR"
fi

exec "$GRADLE_HOME/bin/gradle" "$@"
