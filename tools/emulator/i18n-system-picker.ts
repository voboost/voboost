import {constants,openSync,closeSync,fstatSync,readSync,lstatSync,realpathSync} from 'node:fs';
import {isAbsolute,join,resolve,sep} from 'node:path';
import {isDeepStrictEqual} from 'node:util';
import {readProof,confirmed,restarted,resumeProof,type Expected,type Checkpoint} from './i18n-qnx-runtime.ts';
import {parseInstallerLogs} from './installer-logs.ts';
import {freshDiagnostics,renderLocale,sequence} from './functional-observations.ts';

type Endpoint={stage:string;beforeObserve:string;afterObserve:string;pickerUi?:string;baselineLogs?:string;currentLogs?:string;baselineLogCapture?:string;currentLogCapture?:string};
type Index={schema:1;expected:Expected;endpoints:Endpoint[]};
export type I18nEvidenceDirectories={runDirectory:string;resumeDirectory:string};
export type I18nGateAuthority={expected:Expected;official:unknown;logIdentity:{serial:string;version:string;manifestSha256:string}};
export class I18nEvidenceUnavailable extends Error {}
const requireProof=(value:unknown,reason:string):void=>{if(!value)throw new I18nEvidenceUnavailable(reason);};
const same=isDeepStrictEqual;

/** Read owned raw inputs once; summaries and RESULT.status never provide admission. */
class RawInputs {
 readonly artifacts:string[]=[];
 private cache=new Map<string,Buffer>();
 readonly root:string;
 constructor(root:string){this.root=root;requireProof(isAbsolute(root)&&!lstatSync(root).isSymbolicLink()&&lstatSync(root).isDirectory()&&realpathSync(root)===root,'unsafe i18n evidence directory');}
 read(name:string,limit=2*1024*1024):Buffer {
  requireProof(typeof name==='string'&&name.length<=256&&!isAbsolute(name)&&name.split('/').every(part=>/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(part)&&part!=='.'&&part!=='..'),'unsafe i18n raw artifact path');
  const file=resolve(this.root,name);requireProof(file.startsWith(this.root+sep),'i18n raw artifact escaped evidence');
  let parent=this.root;for(const part of name.split('/').slice(0,-1)){parent=join(parent,part);const stat=lstatSync(parent);requireProof(!stat.isSymbolicLink()&&stat.isDirectory(),'linked i18n artifact parent');}
  const cached=this.cache.get(file);if(cached){requireProof(cached.length<=limit,'oversized i18n artifact');return cached;}
  const fd=openSync(file,constants.O_RDONLY|constants.O_NOFOLLOW);
  try {const before=fstatSync(fd);requireProof(before.isFile()&&before.size>=0&&before.size<=limit,'invalid bounded i18n artifact');const bytes=Buffer.alloc(before.size);let at=0;while(at<bytes.length){const count=readSync(fd,bytes,at,bytes.length-at,null);requireProof(count>0,"truncated i18n artifact");at+=count;}requireProof(readSync(fd,Buffer.alloc(1),0,1,null)===0,"i18n artifact grew while reading");const after=fstatSync(fd);requireProof(bytes.length===before.size&&after.size===before.size&&after.mtimeMs===before.mtimeMs&&after.ctimeMs===before.ctimeMs,'i18n artifact changed while reading');this.artifacts.push(file);if(limit<=2*1024*1024)this.cache.set(file,bytes);return bytes;}finally{closeSync(fd);}
 }
 json(name:string):any{return JSON.parse(this.read(name).toString('utf8'));}
}

function index(raw:RawInputs,stages:string[]):Index {
 const value=raw.json('I18N-ENDPOINTS.json');
 requireProof(value?.schema===1&&value.expected&&Array.isArray(value.endpoints)&&same(value.endpoints.map((e:any)=>e?.stage),stages),'i18n raw stage inventory missing or reordered');
 for(const e of value.endpoints)requireProof(e&&typeof e.beforeObserve==='string'&&typeof e.afterObserve==='string','i18n stage raw observation paths missing');
 return value;
}
function expectedBefore(previous:Expected,current:Expected):void {
 requireProof(same(previous.session,current.session)&&previous.serial===current.serial&&previous.androidGeneration===current.androidGeneration&&previous.qnxGeneration===current.qnxGeneration&&previous.catalogSha256===current.catalogSha256,'i18n evidence differs from current owned session/generation/catalog');
 requireProof(previous.androidBoot!==current.androidBoot&&previous.qnxBoot!==current.qnxBoot,'i18n external reboot did not change both boots');
}

/** A second live observation closes the host-side artifact-validation interval. */
export function assertCurrentI18nObservation(raw:unknown,baseline:unknown,expected:Expected):void {
 const current=readProof(raw,expected),before=readProof(baseline,expected);
 requireProof(confirmed(current,'ru')&&current.cluster.snapshotRevision>=before.cluster.snapshotRevision&&current.cluster.observationRevision>=before.cluster.observationRevision&&current.cluster.desiredRevision===before.cluster.desiredRevision&&current.cluster.desiredSha256===before.cluster.desiredSha256&&same(current.processes,before.processes),'i18n authority changed during evidence validation');
}

