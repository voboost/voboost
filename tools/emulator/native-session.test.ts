import { createHash } from "node:crypto";
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, rmSync, statSync, symlinkSync, unlinkSync } from "node:fs";
import { resolve, join } from "node:path";
import { invocation, response, status, ready, primaryHome, primaryHomeCaptureBoot, capture, captureFiles, allowBlackFrame, NativeSession, nativeDispatch, nativeReadTransportTimeout, NativeTreeUnavailable, NativeTreeReadTimeout, NativeSystemUnavailable, readSystemSample, NativeAppUnlaunchable, NativeInputRejected, missingInputWindow } from "./native-session.ts";
import { waitPrimaryHome, NativeHomeDeliveryPending } from "./native-session.ts";
const session={id:"a".repeat(32),ownership_id:"b".repeat(32)};
const config={executable:"/installed/voyemu",root:"/runtime",profile:"free-09",session};
const state=()=>({ok:true,session_id:session.id,android_pid:123,stopped:false,deliveries:{android:{pid:123,serial:"emulator-5554",quickboot:{mode:"restored"},guest_acknowledged:true,home:{status:"visible_on_primary"},displays:{status:"both_guest_displays_ready"}}}});
const envelope=(q:any,result:any)=>({schema:2,request_id:q.request_id,operation_id:"c".repeat(32),ok:true,elapsed_ms:1,result});
function manifest(screens=["primary","secondary"],rect={x:0,y:0,width:1920,height:720}) {
 return {schema:2,session_id:session.id,android_pid:123,screens,accepted:true,frames_consistent:true,capture_mode:"parallel_fenced",atomic:false,android_boot_identity_verified:true,android_boot_id:"aaaaaaaa-1111-2222-3333-444444444444",android_frames_verified:true,cluster_powered:false,qnx_frame_verified:false,skew_upper_bound_ms:4,frames:screens.map(screen=>({screen,sdk_display_id:screen==="primary"?0:1,display_id:null,source:"android_sdk",derived:rect.width!==1920,width:rect.width,height:rect.height,source_width:1920,source_height:720,rect,file:screen+".png",sha256:"a".repeat(64),sequence:"0",source_timestamp_ns:"0",source_clock_domain:"sdk",host_interval:{clock_domain:"host.steady",clock_id:session.id,start_ns:"1",end_ns:"2"},freshness:{source_clock_mapped:false,newer_guest_frame_proven:false}}))};
}
test("canonical schema 2 invocation, session authority and removed aliases",()=>{
 const i=invocation(config,"session.status","/report/request.json");
 assert.equal(i.request.schema,2); assert.deepEqual(i.args,["test","--root","/runtime","--request","/report/request.json"]);assert.equal(i.request.session,session);
 for(const op of ["output.status","output.tree","control.wait","test.status","input.drag","fixture.apply"]) assert.throws(()=>invocation(config,op as any,"/request"));
 assert.throws(()=>invocation(config,"session.status","/request",{session:{}}));
});
test("response rejects foreign authority, old schema and uncertain transport",()=>{
 const q=invocation(config,"session.status","/request").request, v=envelope(q,state());
 assert.equal(response({exitCode:0,stdout:JSON.stringify(v)},q).android_pid,123);
 for(const patch of [{schema:1},{request_id:"foreign"},{ok:false},{operation_id:"invalid"},{result:{accepted:false}},{result:{status:"unavailable"}}]) assert.throws(()=>response({exitCode:0,stdout:JSON.stringify({...v,...patch})},q));
 for(const result of [{exitCode:4,stdout:"{}"},{exitCode:0,stdout:"{}",timedOut:true},{exitCode:0,stdout:"{}",overflow:true}]) assert.throws(()=>response(result,q),/unresolved/);
});
test("pending receipt is observable and does not authorize replay",()=>{
 const q=invocation(config,"operation.get","/request",{target:{request_id:"original"}}).request;
 for (const status of ["running","failed","unavailable"]) assert.equal(response({exitCode:0,stdout:JSON.stringify(envelope(q,{status,error:{code:"original"}}))},q).status,status);
});
test("current session readiness retains scoped proof",()=>{
 assert.equal(status(state(),session).actualBoot,"restored");
 assert.equal(ready({predicate:"android.ready",ready:true,last_observation:state()},session).pid,123);
 assert.throws(()=>status({...state(),android_pid:999},session));
 assert.throws(()=>ready({predicate:"android.ready",ready:false,last_observation:state()},session));
});
test("capture uses logical screens without placeholder and validates ROI",()=>{
 assert.equal(capture(manifest(),session,123,500).frames.length,2);
 const rect={x:10,y:20,width:100,height:80};
 assert.equal(capture(manifest(["secondary"],rect),session,123,500,["secondary"],rect).frames[0].width,100);
 assert.equal(allowBlackFrame({},{}),false);
 for(const patch of [{schema:1},{session_id:"foreign"},{android_pid:999},{atomic:true},{skew_upper_bound_ms:501},{android_boot_id:"invalid"}]) assert.throws(()=>capture({...manifest(),...patch},session,123,500));
 for(const patch of [{screen:"cluster"},{sdk_display_id:1},{display_id:1},{derived:true},{width:1280},{file:"../outside.png"},{freshness:{source_clock_mapped:false,newer_guest_frame_proven:true}},{host_interval:{clock_domain:"host.steady",clock_id:session.id,start_ns:"3",end_ns:"2"}}]) {const v=manifest();Object.assign(v.frames[0],patch);assert.throws(()=>capture(v,session,123,500));}
});
test("retained manifest, hashes and decoded ROI dimensions must agree",()=>{
 const v:any=manifest(["primary"],{x:0,y:0,width:100,height:80});v.artifact_dir="/capture";v.manifest="/capture/manifest.json";
 const read=(p:string)=>p.endsWith("manifest.json")?Buffer.from(JSON.stringify(v)):Buffer.from("png");
 assert.equal(captureFiles(v,"/capture",read,()=>"a".repeat(64),()=>({width:100,height:80})).schema,2);
 assert.throws(()=>captureFiles(v,"/capture",read,()=>"b".repeat(64),()=>({width:100,height:80})));
 assert.throws(()=>captureFiles(v,"/capture",read,()=>"a".repeat(64),()=>({width:1920,height:720})));
});
for(const roi of [false,true])test('real NativeSession.capture admits API build/tmp staging and verifies retained '+(roi?'keyboard ROI':'paired screens'),async()=>{
 const scratch=resolve(import.meta.dirname,'../../build/unit-fixtures');mkdirSync(scratch,{recursive:true});
 const root=mkdtempSync(join(scratch,'native-capture-contract-'));
 try{
  const executable=join(root,'voyemu');writeFileSync(executable,'fixture executable, never launched');
  const directory=join(root,'.local/build/tmp',roi?'keyboard-capture-fixture':'voboost-capture-fixture');
  const screens:('primary'|'secondary')[]=roi?['primary']:['primary','secondary'],rect=roi?{x:10,y:20,width:100,height:80}:undefined;
  const dimensions=rect??{x:0,y:0,width:1920,height:720},requests:any[]=[];
  const client=new NativeSession({...config,root,executable},join(root,'requests'),async i=>{
   assert.equal(nativeDispatch(i),true);const q=JSON.parse(readFileSync(i.args[4],'utf8'));requests.push(q);
   if(q.op==='session.status')return {exitCode:0,stdout:JSON.stringify(envelope(q,state()))};
   assert.equal(q.op,'session.screenshot');assert.equal(q.artifact_dir,directory);assert.deepEqual(q.screens,screens);assert.deepEqual(q.rect,rect);
   assert.equal(q.timeout_ms,10000);assert.equal(q.max_skew_ms,500);assert.equal(existsSync(directory),false);
   const value:any=manifest(screens,dimensions);value.artifact_dir=directory;value.manifest=join(directory,'manifest.json');
   mkdirSync(directory,{recursive:true,mode:0o700});
   for(const frame of value.frames){const bytes=Buffer.from('captured-'+frame.screen);frame.sha256=createHash('sha256').update(bytes).digest('hex');writeFileSync(join(directory,frame.file),bytes);}
   writeFileSync(value.manifest,JSON.stringify(value));return {exitCode:0,stdout:JSON.stringify(envelope(q,value))};
  },(bytes,allowBlack)=>{assert.equal(allowBlack,false);assert.ok(bytes.toString().startsWith('captured-'));return dimensions;});
  const result=await client.capture(directory,500,screens,rect);
  assert.deepEqual(requests.map(q=>q.op),['session.status','session.screenshot','session.status']);
  assert.equal(result.manifest.artifact_dir,directory);assert.equal(result.identity.pid,123);
  for(const screen of screens){assert.equal(result.screens[screen],join(directory,screen+'.png'));assert.equal(readFileSync(result.screens[screen],'utf8'),'captured-'+screen);}
 }finally{rmSync(root,{recursive:true,force:true});}
});
test('real NativeSession.capture rejects former and sibling staging roots before mock API dispatch',async()=>{
 const scratch=resolve(import.meta.dirname,'../../build/unit-fixtures');mkdirSync(scratch,{recursive:true});
 const root=mkdtempSync(join(scratch,'native-capture-bounds-'));
 try{
  const executable=join(root,'voyemu');writeFileSync(executable,'fixture executable, never launched');let calls=0;
  const client=new NativeSession({...config,root,executable},join(root,'requests'),async()=>{calls++;throw Error('unexpected mock API dispatch');},()=>({width:1920,height:720}));
  for(const directory of [join(root,'.local/tmp/capture'),join(root,'.local/build/tmp-other/capture'),join(root,'.local/build/tmp'),'relative/capture']){
   await assert.rejects(()=>client.capture(directory),/native capture directory outside owned runtime artifacts/);assert.equal(calls,0);
  }
 }finally{rmSync(root,{recursive:true,force:true});}
});

