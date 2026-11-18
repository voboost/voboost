import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
const require=createRequire(process.env.PRODUCT_PACKAGE_JSON ?? new URL("../../package.json",import.meta.url));
const Ajv=require("ajv/dist/2020.js");
const schema=JSON.parse(readFileSync(new URL("../../release/emulator-runtime-evidence.schema.json",import.meta.url),"utf8"));
test("strict full evidence schema compiles and actual source-admission return shape validates",()=>{
 const ajv=new Ajv({strict:true,strictTypes:false,strictTuples:false});ajv.compile(schema);
 const validate=ajv.compile(schema.properties.sourceAdmission);
 assert.equal(validate({status:"passed",identity:{package:"hash"},receiptSha256:"a".repeat(64)}),true);
 for(const input of [{status:"passed",identity:{}},{status:"passed",identity:{},receiptSha256:"bad"},{status:"passed",identity:{},receiptSha256:"a".repeat(64),extra:true}])assert.equal(validate(input),false);
});
test("deferred scope allows exactly full27 or installed regression, never both",()=>{
 const branch=schema.allOf.find((r:any)=>r.if?.required?.includes("imageCapture"));
 const validate=new Ajv({strict:true,strictTypes:false}).compile({type:"object",properties:{imageCapture:{const:"deferred"},functionalMode:{const:"full27"},regressionScenario:{type:"string"},passed:{const:false},boots:{type:"array"},cases:{type:"array"},actions:{type:"array"}},allOf:[branch]});
 assert.equal(validate({imageCapture:"deferred",functionalMode:"full27",passed:false}),true);
 assert.equal(validate({imageCapture:"deferred",regressionScenario:"vehicle",passed:false}),true);
 for(const input of [{imageCapture:"deferred",passed:false},{imageCapture:"deferred",functionalMode:"full27",regressionScenario:"vehicle",passed:false},{imageCapture:"deferred",functionalMode:"full27",passed:true}])assert.equal(validate(input),false);
});

import { gateReport } from "../emulator/gate-report.ts";
import { evidence } from "../emulator/gate-report-fixture.ts";
const localAdmission = () => ({ status: "passed", kind: "local-artifact", evidenceSha256: "a".repeat(64), sourceInventorySha256: "b".repeat(64), verificationDelta: [] });
test("local artifact admission is strict and cannot impersonate source-build authority", () => {
 const validate = new Ajv({strict:true,strictTypes:false}).compile(schema.properties.sourceAdmission);
 assert.equal(validate(localAdmission()), true);
 assert.equal(validate({...localAdmission(), verificationDelta:[{path:"tools/emulator/native-tree.ts",beforeSha256:"c".repeat(64),afterSha256:"d".repeat(64)}]}),true);
 for(const change of [{status:"failed"},{kind:"source-build"},{evidenceSha256:"bad"},{sourceInventorySha256:null},{verificationDelta:null},{identity:{}},{receiptSha256:"a".repeat(64)},{extra:true},{verificationDelta:[{path:"x",beforeSha256:"bad",afterSha256:"d".repeat(64)}]}]) assert.equal(validate({...localAdmission(),...change}),false,JSON.stringify(change));
 for(const key of Object.keys(localAdmission())) {const value:any=localAdmission();delete value[key];assert.equal(validate(value),false,key);}
});
test("full185 failed owning preparation renders all 27 cases as not-run without inventing acceptance", () => {
 // Actual full185 failure shape, with only invocation-specific artifact paths omitted.
 const e=evidence();e.schemaVersion=4;e.preparedCheckInventory=[];e.verificationContract=schema.properties.verificationContract.const;
 e.sourceAdmission=localAdmission();
 e.preparation={status:"failed",reason:"Error: fixed owning host gates did not complete successfully",kind:null,actionId:null,bootId:null,context:null,actions:[],artifacts:[]};
 const report=gateReport(e,"/retained/emulator-runtime-evidence.json");
 assert.match(report,/Preparation: failed/);assert.match(report,/Recorded overall result: not passed/);
 assert.equal((report.match(/\| not-run \| not recorded \|/g)||[]).length,27);
 const forged=structuredClone(e);forged.passed=true;assert.throws(()=>gateReport(forged,"/retained/evidence.json"));
 const malformed=structuredClone(e);malformed.sourceAdmission.evidenceSha256="invalid";assert.throws(()=>gateReport(malformed,"/retained/evidence.json"),/invalid schema/);
});

