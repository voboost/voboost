import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmodSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { pollForensicOnce, probeBootId, shellProbe } from "./forensic-watcher.ts";

// A stub adb that reproduces real adb shell semantics: everything after
// "shell" is one payload; the payload is re-split on the device where
// "sh -c" takes only its first argument as the script. A payload that was
// correctly quoted on the host arrives as a single token and works; an
// argv-spread payload degenerates into a silent no-op with exit 0.
function strictAdb(directory: string, name: string): string {
  const script = join(directory, name);
  writeFileSync(
    script,
    [
      "#!/bin/sh",
      'payload="$*"',
      // Single-token quoted payload: the device shell runs the full script.
      "if printf '%s' \"$payload\" | grep -Fq \"su 0 sh -c 'exec cat /proc/sys/kernel/random/boot_id'\"; then",
      "  echo b7f0bc23-9a4e-4d0a-8a1b-2c3d4e5f6071",
      "  exit 0",
      "fi",
      "# Anything else — including the adb-joined argv style where the script",
      "# word-splits — leaves sh -c with only its first word: silent no-op.",
      "exit 0",
    ].join("\n"),
  );
  chmodSync(script, 0o755);
  return script;
}

function echoAdb(directory: string): string {
  const script = join(directory, "adb-echo");
  writeFileSync(script, '#!/bin/sh\nprintf \'%s\' "$*"\n');
  chmodSync(script, 0o755);
  return script;
}

test("argv-split payload is a silent no-op (the v94 blindness)", () => {
  const directory = mkdtempSync(join(tmpdir(), "watcher-"));
  try {
    const adb = strictAdb(directory, "adb-strict");
    // The historical broken pattern: each word a separate argv element, adb
    // joins them with spaces, "sh -c" receives only "exec" — the probe
    // returns empty output with exit 0 while the device has rebooted.
    const broken = spawnSync(adb, [
      "-s",
      "emulator-5554",
      "shell",
      ...["su", "0", "sh", "-c", "exec", "cat", "/proc/sys/kernel/random/boot_id"],
    ], { encoding: "utf8" });
    assert.equal(broken.status, 0, "the no-op exits successfully — that is the trap");
    assert.equal(broken.stdout, "", "and produces no output — the blindness");
  } finally {
    rmSync(directory, { recursive: true });
  }
});

test("quoted single-token payload reaches the device intact", () => {
  const directory = mkdtempSync(join(tmpdir(), "watcher-"));
  try {
    const adb = strictAdb(directory, "adb-strict");
    const bootId = probeBootId(adb, "emulator-5554");
    assert.equal(bootId, "b7f0bc23-9a4e-4d0a-8a1b-2c3d4e5f6071");
  } finally {
    rmSync(directory, { recursive: true });
  }
});

test("single quotes inside the payload are escaped posix-style", () => {
  const directory = mkdtempSync(join(tmpdir(), "watcher-"));
  try {
    const adb = echoAdb(directory);
    const output = shellProbe(adb, "x", "echo 'a b' | wc -l");
    // $* expands to every argv element including "-s x shell" — the payload
    // token itself must arrive posix-quoted.
    assert.equal(output, "-s x shell su 0 sh -c 'echo '\\''a b'\\'' | wc -l'");
  } finally {
    rmSync(directory, { recursive: true });
  }
});

test("failed adb probes do not throw", () => {
  const directory = mkdtempSync(join(tmpdir(), "watcher-"));
  try {
    const missing = join(directory, "nonexistent-adb");
    assert.equal(shellProbe(missing, "emulator-5554", "echo hi"), null);
    assert.equal(probeBootId(missing, "emulator-5554"), null);
    const once = pollForensicOnce(missing, "emulator-5554");
    assert.match(once, /boot_id: unavailable/u);
    assert.match(once, /uptime: unavailable/u);
    assert.match(once, /policy: unavailable/u);
  } finally {
    rmSync(directory, { recursive: true });
  }
});
