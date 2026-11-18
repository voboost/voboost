import {test} from "node:test";
import assert from "node:assert/strict";
import {startupMilestonesShape,startupMilestoneEvidence,STARTUP_MILESTONE_BYTES} from "./startup-milestones.ts";
const hash="a".repeat(64),boot="00000000-0000-0000-0000-000000000001";
const identity={bootId:boot,daemonPid:123,daemonStarttime:"9007199254740993",manifestSha256:hash,generationId:hash,catalogSha256:hash};
const catalog=[{id:"future-vehicle-member",startupPriority:"vehicle",targets:[{process:"backend"},{process:"intent-only"}]}];
function fixture(){
 const endpoint={targetPid:"234",targetStarttime:"9007199254740994",endpointEpoch:"9007199254740995",generationId:hash,catalogSha256:hash,desiredDocumentGeneration:"9007199254740996",acceptedStatus:{documentGeneration:"9007199254740996",receipt:{sequence:"7",daemonPid:"123",daemonStarttime:identity.daemonStarttime,bootId:boot}}};
 const target={process:"backend",pid:234,starttime:endpoint.targetStarttime,readyMs:10,batch:1,priority:0,dispatchMs:12,dispatchRank:0,endpointEpoch:endpoint.endpointEpoch,adoptedMs:20};
 const application={contract:"future-contract",intent:"user-off",outcome:"matchedNoop",semantics:[{name:"semantic-a",desired:false,actual:false}],androidObservedAtBootMs:1000,receivedMs:30};
 return {manifestSha256:hash,generationId:hash,catalogSha256:hash,state:"ready",panic:false,killed:false,injections:[{id:catalog[0].id,process:"backend",state:"active",documentGeneration:endpoint.desiredDocumentGeneration,lifecycleGeneration:"9007199254740997",currentEndpoint:endpoint}],startupMilestones:{clockAnchor:{clockDomain:"linux.bootElapsedRealtime",observedAtBootMs:1000,ownerBeforeMs:0,ownerAfterMs:1,precisionMs:10},clockDomain:"daemon.monotonicSinceOwnerStart",identity:{...identity},complete:true,reason:null,expectedTargetCount:2,expectedMemberCount:2,targets:[target],members:[{id:catalog[0].id,target:0,documentGeneration:endpoint.desiredDocumentGeneration,lifecycleGeneration:"9007199254740997",application}]}};
}
const evidence=(v:any)=>startupMilestoneEvidence(v,identity,catalog,[catalog[0].id]);
test("generic backend receipt accepts false intent without requiring absent intent-only target",()=>{
 const result=evidence(fixture()); assert.equal(result.status,"passed");
 if(result.status==="passed") assert.deepEqual(result.intervals,[{id:catalog[0].id,process:"backend",readyToDispatchMs:2,dispatchToAdoptMs:8,adoptToApplicationReceiptMs:10,readyToApplicationReceiptMs:20,androidObservedAtBootMs:1000}]);
});
test("missing and overflow diagnostics stay unavailable",()=>{ const v:any=fixture();delete v.startupMilestones;assert.equal(evidence(v).status,"unavailable");v.startupMilestones={...fixture().startupMilestones,complete:false,reason:"budget-exceeded",targets:[],members:[]};assert.equal(evidence(v).status,"unavailable");v.startupMilestones.targets=[fixture().startupMilestones.targets[0]];assert.throws(()=>evidence(v)); });
test("unknown keys, mixed clocks, unsafe integers and noncanonical identities rejected",()=>{
 for(const mutate of [(v:any)=>v.extra=true,(v:any)=>v.identity.extra=true,(v:any)=>v.clockDomain="android.elapsedRealtime",(v:any)=>v.targets[0].readyMs=Number.MAX_SAFE_INTEGER+1,(v:any)=>v.identity.daemonStarttime=9007199254740992,(v:any)=>v.members[0].documentGeneration="01",(v:any)=>v.members[0].application.extra=true]) {const v:any=fixture().startupMilestones;mutate(v);assert.throws(()=>startupMilestonesShape(v));}
});
test("duplicate target member semantic and dispatch rank rejected",()=>{
 for(const mutate of [(v:any)=>v.targets.push({...v.targets[0]}),(v:any)=>v.members.push({...v.members[0]}),(v:any)=>v.members[0].application.semantics.push({...v.members[0].application.semantics[0]}),(v:any)=>{v.targets.push({...v.targets[0],process:"other",pid:235});}]) {const v:any=fixture().startupMilestones;mutate(v);assert.throws(()=>startupMilestonesShape(v));}
});
test("stale endpoint document lifecycle and daemon identity rejected",()=>{
 for(const mutate of [(v:any)=>v.startupMilestones.identity.bootId=boot.replace(/1$/,"2"),(v:any)=>v.startupMilestones.identity.generationId="b".repeat(64),(v:any)=>v.injections[0].documentGeneration="1",(v:any)=>v.injections[0].lifecycleGeneration="1",(v:any)=>v.injections[0].currentEndpoint.endpointEpoch="1",(v:any)=>v.injections[0].currentEndpoint.acceptedStatus.receipt.daemonStarttime="1",(v:any)=>v.injections[0].currentEndpoint.acceptedStatus.receipt.bootId=boot.replace(/1$/,"2")]) {const v:any=fixture();mutate(v);assert.throws(()=>evidence(v));}
});
test("baseline only or unknown inventory cannot claim completion",()=>{const v:any=fixture();v.startupMilestones.members[0].application=null;assert.equal(evidence(v).status,"unavailable");assert.throws(()=>startupMilestoneEvidence(fixture(),identity,catalog,["foreign"]));const extra={id:"another",startupPriority:"vehicle",targets:[{process:"backend"}]};const extraStatus=fixture();extraStatus.startupMilestones.expectedMemberCount=3;assert.equal(startupMilestoneEvidence(extraStatus,identity,[...catalog,extra],[catalog[0].id,"another"]).status,"unavailable");});
test("actual state mismatch and absent dispatch adoption fail closed",()=>{
 for(const mutate of [(v:any)=>v.members[0].application.semantics[0].actual=true,(v:any)=>v.targets[0].dispatchMs=null,(v:any)=>v.targets[0].adoptedMs=11,(v:any)=>v.members[0].application.receivedMs=19]) {const v:any=fixture().startupMilestones;mutate(v);assert.throws(()=>startupMilestonesShape(v));}
});
test("same batch priority rank enforced but independent batches have no barrier",()=>{
 const localCatalog=[...catalog,{id:"ui",targets:[{process:"ui"}]}];
 const localEvidence=(v:any)=>startupMilestoneEvidence(v,identity,localCatalog,[catalog[0].id]);
 const v:any=fixture();v.startupMilestones.targets.push({...v.startupMilestones.targets[0],process:"ui",pid:235,priority:1,dispatchMs:13,dispatchRank:1,adoptedMs:14});v.startupMilestones.expectedTargetCount=3;assert.equal(localEvidence(v).status,"passed");
 v.startupMilestones.targets[1].dispatchRank=0;v.startupMilestones.targets[0].dispatchRank=1;assert.throws(()=>localEvidence(v));
 v.startupMilestones.targets[1].batch=2;assert.equal(localEvidence(v).status,"passed");
});
test("long latency is numeric with no fabricated SLA",()=>{const v:any=fixture();v.startupMilestones.members[0].application.receivedMs=10000000;assert.equal(evidence(v).status,"passed");});
test("byte budget bounded without increasing resident wire limit",()=>{const v:any=fixture().startupMilestones;for(let i=0;i<4096;i++)v.targets.push({...v.targets[0],process:`${i}`.padEnd(255,"x"),dispatchRank:i+1});assert.ok(Buffer.byteLength(JSON.stringify(v))>STARTUP_MILESTONE_BYTES);assert.throws(()=>startupMilestonesShape(v),/byte budget/);});

