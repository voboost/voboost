import {readFileSync} from 'node:fs';
import test from "node:test";
import assert from 'node:assert/strict';
import {stripTypeScriptTypes} from 'node:module';
const source=readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');
assert(source.includes("  const cleanupOwnedFixture = async (mode:"));
assert(source.includes("  const caseProgram = async (number: number) => {"));
assert(source.includes("export async function launcherFinallyCleanup"));
const body=source.slice(source.indexOf('  const cleanupOwnedFixture = async (mode:'),source.indexOf('  const caseProgram = async (number: number) => {'));
const final=source.slice(source.indexOf('export async function launcherFinallyCleanup'),source.indexOf('export type RegressionOptions'));
const js=stripTypeScriptTypes(body)+'\nreturn cleanupOwnedFixture;';
const finalJs=stripTypeScriptTypes(final.replace('export ','' ))+'\nreturn launcherFinallyCleanup;';
const AsyncFunction=Object.getPrototypeOf(async function(){}).constructor;
class Unavailable extends Error{}
async function run({present=true,failRead=false,active=null,reappear=false,mode="removal-and-grid",crossBoot=false,badSuccess=false,lease="retained",healthy=true}={}) {
 const receipt={owned:true,status:'unavailable',removalAction:null,artifacts:['original'],package:'ru.voboost.tests.launcherfixture'};
 const evidence={fixtureCleanup:receipt,actions:[]};let mutations=0,reads=0,id=0,stockChecks=0,bootReads=0; const checks=[];
 const ui={records:[],call:async req=>{id++;evidence.actions.push({artifacts:[{path:'a'+id}]});ui.records.push({result:{actionId:id}});if(req.action==='fixture-state'){reads++;return {body:present?'fixture-present\n':'fixture-absent\n'};}mutations++;present=false;return {body:badSuccess?'unknown\n':'Success\n'};}};
 const cleanup=await new AsyncFunction('evidence','subcheck','bootObservation','fixtureOemItems','ownedFixtureCleanupAllowed','unavailable','ui','plan','launcherVisibleInventory','screenshot','Unavailable','persist',js)(evidence,async(name,fn)=>{try{const result=await fn();checks.push({id:name,status:'passed'});return result}catch(e){checks.push({id:name,status:'failed'});throw e}},async()=>({boot:crossBoot&&++bootReads>1?'other':'same'}),['Music'],()=>true,msg=>{throw new Unavailable(msg)},ui,{fixture:{package:'ru.voboost.tests.launcherfixture'}},async()=>{stockChecks++;if(failRead){failRead=false;throw new Error('actual post-removal snapshot deadline');}return ['Music']},async()=>{},Unavailable,()=>{});
 const finallyCleanup=await new AsyncFunction('ownedFixtureCleanupAllowed',finalJs)(()=>true);
 let initialError;try{await cleanup(mode)}catch(e){initialError=e}
 if(reappear)present=true;
 const result=await finallyCleanup({scenario:'launcher',receipt,activeOperation:active,monitorHealthy:healthy,leaseStatus:lease},()=>cleanup("removal-only"));
 return {receipt,mutations,reads,stockChecks,result,initialError,checks};
}
test("grid failure preserves successful removal and does not repeat UI in finally",async()=>{const r=await run({failRead:true});assert.equal(r.mutations,1);assert.equal(r.receipt.status,"passed");assert.equal(r.stockChecks,1);assert(r.initialError);assert.equal(r.result.attempted,false);assert(r.checks.some(c=>c.id==="fixture-oem-grid-retained"&&c.status==="failed"));});
test("normal cleanup requires independent removal and OEM-grid proofs",async()=>{const r=await run();assert.equal(r.mutations,1);assert.equal(r.receipt.status,"passed");assert.deepEqual(r.checks.map(c=>c.id),["owned-fixture-cleanup","fixture-oem-grid-retained"]);assert.equal(r.stockChecks,1);});
test("exceptional removal-only cleanup never navigates crashed launcher",async()=>{const r=await run({mode:"removal-only"});assert.equal(r.mutations,1);assert.equal(r.stockChecks,0);assert.equal(r.receipt.status,"passed");assert.deepEqual(r.checks.map(c=>c.id),["owned-fixture-cleanup"]);});
test("active transaction, unhealthy monitor or lost lease forbid finally mutation",async()=>{for(const opts of [{active:{}},{healthy:false},{lease:"lost"}]){const r=await run({badSuccess:true,...opts});assert.equal(r.result.attempted,false);assert.equal(r.mutations,1);}});
test("reappearance after acknowledged removal never repeats mutation",async()=>{const r=await run({failRead:true,reappear:true});assert.equal(r.mutations,1);assert.equal(r.result.attempted,false);});
test("absence without acknowledged removal cannot pass",async()=>{const r=await run({present:false});assert.equal(r.mutations,0);assert.notEqual(r.receipt.status,"passed");});
test("boot change after removal cannot pass same-boot absence proof",async()=>{const r=await run({crossBoot:true});assert.equal(r.mutations,1);assert.notEqual(r.receipt.status,"passed");assert.equal(r.stockChecks,0);});
test("launcher summary and full verifier require independent grid proof",()=>{assert(source.includes('"18:owned-fixture-cleanup", "18:fixture-oem-grid-retained"'));assert(source.includes('case18 lacks post-cleanup OEM grid integrity proof'));assert(source.includes('await cleanupOwnedFixture("removal-only")'));});

test("ambiguous removal receipt never repeats dispatched mutation",async()=>{const r=await run({badSuccess:true});assert.equal(r.mutations,1);assert.equal(r.receipt.removalAttempted,true);assert.equal(r.receipt.removalAction,null);assert.notEqual(r.receipt.status,"passed");});

import Ajv2020 from "ajv/dist/2020.js";
test("cleanup schema accepts typed recovery metadata and keeps legacy archives closed",()=>{
 const schema=JSON.parse(readFileSync(new URL('../../release/emulator-runtime-evidence.schema.json',import.meta.url),'utf8'));
 const validate=new Ajv2020({strict:true,strictTypes:false,strictTuples:false}).compile(schema.$defs.fixtureCleanup);
 const old={package:'ru.voboost.tests.launcherfixture',source:'tests/emulator/agents/agent-launcher/app',owned:true,status:'unavailable',reason:'test',bootId:null,artifacts:[],originalAbsenceAction:1,installAction:2,removalAction:null};
 assert.equal(validate(old),true);
 assert.equal(validate({...old,source:'tests/fixtures/launcher'}),true);
 assert.equal(validate({...old,source:'tests/fixtures/foreign'}),false);
 assert.equal(validate({...old,removalAttempted:false,removalBootId:null}),true);
 for(const extra of [{removalAttempted:'false'},{removalAttempted:0},{removalBootId:'bad'},{removalBootId:3},{unknown:true}])assert.equal(validate({...old,...extra}),false);
 const passed={...old,status:'passed',removalAction:3,bootId:'f4ee3f64-4d0e-4cb9-8237-2748db029a24',artifacts:['receipt']};
 assert.equal(validate(passed),true);
 assert.equal(validate({...passed,removalAttempted:true,removalBootId:passed.bootId}),true);
 assert.equal(validate({...passed,removalAttempted:false}),false);
 assert.equal(validate({...passed,removalBootId:null}),false);
});
