#!/usr/bin/env bash
# Shared helpers for the voboost emulator harness.
# Sourced by boot.sh / provision.sh / install.sh / run-test.sh.
# Design: fail-fast, silent-on-success for the test driver, explicit exit codes for CI.

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration (override via environment)
# ---------------------------------------------------------------------------

: "${VOBOOST_ROOT:=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"        # this app repo
: "${VOBOOST_INJECT_DIR:=$(realpath "${VOBOOST_ROOT}/../../voboost-inject" 2>/dev/null || echo "$HOME/voboost/voboost-inject")}"
: "${VOBOOST_SCRIPT_DIR:=$(realpath "${VOBOOST_ROOT}/../../voboost-script" 2>/dev/null || echo "$HOME/voboost/voboost-script")}"
: "${VOBOOST_STUBS_DIR:=$(realpath "${VOBOOST_ROOT}/../../voboost-stubs" 2>/dev/null || echo "$HOME/voboost/voboost-stubs")}"
: "${VOBOOST_INSTALL_DIR:=$(realpath "${VOBOOST_ROOT}/../../voboost-install" 2>/dev/null || echo "$HOME/voboost/voboost-install")}"
: "${ANDROID_HOME:=${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
: "${AVD_NAME:=free}"
: "${ADB_SERIAL:=}"                 # empty => any booted emulator; else emulator-XXXX
: "${ADB_WAIT_TIMEOUT:=120}"        # seconds to wait for adb/boot readiness
: "${ROOT_ZONE:=/data/voboost}"
: "${APP_ZONE:=/data/user/0/ru.voboost}"
# Where the assembled daemon artifacts live (manifest, agents, binary). Override in CI.
: "${DAEMON_ARTIFACTS_DIR:=}"       # if empty, provision.sh derives it

# ---------------------------------------------------------------------------
# Paths to SDK tools
# ---------------------------------------------------------------------------

EMULATOR_BIN="${ANDROID_HOME}/emulator/emulator"
ADB_BIN="${ANDROID_HOME}/platform-tools/adb"

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

# Harness is silent-on-success ONLY for run-test.sh's assertion output.
# Provisioning/boot steps always log so an operator can follow progress.
log()  { printf '[harness] %s\n' "$*" >&2; }
warn() { printf '[harness] WARN: %s\n' "$*" >&2; }
die()  { printf '[harness] ERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# adb helpers
# ---------------------------------------------------------------------------

# Serial selector: -s <serial> if ADB_SERIAL is set, else empty (picks the device).
_adb_serial_args() {
  if [[ -n "${ADB_SERIAL}" ]]; then printf ' -s %s' "${ADB_SERIAL}"; fi
}

# Run adb against the target. Usage: adb_shell <args...>
adb_cmd() {
  "${ADB_BIN}"$(_adb_serial_args) "$@"
}

# True if a device is reachable.
adb_ready() {
  local state
  state="$("${ADB_BIN}"$(_adb_serial_args) get-state 2>/dev/null || true)"
  [[ "${state}" == "device" ]]
}

# Wait until the device answers and sys.boot_completed == 1.
wait_for_boot() {
  local deadline=$(( $(date +%s) + ADB_WAIT_TIMEOUT ))
  log "waiting for device (timeout ${ADB_WAIT_TIMEOUT}s)..."
  until adb_ready; do
    [[ $(date +%s) -lt ${deadline} ]] || die "device not reachable within ${ADB_WAIT_TIMEOUT}s"
    sleep 2
  done
  log "device reachable; waiting for boot_completed..."
  until [[ "$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    [[ $(date +%s) -lt ${deadline} ]] || die "boot did not complete within ${ADB_WAIT_TIMEOUT}s"
    sleep 2
  done
  log "boot completed."
}

# Re-acquire root and relax SELinux for a shell-started daemon (test-only).
root_and_permissive() {
  log "acquiring root..."
  adb_cmd root >/dev/null 2>&1 || die "adb root failed (is the AVD a userdebug image?)"
  sleep 2  # adbd restarts after `adb root`
  wait_for_boot
  log "setting SELinux permissive (test-only)..."
  adb_cmd shell setenforce 0 || warn "setenforce 0 failed (daemon may hit denies)"
  adb_cmd shell getenforce 2>/dev/null | tr -d '\r' | grep -qi permissive \
    || warn "SELinux is not permissive"
}

# Pull a file from the device, tolerating absence. Returns 0 if pulled.
adb_pull_or_skip() {
  local remote="$1" local="$2"
  if adb_cmd shell "[[ -f ${remote} ]]" 2>/dev/null; then
    adb_cmd pull "${remote}" "${local}" >/dev/null 2>&1
  fi
}

# ---------------------------------------------------------------------------
# APK discovery + installation
# ---------------------------------------------------------------------------

# Install every stub APK found under the stubs repo (target processes). Best-effort.
install_stub_apks() {
  log "installing stub APKs (target processes)..."
  local found=0
  while IFS= read -r apk; do
    [[ -z "${apk}" ]] && continue
    log "  install: $(basename "${apk}")"
    adb_cmd install -r -t "${apk}" >/dev/null 2>&1 || warn "failed to install ${apk}"
    found=$((found + 1))
  done < <(find "${VOBOOST_STUBS_DIR}" -path "*/build/outputs/apk/*/*.apk" 2>/dev/null)
  if [[ ${found} -eq 0 ]]; then
    warn "no stub APKs found under ${VOBOOST_STUBS_DIR}; daemon will have no targets. " \
         "Build voboost-stubs (android-apk-port) for full E2E."
  fi
}

# Install the voboost app APK (the plan producer / status reader).
install_app_apk() {
  local apk
  apk="$(find "${VOBOOST_ROOT}/build/outputs/apk" -name "*.apk" 2>/dev/null | head -1)"
  if [[ -z "${apk}" ]]; then
    warn "no app APK found under ${VOBOOST_ROOT}/build. Run './gradlew assembleDebug' first."
    return
  fi
  log "installing app APK: $(basename "${apk}")"
  adb_cmd install -r -t "${apk}" >/dev/null 2>&1 || die "app install failed"
}

# ---------------------------------------------------------------------------
# Readiness verification (exit non-zero + name the failing check)
# ---------------------------------------------------------------------------

verify_readiness() {
  log "verifying readiness..."
  local fail=0
  check() { # check <label> <condition-cmd>
    local label="$1"; shift
    if adb_cmd shell "$@" >/dev/null 2>&1; then
      log "  ok: ${label}"
    else
      warn "  FAIL: ${label}"
      fail=1
    fi
  }
  check "root access"          "su 0 id"
  check "root zone exists"     "su 0 test -d ${ROOT_ZONE}"
  check "root zone mode 700"   "su 0 sh -c '[ \$(stat -c %a ${ROOT_ZONE}) -eq 700 ]'"
  check "daemon binary"        "su 0 test -x ${ROOT_ZONE}/voboost-inject"
  check "signed manifest"      "su 0 test -f ${ROOT_ZONE}/manifest.json"
  check "manifest signature"   "su 0 test -f ${ROOT_ZONE}/manifest.sig"
  check "SELinux permissive"   "getenforce | tr -d '\r' | grep -qi permissive"
  check "daemon running"       "su 0 pgrep -f ${ROOT_ZONE}/voboost-inject"
  [[ ${fail} -eq 0 ]] || die "readiness checks failed (see above)"
  log "all readiness checks passed."
}
