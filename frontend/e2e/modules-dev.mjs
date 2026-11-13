// Read-only domain checks against the normal DEV launcher. Auth login/refresh/logout are real.
import {chromium} from '@playwright/test';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.CTSV_DEV_HOME;
const config=JSON.parse((await readFile(path.join(home,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const ids=JSON.parse(await readFile(path.join(home,'identities.json'),'utf8'));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);
delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin=`https://127.0.0.1:${config.FrontendPort}`;
const evidence=path.join(home,'frontend-modules-evidence');await mkdir(evidence,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[],cases=[];
let status='FAIL';
async function check(id,fn){try{await fn();cases.push({id,status:'PASS'});console.log(id+' PASS');}catch(e){cases.push({id,status:'FAIL'});throw e;}}
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false});contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(12000);await page.goto(origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 const response=page.waitForResponse(r=>r.url().endsWith('/api/xac-thuc/toi'));
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 const me=await response;assert.equal(me.status(),200);assert.equal((await me.json()).accountId,ids[role].accountId);
 await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();return {page,context};
}
try{
 let student,specialist,admin;
 await check('MODULE01_REAL_HTTPS_LOGIN_AND_FOUNDATION',async()=>{
  student=await login('student');
  const response=student.page.waitForResponse(r=>r.url().includes('/api/ctsv/nen-tang/danh-muc?'));
  await student.page.getByRole('button',{name:'Tra cứu',exact:true}).click();assert.equal((await response).status(),200);
  await student.page.getByText('DEV-2026-1',{exact:true}).waitFor();
 });
 await check('MODULE02_FORBIDDEN_IS_NOT_EMPTY_SUCCESS',async()=>{
  await student.page.getByRole('button',{name:'Sinh viên',exact:true}).click();
  const response=student.page.waitForResponse(r=>r.url().includes('/api/ctsv/nen-tang/sinh-vien?'));
  await student.page.getByRole('button',{name:'Tra cứu',exact:true}).click();assert.equal((await response).status(),403);
  await student.page.getByRole('alert').filter({hasText:'không có quyền'}).waitFor();
 });
 await check('MODULE03_SPECIALIST_LOOKUP_AND_ETAG_READ',async()=>{
  specialist=await login('specialist');
  await specialist.page.getByRole('button',{name:'Sinh viên',exact:true}).click();
  await specialist.page.getByLabel('ID sinh viên',{exact:true}).fill(ids.studentId);
  await specialist.page.getByRole('button',{name:'Xem chi tiết',exact:true}).click();await specialist.page.getByText('DEV-SV-001',{exact:true}).waitFor();
  await specialist.page.getByRole('button',{name:'Xác minh & quyền',exact:true}).click();
  await specialist.page.getByLabel('ID đối tượng',{exact:true}).fill(ids.semesterId);
  const response=specialist.page.waitForResponse(r=>r.url().endsWith('/api/ctsv/nen-tang/hoc-ky/'+ids.semesterId));
  await specialist.page.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();const read=await response;assert.equal(read.status(),200);assert(read.headers().etag);
  await specialist.page.getByLabel('Thứ tự học kỳ',{exact:true}).waitFor();
 });
 await check('MODULE04_AUTH_ADMIN_ISOLATED_FROM_FOUNDATION',async()=>{
  admin=await login('admin');await admin.page.getByRole('button',{name:'Quản trị tài khoản',exact:true}).click();
  await admin.page.getByRole('button',{name:'Danh mục quyền Auth',exact:true}).click();await admin.page.getByText('AUTH_ADMIN',{exact:true}).waitFor();
  await admin.page.getByLabel('ID đối tượng',{exact:true}).fill(ids.student.accountId);
  const response=admin.page.waitForResponse(r=>r.url().endsWith('/api/xac-thuc/tai-khoan/'+ids.student.accountId));
  await admin.page.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();assert.equal((await response).status(),200);
  await admin.page.getByRole('button',{name:'Danh mục',exact:true}).click();
  await admin.page.getByRole('button',{name:'Quản trị tài khoản',exact:true}).click();
  assert.equal(await admin.page.getByLabel('ID đối tượng',{exact:true}).inputValue(),'');
  assert(await admin.page.getByRole('button',{name:'Lưu thay đổi đã xác minh',exact:true}).isDisabled());
  assert.equal(await admin.page.getByText('dev.sinhvien',{exact:true}).count(),0);
 });
 await check('MODULE05_SCOPED_CSS_AND_MOBILE',async()=>{
  const page=student.page;await page.getByRole('button',{name:'Danh mục',exact:true}).click();
  const outside=await page.evaluate(()=>{const b=document.createElement('button');b.textContent='outside application';document.body.append(b);const color=getComputedStyle(b).backgroundColor;b.remove();return color;});
  const inside=await page.getByRole('button',{name:'Tra cứu',exact:true}).evaluate(b=>getComputedStyle(b).backgroundColor);
  assert.equal(inside,'rgb(23, 107, 85)');assert.notEqual(outside,inside);
  await page.setViewportSize({width:360,height:800});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
 });
 await check('MODULE06_MULTITAB_REFRESH_AND_COOKIE_ONLY_LOGOUT',async()=>{
  const page=student.page,second=await student.context.newPage();await second.goto(origin);
  await Promise.all([page.evaluate(async()=>{const {createAuthClient}=await import('/src/shared/auth-client.mjs');await createAuthClient().refresh();}),second.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click()]);
  await second.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
  const cookies=await student.context.cookies(),cookie=cookies.find(c=>c.name==='ctsv_refresh');assert(cookie?.httpOnly&&cookie.secure&&cookie.sameSite==='Strict');
  const request=page.waitForRequest(r=>r.url().endsWith('/dang-xuat'));await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();
  const headers=await(await request).allHeaders();assert.equal(headers.authorization,undefined);assert(headers.cookie?.includes('ctsv_refresh='));
  await second.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
 });
 for(const person of [specialist,admin])await person.page.getByRole('button',{name:'Đăng xuất',exact:true}).click();
 status='PASS';
}catch(e){let message=String(e.message).replace(/eyJ[A-Za-z0-9_.-]+/g,'[TOKEN]');for(const value of Object.values(passwords))message=message.replaceAll(value,'[PASSWORD]');console.error(message);process.exitCode=1;}
finally{for(const c of contexts)await c.close();await browser.close();await writeFile(path.join(evidence,'browser.json'),JSON.stringify({status,cases,origin,database:'ctsv_dev',real:'Chrome + HTTPS Vite proxy + Boot DEV + SQL Server; no mock or TLS bypass'},null,2));}
