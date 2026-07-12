#!/usr/bin/env bash
# Diagnose why the voboost app did not write inject.json.
# Checks: app process, logcat errors, recent activity manager state.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

log "=== app process ==="
adb_cmd shell "ps -A | grep -i voboost" 2>&1 || warn "no voboost process found"

log "=== am start (foreground) ==="
adb_cmd shell am start -n ru.voboost/.MainActivity 2>&1 || die "am start failed"

log "=== waiting 8s ==="
sleep 8

log "=== app process after start ==="
adb_cmd shell "ps -A | grep -i voboost" 2>&1 || true

log "=== app zone after start ==="
adb_cmd shell "ls -la ${APP_ZONE}/" 2>&1 || true

log "=== inject.json ==="
adb_cmd shell "cat ${APP_ZONE}/inject.json 2>/dev/null" || warn "inject.json still missing"

log "=== inject-status.json ==="
adb_cmd shell "cat ${APP_ZONE}/inject-status.json 2>/dev/null" || warn "inject-status.json still missing"

log "=== logcat (voboost + errors, last 80 lines) ==="
adb_cmd logcat -d -t 80 2>&1 | grep -iE "voboost|AndroidRuntime|FATAL|Exception|inject" || warn "no matching logcat lines"

log "=== daemon log tail ==="
adb_cmd shell "tail -20 ${ROOT_ZONE}/logs/inject-$(adb_cmd shell date +%Y-%m-%d | tr -d \\r).log" 2>&1 || true

log "=== diagnose-app.sh complete ==="
