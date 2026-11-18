import assert from "node:assert/strict";
import { createRequire } from "node:module";
import test from "node:test";
import { readFileSync } from "node:fs";
import { inspectionResident, residentStatusShape, currentVehicleRead } from "./functional-observations.ts";
import { currentEndpointShape, currentAcceptedEndpoint, stableCurrentAccepted } from "./current-endpoint.ts";
const injectRoot = process.env.VOBOOST_INJECT_CONTRACT_ROOT;
const fixture = JSON.parse(readFileSync(injectRoot ? `${injectRoot}/daemon/tests/fixtures/current-endpoint146.json` : new URL("../../../voboost-inject/daemon/tests/fixtures/current-endpoint146.json", import.meta.url), "utf8"));
const authority = { ...fixture.accepted, ...fixture.accepted.acceptedStatus.receipt };
test("producer serialized fixtures preserve accepted revision exactly", () => {
  for (const key of ["accepted", "pending", "reboundPending", "staleRejected"]) currentEndpointShape(fixture[key]);
  assert.equal(currentAcceptedEndpoint(fixture.accepted, authority)?.acceptedStatus?.documentGeneration, "1791521183847145209");
  assert.equal(currentAcceptedEndpoint(fixture.pending, authority), undefined);
  assert.equal(currentAcceptedEndpoint(undefined, authority), undefined);
  assert.equal(currentAcceptedEndpoint(fixture.reboundPending, authority), undefined);
  assert.equal(currentAcceptedEndpoint(fixture.staleRejected, authority), undefined);
  assert.throws(() => currentEndpointShape(fixture.revisionRejected), /accepted status identity/);
});
test("current attestation must remain stable around fresh CAN", () => {
  assert.equal(stableCurrentAccepted(fixture.accepted, structuredClone(fixture.accepted), authority), true);
  assert.equal(stableCurrentAccepted(fixture.accepted, fixture.pending, authority), false);
  for (const [key, value] of [["targetPid", "2494"], ["targetStarttime", "889"], ["endpointEpoch", "7"], ["generationId", "c".repeat(64)], ["catalogSha256", "d".repeat(64)], ["desiredDocumentGeneration", "1791521183847145210"]]) {
    assert.equal(currentAcceptedEndpoint(fixture.accepted, { ...authority, [key]: value }), undefined);
  }
  for (const [key, value] of [["bootId", "11111111-1111-1111-1111-111111111111"], ["daemonPid", "101"], ["daemonStarttime", "701"]]) assert.equal(currentAcceptedEndpoint(fixture.accepted, { ...authority, [key]: value }), undefined);
  const changed = structuredClone(fixture.accepted); changed.acceptedStatus.receipt.sequence = "2";
  assert.equal(stableCurrentAccepted(fixture.accepted, changed, authority), true);
  assert.equal(stableCurrentAccepted(changed, fixture.accepted, authority), false);
});
test("identities reject unsafe numbers, noncanonical strings and unknown fields", () => {
  for (const path of [["targetPid"], ["targetStarttime"], ["endpointEpoch"], ["desiredDocumentGeneration"], ["acceptedStatus", "documentGeneration"], ["acceptedStatus", "receipt", "sequence"], ["acceptedStatus", "receipt", "daemonPid"], ["acceptedStatus", "receipt", "daemonStarttime"]]) {
    for (const value of [9007199254740993, 1, "01", "-1", "1e3", "18446744073709551616", null]) {
      const copy = structuredClone(fixture.accepted); let parent = copy; for (const key of path.slice(0, -1)) parent = parent[key]; parent[path.at(-1)!] = value;
      assert.throws(() => currentEndpointShape(copy));
    }
  }
  assert.throws(() => currentEndpointShape({ ...fixture.accepted, acceptedStatus: null }));
  assert.throws(() => currentEndpointShape({ ...fixture.accepted, ack: true }));
  const zero = structuredClone(fixture.accepted); zero.desiredDocumentGeneration = zero.acceptedStatus.documentGeneration = "0"; currentEndpointShape(zero);
  const future = structuredClone(fixture.accepted); future.desiredDocumentGeneration = future.acceptedStatus.documentGeneration = "9223372036854775806"; assert.throws(() => currentEndpointShape(future));
});
test("bounded resident diagnostics preserve producer string identity and closed shape", () => {
  const resident = {schemaVersion:1, releaseIdentity:"beta1", manifestSha256:"c".repeat(64), generationId:fixture.accepted.generationId, catalogSha256:fixture.accepted.catalogSha256, startupMode:"interface", state:"ready", killed:false, panic:false, injections:[{id:"vehicle-drive-forced-ev",process:"com.qinggan.systemservice",state:"active",reason:"",details:{},documentGeneration:fixture.accepted.desiredDocumentGeneration,currentEndpoint:fixture.accepted}],executors:[]};
  const parse = (value: unknown) => inspectionResident("\nresident-status:\n/data/voboost/status.json\n" + JSON.stringify(value) + "\n");
  const parsed = parse(resident);
  assert.equal(parsed.injections[0].currentEndpoint.acceptedStatus.documentGeneration, fixture.accepted.desiredDocumentGeneration);
  residentStatusShape(parsed);
  for (const path of [["acceptedStatus", "documentGeneration"], ["desiredDocumentGeneration"], ["endpointEpoch"]]) {
    const wrong = structuredClone(resident); let parent = wrong.injections[0].currentEndpoint; for (const key of path.slice(0, -1)) parent = parent[key]; parent[path.at(-1)!] = 1791521183847145209;
    assert.throws(() => residentStatusShape(parse(wrong)));
  }
  const oldRoot = structuredClone(resident); oldRoot.injections[0].documentGeneration = "1"; assert.throws(() => residentStatusShape(parse(oldRoot)), /resident identity/);
  const legacy = parse(resident); delete legacy.injections[0].currentEndpoint;
  const exactLegacy = inspectionResident("\nresident-status:\n/data/voboost/status.json\n" + JSON.stringify(legacy).replace('"documentGeneration":"1791521183847145209"', '"documentGeneration":1791521183847145209') + "\n");
  assert.equal(exactLegacy.injections[0].documentGeneration, "1791521183847145209");
  const passiveCutover = structuredClone(resident); passiveCutover.generationId = "f".repeat(64); residentStatusShape(parse(passiveCutover));
  assert.equal(currentAcceptedEndpoint(fixture.accepted, {...authority,generationId:passiveCutover.generationId}), undefined);
  const pending = structuredClone(resident); delete pending.injections[0].currentEndpoint.acceptedStatus; residentStatusShape(parse(pending));
});
test("shared closed JSON schema accepts producer serialization and rejects identity numbers", () => {
  const Ajv = createRequire(import.meta.url)("ajv");
  const schema = JSON.parse(readFileSync(new URL("../../release/current-endpoint.schema.json", import.meta.url), "utf8"));
  const validate = new Ajv({strict:true}).compile(schema);
  for (const key of ["accepted", "pending", "reboundPending"]) assert.equal(validate(fixture[key]), true, JSON.stringify(validate.errors));
  assert.equal(validate({...fixture.accepted, targetStarttime:9007199254740993}), false);
  assert.equal(validate({...fixture.accepted, acceptedStatus:null}), false);
  assert.equal(validate({...fixture.accepted, extra:true}), false);
});
test("fresh CAN binds actual producer L metadata to both accepted S observations", () => {
  const proof = () => {
    const record = {schemaVersion:1,sequence:901,timestamp:"2026-10-13T00:00:00Z",source:{kind:"agent",id:"vehicle-drive-forced-ev"},level:"info",component:"agent",message:"fixture",fields:structuredClone(fixture.routedLogMeta)};
    return {baseline:{boot:authority.bootId,records:[{...record,sequence:900}]},current:{boot:authority.bootId,records:[record],diagnostics:[{record,agent:"vehicle-drive-forced-ev",event:"reconcile.snapshot",outcome:"complete",fields:{forced:"TRUE"}}]}};
  };
  const read = (p: ReturnType<typeof proof>, before = fixture.accepted, after = fixture.accepted) => currentVehicleRead("fev",before,after,{...authority,process:fixture.routedLogMeta.process},p.baseline,p.current);
  const falseCan = proof(); falseCan.current.diagnostics[0].fields.forced = "FALSE"; assert.equal(read(falseCan), false);
  const pw = proof(); pw.current.records[0].source.id = "vehicle-drive-pedestrian-warning"; pw.current.diagnostics[0].agent = "vehicle-drive-pedestrian-warning"; pw.current.diagnostics[0].event = "contract.read"; pw.current.diagnostics[0].outcome = "completed"; pw.current.diagnostics[0].fields = {value:2};
  assert.equal(currentVehicleRead("pw",fixture.accepted,fixture.accepted,{...authority,process:fixture.routedLogMeta.process},pw.baseline,pw.current), true);
  assert.equal(read(proof()), true); // L sequence901 and S sequence1 are separate counters.
  assert.equal(read(proof(),fixture.pending), null);
  assert.equal(read(proof(),fixture.accepted,fixture.reboundPending), null);
  for (const key of Object.keys(fixture.routedLogMeta)) {
    const missing = proof(); delete missing.current.records[0].fields[key]; assert.equal(read(missing), null, key);
    const foreign = proof(); foreign.current.records[0].fields[key] = "different"; assert.equal(read(foreign), null, key);
  }
  const old = proof(); old.current.records[0].sequence = 900; assert.equal(read(old), null);
  const boot = proof(); boot.current.boot = "11111111-1111-1111-1111-111111111111"; assert.equal(read(boot), null);
  const unsafe = proof(); unsafe.current.records[0].sequence = 9007199254740993; assert.equal(read(unsafe), null);
});
