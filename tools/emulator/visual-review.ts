import { fullFunctionalCase, semanticBoot, requiredSubchecksPassed } from "./full-semantic.ts";
import {nativeCaptureAction,nativeTreeAction,launcherThemeSnapshotProof,selectedLauncherTheme} from "../../release/emulator-gate.ts";
import { createHash, createPublicKey, randomUUID, verify } from "node:crypto";
import { lstatSync, readFileSync, realpathSync, writeFileSync } from "node:fs";
import { isAbsolute, join, resolve, sep } from "node:path";
import { center, focusedApplication, parseUi, validatePng, secondaryLauncherProof, nativePointerRequest } from "./installer-ui.ts";
import { response as nativeResponse, verificationContract } from "./native-session.ts";
import { NativeTreePartial } from "./native-tree.ts";
import type { SettingsCopy } from "./settings-copy.ts";

export type ReviewAuthority = { publicKey: string; receive: (requestPath: string) => Promise<string | null> };
type Artifact = { path: string; sha256: string; actionId?: number };
type Criterion = { id: string; expected: string };
type Target = { caseNumber: number; id: string; locale: string | null; criteria: Criterion[]; captures: Array<{ boot: string; locale: string; png: Artifact; hierarchy?: Artifact; windowProof?: Artifact; display: "primary" | "secondary" }> };
export type ReviewRequest = {
  schemaVersion: 1; invocationId: string; challenge: string; publicKeySha256: string;
  identity: any; runtime: any; actionCount: number; actionsSha256: string;
  sources: Artifact[]; observations: Artifact[]; targets: Target[];
};
const hash = (bytes: Buffer | string) => createHash("sha256").update(bytes).digest("hex");
const encoded = (value: unknown) => Buffer.from(JSON.stringify(value) + "\n");
const cliReceipt = (value: Buffer) => {
  const lines = value.toString("utf8").split(/\r?\n/).filter(line => line.startsWith("{"));
  if (lines.length !== 1) throw new Error("visual observation requires exactly one CLI JSON receipt");
  return JSON.parse(lines[0]);
};
const keys = (value: any, expected: string[], label: string) => {
  if (!value || typeof value !== "object" || Array.isArray(value) || JSON.stringify(Object.keys(value).sort()) !== JSON.stringify([...expected].sort())) throw new Error(`${label}: unknown or missing fields`);
};
function bytes(path: string, root?: string): Buffer {
  if (!isAbsolute(path) || realpathSync(path) !== resolve(path) || (root && !resolve(path).startsWith(realpathSync(root) + sep))) throw new Error("review artifact outside its evidence directory or through a symlink");
  const stat = lstatSync(path);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size > 32 * 1024 * 1024) throw new Error("review artifact is not a bounded regular file");
  return readFileSync(path);
}
function checked(artifact: Artifact, root?: string): Buffer {
  const value = bytes(artifact.path, root);
  if (hash(value) !== artifact.sha256) throw new Error("review artifact bytes differ");
  return value;
}
function publicIdentity(publicKey: string): string {
  const key = createPublicKey(publicKey);
  if (key.asymmetricKeyType !== "ed25519") throw new Error("visual review requires a session Ed25519 public key");
  return hash(key.export({ type: "spki", format: "der" }));
}
const typography: Criterion[] = [
  { id: "readable-glyphs", expected: "Actual glyphs are readable and match the source-bound text without missing or substituted glyphs." },
  { id: "natural-spacing", expected: "Actual letters have natural spacing with no inserted interletter gaps or spaces." },
];
const controls: Criterion[] = [
  { id: "typography", expected: "Actual labels and descriptions are readable and match the source-bound locale." },
  { id: "spacing", expected: "Actual checkbox, radio and section spacing matches the retained source-bound measurements." },
  { id: "clipping", expected: "Actual text and controls have no clipping, overlap or unintended truncation." },
];
const buttons: Criterion[] = [
  { id: "padding", expected: "Actual panel and dialog buttons retain the source-bound OEM padding and alignment." },
  { id: "wrapping", expected: "Actual button labels wrap correctly and remain fully readable in the selected locale." },
  { id: "clipping", expected: "Actual panel and dialog buttons and adjacent descriptions have no clipping or overlap." },
];
export function visualChecks(number: number, theme?: {label:string;style:"metal"|"color"}): Array<{ id: string; locale: string | null; criteria: Criterion[] }> {
  if (number === 9) return [{ id: "visual-font-cyrillic", locale: null, criteria: typography }];
  if (number === 19) return [{ id: "visual-navi-input-spacing", locale: null, criteria: typography }];
  if (number === 12) return [{ id: "visual-launcher-third-party-icons", locale: null, criteria: [
    { id: "visible-size-radius", expected: "Classic left metal icons match the actual Cloud Space/HiCar native frame at its original size and shape, without scaling the frame. Full-color icons on Speedy Dash and on the right surface match the common native Cloud Space/HiCar visible size and corner radius; transparent padding and near-transparent halos are not counted." },
    { id: "plain-app-grid", expected: "The right all-app grid shows ordinary full-color third-party icons, without the metal treatment, and retains their details." },
    { id: "theme-home-surface", expected: theme?.style==="color" ? "The selected "+theme.label+" theme uses ordinary full-color icons on the left surface, matching actual native Cloud Space/HiCar size and corner radius, without metal treatment." : theme?.style==="metal" ? "The selected "+theme.label+" theme uses steel tones on the left surface, with upper illumination and steel tones matching the actual stock icons, preserving transparency and details without inversion. Background-free symbols remain frameless; the unscaled Cloud Space/HiCar frame is used only when safe background removal is unavailable." : "The left surface matches the current selected OEM theme: Classic uses steel tones with OEM frame and upper illumination; Speedy Dash uses ordinary full-color icons. Selection must be proved before and after both captures." },
    { id: "reference-availability", expected: "Both actual launcher surfaces, the third-party fixture and the actual Cloud Space/HiCar references are visible in the paired captures. An absent reference or fixture is a failed assessment, never assumed from source or bitmap dimensions." },
  ] }];
  if (number === 18) return [{ id: "visual-oem-stock-icons", locale: null, criteria: [
    { id: "stock-icon-retention", expected: "Actual original stock icon images and their labels remain visually intact against the pre-install stock grid captures." },
    { id: "stock-icon-binding", expected: "Actual stock grid icon images remain bound to their original labels and positions after classification and fixture removal." },
  ] }];
  if (number === 20 || number === 21) return ["en", "ru", "zh"].map(locale => ({ id: `visual-${number === 20 ? "control" : "action-button"}-layout-${locale}`, locale, criteria: number === 20 ? controls : buttons }));
  return [];
}
function observedNodes(evidence: any, action: any, output?: string) {
  const artifact = action.artifacts.find((item: any) => item.kind === "stdout");
  if (!artifact) throw new Error("visual hierarchy artifact missing");
  const receipt = cliReceipt(checked(artifact, output));
  if (action.nativeOperation === "session.ui") return nativeTreeAction(evidence, action);
  if (captureRequest(action, output, evidence)?.action !== "snapshot") throw new Error("visual hierarchy operation differs");
  keys(receipt, ["schemaVersion", "serial", "body"], "visual hierarchy");
  if (receipt.schemaVersion !== 1 || receipt.serial !== evidence.runtime.serial || typeof receipt.body !== "string") throw new Error("visual hierarchy receipt differs");
  return parseUi(receipt.body);
}
const isObservation = (request: any) => request?.action === "snapshot" || request?.op === "session.ui";
const retainedRequest = (action: any) => action.artifacts.some((item: any) => item.kind === "request" && item.path === (action.nativeOperation ? action.nativeRequest : action.args[1]));
function captureRequest(action: any, output: string | undefined, evidence: any): any | null {
  if (action.nativeOperation && action.nativeOperation !== "session.screenshot") {
    if (!["session.ui", "input.tap"].includes(action.nativeOperation)) return null;
    if (action.exitCode !== 0 || action.timedOut || action.signal || action.overflow) return null;
    if (evidence.schemaVersion !== 4 || JSON.stringify(evidence.verificationContract) !== JSON.stringify(verificationContract)) throw new Error("native visual contract differs");
    const requestArtifact=action.artifacts.find((a:any)=>a.kind==="request" && a.path===action.nativeRequest);
    // The public retained request omits ownership credentials and therefore has
    // a different path from the private dispatch request in args[4]. Its hash
    // and response correlation are validated below and by nativeTreeAction.
    if (!requestArtifact || action.args[0]!=="test" || action.args[1]!=="--root" || action.args[3]!=="--request") throw new Error("native visual request absent");
    const request=JSON.parse(checked(requestArtifact,output).toString("utf8"));
    if (request.op!==action.nativeOperation || request.profile!==evidence.voyemuProfile || request.session?.id!==evidence.runtime.sessionId || action.context?.runtime?.sessionId!==evidence.runtime.sessionId || action.context?.runtime?.pid!==evidence.runtime.pid || action.context?.runtime?.port!==evidence.runtime.port || action.context?.serial!==evidence.runtime.serial || action.context?.kit!==evidence.identity.package) throw new Error("native visual request authority differs");
    const stdout=action.artifacts.find((a:any)=>a.kind==="stdout");
    if (!stdout) throw new Error("native visual response absent");
    nativeResponse({exitCode:0,stdout:checked(stdout,output).toString("utf8")},request);
    if(request.op==="session.ui") {
      try { nativeTreeAction(evidence,action); }
      catch(error) { if(error instanceof NativeTreePartial) return null; throw error; }
    }
    return request;
  }
  if(action.nativeOperation==="session.screenshot") {
    nativeCaptureAction(evidence,action);
    return {op:"session.screenshot",output:action.nativeAuthority.output,display:action.nativeAuthority.display};
  }
  if (action.args[0] !== "--emulator-check" || action.exitCode !== 0 || action.timedOut || action.signal || action.overflow) return null;
  const request = action.artifacts.find((item: any) => item.kind === "request" && item.path === action.args[1]);
  if (!request) throw new Error("visual capture request artifact missing");
  const retained = JSON.parse(checked(request, output).toString("utf8"));
  if (!["snapshot", "screenshot", "open-secondary-launcher", "secondary-capture-proof", "tap", "observe-settings-uninstall"].includes(retained.action)) return null;
  const context = action.context;
  if (context?.kit !== evidence.identity.package || context?.serial !== evidence.runtime.serial || context?.runtime?.pid !== evidence.runtime.pid || context?.runtime?.port !== evidence.runtime.port || !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(context?.bootId ?? "")) throw new Error("visual capture action authority differs");
  return retained;
}
export function createReviewRequest(evidence: any, output: string, sources: string[], publicKey: string): ReviewRequest | null {
  const targets: Target[] = [];
  for (const number of [9, 12, 18, 19, 20, 21]) {
    const row = evidence.cases.find((item: any) => item.number === number);
    if (!row || row.status === "not-run") continue;
    let theme: {label:string;style:"metal"|"color"}|undefined;
    if(number===12) {
      const lifetime=row.subchecks.find((item:any)=>item.id==="launcher-icons-paired-surfaces"&&item.status==="passed");
      const path=lifetime?.artifacts.find((path:string)=>path.endsWith("-launcher-icons-lifetime.json"));
      if(!path) continue;
      const binding=JSON.parse(bytes(path,output).toString("utf8"));
      const selection=binding.themeSelection;
      if(!selection?.before || !selection?.after || JSON.stringify(selection.before.theme)!==JSON.stringify(selection.after.theme)) throw new Error("launcher theme pair lacks matching selections");
      for(const selected of [selection.before,selection.after]) {
        const action=evidence.actions.find((a:any)=>a.id===selected.actionId);
        const request=action && captureRequest(action,output,evidence);
        const artifact=action?.artifacts.find((a:any)=>a.kind==="stdout"&&a.path===selected.artifact.path&&a.sha256===selected.artifact.sha256);
        if(!artifact || !isObservation(request) || action.context?.bootId!==binding.boot || selected.boot!==binding.boot) throw new Error("launcher theme selection lacks same-boot actual snapshot");
        const nodes=observedNodes(evidence,action,output);
        if(action.nativeOperation) launcherThemeSnapshotProof(evidence,action,nodes,binding.boot);
        if(JSON.stringify(selectedLauncherTheme(nodes))!==JSON.stringify(selected.theme)) throw new Error("launcher theme selected-card proof differs");
      }
      const pairActions=evidence.actions.filter((a:any)=>a.artifacts.some((artifact:any)=>binding.pngs.includes(artifact.path)));
      if(pairActions.length!==2 || selection.before.actionId>=Math.min(...pairActions.map((a:any)=>a.id)) || selection.after.actionId<=Math.max(...pairActions.map((a:any)=>a.id))) throw new Error("launcher theme selection does not bracket paired captures");
      theme=selection.before.theme;
    }
    for (const definition of visualChecks(number,theme)) {
      if (!row.subchecks.some((item: any) => item.id === definition.id)) continue;
      const capturePaths = new Set(row.subchecks.filter((item: any) => item.id !== definition.id).flatMap((item: any) => item.artifacts));
      const actions = evidence.actions.filter((action: any) => action.artifacts.some((item: any) => item.kind === "request" && item.path === (action.nativeOperation ? action.nativeRequest : action.args[1])) && (row.actions.includes(action.id) && action.caseNumber === number || number === 18 && action.caseNumber === 1 && action.artifacts.some((item: any) => capturePaths.has(item.path))) && (!definition.locale || action.context?.locale === definition.locale));
      const requests = actions.map((action: any) => ({ action, request: captureRequest(action, output, evidence) }));
      const captures: Target["captures"] = [];
      for (const { action, request } of requests.filter((item: any) => (item.request?.action === "screenshot" || item.request?.op === "session.screenshot"))) {
        if ((number === 9 || number === 19) && !request.output.endsWith(`-input-${number}.png`)) continue;
        if (number === 12 && !/-launcher-icons-(home|grid)\.png$/.test(request.output)) continue;
        if ((number === 12 || number === 18 || number === 20 || number === 21) && !capturePaths.has(request.output)) continue;
        const png = action.artifacts.find((item: any) => item.kind === "png" && item.path === request.output);
        if (number === 12 && request.display === "secondary") {
          const opening = requests.findLast((item: any) => item.action.id < action.id && ["open-secondary-launcher","secondary-capture-proof"].includes(item.request?.action) && item.action.context.bootId === action.context.bootId);
          const artifact = opening?.action.artifacts.find((item: any) => item.kind === "stdout");
          if (!png || !artifact || png.boot !== action.context.bootId || png.locale !== action.context.locale || png.kit !== evidence.identity.package || png.runtime?.pid !== evidence.runtime.pid || png.runtime?.port !== evidence.runtime.port) throw new Error("secondary visual PNG lacks window authority");
          const receipt = cliReceipt(checked(artifact, output));
          keys(receipt, ["schemaVersion", "serial", "body"], "secondary window receipt");
          if (receipt.schemaVersion !== 1 || receipt.serial !== evidence.runtime.serial || secondaryLauncherProof(receipt.body).boot !== action.context.bootId) throw new Error("secondary window boot differs");
          validatePng(checked(png, output));
          captures.push({ boot: action.context.bootId, locale: action.context.locale, display: "secondary", png: { path: png.path, sha256: png.sha256, actionId: action.id }, windowProof: { path: artifact.path, sha256: artifact.sha256, actionId: opening.action.id } });
          continue;
        }
        const previous = requests.findLast((item: any) => item.action.id < action.id && isObservation(item.request) && item.action.context.bootId === action.context.bootId && item.action.context.locale === action.context.locale);
        const hierarchy = previous?.action.artifacts.find((item: any) => item.kind === "stdout");
        if (!png || !hierarchy || png.boot !== action.context.bootId || png.locale !== action.context.locale || png.kit !== evidence.identity.package || png.runtime?.pid !== evidence.runtime.pid || png.runtime?.port !== evidence.runtime.port) throw new Error(`visual PNG lacks its same-boot hierarchy or authority: case ${number}, action ${action.id}`);
        validatePng(checked(png, output));
        const nodes = observedNodes(evidence, previous!.action, output);
        if (number === 9 || number === 19) {
          const expected = number === 9 ? ["Москва"] : ["Москва", "Ёё", "Москва", "Москваmoscow", "Москваmoscowё"];
          if (nodes.filter(node => node.packageName === "com.pateonavi.naviapp" && node.className === "android.widget.EditText" && node.focused && node.text === expected[captures.length]).length !== 1) throw new Error("visual input hierarchy lacks the exact committed text");
        }
        captures.push({ boot: action.context.bootId, locale: action.context.locale, display: "primary", png: { path: png.path, sha256: png.sha256, actionId: action.id }, hierarchy: { path: hierarchy.path, sha256: hierarchy.sha256, actionId: previous!.action.id } });
      }
      if (!captures.length) continue;
      if ((number === 9 || number === 19) && captures.length !== (number === 19 ? 5 : 1)) continue;
      if (number === 18 && (!captures.some(capture => capture.png.path.endsWith("-oem-grid-stock-baseline.png")) || !captures.some(capture => capture.png.path.endsWith("-oem-grid-classification.png")))) continue;
      if (number === 12) {
        const pair = captures.filter(capture => /-launcher-icons-(home|grid)\.png$/.test(capture.png.path));
        if (pair.length !== 2 || pair.filter(capture => capture.display === "primary").length !== 1 || pair.filter(capture => capture.display === "secondary").length !== 1 || !pair.some(capture => capture.png.path.endsWith("-launcher-icons-home.png")) || !pair.some(capture => capture.png.path.endsWith("-launcher-icons-grid.png")) || pair[0].boot !== pair[1].boot) continue;
        const lifetime = row.subchecks.find((item: any) => item.id === "launcher-icons-paired-surfaces");
        if (lifetime?.status !== "passed") continue;
        const path = lifetime.artifacts.find((path: string) => path.endsWith("-launcher-icons-lifetime.json"));
        if (!path) continue;
        const binding = JSON.parse(bytes(path, output).toString("utf8"));
        if (binding.boot !== pair[0].boot || binding.process !== "com.qinggan.app.launcher" || !/^[1-9][0-9]*$/.test(binding.targetPid) || !/^[1-9][0-9]*$/.test(binding.targetStarttime) || JSON.stringify(binding.pngs) !== JSON.stringify(pair.map(capture => capture.png.path))) throw new Error("launcher visual pair lifetime binding differs");
        if (pair.some(capture => capture.display === "secondary" ? (() => { const proof = secondaryLauncherProof(cliReceipt(checked(capture.windowProof!, output)).body); return proof.boot !== binding.boot || proof.targetPid !== binding.targetPid || proof.targetStarttime !== binding.targetStarttime; })() : !observedNodes(evidence,evidence.actions.find((a:any)=>a.id===capture.hierarchy!.actionId),output).some(node => node.packageName === binding.process && node.visibleToUser))) throw new Error("launcher visual pair lacks actual launcher hierarchy");
        captures.splice(0, captures.length, ...pair);
      }
      targets.push({ caseNumber: number, ...definition, captures });
    }
  }
  if (!targets.length) return null;
  const observations = new Map<string, Artifact>();
  for (const action of evidence.actions) for (const artifact of action.artifacts) {
    checked(artifact, output);
    observations.set(artifact.path, { path: artifact.path, sha256: artifact.sha256, actionId: action.id });
  }
  for (const number of new Set(targets.map(target => target.caseNumber))) {
    const row = evidence.cases.find((item: any) => item.number === number);
    for (const path of row.subchecks.flatMap((item: any) => item.artifacts)) if (!observations.has(path)) observations.set(path, { path, sha256: hash(bytes(path)) });
  }
  return {
    schemaVersion: 1, invocationId: evidence.invocationId, challenge: randomUUID(), publicKeySha256: publicIdentity(publicKey),
    identity: structuredClone(evidence.identity), runtime: structuredClone(evidence.runtime), actionCount: evidence.actions.length, actionsSha256: hash(encoded(evidence.actions)),
    sources: [...new Set(sources)].sort().map(path => ({ path, sha256: hash(bytes(path)) })), observations: [...observations.values()], targets,
  };
}
function currentRequest(request: ReviewRequest, evidence: any, output: string, publicKey: string): void {
  if (request.schemaVersion !== 1 || request.invocationId !== evidence.invocationId || request.publicKeySha256 !== publicIdentity(publicKey) || JSON.stringify(request.identity) !== JSON.stringify(evidence.identity) || JSON.stringify(request.runtime) !== JSON.stringify(evidence.runtime) || hash(encoded(evidence.actions.slice(0, request.actionCount))) !== request.actionsSha256) throw new Error("visual review request identity or ordered actions differ");
  for (const artifact of [...request.sources, ...request.observations]) checked(artifact);
  for (const target of request.targets) for (const capture of target.captures) {
    checked(capture.png, output); checked((capture.hierarchy ?? capture.windowProof)!, output);
  }
}
export function verifyReview(request: ReviewRequest, receiptBytes: Buffer, evidence: any, output: string, publicKey: string): Map<string, { status: "passed" | "failed"; reason: string }> {
  currentRequest(request, evidence, output, publicKey);
  const receipt = JSON.parse(receiptBytes.toString("utf8"));
  if (!receiptBytes.equals(encoded(receipt))) throw new Error("visual review receipt must use exact single-document encoding");
  keys(receipt, ["payload", "signature"], "visual review receipt");
  keys(receipt.payload, ["schemaVersion", "requestSha256", "reviewer", "assessments"], "visual review payload");
  if (receipt.payload.schemaVersion !== 1 || receipt.payload.requestSha256 !== hash(encoded(request)) || receipt.payload.reviewer !== "root" || !/^[0-9a-f]{128}$/.test(receipt.signature) || !verify(null, encoded(receipt.payload), publicKey, Buffer.from(receipt.signature, "hex"))) throw new Error("visual review signature or request binding differs");
  const assessments = receipt.payload.assessments;
  if (!Array.isArray(assessments) || assessments.length !== request.targets.length) throw new Error("visual review target coverage differs");
  const result = new Map<string, { status: "passed" | "failed"; reason: string }>();
  for (const [index, target] of request.targets.entries()) {
    const assessment = assessments[index];
    keys(assessment, ["id", "criteria"], "visual assessment");
    if (assessment.id !== target.id || !Array.isArray(assessment.criteria) || assessment.criteria.length !== target.criteria.length || result.has(assessment.id)) throw new Error("visual review target coverage differs");
    for (const [ordinal, criterion] of target.criteria.entries()) {
      const observed = assessment.criteria[ordinal];
      keys(observed, ["id", "expected", "actual", "verdict"], "visual criterion");
      if (observed.id !== criterion.id || observed.expected !== criterion.expected || typeof observed.actual !== "string" || observed.actual.trim().length < 8 || observed.actual.length > 4096 || !["passed", "failed"].includes(observed.verdict)) throw new Error("visual criterion coverage or actual observation differs");
    }
    result.set(target.id, { status: assessment.criteria.every((item: any) => item.verdict === "passed") ? "passed" : "failed", reason: assessment.criteria.map((item: any) => `${item.id}: ${item.actual}`).join("; ") });
  }
  return result;
}
export function updateReviewedCases(evidence: any): void {
  for (const number of [9, 12, 18, 19, 20, 21]) {
    const row = evidence.cases.find((item: any) => item.number === number);
    if (!row || row.status === "not-run" || row.status === "failed") continue;
    row.status = row.subchecks.some((item: any) => item.status === "failed") ? "failed" : !requiredSubchecksPassed(row) ? "unavailable" : "passed";
    row.reason = row.status === "passed" ? "all independent runtime and authenticated visual observations passed" : "one or more independent required observations remain blocking";
  }
}
export async function consumeVisualReview(evidence: any, output: string, sources: string[], authority?: ReviewAuthority, boundary: () => Promise<void> = async () => {}): Promise<void> {
  if (!authority) return;
  keys(authority, ["publicKey", "receive"], "visual review authority");
  if (typeof authority.publicKey !== "string" || typeof authority.receive !== "function") throw new Error("invalid visual review authority");
  const publicKey = authority.publicKey, receive = authority.receive;
  await boundary();
  const request = createReviewRequest(evidence, output, sources, publicKey), requestPath = join(output, "visual-review-request.json");
  if (!request) return;
  writeFileSync(requestPath, encoded(request), { flag: "wx", mode: 0o600 });
  const receiptPath = await receive(requestPath);
  await boundary();
  if (!bytes(requestPath, output).equals(encoded(request))) throw new Error("visual review request file changed");
  currentRequest(request, evidence, output, publicKey);
  if (receiptPath === null) return;
  const receipt = bytes(receiptPath), results = verifyReview(request, receipt, evidence, output, publicKey);
  const retained = join(output, "visual-review-receipt.json");
  writeFileSync(retained, receipt, { flag: "wx", mode: 0o600 });
  for (const target of request.targets) {
    const row = evidence.cases.find((item: any) => item.number === target.caseNumber), checks = row.subchecks.filter((item: any) => item.id === target.id);
    if (checks.length !== 1) throw new Error("visual subcheck identity changed during assessment");
    Object.assign(checks[0], results.get(target.id), { artifacts: [requestPath, retained, ...target.captures.flatMap(capture => [capture.png.path, (capture.hierarchy ?? capture.windowProof)!.path]), ...request.sources.map(artifact => artifact.path)] });
  }
  updateReviewedCases(evidence);
}

