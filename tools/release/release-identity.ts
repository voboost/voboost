// Release identity: the single editable current release input is
// release/version.toml. Every build run reads it exactly once, validates the
// full definition contract (shared with the Rust authority in
// release/definition.rs), and writes an immutable resolved snapshot named
// <release>.toml into the run's inputs directory. Compose/restore/evidence and
// every version consumer use those frozen bytes and their SHA-256 — a later
// edit of version.toml cannot change an in-flight run, and previous snapshots
// stay usable.
//
// Semantic authority is shared with Rust: the checks below mirror
// release/definition.rs (schema, channel/prerelease rules, full commits,
// versionCode bounds, positive compatibility, non-empty canonical gates) and
// the resolved snapshot must additionally pass the Rust filename check
// (filename == <release>.toml). The authoring input version.toml is validated
// without the filename rule; nothing downstream may consume it directly.
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename, join } from "node:path";
import { parse as parseToml } from "smol-toml";

export const MAX_APK_VERSION_CODE = 2_100_000_000n;

export type ReleaseDefinition = {
  schema: number;
  release: string;
  channel: string;
  baseRelease?: string;
  repositories: {
    app: string;
    agents: string;
    inject: string;
    installer: string;
    logo: string;
  };
  android: { versionCode: number };
  compatibility: {
    installerProtocol: number;
    operationProtocol: number;
    appDaemonProtocol: number;
    agentCatalogSchema: number;
    agentEntryAbi: number;
    implantAbi: number;
  };
  gates: { host: string[]; emulator: string[]; device: string[]; vehicle: string[] };
};

export type ResolvedReleaseIdentity = {
  definition: ReleaseDefinition;
  /** Bytes of the immutable resolved snapshot (not of version.toml). */
  bytes: Buffer;
  resolvedPath: string;
  sha256: string;
};

