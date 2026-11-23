import test from 'node:test';
import assert from 'node:assert/strict';
import {readPdfBytes} from '../src/shared/ui/pdf/pdf-source.mjs';
import {geometryFromPixels,pixelsFromGeometry} from '../src/shared/ui/pdf/geometry.mjs';
import * as geometry from '../src/shared/ui/pdf/geometry.mjs';

const sample=new TextEncoder().encode('%PDF-1.7\nsynthetic bytes');
test('shared PDF uses the authenticated client and exact file endpoint once',async()=>{
 const calls=[],controller=new AbortController();
 const client={request:async(...args)=>{calls.push(args);return new Response(sample,{headers:{'Content-Type':'application/pdf'}});}};
 assert.deepEqual(await readPdfBytes(client,'42',controller.signal),sample);
 assert.equal(calls.length,1);assert.equal(calls[0][0],'/api/ctsv/tep/42/noi-dung');assert.equal(calls[0][1].signal,controller.signal);
});
test('non-PDF MIME is rejected rather than displayed or downloaded as PDF',async()=>{
 await assert.rejects(()=>readPdfBytes({request:async()=>new Response('{}',{headers:{'Content-Type':'application/json'}})},'42',new AbortController().signal),/không phải PDF/);
});
test('a PDF MIME with invalid bytes is rejected',async()=>{
 await assert.rejects(()=>readPdfBytes({request:async()=>new Response('not a PDF',{headers:{'Content-Type':'application/pdf'}})},'42',new AbortController().signal),/không phải PDF/);
});
test('auth and provider errors propagate unchanged; the viewer never retries a request itself',async()=>{
 for(const status of [401,403,404,503]){
  const failure=Object.assign(new Error('private request failed'),{status});let calls=0;
  await assert.rejects(()=>readPdfBytes({request:async()=>{calls++;throw failure;}},'42',new AbortController().signal),e=>e===failure);
  assert.equal(calls,1);
 }
});
test('already aborted selection never starts a request',async()=>{
 const controller=new AbortController();controller.abort();let calls=0;
 await assert.rejects(()=>readPdfBytes({request:async()=>{calls++;}},'42',controller.signal),{name:'AbortError'});assert.equal(calls,0);
});
test('late bytes from an aborted file cannot be accepted',async()=>{
 const controller=new AbortController();
 const response={headers:new Headers({'Content-Type':'application/pdf'}),arrayBuffer:async()=>{controller.abort();return sample.buffer;}};
 await assert.rejects(()=>readPdfBytes({request:async()=>response},'42',controller.signal),{name:'AbortError'});
});
test('each requested document returns its own bytes without a global cache',async()=>{
 const a=sample,b=new TextEncoder().encode('%PDF-1.7\nsecond document');let calls=0;
 const client={request:async path=>{calls++;return new Response(path.includes('/1/')?a:b,{headers:{'Content-Type':'application/pdf'}});}};
 const signal=new AbortController().signal;
 assert.deepEqual(await readPdfBytes(client,'1',signal),a);assert.deepEqual(await readPdfBytes(client,'2',signal),b);assert.equal(calls,2);
});

const rotated={convertToPdfPoint:(x,y)=>[20+y/2,30+x/2],convertToViewportPoint:(x,y)=>[(y-30)*2,(x-20)*2]};
test('shared geometry preserves rotation, scale, crop offsets and PDF page numbering',()=>{
 const rect={x:100,y:80,width:200,height:60};
 const box=geometryFromPixels(rotated,2,rect);
 assert.deepEqual(box,{page:2,x:60,y:80,width:30,height:100});assert.deepEqual(pixelsFromGeometry(rotated,box),rect);
});
test('the same PDF selection is correctly projected after changing viewport scale',()=>{
 const box={page:2,x:10,y:20,width:80,height:30};
 const scaled={convertToViewportPoint:(x,y)=>[x*3,900-y*3]};
 assert.deepEqual(pixelsFromGeometry(scaled,box),{x:30,y:750,width:240,height:90});
});
test('too-small and non-finite selections cannot produce a signable geometry',()=>{
 for(const rect of [{x:1,y:1,width:7,height:20},{x:1,y:1,width:20,height:7},{x:NaN,y:1,width:20,height:20}])assert.throws(()=>geometryFromPixels(rotated,1,rect));
});
test('keyboard selection stays inside a short landscape viewport',()=>{
 for(const viewport of [{width:320,height:117},{width:1000,height:1400}]){
  const rect=geometry.keyboardSelectionRect(viewport);
  assert.ok(rect.width>=8&&rect.height>=8);assert.ok(rect.x>=0&&rect.y>=0);
  assert.ok(rect.x+rect.width<=viewport.width);assert.ok(rect.y+rect.height<=viewport.height);
 }
});
test('keyboard selection asks for zoom instead of creating an impossible box',()=>{
 assert.throws(()=>geometry.keyboardSelectionRect({width:120,height:20}),/Phóng to PDF/);
});