export function actionCoverage(evidence: any, copy: SettingsCopy, normalAbsence: (result: any) => any, semanticFull = false): string[] | null {
  if (semanticFull && (evidence.functionalMode !== "full27" || evidence.imageCapture !== "deferred" || evidence.regressionScenario !== undefined)) throw new Error("semantic action coverage requires full27 no-images evidence");
  const acceptedBoot = (boot:any) => boot.status === "passed" || (semanticFull && semanticBoot(boot));
  for (const locale of ["en", "ru", "zh"] as const) {
    const buttons = copy.languages[locale].layoutTopology.panelTargets.filter(target => target.class === "android.widget.Button").map(target => target.value).sort();
    if (JSON.stringify(buttons) !== JSON.stringify(["uninstall"])) throw new Error("actual panel button topology differs from the covered action inventory");
  }
  const layout = evidence.cases.find((row: any) => row.number === 21), removal = evidence.cases.find((row: any) => row.number === 17);
  const captures = ["en", "ru", "zh"].map(locale => layout?.subchecks.find((check: any) => check.id === (semanticFull ? `uninstall-action-measurements-cancel-${locale}` : `uninstall-action-captures-cancel-${locale}`)));
  if (captures.some(check => check?.status !== "passed" || check.artifacts.length < 2) || (semanticFull ? !fullFunctionalCase(removal) : removal?.status !== "passed") || !removal.actions.length || !evidence.boots.some((boot: any) => boot.caseNumber === 17 && boot.kind === "settings-uninstall" && acceptedBoot(boot))) return null;
  const observer = evidence.actions.find((action: any) => removal.actions.includes(action.id) && action.caseNumber === 17 && action.artifacts.some((item: any) => item.kind === "request" && item.path === action.args[1]) && captureRequest(action, undefined, evidence)?.action === "observe-settings-uninstall");
  if (!observer) return null;
  const tapped = (number: number, locale: string, caption: string) => {
    const actions = evidence.actions.filter((action: any) => action.caseNumber === number && action.context?.locale === locale && retainedRequest(action)).map((action: any) => ({ action, request: captureRequest(action, undefined, evidence) }));
    return actions.filter((item: any) => (item.request?.action === "tap" || item.request?.op === "input.tap")).find((item: any) => {
      const previous = actions.findLast((candidate: any) => candidate.action.id < item.action.id && isObservation(candidate.request) && candidate.action.context.bootId === item.action.context.bootId);
      const stdout = previous?.action.artifacts.find((artifact: any) => artifact.kind === "stdout");
      if (!stdout) return false;
      const nodes = observedNodes(evidence,previous.action);
      const matches = nodes.filter(node => node.packageName === "com.qinggan.app.setting" && focusedApplication(node) && node.enabled && node.text === caption);
      if(matches.length !== 1 || JSON.stringify(center(matches[0])) !== JSON.stringify([item.request.x,item.request.y])) return false;
      if(item.request.op === "input.tap") {
        if(previous.action.nativeOperation !== "session.ui") return false;
        const node=matches[0], w=node.windowRef!, rect=(b:number[])=>`[${b[0]},${b[1]}][${b[2]},${b[3]}]`;
        const tree=nativeResponse({exitCode:0,stdout:checked(stdout).toString("utf8")},previous.request);
        const pointer=nativePointerRequest({action:"tap",x:item.request.x,y:item.request.y,
          expectedWindow:{id:w.id,windowType:w.type,bounds:rect(w.bounds),title:w.title,packageName:w.rootPackage,displayId:0},
          expectedTarget:{windowBounds:rect(w.bounds),windowTitle:w.title,rootPackage:w.rootPackage,className:node.className,windowId:w.id,windowType:w.type,packageName:node.packageName,bounds:rect(node.rawBounds ?? node.bounds),resource:node.resource,text:node.text}
        },tree,{sessionId:evidence.runtime.sessionId,pid:evidence.runtime.pid,boot:item.action.context.bootId});
        for(const key of ["screen","x","y","expected"]) if(JSON.stringify(item.request[key])!==JSON.stringify(pointer.fields[key])) throw new Error("native action source fence differs");
        if(item.request.count!==undefined || item.request.interval_ms!==undefined) throw new Error("native action must be a single tap");
        const artifact=item.action.artifacts.find((a:any)=>a.kind==="stdout");
        const result=nativeResponse({exitCode:0,stdout:checked(artifact).toString("utf8")},item.request);
        if(result.ok!==true || result.session_id!==evidence.runtime.sessionId || result.sdk_completed!==true || result.pointer_released!==true || result.screen!=="primary" || result.sdk_display_id!==0 || result.pre_dispatch_fenced!==true || result.atomic_ui_input!==false || result.occlusion!=="known_window_bounds_only" || result.touchability!=="unknown" || result.visible_effect_verified!==false || result.requested_count!==1 || result.dispatched_count!==1) throw new Error("native action completion differs");
      }
      return true;
    })?.action;
  };
  for (const locale of ["en", "ru", "zh"] as const) {
    const button = tapped(21, locale, copy.languages[locale].uninstall.buttonText), cancel = tapped(21, locale, copy.languages[locale].uninstall.cancelLabel);
    if (!button || !cancel || button.id >= cancel.id) return null;
  }
  const locale = observer.context.locale as "en" | "ru" | "zh", button = tapped(17, locale, copy.languages[locale].uninstall.buttonText), confirm = tapped(17, locale, copy.languages[locale].uninstall.confirmLabel);
  if (!button || !confirm || button.id >= confirm.id || observer.id >= button.id) return null;
  const stdout = observer.artifacts.find((artifact: any) => artifact.kind === "stdout");
  if (!stdout) return null;
  const text = checked(stdout).toString("utf8"), observed = cliReceipt(Buffer.from(text));
  const absence = normalAbsence({ exitCode: 0, stdout: text, stderr: "" });
  if (observed.schemaVersion !== 1 || observed.serial !== evidence.runtime.serial || !absence.transactionId || absence.bootId !== evidence.boots.find((boot: any) => boot.caseNumber === 17 && boot.kind === "settings-uninstall" && acceptedBoot(boot))?.after) return null;
  return [...captures.flatMap(check => check.artifacts), ...observer.artifacts.map((artifact: any) => artifact.path)];
}
