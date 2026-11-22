import {openNav} from './navigation.mjs';
import {chromium} from '@playwright/test';
import {readFile,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import path from 'node:path';
import assert from 'node:assert/strict';

const home=process.env.P07_BROWSER_HOME;
const pdf=process.env.P07_SAMPLE_PDF;
if(!home||!pdf)throw new Error('P07 browser home and synthetic PDF are required.');
const cfg=JSON.parse(await readFile(path.join(home,'credentials.json'),'utf8'));
const browser=await chromium.launch({channel:'chrome',headless:true});
const report={browser:browser.version(),origin:cfg.origin,database:'ctsv_p07_test_20261117',storage:cfg.storage,cases:[]};

async function login(username,menu='Hồ sơ của tôi') {
 const session=await browser.newContext({ignoreHTTPSErrors:false,acceptDownloads:true});
 const page=await session.newPage();
 page.setDefaultTimeout(15000);
 await page.goto(cfg.origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(cfg.password);
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 await openNav(page,menu);
 return page;
}

async function capture(page,method,suffix,status,click) {
 const replied=page.waitForResponse(r=>r.request().method()===method&&new URL(r.url()).pathname.endsWith(suffix));
 await click();
 const response=await replied;
 assert.equal(response.status(),status,method+' '+suffix);
 return response.json();
}

async function check(id,verify) {
 try {await verify();report.cases.push({id,status:'PASS'});console.log(id+' PASS');}
 catch(error){report.cases.push({id,status:'FAIL'});throw error;}
}

let owner,staff,rootCode,supplement;
try {
 await check('P07-B01_STAFF_OPENS_ROUND',async()=>{
  owner=await login(cfg.username);
  const round=owner.getByLabel('Đợt kê khai');
  await round.locator('option[value="'+cfg.round+'"]').waitFor({state:'attached'});
  await round.selectOption(cfg.round);
  await owner.getByLabel('Địa chỉ thường trú',{exact:true}).fill('SYNTHETIC P07 địa chỉ');
  const file=await capture(owner,'POST','/tep/minh-chung',201,()=>owner.getByLabel('Chọn PDF minh chứng').setInputFiles(pdf));
  await owner.getByLabel('PDF cho SYNTHETIC P06 PDF').selectOption(file.id);
  await owner.getByLabel('Tôi cam kết lời khai đúng sự thật').check();
  await capture(owner,'POST','/mghp/ho-so',201,()=>owner.getByRole('button',{name:'Nộp hồ sơ',exact:true}).click());
  rootCode=await owner.getByRole('region',{name:'Chi tiết hồ sơ'}).getByRole('heading',{level:2}).innerText();
  staff=await login(cfg.staff,'Tiếp nhận & bổ sung hồ sơ');
  await staff.getByRole('button',{name:rootCode,exact:true}).click();
  await staff.getByText('Mở vòng bổ sung',{exact:true}).click();
  await staff.getByLabel('Lý do mở vòng').fill('SYNTHETIC P07 kiểm tra điện thoại');
  const date=new Date(Date.now()+3600000);
  const local=new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);
  await staff.getByLabel('Hạn bổ sung').fill(local);
  await staff.getByLabel('Nội dung cần bổ sung').fill('Xác nhận số liên lạc');
  supplement=await capture(staff,'POST','/yeu-cau-bo-sung',201,()=>staff.getByRole('button',{name:'Gửi yêu cầu bổ sung'}).click());
  await staff.getByRole('status').getByText('Đã gửi yêu cầu bổ sung.').waitFor();
  await staff.getByText(/Vòng bổ sung 1 · Mã/).waitFor();
 });
 await check('P07-B02_OWNER_SENDS_ONCE_AND_NON_OWNER_DENIED',async()=>{
  const other=await login(cfg.other);
  await other.getByLabel('Mã yêu cầu bổ sung').fill(supplement.id);
  await capture(other,'GET','/'+supplement.id+'/phan-hoi',404,()=>other.getByRole('button',{name:'Đọc yêu cầu bổ sung'}).click());
  assert.equal(await other.getByRole('button',{name:'Kê khai bổ sung'}).count(),0);
  await openNav(owner,'Thông báo');
  await owner.getByRole('link',{name:'Xem yêu cầu bổ sung'}).click();
  await owner.getByRole('button',{name:'Kê khai bổ sung'}).click();
  await owner.getByLabel('Số điện thoại',{exact:true}).fill('0900000099');
  await owner.getByLabel('Tôi cam kết lời khai đúng sự thật').check();
  await capture(owner,'POST','/'+supplement.id+'/phan-hoi',201,()=>owner.getByRole('button',{name:'Gửi phản hồi bổ sung'}).click());
  await owner.getByText('Phản hồi đã được ghi nhận.',{exact:true}).waitFor();
  assert.equal(await owner.getByRole('button',{name:'Kê khai bổ sung'}).count(),0);
  assert.equal(await owner.getByRole('button',{name:'Gửi yêu cầu bổ sung'}).count(),0);
  const download=owner.waitForEvent('download');
  await owner.getByRole('region',{name:'Chi tiết hồ sơ'}).getByRole('button',{name:/Tải PDF/}).first().click();
  const actual=await readFile(await (await download).path());
  const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
  assert.equal(hash(actual),hash(await readFile(pdf)));
 });
 await check('P07-B03_STAFF_FINAL_ITEM_AND_APPEND_CHECK',async()=>{
  await staff.getByLabel('Lý do đóng vòng').fill('SYNTHETIC thử thao tác với phiên bản cũ');
  const stale=await capture(staff,'POST','/huy',412,()=>staff.getByRole('button',{name:'Hủy vòng'}).click());
  assert.equal(stale.code,'VERSION_CONFLICT');
  await staff.getByText('Dữ liệu đã thay đổi. Hãy đọc lại trước khi thao tác.',{exact:true}).waitFor();
  await staff.getByRole('button',{name:'Đọc vòng bổ sung'}).click();
  await staff.getByLabel('Kết quả mục bổ sung').selectOption('satisfied');
  await staff.getByLabel('Lý do đánh giá').fill('Đã đối chiếu số liên lạc synthetic');
  await capture(staff,'PUT','/danh-gia',200,()=>staff.getByRole('button',{name:'Lưu đánh giá mục'}).click());
  await staff.getByText(/Xác nhận số liên lạc · Đáp ứng/).waitFor();
  await staff.getByLabel('Lý do đóng vòng').fill('Đã xử lý toàn bộ mục');
  await capture(staff,'POST','/hoan-tat',200,()=>staff.getByRole('button',{name:'Hoàn tất vòng'}).click());
  await staff.getByRole('region',{name:'Chi tiết vòng bổ sung'}).getByText(/Đã hoàn tất/).waitFor();
  await staff.getByRole('button',{name:'Đọc đối chiếu và giấy đã nộp'}).click();
  const checks=staff.getByRole('region',{name:'Đối chiếu lời khai và giấy'});
  await capture(staff,'POST','/xac-nhan-ban-cung',201,()=>checks.getByRole('button',{name:'Lưu kiểm giấy'}).click());
  await checks.getByText(/hard_copy · received/).waitFor();
 });
} finally {
 await browser.close();
 const passed=report.cases.length===3&&report.cases.every(item=>item.status==='PASS');
 await writeFile(path.join(home,'browser-report.json'),JSON.stringify(report,null,2));
 await writeFile(path.join(home,'browser.done'),passed?'PASS':'FAIL');
}
