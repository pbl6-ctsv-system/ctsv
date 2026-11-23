import test from 'node:test';
import assert from 'node:assert/strict';
import {canStartApplicationReview,canReviseApplicationReview,reviewComparisonValue,applicationReviewHead,applicationReviewDecision} from '../src/features/mghp/review/model.mjs';
import {applicationStates,applicationStatusLabel} from '../src/features/mghp/presentation.mjs';
test('only approved online sources with fresh server permission offer review',()=>{
 for(const status of ['submitted','processing','supplementing','supplement_responded','reopened','rejected','withdrawn'])assert.equal(canStartApplicationReview({status,evaluationAllowed:true}),false);
 assert.equal(canStartApplicationReview({status:'approved',evaluationAllowed:true}),true);
 assert.equal(canStartApplicationReview({status:'approved',evaluationAllowed:false}),false);
 assert.equal(canStartApplicationReview({status:'approved',workflowStatus:'hard_copy_failed',evaluationAllowed:true}),false);
});
test('terminal paper failure cannot be revised; benefit assessments keep their separate lifecycle',()=>{
 assert.equal(canReviseApplicationReview({status:'confirmed',applicationId:'1',result:'khong_dat'}),false);
 assert.equal(canReviseApplicationReview({status:'confirmed',benefitId:'1',result:'khong_dat'}),true);
 assert.equal(canReviseApplicationReview({status:'confirmed',applicationId:'1',result:'can_xac_minh'}),true);
});
test('progress labels show online and paper stages without confusing them with submission revisions',()=>{
 assert.match(applicationStates.approved,/V1/);
 for(const status of ['approved_v1','approved_v2','hard_copy_failed','awaiting_council','selected','not_selected'])assert.ok(applicationStates[status]);
 assert.equal(applicationStatusLabel({status:'approved',workflowStatus:'approved_v2'}),applicationStates.approved_v2);
 assert.equal(applicationStatusLabel({status:'approved'}),applicationStates.approved);
});
test('citizen ID stays text; only actual reference fields require catalogue labels',()=>{
 assert.equal(reviewComparisonValue('citizenId','DEV-SYNTHETIC-ID',{}),'DEV-SYNTHETIC-ID');
 assert.equal(reviewComparisonValue('classId','7',{classId:{7:'23T'}}),'23T');
 assert.equal(reviewComparisonValue('classId','7',{}),'Chưa xác minh tên danh mục');
});
test('start resumes the latest assessment in the exact policy and semester chain',()=>{
 const chain=[{id:'10',revision:1,status:'confirmed',policyVersionId:'7',semesterId:'1'},{id:'11',revision:2,status:'draft',policyVersionId:'7',semesterId:'1'},{id:'12',revision:5,status:'draft',policyVersionId:'8',semesterId:'1'},{id:'13',revision:8,status:'draft',policyVersionId:'7',semesterId:'2'}];
 assert.equal(applicationReviewHead(chain,'7','1')?.id,'11');
 assert.equal(applicationReviewHead(chain,'7','3'),null);
 assert.deepEqual(chain.map(e=>e.id),['10','11','12','13']);
});
test('approval needs no reason and never silently passes child checks',()=>{
 const form={applicationId:'1',result:'can_xac_minh',reason:'Old draft note',criteria:[{result:'chua_xet'}],evidence:[{result:'can_xac_minh',reason:'Chờ xác minh'}],eligiblePeriods:[{from:'2026-09-01',to:'2026-10-31',evidence:'Nguồn đã kiểm'}]};
 const approved=applicationReviewDecision(form,'dat');
 assert.equal(approved.reason,null);assert.equal(approved.result,'dat');
 assert.deepEqual(approved.criteria,form.criteria);assert.deepEqual(approved.evidence,form.evidence);assert.deepEqual(approved.eligiblePeriods,form.eligiblePeriods);
 assert.throws(()=>applicationReviewDecision(form,'khong_dat','  '),/Lý do/);
 const declined=applicationReviewDecision(form,'khong_dat','  Bản cứng không đúng chủ thể  ');
 assert.equal(declined.reason,'Bản cứng không đúng chủ thể');assert.deepEqual(declined.eligiblePeriods,[]);
 assert.equal(form.result,'can_xac_minh');assert.equal(form.eligiblePeriods.length,1);
});
