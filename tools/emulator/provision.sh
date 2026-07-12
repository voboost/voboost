#!/usr/bin/env bash
# Provision a booted emulator with the daemon, its agents, and the signed manifest.
# Lean raw-adb path (the installer path is install.sh). Two start modes:
#   --manual-start  (default) run the daemon from a root shell now (fast, no reboot)
#   --init-hook     install /system/etc/init/voboost.rc and start the service
#
#   tools/emulator/provision.sh [--manual-start|--init-hook] [--no-app] [--no-stubs]
#
# Artifacts are resolved from env (see lib/common.sh) or auto-built where possible.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

START_MODE=manual-start
INSTALL_APP=1
INSTALL_STUBS=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --manual-start) START_MODE=manual-start; shift ;;
    --init-hook)    START_MODE=init-hook; shift ;;
    --no-app)       INSTALL_APP=0; shift ;;
    --no-stubs)     INSTALL_STUBS=0; shift ;;
    *) die "unknown arg: $1" ;;
  esac
done

# ---------------------------------------------------------------------------
# Resolve daemon artifacts
# ---------------------------------------------------------------------------

resolve_daemon_bin() {
  # 1. explicit env
  if [[ -n "${DAEMON_BIN:-}" && -x "${DAEMON_BIN}" ]]; then echo "${DAEMON_BIN}"; return; fi
  # 2. arm64 cross build outputs
  local d
  for d in \
    "${VOBOOST_INJECT_DIR}/build-android/voboost-inject" \
    "${VOBOOST_INJECT_DIR}/build-android/src/voboost-inject"; do
    if [[ -x "$d" ]]; then echo "$d"; return; fi
  done
  # 3. try to build it (needs NDK + meson cross file)
  log "arm64 daemon binary not found; attempting 'make build-android'..."
  if make -C "${VOBOOST_INJECT_DIR}" build-android >/dev/null 2>&1; then
    for d in \
      "${VOBOOST_INJECT_DIR}/build-android/voboost-inject" \
      "${VOBOOST_INJECT_DIR}/build-android/src/voboost-inject"; do
      if [[ -x "$d" ]]; then echo "$d"; return; fi
    done
  fi
  die "arm64 daemon binary not found and build-android failed. Set DAEMON_BIN or run 'make -C ${VOBOOST_INJECT_DIR} build-android'."
}

resolve_agents_dir() {
  [[ -n "${AGENTS_DIR:-}" && -d "${AGENTS_DIR}" ]] && { echo "${AGENTS_DIR}"; return; }
  [[ -d "${VOBOOST_SCRIPT_DIR}/build" ]] && { echo "${VOBOOST_SCRIPT_DIR}/build"; return; }
  die "agents dir not found. Set AGENTS_DIR or build voboost-script (npm run build)."
}

resolve_manifest() {
  # manifest.json + manifest.sig (app-signed daemon manifest).
  local m="${MANIFEST:-${DAEMON_ARTIFACTS_DIR}/manifest.json}"
  local s="${MANIFEST_SIG:-${DAEMON_ARTIFACTS_DIR}/manifest.sig}"
  # Fallback: the dev fixture in voboost-inject (agents must match its hashes).
  if [[ ! -f "$m" ]]; then
    m="${VOBOOST_INJECT_DIR}/test/fixtures/manifest.json"
    s="${VOBOOST_INJECT_DIR}/test/fixtures/manifest.sig"
    warn "MANIFEST not set; using dev fixture ${m} (its sha256 must match staged agents)."
  fi
  [[ -f "$m" ]] || die "manifest.json not found at ${m}"
  [[ -f "$s" ]] || die "manifest.sig not found at ${s} (sign it: make -C ${VOBOOST_INJECT_DIR} sign KEY=<dev.pem> FILE=<manifest.json>)"
  printf '%s\n%s\n' "$m" "$s"
}

# ---------------------------------------------------------------------------
# Provision
# ---------------------------------------------------------------------------

wait_for_boot
root_and_permissive

DAEMON_BIN="$(resolve_daemon_bin)"
AGENTS_DIR="$(resolve_agents_dir)"
# resolve_manifest prints two lines (manifest path, sig path); read each
# separately so both are captured (a single `read -r A B` only gets line 1).
{ read -r MANIFEST; read -r MANIFEST_SIG; } < <(resolve_manifest)
log "daemon:   ${DAEMON_BIN}"
log "agents:   ${AGENTS_DIR}"
log "manifest: ${MANIFEST} (+ sig)"

log "creating root zone ${ROOT_ZONE} (700)..."
adb_cmd shell "su 0 sh -c '
  mkdir -p ${ROOT_ZONE}/agents ${ROOT_ZONE}/logs ${ROOT_ZONE}/run &&
  chown root:root ${ROOT_ZONE} ${ROOT_ZONE}/agents ${ROOT_ZONE}/logs ${ROOT_ZONE}/run &&
  chmod 700 ${ROOT_ZONE} ${ROOT_ZONE}/agents ${ROOT_ZONE}/logs ${ROOT_ZONE}/run
