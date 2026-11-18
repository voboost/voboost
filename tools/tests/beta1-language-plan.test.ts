import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";
import plan from "../../release/beta1-functional-plan.json" with { type: "json" };
import { FULL_REQUIRED, fullFunctionalCase } from "../emulator/full-semantic.ts";

const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
const start = source.indexOf("      case 6:");
const selection = source.slice(start, source.indexOf("      case 7:", start)).replace(/^\s*case 6:/, "").replace(/\s*break;\s*$/, "");
async function opposite(initial: string) {
  const selected: string[] = [];
  const run = new Function("initial", "observedLanguage", "setLanguage", "panel", "proof", "unavailable", "deferred", "observations", `return (async () => { let initialLanguage = initial; ${stripTypeScriptTypes(selection)}; return initialLanguage; })();`);
  const original = await run(initial, async () => initial, async (language: string) => {
    assert.ok(language === "en" || language === "zh", "baseline must not depend on contributed Russian picker"); selected.push(language);
  }, async () => {}, () => {}, (reason: string) => { throw new Error(reason); }, false, async () => ({}));
  return { original, selected };
}
test("baseline case6 changes EN to OEM Chinese and preserves its original language", async () => {
  assert.deepEqual(await opposite("en"), { original: "en", selected: ["zh"] });
});
test("baseline case6 changes Chinese to OEM English without replacing original with Russian", async () => {
  assert.deepEqual(await opposite("zh"), { original: "zh", selected: ["en"] });
});
test("contributed Russian is not silently converted into an EN/ZH baseline", async () => {
  await assert.rejects(opposite("ru"), /EN\/ZH baseline/);
});
test("plan preserves all 27 identities and separates system picker from contributed RU", () => {
  assert.equal(plan.cases.length, 27);
  assert.deepEqual(plan.cases.map(row => row.number), Array.from({ length: 27 }, (_, i) => i + 1));
  for (const number of [6, 7]) assert.match(plan.cases.find(row => row.number === number)!.observable, /OEM.*EN\/ZH/);
  const i18n = plan.cases.find(row => row.number === 22)!;
  assert.match(i18n.observable, /system.*picker/);
  assert.match(i18n.observable, /gauge.*point.*light/);
  assert.doesNotMatch(i18n.observable, /unchanged OEM locale/);
  assert.ok(plan.cases.every(row => row.required));
  assert.ok(!plan.cases.some(row => /gui|intel/i.test(row.id)));
});
test("obsolete Voboost language radio timing cannot satisfy current i18n admission", () => {
  assert.ok(!FULL_REQUIRED[22].includes("language-native-draw-transition-timing"));
  assert.ok(FULL_REQUIRED[22].includes("i18n-system-picker-multi-owner"));
  const original = { number: 22, status: "unavailable", subchecks: [...FULL_REQUIRED[22].filter(id => id !== "i18n-system-picker-multi-owner"), "language-native-draw-transition-timing", "full-case-program-completed"].map(id => ({ id, status: "passed" })) };
  const retained = JSON.stringify(original);
  assert.equal(fullFunctionalCase(original), false);
  assert.equal(JSON.stringify(original), retained, "historical results are read-only");
  for (const status of ["not-run", "unavailable", "failed"]) {
    assert.equal(fullFunctionalCase({ ...original, subchecks: [...original.subchecks, { id: "i18n-system-picker-multi-owner", status }] }), false);
  }
});
test("runner leaves absent official system-picker and multi-owner proof NOT_RUN", () => {
  const body = source.slice(source.indexOf("  const languageRadioProgram ="), source.indexOf("  const cleanupOwnedFixture ="));
  assert.doesNotMatch(body, /row\.fields\.key === "language"|radioTransition\(/);
  assert.match(body, /subcheck\("i18n-system-picker-multi-owner"/);
  assert.match(body, /!c\.i18nRuntimeEvidence \|\| !nativeConfig/);
  assert.match(body, /admitI18nSystemPicker\(c\.i18nRuntimeEvidence/);
  assert.match(source, /want === "ru" && !admittedI18n\) throw new Unavailable\("OEM Russian picker contribution requires admitted agent-i18n-ru runtime"\)/);
});

const localeProgram=source.slice(source.indexOf('  const setLanguage = async'),source.indexOf('  const selectStartup = async')).replace(/^\s*const setLanguage/, 'const setLanguage');
async function russianInput(admitted:boolean,mode:'normal'|'outside'|'unchecked'|'duplicate'='normal') {
  let page='menu',selected='en';const taps:string[]=[];
  const menu={packageName:'com.qinggan.app.setting',resource:'com.qinggan.app.setting:id/menu_radio_display',clickable:true,parent:null};
  const tree=()=>[
    {packageName:'com.qinggan.app.setting',resource:'com.qinggan.app.setting:id/display_language_radio_group',parent:null},
    {packageName:'com.qinggan.app.setting',resource:'com.qinggan.app.setting:id/display_language_radio_english',parent:0,checkable:true,checked:selected==='en',text:'English'},
    {packageName:'com.qinggan.app.setting',resource:'',parent:mode==='outside'?null:0,checkable:true,checked:mode==='unchecked'?false:selected==='ru',text:'Русский'},
    ...(mode==='duplicate'?[{packageName:'com.qinggan.app.setting',resource:'',parent:0,checkable:true,checked:false,text:'Русский'}]:[]),
  ];
  class Unavailable extends Error {}
  const bindings={copy:{},admittedI18n:admitted?{}:null,Unavailable,SETTINGS:'com.qinggan.app.setting',focusedApplication:()=>true,
    observations:async()=>({}),ui:{call:async()=>{},snapshot:async()=>page==='menu'?[menu]:tree(),tap:async(n:any)=>{taps.push(n.text??'menu');if(n===menu)page='picker';else selected='ru';}},
    uniqueNode:(nodes:any[],predicate:(n:any)=>boolean)=>{const matches=nodes.filter(predicate);assert.equal(matches.length,1);return matches[0];},scroll:async()=>{},open:async()=>{},top:async()=>{},fresh:()=>[],renderLocale:()=>({fields:{effectiveLanguage:'ru',defaultLocale:'ru-RU',resourceLocale:'ru'}})};
  const execute=new Function('bindings',`const {copy,admittedI18n,Unavailable,SETTINGS,focusedApplication,observations,ui,uniqueNode,scroll,open,top,fresh,renderLocale}=bindings;let language='en';${stripTypeScriptTypes(localeProgram)};return setLanguage('ru');`);
  await execute(bindings);return taps;
}
test('actual RU input is guarded before any pointer operation without full raw admission',async()=>assert.rejects(russianInput(false),/admitted agent-i18n-ru runtime/));
test('admitted RU input selects exact observed OEM child and requires fresh checked readback',async()=>assert.deepEqual(await russianInput(true),['menu','Русский']));
test('admission does not permit a Russian label outside OEM group or duplicate child',async()=>{await assert.rejects(russianInput(true,'outside'),/not exposed/);await assert.rejects(russianInput(true,'duplicate'),/ambiguous/);});
test('successful tap without actual checked RU state remains unavailable',async()=>assert.rejects(russianInput(true,'unchecked'),/fresh checked readback/));
