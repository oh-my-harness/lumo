#!/bin/bash
# Lumo 真机调试脚本
# 用法: ./scripts/debug_device.sh [adb命令...]

set -euo pipefail

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# 自动检测已连接的设备
detect_device() {
  local devices=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
  echo "$devices" | head -1
}

DEVICE="${1:-$(detect_device)}"
APK="$PROJECT_ROOT/android/app/build/outputs/apk/debug/app-debug.apk"

if [ -z "$DEVICE" ]; then
  echo "❌ 没有已连接的设备。请先 adb connect。"
  echo "   用法: adb connect <ip>:<port>"
  exit 1
fi

echo "=== 设备: $DEVICE ==="

if [ ! -f "$APK" ]; then
  echo "=== 构建 APK ==="
  export JAVA_HOME ANDROID_SDK_ROOT
  cd "$PROJECT_ROOT/android"
  ./gradlew :app:assembleDebug --no-daemon 2>&1 | tail -5
fi

echo ""
echo "=== 安装 APK ==="
"$ADB" -s "$DEVICE" install -r "$APK"

echo ""
echo "=== 启动 App ==="
"$ADB" -s "$DEVICE" shell am start -n com.lumo.app/.MainActivity

echo ""
echo "=== 完成 ==="
echo "日志: $ADB -s $DEVICE logcat | grep -E 'Lumo|AndroidRuntime|Traceback'"
