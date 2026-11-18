import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {invocation, response, status as sessionStatus, type Config, type Operation} from './native-session.ts';
import {nativePointerRequest} from './installer-ui.ts';
import {parseInstallerLogs,type LogRecord,type AgentDiagnostic} from './installer-logs.ts';
import {freshDiagnostics,renderLocale} from './functional-observations.ts';

const id='i18n-ru';
const owners=['gauge','light','point'];
const reasons={en:'confirmed English locale and selected dictionary',zh:'confirmed Chinese locale and selected dictionary',ru:'confirmed Russian locale and selected dictionary'};
type Locale=keyof typeof reasons;
type Logs={archive:string;capture:string;records:LogRecord[];diagnostics:AgentDiagnostic[]};
type Endpoint={stage:string;beforeObserve:any;afterObserve:any;pickerUi?:any;baselineLogs?:string;currentLogs?:string;baselineLogCapture?:string;currentLogCapture?:string};
type Io={api:(op:string,args?:any)=>Promise<any>;observe:()=>Promise<any>;pause:()=>Promise<void>;logs?:()=>Promise<Logs>;endpoint?:(value:Endpoint)=>Promise<void>};
export type Expected={session:{id:string;ownership_id:string};serial:string;androidBoot:string;androidGeneration:string;qnxBoot:string;qnxGeneration:string;catalogSha256:string};
type Process={pid:number;birthIdentity:string;endpointEpoch:string;lifecycleGeneration:number;executableSha256:string};
type Proof={raw:any;cluster:any;agent:any;enabled:boolean;configRevision:string;processes:Record<string,Process>};
export type Checkpoint={schema:1;stage:'external-reboot';daemonEpoch:string;expected:Expected;processes:Record<string,Process>;observationRevision:number;desiredRevision:number;desiredSha256:string};
class NotRun extends Error {}
class Rejected extends Error {}
const digest=(v:any)=>typeof v==='string'&&/^[a-f0-9]{64}$/.test(v);
const number=(v:any)=>Number.isSafeInteger(v)&&v>0;
const same=(a:any,b:any)=>JSON.stringify(a)===JSON.stringify(b);
const nodes=(u:any)=>u.screens.flatMap((s:any)=>s.nodes.map((n:any)=>({...n,viewport:s,tree:u})));
const resource=(n:any,name:string)=>n.resource_id?.endsWith('/'+name);

