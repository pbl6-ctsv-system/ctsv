import test from 'node:test';
import assert from 'node:assert/strict';
import {ApiError} from '../src/shared/auth/auth-client.mjs';
import * as api from '../src/features/hocbong/api/rounds.ts';

const revision='AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';
const sourceId='9007199254740993';
const roundId='9007199254740995';
const key='32e4cf9a-14cc-4c1a-8a3b-298da9278f7e';
const group={cohortId:'9007199254740997',cohortCode:null,majorId:'202',majorCode:null,majorName:null,studentCount:120};
const scope={id:'401',revision:1,mode:'all',catalogRevision:revision,groups:[group],createdAt:'2026-11-19T03:00:00Z',createdBy:'301'};
const detail={id:roundId,sourceSemesterId:sourceId,number:1,name:'Tên đã lưu',status:'draft',createdBy:'301',semester:{id:'501',academicYear:'2025-2026',semesterOrder:2,startDate:'2026-01-16',endDate:'2026-06-30',status:'draft'},opensAt:null,closedAt:null,tuitionSnapshotAt:null,ruleVersionId:null};
const page=(items=[group],extra={})=>({catalogRevision:revision,items,page:0,size:20,totalElements:items.length,...extra});
function fixture(body,headers={},status=200){
 const calls=[];
 return{calls,client:{request:async(path,options)=>{calls.push({path,options});return Response.json(body,{status,headers});}}};
}

test('HB-T024-008: catalog sends only approved AND filters and keeps nullable labels and bigint IDs',async()=>{
 const {client,calls}=fixture(page([group],{page:2,size:10,totalElements:21}));
 const caller=new AbortController();
 const result=await api.listScopeCatalog(client,{sourceSemesterId:sourceId,cohortId:group.cohortId,majorId:'202',q:' %_😀 ',page:2,size:10,forged:'x'},caller.signal);
 assert.deepEqual(result.items,[group]);
 assert.equal(result.catalogRevision,revision);
 const url=new URL(calls[0].path,'https://fixture.invalid');
 assert.equal(url.pathname,'/api/ctsv/hoc-bong/kkht/pham-vi-nguon');
 assert.deepEqual(Object.fromEntries(url.searchParams),{sourceSemesterId:sourceId,page:'2',size:'10',cohortId:group.cohortId,majorId:'202',q:'%_😀'});
 caller.abort();assert.equal(calls[0].options.signal.aborted,true);
});

test('HB-T024-008: catalog rejects invalid IDs, codepoint query length and offset before GET',async()=>{
 const {client,calls}=fixture(page());
 for(const filters of [{sourceSemesterId:'01'},{sourceSemesterId:sourceId,cohortId:1},{sourceSemesterId:sourceId,majorId:'9223372036854775808'},{sourceSemesterId:sourceId,q:'😀'.repeat(129)},{sourceSemesterId:sourceId,page:1001,size:100},{sourceSemesterId:sourceId,size:101}])await assert.rejects(()=>api.listScopeCatalog(client,filters));
 assert.equal(calls.length,0);
 await api.listScopeCatalog(client,{sourceSemesterId:sourceId,q:'😀'.repeat(128)});
 assert.equal(calls.length,1);
});

test('HB-T024-008/039: malformed catalog never becomes an empty success',async()=>{
 for(const body of [null,page([group],{catalogRevision:'A'.repeat(42)+'B'}),page([{...group,cohortId:123}]),page([{...group,studentCount:-1}]),page([{...group,majorName:undefined}]),page([group,group]),page([group],{page:1}),page([group],{totalElements:0})])await assert.rejects(()=>api.listScopeCatalog(fixture(body).client,{sourceSemesterId:sourceId}));
 const error=new ApiError(503,'SCOPE_SOURCE_UNAVAILABLE');
 await assert.rejects(()=>api.listScopeCatalog({request:async()=>{throw error;}},{sourceSemesterId:sourceId}),value=>value===error);
});

test('HB-T024-008: loading the full catalog follows every page at size100 without filters',async()=>{
 const first=Array.from({length:100},(_,i)=>({...group,cohortId:String(i+1)}));
 const calls=[];
 const client={request:async(path,options)=>{
  calls.push({path,options});const url=new URL(path,'https://fixture.invalid');
  assert.deepEqual(Object.fromEntries(url.searchParams),{sourceSemesterId:sourceId,page:String(calls.length-1),size:'100'});
  return Response.json(page(calls.length===1?first:[group],{page:calls.length-1,size:100,totalElements:101}));
 }};
 const result=await api.loadScopeCatalog(client,sourceId);
 assert.equal(calls.length,2);assert.equal(result.groups.length,101);
 assert.deepEqual(result.groups.at(-1),group);assert.equal(result.catalogRevision,revision);
});

test('HB-T024-008/011: changing fingerprint, total or duplicate groups between pages fails the snapshot',async()=>{
 const first=Array.from({length:100},(_,i)=>({...group,cohortId:String(i+1)}));
 for(const second of [page([group],{page:1,size:100,totalElements:101,catalogRevision:'B'.repeat(42)+'A'}),page([group],{page:1,size:100,totalElements:102}),page([first[0]],{page:1,size:100,totalElements:101})]){
  let calls=0;
  await assert.rejects(()=>api.loadScopeCatalog({request:async()=>Response.json(++calls===1?page(first,{size:100,totalElements:101}):second)},sourceId));
  assert.equal(calls,2);
 }
});

