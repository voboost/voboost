import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import test from "node:test";

const source = readFileSync(new URL("../../.github/workflows/beta1-ci.yml", import.meta.url), "utf8");

test("legacy Android-only pipeline stops before checkout, toolchains, signing or packaging", () => {
    const steps = source.split("    steps:\n")[1];
    assert.ok(steps);
    const first = steps.split(/\n      - /u)[0];
    assert.match(first, /name: Reject retired Android-only build entry point/u);
    assert.doesNotMatch(first, /\n\s+if:|continue-on-error:|uses:/u);
    const script = first.split("        run: |\n")[1];
    assert.ok(script);
    const result = spawnSync("/bin/bash", ["-c", script.replace(/^          /gmu, "")], { encoding: "utf8" });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /beta1-build\.yml/u);
    assert.match(result.stderr, /QNX/u);
});

test("legacy pipeline is visibly retired while its evidence admission remains preserved", () => {
    assert.match(source, /^name: Retired Android-only beta1 CI$/mu);
    assert.match(source, /node tools\/beta1-evidence\.ts local/u);
    assert.match(source, /name: Prove admission is secretless/u);
    assert.match(source, /local_evidence_signature_sha256:/u);
});
