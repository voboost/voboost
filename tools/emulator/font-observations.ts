import { queuedSnapshot, sequence } from "./functional-observations.ts";
import type { LogRecord } from "./installer-logs.ts";
import type { AgentDiagnostic } from "./installer-logs.ts";
import type { UiNode } from "./installer-ui.ts";

const schemas = {
  view: "activity_class attached focused layout_height layout_width spanned script text_length view_class view_id",
  paint: "fake_bold letter_spacing loader output_sha256 provenance scale_x skew_x source_sha256 text_size",
  layout: "accessibility_class ellipsis_count ellipsis_scan_complete height line_count screen_x screen_y width",
  sample: "bounds_bottom bounds_left bounds_right bounds_top sample_id width",
};
const fonts: Record<string, [string, string]> = {
  "asset-regular": ["b90b145194f1206b3d515e05d2bc2f27f2411aa0b6897f61755d7b812fe083c1", "4f03e27101265730adf4634746350eb44aef725e8d8601a48a366a03cf8b6bd0"],
  "resource-semibold": ["305f942097a1b65d1dca1c357c0197acf62346eea83e2fbe95cc55f62821f000", "e8e460cdb59f41367d7c345aa1004f7d65c14c27685e60da5646a1385e846777"],
};
const integer = (value: unknown, minimum = 0) => Number.isSafeInteger(value) && Number(value) >= minimum;
const finite = (value: unknown) => typeof value === "number" && Number.isFinite(value);

/** Structural evidence only: endpoint authentication and independent PNG review remain separate gates. */
export function fontCapture(rows: AgentDiagnostic[], editor: UiNode): AgentDiagnostic[] {
  const views = rows.filter((row) => row.agent === "font-cyrillic" && row.event === "font.visual.view");
  if (!views.length) throw new Error("actual focused font capture missing");
  const view = views.at(-1)!, id = view.fields.capture_id;
  if (rows.some((row, index) => index > 0 && row.record.sequence <= rows[index - 1].record.sequence)) throw new Error("font capture sequence is not increasing");
  const capture = rows.filter((row) => row.agent === "font-cyrillic" && row.fields.capture_id === id);
  if (capture.length !== 6) throw new Error("complete unique font capture parts missing or duplicated");
  for (const row of capture) {
    const kind = row.event.slice("font.visual.".length) as keyof typeof schemas;
    if (!Object.hasOwn(schemas, kind) || row.outcome !== "captured" || row.record.level !== "info" || row.record.source.kind !== "agent" || row.record.source.id !== "font-cyrillic" || !integer(row.fields.epoch, 1) || typeof id !== "string" || id.length > 64 || !integer(Number(id.split(":")[1]), 1) || Number(id.split(":")[1]) > 64 || !new RegExp(`^${row.fields.epoch}:[1-9][0-9]*$`).test(id) || JSON.stringify(Object.keys(row.fields).sort()) !== JSON.stringify(["capture_id", "epoch", ...schemas[kind].split(" ")].sort())) throw new Error("malformed font capture schema or epoch");
  }
  const one = (kind: string) => {
    const found = capture.filter((row) => row.event === `font.visual.${kind}`);
    if (found.length !== 1) throw new Error("font capture part duplicated or missing");
    return found[0].fields;
  };
  const v = one("view"), p = one("paint"), l = one("layout");
  const [left, top, right, bottom] = editor.bounds;
  const script = /[\u0400-\u052f]/u.test(editor.text.slice(0, 4096)) ? "cyrillic" : editor.text.length > 4096 ? "unknown-truncated" : "non-cyrillic";
  if (editor.packageName !== "com.pateonavi.naviapp" || !editor.focused || editor.className !== "android.widget.EditText" || typeof v.view_class !== "string" || v.view_class.length > 255 || !/^[A-Za-z_$][A-Za-z0-9_.$]*$/.test(v.view_class) || v.view_id === "unknown" || v.view_id !== editor.resource || typeof v.activity_class !== "string" || !v.activity_class || v.activity_class.length > 255 || v.attached !== true || v.focused !== true || typeof v.spanned !== "boolean" || v.text_length !== editor.text.length || v.script !== script || !integer(v.layout_width, 1) || !integer(v.layout_height, 1) || v.layout_width !== right - left || v.layout_height !== bottom - top) throw new Error("font capture differs from actual focused UI editor");
  if (l.accessibility_class !== editor.className || l.screen_x !== left || l.screen_y !== top || l.width !== right - left || l.height !== bottom - top || !integer(l.line_count, 1) || !integer(l.ellipsis_count) || l.ellipsis_count !== 0 || l.ellipsis_scan_complete !== true) throw new Error("font capture layout differs or is clipped");
  const expected = fonts[String(p.loader)];
  if (p.provenance !== "corrected" || !expected || p.source_sha256 !== expected[0] || p.output_sha256 !== expected[1]) throw new Error("actual Paint corrected Typeface provenance unavailable");
  if (![p.text_size, p.scale_x].every((value) => finite(value) && Number(value) > 0) || ![p.letter_spacing, p.skew_x].every(finite) || typeof p.fake_bold !== "boolean") throw new Error("malformed actual Paint metrics");
  const samples = capture.filter((row) => row.event === "font.visual.sample");
  if (JSON.stringify(samples.map((row) => row.fields.sample_id).sort()) !== JSON.stringify(["cyrillic", "latin", "yo"])) throw new Error("complete unique public font samples missing");
  for (const { fields: f } of samples) {
    if (!finite(f.width) || Number(f.width) <= 0 || ![f.bounds_left, f.bounds_right, f.bounds_top, f.bounds_bottom].every((value) => Number.isSafeInteger(value)) || Number(f.bounds_right) <= Number(f.bounds_left) || Number(f.bounds_bottom) <= Number(f.bounds_top)) throw new Error("malformed public font sample metrics");
  }
  return capture;
}

