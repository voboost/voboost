import assert from "node:assert/strict";
import { createHash, generateKeyPairSync, sign } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { signAssessments } from "./manual-review.ts";
import test, { after } from "node:test";
import { normalAbsence,selectedLauncherTheme } from "../../release/emulator-gate.ts";
import { png, boot } from "../tests/emulator-gate-fixture.ts";
import { verificationContract } from "./native-session.ts";
import {parseUi} from "./installer-ui.ts";
import { windowModel } from "./window-fixture.ts";
import { actionCoverage, consumeVisualReview, createReviewRequest, verifyReview, visualChecks, type ReviewRequest } from "./visual-review.ts";
import type { SettingsCopy } from "./settings-copy.ts";

const work = fileURLToPath(new URL("../../build/visual-review-tests/", import.meta.url));
const ownedFixtures = new Set<string>();
after(() => { for (const path of ownedFixtures) rmSync(path, { recursive: true, force: true }); });
const hash = (value: Buffer | string) => createHash("sha256").update(value).digest("hex");
const encoded = (value: unknown) => Buffer.from(JSON.stringify(value) + "\n");
const absence = { transactionId: "a".repeat(64), bootId: "22222222-2222-2222-2222-222222222222", checks: ["root", "app-data", "init-file", "package", "app-op", "mounts", "processes", "init-services", "policy"] };
const keys = () => {
  const pair = generateKeyPairSync("ed25519");
  return { privateKey: pair.privateKey, publicKey: pair.publicKey.export({ type: "spki", format: "pem" }).toString() };
};
function fixture() {
  mkdirSync(work, { recursive: true });
  const output = mkdtempSync(join(work, "signed-visual-")), authority = keys();
  ownedFixtures.add(output);
  const evidence: any = {
    invocationId: "33333333-3333-3333-3333-333333333333", identity: { package: "b".repeat(64), closure: "c".repeat(64), dmg: "d".repeat(64), machine: "e".repeat(64), cli: "f".repeat(64) }, runtime: { pid: 41, port: 5554, serial: "emulator-5554" }, actions: [], boots: [],
    cases: [1, 9, 12, 18, 19, 20, 21, 17].map(number => ({ number, status: number === 17 ? "not-run" : "unavailable", actions: [], subchecks: visualChecks(number).map(check => ({ id: check.id, status: "unavailable", artifacts: [] })) })),
  };
  const source = join(output, "source-copy.json"); writeFileSync(source, encoded({ sourceBound: "synthetic host fixture, never Android proof" }));
  const action = (number: number, locale: string, request: any, body?: any) => {
    const id = evidence.actions.length + 1, requestPath = join(output, `${id}-request.json`), stdout = join(output, `${id}-stdout.txt`);
    writeFileSync(requestPath, encoded(request)); writeFileSync(stdout, Buffer.concat([Buffer.from("[INFO] Host protocol fixture\n"), encoded(body ?? { schemaVersion: 1, serial: evidence.runtime.serial, body: "" })]));
    const row: any = { id, caseNumber: number, args: ["--emulator-check", requestPath], exitCode: 0, timedOut: false, signal: null, overflow: false, context: { kit: evidence.identity.package, runtime: { pid: 41, port: 5554 }, serial: evidence.runtime.serial, bootId: boot, locale }, artifacts: [{ kind: "request", path: requestPath, sha256: hash(readFileSync(requestPath)) }, { kind: "stdout", path: stdout, sha256: hash(readFileSync(stdout)) }] };
    evidence.actions.push(row); evidence.cases.find((row: any) => row.number === number).actions.push(id); return row;
  };
  const hierarchy = (text: string, settings = false) => {
    const value = windowModel().replace('text=""', `text="${text}"`);
    return settings ? value.replaceAll("com.pateonavi.naviapp", "com.qinggan.app.setting") : value;
  };
  const capture = (number: number, locale: string, name: string, text = "", settings = false) => {
    const snapshot = action(number, locale, { action: "snapshot" }, { schemaVersion: 1, serial: evidence.runtime.serial, body: hierarchy(text, settings) });
    const path = join(output, `${evidence.actions.length + 1}-${name}.png`); writeFileSync(path, png());
    const screenshot = action(number, locale, { action: "screenshot", output: path }, { schemaVersion: 1, serial: evidence.runtime.serial, png: path });
    screenshot.artifacts.push({ kind: "png", path, sha256: hash(readFileSync(path)), boot, locale, kit: evidence.identity.package, runtime: { pid: 41, port: 5554 } });
    return [snapshot.artifacts[1].path, path];
  };
  const themeSnapshot=(selected="Classic")=>{
    const xml=hierarchy("",true);
    const template=/<node [^>]*><\/node>/u.exec(xml)![0];
    const node=(resource:string,label="",chosen=false,children="",index=0)=>template.replace("index=\"0\"","index=\""+index+"\"").replace(/resource-id="[^"]*"/,'resource-id="com.qinggan.app.setting:id/'+resource+'"').replace('text=""','text="'+label+'"').replace('selected="false"','selected="'+chosen+'"').replace('clickable="false"','clickable="true"').replace('</node>',children+'</node>');
    const cards=["Classic","Speedy Dash","Other"].map((label,index)=>node("rl_root","",label===selected,node("tv_title",label,label===selected),index)).join("");
    const body=xml.replace(/<node [^>]*><\/node>/u,node("recyclerview_theme_pic","",false,cards));
    const row=action(12,"en",{action:"snapshot"},{schemaVersion:1,serial:evidence.runtime.serial,body});
    const artifact=row.artifacts.find((a:any)=>a.kind==="stdout");
    return {theme:selectedLauncherTheme(parseUi(body)),boot,actionId:row.id,artifact:{path:artifact.path,sha256:artifact.sha256}};
  };
  const proof = (number: number, id: string, artifacts: string[], status = "passed") => evidence.cases.find((row: any) => row.number === number).subchecks.push({ id, status, artifacts });
  proof(9, "actual-cyrillic-font-provenance", capture(9, "en", "input-9", "Москва"));
  // This earlier panel screenshot must never become an input typography target.
  capture(9, "en", "panel-extra", "unrelated", true);
  const stock = capture(1, "en", "oem-grid-stock-baseline", "stock", true);
  proof(18, "oem-stock-baseline", stock);
  proof(18, "oem-grid-retained", capture(18, "en", "oem-grid-classification", "stock", true));
  proof(18, "oem-icon-bind-provenance", [], "unavailable");
  for (const text of ["Москва", "Ёё", "Москва", "Москваmoscow", "Москваmoscowё"]) proof(19, `font-${evidence.actions.length}`, capture(19, "en", "input-19", text));
  for (const locale of ["en", "ru", "zh"]) {
    proof(20, `panel-captures-${locale}`, capture(20, locale, `${locale}-panel`, "panel", true));
    proof(21, `uninstall-action-captures-cancel-${locale}`, [...capture(21, locale, `${locale}-action-panel`, "panel", true), ...capture(21, locale, `${locale}-action-dialog`, "dialog", true)]);
  }
  proof(21, "all-action-runtime-coverage", [], "unavailable");
  const nativeSnapshot = (row: any) => {
    Object.assign(evidence,{schemaVersion:4,verificationContract,voyemuProfile:"free-09"});
    evidence.runtime.sessionId="a".repeat(32);
    const stdout=row.artifacts.find((a:any)=>a.kind==="stdout"), legacy=JSON.parse(readFileSync(stdout.path,"utf8").split("\n").find((s:string)=>s.startsWith("{"))!);
    const nodes=parseUi(legacy.body), rect=(b:readonly number[])=>({x:b[0],y:b[1],width:b[2]-b[0],height:b[3]-b[1]});
    const tree={format:"voyemu.ui.v1",source:"android.ui",profile:evidence.voyemuProfile,session_id:evidence.runtime.sessionId,source_instance:"observer-fixture",revision:String(row.id),android_pid:41,boot_id:row.context.bootId,observer_pid:456,observation_id:"cccccccc-1111-2222-3333-444444444444",started_uptime_ms:"100",completed_uptime_ms:"120",host_interval:{clock_domain:"host.steady",clock_id:evidence.runtime.sessionId,start_ns:"1000",end_ns:"22000"},complete:true,status:"ready",atomic_ui_state:false,screens:[{screen:"primary",sdk_display_id:0,display_id:0,width:1920,height:720,status:"ok",complete:true,unavailable_windows:[],scope:"api28_default_display",atomic_ui_state:false,
      windows:[...new Set(nodes.map(n=>n.windowRef!))].map(w=>({id:w.id,type:w.type,layer:w.layer,active:w.active,input_focused:w.inputFocused,accessibility_focused:w.accessibilityFocused,title:w.title,bounds:rect(w.bounds),package:w.rootPackage,component:null,identity_basis:"accessibility_window_id_and_metadata"})),
      nodes:nodes.map((n,i)=>({id:String(i+1),parent:n.parent===null?null:String(n.parent!+1),window_id:n.windowRef!.id,package:n.packageName,resource_id:n.resource,class:n.className,text:n.text,description:n.description,text_redacted:false,bounds:rect(n.rawBounds??n.bounds),clipped_bounds:rect(n.bounds),visible:n.visibleToUser,enabled:n.enabled,clickable:n.clickable,checkable:n.checkable,checked:n.checked,selected:n.selected,scrollable:n.scrollable,focused:n.focused,actionable_candidate:n.visibleToUser===true&&n.enabled&&n.clickable&&n.bounds[2]>n.bounds[0]&&n.bounds[3]>n.bounds[1],actionable:false,occlusion:"unknown",touchability:"unknown",identity_scope:"this_observation_only",actionable_basis:"node_state_and_viewport_only"}))
    }]};
    const {screens,format,profile,session_id,complete,status,atomic_ui_state,...metadata}=tree;
    nativeAction(row,"session.ui",{screen:"primary"},{format:"voyemu.session.ui.v1",profile,session_id,
      complete,status,atomic_ui_state,cluster:null,multimedia:[{...screens[0],...metadata},null]});
    return tree;
  };
  const nativeAction=(row:any,op:string,fields:any,result:any)=>{
    const request=row.artifacts.find((a:any)=>a.kind==="request"),stdout=row.artifacts.find((a:any)=>a.kind==="stdout");
    const wire={schema:2,request_id:"request-"+row.id,op,profile:"free-09",session:{id:evidence.runtime.sessionId,ownership_id:"b".repeat(32)},...fields};
    Object.assign(row,{nativeOperation:op,nativeRequest:request.path,args:["test","--root",output,"--request",join(output,"private-dispatch-"+row.id+".json")]});
    row.context.runtime.sessionId=evidence.runtime.sessionId;
    writeFileSync(request.path,encoded(wire));request.sha256=hash(readFileSync(request.path));
    writeFileSync(stdout.path,encoded({schema:2,request_id:wire.request_id,operation_id:"c".repeat(32),ok:true,elapsed_ms:1,result}));stdout.sha256=hash(readFileSync(stdout.path));
  };
  const request = () => createReviewRequest(evidence, output, [source], authority.publicKey)!;
  const signed = (request: ReviewRequest, mutate: (payload: any) => void = () => {}, pair = authority) => {
    const payload: any = { schemaVersion: 1, requestSha256: hash(encoded(request)), reviewer: "root", assessments: request.targets.map(target => ({ id: target.id, criteria: target.criteria.map(criterion => ({ ...criterion, actual: `Host protocol observation for ${criterion.id}; this fixture is never Android proof.`, verdict: "passed" })) })) };
    mutate(payload); return encoded({ payload, signature: sign(null, encoded(payload), pair.privateKey).toString("hex") });
  };
  const receipt = (request: ReviewRequest, mutate?: (payload: any) => void) => { const path = join(output, `root-${evidence.actions.length}.json`); writeFileSync(path, signed(request, mutate)); return path; };
  return { output, evidence, authority, source, action, hierarchy, capture, themeSnapshot, proof, request, signed, receipt, nativeSnapshot, nativeAction };
}

