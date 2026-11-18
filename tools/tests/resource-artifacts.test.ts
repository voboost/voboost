import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync, readSync, fstatSync, statSync, appendFileSync, readdirSync, copyFileSync, symlinkSync, rmSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseResourceArtifacts, stageResourceArtifacts, readBoundedResource } from '../release/resource-artifacts.ts';
const pair = { apk: 'resource-i18n-ru-setting-a9.apk', ledger: 'resource-i18n-ru-setting-a9.json' };
const catalog = Buffer.from(JSON.stringify({ agents: [{ id: 'i18n-ru' }] }));
function index(artifacts: unknown = [pair]) { return { schemaVersion: 1, owner: 'i18n-ru', artifacts }; }
function parse(value: unknown, cat = catalog) { return parseResourceArtifacts(Buffer.from(JSON.stringify(value)), cat); }
test('owned declared pair accepted', () => assert.deepEqual(parse(index()), [pair]));
test('unknown/missing/executor owner rejected', () => {
  for (const agents of [[], [{id:'i18n-ru',kind:'executor'}], [{id:'i18n-ru'},{id:'i18n-ru'}]]) assert.throws(() => parse(index(), Buffer.from(JSON.stringify({agents}))));
  assert.throws(() => parse({...index(),owner:'launcher'}));
});
test('unbounded, unknown and incomplete index rejected', () => { for (const v of [{...index(),schemaVersion:2},{...index(),extra:true},index([]),index(Array(65).fill(pair)),index(null)]) assert.throws(() => parse(v)); });
test('paths, unmatched ledger and duplicate pair rejected', () => {
  for (const p of [{...pair,apk:'../evil.apk'},{...pair,apk:'/evil.apk'},{...pair,apk:'resource-i18n-ru-X.apk'},{...pair,ledger:'resource-i18n-ru-other.json'},{...pair,extra:true}]) assert.throws(() => parse(index([p])));
  assert.throws(() => parse(index([pair,pair])));
});
function fixture() {
  const base = fileURLToPath(new URL('../../../.build-cache/i18n-en-native-20261103/product-resource-tests/', import.meta.url));
  mkdirSync(base, { recursive: true });
  const root = mkdtempSync(join(base, 'resource-artifacts-')); const bundle=join(root,'bundle'), closure=join(root,'closure');mkdirSync(bundle);mkdirSync(closure);
  writeFileSync(join(bundle,'agents.json'), catalog);writeFileSync(join(bundle,'resource-artifacts.json'),JSON.stringify(index()));
  writeFileSync(join(bundle,pair.apk),'unsigned fixture');writeFileSync(join(bundle,pair.ledger),'ledger fixture');return {root,bundle,closure};
}
test('stage signs only declared APK and copies paired ledger',async()=>{const f=fixture();try{writeFileSync(join(f.bundle,'resource-i18n-ru-unlisted.apk'),'must not ship');const calls:string[]=[];const out=await stageResourceArtifacts(f.bundle,f.closure,async(a,b)=>{calls.push(a);copyFileSync(a,b)});assert.equal(calls.length,1);assert.deepEqual(out,[pair.apk,pair.ledger]);assert.equal(readFileSync(join(f.closure,pair.ledger),'utf8'),'ledger fixture');assert.equal(existsSync(join(f.closure,'resource-i18n-ru-unlisted.apk')),false);}finally{rmSync(f.root,{recursive:true,force:true})}});
test('missing declaration stages nothing',async()=>{const f=fixture();try{rmSync(join(f.bundle,'resource-artifacts.json'));assert.deepEqual(await stageResourceArtifacts(f.bundle,f.closure,async()=>assert.fail('must not sign')),[]);}finally{rmSync(f.root,{recursive:true,force:true})}});
test('symlink APK rejected before signer',async()=>{const f=fixture();try{rmSync(join(f.bundle,pair.apk));symlinkSync(join(f.bundle,pair.ledger),join(f.bundle,pair.apk));await assert.rejects(stageResourceArtifacts(f.bundle,f.closure,async()=>assert.fail('must not sign')));}finally{rmSync(f.root,{recursive:true,force:true})}});
test('missing or existing destination rejects before signer',async()=>{for(const mode of ['missing','existing']){const f=fixture();try{if(mode==='missing')rmSync(join(f.bundle,pair.ledger));else writeFileSync(join(f.closure,pair.apk),'old');await assert.rejects(stageResourceArtifacts(f.bundle,f.closure,async()=>assert.fail('must not sign')));}finally{rmSync(f.root,{recursive:true,force:true})}}});

test('bounded reader rejects concurrent growth without reading beyond initial allocation plus one byte', () => {
  const f=fixture(), file=join(f.bundle,pair.apk); let observed=-1;
  try {
    writeFileSync(file,'pack'); const requests:number[]=[];
    assert.throws(()=>readBoundedResource(file,4,(fd,buffer,offset,length,position)=>{
      observed=fd; requests.push(length);
      if(requests.length===1)appendFileSync(file,Buffer.alloc(8192));
      return readSync(fd,buffer,offset,length,position);
    }),/grew while reading/);
    assert.deepEqual(requests,[4,1]);
    assert.throws(()=>fstatSync(observed),{code:'EBADF'});
  } finally { rmSync(f.root,{recursive:true,force:true}); }
});

