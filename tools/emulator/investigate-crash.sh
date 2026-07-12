#!/usr/bin/env bash
# Investigate the daemon crash: capture manual-start.log, stderr, tombstones.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

log "=== manual-start.log ==="
adb_cmd shell "cat ${ROOT_ZONE}/logs/manual-start.log 2>/dev/null" || warn "no manual-start.log"

log "=== stderr.log ==="
adb_cmd shell "cat ${ROOT_ZONE}/logs/stderr.log 2>/dev/null" || warn "no stderr.log"

log "=== today daemon log (full) ==="
adb_cmd shell "cat ${ROOT_ZONE}/logs/inject-$(adb_cmd shell date +%Y-%m-%d | tr -d \\r).log 2>/dev/null" || warn "no daemon log"

log "=== tombstones ==="
adb_cmd shell "ls -la /data/tombstones/ 2>/dev/null" || warn "no tombstones dir"

log "=== dropbox (voboost) ==="
adb_cmd shell "dumpsys dropbox --print 2>/dev/null | grep -iA8 voboost | head -60" || warn "no dropbox entries"

log "=== dmesg (voboost/abort) ==="
adb_cmd shell "dmesg 2>/dev/null | grep -iE 'voboost|abort|frida' | tail -20" || warn "dmesg not available"

log "=== daemon proc now ==="
adb_cmd shell "pgrep -fa voboost-inject" || warn "daemon NOT running"

log "=== investigate-crash.sh complete ==="
