#!/usr/bin/env bash
# Called by semantic-release (@semantic-release/exec prepareCmd) once the next version is known.
# Builds and verifies the signed release APK that gets attached to the GitHub release.
set -euo pipefail

VERSION="${1:?usage: release-prepare.sh <version>}"

# Stamp the version into gradle.properties; @semantic-release/git commits it afterwards.
sed -i -E "s/^version[[:space:]]*=.*/version = ${VERSION}/" gradle.properties
grep -Eq "^version[[:space:]]*=[[:space:]]*${VERSION}[[:space:]]*$" gradle.properties

# Signing config comes from keystore.properties, which the workflow writes from secrets.
test -f keystore.properties

# versionCode must grow with every release, prereleases included; the run number does.
./gradlew assembleRelease --no-daemon "-PversionCode=${GITHUB_RUN_NUMBER:?must run in CI}"

APK_DIR=app/build/outputs/apk/release
APK="$(ls "$APK_DIR"/*.apk | head -n 1)"
unzip -l "$APK" | grep 'classes.dex' >/dev/null
echo "$APK: built"

rm -rf release-assets
mkdir release-assets
cp "$APK" "release-assets/chobi-${VERSION}.apk"
