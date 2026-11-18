import { ok, strictEqual, throws } from "node:assert";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import {
  buildInstallerVersionInputs,
  isCanonicalSemVer,
  readFrozenProductVersion,
  writeInstallerVersionInputs,
} from "./installer-version.ts";
import { parseReleaseDefinition, resolveReleaseIdentity } from "./release-identity.ts";

const DEFINITION = `schema = 1
release = "1.1.0-beta.2"
channel = "beta"

[repositories]
app = "${"1".repeat(40)}"
agents = "${"2".repeat(40)}"
inject = "${"3".repeat(40)}"
installer = "${"4".repeat(40)}"
logo = "${"6".repeat(40)}"

[android]
versionCode = 1000007

[compatibility]
installerProtocol = 1
operationProtocol = 1
appDaemonProtocol = 1
agentCatalogSchema = 1
agentEntryAbi = 1
implantAbi = 1

[gates]
host = ["component-checks"]
emulator = ["android-9-arm64"]
device = ["android-9-root"]
vehicle = ["free-09-6.0.3"]
`;

function withDefinition(version: string, channel: string): string {
  return DEFINITION.replace(/1\.1\.0-beta\.2/g, version).replace('channel = "beta"', `channel = "${channel}"`);
}

function channelFor(version: string): string {
  return version.includes("-") ? "beta" : "stable";
}

// Shared SemVer corpus: TS parseReleaseDefinition and the precedence rule
// must agree with the normative policy (numeric identifiers compare
// numerically; build metadata never participates).
test("semver precedence corpus: beta.2 is older than beta.10, metadata is not an upgrade", () => {
  const root = mkdtempSync(join(tmpdir(), "voboost-semver-"));
  try {
    const identityOf = (version: string) => {
      const path = join(root, `input-${version.replace(/[^0-9a-zA-Z.+-]/g, "_")}.toml`);
      writeFileSync(path, withDefinition(version, channelFor(version)));
      return resolveReleaseIdentity(path, join(root, `inputs-${version.replace(/[^0-9a-zA-Z.+-]/g, "_")}`));
    };

    // beta.2 → beta.10 must be accepted (numeric identifier comparison).
    const beta10 = identityOf("1.1.0-beta.10");
    strictEqual(beta10.definition.release, "1.1.0-beta.10");

    // 1.0.0+aaa as base of 1.0.0+bbb: equal precedence — build metadata is
    // NOT a newer release, base_release must be rejected.
    const withMetaBase = DEFINITION.replace(
      'release = "1.1.0-beta.2"',
      'release = "1.1.0"\nbase_release = "1.1.0+aaa"',
    ).replace('channel = "beta"', 'channel = "stable"');
    throws(
      () => parseReleaseDefinition(withMetaBase.replace("1.1.0-beta.2", "1.1.0+bbb")),
      /base_release must be older/,
    );

    // Large numeric identifiers compare numerically: beta.9 < beta.10.
    const base = "1.1.0-beta.9";
    const next = "1.1.0-beta.10";
    const withBase = withDefinition(next, "beta").replace(
      'release = "1.1.0-beta.10"',
      'release = "1.1.0-beta.10"\nbase_release = "1.1.0-beta.9"',
    );
    void base;
    strictEqual(parseReleaseDefinition(withBase).baseRelease, base);
    // Reversed order must fail.
    const reversed = withDefinition(next, "beta").replace(
      'release = "1.1.0-beta.10"',
      'release = "1.1.0-beta.9"\nbase_release = "1.1.0-beta.10"',
    );
    throws(() => parseReleaseDefinition(reversed), /base_release must be older/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("canonical semver corpus", () => {
  for (const valid of ["1.0.0", "0.0.0", "1.1.0-beta.2", "1.1.0-beta.10", "1.0.0-alpha-1", "1.0.0+build.5"]) {
    ok(isCanonicalSemVer(valid), `${valid} is canonical`);
  }
  for (const invalid of [
    "1.0",
    "1.0.0.0",
    "01.0.0",
    "1.0.0-01",
    "1.0.0-",
    "1.0.0+",
    "1.0.0-beta..2",
    "1.0.0-beta_2",
    "v1.0.0",
    "1.0.0 ",
    "",
    "1.0.0+build_1",
  ]) {
    ok(!isCanonicalSemVer(invalid), `${invalid} is not canonical`);
  }
});

test("installer inputs derive from the frozen resolved definition and stay immutable", () => {
  const root = mkdtempSync(join(tmpdir(), "voboost-installer-version-"));
  try {
    const current = join(root, "version.toml");
    writeFileSync(current, DEFINITION);
    const identity = resolveReleaseIdentity(current, join(root, "inputs"));
    const inputs = buildInstallerVersionInputs(identity);
    strictEqual(inputs.productVersion, "1.1.0-beta.2");
    strictEqual(inputs.androidVersionCode, 1000007);
    const paths = writeInstallerVersionInputs(join(root, "inputs"), inputs);
    // Idempotent identical rewrite.
    writeInstallerVersionInputs(join(root, "inputs"), inputs);
    // Different bytes for the same paths are rejected.
    const mutated = { ...inputs, productVersion: "1.1.0-beta.3" };
    throws(
      () => writeInstallerVersionInputs(join(root, "inputs"), mutated),
      /different bytes/,
    );
    // Frozen read is bound to the exact definition digest.
    strictEqual(readFrozenProductVersion(join(root, "inputs"), identity.sha256), "1.1.0-beta.2");
    throws(
      () => readFrozenProductVersion(join(root, "inputs"), "0".repeat(64)),
      /bound to definition/,
    );
    void paths;
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("missing, malformed or non-canonical frozen input fails closed", () => {
  const root = mkdtempSync(join(tmpdir(), "voboost-installer-version-"));
  try {
    throws(() => readFrozenProductVersion(root, "a".repeat(64)), /missing frozen/);
    writeFileSync(join(root, "installer-version.env"), "GARBAGE\n");
    throws(() => readFrozenProductVersion(root, "a".repeat(64)), /malformed/);
    writeFileSync(join(root, "installer-version.env"), "VOBOOST_PRODUCT_VERSION=1.0\n");
    writeFileSync(join(root, "frontend-version.json"), '{"definitionSha256":"' + "a".repeat(64) + '"}');
    throws(() => readFrozenProductVersion(root, "a".repeat(64)), /canonical strict SemVer/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
