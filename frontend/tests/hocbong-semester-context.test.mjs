import test from 'node:test';
import assert from 'node:assert/strict';
import * as api from '../src/features/hocbong/api/rounds.ts';
import {ApiError} from '../src/shared/auth/auth-client.mjs';

const source={sourceSemesterId:'9007199254740993',code:'2510',name:null,academicYear:'2025-2026',semesterOrder:1,startDate:'2025-08-01',endDate:'2026-01-15',scholarshipSemesterId:null,selectable:true,unavailableReason:null};
function fixture(body){const calls=[];return{calls,client:{request:async(path,options)=>{calls.push({path,options});return Response.json(body);}}};}
const requireReader=name=>{assert.equal(typeof api[name],'function',name+' must implement the T02.5 read contract');return api[name];};

test('HB-T024-006: default uses its exact GET and preserves nullable fields/bigint',async()=>{
 const read=requireReader('readDefaultSemester'),{client,calls}=fixture({semester:source,reason:null});
 assert.deepEqual(await read(client),{semester:source,reason:null});
 assert.equal(calls[0].path,'/api/ctsv/hoc-bong/hoc-ky-nguon/mac-dinh');
 assert(!calls[0].options.method||calls[0].options.method==='GET');
 assert.equal(calls[0].options.body,undefined);
});
test('HB-T024-006: empty default is explicit, inconsistent/malformed success fails closed',async()=>{
 const read=requireReader('readDefaultSemester');
 assert.deepEqual(await read(fixture({semester:null,reason:'NO_ELIGIBLE_SEMESTER'}).client),{semester:null,reason:'NO_ELIGIBLE_SEMESTER'});
 for(const body of [{},{semester:null,reason:null},{semester:source,reason:'NO_ELIGIBLE_SEMESTER'},{semester:{...source,selectable:false,unavailableReason:'SOURCE_NOT_PRESENT'},reason:null},{semester:source}])await assert.rejects(()=>read(fixture(body).client));
});
test('HB-T024-006: direct source keeps string ID and rejects another source representation',async()=>{
 const read=requireReader('readSourceSemester'),{client,calls}=fixture(source);
 assert.deepEqual(await read(client,source.sourceSemesterId),source);
 assert.equal(calls[0].path,'/api/ctsv/hoc-bong/hoc-ky-nguon/'+source.sourceSemesterId);
 await assert.rejects(()=>read(fixture(source).client,'21'));
});
test('HB-T024-006: invalid source IDs never reach transport',async()=>{
 const read=requireReader('readSourceSemester'),{client,calls}=fixture(source);
 for(const value of ['','0','01','-1','1/../../toi','9223372036854775808',9007199254740993])await assert.rejects(()=>read(client,value));
 assert.equal(calls.length,0);
});
test('HB-T024-006: direct nonselectable source retains its explanation',async()=>{
 const read=requireReader('readSourceSemester'),value={...source,selectable:false,unavailableReason:'SOURCE_DATES_UNVERIFIED',startDate:null};
 assert.deepEqual(await read(fixture(value).client,source.sourceSemesterId),value);
 await assert.rejects(()=>read(fixture({...value,unavailableReason:null}).client,source.sourceSemesterId));
});
test('HB-T024-006: source dependency/security errors are not empty defaults',async()=>{
 const read=requireReader('readDefaultSemester'),direct=requireReader('readSourceSemester');
 for(const status of [403,404,503]){const error=new ApiError(status,'SYNTHETIC');const client={request:async()=>{throw error;}};await assert.rejects(()=>read(client),e=>e===error);await assert.rejects(()=>direct(client,'21'),e=>e===error);}
});
test('HB-T024-006: both reads forward caller cancellation and retain a deadline',async t=>{
 const read=requireReader('readDefaultSemester'),direct=requireReader('readSourceSemester');
 const deadlines=[];t.mock.method(AbortSignal,'timeout',ms=>{assert.equal(ms,20000);const c=new AbortController();deadlines.push(c);return c.signal;});
 for(const run of [(c,s)=>read(c,s),(c,s)=>direct(c,'21',s)]){
  const caller=new AbortController(),calls=[];
  // Transport captures the real signal; the source response shape is immaterial after cancellation.
  const pending=run({request:async(path,options)=>{calls.push({path,options});return new Promise((_resolve,reject)=>options.signal.addEventListener('abort',()=>reject(options.signal.reason),{once:true}));}},caller.signal);
  const rejection=assert.rejects(pending,e=>e.name==='AbortError');caller.abort();await rejection;assert(calls[0].options.signal.aborted);
 }
 assert.equal(deadlines.length,2);
});
