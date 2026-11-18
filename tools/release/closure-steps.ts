// Shared closure steps for every release entrypoint: the Rust release CLI
// stays the sole owner of cryptographic and semantic manifest checks — this
// module only orchestrates it (sign/compose/verify) and owns publication of
// the finished bundle. One writer per output: the staging sibling is reserved
// before any producer runs, the whole bundle (closure, evidence, resolved
// inputs, receipt with complete marker) is prepared and verified in the
// sibling, then published as a single rename. A crash leaves work/evidence
// behind for analysis; the final name only ever appears complete.
import {
  chmodSync,
  closeSync,
  existsSync,
  fsyncSync,
  lstatSync,
  mkdirSync,
  openSync,
  readFileSync,
  renameSync,
  rmSync,
  statSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { dirname, join } from "node:path";
import { runProcess, METADATA_TIMEOUT_MS, type RunOptions } from "./process.ts";

export const NATIVE_ROLES: ReadonlyArray<readonly [string, string]> = [
  ["daemon", "voboost-daemon"],
  ["inject", "voboost-inject"],
  ["install", "voboost-install"],
  ["restore", "voboost-restore"],
  ["uninstall", "voboost-uninstall"],
  ["selinux", "voboost-selinux"],
] as const;

export const NON_ELF_FILES = [
  "voboost.rc",
  "libimplant-arm64-v8a.so",
  "libimplant-armeabi-v7a.so",
] as const;

// emulator-skip markers that must be absent from every machine-profile
// artifact (checked as full strings) — and, correspondingly, the profile
// marker sets verified against real bytes before sign/compose.
export const EMULATOR_SKIP_MARKERS: ReadonlyArray<readonly [string, string]> = [
  ["voboost-daemon", "VOBOOST_EMULATOR_DAEMON_SELINUX_SKIP_V1"],
  ["voboost-inject", "VOBOOST_EMULATOR_DAEMON_SELINUX_SKIP_V1"],
  ["voboost-install", "VOBOOST_EMULATOR_SELINUX_SKIP_V1"],
  ["voboost-restore", "VOBOOST_EMULATOR_RESTORE_SELINUX_SKIP_V1"],
  ["voboost-uninstall", "VOBOOST_EMULATOR_SELINUX_SKIP_V1"],
  ["voboost-uninstall", "VOBOOST_EMULATOR_UNINSTALL_SELINUX_SKIP_V1"],
  ["voboost-uninstall", "VOBOOST_EMULATOR_UNINSTALL_MOUNT_ADAPTER_V1"],
] as const;

export const COMPLETE_MARKER = ".voboost-bundle-complete";

export type BuildProfile = "machine" | "emulator";

export function failStep(message: string): never {
  throw new Error(`closure-steps: ${message}`);
}

// Binary substring search with the semantics of `strings | grep -Fqx`: a
// marker matches only as a full "string" — bounded by a non-printable byte or
// file edge — so embedded prefixes of unrelated symbols never match.
export function containsFullString(buffer: Buffer, needle: string): boolean {
  const needleBuffer = Buffer.from(needle, "latin1");
  let offset = buffer.indexOf(needleBuffer);
  while (offset !== -1) {
    const before = offset === 0 ? 0 : buffer[offset - 1];
    const after = offset + needleBuffer.length;
    const afterByte = after === buffer.length ? 0 : buffer[after];
    const printable = (byte: number) => byte >= 0x20 && byte <= 0x7e;
    if (!printable(before) && !printable(afterByte)) return true;
    offset = buffer.indexOf(needleBuffer, offset + 1);
  }
  return false;
}

// Profile is verified against actual bytes, never trusted from config: the
// machine profile rejects every emulator-skip marker; the emulator profile
// must contain its full expected marker set.
export function verifyProfileMarkers(
  profile: BuildProfile,
  readBinary: (file: string) => Buffer,
): void {
  for (const [file, marker] of EMULATOR_SKIP_MARKERS) {
    const present = containsFullString(readBinary(file), marker);
    if (profile === "machine" && present)
      failStep(`machine closure contains emulator marker ${marker} in ${file}`);
    if (profile === "emulator" && !present)
      failStep(`emulator closure is missing emulator marker ${marker} in ${file}`);
  }
}

// One Rust release CLI invocation through the shared process runner. Manifest
// checks are short metadata calls — bounded by the metadata deadline unless
// the caller overrides it.
export async function releaseCli(
  product: string,
  subcommand: string,
  args: readonly string[],
  options: RunOptions = {},
): Promise<void> {
  await runProcess(
    "cargo",
    [
      "run",
      "--release",
      "--manifest-path",
      join(product, "release/Cargo.toml"),
      "--locked",
      "--offline",
      "--",
      subcommand,
      ...args,
    ],
    { timeoutMs: METADATA_TIMEOUT_MS, ...options },
  );
}

// ---------------------------------------------------------------------------
// Publication: reservation + staged bundle + single atomic rename.
// ---------------------------------------------------------------------------

export type Reservation = {
  owner: string;
  path: string;
  release(): void;
};

function reservationPath(output: string): string {
  return `${output}.owner`;
}

function processAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch (error) {
    return (error as NodeJS.ErrnoException).code === "EPERM";
  }
}

