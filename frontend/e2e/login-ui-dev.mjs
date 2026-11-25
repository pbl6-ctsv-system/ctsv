// Run against the normal DEV launcher; login/refresh/logout are REAL.
// Supply CTSV_DEV_HOME and CTSV_DEV_SMOKE_PASSWORDS in memory, as in local-dev.mjs.
// This smoke only reads business screens; it does not seed or reset data.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {chromium} from 'playwright-core';

const devHome=process.env.CTSV_DEV_HOME;
assert(devHome,'CTSV_DEV_HOME is required');
const config=JSON.parse((await readFile(path.join(devHome,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS??'{}');
delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const username=config.Accounts.student.Username,password=passwords.student;
assert(typeof password==='string'&&password.length>0,'Synthetic student credential is required');
const origin='https://127.0.0.1:'+config.FrontendPort;
const evidence=path.resolve('.local/login-ui-dev');await mkdir(evidence,{recursive:true});
const report={origin,backend:'https://127.0.0.1:'+config.BackendPort,database:'localhost\\DEV2022 / ctsv_dev',mode:'REAL launcher / Auth / SQL Server; verified TLS; synthetic student; no business writes',cases:[]};
const safe=value=>String(value).replaceAll(password,'[REDACTED]').replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]').replace(/ctsv_refresh=[A-Za-z0-9_-]+/g,'ctsv_refresh=[REDACTED]');
const browser=await chromium.launch({channel:'chrome',headless:true});
const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}});
const page=await context.newPage();page.setDefaultTimeout(20000);
const errors=[];page.on('pageerror',error=>errors.push(safe(error.message)));
const network=[];
page.on('request',request=>{if(new URL(request.url()).pathname.startsWith('/api/'))network.push({method:request.method(),path:new URL(request.url()).pathname});});
page.on('requestfailed',request=>{if(new URL(request.url()).pathname.startsWith('/api/'))network.push({path:new URL(request.url()).pathname,failure:request.failure()?.errorText});});
async function check(id,action){
 try{await action();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}
 catch(error){report.cases.push({id,status:'FAIL',error:safe(error.message)});throw error;}
}
async function fill(value){await page.getByLabel('Tên đăng nhập',{exact:true}).fill(username);await page.getByLabel('Mật khẩu',{exact:true}).fill(value);}
try{
 report.browser=browser.version();
 await check('DEV_LOGIN_01_PUBLIC_LAYOUT',async()=>{
  await page.goto(origin);await page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
  assert(await page.getByRole('button',{name:/Đăng nhập với Office 365/}).isDisabled());
  assert(await page.locator('header img').evaluate(image=>image.complete&&image.naturalWidth>0));
  await page.screenshot({path:path.join(evidence,'desktop.png'),fullPage:true});
  await page.setViewportSize({width:360,height:800});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:path.join(evidence,'mobile.png'),fullPage:true});
  await page.setViewportSize({width:1280,height:900});
 });
 await check('DEV_LOGIN_02_BACKEND_REJECTS_INVALID_PASSWORD',async()=>{
  await fill('synthetic-invalid-password');
  const response=page.waitForResponse(value=>value.url().endsWith('/api/xac-thuc/dang-nhap'));
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();assert.equal((await response).status(),401);
  await page.getByRole('alert').waitFor();
  assert.equal(await page.getByLabel('Tên đăng nhập',{exact:true}).inputValue(),username);
  assert.equal(await page.getByLabel('Mật khẩu',{exact:true}).inputValue(),'');
  assert(await page.getByRole('button',{name:'Đăng nhập',exact:true}).isEnabled());
 });
 await check('DEV_LOGIN_03_SUCCESS_AND_EXISTING_APP_SHELL',async()=>{
  await fill(password);
  const request=page.waitForRequest(value=>value.url().endsWith('/api/xac-thuc/dang-nhap'));
  const response=page.waitForResponse(value=>value.url().endsWith('/api/xac-thuc/dang-nhap'));
  await page.getByLabel('Mật khẩu',{exact:true}).press('Enter');
  assert.equal((await response).status(),200);assert.equal((await request).headers()['x-csrf-protection'],'1');
  await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
  await page.getByRole('navigation',{name:'Chức năng CTSV'}).waitFor();
  assert.equal(await page.getByRole('button',{name:/Office 365/}).count(),0);
  assert.equal(await page.getByRole('link',{name:'Quản trị tài khoản',exact:true,includeHidden:true}).count(),0);
  await page.screenshot({path:path.join(evidence,'authenticated-home.png'),fullPage:true});
  const cookie=(await context.cookies()).find(value=>value.name==='ctsv_refresh');
  assert(cookie?.httpOnly&&cookie.secure);assert.equal(cookie.sameSite,'Strict');assert.equal(cookie.path,'/api/xac-thuc');
  report.refreshCookie={httpOnly:cookie.httpOnly,secure:cookie.secure,sameSite:cookie.sameSite,path:cookie.path};
  assert(await page.evaluate(()=>!Object.keys(localStorage).some(key=>/token|password|credential/i.test(key))&&!Object.keys(sessionStorage).some(key=>/token|password|credential/i.test(key))));
 });
 await check('DEV_LOGIN_04_CONTINUE_EXISTING_SESSION',async()=>{
  await page.reload();await page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
  const response=page.waitForResponse(value=>value.url().endsWith('/api/xac-thuc/lam-moi-token'));
  await page.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click();assert.equal((await response).status(),200);
  await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
 });
 await check('DEV_LOGIN_05_DIRECT_URL_DENIED_AND_LOGOUT',async()=>{
  await page.goto(origin+'/quan-tri/tai-khoan');
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
  await page.getByRole('alert').filter({hasText:'Bạn không có quyền truy cập trang này.'}).waitFor();
  const request=page.waitForRequest(value=>value.url().endsWith('/api/xac-thuc/dang-xuat'));
  const response=page.waitForResponse(value=>value.url().endsWith('/api/xac-thuc/dang-xuat'));
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();assert.equal((await response).status(),204);
  assert.equal((await request).headers().authorization,undefined);
  await page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
  assert(!(await context.cookies()).some(value=>value.name==='ctsv_refresh'));
  assert(await page.getByRole('button',{name:/Office 365/}).isDisabled());assert.deepEqual(errors,[]);
 });
}catch(error){
 report.error=safe(error.message);report.network=network;report.pageErrors=errors;
 report.visibleErrors=(await page.getByRole('alert').allTextContents()).map(safe);
 process.exitCode=1;console.error(safe(error.message));
}
finally{
 await context.close();await browser.close();
 await writeFile(path.join(evidence,'result.json'),JSON.stringify(report,null,2));
 console.log(JSON.stringify({passed:report.cases.filter(item=>item.status==='PASS').length,failed:report.cases.filter(item=>item.status==='FAIL').length,evidence}));
}
