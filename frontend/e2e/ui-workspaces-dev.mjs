import {chromium} from '@playwright/test';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import {openNav} from './navigation.mjs';

const home=process.env.CTSV_DEV_HOME,evidence=process.env.CTSV_UI_EVIDENCE;
if(!home||!evidence)throw new Error('Private DEV config and evidence directory required.');
const cfg=JSON.parse((await readFile(path.join(home,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const ids=JSON.parse(await readFile(path.join(home,'identities.json'),'utf8'));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);
delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
await mkdir(evidence,{recursive:true});
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[];
const report={date:'2026-11-18',origin:'https://127.0.0.1:'+cfg.FrontendPort,database:'ctsv_dev',
 browser:browser.version(),cases:[],fixtures:{},pageErrors:[]};
const runId='ui.'+Date.now();
async function login(role){
 const context=await browser.newContext({ignoreHTTPSErrors:false,viewport:{width:1440,height:1000}});
 contexts.push(context);
 const page=await context.newPage();page.setDefaultTimeout(30000);
 page.on('pageerror',e=>report.pageErrors.push(e.name));
 await page.goto(report.origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(cfg.Accounts[role].Username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
 let pending=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 let signedIn=await pending;
 if(signedIn.status()===429){
  const wait=Number(signedIn.headers()['retry-after']);
  assert(Number.isFinite(wait)&&wait>0&&wait<=900,'Valid login Retry-After required.');
  console.log('DEV '+role+' respects login Retry-After '+wait+'s');
  for(let remaining=wait+1;remaining>0;remaining-=Math.min(remaining,30)){
   await new Promise(resolve=>setTimeout(resolve,Math.min(remaining,30)*1000));
  }
  await page.getByLabel('Tên đăng nhập',{exact:true}).fill(cfg.Accounts[role].Username);
  await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);
  pending=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  signedIn=await pending;
 }
 assert.equal(signedIn.status(),200);
 await page.getByRole('heading',{name:'Trang chủ CTSV',exact:true}).waitFor();
 return page;
}
async function capture(page,method,suffix,status,click){
 report.lastAction={method,suffix,expected:status};
 const pending=page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname.endsWith(suffix));
 await click();const response=await pending;
 assert.equal(response.status(),status,method+' '+suffix);
 return response.status()===204?null:response.json();
}
async function check(id,task){
 try{await task();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}
 catch(error){report.cases.push({id,status:'FAIL'});throw error;}
}
async function screenshot(page,name){
 await page.screenshot({path:path.join(evidence,name+'.png'),fullPage:true});
}
let admin,staff,student;
try{
 await check('UI01_ADMIN_CREATE_READ_UPDATE_STATUS',async()=>{
  admin=await login('admin');
  await openNav(admin,'Quản trị tài khoản');
  await admin.getByRole('button',{name:'Nhập tài khoản mới',exact:true}).click();
  await admin.getByLabel('Tên đăng nhập',{exact:true}).fill('dev.'+runId);
  await admin.getByLabel('Mật khẩu',{exact:true}).fill(passwords.student);
  assert.equal(await admin.getByRole('button',{name:'Thêm liên kết đã xác minh',exact:true}).getAttribute('type'),'button',
   'Adding a profile row must not implicitly submit the account form.');
  await admin.getByRole('button',{name:'Thêm liên kết đã xác minh',exact:true}).click();
  await admin.getByLabel('Mã nguồn đã đăng ký',{exact:true}).fill('SIS_MOCK');
  await admin.getByLabel('Định danh hồ sơ nguồn',{exact:true}).fill('UI-STUDENT-'+runId);
  const created=await capture(admin,'POST','/api/xac-thuc/tai-khoan',201,()=>admin.getByRole('button',{name:'Tạo tài khoản',exact:true}).click());
  report.fixtures.accountId=created.accountId;
  await admin.getByRole('button',{name:'Đọc dữ liệu',exact:true}).click();
  await admin.getByRole('heading',{name:'dev.'+runId,exact:true}).waitFor();
  await admin.getByRole('button',{name:'Liên kết hồ sơ',exact:true}).click();
  await capture(admin,'PATCH','/'+created.accountId,200,()=>admin.getByRole('button',{name:'Lưu thay đổi đã xác minh',exact:true}).click());
  await admin.getByRole('status').getByText('Đã lưu thay đổi tài khoản.',{exact:true}).waitFor();
  await admin.getByRole('button',{name:'Khóa / mở khóa',exact:true}).click();
  await admin.getByLabel('Lý do thay đổi trạng thái',{exact:true}).fill('Tài khoản thực hành giao diện quản trị');
  await capture(admin,'PUT','/trang-thai',200,()=>admin.getByRole('button',{name:'Khóa',exact:true}).click());
  await admin.getByRole('status').getByText('Đã khóa tài khoản.',{exact:true}).waitFor();
  await admin.getByRole('button',{name:'Khóa / mở khóa',exact:true}).click();
  await admin.getByLabel('Lý do thay đổi trạng thái',{exact:true}).fill('Hoàn tất kiểm tra, mở lại tài khoản thực hành');
  await capture(admin,'PUT','/trang-thai',200,()=>admin.getByRole('button',{name:'Mở khóa',exact:true}).click());
  await admin.getByRole('status').getByText('Đã mở khóa tài khoản.',{exact:true}).waitFor();
  await screenshot(admin,'admin-desktop');
 });
 await check('UI02_POLICY_CREATE_EDIT_CONFIRM_AND_LIST_REFRESH',async()=>{
  staff=await login('specialist');
  await openNav(staff,'Chính sách MGHP');
  const row=staff.getByRole('row').filter({has:staff.getByRole('cell',{name:'M9',exact:true})});
  await row.getByRole('button',{name:'Xem phiên bản',exact:true}).click();
  await staff.getByRole('button',{name:'Xem bản 1',exact:true}).click();
  await staff.getByRole('button',{name:'Tạo phiên bản chính sách',exact:true}).click();
  await staff.getByLabel('Tên',{exact:true}).fill('Hỗ trợ học phí · Bản thực hành giao diện');
  await staff.getByText('Căn cứ và quy tắc',{exact:true}).click();
  await staff.getByLabel('Mã phiên bản quy tắc',{exact:true}).fill('UI-'+runId);
  await staff.getByLabel('Căn cứ pháp lý',{exact:true}).fill('Dữ liệu mẫu thực hành giao diện trên DEV.');
  const created=await capture(staff,'POST','/phien-ban',201,()=>staff.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());
  report.fixtures.policyVersionId=created.id;
  await staff.getByRole('status').getByText('Đã lưu. Dữ liệu được đọc lại từ hệ thống.',{exact:true}).waitFor();
  await staff.getByLabel('Tên',{exact:true}).fill('Hỗ trợ học phí · Bản điều chỉnh thực hành');
  await capture(staff,'PUT','/'+created.id,200,()=>staff.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());
  await staff.getByRole('status').getByText('Đã lưu. Dữ liệu được đọc lại từ hệ thống.',{exact:true}).waitFor();
  await staff.getByLabel('Căn cứ xác nhận',{exact:true}).fill('Xác nhận cấu hình mẫu cho bài thực hành giao diện DEV.');
  await capture(staff,'POST','/xac-nhan',200,()=>staff.getByRole('button',{name:'Xác nhận chính sách',exact:true}).click());
  await staff.getByRole('status').getByText('Đã cập nhật trạng thái.',{exact:true}).waitFor();
  await staff.getByRole('button',{name:'Danh sách phiên bản',exact:true}).click();
  const versionButton=staff.getByRole('button',{name:'Xem bản '+created.version,exact:true});
  await versionButton.locator('..').getByText(/Đã xác nhận/).waitFor();
  await versionButton.click();
  await screenshot(staff,'policy-desktop');
 });
 await check('UI03_ERROR_VISIBLE_AFTER_SCROLL_AND_MOBILE',async()=>{
  student=await login('student');
  await openNav(student,'Hồ sơ MGHP');
  await student.locator('[data-application-id]').first().click();
  await student.getByRole('button',{name:'Yêu cầu bổ sung',exact:true}).click();
  await student.getByLabel('Mã yêu cầu bổ sung',{exact:true}).fill('9223372036854775806');
  await student.evaluate(()=>window.scrollTo(0,document.body.scrollHeight));
  await capture(student,'GET','/9223372036854775806/phan-hoi',404,()=>student.getByRole('button',{name:'Đọc yêu cầu bổ sung',exact:true}).click());
  const error=student.getByRole('alert');
  await error.waitFor();
  const bounds=await error.boundingBox();
  const size=student.viewportSize();
  assert(bounds&&bounds.y>=0&&bounds.y+bounds.height<=size.height,'Error stays inside viewport after scrolling.');
  await screenshot(student,'error-visible-desktop');
  await student.getByRole('button',{name:'Đóng thông báo lỗi',exact:true}).click();
  await error.waitFor({state:'detached'});
  await student.setViewportSize({width:360,height:800});
  assert(await student.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Mobile page has no horizontal overflow.');
  await screenshot(student,'student-mobile');
  await staff.setViewportSize({width:360,height:800});
  assert(await staff.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Policy mobile has no page overflow.');
  await screenshot(staff,'policy-mobile');
  await admin.setViewportSize({width:360,height:800});
  await screenshot(admin,'admin-mobile');
  assert(await admin.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Admin mobile has no page overflow.');
 });
 await check('UI04_FOUNDATION_AND_ACCESS_GUARD',async()=>{
  await openNav(staff,'Sinh viên');
  await staff.getByLabel('ID sinh viên',{exact:true}).fill(ids.studentId);
  await capture(staff,'GET','/sinh-vien/'+ids.studentId,200,()=>staff.getByRole('button',{name:'Xem chi tiết',exact:true}).click());
  await staff.getByText('DEV-SV-001',{exact:true}).waitFor();
  await student.goto(report.origin+'/quan-tri/tai-khoan');
  await student.getByText('Bạn không có quyền truy cập trang này.',{exact:true}).waitFor();
  assert.equal(await student.getByRole('button',{name:'Nhập tài khoản mới',exact:true}).count(),0);
  assert.equal(report.pageErrors.length,0);
 });
 await check('UI05_ROUND_CREATE_EDIT_OPEN_CLOSE_COMPLETE',async()=>{
  await staff.setViewportSize({width:1440,height:1000});
  await openNav(staff,'Đợt xét MGHP');
  await staff.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
  await staff.getByLabel('Mã',{exact:true}).fill('UI-ROUND-'+runId);
  await staff.getByLabel('Tên',{exact:true}).fill('Đợt thực hành quản lý miễn giảm học phí');
  const semester=staff.getByLabel('Học kỳ',{exact:true});
  await semester.locator('option[value="1"]').waitFor({state:'attached'});
  await semester.selectOption('1');
  await staff.getByText('Lịch tiếp nhận',{exact:true}).click();
  const localDate=days=>{
   const date=new Date(Date.now()+days*86400000);
   return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);
  };
  await staff.getByLabel('Mở nhận lúc',{exact:true}).fill(localDate(-1));
  await staff.getByLabel('Hạn nộp',{exact:true}).fill(localDate(7));
  await staff.getByLabel('Hạn rà soát',{exact:true}).fill(localDate(14));
  await staff.getByText('Hướng dẫn và chính sách tiếp nhận',{exact:true}).click();
  await staff.getByLabel('Hướng dẫn kê khai',{exact:true}).fill('Dữ liệu mẫu: chuẩn bị giấy tờ và kiểm tra thông tin trước khi nộp.');
  const policy=staff.getByLabel('Chính sách thêm vào đợt',{exact:true});
  const option=policy.locator('option').filter({hasText:/^M9 ·/});
  await policy.selectOption(await option.getAttribute('value'));
  const version=staff.getByLabel('Phiên bản thêm vào đợt',{exact:true});
  await version.locator('option[value="'+report.fixtures.policyVersionId+'"]').waitFor({state:'attached'});
  await version.selectOption(report.fixtures.policyVersionId);
  await staff.getByRole('button',{name:'Thêm chính sách vào đợt',exact:true}).click();
  const created=await capture(staff,'POST','/dot-xet',201,()=>staff.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());
  report.fixtures.roundId=created.id;
  await staff.getByRole('status').getByText('Đã lưu. Dữ liệu được đọc lại từ hệ thống.',{exact:true}).waitFor();
  await staff.getByLabel('Tên',{exact:true}).fill('Đợt thực hành quản lý MGHP · Đã cập nhật');
  await capture(staff,'PUT','/'+created.id,200,()=>staff.getByRole('button',{name:'Lưu cấu hình',exact:true}).click());
  await staff.getByRole('status').getByText('Đã lưu. Dữ liệu được đọc lại từ hệ thống.',{exact:true}).waitFor();
  await capture(staff,'POST','/mo',200,()=>staff.getByRole('button',{name:'Công khai đợt',exact:true}).click());
  await staff.getByLabel('Lý do thao tác',{exact:true}).fill('Kết thúc phần thực hành tiếp nhận hồ sơ.');
  await capture(staff,'POST','/dong-nhan-ho-so',200,()=>staff.getByRole('button',{name:'Đóng nhận hồ sơ',exact:true}).click());
  await staff.getByLabel('Lý do thao tác',{exact:true}).fill('Hoàn tất đợt mẫu, không có hồ sơ cần xử lý.');
  await capture(staff,'POST','/hoan-tat',200,()=>staff.getByRole('button',{name:'Hoàn tất đợt',exact:true}).click());
  await staff.getByRole('heading',{name:/Đợt thực hành quản lý MGHP · Đã cập nhật · Đã hoàn tất/}).waitFor();
  assert.equal(await staff.getByRole('combobox',{name:'Chính sách thêm vào đợt',exact:true}).count(),0,'Locked round is a read-only view.');
  const schedule=staff.locator('details').filter({has:staff.locator('summary').filter({hasText:/^Lịch tiếp nhận$/})});
  assert(!/T\d{2}:\d{2}:/.test(await schedule.innerText()),'Read-only dates use local presentation.');
  await screenshot(staff,'round-desktop');
  assert.equal(report.pageErrors.length,0);
 });
}catch(error){
 report.error={name:error.name,message:String(error.message).replace(/Bearer\s+\S+/g,'Bearer [REDACTED]')};
 process.exitCode=1;console.error('UI acceptance failed; see sanitized report.');
}finally{
 for(const context of contexts)await context.close();
 await browser.close();
 report.status=report.cases.length===5&&report.cases.every(c=>c.status==='PASS')?'PASS':'FAIL';
 await writeFile(path.join(evidence,'ui-report.json'),JSON.stringify(report,null,2));
}
