import type { UiNode } from "./installer-ui.ts";

/** Recognize only the observed OEM safety dialog; callers use the normal fenced tap. */
export function navigationSafetyConfirm(nodes: readonly UiNode[]): UiNode | null {
  const owner = "com.pateonavi.naviapp";
  const owned = nodes.filter(n => n.packageName === owner && n.visibleToUser === true && n.enabled && n.windowRef?.type === 1 && n.windowRef.active && n.windowRef.inputFocused && n.windowRef.rootPackage === owner);
  const titles = owned.filter(n => n.resource === `${owner}:id/agree_title` && n.className === "android.widget.TextView" && n.text.trim().length > 0);
  const contexts = owned.filter(n => n.resource === `${owner}:id/agree_context` && n.className === "android.widget.TextView" && n.text.trim().length > 0);
  const buttons = owned.filter(n => n.resource === `${owner}:id/btn_agree` && n.className === "android.widget.TextView" && n.clickable && (n.text.trim().length > 0 || n.description.trim().length > 0));
  if (!titles.length && !contexts.length && !buttons.length) return null;
  if (titles.length !== 1 || contexts.length !== 1 || buttons.length !== 1 || titles[0].windowRef !== buttons[0].windowRef || contexts[0].windowRef !== buttons[0].windowRef) throw new Error("Navi safety dialog is incomplete or ambiguous");
  if (owned.some(n => n.className === "android.widget.EditText")) throw new Error("Navi safety dialog conflicts with editor");
  return buttons[0];
}

/** Actual OEM map search entry; editor must still be independently observed after tap. */
export function navigationSearchEntry(nodes: readonly UiNode[]): UiNode | null {
  const owner = "com.pateonavi.naviapp";
  const owned = nodes.filter(n => n.packageName === owner && n.visibleToUser === true && n.enabled && n.windowRef?.type === 1 && n.windowRef.active && n.windowRef.inputFocused && n.windowRef.rootPackage === owner);
  if (owned.some(n => n.className === "android.widget.EditText")) return null;
  const entries = owned.filter(n => n.resource === `${owner}:id/ll_normal_search` && n.className === "android.widget.LinearLayout" && n.clickable && n.description.trim().length > 0);
  const labels = owned.filter(n => n.resource === `${owner}:id/tv_normal_search` && n.className === "android.widget.TextView" && n.clickable && n.text.trim().length > 0);
  if (!entries.length && !labels.length) return null;
  if (entries.length !== 1 || labels.length !== 1 || entries[0].windowRef !== labels[0].windowRef || nodes.indexOf(entries[0]) !== labels[0].parent) throw new Error("Navi search entry is incomplete or ambiguous");
  return entries[0];
}

import { createHash } from 'node:crypto';

