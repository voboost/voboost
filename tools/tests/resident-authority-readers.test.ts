import assert from "node:assert/strict";
import test from "node:test";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import { join } from "node:path";
import { stripTypeScriptTypes } from "node:module";
import { inspectionResident, queuedSnapshot, sequence } from "../emulator/functional-observations.ts";
import { vehicleRootTarget } from "../../release/emulator-gate.ts";
const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
const root = process.env.VOBOOST_INJECT_CONTRACT_ROOT || new URL("../../../voboost-inject/", import.meta.url).pathname;
const endpoint = JSON.parse(readFileSync(join(root,"daemon/tests/fixtures/current-endpoint146.json"),"utf8")).accepted;
const digest = bytes => createHash("sha256").update(bytes).digest("hex");
const files = {"/fixture/agents.json":Buffer.from("catalog"),"/fixture/voboost.json":Buffer.from("manifest")};
const catalog = [{id:"vehicle-drive-forced-ev",targets:[{process:"com.qinggan.systemservice"}]}];
const dependencies = {inspectionResident,queuedSnapshot,sequence,vehicleRootTarget,digest,join,readFileSync:path=>files[path],c:{closure:"/fixture"},manifest:{releaseIdentity:"beta1"},catalog,SETTINGS:"com.qinggan.app.setting",oemTargetPid:()=>endpoint.targetPid,unavailable:reason=>{throw new Error(reason);}};
function reader(name: string, next: string) {
  const marker = `  const ${name} = `, start = source.indexOf(marker);
  assert.ok(start >= 0);
  const expression = source.slice(start+marker.length,source.indexOf(`  const ${next} = `,start)).trim().replace(/;$/,"");
  const code = stripTypeScriptTypes(`const read = ${expression};`,{mode:"strip"});
  return Function(...Object.keys(dependencies),code+"; return read;")(...Object.values(dependencies));
}
const layout = reader("layoutAuthority","fontAuthority"), vehicle = reader("vehicleAuthority","currentVehicle");
function observation(legacy: boolean) {
  const revision = endpoint.desiredDocumentGeneration;
  const status = {releaseIdentity:"beta1",manifestSha256:digest(files["/fixture/voboost.json"]),generationId:endpoint.generationId,catalogSha256:digest(files["/fixture/agents.json"]),injections:[{id:"settings",process:"com.qinggan.app.setting",state:"active",documentGeneration:revision,currentEndpoint:endpoint},{id:"vehicle-drive-forced-ev",process:"com.qinggan.systemservice",state:"active",documentGeneration:revision,currentEndpoint:endpoint}],schemaVersion:1};
  if (legacy) for (const row of status.injections) delete row.currentEndpoint;
  let json = JSON.stringify(status);
  if (legacy) json=json.replaceAll(`"documentGeneration":"${revision}"`,`"documentGeneration":${revision}`);
  const record = {schemaVersion:1,sequence:2,timestamp:"2026-10-13T00:00:00Z",level:"info",component:"runtime",source:{kind:"daemon"},message:"desired-snapshot-queued",fields:{process:"com.qinggan.systemservice",targetPid:endpoint.targetPid,targetStarttime:endpoint.targetStarttime,endpointEpoch:endpoint.endpointEpoch,generationId:endpoint.generationId,catalogSha256:status.catalogSha256,documentGeneration:revision,snapshotSha256:"c".repeat(64),frames:"D",sourceLanguage:"ru"}};
  return {boot:endpoint.acceptedStatus.receipt.bootId,body:`\nresident-status:\n/data/voboost/status.json\n${json}\n`,logData:{records:[record]}};
}
for (const legacy of [false,true]) test(`actual layout and vehicle readers preserve ${legacy?"legacy numeric":"producer quoted"} revision exactly`,()=>{
  const observed=observation(legacy),baseline={boot:observed.boot,logData:{records:[]}};
  assert.equal(layout(baseline,observed).documentGeneration,endpoint.desiredDocumentGeneration);
  assert.equal(vehicle(baseline,observed,"fev").documentGeneration,endpoint.desiredDocumentGeneration);
  assert.throws(()=>vehicle(baseline,{...observed,logData:{records:[]}},"fev"),/endpoint, lifetime or desired revision missing/);
});
