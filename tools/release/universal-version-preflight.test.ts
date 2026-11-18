import { strictEqual, match } from "node:assert";
import { mkdtempSync, mkdirSync, writeFileSync, copyFileSync, rmSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { test } from "node:test";
import { resolveReleaseIdentity } from "./release-identity.ts";
import { buildInstallerVersionInputs } from "./installer-version.ts";

test("packaging validates definition-derived version before build tools", () => {
  const root = mkdtempSync(join(import.meta.dirname, ".version-preflight-"));
  try {
    mkdirSync(join(root, "scripts"));
    mkdirSync(join(root, "src-tauri"));
    mkdirSync(join(root, "bin"));
    copyFileSync(join(import.meta.dirname, "../../../voboost-installer/scripts/build-macos-universal-app.sh"), join(root, "scripts/build.sh"));
    const definition = join(root, "version.toml");
    writeFileSync(definition, readFileSync(join(import.meta.dirname, "../../release/version.toml")));
    const version = buildInstallerVersionInputs(resolveReleaseIdentity(definition, join(root, "inputs"))).productVersion;
    writeFileSync(join(root, "package.json"), JSON.stringify({ version }));
    writeFileSync(join(root, "src-tauri/tauri.conf.json"), JSON.stringify({ version }));
    writeFileSync(join(root, "src-tauri/Cargo.toml"), `[package]\nversion = "${version}"\n`);
    writeFileSync(join(root, "bin/uname"), "#!/bin/sh\nprintf Darwin\n", { mode: 0o755 });
    writeFileSync(join(root, "bin/rustup"), "#!/bin/sh\necho PREFLIGHT_PASSED >&2\nexit 77\n", { mode: 0o755 });
    const key = join(root, "key");
    writeFileSync(key, "fixture");
    const env: NodeJS.ProcessEnv = { ...process.env, PATH: join(root, "bin") + ":" + process.env.PATH };
    delete env.VOBOOST_PRODUCT_VERSION;
    for (const name of ["VOBOOST_PRODUCT_MANIFEST_PUBLIC_KEY_PEM", "VOBOOST_RESTORE_PUBLIC_KEY_PEM", "VOBOOST_NATIVE_EXECUTABLE_PUBLIC_KEY_PEM", "VOBOOST_PRODUCT_MANIFEST_ROLE_FIXTURE_SIGNATURE", "VOBOOST_RESTORE_ROLE_FIXTURE_SIGNATURE", "VOBOOST_NATIVE_EXECUTABLE_ROLE_FIXTURE_SIGNATURE"]) env[name] = key;
    const run = () => spawnSync("/bin/sh", [join(root, "scripts/build.sh"), join(root, "Voboost Installer.app")], { env, encoding: "utf8" });
    let result = run();
    strictEqual(result.status, 2);
    match(result.stderr, /VOBOOST_PRODUCT_VERSION is required/);
    strictEqual(result.stderr.includes("PREFLIGHT_PASSED"), false);
    env.VOBOOST_PRODUCT_VERSION = "9.0.0";
    result = run();
    strictEqual(result.status, 2);
    strictEqual(result.stderr.includes("PREFLIGHT_PASSED"), false);
    env.VOBOOST_PRODUCT_VERSION = version;
    result = run();
    match(result.stderr, /PREFLIGHT_PASSED/);
    writeFileSync(join(root, "src-tauri/tauri.conf.json"), JSON.stringify({ version: "9.0.0" }));
    result = run();
    strictEqual(result.status, 2);
    strictEqual(result.stderr.includes("PREFLIGHT_PASSED"), false);
  } finally { rmSync(root, { recursive: true, force: true }); }
});
