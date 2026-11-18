// Build-only CI inputs. Production trust and publication admission stay separate.
import { createHash, createPrivateKey, createPublicKey } from "node:crypto";
import { appendFileSync, chmodSync, copyFileSync, mkdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { parse } from "smol-toml";

export const ROLES = ["agent-catalog", "product", "native-executable", "restore"] as const;
export function sourcePins(text: string, codestyle: string): Record<string, string> {
    const definition = parse(text);
    if (definition.release !== "1.0.0-beta1") throw new Error("expected 1.0.0-beta1 source definition");
    const repositories = definition.repositories as Record<string, unknown>;
    const result: Record<string, string> = {};
    for (const name of ["app", "agents", "inject", "installer", "logo", "codestyle"]) {
        const value = name === "codestyle" ? codestyle : repositories?.[name];
        if (typeof value !== "string" || !/^[0-9a-f]{40}$/u.test(value)) throw new Error(`invalid source pin: ${name}`);
        result[name] = value;
    }
    return result;
}
export function decodeSecret(value: string | undefined, name: string, size?: number): Buffer {
    if (!value || !/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/u.test(value)) throw new Error(`missing or invalid base64 secret: ${name}`);
    const bytes = Buffer.from(value, "base64");
    if (bytes.length === 0 || (size !== undefined && bytes.length !== size)) throw new Error(`invalid secret size: ${name}`);
    return bytes;
}
export type PublicRoots = { schemaVersion: 1; roots: Record<(typeof ROLES)[number], string>; androidCertificateSha256: string };
export function validatePublicRoots(raw: unknown): PublicRoots {
    if (typeof raw !== "object" || raw === null || Array.isArray(raw)) throw new Error("invalid CI public roots document");
    const value = raw as PublicRoots;
    if (value.schemaVersion !== 1 || !value.roots || typeof value.roots !== "object" || Array.isArray(value.roots) || Object.keys(value.roots).sort().join(",") !== [...ROLES].sort().join(",")) throw new Error("invalid CI public roots schema");
    for (const hash of [...ROLES.map((role) => value.roots[role]), value.androidCertificateSha256]) {
        if (typeof hash !== "string" || !/^[0-9a-f]{64}$/u.test(hash)) throw new Error("invalid CI public root or certificate fingerprint");
    }
    return value;
}
export function materializeAuthority(directory: string, environment: NodeJS.ProcessEnv, expectedInput: unknown): void {
    // Validate everything before creating any private material. Raw 32-byte
    // Ed25519 seeds match the owning release CLI; CI imports its dedicated authority.
    const expected = validatePublicRoots(expectedInput);
    const keys = ROLES.map((role) => decodeSecret(environment[`KEY_${role.replaceAll("-", "_").toUpperCase()}`], role, 32));
    const keystore = decodeSecret(environment.APP_KEYSTORE, "app.jks");
    if (new Set(keys.map((key) => key.toString("hex"))).size !== ROLES.length) throw new Error("role authorities must differ");
    const publicKeys = keys.map((key) => createPublicKey(createPrivateKey({ key: Buffer.concat([Buffer.from("302e020100300506032b657004220420", "hex"), key]), format: "der", type: "pkcs8" })));
    for (const [index, role] of ROLES.entries()) {
        const actual = (publicKeys[index].export({ format: "der", type: "spki" }) as Buffer).subarray(-32).toString("hex");
        if (actual !== expected.roots[role]) throw new Error(`CI public root mismatch: ${role}`);
    }
    // keytool requires a regular keystore file. Until its certificate is
    // admitted, only that file is staged; failed admission removes our directory.
    mkdirSync(directory, { mode: 0o700 });
    try {
        chmodSync(directory, 0o700);
        const jks = join(directory, "app.jks");
        writeFileSync(jks, keystore, { mode: 0o600, flag: "wx" });
        const certificate = spawnSync("/usr/bin/keytool", ["-exportcert", "-keystore", jks, "-storepass", "changeit", "-alias", "voboost"]);
        if (certificate.status !== 0 || certificate.stdout.length === 0) throw new Error("cannot read CI Android signing certificate");
        if (createHash("sha256").update(certificate.stdout).digest("hex") !== expected.androidCertificateSha256) throw new Error("CI Android signing certificate mismatch");
        for (const [index, role] of ROLES.entries()) {
            const publicKey = publicKeys[index];
            writeFileSync(join(directory, `${role}.key`), keys[index], { mode: 0o600, flag: "wx" });
            writeFileSync(join(directory, `${role}.pub`), (publicKey.export({ format: "der", type: "spki" }) as Buffer).subarray(-32), { flag: "wx" });
            writeFileSync(join(directory, `${role}.pem`), publicKey.export({ format: "pem", type: "spki" }), { flag: "wx" });
        }
    } catch (error) {
        rmSync(directory, { recursive: true });
        throw error;
    }
}
export function exportPublicInputs(product: string, authority: string, output: string): void {
    mkdirSync(output);
    for (const [role, fixtureRole] of [["product", "release-catalog"], ["restore", "restore"], ["native-executable", "native-executable"]]) {
        copyFileSync(join(authority, `${role}.pem`), join(output, `${role}.pem`));
        const fixture = join(output, `${role}.fixture`);
        writeFileSync(fixture, `voboost-role-trust-fixture-v1:${fixtureRole}\n`);
        const result = spawnSync("cargo", ["run", "--manifest-path", join(product, "release/Cargo.toml"), "--locked", "--", "sign-file", join(authority, `${role}.key`), fixture, `${fixture}.sig`], { stdio: "inherit" });
        if (result.status !== 0) throw new Error(`role fixture signing failed: ${role}`);
        unlinkSync(fixture);
    }
}
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    const [command, first, second, third] = process.argv.slice(2);
    if (command === "pins") {
        const pins = sourcePins(readFileSync(first, "utf8"), second);
        for (const [name, sha] of Object.entries(pins)) appendFileSync(process.env.GITHUB_OUTPUT!, `${name}=${sha}\n`);
    } else if (command === "authority") materializeAuthority(first, process.env, JSON.parse(readFileSync(join(fileURLToPath(new URL("../..", import.meta.url)), "release/beta1-build-public-roots.json"), "utf8")));
    else if (command === "public-inputs") exportPublicInputs(first, second, third);
    else throw new Error("usage: ci-build.ts pins <definition> <codestyle-sha> | authority <new-dir> | public-inputs <product> <authority> <new-dir>");
}