test("visual review consumes only signed complete observations for its exact capture request", async () => {
  const f = fixture(), request = f.request();
  assert.equal(request.targets.length, 9);
  assert.equal(request.targets.find(target => target.caseNumber === 9)!.captures.length, 1);
  assert.equal(request.targets.find(target => target.caseNumber === 19)!.captures.length, 5);
  assert.equal(request.targets.find(target => target.caseNumber === 18)!.captures.length, 2);
  assert.equal(request.observations.length, f.evidence.actions.reduce((sum: number, row: any) => sum + row.artifacts.length, 0));
  assert.equal(verifyReview(request, f.signed(request), f.evidence, f.output, f.authority.publicKey).size, 9);
  let boundaries = 0;
  await consumeVisualReview(f.evidence, f.output, [f.source], { publicKey: f.authority.publicKey, receive: async requestPath => f.receipt(JSON.parse(readFileSync(requestPath, "utf8"))) }, async () => { boundaries++; });
  assert.equal(boundaries, 2);
  assert.equal(f.evidence.cases.find((row: any) => row.number === 9).status, "passed");
  assert.equal(f.evidence.cases.find((row: any) => row.number === 21).status, "unavailable");
  assert.equal(f.evidence.cases.find((row: any) => row.number === 18).status, "unavailable");
  const retained = f.evidence.cases.find((row: any) => row.number === 9).subchecks[0].artifacts;
  assert.ok(retained.includes(join(f.output, "visual-review-request.json")));
  assert.ok(retained.includes(join(f.output, "visual-review-receipt.json")));
});

