import test from 'node:test';
import assert from 'node:assert/strict';
import {ApiError} from '../src/shared/auth/auth-client.mjs';
import {updateRound} from '../src/features/hocbong/api/rounds.ts';
const id='9007199254740993';
const command={number:4,name:'Đợt đã sửa'};
const detail={id,sourceSemesterId:'21',number:4,name:command.name,status:'draft',createdBy:'301',semester:{id:'501',academicYear:'2025-2026',semesterOrder:2,startDate:'2026-01-16',endDate:'2026-06-30',status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null};
function fixture(body=detail,status=200,etag='"v2"'){
 const calls=[];
 return{calls,client:{request:async(path,options)=>{calls.push({path,options});return Response.json(body,{status,headers:{ETag:etag}});}}};
}
test('HB-T023-01: PUT exact mutable whitelist, bigint string and original strong If-Match',async()=>{
 const {client,calls}=fixture();
 const view=await updateRound(client,id,{...command,sourceSemesterId:'forged',semesterId:'forged',status:'open'},'"AbC_-123"');
 assert.equal(calls.length,1);assert.equal(calls[0].path,'/api/ctsv/hoc-bong/kkht/dot-xet/'+id);
 assert.equal(calls[0].options.method,'PUT');
 assert.deepEqual(calls[0].options.headers,{'Content-Type':'application/json','If-Match':'"AbC_-123"'});
 assert.deepEqual(JSON.parse(calls[0].options.body),command);
 assert.deepEqual(view,{round:detail,etag:'"v2"'});
});
test('HB-T023-01: missing/weak/wildcard/list/malformed ETag never reaches PUT',async()=>{
 const {client,calls}=fixture();
 for(const tag of [null,undefined,'','*','W/"v1"','v1','"a","b"','""','"a="','"a\r\nb"'])await assert.rejects(()=>updateRound(client,id,command,tag));
 assert.equal(calls.length,0);
});
test('HB-T023-01: invalid ID and mutable fields fail before transport',async()=>{
 const {client,calls}=fixture();
 for(const key of ['0','01','1/../../toi','9223372036854775808',9007199254740993])await assert.rejects(()=>updateRound(client,key,command,'"v1"'));
 for(const body of [{...command,number:0},{...command,number:1.5},{...command,number:2147483648},{...command,name:'  '},{...command,name:'x'.repeat(513)}])await assert.rejects(()=>updateRound(client,id,body,'"v1"'));
 assert.equal(calls.length,0);
});
test('HB-T023-03: stale version propagates unchanged and never silently changes tag or retries',async()=>{
 let calls=0;const error=new ApiError(412,'VERSION_CONFLICT',['If-Match']);
 await assert.rejects(()=>updateRound({request:async(_path,options)=>{calls++;assert.equal(options.headers['If-Match'],'"old"');throw error;}},id,command,'"old"'),value=>value===error);
 assert.equal(calls,1);
});
test('HB-T023-04: conflict/precondition/validation errors propagate without additional writes',async()=>{
 for(const error of [new ApiError(409,'ROUND_EXISTS'),new ApiError(409,'ROUND_NOT_EDITABLE'),new ApiError(428,'PRECONDITION_REQUIRED'),new ApiError(400,'VALIDATION_ERROR',['name'])]){
  let calls=0;await assert.rejects(()=>updateRound({request:async()=>{calls++;throw error;}},id,command,'"v1"'),value=>value===error);assert.equal(calls,1);
 }
});
test('HB-T023-02: no-op success may keep the exact same ETag',async()=>{
 const view=await updateRound(fixture(detail,200,'"unchanged"').client,id,command,'"unchanged"');
 assert.equal(view.etag,'"unchanged"');
});
test('HB-T023-05: transport failure never automatically repeats PUT',async()=>{
 let calls=0;const error=new TypeError('Synthetic lost response');
 await assert.rejects(()=>updateRound({request:async()=>{calls++;throw error;}},id,command,'"v1"'),value=>value===error);assert.equal(calls,1);
});
test('HB-T023-05: malformed or wrong mutation response is not presented as confirmed success',async()=>{
 for(const [body,status]of [[null,200],[{...detail,id:'1002'},200],[{...detail,number:3},200],[detail,201]])await assert.rejects(()=>updateRound(fixture(body,status).client,id,command,'"v1"'));
});
test('HB-T023-05: PUT retains a bounded deadline',async t=>{
 const deadline=new AbortController();let milliseconds;
 t.mock.method(AbortSignal,'timeout',value=>{milliseconds=value;return deadline.signal;});
 const pending=updateRound({request:async(_path,{signal})=>new Promise((_resolve,reject)=>signal.addEventListener('abort',()=>reject(signal.reason),{once:true}))},id,command,'"v1"');
 const rejected=assert.rejects(pending,error=>error.name==='TimeoutError');
 assert.equal(milliseconds,20000);deadline.abort(new DOMException('Synthetic deadline','TimeoutError'));await rejected;
});
