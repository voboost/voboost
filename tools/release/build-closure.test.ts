import { deepStrictEqual, ok, strictEqual, throws, rejects } from "node:assert";
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import test from "node:test";
import {
  buildClosure,
  agentBuildArguments,
  nativeRegistryArguments,
  resolveNdkLlvmBin,
  qnxJavaBuildArguments,
  qnxLocaleBuildArguments,
  qnxPwModuleInputs,
  qnxRuntimeInputs,
  PRODUCTION_HOST_COMMANDS,
  observeAgentBuild,
  parseBuildClosureConfig,
  planBuild,
  planFromConfig,
  snapshotRepository,
  captureToolVersion,
  requireUnchangedSourceSnapshots,
  type RepositorySnapshot,
} from "./build-closure.ts";

const WORKSPACE = join(import.meta.dirname, "..", "..", "..");

test("native registry uses the selected agent set and precedes the native build", () => {
  const config = parseBuildClosureConfig(validConfig());
  const nativeContract = "-PnativeContract=/Users/example/workspace/voboost-inject/contract/native_agent.rs";
  deepStrictEqual(nativeRegistryArguments(config), ["--no-daemon", "generateNativeRegistry", "-PvoboostAgentSet=release", nativeContract]);
  deepStrictEqual(nativeRegistryArguments({ ...config, agentSet: "development" }), ["--no-daemon", "generateNativeRegistry", "-PvoboostAgentSet=development", nativeContract]);
  const relocated = { ...config, repositories: { inject: "/different source/inject" } };
  ok(nativeRegistryArguments(relocated).includes("-PnativeContract=/different source/inject/contract/native_agent.rs"));
  ok(agentBuildArguments(relocated, "/cluster", "/llvm/bin").includes("-PnativeContract=/different source/inject/contract/native_agent.rs"));
  const plan = planBuild(config);
  strictEqual(plan.indexOf("generate-native-registry") + 1, plan.indexOf("build-natives"));
});

test("toolchain metadata accepts JDK-style stderr and rejects an empty version", async () => {
  strictEqual(await captureToolVersion(process.execPath, ["-e", "process.stderr.write('jdk 17\\nmore detail\\n')"]), "jdk 17");
  await rejects(captureToolVersion(process.execPath, ["-e", ""]), /version output is empty/);
});

function validConfig(): Record<string, unknown> {
  return {
    workspaceRoot: "/Users/example/workspace",
    output: "/Users/example/workspace/voboost/build/closures/closure-vX",
    authorityDir: "/Users/example/signing-authority",
    androidSdkRoot: "/opt/android-sdk",
    androidNdkHome: "/opt/android-sdk/ndk/27.0.0",
    profile: "machine",
    sourceMode: "local-working-tree",
    qnxLocaleInputsManifest: "/Users/example/workspace/locale-inputs.json",
  };
}

test("config validation accepts the full valid shape", () => {
  const config = parseBuildClosureConfig(validConfig());
  strictEqual(config.profile, "machine");
  strictEqual(config.sourceMode, "local-working-tree");
});

test("config validation rejects unknown fields, bad enums and relative paths", () => {
  throws(() => parseBuildClosureConfig({ ...validConfig(), extra: 1 }), /unknown field/);
  throws(() => parseBuildClosureConfig({ ...validConfig(), profile: "avd" }), /machine or emulator/);
  throws(
    () => parseBuildClosureConfig({ ...validConfig(), sourceMode: "dirty" }),
    /local-working-tree or exact-source/,
  );
  throws(() => parseBuildClosureConfig({ ...validConfig(), output: "relative" }), /absolute/);
  for (const field of ["workspaceRoot", "authorityDir", "androidSdkRoot", "androidNdkHome"]) {
    throws(() => parseBuildClosureConfig({ ...validConfig(), [field]: "" }), /absolute|required/);
  }
});

test("plan is a fixed ordered sequence shared by CLI and CI dry-runs", () => {
  deepStrictEqual(planBuild(parseBuildClosureConfig(validConfig())), [
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
  ]);
});