/** Official native snapshot only: desired inputs and aggregate state never replace owner ACKs. */
export function readProof(v:any,e:Expected):Proof {
 if(!e||![e.androidBoot,e.androidGeneration,e.qnxBoot].every(v=>typeof v==='string'&&v.length>0&&v.length<=256)||!digest(e.qnxGeneration)||!digest(e.catalogSha256))throw new NotRun('expected authority missing');
 if(v?.schema!==2||v.source!=='voboost'||v.status!=='ready'||v.boot_id!==e.androidBoot)throw new NotRun('official Android boot/envelope unavailable');
 const files=v.data?.files,r=files?.runtime,c=files?.config;
 if(v.data?.root_proof!=='fixed-path-owner-mode-boot-fenced'||c?.status!=='present'||typeof c.revision!=='string'||typeof c.data?.agents?.[id]?.enabled!=='boolean'||r?.status!=='present'||r.current!==true||r.provenance?.current!==true||r.provenance?.proof!=='same-boot-live-publisher'||!Number.isFinite(r.provenance.age_ms)||r.provenance.age_ms<0||r.provenance.age_ms>5000||r.data?.publisher?.bootId!==e.androidBoot||r.data?.generationId!==e.androidGeneration)throw new NotRun('current official publisher/config unavailable');
 const q=r.data.cluster;
 if(r.data.clusterUnavailable||!q)throw new NotRun('official native snapshot unavailable: '+(r.data.clusterUnavailable??'missing'));
 if(q.schemaVersion!==1||q.domain!=='cluster'||q.bootId!==e.qnxBoot||q.generationId!==e.qnxGeneration||q.catalogSha256!==e.catalogSha256||!digest(q.generationId)||!digest(q.catalogSha256)||!digest(q.desiredSha256)||!digest(q.daemonEpoch)||!number(q.snapshotRevision)||!number(q.observationRevision)||!number(q.desiredRevision)||!Array.isArray(q.agents)||!q.agents.every((a:any)=>a&&typeof a==='object'))throw new NotRun('native boot/generation/revision proof differs or is missing');
 const agents=q.agents.filter((a:any)=>a.id===id);
 if(agents.length!==1||!Array.isArray(agents[0].targets)||typeof agents[0].enabled!=='boolean')throw new NotRun('per-owner native observation API unavailable');
 const agent=agents[0],targets=agent.targets;if(!targets.every((t:any)=>t&&typeof t==='object'))throw new NotRun('native target schema unavailable');
 if(targets.length!==owners.length||owners.some(owner=>targets.filter((t:any)=>t.id===id&&t.targetProcess===owner).length!==1))throw new Rejected('incomplete/duplicate native owner ACK set');
 const processes:Record<string,Process>={};
 for(const target of targets){const p=target.process;
  if(p!==null&&p!==undefined){if(!number(p.pid)||!digest(p.birthIdentity)||!digest(p.executableSha256)||!digest(p.endpointEpoch)||!number(p.lifecycleGeneration))throw new NotRun('native process identity unavailable');processes[target.targetProcess]=p;}
 }
 return {raw:v,cluster:q,agent,enabled:c.data.agents[id].enabled,configRevision:c.revision,processes};
}
export function confirmed(p:Proof,locale:Locale,after?:Proof):boolean {
 if(after&&(p.cluster.observationRevision<after.cluster.observationRevision||p.cluster.snapshotRevision<after.cluster.snapshotRevision||p.cluster.desiredRevision<after.cluster.desiredRevision))throw new Rejected('stale native locale observation');
 if(after&&(p.cluster.observationRevision===after.cluster.observationRevision||p.cluster.snapshotRevision===after.cluster.snapshotRevision))return false;
 return p.enabled&&p.agent.enabled&&p.agent.state==='applied'&&owners.every(owner=>p.agent.targets.some((t:any)=>t.targetProcess===owner&&t.state==='applied'&&t.reason===reasons[locale]&&p.processes[owner]));
}
export function restarted(p:Proof,before:Proof):boolean {
 return !p.enabled&&!p.agent.enabled&&p.cluster.observationRevision>before.cluster.observationRevision&&p.cluster.desiredRevision>before.cluster.desiredRevision&&owners.every(owner=>{
  const a=before.processes[owner],b=p.processes[owner],t=p.agent.targets.find((t:any)=>t.targetProcess===owner);
  return a&&b&&a.pid!==b.pid&&a.birthIdentity!==b.birthIdentity&&a.endpointEpoch!==b.endpointEpoch&&t.state==='disabled';
 });
}
export function resumeProof(v:any,checkpoint:Checkpoint,current:Expected):Proof {
 if(!checkpoint?.expected?.session||!checkpoint.processes||!digest(checkpoint.daemonEpoch)||!digest(checkpoint.desiredSha256)||!number(checkpoint.desiredRevision)||!number(checkpoint.observationRevision)||!owners.every(owner=>{const p=checkpoint.processes[owner];return p&&number(p.pid)&&digest(p.birthIdentity)&&digest(p.endpointEpoch)&&digest(p.executableSha256)&&number(p.lifecycleGeneration);}))throw new NotRun('malformed reboot checkpoint');
 if(checkpoint?.schema!==1||checkpoint.stage!=='external-reboot'||!same(current.session,checkpoint.expected.session)||current.androidBoot===checkpoint.expected.androidBoot||current.qnxBoot===checkpoint.expected.qnxBoot||current.androidGeneration!==checkpoint.expected.androidGeneration||current.qnxGeneration!==checkpoint.expected.qnxGeneration||current.catalogSha256!==checkpoint.expected.catalogSha256)throw new NotRun('resume needs the same owned session/artifacts and independently changed Android/QNX boot');
 const p=readProof(v,current);
 if(p.cluster.daemonEpoch===checkpoint.daemonEpoch||p.cluster.desiredRevision!==checkpoint.desiredRevision||p.cluster.desiredSha256!==checkpoint.desiredSha256||!confirmed(p,'ru')||!owners.every(owner=>{const a=checkpoint.processes[owner],b=p.processes[owner];return a&&b&&a.birthIdentity!==b.birthIdentity&&a.endpointEpoch!==b.endpointEpoch;}))throw new Rejected('reboot restoration lacks fresh all-owner Russian proof');
 return p;
}