test("injected CLI persists private schema 2 JSON and routes sources, watches and receipts",async()=>{
 const base=mkdtempSync(resolve("adapter-test-"));try {
 const executable=join(base,".local/native-cpp/bin/voyemu");mkdirSync(join(base,".local/native-cpp/bin"),{recursive:true});writeFileSync(executable,"fixture executable");
 const requests:any[]=[];
 const client=new NativeSession({...config,root:base,executable},join(base,"requests"),async i=>{
  assert.equal(nativeDispatch(i),true);assert.equal(JSON.stringify(i).includes(session.ownership_id),false);
  const file=i.args[4],q=JSON.parse(readFileSync(file,"utf8"));assert.equal(statSync(file).mode&0o077,0);assert.equal(q.schema,2);requests.push(q);
  const sample = q.op === "session.ui" ? {format:"voyemu.session.ui.v1",profile:q.profile,session_id:q.session.id,
    status:"ready",complete:true,atomic_ui_state:false,cluster:null,
    multimedia:[null,{screen:"secondary",status:"ok",complete:true,source:"android.ui",nodes:[],windows:[]}]} : {status:"ready"};
  return {exitCode:0,stdout:JSON.stringify(envelope(q,q.op==="session.status"?state():sample))};
 },()=>({width:1920,height:720}));
 await client.status();await client.multimediaUi(["secondary"]);await client.can({states:["IVI_SOC_MODESET"]});await client.system();await client.state();await client.watch({op:"eq",path:"/can/value",value:5});
 await client.readWatch({watch_id:"w",epoch:"e",cursor:"0"});await client.stopWatch({watch_id:"w",epoch:"e"});await client.wait({op:"present",path:"/ui"});await client.operation({request_id:"original"});await client.operation({operation_id:"c".repeat(32)});
 assert.deepEqual(requests.map(q=>q.op),["session.status","session.ui","vehicle.can.get","multimedia.system","session.observe","session.watch.start","session.watch.read","session.watch.stop","session.wait","operation.get","operation.get"]);
 assert.deepEqual(requests[1].screens,["secondary"]);assert.deepEqual(requests[9].target,{request_id:"original"});
 assert.equal(new Set(requests.map(q=>q.request_id)).size,requests.length);
 await assert.rejects(()=>client.operation({request_id:"x",operation_id:"y"} as any),/target/);
 writeFileSync(executable,"modified");await assert.rejects(()=>client.status(),/executable changed/);
 } finally {rmSync(base,{recursive:true,force:true});}
});

