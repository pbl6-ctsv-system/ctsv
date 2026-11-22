// Daily DEV smoke: create two new synthetic rounds through the UI; preserve all existing data.
// No reset, cleanup, screenshots, report files or credential output.
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
const contexts=[],pages=[],passed=[],created=[],responses=[];
function safe(message){let text=String(message).replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]');for(const value of Object.values(passwords))text=text.replaceAll(value,'[REDACTED]');return text;}
async function check(id,run){await run();passed.push(id);console.log(id+' PASS (REAL DEV)');}
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1280,height:900}});contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(12000);
 pages.push(page);
 page.on('response',response=>{const pathname=new URL(response.url()).pathname;if(pathname.startsWith('/api/'))responses.push({role,path:pathname,status:response.status()});});
 await page.goto(origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 const pending=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/xac-thuc/dang-nhap');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 const response=await pending;assert.equal(response.status(),200);
 const issue=await response.json();
 await expect(page.getByRole('heading',{name:'Trang chủ CTSV',exact:true})).toBeVisible();
 return{page,context,token:issue.accessToken};
}
let cv,source,firstNumber;
async function availableNumber(start){
 for(let number=start;number<start+100;number++){
  const response=await cv.context.request.get(origin+api+`/kkht/dot-xet?sourceSemesterId=${source.sourceSemesterId}&number=${number}`,{headers:{Authorization:'Bearer '+cv.token}});
  assert.equal(response.status(),200);
  if((await response.json()).totalElements===0)return number;
 }
 throw new Error('No free synthetic round number; no data was overwritten.');
}
async function createInUi(number,mobile=false){
 const {page}=cv;
 if(mobile)await page.setViewportSize({width:360,height:800});
 await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
 await page.getByLabel('Tìm học kỳ',{exact:true}).fill('9420');
 await page.getByRole('button',{name:'Tìm học kỳ',exact:true}).click();
 await page.getByRole('radio',{name:'9420 · SYN-HB-T01-DEV HK2',exact:true}).check();
 await page.getByLabel('Số đợt',{exact:true}).fill(String(number));
 const name='SYN-HB-T022 - Đợt thực hành '+number;
 await page.getByLabel('Tên đợt',{exact:true}).fill(name);
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth));
 const pending=page.waitForResponse(response=>new URL(response.url()).pathname===api+'/kkht/dot-xet'&&response.request().method()==='POST');
 await page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
 const response=await pending;assert.equal(response.status(),201);assert(response.headers().etag);assert(response.headers().location);
 const detail=await response.json();
 assert.equal(detail.sourceSemesterId,source.sourceSemesterId);assert.equal(detail.status,'draft');
 await expect(page.getByRole('heading',{name,exact:true})).toBeVisible();
 await expect(page.getByText('Đã tạo đợt xét ở trạng thái Nháp.',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();
 await expect(page.getByText(name,{exact:true})).toBeVisible();
 await expect(page.getByText('1 đợt xét',{exact:true})).toBeVisible();
 created.push({id:detail.id,number:detail.number,semesterId:detail.semester.id});
}
try{
 await check('HB-T022-DEV01-verified-synthetic-source',async()=>{
  cv=await login('specialist');
  const response=await cv.context.request.get(origin+api+'/hoc-ky-nguon?q=9420',{headers:{Authorization:'Bearer '+cv.token}});
  assert.equal(response.status(),200);
  source=(await response.json()).items.find(item=>item.code==='9420');
  assert(source&&source.name==='SYN-HB-T01-DEV HK2'&&source.selectable);
  assert.equal(source.academicYear,'2094-2095');assert.equal(source.semesterOrder,2);
  await cv.page.goto(origin+routePath);
  await expect(cv.page.getByRole('heading',{name:'Danh sách đợt xét',exact:true})).toBeVisible();
  firstNumber=await availableNumber(22);
 });
 await check('HB-T022-DEV02-create-first-and-readback',async()=>{await createInUi(firstNumber);});
 await check('HB-T022-DEV03-create-second-reuses-period-mobile',async()=>{
  const number=await availableNumber(firstNumber+1);await createInUi(number,true);
  assert.equal(created[0].semesterId,created[1].semesterId);assert.notEqual(created[0].id,created[1].id);
 });
 await check('HB-T022-DEV04-student-create-forbidden',async()=>{
  const sv=await login('student');await sv.page.goto(origin+routePath);
  await expect(sv.page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(sv.page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toHaveCount(0);
  const response=await sv.context.request.post(origin+api+'/kkht/dot-xet',{headers:{Authorization:'Bearer '+sv.token},data:{sourceSemesterId:source.sourceSemesterId,number:firstNumber,name:'SYN-HB-T022 forbidden attempt'}});
  assert.equal(response.status(),403);
  assert.equal((await response.json()).code,'FORBIDDEN');
 });
 console.log('Created synthetic rounds: '+JSON.stringify(created));
}catch(error){
 console.error(safe(error.message));
 console.error('API statuses: '+JSON.stringify(responses));
 for(const page of pages){const feedback=await page.locator('[role=alert], [role=status]').allTextContents().catch(()=>[]);console.error('Visible feedback: '+safe(JSON.stringify(feedback)));}
 process.exitCode=1;
}finally{
 for(const context of contexts){await context.request.post(origin+'/api/xac-thuc/dang-xuat',{headers:{Origin:origin,'X-CSRF-Protection':'1'}}).catch(()=>{});await context.close();}
 console.log('HB create DEV: '+passed.length+' PASS; no existing records overwritten');
 await browser.close();
}