export function failIdentity(message: string): never {
  throw new Error(`release-identity: ${message}`);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function requireFields(
  container: Record<string, unknown>,
  required: readonly string[],
  where: string,
  optional: readonly string[] = [],
): void {
  for (const field of required) {
    if (!(field in container)) failIdentity(`${where}.${field} is required`);
  }
  for (const key of Object.keys(container)) {
    if (!required.includes(key) && !optional.includes(key))
      failIdentity(`${where}.${key} is an unknown field`);
  }
}

function positiveInt(container: Record<string, unknown>, field: string, where: string): number {
  const value = container[field];
  if (typeof value !== "number" || !Number.isSafeInteger(value) || value <= 0)
    failIdentity(`${where}.${field} must be a positive integer`);
  return value;
}

// Canonical strict SemVer, mirroring Rust's canonical_version: parse strictly,
// then require the canonical spelling to round-trip (no leading zeros, no
// whitespace, prerelease identifiers without leading zeros).
const SEMVER_RE =
  /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/;

function canonicalVersion(value: string, field: string): { pre: string } {
  const match = SEMVER_RE.exec(value);
  if (match === null) failIdentity(`${field} must be strict canonical SemVer`);
  return { pre: match[4] ?? "" };
}

// SemVer precedence (build metadata excluded): core numerically; prerelease
// identifiers pairwise — numeric identifiers numerically and lower than
// alphanumeric ones, alphanumeric lexically ASCII, a longer identifier list
// wins when all shared identifiers are equal; a release WITHOUT a prerelease
// has higher precedence than the same release with one.
function compareSemVer(left: string, right: string): number {
  const stripMetadata = (value: string) => value.split("+")[0];
  const [lCore, lPre] = splitPre(stripMetadata(left));
  const [rCore, rPre] = splitPre(stripMetadata(right));
  const l = lCore.split(".").map(Number);
  const r = rCore.split(".").map(Number);
  for (let i = 0; i < 3; i += 1) {
    if (l[i] !== r[i]) return l[i] < r[i] ? -1 : 1;
  }
  if (lPre === undefined && rPre === undefined) return 0;
  if (lPre === undefined) return 1;
  if (rPre === undefined) return -1;
  const lIdentifiers = lPre.split(".");
  const rIdentifiers = rPre.split(".");
  const shared = Math.min(lIdentifiers.length, rIdentifiers.length);
  for (let i = 0; i < shared; i += 1) {
    const a = lIdentifiers[i];
    const b = rIdentifiers[i];
    const aNumeric = /^\d+$/.test(a);
    const bNumeric = /^\d+$/.test(b);
    if (aNumeric && bNumeric) {
      const aNumber = Number(a);
      const bNumber = Number(b);
      if (aNumber !== bNumber) return aNumber < bNumber ? -1 : 1;
    } else if (aNumeric !== bNumeric) {
      return aNumeric ? -1 : 1;
    } else if (a !== b) {
      return a < b ? -1 : 1;
    }
  }
  if (lIdentifiers.length === rIdentifiers.length) return 0;
  return lIdentifiers.length < rIdentifiers.length ? -1 : 1;
}

function splitPre(value: string): [string, string | undefined] {
  const index = value.indexOf("-");
  return index === -1 ? [value, undefined] : [value.slice(0, index), value.slice(index + 1)];
}

function fullCommit(value: unknown, where: string): string {
  if (typeof value !== "string" || !/^[0-9a-f]{40}$/.test(value))
    failIdentity(`${where} must be a full 40-character lowercase Git commit`);
  return value;
}

export function parseReleaseDefinition(source: string): ReleaseDefinition {
  let raw: unknown;
  try {
    raw = parseToml(source);
  } catch (error) {
    failIdentity(`definition is not valid TOML: ${(error as Error).message}`);
  }
  if (!isPlainObject(raw)) failIdentity("definition must be a TOML table");
  requireFields(
    raw,
    ["schema", "release", "channel", "repositories", "android", "compatibility", "gates"],
    "definition",
    ["base_release"],
  );
  if (raw.schema !== 1) failIdentity(`unsupported definition schema ${String(raw.schema)}`);

  const release = raw.release;
  const channel = raw.channel;
  if (typeof release !== "string" || typeof channel !== "string")
    failIdentity("release and channel must be strings");
  const version = canonicalVersion(release, "release");
  if (channel === "stable") {
    if (version.pre !== "") failIdentity("stable release must not have a prerelease");
  } else if (channel === "beta") {
    if (version.pre === "" || !version.pre.startsWith("beta"))
      failIdentity("beta release must use a beta prerelease");
  } else {
    failIdentity(`unsupported channel ${JSON.stringify(channel)}`);
  }

  if ("base_release" in raw) {
    const base = raw.base_release;
    if (typeof base !== "string") failIdentity("base_release must be a string");
    canonicalVersion(base, "base_release");
    if (compareSemVer(base, release) >= 0)
      failIdentity("base_release must be older than release");
  }

  if (!isPlainObject(raw.repositories)) failIdentity("repositories must be a table");
  requireFields(raw.repositories, ["app", "agents", "inject", "installer", "logo"], "repositories");
  const repositories = {
    app: fullCommit(raw.repositories.app, "repositories.app"),
    agents: fullCommit(raw.repositories.agents, "repositories.agents"),
    inject: fullCommit(raw.repositories.inject, "repositories.inject"),
    installer: fullCommit(raw.repositories.installer, "repositories.installer"),
    logo: fullCommit(raw.repositories.logo, "repositories.logo"),
  };

  if (!isPlainObject(raw.android)) failIdentity("android must be a table");
  requireFields(raw.android, ["versionCode"], "android");
  const versionCode = positiveInt(raw.android, "versionCode", "android");
  if (BigInt(versionCode) > MAX_APK_VERSION_CODE)
    failIdentity(`android.versionCode must be in 1..=${MAX_APK_VERSION_CODE}`);

  if (!isPlainObject(raw.compatibility)) failIdentity("compatibility must be a table");
  requireFields(
    raw.compatibility,
    ["installerProtocol", "operationProtocol", "appDaemonProtocol", "agentCatalogSchema", "agentEntryAbi", "implantAbi"],
    "compatibility",
  );
  const compatibility = {
    installerProtocol: positiveInt(raw.compatibility, "installerProtocol", "compatibility"),
    operationProtocol: positiveInt(raw.compatibility, "operationProtocol", "compatibility"),
    appDaemonProtocol: positiveInt(raw.compatibility, "appDaemonProtocol", "compatibility"),
    agentCatalogSchema: positiveInt(raw.compatibility, "agentCatalogSchema", "compatibility"),
    agentEntryAbi: positiveInt(raw.compatibility, "agentEntryAbi", "compatibility"),
    implantAbi: positiveInt(raw.compatibility, "implantAbi", "compatibility"),
  };

  if (!isPlainObject(raw.gates)) failIdentity("gates must be a table");
  requireFields(raw.gates, ["host", "emulator", "device", "vehicle"], "gates");
  const gates: ReleaseDefinition["gates"] = {} as ReleaseDefinition["gates"];
  for (const kind of ["host", "emulator", "device", "vehicle"] as const) {
    const list = raw.gates[kind];
    if (!Array.isArray(list) || list.length === 0)
      failIdentity(`gates.${kind} must name at least one required gate`);
    for (const gate of list) {
      if (typeof gate !== "string" || gate.trim() === "" || gate !== gate.trim())
        failIdentity(`gates.${kind} contains an empty or non-canonical name`);
    }
    gates[kind] = list as string[];
  }

  const definition: ReleaseDefinition = {
    schema: 1,
    release,
    channel,
    ...(raw.base_release === undefined ? {} : { baseRelease: raw.base_release as string }),
    repositories,
    android: { versionCode },
    compatibility,
    gates,
  };
  return definition;
}

// Resolved snapshots are immutable: if one already exists it must be the same
// bytes, otherwise the run fails instead of overwriting history.
export function resolveReleaseIdentity(
  currentInput: string,
  inputsDir: string,
): ResolvedReleaseIdentity {
  const bytes = readFileSync(currentInput);
  const definition = parseReleaseDefinition(bytes.toString("utf8"));
  mkdirSync(inputsDir, { recursive: true });
  const resolvedPath = join(inputsDir, `${definition.release}.toml`);
  if (existsSync(resolvedPath)) {
    const existing = readFileSync(resolvedPath);
    if (!existing.equals(bytes))
      failIdentity(`resolved definition ${resolvedPath} already exists with different bytes`);
  } else {
    writeFileSync(resolvedPath, bytes, { mode: 0o600 });
  }
  return {
    definition,
    bytes,
    resolvedPath,
    sha256: createHash("sha256").update(bytes).digest("hex"),
  };
}

// Authoring input must be exactly release/version.toml inside the product
// repository; anything else is rejected so accidental reads of historical
// snapshots cannot pose as the current input.
export function readReleaseIdentity(
  currentInput: string,
  inputsDir: string,
): ResolvedReleaseIdentity {
  if (basename(currentInput) !== "version.toml")
    failIdentity("authoring input must be named version.toml");
  return resolveReleaseIdentity(currentInput, inputsDir);
}

if (process.argv[1] !== undefined && process.argv[1].endsWith("release-identity.ts")) {
  const [currentInput, inputsDir] = process.argv.slice(2);
  if (currentInput === undefined || inputsDir === undefined) {
    console.error("usage: release-identity.ts <release/version.toml> <run-inputs-dir>");
    process.exit(2);
  }
  const identity = readReleaseIdentity(currentInput, inputsDir);
  console.log(
    JSON.stringify(
      {
        release: identity.definition.release,
        channel: identity.definition.channel,
        resolvedPath: identity.resolvedPath,
        sha256: identity.sha256,
      },
      null,
      2,
    ),
  );
}