test("visual review rejects replay tampering incomplete coverage and foreign authority", async () => {
  for (const change of [
    (p: any) => p.assessments.pop(),
    (p: any) => p.assessments[0].criteria.pop(),
    (p: any) => p.assessments[0].criteria[1] = p.assessments[0].criteria[0],
    (p: any) => p.assessments[1] = p.assessments[0],
    (p: any) => p.assessments[0].criteria[0].actual = true,
    (p: any) => p.assessments[0].criteria[0].expected += " altered",
    (p: any) => p.assessments[0].criteria[0].verdict = true,
    (p: any) => p.assessments[0].extra = true,
    (p: any) => p.requestSha256 = "0".repeat(64),
    (p: any) => p.reviewer = "delegated-agent",
  ]) {
    const f = fixture(), request = f.request();
    assert.throws(() => verifyReview(request, f.signed(request, change), f.evidence, f.output, f.authority.publicKey));
  }
  for (const change of [
    (f: any, r: any) => f.evidence.invocationId = "44444444-4444-4444-4444-444444444444",
    (f: any, r: any) => f.evidence.runtime.pid++,
    (f: any, r: any) => f.evidence.identity.package = "0".repeat(64),
    (f: any, r: any) => f.evidence.actions[0].context.bootId = absence.bootId,
    (f: any, r: any) => f.evidence.actions.reverse(),
    (f: any, r: any) => writeFileSync(r.targets[0].captures[0].png.path, png([1, 3, 5])),
    (f: any, r: any) => writeFileSync(r.targets[0].captures[0].hierarchy.path, "changed"),
    (f: any, r: any) => writeFileSync(f.source, "changed"),
    (f: any, r: any) => writeFileSync(r.observations[0].path, "changed"),
  ]) {
    const f = fixture(), request = f.request(), receipt = f.signed(request); change(f, request);
    assert.throws(() => verifyReview(request, receipt, f.evidence, f.output, f.authority.publicKey));
  }
  const f = fixture(), request = f.request(), foreign = keys();
  assert.throws(() => verifyReview(request, f.signed(request, () => {}, foreign), f.evidence, f.output, f.authority.publicKey));
  assert.throws(() => verifyReview(request, f.signed(request), f.evidence, f.output, foreign.publicKey));
  const replay = f.request(); assert.notEqual(replay.challenge, request.challenge);
  assert.throws(() => verifyReview(replay, f.signed(request), f.evidence, f.output, f.authority.publicKey));
  const invalid = JSON.parse(f.signed(request).toString()); invalid.signature = "0".repeat(128);
  assert.throws(() => verifyReview(request, encoded(invalid), f.evidence, f.output, f.authority.publicKey));
  assert.throws(() => verifyReview(request, Buffer.concat([f.signed(request), Buffer.from("\n")]), f.evidence, f.output, f.authority.publicKey));
  await consumeVisualReview(f.evidence, f.output, [f.source]);
  assert.equal(f.evidence.cases.find((row: any) => row.number === 9).status, "unavailable");
  await consumeVisualReview(f.evidence, f.output, [f.source], { publicKey: f.authority.publicKey, receive: async () => null });
  assert.equal(f.evidence.cases.find((row: any) => row.number === 9).status, "unavailable");
  const failed = verifyReview(request, f.signed(request, p => p.assessments[0].criteria[0].verdict = "failed"), f.evidence, f.output, f.authority.publicKey);
  assert.equal(failed.get(request.targets[0].id)!.status, "failed");
});

