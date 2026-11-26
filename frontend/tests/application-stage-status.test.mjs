import test from 'node:test';
import assert from 'node:assert/strict';
import {applicationStatusLabel,onlineStatusLabel,hardCopyStatusLabel,onlineFilters,hardCopyFilters} from '../src/features/mghp/presentation.mjs';
import {applicationListUrl} from '../src/features/mghp/intake/dossier-model.mjs';
import {hardCopyListUrl,canStartApplicationReview} from '../src/features/mghp/review/model.mjs';

test('stage labels stay stable when paper is rejected; common approval means both stages passed',()=>{
 const record={status:'rejected',onlineStatus:'approved',hardCopyStatus:'rejected',currentStage:'ended'};
 assert.equal(applicationStatusLabel(record),'Từ chối');
 assert.equal(onlineStatusLabel(record),'Đã duyệt online — V1');
 assert.equal(hardCopyStatusLabel(record),'Đã từ chối bản cứng');
 assert.equal(applicationStatusLabel({status:'approved'}),'Đã duyệt');
});
test('student labels distinguish pending supplement and paper without publishing council outcome',()=>{
 assert.equal(applicationStatusLabel({status:'processing',onlineStatus:'supplement_responded'},'student'),'Đã gửi bổ sung — chờ CTSV kiểm tra');
 assert.equal(applicationStatusLabel({status:'processing',onlineStatus:'approved',hardCopyStatus:'pending'},'student'),'Hồ sơ online đã được duyệt — chờ đối chiếu bản cứng');
 assert.equal(applicationStatusLabel({status:'approved',councilResult:null},'student'),'Hồ sơ đã được duyệt — chờ kết quả xét MGHP');
 assert.equal(applicationStatusLabel({status:'approved',councilResult:'selected'},'student'),'Được chọn hưởng MGHP');
});
test('each inbox has only its own filters and sends the stage before paging',()=>{
 assert.deepEqual(onlineFilters.map(([,name])=>name),['Tất cả','Chờ xử lý','Sinh viên đã bổ sung','Đã duyệt','Từ chối','Đã rút']);
 assert.deepEqual(hardCopyFilters.map(([,name])=>name),['Tất cả','Cần đối chiếu','Đã duyệt bản cứng','Đã từ chối bản cứng']);
 const online=new URL(applicationListUrl(2,'approved','11','22','online'),'https://test.invalid');
 assert.equal(online.searchParams.get('stage'),'online');assert.equal(online.searchParams.get('roundId'),'22');
 const paper=new URL(hardCopyListUrl(1,'11','22','pending'),'https://test.invalid');
 assert.equal(paper.searchParams.get('status'),'pending');assert.equal(paper.searchParams.get('stage'),'hard-copy');
});
test('online approval with server permission permits paper review although common state is processing',()=>{
 assert.equal(canStartApplicationReview({status:'processing',onlineStatus:'approved',intakeSealed:true,evaluationAllowed:true}),true);
 assert.equal(canStartApplicationReview({status:'rejected',onlineStatus:'approved',hardCopyStatus:'rejected',intakeSealed:true,evaluationAllowed:false}),false);
});
