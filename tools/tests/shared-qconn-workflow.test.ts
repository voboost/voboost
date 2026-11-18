import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const workflow = readFileSync(new URL("../../.github/workflows/beta1-ci.yml", import.meta.url), "utf8");
const compileJobs = ["prepare-installer", "package-windows", "package-linux", "package-macos"];

function admitSharedSource(source: string): void {
    for (const name of compileJobs) {
        const start = source.indexOf(`  ${name}:\n`);
        assert.ok(start >= 0, `missing ${name}`);
        const rest = source.slice(start + 3);
        const end = rest.search(/\n  [a-z][a-z-]*:\n/u);
        const job = end < 0 ? rest : rest.slice(0, end);
        assert.match(job, /repository: voboost\/voboost-inject\n          ref: "\$\{\{ inputs\.inject_sha \}\}"\n          path: voboost-inject\n          sparse-checkout: common\/qconn\n          persist-credentials: false/u);
        assert.match(job, /git -C voboost-inject rev-parse HEAD/u);
        assert.match(job, /git -C voboost-inject status --porcelain=v1/u);
        assert.match(job, /test -f voboost-inject\/common\/qconn\/Cargo.toml/u);
        const admission = job.indexOf("name: Admit exact shared qconn source");
        const build = job.search(/name: (Verify installer source|Build and reopen|Build reopen|Build exact)/u);
        assert.ok(admission >= 0 && (build < 0 || admission < build), `${name}: dependency admitted too late`);
    }
}

test("every installer compilation job admits the exact canonical shared qconn sibling", () => {
    admitSharedSource(workflow);
});

test("missing or relocated shared source is rejected before installer compilation", () => {
    assert.throws(() => admitSharedSource(workflow.replace("sparse-checkout: common/qconn", "sparse-checkout: common/contracts")));
    assert.throws(() => admitSharedSource(workflow.replace("path: voboost-inject", "path: inject-qconn")));
    assert.throws(() => admitSharedSource(workflow.replace("test -f voboost-inject/common/qconn/Cargo.toml", "true")));
});

test("standalone installer verification uses existing pinned source contract without floating fallback", () => {
    const standalone = readFileSync(new URL("../../../voboost-installer/.github/workflows/build.yml", import.meta.url), "utf8");
    assert.match(standalone, /working-directory: installer/u);
    assert.match(standalone, /ci\/emulator-inputs\.json/u);
    assert.match(standalone, /test "\$sha" != 0000000000000000000000000000000000000000/u);
    assert.match(standalone, /ref: \$\{\{ steps\.qconn-source\.outputs\.sha \}\}/u);
    assert.match(standalone, /path: voboost-inject\n          sparse-checkout: common\/qconn/u);
    assert.match(standalone, /test -f \.\.\/voboost-inject\/common\/qconn\/Cargo.toml/u);
    assert.doesNotMatch(standalone, /ref: (main|master)|origin\/main/u);
});

function admitBuildPackaging(source: string): void {
    const packaging = source.split("\n  package:\n")[1];
    assert.ok(packaging, "missing build-only packaging job");
    assert.match(packaging, /repository: voboost\/voboost-inject\n          ref: "\$\{\{ needs\.sources\.outputs\.inject \}\}"\n/u);
    assert.match(packaging, /path: voboost-inject\n          sparse-checkout: \|\n            common\/contracts\n            common\/qconn\n            contract\n            shared\n/u);
    assert.match(packaging, /git -C voboost-inject rev-parse HEAD/u);
    for (const relative of ["common/contracts", "common/qconn", "contract", "shared"])
        assert.ok(packaging.includes(`test -f voboost-inject/${relative}/Cargo.toml`));
    const admission = packaging.indexOf("name: Admit exact shared host sources");
    assert.ok(admission >= 0 && admission < packaging.indexOf("name: Package and independently reopen"));
}

test("build-only packaging admits every current shared host crate for every platform", () => {
    const build = readFileSync(new URL("../../.github/workflows/beta1-build.yml", import.meta.url), "utf8");
    admitBuildPackaging(build);
    for (const relative of ["common/contracts", "common/qconn", "contract", "shared"]) {
        assert.throws(() => admitBuildPackaging(build.replace(`            ${relative}\n`, "")));
        assert.throws(() => admitBuildPackaging(build.replace(`test -f voboost-inject/${relative}/Cargo.toml`, "true")));
    }
    assert.throws(() => admitBuildPackaging(build.replace("path: voboost-inject\n          sparse-checkout", "path: inject-qconn\n          sparse-checkout")));
});

test("QNX offline compiler inputs are provisioned before private signing authority", () => {
    const build = readFileSync(new URL("../../.github/workflows/beta1-build.yml", import.meta.url), "utf8");
    assert.match(build, /toolchain: '1\.98\.0', targets: aarch64-linux-android, components: rust-src/u);
    const fetch = "RUSTC_BOOTSTRAP=1 cargo +1.98.0 fetch --locked --manifest-path voboost-inject/Cargo.toml --target aarch64-unknown-nto-qnx700 -Zbuild-std=core,alloc";
    assert.ok(build.includes(fetch));
    assert.ok(build.indexOf(fetch) < build.indexOf("name: Admit and materialize CI-only beta signing authority"));
});
