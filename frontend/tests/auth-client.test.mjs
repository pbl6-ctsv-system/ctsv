import test from 'node:test';import assert from 'node:assert/strict';
import {createAuthClient} from '../src/shared/auth-client.mjs';
function browser(){
 const storage=new Map();let queue=Promise.resolve();
 return {locks:{request:(_,fn)=>{const result=queue.then(fn);queue=result.catch(()=>{});return result}},
  storage:{getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)},
  channel:{postMessage(){}}};
}
test('logout uses cookie and never attaches access bearer or retries',async()=>{
 const requests=[];const env=browser();
 const fetcher=async(url,init)=>{requests.push([url,init]);return new Response(url.endsWith('dang-xuat')?null:JSON.stringify({accessToken:'synthetic',expiresIn:900}),{status:url.endsWith('dang-xuat')?204:200})};
 const client=createAuthClient({...env,fetcher});await client.login('synthetic','synthetic');await client.logout();
 assert.equal(requests.length,2);assert.equal(requests[1][1].headers.Authorization,undefined);assert.equal(requests[1][1].credentials,'include');
});
test('lost refresh response blocks later refresh until explicit login',async()=>{
 let calls=0;const env=browser();const client=createAuthClient({...env,fetcher:async()=>{calls++;throw new TypeError('lost response')}});
 await assert.rejects(client.refresh());await assert.rejects(client.refresh());assert.equal(calls,1);
});
test('same-tab refresh is single-flight and never persists access token',async()=>{
 const env=browser();let calls=0;let release;const wait=new Promise(r=>release=r);
 const client=createAuthClient({...env,fetcher:async()=>{calls++;await wait;return new Response(JSON.stringify({accessToken:'test-access',expiresIn:900}),{status:200})}});
 const a=client.refresh(),b=client.refresh();release();await Promise.all([a,b]);assert.equal(calls,1);
 assert.equal(env.storage.getItem('ctsv.auth.uncertain'),null);assert.equal(client.token(),'test-access');
});
test('multiple tabs serialize refresh using current cookie, not the previous tab token',async()=>{
 const env=browser();let cookie=0;let running=0;let peak=0;const observed=[];
 const fetcher=async()=>{running++;peak=Math.max(peak,running);observed.push(cookie);await new Promise(r=>setTimeout(r,5));cookie++;running--;return new Response(JSON.stringify({accessToken:'memory-'+cookie,expiresIn:900}),{status:200})};
 const a=createAuthClient({...env,fetcher,channel:{postMessage(){}}}),b=createAuthClient({...env,fetcher,channel:{postMessage(){}}});
 await Promise.all([a.refresh(),b.refresh()]);assert.equal(peak,1);assert.deepEqual(observed,[0,1]);assert.equal(a.token(),'memory-1');assert.equal(b.token(),'memory-2');
});
test('uncertain rotation in one tab blocks refresh in a different tab',async()=>{
 const env=browser();let calls=0;const fetcher=async()=>{calls++;throw new TypeError('connection lost')};
 const a=createAuthClient({...env,fetcher}),b=createAuthClient({...env,fetcher,channel:{postMessage(){}}});
 await assert.rejects(a.refresh());await assert.rejects(b.refresh());assert.equal(calls,1);
});
test('revoked bearer request never triggers an automatic refresh or replay',async()=>{
 const env=browser();let calls=0;const client=createAuthClient({...env,fetcher:async(url)=>{calls++;return url.endsWith('dang-nhap')?new Response(JSON.stringify({accessToken:'test',expiresIn:900})):new Response(JSON.stringify({code:'TOKEN_REVOKED'}),{status:401})}});
 await client.login('test','test');await assert.rejects(client.request('/api/ctsv/nen-tang/sinh-vien'));assert.equal(calls,2);assert.equal(client.token(),null);
});
