import test from 'node:test';
import assert from 'node:assert/strict';
import {linkPaper,changeCriterion,changePaperDates,paperChecks,proposePeriods,applyPeriods,reviewReadiness} from '../src/features/mghp/review/hard-copy-model.mjs';
const check=kind=>({kind,result:kind==='hard_copy'?'received':'pass'});
const document=(id,requirementId,expiresOn=null)=>({id,requirementId,submissionId:'s',expiresOn,checks:['form','content','validity','subject','hard_copy'].map(check)});
const policy={effectiveFrom:'2026-01-01',effectiveUntil:null,ruleVerificationStatus:'da_doi_chieu_nguon',criteria:[{id:'c',name:'Đúng diện',required:true,conditional:false}],documentGroups:[{name:'Giấy xác nhận',minimumRequired:1,documents:[{id:'r1',hardCopyRequired:true}]}]};
const term={startDate:'2026-09-01',endDate:'2027-01-31',verificationStatus:'verified'};
const process={actualStart:'2025-09-01',actualEnd:null,verificationStatus:'verified'};
const form=()=>({applicationId:'a',submissionId:'s',semesterId:'1',criteria:[{criterionId:'c',result:'chua_xet',note:null}],evidence:[],eligiblePeriods:[],timeEvidence:null});
test('linking a paper never passes a criterion and a changed verdict supplies its linked evidence once',()=>{
 const original=form();let value=linkPaper(original,'d','c',true,{from:'2026-09-01',to:'2026-10-31'});
 assert.equal(value.criteria[0].result,'chua_xet');assert.equal(value.evidence[0].result,'can_xac_minh');assert.equal(original.evidence.length,0);
 value=changeCriterion(value,'c',{result:'khong_dat',note:'Sai đối tượng'});assert.equal(value.evidence[0].result,'khong_dat');assert.equal(value.evidence[0].reason,'Sai đối tượng');
 value=changeCriterion(value,'c',{result:'dat'});assert.equal(value.evidence[0].result,'dat');assert.equal(value.evidence[0].reason,null);assert.equal(value.evidence.length,1);
 assert.deepEqual(linkPaper(value,'d','c',true).evidence,value.evidence);
});
test('one date edit updates uses of that paper only and never mutates another paper or old form',()=>{
 const original={...form(),evidence:[{documentId:'d',criterionId:'c',from:'2026-09-01',to:'2026-09-30'},{documentId:'d',criterionId:'c2',from:null,to:null},{documentId:'other',criterionId:null,from:null,to:null}]};
 const next=changePaperDates(original,'d','2026-09-01','2026-10-31');assert.equal(next.evidence[0].to,'2026-10-31');assert.equal(next.evidence[1].to,'2026-10-31');assert.equal(next.evidence[2].to,null);assert.equal(original.evidence[0].to,'2026-09-30');
});
test('physical checks are independent of business verdict and use the latest recorded result',()=>{
 const d=document('d','r1');assert.equal(paperChecks(d,{hardCopyRequired:true}).ready,true);d.checks.push({kind:'subject',result:'needs_verification'});assert.equal(paperChecks(d,{hardCopyRequired:true}).ready,false);
});
const use=(documentId,from,to)=>({documentId,criterionId:'c',from,to,result:'dat',reason:null});
test('suggestion clips to verified term/process/policy and actual evidence expiry, never the entire semester',()=>{
 const value={...form(),evidence:[use('d','2026-08-01','2027-02-28')]};
 const result=proposePeriods(value,policy,[document('d','r1','2026-10-31')],term,process);
 assert.deepEqual(result.periods,[{from:'2026-09-01',to:'2026-10-31'}]);
 assert.deepEqual(value.eligiblePeriods,[]);
});
test('AND groups with min/OR alternatives preserve gaps and count distinct requirements rather than duplicate files',()=>{
 const p={...policy,documentGroups:[{name:'A',minimumRequired:1,documents:[{id:'r1'},{id:'r2'}]},{name:'B',minimumRequired:1,documents:[{id:'r3'}]}]};
 const docs=[document('d1','r1'),document('d2','r2'),document('d3','r3')];
 const v={...form(),evidence:[use('d1','2026-09-01','2026-09-30'),use('d2','2026-11-01','2026-11-30'),use('d3','2026-09-15','2026-11-15')]};
 assert.deepEqual(proposePeriods(v,p,docs,term,process).periods,[{from:'2026-09-15',to:'2026-09-30'},{from:'2026-11-01',to:'2026-11-15'}]);
 const sameRequirement={...p,documentGroups:[{name:'Two',minimumRequired:2,documents:[{id:'r1'},{id:'r2'}]}]};
 assert.deepEqual(proposePeriods({...form(),evidence:[use('d1','2026-09-01','2026-09-30'),use('duplicate','2026-09-01','2026-09-30')]},sameRequirement,[docs[0],document('duplicate','r1')],term,process).periods,[]);
});
test('missing verified dates, wrong submission, unresolved checks or reversed dates never get invented coverage',()=>{
 const value={...form(),evidence:[use('d','2026-09-01','2026-10-31')]};const d=document('d','r1');
 for(const badTerm of [null,{...term,verificationStatus:'unverified'},{...term,startDate:null}])assert.deepEqual(proposePeriods(value,policy,[d],badTerm,process).periods,[]);
 assert.deepEqual(proposePeriods(value,policy,[{...d,submissionId:'other'}],term,process).periods,[]);
 assert.deepEqual(proposePeriods(value,policy,[{...d,checks:[]}],term,process).periods,[]);
 assert.deepEqual(proposePeriods({...value,evidence:[use('d','2026-11-01','2026-09-01')]},policy,[d],term,process).periods,[]);
});
test('adjacent windows backed by different papers remain separate when no single proof covers the combined period',()=>{
 const p={...policy,documentGroups:[{name:'A',minimumRequired:1,documents:[{id:'r1'},{id:'r2'}]}]};
 const value={...form(),evidence:[use('d1','2026-09-01','2026-09-30'),use('d2','2026-10-01','2026-10-31')]};
 assert.deepEqual(proposePeriods(value,p,[document('d1','r1'),document('d2','r2')],term,process).periods,[{from:'2026-09-01',to:'2026-09-30'},{from:'2026-10-01',to:'2026-10-31'}]);
});
test('common time basis is saved once in the existing payload and readiness explains missing work',()=>{
 let value=form();const docs=[document('d','r1')];assert.ok(reviewReadiness(value,policy,docs,term,process).some(s=>s.includes('Đúng diện')));
 value=changeCriterion(linkPaper(value,'d','c',true,{from:'2026-09-01',to:'2026-10-31'}),'c',{result:'dat'});
 value=applyPeriods(value,proposePeriods(value,policy,docs,term,process).periods,'Giấy đã đối chiếu chỉ chứng minh tháng 9–10.');
 assert.equal(value.timeEvidence,value.eligiblePeriods[0].evidence);assert.deepEqual(reviewReadiness(value,policy,docs,term,process),[]);
 assert.ok(reviewReadiness({...value,eligiblePeriods:[{from:'2026-09-01',to:'2026-11-30',evidence:'Too long'}]},policy,docs,term,process).some(s=>s.includes('vượt')));
});
