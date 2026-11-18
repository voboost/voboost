import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {nativePointerRequest} from './installer-ui.ts';
import {status as sessionStatus} from './native-session.ts';

type Io = {api:(op:string,args?:any)=>Promise<any>; observe:()=>Promise<any>; pause:()=>Promise<void>};
const id='vehicle-drive-outing';
const nodes=(ui:any)=>ui.screens?.flatMap((s:any)=>s.nodes??[])??[];
const rid=(name:string)=>(n:any)=>n.resource_id?.endsWith('/'+name);
const text=(...values:string[])=>(n:any)=>values.includes(n.text);
class NotRun extends Error {}

/** Uses observed UI geometry, real vehicle revisions and official passive observation only. */
export async function runOuting(io:Io, expected:{boot:string;generation:string;session:string;serial:string}) {
 const steps:any[]=[]; const outcome:any={status:'NOT_RUN',steps}; let lastUi:string|undefined; let speedTouched=false; let androidPid=0; let publisher:string|undefined; let activePolicy=false; let savedOptions:any; let savedEnabled:boolean|undefined; let policyTouched=false;let enabledTouched=false;let originalSpeed:any;
 const checkBoot=(v:any)=>{if(v.boot_id!==expected.boot)throw new NotRun('boot changed or missing');};
 const ui=async()=>{const v=await io.api('session.ui',{screen:'primary',timeout_ms:10000});checkBoot(v);if(v.android_pid!==androidPid||v.complete!==true||!v.observation_id||v.observation_id===lastUi||v.screens?.length!==1)throw new NotRun('UI stale/incomplete');lastUi=v.observation_id;for(const screen of v.screens)for(const n of screen.nodes??[]){n.viewport=screen;n.tree=v;}return v;};
 const pointer=async(n:any,request:any)=>{const b=n?.clipped_bounds,w=n?.viewport?.windows?.find((v:any)=>v.id===n.window_id),rect=(v:any)=>v&&`[${v.x},${v.y}][${v.x+v.width},${v.y+v.height}]`;if(!n?.visible||n.enabled!==true||!b||b.width<=0||b.height<=0||!w)throw new NotRun('target not actionable');let call;try{call=nativePointerRequest({...request,expectedWindow:{id:w.id,displayId:0,windowType:w.type,packageName:w.package,title:w.title,bounds:rect(w.bounds)},expectedTarget:{windowId:n.window_id,packageName:n.package,resource:n.resource_id,className:n.class,text:n.text,bounds:rect(n.bounds),windowType:w.type,rootPackage:w.package,windowTitle:w.title,windowBounds:rect(w.bounds)}},n.tree,{sessionId:expected.session,pid:androidPid,boot:expected.boot});}catch(error){throw new NotRun('observed pointer fence unavailable: '+String(error));}await io.api(call.op,call.fields);};
 const tap=async(n:any)=>{const b=n?.clipped_bounds;if(!b)throw new NotRun('clipped target missing');await pointer(n,{action:'tap',x:Math.round(b.x+b.width/2),y:Math.round(b.y+b.height/2)});};
 const seek=async(pred:(n:any)=>boolean,left=false)=>{for(let i=0;i<10;i++){const view=await ui(),matches=nodes(view).filter((n:any)=>pred(n)&&n.visible&&n.bounds?.height>0&&n.bounds.y>=0&&n.bounds.y+n.bounds.height<=720);if(matches.length===1)return matches[0];if(matches.length>1)throw new NotRun('ambiguous UI target');const scroll=nodes(view).filter((n:any)=>n.scrollable&&n.visible&&n.enabled&&n.clipped_bounds?.height>100&&(left?n.bounds.x<960:n.bounds.x>=960));if(scroll.length!==1)throw new NotRun('unique observed scroll target unavailable');const n=scroll[0],b=n.clipped_bounds;await pointer(n,{action:'gesture',points:[{x:Math.round(b.x+b.width/2),y:Math.round(b.y+b.height*.8),at_ms:0},{x:Math.round(b.x+b.width/2),y:Math.round(b.y+b.height*.2),at_ms:300}]});}throw new NotRun('target not found');};
 const wait=async(test:()=>Promise<boolean>,why:string)=>{for(let i=0;i<24;i++){if(await test())return;await io.pause();}throw new Error(why);};
 const observed=async()=>{const v=await io.observe();checkBoot(v);const f=v.data?.files,r=f?.runtime,p=r?.data?.publisher,identity=JSON.stringify([p?.bootId,p?.daemonPid,p?.daemonStarttime]);if(v.schema!==2||v.source!=='voboost'||v.status!=='ready'||v.data?.root_proof!=='fixed-path-owner-mode-boot-fenced'||f?.config?.status!=='present'||typeof f.config.revision!=='string'||r?.status!=='present'||r.current!==true||r.provenance?.current!==true||r.provenance?.proof!=='same-boot-live-publisher'||!Number.isFinite(r.provenance.age_ms)||r.provenance.age_ms<0||r.provenance.age_ms>5000||p?.bootId!==expected.boot||!p.daemonPid||!p.daemonStarttime||r.data.generationId!==expected.generation||publisher!==undefined&&publisher!==identity)throw new NotRun('official current publisher unavailable');publisher=identity;if(activePolicy){const a=f.config.data.agents?.[id];if(a?.enabled!==true||a.options?.['exit-target']!=='previous'||a.options?.['above-speed']!=='exit'||!['com.qinggan.app.vehiclesetting','com.qinggan.app.vehicle'].every(process=>r.data.injections?.some((x:any)=>x.id===id&&x.process===process&&x.state==='active')))throw new NotRun('current Outing policy authority changed');}return {config:f.config.data,runtime:r.data,revision:f.config.revision};};
 const mode=async(resource:string,raw:number)=>{if(activePolicy)await observed();const u=await ui();const n=nodes(u).filter(rid(resource));const can=await io.api('vehicle.can.get',{states:['DRIVING_MODE_SET','BCM_DRIVEMODE_CHANGE']});checkBoot(can);if(can.schema!==2||can.source!=='android:qg.canbus'||can.status!=='ready'||typeof can.source_instance!=='string'||!can.source_instance||!Number.isSafeInteger(can.pid)||can.pid<=0||can.transport?.registration!=='registered'||can.transport?.binder_alive!==true||can.transport?.client_manager_cache!=='not_observed')throw new NotRun('live registered CAN source unavailable');const value=can.values?.['vehicle_state.BCM_DRIVEMODE_CHANGE'];if(value?.status!=='observed')throw new NotRun('drive-mode CAN feedback unsupported');return n.length===1&&n[0].checked===true&&value.raw===raw;};
 const speed=async(value:number,restoring=false)=>{const state=await io.api('vehicle.get');if(!Number.isInteger(state.revision))throw new NotRun('vehicle revision unsupported');if(!originalSpeed){originalSpeed=Object.fromEntries(['speed','speed10x','esc_vehicleSpeed'].map(k=>[k,state.state?.raw_oem?.[k]]));if(!Object.values(originalSpeed).every(Number.isFinite))throw new NotRun('original speed inputs unavailable');}const values=restoring?originalSpeed:{speed:value,speed10x:value*10,esc_vehicleSpeed:value};speedTouched=true;await io.api('vehicle.set',{expected_revision:state.revision,values});const read=await io.api('vehicle.get');for(const [k,v]of Object.entries(values))if(read.state?.raw_oem?.[k]!==v)throw new NotRun('speed input readback unavailable');};
 try {
  let live;try{live=sessionStatus(await io.api('session.status'),{id:expected.session,ownership_id:''});}catch(error){throw new NotRun('owned session unavailable: '+String(error));}if(live.serial!==expected.serial)throw new NotRun('observer serial differs from owned session');androidPid=live.pid;await observed();
  await io.api('multimedia.launch',{screen:'primary',package:'com.qinggan.app.vehiclesetting',boot_id:expected.boot});
  const before=await observed();savedOptions={'exit-target':before.config.agents?.[id]?.options?.['exit-target']??'comfort','above-speed':before.config.agents?.[id]?.options?.['above-speed']??'exit'};savedEnabled=before.config.agents?.[id]?.enabled===true;
  await io.api('multimedia.launch',{screen:'primary',package:'com.qinggan.app.setting',boot_id:expected.boot});
  await tap(await seek(text('Voboost'),true));
  const label=await seek(text('Outing drive-mode behavior','Поведение режима движения Outing','郊游驾驶模式行为'));
  const screen=await ui(); const currentLabels=nodes(screen).filter((n:any)=>n.text===label.text);if(currentLabels.length!==1)throw new NotRun('Outing label changed or ambiguous');const siblings=nodes(screen).filter((n:any)=>n.parent===currentLabels[0].parent&&n.checkable);
  if(siblings.length!==1)throw new NotRun('Outing checkbox association unsupported');
  if(!siblings[0].checked){enabledTouched=true;await tap(siblings[0]);}
  await wait(async()=>{const o=await observed();const u=await ui();const l=nodes(u).find((n:any)=>n.text===label.text);const c=nodes(u).find((n:any)=>n.parent===l?.parent&&n.checkable);return o.config.agents?.[id]?.enabled===true&&['com.qinggan.app.vehiclesetting','com.qinggan.app.vehicle'].every(process=>o.runtime.injections?.some((r:any)=>r.id===id&&r.process===process&&r.state==='active'))&&c?.checked===true&&c.enabled===true&&(before.config.agents?.[id]?.enabled===true||o.revision!==before.revision);},'enable not persisted/active/settled');
  steps.push({step:'enable',status:'PASS',basis:'fresh persisted config + current active runtime + settled UI'});
  await speed(0);
  await io.api('multimedia.launch',{screen:'primary',package:'com.qinggan.app.vehiclesetting',boot_id:expected.boot});
  await tap(await seek(rid('rb_menu_drive_preference'),true));
  await tap(await seek(rid('fragment_radio_button_drive_mode_eco_5')));
  await wait(()=>mode('fragment_radio_button_drive_mode_eco_5',1),'Eco not confirmed');
  await tap(await seek(rid('fragment_radio_button_drive_mode_outing')));
  await wait(()=>mode('fragment_radio_button_drive_mode_outing',4),'Outing not confirmed');
  await tap(await seek(text('Settings','Настройки','设置')));
  const policyBefore=await observed();
  const choice=async(values:string[])=>{const n=await seek(text(...values));if(n.checkable!==true)throw new NotRun('policy radio unavailable');return n;};
  const select=async(values:string[])=>{await tap(await choice(values));await wait(async()=>{const n=await choice(values);return n.checked===true;},'policy radio selection not observed');};
  const previous=['Previous mode','Предыдущий','上一个模式'],comfort=['Comfort','Комфорт','舒适'],exit=['Exit Outing','Выйти из Outing','退出郊游模式'],keep=['Keep Outing','Оставить Outing','保持郊游模式'];
  const save=async(expectedOptions:any,revision:string)=>{await tap(await seek(text('Save','Сохранить','保存')));await wait(async()=>{const o=await observed(),u=await ui(),opts=o.config.agents?.[id]?.options;return o.revision!==revision&&Object.keys(expectedOptions).every(k=>opts?.[k]===expectedOptions[k])&&!nodes(u).some(text('Save','Сохранить','保存'));},'policy selection/save/close/revision not persisted');};
  policyTouched=true;let desiredRevision=policyBefore.revision;
  if((await choice(previous)).checked||(await choice(exit)).checked){await select(comfort);await select(keep);await save({'exit-target':'comfort','above-speed':'keep'},policyBefore.revision);desiredRevision=(await observed()).revision;await tap(await seek(text('Settings','Настройки','设置')));}
  await select(previous);await select(exit);await save({'exit-target':'previous','above-speed':'exit'},desiredRevision);
  activePolicy=true;await observed();
  await speed(40);
  for(let sample=0;sample<6;sample++){await io.pause();if(!await mode('fragment_radio_button_drive_mode_outing',4))throw new Error('Outing did not hold at threshold40');}
  steps.push({step:'at40',status:'PASS'});
  await speed(41);
  await wait(()=>mode('fragment_radio_button_drive_mode_eco_5',1),'previous Eco not restored above40');
  await observed();
  steps.push({step:'above40-restore-Eco',status:'PASS'});
  return Object.assign(outcome,{status:'PASS'});
 }catch(error){return Object.assign(outcome,{status:error instanceof NotRun?'NOT_RUN':'FAIL',reason:String(error)});}
 finally {activePolicy=false;if(speedTouched){try {await speed(0,true);steps.push({step:'cleanup-speed0',status:'PASS'});} catch(error){steps.push({step:'cleanup-speed0',status:'FAIL',reason:String(error)});outcome.status='FAIL';outcome.reason='speed cleanup failed';} }
  if(policyTouched&&savedOptions){try{const now=await observed(),opts=now.config.agents?.[id]?.options;if(opts?.['exit-target']!==savedOptions['exit-target']||opts?.['above-speed']!==savedOptions['above-speed']){
    await io.api('multimedia.launch',{screen:'primary',package:'com.qinggan.app.vehiclesetting',boot_id:expected.boot});
    await tap(await seek(rid('fragment_radio_button_drive_mode_outing')));await tap(await seek(text('Settings','Настройки','设置')));
    const labels:any={'exit-target':{previous:['Previous mode','Предыдущий','上一个模式'],comfort:['Comfort','Комфорт','舒适']},'above-speed':{exit:['Exit Outing','Выйти из Outing','退出郊游模式'],keep:['Keep Outing','Оставить Outing','保持郊游模式']}};
    for(const key of ['exit-target','above-speed']){const label=labels[key][savedOptions[key]];if(!label)throw new Error('original policy unsupported');await tap(await seek(text(...label)));await wait(async()=>nodes(await ui()).filter(text(...label)).some((n:any)=>n.checkable&&n.checked),'cleanup radio not selected');}
    await tap(await seek(text('Save','Сохранить','保存')));await wait(async()=>{const o=await observed();return ['exit-target','above-speed'].every(k=>o.config.agents?.[id]?.options?.[k]===savedOptions[k])&&!nodes(await ui()).some(text('Save','Сохранить','保存'));},'original policy not restored');
    await tap(await seek(rid('fragment_radio_button_drive_mode_eco_5')));
   }steps.push({step:'cleanup-policy',status:'PASS'});
  }catch(error){steps.push({step:'cleanup-policy',status:'FAIL',reason:String(error)});outcome.status='FAIL';}}
  if(savedEnabled===false&&enabledTouched){try{await io.api('multimedia.launch',{screen:'primary',package:'com.qinggan.app.setting',boot_id:expected.boot});await tap(await seek(text('Voboost'),true));const u=await ui(),l=nodes(u).filter(text('Outing drive-mode behavior','Поведение режима движения Outing','郊游驾驶模式行为'));const checks=nodes(u).filter((n:any)=>n.parent===l[0]?.parent&&n.checkable);if(l.length!==1||checks.length!==1)throw new Error('cleanup enable checkbox ambiguous');if(checks[0].checked)await tap(checks[0]);await wait(async()=>{const o=await observed();return o.config.agents?.[id]?.enabled===false;},'original disabled state not restored');steps.push({step:'cleanup-enabled',status:'PASS'});}catch(error){steps.push({step:'cleanup-enabled',status:'FAIL',reason:String(error)});outcome.status='FAIL';}}
 }
}

