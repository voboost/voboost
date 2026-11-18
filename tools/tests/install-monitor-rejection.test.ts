import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {stripTypeScriptTypes} from "node:module";
const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
const body=stripTypeScriptTypes(source.slice(source.indexOf("export function installAcceptanceAction("),source.indexOf("const STABLE ="))).replaceAll("export function","function").replaceAll("export async function","async function");
const exactKeys=(row,keys)=>assert.deepEqual(Object.keys(row).sort(),keys.sort());
const uuid=value=>/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(value);
const rejectFailedInstall=Function("exactKeys","uuid",body+"return rejectFailedInstall;")(exactKeys,uuid);
const armed={schemaVersion:1,transactionId:"1".repeat(64),manifestSha256:"1".repeat(64),attemptToken:"3".repeat(64)};
const before="00000000-0000-0000-0000-000000000001";
const boot="00000000-0000-0000-0000-000000000002";
const observation={boot,bootCompleted:true,rebootCount:1};
test("failed monitor settles pending native child by status and reject, never accept",async()=>{
 const decisions=[];let settled=false;
 await rejectFailedInstall(armed,before,async()=>observation,async action=>{
  decisions.push(action.decision);
  if(action.decision==="reject")settled=true;
  const {action:unused,...request}=action;
  return {body:JSON.stringify({...armed,...request,state:action.decision==="status"?"waiting":"rejected",expiresMonotonicMs:"900100"})};
 });
 assert.deepEqual(decisions,["status","reject"]);assert.equal(settled,true);
});
test("old, malformed or foreign-phase boot cannot send any decision",async()=>{
 for(const current of [{...observation,boot:before},{...observation,boot:"bad"},{...observation,rebootCount:2}]){
  let calls=0;await assert.rejects(rejectFailedInstall(armed,before,async()=>current,async()=>{calls++;return {}; }));
  assert.equal(calls,0);
 }
});
test("failed fresh observation cannot reuse old boot or send accept",async()=>{
 let calls=0;await assert.rejects(rejectFailedInstall(armed,before,async()=>{throw Error("transport unknown");},async()=>{calls++;return {};}));assert.equal(calls,0);
});
test("foreign pending receipt blocks rejection",async()=>{
 const decisions=[];await assert.rejects(rejectFailedInstall(armed,before,async()=>observation,async action=>{
  decisions.push(action.decision);const {action:unused,...request}=action;
  return {body:JSON.stringify({...armed,...request,bootId:before,state:"waiting",expiresMonotonicMs:"900100"})};
 }));assert.deepEqual(decisions,["status"]);
});

test("incomplete but exact current intermediate boot can reject pending child without accepting it", async()=>{
 const decisions=[];
 await rejectFailedInstall(armed,before,async()=>({...observation,bootCompleted:false}),async action=>{
  decisions.push(action.decision);const {action:unused,...request}=action;
  return {body:JSON.stringify({...armed,...request,state:action.decision==="status"?"waiting":"rejected",expiresMonotonicMs:"900100"})};
 });
 assert.deepEqual(decisions,["status","reject"]);
});
