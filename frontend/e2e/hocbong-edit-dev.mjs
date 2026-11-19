// Daily DEV create/edit smoke; uses a new synthetic record, never resets or overwrites prior fixtures.
// No screenshots/evidence files; credentials stay in process memory.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
const home=process.env.CTSV_DEV_HOME;
if(!home||!process.env.CTSV_DEV_SMOKE_PASSWORDS)throw new Error('Use the protected DEV wrapper.');
const config=JSON.parse((await readFile(path.join(home,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin='https://127.0.0.1:'+config.FrontendPort,api='/api/ctsv/hoc-bong',routePath='/hoc-bong/kkht/dot-xet';
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[],pages=[],passed=[],responses=[];
function safe(message){let text=String(message).replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]');for(const value of Object.values(passwords))text=text.replaceAll(value,'[REDACTED]');return text;}
async function check(id,run){await run();passed.push(id);console.log(id+' PASS (REAL DEV)');}
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}});contexts.push(context);
 const page=await context.newPage();pages.push(page);page.setDefaultTimeout(12000);
 page.on('response',response=>{const url=new URL(response.url());if(url.pathname.startsWith('/api/'))responses.push({role,path:url.pathname,status:response.status()});});
 await page.goto(origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 const pending=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/xac-thuc/dang-nhap');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();const response=await pending;assert.equal(response.status(),200);const issue=await response.json();
 await expect(page.getByRole('heading',{name:'Trang chủ CTSV',exact:true})).toBeVisible({timeout:20000});
 return{page,context,token:issue.accessToken};
}
const isPut=response=>new URL(response.url()).pathname===api+'/kkht/dot-xet/'+roundId&&response.request().method()==='PUT';
async function read(session){const response=await session.context.request.get(origin+api+'/kkht/dot-xet/'+roundId,{headers:{Authorization:'Bearer '+session.token}});assert.equal(response.status(),200);return{round:await response.json(),etag:response.headers().etag};}
async function edit(session){await session.page.getByRole('button',{name:'Sửa đợt xét',exact:true}).click();await expect(session.page.getByRole('heading',{name:'Sửa đợt xét',exact:true})).toBeVisible();}
async function save(session,status){const pending=session.page.waitForResponse(isPut);await session.page.getByRole('button',{name:'Lưu thay đổi',exact:true}).click();const response=await pending;assert.equal(response.status(),status);return response;}
let a,b,source,roundId,number,newNumber,finalName;
try{
 await check('HB-T023-DEV01-create-edit-list-readback',async()=>{
  a=await login('specialist');
  const response=await a.context.request.get(origin+api+'/hoc-ky-nguon?q=9420',{headers:{Authorization:'Bearer '+a.token}});assert.equal(response.status(),200);
  source=(await response.json()).items.find(item=>item.code==='9420');assert(source?.selectable&&source.name==='SYN-HB-T01-DEV HK2'&&source.academicYear==='2094-2095'&&source.semesterOrder===2);
  for(let candidate=230;candidate<330;candidate+=2){
   const counts=await Promise.all([candidate,candidate+1].map(async n=>{const r=await a.context.request.get(origin+api+`/kkht/dot-xet?sourceSemesterId=${source.sourceSemesterId}&number=${n}`,{headers:{Authorization:'Bearer '+a.token}});assert.equal(r.status(),200);return(await r.json()).totalElements;}));
   if(counts.every(value=>value===0)){number=candidate;newNumber=candidate+1;break;}
  }
  assert(number,'No free synthetic pair; no prior rows overwritten');
  await a.page.goto(origin+routePath);await expect(a.page.getByRole('heading',{name:'Danh sách đợt xét',exact:true})).toBeVisible({timeout:20000});
  await a.page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await a.page.getByLabel('Tìm học kỳ',{exact:true}).fill('9420');await a.page.getByRole('button',{name:'Tìm học kỳ',exact:true}).click();
  await a.page.getByRole('radio',{name:'9420 · SYN-HB-T01-DEV HK2',exact:true}).check();
  await a.page.getByLabel('Số đợt',{exact:true}).fill(String(number));const original='SYN-HB-T023 - Tạo '+number;await a.page.getByLabel('Tên đợt',{exact:true}).fill(original);
  const pending=a.page.waitForResponse(r=>new URL(r.url()).pathname===api+'/kkht/dot-xet'&&r.request().method()==='POST');await a.page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  const createdResponse=await pending;assert.equal(createdResponse.status(),201);const created=await createdResponse.json();roundId=created.id;
  await expect(a.page.getByRole('heading',{name:original,exact:true})).toBeVisible();await edit(a);
  finalName='SYN-HB-T023 - Đã sửa '+newNumber;await a.page.getByLabel('Số đợt',{exact:true}).fill(String(newNumber));await a.page.getByLabel('Tên đợt',{exact:true}).fill(finalName);
  const updated=await save(a,200);assert.match(updated.request().headers()['if-match'],/^"[A-Za-z0-9_-]+"$/);
  await expect(a.page.getByRole('heading',{name:finalName,exact:true})).toBeVisible();
  const persisted=await read(a);assert.equal(persisted.round.semester.id,created.semester.id);assert.equal(persisted.round.sourceSemesterId,created.sourceSemesterId);assert.equal(persisted.round.number,newNumber);
  await a.page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();await expect(a.page.getByText(finalName,{exact:true})).toBeVisible();
 });
 await check('HB-T023-DEV02-two-sessions-conflict-explicit-adoption',async()=>{
  await a.page.goto(origin+routePath+'?dotXetId='+roundId);await expect(a.page.getByRole('heading',{name:finalName,exact:true})).toBeVisible({timeout:20000});
  b=await login('specialist');await b.page.goto(origin+routePath+'?dotXetId='+roundId);await expect(b.page.getByRole('heading',{name:finalName,exact:true})).toBeVisible({timeout:20000});
  await edit(a);await edit(b);
  const first='SYN-HB-T023 - Bản tab 1';await a.page.getByLabel('Tên đợt',{exact:true}).fill(first);await save(a,200);await expect(a.page.getByRole('heading',{name:first,exact:true})).toBeVisible();
  finalName='SYN-HB-T023 - Bản tab 2';await b.page.getByLabel('Tên đợt',{exact:true}).fill(finalName);const stale=await save(b,412);assert.equal((await stale.json()).code,'VERSION_CONFLICT');
  await expect(b.page.getByRole('alert')).toContainText('Dữ liệu đã được người khác thay đổi');await expect(b.page.getByLabel('Tên đợt',{exact:true})).toHaveValue(finalName);
  await b.page.getByRole('button',{name:'Đọc bản mới',exact:true}).click();await expect(b.page.getByText(first,{exact:true})).toBeVisible();assert.equal((await read(a)).round.name,first);
  await b.page.getByRole('button',{name:'Giữ nội dung nhập và dùng bản mới',exact:true}).click();await save(b,200);await expect(b.page.getByRole('heading',{name:finalName,exact:true})).toBeVisible();
 });
 await check('HB-T023-DEV03-noop-precondition-reload-mobile',async()=>{
  const before=await read(b);
  const missing=await b.context.request.put(origin+api+'/kkht/dot-xet/'+roundId,{headers:{Authorization:'Bearer '+b.token},data:{number:newNumber,name:finalName}});assert.equal(missing.status(),428);assert.equal((await missing.json()).code,'PRECONDITION_REQUIRED');
  await b.page.setViewportSize({width:360,height:800});await edit(b);assert(await b.page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
  const noop=await save(b,200);assert.equal(noop.headers().etag,before.etag);await expect(b.page.getByRole('heading',{name:finalName,exact:true})).toBeVisible();
  await b.page.reload();await expect(b.page.getByRole('heading',{name:finalName,exact:true})).toBeVisible({timeout:20000});assert.equal((await read(b)).round.name,finalName);
 });
 await check('HB-T023-DEV04-student-put-forbidden',async()=>{
  const sv=await login('student');await sv.page.goto(origin+routePath+'?dotXetId='+roundId);await expect(sv.page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible({timeout:20000});
  const current=await read(b);const response=await sv.context.request.put(origin+api+'/kkht/dot-xet/'+roundId,{headers:{Authorization:'Bearer '+sv.token,'If-Match':current.etag},data:{number:newNumber,name:finalName}});assert.equal(response.status(),403);assert.equal((await response.json()).code,'FORBIDDEN');
 });
 console.log('Synthetic edit result: '+JSON.stringify({id:roundId,number:newNumber,semesterId:(await read(b)).round.semester.id,name:finalName}));
}catch(error){console.error(safe(error.message));console.error('API statuses: '+JSON.stringify(responses));for(const page of pages){const feedback=await page.locator('[role=alert], [role=status]').allTextContents().catch(()=>[]);console.error('Visible feedback: '+safe(JSON.stringify(feedback)));}process.exitCode=1;}
finally{for(const context of contexts){await context.request.post(origin+'/api/xac-thuc/dang-xuat',{headers:{Origin:origin,'X-CSRF-Protection':'1'}}).catch(()=>{});await context.close();}console.log('HB edit DEV: '+passed.length+' PASS; previous records preserved');await browser.close();}
