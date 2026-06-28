#!/usr/bin/env bash
#
# voboost-restore.sh
#
# Light post-OTA RE-ARM for the voboost boot hook. Run on the host (macOS/Linux)
# with the head unit connected via adb.
#
# Why this exists: on this device a system OTA does NOT wipe root and does NOT wipe
# /data. Empirically verified: the unit stays debuggable, `su 0` keeps working over
# adb, and the entire /data tree survives the update. The ONLY thing an OTA reverts
# is /system/etc/init.logcat.sh, which gets restored to its stock form and therefore
# loses our launch block. So re-arming is cheap: we do NOT re-root and we do NOT
# re-push any payload - we just re-insert a small guarded launch block back into the
# /system hook.
#
# PAYLOAD (/data/voboost/voboost-inject) is the voboost root component: the compiled
# (Rust) frida-inject orchestrator that verifies and injects the agents. It lives in
# /data, survives the OTA, and is delivered separately (it is NOT created by this
# script). This script only makes the post-OTA stock hook launch it again at boot.
# The supervisor loop relaunches it if it exits.
#
# The inserted block re-asserts `setenforce 0` because the stock post-OTA
# init.logcat.sh does not set SELinux permissive, and the component needs that.
#
# Usage: bash voboost-restore.sh   (run from the repo root)

set -u

ADB="${ADB:-adb}"

HOOK="/system/etc/init.logcat.sh"
PAYLOAD="/data/voboost/voboost-inject"
MARKER_BEGIN='# >>> voboost-rearm >>>'
MARKER_END='# <<< voboost-rearm <<<'

say()  { printf '%s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. Host / adb / device checks
# ---------------------------------------------------------------------------
command -v "$ADB" >/dev/null 2>&1 || fail "adb not found in PATH (set ADB=/path/to/adb)."
DEVCOUNT="$("$ADB" devices | grep -cE '\sdevice$' || true)"
[ "$DEVCOUNT" -ge 1 ] || fail "No device in 'device' state. Check 'adb devices'."
[ "$DEVCOUNT" -eq 1 ] || fail "Multiple devices; set ANDROID_SERIAL=<serial>."

# ---------------------------------------------------------------------------
# 2. Detect a working root (su) invocation - writing /system needs uid 0
# ---------------------------------------------------------------------------
detect_su() {
    local form out
    for form in 'su -c' 'su 0' '/data/local/tmp/su 0' 'su root -c' ''; do
        if [ -z "$form" ]; then
            out="$("$ADB" shell 'id -u' 2>/dev/null | tr -d '\r')"
        else
            out="$("$ADB" shell "$form id -u" 2>/dev/null | tr -d '\r')"
        fi
        [ "$out" = "0" ] && { printf '%s' "$form"; return 0; }
    done
    return 1
}

say "Detecting root method..."
SU_FORM="$(detect_su)" || fail "No root (su) returning uid 0. Re-arm needs root to write $HOOK. Re-root the device FIRST, then run this re-arm again."
[ -z "$SU_FORM" ] && say "  root: adb shell already uid 0" || say "  root: su form -> '$SU_FORM'"

run_root() {
    if [ -n "$SU_FORM" ]; then "$ADB" shell "$SU_FORM sh -c '$1'"; else "$ADB" shell "sh -c '$1'"; fi
}

# ---------------------------------------------------------------------------
# 3. Idempotency: bail out if the hook is already armed
# ---------------------------------------------------------------------------
if run_root "grep -q voboost-rearm $HOOK"; then
    say "already armed: $HOOK already contains the voboost-rearm block. Nothing to do."
    exit 0
fi

# ---------------------------------------------------------------------------
# 4. Read the current stock hook and build the patched version on the host
# ---------------------------------------------------------------------------
HOOK_LOCAL="$(mktemp -t voboost-hook.XXXXXX)"
NEW_LOCAL="$(mktemp -t voboost-hook-new.XXXXXX)"
trap 'rm -f "$HOOK_LOCAL" "$NEW_LOCAL"' EXIT

say "Reading current $HOOK from device..."
run_root "cat $HOOK" > "$HOOK_LOCAL" || fail "Could not read $HOOK from device."
[ -s "$HOOK_LOCAL" ] || fail "$HOOK came back empty - refusing to patch."

# The stock post-OTA init.logcat.sh ends with 'exit 0', so an appended block would
# never run. Instead we insert our block IMMEDIATELY BEFORE the first line that runs
# 'wait $logcat_pid' (present in this firmware's hook). Fail loudly if it is missing.
if ! grep -q 'wait $logcat_pid' "$HOOK_LOCAL"; then
    fail "Anchor line 'wait \$logcat_pid' not found in $HOOK. Firmware layout changed; refusing to blindly append (stock file ends with 'exit 0' and the block would never run). Inspect the hook manually."
fi

awk '
    !done && /wait \$logcat_pid/ {
        print "# >>> voboost-rearm >>>";
        print "setenforce 0";
        print "if [ -x \"/data/voboost/voboost-inject\" ]; then";
        print "  ( while true; do /data/voboost/voboost-inject; sleep 5; done ) &";
        print "fi";
        print "# <<< voboost-rearm <<<";
        done = 1;
    }
    { print }
' "$HOOK_LOCAL" > "$NEW_LOCAL"

grep -q voboost-rearm "$NEW_LOCAL" || fail "Failed to build patched hook (block not inserted)."
say "Patched hook built (block inserted before 'wait \$logcat_pid')."

# ---------------------------------------------------------------------------
# 5. Apply: push, remount / rw, copy into place, fix mode/owner, clean up
# ---------------------------------------------------------------------------
say "Pushing patched hook to /sdcard/voboost-init.logcat.sh..."
"$ADB" push "$NEW_LOCAL" /sdcard/voboost-init.logcat.sh >/dev/null || fail "adb push failed."

# Root fs is system-as-root, mounted read-only; remount it read-write first.
say "Remounting / read-write..."
run_root 'mount -o rw,remount /' || fail "Could not remount / read-write."

say "Installing $HOOK..."
run_root "cp /sdcard/voboost-init.logcat.sh $HOOK" || fail "Could not copy hook into place."
run_root "chmod 755 $HOOK" || fail "Could not chmod $HOOK."
run_root "chown root:root $HOOK" || fail "Could not chown $HOOK."
run_root "rm -f /sdcard/voboost-init.logcat.sh" >/dev/null 2>&1 || true

say
say "Re-armed: $HOOK now relaunches $PAYLOAD on boot."
say "A reboot is required for the new hook to take effect."

# ---------------------------------------------------------------------------
# 6. Reboot prompt
# ---------------------------------------------------------------------------
read -p "Reboot now? [y/N] " REPLY
case "$REPLY" in
    y|Y)
        say "Rebooting..."
        "$ADB" reboot
        ;;
    *)
        say "Skipped reboot. Reboot manually when ready:  $ADB reboot"
        ;;
esac

say "Done."