test("report action operation enum exhaustively matches current native dispatcher plus retained legacy names", () => {
 const source=readFileSync(new URL("../emulator/native-session.ts",import.meta.url),"utf8");
 const declaration=source.match(/const operations: Operation\[\] = \[(.*?)\];/s);assert.ok(declaration);
 const operations=[...declaration[1].matchAll(/"([a-z.]+)"/g)].map(m=>m[1]);
 assert.equal(operations.length,new Set(operations).size);assert.ok(operations.length>30);
 const enumValues=schema.$defs.action.properties.nativeOperation.enum;
 assert.deepEqual(enumValues,[...operations,"control.wait","output.status"]);
 const validate=new Ajv({strict:true,strictTypes:false}).compile({$defs:schema.$defs,...schema.$defs.action});
 const base={id:1,caseNumber:0,startedMonotonicNs:"1",finishedMonotonicNs:"2",executable:"/voyemu",args:[],exitCode:0,signal:null,timedOut:false,overflow:false,artifacts:[],nativeRequest:"/retained/native-request.json"};
 for(const op of operations) assert.equal(validate({...base,nativeOperation:op}),true,op);
 for(const op of ["output.fake","session.root","input.adb",null,{},1,""]) assert.equal(validate({...base,nativeOperation:op}),false,JSON.stringify(op));
 assert.equal(validate({...base,nativeOperation:"session.status",fakeAuthority:true}),false);
});
test("full185 run2 native session status and failed readiness remain readable as failed preparation", () => {
 const e=evidence();e.schemaVersion=4;e.preparedCheckInventory=[];e.verificationContract=schema.properties.verificationContract.const;e.sourceAdmission=localAdmission();
 e.preparation={status:"failed",reason:"native operation unresolved; inspect durable operation receipt before replay",kind:null,actionId:null,bootId:null,context:null,actions:[],artifacts:[]};
 const base={caseNumber:0,startedMonotonicNs:"455585805945333",finishedMonotonicNs:"455585813356250",executable:"/voyemu",args:["test","--root","/voyemu-root","--request","/private/request.json"],signal:null,timedOut:false,overflow:false,artifacts:[],nativeRequest:"/retained/native-request.json"};
 e.actions=[{...base,id:12,exitCode:0,nativeOperation:"session.status"},{...base,id:13,exitCode:4,nativeOperation:"session.wait",finishedMonotonicNs:"455645834599166"}];
 const report=gateReport(e,"/retained/run2/evidence.json");assert.match(report,/Preparation: failed/);assert.match(report,/Recorded overall result: not passed/);
 assert.equal((report.match(/\| not-run \| not recorded \|/g)||[]).length,27);
 for(const op of ["output.tree","control.arbitrary","wait.fake"]) {const invalid=structuredClone(e);invalid.actions[1].nativeOperation=op;assert.throws(()=>gateReport(invalid,"/retained/run2/evidence.json"),/invalid schema/);}
});

test("all current producer action fields and artifact kinds validate without accepting invented fields", () => {
 const validate=new Ajv({strict:true,strictTypes:false}).compile({$defs:schema.$defs,...schema.$defs.action});
 const hash="a".repeat(64),token="b".repeat(32);
 const row={id:1,caseNumber:27,startedMonotonicNs:"1",finishedMonotonicNs:"2",executable:"/voyemu",args:[],exitCode:0,signal:null,timedOut:false,overflow:false,nativeOperation:"session.screenshot",nativeRequest:"/request.json",context:{kit:hash,runtime:null,serial:"emulator-5574",bootId:"11111111-1111-1111-1111-111111111111",locale:"zh"},nativeAuthority:{requestId:"request-uuid",operationId:token,sessionId:token,manifest:"/manifest.json",output:"/primary.png",display:"primary"},artifacts:["stdout","stderr","png","request","archive"].map(kind=>({path:"/artifact",sha256:hash,kind,boot:"boot-id",locale:"zh",kit:hash,runtime:null}))};
 assert.equal(validate(row),true,JSON.stringify(validate.errors));
 for(const change of [{nativeAuthority:{...row.nativeAuthority,operationId:"invalid"}},{nativeAuthority:{...row.nativeAuthority,display:"cluster"}},{context:{...row.context,locale:"de"}},{artifacts:[{...row.artifacts[0],kind:"fake"}]},{nativeRequest:12}])assert.equal(validate({...row,...change}),false);
});

test("normal passed schema keeps optional Paint exceptions parent-case aware", () => {
 const ajv=new Ajv({strict:true,strictTypes:false,strictTuples:false});
 const validate=ajv.compile({$defs:schema.$defs,allOf:[schema.$defs.case,schema.allOf[0].then.properties.cases.items]});
 for(const [number,ids] of [[9,["actual-cyrillic-font-provenance"]],[19,["moscow","yo","mixed-prefix","mixed-latin","mixed-yo"].map(x=>x+"-font-provenance")]] as [number,string[]][]) for(const id of ids) {
  const original=evidence().cases.find((r:any)=>r.number===number); const row={...original,status:"passed",subchecks:[{id,status:"unavailable",reason:"optional diagnostic",artifacts:[]}]};
  assert.equal(validate(row),true,JSON.stringify(validate.errors));
  for(const status of ["failed","not-run"]) assert.equal(validate({...row,subchecks:[{...row.subchecks[0],status}]}),false);
  for(const wrong of [1,number===9?19:9]) assert.equal(validate({...row,number:wrong}),false);
  for(const bad of [{...row.subchecks[0],id:id+"-unknown"},{...row.subchecks[0],id:"actual-cyrillic-input"}]) assert.equal(validate({...row,subchecks:[bad]}),false);
  for(const key of ["id","status"]) {const check={...row.subchecks[0]};delete check[key];assert.equal(validate({...row,subchecks:[check]}),false);}
 }
});

test("normal passed branch preserves preparation, cleanup, boot and active-operation guards", () => {
 const validate=new Ajv({strict:false}).compile(schema.allOf[0]);
 const sample={passed:true,cases:[{number:9,status:"passed",subchecks:[{id:"actual-cyrillic-font-provenance",status:"unavailable"}]}],boots:[{status:"passed"}],activeOperation:null,fixtureCleanup:{owned:true,status:"passed"},preparation:{status:"passed"}};
 assert.equal(validate(sample),true);
 for(const mutate of [(e:any)=>e.boots=[],(e:any)=>e.boots[0].status="failed",(e:any)=>e.activeOperation={},(e:any)=>e.fixtureCleanup.status="failed",(e:any)=>e.fixtureCleanup.owned=false,(e:any)=>e.preparation.status="failed",(e:any)=>e.cases[0].subchecks=[]]) {const e=structuredClone(sample);mutate(e);assert.equal(validate(e),false);}
 const whole=new Ajv({strict:true,strictTypes:false,strictTuples:false}).compile(schema);
 for(const mutate of [(e:any)=>e.cases.pop(),(e:any)=>e.cases[1]=structuredClone(e.cases[0])]) {const e=evidence();mutate(e);assert.equal(whole(e),false);}
});