test("asynchronous visual assessment preserves required failures and exact diagnostic unknowns", async () => {
  for (const status of ["unavailable", "failed"]) {
    const f = fixture(), row = f.evidence.cases.find((row: any) => row.number === 9);
    row.subchecks.find((check: any) => check.id === "actual-cyrillic-font-provenance").status = status;
    if (status === "failed") row.status = "failed";
    await consumeVisualReview(f.evidence, f.output, [f.source], { publicKey: f.authority.publicKey, receive: async path => { await Promise.resolve(); return f.receipt(JSON.parse(readFileSync(path, "utf8"))); } });
    assert.equal(row.subchecks[0].status, "passed"); assert.equal(row.status, status === "unavailable" ? "passed" : "failed");
    assert.equal(row.subchecks[1].status, status);
  }
  const unknown = fixture(), unknownRow = unknown.evidence.cases.find((row: any) => row.number === 9);
  unknownRow.subchecks.push({ id: "unknown-required", status: "unavailable", artifacts: [] });
  await consumeVisualReview(unknown.evidence, unknown.output, [unknown.source], { publicKey: unknown.authority.publicKey, receive: async path => unknown.receipt(JSON.parse(readFileSync(path, "utf8"))) });
  assert.equal(unknownRow.status, "unavailable");
  const f = fixture(); let boundary = 0;
  await assert.rejects(() => consumeVisualReview(f.evidence, f.output, [f.source], { publicKey: f.authority.publicKey, receive: async path => f.receipt(JSON.parse(readFileSync(path, "utf8"))) }, async () => { if (++boundary === 2) throw new Error("immutable/runtime/boot changed"); }), /changed/);
  assert.equal(f.evidence.cases.find((row: any) => row.number === 9).subchecks[0].status, "unavailable");
  const changed = fixture();
  await assert.rejects(() => consumeVisualReview(changed.evidence, changed.output, [changed.source], { publicKey: changed.authority.publicKey, receive: async path => { const request = JSON.parse(readFileSync(path, "utf8")); writeFileSync(path, encoded({ ...request, challenge: "changed" })); return changed.receipt(request); } }), /request file changed/);
});

test("action coverage requires three locale cancel captures and the genuine final removal", () => {
  const f = fixture();
  const copy = { languages: Object.fromEntries(["en", "ru", "zh"].map(locale => [locale, { layoutTopology: { panelTargets: [{ role: "uninstall.action", value: "uninstall", class: "android.widget.Button" }] }, uninstall: { buttonText: `${locale}-uninstall`, cancelLabel: `${locale}-cancel`, confirmLabel: `${locale}-confirm` } }])) } as SettingsCopy;
  const tap = (number: number, locale: string, caption: string) => {
    f.action(number, locale, { action: "snapshot" }, { schemaVersion: 1, serial: f.evidence.runtime.serial, body: f.hierarchy(caption, true) });
    return f.action(number, locale, { action: "tap", x: 960, y: 360 });
  };
  for (const locale of ["en", "ru", "zh"]) { tap(21, locale, `${locale}-uninstall`); tap(21, locale, `${locale}-cancel`); }
  assert.equal(actionCoverage(f.evidence, copy, normalAbsence), null);
  const removal = f.evidence.cases.find((row: any) => row.number === 17);
  const observer = f.action(17, "ru", { action: "observe-settings-uninstall" }, { schemaVersion: 1, serial: f.evidence.runtime.serial, bootIdBefore: boot, absence, enforcing: true });
  tap(17, "ru", "ru-uninstall"); const confirmed = tap(17, "ru", "ru-confirm");
  removal.status = "passed";
  f.evidence.boots.push({ caseNumber: 17, kind: "settings-uninstall", status: "passed", after: absence.bootId });
  const complete = actionCoverage(f.evidence, copy, normalAbsence); assert.ok(complete?.includes(observer.artifacts[1].path));
  removal.status = "unavailable"; assert.equal(actionCoverage(f.evidence, copy, normalAbsence), null); removal.status = "passed";
  const cancel = f.evidence.cases.find((row: any) => row.number === 21).subchecks.find((check: any) => check.id === "uninstall-action-captures-cancel-zh");
  cancel.status = "unavailable"; assert.equal(actionCoverage(f.evidence, copy, normalAbsence), null); cancel.status = "passed";
  const requestPath = confirmed.args[1], request = JSON.parse(readFileSync(requestPath, "utf8"));
  writeFileSync(requestPath, encoded({ ...request, x: 10 })); confirmed.artifacts[0].sha256 = hash(readFileSync(requestPath));
  assert.equal(actionCoverage(f.evidence, copy, normalAbsence), null);
  copy.languages.en.layoutTopology.panelTargets.push({ role: "other.action", value: "other", class: "android.widget.Button" });
  assert.throws(() => actionCoverage(f.evidence, copy, normalAbsence), /topology/);
});


