// Daily read-only UI/API smoke on the launcher-owned DEV app and synthetic T01 data.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';

const home=process.env.CTSV_DEV_HOME;
if(!home||!process.env.CTSV_DEV_SMOKE_PASSWORDS)throw new Error('Use the DEV wrapper with protected local credentials.');
const config=JSON.parse((await readFile(path.join(home,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);
delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin='https://127.0.0.1:'+config.FrontendPort;
const routePath='/hoc-bong/kkht/dot-xet',api='/api/ctsv/hoc-bong';
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[],cases=[],responses=[];
function safe(message){let text=String(message).replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]');for(const value of Object.values(passwords))text=text.replaceAll(value,'[REDACTED]');return text;}
async function check(id,run){try{await run();cases.push({id,status:'PASS'});console.log(id+' PASS (REAL DEV)');}catch(error){cases.push({id,status:'FAIL',error:safe(error.message)});throw error;}}
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}});
 contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(12000);
 page.on('response',response=>{const url=new URL(response.url());if(url.pathname.startsWith(api+'/'))responses.push({role,path:url.pathname,status:response.status(),method:response.request().method()});});
 await page.goto(origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 const issued=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/xac-thuc/dang-nhap');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 const response=await issued;assert.equal(response.status(),200);
 const issue=await response.json();
 await expect(page.getByRole('heading',{name:'Trang chủ CTSV',exact:true})).toBeVisible();
 return{page,context,token:issue.accessToken};
}
let cv,sv;
try{
 await check('HB-T021-DEV01-specialist-list-menu',async()=>{
  cv=await login('specialist');
  await cv.page.getByRole('button',{name:'Học bổng',exact:true}).click();
  const pending=cv.page.waitForResponse(response=>new URL(response.url()).pathname===api+'/kkht/dot-xet');
  await cv.page.getByRole('link',{name:'Đợt xét KKHT',exact:true}).click();
  const response=await pending;assert.equal(response.status(),200);
  const list=await response.json();
  assert(list.items.some(row=>row.name==='SYNTHETIC - KKHT HK1 - Dot 1'));
  assert(list.items.some(row=>row.name==='SYNTHETIC - KKHT HK1 - Dot 2'));
  await expect(cv.page.getByText('SYNTHETIC - KKHT HK1 - Dot 1',{exact:true})).toBeVisible();
 });
 await check('HB-T021-DEV02-real-detail-reload-filter',async()=>{
  const pending=cv.page.waitForResponse(response=>/^\/api\/ctsv\/hoc-bong\/kkht\/dot-xet\/\d+$/.test(new URL(response.url()).pathname));
  await cv.page.getByRole('button',{name:'Xem chi tiết SYNTHETIC - KKHT HK1 - Dot 1',exact:true}).click();
  const response=await pending;assert.equal(response.status(),200);assert(response.headers().etag);
  const detail=await response.json();assert.equal(detail.semester.academicYear,'2094-2095');assert.equal(detail.semester.semesterOrder,1);
  await expect(cv.page.getByRole('heading',{name:detail.name,exact:true})).toBeVisible();
  await cv.page.reload();
  await expect(cv.page.getByRole('heading',{name:detail.name,exact:true})).toBeVisible();
  await expect(cv.page.getByText('01/08/2094',{exact:true})).toBeVisible();
  await cv.page.getByRole('button',{name:'Xem các đợt cùng học kỳ',exact:true}).click();
  await expect(cv.page.getByText('Học kỳ 1 · 2094-2095',{exact:true})).toBeVisible();
  await cv.page.getByLabel('Số đợt',{exact:true}).fill('2');
  await cv.page.getByRole('button',{name:'Lọc danh sách',exact:true}).click();
  await expect(cv.page.getByText('1 đợt xét',{exact:true})).toBeVisible();
  await expect(cv.page.getByText('SYNTHETIC - KKHT HK1 - Dot 2',{exact:true})).toBeVisible();
 });
 await check('HB-T021-DEV03-student-direct-route-and-api-denied',async()=>{
  sv=await login('student');
  await sv.page.goto(origin+routePath+'?dotXetId=1');
  await expect(sv.page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(sv.page.getByRole('button',{name:'Học bổng',exact:true})).toHaveCount(0);
  const response=await sv.context.request.get(origin+api+'/kkht/dot-xet',{headers:{Authorization:'Bearer '+sv.token}});
  assert.equal(response.status(),403);assert.equal((await response.json()).code,'FORBIDDEN');
 });
 await check('HB-T021-DEV04-mobile-and-read-only',async()=>{
  await cv.page.setViewportSize({width:360,height:800});
  await cv.page.getByRole('button',{name:'Xem chi tiết SYNTHETIC - KKHT HK1 - Dot 2',exact:true}).click();
  await expect(cv.page.getByRole('heading',{name:'SYNTHETIC - KKHT HK1 - Dot 2',exact:true})).toBeVisible();
  assert(await cv.page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  assert(responses.length>0&&responses.every(response=>response.method==='GET'));
 });
}catch(error){console.error(safe(error.message));process.exitCode=1;}finally{
 for(const context of contexts){
  await context.request.post(origin+'/api/xac-thuc/dang-xuat',{headers:{Origin:origin,'X-CSRF-Protection':'1'}}).catch(()=>{});
  await context.close();
 }
 console.log(`HB browser DEV: ${cases.filter(item=>item.status==='PASS').length} PASS, ${cases.filter(item=>item.status==='FAIL').length} FAIL`);
 await browser.close();
}
