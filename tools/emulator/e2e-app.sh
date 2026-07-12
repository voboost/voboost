#!/usr/bin/env bash
# End-to-end app setup: remove all third-party apps, install the voboost APK,
# grant permissions, launch the app, and wait for inject.json.
#
#   tools/emulator/e2e-app.sh
#
# Prereq: provision.sh has run (daemon is up, adbd is root).
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

APK="${VOBOOST_ROOT}/build/outputs/apk/release/voboost.apk"
[[ -f "${APK}" ]] || die "app APK not found at ${APK} (run './gradlew assembleRelease' first)"

log "=== listing third-party packages ==="
adb_cmd shell "pm list packages -3" || true

log "=== removing ALL third-party packages ==="
# Uninstall every third-party package so stale apps do not interfere.
pkgs="$(adb_cmd shell 'pm list packages -3 | cut -d: -f2' | tr -d '\r')"
for p in ${pkgs}; do
  log "  uninstall: ${p}"
  adb_cmd uninstall "${p}" >/dev/null 2>&1 || warn "failed to uninstall ${p}"
done

log "=== third-party packages after cleanup ==="
adb_cmd shell "pm list packages -3" || true

log "=== installing voboost app APK ==="
adb_cmd install -r -t "${APK}" || die "app install failed"
adb_cmd shell "pm list packages ru.voboost"

log "=== granting permissions ==="
adb_cmd shell pm grant ru.voboost android.permission.SYSTEM_ALERT_WINDOW 2>/dev/null || warn "SYSTEM_ALERT_WINDOW grant failed"
adb_cmd shell pm grant ru.voboost android.permission.READ_LOGS 2>/dev/null || warn "READ_LOGS grant failed"
adb_cmd shell pm grant ru.voboost android.permission.RECORD_AUDIO 2>/dev/null || warn "RECORD_AUDIO grant failed"
adb_cmd shell pm grant ru.voboost android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || warn "WRITE_EXTERNAL_STORAGE grant failed"
adb_cmd shell pm grant ru.voboost android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || warn "WRITE_SECURE_SETTINGS grant failed"
adb_cmd shell appops set ru.voboost REQUEST_INSTALL_PACKAGES allow 2>/dev/null || warn "REQUEST_INSTALL_PACKAGES op failed"

log "=== launching app ==="
adb_cmd shell am start -n ru.voboost/.MainActivity || die "failed to launch app"

log "=== waiting 10s for app to write inject.json ==="
sleep 10

log "=== app zone ==="
adb_cmd shell "ls -la ${APP_ZONE}/" 2>&1 || warn "app zone not accessible"

log "=== inject.json (app->daemon plan) ==="
adb_cmd shell "cat ${APP_ZONE}/inject.json 2>/dev/null" || warn "inject.json not found"

log "=== inject-status.json (daemon->app status) ==="
adb_cmd shell "cat ${APP_ZONE}/inject-status.json 2>/dev/null" || warn "inject-status.json not found"

log "=== daemon log tail ==="
adb_cmd shell "tail -30 ${ROOT_ZONE}/logs/inject-$(adb_cmd shell date +%Y-%m-%d | tr -d \\r).log" 2>&1 || true

log "=== e2e-app.sh complete ==="