test("launcher visual review requires both same-lifetime surfaces and fails a rejected style", async () => {
  const f = fixture(), row = f.evidence.cases.find((row: any) => row.number === 12);
  const selectedBefore=f.themeSnapshot();
  const home = f.capture(12, "en", "launcher-icons-home"), grid = f.capture(12, "en", "launcher-icons-grid");
  const selectedAfter=f.themeSnapshot();
  for (const path of [home[0], grid[0]]) {
    const value = readFileSync(path, "utf8").replaceAll("com.pateonavi.naviapp", "com.qinggan.app.launcher");
    writeFileSync(path, value);
    f.evidence.actions.flatMap((action: any) => action.artifacts).find((artifact: any) => artifact.path === path).sha256 = hash(value);
  }
  f.proof(12, "incomplete-pair", home);
  assert.equal(f.request().targets.some(target => target.caseNumber === 12), false);
  const binding = join(f.output, "pair-launcher-icons-lifetime.json");
  const identity = { boot, process: "com.qinggan.app.launcher", targetPid: "123", targetStarttime: "456", pngs: [home[1], grid[1]],themeSelection:{before:selectedBefore,after:selectedAfter} };
  writeFileSync(binding, encoded(identity));
  f.proof(12, "launcher-icons-paired-surfaces", [...home, ...grid, binding]);
  // Default-display home/grid cannot prove two screens.
  assert.equal(f.request().targets.some(target => target.caseNumber === 12), false);
  const opening = f.evidence.actions.find((action: any) => action.artifacts.some((artifact: any) => artifact.path === grid[0]));
  const requestArtifact = opening.artifacts.find((artifact: any) => artifact.kind === "request");
  writeFileSync(requestArtifact.path, encoded({ action: "open-secondary-launcher" })); requestArtifact.sha256 = hash(readFileSync(requestArtifact.path));
  const body = `bootId=${boot}\npid=123\nstarttime=456\nwindowToken=abc123\ndisplayId=1\ncomponent=com.qinggan.app.launcher/com.qinggan.secondlauncher.activity.AllAppActivity\nvisible=true\nenforcing=Enforcing\n`;
  writeFileSync(grid[0], encoded({ schemaVersion: 1, serial: f.evidence.runtime.serial, body })); opening.artifacts[1].sha256 = hash(readFileSync(grid[0]));
  const secondary = f.evidence.actions.find((action: any) => action.artifacts.some((artifact: any) => artifact.path === grid[1]));
  const screenshotRequest = secondary.artifacts.find((artifact: any) => artifact.kind === "request");
  writeFileSync(screenshotRequest.path, encoded({ action: "screenshot", output: grid[1], display: "secondary" })); screenshotRequest.sha256 = hash(readFileSync(screenshotRequest.path));
  for (const invalid of [body.replace("displayId=1", "displayId=0"), body.replace("windowToken=abc123", "windowToken="), body.replace("pid=123", "pid=999"), body.replace(boot, absence.bootId)]) {
    writeFileSync(grid[0], encoded({ schemaVersion: 1, serial: f.evidence.runtime.serial, body: invalid })); opening.artifacts[1].sha256 = hash(readFileSync(grid[0]));
    assert.throws(() => f.request(), /window|boot|hierarchy/);
  }
  writeFileSync(grid[0], encoded({ schemaVersion: 1, serial: f.evidence.runtime.serial, body })); opening.artifacts[1].sha256 = hash(readFileSync(grid[0]));
  let request = f.request();
  const target = request.targets.find(target => target.caseNumber === 12)!;
  assert.equal(target.captures.length, 2);
  for(const change of [
    {...identity,themeSelection:{before:selectedBefore,after:{...selectedAfter,theme:{label:"Speedy Dash",skin:null,style:"color"}}}},
    {...identity,themeSelection:{before:selectedAfter,after:selectedAfter}},
    {...identity,themeSelection:{before:{...selectedBefore,artifact:{...selectedBefore.artifact,sha256:"0".repeat(64)}},after:selectedAfter}},
  ]) {
    writeFileSync(binding,encoded(change));
    assert.throws(()=>f.request(),/theme pair lacks matching|does not bracket|selection lacks same-boot actual snapshot/);
  }
  writeFileSync(binding,encoded(identity));
  assert.deepEqual(target.criteria.map(criterion => criterion.id), ["visible-size-radius", "plain-app-grid", "theme-home-surface", "reference-availability"]);
  const missing = row.subchecks.find((check: any) => check.id === "launcher-icons-paired-surfaces");
  missing.status = "unavailable";
  assert.equal(f.request().targets.some(target => target.caseNumber === 12), false);
  missing.status = "passed";
  writeFileSync(binding, encoded({ ...identity, boot: absence.bootId }));
  assert.throws(() => f.request(), /lifetime binding|theme selection lacks same-boot/);
  writeFileSync(binding, encoded(identity));
  const action = f.evidence.actions.find((action: any) => action.artifacts.some((artifact: any) => artifact.path === grid[1]));
  action.context.bootId = absence.bootId;
  action.artifacts.find((artifact: any) => artifact.path === grid[1]).boot = absence.bootId;
  assert.throws(() => f.request(), /window authority|boot differs|same-boot hierarchy/);
  action.context.bootId = boot; action.artifacts.find((artifact: any) => artifact.path === grid[1]).boot = boot;
  await consumeVisualReview(f.evidence, f.output, [f.source], { publicKey: f.authority.publicKey, receive: async path => f.receipt(JSON.parse(readFileSync(path, "utf8")), payload => payload.assessments.find((item: any) => item.id === "visual-launcher-third-party-icons").criteria[2].verdict = "failed") });
  assert.equal(row.status, "failed");
});


