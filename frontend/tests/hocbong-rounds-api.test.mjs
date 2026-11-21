import test from 'node:test';
import assert from 'node:assert/strict';
import {ApiError} from '../src/shared/auth/auth-client.mjs';
import {listRounds,readRound,readScholarshipGroups} from '../src/features/hocbong/api/rounds.ts';

const summary={id:'9007199254740993',sourceSemesterId:'21',semesterId:'501',academicYear:'2025-2026',semesterOrder:2,number:1,name:'Đợt xét KKHT lần 1',status:'draft'};
const detail={id:summary.id,sourceSemesterId:'21',number:1,name:summary.name,status:'draft',createdBy:'301',semester:{id:'501',academicYear:'2025-2026',semesterOrder:2,startDate:'2026-01-16',endDate:'2026-06-30',status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null};
const page={items:[summary],page:0,size:20,totalElements:1};
function fixture(body,headers={}){
 const calls=[];
 const client={request:async(path,options)=>{calls.push({path,options});return Response.json(body,{headers});}};
 return{client,calls};
}

test('HB-T021-03: list preserves bigint strings and sends only documented server filters',async()=>{
 const {client,calls}=fixture({...page,page:2,size:10,totalElements:23});
 const abort=new AbortController();
 const result=await listRounds(client,{page:2,size:10,number:3,semesterId:'9007199254740995'},abort.signal);
 assert.equal(result.items[0].id,'9007199254740993');
 assert.equal(result.totalElements,23);
 const url=new URL(calls[0].path,'https://fixture.invalid');
 assert.equal(url.pathname,'/api/ctsv/hoc-bong/kkht/dot-xet');
 assert.deepEqual(Object.fromEntries(url.searchParams),{page:'2',size:'10',number:'3',semesterId:'9007199254740995'});
 abort.abort();
 assert.equal(calls[0].options.signal.aborted,true,'caller cancellation reaches the request');
 assert(!calls[0].options.method||calls[0].options.method==='GET');
});

test('HB-T021-03: no filter is omitted instead of sending empty query values',async()=>{
 const {client,calls}=fixture(page);
 await listRounds(client);
 assert.deepEqual(Object.fromEntries(new URL(calls[0].path,'https://fixture.invalid').searchParams),{page:'0',size:'20'});
});

test('HB-T021-04: detail keeps the persisted representation, nullable fields and strong ETag',async()=>{
 const {client,calls}=fixture(detail,{ETag:'"AAAAAAAAAAE="'});
 const result=await readRound(client,summary.id);
 assert.deepEqual(result.round,detail);
 assert.equal(result.etag,'"AAAAAAAAAAE="');
 assert.equal(calls[0].path,'/api/ctsv/hoc-bong/kkht/dot-xet/9007199254740993');
});

test('HB-T021-04: invalid IDs cannot be interpolated into a request path',async()=>{
 const {client,calls}=fixture(detail);
 for(const id of ['0','01','-1','1/../../toi','9223372036854775808',9007199254740993]){
  await assert.rejects(()=>readRound(client,id));
 }
 assert.equal(calls.length,0);
});

test('HB-T021-03: pagination and numeric filters respect contract bounds',async()=>{
 const {client,calls}=fixture(page);
 for(const filters of [{page:-1},{size:0},{size:101},{page:5001,size:20},{number:0},{number:2147483648},{semesterId:'001'}]){
  await assert.rejects(()=>listRounds(client,filters));
 }
 assert.equal(calls.length,0);
});

test('HB-T021-02: only boolean group flags are accepted, including combined roles',async()=>{
 for(const groups of [{student:true,specialist:true},{student:false,specialist:false}]){
  const {client}=fixture(groups);
  assert.deepEqual(await readScholarshipGroups(client),groups);
 }
 for(const body of [{student:false,specialist:'true'},{student:true},{specialist:true},null]){
  await assert.rejects(()=>readScholarshipGroups(fixture(body).client));
 }
});

test('HB-T021-03: empty success differs from malformed response and unavailable service',async()=>{
 assert.equal((await listRounds(fixture({...page,items:[],totalElements:0}).client)).totalElements,0);
 for(const body of [{},null,{...page,items:null},{...page,items:[{...summary,id:123}]},{...page,totalElements:-1}]){
  await assert.rejects(()=>listRounds(fixture(body).client));
 }
 const denied=new ApiError(403,'FORBIDDEN');
 await assert.rejects(()=>listRounds({request:async()=>{throw denied;}}),error=>error===denied);
});

test('HB-T021-05: passing a cancellation signal does not remove the GET deadline',async t=>{
 const deadline=new AbortController();
 let milliseconds;
 t.mock.method(AbortSignal,'timeout',duration=>{milliseconds=duration;return deadline.signal;});
 const caller=new AbortController();
 const client={request:async(_path,{signal})=>new Promise((_resolve,reject)=>{
  signal.addEventListener('abort',()=>reject(signal.reason),{once:true});
 })};
 const pending=listRounds(client,{},caller.signal);
 const failure=assert.rejects(pending,error=>error.name==='TimeoutError');
 assert.equal(milliseconds,20000);
 deadline.abort(new DOMException('Synthetic deadline','TimeoutError'));
 await failure;
 assert.equal(caller.signal.aborted,false,'deadline must not cancel the owning view');
});
