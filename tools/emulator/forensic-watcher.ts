// Device forensic watcher: polls boot identity and the on-disk SELinux
// policy on a live emulator while a gate run proceeds, appending a timestamped
// observation log outside the gate's evidence path. Ports the last
// local-builds forensic-watcher shell scripts with the adb quoting contract
// proven during the v94–v98 gate debugging:
//
//   adb shell joins argv with spaces and re-splits on the device, so the
//   payload must arrive as ONE shell-quoted token — "su 0 sh -c '<script>'".
//   Splitting it into separate argv elements hands "sh -c" only its first
//   word, and the probe silently degenerates into a no-op with exit 0 and
//   empty stdout (the waitForNewBoot blindness behind "reboot was not
//   observed" gate failures while the device had already rebooted).
import { spawnSync } from "node:child_process";
import { appendFileSync, existsSync, mkdirSync } from "node:fs";
import { isAbsolute, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export type WatcherArgs = {
  adb: string;
  serial: string;
  out: string;
  stopFile?: string;
  intervalMs?: number;
  maxProbes?: number;
};

export function shellProbe(adb: string, serial: string, script: string, timeoutMs = 30_000): string | null {
  const result = spawnSync(adb, ["-s", serial, "shell", `su 0 sh -c '${script.replace(/'/gu, `'\\''`)}'`], {
    stdio: ["ignore", "pipe", "pipe"],
    encoding: "utf8",
    timeout: timeoutMs,
  });
  if (result.error !== undefined || result.status !== 0) return null;
  return result.stdout;
}

export function probeBootId(adb: string, serial: string): string | null {
  const output = shellProbe(adb, serial, "exec cat /proc/sys/kernel/random/boot_id");
  if (output === null) return null;
  const bootId = output.trim().split("\n").at(-1)?.trim() ?? "";
  return /^[0-9a-f-]{36}$/u.test(bootId) ? bootId : null;
}

export function probeUptime(adb: string, serial: string): string | null {
  const output = shellProbe(adb, serial, "cat /proc/uptime");
  if (output === null) return null;
  return output.trim().split("\n").at(-1)?.trim() ?? null;
}

// Counts voboost strings in the on-disk precompiled policy — the disk-state
// half of the "policy absence" verdict (the runtime half lives in /sys).
export function probeDiskPolicyVoboostStrings(adb: string, serial: string): number | null {
  const script =
    'n=$(strings /vendor/etc/selinux/precompiled_sepolicy 2>/dev/null | grep -E "(^|_)voboost(_|$)" | wc -l); echo disk-voboost=$n';
  const output = shellProbe(adb, serial, script);
  if (output === null) return null;
  const line = output.trim().split("\n").at(-1)?.trim() ?? "";
  const match = /^disk-voboost=(\d+)$/u.exec(line);
  return match === null ? null : Number.parseInt(match[1], 10);
}

export function pollForensicOnce(adb: string, serial: string): string {
  const lines = [`probe at ${new Date().toISOString()}`];
  for (const [label, probe] of [
    ["boot_id", () => probeBootId(adb, serial)],
    ["uptime", () => probeUptime(adb, serial)],
    ["policy", () => {
      const count = probeDiskPolicyVoboostStrings(adb, serial);
      return count === null ? null : `disk-voboost=${count}`;
    }],
  ] as const) {
    const value = probe();
    lines.push(`  ${label}: ${value ?? "unavailable"}`);
  }
  return lines.join("\n");
}

export function runForensicWatcher(args: WatcherArgs): void {
  if (!isAbsolute(args.out)) throw new Error("forensic-watcher: out must be an absolute path");
  if (!existsSync(args.adb)) throw new Error(`forensic-watcher: adb not found: ${args.adb}`);
  const intervalMs = args.intervalMs ?? 15_000;
  mkdirSync(args.out, { mode: 0o700, recursive: true });
  const logPath = join(args.out, "watch.log");
  let probes = 0;
  for (;;) {
    // Each probe is independent: an adb failure during a device reboot must
    // not kill the watcher — record the failure and keep polling.
    try {
      appendFileSync(logPath, `===== ${pollForensicOnce(args.adb, args.serial)}\n`);
    } catch (error) {
      appendFileSync(logPath, `probe failed: ${String(error)}\n`);
    }
    probes += 1;
    if (args.maxProbes !== undefined && probes >= args.maxProbes) break;
    for (let slept = 0; slept < intervalMs; slept += 500) {
      if (args.stopFile !== undefined && existsSync(args.stopFile)) {
        appendFileSync(logPath, `watcher stopped after ${probes} probes\n`);
        return;
      }
      Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 500);
    }
  }
  appendFileSync(logPath, `watcher finished after ${probes} probes\n`);
}

function usage(): never {
  console.error(
    "usage: forensic-watcher.ts --adb <adb> --serial <serial> --out <dir> [--stopfile <path>] [--interval-ms <n>] [--max-probes <n>]",
  );
  process.exit(2);
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const raw = process.argv.slice(2);
  const flag = (name: string): string | undefined => {
    const index = raw.indexOf(name);
    return index === -1 ? undefined : raw[index + 1];
  };
  const adb = flag("--adb");
  const serial = flag("--serial");
  const out = flag("--out");
  if (adb === undefined || serial === undefined || out === undefined) usage();
  const intervalMs = flag("--interval-ms");
  const maxProbes = flag("--max-probes");
  runForensicWatcher({
    adb,
    serial,
    out,
    stopFile: flag("--stopfile"),
    intervalMs: intervalMs === undefined ? undefined : Number.parseInt(intervalMs, 10),
    maxProbes: maxProbes === undefined ? undefined : Number.parseInt(maxProbes, 10),
  });
}