'" || die "failed to create root zone"

log "pushing daemon binary..."
adb_cmd push "${DAEMON_BIN}" "${ROOT_ZONE}/voboost-inject" >/dev/null
adb_cmd shell "su 0 sh -c 'chown root:root ${ROOT_ZONE}/voboost-inject && chmod 700 ${ROOT_ZONE}/voboost-inject'"

log "pushing agents..."
# Push each agent .js to /data/voboost/agents/. The manifest references these paths.
adb_cmd push "${AGENTS_DIR}" "${ROOT_ZONE}/agents-tmp" >/dev/null 2>&1 || \
  adb_cmd shell "su 0 sh -c 'mkdir -p ${ROOT_ZONE}/agents'"
# adb push of a dir lays it under <dest>/; flatten into agents/.
adb_cmd shell "su 0 sh -c '
  if [ -d ${ROOT_ZONE}/agents-tmp ]; then
    cp -r ${ROOT_ZONE}/agents-tmp/. ${ROOT_ZONE}/agents/ 2>/dev/null || true;
    rm -rf ${ROOT_ZONE}/agents-tmp;
  fi
  chown -R root:root ${ROOT_ZONE}/agents; chmod -R 600 ${ROOT_ZONE}/agents/*.js 2>/dev/null || true
'" || warn "agents copy had issues"

log "pushing signed manifest..."
adb_cmd push "${MANIFEST}" "${ROOT_ZONE}/manifest.json" >/dev/null
adb_cmd push "${MANIFEST_SIG}" "${ROOT_ZONE}/manifest.sig" >/dev/null
adb_cmd shell "su 0 sh -c 'chown root:root ${ROOT_ZONE}/manifest.json ${ROOT_ZONE}/manifest.sig; chmod 600 ${ROOT_ZONE}/manifest.json ${ROOT_ZONE}/manifest.sig'"

# Prepare the app zone so the daemon can write inject-status.json into it.
log "ensuring app zone ${APP_ZONE} exists..."
adb_cmd shell "su 0 sh -c 'mkdir -p ${APP_ZONE}/staging ${APP_ZONE}/logs && chown -R \$(stat -c %u ${APP_ZONE}) ${APP_ZONE}/staging ${APP_ZONE}/logs 2>/dev/null || true'" || warn "app zone prep had issues"

# ---------------------------------------------------------------------------
# Start the daemon
# ---------------------------------------------------------------------------

stop_running_daemon() {
  adb_cmd shell "su 0 sh -c 'pgrep -f ${ROOT_ZONE}/voboost-inject | xargs -r kill' 2>/dev/null" || true
}

case "${START_MODE}" in
  manual-start)
    log "starting daemon manually (root shell)..."
    stop_running_daemon
    # Detach fully: setsid + </dev/null so adb shell does not deadlock waiting
    # on the daemon's inherited stdin (nohup alone still holds the pty open).
    #
    # Quoting layers (outer to inner), kept here so future edits stay sane:
    #   1. `adb_cmd shell "..."`  -> one arg passed to adb shell.
    #   2. `su 0 sh -c '...'`      -> root runs sh -c with a single-quoted body.
    #   3. `setsid sh -c "..."`   -> the daemon command, double-quoted and
    #      backslash-escaped so it survives layer 2's single quotes.
    # If you change the daemon command, re-verify all three layers with
    # `set -x` locally before committing.
    adb_cmd shell "su 0 sh -c 'setsid sh -c \"cd ${ROOT_ZONE} && ${ROOT_ZONE}/voboost-inject >>${ROOT_ZONE}/logs/manual-start.log 2>&1\" </dev/null >/dev/null 2>&1 &'" \
      || die "failed to start daemon"
    sleep 1
    ;;
  init-hook)
    log "installing init hook /system/etc/init/voboost.rc..."
    adb_cmd root >/dev/null 2>&1 || true
    adb_cmd shell "mount -o remount,rw /" 2>/dev/null || warn "remount rw failed"
    adb_cmd shell "su 0 sh -c 'cat > /system/etc/init/voboost.rc <<\"EOF\"
# >>> voboost-inject (do not edit)
service voboost-inject /data/voboost/voboost-inject
    class late_start
    user root
    group root
    seclabel u:r:su:s0
    oneshot
    disabled

on property:sys.boot_completed=1
    start voboost-inject
# <<< voboost-inject
EOF'" || die "failed to write init hook"
    adb_cmd shell "su 0 sh -c 'chmod 644 /system/etc/init/voboost.rc'"
    log "starting voboost-inject service..."
    adb_cmd shell "su 0 setprop ctl.start voboost-inject" || warn "service start signal failed; reboot to apply hook"
    ;;
esac

# ---------------------------------------------------------------------------
# Optional: stub-APKs (target processes) and the app
# ---------------------------------------------------------------------------

[[ ${INSTALL_STUBS} -eq 1 ]] && install_stub_apks
[[ ${INSTALL_APP}    -eq 1 ]] && install_app_apk

verify_readiness
log "provisioning complete."
