import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import { validateOpenSpec } from "../validate-openspec-evidence.ts";

function fixture(evidence: string): { root: string; specs: string } {
    const root = mkdtempSync(join(tmpdir(), "voboost-openspec-evidence-"));
    const specs = join(root, "openspec", "specs");
    for (const capability of [
        "ci-protected-workflows",
        "product-release",
        "product-verification",
    ]) {
        const directory = join(specs, capability);
        mkdirSync(directory, { recursive: true });
        writeFileSync(join(directory, "spec.md"), `# ${capability}\n\n## Requirements\n\n### Requirement: Requirement ${capability}\n\nThe product SHALL behave.\n\n#### Scenario: Scenario ${capability}\n\n- **WHEN** invoked\n- **THEN** it behaves\n\n${evidence.replaceAll("CAPABILITY", capability)}\n`);
    }
    return { root, specs };
}

const validGate = "# Gate: node --test --test-name-pattern=exact tools/tests/example.test.ts\n\nGate contract: Maps: Requirement `Requirement CAPABILITY`; Scenario `Scenario CAPABILITY`. Preconditions: Host fixture exists. Observable result: The named test observes the result. Evidence boundary: Host-only behavior.";

test("accepts one named emulator-module test without admitting wildcard selectors", () => {
    const value = fixture(validGate.replace("tools/tests/", "tools/emulator/"));
    try {
        mkdirSync(join(value.root, "tools", "emulator"), { recursive: true });
        writeFileSync(join(value.root, "tools", "emulator", "example.test.ts"), "");
        assert.deepEqual(validateOpenSpec(value.specs), []);
        for (const capability of ["ci-protected-workflows", "product-release", "product-verification"]) {
            writeFileSync(join(value.specs, capability, "spec.md"), `# ${capability}\n\n## Requirements\n\n### Requirement: Requirement ${capability}\n\nThe product SHALL behave.\n\n#### Scenario: Scenario ${capability}\n\n- **WHEN** invoked\n- **THEN** it behaves\n\n${validGate.replaceAll("CAPABILITY", capability).replace("tools/tests/example.test.ts", "tools/emulator/*.test.ts")}\n`);
        }
        assert.ok(validateOpenSpec(value.specs).some(error => error.includes("Node Gate must select one named test")));
    } finally {
        rmSync(value.root, { recursive: true, force: true });
    }
});

test("accepts exact named Gates with complete contracts and coverage", () => {
    const value = fixture(validGate);
    try {
        mkdirSync(join(value.root, "tools", "tests"), { recursive: true });
        writeFileSync(join(value.root, "tools", "tests", "example.test.ts"), "");
        assert.deepEqual(validateOpenSpec(value.specs), []);
    } finally {
        rmSync(value.root, { recursive: true, force: true });
    }
});

test("accepts one exact Rust Gate through the flat release crate", () => {
    const cargoGate = "# Gate: cargo test --manifest-path release/Cargo.toml module::tests::exact -- --exact\n\nGate contract: Maps: Requirement `Requirement CAPABILITY`; Scenario `Scenario CAPABILITY`. Preconditions: Host fixture exists. Observable result: The named test observes the result. Evidence boundary: Host-only behavior.";
    const value = fixture(cargoGate);
    try {
        mkdirSync(join(value.root, "release"), { recursive: true });
        writeFileSync(join(value.root, "release", "Cargo.toml"), "");
        assert.deepEqual(validateOpenSpec(value.specs), []);
    } finally {
        rmSync(value.root, { recursive: true, force: true });
    }
});

test("rejects broad suites and incomplete Gate contracts", () => {
    const value = fixture("# Gate: ./gradlew :app:testUnit\n\nGate contract: Maps: Requirement `Requirement CAPABILITY`. Preconditions: Host. Observable result: Result. Evidence boundary: Host.");
    try {
        const errors = validateOpenSpec(value.specs).join("\n");
        assert.match(errors, /exact FQCN\.method selector/u);
        assert.match(errors, /names no Scenario/u);
    } finally {
        rmSync(value.root, { recursive: true, force: true });
    }
});

test("requires scoped Unverified explanations", () => {
    const value = fixture("# Unverified: Device behavior\n\n## Unverified\n\n### Unverified: Device behavior\n\nTests cannot observe it.");
    try {
        const errors = validateOpenSpec(value.specs).join("\n");
        assert.match(errors, /lacks Maps:/u);
        assert.match(errors, /lacks Unavailable observation:/u);
        assert.match(errors, /lacks Closure evidence:/u);
    } finally {
        rmSync(value.root, { recursive: true, force: true });
    }
});

test("rejects an empty Unverified marker", () => {
    const value = fixture("# Unverified:\n\n## Unverified\n\n### Unverified: Device behavior\n\nMaps: Requirement `Requirement CAPABILITY`; Scenario `Scenario CAPABILITY`.\n\nUnavailable observation: Device execution.\n\nWhy unavailable: No target device is attached.\n\nClosure evidence: A recorded target-device run.");
    try {
        assert.match(validateOpenSpec(value.specs).join("\n"), /empty Unverified marker is forbidden/u);
    } finally {
        rmSync(value.root, { recursive: true, force: true });
    }
});

test("accepts one named release-module test without admitting wildcard selectors", () => {
    const value = fixture(validGate.replace("tools/tests/", "tools/release/"));
    try {
        mkdirSync(join(value.root, "tools", "release"), { recursive: true });
        writeFileSync(join(value.root, "tools", "release", "example.test.ts"), "");
        assert.deepEqual(validateOpenSpec(value.specs), []);
        for (const capability of ["ci-protected-workflows", "product-release", "product-verification"]) {
            writeFileSync(join(value.specs, capability, "spec.md"), `# ${capability}\n\n## Requirements\n\n### Requirement: Requirement ${capability}\n\nThe product SHALL behave.\n\n#### Scenario: Scenario ${capability}\n\n- **WHEN** invoked\n- **THEN** it behaves\n\n${validGate.replaceAll("CAPABILITY", capability).replace("tools/tests/example.test.ts", "tools/release/*.test.ts")}\n`);
        }
        assert.ok(validateOpenSpec(value.specs).some(error => error.includes("Node Gate must select one named test")));
    } finally {
        rmSync(value.root, { recursive: true, force: true });
    }
});


test("release Gates reject wildcard broad missing escaped and foreign-owner paths", () => {
    for (const command of [
        "node --test --test-name-pattern=exact tools/release/*.test.ts",
        "node --test tools/release/example.test.ts",
        "node --test --test-name-pattern=exact tools/release/missing.test.ts",
        "node --test --test-name-pattern=exact ../voboost-installer/tests/example.test.ts",
        "node --test --test-name-pattern=exact tools/foreign/example.test.ts",
    ]) {
        const value = fixture(validGate.replace("node --test --test-name-pattern=exact tools/tests/example.test.ts", command));
        try {
            mkdirSync(join(value.root, "tools", "release"), { recursive: true });
            writeFileSync(join(value.root, "tools", "release", "example.test.ts"), "");
            assert.notDeepEqual(validateOpenSpec(value.specs), [], command);
        } finally {
            rmSync(value.root, { recursive: true, force: true });
        }
    }
});
