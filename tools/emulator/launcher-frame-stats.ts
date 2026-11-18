/**
 * Pure parser for `dumpsys gfxinfo <package> framestats` (Android 9) PROFILEDATA CSV.
 *
 * Real Android 9 captures (see plans/2026-10-24/runtime305/launcher-gfxinfo-original298.txt)
 * delimit each PROFILEDATA window with `---PROFILEDATA---` on BOTH sides: there is NO
 * `---ENDPROFILEDATA---` marker. A section therefore starts at one `---PROFILEDATA---`
 * line and ends at the NEXT `---PROFILEDATA---` line; later windows are never
 * consumed into the first section.
 *
 * Latency here is HWUI frame *completion* (FrameCompleted - IntendedVsync), NOT
 * presentation time and NOT an FPS window. No refresh rate is fabricated: slow-frame
 * classification requires an explicit supplied refreshPeriodNs. Timestamps of 0 are
 * treated as uninitialized and invalid; FrameCompleted < IntendedVsync is invalid.
 */

export interface FrameRow {
  flags: number;
  intendedVsyncNs: bigint;
  frameCompletedNs: bigint;
}

export interface SectionStats {
  /** CSV rows present in this PROFILEDATA section (header excluded). */
  rowCount: number;
  /** Rows excluded because Flags != 0 before any latency aggregation. */
  nonzeroFlagCount: number;
  /** Rows dropped for malformed / negative / zero (uninitialized) / missing timestamps, or FrameCompleted < IntendedVsync. */
  invalidTimestampCount: number;
  validFrameCount: number;
  latencyMs: { median: number; p95: number; p99: number; max: number } | null;
  slowFrameCount: number | null;
  /** slowFrameCount / validFrameCount, or null when refreshPeriodNs not supplied or no valid rows. */
  slowFrameShare: number | null;
}

export interface FrameCompletionRate {
  /** Frames per second derived from FrameCompleted deltas; HWUI completion clock, not presentation. */
  framesPerSecond: number;
  source: "HWUI";
  windowMs: number;
}

export interface FrameStatsResult {
  /** Per PROFILEDATA section. Different sections are never merged: they may come from different windows/captures. */
  sections: Array<SectionStats & { window: string | null; header: string[]; rows: FrameRow[] }>;
  frameCompletionRate: FrameCompletionRate | null;
}

/**
 * Android 9 uses this marker to BOTH open and close a PROFILEDATA block.
 * There is no ---ENDPROFILEDATA--- on this platform (verified against real capture
 * plans/2026-10-24/runtime305/launcher-gfxinfo-original298.txt).
 */
const PROFILE_MARKER = "---PROFILEDATA---";

function parseCsvLine(line: string): string[] {
  return line.split(",").map(field => field.trim());
}

function parseNs(value: string | undefined): bigint | null {
  if (value === undefined || value === "") return null;
  // Unsigned decimal nanoseconds only; also reject "0": a zero timestamp is an
  // uninitialized/unpopulated cell in gfxinfo output, never a real frame time.
  if (!/^[1-9]\d{0,18}$/.test(value)) return null;
  return BigInt(value);
}

function requiredColumn(header: string[], name: string): number {
  const index = header.indexOf(name);
  if (index === -1) throw new Error(`missing required column ${name} in PROFILEDATA header`);
  return index;
}

function percentile(sorted: number[], fraction: number): number {
  // Nearest-rank percentile: ceil(fraction*n)-th value (1-based), no interpolation.
  const rank = Math.ceil(fraction * sorted.length);
  return sorted[Math.min(Math.max(rank, 1), sorted.length) - 1];
}

function normalizeRefreshPeriodNs(value: bigint | number): bigint {
  if (typeof value === "bigint") {
    if (value <= 0n || value > BigInt(Number.MAX_SAFE_INTEGER)) {
      throw new Error(`refreshPeriodNs must be a positive safe integer (got ${value.toString()})`);
    }
    return value;
  }
  if (typeof value === "number" && Number.isSafeInteger(value) && value > 0) return BigInt(value);
  throw new Error(`refreshPeriodNs must be a positive safe integer or bigint (got ${String(value)})`);
}

function validateMeasurementWindowMs(value: number): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value <= 0) {
    throw new Error(`measurementWindowMs must be a finite positive number (got ${String(value)})`);
  }
  return value;
}

export interface ParseOptions {
  /** Optional device refresh period in nanoseconds; enables slowFrameCount/slowFrameShare. Never defaulted to 60Hz. */
  refreshPeriodNs?: bigint | number;
  /**
   * Explicit measurement window in milliseconds over which frameCompletionRate is reported.
   * Rate is only produced for an explicitly selected single section (rateSectionIndex, or a
   * single-section dump) with at least 2 valid strictly-monotonic FrameCompleted rows.
   */
  measurementWindowMs?: number;
  /**
   * Explicit section index for frameCompletionRate. Required when the dump contains multiple
   * PROFILEDATA sections: no rate is ever derived by silently picking the first section.
   */
  rateSectionIndex?: number;
}

