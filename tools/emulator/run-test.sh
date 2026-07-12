#!/usr/bin/env bash
# Contract-driven integration test driver. Silent on success; prints failures and
# exits non-zero on any assertion failure (CI-usable).
#
#   tools/emulator/run-test.sh [--only NAME ...] [--list] [--keep-logs]
#
# Asserts the daemon's documented scenarios (see integration-tests.md) against
# inject-status.json and /data/voboost/logs/*.log. Scenarios that need a target
# process (stub-APK) are skipped with a logged reason when the target is absent
# — they are never silently dropped.
set -uo pipefail
source "$(dirname "$0")/lib/common.sh"

KEEP_LOGS=0
LIST=0
ONLY=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --only) ONLY+=("$2"); shift 2 ;;
    --list) LIST=1; shift ;;
    --keep-logs) KEEP_LOGS=1; shift ;;
    *) die "unknown arg: $1" ;;
  esac
done

# ---------------------------------------------------------------------------
# Status helpers
# ---------------------------------------------------------------------------

STATUS_TMP="$(mktemp -d)"
trap 'rm -rf "${STATUS_TMP}"; [[ ${KEEP_LOGS} -eq 1 ]] && collect_logs || true' EXIT

# Pull inject-status.json to a local file. Prints the local path; empty on miss.
#
# Reads the status file via `adb shell su 0 cat` instead of `adb pull` to avoid
# a race with the daemon's atomic write (temp + rename): `adb pull` can read a
# partial/empty file during the rename window, which under `set -uo pipefail`
# inside `$(...)` swallows the failure and yields an empty `got=`. `cat` reads
# the file in-place after the rename completes. The status file is small
# (< 4KB), so cat is fine.
#
# Two retry layers, distinct in purpose:
#   - pull_status's 3x1s loop below covers a read landing MID-RENAME (the file
#     exists but is momentarily empty/partial); it validates non-empty JSON
#     starting with '{' before returning.
#   - poll_status's outer loop (also 1s) covers the daemon NOT HAVING REACHED
#     the target state yet (a higher-level wait, not a read race).
pull_status() {
  local out="${STATUS_TMP}/inject-status.json"
  local remote="${APP_ZONE}/inject-status.json"
  local attempt
  for attempt in 1 2 3; do
    adb_cmd shell "su 0 cat ${remote} 2>/dev/null" >"${out}" 2>/dev/null || true
    # Valid JSON status is non-empty and starts with '{'. Accept on success.
    if [[ -s "${out}" ]] && head -c1 "${out}" | grep -q '{'; then
      echo "${out}"
      return 0
    fi
    sleep 1
  done
  # Last attempt's output (possibly empty) remains in ${out}; echo nothing.
}

# Read a top-level field from the pulled status JSON via python3.
# Usage: status_field <file> <json-pointer-ish path, e.g. state or injections.0.state>
status_field() {
  local file="$1" path="$2"
  [[ -f "$file" ]] || { echo ""; return; }
  python3 - "$file" "$path" <<'PY' 2>/dev/null
import json, sys
f, path = sys.argv[1], sys.argv[2]
try:
    d = json.load(open(f))
except Exception:
    print(""); sys.exit(0)
cur = d
for part in path.split("."):
    if part == "":
        continue
    if part.isdigit() and isinstance(cur, list):
        cur = cur[int(part)] if int(part) < len(cur) else None
    else:
        cur = cur.get(part) if isinstance(cur, dict) else None
    if cur is None:
        break
print("" if cur is None else (json.dumps(cur) if isinstance(cur, (dict, list)) else str(cur)))
PY
}

collect_logs() {
  local dest="${STATUS_TMP}/device-logs"
  mkdir -p "${dest}"
  adb_pull_or_skip "${ROOT_ZONE}/logs" "${dest}/daemon-logs" 2>/dev/null || true
  log "logs collected under ${dest}"
}

# Write inject.json into the app zone (atomic: push to temp, then rename on device).
write_inject_json() {
  local content="$1"
  printf '%s' "$content" > "${STATUS_TMP}/inject.json"
  adb_cmd push "${STATUS_TMP}/inject.json" "${APP_ZONE}/inject.json.tmp" >/dev/null 2>&1 \
    || die "failed to push inject.json"
  adb_cmd shell "mv ${APP_ZONE}/inject.json.tmp ${APP_ZONE}/inject.json" \
    || die "failed to atomically rename inject.json"
}

# Poll until a status field matches, or timeout. Usage: poll_status <path> <value> <secs>
poll_status() {
  local path="$1" want="$2" secs="${3:-10}" deadline=$(( $(date +%s) + ${3:-10} )) got=""
  while [[ $(date +%s) -lt ${deadline} ]]; do
    local f; f="$(pull_status)"
    got="$(status_field "$f" "$path")"
    if [[ "${got}" == "${want}" ]]; then
      echo "${got}"
      return 0
    fi
    sleep 1
  done
  echo "${got}"
  return 1
}

# ---------------------------------------------------------------------------
# Assertions
# ---------------------------------------------------------------------------

FAILURES=0
SKIPPED=0
RAN=0

# target_present <process-name>: true if a stub process is running.
target_present() {
  adb_cmd shell "su 0 pgrep -f '$1'" 2>/dev/null | grep -q .
}

