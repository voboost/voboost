export type Epoch = { sessionId: string; pid: number; bootId: string };
type File = { basename: string; metadata: string };
export type Snapshot = { session_id: string; android_pid: number; boot_id: string; inventory: { files: File[] }; crash_buffer: { excerpt: string }; tombstone?: { basename: string; excerpt: string; timestamp: string | null } };
export type Reader = (remainingMs: number, tombstone?: string) => Promise<unknown>;
export type Fatal = { pid: number; tid: number; signal: number; fault: string; timestamp: string | null };
export type Baseline = { snapshot?: Snapshot; error?: string };
const basename = /^tombstone_[0-9]{2}$/;
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const text = (v: unknown, bound: number) => typeof v === "string" && Buffer.byteLength(v) <= bound;
const errorText = (e: unknown) => String(e).slice(0, 2048);

/** The native API retains bounded, redacted prefixes; it does not prove their boot association. */
export function snapshot(value: any, epoch: Epoch, selected?: string): Snapshot {
  if (!value || Buffer.byteLength(JSON.stringify(value)) > 131072 || value.status !== "ready" || value.source !== "native" || value.session_id !== epoch.sessionId || value.android_pid !== epoch.pid || value.boot_id !== epoch.bootId || !uuid.test(value.boot_id) || !/^[a-f0-9]{32}$/.test(value.session_id)) throw new Error("native crash epoch differs");
  if (!Array.isArray(value.inventory?.files) || value.inventory.files.length > 100 || !text(value.crash_buffer?.excerpt, 32768)) throw new Error("native crash bounds differ");
  const seen = new Set<string>();
  for (const f of value.inventory.files) {
    if (!basename.test(f?.basename ?? "") || seen.has(f.basename) || !text(f.metadata, 8192)) throw new Error("native tombstone inventory differs");
    seen.add(f.basename);
  }
  if (selected !== undefined && (!basename.test(selected) || value.tombstone?.basename !== selected || !text(value.tombstone?.excerpt, 32768) || !(value.tombstone.timestamp === null || text(value.tombstone.timestamp, 256)))) throw new Error("native selected tombstone differs");
  return value;
}

/** Parse the actual libc fatal identity. A different fault address is a different crash. */
export function fatal(line: string): Fatal | null {
  const m = /Fatal signal (\d+) \([^)]*\), code [^,]+, fault addr (\S+) in tid (\d+) \([^)]*\), pid (\d+) \([^)]*\)/.exec(line);
  if (!m) return null;
  const stamp = /^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s/.exec(line.trim());
  return { signal: Number(m[1]), fault: m[2].toLowerCase(), tid: Number(m[3]), pid: Number(m[4]), timestamp: stamp?.[1] ?? null };
}

export function matchTombstone(prefix: string, target: Fatal) {
  const p = /^pid: (\d+), tid: (\d+), name:/m.exec(prefix);
  const s = /^signal (\d+) \([^)]*\), code [^,]+, fault addr (\S+)/m.exec(prefix);
  const timestamp = /^Timestamp: ([^\r\n]+)/m.exec(prefix)?.[1] ?? null;
  const identity = Boolean(p && s && Number(p[1]) === target.pid && Number(p[2]) === target.tid && Number(s[1]) === target.signal && s[2].toLowerCase() === target.fault);
  // Logcat MM-DD timestamps and epoch-relative timestamps cannot identify a tombstone's year/zone.
  const timeMatches = target.timestamp !== null && timestamp === target.timestamp;
  return { identity, timestamp, confirmed: identity && timeMatches, reason: !identity ? "different native identity" : timeMatches ? "identity and timestamp agree; retained file boot association remains unknown" : "identity candidate only; comparable timestamp unavailable or differs", bootAssociation: "unknown" };
}

async function bounded<T>(run: () => Promise<T>, remainingMs: number): Promise<T> {
  if (remainingMs <= 0) throw new Error("native crash collection deadline exhausted");
  let timer: ReturnType<typeof setTimeout>;
  try { return await Promise.race([run(), new Promise<never>((_, reject) => { timer = setTimeout(() => reject(new Error("native crash collection deadline exhausted")), remainingMs); })]); }
  finally { clearTimeout(timer!); }
}

/** Baseline failure must not replace the existing lifecycle test or its crash guard. */
export async function baseline(read: Reader, epoch: Epoch): Promise<Baseline> {
  try { return { snapshot: snapshot(await bounded(() => read(10000), 10000), epoch) }; }
  catch (e) { return { error: errorText(e) }; }
}

/** Read only. No retries, privileges, guest writes, buffer clears, or mutation replay. */
export async function collect(read: Reader, epoch: Epoch, before: Baseline, freshLines: string[], now = () => performance.now()) {
  const start = now(), deadline = start + 30000;
  const report = { schema: 1, epoch, baseline: before.snapshot ?? null, baselineError: before.error ?? null, deadlineMs: 30000, maxPrefixes: 4, targets: freshLines.slice(0, 512).map(fatal).filter((v): v is Fatal => v !== null), snapshots: [] as Snapshot[], prefixes: [] as { basename: string; matches: ReturnType<typeof matchTombstone>[] }[], errors: [] as string[], unread: [] as string[], truncated: false, matching: null as string | null, elapsedMs: 0, bootAssociation: "unknown", originalFailurePreserved: true };
  const obtain = async (selected?: string) => {
    const remaining = Math.floor(deadline - now());
    const value = await bounded(() => read(remaining, selected), remaining);
    if (now() > deadline) throw new Error("native crash response arrived after collection deadline");
    const result = snapshot(value, epoch, selected);
    report.snapshots.push(result);
    return result;
  };
  try {
    const after = await obtain();
    if (before.snapshot) {
      const oldLines = new Set(before.snapshot.crash_buffer.excerpt.split("\n"));
      const candidates = after.crash_buffer.excerpt.split("\n").filter(line => !oldLines.has(line));
      const seen = new Set(report.targets.map(target => JSON.stringify(target)));
      for (const line of candidates.slice(0, 512)) {
        const target = fatal(line), key = JSON.stringify(target);
        if (target && !seen.has(key) && report.targets.length < 512) { report.targets.push(target); seen.add(key); }
      }
    }
    if (!before.snapshot) { report.errors.push("baseline unavailable; new or changed tombstone identity cannot be established"); return report; }
    const old = new Map(before.snapshot.inventory.files.map(f => [f.basename, f.metadata]));
    const changed = after.inventory.files.filter(f => old.get(f.basename) !== f.metadata).map(f => f.basename).sort();
    report.unread = [...changed];
    for (const name of changed.slice(0, 4)) {
      if (now() >= deadline) break;
      try {
        const value = await obtain(name), matches = report.targets.map(t => matchTombstone(value.tombstone!.excerpt, t));
        report.prefixes.push({ basename: name, matches });
        report.unread = report.unread.filter(n => n !== name);
        if (matches.some(m => m.identity)) { report.matching = name; break; }
      } catch (e) { report.errors.push(errorText(e)); break; }
    }
  } catch (e) { report.errors.push(errorText(e)); }
  finally { report.elapsedMs = Math.max(0, now() - start); report.truncated = report.unread.length > 0 || report.errors.length > 0; }
  return report;
}

/** Diagnostic errors must never replace the exact original test failure. */
export async function preserveFailure<T>(failure: T, diagnose: () => Promise<unknown>): Promise<T> {
  try { await diagnose(); } catch {}
  return failure;
}
