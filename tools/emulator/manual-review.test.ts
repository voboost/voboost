import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, createPublicKey, verify } from "node:crypto";
import { mkdirSync, mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
const projectWork = fileURLToPath(new URL("../../build/manual-review-tests/", import.meta.url));
import { hash, sessionPublicKey, signAssessments, waitReceipt } from "./manual-review.ts";
const encode=(v:unknown)=>Buffer.from(JSON.stringify(v)+"\n");
test("manual review signs only explicit complete assessments with independent session key",()=>{
 const base=projectWork; mkdirSync(base,{recursive:true});
 const dir=mkdtempSync(join(base,"manual-review-"));
 try {
  const release=generateKeyPairSync("ed25519"),session=generateKeyPairSync("ed25519");
  const pem=(key:any)=>key.export({type:"spki",format:"pem"}).toString();
  const secret=(key:any)=>key.export({type:"pkcs8",format:"pem"}).toString();
  writeFileSync(join(dir,"product.pem"),pem(release.publicKey));
  assert.throws(()=>sessionPublicKey(pem(release.publicKey),dir),/release authority/);
  const publicKey=sessionPublicKey(pem(session.publicKey),dir);
  const request:any={publicKeySha256:hash(createPublicKey(publicKey).export({type:"spki",format:"der"})),targets:[{id:"visual",criteria:[{id:"size",expected:"native visible size"}]}]};
  const assessment={reviewer:"root",assessments:[{id:"visual",criteria:[{id:"size",expected:"native visible size",actual:"Observed smaller than native reference",verdict:"failed"}]}]};
  const receipt=JSON.parse(signAssessments(request,assessment,secret(session.privateKey),dir).toString());
  assert.equal(receipt.payload.assessments[0].criteria[0].verdict,"failed");
  assert.ok(verify(null,encode(receipt.payload),session.publicKey,Buffer.from(receipt.signature,"hex")));
  assert.equal(receipt.payload.requestSha256,hash(encode(request)));
  assert.throws(()=>signAssessments(request,{reviewer:"root",assessments:[]},secret(session.privateKey),dir),/complete/);
  const missing=structuredClone(assessment); delete (missing.assessments[0].criteria[0] as any).actual;
  assert.throws(()=>signAssessments(request,missing,secret(session.privateKey),dir),/explicit observation/);
  assert.throws(()=>signAssessments(request,assessment,secret(release.privateKey),dir),/release authority/);
 } finally {rmSync(dir,{recursive:true,force:true});}
});
test("receipt wait times out asynchronously without acceptance",async()=>{
 let now=0,calls=0;
 assert.equal(await waitReceipt(join(projectWork,"absent-review-receipt-test"),20,()=>now,async(ms)=>{calls++;now+=Number(ms);}),null);
 assert.ok(calls>0);
 await assert.rejects(waitReceipt("relative",20),/absolute/);
 await assert.rejects(waitReceipt("/absolute",7200001),/bounded/);
});
