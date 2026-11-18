import { deepStrictEqual, notStrictEqual, ok, strictEqual, throws } from "node:assert";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { parseReleaseDefinition, readReleaseIdentity, resolveReleaseIdentity } from "./release-identity.ts";

const VALID_BETA1 = `schema = 1
release = "1.0.0-beta1"
channel = "beta"

[repositories]
app = "1111111111111111111111111111111111111111"
agents = "2222222222222222222222222222222222222222"
inject = "3333333333333333333333333333333333333333"
installer = "4444444444444444444444444444444444444444"
logo = "6666666666666666666666666666666666666666"

[android]
versionCode = 1000006

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

function validBeta(version: string, channel = "beta"): string {
  return VALID_BETA1.replace('release = "1.0.0-beta1"', `release = "${version}"`).replace(
    'channel = "beta"',
    `channel = "${channel}"`,
  );
}

function tempRoot(): string {
  return mkdtempSync(join(tmpdir(), "voboost-release-identity-"));
}

test("accepts the current beta1 input and snapshots it as <release>.toml", () => {
  const root = tempRoot();
  try {
    const current = join(root, "version.toml");
    writeFileSync(current, VALID_BETA1);
    const identity = readReleaseIdentity(current, join(root, "inputs"));
    strictEqual(identity.definition.release, "1.0.0-beta1");
    strictEqual(identity.definition.android.versionCode, 1000006);
    strictEqual(identity.definition.repositories.logo, "6".repeat(40));
    ok(identity.resolvedPath.endsWith("1.0.0-beta1.toml"));
    strictEqual(identity.sha256.length, 64);
    deepStrictEqual(readFileSync(identity.resolvedPath, "utf8"), VALID_BETA1);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("authoring input must be named version.toml; anything else is rejected", () => {
  const root = tempRoot();
  try {
    const misnamed = join(root, "1.0.0-beta1.toml");
    writeFileSync(misnamed, VALID_BETA1);
    throws(() => readReleaseIdentity(misnamed, join(root, "inputs")), /version\.toml/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("resolved snapshot is immutable: identical bytes reuse, different bytes fail", () => {
  const root = tempRoot();
  try {
    const current = join(root, "version.toml");
    writeFileSync(current, VALID_BETA1);
    const first = readReleaseIdentity(current, join(root, "inputs"));
    const second = readReleaseIdentity(current, join(root, "inputs"));
    strictEqual(first.sha256, second.sha256);
    // Current input changed mid-run cannot affect the frozen snapshot: a
    // rerun of the same release with different content must fail instead of
    // rewriting history.
    writeFileSync(current, VALID_BETA1.replace("versionCode = 1000006", "versionCode = 1000007"));
    throws(() => readReleaseIdentity(current, join(root, "inputs")), /different bytes/);
    // The previous snapshot stays usable.
    const previous = resolveReleaseIdentity(join(root, "inputs", "1.0.0-beta1.toml"), join(root, "inputs-2"));
    notStrictEqual(previous.resolvedPath, first.resolvedPath);
    strictEqual(previous.sha256, first.sha256);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("a different next test version resolves into its own snapshot", () => {
  const root = tempRoot();
  try {
    for (const [version, channel] of [
      ["1.0.0-beta2", "beta"],
      ["1.1.0", "stable"],
    ] as const) {
      const current = join(root, "version.toml");
      writeFileSync(current, validBeta(version, channel));
      const identity = readReleaseIdentity(current, join(root, `inputs-${version}`));
      strictEqual(identity.definition.release, version);
      ok(identity.resolvedPath.endsWith(`${version}.toml`));
    }
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("missing required fields, extra fields and invalid values are rejected", () => {
  throws(() => parseReleaseDefinition(VALID_BETA1.replace('[android]\nversionCode = 1000006\n', "")), /android/);
  throws(() => parseReleaseDefinition(`${VALID_BETA1}\nextra = "field"\n`), /unknown field/);
  throws(
    () => parseReleaseDefinition(VALID_BETA1.replace(`installer = "${"4".repeat(40)}"`, 'installer = "deadbeef"')),
    /40-character/,
  );
  throws(() => parseReleaseDefinition(validBeta("1.0.0-beta1", "stable")), /must not have a prerelease/);
  throws(() => parseReleaseDefinition(validBeta("1.0.0")), /beta prerelease/);
  // Numeric prerelease identifiers with leading zeros are non-canonical on
  // both the TS and the Rust side; alphanumeric ones like beta01 are legal.
  throws(() => parseReleaseDefinition(validBeta("1.0.0-01")), /SemVer/);
  throws(() => parseReleaseDefinition(validBeta("1.0.0-beta1").replace("versionCode = 1000006", "versionCode = 0")), /positive/);
  throws(() => parseReleaseDefinition(VALID_BETA1.replace('host = ["component-checks"]', "host = []")), /at least one/);
  throws(() => parseReleaseDefinition("not toml ["), /TOML/);
  throws(() => parseReleaseDefinition(VALID_BETA1.replace("schema = 1", "schema = 2")), /schema/);
  throws(
    () =>
      parseReleaseDefinition(
        VALID_BETA1.replace('release = "1.0.0-beta1"', 'release = "1.0.0-beta1"\nbase_release = "1.0.0-beta1"'),
      ),
    /base_release must be older/,
  );
});

test("source identity rejects retired license pin", () => {
  const definition = parseReleaseDefinition(VALID_BETA1);
  strictEqual(Object.keys(definition.repositories).length, 5);
  throws(() => parseReleaseDefinition(VALID_BETA1.replace("[repositories]", '[repositories]\nlicense = "5555555555555555555555555555555555555555"')), /unknown/);
});
