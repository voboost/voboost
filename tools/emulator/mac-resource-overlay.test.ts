import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, copyFileSync, writeFileSync, readdirSync, rmSync, chmodSync, unlinkSync, symlinkSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { macDeviceResourceInventory, macResourceOverlaySourceIdentity } from './mac-resource-overlay.ts';
import { MAC_LICENSE_NAMES } from '../../../voboost-installer/src/build/readable-licenses.ts';
const source=resolve(import.meta.dirname,'../../../voboost-installer/src-tauri/resources/licenses');
const scratch=resolve(import.meta.dirname,'../../../fixture-work');
mkdirSync(scratch,{recursive:true});
function fixture(run:(dir:string)=>void) {const dir=mkdtempSync(join(scratch,'licenses-'));try{for(const name of MAC_LICENSE_NAMES){copyFileSync(join(source,name),join(dir,name));chmodSync(join(dir,name),0o644);}writeFileSync(join(dir,'voboost.json'),'device closure');run(dir);}finally{rmSync(dir,{recursive:true,force:true});}}
const rows=(dir:string)=>readdirSync(dir).sort().map(path=>({path}));
const admit=(dir:string)=>macDeviceResourceInventory(dir,rows(dir));
test('actual declared Mac overlay leaves exact device closure',()=>fixture(dir=>assert.deepEqual(admit(dir),[{path:'voboost.json'}])));
for(const kind of ['missing','tampered','symlink','executable','writable','setuid'] as const) test(`Mac overlay rejects ${kind} declared license`,()=>fixture(dir=>{const p=join(dir,MAC_LICENSE_NAMES[0]);if(kind==='missing')unlinkSync(p);if(kind==='tampered')writeFileSync(p,'foreign');if(kind==='symlink'){unlinkSync(p);symlinkSync(join(source,MAC_LICENSE_NAMES[0]),p);}if(kind==='executable')chmodSync(p,0o755);if(kind==='writable')chmodSync(p,0o666);if(kind==='setuid')chmodSync(p,0o4644);assert.throws(()=>admit(dir));}));
test('foreign text, nested declared name and undeclared Windows notice remain closure mismatches',()=>fixture(dir=>{for(const name of ['foreign.txt','installer-windows-rust-NOTICES.txt'])writeFileSync(join(dir,name),'foreign');mkdirSync(join(dir,'nested'));writeFileSync(join(dir,'nested',MAC_LICENSE_NAMES[0]),'foreign');assert.notDeepEqual(admit(dir),[{path:'voboost.json'}]);assert.ok(admit(dir).some(r=>r.path==='foreign.txt'));assert.ok(admit(dir).some(r=>r.path==='installer-windows-rust-NOTICES.txt'));}));

test("overlay source identity binds owning input bytes",()=>{const file=join(source,MAC_LICENSE_NAMES[0]);const initial=macResourceOverlaySourceIdentity();const bytes=readFileSync(file);try{writeFileSync(file,Buffer.concat([bytes,Buffer.from("changed")]));assert.notEqual(macResourceOverlaySourceIdentity(),initial);}finally{writeFileSync(file,bytes);}assert.equal(macResourceOverlaySourceIdentity(),initial);});
