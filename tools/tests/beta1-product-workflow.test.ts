import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import {
    chmodSync,
    existsSync,
    mkdirSync,
    mkdtempSync,
    readFileSync,
    readdirSync,
    rmSync,
    statSync,
    symlinkSync,
    unlinkSync,
    writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";

const root = resolve(import.meta.dirname, "../..");
const ci = readFileSync(join(root, ".github/workflows/beta1-ci.yml"), "utf8");
const publication = readFileSync(join(root, ".github/workflows/beta1-publication.yml"), "utf8");
const evidenceWorkflow = readFileSync(join(root, ".github/workflows/beta1-evidence-ingest.yml"), "utf8");
const verifyWorkflow = readFileSync(join(root, ".github/workflows/verify.yml"), "utf8");
const definition = readFileSync(join(root, "release/1.0.0-beta1.toml"), "utf8");
const trust = readFileSync(join(root, "release/1.0.0-beta1-trust.json"), "utf8");
const localBuild = readFileSync(join(root, "release/build-local-beta1.sh"), "utf8");
// release/build-emulator-beta1.sh and release/local-beta1-ci.sh were removed
// after the migration to the build-closure TS chain (single machine profile);
// no emulatorBuild constant is read from disk anymore.
const authorityInit = readFileSync(join(root, "release/init-local-test-authority.sh"), "utf8");
const localBuildPaths = readFileSync(join(root, "release/local-build-paths.sh"), "utf8");
const packageInputs = readFileSync(join(root, "release/prepare-package-inputs.sh"), "utf8");
const injectRoot = resolve(root, "../voboost-inject");
const injectMakefile = readFileSync(join(injectRoot, "Makefile"), "utf8");

function job(workflow: string, name: string, next?: string): string {
    const start = workflow.indexOf(`  ${name}:\n`);
    assert.notEqual(start, -1, `missing job ${name}`);
    const end = next === undefined ? workflow.length : workflow.indexOf(`  ${next}:\n`, start + 1);
    assert.notEqual(end, -1, `missing next job ${next}`);
    return workflow.slice(start, end);
}

test("Android CI resolves exact codestyle siblings and the OEM driver input", () => {
    assert.match(ci, /codestyle_sha: \{description: Full voboost-codestyle commit, required: true, type: string\}/u);
    assert.match(ci, /group: beta1-ci-.*inputs\.codestyle_sha/u);
    const admission = job(ci, "admission", "prepare-app");
    assert.match(admission, /CODESTYLE_SHA: "\$\{\{ inputs\.codestyle_sha \}\}"/u);
    assert.match(admission, /\[\[ "\$CODESTYLE_SHA" =~ \^\[0-9a-f\]\{40\}\$ \]\]/u);
    assert.match(admission, /repos\/voboost\/voboost-codestyle\/git\/commits\/\$CODESTYLE_SHA/u);
    for (const section of [job(ci, "prepare-app", "prepare-agents"), job(ci, "prepare-agents", "prepare-native")]) {
        assert.match(section, /repository: voboost\/voboost-codestyle, ref: "\$\{\{ inputs\.codestyle_sha \}\}", path: voboost-codestyle, persist-credentials: false/u);
        assert.match(section, /git -C "\$GITHUB_WORKSPACE\/voboost-codestyle" rev-parse HEAD/u);
        assert.match(section, /needs\.admission\.outputs\.codestyle_tree/u);
    }
    const agents = job(ci, "prepare-agents", "prepare-native");
    assert.match(agents, /repository: voboost\/voboost-inject, ref: "\$\{\{ inputs\.inject_sha \}\}", path: inject/u);
    assert.ok(agents.includes('./gradlew runOemTestDriverBuild -PoemTestDriverInjectProjectDir="$GITHUB_WORKSPACE/inject" -PoemTestDriverOut="$RUNNER_TEMP/oem-driver.json" --no-daemon -q'));
    assert.ok(agents.includes('driver_sha=$(jq -r .sha256 "$RUNNER_TEMP/oem-driver.json")'));
    assert.doesNotMatch(agents, /python(?:3)?\b|build-oem-test-driver\.py/u);
    assert.match(agents, /-PoemInjectProjectDir="\$GITHUB_WORKSPACE\/inject" -PoemDriverSha="\$driver_sha"/u);
});

test("beta1 CI binds exact commits and records the local artifact plan", () => {
    for (const name of ["app", "agents", "inject", "installer", "logo"]) {
        assert.match(definition, new RegExp(`^${name} = "[0-9a-f]{40}"$`, "mu"));
    }
    assert.doesNotMatch(definition, /^mode\s*=/mu);
    assert.match(definition, /^\[android\]\nversionCode = 1000019$/mu);

    // The legacy local plan script (release/local-beta1-ci.sh) was retired
    // with the build-closure TS chain: its pinned-repository/stage semantics
    // are now enforced by the executed plan (tools/release/build-closure.ts),
    // which this test freezes below + the dry-run plan consumer test in
    // tools/release/build-closure.test.ts.
    const planText = readFileSync(join(root, "tools/release/build-closure.ts"), "utf8");
    for (const step of [
        "freeze-identity",
        "snapshot-sources-before",
        "snapshot-sources-after",
        "verify-closure",
        "verify-profile-markers",
        "publish-bundle",
    ]) assert.ok(planText.includes(`"${step}"`), `build-closure plan must keep step ${step}`);
    assert.match(ci, /cmp release\/version\.toml release\/1\.0\.0-beta1\.toml/u);
});

test("product wire corpus is byte-identical in every component mirror", () => {
    const authority = join(root, "release/fixtures/wire");
    const expected = readdirSync(authority).sort();
    assert.deepEqual(expected, [
        "SHA256SUMS",
        "installed-healthy.json",
        "installed-unknown-no-version.json",
        "operation-install-complete.json",
        "startup-mode-hidden.json",
        "status-interface.json",
        "status-none.json",
        "uninstall-absence.json",
    ]);
    for (const mirror of [
        resolve(root, "../voboost-inject/test/fixtures/wire"),
        resolve(root, "../voboost-installer/src-tauri/tests/fixtures/wire"),
    ]) {
        assert.deepEqual(readdirSync(mirror).sort(), expected);
        for (const name of expected) {
            assert.deepEqual(readFileSync(join(mirror, name)), readFileSync(join(authority, name)), `${mirror}/${name}`);
        }
    }
});

test("wire synchronization updates only the closed corpus and refuses unexpected entries", () => {
    const workspace = mkdtempSync(join(tmpdir(), "voboost-wire-sync-"));
    const mirrors = [
        join(workspace, "voboost-inject/test/fixtures/wire"),
        join(workspace, "voboost-installer/src-tauri/tests/fixtures/wire"),
    ];
    try {
        for (const mirror of mirrors) mkdirSync(mirror, { recursive: true });
        execFileSync(join(root, "release/sync-wire-fixtures.sh"), [workspace]);
        const authority = join(root, "release/fixtures/wire");
        for (const mirror of mirrors) {
            assert.deepEqual(readdirSync(mirror).sort(), readdirSync(authority).sort());
            for (const name of readdirSync(authority)) {
                assert.deepEqual(
                    readFileSync(join(mirror, name)),
                    readFileSync(join(authority, name)),
                );
            }
        }

        const foreign = join(mirrors[0], "foreign.json");
        writeFileSync(foreign, "do not delete\n");
        const rejected = spawnSync(join(root, "release/sync-wire-fixtures.sh"), [workspace], {
            encoding: "utf8",
        });
        assert.notEqual(rejected.status, 0);
        assert.equal(readFileSync(foreign, "utf8"), "do not delete\n");
    } finally {
        rmSync(workspace, { recursive: true, force: true });
    }
});

test("durable local beta1 paths reject source temp cache and symlink outputs", () => {
    assert.match(localBuild, /canonical_new_durable_path/u);
    assert.match(localBuild, /work=\$\(canonical_new_durable_path "\$workspace_root" "\$output\.work" work\)/u);
    assert.doesNotMatch(localBuild, /mktemp -d|trap cleanup|rm -r "\$work"/u);
    for (const role of ["agent-catalog", "product", "native-executable", "restore"]) {
        assert.match(localBuild, new RegExp(`for role in [^\\n]*${role}`, "u"));
    }
    assert.match(localBuild, /prepare-beta1-unsigned\.sh/u);
    assert.match(localBuild, /VOBOOST_EMBEDDED_PRODUCT_PUBLIC_KEY_PEM/u);
    assert.match(localBuild, /make -C "\$inject" build-android/u);
    assert.doesNotMatch(localBuild, /VOBOOST_RELEASE_(?:CHANNEL|BASE_VERSION|BETA)/u);
    assert.doesNotMatch(localBuild, /NATIVE_MANIFEST|legacy-native-manifest/u);
    assert.match(localBuild, /VOBOOST_ANDROID_CERT_SHA256="\$android_certificate"/u);
    assert.match(localBuild, /ANDROID_CERT_SHA256="\$android_certificate"/u);
    assert.match(localBuild, /sign-native-executable/u);
    assert.match(localBuild, /verify-apk-product-root/u);
    assert.match(localBuild, /VOBOOST_EMBEDDED_PRODUCT_PUBLIC_KEY_PEM/u);
    assert.match(localBuild, /verify-embedded-root/u);
    assert.match(localBuild, /verify-file .*agents\.json/u);
    assert.match(localBuild, /verify-restore/u);
    assert.match(localBuild, /verify-product-closure/u);
    assert.match(localBuild, /--features android-integration verify::tests::pinned_android_tools_extract_identity_from_exact_signed_apk -- --exact/u);
    assert.match(packageInputs, /verify-product-closure "\$package_closure" "\$roots\/product\.pub"/u);
    assert.doesNotMatch(packageInputs, /verify-manifest "\$closure\/voboost\.json" "\$closure"/u);
    assert.match(localBuild, /offline-package\.ts" build-mac-dmg/u);
    assert.match(localBuild, /scripts\/build-macos-universal-app\.sh/u);
    assert.doesNotMatch(localBuild, /VOBOOST_LOCAL_MACOS_UNIVERSAL_(APP|ADB)/u);
    assert.match(localBuild, /"\$work\/universal-app\/Voboost Installer\.app" "\$work\/closure"/u);
    assert.match(localBuild, /hdiutil verify/u);
    assert.match(localBuild, /local-build-evidence\.json/u);
    assert.match(localBuild, /publicIdentities/u);
    assert.match(localBuild, /privateKeysPersisted:true/u);
    assert.match(localBuild, /validate_local_test_authority/u);
    assert.doesNotMatch(localBuild, /init-local-test-authority\.sh/u);
    assert.match(localBuild, /privateKeysLocation:"explicit-retained-authority"/u);
    assert.match(localBuild, /evidenceAuthority:"not-created-before-vehicle-gate"/u);
    assert.match(localBuild, /runtimeReadiness:"ready-for-local-vehicle-gate"/u);
    assert.match(localBuild, /snapshot-before\.tsv/u);
    assert.match(localBuild, /snapshot-after\.tsv/u);
    assert.match(localBuild, /snapshot-final\.tsv/u);
    assert.match(localBuild, /output-identity\.txt/u);
    assert.match(localBuild, /snapshot-final\.tsv[\s\S]*output-identity\.txt/u);
    assert.match(localBuildPaths, /must remain outside source repository/u);
    assert.match(localBuildPaths, /must remain outside temporary trees/u);
    assert.match(localBuildPaths, /\.cache[\s\S]*Caches/u);
    assert.doesNotMatch(localBuild, /git (commit|push|tag)|gh release|adb /u);

    const directory = mkdtempSync(join(tmpdir(), "voboost-local-build-blocked-"));
    const workspace = resolve(root, "..");
    const sourceOutput = join(root, `local-beta1-output-${process.pid}`);
    const buildOutput = join(workspace, "build", `local-beta1-output-${process.pid}`);
    const cacheOutput = join(workspace, ".cache", `local-beta1-output-${process.pid}`);
    const link = join(workspace, `.local-beta1-source-link-${process.pid}`);
    try {
        symlinkSync(root, link);
        for (const [output, message] of [
            [join(directory, "output"), directory.startsWith(root + "/") ? /outside source repository/u : /outside temporary trees/u],
            [sourceOutput, /outside source repository/u],
            [buildOutput, /outside build\/cache trees/u],
            [cacheOutput, /outside build\/cache trees/u],
            [join(link, "not-yet-created", "output"), /outside source repository/u],
            ["relative-output", /must be an absolute path/u],
        ] as const) {
            const result = spawnSync(join(root, "release/build-local-beta1.sh"), [workspace, join(workspace, "retained-authority"), output], {
                encoding: "utf8",
                env: { ...process.env, ANDROID_SDK_ROOT: "/Users/vitaly/Library/Android/sdk", ANDROID_NDK_HOME: "/Users/vitaly/Library/Android/sdk/ndk/29.0.14206865" },
            });
            assert.notEqual(result.status, 0);
            assert.match(result.stderr, message);
            assert.equal(existsSync(output), false);
        }
    } finally {
        if (existsSync(link)) unlinkSync(link);
        rmSync(directory, { recursive: true, force: true });
    }
});

test("durable local beta1 authority is private non-overwriting and verifies after reopen", () => {
    const builds = resolve(root, "../local-builds");
    mkdirSync(builds, { recursive: true, mode: 0o700 });
    const directory = mkdtempSync(join(builds, "authority-test-"));
    const authority = join(directory, "authority");
    try {
        execFileSync(join(root, "release/init-local-test-authority.sh"), [authority], { stdio: "pipe" });
        assert.equal(readFileSync(join(authority, "authority-identity.txt"), "utf8"), "schema=1\n");
        assert.equal(statSync(authority).mode & 0o777, 0o700);
        const productKey = readFileSync(join(authority, "product.key"));
        for (const role of ["agent-catalog", "product", "native-executable", "restore"]) {
            assert.equal(readFileSync(join(authority, `${role}.key`)).byteLength, 32);
            assert.equal(statSync(join(authority, `${role}.key`)).mode & 0o777, 0o600);
        }
        assert.equal(statSync(join(authority, "app.jks")).mode & 0o777, 0o600);
        for (const role of ["release-catalog", "restore", "native-executable"]) {
            const publicRole = role === "release-catalog" ? "product" : role;
            execFileSync("cargo", ["run", "--manifest-path", join(root, "release/Cargo.toml"), "--locked", "--offline", "--", "verify-file", join(authority, `${publicRole}.pub`), join(authority, "fixtures", role), join(authority, "fixtures", `${role}.sig`)], { stdio: "pipe" });
        }
        const second = spawnSync(join(root, "release/init-local-test-authority.sh"), [authority], { encoding: "utf8" });
        assert.notEqual(second.status, 0);
        assert.match(second.stderr, /authority must not exist/u);
        assert.deepEqual(readFileSync(join(authority, "product.key")), productKey);
    } finally {
        rmSync(directory, { recursive: true, force: true });
    }
    assert.match(authorityInit, /-validity 365/u);
});

// The legacy emulator builder script (release/build-emulator-beta1.sh) was
// retired with the single machine-profile decision: the build-closure TS
// chain is the only builder, its durable sibling-work/reservation semantics
// are covered by closure-primitives.test.ts (reserveOutput single-owner and
// stale takeover) and its profile markers by the test below.
test("machine closure chain keeps durable output reservation and profile markers", () => {
    const chain = readFileSync(join(root, "tools/release/closure-steps.ts"), "utf8");
    const builder = readFileSync(join(root, "tools/release/build-closure.ts"), "utf8");
    assert.match(builder, /reserveOutput\(output, "build-closure"\)/u);
    for (const marker of [
        "VOBOOST_EMULATOR_DAEMON_SELINUX_SKIP_V1",
        "VOBOOST_EMULATOR_SELINUX_SKIP_V1",
        "VOBOOST_EMULATOR_RESTORE_SELINUX_SKIP_V1",
        "VOBOOST_EMULATOR_UNINSTALL_SELINUX_SKIP_V1",
        "VOBOOST_EMULATOR_UNINSTALL_MOUNT_ADAPTER_V1",
    ]) assert.match(chain, new RegExp(marker, "u"));
    assert.match(builder, /"verify-profile-markers"/u);
    assert.match(builder, /emulator profile cannot be built from current sources/u);
    for (const gate of ["verify-closure", "verify-embedded-root", "sign-natives", "inspect-apk", "compose-and-sign-product"]) {
        assert.match(builder, new RegExp(`"${gate}"`, "u"), `build-closure plan must keep ${gate}`);
    }
    assert.doesNotMatch(localBuild + ci, /VOBOOST_EMULATOR_UNINSTALL_MOUNT_ADAPTER_V1/u);
});

test("AVD beta1 builder composes a closure with production SELinux behavior", () => {
    // Legacy grep of build-emulator-beta1.sh retired with the script; the
    // equivalent production-SELinux properties are asserted against the TS
    // chain in the machine closure chain test above.
    const stepsChain = readFileSync(join(root, "tools/release/closure-steps.ts"), "utf8");
    assert.match(stepsChain, /COMPLETE_MARKER/u);
    assert.match(stepsChain, /machine profile rejects every emulator-skip marker/u);
    assert.doesNotMatch(localBuild + ci, /VOBOOST_EMULATOR_UNINSTALL_MOUNT_ADAPTER_V1/u);
});

test("native build dry run binds the exact Android certificate Make input", () => {
    const start = injectMakefile.indexOf("build-uninstall:");
    const end = injectMakefile.indexOf("\nbuild-install:", start);
    assert.ok(start >= 0 && end > start);
    const recipe = injectMakefile.slice(start, end);
    assert.match(recipe, /ANDROID_CERT_SHA256.*canonical lowercase SHA-256/u);
    assert.match(recipe, /VOBOOST_ANDROID_CERT_SHA256="\$\(ANDROID_CERT_SHA256\)"/u);
    assert.match(localBuild, /ANDROID_CERT_SHA256="\$android_certificate"/u);
    assert.match(ci, /ANDROID_CERT_SHA256="\$VOBOOST_ANDROID_CERT_SHA256"/u);
    assert.doesNotMatch(localBuild + ci, /make[^\n]*VOBOOST_ANDROID_CERT_SHA256=/u);
});

test("beta1 CI separates prepared inputs, protected signing, and secretless final verification", () => {
    assert.match(ci, /workflow_dispatch:/u);
    assert.match(ci, /enable_remote_sources:[\s\S]*?default: false/u);
    assert.match(ci, /permissions: \{actions: read, contents: read\}/u);
    assert.match(ci, /group: beta1-ci-/u);
    assert.match(ci, /cancel-in-progress: false/u);
    assert.doesNotMatch(ci, /git push|gh release|channel update|contents: write/u);

    const admission = job(ci, "admission", "prepare-app");
    const app = job(ci, "prepare-app", "prepare-agents");
    const agents = job(ci, "prepare-agents", "prepare-native");
    const native = job(ci, "prepare-native", "prepare-installer");
    const installer = job(ci, "prepare-installer", "sign-app");
    const signApp = job(ci, "sign-app", "sign-agents");
    const signAgents = job(ci, "sign-agents", "sign-native");
    const signNative = job(ci, "sign-native", "sign-product");
    const signProduct = job(ci, "sign-product", "verify-final-signed");
    const verify = job(ci, "verify-final-signed", "export-vehicle-input");
    const vehicle = job(ci, "export-vehicle-input", "package-windows");
    const packageWindows = job(ci, "package-windows", "package-linux");
    const packageLinux = job(ci, "package-linux", "package-macos");
    const packageMacos = job(ci, "package-macos", "package-aggregate");
    const packageAggregate = job(ci, "package-aggregate");

    assert.match(admission, /test "\$ENABLED" = true/u);
    assert.match(admission, /beta1-local-delivery-evidence/u);
    assert.match(admission, /beta1-evidence-ingest\.yml/u);
    assert.match(admission, /verify-file .*evidence\.pub/u);
    assert.match(admission, /git\/commits\/\$commit" --jq \.tree\.sha/u);
    assert.match(admission, /node tools\/beta1-evidence\.ts local/u);
    assert.match(admission, /--features android-integration verify::tests::pinned_android_tools_extract_identity_from_exact_signed_apk -- --exact/u);
    assert.match(admission, /repository: voboost\/voboost-agents, ref: "\$\{\{ inputs\.agents_sha \}\}", path: \.ci-agents-metadata, persist-credentials: false/u);
    assert.match(admission, /git -C \.ci-agents-metadata rev-parse HEAD/u);
    assert.match(admission, /git -C \.ci-agents-metadata status --porcelain=v1/u);
    assert.match(admission, /generate-agent-status\.ts --check --release --agents-root/u);
    assert.match(admission, /VOBOOST_DOCS_RELEASE_MODE=1 .*VOBOOST_AGENTS_ROOT=/u);
    assert.doesNotMatch(admission, /(?:cd|working-directory:).*\.ci-agents-metadata|\.ci-agents-metadata\/(?:gradlew|tools|scripts)/u);
    assert.match(admission, /product_workflow_blob/u);
    assert.match(admission, /source_definition_sha256/u);
    assert.doesNotMatch(admission, /\bsecrets\./u);

    assert.match(app, /voboost-app lacks the reviewed unsigned APK preparation adapter/u);
    assert.match(app, /prepare-beta1-unsigned\.sh prepared\/voboost-app-unsigned\.apk/u);
    assert.match(app, /VOBOOST_EMBEDDED_PRODUCT_PUBLIC_KEY_PEM/u);
    assert.match(app, /path: product/u);
    assert.match(app, /name: beta1-prepared-app/u);
    assert.match(agents, /rm -f prepared\/agents\.json\.sig/u);
    assert.match(agents, /name: beta1-prepared-agents/u);
    assert.match(native, /prepare-beta1-unsigned\.sh/u);
    assert.match(native, /voboost-inject lacks the reviewed unsigned native preparation adapter/u);
    assert.match(native, /name: beta1-prepared-native/u);
    assert.match(native, /path: product/u);
    for (const name of ["VOBOOST_AGENT_CATALOG_PUBLIC_KEY_PEM", "VOBOOST_RELEASE_CATALOG_PUBLIC_KEY_PEM", "VOBOOST_NATIVE_EXECUTABLE_PUBLIC_KEY_PEM", "VOBOOST_RESTORE_PUBLIC_KEY_PEM"]) {
        assert.match(native, new RegExp(name, "u"));
    }
    for (const name of ["VOBOOST_ANDROID_CERT_SHA256"]) {
        assert.match(native, new RegExp(name.replaceAll(".", "\\."), "u"));
    }
    assert.doesNotMatch(native, /VOBOOST_RELEASE_(?:CHANNEL|BASE_VERSION|BETA)|NATIVE_MANIFEST/u);
    assert.match(native, /ANDROID_CERT_SHA256="\$VOBOOST_ANDROID_CERT_SHA256"/u);
    assert.match(installer, /Verify installer source before secretless platform packaging/u);
    for (const prepared of [app, agents, native, installer]) {
        assert.doesNotMatch(prepared, /\bsecrets\./u);
        assert.doesNotMatch(prepared, /sign-file|apksigner sign|produce-restore/u);
    }

    assert.match(signApp, /environment: beta1-sign-android/u);
    assert.match(signApp, /VOBOOST_BETA1_ANDROID_KEYSTORE/u);
    assert.match(signApp, /sha256sum --check --strict SHA256SUMS/u);
    assert.match(signApp, /apksigner sign/u);
    assert.match(signAgents, /environment: beta1-sign-agents/u);
    assert.match(signAgents, /VOBOOST_BETA1_AGENTS_SIGNING_KEY/u);
    assert.match(signAgents, /sign-file .*agents\.json agents\.json\.sig/u);
    assert.match(signNative, /environment: beta1-sign-native/u);
    assert.match(signNative, /VOBOOST_BETA1_NATIVE_EXECUTABLE_SIGNING_KEY/u);
    assert.match(signNative, /sign-native-executable "\$role"/u);
    assert.match(signNative, /cmp "\$RUNNER_TEMP\/native\.pub" "\$RUNNER_TEMP\/expected-native\.pub"/u);
    assert.doesNotMatch(signNative, /repository: voboost\/voboost-inject|inject\/tools|sign-beta1-prepared/u);
    assert.match(signProduct, /environment: beta1-sign-product/u);
    assert.match(signProduct, /VOBOOST_BETA1_RESTORE_SIGNING_KEY/u);
    assert.match(signProduct, /VOBOOST_BETA1_PRODUCT_SIGNING_KEY/u);
    assert.match(signProduct, /produce-restore/u);
    assert.match(signProduct, /compose release\/1\.0\.0-beta1\.toml/u);
    assert.match(signProduct, /sign-file .*release-identity\.json release-identity\.json\.sig/u);
    assert.match(signProduct, /productCommit/u);
    assert.match(signProduct, /productWorkflowBlob/u);
    assert.match(signProduct, /sourceDefinitionSha256/u);
    assert.match(signProduct, /trustDocumentSha256/u);
    for (const signing of [signApp, signAgents, signNative, signProduct]) {
        assert.doesNotMatch(signing, /gradlew|npm run|make build/u);
        assert.doesNotMatch(signing, /repository: voboost\/voboost-(app|agents|inject|installer)/u);
        assert.match(signing, /ref: "\$\{\{ inputs\.product_sha \}\}"/u);
        assert.match(signing, /git rev-parse HEAD:\.github\/workflows\/beta1-ci\.yml/u);
        assert.match(signing, /1\.0\.0-beta1-trust\.json/u);
        assert.match(signing, /trap 'rm -f/u);
    }

    assert.match(verify, /Re-download and verify final protected-signed closure without secrets/u);
    assert.match(verify, /actions\/runs\/\$GITHUB_RUN_ID\/artifacts/u);
    assert.match(verify, /actions\/artifacts\/\$artifact_id\/zip/u);
    assert.match(verify, /sha256:\$\(sha256sum .*final\.zip/u);
    assert.match(verify, /verify-file .*voboost\.json.*voboost\.json\.sig/u);
    assert.match(verify, /verify-file .*release-identity\.json.*release-identity\.json\.sig/u);
    assert.match(verify, /verify-file .*agents\.json.*agents\.json\.sig/u);
    assert.match(verify, /verify-restore/u);
    assert.match(verify, /inspect-apk/u);
    assert.match(verify, /androidCertificateSha256/u);
    assert.match(verify, /verify-apk-product-root "\$RUNNER_TEMP\/final\/voboost-app\.apk" "\$RUNNER_TEMP\/product\.pub"/u);
    assert.match(verify, /verify-native-executable "\$role"/u);
    assert.match(verify, /verify-embedded-root/u);
    assert.match(verify, /agentCatalogPublicKeyHex/u);
    assert.match(verify, /nativeExecutablePublicKeyHex/u);
    assert.doesNotMatch(verify, /legacyBuildOnly|nativeManifestPublicKeyHex|native-manifest/u);
    assert.match(verify, /restorePublicKeyHex/u);
    assert.match(verify, /productPublicKeyHex/u);
    assert.match(verify, /verify-manifest/u);
    assert.match(verify, /verify-product-closure/u);
    assert.doesNotMatch(verify, /\bsecrets\./u);
    assert.doesNotMatch(verify, /sign-file|apksigner sign|produce-restore|gradlew|npm run|make build/u);
    assert.match(vehicle, /vehicle=not-run/u);
    assert.match(vehicle, /uninstall=not-run/u);
    assert.match(vehicle, /cleanState=not-run/u);
    assert.match(vehicle, /runtimeReadiness=ready-for-ci-vehicle-gate/u);
    assert.match(vehicle, /publication=forbidden/u);
    assert.match(vehicle, /installerArtifactId/u);
    for (const packageJob of [packageWindows, packageLinux, packageMacos, packageAggregate]) {
        assert.doesNotMatch(packageJob, /\bsecrets\./u);
        assert.doesNotMatch(packageJob, /contents: write|git push|gh release/u);
    }
    for (const packageJob of [packageWindows, packageLinux, packageMacos]) {
        assert.match(packageJob, /prepare-package-inputs\.sh product closure fixtures roots package-closure/u);
    }
    assert.match(packageWindows, /build-executable windows/u);
    assert.match(packageWindows, /resources\/adb\/win/u);
    assert.match(packageWindows, /verify-binary-embedded-root/u);
    assert.match(packageLinux, /cargo build --release --locked --manifest-path installer\/src-tauri\/Cargo\.toml --target x86_64-unknown-linux-gnu --no-default-features --features cli --bin voboost-installer-cli/u);
    assert.match(packageLinux, /scripts\/verify-linux-cli\.sh "\$base"/u);
    assert.match(packageLinux, /verify-product-closure package-closure roots\/product\.pub/u);
    for (const packageJob of [packageWindows, packageLinux, packageMacos]) {
        assert.match(packageJob, /for root in product native restore/u);
        assert.doesNotMatch(packageJob, /AGENT_CATALOG_PUBLIC_KEY|NATIVE_MANIFEST|native-manifest/u);
    }
    assert.ok(packageLinux.indexOf("verify-binary-embedded-root") < packageLinux.indexOf("build-executable linux"));
    assert.match(packageLinux, /build-executable linux "\$base" package-closure roots\/product\.pem/u);
    assert.match(packageLinux, /inspect-executable "\$RUNNER_TEMP\/voboost-installer"/u);
    assert.match(packageLinux, /"\$RUNNER_TEMP\/voboost-installer" --verify-package/u);
    assert.match(packageLinux, /linux-tar-bzip2 "\$RUNNER_TEMP\/voboost-installer" "\$GITHUB_WORKSPACE\/voboost-installer-1\.0\.0-beta1\.tar\.bz2"/u);
    const readableInventory = (body: string) => {
        assert.match(body, /inspectSingleEntryTarBzip2\("voboost-installer-1\.0\.0-beta1\.tar\.bz2"\)/u);
        assert.match(body, /const expected = \['voboost-installer', \.\.\.LINUX_LICENSE_NAMES\]\.sort\(\)/u);
        assert.match(body, /JSON\.stringify\(fs\.readdirSync\(root\)\.sort\(\)\) !== JSON\.stringify\(expected\)/u);
        assert.match(body, /verifyReadableLicenses\(root, LINUX_LICENSE_NAMES\)/u);
    };
    readableInventory(packageLinux);
    for (const required of ['verifyReadableLicenses(root, LINUX_LICENSE_NAMES);', "...LINUX_LICENSE_NAMES", 'inspectSingleEntryTarBzip2("voboost-installer-1.0.0-beta1.tar.bz2")']) {
        assert.throws(() => readableInventory(packageLinux.replace(required, '')), /match|expression/u);
    }
    assert.match(packageLinux, /tar -xjf/u);
    assert.match(packageLinux, /stat -c '%a'/u);
    assert.match(packageLinux, /cmp "\$RUNNER_TEMP\/voboost-installer"/u);
    assert.match(packageLinux, /sha256sum "\$RUNNER_TEMP\/voboost-installer"/u);
    assert.match(packageLinux, /name: beta1-package-linux/u);
    assert.doesNotMatch(packageLinux, /npm run tauri|gtk|webkit|appimage|resources\/adb/u);
    assert.match(packageMacos, /scripts\/build-macos-universal-app\.sh/u);
    assert.match(packageMacos, /build-mac-dmg "\$RUNNER_TEMP\/universal\/Voboost Installer\.app" package-closure roots\/product\.pem/u);
    assert.doesNotMatch(packageMacos, /VOBOOST_LOCAL_MACOS_UNIVERSAL_(APP|ADB)/u);
    assert.match(packageMacos, /verify-binary-embedded-root .* 2/u);
    assert.match(packageAggregate, /name: beta1-installer-packages/u);
    assert.match(packageAggregate, /package-inventory\.json/u);
    assert.match(packageAggregate, /artifact-id/u);
    assert.doesNotMatch(ci, /installer-packages-blocked/u);
});

test("publication stays behind manual protected evidence gates and is disabled by default", () => {
    assert.match(publication, /workflow_dispatch:/u);
    assert.match(publication, /confirm_publication:[\s\S]*?default: false/u);
    assert.match(publication, /group: beta1-publication-decision/u);
    assert.match(publication, /cancel-in-progress: false/u);

    const admission = job(publication, "admission", "publish-release");
    const publish = job(publication, "publish-release", "update-channel");
    const channel = job(publication, "update-channel");
    assert.match(admission, /environment: beta1-publication-approval/u);
    assert.match(admission, /VOBOOST_BETA1_PUBLICATION_ENABLED/u);
    assert.match(admission, /final_closure_artifact_id/u);
    assert.match(admission, /installer_packages_artifact_id/u);
    assert.match(admission, /vehicle_evidence_artifact_id/u);
    assert.match(admission, /beta1-ci-vehicle-evidence/u);
    assert.match(admission, /beta1-evidence-ingest\.yml/u);
    assert.match(admission, /verify-file .*evidence\.pub/u);
    assert.match(admission, /node tools\/beta1-evidence\.ts ci/u);
    assert.match(admission, /test "\$\(jq -r \.digest/u);
    assert.match(admission, /finalClosure:\{id:\$closureId,digest:\$closureDigest,payloadSha256:\$closurePayload\}/u);
    assert.match(admission, /installerPackages:\{id:\$packagesId,digest:\$packagesDigest,payloadSha256:\$packagesPayload\}/u);
    assert.match(admission, /workflowBlob/u);
    assert.match(admission, /source_definition_sha/u);
    assert.match(publish, /needs: admission/u);
    assert.match(publish, /environment: beta1-publication/u);
    assert.match(publish, /permissions: \{actions: read, contents: write\}/u);
    assert.match(publish, /vars\.VOBOOST_BETA1_PUBLICATION_ENABLED == 'true'/u);
    assert.match(publish, /Prove checkout clean before external bytes are downloaded/u);
    assert.ok(publish.indexOf("Prove checkout clean") < publish.indexOf("Download exact admitted CI bytes"));
    assert.match(publish, /Download exact admitted CI bytes outside checkout without rebuild or resign/u);
    assert.match(publish, /\$RUNNER_TEMP\/release-set/u);
    assert.match(publish, /actions\/artifacts\/\$id" --jq \.digest/u);
    assert.doesNotMatch(publish, /gradle|npm|sign-file|produce-restore|buildRelease|sign-native-executable/u);
    assert.match(publish, /CLOSURE_PAYLOAD: "\$\{\{ needs\.admission\.outputs\.closure_payload_sha256 \}\}"/u);
    assert.match(publish, /PACKAGES_PAYLOAD: "\$\{\{ needs\.admission\.outputs\.packages_payload_sha256 \}\}"/u);
    const mutation = publish.indexOf('if gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/v1.0.0-beta1"');
    const closureCheck = publish.lastIndexOf('= "$CLOSURE_PAYLOAD"', mutation);
    const packagesCheck = publish.lastIndexOf('= "$PACKAGES_PAYLOAD"', mutation);
    assert.ok(closureCheck >= 0 && closureCheck < mutation);
    assert.ok(packagesCheck >= 0 && packagesCheck < mutation);
    assert.match(publish, /gh api --method POST .*git\/tags/u);
    assert.match(publish, /-f object="\$PRODUCT_SHA"/u);
    assert.match(publish, /git\/refs/u);
    assert.match(publish, /git\/tags\/\$tag_object.*\.object\.sha/u);
    assert.match(publish, /gh release view .*--json isDraft/u);
    assert.match(publish, /gh release create .*--draft/u);
    assert.doesNotMatch(publish, /gh release delete|--clobber/u);
    assert.match(publish, /gh release upload/u);
    assert.match(publish, /gh release download/u);
    assert.match(publish, /cmp "\$path"/u);
    assert.match(publish, /diff -qr/u);
    assert.match(publish, /release_is_draft/u);
    assert.match(publish, /exact public prerelease already exists; publication is idempotently complete/u);
    assert.ok(publish.indexOf("gh release download") < publish.indexOf("--draft=false"));
    assert.match(channel, /vars\.VOBOOST_BETA1_CHANNEL_PUBLICATION_ENABLED == 'true'/u);
    assert.match(channel, /environment: beta1-channel-publication/u);
    assert.match(channel, /Accept only an already-published exact signed channel after ambiguous success/u);
    assert.match(channel, /--max-redirs 0/u);
    assert.match(channel, /verify-file .*channel\.json.*channel\.json\.sig/u);
    assert.match(channel, /verify-channel/u);
    assert.match(channel, /idempotently complete/u);
});

test("publication admission preserves colon-qualified artifact digests and exact names", () => {
    const admission = job(publication, "admission", "publish-release");
    const start = admission.indexOf("          for row in ");
    const end = admission.indexOf('            gh api "repos/$GITHUB_REPOSITORY/actions/artifacts/$id"', start);
    assert.ok(start >= 0 && end > start, "missing publication artifact admission parser");
    const result = spawnSync("bash", ["-c", `${admission.slice(start, end)}\n            printf '%s\\t%s\\t%s\\t%s\\n' "$name" "$id" "$digest" "$expected_name"\n          done`], {
        encoding: "utf8",
        env: {
            ...process.env,
            CLOSURE_ID: "123",
            CLOSURE_DIGEST: `sha256:${"a".repeat(64)}`,
            PACKAGES_ID: "456",
            PACKAGES_DIGEST: `sha256:${"b".repeat(64)}`,
        },
    });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(result.stdout.trim().split("\n").map((row) => row.split("\t")), [
        ["closure", "123", `sha256:${"a".repeat(64)}`, "beta1-final-signed-closure"],
        ["packages", "456", `sha256:${"b".repeat(64)}`, "beta1-installer-packages"],
    ]);
});

test("evidence ingestion verifies signed canonical payloads on exact product main", () => {
    assert.match(evidenceWorkflow, /workflow_dispatch:/u);
    assert.match(evidenceWorkflow, /environment: beta1-evidence-ingestion/u);
    assert.match(evidenceWorkflow, /test "\$GITHUB_REF" = refs\/heads\/main/u);
    assert.match(evidenceWorkflow, /test "\$GITHUB_SHA" = "\$GITHUB_WORKFLOW_SHA"/u);
    assert.match(evidenceWorkflow, /cmp "\$RUNNER_TEMP\/payload\.json" "\$RUNNER_TEMP\/canonical\.json"/u);
    assert.match(evidenceWorkflow, /verify-file "\$RUNNER_TEMP\/evidence\.pub"/u);
    assert.match(evidenceWorkflow, /roots\.evidencePublicKeyHex release\/1\.0\.0-beta1-trust\.json/u);
    assert.doesNotMatch(evidenceWorkflow, /VOBOOST_BETA1_EVIDENCE_PUBLIC_KEY_HEX/u);
    assert.match(evidenceWorkflow, /ci_workflow_blob=\$\(git rev-parse/u);
    assert.match(evidenceWorkflow, /git\/commits\/\$commit" --jq \.tree\.sha/u);
    assert.match(evidenceWorkflow, /beta1-evidence\.ts local/u);
    assert.match(evidenceWorkflow, /beta1-evidence\.ts ci/u);
    assert.doesNotMatch(evidenceWorkflow, /contents: write|git push|gh release/u);
});

test("ordinary verification uses exact clean agents metadata without executing candidate code", () => {
    assert.match(verifyWorkflow, /sed -nE .*agents = .*\[0-9a-f\]\{40\}/u);
    assert.match(verifyWorkflow, /repository: voboost\/voboost-agents/u);
    assert.match(verifyWorkflow, /ref: \$\{\{ steps\.agents-source\.outputs\.sha \}\}/u);
    assert.match(verifyWorkflow, /path: \.ci-agents-metadata/u);
    assert.match(verifyWorkflow, /persist-credentials: false/u);
    assert.match(verifyWorkflow, /git -C \.ci-agents-metadata rev-parse HEAD/u);
    assert.match(verifyWorkflow, /git -C \.ci-agents-metadata status --porcelain=v1/u);
    assert.match(verifyWorkflow, /VOBOOST_DOCS_RELEASE_MODE: '1'/u);
    assert.match(verifyWorkflow, /VOBOOST_AGENTS_CHECKOUT_CLEAN: '1'/u);
    assert.match(verifyWorkflow, /VOBOOST_AGENTS_ROOT: \$\{\{ github\.workspace \}\}\/\.ci-agents-metadata/u);
    assert.doesNotMatch(verifyWorkflow, /(?:cd|working-directory:).*\.ci-agents-metadata|\.ci-agents-metadata\/(?:gradlew|tools|scripts)/u);
    assert.match(verifyWorkflow, /--features android-integration\s+verify::tests::pinned_android_tools_extract_identity_from_exact_signed_apk\s+-- --exact/u);
});

test("every generated agent docs workflow consumer pins a clean exact metadata checkout", () => {
    const workflowRoot = join(root, ".github/workflows");
    const consumers = readdirSync(workflowRoot)
        .filter((name) => name.endsWith(".yml"))
        .filter((name) => /generate-agent-status|target:docs:check|npm run check(?:\s|$)/mu.test(readFileSync(join(workflowRoot, name), "utf8")))
        .sort();
    assert.deepEqual(consumers, ["beta1-ci.yml", "verify.yml"]);
    for (const name of consumers) {
        const workflow = readFileSync(join(workflowRoot, name), "utf8");
        assert.match(workflow, /repository: voboost\/voboost-agents/u, name);
        assert.match(workflow, /path: \.ci-agents-metadata/u, name);
        assert.match(workflow, /persist-credentials: false/u, name);
        assert.match(workflow, /git -C \.ci-agents-metadata rev-parse HEAD/u, name);
        assert.match(workflow, /git -C \.ci-agents-metadata status --porcelain=v1/u, name);
        assert.match(workflow, /VOBOOST_DOCS_RELEASE_MODE/u, name);
        assert.match(workflow, /VOBOOST_AGENTS_ROOT/u, name);
        assert.match(workflow, /VOBOOST_AGENTS_COMMIT/u, name);
        assert.doesNotMatch(workflow, /(?:cd|working-directory:).*\.ci-agents-metadata|\.ci-agents-metadata\/(?:gradlew|tools|scripts)/u, name);
    }
});

test("beta1 trust roots are source pinned and fail closed until provisioned", () => {
    const value = JSON.parse(trust);
    assert.ok(value.state === "unprovisioned" || value.state === "provisioned");
    assert.equal(value.rotation.mode, "source-reviewed-no-runtime-rotation");
    if (value.state === "unprovisioned") {
        assert.ok(Object.values(value.roots).every((root) => root === null));
    } else {
        for (const root of Object.values(value.roots)) {
            assert.equal(typeof root, "string");
            assert.match(root as string, /^[0-9a-f]{64}$/u);
        }
        const roleRoots = Object.entries(value.roots)
            .filter(([name]) => name !== "androidCertificateSha256")
            .map(([, root]) => root);
        assert.equal(new Set(roleRoots).size, roleRoots.length);
    }
    assert.doesNotMatch(ci + publication + evidenceWorkflow, /vars\.VOBOOST_BETA1_(EVIDENCE|PRODUCT|RESTORE|AGENTS|NATIVE|ANDROID).*PUBLIC/u);
    assert.match(ci, /test "\$\(jq -r \.state release\/1\.0\.0-beta1-trust\.json\)" = provisioned/u);
});

test("all third-party actions in beta1 workflows are pinned to immutable commits", () => {
    for (const workflow of [ci, publication, evidenceWorkflow, verifyWorkflow]) {
        for (const match of workflow.matchAll(/^\s*-?\s*uses:\s*([^\s#]+)@([^\s#]+)/gmu)) {
            assert.match(match[2], /^[0-9a-f]{40}$/u, match[0]);
        }
    }
});

function requireNativeHostGate(workflow: string): void {
    const native = job(workflow, "prepare-native", "prepare-installer");
    assert.match(native, /repository: voboost\/voboost-inject, ref: "\$\{\{ inputs\.inject_sha \}\}", path: inject, submodules: recursive, persist-credentials: false/u);
    assert.ok(native.includes('test "$(git rev-parse HEAD)" = "${{ inputs.inject_sha }}"'));
    assert.ok(native.includes('repository: voboost/voboost-codestyle, ref: "${{ inputs.codestyle_sha }}", path: voboost-codestyle, persist-credentials: false'));
    assert.ok(native.includes('CODESTYLE_TREE: "${{ needs.admission.outputs.codestyle_tree }}"'));
    for (const guard of [
        'test "$(git -C "$GITHUB_WORKSPACE/voboost-codestyle" rev-parse HEAD)" = "$CODESTYLE_SHA"',
        "test \"$(git -C \"$GITHUB_WORKSPACE/voboost-codestyle\" rev-parse 'HEAD^{tree}')\" = \"$CODESTYLE_TREE\"",
        'test -z "$(git -C "$GITHUB_WORKSPACE/voboost-codestyle" status --porcelain=v1)"',
    ]) {
        assert.ok(native.includes(guard), `missing codestyle identity guard: ${guard}`);
        assert.ok(native.indexOf(guard) < native.indexOf("          make init\n"));
    }

    const commands = ["make init", "make build-android build-selinux", "make test-tooling", "make lint", "make test", "make coverage", 'tools/prepare-beta1-unsigned.sh "$RUNNER_TEMP/prepared-native"'];
    let previous = -1;
    for (const command of commands) {
        const position = native.indexOf(`          ${command}\n`);
        // Android construction spans continued lines; its command begins here.
        const actual = command === "make build-android build-selinux" ? native.indexOf(`          ${command} \\`) : position;
        assert.ok(actual > previous, `native preparation must execute ${command} before publishing inputs`);
        previous = actual;
    }
    assert.match(native, /set -euo pipefail/u);
    assert.doesNotMatch(native, /continue-on-error: true|make (?:lint|test|coverage).*\|\|/u);
    const sign = job(workflow, "sign-native", "sign-product");
    assert.match(sign, /needs:.*prepare-native/u);
}

test("beta1 preparation requires exact-source owning native host gates before signing", () => {
    requireNativeHostGate(ci);
    for (const command of ["make init", "make test-tooling", "make lint", "make test", "make coverage"]) {
        assert.throws(() => requireNativeHostGate(ci.replace(`          ${command}\n`, "")), undefined, command);
    }
    assert.throws(() => requireNativeHostGate(ci.replaceAll('ref: "${{ inputs.inject_sha }}", path: inject, submodules: recursive', 'ref: main, path: inject, submodules: recursive')));
    for (const mutation of [
        ['repository: voboost/voboost-codestyle, ref: "${{ inputs.codestyle_sha }}"', 'repository: voboost/voboost-codestyle, ref: main'],
        ["rev-parse 'HEAD^{tree}'", "rev-parse HEAD"],
        ['status --porcelain=v1', 'status --short'],
        ['CODESTYLE_TREE: "${{ needs.admission.outputs.codestyle_tree }}"', 'CODESTYLE_TREE: untrusted'],
    ]) assert.throws(() => requireNativeHostGate(ci.replaceAll(...mutation)));

});

function requirePinnedConsumerTools(workflow: string): void {
    const blocks = workflow.split(/^  (?=[a-z][a-z-]*:\n)/mu).slice(1);
    for (const block of blocks) {
        for (const [command, action, version] of [
            ["cargo run", "dtolnay/rust-toolchain@4360b52568e2003a75bf9bc1d59f33a8e3fc893c", "toolchain: '1.98.0'"],
            ["node tools/", "actions/setup-node@a0853c24544627f65ddf259abe73b1d18a591444", "node-version: '24.13.0'"],
        ]) {
            if (!block.includes(command)) continue;
            assert.ok(block.includes(action) && block.includes(version), `missing pinned tool for ${command}`);
            assert.ok(block.indexOf(action) < block.indexOf(command), `setup must precede ${command}`);
        }
    }
}

test("evidence and publication consumers set pinned Rust and Node before execution", () => {
    for (const workflow of [evidenceWorkflow, publication]) {
        requirePinnedConsumerTools(workflow);
        assert.throws(() => requirePinnedConsumerTools(workflow.replaceAll("toolchain: '1.98.0'", "toolchain: stable")));
        assert.throws(() => requirePinnedConsumerTools(workflow.replaceAll("node-version: '24.13.0'", "node-version: latest")));
    }
});

test("aggregate verifies the complete Linux archive with the exact installer source", () => {
    const aggregate = job(ci, "package-aggregate");
    const checkout = 'repository: voboost/voboost-installer, ref: "${{ inputs.installer_sha }}", path: installer';
    const verifier = 'inspectSingleEntryTarBzip2("packages/voboost-installer-1.0.0-beta1.tar.bz2")';
    function requireArchiveGate(value: string): void {
        assert.ok(value.includes(checkout));
        assert.ok(value.includes("node-version: '24.13.0'"));
        assert.ok(value.includes('from "./installer/src/build/offline-package.ts"'));
        assert.ok(value.includes(verifier));
        assert.ok(value.indexOf(checkout) < value.indexOf(verifier));
        assert.ok(value.indexOf("node-version: '24.13.0'") < value.indexOf(verifier));
        assert.ok(!value.includes('test "$(tar -tjf'));
    }
    requireArchiveGate(aggregate);
    assert.throws(() => requireArchiveGate(aggregate.replace(checkout, "ref: main")));
    assert.throws(() => requireArchiveGate(aggregate.replace(verifier, "true")));
});


test("current license authority is product code, with no archive checkout or logo MIT requirement", () => {
 for (const file of [".github/workflows/beta1-ci.yml", ".github/workflows/beta1-build.yml", ".github/workflows/beta1-evidence-ingest.yml"]) {
  const source = readFileSync(join(root,file),"utf8");
  assert.doesNotMatch(source, /voboost\/voboost-license|inputs\.license_sha|outputs\.license/u);
 }
 assert.match(ci, /cp "\$file" "\$RUNNER_TEMP\/license-authority"/u);
 assert.match(ci, /installer:\$INSTALLER_SHA codestyle:\$CODESTYLE_SHA; do/u);
 assert.doesNotMatch(ci, /for name in app agents inject installer license/u);
 const build=readFileSync(join(root,"tools/release/build-closure.ts"),"utf8");
 assert.doesNotMatch(build, /\["license", repositoryDirectory/u);
 const schema=JSON.parse(readFileSync(join(root,"release/local-delivery-evidence.schema.json"),"utf8"));
 assert.deepEqual(schema.$defs.sources.required,["app","agents","inject","installer","logo"]);
});
