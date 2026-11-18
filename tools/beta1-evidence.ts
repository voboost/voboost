import { existsSync, lstatSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

type RecordValue = Record<string, unknown>;

function object(value: unknown, name: string): RecordValue {
    if (value === null || typeof value !== "object" || Array.isArray(value)) throw new Error(`${name} must be an object`);
    return value as RecordValue;
}

function exactKeys(value: RecordValue, keys: string[], name: string): void {
    const actual = Object.keys(value).sort();
    const expected = [...keys].sort();
    if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${name} keys differ`);
}

function equal(actual: unknown, expected: unknown, name: string): void {
    if (actual !== expected) throw new Error(`${name} differs`);
}

function sha(value: unknown, name: string, length = 64): string {
    if (typeof value !== "string" || !new RegExp(`^[0-9a-f]{${length}}$`, "u").test(value)) throw new Error(`${name} is invalid`);
    return value;
}

function positive(value: unknown, name: string): string {
    if (typeof value !== "string" || !/^[1-9][0-9]*$/u.test(value)) throw new Error(`${name} is invalid`);
    return value;
}

function digestCanonical(value: unknown): string {
    return createHash("sha256").update(`${JSON.stringify(value)}\n`).digest("hex");
}

type ArtifactSet = { schemaVersion: number; artifacts: Array<{ component: string; file: string; size: number; sha256: string }> };

// Classification consumes the signed product's declarations; authentication remains the
// preceding verified-closure stage, and byte identities here bind the evidence inventory.
function resourceFiles(closure: string): Set<string> {
    const manifestPath = resolve(closure, "voboost.json");
    if (!existsSync(manifestPath)) return new Set();
    const manifest = object(JSON.parse(readFileSync(manifestPath, "utf8")), "product manifest");
    if (!Array.isArray(manifest.artifacts)) throw new Error("product artifacts are invalid");
    const rows = manifest.artifacts.map((value, index) => object(value, `product artifact ${index}`));
    const resources = rows.filter(row => ["resource-apk", "resource-ledger", "qnx-resource-pack", "qnx-resource-ledger"].includes(String(row.role)));
    if (resources.length === 0) return new Set();
    const catalog = object(JSON.parse(readFileSync(resolve(closure, "agents.json"), "utf8")), "agent catalog");
    if (!Array.isArray(catalog.agents)) throw new Error("resource owner catalog is invalid");
    const result = new Set<string>();
    const claim = (row: RecordValue, suffix: string, prefix = "resource-i18n-ru-"): void => {
        if (typeof row.file !== "string" || row.file.length > 150 || !new RegExp(`^${prefix}[a-z0-9]+(?:-[a-z0-9]+)*\\.${suffix}$`, "u").test(row.file)
            || row.activation !== "agent-generation" || result.has(row.file)
            || rows.filter(other => other.file === row.file).length !== 1) throw new Error("resource declaration is invalid");
        const file = resolve(closure, row.file), metadata = lstatSync(file);
        if (!metadata.isFile() || metadata.isSymbolicLink() || metadata.size !== row.size
            || createHash("sha256").update(readFileSync(file)).digest("hex") !== row.sha256) throw new Error("resource artifact identity differs");
        result.add(row.file);
    };
    for (const row of resources.filter(row => row.role === "resource-apk")) {
        const resource = object(row.resource, "resource descriptor");
        const locale = resource.locale;
        if ((locale !== "ru" && locale !== "en") || resource.owner !== `i18n-${locale}` || resource.compatibilitySchema !== 1
            || typeof resource.targetPackage !== "string" || !resource.targetPackage
            || resource.overlayPackage !== row.packageName
            || catalog.agents.filter(raw => object(raw, "agent").id === resource.owner).length !== 1) throw new Error("resource owner differs");
        const ledger = object(resource.coverageLedger, "resource coverage ledger");
        const matches = resources.filter(other => other.role === "resource-ledger" && other.file === ledger.file
            && other.size === ledger.size && other.sha256 === ledger.sha256);
        if (matches.length !== 1 || ledger.file !== String(row.file).replace(/\.apk$/u, ".json")) throw new Error("resource ledger binding differs");
        const prefix = `resource-i18n-${locale}-`;
        claim(row, "apk", prefix); claim(matches[0], "json", prefix);
    }
    for (const row of resources.filter(row => row.role === "qnx-resource-pack")) {
        const ledgerFile = String(row.file).replace(/\.kzb$/u, ".json");
        const matches = resources.filter(other => other.role === "qnx-resource-ledger" && other.file === ledgerFile);
        if (matches.length !== 1) throw new Error("QNX resource ledger binding differs");
        for (const artifact of [row, matches[0]]) {
            if (artifact.abi !== "qnx-aarch64" || ["resource", "packageName", "versionCode", "certificateSha256"].some(key => key in artifact)) throw new Error("QNX resource role metadata differs");
        }
        claim(row, "kzb", "qnx-resource-i18n-ru-"); claim(matches[0], "json", "qnx-resource-i18n-ru-");
        const ledger = object(JSON.parse(readFileSync(resolve(closure, ledgerFile), "utf8")), "QNX resource ledger");
        if (ledger.schemaVersion !== 1 || ledger.owner !== "i18n-ru" || ledger.compatibilitySchema !== 1
            || !["gauge", "point", "light"].includes(String(ledger.targetProcess)) || !(ledger.format === "KZBF-v3-indexed" || ledger.format === "KZBF-v3-inline")
            || ledger.locale !== "voboost-ru-" + ledger.targetProcess || ledger.engineAdmission !== "unverified") throw new Error("QNX resource ledger authority differs");
        const owners = catalog.agents.map(raw => object(raw, "agent")).filter(agent => agent.id === ledger.owner);
        if (owners.length !== 1 || "kind" in owners[0] || !Array.isArray(owners[0].targets)) throw new Error("QNX resource owner differs");
        const targets = owners[0].targets.map(raw => object(raw, "QNX owner target")).filter(target => target.platform === "qnx" && target.domain === "cluster"
            && target.entryAbi === "voboost-agent3-v1" && target.process === ledger.targetProcess && target.boot === false);
        if (targets.length !== 1 || targets[0].file !== "libagent-i18n-ru.so" || rows.filter(artifact => artifact.role === "qnx-agent-module" && artifact.file === targets[0].file
            && artifact.size === targets[0].size && artifact.sha256 === targets[0].sha256).length !== 1) throw new Error("QNX resource owner module differs");
    }
    if (result.size !== resources.length) throw new Error("unowned resource ledger");
    return result;
}

function componentFor(file: string, packages: boolean, resources: Set<string>): string {
    if (packages) {
        if (/^voboost-installer-1\.0\.0-beta1(?:(?:-(?:arm64|x86_64))?\.dmg|\.exe|\.tar\.bz2)$/u.test(file)) return "installer";
        throw new Error(`unknown installer package ${file}`);
    }
    if (resources.has(file)) return "agents";
    if (file === "voboost-app.apk") return "app";
    if (file === "agents.json" || file === "agents.json.sig" || /^agent-[A-Za-z0-9._-]+\.dex$/u.test(file)) return "agents";
    if (file === "libvoboost-runtime-cluster.so" || /^libagent-[a-z][a-z0-9-]*\.so$/u.test(file)) return "agents";
    if (["voboost-cluster-daemon", "voboost-cluster-startup.sh", "libvoboost-cluster-inject.so"].includes(file)) return "inject";
    if (/^(voboost-(daemon|inject|install|restore|uninstall|selinux)|voboost\.rc|lib(implant|lsplant)-(arm64-v8a|armeabi-v7a)\.so)$/u.test(file)) return "inject";
    if (/^(restore\.json|restore\.json\.sig|voboost\.json|voboost\.json\.sig|release-identity\.json|release-identity\.json\.sig|SHA256SUMS)$/u.test(file)) return "product";
    throw new Error(`unknown final closure file ${file}`);
}

export function buildArtifactSet(closure: string, packages: string): ArtifactSet {
    const artifacts: ArtifactSet["artifacts"] = [];
    const resources = resourceFiles(closure);
    for (const [directory, packageFiles] of [[closure, false], [packages, true]] as const) {
        for (const file of readdirSync(directory)) {
            const path = resolve(directory, file);
            const metadata = lstatSync(path);
            if (!metadata.isFile() || metadata.isSymbolicLink()) throw new Error(`artifact set entry is not a regular file: ${file}`);
            artifacts.push({ component: componentFor(file, packageFiles, resources), file, size: metadata.size, sha256: createHash("sha256").update(readFileSync(path)).digest("hex") });
        }
    }
    artifacts.sort((left, right) => {
        const leftKey = `${left.component}\0${left.file}`;
        const rightKey = `${right.component}\0${right.file}`;
        return leftKey < rightKey ? -1 : leftKey > rightKey ? 1 : 0;
    });
    return { schemaVersion: 1, artifacts };
}

function artifactSet(value: unknown, expectedDigest: unknown): ArtifactSet {
    const set = object(value, "artifactSet");
    exactKeys(set, ["schemaVersion", "artifacts"], "artifactSet");
    equal(set.schemaVersion, 1, "artifactSet.schemaVersion");
    if (!Array.isArray(set.artifacts) || set.artifacts.length === 0 || set.artifacts.length > 1024) throw new Error("artifactSet.artifacts is invalid");
    let previous = "";
    const seen = new Set<string>();
    const normalized: ArtifactSet = { schemaVersion: 1, artifacts: [] };
    for (const [index, raw] of set.artifacts.entries()) {
        const row = object(raw, `artifactSet.artifacts[${index}]`);
        exactKeys(row, ["component", "file", "size", "sha256"], `artifactSet.artifacts[${index}]`);
        if (typeof row.component !== "string" || !/^(product|app|agents|inject|installer|license|logo)$/u.test(row.component)) throw new Error("artifact component is invalid");
        if (typeof row.file !== "string" || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,149}$/u.test(row.file)) throw new Error("artifact filename is invalid");
        if (!Number.isSafeInteger(row.size) || Number(row.size) <= 0) throw new Error("artifact size is invalid");
        sha(row.sha256, "artifact sha256");
        const key = `${row.component}\0${row.file}`;
        if (key <= previous || seen.has(key)) throw new Error("artifactSet must be unique and canonically ordered");
        previous = key;
        seen.add(key);
        normalized.artifacts.push({ component: row.component, file: row.file, size: row.size, sha256: row.sha256 } as ArtifactSet["artifacts"][number]);
    }
    equal(expectedDigest, digestCanonical(normalized), "artifactSetSha256");
    return normalized;
}

function receipt(value: unknown, name: string, artifactSetSha256: string, sequence: number, previousReceiptSha256: string, vehicle = false): string {
    const row = object(value, name);
    exactKeys(row, vehicle
        ? ["status", "receiptSha256", "completedAt", "nonce", "sequence", "artifactSetSha256", "previousReceiptSha256", "vehicleIdentitySha256", "firmware"]
        : ["status", "receiptSha256", "completedAt", "nonce", "sequence", "artifactSetSha256", "previousReceiptSha256"], name);
    equal(row.status, "passed", `${name}.status`);
    equal(row.sequence, sequence, `${name}.sequence`);
    equal(row.artifactSetSha256, artifactSetSha256, `${name}.artifactSetSha256`);
    equal(row.previousReceiptSha256, previousReceiptSha256, `${name}.previousReceiptSha256`);
    sha(row.nonce, `${name}.nonce`);
    if (typeof row.completedAt !== "string" || Number.isNaN(Date.parse(row.completedAt))) throw new Error(`${name}.completedAt is invalid`);
    if (vehicle) {
        sha(row.vehicleIdentitySha256, `${name}.vehicleIdentitySha256`);
        if (typeof row.firmware !== "string" || row.firmware.length === 0 || row.firmware.length > 256) throw new Error(`${name}.firmware is invalid`);
    }
    const body: RecordValue = { status: row.status, completedAt: row.completedAt, nonce: row.nonce, sequence: row.sequence, artifactSetSha256: row.artifactSetSha256, previousReceiptSha256: row.previousReceiptSha256 };
    if (vehicle) {
        body.vehicleIdentitySha256 = row.vehicleIdentitySha256;
        body.firmware = row.firmware;
    }
    equal(row.receiptSha256, digestCanonical(body), `${name}.receiptSha256`);
    return String(row.receiptSha256);
}

export type LocalExpected = {
    producer: { workflowCommitSha: string; workflowBlobSha: string; runId: string; runAttempt: string };
    product: { commit: string; tree: string; workflowBlobSha: string; sourceDefinitionSha256: string; trustDocumentSha256: string };
    sources: Record<"app" | "agents" | "inject" | "installer" | "logo", { commit: string; tree: string }> & { license?: { commit: string; tree: string } };
};

export function validateLocalEvidence(value: unknown, expected: LocalExpected): void {
    const root = object(value, "evidence");
    exactKeys(root, ["schemaVersion", "releaseIdentity", "producer", "product", "sources", "artifactSet", "artifactSetSha256", "localBuild", "vehicle", "uninstall", "cleanState"], "evidence");
    equal(root.schemaVersion, 1, "schemaVersion");
    equal(root.releaseIdentity, "1.0.0-beta1", "releaseIdentity");
    const producer = object(root.producer, "producer");
    exactKeys(producer, ["repository", "workflowPath", "workflowCommitSha", "workflowBlobSha", "ref", "runId", "runAttempt", "job", "artifactName"], "producer");
    equal(producer.repository, "voboost/voboost", "producer.repository");
    equal(producer.workflowPath, ".github/workflows/beta1-evidence-ingest.yml", "producer.workflowPath");
    equal(producer.ref, "refs/heads/main", "producer.ref");
    equal(producer.job, "ingest", "producer.job");
    equal(producer.artifactName, "beta1-local-delivery-evidence", "producer.artifactName");
    for (const key of ["workflowCommitSha", "workflowBlobSha", "runId", "runAttempt"] as const) equal(producer[key], expected.producer[key], `producer.${key}`);
    const product = object(root.product, "product");
    exactKeys(product, ["commit", "tree", "workflowBlobSha", "sourceDefinitionSha256", "trustDocumentSha256"], "product");
    for (const key of ["commit", "tree", "workflowBlobSha", "sourceDefinitionSha256", "trustDocumentSha256"] as const) equal(product[key], expected.product[key], `product.${key}`);
    const sources = object(root.sources, "sources");
    exactKeys(sources, Object.keys(expected.sources), "sources");
    for (const [name, identity] of Object.entries(expected.sources)) {
        if (!identity) throw new Error(`expected source identity is missing: ${name}`);
        const source = object(sources[name], `sources.${name}`);
        exactKeys(source, ["commit", "tree"], `sources.${name}`);
        equal(source.commit, identity.commit, `sources.${name}.commit`);
        equal(source.tree, identity.tree, `sources.${name}.tree`);
    }
    const setDigest = sha(root.artifactSetSha256, "artifactSetSha256");
    artifactSet(root.artifactSet, setDigest);
    let previous = "0".repeat(64);
    const times: number[] = [];
    const nonces = new Set<string>();
    for (const [name, value, vehicle, sequence] of [["localBuild", root.localBuild, false, 1], ["vehicle", root.vehicle, true, 2], ["uninstall", root.uninstall, false, 3], ["cleanState", root.cleanState, false, 4]] as const) {
        const row = object(value, name);
        times.push(Date.parse(String(row.completedAt)));
        nonces.add(String(row.nonce));
        previous = receipt(value, name, setDigest, sequence, previous, vehicle);
    }
    if (!times.every((value, index) => index === 0 || times[index - 1] <= value)) throw new Error("receipt timestamps are not ordered");
    if (nonces.size !== 4) throw new Error("receipt nonces must be distinct");
}

export type CiExpected = {
    producer: { workflowCommitSha: string; workflowBlobSha: string; runId: string; runAttempt: string };
    product: { commit: string; workflowBlobSha: string; sourceDefinitionSha256: string; trustDocumentSha256: string };
    ci: { runId: string; runAttempt: string; finalClosure: { id: string; digest: string; payloadSha256: string }; installerPackages: { id: string; digest: string; payloadSha256: string } };
    artifactSet?: ArtifactSet;
};

export function validateCiVehicleEvidence(value: unknown, expected: CiExpected): void {
    const root = object(value, "evidence");
    exactKeys(root, ["schemaVersion", "releaseIdentity", "producer", "product", "ci", "artifactSet", "artifactSetSha256", "vehicle", "uninstall", "cleanState"], "evidence");
    equal(root.schemaVersion, 1, "schemaVersion");
    equal(root.releaseIdentity, "1.0.0-beta1", "releaseIdentity");
    const producer = object(root.producer, "producer");
    exactKeys(producer, ["repository", "workflowPath", "workflowCommitSha", "workflowBlobSha", "ref", "runId", "runAttempt", "job", "artifactName"], "producer");
    equal(producer.repository, "voboost/voboost", "producer.repository");
    equal(producer.workflowPath, ".github/workflows/beta1-evidence-ingest.yml", "producer.workflowPath");
    equal(producer.ref, "refs/heads/main", "producer.ref");
    equal(producer.job, "ingest", "producer.job");
    equal(producer.artifactName, "beta1-ci-vehicle-evidence", "producer.artifactName");
    for (const key of ["workflowCommitSha", "workflowBlobSha", "runId", "runAttempt"] as const) equal(producer[key], expected.producer[key], `producer.${key}`);
    const product = object(root.product, "product");
    exactKeys(product, ["commit", "workflowBlobSha", "sourceDefinitionSha256", "trustDocumentSha256"], "product");
    for (const key of ["commit", "workflowBlobSha", "sourceDefinitionSha256", "trustDocumentSha256"] as const) equal(product[key], expected.product[key], `product.${key}`);
    const ci = object(root.ci, "ci");
    exactKeys(ci, ["runId", "runAttempt", "finalClosure", "installerPackages"], "ci");
    equal(ci.runId, expected.ci.runId, "ci.runId");
    equal(ci.runAttempt, expected.ci.runAttempt, "ci.runAttempt");
    for (const kind of ["finalClosure", "installerPackages"] as const) {
        const artifact = object(ci[kind], `ci.${kind}`);
        exactKeys(artifact, ["id", "digest", "payloadSha256"], `ci.${kind}`);
        for (const key of ["id", "digest", "payloadSha256"] as const) equal(artifact[key], expected.ci[kind][key], `ci.${kind}.${key}`);
    }
    const setDigest = sha(root.artifactSetSha256, "artifactSetSha256");
    const set = artifactSet(root.artifactSet, setDigest);
    if (expected.artifactSet !== undefined && JSON.stringify(set) !== JSON.stringify(expected.artifactSet)) throw new Error("artifactSet differs from downloaded final bytes");
    let previous = "0".repeat(64);
    const times: number[] = [];
    const nonces = new Set<string>();
    for (const [name, value, vehicle, sequence] of [["vehicle", root.vehicle, true, 1], ["uninstall", root.uninstall, false, 2], ["cleanState", root.cleanState, false, 3]] as const) {
        const row = object(value, name);
        times.push(Date.parse(String(row.completedAt)));
        nonces.add(String(row.nonce));
        previous = receipt(value, name, setDigest, sequence, previous, vehicle);
    }
    if (!times.every((value, index) => index === 0 || times[index - 1] <= value)) throw new Error("receipt timestamps are not ordered");
    if (nonces.size !== 3) throw new Error("receipt nonces must be distinct");
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const [kind, evidencePath, expectedPath] = process.argv.slice(2);
    if (kind === "inventory") {
        if (evidencePath === undefined || expectedPath === undefined || process.argv[5] === undefined) throw new Error("usage: beta1-evidence.ts inventory <closure> <packages> <output>");
        writeFileSync(process.argv[5], `${JSON.stringify(buildArtifactSet(evidencePath, expectedPath))}\n`);
        process.exit(0);
    }
    const evidence = JSON.parse(readFileSync(evidencePath, "utf8"));
    const expected = JSON.parse(readFileSync(expectedPath, "utf8"));
    if (kind === "local") validateLocalEvidence(evidence, expected as LocalExpected);
    else if (kind === "ci") validateCiVehicleEvidence(evidence, expected as CiExpected);
    else throw new Error("usage: beta1-evidence.ts <local|ci> <evidence.json> <expected.json>");
}
