import {chromium} from '@playwright/test';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';
import {openNav} from './navigation.mjs';

const devHome=process.env.CTSV_DEV_HOME;
const pdf=process.env.P07_SAMPLE_PDF;
const evidence=process.env.P07_DEV_EVIDENCE;
if(!devHome||!pdf||!evidence)throw new Error('DEV home, PDF and evidence directory are required.');
await mkdir(evidence,{recursive:true});
const cfg=JSON.parse((await readFile(path.join(devHome,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);
delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin='https://127.0.0.1:'+cfg.FrontendPort;
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[];
const report={date:'2026-11-18',browser:browser.version(),origin,database:'ctsv_dev',
 backend:'https://127.0.0.1:'+cfg.BackendPort,storage:'Cloudinary REAL',cases:[],fixture:{},pageErrors:[]};
const runId='P07-DEV-'+Date.now();

async function login(role) {
 const context=await browser.newContext({ignoreHTTPSErrors:false,acceptDownloads:true});
 contexts.push(context);
 const page=await context.newPage();
 page.setDefaultTimeout(20000);
 page.on('pageerror',error=>report.pageErrors.push(error.name));
 await page.goto(origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(cfg.Accounts[role].Username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 let response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 let signedIn=await response;
 if(signedIn.status()===429){
  const wait=Number(signedIn.headers()['retry-after']);
  assert(Number.isFinite(wait)&&wait>0&&wait<=900,'Valid login Retry-After required.');
  console.log('DEV '+role+' respects login Retry-After '+wait+'s');
  for(let remaining=wait+1;remaining>0;remaining-=Math.min(remaining,30)){
   await new Promise(resolve=>setTimeout(resolve,Math.min(remaining,30)*1000));
  }
  await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
  response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  signedIn=await response;
 }
 assert.equal(signedIn.status(),200,'DEV '+role+' login');
 const token=(await signedIn.json()).accessToken;
 await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
 return {page,token};
}

async function api(actor,method,url,body,tag,expected=200) {
 const headers={Authorization:'Bearer '+actor.token};
 if(tag)headers['If-Match']=tag;
 const response=await actor.page.request.fetch(origin+url,{method,headers,...(body===undefined?{}:{data:body})});
 assert.equal(response.status(),expected,method+' '+url);
 return {data:await response.json(),tag:response.headers().etag};
}

async function capture(page,method,suffix,status,action,readBody=false) {
 report.lastAction={method,suffix,expected:status};
 const replied=page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname.endsWith(suffix));
 await action();
 const response=await replied;
 assert.equal(response.status(),status,method+' '+suffix);
 return readBody?response.json():null;
}

async function check(id,action) {
 try {await action();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}
 catch(error){report.cases.push({id,status:'FAIL'});throw error;}
}

let specialist,student,admin,form,round,rootCode,supplement;
try {
 await check('P07-DEV01_LOGIN_AND_NEW_SYNTHETIC_ROUND',async()=>{
  specialist=await login('specialist');
  student=await login('student');
  admin=await login('admin');
  const retryRound=process.env.P07_DEV_ROUND_ID;
  if(retryRound) {
   assert(/^[1-9][0-9]*$/.test(retryRound),'Invalid DEV retry round ID.');
   round=(await api(specialist,'GET','/api/ctsv/mghp/dot-xet/'+retryRound)).data;
   assert(round.code.startsWith('P07-DEV-')&&round.status==='open','Retry requires an owned synthetic open round.');
   const existing=await api(specialist,'GET','/api/ctsv/mghp/ho-so?roundId='+retryRound);
   assert.equal(existing.data.totalElements,0,'Retry round already has a submission.');
   report.fixture={roundId:round.id,roundCode:round.code,policyVersionIds:round.policyVersions.map(p=>p.policyVersionId)};
   return;
  }
  const rounds=await api(specialist,'GET','/api/ctsv/mghp/dot-xet?size=100');
  const template=rounds.data.items.find(row=>row.code.startsWith('NAV-SYNTH-'));
  assert(template,'A synthetic DEV source round is required.');
  const original=(await api(specialist,'GET','/api/ctsv/mghp/dot-xet/'+template.id)).data;
  const fields=['semesterId','description','declarationGuidance','lateSubmissionGuidance','allowRetroactive',
   'retroactiveMode','midSemesterMode','policyVersions'];
  const body=Object.fromEntries(fields.map(key=>[key,original[key]]));
  const timestamp=seconds=>new Date(Math.floor(Date.now()/1000)*1000+seconds*1000).toISOString();
  Object.assign(body,{code:runId,name:'Đợt thực hành bổ sung hồ sơ · Tháng 11',
   description:'Dữ liệu mẫu để thực hành nộp và bổ sung hồ sơ miễn giảm học phí.',
   declarationGuidance:'Chuẩn bị đơn đề nghị và minh chứng, kiểm tra thông tin trước khi nộp.',
   opensAt:timestamp(-3600),submissionDeadline:timestamp(7*86400),reviewDeadline:timestamp(14*86400)});
  const created=await api(specialist,'POST','/api/ctsv/mghp/dot-xet',body,undefined,201);
  const opened=await api(specialist,'POST','/api/ctsv/mghp/dot-xet/'+created.data.id+'/mo',undefined,created.tag);
  round=opened.data;
  report.fixture={roundId:round.id,roundCode:round.code,policyVersionIds:body.policyVersions.map(p=>p.policyVersionId)};
 });
 await check('P07-DEV02_UI_SUBMISSION_REAL_UPLOAD',async()=>{
  const page=student.page;
  await openNav(page,'Hồ sơ MGHP');
  await page.getByRole('button',{name:'Nộp hồ sơ mới',exact:true}).click();
  const select=page.getByLabel('Đợt kê khai',{exact:true});
  await select.locator('option[value="'+round.id+'"]').waitFor({state:'attached'});
  const context=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/toi/ngu-canh-ke-khai'));
  await select.selectOption(round.id);
  form=await (await context).json();
  const policy=form.policyVersions[0];
  assert(policy.documentGroups.every(g=>g.documents.every(d=>d.evidenceSubject!=='cha_hoac_me')),
   'Synthetic DEV template needs a student-only document set.');
  await page.getByLabel('Địa chỉ thường trú',{exact:true}).fill('SYNTHETIC P07 DEV địa chỉ');
  await page.locator('datalist option').first().waitFor({state:'attached'});
  assert(!/^[0-9]+$/.test(await page.getByRole('combobox',{name:'Dân tộc',exact:true}).inputValue()),'Reference field displays the source name.');
  await page.screenshot({path:path.join(evidence,'student-form-desktop.png'),fullPage:true});
  await page.getByLabel('Đang học văn bằng thứ nhất').selectOption('true');
  await page.getByLabel('Đã hưởng ở cùng trình độ').selectOption('false');
  await page.getByLabel('Đang hưởng lương khi đi học').selectOption('false');
  await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();
  await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();
  const uploaded=await capture(page,'POST','/tep/minh-chung',201,()=>page.getByLabel('Chọn PDF minh chứng').setInputFiles(pdf),true);
  const filename=(await readFile(pdf)).byteLength;
  report.fixture.fileId=uploaded.id;
  report.pdf={file:'du lieu test.pdf',bytes:filename,sha256:createHash('sha256').update(await readFile(pdf)).digest('hex')};
  for(const group of policy.documentGroups){
   for(const requirement of group.documents.slice(0,group.minimumRequired)){
    await page.getByLabel('PDF cho '+requirement.name).selectOption(uploaded.id);
   }
  }
  await page.getByLabel('Tôi cam kết lời khai đúng sự thật').check();
  const ack=await capture(page,'POST','/mghp/ho-so',201,()=>page.getByRole('button',{name:'Nộp hồ sơ',exact:true}).click(),true);
  report.fixture.applicationId=ack.applicationId;
  report.fixture.initialVersionId=ack.versionId;
  rootCode=await page.getByRole('region',{name:'Chi tiết hồ sơ'}).getByRole('heading',{level:2}).innerText();
  report.fixture.applicationCode=rootCode;
  await page.screenshot({path:path.join(evidence,'student-record-desktop.png'),fullPage:true});
 });
 await check('P07-DEV03_UI_SUPPLEMENT_AND_CAPABILITY',async()=>{
  const page=specialist.page;
  await openNav(page,'Tiếp nhận & bổ sung hồ sơ');
  await page.locator('[data-application-id="'+report.fixture.applicationId+'"]').click();
  await page.getByRole('button',{name:'Bổ sung & kiểm giấy',exact:true}).click();
  await page.getByText('Mở vòng bổ sung',{exact:true}).click();
  await page.getByLabel('Lý do mở vòng').fill('Thực hành DEV: đề nghị xác nhận lại thông tin liên lạc.');
  const date=new Date(Date.now()+2*86400000);
  await page.getByLabel('Hạn bổ sung',{exact:true}).fill(new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16));
  await page.getByLabel('Nội dung cần bổ sung',{exact:true}).fill('SYNTHETIC xác nhận điện thoại');
  supplement=await capture(page,'POST','/yeu-cau-bo-sung',201,()=>page.getByRole('button',{name:'Gửi yêu cầu bổ sung',exact:true}).click(),true);
  report.fixture.supplementId=supplement.id;
  await page.getByRole('region',{name:'Chi tiết vòng bổ sung'}).waitFor();
  await page.screenshot({path:path.join(evidence,'specialist-supplement-desktop.png'),fullPage:true});
  await admin.page.goto(origin+'/mghp/ho-so');
  await admin.page.getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();
  await api(admin,'GET','/api/ctsv/mghp/yeu-cau-bo-sung/'+supplement.id,undefined,undefined,403);
  await openNav(student.page,'Thông báo');
  await student.page.locator('a[href="/mghp/ho-so?bo-sung='+supplement.id+'"]').click();
  await student.page.getByRole('button',{name:'Kê khai bổ sung',exact:true}).click();
  await student.page.getByLabel('Số điện thoại',{exact:true}).fill('0900000099');
  await student.page.getByRole('button',{name:'3. Minh chứng & nộp',exact:true}).click();
  await student.page.getByLabel('Tôi cam kết lời khai đúng sự thật').check();
  const reply=await capture(student.page,'POST','/'+supplement.id+'/phan-hoi',201,
   ()=>student.page.getByRole('button',{name:'Gửi phản hồi bổ sung',exact:true}).click(),true);
  report.fixture.responseVersionId=reply.versionId;
  await student.page.getByText('Phản hồi đã được ghi nhận.',{exact:true}).waitFor();
  assert.equal(await student.page.getByRole('button',{name:'Kê khai bổ sung',exact:true}).count(),0);
  await student.page.getByRole('button',{name:'Lịch sử & minh chứng',exact:true}).click();
  const download=student.page.waitForEvent('download');
  await student.page.getByRole('region',{name:'Chi tiết hồ sơ'}).getByRole('button',{name:/Tải PDF/}).first().click();
  const bytes=await readFile(await (await download).path());
  assert.equal(createHash('sha256').update(bytes).digest('hex'),report.pdf.sha256);
  report.pdf.downloadSha256=report.pdf.sha256;
 });
 await check('P07-DEV04_STALE_REVIEW_COMPLETE_AND_PAPER',async()=>{
  const page=specialist.page;
  await page.getByText('Đóng vòng bổ sung',{exact:true}).click();
  await page.getByLabel('Lý do đóng vòng',{exact:true}).fill('SYNTHETIC kiểm trạng thái cũ');
  await capture(page,'POST','/huy',412,()=>page.getByRole('button',{name:'Hủy vòng',exact:true}).click());
  await page.getByText('Dữ liệu đã thay đổi. Hãy đọc lại trước khi thao tác.',{exact:true}).waitFor();
  await page.getByRole('button',{name:'Đọc vòng bổ sung',exact:true}).click();
  await page.getByLabel('Kết quả mục bổ sung').selectOption('satisfied');
  await page.getByLabel('Lý do đánh giá',{exact:true}).fill('SYNTHETIC DEV xác nhận liên lạc');
  await capture(page,'PUT','/danh-gia',200,()=>page.getByRole('button',{name:'Lưu đánh giá mục',exact:true}).click());
  await page.getByText(/SYNTHETIC xác nhận điện thoại · Đáp ứng/).waitFor();
  await page.getByText('Đóng vòng bổ sung',{exact:true}).click();
  await page.getByLabel('Lý do đóng vòng',{exact:true}).fill('SYNTHETIC đã kiểm toàn bộ mục');
  await capture(page,'POST','/hoan-tat',200,()=>page.getByRole('button',{name:'Hoàn tất vòng',exact:true}).click());
  await page.getByRole('region',{name:'Chi tiết vòng bổ sung'}).getByText(/Đã hoàn tất/).waitFor();
  await page.getByRole('button',{name:'Đối chiếu & kiểm giấy',exact:true}).click();
  const checks=page.getByRole('region',{name:'Đối chiếu lời khai và giấy'});
  await checks.locator('details > summary').first().click();
  await capture(page,'POST','/xac-nhan-ban-cung',201,()=>checks.getByRole('button',{name:'Lưu kiểm giấy',exact:true}).first().click());
  await checks.getByText(/hard_copy · received/).waitFor();
  await page.reload();
  await page.locator('[data-application-id="'+report.fixture.applicationId+'"]').click();
  await page.getByRole('button',{name:'Bổ sung & kiểm giấy',exact:true}).click();
  await page.getByLabel('Mã vòng bổ sung',{exact:true}).fill(supplement.id);
  await page.getByRole('button',{name:'Đọc vòng bổ sung',exact:true}).click();
  await page.getByRole('region',{name:'Chi tiết vòng bổ sung'}).getByText(/Đã hoàn tất/).waitFor();
  await student.page.getByRole('button',{name:'Tổng quan',exact:true}).click();
  await student.page.locator('summary').filter({hasText:/^Rút hồ sơ$/}).click();
  await student.page.getByLabel('Lý do rút hồ sơ',{exact:true}).fill('Hoàn tất bài thực hành giao diện, rút hồ sơ mẫu');
  // The specialist changed the root after the student last read it. Preserve HS06 stale protection.
  await capture(student.page,'POST','/rut',412,()=>student.page.getByRole('button',{name:'Rút hồ sơ',exact:true}).click());
  await student.page.getByRole('alert').getByText('Dữ liệu đã thay đổi. Hãy đọc lại trước khi lưu.',{exact:true}).waitFor();
  await capture(student.page,'GET','/ho-so/'+report.fixture.applicationId,200,
   ()=>student.page.getByRole('button',{name:'Đọc lại hồ sơ',exact:true}).click());
  await student.page.locator('section[aria-busy="false"]').getByRole('button',{name:'Đọc lại hồ sơ',exact:true}).waitFor();
  await student.page.getByLabel('Lý do rút hồ sơ',{exact:true}).fill('Hoàn tất bài thực hành giao diện, rút hồ sơ mẫu');
  await capture(student.page,'POST','/rut',200,()=>student.page.getByRole('button',{name:'Rút hồ sơ',exact:true}).click());
  await student.page.getByRole('region',{name:'Chi tiết hồ sơ'}).getByText('Đã rút',{exact:true}).waitFor();
  await student.page.getByRole('button',{name:'Về danh sách',exact:true}).click();
  await student.page.locator('[data-application-id="'+report.fixture.applicationId+'"]').locator('../..').getByText('Đã rút',{exact:true}).waitFor();
  for(const actor of [specialist,student]){
   await actor.page.getByRole('button',{name:'Đăng xuất',exact:true}).click();
   await actor.page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();
  }
  assert.equal(report.pageErrors.length,0,'Browser runtime errors');
 });
}catch(error){
 report.error={name:error.name,message:String(error.message).replace(/Bearer\s+\S+/g,'Bearer [REDACTED]')};
 process.exitCode=1;
 console.error('P07 DEV smoke failed. See sanitized report.');
}finally{
 for(const context of contexts)await context.close();
 await browser.close();
 report.status=report.cases.length===4&&report.cases.every(c=>c.status==='PASS')?'PASS':'FAIL';
 await mkdir(evidence,{recursive:true});
 await writeFile(path.join(evidence,'browser-report.json'),JSON.stringify(report,null,2));
}
