// Full source-build entrypoint: builds a new complete closure from the named
// sources — it never reads a retained base closure from local-builds and
// never falls back to stale outputs. Ports the orchestration of
// release/build-emulator-beta1.sh and release/local-beta1-ci.sh onto the
// shared primitives (paths/process/closure-steps) with the resolved release
// identity from release/version.toml as the single current version input.
//
// The machine profile is the full-source build. The emulator profile cannot
// currently be produced from source (its skip markers were retired from the
// native sources) and is rejected here with an actionable error instead of
// being faked from a retained closure.
import { createHash } from "node:crypto";
import { requireAndroidVersionCodeAgreement } from "./android-version-preflight.ts";
import {
  chmodSync,
  cpSync,
  existsSync,
  lstatSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  realpathSync,
  renameSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { basename, dirname, isAbsolute, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  NON_ELF_FILES,
  NATIVE_ROLES,
  reserveOutput,
  releaseCli,
  publishStagedBundle,
  verifyProfileMarkers,
} from "./closure-steps.ts";
import { checkOutputPolicy, productBuildRoot } from "./paths.ts";
import { COMPONENT_BUILD_TIMEOUT_MS, METADATA_TIMEOUT_MS, captureProcess, runProcess } from "./process.ts";
import { readReleaseIdentity, type ResolvedReleaseIdentity } from "./release-identity.ts";
import {
  buildInstallerVersionInputs,
  writeInstallerVersionInputs,
} from "./installer-version.ts";

import { stageResourceArtifacts } from "./resource-artifacts.ts";
import { stageQnxResourceArtifacts } from "./qnx-resource-artifacts.ts";

export type SourceMode = "local-working-tree" | "exact-source";
export type AgentSet = "release" | "development";
export type Profile = "machine" | "emulator";

export type BuildClosureConfig = {
  workspaceRoot: string;
  output: string;
  authorityDir: string;
  androidSdkRoot: string;
  androidNdkHome: string;
  profile: Profile;
  sourceMode: SourceMode;
  /** Development candidates are for local device testing only. Defaults to release. */
  agentSet?: AgentSet;
  /** Optional pinned NDK revision (Pkg.Revision) enforced against the installed NDK. */
  androidNdkRevision?: string;
  qnxLocaleInputsManifest?: string;
  qnxLibraries?: Record<"libc" | "socket" | "ssl" | "crypto", string>;
  /** Per-repository directory overrides (exact-source builds from clean
   * worktrees at the pinned commit). Keys: app, agents, inject, installer,
   * logo, product, codestyle. */
  repositories?: Partial<Record<string, string>>;
};

const PINNED_BUILD_TOOLS = "34.0.0";
const AUTHORITY_ROLES = ["agent-catalog", "product", "native-executable", "restore"] as const;

export function failBuild(message: string): never {
  throw new Error(`build-closure: ${message}`);
}

// Strict runtime validation: TS `as BuildClosureConfig` casts are not
// validation. Types, allowed fields and absolute paths are all enforced.
export function parseBuildClosureConfig(raw: unknown): BuildClosureConfig {
  if (typeof raw !== "object" || raw === null || Array.isArray(raw))
    failBuild("config must be a JSON object");
  const allowed = [
    "workspaceRoot",
    "output",
    "authorityDir",
    "androidSdkRoot",
    "androidNdkHome",
    "profile",
    "sourceMode",
    "agentSet",
    "androidNdkRevision",
    "qnxLibraries",
    "qnxLocaleInputsManifest",
    "repositories",
  ];
  for (const key of Object.keys(raw as Record<string, unknown>)) {
    if (!allowed.includes(key)) failBuild(`config has an unknown field: ${key}`);
  }
  const config = raw as Record<string, unknown>;
  for (const field of allowed) {
    if (field === "agentSet" || field === "repositories" || field === "androidNdkRevision" || field === "qnxLibraries" || field === "qnxLocaleInputsManifest") continue;
    if (typeof config[field] !== "string")
      failBuild(`config.${field} is required and must be a string`);
  }
  if (config.profile !== "machine" && config.profile !== "emulator")
    failBuild("config.profile must be machine or emulator");
  if (config.sourceMode !== "local-working-tree" && config.sourceMode !== "exact-source")
    failBuild("config.sourceMode must be local-working-tree or exact-source");
  if (config.agentSet !== undefined && config.agentSet !== "release" && config.agentSet !== "development")
    failBuild("config.agentSet must be release or development");
  if (config.agentSet === "development" && config.sourceMode !== "local-working-tree")
    failBuild("development agentSet requires local-working-tree sourceMode");
  if (config.androidNdkRevision !== undefined && !/^[0-9]+(\.[0-9]+)*$/.test(String(config.androidNdkRevision)))
    failBuild("config.androidNdkRevision must be a dotted revision like 29.0.14206865");
  for (const field of ["workspaceRoot", "output", "authorityDir", "androidSdkRoot", "androidNdkHome"]) {
    if (!isAbsolute(config[field] as string))
      failBuild(`config.${field} must be an absolute path`);
  }
  const repositoryNames = ["product", "app", "agents", "inject", "installer", "logo", "codestyle"];
  const repositories: Record<string, string> = {};
  let qnxLibraries: BuildClosureConfig["qnxLibraries"];
  if (config.qnxLibraries !== undefined) {
    if (typeof config.qnxLibraries !== "object" || config.qnxLibraries === null || Array.isArray(config.qnxLibraries))
      failBuild("config.qnxLibraries must be an exact library-path object");
    const libraries = config.qnxLibraries as Record<string, unknown>;
    const roles = ["libc", "socket", "ssl", "crypto"];
    if (Object.keys(libraries).length !== roles.length || Object.keys(libraries).some(key => !roles.includes(key)))
      failBuild("config.qnxLibraries requires exactly libc, socket, ssl, crypto");
    for (const role of roles) {
      if (typeof libraries[role] !== "string" || !isAbsolute(libraries[role] as string))
        failBuild(`config.qnxLibraries.${role} must be an absolute path`);
    }
    qnxLibraries = { ...libraries } as NonNullable<BuildClosureConfig["qnxLibraries"]>;
  }
  if (config.qnxLocaleInputsManifest !== undefined && (typeof config.qnxLocaleInputsManifest !== "string" || !isAbsolute(config.qnxLocaleInputsManifest)))
    failBuild("config.qnxLocaleInputsManifest must be an absolute path");
  if (config.agentSet === "development" && config.qnxLocaleInputsManifest === undefined)
    failBuild("development i18n selection requires qnxLocaleInputsManifest");
  if (config.repositories !== undefined) {
    if (typeof config.repositories !== "object" || Array.isArray(config.repositories))
      failBuild("config.repositories must be an object of name → absolute directory");
    for (const [name, directory] of Object.entries(config.repositories as Record<string, unknown>)) {
      if (!repositoryNames.includes(name)) failBuild(`config.repositories has an unknown repository: ${name}`);
      if (typeof directory !== "string" || !isAbsolute(directory))
        failBuild(`config.repositories.${name} must be an absolute directory`);
      repositories[name] = directory;
    }
  }
  return {
    workspaceRoot: config.workspaceRoot as string,
    output: config.output as string,
    authorityDir: config.authorityDir as string,
    androidSdkRoot: config.androidSdkRoot as string,
    androidNdkHome: config.androidNdkHome as string,
    profile: config.profile as Profile,
    sourceMode: config.sourceMode as SourceMode,
    agentSet: (config.agentSet ?? "release") as AgentSet,
    ...(config.androidNdkRevision === undefined
      ? {}
      : { androidNdkRevision: config.androidNdkRevision as string }),
    ...(Object.keys(repositories).length === 0 ? {} : { repositories }),
    ...(qnxLibraries === undefined ? {} : { qnxLibraries }),
    ...(config.qnxLocaleInputsManifest === undefined ? {} : { qnxLocaleInputsManifest: config.qnxLocaleInputsManifest as string }),
  };
}

// Explicit property overrides ambient Gradle defaults for both build modes.
export function nativeRegistryArguments(config: BuildClosureConfig): string[] {
  return ["--no-daemon", "generateNativeRegistry", `-PvoboostAgentSet=${config.agentSet ?? "release"}`,
    `-PnativeContract=${join(repositoryDirectory(config.workspaceRoot, config, "inject"), "contract/native_agent.rs")}`];
}

export function agentBuildArguments(config: BuildClosureConfig, qnxAgents: string, llvmBin: string): string[] {
  const agentSet = config.agentSet ?? "release";
  if (agentSet === "development" && config.sourceMode !== "local-working-tree")
    failBuild("development agentSet requires local-working-tree sourceMode");
  return [agentSet === "development" ? "bundleAgents" : "buildRelease", "--no-daemon",
    `-PvoboostAgentSet=${agentSet}`, `-PclusterNativeDirectory=${qnxAgents}`, `-PvoboostI18nLlvmBin=${llvmBin}`,
    `-PnativeContract=${join(repositoryDirectory(config.workspaceRoot, config, "inject"), "contract/native_agent.rs")}`,
    ...(agentSet === "development" ? [`-Pi18nNativeModuleDirectory=${qnxAgents}`, `-PpwNativeModuleDirectory=${join(dirname(qnxAgents), "pw-inert")}`] : [])];
}

// JSON preserves paths containing spaces as individual Java arguments.
export function qnxJavaBuildArguments(kind: "product" | "runtime" | "module" | "pw-module", arguments_: string[], llvmBin: string): string[] {
  const task = kind === "pw-module" ? "runQnxPwModuleBuild" : kind === "product" ? "runQnxProductBuild" : kind === "runtime" ? "runQnxRuntimeBuild" : "runQnxNativeModuleBuild";
  const property = kind === "pw-module" ? "qnxPwModuleArguments" : kind === "product" ? "qnxProductArguments" : kind === "runtime" ? "qnxRuntimeArguments" : "qnxModuleArguments";
  return [task, "--no-daemon", "--console=plain", "-P" + property + "=" + JSON.stringify(arguments_), `-PvoboostI18nLlvmBin=${llvmBin}`];
}

export function qnxLocaleBuildArguments(manifest: string, llvmBin: string): string[] {
  return ["buildI18nQnxResources", "--no-daemon", "--console=plain",
    `-PvoboostQnxLocaleInputs=${manifest}`, `-PvoboostI18nLlvmBin=${llvmBin}`];
}

// The inert output is separate from catalogued native modules, preserving repository overrides.
export function qnxPwModuleInputs(agents: string, inject: string, ndk: string): string[] {
  return ["--agents", agents, "--inject", inject, "--sdk", join(inject, "build/cluster/product/sdk-v3"),
    "--ndk", ndk, "--output", join(agents, "build/cluster/pw-inert")];
}

// Exported bytes and canonical source are independent explicit build inputs.
export function qnxRuntimeInputs(agents: string, inject: string, ndk: string, output: string): string[] {
  return ["--agents", agents, "--sdk", join(inject, "build/cluster/product/sdk-v3"),
    "--source-sdk", join(inject, "cluster/sdk"), "--ndk", ndk, "--output", output];
}

// Record the actual catalog bytes and statuses without promoting candidates.
export function observeAgentBuild(config: BuildClosureConfig, qnxAgents: string, llvmBin: string, catalog: Buffer) {
  const agents = JSON.parse(catalog.toString("utf8")).agents as Array<{ id: string; status?: string }>;
  return {
    arguments: agentBuildArguments(config, qnxAgents, llvmBin),
    catalogSha256: createHash("sha256").update(catalog).digest("hex"),
    observedAgents: agents.map(agent => ({ id: agent.id, ...(agent.status === undefined ? {} : { status: agent.status }) })),
  };
}

function repositoryDirectory(
  workspaceRoot: string,
  config: BuildClosureConfig | undefined,
  name: string,
): string {
  const override = config?.repositories?.[name];
  if (override !== undefined) return override;
  return join(workspaceRoot, name === "product" ? "voboost" : `voboost-${name}`);
}

// Ordered plan derived only from the validated config — one routine shared by
// the CLI run and CI dry-runs so both must agree on the same definition.
export function planBuild(config: BuildClosureConfig): string[] {
  return [
    "freeze-identity",
    "validate-authority",
    "validate-toolchain",
    "snapshot-sources-before",
    "generate-native-registry",
    "build-natives",
    "build-qnx-natives",
    "build-qnx-agents",
    "export-native-inventory",
    "build-app-apk",
    "sign-app-apk",
    "build-agents",
    "sign-agents",
    "stage-natives",
    "sign-natives",
    "inspect-apk",
    "compose-and-sign-product",
    "verify-closure",
    "verify-profile-markers",
    "snapshot-sources-after",
    "evidence-inventory",
    "snapshot-sources-final",
    "publish-bundle",
  ];
}

// ---------------------------------------------------------------------------
// Source snapshots (port of local-beta1-ci.sh record_repository): HEAD, tree
// identity of the working tree via a scratch git index (the real index is
// never touched), and a status digest.
// ---------------------------------------------------------------------------

export type SubmoduleSnapshot = {
  path: string;
  initialized: boolean;
  gitlinkMode: string;
  gitlinkCommit: string;
  head: string;
  // Digest over the ACTUAL build inputs inside the submodule: index blob
  // identities, working-tree diff (tracked modifications and applied
  // patches), and untracked non-ignored sources — recursively including
  // nested submodules. A changed dep/input.c under an unchanged gitlink
  // changes this digest.
  contentDigest: string;
  // True when the submodule working tree exactly matches its HEAD: no
  // tracked modifications, no untracked sources, nested submodules clean.
  clean: boolean;
  nested: SubmoduleSnapshot[];
};

export type RepositorySnapshot = {
  name: string;
  head: string;
  tree: string;
  statusDigest: string;
  // True when the working tree exactly matches HEAD, including submodule
  // contents (no drift invisible to the gitlink).
  clean: boolean;
  submodules: SubmoduleSnapshot[];
};

const EMPTY_SHA256_LENGTH = 64;

async function gitText(directory: string, args: string[], env: Record<string, string> = {}): Promise<string> {
  const stdout = await captureProcess("git", ["-C", directory, ...args], {
    env,
    timeoutMs: METADATA_TIMEOUT_MS,
  });
  return stdout;
}

async function workingTreeIdentity(directory: string, label: string): Promise<string> {
  const gitDirectory = (await gitText(directory, ["rev-parse", "--path-format=absolute", "--git-common-dir"])).trim();
  const scratch = join(productBuildRootScratch(), `snapshot-${process.pid}-${createHash("sha256").update(label).digest("hex")}`);
  rmSync(scratch, { recursive: true, force: true });
  mkdirSync(join(scratch, "objects"), { recursive: true });
  const scratchEnv = {
    GIT_INDEX_FILE: join(scratch, "index"),
    GIT_OBJECT_DIRECTORY: join(scratch, "objects"),
    GIT_ALTERNATE_OBJECT_DIRECTORIES: join(gitDirectory, "objects"),
  };
  try {
    await gitText(directory, ["read-tree", "HEAD"], scratchEnv);
    await gitText(directory, ["-c", "advice.addEmbeddedRepo=false", "add", "-A", "--", "."], scratchEnv);
    return (await gitText(directory, ["write-tree"], scratchEnv)).trim();
  } finally {
    rmSync(scratch, { recursive: true, force: true });
  }
}

async function snapshotSubmodule(path: string, directory: string, depth: number, parent: string, relative: string): Promise<SubmoduleSnapshot> {
  const indexed = await gitText(parent, ["ls-files", "--stage", "-z", "--", relative]);
  const match = /^160000 ([0-9a-f]{40}|[0-9a-f]{64}) 0\t([^\0]+)\0$/.exec(indexed);
  if (!match || match[2] !== relative) failBuild(`invalid or unmerged submodule gitlink: ${path}`);
  const gitlinkMode = "160000", gitlinkCommit = match[1];
  if (!existsSync(join(directory, ".git"))) {
    if (existsSync(directory) && readdirSync(directory).length !== 0)
      failBuild(`uninitialized or broken submodule has nonempty workdir: ${path}`);
    return {path, initialized:false, gitlinkMode, gitlinkCommit, head:gitlinkCommit,
      contentDigest:createHash("sha256").update(`uninitialized\t${gitlinkMode}\t${gitlinkCommit}\n`).digest("hex"), clean:true, nested:[]};
  }
  // A nonempty or broken checkout is never silently reclassified as empty.
  const head = (await gitText(directory, ["rev-parse", "HEAD"])).trim();
  const diff = await gitText(directory, ["diff", "--no-ext-diff", "--binary"]);
  const status = await gitText(directory, ["status", "--porcelain=v1", "-z", "--untracked-files=all"]);
  const files = await gitText(directory, ["ls-files", "-s"]);
  const parts: Buffer[] = [];
  parts.push(Buffer.from(`head\t${head}\n`));
  parts.push(Buffer.from(files));
  parts.push(Buffer.from(diff));
  parts.push(Buffer.from(status));
  parts.push(Buffer.from(await workingTreeIdentity(directory, `submodule:${path}`)));
  const nested: SubmoduleSnapshot[] = [];
  if (existsSync(join(directory, ".gitmodules"))) {
    const config = await gitText(directory, ["config", "--file", ".gitmodules", "--get-regexp", "^submodule\..*\.path$"]);
    for (const line of config.split("\n")) {
      const subPath = line.split(" ").pop()?.trim();
      if (line === "" || subPath === undefined) continue;
      const nestedDir = join(directory, subPath);
      if (depth <= 0) failBuild(`submodule nesting exceeds supported depth: ${path}/${subPath}`);
      nested.push(await snapshotSubmodule(`${path}/${subPath}`, nestedDir, depth - 1, directory, subPath));
    }
  }
  const digest = createHash("sha256");
  for (const part of parts) digest.update(part);
  for (const child of nested) digest.update(Buffer.from(`${JSON.stringify(child)}\n`));
  const clean = diff === "" && status === "" && nested.every((child) => child.clean);
  return { path, initialized:true, gitlinkMode, gitlinkCommit, head, contentDigest: digest.digest("hex"), clean, nested };
}

export function requireUnchangedSourceSnapshots(
  before: readonly RepositorySnapshot[],
  after: readonly RepositorySnapshot[],
): void {
  if (before.length !== after.length) failBuild("source repository inventory changed during the run");
  for (let index = 0; index < before.length; index += 1) {
      if (
        before[index].head !== after[index].head ||
        before[index].tree !== after[index].tree ||
        before[index].statusDigest !== after[index].statusDigest ||
        JSON.stringify(before[index].submodules) !== JSON.stringify(after[index].submodules)
      )
        failBuild(
          `source drift during the run: ${before[index].name} identity changed (build outputs changed the tree — exclude them from the source tree)`,
        );
    }
}

export async function snapshotRepository(
  name: string,
  directory: string,
): Promise<RepositorySnapshot> {
  const head = (await gitText(directory, ["rev-parse", "HEAD"])).trim();
  // Full status including submodule dirtiness (--ignore-submodules=none):
  // nested content changes are part of the source identity, never invisible.
  const status = await gitText(directory, [
    "status",
    "--porcelain=v1",
    "-z",
    "--untracked-files=all",
    "--ignore-submodules=none",
  ]);
  const statusDigest = createHash("sha256").update(status).digest("hex");
  const statusClean = status === "";
  const submodules: SubmoduleSnapshot[] = [];
  if (existsSync(join(directory, ".gitmodules"))) {
    const config = await gitText(directory, ["config", "--file", ".gitmodules", "--get-regexp", "^submodule\..*\.path$"]);
    for (const line of config.split("\n")) {
      const subPath = line.split(" ").pop()?.trim();
      if (line === "" || subPath === undefined) continue;
      const subDir = join(directory, subPath);
      submodules.push(await snapshotSubmodule(subPath, subDir, 3, directory, subPath));
    }
  }
  const tree = await workingTreeIdentity(directory, `repository:${name}`);
  const clean = statusClean && submodules.every((submodule) => submodule.clean);
  return { name, head, tree, statusDigest, clean, submodules };
}

function productBuildRootScratch(): string {
  // Scratch git indexes are ephemeral state, not release artifacts.
  const scratch = join(import.meta.dirname, "..", "..", "..", ".work162", "build-closure-scratch");
  mkdirSync(scratch, { recursive: true });
  return scratch;
}

async function snapshotWorkspaceSources(
  workspaceRoot: string,
  identity: ResolvedReleaseIdentity,
  sourceMode: SourceMode,
  config?: BuildClosureConfig,
): Promise<RepositorySnapshot[]> {
  const definition = identity.definition;
  // codestyle is an external Gradle build input of the app repository
  // (../voboost-codestyle/codestyle.gradle); it is recorded as an
  // identity-only source input (no definition commit is pinned for it yet).
  const repositories: Array<[string, string | undefined, string | undefined]> = [
    ["product", repositoryDirectory(workspaceRoot, config, "product"), undefined],
    ["app", repositoryDirectory(workspaceRoot, config, "app"), definition.repositories.app],
    ["agents", repositoryDirectory(workspaceRoot, config, "agents"), definition.repositories.agents],
    ["inject", repositoryDirectory(workspaceRoot, config, "inject"), definition.repositories.inject],
    ["installer", repositoryDirectory(workspaceRoot, config, "installer"), definition.repositories.installer],
    ["logo", repositoryDirectory(workspaceRoot, config, "logo"), definition.repositories.logo],
    ["codestyle", repositoryDirectory(workspaceRoot, config, "codestyle"), undefined],
  ];
  const snapshots: RepositorySnapshot[] = [];
  for (const [name, directory, expected] of repositories) {
    if (!existsSync(directory)) failBuild(`workspace is missing repository: ${name}`);
    const snapshot = await snapshotRepository(name, directory);
    // No product exemption: the executed product tooling is a source input
    // like every other repository; exact-source requires it committed too.
    if (expected !== undefined) {
      if (sourceMode === "exact-source" && snapshot.head !== expected)
        failBuild(
          `${name} HEAD ${snapshot.head} does not match definition ${expected} (exact-source mode)`,
        );
      if (sourceMode === "local-working-tree" && snapshot.head !== expected)
        console.warn(
          `build-closure: ${name} HEAD ${snapshot.head} differs from definition ${expected}; local-working-tree identity recorded, result is not an exact-commit build`,
        );
    }
    if (sourceMode === "exact-source" && !snapshot.clean)
      failBuild(
        `${name} has a dirty working tree or dirty submodule content in exact-source mode (tracked modifications, untracked sources, or nested submodule drift)`,
      );
    snapshots.push(snapshot);
  }
  return snapshots;
}

function sha256Of(bytes: Buffer): string {
  return createHash("sha256").update(bytes).digest("hex");
}

function hashFile(path: string): { size: number; sha256: string } {
  const bytes = readFileSync(path);
  return { size: bytes.length, sha256: sha256Of(bytes) };
}

function validateAuthorityLayout(authority: string): void {
  if (!isAbsolute(authority)) failBuild("authorityDir must be an absolute path");
  const directory = lstatSync(authority);
  if (!directory.isDirectory()) failBuild("authority must be a regular directory");
  if (realpathSync(authority) !== authority) failBuild("authority must use its canonical path");
  if ((directory.mode & 0o777) !== 0o700) failBuild("authority directory must have mode 0700");
  for (const role of AUTHORITY_ROLES) {
    for (const suffix of ["key", "pub", "pem"]) {
      const path = join(authority, `${role}.${suffix}`);
      const stats = lstatSync(path);
      if (!stats.isFile() || stats.isSymbolicLink())
        failBuild(`missing local authority input: ${role}.${suffix}`);
    }
    if ((lstatSync(join(authority, `${role}.key`)).mode & 0o777) !== 0o600)
      failBuild(`private authority file must have mode 0600: ${role}.key`);
  }
  const jks = lstatSync(join(authority, "app.jks"));
  if (!jks.isFile() || (jks.mode & 0o777) !== 0o600)
    failBuild("missing or unprotected local test Android keystore: app.jks");
}

export type ToolchainIdentity = {
  buildTools: string;
  androidNdkHome: string;
  androidNdkRevision: string;
  node: string;
  rustc: string;
  cargo: string;
  jdkKeytool: string;
};

function readPropertiesRevision(path: string): string {
  const content = readFileSync(path, "utf8");
  for (const line of content.split("\n")) {
    const match = /^Pkg\.Revision\s*=\s*(.+)$/.exec(line.trim());
    if (match !== null) return match[1].trim();
  }
  failBuild(`cannot read Pkg.Revision from ${path}`);
}

// Capture the ACTUAL toolchain versions used by the build — a directory named
// "29.0.x" proves nothing. NDK/build-tools revisions come from their
// source.properties; host tools from --version output.
export async function captureToolVersion(executable: string, args: string[]): Promise<string> {
  const result = await runProcess(executable, args, { timeoutMs: METADATA_TIMEOUT_MS });
  const output = result.stdout.toString("utf8").trim() || result.stderr.toString("utf8").trim();
  const version = output.split("\n")[0]?.trim() ?? "";
  if (!version) failBuild(`tool version output is empty: ${executable}`);
  return version;
}
export async function captureToolchainIdentity(config: BuildClosureConfig): Promise<ToolchainIdentity> {
  const ndkProperties = join(config.androidNdkHome, "source.properties");
  if (!existsSync(ndkProperties)) failBuild(`NDK source.properties is missing: ${ndkProperties}`);
  const androidNdkRevision = readPropertiesRevision(ndkProperties);
  const buildToolsProperties = join(
    config.androidSdkRoot,
    "build-tools",
    PINNED_BUILD_TOOLS,
    "source.properties",
  );
  if (!existsSync(buildToolsProperties))
    failBuild(`build-tools source.properties is missing: ${buildToolsProperties}`);
  const buildToolsRevision = readPropertiesRevision(buildToolsProperties);
  if (buildToolsRevision !== PINNED_BUILD_TOOLS)
    failBuild(
      `installed build-tools revision ${buildToolsRevision} differs from the pinned ${PINNED_BUILD_TOOLS}`,
    );
  if (config.androidNdkRevision !== undefined && config.androidNdkRevision !== androidNdkRevision)
    failBuild(
      `installed NDK revision ${androidNdkRevision} differs from the pinned ${config.androidNdkRevision}`,
    );
  return {
    buildTools: PINNED_BUILD_TOOLS,
    androidNdkHome: config.androidNdkHome,
    androidNdkRevision,
    node: await captureToolVersion("node", ["--version"]),
    rustc: await captureToolVersion("rustc", ["--version"]),
    cargo: await captureToolVersion("cargo", ["--version"]),
    jdkKeytool: await captureToolVersion("/usr/bin/keytool", ["-J-version"]),
  };
}

export function resolveNdkLlvmBin(ndk: string): string {
  const prebuilt = join(ndk, "toolchains/llvm/prebuilt");
  const bins = existsSync(prebuilt) ? readdirSync(prebuilt).map(entry => join(prebuilt, entry, "bin"))
    .filter(bin => { const clang = join(bin, "clang++"); return existsSync(clang) && statSync(clang).isFile(); }) : [];
  if (bins.length !== 1) failBuild(`Exactly one LLVM prebuilt bin with clang++ is required in declared NDK: ${prebuilt}`);
  return bins[0];
}

function validateToolchain(config: BuildClosureConfig): void {
  const androidTools = join(config.androidSdkRoot, "build-tools", PINNED_BUILD_TOOLS);
  for (const tool of ["apksigner", "aapt2"]) {
    if (!existsSync(join(androidTools, tool)))
      failBuild(`Android build-tools ${PINNED_BUILD_TOOLS} are required (${tool})`);
  }
  if (!existsSync(join(config.androidSdkRoot, "platform-tools")))
    failBuild("androidSdkRoot is invalid");
  // Pinned NDK is an explicit input — no fallback to the newest installed NDK.
  if (!existsSync(join(config.androidNdkHome, "toolchains")))
    failBuild("androidNdkHome is invalid (pinned NDK required, no newest-NDK fallback)");
}

export const PRODUCTION_HOST_COMMANDS = ["cargo", "keytool", "make", "node"] as const;

async function requireCommands(commands: readonly string[]): Promise<void> {
  for (const command of commands) {
    try {
      await runProcess("/usr/bin/which", [command], { timeoutMs: METADATA_TIMEOUT_MS });
    } catch {
      failBuild(`missing host command: ${command}`);
    }
  }
}

export async function buildClosure(rawConfig: unknown): Promise<void> {
  const config = parseBuildClosureConfig(rawConfig);
  if (config.profile === "emulator")
    failBuild(
      "emulator profile cannot be built from current sources (skip markers retired); use the retained emulator closure until sources regain an emulator build — this tool does not fake it",
    );

  const workspaceRoot = realpathSync(config.workspaceRoot);
  const product = repositoryDirectory(workspaceRoot, config, "product");
  const app = repositoryDirectory(workspaceRoot, config, "app");
  const agents = repositoryDirectory(workspaceRoot, config, "agents");
  const inject = repositoryDirectory(workspaceRoot, config, "inject");
  const output = resolve(config.output);
  const authority = config.authorityDir;

  // The output lives inside <voboost>/build (gitignored artifact tree) — the
  // confinement check in the path policy already rejects any symlink that
  // would escape into source repositories, so sibling repositories, the
  // authority and toolchain roots are listed for overlap checks, while the
  // product repository itself is covered by the confinement rule.
  checkOutputPolicy(workspaceRoot, output, {
    sources: [app, agents, inject, repositoryDirectory(workspaceRoot, config, "installer")],
    authority: [authority],
    toolchains: [config.androidSdkRoot, config.androidNdkHome],
  });
  validateAuthorityLayout(authority);
  // CI uses the audited metadata-only link interface; originals are optional local verification.
  for (const path of Object.values(config.qnxLibraries ?? {})) {
    if (!lstatSync(path).isFile()) failBuild(`QNX library input must be a regular file: ${path}`);
  }
  validateToolchain(config);
  const llvmBin = resolveNdkLlvmBin(config.androidNdkHome);
  await requireCommands(PRODUCTION_HOST_COMMANDS);
  const toolchain = await captureToolchainIdentity(config);

  const reservation = reserveOutput(output, "build-closure");
  // Everything after acquiring the reservation lives inside try/finally so an
  // early refusal (staging collision, mkdir failure) releases the lease.
  try {
    await buildClosureReserved(
      config,
      workspaceRoot,
      output,
      { product, app, agents, inject, authority, toolchain, llvmBin },
    );
  } finally {
    reservation.release();
  }
}

async function buildClosureReserved(
  config: BuildClosureConfig,
  workspaceRoot: string,
  output: string,
  directories: {
    product: string;
    app: string;
    agents: string;
    inject: string;
    authority: string;
    toolchain: ToolchainIdentity;
    llvmBin: string;
  },
): Promise<void> {
  const { product, app, agents, inject, authority, toolchain, llvmBin } = directories;
  const staging = `${output}.staging`;
  if (existsSync(staging)) failBuild(`staging already exists: ${staging}`);
  mkdirSync(staging, { mode: 0o700, recursive: true });
  const work = join(staging, "work");
  const closure = join(work, "closure");
  const inputsDir = join(staging, "inputs");
  mkdirSync(work, { recursive: true });
  mkdirSync(closure, { recursive: true });

  try {
    // 1. Freeze the release identity: current input is read exactly once, the
    // resolved <release>.toml snapshot carries the run. The installer
    // compile-time version inputs are generated from the SAME frozen
    // definition as one consistent immutable set.
    const identity = readReleaseIdentity(join(product, "release/version.toml"), inputsDir);
    writeFileSync(join(inputsDir, "requested-build-config.json"), JSON.stringify(config, null, 2) + "\n");
    requireAndroidVersionCodeAgreement(
      readFileSync(join(app, "build.gradle.kts"), "utf8"),
      identity.definition.android.versionCode,
    );
    await releaseCli(product, "check-definition", [identity.resolvedPath]);
    writeInstallerVersionInputs(inputsDir, buildInstallerVersionInputs(identity));

    // 2. Source snapshots before the expensive builds.
    const before = await snapshotWorkspaceSources(workspaceRoot, identity, config.sourceMode, config);

    // Installer observation is an executable compile input, never an unsigned
    // closure resource. Preserve the exact producer bytes and provenance for
    // the later platform executable/signing jobs alongside frozen identity.
    const observationInputs = join(inputsDir, "observation");
    await runProcess("node", [join(repositoryDirectory(workspaceRoot, config, "installer"), "src/build/observation-helper.ts"), observationInputs], {
      timeoutMs: COMPONENT_BUILD_TIMEOUT_MS,
      env: { ANDROID_SDK_ROOT: config.androidSdkRoot, JAVA_HOME: process.env.JAVA_HOME ?? "" },
    });

    // 3. APK certificate digest from the actual keystore bytes.
    const certificate = await runProcess(
      "/usr/bin/keytool",
      [
        "-exportcert",
        "-keystore",
        join(authority, "app.jks"),
        "-storepass",
        "changeit",
        "-alias",
        "voboost",
      ],
      { secrets: ["changeit"], timeoutMs: METADATA_TIMEOUT_MS },
    );
    const certSha = sha256Of(certificate.stdout);

    // Native source selection is generated by the agent metadata owner.
    await runProcess(join(agents, "gradlew"), nativeRegistryArguments(config), {
      cwd: agents,
      timeoutMs: COMPONENT_BUILD_TIMEOUT_MS,
      env: { ANDROID_SDK_ROOT: config.androidSdkRoot, NDK_HOME: config.androidNdkHome,
        ANDROID_NDK_HOME: config.androidNdkHome, JAVA_HOME: process.env.JAVA_HOME ?? "" },
    });

    // 4. Native build with explicit role/public-root/certificate inputs.
    await runProcess("make", ["-C", inject, "build-android"], {
      timeoutMs: COMPONENT_BUILD_TIMEOUT_MS,
      env: {
        AGENT_CATALOG_PUBLIC_KEY_PEM: join(authority, "agent-catalog.pem"),
        RELEASE_CATALOG_PUBLIC_KEY_PEM: join(authority, "product.pem"),
        NATIVE_EXECUTABLE_PUBLIC_KEY_PEM: join(authority, "native-executable.pem"),
        RESTORE_PUBLIC_KEY_PEM: join(authority, "restore.pem"),
        ANDROID_CERT_SHA256: certSha,
        ANDROID_NDK_HOME: config.androidNdkHome,
        NDK_HOME: config.androidNdkHome,
        NATIVE_REGISTRY: join(agents, "build", "native", "registry.rs"),
        PYTHONDONTWRITEBYTECODE: "1",
      },
    });

    // Native Cluster bytes are fresh inputs of the signed mixed-platform catalog.
    const qnxBuildEnv = { ANDROID_SDK_ROOT: config.androidSdkRoot, ANDROID_HOME: config.androidSdkRoot };
    const qnxNative = join(inject, "build/cluster/product");
    const libraries = config.qnxLibraries;
    const qnxOriginalArguments = libraries ? [
      "--qnx-libc", libraries.libc, "--qnx-socket", libraries.socket,
      "--qnx-ssl", libraries.ssl, "--qnx-crypto", libraries.crypto,
    ] : [];
    await runProcess(join(agents, "gradlew"), qnxJavaBuildArguments("product", ["--product",
      "--inject", inject, "--ndk", config.androidNdkHome, ...qnxOriginalArguments, "--output", qnxNative], llvmBin), {
      cwd: agents, timeoutMs: COMPONENT_BUILD_TIMEOUT_MS,
      env: {
        ...qnxBuildEnv,
        VOBOOST_AGENT_CATALOG_PUBLIC_KEY_PEM: join(authority, "agent-catalog.pem"),
        VOBOOST_RELEASE_CATALOG_PUBLIC_KEY_PEM: join(authority, "product.pem"),
        VOBOOST_NATIVE_EXECUTABLE_PUBLIC_KEY_PEM: join(authority, "native-executable.pem"),
        VOBOOST_RESTORE_PUBLIC_KEY_PEM: join(authority, "restore.pem"),
      },
    });
    const qnxAgents = join(agents, "build/cluster/product");
    await runProcess(join(agents, "gradlew"), qnxJavaBuildArguments("runtime",
      qnxRuntimeInputs(agents, inject, config.androidNdkHome, qnxAgents), llvmBin), {
      cwd: agents, timeoutMs: COMPONENT_BUILD_TIMEOUT_MS, env: qnxBuildEnv,
    });
    if (config.agentSet === "development") {
      // Build current PW module before the development catalog stages its light target.
      await runProcess(join(agents, "gradlew"), qnxJavaBuildArguments("pw-module", qnxPwModuleInputs(agents, inject, config.androidNdkHome), llvmBin), {
        cwd: agents, timeoutMs: COMPONENT_BUILD_TIMEOUT_MS, env: qnxBuildEnv,
      });
    }
    // Compile the inert locale module from current sources; runtime admission remains capability-gated.
    await runProcess(join(agents, "gradlew"), qnxJavaBuildArguments("module", [
      "--agents", agents, "--inject", inject, "--sdk", join(qnxNative, "sdk-v3"),
      "--ndk", config.androidNdkHome, "--output", qnxAgents], llvmBin), {
      cwd: agents, timeoutMs: COMPONENT_BUILD_TIMEOUT_MS, env: qnxBuildEnv,
    });
    if (config.agentSet === "development") {
      await runProcess(join(agents, "gradlew"), qnxLocaleBuildArguments(config.qnxLocaleInputsManifest!, llvmBin), {
        cwd: agents, timeoutMs: COMPONENT_BUILD_TIMEOUT_MS, env: qnxBuildEnv,
      });
    }
    cpSync(join(qnxAgents, "i18n-native-build.json"), join(inputsDir, "qnx-i18n-build.json"));
    cpSync(join(qnxNative, "build-evidence.json"), join(inputsDir, "qnx-native-build.json"));
    cpSync(join(qnxAgents, "bundle.json"), join(inputsDir, "qnx-agent-build.json"));

    // 5. Export the exact unsigned native inventory (component-owned TS
    // exporter; the shell exporter stays available for parity checks).
    const nativeUnsigned = join(work, "native-profile");
    await runProcess("node", [join(inject, "tools/prepare-beta1-unsigned.ts"), nativeUnsigned], {
      env: { NDK_HOME: config.androidNdkHome, ANDROID_NDK_HOME: config.androidNdkHome },
      timeoutMs: METADATA_TIMEOUT_MS,
    });

    // 6. App APK via the app's own entrypoint, then pinned apksigner.
    const unsignedApk = join(work, "voboost-app-unsigned.apk");
    await runProcess(join(app, "scripts/prepare-beta1-unsigned.sh"), [unsignedApk], {
      timeoutMs: COMPONENT_BUILD_TIMEOUT_MS,
      env: {
        VOBOOST_EMBEDDED_PRODUCT_PUBLIC_KEY_PEM: join(authority, "product.pem"),
        ANDROID_HOME: config.androidSdkRoot,
      },
    });
    await runProcess(
      join(config.androidSdkRoot, "build-tools", PINNED_BUILD_TOOLS, "apksigner"),
      [
        "sign",
        "--ks",
        join(authority, "app.jks"),
        "--ks-pass",
        "pass:changeit",
        "--key-pass",
        "pass:changeit",
        "--v4-signing-enabled",
        "false",
        "--out",
        join(closure, "voboost-app.apk"),
        unsignedApk,
      ],
      { secrets: ["changeit"], timeoutMs: METADATA_TIMEOUT_MS },
    );

    // 7. Agents via their own gradle entrypoint.
    await runProcess(join(agents, "gradlew"), agentBuildArguments(config, qnxAgents, llvmBin), {
      cwd: agents,
      timeoutMs: COMPONENT_BUILD_TIMEOUT_MS,
      env: { ANDROID_SDK_ROOT: config.androidSdkRoot, ANDROID_HOME: config.androidSdkRoot },
    });
    cpSync(join(agents, "build/agents-bundle/agents.json"), join(closure, "agents.json"));
    cpSync(join(agents, "build/agents-bundle/agent-common.dex"), join(closure, "agent-common.dex"));
    for (const entry of readdirSync(join(agents, "build/agents-bundle"))) {
      if (/^agent-.*\.dex$/.test(entry) && entry !== "agent-common.dex")
        cpSync(join(agents, "build/agents-bundle", entry), join(closure, entry));
    }
    cpSync(join(agents, "build/agents-bundle/libvoboost-runtime-cluster.so"), join(closure, "libvoboost-runtime-cluster.so"));
    for (const entry of readdirSync(join(agents, "build/agents-bundle"))) {
      if (/^libagent-[a-z0-9]+(?:-[a-z0-9]+)*\.so$/.test(entry))
        cpSync(join(agents, "build/agents-bundle", entry), join(closure, entry));
    }
    await stageResourceArtifacts(join(agents, "build/agents-bundle"), closure, async (input, output) => {
      await runProcess(join(config.androidSdkRoot, "build-tools", PINNED_BUILD_TOOLS, "apksigner"), [
        "sign", "--ks", join(authority, "app.jks"), "--ks-pass", "pass:changeit",
        "--key-pass", "pass:changeit", "--v4-signing-enabled", "false", "--out", output, input,
      ], { secrets: ["changeit"], timeoutMs: METADATA_TIMEOUT_MS });
    });
    if (config.agentSet === "development") {
      stageQnxResourceArtifacts(join(agents, "build/i18n-qnx-resources"), closure, readFileSync(join(closure, "agents.json")));
    }
    await releaseCli(product, "sign-file", [
      join(authority, "agent-catalog.key"),
      join(closure, "agents.json"),
      join(closure, "agents.json.sig"),
    ]);

    // 8. Stage and role-sign the native artifacts.
    for (const file of NON_ELF_FILES) cpSync(join(nativeUnsigned, file), join(closure, file));
    for (const [role, file] of NATIVE_ROLES) {
      await releaseCli(product, "sign-native-executable", [
        role,
        join(authority, "native-executable.key"),
        join(nativeUnsigned, file),
        join(closure, file),
      ]);
    }
    for (const file of ["voboost-cluster-startup.sh", "libvoboost-cluster-inject.so"])
      cpSync(join(qnxNative, file), join(closure, file));
    await releaseCli(product, "sign-native-executable", ["daemon", join(authority, "native-executable.key"),
      join(qnxNative, "voboost-cluster-daemon"), join(closure, "voboost-cluster-daemon")]);

    // 9. Independent verification of the actual APK bytes.
    const apkEvidencePath = join(work, "apk-evidence.json");
    await releaseCli(product, "inspect-apk", [
      identity.resolvedPath,
      closure,
      config.androidSdkRoot,
      apkEvidencePath,
    ]);
    const apkEvidence = JSON.parse(readFileSync(apkEvidencePath, "utf8")) as {
      certificateSha256: unknown;
    };
    if (apkEvidence.certificateSha256 !== certSha)
      failBuild("APK certificate differs from the authority keystore certificate");
    await releaseCli(product, "verify-apk-product-root", [
      join(closure, "voboost-app.apk"),
      join(authority, "product.pub"),
    ]);

    // 10. Compose, sign, verify — through the Rust authority.
    await releaseCli(product, "produce-restore", [
      identity.resolvedPath,
      closure,
      config.androidSdkRoot,
      join(authority, "restore.key"),
    ]);
    await releaseCli(product, "compose", [
      identity.resolvedPath,
      closure,
      config.androidSdkRoot,
      apkEvidencePath,
      join(authority, "restore.pub"),
      join(work, "voboost.json"),
    ]);
    await releaseCli(product, "sign-file", [
      join(authority, "product.key"),
      join(work, "voboost.json"),
      join(work, "voboost.json.sig"),
    ]);
    cpSync(join(work, "voboost.json"), join(closure, "voboost.json"));
    cpSync(join(work, "voboost.json.sig"), join(closure, "voboost.json.sig"));

    await releaseCli(product, "verify-file", [
      join(authority, "product.pub"),
      join(closure, "voboost.json"),
      join(closure, "voboost.json.sig"),
    ]);
    await releaseCli(product, "verify-file", [
      join(authority, "agent-catalog.pub"),
      join(closure, "agents.json"),
      join(closure, "agents.json.sig"),
    ]);
    await releaseCli(product, "verify-restore", [
      identity.resolvedPath,
      join(closure, "voboost.json"),
      closure,
      join(authority, "restore.pub"),
    ]);
    await releaseCli(product, "verify-native-executable", ["daemon", join(authority, "native-executable.pub"), join(closure, "voboost-cluster-daemon")]);
    for (const role of AUTHORITY_ROLES)
      await releaseCli(product, "verify-embedded-root", [join(authority, `${role}.pub`), join(closure, "voboost-cluster-daemon")]);
    for (const [role, file] of NATIVE_ROLES) {
      await releaseCli(product, "verify-native-executable", [
        role,
        join(authority, "native-executable.pub"),
        join(closure, file),
      ]);
      await releaseCli(product, "verify-embedded-root", [
        join(authority, "native-executable.pub"),
        join(closure, file),
      ]);
    }
    await releaseCli(product, "verify-embedded-root", [
      join(authority, "agent-catalog.pub"),
      join(closure, "voboost-daemon"),
    ]);
    for (const file of ["voboost-inject", "voboost-install"]) {
      await releaseCli(product, "verify-embedded-root", [
        join(authority, "agent-catalog.pub"),
        join(closure, file),
      ]);
      await releaseCli(product, "verify-embedded-root", [
        join(authority, "product.pub"),
        join(closure, file),
      ]);
    }
    await releaseCli(product, "verify-embedded-root", [
      join(authority, "product.pub"),
      join(closure, "voboost-restore"),
    ]);
    for (const file of ["voboost-inject", "voboost-install", "voboost-restore", "voboost-uninstall"]) {
      await releaseCli(product, "verify-embedded-root", [
        join(authority, "restore.pub"),
        join(closure, file),
      ]);
    }
    await releaseCli(product, "verify-product-closure", [closure, join(authority, "product.pub")]);

    // 11. Profile verified against the actual bytes.
    verifyProfileMarkers(config.profile, (file) => readFileSync(join(closure, file)));
    if (
      !readFileSync(join(closure, "voboost-uninstall")).includes(
        Buffer.from(`VOBOOST-ANDROID-CERT-SHA256:${certSha}`, "latin1"),
      )
    )
      failBuild("signed Uninstall does not embed the APK certificate");
    if (readFileSync(join(closure, "voboost.rc")).equals(readFileSync(join(inject, "config/voboost.rc"))) === false)
      failBuild("closure voboost.rc differs from voboost-inject/config/voboost.rc");

    // 12. Re-check sources: post-build outputs and local configs are excluded
    // from the source comparison by comparing the recorded identity fields.
    const snapshot = () => snapshotWorkspaceSources(workspaceRoot, identity, config.sourceMode, config);
    const after = await snapshot();
    requireUnchangedSourceSnapshots(before, after);

    // 13. Evidence inventory and receipt.
    const noPackages = join(work, "no-packages");
    mkdirSync(noPackages);
    const closureInventory = join(work, "closure-inventory.json");
    await runProcess("node", [
      join(product, "tools/beta1-evidence.ts"),
      "inventory",
      closure,
      noPackages,
      closureInventory,
    ], { timeoutMs: METADATA_TIMEOUT_MS });

    const artifacts: Record<string, { size: number; sha256: string }> = {};
    for (const entry of readdirSync(closure)) artifacts[entry] = hashFile(join(closure, entry));
    const receipt = {
      schemaVersion: 1,
      kind: "source-build",
      profile: config.profile,
      sourceMode: config.sourceMode,
      agentSet: config.agentSet ?? "release",
      requestedConfig: config,
      release: identity.definition.release,
      channel: identity.definition.channel,
      definitionSha256: identity.sha256,
      androidCertificateSha256: certSha,
      sources: after,
      artifacts,
      closureInventorySha256: hashFile(closureInventory).sha256,
      toolchain,
      agentBuild: observeAgentBuild(config, qnxAgents, llvmBin, readFileSync(join(closure, "agents.json"))),
      installerObservation: JSON.parse(readFileSync(join(inputsDir, "observation/provenance.json"), "utf8")),
      publication: "forbidden",
    };
    renameSync(closure, join(staging, "closure"));
    renameSync(closureInventory, join(staging, "closure-inventory.json"));
    renameSync(apkEvidencePath, join(staging, "apk-evidence.json"));
    rmSync(work, { recursive: true });
    requireUnchangedSourceSnapshots(before, await snapshot());
    publishStagedBundle(staging, output, receipt);
    console.log(`closure built at ${output}`);
  } catch (error) {
    // Crash recovery: keep staging (work/evidence) for analysis, never
    // publish; the outer wrapper releases the reservation.
    console.error((error as Error).message);
    throw error;
  }
}

// Dry-run: validate the config and the current release input and print the
// exact ordered plan both local runs and CI consume — without executing any
// producer. The plan is the same array buildClosure follows.
export async function planFromConfig(rawConfig: unknown): Promise<string> {
  const config = parseBuildClosureConfig(rawConfig);
  const workspaceRoot = realpathSync(config.workspaceRoot);
  const product = repositoryDirectory(workspaceRoot, config, "product");
  const inputsDir = mkdtempSync(join(productBuildRootScratch(), "plan-"));
  try {
    const identity = readReleaseIdentity(join(product, "release/version.toml"), inputsDir);
    return JSON.stringify(
    {
      plan: planBuild(config),
      release: identity.definition.release,
      channel: identity.definition.channel,
      definitionSha256: identity.sha256,
      profile: config.profile,
      sourceMode: config.sourceMode,
      agentSet: config.agentSet ?? "release",
      requestedConfig: config,
    },
    null,
    2,
    );
  } finally {
    rmSync(inputsDir, { recursive: true });
  }
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const planOnly = process.argv.includes("--plan");
  const configPath = process.argv.slice(2).find((argument) => !argument.startsWith("--"));
  if (configPath === undefined) {
    console.error("usage: build-closure.ts [--plan] <config.json>");
    process.exit(2);
  }
  const rawConfig = JSON.parse(readFileSync(configPath, "utf8"));
  if (planOnly) {
    planFromConfig(rawConfig)
      .then((plan) => console.log(plan))
      .catch((error: Error) => {
        console.error(error.message);
        process.exit(1);
      });
  } else {
    buildClosure(rawConfig).catch((error: Error) => {
      console.error(error.message);
      process.exit(1);
    });
  }
}
