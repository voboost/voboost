import { deepStrictEqual, throws } from 'node:assert';
import { appendFileSync, mkdirSync, mkdtempSync, readFileSync, readSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';
import { parseQnxResourceArtifacts, stageQnxResourceArtifacts, readBoundedQnxResource } from './qnx-resource-artifacts.ts';
const root=join(import.meta.dirname,'../../../.work162/qnx-resource-stage-tests');
const pair={pack:'qnx-resource-i18n-ru-a9-gauge.kzb',ledger:'qnx-resource-i18n-ru-a9-gauge.json'};
const index={schemaVersion:1,owner:'i18n-ru',artifacts:[pair]};
const catalog=Buffer.from(JSON.stringify({agents:[{id:'i18n-ru',targets:[{platform:'qnx'}]}]}));
const bytes=(value: unknown)=>Buffer.from(JSON.stringify(value));
test('closed QNX index requires selected native owner, exact pairs and nonempty inventory',()=>{
  deepStrictEqual(parseQnxResourceArtifacts(bytes(index),catalog),[pair]);
  for(const bad of [{...index,artifacts:[]},{...index,extra:true},{...index,artifacts:[pair,pair]},{...index,artifacts:[{...pair,pack:'../bad.kzb'}]},{...index,artifacts:[{...pair,ledger:'other.json'}]}])throws(()=>parseQnxResourceArtifacts(bytes(bad),catalog));
  throws(()=>parseQnxResourceArtifacts(bytes(index),bytes({agents:[{id:'i18n-ru',targets:[{platform:'android'}]}]})));
});
test('staging preserves exact declared bytes and rejects unknown files, links and duplicate destinations',()=>{
  mkdirSync(root,{recursive:true});const work=mkdtempSync(join(root,'case-')),source=join(work,'source'),target=join(work,'closure');mkdirSync(source);mkdirSync(target);
  try{
    writeFileSync(join(source,'qnx-resource-artifacts.json'),bytes(index));writeFileSync(join(source,pair.pack),'pack');writeFileSync(join(source,pair.ledger),'ledger');
    deepStrictEqual(stageQnxResourceArtifacts(source,target,catalog),[pair.pack,pair.ledger]);deepStrictEqual(readFileSync(join(target,pair.pack)),Buffer.from('pack'));
    throws(()=>stageQnxResourceArtifacts(source,target,catalog));writeFileSync(join(source,'foreign'),'bad');throws(()=>stageQnxResourceArtifacts(source,target,catalog));rmSync(join(source,'foreign'));
    rmSync(join(source,pair.pack));symlinkSync(join(target,pair.pack),join(source,pair.pack));throws(()=>stageQnxResourceArtifacts(source,target,catalog));
  }finally{rmSync(work,{recursive:true,force:true});}
});

test('bounded reader rejects growth after fstat without requesting bytes beyond initial bound',()=>{
  mkdirSync(root,{recursive:true});const work=mkdtempSync(join(root,'growth-')),file=join(work,'pack');
  try{
    writeFileSync(file,'pack');let grown=false;const requests:number[]=[];
    throws(()=>readBoundedQnxResource(file,4,(fd,buffer,offset,length,position)=>{
      requests.push(length);if(!grown){grown=true;appendFileSync(file,Buffer.alloc(8192));}
      return readSync(fd,buffer,offset,length,position);
    }),/grew while reading/);
    deepStrictEqual(requests,[4,1]);
  }finally{rmSync(work,{recursive:true,force:true});}
});
test('bounded reader handles short reads and rejects truncation before exposing bytes',()=>{
  mkdirSync(root,{recursive:true});const work=mkdtempSync(join(root,'short-')),file=join(work,'pack');
  try{
    writeFileSync(file,'pack');deepStrictEqual(readBoundedQnxResource(file,4,(fd,buffer,offset,length,position)=>readSync(fd,buffer,offset,Math.min(length,1),position)),Buffer.from('pack'));
    let truncated=false;throws(()=>readBoundedQnxResource(file,4,(fd,buffer,offset,length,position)=>{if(!truncated){truncated=true;writeFileSync(file,'p');}return readSync(fd,buffer,offset,length,position);}),/changed while reading/);
  }finally{rmSync(work,{recursive:true,force:true});}
});