test("QNX library configuration is exact and path-bound", () => {
  const libraries = { libc: "/qnx/libc.so.4", socket: "/qnx/libsocket.so.3", ssl: "/qnx/libssl.so", crypto: "/qnx/libcrypto.so" };
  const config = parseBuildClosureConfig({ ...validConfig(), qnxLibraries: libraries });
  deepStrictEqual(config.qnxLibraries, libraries);
  for (const input of [null, [], {}, { ...libraries, extra: "/qnx/extra" }, { ...libraries, libc: "relative" }, { ...libraries, socket: 3 }])
    throws(() => parseBuildClosureConfig({ ...validConfig(), qnxLibraries: input }), /qnxLibraries/);
  const plan = planBuild(config);
  ok(plan.indexOf("build-qnx-natives") < plan.indexOf("export-native-inventory"));
  ok(plan.indexOf("build-qnx-agents") < plan.indexOf("build-agents"));
});

test("emulator profile is rejected up front, not faked from a retained closure", async () => {
  await (async () => {
    try {
      await buildClosure({ ...validConfig(), profile: "emulator" });
      throw new Error("unreachable");
    } catch (error) {
      ok((error as Error).message.includes("emulator profile cannot be built"));
    }
  })();
});

test("repository snapshot records HEAD, tree and status identity", async () => {
  // A real isolated Git repository prevents concurrent host fixtures or edits
  // in the active checkout from changing the measured source identity.
  const work = join(WORKSPACE, ".work162", "build-closure-snapshot-tests");
  mkdirSync(work, { recursive: true });
  const scratch = mkdtempSync(join(work, "identity-"));
  try {
    const git = (...args: string[]) => execFileSync("git", ["-C", scratch, ...args]);
    git("init", "-q");
    git("config", "user.email", "fixture@example.invalid");
    git("config", "user.name", "Snapshot fixture");
    writeFileSync(join(scratch, "input.txt"), "committed source\n");
    git("add", "input.txt"); git("commit", "-qm", "fixture");
    writeFileSync(join(scratch, "input.txt"), "changed source\n");
    const first: RepositorySnapshot = await snapshotRepository("product", scratch);
    const second = await snapshotRepository("product", scratch);
    strictEqual(first.head, second.head);
    strictEqual(first.tree, second.tree);
    strictEqual(first.statusDigest, second.statusDigest);
    ok(/^[0-9a-f]{40}$/.test(first.head));
    ok(/^[0-9a-f]{40}$/.test(first.tree));
    ok(/^[0-9a-f]{64}$/.test(first.statusDigest));
    ok(first.tree !== git("rev-parse", "HEAD^{tree}").toString().trim());
  } finally { rmSync(scratch, { recursive: true, force: true }); }
});

