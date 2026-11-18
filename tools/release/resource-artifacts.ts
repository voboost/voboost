import { constants, openSync, closeSync, fstatSync, readSync, writeFileSync, mkdtempSync, rmSync, existsSync, lstatSync } from 'node:fs';
import { join } from 'node:path';
export type ResourceArtifact = { apk: string; ledger: string };
const englishProfiles = new Set(["setting-a11", "launcher-a9", "launcher-a11", "systemui-a9", "media-a9", "media-a11"]);
function exact(value: unknown, keys: string[]): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    && Object.keys(value).sort().join('\0') === [...keys].sort().join('\0');
}
export function parseResourceArtifacts(bytes: Buffer, catalogBytes: Buffer): ResourceArtifact[] {
  if (bytes.length === 0 || bytes.length > 64 * 1024) throw Error('resource artifact index byte bound');
  const value: unknown = JSON.parse(bytes.toString('utf8'));
  const legacy = exact(value, ['schemaVersion', 'owner', 'artifacts'])
    && value.schemaVersion === 1 && value.owner === 'i18n-ru';
  if (!legacy && !(exact(value, ['schemaVersion', 'artifacts']) && value.schemaVersion === 2))
    throw Error('resource artifact index shape');
  if (!Array.isArray(value.artifacts) || value.artifacts.length === 0 || value.artifacts.length > 64)
    throw Error('resource artifact index shape');
  const catalog = JSON.parse(catalogBytes.toString('utf8')) as { agents?: Array<{ id?: string; kind?: string }> };
  if (!Array.isArray(catalog.agents)) throw Error('resource artifact catalog shape');
  const names = new Set<string>();
  return value.artifacts.map(entry => {
    if (!exact(entry, legacy ? ['apk', 'ledger'] : ['owner', 'apk', 'ledger']))
      throw Error('resource artifact pair shape');
    const owner = legacy ? 'i18n-ru' : entry.owner;
    if (owner !== 'i18n-ru' && owner !== 'i18n-en') throw Error('resource artifact owner unsupported');
    const agents = catalog.agents.filter(agent => agent.id === owner);
    if (agents.length !== 1 || agents[0].kind === 'executor')
      throw Error('resource artifact owner absent or ambiguous in agent catalog');
    if (typeof entry.apk !== 'string' || typeof entry.ledger !== 'string'
      || !new RegExp('^resource-' + owner + '-[a-z0-9]+(?:-[a-z0-9]+)*\\.apk$').test(entry.apk) || entry.apk.length > 149
      || (owner === "i18n-en" && !englishProfiles.has(entry.apk.slice("resource-i18n-en-".length, -4)))
      || entry.ledger !== entry.apk.slice(0, -4) + '.json' || names.has(entry.apk) || names.has(entry.ledger))
      throw Error('resource artifact pair identity');
    names.add(entry.apk); names.add(entry.ledger);
    return { apk: entry.apk, ledger: entry.ledger };
  });
}
type FileRead = (fd: number, buffer: Buffer, offset: number, length: number, position: null) => number;
export function readBoundedResource(path: string, maximum: number, read: FileRead = readSync): Buffer {
  const fd = openSync(path, constants.O_RDONLY | constants.O_NOFOLLOW);
  try {
    const before = fstatSync(fd);
    if (!before.isFile() || before.nlink !== 1 || before.size === 0 || before.size > maximum) throw Error('resource artifact file identity/bound');
    const data = Buffer.alloc(before.size);
    let offset = 0;
    while (offset < data.length) {
      const count = read(fd, data, offset, data.length - offset, null);
      if (count <= 0 || count > data.length - offset) throw Error('resource artifact changed while reading');
      offset += count;
    }
    if (read(fd, Buffer.alloc(1), 0, 1, null) !== 0) throw Error('resource artifact grew while reading');
    const after = fstatSync(fd);
    if (after.size !== before.size || after.nlink !== before.nlink || after.dev !== before.dev || after.ino !== before.ino
      || after.mtimeMs !== before.mtimeMs || after.ctimeMs !== before.ctimeMs) throw Error('resource artifact changed while reading');
    return data;
  } finally { closeSync(fd); }
}
/** Caller must exclusively own the private closure directory for the entire operation.
 * Only declared owned build outputs enter it; Rust repeats APK semantic/signature admission. */
export async function stageResourceArtifacts(bundle: string, closure: string, sign: (input: string, output: string) => Promise<void>): Promise<string[]> {
  const index = join(bundle, 'resource-artifacts.json');
  if (!existsSync(index)) return [];
  if (lstatSync(index).isSymbolicLink()) throw Error('resource artifact index symlink');
  const plan = parseResourceArtifacts(readBoundedResource(index, 64 * 1024), readBoundedResource(join(bundle, 'agents.json'), 1024 * 1024));
  const staged: string[] = [];
  for (const pair of plan) {
    const source = join(bundle, pair.apk), ledger = join(bundle, pair.ledger);
    const apkBytes = readBoundedResource(source, 16 * 1024 * 1024), ledgerBytes = readBoundedResource(ledger, 1024 * 1024);
    if (existsSync(join(closure, pair.apk)) || existsSync(join(closure, pair.ledger))) throw Error('resource artifact destination already exists');
    const temporary = mkdtempSync(join(closure, '.resource-sign-'));
    try {
      const input = join(temporary, pair.apk);
      writeFileSync(input, apkBytes, { flag: 'wx', mode: 0o600 });
      await sign(input, join(closure, pair.apk));
      writeFileSync(join(closure, pair.ledger), ledgerBytes, { flag: 'wx' });
    } finally { rmSync(temporary, { recursive: true, force: true }); }
    staged.push(pair.apk, pair.ledger);
  }
  return staged;
}
