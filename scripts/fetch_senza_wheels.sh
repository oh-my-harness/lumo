#!/usr/bin/env bash
set -euo pipefail

# Fetch Senza Android wheels from GitHub Releases.
#
# Wheels are built by Senza CI (tag push) and published to GitHub Releases.
# This script downloads them into android/senza-wheel/ so Chaquopy can
# pip install them during the Gradle build.
#
# Usage:
#   ./scripts/fetch_senza_wheels.sh           # latest release
#   ./scripts/fetch_senza_wheels.sh v1.0.1    # specific version

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WHEEL_DIR="$SCRIPT_DIR/../android/senza-wheel"
SENZA_REPO="oh-my-harness/Senza"
VERSION="${1:-latest}"

mkdir -p "$WHEEL_DIR"

echo "==> Fetching Senza Android wheels from GitHub Releases ($VERSION)..."

if command -v gh &>/dev/null; then
    # Use GitHub CLI if available
    if [ "$VERSION" = "latest" ]; then
        gh release download --repo "$SENZA_REPO" --latest \
            --pattern 'senza_sdk-*-android_*.whl' \
            --dir "$WHEEL_DIR" --clobber
    else
        gh release download --repo "$SENZA_REPO" "$VERSION" \
            --pattern 'senza_sdk-*-android_*.whl' \
            --dir "$WHEEL_DIR" --clobber
    fi
else
    # Fallback: use curl + GitHub API
    if [ "$VERSION" = "latest" ]; then
        API_URL="https://api.github.com/repos/$SENZA_REPO/releases/latest"
    else
        API_URL="https://api.github.com/repos/$SENZA_REPO/releases/tags/$VERSION"
    fi

    ASSETS=$(curl -fsSL "$API_URL" | grep -o '"browser_download_url": *"[^"]*android[^"]*\.whl"' | sed 's/"browser_download_url": *"//;s/"$//')

    if [ -z "$ASSETS" ]; then
        echo "ERROR: No Android wheels found in release $VERSION"
        echo "       Install GitHub CLI (gh) or check the release page:"
        echo "       https://github.com/$SENZA_REPO/releases"
        exit 1
    fi

    for url in $ASSETS; do
        echo "  Downloading $(basename "$url")..."
        curl -fsSL -o "$WHEEL_DIR/$(basename "$url")" "$url"
    done
fi

echo ""
echo "==> Downloaded wheels:"
ls -1 "$WHEEL_DIR"/*.whl 2>/dev/null || echo "  (none)"
echo ""
echo "==> Next: build the app with ./gradlew assembleDebug"
