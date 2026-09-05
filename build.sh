#!/usr/bin/env bash
# ==============================================================================
# Voice Assist - GitHub & Local Build Automation Script
# ==============================================================================
# This script automates building the Android APK/AAB in CI/CD (such as GitHub
# Actions) and local developer workstations.
#
# Usage:
#   ./build.sh [debug|release|bundle|test|all]
#
# Default is 'debug' which builds the installable debug APK.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

TARGET="${1:-debug}"

echo "=================================================="
echo " Voice Assist Android Builder"
echo " Target: ${TARGET}"
echo " Root directory: ${SCRIPT_DIR}"
echo "=================================================="

# 1. Check / Set up Java
if ! command -v java &>/dev/null; then
  echo "Error: Java is not installed or not in PATH."
  echo "Please install JDK 17 or JDK 21 (e.g. Temurin 21)."
  exit 1
fi

JAVA_VER="$(java -version 2>&1 | head -n 1)"
echo "Using Java: ${JAVA_VER}"

# 2. Restore debug.keystore if missing (GitHub checkouts omit gitignored files)
if [ ! -f "debug.keystore" ]; then
  if [ -f "debug.keystore.base64" ]; then
    echo "Restoring debug.keystore from debug.keystore.base64..."
    if base64 --decode < "debug.keystore.base64" > "debug.keystore" 2>/dev/null; then
      echo "Debug keystore restored successfully."
    elif base64 -d < "debug.keystore.base64" > "debug.keystore" 2>/dev/null; then
      echo "Debug keystore restored successfully."
    else
      echo "Warning: Failed to decode debug.keystore.base64 with standard base64."
    fi
  fi
fi

# Fallback: if debug.keystore still does not exist, generate one with keytool
if [ ! -f "debug.keystore" ]; then
  echo "Generating standard debug.keystore using keytool..."
  keytool -genkey -v \
    -keystore debug.keystore \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null || true
fi

# 3. Prepare .env file for the Secrets Gradle Plugin
if [ ! -f ".env" ]; then
  if [ -f ".env.example" ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
  else
    echo "Creating empty .env file..."
    touch .env
  fi
fi

# If GEMINI_API_KEY environment variable is set in GitHub Secrets/environment, append or update it in .env
if [ -n "${GEMINI_API_KEY:-}" ]; then
  echo "Injecting GEMINI_API_KEY into .env..."
  # Remove existing GEMINI_API_KEY entry if present
  sed -i.bak '/^GEMINI_API_KEY=/d' .env 2>/dev/null || true
  rm -f .env.bak
  echo "GEMINI_API_KEY=${GEMINI_API_KEY}" >> .env
fi

# 4. Determine Gradle executable
GRADLE_CMD=""
if [ -f "./gradlew" ]; then
  chmod +x ./gradlew
  GRADLE_CMD="./gradlew"
elif command -v gradle &>/dev/null; then
  GRADLE_CMD="gradle"
else
  echo "Error: Neither ./gradlew nor system 'gradle' command was found."
  echo "Please install Gradle or generate wrapper via 'gradle wrapper'."
  exit 1
fi

echo "Using Gradle: ${GRADLE_CMD}"
echo "--------------------------------------------------"

# 5. Execute requested target
case "${TARGET}" in
  debug)
    echo "Building Debug APK..."
    ${GRADLE_CMD} :app:assembleDebug --no-daemon
    echo ""
    echo "=================================================="
    echo "BUILD SUCCESSFUL!"
    echo "Debug APK located at:"
    find app/build/outputs/apk/debug -name "*.apk" -print 2>/dev/null || true
    echo "=================================================="
    ;;

  release)
    echo "Building Release APK..."
    ${GRADLE_CMD} :app:assembleRelease --no-daemon
    echo ""
    echo "=================================================="
    echo "BUILD SUCCESSFUL!"
    echo "Release APK located at:"
    find app/build/outputs/apk/release -name "*.apk" -print 2>/dev/null || true
    echo "=================================================="
    ;;

  bundle)
    echo "Building Release Android App Bundle (AAB)..."
    ${GRADLE_CMD} :app:bundleRelease --no-daemon
    echo ""
    echo "=================================================="
    echo "BUILD SUCCESSFUL!"
    echo "Release AAB located at:"
    find app/build/outputs/bundle/release -name "*.aab" -print 2>/dev/null || true
    echo "=================================================="
    ;;

  test)
    echo "Running Unit & Robolectric Tests..."
    ${GRADLE_CMD} :app:testDebugUnitTest --no-daemon
    echo ""
    echo "=================================================="
    echo "ALL TESTS PASSED!"
    echo "Test report: app/build/reports/tests/testDebugUnitTest/index.html"
    echo "=================================================="
    ;;

  all)
    echo "Running Tests and Building Debug APK..."
    ${GRADLE_CMD} :app:testDebugUnitTest :app:assembleDebug --no-daemon
    echo ""
    echo "=================================================="
    echo "ALL TESTS PASSED AND DEBUG APK BUILT!"
    find app/build/outputs/apk/debug -name "*.apk" -print 2>/dev/null || true
    echo "=================================================="
    ;;

  *)
    echo "Unknown target: ${TARGET}"
    echo "Supported targets: debug | release | bundle | test | all"
    exit 1
    ;;
esac
