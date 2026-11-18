import { createHash, createPrivateKey, createPublicKey } from "node:crypto";
import { spawnSync } from "node:child_process";
import assert from "node:assert/strict";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { decodeSecret, materializeAuthority, ROLES, sourcePins, validatePublicRoots } from "./ci-build.ts";

const definition = readFileSync("release/1.0.0-beta1.toml", "utf8");
test("build pins come only from the version definition plus explicit codestyle commit", () => {
    const pins = sourcePins(definition, "a".repeat(40));
    assert.equal(Object.keys(pins).length, 6);
    assert.equal(pins.license, undefined);
    assert.equal(pins.codestyle, "a".repeat(40));
    assert.throws(() => sourcePins(definition, "main"), /source pin/u);
    assert.throws(() => sourcePins(definition.replace('release = "1.0.0-beta1"', 'release = "2.0.0"'), "a".repeat(40)), /source definition/u);
    assert.throws(() => sourcePins(definition.replace(/^app = .+$/mu, 'app = "main"'), "a".repeat(40)), /source pin: app/u);
});
test("private authority import refuses malformed, empty or wrong-size secrets", () => {
    for (const value of [undefined, "", "garbage!", "YQ=="]) assert.throws(() => decodeSecret(value, "seed", 32));
    assert.equal(decodeSecret(Buffer.alloc(32, 7).toString("base64"), "seed", 32).length, 32);
});
test("authority import protects keys and derives only matching public roots", () => {
    mkdirSync("build", { recursive: true });
    const root = mkdtempSync("build/ci-authority-test-");
    try {
        const jks = join(root, "fixture.jks");
        const generated = spawnSync("/usr/bin/keytool", ["-genkeypair", "-alias", "voboost", "-keyalg", "EC", "-groupname", "secp256r1", "-keystore", jks, "-storepass", "changeit", "-keypass", "changeit", "-dname", "CN=CI test fixture", "-validity", "1"]);
        assert.equal(generated.status, 0, "test Android keystore generation failed");
        const exported = spawnSync("/usr/bin/keytool", ["-exportcert", "-keystore", jks, "-storepass", "changeit", "-alias", "voboost"]);
        assert.equal(exported.status, 0);
        const environment: NodeJS.ProcessEnv = { APP_KEYSTORE: readFileSync(jks).toString("base64") };
        for (const [index, role] of ROLES.entries()) environment[`KEY_${role.replaceAll("-", "_").toUpperCase()}`] = Buffer.alloc(32, index + 1).toString("base64");
        const expected = { schemaVersion: 1 as const, roots: {} as Record<(typeof ROLES)[number], string>, androidCertificateSha256: createHash("sha256").update(exported.stdout).digest("hex") };
        for (const [index, role] of ROLES.entries()) expected.roots[role] = (createPublicKey(createPrivateKey({ key: Buffer.concat([Buffer.from("302e020100300506032b657004220420", "hex"), Buffer.alloc(32, index + 1)]), format: "der", type: "pkcs8" })).export({ format: "der", type: "spki" }) as Buffer).subarray(-32).toString("hex");
        assert.deepEqual(validatePublicRoots(expected), expected);
        assert.throws(() => validatePublicRoots({ ...expected, schemaVersion: 2 }));
        const swapped = structuredClone(expected);
        [swapped.roots.product, swapped.roots.restore] = [swapped.roots.restore, swapped.roots.product];
        assert.throws(() => materializeAuthority(join(root, "swapped"), environment, swapped), /public root mismatch/u);
        assert.equal(existsSync(join(root, "swapped")), false);
        assert.throws(() => materializeAuthority(join(root, "wrong-certificate"), environment, { ...expected, androidCertificateSha256: "0".repeat(64) }), /certificate mismatch/u);
        assert.equal(existsSync(join(root, "wrong-certificate")), false);
        const authority = join(root, "authority");
        materializeAuthority(authority, environment, expected);
        assert.equal(statSync(authority).mode & 0o777, 0o700);
        assert.equal(readdirSync(authority).length, 13);
        for (const role of ROLES) {
            assert.equal(statSync(join(authority, `${role}.key`)).mode & 0o777, 0o600);
            assert.equal(readFileSync(join(authority, `${role}.pub`)).length, 32);
            assert.match(readFileSync(join(authority, `${role}.pem`), "utf8"), /BEGIN PUBLIC KEY/u);
        }
        assert.equal(statSync(join(authority, "app.jks")).mode & 0o777, 0o600);
        assert.throws(() => materializeAuthority(authority, environment, expected));
        delete environment.APP_KEYSTORE;
        assert.throws(() => materializeAuthority(join(root, "incomplete"), environment, expected));
        assert.equal(existsSync(join(root, "incomplete")), false);
    } finally { rmSync(root, { recursive: true }); }
});
test("build-only workflow isolates signing from three secretless packaging jobs", () => {
    const workflow = readFileSync(".github/workflows/beta1-build.yml", "utf8");
    assert.match(workflow, /environment: beta1-build/u);
    for (const match of workflow.matchAll(/uses: ([^\n]+)/gu)) assert.match(match[1], /^[A-Za-z0-9_-]+\/[A-Za-z0-9_-]+@[0-9a-f]{40}$/u);
    assert.match(workflow, /permissions: \{contents: read\}/u);
    assert.match(workflow, /sourceMode: 'exact-source'/u);
    assert.match(workflow, /profile: 'machine'/u);
    assert.match(workflow, /if: always\(\)[\s\S]*?rm -rf "\$GITHUB_WORKSPACE\/\.ci\/authority"/u);
    const packaging = workflow.split("\n  package:\n")[1];
    for (const platform of ["macos", "windows", "linux"]) assert.match(packaging, new RegExp(`platform: ${platform}`));
    assert.doesNotMatch(packaging, /SIGNING_KEY|KEYSTORE|environment: beta1-build/u);
    assert.doesNotMatch(workflow, /contents: write|gh release|local_evidence_artifact_id|check-trust-roots|voyemu|vehicle.*gate/u);
    const uploads = [...workflow.matchAll(/path: \|\n([\s\S]*?)          if-no-files-found/gu)].map((match) => match[1]).join("");
    assert.doesNotMatch(uploads, /authority|\.key|\.jks|\.ci\//u);
    for (const required of ["closure/", "public-inputs/", "receipt.json"]) assert.ok(uploads.includes(required));
});

test("CI verifies internal macOS source inputs without exporting a source companion", () => {
    const workflow = readFileSync(".github/workflows/beta1-build.yml", "utf8");
    const legacy = readFileSync(".github/workflows/beta1-ci.yml", "utf8").split("\n  package-macos:\n")[1].split("\n  package-aggregate:\n")[0];
    for (const name of ["product", "app", "agents", "inject", "installer", "logo", "codestyle"]) {
        assert.ok(legacy.includes(`inputs.${name}_sha`));
        assert.ok(legacy.includes(`path: ${name}, submodules: recursive`));
    }
    assert.doesNotMatch(legacy, /corresponding-source\.ts create/u);
    assert.match(legacy, /offline-package\.ts build-mac-dmg[^\n]+"\$dmg" "\$architecture"/u);
    for (const source of [workflow, legacy]) {
        assert.doesNotMatch(source, /name: beta1-corresponding-source/u);
    }
});

test("local macOS output keeps source archives in internal build inputs", () => {
    mkdirSync("build", { recursive: true });
    const root = mkdtempSync("build/local-source-companion-test-");
    try {
        const work = join(root, "work");
        const output = join(root, "output");
        const sourceFiles = ["corresponding-source.tar.gz", "corresponding-source.json", "BUILD-MODIFIED.md"];
        mkdirSync(join(work, "closure"), { recursive: true });
        mkdirSync(join(work, "corresponding-source"));
        writeFileSync(join(work, "closure", "voboost.json"), "manifest fixture");
        const dmg = join(work, "voboost-installer-1.0.0-beta1.dmg");
        writeFileSync(dmg, "immutable DMG fixture");
        for (const file of sourceFiles) writeFileSync(join(work, "corresponding-source", file), `exact ${file} bytes`);
        const constructor = readFileSync("release/build-local-beta1.sh", "utf8");
        const start = constructor.indexOf('mkdir "$output"\n');
        const end = constructor.indexOf('cp "$work/snapshot-before.tsv"');
        assert.ok(start >= 0 && end > start, "local constructor export block must remain identifiable");
        const result = spawnSync("bash", ["-euo", "pipefail", "-c", constructor.slice(start, end)], {
            encoding: "utf8", env: { ...process.env, work, output, dmg, product_version: "1.0.0-beta1" },
        });
        assert.equal(result.status, 0, result.stderr);
        assert.deepEqual(readdirSync(output).sort(), ["closure", "voboost-installer-1.0.0-beta1.dmg"]);
        for (const file of sourceFiles) {
            assert.equal(existsSync(join(output, file)), false);
            assert.equal(readFileSync(join(work, "corresponding-source", file), "utf8"), `exact ${file} bytes`);
        }
        assert.equal(readFileSync(join(output, "voboost-installer-1.0.0-beta1.dmg"), "utf8"), "immutable DMG fixture");
    } finally { rmSync(root, { recursive: true, force: true }); }
});