test("submodule content changes are visible even with an unchanged gitlink", async () => {
  const work = join(WORKSPACE, ".work162", "build-closure-snapshot-tests");
  mkdirSync(work, { recursive: true });
  const scratch = mkdtempSync(join(work, "submodule-"));
  try {
    // A local submodule repository shared between parent and clone.
    const dep = join(scratch, "dep.git");
    execFileSync("git", ["-C", scratch, "init", "-q", "--bare", "dep.git"]);
    const seed = join(scratch, "seed");
    execFileSync("git", ["-C", scratch, "init", "-q", "seed"]);
    execFileSync("git", ["-C", seed, "config", "user.email", "t@example"]);
    execFileSync("git", ["-C", seed, "config", "user.name", "t"]);
    const nestedSeed = join(scratch, "nested-seed");
    execFileSync("git", ["-C", scratch, "init", "-q", "nested-seed"]);
    execFileSync("git", ["-C", nestedSeed, "config", "user.email", "t@example"]);
    execFileSync("git", ["-C", nestedSeed, "config", "user.name", "t"]);
    writeFileSync(join(nestedSeed, "nested.c"), "nested original\n");
    execFileSync("git", ["-C", nestedSeed, "add", "-A"]);
    execFileSync("git", ["-C", nestedSeed, "commit", "-qm", "nested"]);
    execFileSync("git", ["-C", seed, "-c", "protocol.file.allow=always", "submodule", "add", "-q", nestedSeed, "nested"]);
    writeFileSync(join(seed, "input.c"), "int dep(void);\n");
    execFileSync("git", ["-C", seed, "add", "-A"]);
    execFileSync("git", ["-C", seed, "commit", "-qm", "dep"]);
    execFileSync("git", ["-C", seed, "push", "-q", dep, "HEAD:refs/heads/main"]);

    const parent = join(scratch, "parent");
    execFileSync("git", ["-C", scratch, "init", "-q", "parent"]);
    execFileSync("git", ["-C", parent, "config", "user.email", "t@example"]);
    execFileSync("git", ["-C", parent, "config", "user.name", "t"]);
    writeFileSync(join(parent, ".gitmodules"), `[submodule "dep"]\n\tpath = dep\n\turl = ${dep}\n`);
    execFileSync("git", ["-C", parent, "-c", "protocol.file.allow=always", "submodule", "add", "-q", "--name", "dep", "--branch", "main", dep, "dep"]);
    execFileSync("git", ["-C", parent, "commit", "-qm", "parent"]);

    const unpopulated = await snapshotRepository("parent", parent);
    strictEqual(unpopulated.submodules[0].nested[0].initialized, false);
    writeFileSync(join(parent, "dep", "nested", "unexpected"), "unknown\n");
    await rejects(snapshotRepository("parent", parent), /nonempty workdir/u);
    rmSync(join(parent, "dep", "nested", "unexpected"));
    execFileSync("git", ["-C", parent, "-c", "protocol.file.allow=always", "submodule", "update", "--init", "--recursive", "-q"]);
    const before = await snapshotRepository("parent", parent);
    strictEqual(before.submodules[0].nested[0].initialized, true);
    throws(() => requireUnchangedSourceSnapshots([unpopulated], [before]), /source drift/u);
    strictEqual(before.clean, true, "fresh clone with pristine submodule is clean");

    // Change content INSIDE the submodule: the gitlink stays identical, but
    // the actual build input changed — the snapshot must see it.
    writeFileSync(join(parent, "dep", "input.c"), "int dep(void){return 42;}\n");
    const after = await snapshotRepository("parent", parent);
    strictEqual(after.head, before.head, "gitlink/HEAD unchanged");
    ok(!after.clean, "dirty submodule content breaks pristine/exact-source");
    ok(
      after.submodules[0].contentDigest !== before.submodules[0].contentDigest,
      "submodule content digest changes",
    );
    writeFileSync(join(parent, "dep", "input.c"), "int dep(void){return 43;}\n");
    const dirtyAgain = await snapshotRepository("parent", parent);
    strictEqual(dirtyAgain.head, after.head);
    strictEqual(dirtyAgain.tree, after.tree);
    strictEqual(dirtyAgain.statusDigest, after.statusDigest);
    throws(() => requireUnchangedSourceSnapshots([after], [dirtyAgain]), /source drift/u);
    requireUnchangedSourceSnapshots([dirtyAgain], [dirtyAgain]);
    writeFileSync(join(parent, "dep", "new-input.c"), "untracked one\n");
    const untracked = await snapshotRepository("parent", parent);
    writeFileSync(join(parent, "dep", "new-input.c"), "untracked two\n");
    const untrackedChanged = await snapshotRepository("parent", parent);
    strictEqual(untracked.statusDigest, untrackedChanged.statusDigest);
    throws(() => requireUnchangedSourceSnapshots([untracked], [untrackedChanged]), /source drift/u);
    writeFileSync(join(parent, "dep", "nested", "new.c"), "one\n");
    const nestedOne = await snapshotRepository("parent", parent);
    writeFileSync(join(parent, "dep", "nested", "new.c"), "two\n");
    const nestedTwo = await snapshotRepository("parent", parent);
    strictEqual(nestedOne.statusDigest, nestedTwo.statusDigest);
    throws(() => requireUnchangedSourceSnapshots([nestedOne], [nestedTwo]), /source drift/u);
    const nestedPath = join(parent, "dep", "nested");
    execFileSync("git", ["-C", nestedPath, "config", "user.email", "t@example"]);
    execFileSync("git", ["-C", nestedPath, "config", "user.name", "t"]);
    execFileSync("git", ["-C", nestedPath, "add", "new.c"]);
    execFileSync("git", ["-C", nestedPath, "commit", "-qm", "change nested head"]);
    const nestedHeadChanged = await snapshotRepository("parent", parent);
    throws(() => requireUnchangedSourceSnapshots([nestedTwo], [nestedHeadChanged]), /source drift/u);

  } finally {
    rmSync(scratch, { recursive: true, force: true });
  }
});

