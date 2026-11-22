// Daily DEV smoke: existing synthetic terms; one append-only synthetic round, no cleanup/reset.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
if(!process.env.CTSV_DEV_HOME||!process.env.CTSV_DEV_SMOKE_PASSWORDS)throw new Error('Run through DEV wrapper with protected process credentials.');
const config=JSON.parse((await readFile(path.join(process.env.CTSV_DEV_HOME,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin='https://127.0.0.1:'+config.FrontendPort,routePath='/hoc-bong/kkht/dot-xet',api='/api/ctsv/hoc-bong';
const browser=await chromium.launch({channel:'chrome',headless:true}),contexts=[],calls=[],authCalls=[];let count=0,cv,sv,first,second,created;
const safe=value=>{let s=String(value).replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]');for(const p of Object.values(passwords))s=s.replaceAll(p,'[REDACTED]');return s;};
async function check(id,run){if(process.env.CTSV_HB_DEV_CASE&&!id.includes(process.env.CTSV_HB_DEV_CASE))return;await run();count++;console.log(id+' PASS (REAL DEV)');}
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}});contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(15000);
 page.on('request',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/xac-thuc/'))authCalls.push({role,event:'request',path:u.pathname,method:r.method()});});
 page.on('response',r=>{const u=new URL(r.url());if(u.pathname.startsWith('/api/xac-thuc/'))authCalls.push({role,event:'response',path:u.pathname,status:r.status()});});
 page.on('requestfailed',r=>authCalls.push({role,event:'failed',path:new URL(r.url()).pathname,error:r.failure()?.errorText}));
 page.on('response',r=>{const u=new URL(r.url());if(u.pathname.startsWith(api+'/'))calls.push({role,path:u.pathname,method:r.request().method(),status:r.status()});});
 if(role==='student'){
  // Real credential login/cookie setup. Student login-form transport was intermittent on DEV;
  // the scope here is HB route/API denial after the normal browser refresh/identity boundary.
  const response=await context.request.post('https://127.0.0.1:'+config.BackendPort+'/api/xac-thuc/dang-nhap',{
   headers:{Origin:origin,'X-CSRF-Protection':'1'},data:{username:config.Accounts[role].Username,password:passwords[role]},timeout:15000});
  if(response.status()===429){const error=new Error('Auth DEV rate limited; Retry-After '+response.headers()['retry-after']+' seconds');error.name='DevAuthRateLimit';throw error;}
  assert.equal(response.status(),200);const issued=await response.json();return{context,page,token:issued.accessToken};
 }
 await page.goto(origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 const pending=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();const response=await pending;assert.equal(response.status(),200);const issued=await response.json();await expect(page.getByRole('heading',{name:'Trang chủ CTSV',exact:true})).toBeVisible();
 return{context,page,token:issued.accessToken};
}
async function get(actor,path){const r=await actor.context.request.get(origin+path,{headers:{Authorization:'Bearer '+actor.token}});assert.equal(r.status(),200,'DEV GET failed: '+path);return r.json();}
async function choose(source){const p=cv.page;await p.getByLabel('Tìm học kỳ',{exact:true}).fill(source.code);await p.getByRole('button',{name:'Tìm học kỳ',exact:true}).click();await p.getByRole('radio',{name:source.code+' · '+(source.name??'Chưa có tên'),exact:true}).check();await expect(p).toHaveURL(new RegExp('hocKyNguonId='+source.sourceSemesterId));}
try{
 await check('HB-T026-DEV01-default-and-sources',async()=>{
  cv=await login('specialist');const terms=await get(cv,api+'/hoc-ky-nguon?size=100');assert(terms.totalElements<=100);
  first=terms.items.find(s=>s.code==='9410');second=terms.items.find(s=>s.code==='9420');
  assert(first?.selectable&&second?.selectable&&first.academicYear==='2094-2095'&&second.academicYear==='2094-2095','Expected owner-prepared synthetic source terms');
  const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
  assert.equal(terms.items.filter(s=>s.selectable&&s.startDate<=today).length,0,'DEV baseline changed: review default expectation before smoke');
  const pending=cv.page.waitForResponse(r=>new URL(r.url()).pathname===api+'/hoc-ky-nguon/mac-dinh');await cv.page.goto(origin+routePath);
  assert.deepEqual(await (await pending).json(),{semester:null,reason:'NO_ELIGIBLE_SEMESTER'});await expect(cv.page.getByText(/Chưa có học kỳ phù hợp để chọn mặc định/)).toBeVisible();await expect(cv.page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeDisabled();
 });
 await check('HB-T026-DEV02-selection-history-reload',async()=>{
  await choose(first);await expect(cv.page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeEnabled();await choose(second);
  await cv.page.goBack();await expect(cv.page).toHaveURL(new RegExp('hocKyNguonId='+first.sourceSemesterId));await expect(cv.page.getByText('Học kỳ đã chọn: '+first.code+' · '+first.name,{exact:true})).toBeVisible();
  await cv.page.goForward();await cv.page.reload();await expect(cv.page.getByText('Học kỳ đã chọn: '+second.code+' · '+second.name,{exact:true})).toBeVisible();
 });
 await check('HB-T026-DEV03-legacy-detail-restores-source',async()=>{
  const list=await get(cv,api+'/kkht/dot-xet?sourceSemesterId='+first.sourceSemesterId+'&size=100');assert(list.items.length>0);
  const row=list.items[0],before=calls.filter(c=>c.path.endsWith('/mac-dinh')).length;await cv.page.goto(origin+routePath+'?dotXetId='+row.id);await expect(cv.page.getByRole('heading',{name:row.name,exact:true})).toBeVisible();await expect(cv.page).toHaveURL(new RegExp('hocKyNguonId='+first.sourceSemesterId));assert.equal(calls.filter(c=>c.path.endsWith('/mac-dinh')).length,before);
  await cv.page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();await expect(cv.page.getByText('Học kỳ đã chọn: '+first.code+' · '+first.name,{exact:true})).toBeVisible();
 });
 await check('HB-T026-DEV04-create-inherits-period',async()=>{
  await choose(second);const list=await get(cv,api+'/kkht/dot-xet?sourceSemesterId='+second.sourceSemesterId+'&size=100');assert(list.totalElements<=100);
  if(process.env.CTSV_HB_DEV_EXISTING_ROUND){
   created=await get(cv,api+'/kkht/dot-xet/'+process.env.CTSV_HB_DEV_EXISTING_ROUND);
   assert.equal(created.name,'SYNTHETIC - T02.6 - Đợt theo học kỳ đã chọn');assert.equal(created.sourceSemesterId,second.sourceSemesterId);
   await cv.page.goto(origin+routePath+'?hocKyNguonId='+second.sourceSemesterId+'&dotXetId='+created.id);
   await expect(cv.page.getByRole('heading',{name:created.name,exact:true})).toBeVisible();
   console.log('Recovery read-only: existing synthetic round '+created.id);return;
  }
  const number=Math.max(0,...list.items.map(r=>r.number))+1;assert(number<2147483647);
  await cv.page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await expect(cv.page.getByText(second.code+' · '+second.name,{exact:true})).toBeVisible();await expect(cv.page.getByLabel('Tìm học kỳ',{exact:true})).toHaveCount(0);
  await cv.page.getByLabel('Số đợt',{exact:true}).fill(String(number));await cv.page.getByLabel('Tên đợt',{exact:true}).fill('SYNTHETIC - T02.6 - Đợt theo học kỳ đã chọn');
  const pending=cv.page.waitForResponse(r=>new URL(r.url()).pathname===api+'/kkht/dot-xet'&&r.request().method()==='POST');await cv.page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();const response=await pending;assert.equal(response.status(),201);created=await response.json();assert.equal(created.sourceSemesterId,second.sourceSemesterId);assert.equal(created.semester.id,second.scholarshipSemesterId);
  await expect(cv.page.getByRole('heading',{name:created.name,exact:true})).toBeVisible();await expect(cv.page).toHaveURL(new RegExp('hocKyNguonId='+second.sourceSemesterId+'.*dotXetId='+created.id));
  console.log('Synthetic append: round '+created.id+', source '+created.sourceSemesterId+', number '+created.number);
 });
 await check('HB-T026-DEV05-mobile-context',async()=>{
  await cv.page.setViewportSize({width:360,height:800});await cv.page.reload();await expect(cv.page.getByRole('heading',{name:created.name,exact:true})).toBeVisible();assert(await cv.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await cv.page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();await expect(cv.page.getByText('Học kỳ đã chọn: '+second.code+' · '+second.name,{exact:true})).toBeVisible();assert(await cv.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 });
 await check('HB-T026-DEV06-student-route-and-api',async()=>{
  const sourceId=first?.sourceSemesterId??'2';
  sv=await login('student');await sv.page.goto(origin+routePath+'?hocKyNguonId='+sourceId);await expect(sv.page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();await expect(sv.page.getByRole('button',{name:'Học bổng',exact:true})).toHaveCount(0);
  for(const endpoint of ['/hoc-ky-nguon/mac-dinh','/hoc-ky-nguon/'+sourceId]){const r=await sv.context.request.get(origin+api+endpoint,{headers:{Authorization:'Bearer '+sv.token}});assert.equal(r.status(),403);assert.equal((await r.json()).code,'FORBIDDEN');}
  assert.equal(calls.filter(c=>c.method==='POST').length,process.env.CTSV_HB_DEV_EXISTING_ROUND?0:1);assert(!calls.some(c=>['PUT','DELETE','PATCH'].includes(c.method)));
 });
}catch(e){
 console.error(safe(e.message));
 if(e.name==='DevAuthRateLimit')console.error('BLOCKED by existing Auth policy; no capability assertion was skipped or changed.');
 else{
  if(cv){console.error('DEV diagnostic UI: '+safe((await cv.page.locator('main').innerText().catch(()=>''))).slice(0,1400));console.error('DEV last HB responses: '+JSON.stringify(calls.slice(-10)));}
  for(const c of contexts){const p=c.pages().at(-1);if(p)console.error('DEV actor UI: '+safe((await p.locator('body').innerText({timeout:1000}).catch(()=>''))).slice(0,1200));}
  console.error('DEV auth diagnostic: '+JSON.stringify(authCalls.slice(-14)));
 }
 process.exitCode=1;
}finally{
 for(const c of contexts){const r=await c.request.post(origin+'/api/xac-thuc/dang-xuat',{headers:{Origin:origin,'X-CSRF-Protection':'1'},timeout:10000}).catch(e=>{console.error('DEV logout transport: '+safe(e.message).split('\n')[0]);return null;});if(r?.status()!==204){console.error('DEV logout not verified, status '+(r?.status()??'transport error'));process.exitCode=1;}await c.close();}
 console.log('T02.6 REAL DEV: '+count+' PASS; no SQL reset/cleanup; no screenshots/evidence');await browser.close();
}
