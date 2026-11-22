import {chromium,expect} from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
const mode=process.argv[2]??'audit',origin='https://127.0.0.1:5173',out=path.resolve('target/student-journey-dev-20261121');
await fs.mkdir(out,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[];const report={mode,origin,database:'ctsv_dev',http:'REAL',sql:'REAL',cases:[],pageErrors:[]};
async function login(kind){
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1440,height:1000}});contexts.push(context);
 const page=await context.newPage();page.on('pageerror',e=>report.pageErrors.push(e.message));await page.goto(origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(process.env['RF_'+kind.toUpperCase()+'_USER']);
 async function signIn(){await page.getByLabel('Mật khẩu',{exact:true}).fill(process.env['RF_'+kind.toUpperCase()+'_PASSWORD']);const[r]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap'&&r.request().method()==='POST'),page.getByRole('button',{name:'Đăng nhập',exact:true}).click()]);return r;}
 let response=await signIn();if(response.status()===429){const seconds=Number(response.headers()['retry-after']);assert.ok(seconds>0&&seconds<=900);console.log('Respecting Retry-After '+seconds+'s');for(let left=seconds+1;left>0;left-=Math.min(left,30))await new Promise(r=>setTimeout(r,Math.min(left,30)*1000));response=await signIn();}
 assert.equal(response.status(),200);const token=(await response.json()).accessToken;await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();return {page,token};
}
async function api(actor,url){return actor.page.request.get(origin+url,{headers:{Authorization:'Bearer '+actor.token}});}
async function check(id,page,task){await task();report.cases.push({id,status:'PASS'});await page.screenshot({path:path.join(out,id+'.png'),fullPage:true});console.log(id+' PASS');}
async function capture(page,method,suffix,status,action,readBody=true){const[r]=await Promise.all([page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname.endsWith(suffix)),action()]);assert.equal(r.status(),status,method+' '+suffix);return readBody?r.json():null;}
const wall=days=>new Date(Date.now()+days*86400000+7*3600000).toISOString().slice(0,16);
async function findRound(page,round){
 await page.goto(origin+'/mghp/dot-xet');
 const loaded=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/dot-xet')&&new URL(r.url()).searchParams.get('semesterId')===round.semesterId);
 await page.getByLabel('Lọc học kỳ',{exact:true}).selectOption(round.semesterId);await loaded;await expect(page.getByText('Đang tải hoặc xử lý…',{exact:true})).toHaveCount(0);
 for(let p=0;p<30;p++){const b=page.getByRole('row').filter({hasText:round.code}).getByRole('button',{name:'Xem đợt',exact:true});if(await b.count()){await b.click();return;}await capture(page,'GET','/dot-xet',200,()=>page.getByRole('button',{name:'Trang sau',exact:true}).click());await expect(page.getByText('Đang tải hoặc xử lý…',{exact:true})).toHaveCount(0);}throw Error('Round not found');
}
try{
 const student=await login('student'),staff=await login('specialist');
 const access=await (await api(student,'/api/ctsv/nen-tang/toi/quyen-giao-dien')).json();report.studentAccess=access;
 assert.equal(access.student,true);assert.equal(access.mghpSpecialist,false);
 const mine=await (await api(student,'/api/ctsv/mghp/ho-so?size=100')).json(),all=await (await api(staff,'/api/ctsv/mghp/ho-so?size=100')).json();
 report.scope={studentCount:mine.totalElements,staffCount:all.totalElements,studentApplicationIds:mine.items.map(x=>x.id)};
 const foreign=all.items.find(x=>!mine.items.some(m=>m.id===x.id));
 if(foreign){const response=await api(student,'/api/ctsv/mghp/ho-so/'+foreign.id);report.scope.foreignId=foreign.id;report.scope.foreignDetailStatus=response.status();assert.equal(response.status(),404);}
 else report.scope.foreignDetail='NOT VERIFIED: no different-owner application in current DEV data';
 if(mode==='audit'){
  await student.page.goto(origin+'/mghp/ho-so');await expect(student.page.getByRole('heading',{name:'Hồ sơ miễn giảm học phí',exact:true})).toBeVisible();
  await student.page.getByRole('button',{name:'Miễn giảm học phí',exact:false}).click();await student.page.screenshot({path:path.join(out,'before-student-menu.png'),fullPage:true});
  report.cases.push({id:'AUDIT_STUDENT_SCOPE',status:'PASS'});
  await fs.writeFile(path.join(out,'scope-internal.json'),JSON.stringify({all:all.items.map(x=>({id:x.id,studentId:x.studentId})),mine:mine.items.map(x=>x.id)},null,2));
 }else{
 const page=student.page;
 await check('SJ01_PERSONAL_MENU_AND_SCOPE',page,async()=>{
  await page.goto(origin+'/mghp/ho-so');await expect(page.getByRole('heading',{name:'Hồ sơ của tôi',exact:true})).toBeVisible();await page.getByRole('button',{name:'Miễn giảm học phí',exact:false}).click();
  await expect(page.getByRole('link',{name:'Hồ sơ của tôi',exact:true})).toBeVisible();await expect(page.getByRole('link',{name:'Quyền hưởng của tôi',exact:true})).toBeVisible();await expect(page.getByRole('link',{name:'Rà soát quyền hưởng',exact:true})).toHaveCount(0);await page.keyboard.press('Escape');
  assert(mine.items.every(x=>!Object.hasOwn(x,'studentId')&&!Object.hasOwn(x,'fullName')));
  await page.goto(origin+'/mghp/tiep-nhan-ho-so');await expect(page.getByRole('alert').getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  assert.equal((await api(student,'/api/ctsv/mghp/quyen-huong/can-ra-soat?semesterId=1')).status(),403);
 });
 await check('SJ02_UPCOMING_REASON_AND_DEEP_LINK_GUARD',page,async()=>{
  const future=await (await api(student,'/api/ctsv/mghp/dot-xet/20033')).json();await findRound(page,future);
  await expect(page.getByRole('heading',{name:'Chưa đến thời gian nhận hồ sơ',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Chưa đến thời gian nhận hồ sơ',exact:true})).toBeDisabled();await expect(page.getByRole('link',{name:'Kê khai và nộp hồ sơ',exact:true})).toHaveCount(0);
  await page.goto(origin+'/mghp/ho-so?dot_xet_id='+future.id);await expect(page.getByRole('alert').last()).toContainText('Bạn có thể kê khai và nộp từ');await expect(page.getByRole('heading',{name:'Kê khai hồ sơ',exact:true})).toHaveCount(0);
 });
 let round,formContext;
 await check('SJ03_STAFF_CREATES_SYNTHETIC_RECEIVING_ROUND',staff.page,async()=>{
  const prior=await fs.readFile(path.join(out,'verify-report.json'),'utf8').then(JSON.parse).catch(()=>null);
  if(prior?.error&&prior.round){const candidate=await(await api(staff,'/api/ctsv/mghp/dot-xet/'+prior.round.id)).json();if(candidate.code===prior.round.code&&candidate.code.startsWith('STUDENT-UX-')&&candidate.status==='open'&&!all.items.some(x=>x.roundId===candidate.id)){round=candidate;report.round={id:round.id,code:round.code,semesterId:round.semesterId};report.fixture='Reused owned synthetic round from interrupted UI run';await findRound(staff.page,round);return;}}
  const p=staff.page;await p.goto(origin+'/mghp/dot-xet');await p.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();await p.getByLabel('Mã',{exact:true}).fill('STUDENT-UX-'+Date.now());await p.getByLabel('Tên',{exact:true}).fill('Đợt thực hành nộp hồ sơ sinh viên — SYNTHETIC');
  const semester=p.getByLabel('Học kỳ',{exact:true});await semester.selectOption(await semester.locator('option').evaluateAll(o=>o.find(x=>x.value)?.value));
  for(const[label,d]of [['Mở nhận lúc',-1],['Hạn nộp',5],['Hạn rà soát',10]])await p.getByLabel(label,{exact:true}).fill(wall(d));
  const table=p.getByRole('table',{name:'Chính sách áp dụng trong đợt'});await table.getByLabel('Áp dụng M1',{exact:true}).check();const version=table.getByLabel('Phiên bản M1',{exact:true});await version.selectOption(await version.locator('option').evaluateAll(o=>o.find(x=>x.value)?.value));
  round=await capture(p,'POST','/dot-xet',201,()=>p.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());round=await capture(p,'POST','/'+round.id+'/mo',200,()=>p.getByRole('button',{name:'Công khai đợt',exact:true}).click());report.round={id:round.id,code:round.code,semesterId:round.semesterId};
 });
 await check('SJ04_ROUND_TO_FORM_REFRESH_CANCEL',page,async()=>{
  await findRound(page,round);await expect(page.getByRole('link',{name:'Kê khai và nộp hồ sơ',exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'Minh chứng cần chuẩn bị',exact:true})).toBeVisible();await expect(page.getByText('Đang tải yêu cầu minh chứng…',{exact:true})).toHaveCount(0);
  await page.screenshot({path:path.join(out,'receiving-round-desktop.png'),fullPage:true});
  const response=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/toi/ngu-canh-ke-khai')&&new URL(r.url()).searchParams.get('dot_xet_id')===round.id);await page.getByRole('link',{name:'Kê khai và nộp hồ sơ',exact:true}).click();formContext=await (await response).json();await expect(page.getByRole('heading',{name:'Kê khai hồ sơ',exact:true})).toBeVisible();assert.equal(formContext.round.id,round.id);
  await page.reload();await expect(page.getByRole('heading',{name:'Kê khai hồ sơ',exact:true})).toBeVisible();assert.equal(new URL(page.url()).searchParams.get('dot_xet_id'),round.id);
  await page.getByRole('button',{name:'Hủy kê khai',exact:true}).click();assert.equal(new URL(page.url()).searchParams.has('dot_xet_id'),false);await page.reload();await expect(page.getByRole('heading',{name:'Hồ sơ của tôi',exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'Kê khai hồ sơ',exact:true})).toHaveCount(0);
 });
 await check('SJ05_MOBILE_AND_VISIBLE_VALIDATION',page,async()=>{
  await findRound(page,round);await expect(page.getByText('Đang tải yêu cầu minh chứng…',{exact:true})).toHaveCount(0);await page.setViewportSize({width:360,height:800});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await page.screenshot({path:path.join(out,'receiving-round-mobile.png'),fullPage:true});await page.getByRole('link',{name:'Kê khai và nộp hồ sơ',exact:true}).click();await expect(page.getByRole('heading',{name:'Kê khai hồ sơ',exact:true})).toBeVisible();
  const fullName=page.getByLabel('Họ tên',{exact:true});const name=await fullName.inputValue();await fullName.fill('');await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();await expect(page.getByRole('alert').last()).toBeInViewport();await fullName.fill(name||'Sinh viên DEV giả lập');await expect(page.getByRole('alert')).toHaveCount(0);await page.setViewportSize({width:1440,height:1000});
 });
 let file,ack;
 await check('SJ06_REAL_PDF_UPLOAD_AND_SUBMIT',page,async()=>{
  const claims=formContext.suggestedClaims;
  const values={'CCCD':claims.citizenId||'000000000001','Ngày sinh':claims.birthDate||'2005-01-15','Nơi sinh':claims.birthPlace||'Đà Nẵng (SYNTHETIC)','Giới tính':claims.gender||'Nam','Địa chỉ thường trú':'Địa chỉ thực hành sinh viên — SYNTHETIC','Số điện thoại':claims.phoneNumber||'0900000001'};
  for(const[label,value]of Object.entries(values))await page.getByLabel(label,{exact:true}).fill(value);
  for(const[label,kind]of [['Dân tộc','ethnicities'],['Lớp','classes'],['Khóa học','cohorts'],['Đơn vị quản lý','units']]){const items=await(await api(student,'/api/ctsv/nen-tang/danh-muc?kind='+kind+'&size=100')).json();assert(items.items.length);await page.getByLabel(label,{exact:true}).fill(items.items[0].id);}
  await page.getByLabel('Đang học văn bằng thứ nhất').selectOption('true');await page.getByLabel('Đã hưởng ở cùng trình độ').selectOption('false');await page.getByLabel('Đang hưởng lương khi đi học').selectOption('false');
  await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();await page.getByRole('button',{name:'Tiếp tục',exact:true}).click();
  file=await capture(page,'POST','/tep/minh-chung',201,()=>page.getByLabel('Chọn PDF minh chứng',{exact:true}).setInputFiles(process.env.RF_SAMPLE_PDF));report.storage='Cloudinary REAL';report.fileId=file.id;
  for(const group of formContext.policyVersions[0].documentGroups)for(const req of group.documents)await page.getByLabel('PDF cho '+req.name).selectOption(file.id);
  await page.getByRole('checkbox',{name:'Tôi cam kết lời khai đúng sự thật',exact:true}).check();ack=await capture(page,'POST','/ho-so',201,()=>page.getByRole('button',{name:'Nộp hồ sơ',exact:true}).click());report.applicationId=ack.applicationId;
  await expect(page.getByText('Đã tiếp nhận hồ sơ.',{exact:true})).toBeVisible();await expect(page.getByRole('region',{name:'Chi tiết hồ sơ'}).getByText('Đã nộp',{exact:true})).toBeVisible();
  const actual=await(await api(student,'/api/ctsv/mghp/ho-so/'+ack.applicationId)).json();assert.equal(actual.roundId,round.id);assert.equal(actual.submittedVersions[0].documents[0].fileId,file.id);
 });
 await check('SJ07_DOWNLOAD_HISTORY_HASH_AND_WITHDRAW',page,async()=>{
  await page.getByRole('button',{name:'Lịch sử & minh chứng',exact:true}).click();const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:/^Tải PDF/}).first().click();const download=await downloaded;const bytes=await fs.readFile(await download.path());const hash=x=>createHash('sha256').update(x).digest('hex');assert.equal(hash(bytes),hash(await fs.readFile(process.env.RF_SAMPLE_PDF)));report.sha256=hash(bytes);
  await page.getByRole('button',{name:'Tổng quan',exact:true}).click();await page.locator('summary').filter({hasText:/^Rút hồ sơ$/}).click();await page.getByLabel('Lý do rút hồ sơ',{exact:true}).fill('Kết thúc bài thực hành kiểm giao diện sinh viên — SYNTHETIC');await capture(page,'POST','/'+ack.applicationId+'/rut',200,()=>page.getByRole('button',{name:'Rút hồ sơ',exact:true}).click(),false);await expect(page.getByText('Đã rút',{exact:true})).toBeVisible();assert.equal((await(await api(student,'/api/ctsv/mghp/ho-so/'+ack.applicationId)).json()).status,'withdrawn');
  await page.getByRole('button',{name:'Về danh sách',exact:true}).click();await expect(page.locator('[data-application-id="'+ack.applicationId+'"]').locator('..').getByText('Đã rút',{exact:true})).toBeVisible();
 });
 await check('SJ08_CLOSED_ROUND_INBOX_AND_LOGOUT',page,async()=>{
  const p=staff.page;await p.getByLabel('Lý do thao tác',{exact:true}).fill('Kết thúc bài kiểm giao diện — SYNTHETIC');await capture(p,'POST','/'+round.id+'/dong-nhan-ho-so',200,()=>p.getByRole('button',{name:'Đóng nhận hồ sơ',exact:true}).click(),false);assert.equal((await(await api(staff,'/api/ctsv/mghp/dot-xet/'+round.id)).json()).status,'closed');await findRound(page,round);await expect(page.getByText('Đợt đã đóng nhận hồ sơ.',{exact:true})).toBeVisible();await expect(page.getByRole('link',{name:'Kê khai và nộp hồ sơ',exact:true})).toHaveCount(0);
  await page.goto(origin+'/thong-bao');await expect(page.getByRole('heading',{name:'Thông báo của tôi',exact:true})).toBeVisible();await page.getByRole('button',{name:'Đăng xuất',exact:true}).click();await expect(page.getByRole('heading',{name:'Đăng nhập CTSV',exact:true})).toBeVisible();
 });assert.deepEqual(report.pageErrors,[]);
 }
}catch(e){report.error=e.message;report.cases.push({id:'EXECUTION_FAILURE',status:'FAIL',reason:e.message});for(let i=0;i<contexts.length;i++)for(const p of contexts[i].pages())await p.screenshot({path:path.join(out,mode+'-failure-'+i+'.png'),fullPage:true}).catch(()=>{});process.exitCode=1;}
finally{await fs.writeFile(path.join(out,mode+'-report.json'),JSON.stringify(report,null,2));await Promise.all(contexts.map(c=>c.close()));await browser.close();}