test("repository snapshot detects working-tree drift", async () => {
  const work = join(WORKSPACE, ".work162", "build-closure-snapshot-tests");
  mkdirSync(work, { recursive: true });
  const scratch = mkdtempSync(join(work, "drift-"));
  try {
    execFileSync("git", ["-C", scratch, "init", "-q"]);
    execFileSync("git", ["-C", scratch, "config", "user.email", "t@example"]);
    execFileSync("git", ["-C", scratch, "config", "user.name", "t"]);
    execFileSync("git", ["-C", scratch, "commit", "--allow-empty", "-m", "init", "-q"]);
    const before = await snapshotRepository("scratch", scratch);
    writeFileSync(join(scratch, "untracked.txt"), "x\n");
    const after = await snapshotRepository("scratch", scratch);
    ok(before.statusDigest !== after.statusDigest, "untracked input changes the tree identity");
    ok(before.tree !== after.tree, "scratch index tree captures untracked sources");
  } finally {
    rmSync(scratch, { recursive: true, force: true });
  }
});

test("the committed release snapshot equals the current authoring input", () => {
  // CI admission compares release/version.toml with the committed snapshot;
  // this keeps the local contract in the product suite too. When the current
  // release changes, the new <release>.toml snapshot must be committed and
  // the CI comparison updated to it.
  const current = readFileSync(join(WORKSPACE, "voboost", "release/version.toml"));
  const snapshot = readFileSync(join(WORKSPACE, "voboost", "release/1.0.0-beta1.toml"));
  ok(current.equals(snapshot), "release/version.toml must equal the committed 1.0.0-beta1.toml snapshot");
});

test("dry-run plan consumer agrees with the executed plan and freezes identity", async () => {
  // planFromConfig touches the real product tree (version.toml + inputs dir),
  // so it runs against the actual workspace, not the fictional config paths.
  const config = {
    ...validConfig(),
    workspaceRoot: WORKSPACE,
    output: join(WORKSPACE, "voboost", "build", "dry-run-test", "closure"),
    authorityDir: join(WORKSPACE, "signing-authority"),
  };
  const plan = JSON.parse(await planFromConfig(config));
  deepStrictEqual(plan.plan, planBuild(parseBuildClosureConfig(config)));
  strictEqual(plan.release, "1.0.0-beta1");
  strictEqual(plan.definitionSha256.length, 64);
  deepStrictEqual(JSON.parse(await planFromConfig(config)), plan, "dry-run invocations do not share frozen inputs");
});

test("agent set defaults to release and development stays local only", () => {
  strictEqual(parseBuildClosureConfig(validConfig()).agentSet, "release");
  strictEqual(parseBuildClosureConfig({ ...validConfig(), agentSet: "development" }).agentSet, "development");
  for (const agentSet of [null, true, "candidate", 1])
    throws(() => parseBuildClosureConfig({ ...validConfig(), agentSet }), /agentSet/);
  throws(() => parseBuildClosureConfig({ ...validConfig(), agentSet: "development", sourceMode: "exact-source" }), /development.*local-working-tree/);
  strictEqual(parseBuildClosureConfig({ ...validConfig(), agentSet: "release", sourceMode: "exact-source" }).agentSet, "release");
});

