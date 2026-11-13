import {chromium} from '@playwright/test';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const dir=path.resolve('../.local/p04-tls');
const fixture=JSON.parse(await readFile(path.join(dir,'browser-fixture.json'),'utf8'));
const evidence=path.resolve('../../../nghiepvu/.analysis/runtime/p04/20261112/wasm');
await mkdir(evidence,{recursive:true});
const ORIGIN='https://127.0.0.1:5174',F='/api/ctsv/nen-tang',A='/api/xac-thuc';
const browser=await chromium.launch({channel:'chrome',headless:true});
const report={browser:browser.version(),origin:ORIGIN,backend:'https://127.0.0.1:8544',tls:'VERIFIED: system CurrentUser trust; ignoreHTTPSErrors=false',backendMode:'REAL Auth/Foundation/SQL Server',cases:[],consoleErrors:[]};
const contexts=[];
function safe(text){return String(text).replaceAll(fixture.password,'[REDACTED]').replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]').replace(/(ctsv_refresh=)[A-Za-z0-9_-]+/g,'$1[REDACTED]');}
async function check(id,fn){try{await fn();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}catch(e){report.cases.push({id,status:'FAIL',error:safe(e.message)});throw e;}}
async function context(){const c=await browser.newContext({ignoreHTTPSErrors:false});contexts.push(c);return c;}
async function page(c){const p=await c.newPage();p.setDefaultTimeout(15000);p.on('pageerror',e=>report.consoleErrors.push(safe(e.message)));await p.goto(ORIGIN);return p;}
async function login(p,user){
 await p.getByLabel('Tên đăng nhập',{exact:true}).fill(user.username);
 await p.getByLabel('Mật khẩu',{exact:true}).fill(fixture.password);
 await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 await p.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
}
async function api(p,method,url,body,tag){
 return p.evaluate(async({method,url,body,tag})=>{
  const module=await import('/src/shared/auth-client.mjs');
  window.p04Client??=module.createAuthClient();
  try{
   const response=await window.p04Client.request(url,{method,headers:{...(body?{'Content-Type':'application/json'}:{}),...(tag?{'If-Match':tag}:{})},...(body?{body:JSON.stringify(body)}:{})});
   return{status:response.status,tag:response.headers.get('ETag'),json:response.status===204?null:await response.json()};
  }catch(e){return{status:e.status??0,code:e.code??'CLIENT_ERROR'};}
 },{method,url,body,tag});
}
let student,cv,admin,sc,cc,ac;
try{
 await check('B01_HTTPS_LOGIN_COOKIE_STORAGE',async()=>{
  sc=await context();student=await page(sc);await login(student,fixture.student);
  const cookie=(await sc.cookies()).find(c=>c.name==='ctsv_refresh');
  assert(cookie);assert.equal(cookie.httpOnly,true);assert.equal(cookie.secure,true);assert.equal(cookie.sameSite,'Strict');assert.equal(cookie.path,A);
  const storage=await student.evaluate(()=>{
   history.pushState({},'', '/api/xac-thuc/check');
   const visible=document.cookie;history.replaceState({},'','/');
   return{visible,local:Object.keys(localStorage),session:Object.keys(sessionStorage)};
  });
  assert(!storage.visible.includes('ctsv_refresh'));assert(!storage.local.some(k=>/token|password/i.test(k)));assert(!storage.session.some(k=>/token|password/i.test(k)));
  report.cookie={httpOnly:cookie.httpOnly,secure:cookie.secure,sameSite:cookie.sameSite,path:cookie.path,domain:cookie.domain};
  await student.screenshot({path:path.join(evidence,'https-student.png'),fullPage:true});
 });
 await check('B02_STUDENT_LOOKUP_AND_FORBIDDEN',async()=>{
  await student.getByRole('button',{name:'Tra cứu',exact:true}).click();
  await student.getByRole('table').waitFor();
  await student.getByRole('button',{name:'Sinh viên',exact:true}).click();
  await student.getByRole('button',{name:'Tra cứu',exact:true}).click();
  await student.getByRole('alert').filter({hasText:'không có quyền'}).waitFor();
 });
 await check('B03_REFRESH_AFTER_RELOAD_AND_TWO_TABS',async()=>{
  await student.reload();
  await student.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click();
  await student.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
  const second=await page(sc);
  const refreshStatuses=[];
  sc.on('response',response=>{if(response.url().endsWith('/lam-moi-token'))refreshStatuses.push(response.status());});
  await Promise.all([
   student.evaluate(async()=>{const {createAuthClient}=await import('/src/shared/auth-client.mjs');await createAuthClient().refresh();}),
   second.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click()
  ]);
  await second.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
  assert.deepEqual(refreshStatuses,[200,200]);
  await second.close();
 });
 await check('B04_CSRF_REJECTED_WITHOUT_ROTATION',async()=>{
  const before=(await sc.cookies()).find(c=>c.name==='ctsv_refresh').value;
  const status=await student.evaluate(async()=>{const r=await fetch('/api/xac-thuc/lam-moi-token',{method:'POST',credentials:'include'});return r.status;});
  assert.equal(status,403);
  assert.equal((await sc.cookies()).find(c=>c.name==='ctsv_refresh').value,before);
 });
 await check('B05_SPECIALIST_STUDENT_DETAIL_AND_SEMESTER_UI',async()=>{
  cc=await context();cv=await page(cc);await login(cv,fixture.specialist);
  await cv.getByRole('button',{name:'Sinh viên',exact:true}).click();
  await cv.getByLabel('ID sinh viên',{exact:true}).fill(fixture.student.studentId);
  await cv.getByRole('button',{name:'Xem chi tiết',exact:true}).click();
  await cv.getByText('Sinh viên Browser TEST',{exact:true}).waitFor();
  await cv.getByRole('button',{name:'Xác minh & quyền',exact:true}).click();
  await cv.getByLabel(/^Loại dữ liệu/).selectOption('semester');
  await cv.getByLabel('ID đối tượng',{exact:true}).fill(fixture.semesterId);
  await cv.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();
  await cv.getByLabel('Thứ tự học kỳ',{exact:true}).fill(String(fixture.semesterOrder));
  await cv.getByLabel('Ngày bắt đầu',{exact:true}).fill('2026-09-01');
  await cv.getByLabel('Ngày kết thúc',{exact:true}).fill('2027-01-31');
  await cv.getByLabel('Căn cứ xác minh',{exact:true}).fill('BROWSER-TEST-CALENDAR');
  const response=cv.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/hoc-ky/'+fixture.semesterId+'/xac-minh'));
  await cv.getByRole('button',{name:'Lưu thay đổi đã xác minh',exact:true}).click();
  assert.equal((await response).status(),200);
  await cv.getByText('verified',{exact:true}).waitFor();
  await cv.screenshot({path:path.join(evidence,'https-specialist.png'),fullPage:true});
 });
 await check('B06_CORS_REAL_BROWSER_ALLOW_AND_DENY',async()=>{
  const statuses=await cv.evaluate(async()=>{
   const allowed=await fetch('https://127.0.0.1:8544/api/xac-thuc/lam-moi-token',{method:'POST',credentials:'include',headers:{'X-CSRF-Protection':'1'}});
   return allowed.status;
  });
  assert.equal(statuses,200);
  const hostile=await cc.newPage();await hostile.goto('https://127.0.0.1:8544/api/xac-thuc/toi');
  const denied=await hostile.evaluate(async()=>{
   try{await fetch('https://127.0.0.1:5174/api/xac-thuc/lam-moi-token',{method:'POST',credentials:'include',headers:{'X-CSRF-Protection':'1'}});return false;}catch{return true;}
  });
  assert.equal(denied,true);await hostile.close();
 });
 await check('B07_ADMIN_AUTH_UI_AND_ETAG',async()=>{
  ac=await context();admin=await page(ac);await login(admin,fixture.admin);
  await admin.getByRole('button',{name:'Quản trị tài khoản',exact:true}).click();
  await admin.getByRole('button',{name:'Danh mục quyền Auth',exact:true}).click();
  await admin.getByText('AUTH_ADMIN',{exact:true}).waitFor();
  await admin.getByLabel(/^Loại dữ liệu/).selectOption('account');
  await admin.getByLabel('ID đối tượng',{exact:true}).fill(fixture.student.accountId);
  const read=admin.waitForResponse(r=>r.request().method()==='GET'&&r.url().endsWith('/tai-khoan/'+fixture.student.accountId));
  await admin.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();
  assert((await read).headers()['etag']);
  await admin.locator('legend').filter({hasText:'Liên kết hồ sơ'}).waitFor();
  await admin.screenshot({path:path.join(evidence,'https-admin.png'),fullPage:true});
 });
 await check('B08_LOCAL_ROLE_GRANT_REVOKE_REAL_HTTP',async()=>{
  const user=fixture.specialist.userId,role=fixture.roleId;
  const path=F+'/nguoi-dung/'+user+'/vai-tro-mghp';
  const current=await api(admin,'GET',path);assert.equal(current.status,200);
  const revoke=await api(admin,'DELETE',path+'/'+role,null,current.tag);assert.equal(revoke.status,200);
  assert.equal((await api(cv,'GET',F+'/sinh-vien')).status,403);
  const grant=await api(admin,'PUT',path+'/'+role,null,revoke.tag);assert.equal(grant.status,200);
  assert.equal((await api(cv,'GET',F+'/sinh-vien')).status,200);
 });
 await check('B09_PROCESS_HISTORY_REAL_HTTP_FROM_BROWSER',async()=>{
  const path=F+'/qua-trinh-hoc/'+fixture.processId;
  const current=await api(cv,'GET',path+'/lich-su');assert.equal(current.status,200);
  const body={programId:null,cohortId:null,startSemesterId:fixture.semesterId,endSemesterId:null,actualStart:'2026-09-01',expectedEnd:null,actualEnd:null,evidence:'BROWSER-TEST-PROCESS',evidenceFileId:null};
  const update=await api(cv,'PUT',path+'/xac-minh',body,current.tag);assert.equal(update.status,200);
  const fresh=await api(cv,'GET',path+'/lich-su');
  const history={semesterId:fixture.semesterId,status:'TEST_CONFIRMED',from:'2026-09-01',to:null,decisionNumber:null,decisionDate:null,evidence:'BROWSER-TEST-HISTORY'};
  const added=await api(cv,'POST',path+'/lich-su-trang-thai',history,fresh.tag);assert.equal(added.status,201);
  assert.equal(added.json.code,current.json.code);
 });
 await check('B10_LOST_REFRESH_RESPONSE_REQUIRES_LOGIN',async()=>{
  await student.reload();
  let committed=false,routeError=null;
  await student.route('**/api/xac-thuc/lam-moi-token',async route=>{
   try{const actual=await route.fetch();assert.equal(actual.status(),200);committed=true;await route.abort('failed');}catch(e){routeError=safe(e.message);await route.abort('failed').catch(()=>{});}
  });
  await student.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click();
  await student.getByRole('alert').waitFor();
  assert.equal(routeError,null);assert.equal(committed,true);
  await student.unroute('**/api/xac-thuc/lam-moi-token');
  let calls=0;student.on('request',r=>{if(r.url().endsWith('/lam-moi-token'))calls++});
  await student.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click();
  await student.getByRole('alert').filter({hasText:'Vui lòng đăng nhập lại'}).waitFor();
  assert.equal(calls,0);
  await login(student,fixture.student);
 });
 await check('B11_LOGOUT_COOKIE_ONLY_AND_TAB_INVALIDATION',async()=>{
  const second=await page(sc);
  await second.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click();
  await second.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
  const outgoing=student.waitForRequest(r=>r.url().endsWith('/dang-xuat'));
  await student.getByRole('button',{name:'Đăng xuất',exact:true}).click();
  const request=await outgoing;const headers=await request.allHeaders();
  assert.equal(headers.authorization,undefined);assert.equal(request.method(),'POST');
  assert(headers.cookie?.includes('ctsv_refresh='));
  await second.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
  await student.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
  assert(!(await sc.cookies()).some(c=>c.name==='ctsv_refresh'));await second.close();
 });
 report.status='PASS';
}catch(e){report.status='FAIL';report.error=safe(e.message);console.error('HTTPS E2E failed; see sanitized report');process.exitCode=1;}
finally{for(const c of contexts)await c.close();await browser.close();await writeFile(path.join(evidence,'https-e2e.json'),JSON.stringify(report,null,2));}
