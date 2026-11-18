import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { CASES, EXECUTION_ORDER } from "../../release/emulator-gate.ts";

const numbers = Array.from({ length: 27 }, (_, index) => index + 1);
const expected = CASES.map(({ number, id }) => ({ number, id }));

function checklistCases(markdown: string) {
  const section = markdown.split(/^## Исполняемые кейсы\s*$/m)[1]?.split(/^## /m)[0];
  assert.ok(section, "checklist must contain the executable cases section");
  return section.split("\n").filter(line => /^\|\s*\d+\s*\|/.test(line)).map(line => {
    const cells = line.split("|").slice(1, -1).map(cell => cell.trim());
    assert.ok(cells.length >= 3 && cells[2], "each case needs an observable check");
    return { number: Number(cells[0]), id: cells[1].replace(/^`|`$/g, "") };
  });
}

function validateChecklist(markdown: string) {
  assert.deepEqual(checklistCases(markdown), expected, "checklist IDs and order must match the executable plan exactly");
}

test("repository checklist, required plan and executable handlers cover the same 27 cases", () => {
  assert.deepEqual(CASES.map(row => row.number), numbers);
  assert.ok(CASES.every(row => row.required === true), "beta1 cases cannot silently become optional");
  assert.equal(new Set(CASES.map(row => row.id)).size, numbers.length);
  assert.deepEqual([...EXECUTION_ORDER].sort((a, b) => a - b), numbers);
  validateChecklist(readFileSync(new URL("../../release/BETA1-CHECKLIST.md", import.meta.url), "utf8"));
  const source = readFileSync(new URL("../../release/emulator-gate.ts", import.meta.url), "utf8");
  const program = source.split("const caseProgram = async (number: number) => {")[1]?.split("throw new Error(\"unknown case ID\")")[0];
  assert.ok(program, "closed executable case dispatcher must exist");
  const handlers = [...program.matchAll(/^\s*case (\d+):/gm)].map(match => Number(match[1]));
  assert.deepEqual(handlers.sort((a, b) => a - b), numbers, "every required case needs an executable handler");
});

test("checklist consistency rejects missing, duplicated and foreign cases", () => {
  const rows = expected.map(row => `| ${row.number} | \`${row.id}\` | Observable result |`);
  const render = (items: string[]) => "## Исполняемые кейсы\n\n" + items.join("\n") + "\n";
  assert.doesNotThrow(() => validateChecklist(render(rows)));
  assert.throws(() => validateChecklist(render(rows.slice(1))));
  assert.throws(() => validateChecklist(render([...rows, rows[0]])));
  assert.throws(() => validateChecklist(render(rows.map((row, index) => index === 0 ? row.replace("cli-install-first", "foreign-case") : row))));
  assert.throws(() => validateChecklist(render([...rows].reverse())));
});