/** All mutations are observed UI inputs. No lifecycle or shell API is accepted. */
export async function runI18n(io:Io,e:Expected,resume?:Checkpoint){
 const steps:any[]=[];let lastUi:string|undefined;let androidPid:number|undefined;
 const result:any={status:'NOT_RUN',steps,coverage:'native confirmed locale and selected dictionary',androidBinderLocale:{status:'NOT_RUN',reason:'not exported by official observation'}};
 const api=async(op:string,args:any={})=>{if(!['session.status','multimedia.launch','session.ui','input.tap','input.gesture'].includes(op))throw new NotRun('operation outside acceptance allowlist');return io.api(op,args);};
 const observed=async()=>readProof(await io.observe(),e);
 const wait=async(check:()=>Promise<Proof|false>,reason:string)=>{for(let n=0;n<24;n++){const value=await check();if(value)return value;await io.pause();}throw new Rejected(reason);};
 const ui=async()=>{const u=await api('session.ui',{screen:'primary',timeout_ms:10000});if(u?.boot_id!==e.androidBoot||u.complete!==true||!u.observation_id||u.observation_id===lastUi||!Array.isArray(u.screens)||u.screens.length!==1||!number(u.screens[0]?.width)||!number(u.screens[0].height)||!Array.isArray(u.screens[0].nodes)||!u.screens[0].nodes.every((n:any)=>n&&typeof n==='object'&&typeof n.id==='string'&&typeof n.package==='string'&&(n.parent===null||typeof n.parent==='string')))throw new NotRun('UI observation stale/incomplete');lastUi=u.observation_id;return u;};
 const tap=async(n:any)=>{
  const b=n?.clipped_bounds,s=n?.viewport,u=n?.tree,w=s?.windows?.find((w:any)=>w.id===n.window_id);
  const rect=(b:any)=>b&&[b.x,b.y,b.width,b.height].every(Number.isSafeInteger)&&b.width>0&&b.height>0?'['+b.x+','+b.y+']['+(b.x+b.width)+','+(b.y+b.height)+']':null;
  if(!w||!number(androidPid)||!Number.isSafeInteger(w.layer)||!rect(w.bounds)||!rect(n.bounds)||!rect(b)||n?.visible!==true||n.enabled!==true||n.clickable!==true||b.x<0||b.y<0||b.x+b.width>s.width||b.y+b.height>s.height)throw new NotRun('observed pointer/window target unavailable');
  let call;try{call=nativePointerRequest({action:'tap',x:Math.round(b.x+b.width/2),y:Math.round(b.y+b.height/2),expectedWindow:{id:w.id,displayId:0,windowType:w.type,packageName:w.package,title:w.title,bounds:rect(w.bounds)},expectedTarget:{windowId:n.window_id,packageName:n.package,resource:n.resource_id,className:n.class,text:n.text,bounds:rect(n.bounds),windowType:w.type,rootPackage:w.package,windowTitle:w.title,windowBounds:rect(w.bounds)}},u,{sessionId:e.session.id,pid:androidPid!,boot:e.androidBoot});}catch(error){throw new NotRun('observed pointer fence unavailable: '+String(error));}
  await api(call.op,call.fields);
 };
 const unique=(list:any[],reason:string)=>{if(list.length!==1)throw new NotRun(reason);return list[0];};
 const menu=async(labels:string[])=>{const u=await ui();await tap(unique(nodes(u).filter((n:any)=>n.package==='com.qinggan.app.setting'&&labels.includes(n.text)&&n.visible),'menu missing/ambiguous: '+labels[0]));};
 const launch=async()=>api('multimedia.launch',{screen:'primary',package:'com.qinggan.app.setting',boot_id:e.androidBoot});
 const toggle=async(enabled:boolean)=>{await launch();await menu(['Voboost']);const u=await ui(),all=nodes(u);const label=unique(all.filter((n:any)=>['Russian language','Русский язык','俄语'].includes(n.text)&&n.package==='com.qinggan.app.setting'&&n.visible),'Russian agent row unavailable');if(label.parent===null||label.parent===undefined||!all.some((n:any)=>n.id===label.parent&&n.package==='com.qinggan.app.setting'))throw new NotRun('agent row parent unavailable');const box=unique(all.filter((n:any)=>n.package==='com.qinggan.app.setting'&&n.parent===label.parent&&resource(n,'hint_switch_button')&&n.checkable===true&&n.visible),'agent checkbox missing/ambiguous');if(box.checked!==enabled)await tap(box);};
 const select=async(locale:Locale,verifyOnly=false)=>{await launch();let u=await ui();if(!nodes(u).some((n:any)=>n.package==='com.qinggan.app.setting'&&n.resource_id==='com.qinggan.app.setting:id/display_language_radio_group')){await menu(['Display','显示','Экран','Дисплей']);u=await ui();}
  const all=nodes(u),group=unique(all.filter((n:any)=>n.package==='com.qinggan.app.setting'&&n.resource_id==='com.qinggan.app.setting:id/display_language_radio_group'&&n.visible),'OEM language group unavailable');
  const inside=(n:any)=>{const visited=new Set();while(n?.parent!==null&&n?.parent!==undefined){if(n.package!=='com.qinggan.app.setting')return false;if(n.parent===group.id)return true;if(visited.has(n.parent))return false;visited.add(n.parent);n=all.find((v:any)=>v.id===n.parent);}return false;};
  if(all.some((n:any)=>inside(n)&&resource(n,'display_language_radio_russian')))throw new NotRun('native RU picker needs its validated adapter');
  const radio=unique(all.filter((n:any)=>n.package==='com.qinggan.app.setting'&&inside(n)&&n.visible&&(locale==='ru'?n.text==='Русский':n.resource_id==='com.qinggan.app.setting:id/'+(locale==='en'?'display_language_radio_english':'display_language_radio_chinese'))),'locale radio missing/ambiguous');
  if(radio.checked!==true){if(verifyOnly)throw new Rejected('original picker did not retain requested language: '+locale);await tap(radio);}
  const checked=await ui(),checkedNodes=nodes(checked),checkedGroups=checkedNodes.filter((n:any)=>n.package==='com.qinggan.app.setting'&&n.resource_id==='com.qinggan.app.setting:id/display_language_radio_group'&&n.visible);
  if(checkedGroups.length!==1)throw new NotRun('post-action picker group unavailable');
  const selected=checkedNodes.filter((n:any)=>n.package==='com.qinggan.app.setting'&&n.visible&&n.checked===true&&(locale==='ru'?n.text==='Русский':n.resource_id==='com.qinggan.app.setting:id/'+(locale==='en'?'display_language_radio_english':'display_language_radio_chinese')));
  if(selected.length!==1)throw new NotRun('fresh selected system picker readback unavailable');
  let cursor=selected[0],found=false;const seen=new Set();while(cursor?.parent!=null){if(cursor.package!=='com.qinggan.app.setting'||seen.has(cursor.parent))break;if(cursor.parent===checkedGroups[0].id){found=true;break;}seen.add(cursor.parent);const parents=checkedNodes.filter((n:any)=>n.id===cursor.parent);if(parents.length!==1)break;cursor=parents[0];}
  if(!found)throw new NotRun('selected radio not in original picker group');return checked;
 };
 const endpoint=async(stage:string,before:Proof,after:Proof,pickerUi?:any,baseline?:Logs,current?:Logs)=>{if(io.endpoint)await io.endpoint({stage,beforeObserve:before.raw,afterObserve:after.raw,...(pickerUi?{pickerUi}:{}),...(baseline?{baselineLogs:baseline.archive,baselineLogCapture:baseline.capture}:{}),...(current?{currentLogs:current.archive,currentLogCapture:current.capture}:{})});};
 const render=async(locale:Locale,baseline?:Logs)=>{await launch();await menu(['Voboost']);if(!baseline||!io.logs)return undefined;const current=await io.logs();let row;try{row=renderLocale(freshDiagnostics({boot:e.androidBoot,records:baseline.records},{boot:e.androidBoot,records:current.records,diagnostics:current.diagnostics}));}catch(error){throw new NotRun('actual Android render locale unavailable: '+String(error));}if(row.fields.effectiveLanguage!==locale||![row.fields.defaultLocale,row.fields.resourceLocale].every(v=>typeof v==='string'&&new RegExp('^'+locale+'(?:[-_]|$)').test(v.toLowerCase())))throw new Rejected('fresh Android process/resource locale differs');return current;};
 try{
  const s=await api('session.status');const live=sessionStatus(s,e.session);androidPid=live.pid;if(live.serial!==e.serial)throw new NotRun('official observer device differs from owned session');
  if(resume){const before=resumeProof(await io.observe(),resume,e),baseline=await io.logs?.(),picker=await select('ru',true),current=await render('ru',baseline),p=resumeProof(await io.observe(),resume,e);await endpoint('reboot-restoration',before,p,picker,baseline,current);steps.push({step:'reboot-restoration',status:'PASS',qnxBoot:p.cluster.bootId,owners});return {...result,status:'PASS'};}
  let before=await observed();const disabledBefore=before;await toggle(true);
  before=await wait(async()=>{const p=await observed();return p.enabled&&p.agent.enabled&&(before.enabled||p.configRevision!==before.configRevision)?p:false;},'UI enable was not persisted');await endpoint('enable',disabledBefore,before);steps.push({step:'enable',status:'PASS'});
  let previous=before;
  for(const locale of ['ru','en','zh'] as Locale[]){const before=await observed(),baseline=await io.logs?.(),picker=await select(locale);await wait(async()=>{const p=await observed();return confirmed(p,locale,before)?p:false;},'all-owner locale/dictionary ACK missing: '+locale);const current=await render(locale,baseline);previous=await observed();if(!confirmed(previous,locale,before))throw new Rejected('locale changed during Android render');await endpoint('locale-'+locale,before,previous,picker,baseline,current);steps.push({step:'locale-'+locale,status:'PASS',owners,observationRevision:previous.cluster.observationRevision});}
  const active=previous,disableBaselineLogs=await io.logs?.();await toggle(false);
  await wait(async()=>{const p=await observed();return restarted(p,active)?p:false;},'disabled intent or independent owner restart was not observed');
  const disabledPicker=await select('zh',true),disabledNodes=nodes(disabledPicker),disabledGroup=unique(disabledNodes.filter((n:any)=>n.package==='com.qinggan.app.setting'&&n.resource_id==='com.qinggan.app.setting:id/display_language_radio_group'),'disabled original picker group unavailable');
  const stillRussian=disabledNodes.some((n:any)=>{if(n.package!=='com.qinggan.app.setting'||!(n.text==='Русский'||resource(n,'display_language_radio_russian')))return false;const seen=new Set();while(n?.parent!=null){if(seen.has(n.parent))return false;if(n.parent===disabledGroup.id)return true;seen.add(n.parent);const parents=disabledNodes.filter((v:any)=>v.id===n.parent);if(parents.length!==1)return false;n=parents[0];}return false;});
  if(stillRussian)throw new Rejected('disabled Russian contribution still present in original picker');
  const disabledLogs=await render('zh',disableBaselineLogs),stopped=await observed();if(!restarted(stopped,active))throw new Rejected('disabled owner proof changed during Android readback');await endpoint('disable-owner-restart',active,stopped,disabledPicker,disableBaselineLogs,disabledLogs);steps.push({step:'disable-owner-restart',status:'PASS',owners});
  await toggle(true);const baseline=await observed(),baselineLogs=await io.logs?.(),armedPicker=await select('ru');
  const armed=await wait(async()=>{const p=await observed();return confirmed(p,'ru',baseline)?p:false;},'reboot baseline lacks all-owner Russian ACK');
  const armedLogs=await render('ru',baselineLogs),finalArmed=await observed();if(!confirmed(finalArmed,'ru',baseline))throw new Rejected('reboot baseline changed during Android readback');await endpoint('reboot-armed',baseline,finalArmed,armedPicker,baselineLogs,armedLogs);
  const checkpoint:Checkpoint={schema:1,stage:'external-reboot',daemonEpoch:finalArmed.cluster.daemonEpoch,expected:e,processes:finalArmed.processes,observationRevision:finalArmed.cluster.observationRevision,desiredRevision:finalArmed.cluster.desiredRevision,desiredSha256:finalArmed.cluster.desiredSha256};
  steps.push({step:'external-reboot',status:'NOT_RUN',reason:'owner must reboot externally, then resume with new boot identities'});
  return {...result,status:'AWAITING_REBOOT',checkpoint};
 }catch(error){return {...result,status:error instanceof NotRun?'NOT_RUN':'FAIL',reason:String(error)};}
}