test("agent producer explicitly selects the requested set despite ambient Gradle defaults", () => {
  deepStrictEqual(agentBuildArguments(parseBuildClosureConfig(validConfig()), "/cluster", "/configured ndk/llvm/bin"),
    ["buildRelease", "--no-daemon", "-PvoboostAgentSet=release", "-PclusterNativeDirectory=/cluster", "-PvoboostI18nLlvmBin=/configured ndk/llvm/bin", "-PnativeContract=/Users/example/workspace/voboost-inject/contract/native_agent.rs"]);
  deepStrictEqual(agentBuildArguments(parseBuildClosureConfig({ ...validConfig(), agentSet: "development" }), "/cluster", "/llvm/bin"),
    ["bundleAgents", "--no-daemon", "-PvoboostAgentSet=development", "-PclusterNativeDirectory=/cluster", "-PvoboostI18nLlvmBin=/llvm/bin", "-PnativeContract=/Users/example/workspace/voboost-inject/contract/native_agent.rs", "-Pi18nNativeModuleDirectory=/cluster", "-PpwNativeModuleDirectory=/pw-inert"]);
  throws(() => agentBuildArguments({ ...parseBuildClosureConfig(validConfig()), agentSet: "development", sourceMode: "exact-source" }, "/cluster", "/llvm/bin"), /development.*local-working-tree/);
});

test("declared NDK must contain exactly one LLVM prebuilt", () => {
  const work = join(WORKSPACE, ".work162", "build-closure-ndk-tests");
  mkdirSync(work, { recursive: true });
  const scratch = mkdtempSync(join(work, "ndk-"));
  try {
    throws(() => resolveNdkLlvmBin(scratch), /Exactly one LLVM/);
    const first = join(scratch, "toolchains/llvm/prebuilt/host one/bin");
    mkdirSync(first, { recursive: true });
    writeFileSync(join(first, "clang++"), "fixture");
    strictEqual(resolveNdkLlvmBin(scratch), first);
    const second = join(scratch, "toolchains/llvm/prebuilt/host-two/bin");
    mkdirSync(second, { recursive: true });
    writeFileSync(join(second, "clang++"), "fixture");
    throws(() => resolveNdkLlvmBin(scratch), /Exactly one LLVM/);
  } finally { rmSync(scratch, { recursive: true, force: true }); }
});

test("dry-run identifies development explicitly and retains requested config", async () => {
  const config = { ...validConfig(), agentSet: "development", workspaceRoot: WORKSPACE };
  const plan = JSON.parse(await planFromConfig(config));
  strictEqual(plan.agentSet, "development");
  strictEqual(plan.requestedConfig.agentSet, "development");
  strictEqual(plan.sourceMode, "local-working-tree");
});


test("agent evidence binds actual catalog bytes and preserves unstable candidate statuses", () => {
  const config = parseBuildClosureConfig({ ...validConfig(), agentSet: "development" });
  const catalog = Buffer.from(JSON.stringify({ agents: [
    { id: "phone", status: "unstable" }, { id: "outing", status: "unstable" }, { id: "executor" },
  ] }));
  const evidence = observeAgentBuild(config, "/cluster", "/llvm/bin", catalog);
  deepStrictEqual(evidence.observedAgents, [
    { id: "phone", status: "unstable" }, { id: "outing", status: "unstable" }, { id: "executor" },
  ]);
  strictEqual(evidence.catalogSha256.length, 64);
  ok(evidence.catalogSha256 !== observeAgentBuild(config, "/cluster", "/llvm/bin", Buffer.concat([catalog, Buffer.from("\n")])).catalogSha256);
  deepStrictEqual(evidence.arguments, agentBuildArguments(config, "/cluster", "/llvm/bin"));
});

 test("QNX Java invocations preserve each path without shell splitting", () => {
  const inputs = ["--output", "/project with spaces/build", "--ndk", "/toolchain/[29]", "--inject", "/source/quoted\"path"];
  for (const [kind, task, property] of [["product", "runQnxProductBuild", "qnxProductArguments"], ["runtime", "runQnxRuntimeBuild", "qnxRuntimeArguments"], ["module", "runQnxNativeModuleBuild", "qnxModuleArguments"], ["pw-module", "runQnxPwModuleBuild", "qnxPwModuleArguments"]] as const) {
    const args = qnxJavaBuildArguments(kind, inputs, "/declared ndk/llvm/bin");
    deepStrictEqual(args.slice(0, 3), [task, "--no-daemon", "--console=plain"]);
    const prefix = "-P" + property + "=";
    ok(args[3].startsWith(prefix));
    deepStrictEqual(JSON.parse(args[3].slice(prefix.length)), inputs);
    strictEqual(args[4], "-PvoboostI18nLlvmBin=/declared ndk/llvm/bin");
    strictEqual(args.length, 5);
  }
});

