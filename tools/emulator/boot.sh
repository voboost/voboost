#!/usr/bin/env bash
# Boot the rooted arm64 AVD and bring it to a daemon-ready state.
#
#   tools/emulator/boot.sh [-w]
#       -w   wait for an already-running emulator instead of starting one
#
# Environment: AVD_NAME (default: free), ANDROID_HOME, ADB_SERIAL, ADB_WAIT_TIMEOUT.
# Exit codes: 0 booted+rooted+permissive; non-zero on failure (CI-usable).
set -euo pipefail
source "$(dirname "$0")/lib/common.sh"

WAIT_ONLY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    -w|--wait) WAIT_ONLY=1; shift ;;
    *) die "unknown arg: $1" ;;
  esac
done

[[ -x "${EMULATOR_BIN}" ]] || die "emulator not found at ${EMULATOR_BIN} (set ANDROID_HOME)"
[[ -x "${ADB_BIN}" ]]      || die "adb not found at ${ADB_BIN} (set ANDROID_HOME)"

if [[ ${WAIT_ONLY} -eq 0 ]]; then
  # Start the AVD with no snapshot for deterministic state. Detached; -no-window for CI.
  if ! adb_ready; then
    log "starting AVD '${AVD_NAME}'..."
    # shellcheck disable=SC2086
    "${EMULATOR_BIN}" -avd "${AVD_NAME}" -no-snapshot ${EMULATOR_FLAGS:-} &
    EMU_PID=$!
    log "emulator pid ${EMU_PID}"
  else
    log "device already reachable; reusing it."
  fi
fi

root_and_permissive
log "device is daemon-ready."