test("manual signer integrates with actual review verifier and preserves a failed assessment", async () => {
  const f = fixture();
  try {
    const trust = join(f.output, "release-authority"); mkdirSync(trust);
    writeFileSync(join(trust, "product.pem"), keys().publicKey);
    await consumeVisualReview(f.evidence, f.output, [f.source], {
      publicKey: f.authority.publicKey,
      receive: async requestPath => {
        const request = JSON.parse(readFileSync(requestPath, "utf8"));
        const completed = {reviewer:"root",assessments:request.targets.map((target:any,index:number)=>({id:target.id,criteria:target.criteria.map((criterion:any)=>({...criterion,actual:"Synthetic protocol observation; not Android acceptance",verdict:index===0 ? "failed" : "passed"}))}))};
        const receipt = signAssessments(request, completed, f.authority.privateKey.export({type:"pkcs8",format:"pem"}).toString(), trust);
        const results = verifyReview(request,receipt,f.evidence,f.output,f.authority.publicKey);
        assert.equal(results.get(request.targets[0].id)?.status,"failed");
        assert.equal(results.get(request.targets[1].id)?.status,"passed");
        const path=join(f.output,"manual-signer-receipt.json");writeFileSync(path,receipt);return path;
      }
    });
    assert.equal(f.evidence.cases.find((row:any)=>row.number===9).status,"failed");
    assert.ok(readFileSync(join(f.output,"visual-review-receipt.json")).length>0);
  } finally {rmSync(f.output,{recursive:true,force:true});}
});

test("native observations build signed typography targets and reject retained authority drift", () => {
  const f=fixture();
  try {
    for(const row of f.evidence.actions) if(JSON.parse(readFileSync(row.artifacts[0].path,"utf8")).action==="snapshot") f.nativeSnapshot(row);
    const request=f.request();
    assert.equal(request.targets.find(t=>t.caseNumber===9)!.captures.length,1);
    assert.equal(request.targets.find(t=>t.caseNumber===19)!.captures.length,5);
    assert.equal(verifyReview(request,f.signed(request),f.evidence,f.output,f.authority.publicKey).size,9);
    const row=f.evidence.actions[0], artifact=row.artifacts.find((a:any)=>a.kind==="stdout"), original=readFileSync(artifact.path);
    for(const mutate of [
      (v:any)=>v.request_id="foreign",
      (v:any)=>v.result.session_id="d".repeat(32),
      (v:any)=>v.result.multimedia[0].android_pid++,
      (v:any)=>v.result.multimedia[0].boot_id=absence.bootId,
      (v:any)=>v.result.profile="other",
      (v:any)=>v.result.complete=false,
      (v:any)=>v.result.multimedia[0].nodes[0].text="wrong",
    ]) {
      const wire=JSON.parse(original.toString());mutate(wire);writeFileSync(artifact.path,encoded(wire));artifact.sha256=hash(readFileSync(artifact.path));
      assert.throws(()=>f.request());
    }
    writeFileSync(artifact.path,original);artifact.sha256=hash(original);
    writeFileSync(artifact.path,Buffer.concat([original,Buffer.from(" ")]));
    assert.throws(()=>f.request(),/bytes differ|retained response differs/);
  } finally {rmSync(f.output,{recursive:true,force:true});}
});