test("QNX locale Gradle receives the configured LLVM and manifest as distinct arguments", () => {
  deepStrictEqual(qnxLocaleBuildArguments("/source path/locale.json", "/declared ndk/llvm/bin"),
    ["buildI18nQnxResources", "--no-daemon", "--console=plain", "-PvoboostQnxLocaleInputs=/source path/locale.json", "-PvoboostI18nLlvmBin=/declared ndk/llvm/bin"]);
});

test("production source closure needs no Python executable", () => {
  deepStrictEqual(PRODUCTION_HOST_COMMANDS, ["cargo", "keytool", "make", "node"]);
  ok(PRODUCTION_HOST_COMMANDS.every((name) => !name.startsWith("python")));
});

test("selected native i18n requires explicit stock resource inputs", () => {
  const config = {...validConfig(), agentSet:"development"};
  strictEqual(parseBuildClosureConfig(config).qnxLocaleInputsManifest, "/Users/example/workspace/locale-inputs.json");
  const {qnxLocaleInputsManifest: _, ...missing} = config;
  throws(()=>parseBuildClosureConfig(missing), /requires qnxLocaleInputsManifest/);
  throws(()=>parseBuildClosureConfig({...config,qnxLocaleInputsManifest:"relative.json"}), /absolute path/);
  strictEqual(parseBuildClosureConfig({...missing,agentSet:"release"}).qnxLocaleInputsManifest, undefined);
});

test("runtime source SDK follows explicit overridden inject repository, independently of agents", () => {
  const args=qnxRuntimeInputs("/different agents/source", "/overridden inject/source", "/sdk/ndk29", "/different agents/source/build/native");
  deepStrictEqual(args,["--agents","/different agents/source","--sdk","/overridden inject/source/build/cluster/product/sdk-v3",
    "--source-sdk","/overridden inject/source/cluster/sdk","--ndk","/sdk/ndk29","--output","/different agents/source/build/native"]);
  const json=qnxJavaBuildArguments("runtime",args, "/llvm/bin")[3].split("=").slice(1).join("=");
  deepStrictEqual(JSON.parse(json),args);
});

test("PW source closure declares same-owner module from explicit repositories", () => {
  deepStrictEqual(qnxPwModuleInputs("/other agents", "/other inject", "/actual ndk"), [
    "--agents", "/other agents", "--inject", "/other inject", "--sdk", "/other inject/build/cluster/product/sdk-v3",
    "--ndk", "/actual ndk", "--output", "/other agents/build/cluster/pw-inert",
  ]);
  ok(agentBuildArguments({...validConfig(), sourceMode:"local-working-tree", agentSet:"development"}, "/native", "/llvm/bin").includes("-PpwNativeModuleDirectory=/pw-inert"));
});

test("config rejects retired license repository override and accepts active overrides", () => {
  for (const name of ["product", "app", "agents", "inject", "installer", "logo", "codestyle"]) {
    const config = parseBuildClosureConfig({ ...validConfig(), repositories: { [name]: "/work/source" } });
    strictEqual(config.repositories?.[name], "/work/source");
  }
  throws(() => parseBuildClosureConfig({ ...validConfig(), repositories: { license: "/work/retired" } }), /unknown repository/);
});