test('bounded reader loops short reads, rejects truncation and closes on success and reader failure', () => {
  const f=fixture(),file=join(f.bundle,pair.apk); let observed=-1;
  try {
    writeFileSync(file,'pack');
    assert.equal(readBoundedResource(file,4,(fd,buffer,offset,length,position)=>{
      observed=fd; return readSync(fd,buffer,offset,Math.min(length,1),position);
    }).toString(),'pack');
    assert.throws(()=>fstatSync(observed),{code:'EBADF'});
    let truncated=false;
    assert.throws(()=>readBoundedResource(file,4,(fd,buffer,offset,length,position)=>{
      observed=fd;if(!truncated){truncated=true;writeFileSync(file,'p');}
      return readSync(fd,buffer,offset,length,position);
    }),/changed while reading/);
    assert.throws(()=>fstatSync(observed),{code:'EBADF'});
    assert.throws(()=>readBoundedResource(file,4,(fd)=>{observed=fd;throw Error('injected read failure');}),/injected read failure/);
    assert.throws(()=>fstatSync(observed),{code:'EBADF'});
  } finally { rmSync(f.root,{recursive:true,force:true}); }
});

test('staging signs captured APK and writes captured ledger after original paths change',async()=>{
  const f=fixture();let temporary='';
  try {
    await stageResourceArtifacts(f.bundle,f.closure,async(input,output)=>{
      temporary=input;
      assert.ok(input.startsWith(join(f.closure,'.resource-sign-')));
      assert.equal(statSync(input).mode&0o777,0o600);
      writeFileSync(join(f.bundle,pair.apk),'changed APK after capture');
      writeFileSync(join(f.bundle,pair.ledger),'changed ledger after capture');
      assert.equal(readFileSync(input,'utf8'),'unsigned fixture');
      copyFileSync(input,output);
    });
    assert.equal(readFileSync(join(f.closure,pair.apk),'utf8'),'unsigned fixture');
    assert.equal(readFileSync(join(f.closure,pair.ledger),'utf8'),'ledger fixture');
    assert.equal(existsSync(temporary),false);
    assert.deepEqual(readdirSync(f.closure).sort(),[pair.apk,pair.ledger].sort());
  } finally { rmSync(f.root,{recursive:true,force:true}); }
});

test('signer failure cleans private captured input and ledger remains exclusive across await',async()=>{
  for(const mode of ['signer failure','ledger race']){
    const f=fixture();let temporary='';
    try {
      await assert.rejects(stageResourceArtifacts(f.bundle,f.closure,async(input,output)=>{
        temporary=input;
        if(mode==='signer failure')throw Error('injected signer failure');
        writeFileSync(join(f.closure,pair.ledger),'foreign ledger');copyFileSync(input,output);
      }),mode==='signer failure'?/injected signer failure/:{code:'EEXIST'});
      assert.equal(existsSync(temporary),false);
      assert.ok(!readdirSync(f.closure).some(name=>name.startsWith('.resource-sign-')));
      if(mode==='ledger race')assert.equal(readFileSync(join(f.closure,pair.ledger),'utf8'),'foreign ledger');
    } finally { rmSync(f.root,{recursive:true,force:true}); }
  }
});

const englishPair = { apk: 'resource-i18n-en-setting-a11.apk', ledger: 'resource-i18n-en-setting-a11.json' };
test('schema 2 admits independent RU and EN pairs with exact catalog owners', () => {
  const both = Buffer.from(JSON.stringify({agents:[{id:'i18n-ru'},{id:'i18n-en'}]}));
  const entries = [{owner:'i18n-ru',...pair},{owner:'i18n-en',...englishPair}];
  assert.deepEqual(parse({schemaVersion:2,artifacts:entries},both),[pair,englishPair]);
  assert.deepEqual(parse({schemaVersion:2,artifacts:[entries[1]]},Buffer.from(JSON.stringify({agents:[{id:'i18n-en'}]}))),[englishPair]);
  for (const entry of [{owner:'i18n-ru',...englishPair},{owner:'i18n-en',...pair},{owner:'launcher',...pair},{...englishPair}])
    assert.throws(()=>parse({schemaVersion:2,artifacts:[entry]},both));
  assert.throws(()=>parse({schemaVersion:2,artifacts:entries},catalog));
  assert.throws(()=>parse({schemaVersion:2,artifacts:[entries[1],entries[1]]},both));
});
test('schema 2 rejects EN profiles with no native correction delta', () => {
  const cat = Buffer.from(JSON.stringify({agents:[{id:'i18n-en'}]}));
  for (const profile of ['setting-a9', 'systemui-a11', 'vehiclesetting-a11']) {
    const stem = 'resource-i18n-en-' + profile;
    assert.throws(() => parse({schemaVersion:2,artifacts:[{owner:'i18n-en',apk:stem+'.apk',ledger:stem+'.json'}]},cat));
  }
});
test('schema 2 stages both independent agents and preserves pair identities', async () => {
  const f=fixture();
  try {
    writeFileSync(join(f.bundle,'agents.json'),JSON.stringify({agents:[{id:'i18n-ru'},{id:'i18n-en'}]}));
    writeFileSync(join(f.bundle,'resource-artifacts.json'),JSON.stringify({schemaVersion:2,artifacts:[{owner:'i18n-ru',...pair},{owner:'i18n-en',...englishPair}]}));
    writeFileSync(join(f.bundle,englishPair.apk),'English fixture');writeFileSync(join(f.bundle,englishPair.ledger),'English ledger');
    const calls:string[]=[];
    assert.deepEqual(await stageResourceArtifacts(f.bundle,f.closure,async(a,b)=>{calls.push(a);copyFileSync(a,b)}),[pair.apk,pair.ledger,englishPair.apk,englishPair.ledger]);
    assert.equal(calls.length,2);
    assert.equal(readFileSync(join(f.closure,englishPair.ledger),'utf8'),'English ledger');
  } finally { rmSync(f.root,{recursive:true,force:true}); }
});
