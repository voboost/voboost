import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

test("the product owns one byte-identical policy sidecar fixture for native and installer parsers", () => {
    const fixture = readFileSync(new URL("../../testdata/policy-sidecars.json", import.meta.url));
    for (const peer of ["../../../voboost-inject/test/fixtures/policy-sidecars.json", "../../../voboost-installer/src-tauri/tests/fixtures/policy-sidecars.json"]) {
        assert.deepEqual(readFileSync(new URL(peer, import.meta.url)), fixture);
    }
    const body = JSON.parse(fixture.toString());
    assert.equal(body.schemaVersion, 1);
    assert.equal(body.sidecars.length, 1);
    assert.equal(body.sidecars[0].path, "/vendor/etc/selinux/precompiled_sepolicy.plat_and_mapping.sha256");
});
