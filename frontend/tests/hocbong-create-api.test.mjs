import test from 'node:test';
import assert from 'node:assert/strict';
import {ApiError} from '../src/shared/auth/auth-client.mjs';
import {listSourceSemesters,createRound,listRounds} from '../src/features/hocbong/api/rounds.ts';

const source={sourceSemesterId:'9007199254740993',code:'2520',name:'Học kỳ 2',academicYear:'2025-2026',semesterOrder:2,startDate:'2026-01-16',endDate:'2026-06-30',scholarshipSemesterId:null,selectable:true,unavailableReason:null};
const detail={id:'9007199254740995',sourceSemesterId:source.sourceSemesterId,number:3,name:'Đợt xét lần 3',status:'draft',createdBy:'301',semester:{id:'501',academicYear:'2025-2026',semesterOrder:2,startDate:source.startDate,endDate:source.endDate,status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null};
const command={sourceSemesterId:source.sourceSemesterId,number:3,name:detail.name};
function fixture(body,status=200){
 const calls=[];
 const client={request:async(path,options)=>{calls.push({path,options});return Response.json(body,{status,headers:{ETag:'"AAAAAAAAAAE="'}});}};
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

test('HB-T022-02: create sends one POST with only the approved whitelist and preserves ETag',async()=>{
 const {client,calls}=fixture(detail,201);
 const result=await createRound(client,{...command,status:'open',semesterId:'forged',createdBy:'forged'});
 assert.equal(calls.length,1);
 assert.equal(calls[0].path,'/api/ctsv/hoc-bong/kkht/dot-xet');
 assert.equal(calls[0].options.method,'POST');
 assert.equal(calls[0].options.headers['Content-Type'],'application/json');
 assert.deepEqual(JSON.parse(calls[0].options.body),command);
 assert.deepEqual(result.round,detail);
 assert.equal(result.etag,'"AAAAAAAAAAE="');
 assert(calls[0].options.signal instanceof AbortSignal);
});

test('HB-T022-02: invalid create fields never reach the server',async()=>{
 const {client,calls}=fixture(detail,201);
 for(const value of [{...command,sourceSemesterId:9007199254740993},{...command,sourceSemesterId:'001'},{...command,number:0},{...command,number:1.5},{...command,number:2147483648},{...command,name:'  '},{...command,name:'x'.repeat(513)}])await assert.rejects(()=>createRound(client,value));
 assert.equal(calls.length,0);
});

test('HB-T022-04: duplicate/validation errors propagate unchanged without automatic POST replay',async()=>{
 for(const error of [new ApiError(409,'ROUND_EXISTS'),new ApiError(400,'VALIDATION_ERROR',['name'])]){
  let calls=0;
  await assert.rejects(()=>createRound({request:async()=>{calls++;throw error;}},command),value=>value===error);
  assert.equal(calls,1);
 }
});

test('HB-T022-07: uncertain transport result never triggers a second POST',async()=>{
 let calls=0;
 const error=new TypeError('Synthetic lost response');
 await assert.rejects(()=>createRound({request:async()=>{calls++;throw error;}},command),value=>value===error);
 assert.equal(calls,1);
 await assert.rejects(()=>createRound(fixture(detail,200).client,command));
});

test('HB-T022-07: recovery lookup uses sourceSemesterId/number and never both semester filters',async()=>{
 const {client,calls}=fixture({items:[],page:0,size:20,totalElements:0});
 await listRounds(client,{sourceSemesterId:source.sourceSemesterId,number:3});
 assert.deepEqual(Object.fromEntries(new URL(calls[0].path,'https://fixture.invalid').searchParams),{page:'0',size:'20',number:'3',sourceSemesterId:source.sourceSemesterId});
 await assert.rejects(()=>listRounds(client,{semesterId:'501',sourceSemesterId:source.sourceSemesterId}));
 assert.equal(calls.length,1);
});
