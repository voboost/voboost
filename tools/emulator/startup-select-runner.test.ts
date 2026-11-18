import test from "node:test";
import assert from "node:assert/strict";
import {stripTypeScriptTypes} from "node:module";
import {readFileSync} from "node:fs";
import {startupDeferredSelection} from "./startup-deferred-selection.ts";
const boot="11111111-1111-1111-1111-111111111111",hash="a".repeat(64);
function makeBody(){const r={schemaVersion:1,releaseIdentity:"beta1",manifestSha256:hash,generationId:hash,catalogSha256:hash,startupMode:"interface",state:"ready",killed:false,panic:false,injections:[{id:"settings",process:"settings",state:"active",lifecycleGeneration:"3"}]};return `\nresident-status:\n/data/voboost/status.json\n${JSON.stringify(r)}\ncurrent-process-identities:\nschemaVersion=1\nbootId=${boot}\nenforcing=Enforcing\nprocess=voboost-inject pid=23 starttime=345 argv0=/data/voboost/voboost-inject\nprocess=com.qinggan.app.setting pid=42 starttime=456 argv0=com.qinggan.app.setting\nbootIdAfter=${boot}\nenforcingAfter=Enforcing\nbootCompletedAfter=1\ncurrent-process-identities-end=complete\n`;}
const path=process.env.STARTUP_GATE_SOURCE??new URL('../../release/emulator-gate.ts',import.meta.url);
test("actual selectStartup accepts saved none without applying none in current boot",async()=>{
 const source=readFileSync(path,'utf8');const code=source.slice(source.indexOf('  const selectStartup = async'),source.indexOf('  const returnInterface = async'));
 let selected=false,readbacks=0;
 const node=()=>({packageName:'settings',checkable:true,text:'None',checked:selected});
 const scope={copy:{languages:{en:{startup:[{value:'none',label:'None'}]}}},unavailable:(s:string)=>{throw Error(s)},bootObservation:async()=>({boot}),inspect:async()=>({boot,body:makeBody()}),language:'en',top:async()=>{},ui:{snapshot:async()=>[node(),{packageName:"settings",text:"Voboost",checked:true}],tap:async()=>{selected=true;}},uniqueNode:(nodes:any[],f:any)=>nodes.filter(f)[0],SETTINGS:'settings',focusedApplication:()=>true,manifest:{releaseIdentity:'beta1'},digest:()=>hash,readFileSync:()=>Buffer.from('manifest'),join:(...p:string[])=>p.join('/'),c:{closure:'closure'},startup:'interface',cli:async()=>{readbacks++;return{stdout:JSON.stringify({deviceSerial:'emulator-5566',installedVersion:'beta1',contentRelation:'same',daemonStatus:'running',activeGeneration:hash,startupMode:'none'})}},resultOk:()=>{},jsonReceipt:JSON.parse,serial:'emulator-5566',startupDeferredSelection,setTimeout:(fn:()=>void)=>{fn();return 0;}};
 const run=Function(...Object.keys(scope),stripTypeScriptTypes(code)+'return selectStartup;')(...Object.values(scope));
 await run('none');assert.equal(selected,true);assert.equal(readbacks,1);
});
