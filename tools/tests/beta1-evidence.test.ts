import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import test from "node:test";
import { buildArtifactSet, validateCiVehicleEvidence, validateLocalEvidence, type CiExpected, type LocalExpected } from "../beta1-evidence.ts";

function tmpdir(): string { const directory=join(import.meta.dirname,"../../../.work162"); mkdirSync(directory,{recursive:true}); return directory; }

const sha = (value: string): string => value.repeat(64).slice(0, 64);
const commit = (value: string): string => value.repeat(40).slice(0, 40);
const digest = (value: unknown): string => createHash("sha256").update(`${JSON.stringify(value)}\n`).digest("hex");
const localSet = { schemaVersion: 1, artifacts: [{ component: "app", file: "voboost-app.apk", size: 7, sha256: sha("a") }] };
const ciSet = { schemaVersion: 1, artifacts: [{ component: "installer", file: "voboost-installer-1.0.0-beta1.dmg", size: 9, sha256: sha("b") }] };

function receipts(set: typeof localSet, count: number): Array<Record<string, unknown>> {
    const artifactSetSha256 = digest(set);
    let previousReceiptSha256 = "0".repeat(64);
    return Array.from({ length: count }, (_, index) => {
        const body: Record<string, unknown> = { status: "passed", completedAt: `2026-09-06T0${index + 3}:00:00Z`, nonce: String(index + 1).repeat(64), sequence: index + 1, artifactSetSha256, previousReceiptSha256 };
        if (index === (count === 4 ? 1 : 0)) {
            body.vehicleIdentitySha256 = sha("c");
            body.firmware = "free-09-chinese-6.0.3";
        }
        const row = { ...body, receiptSha256: digest(body) };
        previousReceiptSha256 = String(row.receiptSha256);
        return row;
    });
}

const localExpected: LocalExpected = {
    producer: { workflowCommitSha: commit("1"), workflowBlobSha: commit("2"), runId: "10", runAttempt: "1" },
    product: { commit: commit("3"), tree: commit("4"), workflowBlobSha: commit("5"), sourceDefinitionSha256: sha("d"), trustDocumentSha256: sha("e") },
    sources: Object.fromEntries(["app", "agents", "inject", "installer", "license", "logo"].map((name, index) => [name, { commit: commit(String(index + 4)), tree: commit(String(index + 5)) }])) as LocalExpected["sources"],
};

function localEvidence(): Record<string, unknown> {
    const [localBuild, vehicle, uninstall, cleanState] = receipts(localSet, 4);
    return {
        schemaVersion: 1,
        releaseIdentity: "1.0.0-beta1",
        producer: { repository: "voboost/voboost", workflowPath: ".github/workflows/beta1-evidence-ingest.yml", ...localExpected.producer, ref: "refs/heads/main", job: "ingest", artifactName: "beta1-local-delivery-evidence" },
        product: { ...localExpected.product },
        sources: structuredClone(localExpected.sources),
        artifactSet: structuredClone(localSet), artifactSetSha256: digest(localSet),
        localBuild, vehicle, uninstall, cleanState,
    };
}

const ciExpected: CiExpected = {
    producer: { workflowCommitSha: commit("1"), workflowBlobSha: commit("2"), runId: "20", runAttempt: "1" },
    product: { commit: commit("3"), workflowBlobSha: commit("5"), sourceDefinitionSha256: sha("d"), trustDocumentSha256: sha("e") },
    ci: {
        runId: "30", runAttempt: "2",
        finalClosure: { id: "40", digest: `sha256:${sha("e")}`, payloadSha256: sha("f") },
        installerPackages: { id: "41", digest: `sha256:${sha("1")}`, payloadSha256: sha("2") },
    },
    artifactSet: ciSet,
};

function ciEvidence(): Record<string, unknown> {
    const [vehicle, uninstall, cleanState] = receipts(ciSet, 3);
    return {
        schemaVersion: 1,
        releaseIdentity: "1.0.0-beta1",
        producer: { repository: "voboost/voboost", workflowPath: ".github/workflows/beta1-evidence-ingest.yml", ...ciExpected.producer, ref: "refs/heads/main", job: "ingest", artifactName: "beta1-ci-vehicle-evidence" },
        product: { ...ciExpected.product }, ci: structuredClone(ciExpected.ci), artifactSet: structuredClone(ciSet), artifactSetSha256: digest(ciSet),
        vehicle, uninstall, cleanState,
    };
}

test("local evidence rejects forged producer, changed trees, failed receipts, and extra fields", () => {
    validateLocalEvidence(localEvidence(), localExpected);
    for (const mutate of [
        (value: any) => { value.producer.workflowPath = ".github/workflows/other.yml"; },
        (value: any) => { value.producer.workflowBlobSha = commit("f"); },
        (value: any) => { value.product.tree = commit("f"); },
        (value: any) => { value.sources.app.tree = commit("f"); },
        (value: any) => { value.artifactSet.artifacts[0].sha256 = sha("f"); },
        (value: any) => { value.vehicle.artifactSetSha256 = sha("f"); },
        (value: any) => { value.uninstall.previousReceiptSha256 = sha("f"); },
        (value: any) => { value.cleanState.receiptSha256 = sha("f"); },
        (value: any) => { value.vehicle.status = "failed"; },
        (value: any) => { value.cleanState.extra = true; },
    ]) {
        const value = localEvidence();
        mutate(value);
        assert.throws(() => validateLocalEvidence(value, localExpected));
    }
});