test("reboot admits bounded warm/cold commands with IPC margin",()=>{
 for (const mode of ["warm","cold"]) {
  const i=invocation(config,"multimedia.reboot","/request",{mode});
  assert.equal(i.timeoutMs,185000);assert.equal(i.request.mode,mode);
  assert.equal(invocation(config,"multimedia.reboot","/request",{mode,timeout_ms:1}).timeoutMs,5001);
 }
 for (const extra of [{},{mode:"hard"},{mode:"warm",timeout_ms:0},{mode:"cold",timeout_ms:180001},{mode:"warm",timeout_ms:1.5}])
  assert.throws(()=>invocation(config,"multimedia.reboot","/request",extra),/reboot request/);
});
test("TREE_REJECTED finite diagnostics preserve receipt authority and reject arbitrary payload",()=>{
 const q=invocation(config,"session.ui","/request").request;
 const base="TREE_REJECTED: guest could not verify current UI tree";
 const rejected=(message:string)=>({schema:2,request_id:q.request_id,operation_id:"c".repeat(32),ok:false,elapsed_ms:1,error:{code:"TREE_REJECTED",message,outcome:"not_started",retryable:false}});
 for (const suffix of ["","; reason=display_unavailable","; error_type=RuntimeException; cause_type=SecurityException","; reason=observer_unavailable; error_type=IllegalStateException; cause_type=RemoteException"]) {
  assert.throws(()=>response({exitCode:4,stdout:JSON.stringify(rejected(base+suffix))},q),error=>{
   assert.ok(error instanceof NativeTreeUnavailable);assert.equal(error.requestId,q.request_id);assert.equal(error.operationId,"c".repeat(32));assert.equal(error.retryable,false);assert.ok(error.message.includes(base+suffix));return true;
  });
 }
 for (const suffix of ["; reason=arbitrary","; error_type=java.lang.RuntimeException","; reason=display_unavailable; reason=display_changed","; cause_type=IOException; error_type=RuntimeException","; reason=display_unavailable\nsecret","; unknown=IOException","; reason=deadline; "]) 
  assert.throws(()=>response({exitCode:4,stdout:JSON.stringify(rejected(base+suffix))},q),e=>!(e instanceof NativeTreeUnavailable));
 const v=rejected(base+"; reason=observer_unavailable");
 for (const patch of [{request_id:"foreign"},{operation_id:"invalid"},{extra:true}]) 
  assert.throws(()=>response({exitCode:4,stdout:JSON.stringify({...v,...patch})},q),e=>!(e instanceof NativeTreeUnavailable));
 assert.throws(()=>response({exitCode:4,stdout:JSON.stringify(v)}, {...q,op:"multimedia.reboot"}),e=>!(e instanceof NativeTreeUnavailable));
});
test("uncertain reboot dispatch is never replayed",async()=>{
 const base=mkdtempSync(resolve("adapter-reboot-test-"));try {
  const executable=join(base,".local/native-cpp/bin/voyemu");mkdirSync(join(base,".local/native-cpp/bin"),{recursive:true});writeFileSync(executable,"fixture");
  let calls=0;const client=new NativeSession({...config,root:base,executable},join(base,"requests"),async i=>{
   calls++;assert.equal(nativeDispatch(i),true);const q=JSON.parse(readFileSync(i.args[4],"utf8"));assert.equal(q.op,"multimedia.reboot");assert.equal(q.mode,"cold");assert.equal(i.timeoutMs,185000);
   return {exitCode:null,stdout:"",timedOut:true};
  },()=>({width:1,height:1}));
  await assert.rejects(()=>client.reboot("cold"),/inspect durable operation receipt/);assert.equal(calls,1);
 }finally{rmSync(base,{recursive:true,force:true});}
});

test("application launch carries a boot fence and bounded single request",()=>{
 const boot_id="12345678-1234-1234-1234-123456789abc";
 const fields={package:"com.example.app",boot_id,screen:"secondary",component:"com.example.app/.Main"};
 const i=invocation(config,"multimedia.launch","/request",fields);
 assert.equal(i.request.op,"multimedia.launch"); assert.equal(i.request.boot_id,boot_id); assert.equal(i.timeoutMs,15000);
 for(const patch of [{package:"com.example;reboot"},{boot_id:"stale"},{screen:"cluster"},{component:"foreign/.Main"}]) assert.throws(()=>invocation(config,"multimedia.launch","/request",{...fields,...patch}));
 assert.throws(()=>response({exitCode:4,stdout:"{}"},i.request),/unresolved/);
});

test("real action-8 launch rejection retains not_started receipt without authorizing replay",()=>{
 const q=invocation(config,"multimedia.launch","/request",{package:"com.qinggan.settings",boot_id:"12345678-1234-1234-1234-123456789abc"}).request;
 const v={elapsed_ms:60.798,error:{code:"APP_UNLAUNCHABLE",message:"APP_UNLAUNCHABLE: package has no unique launcher activity",outcome:"not_started",retryable:false},ok:false,operation_id:"a211fe4baac04af071f584e952b9ffd9",request_id:q.request_id,schema:2};
 const result={exitCode:4,stdout:JSON.stringify(v)};
 assert.throws(()=>response(result,q),error=>{assert.ok(error instanceof NativeAppUnlaunchable);assert.equal(error.outcome,"not_started");assert.equal(error.retryable,false);assert.equal(error.requestId,q.request_id);assert.equal(error.operationId,v.operation_id);return true;});
 for (const patch of [{request_id:"foreign"},{operation_id:"invalid"},{schema:1},{extra:true},{elapsed_ms:-1},{error:{...v.error,outcome:"unknown"}},{error:{...v.error,retryable:true}},{error:{...v.error,message:v.error.message+"\nsecret"}},{error:{...v.error,extra:true}}])
  assert.throws(()=>response({...result,stdout:JSON.stringify({...v,...patch})},q),/unresolved/);
 for (const patch of [{timedOut:true},{overflow:true},{signal:"SIGTERM"},{exitCode:null}]) assert.throws(()=>response({...result,...patch},q),/unresolved/);
 assert.throws(()=>response(result,{...q,op:"multimedia.reboot"}),/unresolved/);
});

