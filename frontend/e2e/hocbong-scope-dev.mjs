// Launcher-owned DEV smoke. Credentials only in process memory, no files or screenshots.
import {chromium,expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const config=JSON.parse((await readFile(path.join(process.env.CTSV_DEV_HOME,'config.json'),'utf8')).replace(/^\uFEFF/,''));
const passwords=JSON.parse(process.env.CTSV_DEV_SMOKE_PASSWORDS);delete process.env.CTSV_DEV_SMOKE_PASSWORDS;
const origin='https://127.0.0.1:'+config.FrontendPort,api='/api/ctsv/hoc-bong',route='/hoc-bong/kkht/dot-xet';
const browser=await chromium.launch({channel:'chrome',headless:true});const contexts=[];let passed=0;
async function login(role){const context=await browser.newContext();contexts.push(context);const page=await context.newPage();page.setDefaultTimeout(12000);await page.goto(origin);await page.getByLabel('Tên đăng nhập',{exact:true}).fill(config.Accounts[role].Username);await page.getByLabel('Mật khẩu',{exact:true}).fill(passwords[role]);const issued=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/xac-thuc/dang-nhap');await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();assert.equal((await issued).status(),200);const token=(await (await issued).json()).accessToken;await expect(page.getByRole('heading',{name:'Trang chủ CTSV',exact:true})).toBeVisible();return{page,context,token};}
async function get(actor,p){return actor.context.request.get(origin+p,{headers:{Authorization:'Bearer '+actor.token}});}
try{
 const cv=await login('specialist');let result=await get(cv,api+'/toi/nhom-chuc-nang');assert.equal(result.status(),200);assert.equal((await result.json()).specialist,true);console.log('T028-DEV-role PASS (REAL)');passed++;
 result=await get(cv,api+'/hoc-ky-nguon?size=100');assert.equal(result.status(),200);const terms=(await result.json()).items.filter(t=>t.selectable);assert(terms.length,'No selectable DEV source semester');
 const term=terms.find(t=>t.code.endsWith('10'))??terms[0];await cv.page.goto(origin+route+'?hocKyNguonId='+term.sourceSemesterId);await expect(cv.page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeEnabled();
 const loaded=cv.page.waitForResponse(r=>new URL(r.url()).pathname===api+'/kkht/pham-vi-nguon');await cv.page.getByRole('button',{name:'Tạo đợt xét',exact:true}).click();result=await loaded;
 console.log('DEV catalog HTTP '+result.status()+'; source semester '+term.code);
 if(result.status()===503){assert.equal((await result.json()).code,'SCOPE_SOURCE_UNAVAILABLE');await expect(cv.page.getByText(/Nguồn chưa đủ thông tin Khóa, Ngành hoặc kỳ bắt đầu học/)).toBeVisible();await expect(cv.page.getByRole('button',{name:'Tạo đợt xét',exact:true})).toBeDisabled();console.log('T028-DEV-source-missing-guard PASS (REAL); CREATE/EDIT NOT VERIFIED: source mapping unavailable');passed++;}
 else{assert.equal(result.status(),200);const body=await result.json();console.log('DEV catalog groups '+body.totalElements);await expect(cv.page.getByRole('group',{name:'Phạm vi sinh viên'})).toBeVisible();console.log('T028-DEV-catalog PASS (REAL); CREATE/EDIT NOT RUN: no verified writable synthetic fixture selected');passed++;}
 await cv.page.setViewportSize({width:360,height:800});assert(await cv.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));console.log('T028-DEV-mobile PASS (REAL)');passed++;
 const sv=await login('student');await sv.page.goto(origin+route+'?hocKyNguonId='+term.sourceSemesterId);await expect(sv.page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();result=await get(sv,api+'/kkht/pham-vi-nguon?sourceSemesterId='+term.sourceSemesterId);assert.equal(result.status(),403);console.log('T028-DEV-student-direct-api-denied PASS (REAL)');passed++;
 for(const actor of [cv,sv]){await actor.page.getByRole('button',{name:'Đăng xuất',exact:true}).click();await expect(actor.page.getByRole('heading',{name:'Đăng nhập CTSV'})).toBeVisible();}
 console.log('T02.8 REAL DEV '+passed+' checks passed; no data writes/seed');
}catch(e){let message=String(e.message).replace(/eyJ[A-Za-z0-9_.-]+/g,'[TOKEN]');for(const secret of Object.values(passwords))message=message.replaceAll(secret,'[SECRET]');console.error(message);process.exitCode=1;}
finally{for(const context of contexts)await context.close();await browser.close();}