import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync,mkdirSync,writeFileSync,rmSync,chmodSync} from "node:fs";
import {join,resolve} from "node:path";
import {createHash,randomUUID} from "node:crypto";
import Ajv2020 from "ajv/dist/2020.js";
import {CASES,EXECUTION_ORDER,voyemuInvocation,nativeSessionFromConfig,nativeCaptureAction} from "../../release/emulator-gate.ts";
import {verificationContract} from "../emulator/native-session.ts";
const hash=(b:Buffer|string)=>createHash("sha256").update(b).digest("hex");
test("native transport preserves exact full27 and rejects legacy dispatch before execution",()=>{
 assert.equal(CASES.length,27);assert.deepEqual(EXECUTION_ORDER,[1,2,3,4,5,13,14,12,6,7,8,9,19,10,11,18,24,25,15,16,20,21,22,23,27,17,26]);
 for(const op of ["status","verify"] as const) assert.throws(()=>voyemuInvocation({nativeSession:{}} as any,op),/legacy/);
});
test("private root receipt derives native session without serial guesses and rejects public permissions",()=>{
 const dir=resolve("test-tmp/native-receipt-"+randomUUID());mkdirSync(dir,{recursive:true,mode:0o700});
 try {
  const exe=join(dir,"voyemu"),receiptFile=join(dir,"receipt.json");writeFileSync(exe,"fixture");
  writeFileSync(receiptFile,JSON.stringify({schema:2,ok:true,result:{profile:"free-09",session:{id:"a".repeat(32),ownership_id:"b".repeat(32)}}}),{mode:0o600});
  const c:any={nativeSession:{executable:exe,receiptFile},voyemuRoot:dir,voyemuProfile:"free-09"};
  assert.equal(nativeSessionFromConfig(c).session.id,"a".repeat(32));
  chmodSync(receiptFile,0o644);assert.throws(()=>nativeSessionFromConfig(c),/private/);
 }finally{rmSync(dir,{recursive:true});}
});
test("schema4 cannot claim legacy inventory equivalence and schema3 cannot claim native readiness",()=>{
 const schema=JSON.parse(readFileSync(new URL("../../release/emulator-runtime-evidence.schema.json",import.meta.url),"utf8"));
 const validate=new Ajv2020({strict:false}).compile(schema);
 const e:any={schemaVersion:4,verificationContract,evidenceType:"emulator-functional",invocationId:"host",releaseIdentity:"1.0.0-beta1",publication:"forbidden",productionEvidence:false,vehicleEvidence:false,passed:false,identity:Object.fromEntries(["package","closure","dmg","machine","cli"].map(k=>[k,"0".repeat(64)])),voyemuProfile:"free-09",preparedCheckInventory:[],runtime:null,leaseStatus:"retained",activeOperation:null,preparation:{status:"not-run",reason:"not started",kind:null,actionId:null,bootId:null,context:null,actions:[],artifacts:[]},fixtureCleanup:{package:"ru.voboost.tests.launcherfixture",source:"tests/emulator/agents/agent-launcher/app",owned:false,status:"not-run",reason:"none",originalAbsenceAction:null,installAction:null,removalAction:null,bootId:null,artifacts:[]},cases:CASES.map(c=>({number:c.number,id:c.id,status:"not-run",reason:"",actions:[],subchecks:[]})),actions:[],boots:[]};
 assert.equal(validate(e),true,JSON.stringify(validate.errors));
 assert.equal(validate({...e,preparedCheckInventory:[{id:"boot",expected:1}]}),false);
 assert.equal(validate({...e,schemaVersion:3}),false);
 const {verificationContract:_,...legacy}=e;
 assert.equal(validate({...legacy,schemaVersion:3,preparedCheckInventory:[{id:"boot",expected:1}]}),true);
 assert.equal(validate({...e,verificationContract:{...verificationContract,excluded:[]}}),false);
});
test("staged native wiring has no old CLI screenshot bridge on native branch",()=>{
 const source=readFileSync(new URL("../../release/emulator-gate.ts",import.meta.url),"utf8");
 const native=source.slice(source.indexOf("if (native) {",source.indexOf("const screenshot =")),source.indexOf("const shot = await ui.screenshot"));
 assert.match(native,/native.capture/);assert.doesNotMatch(native,/ui.screenshot|src\/cli.ts|--emulator-check/);
});

