import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {stripTypeScriptTypes} from "node:module";
import {installerRebootProgress} from "../emulator/installer-diagnostics.ts";
const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
const start=source.indexOf("  const install = async () =>");
const install=stripTypeScriptTypes(source.slice(start,source.indexOf("\n  const ",start+10)));
const helpers=stripTypeScriptTypes(source.slice(source.indexOf("export function installAcceptanceArmed("),source.indexOf("const STABLE ="))).replaceAll("export function","function").replaceAll("export async function","async function");
const before="00000000-0000-0000-0000-000000000001",boot="00000000-0000-0000-0000-000000000002",sha="1".repeat(64);
const exactKeys=(row,keys)=>assert.deepEqual(Object.keys(row).sort(),keys.sort());
const uuid=value=>/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(value);
async function run(mode){
 let observations=0,decisions=[],terminal=false,resultChecks=0;
 const context={
  MAX_CAPTURE:100000,installerRebootProgress,c:{closure:"unused"},digest:()=>sha,readFileSync:()=>"",join:()=>"",
  bootObservation:async()=>{observations++;if(observations===1)return {boot:before};
   if(mode==="unreadable")throw Error("unknown transport");
   return {boot:mode==="old"?before:boot,bootCompleted:true,rebootCount:1};},
  bootTiming:()=>{},bootCheck:async()=>({after:null,status:"failed",reason:"original boot observation failed"}),
  evidence:{boots:[]},monitorLive:()=>{},
  ui:{call:async action=>{decisions.push(action.decision);const {action:unused,...request}=action;
   if(action.decision==="reject")resolveChild({exitCode:1});
   return {body:JSON.stringify({...request,schemaVersion:1,state:action.decision==="status"?"waiting":"rejected",expiresMonotonicMs:"900100"})};}},
  resultOk:result=>{resultChecks++;if(result.exitCode!==0)throw Error("generic child failure");},
 };
 let resolveChild;
 context.cli=async(args,onOutput)=>{
  const child=new Promise(resolve=>resolveChild=resolve);
  onOutput("stdout","install-acceptance-armed "+JSON.stringify({schemaVersion:1,transactionId:sha,manifestSha256:sha,attemptToken:"3".repeat(64)})+"\nRestarting the head unit (reboot 1)\n");
  const deadline=setTimeout(()=>resolveChild({exitCode:1}),50);
  const result=await child;clearTimeout(deadline);terminal=true;return result;
 };
 const factory=Function(...Object.keys(context),"exactKeys","uuid",helpers+"let installerActive=false, installerMonitorStopped=false, monitor=Promise.resolve(), monitorResult=null;"+install+"return install;");
 const execute=factory(...Object.values(context),exactKeys,uuid);
 let error;try{await execute();}catch(e){error=e;}
 return {error,terminal,decisions,resultChecks};
}
test("actual install coordinator negative ACK settles child and retains monitor failure",async()=>{
 const r=await run("fresh");assert.equal(r.terminal,true);assert.deepEqual(r.decisions,["status","reject"]);assert.match(r.error.message,/original boot observation failed/);assert.match(r.error.message,/installer child: Error: generic child failure/);assert.equal(r.resultChecks,1);
});
test("actual coordinator cannot ACK old boot or unreadable observation",async()=>{
 for(const mode of ["old","unreadable"]){const r=await run(mode);assert.equal(r.terminal,true);assert.deepEqual(r.decisions,[]);assert.match(r.error.message,/negative acceptance unavailable/);assert.equal(r.resultChecks,1);}
});
