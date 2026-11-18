/** Optional diagnostics only: all times below are daemon receipt times, never backend latency. */
export const STARTUP_MILESTONE_BYTES = 64 * 1024;
const fail = (reason: string): never => { throw new Error(`startup milestones ${reason}`); };
const object = (v: any, keys: string[]) => {
  if (!v || typeof v !== "object" || Array.isArray(v) || keys.length !== Object.keys(v).length || keys.some(k => !Object.hasOwn(v,k))) fail("closed shape differs");
};
const uint = (v: any) => Number.isSafeInteger(v) && v >= 0;
const positive = (v: any) => uint(v) && v > 0;
const decimal = (v: any, zero = false) => typeof v === "string" && /^(0|[1-9][0-9]*)$/.test(v) && (zero || v!=="0") && BigInt(v) <= (zero ? 9223372036854775807n : 18446744073709551615n);
const digest = (v: any) => typeof v === "string" && /^[0-9a-f]{64}$/.test(v);
const text = (v: any, max: number) => typeof v === "string" && Buffer.byteLength(v) > 0 && Buffer.byteLength(v) <= max && !/[\x00-\x1f\x7f]/.test(v);
const uuid = (v: any) => typeof v === "string" && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(v);
export type StartupIdentity = { bootId: string; daemonPid: number; daemonStarttime: string; manifestSha256: string; generationId: string; catalogSha256: string };
export type StartupTarget = { process: string; pid: number; starttime: string; readyMs: number; batch: number; priority: number; dispatchMs: number|null; dispatchRank: number|null; endpointEpoch: string|null; adoptedMs: number|null };
export type StartupApplication = { contract: string; intent: string; outcome: "matchedNoop"|"writeVerified"; semantics: {name:string;desired:boolean;actual:boolean}[]; androidObservedAtBootMs: number; receivedMs: number };
export type StartupMember = {id:string;target:number;documentGeneration:string;lifecycleGeneration:string;application:StartupApplication|null};
export type StartupClockAnchor = {clockDomain:"linux.bootElapsedRealtime";observedAtBootMs:number;ownerBeforeMs:number;ownerAfterMs:number;precisionMs:10};
export type StartupMilestones = {clockAnchor:StartupClockAnchor|null;clockDomain:"daemon.monotonicSinceOwnerStart";identity:StartupIdentity;complete:boolean;reason:null|"budget-exceeded";expectedTargetCount:number;expectedMemberCount:number;targets:StartupTarget[];members:StartupMember[]};
/** Reject unknown fields, unsafe JSON integers, mixed clocks, inconsistent nullable milestones and duplicates. */
export function startupMilestonesShape(v: any): asserts v is StartupMilestones {
  if (Buffer.byteLength(JSON.stringify(v)) > STARTUP_MILESTONE_BYTES) fail("byte budget exceeded");
  object(v,["clockDomain","clockAnchor","identity","complete","reason","expectedTargetCount","expectedMemberCount","targets","members"]);
  object(v.identity,["bootId","daemonPid","daemonStarttime","manifestSha256","generationId","catalogSha256"]);
  if(v.clockAnchor!==null) {
    object(v.clockAnchor,["clockDomain","observedAtBootMs","ownerBeforeMs","ownerAfterMs","precisionMs"]);
    const a=v.clockAnchor;
    if(a.clockDomain!=="linux.bootElapsedRealtime" || !uint(a.observedAtBootMs) || a.observedAtBootMs%10!==0 || !uint(a.ownerBeforeMs) || !uint(a.ownerAfterMs) || a.ownerBeforeMs>a.ownerAfterMs || a.precisionMs!==10) fail("owner clock anchor differs");
  }

  if(v.clockDomain!=="daemon.monotonicSinceOwnerStart" || !uuid(v.identity.bootId) || !positive(v.identity.daemonPid) || v.identity.daemonPid<2 || v.identity.daemonPid>2147483647 || !decimal(v.identity.daemonStarttime) || !["manifestSha256","generationId","catalogSha256"].every(k=>digest(v.identity[k])) || typeof v.complete!=="boolean" || !uint(v.expectedTargetCount) || v.expectedTargetCount>4096 || !uint(v.expectedMemberCount) || v.expectedMemberCount>4096 || !Array.isArray(v.targets) || !Array.isArray(v.members)) fail("identity or inventory differs");
  if(!v.complete) {
    if(v.reason!=="budget-exceeded" || v.targets.length || v.members.length) fail("overflow projection differs");
    return;
  }
  if(v.reason!==null || v.targets.length>v.expectedTargetCount || v.members.length>v.expectedMemberCount) fail("complete projection differs");
  const processes=new Set<string>(), ranks=new Set<string>(), members=new Set<string>();
  for(const t of v.targets) {
    object(t,["process","pid","starttime","readyMs","batch","priority","dispatchMs","dispatchRank","endpointEpoch","adoptedMs"]);
    if(!text(t.process,255) || processes.has(t.process) || !positive(t.pid) || t.pid<2 || t.pid>2147483647 || !decimal(t.starttime) || !uint(t.readyMs) || !uint(t.batch) || !uint(t.priority) || t.priority>3) fail("target differs");
    processes.add(t.process);
    if(t.dispatchMs===null) { if(t.dispatchRank!==null || t.endpointEpoch!==null || t.adoptedMs!==null) fail("target lacks dispatch"); }
    else {
      if(!uint(t.dispatchMs) || t.dispatchMs<t.readyMs || !uint(t.dispatchRank) || t.dispatchRank>4294967295) fail("dispatch differs");
      const rank=`${t.batch}:${t.dispatchRank}`; if(ranks.has(rank)) fail("duplicate dispatch rank"); ranks.add(rank);
      if(t.endpointEpoch===null) { if(t.adoptedMs!==null) fail("adoption lacks endpoint"); }
      else if(!decimal(t.endpointEpoch) || !uint(t.adoptedMs) || t.adoptedMs<t.dispatchMs) fail("adoption differs");
    }
  }
  for(const m of v.members) {
    object(m,["id","target","documentGeneration","lifecycleGeneration","application"]);
    if(!text(m.id,96) || !uint(m.target) || m.target>=v.targets.length || !decimal(m.documentGeneration,true) || !decimal(m.lifecycleGeneration,true)) fail("member differs");
    const key=`${m.id}:${m.target}`; if(members.has(key)) fail("duplicate member"); members.add(key);
    const a=m.application; if(a===null) continue;
    object(a,["contract","intent","outcome","semantics","androidObservedAtBootMs","receivedMs"]);
    const t=v.targets[m.target];
    if(!text(a.contract,96) || !text(a.intent,64) || !["matchedNoop","writeVerified"].includes(a.outcome) || !uint(a.androidObservedAtBootMs) || !uint(a.receivedMs) || t.adoptedMs===null || a.receivedMs<t.adoptedMs || !Array.isArray(a.semantics) || !a.semantics.length || a.semantics.length>16) fail("application differs");
    const names=new Set<string>();
    for(const s of a.semantics) { object(s,["name","desired","actual"]); if(!text(s.name,96) || names.has(s.name) || typeof s.desired!=="boolean" || typeof s.actual!=="boolean" || s.actual!==s.desired) fail("semantic confirmation differs"); names.add(s.name); }
  }
}
export type StartupCatalogMember = {id:string;startupPriority?:string;targets:{process:string}[]};
export type StartupInterval = {id:string;process:string;readyToDispatchMs:number;dispatchToAdoptMs:number;adoptToApplicationReceiptMs:number;readyToApplicationReceiptMs:number;androidObservedAtBootMs:number};
export type StartupResult = {status:"passed";ownerBootAnchor:StartupClockAnchor;intervals:StartupInterval[]}|{status:"unavailable";reason:string};
/** At least one current backend receipt for every enabled vehicle member; no completion-before-UI barrier. */
export function startupMilestoneEvidence(status:any, identity:StartupIdentity, catalog:StartupCatalogMember[], enabledIds:readonly string[]):StartupResult {
  if(!Object.hasOwn(status,"startupMilestones")) return {status:"unavailable",reason:"optional startup diagnostics absent"};
  const v=status.startupMilestones; startupMilestonesShape(v);
  if(Object.keys(identity).some(k=>identity[k as keyof StartupIdentity]!==v.identity[k])) fail("independent daemon identity differs");
  if(["manifestSha256","generationId","catalogSha256"].some(k=>status[k]!==v.identity[k]) || status.state!=="ready" || status.panic!==false || status.killed!==false) fail("resident generation or health differs");
  if(!v.complete) return {status:"unavailable",reason:"startup diagnostics budget exceeded"};
  if(v.clockAnchor===null) return {status:"unavailable",reason:"trusted owner initialization boot clock anchor unavailable"};
  if(!Array.isArray(status.injections) || new Set(enabledIds).size!==enabledIds.length || new Set(catalog.map(m=>m.id)).size!==catalog.length || enabledIds.some(id=>!catalog.some(m=>m.id===id))) fail("enabled catalog inventory differs");
  const expected=catalog.filter(m=>enabledIds.includes(m.id)&&m.startupPriority==="vehicle");
  if(!expected.length) return {status:"unavailable",reason:"no enabled vehicle members"};
  const catalogProcesses = new Set(catalog.flatMap(m => m.targets.map(t => t.process)));
  const pairCount = expected.reduce((n,m) => n + m.targets.length,0);
  if (v.expectedTargetCount !== catalogProcesses.size || v.expectedMemberCount !== pairCount ||
      v.targets.some(t => !catalogProcesses.has(t.process)) ||
      v.members.some(m => !expected.some(e => e.id === m.id && e.targets.some(t => t.process === v.targets[m.target].process)))) fail("signed startup inventory differs");
  for (const t of v.targets) {
    if (expected.some(m => m.targets.some(target => target.process === t.process)) && t.priority !== 0) fail("vehicle target priority differs");
    if (t.dispatchMs === null || t.adoptedMs === null) return {status:"unavailable",reason:`admitted-ready target dispatch/adoption missing: ${t.process}`};
  }

  for(const t of v.targets) if(t.dispatchMs!==null) for(const other of v.targets) {
    if(other.batch===t.batch && other.priority<t.priority && other.dispatchMs!==null && (other.dispatchRank!>=t.dispatchRank! || other.dispatchMs>t.dispatchMs)) fail("same-batch priority dispatch differs");
  }
  const intervals:StartupInterval[]=[];
  for(const member of expected) {
    const candidates=v.members.filter(m=>m.id===member.id && m.application!==null);
    let found=false;
    for(const m of candidates) {
      const t=v.targets[m.target], a=m.application!;
      if(!member.targets.some(target=>target.process===t.process)) fail("member process outside signed catalog");
      const rows=status.injections.filter((row:any)=>row.id===m.id&&row.process===t.process);
      if(rows.length!==1) fail("member injection ownership differs");
      const row=rows[0], e=row.currentEndpoint, r=e?.acceptedStatus?.receipt;
      if(row.state!=="active" || String(row.documentGeneration)!==String(m.documentGeneration) || String(row.lifecycleGeneration)!==String(m.lifecycleGeneration) || !e?.acceptedStatus || String(e.targetPid)!==String(t.pid) || String(e.targetStarttime)!==String(t.starttime) || String(e.endpointEpoch)!==String(t.endpointEpoch) || e.generationId!==identity.generationId || e.catalogSha256!==identity.catalogSha256 || e.desiredDocumentGeneration!==String(m.documentGeneration) || e.acceptedStatus.documentGeneration!==String(m.documentGeneration) || r.bootId!==identity.bootId || r.daemonPid!==String(identity.daemonPid) || r.daemonStarttime!==String(identity.daemonStarttime)) fail("member current accepted endpoint differs");
      if(t.dispatchMs===null || t.adoptedMs===null) continue;
      intervals.push({id:m.id,process:t.process,readyToDispatchMs:t.dispatchMs-t.readyMs,dispatchToAdoptMs:t.adoptedMs-t.dispatchMs,adoptToApplicationReceiptMs:a.receivedMs-t.adoptedMs,readyToApplicationReceiptMs:a.receivedMs-t.readyMs,androidObservedAtBootMs:a.androidObservedAtBootMs}); found=true;
    }
    if(!found) return {status:"unavailable",reason:`current backend application receipt missing: ${member.id}`};
  }
  return {status:"passed",ownerBootAnchor:{...v.clockAnchor},intervals};
}
