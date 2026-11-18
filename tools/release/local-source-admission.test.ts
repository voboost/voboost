import test from "node:test";
import assert from "node:assert/strict";
import { assertSources, sourceNames, type SourceInventory } from "./local-source-admission.ts";
const a="a".repeat(64),b="b".repeat(64);
const baseline=():SourceInventory=>({schemaVersion:1,repositories:sourceNames.map(name=>({name,head:"c".repeat(40)})),files:[{repository:"product",path:"tools/emulator/native-tree.ts",mode:420,kind:"file",sha256:a},{repository:"agents",path:"src/Agent.java",mode:420,kind:"file",sha256:a}]});
test("exact seven source inventory accepted",()=>assert.doesNotThrow(()=>assertSources(baseline(),baseline())));
test("reviewed verifier-only change accepted",()=>{const x=baseline(),y=baseline();y.files[0].sha256=b;assert.doesNotThrow(()=>assertSources(x,y,[{path:x.files[0].path,beforeSha256:a,afterSha256:b}]));});
test("production change rejected even with verifier delta",()=>{const x=baseline(),y=baseline();y.files[0].sha256=b;y.files[1].sha256=b;assert.throws(()=>assertSources(x,y,[{path:x.files[0].path,beforeSha256:a,afterSha256:b}]));});
test("unreviewed paths and dishonest before hash rejected",()=>{const x=baseline(),y=baseline();y.files[0].sha256=b;assert.throws(()=>assertSources(x,y,[{path:x.files[0].path,beforeSha256:b,afterSha256:b}]));assert.throws(()=>assertSources(x,y,[{path:"tools/emulator/provider.ts",beforeSha256:a,afterSha256:b}]));});
test("codestyle source HEAD drift rejected",()=>{const x=baseline(),y=baseline();y.repositories[sourceNames.indexOf("codestyle")].head="d".repeat(40);assert.throws(()=>assertSources(x,y));});
test("nested source drift and added sources rejected",()=>{const x=baseline(),y=baseline();y.files.push({repository:"inject",path:"vendor/child/src.c",mode:420,kind:"file",sha256:b});assert.throws(()=>assertSources(x,y));});
test("verifier type/mode drift rejected",()=>{const x=baseline(),y=baseline();y.files[0].sha256=b;y.files[0].mode=493;assert.throws(()=>assertSources(x,y,[{path:x.files[0].path,beforeSha256:a,afterSha256:b}]));});
test("missing seventh source rejected",()=>{const x=baseline();x.repositories.pop();assert.throws(()=>assertSources(x,x));});

for(const path of ["release/emulator-regression.ts","tools/tests/installed-semantic-subsets.test.ts","tools/tests/emulator-regression-report.test.ts","tools/tests/launcher-checkbox158.test.ts","tools/tests/launcher-grid-target.test.ts","tools/tests/emulator-font-toggle-home.test.ts","tools/emulator/gate-report.ts","tools/emulator/gate-report.test.ts","tools/emulator/launcher-stock.ts","tools/emulator/launcher-stock.test.ts","tools/emulator/navigation-editor.ts","tools/emulator/navigation-editor.test.ts","tools/emulator/visual-review.ts","tools/emulator/visual-review.test.ts"]) {
 test(`existing host verifier change accepted: ${path}`,()=>{const x=baseline(),y=baseline();x.files[0].path=path;y.files[0].path=path;y.files[0].sha256=b;assert.doesNotThrow(()=>assertSources(x,y,[{path,beforeSha256:a,afterSha256:b}]));});
 test(`new host verifier file rejected: ${path}`,()=>{const x=baseline(),y=baseline();y.files.push({repository:"product",path,mode:420,kind:"file",sha256:b});assert.throws(()=>assertSources(x,y,[{path,beforeSha256:a,afterSha256:b}]),/source file set differs/);});
 test(`host verifier path cannot authorize another repository: ${path}`,()=>{const x=baseline(),y=baseline();x.files[0].repository="agents";y.files[0].repository="agents";x.files[0].path=path;y.files[0].path=path;y.files[0].sha256=b;assert.throws(()=>assertSources(x,y,[{path,beforeSha256:a,afterSha256:b}]),/source differs/);});
}
test("navigation verifier delta cannot authorize production runtime",()=>{const x=baseline(),y=baseline();x.files[0].path="src/navigation-editor.ts";y.files[0].path=x.files[0].path;y.files[0].sha256=b;assert.throws(()=>assertSources(x,y,[{path:x.files[0].path,beforeSha256:a,afterSha256:b}]),/invalid verification delta/);});
test("unknown new verifier path remains rejected",()=>{const x=baseline(),y=baseline();y.files[0].path="tools/emulator/navigation-editor-new.ts";y.files[0].sha256=b;assert.throws(()=>assertSources(x,y,[{path:y.files[0].path,beforeSha256:a,afterSha256:b}]),/invalid verification delta/);});

