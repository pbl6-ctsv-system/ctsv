import {openNav} from './navigation.mjs';
import {chromium} from '@playwright/test';
import {readFile,writeFile,access} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';
const dir=path.resolve('../.local/p04-tls');
const fixture=JSON.parse(await readFile(path.join(dir,'browser-fixture.json'),'utf8'));
const evidence=path.resolve('../../../nghiepvu/.analysis/runtime/p04/20261112/wasm');
const browser=await chromium.launch({channel:'chrome',headless:true});
const report={browser:browser.version(),tls:'ignoreHTTPSErrors=false; trusted exact TEST certificate',mode:'REAL HTTPS/Auth/Foundation/SQL Server; no stub',cases:[]};
const contexts=[];
const safe=text=>String(text).replaceAll(fixture.password,'[REDACTED]').replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]').replace(/(ctsv_refresh=)[A-Za-z0-9_-]+/g,'$1[REDACTED]');
async function check(id,fn){try{await fn();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}catch(e){report.cases.push({id,status:'FAIL',error:safe(e.message)});throw e;}}
async function login(user){const c=await browser.newContext({ignoreHTTPSErrors:false});contexts.push(c);const p=await c.newPage();p.setDefaultTimeout(15000);await p.goto('https://127.0.0.1:5174');await p.getByLabel('Tên đăng nhập',{exact:true}).fill(user.username);await p.getByLabel('Mật khẩu',{exact:true}).fill(fixture.password);await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();await p.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();return p;}
async function api(p,method,url,body,tag){return p.evaluate(async({method,url,body,tag})=>{const {createAuthClient}=await import('/src/shared/auth-client.mjs');window.extraClient??=createAuthClient();try{const r=await window.extraClient.request(url,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(tag?{'If-Match':tag}:{})},...(body?{body:JSON.stringify(body)}:{})});return{status:r.status,tag:r.headers.get('ETag'),json:r.status===204?null:await r.json()};}catch(e){return{status:e.status,code:e.code};}},{method,url,body,tag});}
async function waitMarker(name,present){const deadline=Date.now()+10000;while(Date.now()<deadline){let exists=true;try{await access(path.join(dir,name));}catch{exists=false;}if(exists===present)return;await new Promise(r=>setTimeout(r,100));}throw new Error('Test marker timeout: '+name);}
let cv,admin;
try{
 await check('B12_UI_404_AND_STALE_ETAG_412',async()=>{
  cv=await login(fixture.specialist);
  await openNav(cv,'Sinh viên');
  await cv.getByLabel('ID sinh viên',{exact:true}).fill('9223372036854775807');
  const missing=cv.waitForResponse(r=>r.url().endsWith('/sinh-vien/9223372036854775807'));
  await cv.getByRole('button',{name:'Xem chi tiết',exact:true}).click();assert.equal((await missing).status(),404);
  await cv.getByRole('alert').filter({hasText:'Không tìm thấy dữ liệu'}).waitFor();
  await openNav(cv,'Xác minh dữ liệu');
  await cv.getByLabel(/^Loại dữ liệu/).selectOption('semester');
  await cv.getByLabel('ID đối tượng',{exact:true}).fill(fixture.semesterId);
  await cv.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();
  await cv.getByLabel('Thứ tự học kỳ',{exact:true}).fill(String(fixture.semesterOrder));
  await cv.getByLabel('Ngày bắt đầu',{exact:true}).fill('2026-09-01');await cv.getByLabel('Ngày kết thúc',{exact:true}).fill('2027-01-31');
  await cv.getByLabel('Căn cứ xác minh',{exact:true}).fill('BROWSER-ERRORS-CALENDAR');
  const write=cv.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/hoc-ky/'+fixture.semesterId+'/xac-minh'));
  await cv.getByRole('button',{name:'Lưu thay đổi đã xác minh',exact:true}).click();assert.equal((await write).status(),200);
  await cv.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();
  await cv.getByLabel('Căn cứ xác minh',{exact:true}).filter({visible:true}).waitFor();
  const root='/api/ctsv/nen-tang/hoc-ky/'+fixture.semesterId;
  const current=await api(cv,'GET',root);assert.equal(current.status,200);
  const update=await api(cv,'PUT',root+'/xac-minh',{order:fixture.semesterOrder,startDate:'2026-09-01',endDate:'2027-01-31',evidence:'BROWSER-OTHER-WRITER'},current.tag);assert.equal(update.status,200);
  const stale=cv.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/xac-minh'));
  await cv.getByRole('button',{name:'Lưu thay đổi đã xác minh',exact:true}).click();assert.equal((await stale).status(),412);
  await cv.getByRole('alert').filter({hasText:'Dữ liệu đã thay đổi'}).waitFor();
 });
 await check('B13_ADMIN_CREATE_LOCK_UNLOCK_RESET_UI',async()=>{
  admin=await login(fixture.admin);await openNav(admin,'Quản trị tài khoản');
  await admin.getByRole('button',{name:'Nhập tài khoản mới',exact:true}).click();
  await admin.getByLabel('Tên đăng nhập',{exact:true}).fill('browser.extra.'+randomUUID());
  await admin.getByLabel('Mật khẩu',{exact:true}).fill(fixture.password);
  const create=admin.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/tai-khoan'));
  await admin.getByRole('button',{name:'Lưu thay đổi đã xác minh',exact:true}).click();
  const created=await create;assert.equal(created.status(),201);const id=(await created.json()).accountId;
  await admin.getByLabel('ID đối tượng',{exact:true}).fill(id);
  for(const button of ['Khóa','Mở khóa']){
   await admin.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();
   const result=admin.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/trang-thai'));
   await admin.getByRole('button',{name:button,exact:true}).click();assert.equal((await result).status(),200);
  }
  await admin.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();await admin.getByRole('button',{name:'Nhập mật khẩu mới',exact:true}).click();
  await admin.getByLabel('Mật khẩu mới',{exact:true}).fill(fixture.password+' changed');await admin.getByLabel('Lý do',{exact:true}).fill('SYNTHETIC browser reset verification');
  const reset=admin.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/mat-khau'));
  await admin.getByRole('button',{name:'Lưu thay đổi đã xác minh',exact:true}).click();assert.equal((await reset).status(),204);
 });
 await check('B14_LOCKED_ACCOUNT_UI_401_ORDINARY_GET_ALLOWED',async()=>{
  await openNav(cv,'Danh mục');
  await admin.getByLabel('ID đối tượng',{exact:true}).fill(fixture.specialist.accountId);await admin.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();
  const lock=admin.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/trang-thai'));
  await admin.getByRole('button',{name:'Khóa',exact:true}).click();assert.equal((await lock).status(),200);
  const ordinary=cv.waitForResponse(r=>r.url().includes('/nen-tang/danh-muc?'));
  await cv.getByRole('button',{name:'Tra cứu',exact:true}).click();assert.equal((await ordinary).status(),200);
  await openNav(cv,'Sinh viên');
  const sensitive=cv.waitForResponse(r=>r.url().includes('/nen-tang/sinh-vien?'));
  await cv.getByRole('button',{name:'Tra cứu',exact:true}).click();assert.equal((await sensitive).status(),401);
  await cv.getByRole('alert').filter({hasText:'Tài khoản hoặc token đã bị thu hồi'}).waitFor();
  await admin.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();
  const unlock=admin.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/trang-thai'));
  await admin.getByRole('button',{name:'Mở khóa',exact:true}).click();assert.equal((await unlock).status(),200);
  cv=await login(fixture.specialist);
 });
 await check('B15_REAL_SQL_AUTH_TIMEOUT_UI_503_AND_RECOVERY',async()=>{
  await openNav(cv,'Sinh viên');
  await writeFile(path.join(dir,'auth-lock-request'),'synthetic specialist only');
  try{
   await waitMarker('auth-lock-held',true);
   const unavailable=cv.waitForResponse(r=>r.url().includes('/nen-tang/sinh-vien?'));
   await cv.getByRole('button',{name:'Tra cứu',exact:true}).click();const r=await unavailable;
   assert.equal(r.status(),503);assert.equal((await r.json()).code,'DEPENDENCY_UNAVAILABLE');
   await cv.getByRole('alert').filter({hasText:'Chưa thể kiểm tra trạng thái bảo mật'}).waitFor();
  }finally{await writeFile(path.join(dir,'auth-lock-release'),'rollback');await waitMarker('auth-lock-held',false);}
  const recovered=cv.waitForResponse(r=>r.url().includes('/nen-tang/sinh-vien?'));
  await cv.getByRole('button',{name:'Tra cứu',exact:true}).click();assert.equal((await recovered).status(),200);
  report.fault='Real exclusive lock on synthetic Auth row, test-only JDBC queryTimeout=3s; rollback and recovery200; no interface fault/stub';
 });
 report.status='PASS';
}catch(e){report.status='FAIL';report.error=safe(e.message);process.exitCode=1;console.error('HTTPS UI errors: see sanitized report');}
finally{for(const c of contexts)await c.close();await browser.close();await writeFile(path.join(evidence,'https-ui-errors.json'),JSON.stringify(report,null,2));}
