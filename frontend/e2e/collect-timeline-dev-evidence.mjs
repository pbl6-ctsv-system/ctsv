import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';

const root=process.cwd(),home=path.resolve(process.argv[2]??'target/pre-p10-extended-20261121');
const read=async p=>JSON.parse((await fs.readFile(p,'utf8')).replace(/^\uFEFF/,''));
const main=await read(path.join(home,'run-02/report.json')),admin=await read(path.join(home,'admin/report.json'));
const fixture=await read(path.join(home,'run-02/fixture.json'));
const expected=[...Array.from({length:5},(_,i)=>'A'+String(i+1).padStart(2,'0')),...Array.from({length:10},(_,i)=>'B'+String(i+1).padStart(2,'0')),...Array.from({length:7},(_,i)=>'C'+String(i+1).padStart(2,'0')),'D01','D02'];
const cases=[...main.cases,...admin.cases].filter(c=>expected.includes(c.id));
assert.equal(cases.length,24);assert.equal(new Set(cases.map(c=>c.id)).size,24);
for(const id of expected)assert.equal(cases.find(c=>c.id===id)?.status,'PASS',id+' lacks executed PASS evidence');
for(const r of [main,admin]){assert.equal(r.database,'ctsv_dev');assert.equal(r.origin,'https://127.0.0.1:5173');assert.deepEqual(r.pageErrors,[]);}
const before=await read(path.join(home,'run-02/protected-before.json')),after=await read(path.join(home,'run-02/protected-after.json'));assert.deepEqual(after,before);
const baseline=await read(path.join(home,'run-02/baseline-source-comparison.json'));assert.equal(baseline.length,35);assert.ok(baseline.every(x=>x.unchanged));
const traceScan=await read(path.join(home,'run-02/trace-scan.json'));assert.equal(traceScan.unredactedJwtCount,0);assert.equal(traceScan.uncaughtPageErrorEvents,0);assert.equal(traceScan.invalidCookieObjects,0);
const sql=await read(path.join(home,'run-02/sql-snapshot.json'));const audit=sql.filter(x=>x.auditId);assert.ok(audit.length>0);assert.ok(audit.every(x=>x.actorId===x.auditActorId&&x.reason===x.auditReason));
assert.equal(main.pdf.sha256,main.pdf.downloadSha256);
const files=['timeline-extended-dev.mjs','timeline-dev-db.ps1','run-timeline-extended-dev.ps1','sanitize-timeline-trace.mjs','collect-timeline-dev-evidence.mjs'];
const code=[];for(const f of files)code.push({file:'frontend/e2e/'+f,sha256:createHash('sha256').update(await fs.readFile(path.join(root,'frontend/e2e',f))).digest('hex')});
const artifacts=[];for(const directory of ['run-02','admin'])for(const f of await fs.readdir(path.join(home,directory)))if(/\.png$|-sanitized-trace\.zip$/.test(f))artifacts.push(path.relative(root,path.join(home,directory,f)).replaceAll('\\','/'));
const manifest={task:'PRE-P10-01 EXTENDED DEV E2E',date:'2026-11-21',technical:'PASS',G8:'Existing OWNER WAIVER unchanged; no independent test claimed',database:'ctsv_dev',environment:main.environment,
 counts:{functional:{pass:24,fail:0,skip:0,blocked:0},setupAssertions:1,historicalFailedScriptAttempts:main.attempts.length},cases:cases.sort((a,b)=>a.id.localeCompare(b.id)),
 services:{frontend:'REAL Chrome + React HTTPS',backend:'REAL daily executable Spring Boot',database:'REAL SQL Server ctsv_dev V17',storage:'REAL Cloudinary authenticated for B06/B08; five source metadata fixtures do not claim real upload',CA:'NOT USED'},
 fixtures:fixture,attempts:main.attempts,sourceBaseline:{compared:35,changed:0},protectedData:{hashesUnchanged:true,artifacts:after.find(x=>x.checkName==='P10 artifacts')},audit:{linkedHistoryRows:audit.length,actorReasonMatch:true},pdf:main.pdf,
 limitations:['No new backend suite/migration/build: application source and previous 35-file baseline unchanged','Empty-round close reuses valid prior DEV evidence; new source cases use non-empty rounds','Preflight DTO lists blocker reasons; eligible/excluded reasons verified on assessment detail screens','Two successful uploads from intermediate script attempts remain unbound (10019/10020); no unsafe Cloudinary cleanup','Completed state and clock passage are explicit owned SQL fixtures, not P12 completion or production clock changes'],
 traceScan,code,artifacts,commands:['pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 rounds -EvidenceDirectory target/pre-p10-extended-20261121/run-02','pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 sources -EvidenceDirectory target/pre-p10-extended-20261121/run-02','pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 student -EvidenceDirectory target/pre-p10-extended-20261121/run-02','pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 additional -EvidenceDirectory target/pre-p10-extended-20261121/run-02','pwsh -File frontend/e2e/run-timeline-extended-dev.ps1 security-admin -EvidenceDirectory target/pre-p10-extended-20261121/admin']};
const output=path.resolve(root,'../nghiepvu/.analysis/runtime/pre-p10/20261121');await fs.mkdir(output,{recursive:true});await fs.writeFile(path.join(output,'manifest.json'),JSON.stringify(manifest,null,2));
await fs.writeFile(path.join(home,'coverage.md'),'| ID | Hành vi đã kiểm | Kết quả |\n|---|---|---|\n'+manifest.cases.map(c=>`| ${c.id} | ${c.description} | ${c.status} |`).join('\n')+'\n');
console.log('Verified manifest: 24 functional PASS; 0 FAIL/SKIP/BLOCKED; setup assertion separate.');