test("CI vehicle evidence rejects foreign producer, artifact, product, and clean-state substitutions", () => {
    validateCiVehicleEvidence(ciEvidence(), ciExpected);
    for (const mutate of [
        (value: any) => { value.producer.job = "untrusted"; },
        (value: any) => { value.product.commit = commit("f"); },
        (value: any) => { value.ci.runId = "31"; },
        (value: any) => { value.ci.finalClosure.id = "99"; },
        (value: any) => { value.ci.installerPackages.digest = `sha256:${sha("f")}`; },
        (value: any) => { value.artifactSet.artifacts[0].size = 10; },
        (value: any) => { value.artifactSet.artifacts[0].size = 10; value.artifactSetSha256 = digest(value.artifactSet); },
        (value: any) => { value.cleanState.artifactSetSha256 = sha("f"); },
        (value: any) => { value.uninstall.status = "failed"; },
    ]) {
        const value = ciEvidence();
        mutate(value);
        assert.throws(() => validateCiVehicleEvidence(value, ciExpected));
    }
});


test("current local evidence does not require archived licensing source metadata", () => {
 const current=structuredClone(localExpected);
 delete current.sources.license;
 const value=localEvidence() as any;
 delete value.sources.license;
 validateLocalEvidence(value,current);
});

test("inventory includes cluster producers and both thin macOS packages", () => {
    const root = mkdtempSync(join(tmpdir(), "beta1-inventory-"));
    try {
        const closure = join(root, "closure"), packages = join(root, "packages");
        mkdirSync(closure); mkdirSync(packages);
        for (const name of ["voboost-daemon", "voboost-inject", "voboost-cluster-daemon", "voboost-cluster-startup.sh", "libvoboost-cluster-inject.so", "libvoboost-runtime-cluster.so", "libagent-font-cyrillic.so"]) writeFileSync(join(closure, name), name);
        for (const suffix of ["-arm64.dmg", "-x86_64.dmg", ".exe", ".tar.bz2"]) writeFileSync(join(packages, "voboost-installer-1.0.0-beta1" + suffix), suffix);
        const result = buildArtifactSet(closure, packages);
        assert.equal(result.artifacts.length, 11);
        assert.equal(result.artifacts.filter(row => row.component === "inject").length, 5);
        assert.equal(result.artifacts.filter(row => row.component === "agents").length, 2);
        assert.equal(result.artifacts.filter(row => row.component === "installer").length, 4);
        writeFileSync(join(closure, "unowned.so"), "unexpected");
        assert.throws(() => buildArtifactSet(closure, packages), /unknown final closure file/);
    } finally { rmSync(root, { recursive: true, force: true }); }
});

test("inventory admits only product-declared resource pairs owned by the catalog", () => {
    const root = mkdtempSync(join(tmpdir(), "beta1-resource-inventory-"));
    try {
        const closure = join(root, "closure"), packages = join(root, "packages");
        mkdirSync(closure); mkdirSync(packages);
        const apk = "resource-i18n-ru-setting-a9.apk", ledger = apk.replace(/\.apk$/u, ".json");
        const row = (role: string, file: string) => ({ role, file, activation: "agent-generation", size: file.length, sha256: createHash("sha256").update(file).digest("hex") });
        writeFileSync(join(closure, apk), apk); writeFileSync(join(closure, ledger), ledger);
        assert.throws(() => buildArtifactSet(closure, packages), /unknown final closure file/);
        const descriptor = { owner: "i18n-ru", locale: "ru", targetPackage: "com.qinggan.app.setting", compatibilitySchema: 1, overlayPackage: "ru.voboost.translation.setting", coverageLedger: row("resource-ledger", ledger) };
        const manifest = { artifacts: [{ ...row("resource-apk", apk), packageName: descriptor.overlayPackage, resource: descriptor }, row("resource-ledger", ledger)] };
        const save = (value: unknown) => writeFileSync(join(closure, "voboost.json"), JSON.stringify(value));
        writeFileSync(join(closure, "agents.json"), JSON.stringify({ agents: [{ id: "i18n-ru" }] })); save(manifest);
        assert.equal(buildArtifactSet(closure, packages).artifacts.filter(row => row.file.startsWith("resource-") && row.component === "agents").length, 2);
        for (const mutate of [
            (value: any) => { value.artifacts[0].resource.owner = "foreign"; },
            (value: any) => { value.artifacts[0].resource.coverageLedger.sha256 = sha("a"); },
            (value: any) => { value.artifacts[0].activation = "root-flat"; },
            (value: any) => { value.artifacts[1].role = "foreign"; },
            (value: any) => { value.artifacts.push(value.artifacts[1]); },
            (value: any) => { value.artifacts[0].sha256 = sha("b"); },
        ]) { const value = structuredClone(manifest); mutate(value); save(value); assert.throws(() => buildArtifactSet(closure, packages)); }
        save(manifest); writeFileSync(join(closure, "agents.json"), JSON.stringify({ agents: [] }));
        assert.throws(() => buildArtifactSet(closure, packages), /owner/);
        writeFileSync(join(closure, "agents.json"), JSON.stringify({ agents: [{ id: "i18n-ru" }] }));
        writeFileSync(join(closure, "resource-i18n-ru-unlisted.apk"), "unlisted");
        assert.throws(() => buildArtifactSet(closure, packages), /unknown final closure file/);
    } finally { rmSync(root, { recursive: true, force: true }); }
});