test("definitive stale pointer rejection retains authority and never becomes transport certainty",()=>{
 const q=invocation(config,"input.gesture","/request").request;
 const v={elapsed_ms:37.873,error:{code:"TEST_INPUT_STALE",message:"TEST_INPUT_STALE: target identity or geometry changed before dispatch",outcome:"not_started",retryable:false},ok:false,operation_id:"f17c31c257d7a717afd65b240e50bded",request_id:q.request_id,schema:2};
 const result={exitCode:4,stdout:JSON.stringify(v)};
 for(const op of ["input.tap","input.gesture"]) assert.throws(()=>response(result,{...q,op}),error=>{
  assert.ok(error instanceof NativeInputRejected);assert.equal(error.code,"TEST_INPUT_STALE");assert.equal(error.outcome,"not_started");assert.equal(error.retryable,false);assert.equal(error.requestId,q.request_id);assert.equal(error.operationId,v.operation_id);return true;
 });
 for(const patch of [{schema:1},{request_id:"foreign"},{operation_id:"invalid"},{ok:true},{extra:true},{elapsed_ms:-1},{elapsed_ms:null},...[
  {code:"TEST_INPUT_REJECTED"},{message:v.error.message+" extra"},{outcome:"unknown"},{outcome:"started"},{retryable:true},{extra:true}
 ].map(error=>({error:{...v.error,...error}}))]) assert.throws(()=>response({...result,stdout:JSON.stringify({...v,...patch})},q),/unresolved/);
 for(const patch of [{timedOut:true},{overflow:true},{signal:"SIGTERM"},{exitCode:null},{exitCode:1}]) assert.throws(()=>response({...result,...patch},q),/unresolved/);
 for(const op of ["input.key.press","multimedia.reboot","multimedia.launch","session.ui"]) assert.throws(()=>response(result,{...q,op}),/unresolved/);
});
test("definitive pointer rejection is dispatched once without automatic replay",async()=>{
 const base=mkdtempSync(resolve("adapter-pointer-test-"));try {
  const executable=join(base,".local/native-cpp/bin/voyemu");mkdirSync(join(base,".local/native-cpp/bin"),{recursive:true});writeFileSync(executable,"fixture");
  let calls=0;const client=new NativeSession({...config,root:base,executable},join(base,"requests"),async i=>{
   calls++;const q=JSON.parse(readFileSync(i.args[4],"utf8"));
   return {exitCode:4,stdout:JSON.stringify({schema:2,request_id:q.request_id,operation_id:"c".repeat(32),ok:false,elapsed_ms:1,error:{code:"TEST_INPUT_STALE",message:"TEST_INPUT_STALE: target identity or geometry changed before dispatch",outcome:"not_started",retryable:false,details:{reason:"target_changed",target_count:1}}})};
  },()=>({width:1,height:1}));
  await assert.rejects(()=>client.call("input.gesture"),NativeInputRejected);assert.equal(calls,1);
 }finally{rmSync(base,{recursive:true,force:true});}
});

// Recorded failed OFF action: plans/2026-10-15/beta1-api2-drawer171-24/action-346-stdout.txt.
const recordedOffFence = {elapsed_ms:282.001,error:{code:"TEST_INPUT_STALE",message:"TEST_INPUT_STALE: target identity or geometry changed before dispatch",outcome:"not_started",retryable:false},ok:false,operation_id:"3d0f7383d11d6482fb49ff013f4d4cbb",request_id:"3a91c2af-d7b5-4024-9475-68596fe858ca",schema:2};
test("recorded OFF rejection supports closed numeric window change diagnostics",()=>{
 const q={...invocation(config,"input.tap","/request").request,request_id:recordedOffFence.request_id};
 const details={reason:"window_changed",expected_window:{id:9,type:1,layer:2,bounds:{x:0,y:0,width:1920,height:720}},observed_window:{id:9,type:1,layer:3,bounds:{x:0,y:0,width:1920,height:720}}};
 const reject=(error:any)=>response({exitCode:4,stdout:JSON.stringify({...recordedOffFence,error})},q);
 assert.throws(()=>reject(recordedOffFence.error),e=>e instanceof NativeInputRejected && e.details===undefined);
 for(const reason of ["boot_changed","source_changed","window_missing","window_changed","target_missing","target_ambiguous","target_changed","point_outside","coverage_unavailable"]) {
  const d={...details,reason,target_count:2147483647};
  assert.throws(()=>reject({...recordedOffFence.error,details:d}),e=>{
   assert.ok(e instanceof NativeInputRejected);assert.deepEqual(e.details,d);assert.ok(e.message.includes('"layer":3'));assert.equal(e.retryable,false);return true;
  });
 }
 const blocked={...recordedOffFence.error,code:"TEST_INPUT_BLOCKED",message:"TEST_INPUT_BLOCKED: complete actionable input coverage unavailable",details:{reason:"coverage_unavailable"}};
 assert.throws(()=>reject(blocked),e=>e instanceof NativeInputRejected && e.code==="TEST_INPUT_BLOCKED");
 for(const d of [null,[],"secret",{}, {reason:"unknown"},{reason:"window_changed",secret:"private"},{...details,target_count:-1},{...details,target_count:2147483648},{...details,target_count:0.5},{...details,target_count:"1"},...[
  {},[],null,{id:"9"},{id:2147483648},{id:-2147483649},{layer:1.5},{type:null},{title:"private"},{bounds:{x:0,y:0,width:1}},{bounds:{x:0,y:0,width:1,height:1,extra:1}},{bounds:{x:0,y:0,width:1,height:"1"}}
 ].map(expected_window=>({...details,expected_window})),{...details,observed_window:{package:"private"}}])
  assert.throws(()=>reject({...recordedOffFence.error,details:d}),/unresolved/);
 const v={...recordedOffFence,error:{...recordedOffFence.error,details}};
 for(const patch of [{request_id:"foreign"},{operation_id:"invalid"},{schema:1},{extra:true},{ok:true},{elapsed_ms:null},{elapsed_ms:-1},...[
  {extra:true},{outcome:"unknown"},{outcome:"started"},{retryable:true},{message:blocked.message},{code:"TEST_INPUT_BLOCKED"}
 ].map(error=>({error:{...v.error,...error}}))]) assert.throws(()=>response({exitCode:4,stdout:JSON.stringify({...v,...patch})},q),/unresolved/);
 for(const patch of [{timedOut:true},{overflow:true},{signal:"SIGTERM"},{exitCode:null},{exitCode:1}]) assert.throws(()=>response({exitCode:4,stdout:JSON.stringify(v),...patch},q),/unresolved/);
 for(const op of ["session.ui","multimedia.launch","input.key.press"]) assert.throws(()=>response({exitCode:4,stdout:JSON.stringify(v)},{...q,op}),/unresolved/);
 assert.throws(()=>response({exitCode:4,stdout:"{invalid"},q),/unresolved/);
 // A syntactically valid but unbounded diagnostic remains unresolved.
 assert.throws(()=>reject({...v.error,details:{...details,reason:"x".repeat(512)}}),/unresolved/);
});

