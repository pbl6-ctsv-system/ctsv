// UI regression only: the production build runs in Chrome; every API response is synthetic.
// Run from frontend after npm.cmd run build: node e2e/login-ui.mjs
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {mkdir,writeFile} from 'node:fs/promises';
import path from 'node:path';
import {chromium} from 'playwright-core';
import AxeBuilder from '@axe-core/playwright';

const origin='http://127.0.0.1:4175';
const evidence=path.resolve('.local/login-ui');
await mkdir(evidence,{recursive:true});
const report={origin,mode:'Chrome / production build / synthetic HTTP fixtures; no backend or database',cases:[]};
const server=spawn(process.execPath,['node_modules/vite/bin/vite.js','preview','--host','127.0.0.1','--port','4175','--strictPort'],{windowsHide:true,stdio:['ignore','pipe','pipe']});
let serverOutput='';
server.stdout.on('data',value=>serverOutput+=value);
server.stderr.on('data',value=>serverOutput+=value);
let browser;
const contexts=[];

async function check(id,action){
 try{await action();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}
 catch(error){report.cases.push({id,status:'FAIL',error:error.message});throw error;}
}
async function fixture({login='success',refresh='success',holdLogin,holdRefresh}={}){
 const context=await browser.newContext();contexts.push(context);
 const calls=[];
 const errors=[];
 await context.route('**/api/**',async route=>{
  const request=route.request(),url=new URL(request.url());
  calls.push({path:url.pathname,method:request.method()});
  const send=(body,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  if(url.pathname==='/api/xac-thuc/dang-nhap'){
   assert.deepEqual(JSON.parse(request.postData()),{username:'synthetic.login',password:'synthetic-password'});
   assert.equal(request.headers()['x-csrf-protection'],'1');
   if(holdLogin)await holdLogin;
   if(login==='network')return route.abort('failed');
   if(login==='reject')return send({code:'UNAUTHENTICATED'},401);
   return send({accessToken:'synthetic-access',expiresIn:900});
  }
  if(url.pathname==='/api/xac-thuc/lam-moi-token'){
   if(holdRefresh)await holdRefresh;
   return refresh==='reject'?send({code:'UNAUTHENTICATED'},401):send({accessToken:'synthetic-refreshed',expiresIn:900});
  }
  if(url.pathname==='/api/xac-thuc/dang-xuat')return route.fulfill({status:204});
  if(url.pathname==='/api/xac-thuc/toi')return send({accountId:'900001',username:'synthetic.login',functions:[]});
  if(url.pathname==='/api/ctsv/nen-tang/toi/quyen-giao-dien')return send({student:true,mghpSpecialist:false,ctsvRoleAdmin:false});
  if(url.pathname==='/api/shcn/khoa/pham-vi')return send({khoa:[]});
  if(url.pathname==='/api/ctsv/hoc-bong/toi/nhom-chuc-nang')return send({student:true,specialist:false});
  throw new Error('Unexpected fixture request: '+url.pathname);
 });
 const page=await context.newPage();page.setDefaultTimeout(7000);page.on('pageerror',error=>errors.push(error.message));
 return{context,page,calls,errors};
}
async function credentials(page){
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill('synthetic.login');
 await page.getByLabel('Mật khẩu',{exact:true}).fill('synthetic-password');
}
async function loginPage(page){await page.goto(origin);await page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();}
const loginCount=calls=>calls.filter(call=>call.path.endsWith('/dang-nhap')).length;
try{
 let ready=false;
 for(let i=0;i<50;i++){
  if(server.exitCode!==null)throw new Error('Preview failed: '+serverOutput);
  try{if((await fetch(origin)).ok){ready=true;break;}}catch{}
  await new Promise(resolve=>setTimeout(resolve,100));
 }
 assert(ready,'Preview ready');
 browser=await chromium.launch({channel:'chrome',headless:true});
 report.browser=browser.version();

 await check('LOGIN_UI_01_LAYOUT_AND_OFFICE_DISABLED',async()=>{
  const {page,calls,errors}=await fixture();await page.setViewportSize({width:1280,height:900});await loginPage(page);
  await page.getByText('Trường Đại học Bách khoa – Đại học Đà Nẵng',{exact:true}).waitFor();
  const office=page.getByRole('button',{name:/Đăng nhập với Office 365/});assert(await office.isDisabled());
  assert(await office.getByText('Chưa hỗ trợ',{exact:true}).isVisible());
  assert.equal(await page.locator('main').count(),1);assert.equal(await page.locator('form').count(),1);
  assert.equal(await page.getByRole('navigation',{name:'Chức năng CTSV'}).count(),0);
  assert(await page.locator('header img').evaluate(image=>image.complete&&image.naturalWidth>0));
  await page.screenshot({path:path.join(evidence,'desktop.png'),fullPage:true});
  await page.setViewportSize({width:360,height:800});
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  const formBox=await page.locator('form').boundingBox(),heroBox=await page.getByRole('complementary').boundingBox();assert(formBox.y<heroBox.y);
  await page.screenshot({path:path.join(evidence,'mobile.png'),fullPage:true});
  assert.equal(calls.length,0);assert.deepEqual(errors,[]);
 });
 await check('LOGIN_UI_02_KEYBOARD_VALIDATION_AND_LOCAL_CONTROLS',async()=>{
  const {page,calls}=await fixture();await loginPage(page);
  await page.keyboard.press('Tab');assert.equal(await page.getByRole('link',{name:'Đến phần đăng nhập'}).evaluate(element=>element===document.activeElement),true);
  await page.keyboard.press('Enter');await page.keyboard.press('Tab');
  assert(await page.getByLabel('Tên đăng nhập',{exact:true}).evaluate(element=>element===document.activeElement));
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();assert.equal(loginCount(calls),0);
  assert(await page.getByLabel('Tên đăng nhập',{exact:true}).evaluate(element=>!element.validity.valid));
  await credentials(page);
  await page.getByRole('button',{name:'Hiện mật khẩu',exact:true}).click();
  assert.equal(await page.getByLabel('Mật khẩu',{exact:true}).getAttribute('type'),'text');
  assert.equal(await page.getByLabel('Mật khẩu',{exact:true}).inputValue(),'synthetic-password');
  await page.getByRole('button',{name:'Ẩn mật khẩu',exact:true}).click();
  assert.equal(await page.getByLabel('Mật khẩu',{exact:true}).getAttribute('type'),'password');
  const help=page.getByRole('button',{name:'Cần hỗ trợ tài khoản?',exact:true});await help.click();
  assert.equal(await help.getAttribute('aria-expanded'),'true');assert(await page.getByText('Hỗ trợ tài khoản',{exact:true}).isVisible());
  await help.click();assert.equal(await help.getAttribute('aria-expanded'),'false');assert.equal(loginCount(calls),0);
 });
 await check('LOGIN_UI_03_PENDING_AND_BACKEND_ERROR',async()=>{
  let release;const holdLogin=new Promise(resolve=>release=resolve);
  const {page,calls}=await fixture({login:'reject',holdLogin});await loginPage(page);await credentials(page);
  const submit=page.getByRole('button',{name:'Đăng nhập',exact:true});await submit.click();
  await page.waitForFunction(()=>document.querySelector('button[type=submit]')?.disabled);
  assert(await page.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).isDisabled());
  await page.getByLabel('Mật khẩu',{exact:true}).press('Enter');assert.equal(loginCount(calls),1);
  release();await page.getByRole('alert').filter({hasText:'Vui lòng đăng nhập.'}).waitFor();
  assert.equal(await page.getByLabel('Tên đăng nhập',{exact:true}).inputValue(),'synthetic.login');
  assert.equal(await page.getByLabel('Mật khẩu',{exact:true}).inputValue(),'');
  assert(await submit.isEnabled());assert.equal(loginCount(calls),1);
  await page.setViewportSize({width:360,height:800});await page.getByRole('button',{name:'Cần hỗ trợ tài khoản?',exact:true}).click();await page.evaluate(()=>scrollTo(0,document.body.scrollHeight));
  assert(await page.getByRole('alert').evaluate(element=>{const box=element.getBoundingClientRect();return box.top>=0&&box.bottom<=innerHeight;}));
  await page.screenshot({path:path.join(evidence,'mobile-error.png'),fullPage:true});
 });
 await check('LOGIN_UI_04_NETWORK_ERROR_NO_REPLAY',async()=>{
  const {page,calls}=await fixture({login:'network'});await loginPage(page);await credentials(page);await page.getByLabel('Mật khẩu',{exact:true}).press('Enter');
  await page.getByRole('alert').waitFor();assert.equal(loginCount(calls),1);
  assert.equal(await page.getByLabel('Tên đăng nhập',{exact:true}).inputValue(),'synthetic.login');
  assert.equal(await page.getByLabel('Mật khẩu',{exact:true}).inputValue(),'');
 });
 await check('LOGIN_UI_05_SUCCESS_EXISTING_APP_SHELL_AND_LOGOUT',async()=>{
  const {page,calls,errors}=await fixture();await loginPage(page);await credentials(page);await page.getByLabel('Mật khẩu',{exact:true}).press('Enter');
  await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
  assert(await page.getByRole('navigation',{name:'Chức năng CTSV'}).isVisible());
  assert.equal(await page.getByRole('button',{name:/Office 365/}).count(),0);assert.equal(await page.locator('header img').count(),0);
  assert.equal(await page.getByRole('link',{name:'Quản trị tài khoản',exact:true,includeHidden:true}).count(),0);
  await page.screenshot({path:path.join(evidence,'authenticated-home.png'),fullPage:true});
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();await page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
  assert.equal(loginCount(calls),1);assert.equal(calls.filter(call=>call.path.endsWith('/dang-xuat')).length,1);assert.deepEqual(errors,[]);
 });
 await check('LOGIN_UI_06_MANUAL_RESTORE_AND_DEEP_ROUTE',async()=>{
  const {page,calls}=await fixture();await loginPage(page);await page.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click();
  await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();assert.equal(loginCount(calls),0);
  await page.goto(origin+'/quan-tri/tai-khoan');await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
  await page.getByRole('alert').filter({hasText:'Bạn không có quyền truy cập trang này.'}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Đọc danh sách tài khoản',exact:true}).count(),0);
 });
 await check('LOGIN_UI_07_EXPIRED_RESTORE_SHOWS_LOGIN',async()=>{
  let release;const holdRefresh=new Promise(resolve=>release=resolve);
  const {page,calls}=await fixture({refresh:'reject',holdRefresh});await page.goto(origin+'/quan-tri/tai-khoan');
  await page.getByRole('status').filter({hasText:'Đang khôi phục đăng nhập…'}).waitFor();
  assert.equal(await page.getByRole('button',{name:'Đăng nhập',exact:true}).count(),0);
  release();await page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();await page.getByRole('alert').waitFor();
  assert(await page.getByRole('button',{name:/Office 365/}).isDisabled());assert.equal(loginCount(calls),0);
 });
 await check('LOGIN_UI_08_LOGOUT_PROPAGATES_TO_OTHER_TAB',async()=>{
  const {page,context}=await fixture();await loginPage(page);await credentials(page);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
  const second=await context.newPage();await loginPage(second);await second.getByRole('button',{name:'Tiếp tục đăng nhập',exact:true}).click();
  await second.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();
  await second.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();assert(await second.getByRole('button',{name:/Office 365/}).isDisabled());
 });
 await check('LOGIN_UI_09_LOGIN_ACCESSIBILITY',async()=>{
  const {page}=await fixture();await loginPage(page);
  // The existing Shared feedback portal lives outside #root. Keep full-page findings
  // in the report; this task's accessibility assertion covers the login subtree.
  const full=await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();
  report.fullPageAccessibilityFindings=full.violations.map(violation=>({id:violation.id,targets:violation.nodes.map(node=>node.target)}));
  for(const viewport of [{width:1280,height:900},{width:360,height:800}]){
   await page.setViewportSize(viewport);const result=await new AxeBuilder({page}).include('#root').withTags(['wcag2a','wcag2aa','wcag21aa']).analyze();
   assert.deepEqual(result.violations.map(violation=>({id:violation.id,targets:violation.nodes.map(node=>node.target)})),[]);
  }
 });
}catch(error){report.error=error.message;process.exitCode=1;console.error(error.message);}
finally{
 for(const context of contexts)await context.close();if(browser)await browser.close();server.kill();
 await writeFile(path.join(evidence,'result.json'),JSON.stringify(report,null,2));
 console.log(JSON.stringify({passed:report.cases.filter(item=>item.status==='PASS').length,failed:report.cases.filter(item=>item.status==='FAIL').length,evidence}));
}
