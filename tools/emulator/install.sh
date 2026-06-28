#!/usr/bin/env bash
# Provisioning via the operator installer (voboost-install), driven headless.
# This both provisions the device AND smoke-tests the installer itself.
#
#   tools/emulator/install.sh [--dry-run]
#
# Requires the installer binary. Set VOBOOST_INSTALL_DIR (default: sibling voboost-install).
# If the installer is not built, this script points to it and falls back to provision.sh.
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

# Resolve the installer binary (Tauri release/debug output).
resolve_installer() {
  local d="${VOBOOST_INSTALL_DIR}"
  for b in \
    "${d}/target/release/voboost-install" \
    "${d}/target/debug/voboost-install" \
    "${d}/src-tauri/target/release/voboost-install" \
    "${d}/src-tauri/target/debug/voboost-install"; do
    [[ -x "$b" ]] && { echo "$b"; return; }
  done
  echo ""
}

wait_for_boot
root_and_permissive

INSTALLER="$(resolve_installer || true)"
if [[ -z "${INSTALLER}" ]]; then
  die "voboost-install binary not found under ${VOBOOST_INSTALL_DIR}. " \
      "Build it (e.g. 'cargo tauri build' / 'npm run tauri build') or use provision.sh for the raw-adb path."
fi

# The app APK to install.
APK="$(find "${VOBOOST_ROOT}/build/outputs/apk" -name "*.apk" 2>/dev/null | head -1)"
[[ -n "${APK}" ]] || die "no app APK; run './gradlew assembleDebug' first."

# Pick a serial if none given.
SERIAL="${ADB_SERIAL}"
if [[ -z "${SERIAL}" ]]; then
  SERIAL="$("${ADB_BIN}" devices | awk '/emulator-[0-9]+/ {print $1; exit}')"
  [[ -n "${SERIAL}" ]] || die "no emulator serial found; set ADB_SERIAL."
fi

ARGS=(--auto-install "${APK}" -s "${SERIAL}")
[[ ${DRY_RUN} -eq 1 ]] && ARGS+=(--dry-run)

log "driving installer: ${INSTALLER} ${ARGS[*]}"
if "${INSTALLER}" "${ARGS[@]}"; then
  log "installer provisioned the device and exited 0 (smoke-test passed)."
  install_stub_apks
  verify_readiness
else
  die "installer failed. Use provision.sh for the raw-adb fallback."
fi