test("window coverage rejection exposes only finite diagnostic enums without new authority",()=>{
 const q=invocation(config,"session.ui","/request").request;
 const base="TREE_REJECTED: guest could not verify current UI tree";
 const receipt=(suffix:string)=>({schema:2,request_id:q.request_id,operation_id:"c".repeat(32),ok:false,elapsed_ms:1,error:{code:"TREE_REJECTED",message:base+suffix,outcome:"not_started",retryable:false}});
 const result=(v:any)=>({exitCode:4,stdout:JSON.stringify(v)});
 for(const reason of ["application_window_missing","application_window_ambiguous","window_coverage_unavailable","window_focus_unavailable","window_focus_ambiguous"]){
  const suffix="; reason="+reason;
  assert.throws(()=>response(result(receipt(suffix)),q),e=>{assert.ok(e instanceof NativeTreeUnavailable);assert.equal(e.outcome,"not_started");assert.equal(e.retryable,false);assert.ok(e.message.includes(suffix));return true;});
 }
 for(const status of ["absent","stale","window_absent","owner_mismatch","window_mismatch","matched","unavailable"]){
  const suffix="; reason=application_window_missing; error_type=IllegalStateException; active_root="+status;
  assert.throws(()=>response(result(receipt(suffix)),q),e=>{assert.ok(e instanceof NativeTreeUnavailable);assert.ok(e.message.includes(suffix));return true;});
 }
 for(const suffix of ["; reason=application_window_missing; active_root=private_app","; reason=application_window_missing; active_root=matched; active_root=absent","; active_root=matched","; reason=deadline; active_root=matched","; reason=application_window_missing; active_root=matched; error_type=IOException","; reason=application_window_missing; active_root=matched\nsecret","; reason=window_focus_unknown","; reason=application_window_missing; active_root=42"])
  assert.throws(()=>response(result(receipt(suffix)),q),/unresolved/);
 const v=receipt("; reason=application_window_missing; active_root=matched");
 for(const patch of [{request_id:"foreign"},{operation_id:"invalid"},{schema:1},{elapsed_ms:-1},{extra:true},{error:{...v.error,retryable:true}},{error:{...v.error,outcome:"unknown"}},{error:{...v.error,private:"secret"}}])
  assert.throws(()=>response(result({...v,...patch}),q),/unresolved/);
 for(const patch of [{timedOut:true},{overflow:true},{signal:"SIGTERM"},{exitCode:null}]) assert.throws(()=>response({...result(v),...patch},q),/unresolved/);
 for(const op of ["input.tap","input.gesture","multimedia.launch","multimedia.reboot"]) assert.throws(()=>response(result(v),{...q,op}),/unresolved/);
});

test("current HOME proof fences exact OEM owner, display, process and boot without claiming UI tree",()=>{
 const boot="aaaaaaaa-1111-2222-3333-444444444444", owned={sessionId:session.id,pid:123,serial:"emulator-5554"};
 const valid=()=>{const v:any=state();v.deliveries.android.home.component="com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity";v.deliveries.android.home.display_id=0;v.deliveries.android.external_reboot={boot_id:boot};v.deliveries.android.stage_errors={};return v;};
 assert.equal(primaryHome(valid(),session,owned,boot).kind,"native-primary-home");
 for(const change of [
  (v:any)=>v.deliveries.android.home.component="com.other/.Main",
  (v:any)=>v.deliveries.android.home.display_id=1,
  (v:any)=>v.android_pid=456,
  (v:any)=>v.session_id="c".repeat(32),
  (v:any)=>v.deliveries.android.serial="emulator-5556",
  (v:any)=>v.deliveries.android.external_reboot.boot_id="bbbbbbbb-1111-2222-3333-444444444444",
  (v:any)=>v.deliveries.android.stage_errors={home:"failed"},
  (v:any)=>v.deliveries.android.last_error="failed",
  (v:any)=>v.stage_errors={boot:"failed"},
  (v:any)=>v.deliveries.android.home.status="unknown"
 ]) {const v=valid();change(v);assert.throws(()=>primaryHome(v,session,owned,boot));}
 assert.throws(()=>primaryHome(valid(),session,owned,"bbbbbbbb-1111-2222-3333-444444444444"));
});

test("HOME capture must match the official fenced boot",()=>{
 const boot="aaaaaaaa-1111-2222-3333-444444444444";
 assert.doesNotThrow(()=>primaryHomeCaptureBoot(manifest(),boot));
 assert.throws(()=>primaryHomeCaptureBoot(manifest(),"bbbbbbbb-1111-2222-3333-444444444444"),/HOME capture boot differs/);
 assert.throws(()=>primaryHomeCaptureBoot({},boot));
});

