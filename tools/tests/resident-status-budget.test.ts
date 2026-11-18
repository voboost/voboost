import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { createHash } from "node:crypto";
import test from "node:test";
import { parseBootObservation, parseResidentStatus } from "../../release/emulator-gate.ts";
import { inspectionResident, residentStatusShape } from "../emulator/functional-observations.ts";
import { RESIDENT_STATUS_BYTES } from "../emulator/current-endpoint.ts";
function reviewedInventory(catalog: any) {
  return {schemaVersion:catalog.schemaVersion,supportedAbis:catalog.supportedAbis,agentEntryAbi:catalog.agentEntryAbi,
    agents:catalog.agents.map(agent=>({id:agent.id,kind:agent.kind ?? "agent",status:agent.status ?? null,
      targets:agent.targets.map(target=>target.process).sort()})).sort((a,b)=>a.id.localeCompare(b.id))};
}
const boot = "11111111-1111-1111-1111-111111111111";
const prefix = `bootCompleted=1\nbootId=${boot}\nenforcing=Enforcing\nbootIdAfter=${boot}\nresident-status:\n/data/voboost/status.json\n`;
const document = (bytes: number) => {
  const base = {schemaVersion:1,injections:[],padding:""};
  const fixed = JSON.stringify(base); base.padding = "x".repeat(bytes - Buffer.byteLength(fixed) - 1);
  return JSON.stringify(base) + "\n";
};
test("resident status exact 1MiB document remains bounded through JSON receipt escaping", () => {
  const status = document(RESIDENT_STATUS_BYTES); assert.equal(Buffer.byteLength(status), RESIDENT_STATUS_BYTES);
  const body = `${prefix}${status}\n`;
  assert.equal(parseResidentStatus(body).boot, boot);
  assert.equal(inspectionResident(body).padding.length > 16384, true);
  const receipt = JSON.stringify({schemaVersion:1,serial:"fixture",body});
  assert.ok(Buffer.byteLength(receipt) < 8 * 1024 * 1024);
});
test("resident status rejects +1 document, truncation and boot changes while boot operation stays 16KiB", () => {
  for (const body of [`${prefix}${document(RESIDENT_STATUS_BYTES + 1)}\n`, `${prefix}${document(RESIDENT_STATUS_BYTES).slice(0,-3)}\n\n`, `${prefix.replace("bootIdAfter="+boot,"bootIdAfter=22222222-2222-2222-2222-222222222222")}${document(100)}\n`]) assert.throws(() => parseResidentStatus(body));
  assert.throws(() => inspectionResident(`${prefix}${document(RESIDENT_STATUS_BYTES + 1)}\n`), /oversized/);
  assert.throws(() => inspectionResident(`${prefix}${document(100).slice(0,-3)}\n\n`));
  assert.throws(() => parseBootObservation(`bootCompleted=1\nbootId=${boot}\nenforcing=Enforcing\noperation:\n${"x".repeat(16385)}`));
});
test("actual beta1 max serializer fixture fits every resident consumer with finite signed inventory", () => {
  const root = process.env.VOBOOST_INJECT_CONTRACT_ROOT || new URL("../../../voboost-inject/", import.meta.url).pathname;
  const budget = JSON.parse(readFileSync(`${root}/daemon/tests/fixtures/current-endpoint197-beta1-budget.json`, "utf8"));
  assert.equal(budget.residentReadCapBytes, RESIDENT_STATUS_BYTES);
  
  
  
  const catalogBytes = readFileSync(`${root}/daemon/tests/fixtures/current-endpoint197-beta1-catalog.json`);
  assert.equal(createHash("sha256").update(catalogBytes).digest("hex"), budget.catalogSha256);
  const catalog = JSON.parse(catalogBytes.toString("utf8"));
  const expected = catalog.agents.flatMap(agent => agent.targets.map(target => ({id:agent.id,process:target.process})));
  assert.equal(expected.length, budget.catalogTargetRows);
  assert.equal(catalog.agents.filter(agent => agent.status === "stable" && agent.kind !== "executor").length, budget.stableAgents);
  assert.equal(catalog.agents.filter(agent => agent.kind === "executor").length, budget.executors);
  assert.ok(expected.every(row => typeof row.process === "string" && !row.process.includes("*")));
  const status = readFileSync(`${root}/daemon/tests/fixtures/current-endpoint197-beta1-max-status.json`, "utf8");
  assert.equal(Buffer.byteLength(status), budget.statusBytesIncludingFinalLf);
  assert.ok(Buffer.byteLength(status) > 64 * 1024);
  assert.ok(Buffer.byteLength(status) < RESIDENT_STATUS_BYTES);
  const body = `${prefix}${status}\n`;
  parseResidentStatus(body);
  const resident = inspectionResident(body); residentStatusShape(resident);
  const identities = rows => rows.map(row => `${row.id}:${row.process}`).sort();
  assert.equal(resident.injections.length,budget.observedResidentRows);
  assert.deepEqual(identities(resident.injections), identities(expected));
  const agentsRoot = process.env.VOBOOST_AGENTS_ROOT || new URL("../../../voboost-agents/", import.meta.url).pathname;
  const metadataRoot = join(agentsRoot, "src/main/java");
  const metadata = readdirSync(metadataRoot, {recursive:true}).filter(name => /(?:Agent|Executor)\.json$/.test(String(name))).map(name => JSON.parse(readFileSync(join(metadataRoot, String(name)), "utf8"))).filter(entry => entry.status === "stable" || entry.kind === "executor");
  assert.equal(metadata.filter(entry => entry.status === "stable").length, budget.stableAgents);
  assert.equal(metadata.filter(entry => entry.kind === "executor").length, budget.executors);
  const authored = metadata.flatMap(entry => (Array.isArray(entry.target) ? entry.target : [entry.target]).map(process => ({id:entry.id,process})));
  const android = catalog.agents.flatMap(agent => agent.targets.filter(target => target.platform !== "qnx").map(target => ({id:agent.id,process:target.process})));
  assert.deepEqual(identities(authored), identities(android), "current pinned agents metadata target growth requires reviewed beta1 budget/fixture update");
  if (process.env.VOBOOST_ENDPOINT197_CATALOG_PATH) assert.deepEqual(reviewedInventory(JSON.parse(readFileSync(process.env.VOBOOST_ENDPOINT197_CATALOG_PATH,"utf8"))), reviewedInventory(catalog), "actual generated catalog inventory differs from reviewed beta1 budget");

  assert.ok(resident.injections.every(row => Buffer.byteLength(JSON.stringify(row.details)) === 8192 && Buffer.byteLength(row.reason) === 128 && row.currentEndpoint?.acceptedStatus));
  assert.ok(Buffer.byteLength(JSON.stringify({schemaVersion:1,serial:"fixture",body})) < 8 * 1024 * 1024);
  const future = {...resident,injections:Array.from({length:4096}, (_, i) => ({...resident.injections[i % resident.injections.length],details:{}}))};
  const unsupported = `${prefix}${JSON.stringify(future)}\n\n`;
  assert.throws(() => inspectionResident(unsupported), /oversized/);
  assert.throws(() => parseResidentStatus(unsupported));
});

test("reviewed current catalog inventory admits rebuilt payload hashes and rejects target growth",()=>{
 const root=process.env.VOBOOST_INJECT_CONTRACT_ROOT || new URL("../../../voboost-inject/",import.meta.url).pathname;
 const reviewed=JSON.parse(readFileSync(root+"/daemon/tests/fixtures/current-endpoint197-beta1-catalog.json","utf8"));
 const rebuilt=structuredClone(reviewed);
 for(const agent of rebuilt.agents) for(const target of agent.targets) {target.sha256="a".repeat(64);target.size+=4;}
 assert.notDeepEqual(rebuilt,reviewed);assert.deepEqual(reviewedInventory(rebuilt),reviewedInventory(reviewed));
 for(const change of [(c:any)=>c.agents[0].targets.push({...c.agents[0].targets[0],process:"com.example.new"}),(c:any)=>c.agents[0].targets.pop(),(c:any)=>c.agents[0].id="foreign",(c:any)=>c.agents[0].status="unstable",(c:any)=>c.agentEntryAbi++]) {
  const changed=structuredClone(rebuilt);change(changed);assert.notDeepEqual(reviewedInventory(changed),reviewedInventory(reviewed));
 }
});
