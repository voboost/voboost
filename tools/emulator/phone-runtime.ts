import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {readBoundedQnxResource} from '../release/qnx-resource-artifacts.ts';
import {nativePointerRequest} from './installer-ui.ts';
import {status as sessionStatus} from './native-session.ts';
/** Actual Voyemu schema-2 phone stimulus and passive UI checks. No VM lifecycle.
 * API source: voyemu/src/app/runtime/phone_control.hpp and Ivt8BTAdapter.java.
 * Cluster contact text is checked by human review of captures. */
export const phoneContacts = [{name:'Иван Иванов',number:'+70000000001'}, {name:'Анна Петрова',number:'+70000000002'}];
type Io = {api:(operation:string,args?:any)=>Promise<any>; observe:()=>Promise<any>; pause:()=>Promise<void>};
type Expected = {session:string;serial:string;boot:string;generation:string;captureDirectory:string};
class NotRun extends Error {}
const pkg='com.qinggan.bluetoothphone';
export function retainPhoneCapture(value:any,source:string,target:string,expected:{session:string;boot:string}){
 if(value?.artifact_dir!==source||value.manifest!==path.join(source,'manifest.json')||value.session_id!==expected.session||value.android_boot_id!==expected.boot||value.accepted!==true||!Array.isArray(value.frames)||value.frames.length!==3)throw new NotRun('capture identity differs');
 const files=['manifest.json',...value.frames.map((f:any)=>f.file)];
 if(JSON.stringify([...files].sort())!==JSON.stringify(['cluster.png','manifest.json','primary.png','secondary.png']))throw new NotRun('capture file inventory differs');
 fs.mkdirSync(target,{mode:0o700});let remaining=40*1024*1024;
 for(const name of files){const bytes=readBoundedQnxResource(path.join(source,name),remaining);remaining-=bytes.length;fs.writeFileSync(path.join(target,name),bytes,{flag:'wx',mode:0o600});if(!readBoundedQnxResource(path.join(target,name),bytes.length).equals(bytes))throw new NotRun('capture retained bytes differ');}
 const manifest=JSON.parse(readBoundedQnxResource(path.join(target,'manifest.json'),1024*1024).toString());
 for(const key of ['schema','session_id','android_boot_id','frames','accepted'])if(JSON.stringify(manifest[key])!==JSON.stringify(value[key]))throw new NotRun('capture retained manifest differs');
 for(const name of files)fs.unlinkSync(path.join(source,name));fs.rmdirSync(source);return {...value,retained_directory:target};
}
export async function runPhone(io:Io, expected:Expected) {
 const steps:any[]=[];let touched=false;let lastUi:string|undefined;let provider:number|undefined;let sequence=-1;let androidPid=0;let publisher:string|undefined;let completed=false;
 const result:any={status:'NOT_RUN',steps};
 const phone=async(current=true)=>{const p=await (current?api:io.api)('multimedia.phone.state');if(p.schema!==1||p.session_id!==expected.session||p.boot_id!==expected.boot||!Number.isSafeInteger(p.provider_pid)||p.provider_pid<=0||!Number.isSafeInteger(p.sequence)||p.sequence<sequence||p.data?.ok!==true)throw new NotRun('phone observation ownership/boot/sequence unavailable');if(provider!==undefined&&provider!==p.provider_pid)throw new NotRun('phone provider changed');provider=p.provider_pid;sequence=p.sequence;return p.data;};
 const observe=async()=>{const o=await io.observe(),f=o?.data?.files,r=f?.runtime,p=r?.data?.publisher;
  if(o?.schema!==2||o.source!=='voboost'||o.boot_id!==expected.boot||o.status!=='ready'||o.data?.root_proof!=='fixed-path-owner-mode-boot-fenced'||f?.config?.status!=='present'||typeof f.config.revision!=='string'||f.config.data?.agents?.phone?.enabled!==true||r?.status!=='present'||r.current!==true||r.provenance?.current!==true||r.provenance?.proof!=='same-boot-live-publisher'||!Number.isFinite(r.provenance.age_ms)||r.provenance.age_ms<0||r.provenance.age_ms>5000||p?.bootId!==expected.boot||![p.daemonPid,p.daemonStarttime].every(v=>typeof v==='string'&&/^[1-9][0-9]*$/.test(v))||r.data.generationId!==expected.generation||r.data.injections?.filter((v:any)=>v.id==='phone'&&v.process===pkg&&v.state==='active').length!==1)throw new NotRun('current phone authority unavailable');
  const identity=JSON.stringify([p.bootId,p.daemonPid,p.daemonStarttime]);if(publisher!==undefined&&publisher!==identity)throw new NotRun('current phone authority publisher changed');publisher=identity;
 };
 // Fence the official current agent around every scenario input and readback.
 const api=async(op:string,args:any={})=>{await observe();if(op==='multimedia.phone.configure')touched=true;const value=await io.api(op,args);await observe();return value;};
 const ui=async()=>{const u=await api('session.ui',{screen:'primary',timeout_ms:10000});if(u?.boot_id!==expected.boot||u.complete!==true||!u.observation_id||u.observation_id===lastUi||!Array.isArray(u.screens)||u.screens.length!==1||!Array.isArray(u.screens[0]?.nodes))throw new NotRun('UI stale/partial');lastUi=u.observation_id;const nodes=u.screens.flatMap((s:any)=>s.nodes.map((n:any)=>({...n,viewport:s,tree:u})));return nodes.filter((n:any)=>n.visible&&n.package===pkg);};
 const wait=async(test:()=>Promise<boolean>,why:string)=>{for(let i=0;i<24;i++){if(await test())return;await io.pause();}throw new NotRun(why);};
 const capture=async(phase:string)=>{const value=await api('session.screenshot',{screens:['primary','secondary','cluster'],artifact_dir:expected.captureDirectory+'/'+phase});steps.push({step:phase+'-capture',status:'MANUAL_REVIEW',evidence:value});};
 const contacts=async()=>{const nodes=await ui();return phoneContacts.every(c=>nodes.filter((n:any)=>n.resource_id===pkg+':id/tv_name'&&n.text===c.name).length===1);};
 try {
  const live=sessionStatus(await io.api('session.status'),{id:expected.session,ownership_id:''});if(live.serial!==expected.serial)throw new NotRun('observer serial differs from owned session');androidPid=live.pid;
  await observe();
  await api('multimedia.phone.configure',{payload:{peer_name:'Voboost test phone',contacts:phoneContacts}});
  await api('multimedia.phone.connect',{payload:{}});
  await wait(async()=>{const p=await phone();return p.connected===true&&p.pbap_connected===true&&p.sync_state===97;},'HFP/PBAP connection or import pending');
  await api('multimedia.launch',{screen:'primary',package:pkg,boot_id:expected.boot});
  // Exact original resource target from retained RUN20; no coordinate guessing.
  let nodes=await ui();const tabs=nodes.filter((n:any)=>n.resource_id===pkg+':id/ll_contact_title'&&n.clickable===true);
  const tap=async(n:any)=>{const b=n?.clipped_bounds,s=n?.viewport,w=s?.windows?.find((v:any)=>v.id===n.window_id),rect=(v:any)=>v&&[v.x,v.y,v.width,v.height].every(Number.isSafeInteger)&&v.width>0&&v.height>0?`[${v.x},${v.y}][${v.x+v.width},${v.y+v.height}]`:null;if(n?.enabled!==true||!b||!w||!Number.isSafeInteger(w.layer)||!rect(b)||!rect(n.bounds)||!rect(w.bounds)||typeof n.class!=='string'||typeof n.text!=='string'||typeof w.title!=='string'||b.x<0||b.y<0||b.x+b.width>s.width||b.y+b.height>s.height)throw new NotRun('OEM target not actionable');let call;
   try{call=nativePointerRequest({action:'tap',x:Math.round(b.x+b.width/2),y:Math.round(b.y+b.height/2),expectedWindow:{id:w.id,displayId:0,windowType:w.type,packageName:w.package,title:w.title,bounds:rect(w.bounds)},expectedTarget:{windowId:n.window_id,packageName:n.package,resource:n.resource_id,className:n.class,text:n.text,bounds:rect(n.bounds),windowType:w.type,rootPackage:w.package,windowTitle:w.title,windowBounds:rect(w.bounds)}},n.tree,{sessionId:expected.session,pid:androidPid,boot:expected.boot});}catch(error){throw new NotRun('observed pointer fence unavailable: '+String(error));}
   await api(call.op,call.fields);
  };
  if(tabs.length!==1)throw new NotRun('unique original Contacts tab unavailable');await tap(tabs[0]);
  await wait(contacts,'actual imported Russian contacts not visible');await capture('contacts');steps.push({step:'PBAP-Russian-names',status:'PASS',basis:'fresh original Phone UI exact names'});
  // Select original contact row through its observed parent, not a fixed pixel.
  nodes=await ui();const names=nodes.filter((n:any)=>n.resource_id===pkg+':id/tv_name'&&n.text===phoneContacts[0].name);
  if(names.length!==1)throw new NotRun('outgoing contact ambiguous');let ancestor=names[0],row:any;
  for(let depth=0;depth<8;depth++){const parents=nodes.filter((n:any)=>n.id===ancestor.parent);if(parents.length!==1)break;ancestor=parents[0];if(ancestor.resource_id===pkg+':id/info_rl'&&ancestor.clickable===true){row=ancestor;break;}}
  if(!row)throw new NotRun('original clickable contact row unavailable');await tap(row);
  nodes=await ui();const dial=nodes.filter((n:any)=>n.resource_id===pkg+':id/dial_out_rl'&&n.clickable===true);if(dial.length!==1)throw new NotRun('original dial target unavailable');await tap(dial[0]);
  await wait(async()=>{const p=await phone();return p.call?.outgoing===true&&p.call.number===phoneContacts[0].number&&Array.isArray(p.outgoing)&&p.outgoing.at(-1)===phoneContacts[0].number;},'OEM outgoing +7 readback unavailable');await capture('outgoing');steps.push({step:'outgoing-plus7',status:'PASS',basis:'OEM UI stimulus + actual peer outgoing readback'});
  await api('multimedia.phone.hangup',{payload:{}});
  await api('multimedia.phone.incoming',{payload:{number:phoneContacts[1].number}});
  await wait(async()=>{const p=await phone();return p.call?.number===phoneContacts[1].number&&p.call.outgoing===false;},'incoming +7 peer readback unavailable');
  await capture('incoming');await api('multimedia.phone.answer',{payload:{}});await capture('answered');
  steps.push({step:'call-name-and-QNX-text',status:'NOT_RUN',reason:'peer state is not actual rendered Phone/cluster text; review captures'});
  await api('multimedia.phone.hangup',{payload:{}});await api('multimedia.phone.disconnect',{payload:{}});
  await wait(async()=>{const p=await phone();return p.connected===false&&p.pbap_connected===false;},'disconnect readback pending');
  await api('multimedia.phone.connect',{payload:{}});await wait(async()=>{const p=await phone();return p.connected===true&&p.pbap_connected===true&&p.sync_state===97;},'reconnect readback pending');
  await api('multimedia.launch',{screen:'primary',package:pkg,boot_id:expected.boot});
  nodes=await ui();const reconnectTabs=nodes.filter((n:any)=>n.resource_id===pkg+':id/ll_contact_title'&&n.clickable===true);
  if(reconnectTabs.length!==1)throw new NotRun('original Contacts return route unavailable');await tap(reconnectTabs[0]);
  await wait(contacts,'Russian contacts after reconnect unavailable');await capture('reconnected');await observe();steps.push({step:'reconnect',status:'PASS'});
  completed=true;result.reason='QNX Phone text/native downstream observation unavailable; captures require manual review';
 }catch(error){result.reason=String(error);result.status=error instanceof NotRun?'NOT_RUN':'FAIL';}
 finally{if(touched){
  for(const op of ['multimedia.phone.hangup','multimedia.phone.disconnect'])try{await io.api(op,{payload:{}});}catch(error){result.status='FAIL';steps.push({step:'cleanup-'+op,status:'FAIL',reason:String(error)});}
  try{await wait(async()=>{const p=await phone(false);return p.connected===false&&p.pbap_connected===false;},'cleanup disconnect readback pending');steps.push({step:'cleanup-disconnected',status:'PASS'});}catch(error){result.status='FAIL';steps.push({step:'cleanup-readback',status:'FAIL',reason:String(error)});}
 }}
 // Cleanup is unconditional; it cannot stand in for a final current-agent fence.
 if(completed)try{await observe();}catch(error){if(result.status!=='FAIL')result.status='NOT_RUN';result.reason=String(error);}
 return result;
}
/** Transport follows outing-runtime: an owner supplies the already running session. */
export async function main(configPath:string){
 const c=JSON.parse(fs.readFileSync(configPath,'utf8'));
 if(![c.evidence,c.cli,c.installer,c.voyemuRoot].every((p:any)=>typeof p==='string'&&path.isAbsolute(p))||!c.boot||!c.generation||typeof c.serial!=='string'||!c.serial||!(/^[a-z0-9][a-z0-9._-]{0,63}$/).test(c.profile)||![c.session?.id,c.session?.ownership_id].every(v=>typeof v==='string'&&/^[0-9a-f]{32}$/.test(v)))throw Error('explicit owned session, boot, generation and absolute paths required');
 fs.mkdirSync(c.evidence,{recursive:true,mode:0o700});fs.mkdirSync(path.join(c.evidence,'captures'),{mode:0o700});
 const captureRoot=fs.mkdtempSync(path.join(c.voyemuRoot,'.local/build/tmp/voboost-phone-'));let sequence=0;
 const api=async(op:string,args:any={})=>{
  const tag=String(++sequence).padStart(3,'0')+'-'+op,input=path.join(c.evidence,tag+'-request.json');
  fs.writeFileSync(input,JSON.stringify({...args,schema:2,request_id:'phone-'+Date.now()+'-'+sequence,op,profile:c.profile,session:c.session})+'\n',{flag:'wx'});
  const p=spawnSync(c.cli,['test','--root',c.voyemuRoot,'--request',input],{encoding:'utf8',timeout:130000,maxBuffer:2000000});
  fs.writeFileSync(path.join(c.evidence,tag+'-result.json'),p.stdout??'',{flag:'wx'});fs.writeFileSync(path.join(c.evidence,tag+'-stderr.log'),p.stderr??'',{flag:'wx'});
  if(p.status!==0||p.error)throw new NotRun('API transport unavailable: '+op);const v=JSON.parse(p.stdout);if(v.ok!==true)throw new NotRun('API rejected: '+op);
  if(op==='session.screenshot'){
   const source=args.artifact_dir,phase=path.basename(source),value=v.result;
   if(path.dirname(source)!==captureRoot)throw new NotRun('capture outside own stage');
   return retainPhoneCapture(value,source,path.join(c.evidence,'captures',phase),{session:c.session.id,boot:c.boot});
  }
  return v.result;
 };
 const observe=async()=>{const p=spawnSync(c.installer,['--observe','--device',c.serial],{encoding:'utf8',timeout:15000,maxBuffer:1200000});fs.writeFileSync(path.join(c.evidence,String(++sequence).padStart(3,'0')+'-official-observe.json'),p.stdout??'',{flag:'wx'});if(p.status!==0||p.error)throw new NotRun('official observer unavailable');return JSON.parse(p.stdout);};
 const r=await runPhone({api,observe,pause:()=>new Promise(resolve=>setTimeout(resolve,500))},{session:c.session.id,serial:c.serial,boot:c.boot,generation:c.generation,captureDirectory:captureRoot});
 if(fs.readdirSync(captureRoot).length===0)fs.rmdirSync(captureRoot);
 fs.writeFileSync(path.join(c.evidence,'RESULT.json'),JSON.stringify(r,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(r));process.exitCode=r.status==='PASS'?0:2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await main(process.argv[2]);