const creations=["tools/emulator/native-crash.ts","tools/emulator/native-crash.test.ts"];
for(const path of creations) {
 const delta=()=>({path,beforeSha256:null,afterSha256:b});
 const created=()=>({repository:"product",path,mode:0o644,kind:"file" as const,sha256:b});
 test(`exact creation admitted: ${path}`,()=>{const x=baseline(),y=baseline();y.files.unshift(created());assert.doesNotThrow(()=>assertSources(x,y,[delta()]));});
 test(`foreign repository creation rejected: ${path}`,()=>{const x=baseline(),y=baseline();y.files.push({...created(),repository:"agents"});assert.throws(()=>assertSources(x,y,[delta()]),/source file set differs/);});
 for(const mode of [0o600,0o644|0o111,0o664]) test(`wrong creation mode ${mode} rejected: ${path}`,()=>{const x=baseline(),y=baseline();y.files.push({...created(),mode});assert.throws(()=>assertSources(x,y,[delta()]));});
 for(const kind of ["symlink","submodule"] as const) test(`wrong creation kind ${kind} rejected: ${path}`,()=>{const x=baseline(),y=baseline();y.files.push({...created(),kind});assert.throws(()=>assertSources(x,y,[delta()]));});
 test(`creation hash mismatch rejected: ${path}`,()=>{const x=baseline(),y=baseline();y.files.push({...created(),sha256:a});assert.throws(()=>assertSources(x,y,[delta()]));});
 test(`creation without delta rejected: ${path}`,()=>{const x=baseline(),y=baseline();y.files.push(created());assert.throws(()=>assertSources(x,y));});
 test(`creation with nonnull before rejected: ${path}`,()=>{const x=baseline(),y=baseline();y.files.push(created());assert.throws(()=>assertSources(x,y,[{...delta(),beforeSha256:a}]),/invalid verification delta/);});
 test(`creation null cannot hide preexisting file change: ${path}`,()=>{const x=baseline(),y=baseline();x.files.push({...created(),sha256:a});y.files.push(created());assert.throws(()=>assertSources(x,y,[delta()]));});
 test(`creation delta unused or duplicate rejected: ${path}`,()=>{assert.throws(()=>assertSources(baseline(),baseline(),[delta()]),/unused/);const x=baseline(),y=baseline();y.files.push(created());assert.throws(()=>assertSources(x,y,[delta(),delta()]),/invalid/);});
 test(`creation removal rejected: ${path}`,()=>{const x=baseline(),y=baseline();x.files.push(created());assert.throws(()=>assertSources(x,y,[delta()]),/source file set differs/);});
}
test("both exact new files coexist with existing verifier hash delta",()=>{const x=baseline(),y=baseline();y.files[0].sha256=b;for(const path of creations)y.files.unshift({repository:"product",path,mode:0o644,kind:"file",sha256:b});assert.doesNotThrow(()=>assertSources(x,y,[{path:x.files[0].path,beforeSha256:a,afterSha256:b},...creations.map(path=>({path,beforeSha256:null,afterSha256:b}))]));});
test("map comparison permits ordering changes without accepting modifications",()=>{const x=baseline(),y=baseline();y.files.reverse();assert.doesNotThrow(()=>assertSources(x,y));y.files[0].sha256=b;assert.throws(()=>assertSources(x,y),/source differs/);});
test("duplicate before and after inventory entries rejected",()=>{const x=baseline(),y=baseline();x.files.push({...x.files[0]});assert.throws(()=>assertSources(x,y),/duplicate/);assert.throws(()=>assertSources(y,x),/duplicate/);});
test("null before disallowed for old allowed verifier and unknown new path",()=>{for(const path of ["release/emulator-gate.ts","tools/emulator/new-crash.ts","src/native-crash.ts"]){const x=baseline(),y=baseline();y.files.push({repository:"product",path,mode:0o644,kind:"file",sha256:b});assert.throws(()=>assertSources(x,y,[{path,beforeSha256:null,afterSha256:b}]),/invalid verification delta/);}});
test("malformed creation after hash rejected",()=>{const x=baseline(),y=baseline();y.files.push({repository:"product",path:creations[0],mode:0o644,kind:"file",sha256:b});assert.throws(()=>assertSources(x,y,[{path:creations[0],beforeSha256:null,afterSha256:"not-a-hash"}]),/invalid/);});


test("current source inventory excludes the archived license repository", () => {
 assert.deepEqual(sourceNames, ["product", "app", "agents", "inject", "installer", "logo", "codestyle"]);
 const old = baseline();
 old.repositories.splice(5, 0, {name:"license",head:"c".repeat(40)});
 assert.throws(() => assertSources(old, old), /source repositories differ/);
});