test("HOME refresh retries missing displays but rechecks boot and lifecycle on every attempt",async()=>{
 const boot="aaaaaaaa-1111-2222-3333-444444444444",owned={sessionId:session.id,pid:123,serial:"emulator-5554"};
 const sample=()=>{const v:any=state();Object.assign(v.deliveries.android.home,{component:"com.qinggan.app.launcher/com.qinggan.app.launcher.activity.MainActivity",display_id:0});v.deliveries.android.external_reboot={boot_id:boot};return v;};
 let calls=0,now=0;
 const clock=()=>now,pause=async(ms:number)=>{now+=ms;};
 const proof=await waitPrimaryHome(async()=>{calls++;const v=sample();if(calls===1)delete v.deliveries.android.displays;return primaryHome(v,session,owned,boot);},1000,clock,pause);
 assert.equal((proof as any).bootId,boot);assert.equal(calls,2);
 calls=0;now=0;
 await assert.rejects(waitPrimaryHome(async()=>{calls++;const v=sample();delete v.deliveries.android.displays;if(calls===2)v.deliveries.android.external_reboot.boot_id="bbbbbbbb-1111-2222-3333-444444444444";return primaryHome(v,session,owned,boot);},1000,clock,pause),/HOME proof differs/);
 assert.equal(calls,2);
 calls=0;now=0;
 await assert.rejects(waitPrimaryHome(async()=>{if(++calls===2)throw Error("installer exited");throw new NativeHomeDeliveryPending();},1000,clock,pause),/installer exited/);
 assert.equal(calls,2);
 now=0;calls=0;
 await assert.rejects(waitPrimaryHome(async()=>{calls++;throw new NativeHomeDeliveryPending();},500,clock,pause),/before deadline/);
 assert.equal(now,500);assert.equal(calls,2);
 const invalid=sample();delete invalid.deliveries.android.displays;invalid.deliveries.android.home.component="foreign/.Main";
 assert.throws(()=>primaryHome(invalid,session,owned,boot),error=>!(error instanceof NativeHomeDeliveryPending));
});

test("missing-window reacquisition requires exact definitive rejection authority",()=>{
 const window={id:24,type:3,layer:0,bounds:{x:0,y:0,width:1224,height:720}};
 const rejection=new NativeInputRejected("request","operation","missing","TEST_INPUT_STALE",{reason:"window_missing",expected_window:window});
 assert.equal(missingInputWindow(rejection,window),true);
 assert.equal(missingInputWindow(rejection,{bounds:{height:720,width:1224,y:0,x:0},layer:0,type:3,id:24}),true);
 for(const error of [new Error("delivered"),{...rejection,outcome:"unknown"},{...rejection,outcome:"not_started"},new NativeInputRejected("r","o","stale","TEST_INPUT_STALE",{reason:"source_changed",expected_window:window}),new NativeInputRejected("r","o","blocked","TEST_INPUT_BLOCKED",{reason:"window_missing",expected_window:window})]) assert.equal(missingInputWindow(error,window),false);
 assert.equal(missingInputWindow(rejection,{...window,id:25}),false);
});

const emptySystem = () => ({status:"unavailable",value:null,boot_id:"",source_instance:"",revision:"",coverage:{continuous:false,gap:true},host_interval:{clock:"host.steady",start_ms:"10",end_ms:"10"}});
test("only authoritative empty system source is a typed unavailable read", () => {
 const q=invocation(config,"multimedia.system","/request").request;
 const check=(value:any,patch:any={})=>response({exitCode:0,stdout:JSON.stringify({...envelope(q,value),...patch})},q);
 assert.throws(()=>check(emptySystem()), NativeSystemUnavailable);
 for(const patch of [{boot_id:"foreign"},{source_instance:"foreign"},{revision:"1"},{value:{}},{coverage:{continuous:false,gap:false}},{coverage:{continuous:false,gap:true,extra:true}},{host_interval:{clock:"host.steady",start_ms:"11",end_ms:"10"}},{host_interval:{clock:"other",start_ms:"10",end_ms:"10"}},{status:"failed"},{extra:true}]) {
  assert.throws(()=>check({...emptySystem(),...patch}),error=>!(error instanceof NativeSystemUnavailable));
 }
 for(const patch of [{request_id:"foreign"},{operation_id:"invalid"},{extra:true},{ok:false},{elapsed_ms:-1}]) assert.throws(()=>check(emptySystem(),patch),error=>!(error instanceof NativeSystemUnavailable));
 const other=invocation(config,"session.observe","/request").request;
 assert.throws(()=>response({exitCode:0,stdout:JSON.stringify(envelope(other,emptySystem()))},other),error=>!(error instanceof NativeSystemUnavailable));
});
test("system first empty response then ready repeats reads with one bounded deadline", async () => {
 let now=0; const reads:number[]=[], receipts:string[]=[];
 const q=invocation(config,"multimedia.system","/request").request;
 const final={status:"ready",boot_id:"owned",value:{power:{status:"ready",wakefulness:"Asleep",display_state:"OFF"}}};
 const observed=await readSystemSample(async remaining=>{reads.push(remaining);const result=reads.length===1?emptySystem():final;receipts.push(JSON.stringify(result));return response({exitCode:0,stdout:JSON.stringify(envelope(q,result))},q);},1000,()=>now,async ms=>{now+=ms;});
 assert.deepEqual(observed,final);assert.deepEqual(reads,[1000,900]);assert.equal(receipts.length,2);
});
test("system unavailable deadline and generic failures do not replay operations", async () => {
 let now=0,reads=0;const unavailable=new NativeSystemUnavailable("request","operation");
 await assert.rejects(readSystemSample(async()=>{reads++;throw unavailable;},250,()=>now,async ms=>{now+=ms;}),error=>error===unavailable);
 assert.equal(reads,3);assert.equal(now,250);
 for(const error of [new Error("transport uncertain"),new Error("foreign boot"),new Error("malformed")]) {
  reads=0;await assert.rejects(readSystemSample(async()=>{reads++;throw error;},250,()=>now,async ms=>{now+=ms;}),actual=>actual===error);assert.equal(reads,1);
 }
});

