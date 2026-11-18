import { constants, openSync, closeSync, fstatSync, readSync, writeFileSync, lstatSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
export type QnxResourceArtifact = { pack: string; ledger: string };
function exact(value: unknown, keys: string[]): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    && Object.keys(value).sort().join('\0') === [...keys].sort().join('\0');
}
export function parseQnxResourceArtifacts(bytes: Buffer, catalogBytes: Buffer): QnxResourceArtifact[] {
  if (bytes.length === 0 || bytes.length > 64 * 1024) throw Error('QNX resource index byte bound');
  const value: unknown = JSON.parse(bytes.toString('utf8'));
  if (!exact(value, ['schemaVersion', 'owner', 'artifacts']) || value.schemaVersion !== 1 || value.owner !== 'i18n-ru'
    || !Array.isArray(value.artifacts) || value.artifacts.length === 0 || value.artifacts.length > 24)
    throw Error('QNX resource index shape');
  const catalog = JSON.parse(catalogBytes.toString('utf8')) as { agents?: Array<{ id?: string; targets?: Array<{ platform?: string }> }> };
  const owners = catalog.agents?.filter(a => a.id === value.owner) ?? [];
  if (owners.length !== 1 || !owners[0].targets?.some(t => t.platform === 'qnx')) throw Error('QNX resource native owner absent or ambiguous');
  const names = new Set<string>();
  return value.artifacts.map(entry => {
    if (!exact(entry, ['pack', 'ledger']) || typeof entry.pack !== 'string' || typeof entry.ledger !== 'string'
      || !/^qnx-resource-i18n-ru-[a-z0-9]+(?:-[a-z0-9]+)*\.kzb$/.test(entry.pack) || entry.pack.length > 121
      || entry.ledger !== entry.pack.slice(0, -4) + '.json' || names.has(entry.pack) || names.has(entry.ledger)) throw Error('QNX resource pair identity');
    names.add(entry.pack); names.add(entry.ledger); return { pack: entry.pack, ledger: entry.ledger };
  });
}
type FileRead = (fd: number, buffer: Buffer, offset: number, length: number, position: null) => number;
export function readBoundedQnxResource(path: string, maximum: number, read: FileRead = readSync): Buffer {
  const fd = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const before = fstatSync(fd);
    if (!before.isFile() || before.nlink !== 1 || before.size === 0 || before.size > maximum) throw Error('QNX resource file identity/bound');
    const bytes = Buffer.alloc(before.size);
    let offset = 0;
    while (offset < bytes.length) {
      const count = read(fd, bytes, offset, bytes.length - offset, null);
      if (count <= 0 || count > bytes.length - offset) throw Error('QNX resource changed while reading');
      offset += count;
    }
    const extra = Buffer.alloc(1);
    if (read(fd, extra, 0, 1, null) !== 0) throw Error('QNX resource grew while reading');
    const after = fstatSync(fd);
    if (after.size !== before.size || after.nlink !== before.nlink || after.dev !== before.dev || after.ino !== before.ino
      || after.mtimeMs !== before.mtimeMs || after.ctimeMs !== before.ctimeMs) throw Error('QNX resource changed while reading');
    return bytes;
  } finally { closeSync(fd); }
}
/** Copies only bounded declared pack/ledger bytes; the existing Rust producer admits and signs their roles. */
export function stageQnxResourceArtifacts(directory: string, closure: string, catalog: Buffer): string[] {
  if (!lstatSync(directory).isDirectory() || lstatSync(directory).isSymbolicLink()) throw Error('QNX resource owned root');
  const plan = parseQnxResourceArtifacts(readBoundedQnxResource(join(directory, 'qnx-resource-artifacts.json'), 64 * 1024), catalog);
  const inventory = new Set(['qnx-resource-artifacts.json', ...plan.flatMap(p => [p.pack, p.ledger])]);
  if (readdirSync(directory).some(name => !inventory.delete(name)) || inventory.size !== 0) throw Error('QNX resource output inventory');
  const staged: string[] = [];
  for (const pair of plan) for (const [name, maximum] of [[pair.pack, 16 * 1024 * 1024], [pair.ledger, 1024 * 1024]] as const) {
    writeFileSync(join(closure, name), readBoundedQnxResource(join(directory, name), maximum), { flag: 'wx' }); staged.push(name);
  }
  return staged;
}