export async function main(configPath:string){
 const c=JSON.parse(fs.readFileSync(configPath,'utf8'));
 if(![c.evidence,c.installer,c.native?.executable,c.native?.root].every(v=>typeof v==='string'&&path.isAbsolute(v))||!c.expected||!same(c.native.session,c.expected.session)||c.serial!==c.expected.serial)throw new Error('absolute paths and exact owned session/expected proof required');
 if(!c.logIdentity||c.logIdentity.serial!==c.serial||typeof c.logIdentity.version!=='string'||!c.logIdentity.version||!digest(c.logIdentity.manifestSha256))throw new Error('official log identity required before any UI mutation');
 const workspace=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
 if(!fs.realpathSync(path.dirname(c.evidence)).startsWith(workspace+path.sep))throw new Error('evidence must stay inside the workspace');
 fs.mkdirSync(c.evidence,{mode:0o700});let sequence=0;
 const retain=(suffix:string,value:string)=>{const file=path.join(c.evidence,String(++sequence).padStart(4,'0')+'-'+suffix);fs.writeFileSync(file,value,{flag:'wx',mode:0o600});return file;};
 const api=async(op:string,args:any={})=>{const requestFile=path.join(c.evidence,String(sequence+1).padStart(4,'0')+'-request.json');const call=invocation(c.native as Config,op as Operation,requestFile,args);fs.writeFileSync(requestFile,JSON.stringify(call.request)+'\n',{flag:'wx',mode:0o600});const p=spawnSync(call.executable,call.args,{encoding:'utf8',timeout:call.timeoutMs,maxBuffer:2*1024*1024});retain('result.json',p.stdout??'');retain('stderr.log',p.stderr??'');const result=response({exitCode:p.status,stdout:p.stdout??'',timedOut:p.error?.code==='ETIMEDOUT',signal:p.signal},call.request);if(op==='session.status')sessionStatus(result,c.native.session,c.native);return result;};
 const observe=async()=>{const p=spawnSync(c.installer,['--observe','--device',c.serial],{encoding:'utf8',timeout:15000,maxBuffer:2*1024*1024});retain('official-observe.json',p.stdout??'');retain('observe-stderr.log',p.stderr??'');if(p.status!==0||p.error)throw new NotRun('official observe unavailable');return JSON.parse(p.stdout);};
 const logs=async():Promise<Logs>=>{
  if(!c.logIdentity||c.logIdentity.serial!==c.serial)throw new NotRun('official log archive identity missing');const directory=path.join(c.evidence,'logs-'+String(++sequence).padStart(4,'0'));fs.mkdirSync(directory,{mode:0o700});
  const argv=['--logs',directory,'--device',c.serial],startedAtUtc=new Date().toISOString(),p=spawnSync(c.installer,argv,{encoding:'utf8',timeout:120000,maxBuffer:2*1024*1024}),completedAtUtc=new Date().toISOString();
  const stdout=path.relative(c.evidence,retain('logs-stdout.log',p.stdout??'')),stderr=path.relative(c.evidence,retain('logs-stderr.log',p.stderr??''));
  if(p.status!==0||p.error)throw new NotRun('official log archive unavailable');const files=fs.readdirSync(directory).filter(f=>f.endsWith('.tar.xz'));if(files.length!==1)throw new NotRun('exactly one official log archive required');const archive=path.join(directory,files[0]),relativeArchive=path.relative(c.evidence,archive);
  const capture=path.relative(c.evidence,retain('logs-capture.json',JSON.stringify({schema:1,operation:'installer-logs',startedAtUtc,completedAtUtc,exitCode:p.status,stdout,stderr,archive:relativeArchive,argv,deviceSerial:c.serial})+'\n'));
  return {archive:relativeArchive,capture,...parseInstallerLogs(fs.readFileSync(archive),c.logIdentity)};
 };
 const endpoints:any[]=[];
 const endpoint=async(value:Endpoint)=>{const row:any={stage:value.stage,beforeObserve:path.relative(c.evidence,retain(value.stage+'-before-observe.json',JSON.stringify(value.beforeObserve)+'\n')),afterObserve:path.relative(c.evidence,retain(value.stage+'-after-observe.json',JSON.stringify(value.afterObserve)+'\n'))};if(value.pickerUi)row.pickerUi=path.relative(c.evidence,retain(value.stage+'-picker-ui.json',JSON.stringify(value.pickerUi)+'\n'));for(const key of ['baselineLogs','currentLogs','baselineLogCapture','currentLogCapture'] as const)if(value[key])row[key]=value[key];endpoints.push(row);};
 const checkpoint=c.resume?JSON.parse(fs.readFileSync(c.resume,'utf8')):undefined;
 const result=await runI18n({api,observe,logs,endpoint,pause:()=>new Promise(r=>setTimeout(r,500))},c.expected,checkpoint);
 fs.writeFileSync(path.join(c.evidence,'I18N-ENDPOINTS.json'),JSON.stringify({schema:1,expected:c.expected,endpoints},null,2)+'\n',{flag:'wx',mode:0o600});
 if(result.checkpoint)fs.writeFileSync(path.join(c.evidence,'CHECKPOINT.json'),JSON.stringify(result.checkpoint,null,2)+'\n',{flag:'wx',mode:0o600});
 fs.writeFileSync(path.join(c.evidence,'RESULT.json'),JSON.stringify(result,null,2)+'\n',{flag:'wx',mode:0o600});console.log(JSON.stringify(result));process.exitCode=result.status==='PASS'?0:2;
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await main(process.argv[2]);
