import {chromium} from '@playwright/test';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';
import {openNav} from './navigation.mjs';

const devHome=process.env.CTSV_DEV_HOME;
const pdf=process.env.P08_SAMPLE_PDF;
const evidence=process.env.P08_DEV_EVIDENCE;
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
const runId='P08-DEV-'+Date.now();

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
 const [response]=await Promise.all([replied,action()]);
 if(response.status()!==status){const problem=await response.json().catch(()=>null);report.lastAction.actual=response.status();report.lastAction.code=problem?.code??null;}
 assert.equal(response.status(),status,method+' '+suffix);
 return readBody?response.json():null;
}

async function check(id,action) {
 try {await action();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}
 catch(error){report.cases.push({id,status:'FAIL'});throw error;}
}

let specialist,student,admin,round,evaluation;
try {
 await check('P08-DEV01_SYNTHETIC_ROUND_AND_STUDENT_SUBMISSION',async()=>{
  specialist=await login('specialist');student=await login('student');admin=await login('admin');
  if(process.env.P08_DEV_APPLICATION_ID){
   const id=process.env.P08_DEV_APPLICATION_ID;assert(/^[1-9][0-9]*$/.test(id),'Valid local application ID required.');
   const root=(await api(student,'GET','/api/ctsv/mghp/ho-so/'+id)).data;
   round=(await api(specialist,'GET','/api/ctsv/mghp/dot-xet/'+root.roundId)).data;
   assert(round.code.startsWith('P08-DEV-')&&root.status!=='withdrawn','Only an existing synthetic P08 DEV application may be reused.');
   const version=root.submittedVersions[0];assert(version?.documents?.length>0,'Existing submission and PDF required.');
   report.fixture={roundId:round.id,roundCode:round.code,applicationId:id,submissionId:version.id,fileId:version.documents[0].fileId};
   report.setup='RESUMED_SYNTHETIC_SUBMISSION; no new round, account or upload';
   report.pdf={file:'du lieu test.pdf',bytes:(await readFile(pdf)).byteLength,sha256:createHash('sha256').update(await readFile(pdf)).digest('hex')};
   return;
  }
  const rounds=(await api(specialist,'GET','/api/ctsv/mghp/dot-xet?size=100')).data;
  const template=rounds.items.find(row=>row.code.startsWith('NAV-SYNTH-'));
  assert(template,'An existing synthetic DEV policy/round template is required.');
  const original=(await api(specialist,'GET','/api/ctsv/mghp/dot-xet/'+template.id)).data;
  const fields=['semesterId','description','declarationGuidance','lateSubmissionGuidance','allowRetroactive','retroactiveMode','midSemesterMode','policyVersions'];
  const body=Object.fromEntries(fields.map(key=>[key,original[key]]));
  const timestamp=seconds=>new Date(Math.floor(Date.now()/1000)*1000+seconds*1000).toISOString();
  Object.assign(body,{code:runId,name:'Thực hành đánh giá hồ sơ · Tháng 11',description:'Dữ liệu mẫu thực hành đánh giá và đối chiếu nguồn, chưa cấp quyền hưởng.',
   declarationGuidance:'Kiểm tra thông tin, đính kèm đơn và minh chứng trước khi nộp.',opensAt:timestamp(-3600),submissionDeadline:timestamp(7*86400),reviewDeadline:timestamp(14*86400)});
  const created=await api(specialist,'POST','/api/ctsv/mghp/dot-xet',body,undefined,201);
  round=(await api(specialist,'POST','/api/ctsv/mghp/dot-xet/'+created.data.id+'/mo',undefined,created.tag)).data;
  report.fixture={roundId:round.id,roundCode:round.code};
  const page=student.page;await openNav(page,'Hồ sơ của tôi');await page.getByRole('button',{name:'Nộp hồ sơ mới',exact:true}).click();
  const select=page.getByLabel('Đợt kê khai',{exact:true});await select.locator('option[value="'+round.id+'"]').waitFor({state:'attached'});
  const pending=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/toi/ngu-canh-ke-khai'));await select.selectOption(round.id);const context=await (await pending).json();
  const policy=context.policyVersions[0];assert(policy.documentGroups.every(g=>g.documents.every(d=>d.evidenceSubject!=='cha_hoac_me')),'Synthetic template must match this student-only fixture.');
  await page.getByLabel('Địa chỉ thường trú',{exact:true}).fill('Dữ liệu mẫu · 54 Nguyễn Lương Bằng, Đà Nẵng');
  await page.getByLabel('Đang học văn bằng thứ nhất').selectOption('true');await page.getByLabel('Đã hưởng ở cùng trình độ').selectOption('false');await page.getByLabel('Đang hưởng lương khi đi học').selectOption('false');
  await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();
  const file=await capture(page,'POST','/tep/minh-chung',201,()=>page.getByLabel('Chọn PDF minh chứng').setInputFiles(pdf),true);
  report.pdf={file:'du lieu test.pdf',bytes:(await readFile(pdf)).byteLength,sha256:createHash('sha256').update(await readFile(pdf)).digest('hex')};report.fixture.fileId=file.id;
  for(const group of policy.documentGroups)for(const document of group.documents.slice(0,group.minimumRequired))await page.getByLabel('PDF cho '+document.name).selectOption(file.id);
  await page.getByLabel('Tôi cam kết lời khai đúng sự thật').check();
  const ack=await capture(page,'POST','/mghp/ho-so',201,()=>page.getByRole('button',{name:'Nộp hồ sơ',exact:true}).click(),true);
  report.fixture.applicationId=ack.applicationId;report.fixture.submissionId=ack.versionId;
 });
 await check('P08-DEV02_DRAFT_EDIT_CONFIRM_REVISION',async()=>{
  const page=specialist.page;await openNav(page,'Đánh giá hồ sơ');await page.getByRole('button',{name:'Chọn hồ sơ '+report.fixture.applicationId,exact:true}).click();
  const context=(await api(specialist,'GET','/api/ctsv/mghp/ho-so/'+report.fixture.applicationId+'/ngu-canh-xet-duyet')).data;
  const existing=context.evaluations.find(e=>e.status==='draft');
  if(existing)await page.getByRole('button',{name:new RegExp('^Đánh giá '+existing.id+' ·')}).click();
  else await page.getByRole('button',{name:'Chuẩn bị đánh giá',exact:true}).click();
  const results=page.getByRole('combobox',{name:'Kết quả',exact:true});await results.first().waitFor({state:'visible'});
  assert(await results.count()>0,'Loaded assessment results required.');
  for(let i=0;i<await results.count();i++)await results.nth(i).selectOption('can_xac_minh');
  await page.getByLabel('Lý do nhận định',{exact:true}).fill('Thực hành DEV: chờ đối chiếu minh chứng, chưa đủ căn cứ kết luận đạt.');
  const notes=page.getByLabel('Nhận xét tiêu chí',{exact:true});for(let i=0;i<await notes.count();i++)await notes.nth(i).fill('Cần chuyên viên xác minh nguồn, không suy quyền hưởng từ lời khai.');
  if(!existing)await page.getByRole('button',{name:'Thêm giấy làm căn cứ',exact:true}).click();await page.getByLabel('Lý do dùng giấy',{exact:true}).fill('Minh chứng mẫu đã nộp, nội dung đang chờ đối chiếu.');
  evaluation=await capture(page,existing?'PUT':'POST',existing?'/'+existing.id:'/danh-gia',existing?200:201,()=>page.getByRole('button',{name:'Lưu bản nháp đánh giá',exact:true}).click(),true);
  assert.equal(evaluation.result,'can_xac_minh');assert(evaluation.criteria.every(c=>c.result==='can_xac_minh'));
  report.fixture.evaluationId=evaluation.id;
  await page.getByRole('status').getByText('Đã lưu đánh giá và đọc lại dữ liệu mới.',{exact:true}).waitFor();
  await page.getByLabel('Lý do nhận định',{exact:true}).fill('Thực hành DEV: đã đọc lại nguồn, vẫn cần xác minh điều kiện.');
  await capture(page,'PUT','/'+evaluation.id,200,()=>page.getByRole('button',{name:'Lưu bản nháp đánh giá',exact:true}).click());
  await page.getByRole('status').getByText('Đã lưu đánh giá và đọc lại dữ liệu mới.',{exact:true}).waitFor();
  await page.getByLabel('Lý do thao tác đánh giá',{exact:true}).fill('Xác nhận nhận định cần xác minh, không cấp quyền hưởng.');
  await capture(page,'POST','/xac-nhan',200,()=>page.getByRole('button',{name:'Xác nhận đánh giá',exact:true}).click());await page.getByText('Đã xác nhận',{exact:true}).waitFor();
  await page.screenshot({path:path.join(evidence,'dev-evaluation-confirmed.png'),fullPage:true});
  await page.getByLabel('Lý do thao tác đánh giá',{exact:true}).fill('Thực hành đọc nguồn ở bản đánh giá kế tiếp.');
  const next=await capture(page,'POST','/phien-ban',201,()=>page.getByRole('button',{name:'Tạo bản đánh giá lại',exact:true}).click(),true);
  assert.equal(next.previousEvaluationId,evaluation.id);report.fixture.evaluationId=evaluation.id;report.fixture.draftRevisionId=next.id;
  await page.getByText('Bản nháp',{exact:true}).waitFor();await page.reload();await page.getByLabel('Mã đánh giá cần đọc',{exact:true}).fill(next.id);
  await page.getByRole('button',{name:'Đọc đánh giá',exact:true}).click();await page.getByText('Bản nháp',{exact:true}).waitFor();
 });
 await check('P08-DEV03_REVIEW_LIST_AND_ACCESS',async()=>{
  await openNav(specialist.page,'Rà soát quyền hưởng');await specialist.page.getByLabel('Học kỳ rà soát',{exact:true}).selectOption(round.semesterId);
  await capture(specialist.page,'GET','/can-ra-soat',200,()=>specialist.page.getByRole('button',{name:'Xem quyền cần rà soát',exact:true}).click());
  for(const actor of [student,admin]){await actor.page.goto(origin+'/mghp/danh-gia');await actor.page.getByRole('alert').getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();}
  await openNav(student.page,'Hồ sơ của tôi');await student.page.locator('[data-application-id="'+report.fixture.applicationId+'"]').click();
  // ApplicationDetail has no withdrawability flag. Exercise the server fence through the existing UI.
  await student.page.locator('summary').filter({hasText:/^Rút hồ sơ$/}).click();
  await student.page.getByLabel('Lý do rút hồ sơ',{exact:true}).fill('Thực hành kiểm mốc chặn rút sau xác nhận đánh giá.');
  const denied=await capture(student.page,'POST','/rut',409,()=>student.page.getByRole('button',{name:'Rút hồ sơ',exact:true}).click(),true);
  assert.equal(denied.code,'APPLICATION_NOT_WITHDRAWABLE');await student.page.getByRole('alert').waitFor({state:'visible'});
  const unchanged=(await api(student,'GET','/api/ctsv/mghp/ho-so/'+report.fixture.applicationId)).data;assert.notEqual(unchanged.status,'withdrawn');
  await student.page.getByRole('button',{name:'Lịch sử & minh chứng',exact:true}).click();const download=student.page.waitForEvent('download');
  await student.page.getByRole('region',{name:'Chi tiết hồ sơ'}).getByRole('button',{name:/Tải PDF/}).first().click();
  const bytes=await readFile(await (await download).path());assert.equal(createHash('sha256').update(bytes).digest('hex'),report.pdf.sha256);report.pdf.downloadSha256=report.pdf.sha256;
  for(const actor of [specialist,student,admin]){await actor.page.getByRole('button',{name:'Đăng xuất',exact:true}).click();await actor.page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true}).waitFor();}
  assert.equal(report.pageErrors.length,0,'No browser runtime errors.');
 });
}catch(error){report.error={name:error.name,message:String(error.message).replace(/Bearer\s+\S+/g,'Bearer [REDACTED]')};process.exitCode=1;if(specialist)await specialist.page.screenshot({path:path.join(evidence,'failure.png'),fullPage:true}).catch(()=>{});console.error('P08 DEV smoke failed; see sanitized report.');}
finally{for(const context of contexts)await context.close();await browser.close();report.status=report.cases.length===3&&report.cases.every(c=>c.status==='PASS')?'PASS':'FAIL';await writeFile(path.join(evidence,'browser-report.json'),JSON.stringify(report,null,2));}