export type Rect = readonly [number, number, number, number]; // left, top, right, bottom
export type KeyboardFrame = {
  boot: string; session: string; pid: number; window: number; resource: string;
  bounds: Rect; geometryVerified: boolean; frameBoot: string;
  observedAt: number; width: number; height: number; rgba: Uint8Array;
};
export type ReviewedLayout = {
  schema: 1; language: 'en' | 'ru'; theme: string; sourceRevision: string;
  baselineSha256: string; reviewer: string; reviewedAt: string;
  bounds: Rect; width: number; height: number;
  controls?: Array<{ label: 'shift' | 'switch'; targetBaselineSha256: string }>;
  keys: Array<{ label: string; region: Rect; center: readonly [number, number] }>;
};
const fail = (why: string): never => { throw new Error('visual keyboard unavailable: ' + why); };
const digest = (bytes: Uint8Array) => createHash('sha256').update(bytes).digest('hex');
const eq = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);
function rect(r: Rect, width: number, height: number): boolean {
  return Array.isArray(r) && r.length === 4 && r.every(Number.isSafeInteger) && r[0] >= 0 && r[1] >= 0 && r[2] > r[0] && r[3] > r[1] && r[2] <= width && r[3] <= height;
}
function frame(f: KeyboardFrame): void {
  if (!f.geometryVerified || f.frameBoot !== f.boot || !f.boot || !f.session || !Number.isSafeInteger(f.pid) || f.pid < 1 || !Number.isSafeInteger(f.window) || f.window < 0 || !/^com\.qinggan\.app\.qgime(?:\.second)?:id\/alpha_view1$/.test(f.resource) || !rect(f.bounds, 1920, 720) || f.width !== f.bounds[2]-f.bounds[0] || f.height !== f.bounds[3]-f.bounds[1] || f.rgba.length !== f.width*f.height*4 || !Number.isFinite(f.observedAt)) fail('capture identity or geometry');
}
/** Baseline PNG is decoded by the existing capture reader. No baseline creation/update here. */
export function visualKey(layouts: ReviewedLayout[], baseline: (sha: string) => Uint8Array, current: KeyboardFrame,
  fresh: KeyboardFrame, label: string, context: { theme: string; sourceRevision: string; now: number; language: 'en' | 'ru' }): { x: number; y: number; evidence: 'reviewed-pixels-and-live-editor' } {
  frame(current); frame(fresh);
  if (!eq([current.boot,current.session,current.pid,current.window,current.resource,current.bounds], [fresh.boot,fresh.session,fresh.pid,fresh.window,fresh.resource,fresh.bounds])) fail('changed live window');
  if (fresh.observedAt < current.observedAt || context.now < fresh.observedAt) fail('capture chronology differs');
  // The older frame proves stable pixels; only the latest frame authorises a current key.
  const freshAge = context.now-fresh.observedAt;
  if (freshAge > 2000) fail('fresh capture age exceeds 2000ms: '+Math.round(freshAge)+'ms');
  const matches = layouts.filter(layout => {
    if (layout.schema !== 1 || layout.language !== context.language || layout.theme !== context.theme || layout.sourceRevision !== context.sourceRevision || !layout.reviewer.trim() || !Number.isFinite(Date.parse(layout.reviewedAt)) || !/^[a-f0-9]{64}$/.test(layout.baselineSha256) || !eq(layout.bounds,current.bounds) || layout.width !== current.width || layout.height !== current.height) return false;
    const bytes = baseline(layout.baselineSha256);
    if (bytes.length !== current.rgba.length || digest(bytes) !== layout.baselineSha256) fail('reviewed baseline content differs');
    if (!layout.keys.length || new Set(layout.keys.map(k=>k.label)).size !== layout.keys.length) fail('duplicate or empty labels');
    for (const key of layout.keys) {
      if (!key.label || !rect(key.region,current.width,current.height) || key.center.length !== 2 || !key.center.every(Number.isSafeInteger) || key.center[0] <= key.region[0] || key.center[0] >= key.region[2] || key.center[1] <= key.region[1] || key.center[1] >= key.region[3]) fail('key geometry');
      // Exact label-region comparison is conservative and deterministic. Dynamic suggestions are excluded.
      for (let y=key.region[1]; y<key.region[3]; y++) for (let x=key.region[0]; x<key.region[2]; x++) for (let c=0; c<4; c++) {
        const i=(y*current.width+x)*4+c;
        if (bytes[i] !== current.rgba[i] || current.rgba[i] !== fresh.rgba[i]) return false;
      }
    }
    return true;
  });
  if (matches.length !== 1) fail('no unique reviewed pixel layout');
  const key=matches[0].keys.find(key=>key.label === label);
  if (!key) fail('requested label absent');
  return { x: current.bounds[0]+key.center[0], y: current.bounds[1]+key.center[1], evidence: 'reviewed-pixels-and-live-editor' };
}
/** Call after every actual gesture, before another key; UTF-16 selection is Android's contract. */
export function committedPrefix(editor: { password: boolean; focused: boolean; visible: boolean; enabled: boolean; value: string; showingHint?: boolean; selectionStart?: number; selectionEnd?: number }, expected: string): void {
  // A proven empty hint can precede the first key with Android reporting an unknown caret.
  // Preserve -1/-1: this admits empty input, not a claimed observed position of zero.
  const emptyHint = expected === '' && editor.value === '' && editor.showingHint === true && editor.selectionStart === -1 && editor.selectionEnd === -1;
  const endCaret = editor.selectionStart === expected.length && editor.selectionEnd === expected.length;
  if (editor.password || !editor.focused || !editor.visible || !editor.enabled || editor.value !== expected || !(emptyHint || endCaret)) fail('committed prefix or caret differs');
}

/** Only a semantic lookup miss may choose canvas input; dispatch uncertainty is never replayed. */
export async function actualKeyboardInput<T>(lookup:()=>T, tap:(key:T)=>Promise<void>, canvas:()=>Promise<void>, missing:(error:unknown)=>boolean): Promise<void> {
  let key:T;
  try { key=lookup(); }
  catch(error) { if(!missing(error)) throw error; await canvas(); return; }
  await tap(key);
}