/** Owner supplies an already-running session. This driver never starts, stops or reboots it. */
export async function main(configPath:string){
 const c=JSON.parse(fs.readFileSync(configPath,'utf8'));
 if(!path.isAbsolute(c.evidence)||!path.isAbsolute(c.cli)||!path.isAbsolute(c.installer)||!c.session||!c.boot||!c.generation)throw Error('absolute paths and owned session/boot/generation required');
 fs.mkdirSync(c.evidence,{recursive:true,mode:0o700});let seq=0;
 const api=async(op:string,args:any={})=>{const tag=String(++seq).padStart(3,'0')+'-'+op;const request={schema:2,request_id:'outing-'+Date.now()+'-'+seq,op,profile:c.profile,session:c.session,...args};const input=path.join(c.evidence,tag+'-request.json');fs.writeFileSync(input,JSON.stringify(request)+'\n',{flag:'wx'});const p=spawnSync(c.cli,['test','--root',c.voyemuRoot,'--request',input],{encoding:'utf8',timeout:130000});fs.writeFileSync(path.join(c.evidence,tag+'-result.json'),p.stdout??'',{flag:'wx'});fs.writeFileSync(path.join(c.evidence,tag+'-stderr.log'),p.stderr??'',{flag:'wx'});if(p.status!==0||p.error)throw new NotRun('API transport unavailable: '+op);const value=JSON.parse(p.stdout);if(!value.ok)throw new NotRun('API rejected: '+op);return value.result;};
 const observe=async()=>{const p=spawnSync(c.installer,['--observe','--device',c.serial],{encoding:'utf8',timeout:15000,maxBuffer:1200000});const name=path.join(c.evidence,String(++seq).padStart(3,'0')+'-official-observe.json');fs.writeFileSync(name,p.stdout??'',{flag:'wx'});if(p.status!==0||p.error)throw new NotRun('official observer unavailable');return JSON.parse(p.stdout);};
 const result=await runOuting({api,observe,pause:()=>new Promise(r=>setTimeout(r,500))},{boot:c.boot,generation:c.generation,session:typeof c.session==='string'?c.session:c.session.id,serial:c.serial});fs.writeFileSync(path.join(c.evidence,'RESULT.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx'});console.log(JSON.stringify(result));process.exitCode=result.status==='PASS'?0:2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await main(process.argv[2]);
