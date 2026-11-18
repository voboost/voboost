import test from 'node:test';
import {stripTypeScriptTypes} from 'node:module';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../../release/emulator-gate.ts', import.meta.url), 'utf8');
const marker = '      await open(); language = await observedLanguage(); initialLanguage = language;';
const start = source.indexOf(marker, source.indexOf('// Panel navigation and checkbox reads follow'));
const finish = source.indexOf('    } else {', start);
assert.ok(start > 0 && finish > start);
const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
const run = new AsyncFunction('regression', 'open', 'observedLanguage', 'top', 'ui', 'visibleAgentChecks', 'catalog', 'STABLE', 'scroll', 'enabled', `let language, initialLanguage, vehiclePart;${stripTypeScriptTypes(source.slice(start, finish))};return {language,initialLanguage};`);
for (const scenario of ['launcher', 'vehicle', 'settings-radio']) test('production setup inventory: ' + scenario, async () => {
  const calls: string[] = [], enabled = new Map();
  const result = await run(scenario, async () => calls.push('open'), async () => { calls.push('language'); return 'zh'; }, async () => { calls.push('top'); return []; }, { snapshot: async () => { calls.push('snapshot'); return []; } }, () => { calls.push('inventory'); return [['a', true], ['b', false]]; }, [], ['settings', 'a', 'b'], async () => calls.push('scroll'), enabled);
  assert.deepEqual(result, { language: 'zh', initialLanguage: 'zh' });
  if (scenario === 'launcher') { assert.deepEqual(calls, ['open', 'language']); assert.equal(enabled.size, 0); }
  else { assert.deepEqual(calls, ['open', 'language', 'top', 'inventory', 'top']); assert.deepEqual([...enabled], [['a', true], ['b', false]]); }
});
test('actual dispatcher retains full27 and launcher cases use ordinary UI toggle', async () => {
  const execution = source.match(/export const EXECUTION_ORDER\s*=\s*(\[[\s\S]*?\])/)!;
  assert.ok(execution);
  const regression = source.match(/const REGRESSION_ORDER = (\{[^\n]+\}) as const;/)!;
  assert.ok(regression);
  const expression = source.match(/for \(const number of (regression [^\n]+)\) \{/)!;
  assert.ok(expression);
  const select = new Function('regression', 'vehiclePart', 'settingsPart', `const EXECUTION_ORDER=${execution[1]}, REGRESSION_ORDER=${regression[1]};return ${expression[1]};`);
  assert.deepEqual(select(undefined), [1,2,3,4,5,13,14,12,6,7,8,9,19,10,11,18,24,25,15,16,20,21,22,23,27,17,26]);
  assert.deepEqual(select('launcher'), [12,18]);
  assert.deepEqual(select('settings-radio', undefined, 'postboot'), [22,23]);
  assert.deepEqual(select('vehicle', 'pw'), [14]);
  assert.deepEqual(select('vehicle', 'fev-drawer'), [13]);
  assert.deepEqual(select('vehicle', undefined), [13,14]);
  const calls: unknown[] = [];
  const own12Start = source.indexOf('await toggle("launcher-apps", true);', source.indexOf('case 12:'));
  const own12End = source.indexOf('const installCacheBaseline =', own12Start);
  assert.ok(own12Start > source.indexOf('fixtureOemItems = (await launcherGrid(true))', source.indexOf('case 12:')) && own12End > own12Start);
  const own12 = source.slice(own12Start, own12End);
  await new AsyncFunction('toggle',own12)(async (...args: unknown[]) => calls.push(args));
  const own18 = source.match(/await toggle\("launcher-apps", false\);\s*await toggle\("launcher-apps", true\);/)!;
  assert.ok(own18);
  await new AsyncFunction('toggle', own18[0])(async (...args: unknown[]) => calls.push(args));
  assert.deepEqual(calls, [['launcher-apps',true],['launcher-apps',false],['launcher-apps',true]]);
  assert.ok(source.includes('agentToggleReady(before, currentResident, id, want)'));
  assert.ok(source.includes('throw new Error("agent readback or retained tab differs")'));
});
