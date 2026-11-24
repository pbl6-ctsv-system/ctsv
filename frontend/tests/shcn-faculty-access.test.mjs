import test from 'node:test';
import assert from 'node:assert/strict';
import {probeFacultyScope} from '../src/features/shcn/api/faculty-access.mjs';
const signal=new AbortController().signal;
test('specialist without faculty capability does not call faculty API',async()=>{
 let calls=0;
 const result=await probeFacultyScope({request:async()=>{calls++;throw new Error('unexpected faculty request');}},[{code:'shcn.dot.prepare',active:true}],signal);
 assert.deepEqual(result,{khoa:[]});assert.equal(calls,0);
});
test('active faculty read or prepare permission still requires real scope response',async()=>{
 for(const code of ['shcn.phanCongKhoa.read','shcn.phanCongKhoa.prepare']){
  const scope={khoa:[{maKhoa:'K1',duocDoc:true,duocLapNhap:false}]};let calls=0;
  const result=await probeFacultyScope({request:async(url,options)=>{calls++;assert.equal(url,'/api/shcn/khoa/pham-vi');assert.equal(options.signal,signal);return {json:async()=>scope};}},[{code,active:true},{code:'shcn.dot.prepare',active:true}],signal);
  assert.deepEqual(result,scope);assert.equal(calls,1);
 }
});
test('inactive faculty permissions and unrelated accounts do not issue faculty requests',async()=>{
 for(const functions of [[],[{code:'shcn.phanCongKhoa.read',active:false}],[{code:'shcn.phanCongKhoa.other',active:true}]]){
  const result=await probeFacultyScope({request:async()=>assert.fail('unexpected faculty request')},functions,signal);assert.deepEqual(result,{khoa:[]});
 }
});
test('real denial or dependency error is propagated without inventing scope',async()=>{
 for(const status of [401,403,503]){const failure=Object.assign(new Error('denied'),{status});await assert.rejects(()=>probeFacultyScope({request:async()=>{throw failure;}},[{code:'shcn.phanCongKhoa.read',active:true}],signal),e=>e===failure);}
});
