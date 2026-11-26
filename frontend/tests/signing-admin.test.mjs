import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveRoute} from '../src/app/navigation/modules.mjs';
import {supportSummary,certificateState,supportError} from '../src/features/mghp/signing-admin/model.mjs';
import {createSigningAdminApi} from '../src/features/mghp/signing-admin/api.mjs';
test('dedicated backend grant alone opens both Admin routes, not Auth administrator or signer',()=>{
 for(const path of ['/quan-tri/ky-so/su-co','/quan-tri/ky-so/chung-thu']){
  for(const access of [null,{ctsvRoleAdmin:true},{mghpSigning:true}])assert.equal(resolveRoute(path,[],access).kind,'denied');
  assert.equal(resolveRoute(path,[],{mghpSigningAdmin:true}).kind,'page');
 }
});
test('support copy contains useful context and expiry is never invented',()=>{
 const text=supportSummary({id:'4',title:'Biên bản',roundName:'Đợt 1',actorRole:'chuyen_vien',actorAccount:'dev.cv',apiMethod:'GET',apiPath:'/api/v1/documents/{documentId}/view-file/',code:'CA_STORAGE_NO_SUCH_KEY',message:'Không tải được PDF',occurredAt:'2026-11-25T01:00Z'});
 for(const value of ['Biên bản','Đợt 1','dev.cv','GET','CA_STORAGE_NO_SUCH_KEY'])assert.ok(text.includes(value));
 assert.equal(certificateState('unknown'),'Chưa kiểm tra');assert.equal(certificateState('expiring'),'Sắp hết hạn');
 assert.match(supportError({status:412}),/thay đổi/);
});
test('commands send one POST with ETag and never retry a lost renew response',async()=>{
 const calls=[];const api=createSigningAdminApi({request:async(path,options)=>{calls.push({path,options});throw new Error('network');}});
 await assert.rejects(()=>api.renew('2','Lý do','"a"'));assert.equal(calls.length,1);assert.equal(calls[0].options.headers['If-Match'],'"a"');
 assert.equal(JSON.parse(calls[0].options.body).reason,'Lý do');
});
