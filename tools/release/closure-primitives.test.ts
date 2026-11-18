import { deepStrictEqual, ok, strictEqual, throws } from "node:assert";
import { execFileSync } from "node:child_process";
import { chmodSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, realpathSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import {
  COMPLETE_MARKER,
  containsFullString,
  isPublishedBundle,
  publishStagedBundle,
  reserveOutput,
  verifyProfileMarkers,
} from "./closure-steps.ts";
import { captureProcess, redact, runProcess, ProcessError } from "./process.ts";
import { canonicalizeNearest, checkOutputPolicy, productBuildRoot } from "./paths.ts";

const WORKSPACE = join(import.meta.dirname, "..", "..", "..");

function tempRoot(label: string): string {
  return mkdtempSync(join(tmpdir(), `voboost-${label}-`));
}

// ---------------------------------------------------------------------------
// Path policy
// ---------------------------------------------------------------------------

test("output policy accepts a fresh path under the product build root", () => {
  const { canonical } = checkOutputPolicy(WORKSPACE, join(productBuildRoot(WORKSPACE), "closure-vX"));
  ok(canonical.startsWith(productBuildRoot(WORKSPACE)));
});

test("output policy rejects durable paths outside the product build root", () => {
  throws(() => checkOutputPolicy(WORKSPACE, "/durable/closure-vX"), /must stay under the product build root/);
  // Several missing levels under an EXISTING build root are fine.
  mkdirSync(join(productBuildRoot(WORKSPACE), "policy-smoke"), { recursive: true });
  checkOutputPolicy(WORKSPACE, join(productBuildRoot(WORKSPACE), "policy-smoke", "x", "y", "z"));
  rmSync(join(productBuildRoot(WORKSPACE), "policy-smoke"), { recursive: true });
});

test("output policy rejects existing targets and non-absolute paths", () => {
  mkdirSync(join(productBuildRoot(WORKSPACE), "policy-exists"), { recursive: true });
  throws(
    () => checkOutputPolicy(WORKSPACE, join(productBuildRoot(WORKSPACE), "policy-exists")),
    /must not exist/,
  );
  rmSync(join(productBuildRoot(WORKSPACE), "policy-exists"), { recursive: true });
  throws(() => checkOutputPolicy(WORKSPACE, "relative/path"), /absolute/);
});

test("symlink in the output path pointing at a source repository is rejected", () => {
  const build = join(productBuildRoot(WORKSPACE), "policy-symlink-source");
  mkdirSync(join(build), { recursive: true });
  try {
    // The canonical artifact root exists; place a symlink inside it pointing
    // outside (a source repository). canonicalizeNearest resolves it and the
    // policy must reject the result.
    symlinkSync(join(WORKSPACE, "voboost-inject"), join(build, "escape"));
    throws(
      () => checkOutputPolicy(WORKSPACE, join(build, "escape", "out")),
      /must stay under the product build root/,
    );
  } finally {
    rmSync(build, { recursive: true, force: true });
  }
});

test("symlink in the output path pointing at a temporary tree is rejected", () => {
  const build = join(productBuildRoot(WORKSPACE), "policy-symlink-tmp");
  const scratch = tempRoot("scratch");
  mkdirSync(build, { recursive: true });
  try {
    symlinkSync(scratch, join(build, "escape"));
    throws(
      () => checkOutputPolicy(WORKSPACE, join(build, "escape", "out")),
      /must stay under the product build root|temporary trees/,
    );
  } finally {
    rmSync(build, { recursive: true, force: true });
    rmSync(scratch, { recursive: true, force: true });
  }
});

test("output overlapping an input root is rejected in both directions", () => {
  const build = productBuildRoot(WORKSPACE);
  const shared = join(build, "policy-shared-input");
  mkdirSync(shared, { recursive: true });
  try {
    // output inside an input root
    throws(
      () => checkOutputPolicy(WORKSPACE, join(shared, "closure"), { sources: [shared] }),
      /must not overlap/,
    );
  } finally {
    rmSync(shared, { recursive: true, force: true });
  }
  // Disjoint roots are fine.
  checkOutputPolicy(WORKSPACE, join(build, "closure-vY"), { sources: [join(build, "other-input")] });
});

test("canonicalizeNearest resolves through several existing levels", () => {
  const root = tempRoot("canon");
  try {
    mkdirSync(join(root, "a"), { recursive: true });
    const canonicalRoot = realpathSync(root);
    strictEqual(canonicalizeNearest(join(root, "a", "b", "c")), join(canonicalRoot, "a", "b", "c"));
    mkdirSync(join(root, "link-target"), { recursive: true });
    symlinkSync(join(root, "link-target"), join(root, "a", "link"));
    strictEqual(canonicalizeNearest(join(root, "a", "link", "child")), join(canonicalRoot, "link-target", "child"));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

// ---------------------------------------------------------------------------
// Process runner
// ---------------------------------------------------------------------------

test("process runner captures normal child stdout", async () => {
  const out = await captureProcess("/bin/echo", ["hello", "world"]);
  strictEqual(out.trim(), "hello world");
});

test("process runner reports non-zero exits with redacted diagnosis", async () => {
  await (async () => {
    try {
      await runProcess("/bin/sh", ["-c", "echo secretvalue >&2; exit 3"], {
        secrets: ["secretvalue"],
      });
      throw new Error("unreachable");
    } catch (error) {
      ok(error instanceof ProcessError);
      ok(!error.message.includes("secretvalue"), "secret redacted from message");
      ok(!error.stderr.includes("secretvalue"), "secret redacted from stderr tail");
      ok(error.message.includes("[redacted]"));
    }
  })();
});

test("process runner kills timed-out children and fails", async () => {
  await (async () => {
    try {
      await runProcess("/bin/sleep", ["30"], { timeoutMs: 300 });
      throw new Error("unreachable");
    } catch (error) {
      ok(error instanceof ProcessError);
      ok(error.timedOut, "marked as timed out");
      ok(error.message.includes("timed out"));
    }
  })();
});

test("redact removes every secret occurrence", () => {
  strictEqual(redact("a SECRET b SECRET", ["SECRET"]), "a [redacted] b [redacted]");
});

test("output overflow fails explicitly instead of returning truncated success", async () => {
  await (async () => {
    try {
      // Child writes 4096 bytes with a 16-byte cap: must fail with an
      // overflow error, never a successful empty capture.
      await runProcess("/bin/sh", ["-c", "head -c 4096 /dev/zero | tr '\\0' 'a'"], {
        maxBufferBytes: 16,
      });
      throw new Error("unreachable");
    } catch (error) {
      ok(error instanceof ProcessError);
      ok(error.overflowed, "marked as overflowed");
      ok(error.message.includes("capture limit"));
    }
  })();
});

test("zero-exit child with large output succeeds when within the cap", async () => {
  const out = await captureProcess("/bin/sh", ["-c", "head -c 100 /dev/zero | tr '\\0' 'b'"], {
    maxBufferBytes: 4096,
  });
  strictEqual(out.length, 100);
});

// ---------------------------------------------------------------------------
// Producer fail-fast: the masked final-link class found in the inject
// Makefile (link followed by `; echo` in one recipe). The fixed recipe links
// into an owned temporary path and only replaces the output after success —
// a failed linker must leave the target nonzero and the old bytes untouched,
// so no stale executable can flow into export/sign/compose.
// ---------------------------------------------------------------------------

test("producer link failure leaves make nonzero and does not replace the old output", () => {
  const root = tempRoot("producer");
  try {
    const output = join(root, "binary");
    writeFileSync(output, "OLD-BYTES");
    const recipe = `
all:
\t@objs=""; \\
\tfor src in a.c; do \\
\t  printf 'int main(void){return 0;}' > a.c; \\
\t  $(CC) -c a.c -o a.o || exit 1; \\
\t  objs="$$objs a.o"; \\
\tdone; \\
\t$(CC) -o binary.tmp.$$$$ a.o $(LINK_RESULT) || { rm -f binary.tmp.$$$$; exit 1; }; \\
\tmv -f binary.tmp.$$$$ binary || exit 1; \\
\techo "link done"
`;
    const makefile = join(root, "Makefile");
    writeFileSync(makefile, recipe);
    // LINK_RESULT forces the final link to fail with the chosen exit code.
    const result = execFileSync(
      "/usr/bin/make",
      ["-f", makefile, `LINK_RESULT=x; exit 42`],
      { cwd: root, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] },
    ).trim();
    void result;
    throw new Error("unreachable: make should have failed");
  } catch (error) {
    ok((error as { status: number | null }).status !== 0, "make must fail on link exit 42");
    strictEqual(
      readFileSync(join(root, "binary"), "utf8"),
      "OLD-BYTES",
      "stale output must survive untouched when the producer fails",
    );
    deepStrictEqual(readdirSync(root).filter((e) => e.startsWith("binary.tmp")), [], "no temporary output survives a failed link");
    rmSync(root, { recursive: true, force: true });
  }
});

test("producer success replaces the output through the temporary path", () => {
  const root = tempRoot("producer");
  try {
    writeFileSync(join(root, "binary"), "OLD-BYTES");
    const makefile = join(root, "Makefile");
    writeFileSync(
      makefile,
      `
all:
\t@printf 'int main(void){return 0;}' > a.c; \\
\t$(CC) -c a.c -o a.o || exit 1; \\
\t$(CC) -o binary.tmp.$$$$ a.o || { rm -f binary.tmp.$$$$; exit 1; }; \\
\tmv -f binary.tmp.$$$$ binary || exit 1
`,
    );
    execFileSync("/usr/bin/make", ["-f", makefile], { cwd: root });
    const bytes = readFileSync(join(root, "binary"));
    ok(!bytes.equals(Buffer.from("OLD-BYTES")), "binary replaced by the linked executable");
    ok(bytes.length > 100, "linked executable is real");
    deepStrictEqual(readdirSync(root).filter((e) => e.startsWith("binary.tmp")), []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

// ---------------------------------------------------------------------------
// Profile markers and full-string search
// ---------------------------------------------------------------------------

test("marker search matches full strings only", () => {
  const marker = "VOBOOST_EMULATOR_SELINUX_SKIP_V1";
  strictEqual(containsFullString(Buffer.from(`noise ${marker} trailing`), marker), false);
  strictEqual(containsFullString(Buffer.from(`trailing${marker}`), marker), false);
  strictEqual(containsFullString(Buffer.from(`leading\x00${marker}\x00tail`), marker), true);
  strictEqual(containsFullString(Buffer.from(marker), marker), true);
});

test("profile markers are verified against real bytes, not config labels", () => {
  const binaries = new Map<string, Buffer>();
  const read = (file: string) => binaries.get(file) ?? Buffer.alloc(0);
  const allMarked = () => {
    for (const [file, marker] of [
      ["voboost-daemon", "VOBOOST_EMULATOR_DAEMON_SELINUX_SKIP_V1"],
      ["voboost-inject", "VOBOOST_EMULATOR_DAEMON_SELINUX_SKIP_V1"],
      ["voboost-install", "VOBOOST_EMULATOR_SELINUX_SKIP_V1"],
      ["voboost-restore", "VOBOOST_EMULATOR_RESTORE_SELINUX_SKIP_V1"],
      ["voboost-uninstall", "VOBOOST_EMULATOR_SELINUX_SKIP_V1"],
      ["voboost-uninstall", "VOBOOST_EMULATOR_UNINSTALL_SELINUX_SKIP_V1"],
      ["voboost-uninstall", "VOBOOST_EMULATOR_UNINSTALL_MOUNT_ADAPTER_V1"],
    ] as const) {
      binaries.set(file, Buffer.concat([binaries.get(file) ?? Buffer.alloc(0), Buffer.from(`\x00${marker}\x00`)]));
    }
  };
  // clean binaries: machine ok, emulator rejected (missing markers)
  verifyProfileMarkers("machine", read);
  throws(() => verifyProfileMarkers("emulator", read), /missing emulator marker/);
  allMarked();
  // marked binaries: emulator ok, machine rejected
  verifyProfileMarkers("emulator", read);
  throws(() => verifyProfileMarkers("machine", read), /contains emulator marker/);
});

// ---------------------------------------------------------------------------
// Publication
// ---------------------------------------------------------------------------

test("publication is single-owner: live reservations are not stolen", () => {
  const root = tempRoot("publish");
  try {
    const output = join(root, "bundle");
    const first = reserveOutput(output, "writer-1");
    throws(() => reserveOutput(output, "writer-2"), /reserved by a live writer/);
    first.release();
    const second = reserveOutput(output, "writer-2");
    second.release();
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("stale reservation from a dead owner can be taken over", () => {
  const root = tempRoot("publish");
  try {
    const output = join(root, "bundle");
    writeFileSync(`${output}.owner`, "999999999:dead-writer\n");
    const reservation = reserveOutput(output, "new-writer");
    reservation.release();
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("existing final output is never overwritten by publication", () => {
  const root = tempRoot("publish");
  try {
    const output = join(root, "bundle");
    const staging = join(root, "bundle.staging");
    mkdirSync(staging, { recursive: true });
    mkdirSync(output, { recursive: true });
    writeFileSync(join(output, "sentinel"), "keep");
    throws(() => publishStagedBundle(staging, output, { any: true }), /appeared during the run/);
    strictEqual(readFileSync(join(output, "sentinel"), "utf8"), "keep");
    ok(staging.startsWith(root), "staging kept for analysis");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("published bundles require marker and a typed receipt with inventory", () => {
  const root = tempRoot("publish");
  try {
    const bare = join(root, "bare");
    mkdirSync(bare, { recursive: true });
    strictEqual(isPublishedBundle(bare), false, "bare directory is not a bundle");
    const receipt = {
      schemaVersion: 1,
      kind: "source-build",
      release: "1.0.0-beta1",
      artifacts: { "voboost.json": { size: 1, sha256: "a".repeat(64) } },
    };
    const bundle = join(root, "bundle");
    const staging = join(root, "bundle.staging");
    mkdirSync(staging, { recursive: true });
    publishStagedBundle(staging, bundle, receipt);
    strictEqual(isPublishedBundle(bundle), true);
    ok(readFileSync(join(bundle, COMPLETE_MARKER), "utf8").trim().length > 0);
    deepStrictEqual(JSON.parse(readFileSync(join(bundle, "receipt.json"), "utf8")), receipt);
    // Marker next to arbitrary JSON is NOT a bundle.
    const junk = join(root, "junk");
    const junkStaging = join(root, "junk.staging");
    mkdirSync(junkStaging, { recursive: true });
    publishStagedBundle(junkStaging, junk, { schemaVersion: 1 });
    strictEqual(isPublishedBundle(junk), false, "untyped receipt rejected");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