test("case27 actual callback binds first-dispatch preparation and independent CAN after fresh reboot", async()=>{
 const {readFileSync}=await import("node:fs"),{stripTypeScriptTypes}=await import("node:module");
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const marker='await subcheck("vehicle-startup-current-receipts", ',start=source.indexOf(marker)+marker.length;
 assert.ok(start>=marker.length);
 const code=source.slice(start,source.indexOf('\n        proof("host-passive-acc-listener-contract"',start)).trim().replace(/\);$/,"");
 const events:string[]=[],writes:any[]=[];let booted=false;
 const current:any=fixture();current.releaseIdentity="beta1";
 const oldBoot=boot.replace(/1$/,"2");
 let now=0;let preparationAvailable=true;
 const deps={performance:{now:()=>now++},bootReadinessBudgetMs:()=>1,toggle:async(id:string,want:boolean)=>{assert.equal(want,true);events.push(`toggle:${id}`);},VEHICLE:{fev:"fev",pw:"pw"},observations:async()=>({boot:booted?boot:oldBoot,body:JSON.stringify(current),archive:"archive",logData:{records:[]}}),selectOem:async(vehicle:string,want:boolean)=>{events.push(`select:${vehicle}:${want}`);},currentVehicle:async()=>{events.push("confirmed");},inspectionResident:JSON.parse,residentStatusShape:()=>{},enabled:new Map([[catalog[0].id,true]]),reboot:async()=>{events.push("reboot");booted=true;},manifest:{releaseIdentity:"beta1"},digest:()=>hash,readFileSync:()=>Buffer.from("closure"),join:(...p:string[])=>p.join("/"),c:{closure:"closure",output:"output"},catalog,native:{can:async(request:any)=>{assert.deepEqual(request,{states:["IVI_SOC_MODESET","HUM_VSP_FUNCTION_SW"],timeout_ms:5000});events.push("can");return {sample:"native"};}},runtime:{sessionId:"a".repeat(32)},evidence:{actions:[{id:"can-action"}]},frozen:{package:"kit"},serial:"emulator-5574",language:"en",startupVehicleCanEvidence:(sample:any,authority:any)=>{assert.deepEqual(sample,{sample:"native"});assert.deepEqual(authority,{bootId:boot,sessionId:"a".repeat(32),afterBootMs:1000});return{status:"passed",coverage:"sampled-current-getters"};},startupMilestoneEvidence,startupPreparationEvidence:(records:any,receivedAuthority:any,targets:any)=>{assert.deepEqual(records,[]);assert.deepEqual(receivedAuthority,identity);assert.deepEqual(targets,current.startupMilestones.targets);return preparationAvailable?{status:"passed",intervals:[]}:{status:"unavailable",reason:"current preparation missing"};},unavailable:(reason:string)=>{throw new Error(reason);},writeFileSync:(...a:any[])=>{writes.push(a);}};
 const fn=Function(...Object.keys(deps),`return (${stripTypeScriptTypes(code,{mode:"strip"})});`)(...Object.values(deps));
 const artifacts=await fn();
 assert.deepEqual(events,["toggle:fev","select:fev:true","confirmed","toggle:pw","select:pw:false","confirmed","reboot","can"]);
 assert.equal(writes.length,1);const evidence=JSON.parse(writes[0][1]);assert.equal(evidence.measurement,"daemon-receipt-intervals");assert.equal(evidence.latencySla,null);assert.equal(evidence.backendExecutionLatency,"not measured");assert.equal(evidence.status,"passed");assert.deepEqual(evidence.ownerBootAnchor,current.startupMilestones.clockAnchor);assert.equal(evidence.authority.daemonStarttime,identity.daemonStarttime);assert.equal(artifacts.length,3);
 assert.deepEqual(evidence.preparation,{status:"passed",intervals:[]});
 assert.equal(evidence.canActionId,"can-action");assert.equal(evidence.independentCan.coverage,"sampled-current-getters");
 booted=false;preparationAvailable=false;await assert.rejects(fn(),/current preparation missing/);
 preparationAvailable=true;booted=false;delete current.startupMilestones;
 await assert.rejects(fn(),/optional startup diagnostics absent/);
});