test("review skips non-UI boot probes and validated partial reads without weakening capture authority", () => {
  const f=fixture();
  try {
    const probe=f.action(9,"en",{action:"boot-observation"});delete probe.context;
    const partial=f.action(9,"en",{action:"snapshot"},{schemaVersion:1,serial:f.evidence.runtime.serial,body:f.hierarchy("Москва")});
    const tree=f.nativeSnapshot(partial);
    tree.complete=false;tree.status="partial";tree.screens[0].complete=false;tree.screens[0].status="partial";
    tree.screens[0].unavailable_windows=[18];tree.screens[0].nodes=tree.screens[0].nodes.filter((n:any)=>n.window_id!==18);
    f.nativeAction(partial,"session.ui",{screen:"primary"},tree);
    // A valid partial read immediately before the original input capture cannot
    // replace the earlier complete observation or become dispatch authority.
    f.evidence.actions.pop();partial.id=1.5;f.evidence.actions.splice(1,0,partial);
    assert.equal(f.request().targets.find(t=>t.caseNumber===9)!.captures.length,1);
    delete f.evidence.actions.find((a:any)=>a.id===2).context;
    assert.throws(()=>f.request(),/capture action authority/);
  } finally {rmSync(f.output,{recursive:true,force:true});}
});

test("native uninstall tap coverage binds complete trees and exact dispatch fences", () => {
  const f=fixture();
  try {
    const copy={languages:Object.fromEntries(["en","ru","zh"].map(locale=>[locale,{layoutTopology:{panelTargets:[{role:"uninstall.action",value:"uninstall",class:"android.widget.Button"}]},uninstall:{buttonText:locale+"-uninstall",cancelLabel:locale+"-cancel",confirmLabel:locale+"-confirm"}}]))} as SettingsCopy;
    const tap=(number:number,locale:string,caption:string)=>{
      const before=f.action(number,locale,{action:"snapshot"},{schemaVersion:1,serial:f.evidence.runtime.serial,body:f.hierarchy(caption,true).replace('clickable="false"','clickable="true"')}),tree=f.nativeSnapshot(before);
      const node=tree.screens[0].nodes[0],w=tree.screens[0].windows[0],project=(v:any,ks:string[])=>Object.fromEntries(ks.map(k=>[k,v[k]]));
      const row=f.action(number,locale,{action:"tap",x:960,y:360});
      f.nativeAction(row,"input.tap",{screen:"primary",x:960,y:360,expected:{boot_id:tree.boot_id,source_instance:tree.source_instance,revision:tree.revision,window:project(w,["id","type","layer","bounds","title","package"]),target:project(node,["window_id","resource_id","package","class","text","bounds","clipped_bounds"])}},{ok:true,session_id:f.evidence.runtime.sessionId,sdk_completed:true,pointer_released:true,screen:"primary",sdk_display_id:0,pre_dispatch_fenced:true,atomic_ui_input:false,occlusion:"known_window_bounds_only",touchability:"unknown",visible_effect_verified:false,requested_count:1,dispatched_count:1});
      return row;
    };
    for(const locale of ["en","ru","zh"]){tap(21,locale,locale+"-uninstall");tap(21,locale,locale+"-cancel");}
    const observer=f.action(17,"ru",{action:"observe-settings-uninstall"},{schemaVersion:1,serial:f.evidence.runtime.serial,bootIdBefore:boot,absence,enforcing:true});
    tap(17,"ru","ru-uninstall");const confirm=tap(17,"ru","ru-confirm");
    f.evidence.cases.find((r:any)=>r.number===17).status="passed";
    f.evidence.boots.push({caseNumber:17,kind:"settings-uninstall",status:"passed",after:absence.bootId});
    assert.ok(actionCoverage(f.evidence,copy,normalAbsence)?.includes(observer.artifacts[1].path));
    const req=confirm.artifacts[0],original=readFileSync(req.path);
    for(const change of [(q:any)=>q.expected.revision="other",(q:any)=>q.expected.source_instance="other",(q:any)=>q.expected.window.id++,(q:any)=>q.expected.target.text="other",(q:any)=>q.session.id="d".repeat(32),(q:any)=>q.profile="other",(q:any)=>q.count=2]){
      const q=JSON.parse(original.toString());change(q);writeFileSync(req.path,encoded(q));req.sha256=hash(readFileSync(req.path));
      assert.throws(()=>actionCoverage(f.evidence,copy,normalAbsence));
    }
    writeFileSync(req.path,original);req.sha256=hash(original);
    const stdout=confirm.artifacts[1],wire=JSON.parse(readFileSync(stdout.path,"utf8"));wire.result.dispatched_count=0;writeFileSync(stdout.path,encoded(wire));stdout.sha256=hash(readFileSync(stdout.path));
    assert.throws(()=>actionCoverage(f.evidence,copy,normalAbsence),/completion/);
  } finally {rmSync(f.output,{recursive:true,force:true});}
});

