import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import ts from 'typescript';
const source=await readFile(new URL('../src/features/shcn/api/rounds.ts',import.meta.url),'utf8');
const templateSource=await readFile(new URL('../src/features/shcn/api/minutes-templates.ts',import.meta.url),'utf8');
const templateModule='data:text/javascript;base64,'+Buffer.from(ts.transpileModule(templateSource,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText).toString('base64');
const outputText=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText.replace(/from ['"]\.\/minutes-templates['"]/g,'from '+JSON.stringify(templateModule));
const {roundsApi}=await import('data:text/javascript;base64,'+Buffer.from(outputText).toString('base64'));
function client(){const calls=[];return {calls,request:async(path,options)=>{calls.push({path,options});return new Response(JSON.stringify({items:[],page:0,size:25,totalElements:0}),{headers:{'X-SHCN-Can-Prepare':'false'}});}};}
test('class lookup carries semester selector and server pagination, keeps codes as strings',async()=>{
 const c=client(),api=roundsApi(c),abort=new AbortController();
 assert.equal(typeof api.classes,'function');
 await api.classes('0010','KHOA_HOC','K/2024','lớp',1,abort.signal);
 const url=new URL(c.calls[0].path,'https://fixture');
 assert.equal(url.pathname,'/api/shcn/tra-cuu/lop-theo-ky');
 assert.deepEqual(Object.fromEntries(url.searchParams),{maHocKy:'0010',loaiDoiTuong:'KHOA_HOC',maDoiTuong:'K/2024',q:'lớp',page:'1',size:'25',sort:'id:asc'});
 assert.equal(c.calls[0].options.signal,abort.signal);assert.equal(c.calls.length,1);
});
test('student lookup uses receiving class code and never requests other students separately',async()=>{
 const c=client(),api=roundsApi(c);assert.equal(typeof api.students,'function');
 const result=await api.students('0010','RL/1','',0);
 const url=new URL(c.calls[0].path,'https://fixture');assert.equal(url.pathname,'/api/shcn/tra-cuu/sinh-vien-theo-ky');
 assert.equal(url.searchParams.get('maLop'),'RL/1');assert(!url.searchParams.has('q'));assert.equal(result.totalElements,0);assert.equal(c.calls.length,1);
});
test('saved preview preserves root version and cancellation context',async()=>{
 const c=client(),api=roundsApi(c),abort=new AbortController();assert.equal(typeof api.audiences,'function');
 await api.audiences('9007199254740999','',0,abort.signal);
 assert.equal(new URL(c.calls[0].path,'https://fixture').pathname,'/api/shcn/dot-sinh-hoat/9007199254740999/doi-tuong');
 assert.equal(c.calls[0].options.signal,abort.signal);
});
test('source errors propagate instead of becoming an empty successful roster',async()=>{
 const failure=Object.assign(new Error('fixture'),{status:503,code:'DEPENDENCY_UNAVAILABLE'});
 const api=roundsApi({request:async()=>{throw failure;}});assert.equal(typeof api.students,'function');
 await assert.rejects(()=>api.students('0010','L1','',0),e=>e===failure);
});


test('template reads carry cancellation and save uses singleton PUT with root token',async()=>{
 const c=client(),api=roundsApi(c),abort=new AbortController();await api.templates.read('7',abort.signal);
 assert.equal(c.calls[0].path,'/api/shcn/dot-sinh-hoat/7/mau-bien-ban');assert.equal(c.calls[0].options.signal,abort.signal);
 const body={expectedVersion:'AAAAAAAAAAE=',tenMau:'Biên bản',muc:[]};await api.templates.save('7',body);
 assert.equal(c.calls[1].options.method,'PUT');assert.deepEqual(JSON.parse(c.calls[1].options.body),body);assert.equal(c.calls.length,2);
});
test('oversized template write fails locally without any HTTP request',async()=>{
 const c=client(),api=roundsApi(c);await assert.rejects(api.templates.save('7',{tenMau:'x'.repeat(1048577),muc:[],expectedVersion:'AAAAAAAAAAE='}),/1 MiB/);assert.equal(c.calls.length,0);
});