test("actual maximal beta1 resident fixture plus bounded startup diagnostics stays within unchanged 1MiB", async()=>{
 const {readFileSync}=await import("node:fs");
 const {inspectionResident,residentStatusShape}=await import("./functional-observations.ts");
 const {RESIDENT_STATUS_BYTES}=await import("./current-endpoint.ts");
 const root=process.env.VOBOOST_INJECT_CONTRACT_ROOT || new URL("../../../voboost-inject/",import.meta.url).pathname;
 const status=inspectionResident(`\nresident-status:\n/data/voboost/status.json\n${readFileSync(`${root}/daemon/tests/fixtures/current-endpoint146-beta1-max-status.json`,"utf8")}\n`);
 const v:any=fixture().startupMilestones;v.identity.manifestSha256=status.manifestSha256;v.identity.generationId=status.generationId;v.identity.catalogSha256=status.catalogSha256;
 // Fill near diagnostic cap with legal unique target records; this checks total
 // host transport headroom, independent of the runtime catalog admission test.
 v.targets=[];v.members=[];v.expectedTargetCount=4096;v.expectedMemberCount=0;
 for(let i=0;i<4096;i++){
   const row={...fixture().startupMilestones.targets[0],process:String(i).padEnd(255,"x"),dispatchRank:i};v.targets.push(row);
   if(Buffer.byteLength(JSON.stringify(v))>STARTUP_MILESTONE_BYTES){v.targets.pop();break;}
 }
 startupMilestonesShape(v);assert.ok(Buffer.byteLength(JSON.stringify(v))>STARTUP_MILESTONE_BYTES-600);
 status.startupMilestones=v;const json=JSON.stringify(status);assert.ok(Buffer.byteLength(json)+1<RESIDENT_STATUS_BYTES);
 residentStatusShape(inspectionResident(`\nresident-status:\n/data/voboost/status.json\n${json}\n`));
});

