import test from 'node:test';
import assert from 'node:assert/strict';
import {ApiError} from '../src/shared/auth/auth-client.mjs';
import {listSourceSemesters,createRound,listRounds} from '../src/features/hocbong/api/rounds.ts';

const source={sourceSemesterId:'9007199254740993',code:'2520',name:'Học kỳ 2',academicYear:'2025-2026',semesterOrder:2,startDate:'2026-01-16',endDate:'2026-06-30',scholarshipSemesterId:null,selectable:true,unavailableReason:null};
const detail={id:'9007199254740995',sourceSemesterId:source.sourceSemesterId,number:3,name:'Đợt xét lần 3',status:'draft',createdBy:'301',semester:{id:'501',academicYear:'2025-2026',semesterOrder:2,startDate:source.startDate,endDate:source.endDate,status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null};
const revision='AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';
const key='32e4cf9a-14cc-4c1a-8a3b-298da9278f7e';
const command={sourceSemesterId:source.sourceSemesterId,catalogRevision:revision,scope:{mode:'all'}};
function fixture(body,status=200,headers={ETag:'"v1"','Idempotency-Replayed':String(status===200)}){
 const calls=[];
 const client={request:async(path,options)=>{calls.push({path,options});return Response.json(body,{status,headers});}};
 return{client,calls};
}
const sourcePage=items=>({items,page:0,size:20,totalElements:items.length});

test('HB-T022-01: source search sends literal q and server pagination, keeps bigint string',async()=>{
 const {client,calls}=fixture({...sourcePage([source]),page:2,size:10,totalElements:21});
 const result=await listSourceSemesters(client,{q:' 25%_20 ',page:2,size:10});
 assert.equal(result.items[0].sourceSemesterId,'9007199254740993');
 const url=new URL(calls[0].path,'https://fixture.invalid');
 assert.equal(url.pathname,'/api/ctsv/hoc-bong/hoc-ky-nguon');
 assert.deepEqual(Object.fromEntries(url.searchParams),{page:'2',size:'10',q:'25%_20'});
});

test('HB-T022-01: source NULL/unavailable rows remain explicit instead of being silently discarded',async()=>{
 const summer={...source,sourceSemesterId:'22',code:'2521',name:null,scholarshipSemesterId:'501',selectable:false,unavailableReason:'SUMMER_MERGED_INTO_SEMESTER_2'};
 const missing={...source,sourceSemesterId:'23',code:'unmapped',name:null,academicYear:null,semesterOrder:null,startDate:null,endDate:null,selectable:false,unavailableReason:'SEMESTER_MAPPING_REQUIRED'};
 assert.deepEqual((await listSourceSemesters(fixture(sourcePage([source,summer,missing])).client)).items,[source,summer,missing]);
 for(const item of [{...source,selectable:'true'},{...source,startDate:null},{...summer,unavailableReason:null},{...source,sourceSemesterId:123}]){
  await assert.rejects(()=>listSourceSemesters(fixture(sourcePage([item])).client));
 }
});

test('HB-T022-01: invalid source query/page is rejected before a request',async()=>{
 const {client,calls}=fixture(sourcePage([]));
 for(const query of [{q:'x'.repeat(129)},{page:-1},{size:101},{page:5001,size:20}])await assert.rejects(()=>listSourceSemesters(client,query));
 assert.equal(calls.length,0);
});

test('HB-T024-014/038: create sends the scope whitelist and one canonical idempotency key',async()=>{
 const {client,calls}=fixture(detail,201);
 const result=await createRound(client,{...command,number:999,name:'forged',status:'open',semesterId:'forged',createdBy:'forged'},key);
 assert.equal(calls.length,1);
 assert.equal(calls[0].path,'/api/ctsv/hoc-bong/kkht/dot-xet');
 assert.equal(calls[0].options.method,'POST');
 assert.equal(calls[0].options.headers['Content-Type'],'application/json');
 assert.equal(calls[0].options.headers['Idempotency-Key'],key);
 assert.deepEqual(JSON.parse(calls[0].options.body),command);
 assert.deepEqual(result.round,detail);
 assert.equal(result.etag,'"v1"');
 assert.equal(result.replayed,false);
 assert(calls[0].options.signal instanceof AbortSignal);
});

test('HB-T024-009/019: invalid source, fingerprint, key or scope never reaches POST',async()=>{
 const {client,calls}=fixture(detail,201);
 const invalidScopes=[null,{mode:'other'},{mode:'all',groups:[]},{mode:'groups',groups:[]},{mode:'groups',groups:[{cohortId:1,majorId:'2'}]},{mode:'groups',groups:[{cohortId:'1',majorId:'2'},{cohortId:'1',majorId:'2'}]},{mode:'groups',groups:Array.from({length:2001},(_,i)=>({cohortId:String(i+1),majorId:'2'}))}];
 for(const value of [{...command,sourceSemesterId:9007199254740993},{...command,sourceSemesterId:'001'},{...command,catalogRevision:'A'.repeat(42)},{...command,catalogRevision:'A'.repeat(42)+'B'},...invalidScopes.map(scope=>({...command,scope}))])await assert.rejects(()=>createRound(client,value,key));
 for(const value of [undefined,'',key.toUpperCase(),key.replace('-4c1a-','-1c1a-'),key.replace('-8a3b-','-7a3b-')])await assert.rejects(()=>createRound(client,command,value));
 assert.equal(calls.length,0);
});

test('HB-T024-011/015/019: API errors propagate unchanged without automatic POST replay',async()=>{
 for(const error of [new ApiError(409,'SCOPE_SOURCE_CHANGED'),new ApiError(409,'ROUND_SEQUENCE_NOT_READY'),new ApiError(409,'IDEMPOTENCY_KEY_REUSED'),new ApiError(400,'VALIDATION_ERROR',['scope'])]){
  let calls=0;
  await assert.rejects(()=>createRound({request:async()=>{calls++;throw error;}},command,key),value=>value===error);
  assert.equal(calls,1);
 }
});

test('HB-T024-022: uncertain transport result never triggers a second POST',async()=>{
 let calls=0;
 const error=new TypeError('Synthetic lost response');
 await assert.rejects(()=>createRound({request:async()=>{calls++;throw error;}},command,key),value=>value===error);
 assert.equal(calls,1);
});

test('HB-T024-017/038: replay accepts the current non-draft representation and exposes replayed',async()=>{
 const current={...detail,number:4,name:'Tên hiện hành',status:'completed'};
 const result=await createRound(fixture(current,200).client,command,key);
 assert.deepEqual(result,{round:current,etag:'"v1"',replayed:true});
});

test('HB-T024-038: malformed headers, mismatched parent or unexpected success status are uncertain',async()=>{
 for(const [body,status,headers] of [
  [detail,201,{ETag:'"v1"'}],[detail,201,{ETag:'"v1"','Idempotency-Replayed':'true'}],
  [detail,200,{ETag:'"v1"','Idempotency-Replayed':'false'}],
  [detail,201,{ETag:'W/"v1"','Idempotency-Replayed':'false'}],
  [detail,201,{'Idempotency-Replayed':'false'}],[detail,202,{ETag:'"v1"','Idempotency-Replayed':'false'}],
  [{...detail,sourceSemesterId:'999'},201,{ETag:'"v1"','Idempotency-Replayed':'false'}],
 ])await assert.rejects(()=>createRound(fixture(body,status,headers).client,command,key));
});

test('HB-T024-009: selected bigint group IDs are serialized as pairs without snapshot labels',async()=>{
 const selected={...command,scope:{mode:'groups',groups:[{cohortId:'9007199254740997',majorId:'9223372036854775807',majorName:'forged'}]}};
 const {client,calls}=fixture(detail,201);
 await createRound(client,selected,key);
 assert.deepEqual(JSON.parse(calls[0].options.body).scope,{mode:'groups',groups:[{cohortId:'9007199254740997',majorId:'9223372036854775807'}]});
});

test('HB-T024-022: create validates the response against the source actually sent',async()=>{
 const pendingCommand={...command};
 const client={request:async(_path,options)=>{
  assert.equal(JSON.parse(options.body).sourceSemesterId,source.sourceSemesterId);
  pendingCommand.sourceSemesterId='999';
  return Response.json(detail,{status:201,headers:{ETag:'"v1"','Idempotency-Replayed':'false'}});
 }};
 assert.equal((await createRound(client,pendingCommand,key)).round.sourceSemesterId,source.sourceSemesterId);
});

test('HB-T022-07: source-filtered list supports number and never both semester filters',async()=>{
 const {client,calls}=fixture({items:[],page:0,size:20,totalElements:0});
 await listRounds(client,{sourceSemesterId:source.sourceSemesterId,number:3});
 assert.deepEqual(Object.fromEntries(new URL(calls[0].path,'https://fixture.invalid').searchParams),{page:'0',size:'20',number:'3',sourceSemesterId:source.sourceSemesterId});
 await assert.rejects(()=>listRounds(client,{semesterId:'501',sourceSemesterId:source.sourceSemesterId}));
 assert.equal(calls.length,1);
});