/** Explicit reviewed layout edges only. Neither case nor language implies a touch coordinate. */
export async function reviewedKeyboardRoute(layouts: ReviewedLayout[], baseline: (sha: string)=>Uint8Array,
  capture: ()=>Promise<[KeyboardFrame,KeyboardFrame]>, dispatch: (point:{x:number;y:number}, frame:KeyboardFrame)=>Promise<void>,
  checkEditor: ()=>Promise<void>, context: {theme:string;sourceRevision:string;now:()=>number},
  want: {language?:'en'|'ru';label?:string}): Promise<void> {
  if(!layouts.length || layouts.length>16 || new Set(layouts.map(l=>l.baselineSha256)).size!==layouts.length) fail('ambiguous map identities');
  for(const layout of layouts) {
    if(new Set((layout.controls ?? []).map(c=>c.label)).size!==(layout.controls ?? []).length) fail('ambiguous reviewed controls');
  }
  for(const layout of layouts) for(const control of layout.controls ?? []) {
    if(!['shift','switch'].includes(control.label) || !layout.keys.some(k=>k.label===control.label) || !layouts.some(l=>l.baselineSha256===control.targetBaselineSha256) || control.targetBaselineSha256===layout.baselineSha256) fail('unreviewed control edge');
    const target=layouts.find(l=>l.baselineSha256===control.targetBaselineSha256)!;
    // A reviewed language-key edge can reset case in an EN-only layout.
    // Its different exact target pixels remain mandatory; only Shift cannot change language.
    if(control.label==='shift' && target.language!==layout.language) fail('control edge language differs');
  }
  const observe=async()=>{await checkEditor();const pair=await capture();await checkEditor();return pair;};
  const match=(pair:[KeyboardFrame,KeyboardFrame])=>{
    const found=layouts.filter(layout=>{
      try { visualKey([layout],baseline,pair[0],pair[1],layout.keys[0]?.label ?? '',{...context,now:context.now(),language:layout.language});return true; }
      catch(error){if(String(error).includes('no unique reviewed pixel layout'))return false;throw error;}
    });
    if(found.length!==1)fail('no unique current layout');return found[0];
  };
  let pair=await observe(), current=match(pair);
  const goal=(layout:ReviewedLayout)=>(want.language===undefined || layout.language===want.language) && (want.label===undefined || layout.keys.some(k=>k.label===want.label));
  const paths:Array<Array<{from:ReviewedLayout;label:string;to:ReviewedLayout}>>=[[]];
  let route:typeof paths[number]|undefined;
  for(let i=0;i<paths.length;i++) {
    const path=paths[i], at=path.length?path[path.length-1].to:current;
    if(goal(at)){route=path;break;}
    // A simple path through the finite reviewed graph has at most N-1 edges.
    if(path.length>=layouts.length-1)continue;
    for(const edge of at.controls ?? []) {
      const to=layouts.find(l=>l.baselineSha256===edge.targetBaselineSha256)!;
      if(to===current || path.some(step=>step.from===to))continue;
      paths.push([...path,{from:at,label:edge.label,to}]);
    }
  }
  if(!route)fail('no reviewed route to requested layout');
  for(const edge of route) {
    const point=visualKey([current],baseline,pair[0],pair[1],edge.label,{...context,now:context.now(),language:current.language});
    // One dispatch only. An uncertain receipt must escape, never cause a retry.
    await dispatch(point,pair[1]);
    const next=await observe();
    if(!eq([pair[1].boot,pair[1].session,pair[1].pid,pair[1].window,pair[1].resource,pair[1].bounds],[next[1].boot,next[1].session,next[1].pid,next[1].window,next[1].resource,next[1].bounds]))fail('control changed live window');
    current=match(next);if(current!==edge.to)fail('control did not reach reviewed target');pair=next;
  }
  if(want.label!==undefined) {
    const point=visualKey([current],baseline,pair[0],pair[1],want.label,{...context,now:context.now(),language:want.language ?? current.language});
    await dispatch(point,pair[1]);
  }
}

/** Delete only observed end-caret text; uncertain key receipts escape without replay. */
export async function clearEditorByBackspace(read:()=>Promise<{identity:string;password:boolean;focused:boolean;visible:boolean;enabled:boolean;value:string;showingHint?:boolean;selectionStart?:number;selectionEnd?:number}>, erase:(expected:string)=>Promise<void>, now:()=>number=()=>performance.now()):Promise<void> {
  let current=await read(), expected=current.value;
  const identity=current.identity;
  if(!identity || expected.length>256 || Array.from(expected).length>128) fail('bounded editor clearing authority unavailable');
  const verify=()=>{
    if(current.identity!==identity) fail('editor identity changed during clearing');
    committedPrefix(current,expected);
  };
  verify();
  while(expected.length) {
    const last=Array.from(expected).at(-1)!;
    const previous=expected;
    await erase(expected);
    expected=expected.slice(0,-last.length);
    const deadline=now()+5000;
    for (;;) {
      if(now()>=deadline) fail('delete effect not observed before 5000ms deadline');
      current=await read();
      if(current.identity!==identity) fail('editor identity changed during clearing');
      if(now()>=deadline) fail('delete effect not observed before 5000ms deadline');
      if(current.value===expected) { verify();break; }
      // A successful key receipt is not an observed edit. Wait without sending another key.
      if(current.value!==previous) fail('unexpected editor text after deletion');
      committedPrefix(current,previous);
    }
  }
}
