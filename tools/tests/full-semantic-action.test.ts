import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import test from "node:test";
import assert from "node:assert/strict";
import { fullFunctionalCase, semanticBoot, requiredSubchecksPassed } from "../emulator/full-semantic.ts";
const source=readFileSync(new URL("../emulator/visual-review.ts",import.meta.url),"utf8");
const body=source.slice(source.indexOf("export function actionCoverage"),source.indexOf("export function actionCoverage")+source.slice(source.indexOf("export function actionCoverage")).length).replace(/^export /,"");
const helpers=source.slice(source.indexOf("function observedNodes("),source.indexOf("function captureRequest("));
const coverage=new Function("fullFunctionalCase","semanticBoot","captureRequest","parseUi","focusedApplication","center","checked","cliReceipt","keys","requiredSubchecksPassed",stripTypeScriptTypes(helpers+body)+";return actionCoverage;")(
 fullFunctionalCase,semanticBoot,(a:any)=>a.request,(text:string)=>JSON.parse(text),()=>true,(n:any)=>n.center,
 (a:any)=>Buffer.from(a.text), (b:Buffer)=>JSON.parse(b.toString()),()=>{},requiredSubchecksPassed);
function fixture(){
 let id=0;const actions:any[]=[];const copy:any={languages:{}};
 const add=(number:number,locale:string,caption:string)=>{
  const snap={id:++id,caseNumber:number,context:{locale,bootId:"boot"},args:["--emulator-check","req"],request:{action:"snapshot"},artifacts:[{kind:"request",path:"req"},{kind:"stdout",text:JSON.stringify({schemaVersion:1,serial:"emulator-5566",body:JSON.stringify([{packageName:"com.qinggan.app.setting",enabled:true,text:caption,center:[5,8]}])})}]};actions.push(snap);
  actions.push({id:++id,caseNumber:number,context:{locale,bootId:"boot"},args:["--emulator-check","req"],request:{action:"tap",x:5,y:8},artifacts:[{kind:"request",path:"req"}]});
 };
 const measurements=[];
 for(const locale of ["en","ru","zh"]){copy.languages[locale]={layoutTopology:{panelTargets:[{class:"android.widget.Button",value:"uninstall"}]},uninstall:{buttonText:"remove-"+locale,cancelLabel:"cancel-"+locale,confirmLabel:"yes-"+locale}};add(21,locale,"remove-"+locale);add(21,locale,"cancel-"+locale);measurements.push({id:"uninstall-action-measurements-cancel-"+locale,status:"passed",artifacts:["XML","telemetry"]});}
 const observer={id:++id,caseNumber:17,context:{locale:"ru",bootId:"boot"},args:["--emulator-check","req"],request:{action:"observe-settings-uninstall"},artifacts:[{kind:"request",path:"req"},{kind:"stdout",text:JSON.stringify({schemaVersion:1,serial:"emulator-5566"})}]};actions.push(observer);add(17,"ru","remove-ru");add(17,"ru","yes-ru");
 const e:any={functionalMode:"full27",imageCapture:"deferred",runtime:{serial:"emulator-5566"},cases:[{number:21,subchecks:measurements},{number:17,status:"unavailable",actions:actions.filter(a=>a.caseNumber===17).map(a=>a.id),subchecks:["settings-uninstall-normal-absence-reboot","full-case-program-completed"].map(id=>({id,status:"passed"}))}],actions,boots:[{caseNumber:17,kind:"settings-uninstall",status:"unavailable",semanticStatus:"passed",screenshot:null,after:"after"}]};
 return {e,copy,normal:()=>({transactionId:"transaction",bootId:"after"})};
}
test("semantic action coverage accepts exact actual three-locale cancel and final confirm receipts without images",()=>{const {e,copy,normal}=fixture();assert.ok(coverage(e,copy,normal,true));assert.equal(coverage(e,copy,normal),null);});
for(const [label,mutate] of [
 ["missing locale",(e:any)=>e.actions=e.actions.filter((a:any)=>a.context.locale!=="zh")],
 ["wrong coordinates",(e:any)=>e.actions.find((a:any)=>a.request.action==="tap").request.x=99],
 ["missing final observer",(e:any)=>e.actions=e.actions.filter((a:any)=>a.request.action!=="observe-settings-uninstall")],
 ["boot unverified",(e:any)=>e.boots[0].semanticStatus="unavailable"],
 ["functional measurement unavailable",(e:any)=>e.cases[0].subchecks[0].status="unavailable"],
 ["final removal unavailable",(e:any)=>e.cases[1].subchecks[0].status="unavailable"],
] as const)test(label+" blocks semantic action coverage",()=>{const {e,copy,normal}=fixture();mutate(e);assert.equal(coverage(e,copy,normal,true),null);});
test("installed partial regression cannot use full semantic coverage",()=>{const {e,copy,normal}=fixture();e.regressionScenario="final-uninstall";assert.throws(()=>coverage(e,copy,normal,true));});
const gate=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
const ackSource=gate.slice(gate.indexOf("export async function recoveryBootAck"),gate.indexOf("export function observerArmed")).replace(/^export /,"");
const ack=new Function("semanticBoot",stripTypeScriptTypes(ackSource)+";return recoveryBootAck;")(semanticBoot);
test("recovery ACK requires same independently verified boot and explicit full27 semantic admission",async()=>{
 const identity={bootId:"boot",priorBootId:"prior",state:"waiting"};
 for(const mode of [undefined,"full27"]){const result=JSON.parse(await ack(identity,{functionalMode:mode,ownership:async()=>{},verify:async()=>({status:"unavailable",semanticStatus:"passed",screenshot:null,after:"boot",verifiedBoot:"boot"}),current:async()=>({boot:"boot",bootCompleted:true})}));assert.equal(result.decision,mode?"accept":"reject");}
 for(const mutate of [(r:any)=>r.semanticStatus="unavailable",(r:any)=>r.screenshot="fake.png",(r:any)=>r.after="other",(r:any)=>r.verifiedBoot="other"]){const row={status:"unavailable",semanticStatus:"passed",screenshot:null,after:"boot",verifiedBoot:"boot"};mutate(row);assert.equal(JSON.parse(await ack(identity,{functionalMode:"full27",ownership:async()=>{},verify:async()=>row,current:async()=>({boot:"boot",bootCompleted:true})})).decision,"reject");}
 assert.equal(JSON.parse(await ack(identity,{functionalMode:"full27",ownership:async()=>{},verify:async()=>({status:"unavailable",semanticStatus:"passed",screenshot:null,after:"boot",verifiedBoot:"boot"}),current:async()=>({boot:"other",bootCompleted:true})})).decision,"reject");
});
