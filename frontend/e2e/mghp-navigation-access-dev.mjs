import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import {openNav,revealNav} from './navigation.mjs';

const home=process.env.CTSV_DEV_HOME,evidence=process.env.CTSV_NAV_EVIDENCE;
if(!home||!evidence)throw new Error('Private DEV home and evidence directory required.');
const config=JSON.parse((await readFile(path.join(home,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);
delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin='https://127.0.0.1:'+config.FrontendPort;
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[];
const report={origin,database:'ctsv_dev',http:'REAL',external:'NONE',cases:[],pageErrors:[]};
await mkdir(evidence,{recursive:true});
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1440,height:1000}});
 contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(20000);
 page.on('pageerror',error=>report.pageErrors.push(error.name));
 await page.goto(origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 assert.equal((await response).status(),200,role+' real DEV login');
 await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
 return page;
}
async function denied(page,route){
 await page.goto(origin+route);
 await page.getByRole('alert').getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'Nộp hồ sơ mới',exact:true}).count(),0);
 assert.equal(await page.getByRole('button',{name:'Gửi yêu cầu bổ sung',exact:true}).count(),0);
}
async function check(id,action){
 try{await action();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}
 catch(error){report.cases.push({id,status:'FAIL'});throw error;}
}
try{
 await check('NAV-CASE01_SPECIALIST_CASEWORK_ONLY',async()=>{
  const page=await login('specialist');
  const listed=page.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname==='/api/ctsv/mghp/ho-so');
  await openNav(page,'Tiếp nhận & bổ sung hồ sơ');
  assert.equal((await listed).status(),200);
  await page.getByRole('heading',{name:'Tiếp nhận & bổ sung hồ sơ MGHP',exact:true}).waitFor();
  assert.equal(new URL(page.url()).pathname,'/mghp/tiep-nhan-ho-so');
  assert.equal(await page.getByRole('link',{name:'Hồ sơ của tôi',exact:true,includeHidden:true}).count(),0);
  assert.equal(await page.getByRole('button',{name:'Nộp hồ sơ mới',exact:true}).count(),0);
  await page.getByRole('button',{name:'Xem hồ sơ',exact:true}).first().click();
  await page.getByRole('button',{name:'Bổ sung & kiểm giấy',exact:true}).click();
  await page.getByRole('heading',{name:'Bổ sung và kiểm giấy',exact:true}).waitFor();
  await page.reload();
  await page.getByRole('heading',{name:'Tiếp nhận & bổ sung hồ sơ MGHP',exact:true}).waitFor();
  await revealNav(page,'Tiếp nhận & bổ sung hồ sơ');
  await page.screenshot({path:path.join(evidence,'specialist-navigation.png'),fullPage:true});
  await denied(page,'/mghp/ho-so');
 });
 await check('NAV-CASE02_STUDENT_OWN_APPLICATIONS_ONLY',async()=>{
  const page=await login('student');
  await openNav(page,'Hồ sơ của tôi');
  await page.getByRole('heading',{name:'Hồ sơ của tôi',exact:true}).waitFor();
  await page.getByRole('button',{name:'Nộp hồ sơ mới',exact:true}).waitFor();
  assert.equal(new URL(page.url()).pathname,'/mghp/ho-so');
  assert.equal(await page.getByRole('link',{name:'Tiếp nhận & bổ sung hồ sơ',exact:true,includeHidden:true}).count(),0);
  await denied(page,'/mghp/tiep-nhan-ho-so');
 });
 await check('NAV-CASE03_AUTH_ADMIN_HAS_NEITHER_CASEWORK_NOR_SUBMISSION',async()=>{
  const page=await login('admin');
  for(const label of ['Hồ sơ của tôi','Tiếp nhận & bổ sung hồ sơ'])
   assert.equal(await page.getByRole('link',{name:label,exact:true,includeHidden:true}).count(),0);
  await denied(page,'/mghp/ho-so');
  await denied(page,'/mghp/tiep-nhan-ho-so');
 });
 assert.equal(report.pageErrors.length,0);
}catch(error){
 const message=Object.values(passwords).filter(value=>typeof value==='string'&&value).reduce((text,password)=>text.replaceAll(password,'[REDACTED]'),String(error.message));
 report.error={name:error.name,message};process.exitCode=1;
 console.error('Navigation access smoke failed; see sanitized report.');
}finally{
 for(const context of contexts)await context.close();
 await browser.close();
 report.status=report.cases.length===3&&report.cases.every(test=>test.status==='PASS')?'PASS':'FAIL';
 await writeFile(path.join(evidence,'report.json'),JSON.stringify(report,null,2));
}