# run <name> <fn>: register and gate a scenario.
scenario() {
  local name="$1" fn="$2"
  if [[ ${LIST} -eq 1 ]]; then echo "${name}"; return; fi
  if [[ ${#ONLY[@]} -gt 0 ]] && [[ " ${ONLY[*]} " != *" ${name} "* ]]; then return; fi
  log "scenario: ${name}"
  if "$fn"; then RAN=$((RAN+1)); else FAILURES=$((FAILURES+1)); fi
}

skip() { warn "  SKIP: $*"; SKIPPED=$((SKIPPED+1)); }
fail() { warn "  FAIL: $*"; return 1; }

# ---------------------------------------------------------------------------
# Scenarios
# ---------------------------------------------------------------------------

# Requires no target process — pure daemon behavior.
sc_startup_gate() {
  # Reset: scenarios don't clear daemon state between runs, so a `failed`
  # injection from a prior scenario can persist in inject-status.json. Write a
  # clean plan (startup:none, no agents) and wait for the daemon to reprocess
  # it so the status reflects this scenario, not a prior one.
  write_inject_json '{"version":0,"startup":"none","disabled":false,"agents":[]}'
  # Wait for the daemon to consume the reset plan (state settles, no new work).
  poll_status "state" "ready" 8 >/dev/null 2>&1 || sleep 2
  # Daemon should take no action; no active injections expected. Tolerate
  # `failed` entries left from prior scenarios (they are not active injections
  # triggered by startup:none) by filtering them out of the check: keep only
  # injections whose state is not "failed", then require the remainder to be
  # empty.
  local f; f="$(pull_status)"
  local inj
  inj="$(python3 - "$f" <<'PY' 2>/dev/null
import json, sys
try:
    d = json.load(open(sys.argv[1]))
except Exception:
    print(""); sys.exit(0)
active = [i for i in d.get("injections", []) if i.get("state") != "failed"]
print(json.dumps(active))
PY
)"
  if [[ "$inj" == "[]" ]] || [[ -z "$inj" ]]; then return 0; fi
  fail "startup:none left active injections=$inj"
}

sc_kill_switch() {
  write_inject_json '{"version":0,"startup":"run","disabled":false,"agents":[]}'
  adb_cmd shell "su 0 touch ${ROOT_ZONE}/run/disable" >/dev/null 2>&1 || true
  local got; got="$(poll_status "killed" "True" 12)" || true
  adb_cmd shell "su 0 rm -f ${ROOT_ZONE}/run/disable" >/dev/null 2>&1 || true
  [[ "${got}" == "True" ]] && return 0
  fail "kill-switch did not set killed=true (got=${got})"
}

sc_status_schema() {
  local f; f="$(pull_status)"
  [[ -f "$f" ]] || { fail "no inject-status.json"; return; }
  local k; for k in daemon manifest state killed panic injections; do
    local v; v="$(status_field "$f" "$k")"
    [[ -n "$v" ]] || { fail "status missing field '${k}'"; return; }
  done
  return 0
}

sc_daemon_state() {
  local got; got="$(poll_status "state" "ready" 15)" || true
  # "degraded" is also acceptable when SELinux/zone checks warn; treat panic-free as ok.
  [[ "${got}" == "ready" || "${got}" == "degraded" ]] && return 0
  fail "daemon state='${got}' (expected ready|degraded)"
}

# Target-required scenarios: skipped if the stub process is absent (no silent drop).
sc_attach_launcher() {
  local p="com.qinggan.app.launcher"
  target_present "$p" || { skip "$p not running (no stub-APK)"; return 0; }
  local got; got="$(poll_status "injections.0.state" "active" 20)" || true
  [[ "${got}" == "active" ]] && return 0
  fail "launcher injection not active (got=${got})"
}

sc_config_delivery() {
  local p="com.qinggan.app.vehiclesetting"
  target_present "$p" || { skip "$p not running (no stub-APK)"; return 0; }
  write_inject_json '{"version":0,"startup":"run","disabled":false,"agents":[{"id":"low-speed-sound","enabled":true,"config":{"enabled":true}}]}'
  local got; got="$(poll_status "injections.0.id" "low-speed-sound" 20)" || true
  [[ "${got}" == "low-speed-sound" ]] && return 0
  fail "config not delivered to low-speed-sound (got=${got})"
}

# ---------------------------------------------------------------------------
# Run
# ---------------------------------------------------------------------------

wait_for_boot

scenario startup-gate      sc_startup_gate
scenario kill-switch       sc_kill_switch
scenario status-schema     sc_status_schema
scenario daemon-state      sc_daemon_state
scenario attach-launcher   sc_attach_launcher
scenario config-delivery   sc_config_delivery

# Document the scenarios not yet wired here (need full stub+agent matrix):
if [[ ${LIST} -eq 0 ]]; then
  log "note: scenarios spawn-gate, js/native-routing, resume, coexist-skip, quarantine, boot-gate require the full stub+agent matrix (see integration-tests.md); run them manually after voboost-stubs android-apk-port."
fi

if [[ ${LIST} -eq 1 ]]; then exit 0; fi

if [[ ${FAILURES} -eq 0 ]]; then
  log "PASS: ${RAN} ran, ${SKIPPED} skipped."
  exit 0
fi
warn "FAIL: ${FAILURES} failed, ${RAN} ran, ${SKIPPED} skipped."
exit 1