/** The observed selected radio must be a unique child of the actual OEM group. */
export function checkedSystemPicker(ui:any,expected:Expected,locale:'en'|'zh'|'ru',withoutRussian=false):void {
 requireProof(ui?.session_id===expected.session.id&&ui.boot_id===expected.androidBoot&&ui.source==='android.ui'&&typeof ui.source_instance==='string'&&ui.source_instance.length>0&&typeof ui.observation_id==='string'&&ui.observation_id.length>0&&ui.complete===true&&Array.isArray(ui.screens)&&ui.screens.length===1,'fresh owned system picker UI unavailable');
 const screen=ui.screens[0];requireProof(Array.isArray(screen.nodes)&&Array.isArray(screen.windows),'system picker tree unavailable');
 const nodes=screen.nodes,owner='com.qinggan.app.setting';
 const groups=nodes.filter((n:any)=>n.package===owner&&n.resource_id===owner+':id/display_language_radio_group'&&n.visible===true);
 requireProof(groups.length===1,'system picker group missing or ambiguous');const group=groups[0];
 const window=screen.windows.find((w:any)=>w.id===group.window_id);requireProof(window&&window.package===owner&&window.type===1,'system picker window not owned Settings');
 const inside=(n:any)=>{const seen=new Set();while(n?.parent!==null&&n?.parent!==undefined){if(n.package!==owner||seen.has(n.parent))return false;if(n.parent===group.id)return true;seen.add(n.parent);n=nodes.find((v:any)=>v.id===n.parent);}return false;};
 requireProof(!nodes.some((n:any)=>inside(n)&&n.resource_id===owner+':id/display_language_radio_russian'),'native Russian picker requires its independently validated adapter');
 requireProof(!withoutRussian||!nodes.some((n:any)=>inside(n)&&n.visible===true&&n.text==='Русский'),'disabled Russian contribution remains in the OEM picker');
 const checked=nodes.filter((n:any)=>n.package===owner&&n.window_id===group.window_id&&inside(n)&&n.visible===true&&n.enabled===true&&n.checkable===true&&n.checked===true);
 requireProof(checked.length===1,'system picker has no unique checked language');const radio=checked[0];
 requireProof(locale==='ru'?radio.text==='Русский':radio.resource_id===owner+':id/display_language_radio_'+(locale==='en'?'english':'chinese'),'checked system picker differs from confirmed locale');
}

function collectedLogs(raw:RawInputs,archive:string,capture:string|undefined,authority:I18nGateAuthority):ReturnType<typeof parseInstallerLogs> {
 requireProof(capture,'actual log collection transcript missing');const call=raw.json(capture!);
 requireProof(call?.schema===1&&call.operation==='installer-logs'&&call.exitCode===0&&call.deviceSerial===authority.expected.serial&&call.archive===archive&&Array.isArray(call.argv)&&call.argv.length===4&&call.argv[0]==='--logs'&&call.argv[2]==='--device'&&call.argv[3]===authority.expected.serial,'actual log collection identity differs');
 const start=Date.parse(call.startedAtUtc),end=Date.parse(call.completedAtUtc);
 requireProof(Number.isFinite(start)&&Number.isFinite(end)&&end>=start&&end-start<=120000,'invalid log capture interval');
 const lines=raw.read(call.stdout).toString('utf8').split(/\r?\n/).filter(line=>line&&!/^\[[0-9:. ]+ (INFO|DEBUG|WARN|ERROR)\]/.test(line));raw.read(call.stderr);
 requireProof(lines.length===1&&lines[0]===resolve(raw.root,archive)&&resolve(call.argv[1])===resolve(raw.root,archive,'..'),'log receipt path differs from retained archive');
 const parsed=parseInstallerLogs(raw.read(archive,401*1024*1024),authority.logIdentity),at=Date.parse(parsed.collectedAt);
 requireProof(at>=start&&at<=end,'log archive was not collected during the retained invocation');return parsed;
}

function androidEndpoint(raw:RawInputs,endpoint:Endpoint,expected:Expected,locale:'en'|'zh'|'ru',authority:I18nGateAuthority,withoutRussian=false):void {
 requireProof(endpoint.pickerUi&&endpoint.baselineLogs&&endpoint.currentLogs,'Android checked picker and raw log endpoints missing');
 checkedSystemPicker(raw.json(endpoint.pickerUi!),expected,locale,withoutRussian);
 const baseline=collectedLogs(raw,endpoint.baselineLogs!,endpoint.baselineLogCapture,authority);
 const current=collectedLogs(raw,endpoint.currentLogs!,endpoint.currentLogCapture,authority);
 sequence(baseline.records);sequence(current.records);
 const observed=renderLocale(freshDiagnostics({boot:expected.androidBoot,records:baseline.records},{boot:expected.androidBoot,...current}));
 requireProof(observed.fields.effectiveLanguage===locale,'fresh Android effective language differs');
 for(const key of ['defaultLocale','resourceLocale'])requireProof(new RegExp('^'+locale+'(?:-|$)').test(String(observed.fields[key]).toLowerCase().replaceAll('_','-')),'fresh Android process/resource locale differs');
 requireProof(Date.parse(current.collectedAt)>=Date.parse(baseline.collectedAt),'Android locale log endpoint time regressed');
}