/**
 * Parses `dumpsys gfxinfo <pkg> framestats` output (Android 9 marker convention:
 * opening and closing markers use the same text, paired per window).
 * All sections are kept separate so multiple windows are never silently merged.
 */
export function parseFrameStats(dump: string, options: ParseOptions = {}): FrameStatsResult {
  const lines = dump.split(/\r?\n/);
  const sections: FrameStatsResult["sections"] = [];
  const marker = PROFILE_MARKER;
  const refreshMs: number | null = options.refreshPeriodNs === undefined
    ? null
    : Number(normalizeRefreshPeriodNs(options.refreshPeriodNs)) / 1e6;

  let i = 0;
  let window: string | null = null;
  while (i < lines.length) {
    if (lines[i].startsWith("Window: ")) window = lines[i].slice(8).trim();
    if (lines[i].trim() !== marker) { i++; continue; }
    let j = i + 1;
    if (j >= lines.length || lines[j].trim() === marker) throw new Error("missing PROFILEDATA header");
    const header = parseCsvLine(lines[j]);
    const flagsIdx = requiredColumn(header, "Flags");
    const ivsIdx = requiredColumn(header, "IntendedVsync");
    const doneIdx = requiredColumn(header, "FrameCompleted");
    const rows: FrameRow[] = [];
    let nonzeroFlagCount = 0;
    let invalidTimestampCount = 0;
    const latenciesMs: number[] = [];
    j++;
    // Section body runs until the NEXT ---PROFILEDATA--- marker: on Android 9 the same
    // closing marker terminates this window and must be skipped before the next opening.
    while (j < lines.length && lines[j].trim() !== marker) {
      const fields = parseCsvLine(lines[j]);
      if (fields.some(f => f !== "")) {
        const flags = /^(\d+)$/.test(fields[flagsIdx] ?? "") ? Number(fields[flagsIdx]) : NaN;
        const intended = parseNs(fields[ivsIdx]);
        const completed = parseNs(fields[doneIdx]);
        if (Number.isNaN(flags)) invalidTimestampCount++;
        else if (flags !== 0) nonzeroFlagCount++;
        else if (intended === null || completed === null) invalidTimestampCount++;
        else if (completed <= intended) invalidTimestampCount++; // completion before vsync: invalid row
        else {
          rows.push({ flags, intendedVsyncNs: intended, frameCompletedNs: completed });
          // BigInt difference first, only then convert to milliseconds.
          if (completed > intended) latenciesMs.push(Number(completed - intended) / 1e6);
        }
      }
      j++;
    }
    if (j >= lines.length) throw new Error("unterminated PROFILEDATA section");
    latenciesMs.sort((a, b) => a - b);
    const slowFrameCount = refreshMs !== null
      ? (latenciesMs.length > 0 ? latenciesMs.filter(ms => ms > refreshMs).length : 0)
      : null;
    sections.push({
      window, header, rows,
      rowCount: rows.length + nonzeroFlagCount + invalidTimestampCount,
      nonzeroFlagCount,
      invalidTimestampCount,
      validFrameCount: rows.length,
      latencyMs: latenciesMs.length > 0
        ? {
            median: percentile(latenciesMs, 0.5),
            p95: percentile(latenciesMs, 0.95),
            p99: percentile(latenciesMs, 0.99),
            max: latenciesMs[latenciesMs.length - 1],
          }
        : null,
      slowFrameCount,
      slowFrameShare: slowFrameCount !== null && rows.length > 0 ? slowFrameCount / rows.length : null,
    });
    i = j + 1;
  }
  if (sections.length === 0) throw new Error("no PROFILEDATA section found in gfxinfo framestats output");

  let frameCompletionRate: FrameCompletionRate | null = null;
  if (options.measurementWindowMs !== undefined) {
    const windowMs = validateMeasurementWindowMs(options.measurementWindowMs);
    let section: FrameStatsResult["sections"][number] | null = null;
    if (options.rateSectionIndex !== undefined) {
      if (!Number.isSafeInteger(options.rateSectionIndex)
        || options.rateSectionIndex < 0
        || options.rateSectionIndex >= sections.length) {
        throw new Error(`rateSectionIndex ${String(options.rateSectionIndex)} out of range (sections: ${sections.length})`);
      }
      section = sections[options.rateSectionIndex];
    } else if (sections.length === 1) {
      // Unambiguous single-section selection; never silently taken when sections > 1.
      section = sections[0];
    }
    if (section !== null && section.rows.length >= 2) {
      const all = section.rows;
      let monotonic = true;
      for (let k = 1; k < all.length; k++) if (all[k].frameCompletedNs <= all[k - 1].frameCompletedNs) monotonic = false;
      if (monotonic) {
        const spanMs = Number(all[all.length - 1].frameCompletedNs - all[0].frameCompletedNs) / 1e6;
        if (spanMs > windowMs) throw new Error("measurementWindowMs does not contain section span");
        frameCompletionRate = {
          framesPerSecond: (all.length / windowMs) * 1000,
          source: "HWUI",
          windowMs,
        };
      }
    }
  }
  return { sections, frameCompletionRate };
}