test("resident parser preserves legacy numeric lifecycle generation losslessly for startup binding", async()=>{
 const {parseResidentDocument,residentStatusShape}=await import("./functional-observations.ts");
 const v:any=fixture();Object.assign(v,{schemaVersion:1,releaseIdentity:"beta1",startupMode:"interface",executors:[]});
 Object.assign(v.injections[0],{reason:"",details:{}});
 const raw=JSON.stringify(v).replace('"lifecycleGeneration":"9007199254740997"','"lifecycleGeneration":9007199254740997');
 const parsed=parseResidentDocument(raw);assert.equal(parsed.injections[0].lifecycleGeneration,"9007199254740997");
 residentStatusShape(parsed);assert.equal(evidence(parsed).status,"passed");
});

test("owning native startup maximum serializer bytes roundtrip through unchanged bounded strict host status reader", async()=>{
 const {readFileSync}=await import("node:fs");
 const {inspectionResident,residentStatusShape}=await import("./functional-observations.ts");
 const {RESIDENT_STATUS_BYTES}=await import("./current-endpoint.ts");
 const root=process.env.VOBOOST_INJECT_CONTRACT_ROOT || new URL("../../../voboost-inject/",import.meta.url).pathname;
 const bytes=readFileSync(`${root}/daemon/tests/fixtures/startup-milestones-beta1-max-status.json`);
 const status=inspectionResident(`\nresident-status:\n/data/voboost/status.json\n${bytes.toString("utf8")}\n`);
 residentStatusShape(status);startupMilestonesShape(status.startupMilestones);
 assert.equal(status.injections.length,52);assert.ok(bytes.length<=RESIDENT_STATUS_BYTES);
 assert.equal(status.startupMilestones.complete,true);assert.ok(status.startupMilestones.targets.length>0);assert.notEqual(status.startupMilestones.clockAnchor,null);
 assert.ok(status.startupMilestones.members.some(m=>m.application!==null));
 assert.ok(Buffer.byteLength(JSON.stringify(status.startupMilestones))<=STARTUP_MILESTONE_BYTES);
 // The native owning golden test must compare exact serialized bytes to this
 // fixture. This is a cross-language parser/budget contract, not runtime proof.
});


test("owner initialization anchor is separately retained and unavailable never becomes synthetic process start",()=>{
 const v:any=fixture(),result=evidence(v);assert.equal(result.status,"passed");
 if(result.status==="passed")assert.deepEqual(result.ownerBootAnchor,v.startupMilestones.clockAnchor);
 v.startupMilestones.clockAnchor=null;assert.equal(evidence(v).status,"unavailable");
 delete v.startupMilestones.clockAnchor;assert.throws(()=>evidence(v),/closed shape/);
 for(const mutate of [(a:any)=>a.clockDomain="linux.monotonic",(a:any)=>a.observedAtBootMs=1001,(a:any)=>a.ownerBeforeMs=2,(a:any)=>a.ownerAfterMs=-1,(a:any)=>a.precisionMs=1,(a:any)=>a.observedAtBootMs=Number.MAX_SAFE_INTEGER+1,(a:any)=>a.extra=true]) {
  const hostile:any=fixture();mutate(hostile.startupMilestones.clockAnchor);assert.throws(()=>evidence(hostile),/owner clock anchor|closed shape/);
 }
 // The independent clocks are not subtracted or coerced into an exact process
 // start timestamp: an arbitrarily late owner anchor is valid diagnostic data.
 const late:any=fixture();late.startupMilestones.clockAnchor.observedAtBootMs=900000;assert.equal(evidence(late).status,"passed");
});
