import { spawnSync } from "node:child_process";

export type ArchiveIdentity = { serial: string; version: string; manifestSha256: string };
export type TarMember = { name: string; body: Buffer; type?: string; size?: number };

export function tarBytes(members: TarMember[]): Buffer {
  const parts: Buffer[] = [];
  for (const { name, body, type = "0", size = body.length } of members) {
    const header = Buffer.alloc(512);
    header.write(name, 0, 100, "utf8");
    for (const [offset, length, value] of [[100, 8, 0o600], [108, 8, 0], [116, 8, 0], [124, 12, size], [136, 12, 0]])
      header.write(value.toString(8).padStart(length - 1, "0") + "\0", offset, length, "ascii");
    header.fill(32, 148, 156);
    header.write(type, 156, 1, "ascii");
    header.write("ustar\0", 257, 6, "ascii");
    header.write("00", 263, 2, "ascii");
    const checksum = header.reduce((sum, byte) => sum + byte, 0);
    header.write(checksum.toString(8).padStart(6, "0") + "\0 ", 148, 8, "ascii");
    parts.push(header, body, Buffer.alloc((512 - body.length % 512) % 512));
  }
  return Buffer.concat([...parts, Buffer.alloc(1024)]);
}

export function compressTar(bytes: Buffer, check: "crc64" | "none" = "crc64"): Buffer {
  const env = { ...process.env };
  delete env.XZ_OPT;
  delete env.XZ_DEFAULTS;
  const result = spawnSync("xz", ["--compress", "--stdout", "-1", "--check=" + check], { input: bytes, env, maxBuffer: 34 * 1024 * 1024, timeout: 30_000 });
  if (result.error || result.status !== 0 || result.signal) throw new Error("synthetic log fixture needs installed xz");
  return result.stdout;
}

export function logMembers(identity: ArchiveIdentity, records: unknown[]): TarMember[] {
  const body = Buffer.from(records.map(row => JSON.stringify(row) + "\n").join(""));
  const context = Buffer.from(JSON.stringify({ schemaVersion: 1, releaseIdentity: identity.version, manifestSha256: identity.manifestSha256, logSource: "root-owned-segments" }));
  const manifest = Buffer.from(JSON.stringify({ schemaVersion: 1, format: "VBOOSTLOG1", files: ["records.jsonl", "context.json"], header: { schemaVersion: 1, recordsLength: body.length, contextLength: context.length }, hostEnvelope: { deviceSerial: identity.serial, installerVersion: identity.version, completeness: "verified-stream", collectedAtUtc: "2026-10-08T10:00:01Z" } }));
  return [{ name: "manifest.json", body: manifest }, { name: "records.jsonl", body }, { name: "context.json", body: context }];
}

export function logArchive(identity: ArchiveIdentity, records: unknown[]): Buffer {
  return compressTar(tarBytes(logMembers(identity, records)));
}
