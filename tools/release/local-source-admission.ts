import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { lstatSync, readFileSync, readlinkSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

export const sourceNames = ["product","app","agents","inject","installer","logo","codestyle"];
const sha = (bytes: string | Buffer) => createHash("sha256").update(bytes).digest("hex");
export type SourceFile = { repository:string; path:string; mode:number; kind:"file"|"symlink"|"submodule"; sha256:string };
export type SourceInventory = { schemaVersion:1; repositories:Array<{name:string;head:string}>; files:SourceFile[] };
function git(directory:string, args:string[]):Buffer {
  return execFileSync("/Library/Developer/CommandLineTools/usr/bin/git",["-c","core.fsmonitor=false","-C",directory,...args],{env:{...process.env,GIT_OPTIONAL_LOCKS:"0"},timeout:60000,maxBuffer:64*1024*1024});
}
export function captureSources(workspace:string):SourceInventory {
 const result:SourceInventory={schemaVersion:1,repositories:[],files:[]};
 function scan(repository:string,directory:string,prefix:string) {
  const paths=git(directory,["ls-files","--cached","--others","--exclude-standard","-z"]).toString("utf8").split("\0").filter(Boolean);
  for(const path of [...new Set(paths)].sort()) {
   const full=join(directory,path); let stat;
   try { stat=lstatSync(full); } catch(error:any) { if(error.code==="ENOENT") continue; throw error; }
   const relative=prefix+path;
   if(stat.isSymbolicLink()) result.files.push({repository,path:relative,mode:stat.mode&0o777,kind:"symlink",sha256:sha(readlinkSync(full))});
   else if(stat.isFile()) result.files.push({repository,path:relative,mode:stat.mode&0o777,kind:"file",sha256:sha(readFileSync(full))});
   else if(stat.isDirectory()) {
    const ownRoot=git(full,["rev-parse","--show-toplevel"]).toString().trim();
    if(resolve(ownRoot)!==resolve(full)) {
      const entry=git(directory,["ls-files","--stage","--",path]).toString().trim();
      if(!entry.startsWith("160000 ")) throw new Error("unexpected source directory "+relative);
      result.files.push({repository,path:relative,mode:stat.mode&0o777,kind:"submodule",sha256:sha("uninitialized:"+entry.split(/\s+/)[1])});
      continue;
    }
    const head=git(full,["rev-parse","HEAD"]).toString().trim();
    result.files.push({repository,path:relative,mode:stat.mode&0o777,kind:"submodule",sha256:sha(head)});
    scan(repository,full,relative+"/");
   } else throw new Error("unsupported source entry "+relative);
  }
 }
 for(const name of sourceNames) {
  const directory=join(workspace,name==="product"?"voboost":"voboost-"+name);
  result.repositories.push({name,head:git(directory,["rev-parse","HEAD"]).toString().trim()});
  scan(name,directory,"");
 }
 result.files.sort((a,b)=>(a.repository+"\0"+a.path).localeCompare(b.repository+"\0"+b.path,"en"));
 return result;
}
export type VerificationDelta = { path:string; beforeSha256:string|null; afterSha256:string };
const verificationPaths = new Set(["release/emulator-regression.ts","tools/tests/installed-semantic-subsets.test.ts","tools/tests/emulator-regression-report.test.ts","tools/tests/emulator-font-toggle-home.test.ts","tools/tests/pw-driver141.test.ts","tools/emulator/gate-report.ts","tools/emulator/gate-report.test.ts","tools/emulator/launcher-stock.ts","tools/emulator/launcher-stock.test.ts","tools/emulator/visual-review.ts","tools/emulator/visual-review.test.ts","tools/tests/emulator-launcher-regression.test.ts","tools/tests/launcher-cleanup.test.ts","tools/tests/launcher-checkbox158.test.ts","tools/tests/launcher-grid-target.test.ts","tools/tests/emulator-launcher-toggle-home.test.ts","release/emulator-runtime-evidence.schema.json","tools/tests/full-semantic-schema.test.ts","release/beta1-functional-plan.json","tools/emulator/full-semantic.ts","tools/emulator/installer-ui.test.ts","tools/emulator/installer-ui.ts","tools/tests/vehicle-focus155.test.ts","tools/tests/emulator-gate.test.ts","tools/tests/emulator-regression.test.ts","tools/tests/vehicle-capture-deferred.test.ts","tools/tests/emulator-gate-fixture.ts","tools/emulator/native-session.ts","tools/emulator/native-session.test.ts","tools/emulator/navigation-editor.ts","tools/emulator/navigation-editor.test.ts","tools/tests/full-semantic.test.ts","tools/emulator/host-gates.ts","tools/tests/host-gate-failfast.test.ts","tools/emulator/native-tree.ts","tools/emulator/native-tree.test.ts","release/emulator-gate.ts","tools/release/local-source-admission.ts","tools/release/local-source-admission.test.ts","tools/emulator/launcher-cache.ts","tools/emulator/launcher-cache.test.ts","tools/emulator/launcher-page-observation.ts","tools/tests/launcher-semantic-boundary.test.ts"]);
const verificationCreations = new Set(["tools/emulator/native-crash.ts","tools/emulator/native-crash.test.ts"]);
export function assertSources(before:SourceInventory,after:SourceInventory,delta:VerificationDelta[]=[]):void {
 if(before.schemaVersion!==1||after.schemaVersion!==1||JSON.stringify(before.repositories)!==JSON.stringify(after.repositories)||before.repositories.length!==sourceNames.length||before.repositories.some((r,i)=>r.name!==sourceNames[i])) throw new Error("source repositories differ");
 const approved=new Map<string,VerificationDelta>();
 for(const row of delta) {
  if((row.beforeSha256===null?!verificationCreations.has(row.path):!verificationPaths.has(row.path)||!/^[a-f0-9]{64}$/.test(row.beforeSha256))||approved.has(row.path)||!/^[a-f0-9]{64}$/.test(row.afterSha256)||row.beforeSha256===row.afterSha256) throw new Error("invalid verification delta");
  approved.set(row.path,row);
 }
 const indexed=(files:SourceFile[])=>{
  const result=new Map<string,SourceFile>();
  for(const file of files) {
   const key=JSON.stringify([file.repository,file.path]);
   if(result.has(key)) throw new Error("source file set differs: duplicate entry");
   result.set(key,file);
  }
  return result;
 };
 const previous=indexed(before.files),current=indexed(after.files),consumed=new Set<string>();
 for(const [key,a] of previous) {
  const b=current.get(key);
  if(!b) throw new Error("source file set differs: removed entry");
  if(JSON.stringify(a)===JSON.stringify(b)) continue;
  const row=a.repository==="product"?approved.get(a.path):undefined;
  if(!row||a.kind!=="file"||b.kind!=="file"||a.mode!==b.mode||a.sha256!==row.beforeSha256||b.sha256!==row.afterSha256) throw new Error("source differs: "+a.repository+"/"+a.path);
  consumed.add(a.path);
 }
 for(const [key,b] of current) {
  if(previous.has(key)) continue;
  const row=b.repository==="product"?approved.get(b.path):undefined;
  if(!row||row.beforeSha256!==null||!verificationCreations.has(b.path)||b.kind!=="file"||b.mode!==0o644||b.sha256!==row.afterSha256) throw new Error("source file set differs: unapproved creation");
  consumed.add(b.path);
 }
 if(consumed.size!==approved.size) throw new Error("unused verification delta");
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
 const [action,workspace,output]=process.argv.slice(2);
 if(action!=="capture"||!workspace||!output) throw new Error("usage: local-source-admission.ts capture workspace output");
 writeFileSync(output,JSON.stringify(captureSources(workspace))+"\n",{mode:0o600});
}

export function admitLocalArtifact(workspace:string,closure:string,dmg:string,delta:VerificationDelta[]=[]):unknown {
 const directory=resolve(closure,"..");
 const evidenceBytes=readFileSync(join(directory,"local-build-evidence.json"));
 const evidence=JSON.parse(evidenceBytes.toString("utf8"));
 const inventoryBytes=readFileSync(join(directory,"source-inventory.json"));
 const snapshotBytes=readFileSync(join(directory,"source-snapshot.tsv"));
 if(evidence.schemaVersion!==1||evidence.releaseIdentity!=="1.0.0-beta1"||evidence.authority!=="persistent-local-test-only"||evidence.publication!=="forbidden"||evidence.sourceSnapshot?.finalVerified!==true||evidence.sourceSnapshot.inventorySha256!==sha(inventoryBytes)||evidence.sourceSnapshot.sha256!==sha(snapshotBytes)) throw new Error("local source evidence differs");
 const expected=JSON.parse(inventoryBytes.toString("utf8")) as SourceInventory;
 const rows=snapshotBytes.toString("utf8").split("\n").filter(line=>line.startsWith("repository\t")).map(line=>line.split("\t"));
 if(rows.length!==sourceNames.length||rows.some((r,i)=>r[1]!==sourceNames[i]||r[3]!==expected.repositories[i]?.head)||JSON.stringify(evidence.sourceSnapshot.repositories)!==JSON.stringify(rows.map(r=>({name:r[1],head:r[3],tree:r[4],statusSha256:r[5]})))) throw new Error("local repository evidence differs");
 const actualDmg=readFileSync(dmg);
 if(evidence.package?.sha256!==sha(actualDmg)||evidence.package?.size!==actualDmg.length||evidence.package?.reopened!==true) throw new Error("local package differs");
 for(const [file,key] of [["voboost.json","manifestSha256"],["voboost.json.sig","manifestSignatureSha256"]]) if(sha(readFileSync(join(closure,file)))!==evidence.closure?.[key]) throw new Error("local manifest differs");
 assertSources(expected,captureSources(workspace),delta);
 return {status:"passed",kind:"local-artifact",evidenceSha256:sha(evidenceBytes),sourceInventorySha256:sha(inventoryBytes),verificationDelta:delta};
}
