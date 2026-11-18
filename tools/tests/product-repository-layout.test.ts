import { execFileSync } from "node:child_process";
import assert from "node:assert/strict";
import { existsSync, readdirSync } from "node:fs";
import { test } from "node:test";

const requiredRoots = [
    ".github/workflows",
    "docs-dev",
    "docs-user",
    "openspec/specs",
    "release",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle/wrapper",
    "gradlew",
    "tests",
    "check-candidate",
];

const forbiddenComponentRoots = [
    "plans",
    "local-builds",
    ".claude",
    "app",
    "agents",
    "contracts",
    "docs",
    "reflect",
    "REMOVEME",
    "stubs",
    "ui",
    "ui-res",
];

const forbiddenBuildFiles = ["local.properties"];

test("product repository contains no component production source", () => {
    for (const path of requiredRoots) {
        assert.equal(existsSync(path), true, `missing product root: ${path}`);
    }
    const sources = execFileSync("git", ["ls-files", "--cached", "--others", "--exclude-standard", "-z"], { encoding: "utf8" }).split("\0").filter(Boolean);
    for (const path of [...forbiddenComponentRoots, ...forbiddenBuildFiles]) {
        assert.equal(sources.some(file => file === path || file.startsWith(path + "/")), false, `component-owned source remains: ${path}`);
    }
    assert.deepEqual(readdirSync("openspec/specs").sort(), [
        "ci-protected-workflows",
        "product-release",
        "product-verification",
    ]);
});

test("product repository contains no component publication workflow", () => {
    assert.deepEqual(readdirSync(".github/workflows").sort(), [
        "beta1-build.yml",
        "beta1-ci.yml",
        "beta1-evidence-ingest.yml",
        "beta1-publication.yml",
        "verify.yml",
    ]);
    for (const path of [
        "tools/agent-catalog-contract.ts",
        "tools/app-release-event-ledger.ts",
        "tools/app-release-evidence.ts",
        "tools/app-release-request.ts",
        "tools/channel-apk.ts",
        "tools/installer-release-event-tool.json",
        "tools/with-ephemeral-android-signing.ts",
    ]) {
        assert.equal(existsSync(path), false, `component publication tool remains: ${path}`);
    }
});
