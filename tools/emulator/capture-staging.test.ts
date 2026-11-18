import test from 'node:test';
import {strict as assert} from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import vm from 'node:vm';

const phone=fs.readFileSync(new URL('./phone-runtime.ts',import.meta.url),'utf8');
const gate=fs.readFileSync(new URL('../../release/emulator-gate.ts',import.meta.url),'utf8');
// Execute the actual staging declarations without driver, transport or guest operations.
function directory(source:string,anchor:string,variable:string,root:string):string {
 const start=source.indexOf(anchor);assert.ok(start>=0,'production capture route missing');
 const declaration=source.slice(start).match(new RegExp('const\\s+'+variable+'\\s*=\\s*([^;]+);'));
 assert.ok(declaration,'production capture directory missing');
 return vm.runInNewContext(declaration[1],{fs,path,join:path.join,randomUUID,c:{voyemuRoot:root}});
}
for(const [name,source,anchor,variable,prefix,created] of [
 ['phone',phone,'export async function main','captureRoot','voboost-phone-',true],
 ['screenshot',gate,'const screenshot = async','source','voboost-capture-',false],
 ['keyboard',gate,'const take = async (): Promise<KeyboardFrame>','source','keyboard-capture-',false],
] as const)test(name+' capture staging obeys the Voyemu new-artifact path contract',()=>{
 const scratch=path.resolve(import.meta.dirname,'../../build/unit-fixtures');fs.mkdirSync(scratch,{recursive:true});
 const root=fs.mkdtempSync(path.join(scratch,'capture-staging-'));
 try{
  const base=path.join(root,'.local/build/tmp');fs.mkdirSync(base,{recursive:true});fs.mkdirSync(path.join(root,'.local/tmp'));
  const first=directory(source,anchor,variable,root),second=directory(source,anchor,variable,root);
  assert.equal(path.dirname(first),base);assert.equal(path.dirname(second),base);assert.notEqual(first,second);
  assert.equal(path.isAbsolute(first),true);assert.equal(path.normalize(first),first);assert.ok(path.basename(first).startsWith(prefix));
  assert.equal(fs.existsSync(first),created);assert.equal(fs.existsSync(second),created);
  if(created){assert.equal(fs.statSync(first).mode&0o777,0o700);assert.equal(fs.existsSync(path.join(first,'contacts')),false);assert.deepEqual(fs.readdirSync(first),[]);}
 }finally{fs.rmSync(root,{recursive:true,force:true});}
});