test('HB-T024-008: empty catalog is explicit and unreachable offsets fail instead of truncating',async()=>{
 assert.deepEqual(await api.loadScopeCatalog(fixture(page([],{size:100})).client,sourceId),{catalogRevision:revision,groups:[]});
 let calls=0;
 await assert.rejects(()=>api.loadScopeCatalog({request:async()=>{calls++;return Response.json(page(Array.from({length:100},(_,i)=>({...group,cohortId:String(i+1)})),{size:100,totalElements:100101}));}},sourceId));
 assert.equal(calls,1,'reject before promising a partial catalog');
});

test('HB-T024-023/029: scope reads keep the parent ETag, immutable snapshot and explicit legacy null',async()=>{
 for(const saved of [scope,null]){
  const body={roundId,roundEtag:'"parent_1"',scope:saved};
  const {client,calls}=fixture(body);
  assert.deepEqual(await api.readRoundScope(client,roundId),body);
  assert.equal(calls[0].path,'/api/ctsv/hoc-bong/kkht/dot-xet/'+roundId+'/pham-vi');
 }
});

test('HB-T024-023: invalid scope IDs, weak tags, incomplete snapshots and duplicates are rejected',async()=>{
 const valid={roundId,roundEtag:'"v1"',scope};
 for(const body of [{...valid,roundId:'1'},{...valid,roundEtag:'W/"v1"'},{...valid,scope:undefined},{...valid,scope:{...scope,id:1}},{...valid,scope:{...scope,mode:'bad'}},{...valid,scope:{...scope,groups:[]}},{...valid,scope:{...scope,groups:[group,group]}},{...valid,scope:{...scope,createdAt:'invalid'}}])await assert.rejects(()=>api.readRoundScope(fixture(body).client,roundId));
 const {client,calls}=fixture(valid);await assert.rejects(()=>api.readRoundScope(client,'1/../../toi'));assert.equal(calls.length,0);
});

test('HB-T024-023: context retries only GETs until root and scope belong to the same parent version',async()=>{
 const calls=[];
 const client={request:async(path,options)=>{
  calls.push({path,options});
  if(path.endsWith('/pham-vi'))return Response.json({roundId,roundEtag:'"v2"',scope});
  return Response.json({...detail,name:calls.length===1?'Cũ':'Hiện hành'},{headers:{ETag:calls.length===1?'"v1"':'"v2"'}});
 }};
 const result=await api.readRoundContext(client,roundId);
 assert.equal(result.round.name,'Hiện hành');assert.equal(result.etag,'"v2"');assert.deepEqual(result.scope,scope);
 assert.equal(calls.length,4);assert(calls.every(call=>!call.options.method||call.options.method==='GET'));
});

test('HB-T024-023: continuously changing parents stop after three read pairs without mutation',async()=>{
 let calls=0;
 await assert.rejects(()=>api.readRoundContext({request:async(path)=>{calls++;return path.endsWith('/pham-vi')?Response.json({roundId,roundEtag:'"v2"',scope}):Response.json(detail,{headers:{ETag:'"v1"'}});}},roundId));
 assert.equal(calls,6);
});

test('HB-T024-023/029: context permits legacy scope only with the matching strong parent tag',async()=>{
 assert.deepEqual(await api.readRoundContext({request:async path=>path.endsWith('/pham-vi')?Response.json({roundId,roundEtag:'"v1"',scope:null}):Response.json(detail,{headers:{ETag:'"v1"'}})},roundId),{round:detail,etag:'"v1"',scope:null});
 await assert.rejects(()=>api.readRoundContext({request:async path=>path.endsWith('/pham-vi')?Response.json({roundId,roundEtag:'"v1"',scope}):Response.json(detail)},roundId));
});

test('HB-T024-020/022: recovery looks up only the original command key and returns committed round ID',async()=>{
 const {client,calls}=fixture({requestId:key,roundId});
 const caller=new AbortController();
 assert.deepEqual(await api.lookupCreation(client,key,caller.signal),{requestId:key,roundId});
 assert.equal(calls[0].path,'/api/ctsv/hoc-bong/kkht/lenh-tao-dot/'+key);
 caller.abort();assert.equal(calls[0].options.signal.aborted,true);assert.equal(calls.length,1);
});

test('HB-T024-020/021/022: lookup404 means not found while all other failures remain failures',async()=>{
 assert.equal(await api.lookupCreation({request:async()=>{throw new ApiError(404,'NOT_FOUND');}},key),null);
 assert.equal(await api.lookupCreation(fixture({code:'NOT_FOUND'},{},404).client,key),null);
 for(const error of [new ApiError(403,'FORBIDDEN'),new ApiError(503,'UNAVAILABLE'),new TypeError('Synthetic lost response')])await assert.rejects(()=>api.lookupCreation({request:async()=>{throw error;}},key),value=>value===error);
 for(const body of [{requestId:key,roundId:123},{requestId:'d2cb72b2-27d3-4d5c-9e96-f5b9111bf1b3',roundId},{}])await assert.rejects(()=>api.lookupCreation(fixture(body).client,key));
 const {client,calls}=fixture({requestId:key,roundId});for(const value of ['',key.toUpperCase()])await assert.rejects(()=>api.lookupCreation(client,value));assert.equal(calls.length,0);
});