test("NativeSession system preserves both real dispatch receipts on empty then ready", async () => {
 const base=mkdtempSync(resolve("adapter-system-test-"));try {
  const executable=join(base,".local/native-cpp/bin/voyemu");mkdirSync(join(base,".local/native-cpp/bin"),{recursive:true});writeFileSync(executable,"fixture executable");
  const requests:any[]=[], receipts:any[]=[];
  const client=new NativeSession({...config,root:base,executable},join(base,"requests"),async dispatch=>{
   assert.equal(nativeDispatch(dispatch),true);
   const q=JSON.parse(readFileSync(dispatch.args[4],"utf8"));requests.push(q);
   const receipt=envelope(q,requests.length===1?emptySystem():{status:"ready",boot_id:"owned",value:{power:{status:"ready"}}});receipts.push(receipt);
   return {exitCode:0,stdout:JSON.stringify(receipt)};
  },()=>({width:1920,height:720}));
  assert.equal((await client.system({timeout_ms:1000})).status,"ready");
  assert.deepEqual(requests.map(q=>q.op),["multimedia.system","multimedia.system"]);
  assert.notEqual(requests[0].request_id,requests[1].request_id);
  assert.ok(requests[1].timeout_ms<requests[0].timeout_ms);
  assert.equal(receipts[0].result.status,"unavailable");assert.equal(receipts[1].result.status,"ready");
 }finally{rmSync(base,{recursive:true,force:true});}
});

test("UI read transport permits receipt completion after server timeout without authorizing mutations or copies", async () => {
 const base=mkdtempSync(resolve("adapter-transport-test-"));try {
  const executable=join(base,".local/native-cpp/bin/voyemu");mkdirSync(join(base,".local/native-cpp/bin"),{recursive:true});writeFileSync(executable,"fixture");
  let retained:any, calls=0;
  const client=new NativeSession({...config,root:base,executable},join(base,"requests"),async dispatch=>{
   calls++; const q=JSON.parse(readFileSync(dispatch.args[4],"utf8"));
   assert.equal(nativeDispatch(dispatch),true);
   assert.equal(nativeReadTransportTimeout(dispatch,3796),q.op==="session.ui"?8796:3796);
   assert.equal(nativeReadTransportTimeout({...dispatch},3796),3796);
   assert.equal(nativeReadTransportTimeout(dispatch,20000),dispatch.timeoutMs);
   assert.throws(()=>nativeReadTransportTimeout(dispatch,0),/deadline/);
   assert.throws(()=>nativeReadTransportTimeout(dispatch,NaN),/deadline/);
   if(q.op==="session.ui") { retained=dispatch; assert.equal(q.timeout_ms,3796); }
   const value = q.op === "session.ui" ? {format:"voyemu.session.ui.v1",profile:q.profile,session_id:q.session.id,
     status:"ready",complete:true,atomic_ui_state:false,cluster:null,
     multimedia:[{screen:"primary",status:"ok",complete:true,source:"android.ui",nodes:[],windows:[]},null]} : {accepted:true};
   return {exitCode:0,stdout:JSON.stringify(envelope(q,value))};
  },()=>({width:1,height:1}));
  await client.primaryMultimediaUi(3796); assert.equal(nativeReadTransportTimeout(retained,3796),3796);
  await client.call("input.key.press",{key:"HOME"}); assert.equal(calls,2);
 }finally{rmSync(base,{recursive:true,force:true});}
});


test("dispatch accepts only pinned unified or native CLI layouts and preserves authority guards", async () => {
 for (const layout of ["voyemu", ".local/native-cpp/bin/voyemu", "bin/voyemu"]) {
  const base=mkdtempSync(resolve("adapter-test-"));try {
   const executable=join(base,layout);mkdirSync(resolve(executable,".."),{recursive:true});writeFileSync(executable,"fixture");
   const client=new NativeSession({...config,root:base,executable},join(base,"requests"),async dispatch=>{
    assert.equal(nativeDispatch(dispatch),layout!=="bin/voyemu");
    assert.equal(nativeDispatch({...dispatch}),false);
    const original=[...dispatch.args];dispatch.args.push("--foreign");assert.equal(nativeDispatch(dispatch),false);dispatch.args=original;
    const path=dispatch.args[4], text=readFileSync(path,"utf8"),q=JSON.parse(text);
    writeFileSync(path,text+" ");assert.equal(nativeDispatch(dispatch),false);writeFileSync(path,text);
    const alternate=join(base,"alternate");writeFileSync(alternate,"fixture");unlinkSync(executable);symlinkSync(alternate,executable);
    assert.equal(nativeDispatch(dispatch),false);
    unlinkSync(executable);writeFileSync(executable,"changed");assert.equal(nativeDispatch(dispatch),false);
    return {exitCode:0,stdout:JSON.stringify(envelope(q,state()))};
   },()=>({width:1920,height:720}));
   await client.status();
  } finally {rmSync(base,{recursive:true,force:true});}
 }
});


test("selected portable machine preserves canonical profile and pins status and readiness to its seed", async () => {
 const base=mkdtempSync(resolve("adapter-test-"));try {
  const machine="free-09-chinese-20260106-6.0.3-voboost",seed=join(base,"machines",machine),manifestPath=join(seed,"runtime.json");mkdirSync(seed,{recursive:true});
  const manifest=JSON.stringify({profile:config.profile});writeFileSync(manifestPath,manifest);
  const machineManifestSha256=createHash("sha256").update(manifest).digest("hex");
  const selected={...config,root:base,machine,machineManifestSha256};
  const observed={...state(),machine,machine_seed:seed,machine_manifest_sha256:machineManifestSha256};
  const identity=status(observed,session,selected);
  assert.equal(identity.machine,machine);assert.equal(identity.machineSeed,seed);assert.equal(identity.machineManifestSha256,machineManifestSha256);
  assert.equal(ready({predicate:"android.ready",ready:true,last_observation:observed},session,selected).machine,machine);
  const request=invocation(selected,"session.start",join(base,"start.json")).request;
  assert.equal(request.profile,config.profile);assert.equal(request.machine,machine);
  for(const patch of [{machine:undefined},{machine:"another"},{machine_seed:join(base,"machines",config.profile)},{machine_manifest_sha256:"0".repeat(64)}]) assert.throws(()=>status({...observed,...patch},session,selected),/selected machine/);
  for(const patch of [{machine:"../escape"},{machine:"/absolute"},{machine:"."},{machineManifestSha256:undefined},{machine:undefined}]) assert.throws(()=>invocation({...selected,...patch},"session.status",join(base,"request.json")),/machine configuration/);
  assert.throws(()=>invocation(selected,"session.start",join(base,"request.json"),{machine:"another"}),/reserved/);
  writeFileSync(manifestPath,JSON.stringify({profile:"wrong"}));assert.throws(()=>status(observed,session,selected),/selected machine/);
  writeFileSync(manifestPath,manifest);const alternate=join(base,"alternate.json");writeFileSync(alternate,manifest);unlinkSync(manifestPath);symlinkSync(alternate,manifestPath);
  assert.throws(()=>status(observed,session,selected),/ordinary file/);
 } finally {rmSync(base,{recursive:true,force:true});}
});