import {deflateSync} from "node:zlib";
import {validatePng,parseUi} from "../emulator/installer-ui.ts";
import {allowBlackFrame} from "../emulator/native-session.ts";
import {selectedLauncherTheme} from "../../release/emulator-gate.ts";
import {visualChecks} from "../emulator/visual-review.ts";
function raster(black=false):Buffer {
 const chunk=(type:string,data:Buffer)=>{
  const t=Buffer.from(type),a=Buffer.concat([t,data]);let n=0xffffffff;
  for(const b of a){n^=b;for(let i=0;i<8;i++)n=n&1?(n>>>1)^0xedb88320:n>>>1;}
  const out=Buffer.alloc(data.length+12);out.writeUInt32BE(data.length);a.copy(out,4);out.writeUInt32BE((n^0xffffffff)>>>0,out.length-4);return out;
 };
 const header=Buffer.alloc(13);header.writeUInt32BE(1920);header.writeUInt32BE(720,4);header[8]=8;header[9]=2;
 const row=Buffer.alloc(1920*3+1,black?0:55);row[0]=0;
 return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk("IHDR",header),chunk("IDAT",deflateSync(Buffer.concat(Array.from({length:720},()=>row)))),chunk("IEND",Buffer.alloc(0))]);
}
test("schema2 captures reject black placeholders rather than inventing a missing cluster screen",()=>{
 const image=raster(true);
 assert.throws(()=>validatePng(image),/black/);
 const manifest:any={cluster_powered:false,qnx_frame_verified:false};
 const cluster={surface:"cluster",source:"placeholder",derived:true,reason:"cluster_off"};
 assert.throws(()=>validatePng(image,allowBlackFrame(manifest,cluster)),/black/);
 for(const frame of [{...cluster,surface:"media_primary"},{...cluster,source:"android_sdk"},{...cluster,derived:false}])
  assert.throws(()=>validatePng(image,allowBlackFrame(manifest,frame)));
});
test("retained native bundle rejects foreign session even with identical PID, boot and internally matching response",()=>{
 const dir=resolve("test-tmp/native-bundle-"+randomUUID());mkdirSync(dir,{recursive:true,mode:0o700});
 try{
  const sessionId="a".repeat(32),foreign="f".repeat(32),boot="aaaaaaaa-1111-2222-3333-444444444444",image=raster();
  const frames=["primary","secondary"].map((screen,index)=>({screen,file:screen+".png",source:"android_sdk",derived:false,sdk_display_id:index,display_id:null,width:1920,height:720,source_width:1920,source_height:720,rect:{x:0,y:0,width:1920,height:720},sha256:hash(image),sequence:"0",source_timestamp_ns:"0",source_clock_domain:"sdk",host_interval:{clock_domain:"host.steady",clock_id:sessionId,start_ns:"1",end_ns:"2"},freshness:{source_clock_mapped:false,newer_guest_frame_proven:false}}));
  let result:any={schema:2,screens:["primary","secondary"],session_id:sessionId,android_pid:123,android_boot_id:boot,accepted:true,frames_consistent:true,capture_mode:"parallel_fenced",atomic:false,android_boot_identity_verified:true,android_frames_verified:true,skew_upper_bound_ms:1,frames,cluster_powered:false,qnx_frame_verified:false};
  const request:any={schema:2,request_id:"request",op:"session.screenshot",profile:"free-09",session:{id:sessionId}};
  const requestFile=join(dir,"request.json"),stdout=join(dir,"response.json"),manifest=join(dir,"manifest.json"),alias=join(dir,"chosen.png");
  const action:any={id:1,args:["test","--root",dir,"--request",join(dir,"private.json")],nativeRequest:requestFile,nativeOperation:"session.screenshot",exitCode:0,context:{bootId:boot,runtime:{sessionId}},nativeAuthority:{requestId:"request",operationId:"c".repeat(32),sessionId,manifest,output:alias,display:"primary"},artifacts:[]};
  const retain=(path:string,bytes:Buffer,kind:string)=>{writeFileSync(path,bytes);const old=action.artifacts.find((a:any)=>a.path===path);if(old)old.sha256=hash(bytes);else action.artifacts.push({path,sha256:hash(bytes),kind});};
  const refresh=()=>{
   retain(requestFile,Buffer.from(JSON.stringify(request)),"request");
   retain(stdout,Buffer.from(JSON.stringify({schema:2,request_id:"request",operation_id:"c".repeat(32),ok:true,elapsed_ms:1,result})),"stdout");
   retain(manifest,Buffer.from(JSON.stringify(result)),"archive");
   for(const frame of frames)retain(join(dir,frame.file),image,"png");
   retain(alias,image,"png");
  };
  refresh();const evidence:any={schemaVersion:4,verificationContract,runtime:{sessionId,pid:123}};
  assert.equal(nativeCaptureAction(evidence,action).session_id,sessionId);
  for(const flag of ["cluster_powered","qnx_frame_verified"]) {
    retain(manifest,Buffer.from(JSON.stringify({...result,[flag]:true})),"archive");
    assert.throws(()=>nativeCaptureAction(evidence,action),/retained native capture authority/);
    refresh();
  }
  request.session.id=foreign;result.session_id=foreign;refresh();
  assert.throws(()=>nativeCaptureAction(evidence,action),/request session/);
  request.session.id=sessionId;refresh();
  assert.throws(()=>nativeCaptureAction(evidence,action),/fence/);
  result.session_id=sessionId;action.nativeAuthority.sessionId=foreign;refresh();
  assert.throws(()=>nativeCaptureAction(evidence,action),/correlation/);
 }finally{rmSync(dir,{recursive:true});}
});
function themeNodes(selected="Classic"):any[] {
 const windowRef:any={rootPackage:"com.qinggan.app.setting",type:1,active:true,inputFocused:true,rootStatus:"available",scope:"application"};
 const base:any={packageName:"com.qinggan.app.setting",enabled:true,windowRef,bounds:[0,0,100,100],visibleToUser:true};
 const nodes:any[]=[{...base,resource:base.packageName+":id/recyclerview_theme_pic",parent:null}];
 for(const label of ["Classic","Speedy Dash","Other"]){
  const parent=nodes.length;nodes.push({...base,parent:0,resource:base.packageName+":id/rl_root",clickable:true,selected:label===selected});
  nodes.push({...base,parent,resource:base.packageName+":id/tv_title",text:label,selected:label===selected});
 }
 return nodes;
}
test("actual selected-card parser chooses Speedy color and Classic metal, rejects disagreeing selection",()=>{
 assert.equal(selectedLauncherTheme(themeNodes("Classic")).style,"metal");
 assert.equal(selectedLauncherTheme(themeNodes("Speedy Dash")).style,"color");
 const nodes=themeNodes();nodes[2].selected=false;assert.throws(()=>selectedLauncherTheme(nodes),/identity differs/);
 assert.throws(()=>selectedLauncherTheme(themeNodes("Other")),/no reviewed/);
 const criterion=(theme:any)=>visualChecks(12,theme)[0].criteria.find(c=>c.id==="theme-home-surface")!.expected;
 assert.match(criterion({label:"Speedy Dash",style:"color"}),/ordinary full-color/);
 assert.doesNotMatch(criterion({label:"Speedy Dash",style:"color"}),/uses steel/);
 assert.match(criterion({label:"Classic",style:"metal"}),/uses steel/);
});
