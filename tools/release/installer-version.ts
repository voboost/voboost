// Generated installer/frontend version inputs. The single source is the
// frozen resolved release definition: this module writes the compile-time
// inputs (VOBOOST_PRODUCT_VERSION env value, Tauri config override, frontend
// metadata) into the run's inputs directory as one consistent set bound to
// the exact resolved definition SHA-256. Tracked sources are never patched by
// the generator — consumers read these generated files at build time (tauri
// --config, frontend bundler input, cargo env). Like the resolved definition
// itself, the generated inputs are immutable: rewriting with different bytes
// fails; an identical rewrite is idempotent.
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { readReleaseIdentity, type ResolvedReleaseIdentity } from "./release-identity.ts";

const SEMVER_RE =
  /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$/;

export type InstallerVersionInputs = {
  productVersion: string;
  androidVersionCode: number;
  definitionSha256: string;
  tauriConfigOverride: { version: string };
};

export function failVersion(message: string): never {
  throw new Error(`installer-version: ${message}`);
}

export function isCanonicalSemVer(value: string): boolean {
  return SEMVER_RE.test(value);
}

export function buildInstallerVersionInputs(identity: ResolvedReleaseIdentity): InstallerVersionInputs {
  return {
    productVersion: identity.definition.release,
    androidVersionCode: identity.definition.android.versionCode,
    definitionSha256: identity.sha256,
    tauriConfigOverride: { version: identity.definition.release },
  };
}

function immutableWrite(path: string, bytes: string): void {
  if (existsSync(path)) {
    const existing = readFileSync(path);
    if (!existing.equals(Buffer.from(bytes, "utf8")))
      failVersion(`generated version input ${path} already exists with different bytes`);
    return; // identical rewrite is idempotent
  }
  writeFileSync(path, bytes, { mode: 0o600 });
}

export function writeInstallerVersionInputs(inputsDir: string, inputs: InstallerVersionInputs): {
  envPath: string;
  tauriConfigPath: string;
  frontendMetaPath: string;
} {
  mkdirSync(inputsDir, { recursive: true });
  const envPath = join(inputsDir, "installer-version.env");
  const tauriConfigPath = join(inputsDir, "tauri.version.json");
  const frontendMetaPath = join(inputsDir, "frontend-version.json");
  immutableWrite(envPath, `VOBOOST_PRODUCT_VERSION=${inputs.productVersion}\n`);
  immutableWrite(
    tauriConfigPath,
    `${JSON.stringify({ ...inputs.tauriConfigOverride, definitionSha256: inputs.definitionSha256 }, null, 2)}\n`,
  );
  immutableWrite(
    frontendMetaPath,
    `${JSON.stringify({ productVersion: inputs.productVersion, definitionSha256: inputs.definitionSha256 }, null, 2)}\n`,
  );
  return { envPath, tauriConfigPath, frontendMetaPath };
}

// Read the frozen env input for the cargo invocation. The input must exist,
// be well-formed, carry canonical strict SemVer, and be bound to the exact
// resolved definition digest the run froze — a stray or mixed input fails
// closed. No fallback to Cargo.toml/package.json versions.
export function readFrozenProductVersion(inputsDir: string, expectedDefinitionSha256: string): string {
  const envPath = join(inputsDir, "installer-version.env");
  if (!existsSync(envPath)) failVersion(`missing frozen installer version input: ${envPath}`);
  const content = readFileSync(envPath, "utf8");
  const match = /^VOBOOST_PRODUCT_VERSION=(\S+)\n?$/.exec(content);
  if (match === null) failVersion(`malformed installer version input: ${envPath}`);
  const version = match[1];
  if (!isCanonicalSemVer(version))
    failVersion(`frozen installer version is not canonical strict SemVer: ${version}`);
  const bindingPath = join(inputsDir, "frontend-version.json");
  if (existsSync(bindingPath)) {
    const binding = JSON.parse(readFileSync(bindingPath, "utf8")) as { definitionSha256?: unknown };
    if (binding.definitionSha256 !== expectedDefinitionSha256)
      failVersion(
        `installer version inputs are bound to definition ${String(binding.definitionSha256)} but the run froze ${expectedDefinitionSha256}`,
      );
  } else {
    failVersion(`installer version input set is incomplete: ${bindingPath} is missing`);
  }
  return version;
}

if (process.argv[1] !== undefined && process.argv[1].endsWith("installer-version.ts")) {
  const [currentInput, inputsDir] = process.argv.slice(2);
  if (currentInput === undefined || inputsDir === undefined) {
    console.error("usage: installer-version.ts <release/version.toml> <run-inputs-dir>");
    process.exit(2);
  }
  const identity = readReleaseIdentity(currentInput, inputsDir);
  const paths = writeInstallerVersionInputs(inputsDir, buildInstallerVersionInputs(identity));
  console.log(JSON.stringify({ productVersion: identity.definition.release, ...paths }, null, 2));
}
