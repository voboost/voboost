import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";
import { validateTargetDocs } from "../docs-target-structure.ts";
import {
    checkAgentStatus,
    generateAgentStatus,
    generateEnglishAgentStatus,
    validateReleaseAgentsCheckout,
} from "../generate-agent-status.ts";

test("target user and developer roots have paired documents and valid links", () => {
    assert.deepEqual(validateTargetDocs(), []);
    assert.equal(checkAgentStatus(), true, "run node tools/generate-agent-status.ts");
});

test("developer docs use the split app and agent repository owners", () => {
    const corpus = readdirSync("docs-dev")
        .filter((name) => name.endsWith(".md"))
        .map((name) => readFileSync(`docs-dev/${name}`, "utf8"))
        .join("\n");
    for (const legacyOwner of [
        "`voboost/app`",
        "`voboost/agents`",
        "`app/src",
        "`agents/src",
        "`agents/build.gradle.kts`",
        "`openspec/specs/app-uninstall/spec.md`",
        "`openspec/specs/app-apk-install/spec.md`",
    ]) {
        assert.equal(corpus.includes(legacyOwner), false, legacyOwner);
    }
});

test("release docs require an explicit clean exact agents checkout", () => {
    const base = mkdtempSync(join(tmpdir(), "voboost-docs-agents-"));
    const root = join(base, "agents");
    const expected = "a".repeat(40);
    try {
        assert.throws(() => validateReleaseAgentsCheckout(root, expected, () => ({ head: expected, status: "" })), /absent/u);
        mkdirSync(join(root, "src/main/java/ru/voboost/agents"), { recursive: true });
        mkdirSync(join(root, "openspec/specs"), { recursive: true });
        assert.equal(validateReleaseAgentsCheckout(root, expected, () => ({ head: expected, status: "" })), root);
        assert.throws(() => validateReleaseAgentsCheckout(root, expected, () => ({ head: "b".repeat(40), status: "" })), /differs/u);
        assert.throws(() => validateReleaseAgentsCheckout(root, expected, () => ({ head: expected, status: "?? changed" })), /clean/u);
        assert.throws(() => validateReleaseAgentsCheckout("relative", expected, () => ({ head: expected, status: "" })), /absolute/u);
        assert.throws(() => validateReleaseAgentsCheckout(root, "short", () => ({ head: expected, status: "" })), /full lowercase commit/u);
    } finally {
        rmSync(base, { recursive: true, force: true });
    }
});

test("agent status output is independent of the absolute checkout path", () => {
    const base = mkdtempSync(join(tmpdir(), "voboost-docs-path-"));
    const productRoot = resolve(".");
    const canonicalAgentsRoot = resolve("../voboost-agents");
    const noncanonicalAgentsRoot = join(base, ".ci-agents-metadata");
    try {
        symlinkSync(canonicalAgentsRoot, noncanonicalAgentsRoot, "dir");
        assert.equal(
            generateAgentStatus(productRoot, noncanonicalAgentsRoot),
            generateAgentStatus(productRoot, canonicalAgentsRoot),
        );
        assert.equal(
            generateEnglishAgentStatus(productRoot, noncanonicalAgentsRoot),
            generateEnglishAgentStatus(productRoot, canonicalAgentsRoot),
        );
    } finally {
        rmSync(base, { recursive: true, force: true });
    }
});