// Reserve the output for one writer. A stale reservation is claimed by an
// atomic rename (only one contender can remove the old marker; a lost
// contender re-reads the new one and fails against its live owner) — never
// unlinked blind, never by age.
export function reserveOutput(output: string, owner: string): Reservation {
  const marker = reservationPath(output);
  mkdirSync(dirname(output), { recursive: true });
  if (existsSync(output)) {
    failStep(`final output already exists: ${output}`);
  }
  for (let attempt = 0; attempt < 4; attempt += 1) {
    if (existsSync(marker)) {
      const previous = readFileSync(marker, "utf8").trim();
      const pid = Number(previous.split(":")[0]);
      if (Number.isInteger(pid) && processAlive(pid))
        failStep(`output ${output} is reserved by a live writer (pid ${pid})`);
      // Owner is gone: atomically claim the abandoned marker by renaming it
      // away. Only one contender's rename succeeds; the others observe
      // ENOENT and loop to re-read whatever marker now exists.
      const claim = `${marker}.dead.${process.pid}`;
      try {
        renameSync(marker, claim);
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code === "ENOENT") continue;
        throw error;
      }
      try {
        const claimed = readFileSync(claim, "utf8").trim();
        if (claimed === previous) {
          unlinkSync(claim);
        } else {
          // A different marker content means someone replaced the marker
          // between our read and our rename: restore their marker and honor
          // their reservation instead of discarding it.
          try {
            renameSync(claim, marker);
          } catch {
            // their new marker already exists again — nothing to restore
          }
          continue;
        }
      } catch (error) {
        // claim vanished mid-check (another contender?): loop and re-read
        void error;
        continue;
      }
    }
    try {
      writeFileSync(marker, `${process.pid}:${owner}\n`, { mode: 0o600, flag: "wx" });
      const token = `${process.pid}:${owner}`;
      return {
        owner,
        path: marker,
        release() {
          // Only remove OUR marker — never a newer owner's reservation.
          try {
            if (readFileSync(marker, "utf8").trim() === token) unlinkSync(marker);
          } catch {
            // already gone — nothing to release
          }
        },
      };
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "EEXIST") continue;
      throw error;
    }
  }
  failStep(`could not reserve ${output}: reservation contention did not settle`);
}

// Durability support: fsync a file and a directory entry. chmod is NOT a
// sync. The durability claim below is process-level crash safety (data
// written and flushed before the final rename; the parent directory synced
// after it) — it is not a certified power-loss proof.
function syncFile(path: string): void {
  const fd = openSync(path, "r");
  try {
    fsyncSync(fd);
  } finally {
    closeSync(fd);
  }
}

function syncDirectory(path: string): void {
  const fd = openSync(path, "r");
  try {
    fsyncSync(fd);
  } finally {
    closeSync(fd);
  }
}

// Publish the fully prepared staging bundle: write the complete marker and
// receipt inside the bundle, fsync them and the staging directory, then a
// single rename followed by a parent-directory sync. If the rename target
// appeared meanwhile, fail closed — never remove the final directory.
export function publishStagedBundle(staging: string, output: string, receipt: unknown): void {
  const markerPath = join(staging, COMPLETE_MARKER);
  if (existsSync(markerPath)) failStep(`staging bundle already carries a complete marker: ${staging}`);
  const receiptPath = join(staging, "receipt.json");
  writeFileSync(receiptPath, `${JSON.stringify(receipt, null, 2)}\n`, {
    mode: 0o600,
  });
  writeFileSync(markerPath, `${new Date().toISOString()}\n`, { mode: 0o600, flag: "wx" });
  syncFile(receiptPath);
  syncFile(markerPath);
  chmodSync(staging, 0o700);
  syncDirectory(staging);
  if (existsSync(output))
    failStep(`final output appeared during the run: ${output} (no overwrite attempted)`);
  try {
    renameSync(staging, output);
  } catch (error) {
    // Keep staging for analysis; never clean the final name on failure.
    failStep(
      `publishing ${staging} to ${output} failed: ${(error as Error).message}; staging kept for analysis`,
    );
  }
  try {
    syncDirectory(dirname(output));
  } catch {
    // Directory fsync is unsupported on some platforms/filesystems; the
    // rename itself is already atomic and the payload was fully synced.
  }
}

// The single definition of a finished bundle: complete marker plus a TYPED
// receipt (schemaVersion 1, a known kind, and an artifacts inventory). A
// marker next to arbitrary parseable JSON is not a bundle. Downstream
// consumers (packaging/import) must use this validator.
export function isPublishedBundle(directory: string): boolean {
  const marker = join(directory, COMPLETE_MARKER);
  if (!existsSync(marker) || !statSync(marker).isFile()) return false;
  const receiptPath = join(directory, "receipt.json");
  if (!existsSync(receiptPath) || !statSync(receiptPath).isFile()) return false;
  try {
    const receipt = JSON.parse(readFileSync(receiptPath, "utf8")) as {
      schemaVersion?: unknown;
      kind?: unknown;
      artifacts?: unknown;
    };
    if (receipt.schemaVersion !== 1 || typeof receipt.kind !== "string") return false;
    if (receipt.artifacts === undefined || typeof receipt.artifacts !== "object")
      return false;
    return Object.keys(receipt.artifacts as Record<string, unknown>).length > 0;
  } catch {
    return false;
  }
}