/** Revalidate actual ordered observations against the current gate authority. */
export function admitI18nSystemPicker(directories:I18nEvidenceDirectories,authority:I18nGateAuthority):{artifacts:string[];reason:string} {
 try {
  requireProof(authority.expected.serial===authority.logIdentity.serial,'i18n log device differs from current authority');
  const live=readProof(authority.official,authority.expected);
  const run=new RawInputs(directories.runDirectory),resume=new RawInputs(directories.resumeDirectory);
  const initial=index(run,['enable','locale-ru','locale-en','locale-zh','disable-owner-restart','reboot-armed']);
  const restored=index(resume,['reboot-restoration']);expectedBefore(initial.expected,authority.expected);
  requireProof(same(restored.expected,authority.expected),'i18n restoration differs from current owned boot authority');
  let previous:ReturnType<typeof readProof>|undefined,armed:ReturnType<typeof readProof>|undefined;
  for(const endpoint of initial.endpoints){
   const before=readProof(run.json(endpoint.beforeObserve),initial.expected),after=readProof(run.json(endpoint.afterObserve),initial.expected);
   requireProof(after.cluster.snapshotRevision>before.cluster.snapshotRevision&&after.cluster.observationRevision>before.cluster.observationRevision,'i18n endpoint observations are not fresh');
   if(previous)requireProof(before.cluster.snapshotRevision>=previous.cluster.snapshotRevision&&before.cluster.observationRevision>=previous.cluster.observationRevision&&before.cluster.desiredRevision>=previous.cluster.desiredRevision,'i18n raw stage chronology regressed');
   if(endpoint.stage==='enable')requireProof(after.enabled&&after.agent.enabled&&(before.enabled||after.configRevision!==before.configRevision),'i18n UI enable not persisted');
   else if(endpoint.stage==='disable-owner-restart'){requireProof(restarted(after,before),'i18n disable lacks independently restarted owners');androidEndpoint(run,endpoint,initial.expected,'zh',authority,true);}
   else {const locale=endpoint.stage==='reboot-armed'?'ru':endpoint.stage.slice(7) as 'en'|'zh'|'ru';requireProof(confirmed(after,locale,before),'all-owner selected dictionary locale proof missing');androidEndpoint(run,endpoint,initial.expected,locale,authority);if(endpoint.stage==='reboot-armed')armed=after;}
   previous=after;
  }
  requireProof(armed,'i18n reboot baseline missing');
  const checkpoint:Checkpoint={schema:1,stage:'external-reboot',expected:initial.expected,daemonEpoch:armed!.cluster.daemonEpoch,processes:armed!.processes,observationRevision:armed!.cluster.observationRevision,desiredRevision:armed!.cluster.desiredRevision,desiredSha256:armed!.cluster.desiredSha256};
  requireProof(same(run.json('CHECKPOINT.json'),checkpoint),'i18n checkpoint differs from actual armed transcript');
  const endpoint=restored.endpoints[0];const resumedBefore=resumeProof(resume.json(endpoint.beforeObserve),checkpoint,authority.expected);
  const after=resumeProof(resume.json(endpoint.afterObserve),checkpoint,authority.expected);
  requireProof(after.cluster.snapshotRevision>resumedBefore.cluster.snapshotRevision&&after.cluster.observationRevision>resumedBefore.cluster.observationRevision,'i18n restored endpoint observation is not fresh');
  androidEndpoint(resume,endpoint,authority.expected,'ru',authority);
  requireProof(confirmed(live,'ru')&&live.cluster.snapshotRevision>=after.cluster.snapshotRevision&&live.cluster.observationRevision>=after.cluster.observationRevision&&live.cluster.desiredRevision===after.cluster.desiredRevision&&live.cluster.desiredSha256===after.cluster.desiredSha256&&same(live.processes,after.processes),'restored i18n proof is not current in this gate');
  return {artifacts:[...new Set([...run.artifacts,...resume.artifacts])],reason:'Raw checked OEM picker and fresh Android process/resource locales; all three QNX owners applied, disabled/restarted, and restored across the current external reboot'};
 }catch(error){if(error instanceof I18nEvidenceUnavailable)throw error;throw new I18nEvidenceUnavailable('i18n raw runtime evidence unavailable: '+String(error));}
}
