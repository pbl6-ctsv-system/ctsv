import {openNav} from './navigation.mjs';
import {chromium} from '@playwright/test';
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';

const home=process.env.P06_BROWSER_HOME;
const pdf=process.env.P06_SAMPLE_PDF;
if(!home||!pdf)throw new Error('P06 requires local private browser home and synthetic PDF.');
const cfg=JSON.parse(await readFile(path.join(home,'credentials.json'),'utf8'));
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[];
const report={browser:browser.version(),environment:'HTTPS + Spring Boot + SQL Server P06',storage:cfg.storage,cases:[]};

async function login(username) {
 const context=await browser.newContext({ignoreHTTPSErrors:false,acceptDownloads:true});
 contexts.push(context);
 const p=await context.newPage();
 p.setDefaultTimeout(20000);
 await p.goto(cfg.origin);
 await p.getByLabel('Tên đăng nhập',{exact:true}).fill(username);
 await p.getByLabel('Mật khẩu',{exact:true}).fill(cfg.password);
 await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 await p.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
 await openNav(p,'Hồ sơ của tôi');
 return p;
}

async function check(id,verify) {
 try { await verify(); report.cases.push({id,status:'PASS'}); console.log(id+' PASS'); }
 catch(e) {report.cases.push({id,status:'FAIL'});throw e;}
}

let owner,id,code,file;
const view='Chi tiết hồ sơ';
try {
 await check('P06-B01_LIVE_UPLOAD_SUBMIT_AND_DOWNLOAD',async()=>{
  owner=await login(cfg.username);
  const option=owner.getByLabel('Đợt kê khai').locator('option[value="'+cfg.round+'"]');
  await option.waitFor({state:'attached'});
  const roundCode=(await option.innerText()).split(' · ').at(-1);
  await openNav(owner,'Đợt xét MGHP');
  const row=owner.getByRole('row').filter({has:owner.getByRole('cell',{name:roundCode,exact:true})});
  await row.getByRole('button',{name:'Xem đợt',exact:true}).click();
  const publicDetail=owner.getByRole('region',{name:'Thông tin đợt xét'});
  await publicDetail.getByText('SYNTHETIC P06 PDF',{exact:true}).waitFor();
  assert.equal(await owner.getByText('Nội dung cấu hình',{exact:true}).count(),0);
  assert.equal(await owner.getByRole('button',{name:'Thêm chính sách vào đợt',exact:true}).count(),0);
  await openNav(owner,'Hồ sơ của tôi');
  const round=owner.getByLabel('Đợt kê khai');
  await round.locator('option[value="'+cfg.round+'"]').waitFor({state:'attached'});
  await round.selectOption(cfg.round);
  await owner.getByLabel('Địa chỉ thường trú',{exact:true}).fill('SYNTHETIC P06 địa chỉ');
  const pick=owner.getByLabel('Chọn PDF minh chứng');
  await pick.setInputFiles({name:'not-a-pdf.txt',mimeType:'text/plain',buffer:Buffer.from('SYNTHETIC')});
  await owner.getByText('Chỉ chấp nhận tệp PDF.',{exact:true}).waitFor();
  const sent=owner.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/tep/minh-chung'));
  await pick.setInputFiles(pdf);
  const uploaded=await sent;
  assert.equal(uploaded.status(),201);
  file=(await uploaded.json()).id;
  await owner.getByText(/PDF đã tải:/).waitFor();
  await owner.getByLabel('PDF cho SYNTHETIC P06 PDF').selectOption(file);
  assert.equal(await owner.getByLabel(/^(Số giấy tờ|Cơ quan cấp|Ngày cấp|Ngày hết hạn) /).count(),0);
  await owner.getByLabel('Tôi cam kết lời khai đúng sự thật').check();
  const ethnicity=owner.getByLabel('ID dân tộc',{exact:true});
  const originalEthnicity=await ethnicity.inputValue();
  await ethnicity.fill('INVALID-DEV-ID');
  const rejected=owner.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/mghp/ho-so'));
  await owner.getByRole('button',{name:'Nộp hồ sơ',exact:true}).click();
  assert.equal((await rejected).status(),400);
  await owner.getByText('ID dân tộc phải là mã số có trong dữ liệu CTSV.',{exact:true}).waitFor();
  await ethnicity.fill(originalEthnicity);
  const created=owner.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/mghp/ho-so'));
  await owner.getByRole('button',{name:'Nộp hồ sơ',exact:true}).click();
  const response=await created;
  assert.equal(response.status(),201);
  id=(await response.json()).applicationId;
  const detail=owner.getByRole('region',{name:view});
  await detail.getByText('Đã nộp',{exact:true}).waitFor();
  code=await detail.getByRole('heading',{level:2}).innerText();
  await owner.getByRole('button',{name:code,exact:true}).waitFor();
  const downloading=owner.waitForEvent('download');
  await detail.getByRole('button',{name:/Tải PDF/}).click();
  const download=await downloading;
  const actual=createHash('sha256').update(await readFile(await download.path())).digest('hex');
  const expected=createHash('sha256').update(await readFile(pdf)).digest('hex');
  assert.equal(actual,expected);
 });
 await check('P06-B02_WRONG_OWNER_AND_WITHDRAW_READBACK',async()=>{
  const other=await login(cfg.other);
  await other.getByText('Chưa có hồ sơ.',{exact:true}).waitFor();
  const statuses=await other.evaluate(async ({id,file})=>{
   const {createAuthClient}=await import('/src/shared/auth/auth-client.mjs');
   const c=createAuthClient();await c.refresh();
   const status=async url=>{try{return (await c.request(url)).status;}catch(e){return e.status;}};
   return Promise.all([status('/api/ctsv/mghp/ho-so/'+id),status('/api/ctsv/tep/'+file+'/noi-dung')]);
  },{id,file});
  assert.deepEqual(statuses,[404,404]);
  await owner.getByLabel('Lý do rút hồ sơ').fill('SYNTHETIC P06 rút hồ sơ');
  const withdrawn=owner.waitForResponse(r=>r.url().endsWith('/rut'));
  await owner.getByRole('button',{name:'Rút hồ sơ',exact:true}).click();
  assert.equal((await withdrawn).status(),200);
  await owner.getByRole('region',{name:view}).getByText('Đã rút',{exact:true}).waitFor();
  await owner.getByRole('button',{name:code,exact:true}).locator('..').getByText('Đã rút',{exact:true}).waitFor();
 });
 await check('P06-B03_READ_NOTIFICATION',async()=>{
  await openNav(owner,'Thông báo');
  const notice=owner.getByRole('listitem').filter({has:owner.getByRole('heading',{name:'Hồ sơ đã được rút',exact:true})});
  await notice.getByRole('button',{name:'Đánh dấu đã đọc'}).click();
  await notice.getByText('Đã đọc',{exact:true}).waitFor();
  await owner.getByLabel('Chỉ thông báo chưa đọc').check();
  await notice.waitFor({state:'detached'});
 });
 report.status='PASS';
} catch(e) {
 report.status='FAIL'; report.error=String(e.message).replaceAll(cfg.password,'[REDACTED]').replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]');
 console.error(report.error);process.exitCode=1;
} finally {
 for(const c of contexts)await c.close();
 await browser.close();
 await writeFile(path.join(home,'browser-report.json'),JSON.stringify(report,null,2));
 await writeFile(path.join(home,'browser.done'),report.status);
}