test("inventory binds indexed and inline inert QNX locale pairs to the signed catalog native owner", () => {
    const root = mkdtempSync(join(tmpdir(), "beta1-qnx-resource-inventory-"));
    try {
        const closure = join(root, "closure"), packages = join(root, "packages"); mkdirSync(closure); mkdirSync(packages);
        const pack = "qnx-resource-i18n-ru-a9-gauge.kzb", ledgerFile = pack.replace(/\.kzb$/u, ".json"), moduleFile = "libagent-i18n-ru.so";
        const ledger = { schemaVersion: 1, owner: "i18n-ru", targetProcess: "gauge", compatibilitySchema: 1, format: "KZBF-v3-indexed", locale: "voboost-ru-gauge", engineAdmission: "unverified" };
        const identity = (file: string, bytes: string) => ({ file, size: Buffer.byteLength(bytes), sha256: createHash("sha256").update(bytes).digest("hex") });
        const manifest = { artifacts: [] as any[] };
        const module = { role: "qnx-agent-module", activation: "agent-generation", abi: "qnx-aarch64", ...identity(moduleFile, "module") };
        const target = { platform: "qnx", domain: "cluster", entryAbi: "voboost-agent3-v1", boot: false, process: "gauge", ...identity(moduleFile, "module") };
        const catalog = { agents: [{ id: "i18n-ru", targets: [target] }] };
        const save = (l = ledger, c: unknown = catalog, mutate?: (manifest: any) => void) => {
            const text = JSON.stringify(l); writeFileSync(join(closure, ledgerFile), text);
            manifest.artifacts = [{ role: "qnx-resource-pack", activation: "agent-generation", abi: "qnx-aarch64", ...identity(pack, "pack") }, { role: "qnx-resource-ledger", activation: "agent-generation", abi: "qnx-aarch64", ...identity(ledgerFile, text) }, structuredClone(module)];
            mutate?.(manifest); writeFileSync(join(closure, "voboost.json"), JSON.stringify(manifest)); writeFileSync(join(closure, "agents.json"), JSON.stringify(c));
        };
        writeFileSync(join(closure, pack), "pack"); writeFileSync(join(closure, moduleFile), "module"); save();
        for (const format of ["KZBF-v3-indexed", "KZBF-v3-inline"]) {
            const supported = { ...ledger, format };
            save(supported);
            assert.equal(buildArtifactSet(closure, packages).artifacts.filter(row => row.file.startsWith("qnx-resource-") && row.component === "agents").length, 2, format);
            for (const invalid of [
                { ...supported, format: "KZBF-v3-unknown" },
                { ...supported, format: "KZBF-v3-inline " },
                { ...supported, owner: "foreign" },
                { ...supported, locale: "ru" },
            ]) { save(invalid); assert.throws(() => buildArtifactSet(closure, packages), /authority/); }
            save({ ...supported, targetProcess: "point", locale: "voboost-ru-point" });
            assert.throws(() => buildArtifactSet(closure, packages), /module/);
        }
        save();
        for (const mutate of [
            (m: any) => { m.artifacts[0].abi = "arm64-v8a"; },
            (m: any) => { m.artifacts[0].activation = "root-flat"; },
            (m: any) => { m.artifacts[0].resource = {}; },
            (m: any) => { m.artifacts[1].role = "resource-ledger"; },
            (m: any) => { m.artifacts[0].sha256 = sha("a"); },
            (m: any) => { m.artifacts[2].role = "qnx-runtime"; },
        ]) { save(ledger, catalog, mutate); assert.throws(() => buildArtifactSet(closure, packages)); }
        save({ ...ledger, engineAdmission: "ready" }); assert.throws(() => buildArtifactSet(closure, packages), /authority/);
        save({ ...ledger, targetProcess: "point", locale: "voboost-ru-point" }); assert.throws(() => buildArtifactSet(closure, packages), /module/);
        save(ledger, { agents: [{ id: "i18n-ru", targets: [{ ...target, domain: "android" }] }] }); assert.throws(() => buildArtifactSet(closure, packages), /module/);
        save(); writeFileSync(join(closure, "qnx-resource-i18n-ru-foreign-gauge.kzb"), "unlisted"); assert.throws(() => buildArtifactSet(closure, packages), /unknown final closure file/);
    } finally { rmSync(root, { recursive: true, force: true }); }
});
