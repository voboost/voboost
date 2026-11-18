import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import test from "node:test";

const root = resolve(import.meta.dirname, "../..");
const names = ["arm64.dmg", "x86_64.dmg"].map(suffix => `voboost-installer-1.0.0-beta1-${suffix}`)
  .concat(["voboost-installer-1.0.0-beta1.exe", "voboost-installer-1.0.0-beta1.tar.bz2"]);

for (const workflow of ["beta1-ci.yml", "beta1-publication.yml"]) {
  test(`${workflow} admits exactly both macOS architectures plus Windows and Linux`, () => {
    const source = readFileSync(join(root, ".github/workflows", workflow), "utf8");
    const directory = workflow === "beta1-ci.yml" ? "packages" : "$RUNNER_TEMP/packages-now";
    const lines = source.split("\n").filter(line => line.trimStart().startsWith('test "$(find ') && line.includes(directory));
    assert.equal(lines.length, 2);
    const work = join(root, "..", ".work162", "workflow-tests");
    mkdirSync(work, { recursive: true });
    const scratch = mkdtempSync(join(work, "inventory-"));
    const packages = join(scratch, workflow === "beta1-ci.yml" ? "packages" : "packages-now");
    mkdirSync(packages);
    const check = () => spawnSync("bash", ["-euc", lines.join("\n")], {
      cwd: scratch, env: { ...process.env, RUNNER_TEMP: scratch }, encoding: "utf8",
    });
    try {
      for (const name of names) writeFileSync(join(packages, name), "package fixture");
      assert.equal(check().status, 0);
      for (const name of names) {
        rmSync(join(packages, name));
        assert.notEqual(check().status, 0, `missing ${name} must be rejected`);
        writeFileSync(join(packages, name), "package fixture");
      }
      writeFileSync(join(packages, "voboost-installer-1.0.0-beta1.dmg"), "legacy fixture");
      assert.notEqual(check().status, 0, "extra unsuffixed DMG must be rejected");
      rmSync(join(packages, names[0]));
      assert.notEqual(check().status, 0, "same-size inventory with unsuffixed DMG must be rejected");
    } finally { rmSync(scratch, { recursive: true, force: true }); }
  });
}


test("legacy macOS jobs execute each thin package on its native architecture", () => {
  const source = readFileSync(join(root, ".github/workflows/beta1-ci.yml"), "utf8");
  const mac = source.split("\n  package-macos:\n")[1].split("\n  package-aggregate:\n")[0];
  assert.ok(mac.includes("{runner: macos-15, architecture: arm64}"));
  assert.ok(mac.includes("{runner: macos-15-intel, architecture: x86_64}"));
  assert.ok(mac.includes('test "$(uname -m)" = "$architecture"'));
  assert.ok(mac.includes('"$dmg" "$architecture"'));
  assert.ok(mac.includes('packaged-cli-contract.ts desktop "$cli_mount/voboost-installer"'));
  assert.ok(mac.includes('hdiutil verify "$dmg"'));
  for (const architecture of ["arm64", "x86_64"])
    assert.ok(source.includes(`name: beta1-package-macos-${architecture}, path: packages`));
  assert.ok(mac.includes('name: "beta1-package-macos-${{ matrix.architecture }}"'));
});

test("build config checks audited QNX interfaces without private OEM inputs", () => {
  const source = readFileSync(join(root, ".github/workflows/beta1-build.yml"), "utf8");
  const validation = source.indexOf("Validate audited QNX link interfaces before signing authority is materialized");
  assert.ok(validation > 0 && validation < source.indexOf("Admit and materialize CI-only beta signing authority"));
  const gate = source.slice(validation, source.indexOf("      - name:", validation + 1));
  assert.ok(gate.includes("(cd voboost-agents && ./gradlew verifyQnxLinkInterfaces --offline --no-daemon --console=plain)"));
  assert.doesNotMatch(gate, /python(?:3)?\b|test_link_interfaces\.py/u);
  assert.doesNotMatch(source, /qnx_libraries|QNX_LIBRARIES_JSON/u);
  assert.ok(source.includes("const config = parseBuildClosureConfig({"));
  assert.ok(source.includes("agentSet: 'release'"));
  assert.doesNotMatch(source, /qnx_libraries_artifact|qnx-libraries\.zip/u);
});
