// Local-dev acceptance only. Does not rerun P04 acceptance or use its test host/database.
import {chromium} from '@playwright/test';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.CTSV_DEV_HOME;
const config=JSON.parse(await readFile(path.join(home,'config.json'),'utf8').then(s=>s.replace(/^\uFEFF/,'')));
const identities=JSON.parse(await readFile(path.join(home,'identities.json'),'utf8'));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);
delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin='https://127.0.0.1:'+config.FrontendPort,backend='https://127.0.0.1:'+config.BackendPort;
const phase=process.argv[2]??'before';
// Reproducible without the author's sibling documentation/evidence checkout.
const evidence=path.resolve(process.env.CTSV_DEV_SMOKE_EVIDENCE??path.join(home,'evidence'));
await mkdir(evidence,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});
const report={phase,browser:browser.version(),database:'localhost\\DEV2022 / ctsv_dev',origin,backend,real:'Normal Boot dev application + Hikari/JPA + SQL Server; no test host/stub/TLS bypass',cases:[]};
const contexts=[];
function safe(text){let s=String(text).replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]').replace(/ctsv_refresh=[A-Za-z0-9_-]+/g,'ctsv_refresh=[REDACTED]');for(const pw of Object.values(passwords))s=s.replaceAll(pw,'[REDACTED]');return s;}
async function check(id,fn){try{await fn();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}catch(e){report.cases.push({id,status:'FAIL',error:safe(e.message)});throw e;}}
async function page(role){const context=await browser.newContext({ignoreHTTPSErrors:false});contexts.push(context);const p=await context.newPage();p.setDefaultTimeout(12000);await p.goto(origin);await p.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);await p.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);const login=p.waitForResponse(r=>r.url().endsWith('/dang-nhap'));await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();const response=await login;assert.equal(response.status(),200);const issue=await response.json();await p.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();return{p,context,issue};}
try{
 if(phase==='before'){
  let sv,cv,admin;
  await check('DEV01_LOGIN_SYNTHETIC_ROLES_AND_P04_DATABASE',async()=>{
   sv=await page('student');cv=await page('specialist');admin=await page('admin');
   for(const [key,v]of [['student',sv],['specialist',cv],['admin',admin]])assert.equal(v.issue.accountId,identities[key].accountId);
   await sv.p.getByRole('button',{name:'Tra cứu',exact:true}).click();await sv.p.getByText('DEV-2026-1',{exact:true}).waitFor();
   await cv.p.getByRole('button',{name:'Sinh viên',exact:true}).click();await cv.p.getByLabel('ID sinh viên',{exact:true}).fill(identities.studentId);await cv.p.getByRole('button',{name:'Xem chi tiết',exact:true}).click();await cv.p.getByText('DEV-SV-001',{exact:true}).waitFor();
   await admin.p.getByRole('button',{name:'Quản trị tài khoản',exact:true}).click();await admin.p.getByRole('button',{name:'Danh mục quyền Auth',exact:true}).click();await admin.p.getByText('AUTH_ADMIN',{exact:true}).waitFor();
  });
  await check('DEV02_HTTPS_HTTPONLY_COOKIE_CSRF_CORS',async()=>{
   const cookie=(await sv.context.cookies()).find(c=>c.name==='ctsv_refresh');assert(cookie.httpOnly&&cookie.secure);assert.equal(cookie.sameSite,'Strict');assert.equal(cookie.path,'/api/xac-thuc');
   const csrf=await sv.p.evaluate(async()=>{const r=await fetch('/api/xac-thuc/lam-moi-token',{method:'POST',credentials:'include'});return r.status});assert.equal(csrf,403);
   assert.equal((await sv.context.cookies()).find(c=>c.name==='ctsv_refresh').value,cookie.value);
   const cors=await cv.p.evaluate(async backend=>{const r=await fetch(backend+'/api/xac-thuc/lam-moi-token',{method:'POST',credentials:'include',headers:{'X-CSRF-Protection':'1'}});return{status:r.status,allow:r.headers.get('access-control-allow-origin')};},backend);assert.equal(cors.status,200);
   const hostile=await sv.context.newPage();await hostile.goto(backend+'/api/xac-thuc/toi');
   assert.equal(await hostile.evaluate(async origin=>{try{await fetch(origin+'/api/xac-thuc/lam-moi-token',{method:'POST',credentials:'include',headers:{'X-CSRF-Protection':'1'}});return false}catch{return true}},origin),true);await hostile.close();
   report.cookie={httpOnly:cookie.httpOnly,secure:cookie.secure,sameSite:cookie.sameSite,path:cookie.path};
  });
  await check('DEV03_MULTITAB_REFRESH_COOKIE_LOGOUT',async()=>{
   const second=await sv.context.newPage();await second.goto(origin);
   await Promise.all([sv.p.evaluate(async()=>{const{createAuthClient}=await import('/src/shared/auth-client.mjs');await createAuthClient().refresh()}),second.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click()]);
   await second.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
   const request=sv.p.waitForRequest(r=>r.url().endsWith('/dang-xuat'));await sv.p.getByRole('button',{name:'Đăng xuất',exact:true}).click();const headers=await(await request).allHeaders();assert.equal(headers.authorization,undefined);assert(headers.cookie.includes('ctsv_refresh='));await second.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();assert(!(await sv.context.cookies()).some(c=>c.name==='ctsv_refresh'));
  });
  await check('DEV04_SAVE_PRIVATE_RESTART_PROOF',async()=>{
   const next=await page('student');await next.context.storageState({path:path.join(home,'restart-browser.json')});await writeFile(path.join(home,'restart-proof.json'),JSON.stringify({access:next.issue.accessToken,accountId:next.issue.accountId,thumbprint:config.Thumbprint}));
  });
 }else{
  await check('DEV05_RESTART_PRESERVES_JWT_COOKIE_IDENTITY',async()=>{
   const proof=JSON.parse(await readFile(path.join(home,'restart-proof.json'),'utf8'));
   assert.equal(proof.thumbprint,config.Thumbprint);
   const context=await browser.newContext({ignoreHTTPSErrors:false,storageState:path.join(home,'restart-browser.json')});contexts.push(context);const p=await context.newPage();await p.goto(origin);
   const old=await p.evaluate(async token=>{const r=await fetch('/api/xac-thuc/toi',{headers:{Authorization:'Bearer '+token}});return{status:r.status,accountId:(await r.json()).accountId}},proof.access);assert.equal(old.status,200);assert.equal(old.accountId,proof.accountId);
   const refresh=p.waitForResponse(r=>r.url().endsWith('/lam-moi-token'));await p.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click();assert.equal((await refresh).status(),200);await p.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
   await p.getByRole('button',{name:'Tra cứu',exact:true}).click();await p.getByText('DEV-2026-1',{exact:true}).waitFor();
   await p.getByRole('button',{name:'Đăng xuất',exact:true}).click();await p.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
  });
 }
 report.status='PASS';
}catch(e){report.status='FAIL';report.error=safe(e.message);process.exitCode=1;console.error('Dev smoke failed; see sanitized JSON');}
finally{for(const c of contexts)await c.close();await browser.close();await writeFile(path.join(evidence,'browser-'+phase+'.json'),JSON.stringify(report,null,2));}
