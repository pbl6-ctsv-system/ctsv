// Read-only business smoke through the daily DEV launcher. Credentials stay in memory.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
if(!process.env.CTSV_DEV_HOME||!process.env.CTSV_DEV_SMOKE_PASSWORDS)throw new Error('Use the DEV wrapper with protected process credentials.');
const config=JSON.parse((await readFile(path.join(process.env.CTSV_DEV_HOME,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin='https://127.0.0.1:'+config.FrontendPort,api='/api/ctsv/hoc-bong',routePath='/hoc-bong/kkht/dot-xet';
const browser=await chromium.launch({channel:'chrome',headless:true}),contexts=[];let passed=0;
const calls=[],errors=[];
async function check(name,run){await run();passed++;console.log(name+' PASS (REAL DEV)');}
async function login(role){
 const context=await browser.newContext({viewport:{width:1280,height:900}});contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(15000);page.on('pageerror',e=>errors.push(e.message));
 page.on('request',r=>{const u=new URL(r.url());if(u.pathname.startsWith(api))calls.push({path:u.pathname,method:r.method(),query:Object.fromEntries(u.searchParams)});});
 await page.goto(origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 const pending=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 const response=await pending;assert.equal(response.status(),200);const token=(await response.json()).accessToken;
 await expect(page.getByRole('heading',{name:'Trang chủ CTSV',exact:true})).toBeVisible();return{page,context,token};
}
async function get(actor,endpoint){const r=await actor.context.request.get(origin+api+endpoint,{headers:{Authorization:'Bearer '+actor.token}});assert.equal(r.status(),200);return r.json();}
try{
 const cv=await login('specialist');let first,second;
 await check('HB-LIVE-DEV01-menu-and-source',async()=>{
  const source=await get(cv,'/hoc-ky-nguon?size=100');assert(source.totalElements<=100,'Review pagination before using this smoke dataset.');
  [first,second]=source.items.filter(s=>s.selectable);assert(first&&second,'Need two existing selectable source terms.');
  await cv.page.getByRole('button',{name:'Học bổng',exact:true}).click();await cv.page.getByRole('link',{name:'Đợt xét KKHT',exact:true}).click();
  await expect(cv.page.getByRole('heading',{name:'Đợt xét học bổng KKHT',exact:true})).toBeVisible();
  await expect(cv.page.getByRole('combobox',{name:'Tìm học kỳ',exact:true})).toBeVisible();
 });
 await check('HB-LIVE-DEV02-live-selection-history-reload',async()=>{
  const input=cv.page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});
  for(const source of [first,second]){
   await input.fill(source.code);await cv.page.getByRole('option',{name:source.code+' · '+(source.name??'Chưa có tên'),exact:false}).click();
   await expect(cv.page).toHaveURL(new RegExp('hocKyNguonId='+source.sourceSemesterId));
   await expect(cv.page.getByText('Học kỳ đã chọn: '+source.code+' · '+(source.name??'Chưa có tên'),{exact:true})).toBeVisible();
   const list=await get(cv,'/kkht/dot-xet?sourceSemesterId='+source.sourceSemesterId+'&size=20');
   await expect(cv.page.getByText(list.totalElements+' đợt xét',{exact:true})).toBeVisible();
   assert(calls.some(c=>c.path===api+'/hoc-ky-nguon'&&c.query.q===source.code));
  }
  await cv.page.goBack();await expect(cv.page).toHaveURL(new RegExp('hocKyNguonId='+first.sourceSemesterId));
  await cv.page.reload();await expect(input).toHaveValue(first.code+' · '+(first.name??'Chưa có tên'));
 });
 await check('HB-LIVE-DEV03-mobile-keyboard',async()=>{
  await cv.page.setViewportSize({width:360,height:800});const input=cv.page.getByRole('combobox',{name:'Tìm học kỳ',exact:true});await input.fill(second.code);
  const option=cv.page.getByRole('option',{name:second.code+' · '+(second.name??'Chưa có tên'),exact:false});await expect(option).toBeVisible();
  const box=await option.boundingBox();assert(box.x>=0&&box.x+box.width<=360);await input.press('ArrowDown');await input.press('Enter');
  await expect(cv.page).toHaveURL(new RegExp('hocKyNguonId='+second.sourceSemesterId));assert(await cv.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 });
 await check('HB-LIVE-DEV04-student-denied',async()=>{
  const sv=await login('student');await expect(sv.page.getByRole('button',{name:'Học bổng',exact:true})).toHaveCount(0);
  await sv.page.goto(origin+routePath);await expect(sv.page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(sv.page.getByRole('combobox')).toHaveCount(0);
  const response=await sv.context.request.get(origin+api+'/hoc-ky-nguon?q='+first.code,{headers:{Authorization:'Bearer '+sv.token}});assert.equal(response.status(),403);
 });
 assert(calls.every(c=>c.method==='GET'),'Business smoke must not write data.');assert.deepEqual(errors,[]);
}catch(e){
 let message=String(e.message).replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]');for(const value of Object.values(passwords))message=message.replaceAll(value,'[REDACTED]');
 console.error(message);process.exitCode=1;
}finally{
 for(const context of contexts){
  const response=await context.request.post(origin+'/api/xac-thuc/dang-xuat',{headers:{Origin:origin,'X-CSRF-Protection':'1'},timeout:10000}).catch(()=>null);
  if(response?.status()!==204){console.error('DEV logout NOT VERIFIED');process.exitCode=1;}await context.close();
 }
 await browser.close();console.log('HB live-search REAL DEV: '+passed+' PASS; business GET only; no screenshots/evidence.');
}
