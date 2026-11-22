import {openNav,revealNav} from './navigation.mjs';
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
const contexts=[],cases=[],browserErrors=[];
let status='FAIL';
async function check(id,fn){try{await fn();cases.push({id,status:'PASS'});console.log(id+' PASS');}catch(e){cases.push({id,status:e.rateLimited?'BLOCKED':'FAIL'});throw e;}}
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false});contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(20000);
 page.on('pageerror',error=>browserErrors.push(error.name));
 page.on('console',message=>{
  // The permission case below intentionally exercises a real HTTP 403.
  if(message.type()!=='error'||/^Failed to load resource: .*\b403\b/.test(message.text()))return;
  const code=/^Failed to load resource: .*status of (\d{3})\b/.exec(message.text())?.[1];
  const source=message.location().url;
  const pathname=(source?new URL(source,origin).pathname:'').replace(/\/[0-9a-f]{8}-[0-9a-f-]{27,}\b/gi,'/[ID]');
  browserErrors.push(code?'Console HTTP '+code+' '+pathname:'Unexpected browser console error '+pathname);
 });
 page.on('response',response=>{
  const {pathname}=new URL(response.url());
  if(response.status()===404&&(pathname.startsWith('/api/')||response.request().resourceType()==='document'))browserErrors.push('HTTP 404 '+pathname);
 });
 await page.goto(origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 const loginResponse=page.waitForResponse(r=>r.url().endsWith('/api/xac-thuc/dang-nhap'));
 const accountResponse=page.waitForResponse(r=>r.url().endsWith('/api/xac-thuc/toi'));
 // If login fails, the app will not request AUTH04.
 void accountResponse.catch(()=>{});
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 const signedIn=await loginResponse;
 if(signedIn.status()===429){
  const error=new Error(`DEV ${role} login rate-limited (Retry-After ${signedIn.headers()['retry-after']??'n/a'})`);
  error.rateLimited=true;
  throw error;
 }
 assert.equal(signedIn.status(),200,`DEV ${role} login returned ${signedIn.status()}`);
 const me=await accountResponse;assert.equal(me.status(),200);assert.equal((await me.json()).accountId,ids[role].accountId);
 await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
 assert.equal(new URL(page.url()).pathname,'/');
 await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();return {page,context};
}
try{
 let student,specialist,admin;
 await check('MODULE01_REAL_HTTPS_LOGIN_AND_FOUNDATION',async()=>{
  student=await login('student');
  const {page}=student;
  const menu=page.getByRole('navigation',{name:'Chức năng CTSV'});
  const trigger=menu.getByRole('button',{name:'Miễn giảm học phí',exact:true});
  await trigger.waitFor();
  assert.deepEqual((await menu.locator(':scope > ul > li').evaluateAll(items=>items.map(item=>
   item.firstElementChild.getAttribute('aria-label')??item.firstElementChild.textContent.trim(),
  ))),['Trang chủ','Điểm rèn luyện — Chưa triển khai','Miễn giảm học phí','Lịch SHCN — Chưa triển khai','Thông báo','Học bổng — Chưa triển khai']);
  assert.equal(await menu.locator('[aria-disabled="true"]:not(a):not(button)').count(),3);
  const rounds=await revealNav(page,'Đợt xét MGHP');
  await rounds.waitFor();
  await page.keyboard.press('Escape');
  assert.equal(await trigger.getAttribute('aria-expanded'),'false');
  assert(await trigger.evaluate(button=>document.activeElement===button));
  await trigger.click();
  await page.getByRole('heading',{name:'Trang chủ CTSV'}).click();
  assert.equal(await trigger.getAttribute('aria-expanded'),'false');
  const response=page.waitForResponse(r=>r.url().includes('/api/ctsv/mghp/dot-xet?'))
   .catch(error=>{throw new Error('No round list API response after navigating',{cause:error});});
  await openNav(page,'Đợt xét MGHP');
  assert.equal((await response).status(),200);
  await page.getByRole('heading',{name:'Đợt xét miễn giảm học phí'}).waitFor();
  assert.equal(await trigger.getAttribute('data-active'),'true');
  assert.equal(await trigger.getAttribute('aria-expanded'),'false');
  await page.reload();
  await page.getByRole('heading',{name:'Đợt xét miễn giảm học phí'}).waitFor();
  assert.equal(new URL(page.url()).pathname,'/mghp/dot-xet');
  await openNav(page,'Thông báo');
  await page.getByRole('heading',{name:'Thông báo của tôi'}).waitFor();
  await page.goBack();
  await page.getByRole('heading',{name:'Đợt xét miễn giảm học phí'}).waitFor();
  await page.goForward();
  await page.getByRole('heading',{name:'Thông báo của tôi'}).waitFor();
  await openNav(page,'Hồ sơ của tôi');
  await page.getByRole('heading',{name:'Hồ sơ của tôi'}).waitFor();
 });
 await check('MODULE02_FORBIDDEN_IS_NOT_EMPTY_SUCCESS',async()=>{
  const denied=await student.page.evaluate(async()=>{
   const {createAuthClient}=await import('/src/shared/auth-client.mjs');
   const client=createAuthClient();
   await client.refresh();
   try{await client.request('/api/ctsv/nen-tang/sinh-vien');return 200;}
   catch(error){return error.status;}
  });
  assert.equal(denied,403);
  await student.page.goto(origin+'/mghp/chinh-sach');
  await student.page.getByRole('alert').filter({hasText:'Bạn không có quyền truy cập trang này.'}).waitFor();
  assert.equal(await student.page.getByRole('link',{name:'Chính sách MGHP',includeHidden:true}).count(),0);
  await openNav(student.page,'Trang chủ');
 });
 await check('MODULE03_SPECIALIST_LOOKUP_AND_ETAG_READ',async()=>{
  specialist=await login('specialist');
  const policy=await revealNav(specialist.page,'Chính sách MGHP');
  await policy.waitFor();
  assert.equal(await specialist.page.getByRole('button',{name:'Quản trị',exact:true}).count(),0);
  await openNav(specialist.page,'Sinh viên');
  await specialist.page.getByLabel('ID sinh viên',{exact:true}).fill(ids.studentId);
  await specialist.page.getByRole('button',{name:'Xem chi tiết',exact:true}).click();await specialist.page.getByText('DEV-SV-001',{exact:true}).waitFor();
  await openNav(specialist.page,'Xác minh dữ liệu');
  assert.equal(await specialist.page.getByRole('link',{name:'Phân quyền MGHP',exact:true,includeHidden:true}).count(),0);
  await specialist.page.getByLabel('ID đối tượng',{exact:true}).fill(ids.semesterId);
  const response=specialist.page.waitForResponse(r=>r.url().endsWith('/api/ctsv/nen-tang/hoc-ky/'+ids.semesterId));
  await specialist.page.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();const read=await response;assert.equal(read.status(),200);assert(read.headers().etag);
  await specialist.page.getByLabel('Thứ tự học kỳ',{exact:true}).waitFor();
  await openNav(specialist.page,'Đợt xét MGHP');
  await specialist.page.getByRole('button',{name:'Tạo đợt xét',exact:true}).waitFor();
  await openNav(specialist.page,'Tiếp nhận & bổ sung hồ sơ');
  await specialist.page.getByRole('heading',{name:'Tiếp nhận & bổ sung hồ sơ MGHP'}).waitFor();
  assert.equal(await specialist.page.getByLabel('Đợt kê khai',{exact:true}).count(),0);
 });
 await check('MODULE04_AUTH_ADMIN_ISOLATED_FROM_FOUNDATION',async()=>{
  admin=await login('admin');
  await openNav(admin.page,'Danh mục quyền Auth');
  await admin.page.getByRole('button',{name:'Đọc danh mục quyền Auth',exact:true}).click();await admin.page.getByText('AUTH_ADMIN',{exact:true}).waitFor();
  await openNav(admin.page,'Quản trị tài khoản');
  await admin.page.getByLabel('ID đối tượng',{exact:true}).fill(ids.student.accountId);
  const response=admin.page.waitForResponse(r=>r.url().endsWith('/api/xac-thuc/tai-khoan/'+ids.student.accountId));
  await admin.page.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();assert.equal((await response).status(),200);
  assert.equal(await admin.page.getByRole('button',{name:'Miễn giảm học phí',exact:true}).count(),0);
  await openNav(admin.page,'Phân quyền MGHP');
  await openNav(admin.page,'Quản trị tài khoản');
  assert.equal(await admin.page.getByLabel('ID đối tượng',{exact:true}).inputValue(),'');
  assert(await admin.page.getByRole('button',{name:'Lưu thay đổi đã xác minh',exact:true}).isDisabled());
  assert.equal(await admin.page.getByText('dev.sinhvien',{exact:true}).count(),0);
 });
 await check('MODULE05_SCOPED_CSS_AND_MOBILE',async()=>{
  const page=specialist.page;await openNav(page,'Danh mục');
  const outside=await page.evaluate(()=>{const b=document.createElement('button');b.textContent='outside application';document.body.append(b);const color=getComputedStyle(b).backgroundColor;b.remove();return color;});
  const inside=await page.getByRole('button',{name:'Tra cứu',exact:true}).evaluate(b=>getComputedStyle(b).backgroundColor);
  assert.equal(inside,'rgb(23, 107, 85)');assert.notEqual(outside,inside);
  await page.setViewportSize({width:360,height:800});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  const link=await revealNav(page,'Chính sách MGHP');
  const box=await link.boundingBox();
  assert(box&&box.x>=0&&box.x+box.width<=360);
  await link.click();
  await page.getByRole('heading',{name:'Chính sách miễn giảm học phí'}).waitFor();
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
 assert.deepEqual(browserErrors,[]);
 status='PASS';
}catch(e){if(e.rateLimited)status='BLOCKED';let message=String(e.message).replace(/eyJ[A-Za-z0-9_.-]+/g,'[TOKEN]');for(const value of Object.values(passwords))message=message.replaceAll(value,'[PASSWORD]');console.error(message);process.exitCode=1;}
finally{for(const c of contexts)await c.close();await browser.close();await writeFile(path.join(evidence,'browser.json'),JSON.stringify({status,cases,origin,database:'ctsv_dev',real:'Chrome + HTTPS Vite proxy + Boot DEV + SQL Server; no mock or TLS bypass'},null,2));}
