import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const workflow = readFileSync(".github/workflows/verify.yml", "utf8");
const packageJson = JSON.parse(readFileSync("package.json", "utf8"));

function literal(value: string): RegExp {
    return new RegExp(value.replaceAll(/[\\^$.*+?()[\]{}|]/g, "\\$&"), "u");
}

test("ordinary CI verifies product sources without rebuilding components", () => {
    for (const required of [
        "cargo +1.98.0 fmt --manifest-path release/Cargo.toml -- --check",
        "cargo +1.98.0 clippy --manifest-path release/Cargo.toml --locked --all-targets -- -D warnings",
        "cargo +1.98.0 test --manifest-path release/Cargo.toml --locked",
        "verify::tests::pinned_android_tools_extract_identity_from_exact_signed_apk",
        "npm run check",
    ]) {
        assert.match(workflow, literal(required));
    }
    for (const forbidden of [
        "./gradlew",
        ":app:",
        ":agents:",
        ":stubs:",
        "dev:apk",
        "channel-apk",
        "upload-artifact",
        "dev-vithar",
    ]) {
        assert.doesNotMatch(workflow, literal(forbidden));
    }
});

test("ordinary CI executes the complete product tool suite", () => {
    assert.equal(
        packageJson.scripts.check,
        'node tools/validate-openspec-evidence.ts && node tools/check-target-docs.ts && node --test "tools/**/*.test.ts" && openspec validate --all --strict',
    );
    assert.match(workflow, /^\s*run: npm run check\s*$/mu);
    assert.doesNotMatch(
        packageJson.scripts.check,
        /(?:^|\s)(?:app|agents|reflect|ui|ui-res|stubs|docs)(?:\/|\s)/u,
    );
    assert.doesNotMatch(workflow, /node --test tools\/tests\/(?!\*\.test\.ts)[^\s]+\.test\.ts/u);
});

test("shared resource parser uses verified inject source at its explicit Cargo sibling path", () => {
    const manifest = readFileSync("release/Cargo.toml", "utf8");
    assert.match(manifest, literal('voboost-contract-core = { path = "../../voboost-inject/common/contracts" }'));
    const identity = workflow.indexOf("- name: Verify clean exact inject contract checkout");
    const linkage = workflow.indexOf("- name: Expose pinned shared contract at the required sibling path");
    const cargo = workflow.indexOf("cargo +1.98.0 fmt --manifest-path release/Cargo.toml");
    assert.ok(identity >= 0 && linkage > identity && cargo > linkage);
    const step = workflow.slice(linkage, workflow.indexOf("- name:", linkage + 8));
    for (const line of [
        'test -f "$GITHUB_WORKSPACE/.ci-inject-contract/common/contracts/Cargo.toml"',
        'test ! -e "$GITHUB_WORKSPACE/../voboost-inject"',
        'test ! -L "$GITHUB_WORKSPACE/../voboost-inject"',
        'ln -s "$GITHUB_WORKSPACE/.ci-inject-contract" "$GITHUB_WORKSPACE/../voboost-inject"',
        'test -f "$GITHUB_WORKSPACE/../voboost-inject/common/contracts/Cargo.toml"',
    ]) assert.match(step, literal(line));
    assert.doesNotMatch(step, /\bcp\b|\|\|\s*true|if\s+\[/u);
});
