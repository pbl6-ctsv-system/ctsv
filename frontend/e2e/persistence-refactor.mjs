import {openNav,revealNav} from './navigation.mjs';
// REAL browser + HTTPS + the owner-authorized refactor DB. No DEV/evidence DB writes and no TLS bypass.
import {chromium} from '@playwright/test';
import {readFile, writeFile} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const home=process.env.P05_BROWSER_HOME;
if(!home) throw new Error('Private P05_BROWSER_HOME required');
const cfg=JSON.parse(await readFile(path.join(home,'credentials.json'),'utf8'));
assert.equal(cfg.database,'ctsv_jpa_refactor_test_20261113');
const report={database:cfg.database,transport:'REAL browser + HTTPS + SQL Server, no stub',cases:[]};
const browser=await chromium.launch({channel:'chrome',headless:true});
const contexts=[];
async function check(name,action) { await action();report.cases.push({name,status:'PASS'});console.log(name+' PASS'); }
async function login(role) {
  const context=await browser.newContext({ignoreHTTPSErrors:false});contexts.push(context);
  const page=await context.newPage();page.setDefaultTimeout(15000);await page.goto(cfg.origin);
  await page.getByLabel('Tên đăng nhập',{exact:true}).fill(cfg[role]);await page.getByLabel('Mật khẩu',{exact:true}).fill(cfg.password);
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();return page;
}
try {
  const p=await login('cv');let created;
  await check('JPA-B01 policy create/edit/confirm and list-detail consistency',async()=>{
    await openNav(p,'Chính sách MGHP');
    const row=p.getByRole('row').filter({has:p.getByRole('cell',{name:'M2',exact:true})});
    await row.getByRole('button',{name:'Xem phiên bản',exact:true}).click();
    await p.getByRole('button',{name:'Xem bản '+cfg.lockedVersion,exact:true}).click();
    await p.getByRole('button',{name:'Tạo phiên bản chính sách',exact:true}).click();
    const name=p.getByRole('textbox',{name:'Tên',exact:true});const run='SYNTHETIC-JPA-'+Date.now();await name.fill(run);
    let result=p.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/phien-ban'));
    await p.getByRole('button',{name:'Lưu cấu hình',exact:true}).click();let response=await result;assert.equal(response.status(),201);created=await response.json();
    await p.getByText('Đã lưu. Dữ liệu được đọc lại từ hệ thống.',{exact:true}).waitFor();await name.fill(run+' updated');
    result=p.waitForResponse(r=>r.request().method()==='PUT'&&r.url().endsWith('/'+created.id));
    await p.getByRole('button',{name:'Lưu cấu hình',exact:true}).click();response=await result;assert.equal(response.status(),200);assert.equal(await response.headerValue('etag'),null);
    await p.getByText('Đã lưu. Dữ liệu được đọc lại từ hệ thống.',{exact:true}).waitFor();assert.equal(await name.inputValue(),run+' updated');
    await p.getByLabel('Căn cứ xác nhận',{exact:true}).fill('SYNTHETIC JPA refactor smoke; no real legal confirmation');
    result=p.waitForResponse(r=>r.url().endsWith('/'+created.id+'/xac-nhan'));
    await p.getByRole('button',{name:'Xác nhận chính sách',exact:true}).click();response=await result;assert.equal(response.status(),200);const confirmed=await response.json();assert.equal(confirmed.status,'locked');
    await p.getByText('Đã cập nhật trạng thái.',{exact:true}).waitFor();assert(await name.isDisabled());
    const list=p.getByRole('heading',{name:'Các phiên bản',exact:true}).locator('..');assert((await list.innerText()).includes(`Phiên bản ${confirmed.version} · Đã xác nhận · ${confirmed.rate}`));
  });
  const code='SYNTHETIC-JPA-ROUND-'+Date.now();
  await check('JPA-B02 round create/open/close',async()=>{
    await openNav(p,'Đợt xét MGHP');await p.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();
    await p.getByRole('textbox',{name:'Mã',exact:true}).fill(code);await p.getByLabel('Học kỳ',{exact:true}).selectOption(cfg.semesterId);await p.getByRole('textbox',{name:'Tên',exact:true}).fill('SYNTHETIC JPA smoke');
    await p.getByLabel('Mở nhận lúc',{exact:true}).fill('2026-11-03T08:00');await p.getByLabel('Hạn nộp',{exact:true}).fill('2026-11-06T08:00');await p.getByLabel('Hạn rà soát',{exact:true}).fill('2026-11-15T08:00');
    await p.getByLabel('Chính sách thêm vào đợt',{exact:true}).selectOption(cfg.policyId);await p.getByLabel('Phiên bản thêm vào đợt',{exact:true}).selectOption(cfg.lockedVersionId);await p.getByRole('button',{name:'Thêm chính sách vào đợt',exact:true}).click();
    let result=p.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/dot-xet'));await p.getByRole('button',{name:'Lưu cấu hình',exact:true}).click();assert.equal((await result).status(),201);
    await p.getByRole('button',{name:'Công khai đợt',exact:true}).waitFor();result=p.waitForResponse(r=>r.url().endsWith('/mo'));await p.getByRole('button',{name:'Công khai đợt',exact:true}).click();assert.equal((await result).status(),200);
    await p.getByRole('button',{name:'Đóng nhận hồ sơ',exact:true}).waitFor();await p.getByLabel('Lý do thao tác',{exact:true}).fill('SYNTHETIC JPA smoke close');
    result=p.waitForResponse(r=>r.url().endsWith('/dong-nhan-ho-so'));await p.getByRole('button',{name:'Đóng nhận hồ sơ',exact:true}).click();assert.equal((await result).status(),200);
  });
  await check('JPA-B03 student public round and denied management',async()=>{
    const s=await login('student');
    await (await revealNav(s,'Đợt xét MGHP')).waitFor();
    assert.equal(await s.getByRole('link',{name:'Chính sách MGHP',exact:true,includeHidden:true}).count(),0);
    await openNav(s,'Đợt xét MGHP');const row=s.getByRole('row').filter({has:s.getByRole('cell',{name:code,exact:true})});await row.getByRole('button',{name:'Xem đợt',exact:true}).click();
    assert.equal(await s.getByRole('button',{name:'Lưu cấu hình',exact:true}).count(),0);
    const logout=s.waitForResponse(r=>r.request().method()==='POST'&&r.url().endsWith('/dang-xuat'));
    await s.getByRole('button',{name:'Đăng xuất',exact:true}).click();const response=await logout;
    assert.equal(response.status(),204,'Cookie-only logout HTTP status');
    await s.getByRole('button',{name:'Đăng nhập',exact:true}).waitFor();
  });
  report.status='PASS';
} catch(e) { report.status='FAIL';report.error=String(e.message).replaceAll(cfg.password,'[REDACTED]').replace(/eyJ[A-Za-z0-9_.-]+/g,'[REDACTED_JWT]');process.exitCode=1;console.error('Browser smoke failed; inspect sanitized browser-report.json'); }
finally { for(const c of contexts)await c.close();await browser.close();await writeFile(path.join(home,'browser-report.json'),JSON.stringify(report,null,2));await writeFile(path.join(home,'browser.done'),report.status); }