test("native theme brackets and passive secondary proof retain exact launcher lifetime", () => {
  const f=fixture();
  try {
    const before=f.themeSnapshot(),home=f.capture(12,"en","launcher-icons-home"),grid=f.capture(12,"en","launcher-icons-grid"),after=f.themeSnapshot();
    const homeRow=f.evidence.actions.find((a:any)=>a.artifacts.some((v:any)=>v.path===home[0]));
    writeFileSync(home[0],readFileSync(home[0],"utf8").replaceAll("com.pateonavi.naviapp","com.qinggan.app.launcher"));
    homeRow.artifacts[1].sha256=hash(readFileSync(home[0]));f.nativeSnapshot(homeRow);
    for(const selected of [before,after]){
      const row=f.evidence.actions.find((a:any)=>a.id===selected.actionId);f.nativeSnapshot(row);
      selected.artifact.sha256=row.artifacts[1].sha256;
    }
    const opening=f.evidence.actions.find((a:any)=>a.artifacts.some((v:any)=>v.path===grid[0]));
    writeFileSync(opening.artifacts[0].path,encoded({action:"secondary-capture-proof"}));opening.artifacts[0].sha256=hash(readFileSync(opening.artifacts[0].path));
    const body=`bootId=${boot}\npid=123\nstarttime=456\nwindowToken=abc123\ndisplayId=1\ncomponent=com.qinggan.app.launcher/com.qinggan.secondlauncher.activity.AllAppActivity\nvisible=true\nenforcing=Enforcing\n`;
    writeFileSync(grid[0],encoded({schemaVersion:1,serial:f.evidence.runtime.serial,body}));opening.artifacts[1].sha256=hash(readFileSync(grid[0]));
    const secondary=f.evidence.actions.find((a:any)=>a.artifacts.some((v:any)=>v.path===grid[1]));
    writeFileSync(secondary.artifacts[0].path,encoded({action:"screenshot",output:grid[1],display:"secondary"}));secondary.artifacts[0].sha256=hash(readFileSync(secondary.artifacts[0].path));
    const binding=join(f.output,"native-launcher-icons-lifetime.json"),identity={boot,process:"com.qinggan.app.launcher",targetPid:"123",targetStarttime:"456",pngs:[home[1],grid[1]],themeSelection:{before,after}};
    writeFileSync(binding,encoded(identity));f.proof(12,"launcher-icons-paired-surfaces",[...home,...grid,binding]);
    const bootCapture=f.capture(12,"en","boot-14"),bootRows=f.evidence.actions.slice(-2);
    bootRows[0].exitCode=1;
    for(const row of bootRows) row.context.bootId=absence.bootId;
    bootRows[1].artifacts.find((a:any)=>a.kind==="png").boot=absence.bootId;
    f.proof(12,"launcher-reboot-runtime",bootCapture);
    assert.equal(f.request().targets.find(t=>t.caseNumber===12)!.captures.length,2);
    writeFileSync(binding,encoded({...identity,targetStarttime:"457"}));assert.throws(()=>f.request(),/hierarchy/);
    writeFileSync(binding,encoded({...identity,themeSelection:{before:after,after}}));assert.throws(()=>f.request(),/bracket/);
  } finally {rmSync(f.output,{recursive:true,force:true});}
});

test("native captured PNG source and manifest hashes bind the signed target", () => {
  const f=fixture();
  try {
    const hierarchy=f.evidence.actions[0];f.nativeSnapshot(hierarchy);
    const row=f.evidence.actions[1],alias=row.artifacts.find((a:any)=>a.kind==="png"),directory=join(f.output,"capture-native");mkdirSync(directory);
    const image=join(directory,"primary.png");writeFileSync(image,readFileSync(alias.path));
    const rect={x:0,y:0,width:1,height:1},frame={screen:"primary",sdk_display_id:0,display_id:null,source:"android_sdk",derived:true,width:1,height:1,source_width:1920,source_height:720,rect,file:"primary.png",sha256:alias.sha256,sequence:"0",source_timestamp_ns:"0",source_clock_domain:"sdk",host_interval:{clock_domain:"host.steady",clock_id:f.evidence.runtime.sessionId,start_ns:"1",end_ns:"2"},freshness:{source_clock_mapped:false,newer_guest_frame_proven:false}};
    const manifest={schema:2,session_id:f.evidence.runtime.sessionId,android_pid:41,screens:["primary"],accepted:true,frames_consistent:true,capture_mode:"parallel_fenced",atomic:false,android_boot_identity_verified:true,android_boot_id:boot,android_frames_verified:true,cluster_powered:false,qnx_frame_verified:false,skew_upper_bound_ms:4,frames:[frame]};
    const manifestPath=join(directory,"manifest.json");writeFileSync(manifestPath,encoded(manifest));
    f.nativeAction(row,"session.screenshot",{screens:["primary"],rect},manifest);
    row.nativeAuthority={requestId:"request-"+row.id,operationId:"c".repeat(32),sessionId:f.evidence.runtime.sessionId,manifest:manifestPath,output:alias.path,display:"primary"};
    row.artifacts.push({kind:"manifest",path:manifestPath,sha256:hash(readFileSync(manifestPath))},{kind:"png",path:image,sha256:alias.sha256});
    assert.equal(f.request().targets.find(t=>t.caseNumber===9)!.captures[0].png.path,alias.path);
    for(const change of [(m:any)=>m.session_id="d".repeat(32),(m:any)=>m.android_boot_id=absence.bootId,(m:any)=>m.frames[0].sha256="0".repeat(64)]){
      const m=structuredClone(manifest);change(m);writeFileSync(manifestPath,encoded(m));row.artifacts.find((a:any)=>a.path===manifestPath).sha256=hash(readFileSync(manifestPath));assert.throws(()=>f.request());
    }
  } finally {rmSync(f.output,{recursive:true,force:true});}
});