export type FontAuthority = {
  boot: string; baselineBoot: string; afterSequence: number; records: LogRecord[];
  process: string; targetPid: string; targetStarttime: string; endpointEpoch: string;
  generationId: string; catalogSha256: string; activityClass: string;
  daemonPid: string; daemonStarttime: string; desiredDocumentGeneration: string;
};

/** Proves actual Paint provenance at this endpoint, never applied desired state or PNG appearance. */
export function actualFont(rows: AgentDiagnostic[], editor: UiNode, authority: FontAuthority): AgentDiagnostic[] {
  const endpointKeys = ["process", "targetPid", "targetStarttime", "endpointEpoch", "generationId", "catalogSha256"];
  const keys = [...endpointKeys, "bootId", "daemonPid", "daemonStarttime", "desiredDocumentGeneration"];
  const decimal = (value: unknown, zero = false) => typeof value === "string" && (zero ? /^(0|[1-9][0-9]*)$/ : /^[1-9][0-9]*$/).test(value) && BigInt(value) <= 18446744073709551615n;
  if (![authority.daemonPid, authority.daemonStarttime].every(value => decimal(value)) || !decimal(authority.desiredDocumentGeneration, true)) throw new Error("independent font daemon or desired revision missing");
  if (!authority.boot || authority.boot !== authority.baselineBoot || authority.process !== "com.pateonavi.naviapp" || !integer(authority.afterSequence) || ![authority.targetPid, authority.targetStarttime, authority.endpointEpoch].every((value) => typeof value === "string" && /^[1-9][0-9]*$/.test(value)) || ![authority.generationId, authority.catalogSha256].every((value) => /^[0-9a-f]{64}$/.test(value)) || !authority.activityClass) throw new Error("independent current font endpoint authority missing or crossed a boot");
  sequence(authority.records);
  const capture = fontCapture(rows, editor);
  const first = capture[0].record.sequence;
  const endpointMatches = (fields: Record<string, unknown>) => endpointKeys.every((key) => fields[key] === authority[key as keyof FontAuthority]);
  const matches = (fields: Record<string, unknown>) => endpointMatches(fields) && fields.bootId === authority.boot && fields.daemonPid === authority.daemonPid && fields.daemonStarttime === authority.daemonStarttime && fields.desiredDocumentGeneration === authority.desiredDocumentGeneration;
  const queued = authority.records.filter((record) => record.message === "desired-snapshot-queued").map((record) => ({ record, fields: queuedSnapshot(record) })).filter((row) => row.fields.process === authority.process);
  const prior = queued.findLast((row) => row.record.sequence < first);
  if (!prior || !endpointMatches(prior.fields)) throw new Error("current root queued font endpoint identity missing or differs");
  if (queued.some((row) => row.record.sequence >= first && !endpointMatches(row.fields))) throw new Error("newer root font endpoint identity conflicts with capture");
  for (const row of capture) {
    if (row.record.sequence <= authority.afterSequence || !authority.records.some((record) => record.sequence === row.record.sequence && JSON.stringify(record) === JSON.stringify(row.record))) throw new Error("fresh canonical font capture membership missing");
    if (JSON.stringify(Object.keys(row.record.fields).sort()) !== JSON.stringify(keys.toSorted()) || !matches(row.record.fields)) throw new Error("authoritative native font endpoint identity missing or differs");
  }
  const epoch = capture[0].fields.epoch;
  if (rows.some((row) => row.agent === "font-cyrillic" && row.event === "font.visual.lifecycle" && matches(row.record.fields) && (row.record.sequence > first || row.fields.epoch !== epoch))) throw new Error("font observer lifecycle changed across current capture");
  if (capture.find((row) => row.event === "font.visual.view")!.fields.activity_class !== authority.activityClass) throw new Error("font capture differs from current focused Activity");
  return capture;
}

/** Optional exact Paint identity diagnostics, separate from functional input and visible typography. */
export function fontDiagnosticOnly(number: number, id: string): boolean {
  const ids: Record<number, readonly string[]> = {
    9: ["actual-cyrillic-font-provenance"],
    19: ["moscow-font-provenance", "yo-font-provenance", "mixed-prefix-font-provenance", "mixed-latin-font-provenance", "mixed-yo-font-provenance"],
  };
  return (ids[number] ?? []).includes(id);
}