test("gate config retains selected machine from private start receipt and rejects mismatched seed", async () => {
 const {nativeSessionFromConfig}=await import("../../release/emulator-gate.ts");
 const base=mkdtempSync(resolve("adapter-test-"));try {
  const machine="free-09-chinese-20260106-6.0.3-voboost",seed=join(base,"machines",machine),manifest=join(seed,"runtime.json"),executable=join(base,"voyemu"),receiptFile=join(base,"receipt.json");
  mkdirSync(seed,{recursive:true});writeFileSync(executable,"fixture");
  const bytes=JSON.stringify({profile:config.profile});writeFileSync(manifest,bytes);
  const sha=createHash("sha256").update(bytes).digest("hex"), result={profile:config.profile,session,machine,machine_seed:seed,machine_manifest_sha256:sha};
  const c:any={nativeSession:{executable,receiptFile},voyemuRoot:base,voyemuProfile:config.profile};
  const receipt=(value:any)=>writeFileSync(receiptFile,JSON.stringify({schema:2,ok:true,result:value}),{mode:0o600});
  receipt(result);const selected=nativeSessionFromConfig(c);assert.equal(selected.machine,machine);assert.equal(selected.machineManifestSha256,sha);assert.equal(selected.profile,config.profile);
  for(const patch of [{machine:"../escape"},{machine:undefined},{machine_seed:join(base,"machines",config.profile)},{machine_manifest_sha256:undefined},{machine_manifest_sha256:"0".repeat(64)}]) {receipt({...result,...patch});assert.throws(()=>nativeSessionFromConfig(c),/selected machine/);}
  receipt(result);writeFileSync(manifest,"changed");assert.throws(()=>nativeSessionFromConfig(c),/selected machine manifest/);
  writeFileSync(manifest,bytes);const alternate=join(base,"alternate.json");writeFileSync(alternate,bytes);unlinkSync(manifest);symlinkSync(alternate,manifest);assert.throws(()=>nativeSessionFromConfig(c),/unsafe file/);
  receipt({profile:config.profile,session});assert.equal(nativeSessionFromConfig(c).machine,undefined);
 } finally {rmSync(base,{recursive:true,force:true});}
});


test("current application-window omission stays typed unavailable and never authorizes retry",()=>{
 const q=invocation(config,"session.ui","/report/request.json",{screen:"primary",timeout_ms:5000}).request;
 const message="TREE_REJECTED: guest could not verify current UI tree; reason=application_window_unverified; error_type=IllegalStateException";
 const value={schema:2,request_id:q.request_id,operation_id:"c".repeat(32),ok:false,elapsed_ms:333.644,error:{code:"TREE_REJECTED",message,outcome:"not_started",retryable:false}};
 assert.throws(()=>response({exitCode:4,stdout:JSON.stringify(value)},q),(error:any)=>error instanceof NativeTreeUnavailable && error.message.includes(message) && error.retryable===false && error.outcome==="not_started" && error.requestId===q.request_id && error.operationId===value.operation_id);
 for(const patch of [{retryable:true},{message:message.replace("application_window_unverified","application_window_invented")}])
  assert.throws(()=>response({exitCode:4,stdout:JSON.stringify({...value,error:{...value.error,...patch}})},q),/unresolved/);
});


test("completed read timeout is typed only for correlated synchronous session.ui; uncertain mutations stay fatal", () => {
 const q=invocation(config,"session.ui","/report/request.json",{screen:"primary",timeout_ms:5000}).request;
 const wire={schema:2,request_id:q.request_id,operation_id:"c193ee42e212a4991f595636c4eb74ef",ok:false,elapsed_ms:5000.288,error:{code:"TREE_TIMEOUT",message:"TREE_TIMEOUT: observer deadline elapsed",outcome:"unknown",retryable:true}};
 const result={exitCode:4,stdout:JSON.stringify(wire)};
 assert.throws(()=>response(result,q),(error:any)=>error instanceof NativeTreeReadTimeout && error.requestId===q.request_id && error.operationId===wire.operation_id);
 for(const op of ["input.tap","input.gesture","input.key.press","multimedia.reboot","multimedia.launch","operation.get"]) assert.throws(()=>response(result,{...q,op}),error=>!(error instanceof NativeTreeReadTimeout));
 for(const patch of [{request_id:"foreign"},{operation_id:"invalid"},{schema:1},{ok:true},{extra:true},{elapsed_ms:-1},{error:{...wire.error,retryable:false}},{error:{...wire.error,outcome:"completed"}},{error:{...wire.error,code:"TEST_OPERATION_RECORD"}},{error:{...wire.error,message:wire.error.message+"; secret"}}]) assert.throws(()=>response({...result,stdout:JSON.stringify({...wire,...patch})},q),error=>!(error instanceof NativeTreeReadTimeout));
 for(const patch of [{exitCode:0},{timedOut:true},{overflow:true},{signal:"SIGTERM"}]) assert.throws(()=>response({...result,...patch},q),error=>!(error instanceof NativeTreeReadTimeout));
});
