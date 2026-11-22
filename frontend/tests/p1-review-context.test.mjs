import test from 'node:test';import assert from 'node:assert/strict';
import {reviewDocumentLabel,reviewDateErrors,reviewFormErrors} from '../src/features/mghp/review/model.mjs';
test('a document choice names its requirement and submission revision, preserving old versions',()=>{
 const source={documentRequirements:[{id:'2',name:'Giấy xác nhận'}],submittedVersions:[{id:'9',revision:3}]};
 assert.equal(reviewDocumentLabel({requirementId:'2',submissionId:'9'},source),'Giấy xác nhận · Bản nộp 3');
});
test('an unresolved evidence result asks for its required reason before sending',()=>{
 assert.deepEqual(reviewFormErrors({evidence:[{result:'can_xac_minh',reason:''}]}),['Giấy 1: nhập lý do khi chưa kết luận Đạt.']);
 assert.deepEqual(reviewFormErrors({evidence:[{result:'dat',reason:''}]}),[]);
});
test('evidence dates and eligible dates have independent validation and no automatic filling',()=>{
 const v={evidence:[{from:'2026-12-01',to:'2026-09-01'}],eligiblePeriods:[{from:'2026-09-01',to:'2026-10-01'}]};
 assert.deepEqual(reviewDateErrors(v),['Khoảng ngày của giấy 1 bị đảo thứ tự.']);assert.equal(v.eligiblePeriods[0].to,'2026-10-01');
});
