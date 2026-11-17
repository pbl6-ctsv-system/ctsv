// UI-only regressions with controlled AUTH04 and Foundation responses. No DB writes.
import {chromium,expect} from '@playwright/test';
import assert from 'node:assert/strict';
import {openNav,revealNav} from './navigation.mjs';

const origin=process.env.CTSV_FRONTEND_ORIGIN??'https://127.0.0.1:5173';
const browser=await chromium.launch({channel:'chrome',headless:true});
const emptyAccess={student:false,mghpSpecialist:false,ctsvRoleAdmin:false};
const cases=[];

async function check(name,run){
 const context=await browser.newContext({ignoreHTTPSErrors:false});
 const page=await context.newPage();
 const state={functions:[],access:{...emptyAccess},authStatus:200,menuStatus:200,accountId:'fixture-account'};
 try{
  await page.route(url=>url.pathname.startsWith('/api/'),async route=>{
   const {pathname}=new URL(route.request().url());
   if(pathname==='/api/xac-thuc/dang-nhap'||pathname==='/api/xac-thuc/lam-moi-token'){
    return route.fulfill({json:{accessToken:'fixture-token',expiresIn:900}});
   }
   if(pathname==='/api/xac-thuc/dang-xuat')return route.fulfill({status:204});
   if(pathname==='/api/xac-thuc/toi')return route.fulfill({status:state.authStatus,json:state.authStatus===200
    ?{accountId:state.accountId,username:'fixture',functions:state.functions}:{code:'TOKEN_REVOKED'}});
   if(pathname==='/api/ctsv/nen-tang/toi/quyen-giao-dien')return route.fulfill({status:state.menuStatus,json:state.menuStatus===200
    ?state.access:{code:state.menuStatus===403?'FORBIDDEN':'DEPENDENCY_UNAVAILABLE'}});
   if(pathname==='/api/ctsv/mghp/chinh-sach')return route.fulfill({json:[]});
   throw new Error('Unexpected fixture request '+pathname);
  });
  const login=async()=>{
   await context.clearCookies();
   await page.goto(origin);
   await page.getByLabel('Tên đăng nhập',{exact:true}).fill('fixture');
   await page.getByLabel('Mật khẩu',{exact:true}).fill('synthetic');
   await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
   await page.getByRole('heading',{name:'Trang chủ CTSV'}).waitFor({timeout:5000});
  };
  await run({page,state,login});
  cases.push(name);
  console.log(name+' PASS (STUB)');
 }finally{await context.close();}
}

try{
 await check('Withdrawn CTSV capability blocks navigation in the same session',async({page,state,login})=>{
  state.access.mghpSpecialist=true;
  await login();
  const link=await revealNav(page,'Chính sách MGHP');
  await expect(link).toBeVisible();
  state.access.mghpSpecialist=false;
  await link.click();
  await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Miễn giảm học phí'})).toHaveCount(0);
 });
 await check('Focus recheck clears withdrawn Auth rights and the open page',async({page,state,login})=>{
  state.functions=[{code:'auth.accounts.manage',active:true}];
  await login();
  await openNav(page,'Quản trị tài khoản');
  await expect(page.getByLabel('ID đối tượng',{exact:true})).toBeVisible();
  state.functions=[];
  await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));
  await expect(page.getByText('Bạn không có quyền truy cập trang này.',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Quản trị',exact:true})).toHaveCount(0);
 });
 await check('401 clears retained menus without attempting another write',async({page,state,login})=>{
  state.access.mghpSpecialist=true;
  await login();
  await openNav(page,'Chính sách MGHP');
  await expect(page.getByRole('heading',{name:'Chính sách miễn giảm học phí'})).toBeVisible();
  state.authStatus=401;
  await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));
  await expect(page.getByText('Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.',{exact:true})).toBeVisible();
  await expect(page.getByRole('navigation',{name:'Chức năng CTSV'})).toHaveCount(0);
  await expect(page.getByRole('heading',{name:'Chính sách miễn giảm học phí'})).toHaveCount(0);
  let newAuthRequests=0;
  page.on('request',request=>{if(new URL(request.url()).pathname.startsWith('/api/xac-thuc/'))newAuthRequests++;});
  await page.evaluate(()=>document.dispatchEvent(new Event('visibilitychange')));
  assert.equal(newAuthRequests,0);
 });
 await check('403 CTSV preserves a separately granted Auth capability',async({page,state,login})=>{
  state.functions=[{code:'auth.accounts.manage',active:true}];
  state.menuStatus=403;
  await login();
  await openNav(page,'Quản trị tài khoản');
  await expect(page.getByLabel('ID đối tượng',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Miễn giảm học phí'})).toHaveCount(0);
 });
 await check('503 makes CTSV navigation unavailable while Auth remains independent',async({page,state,login})=>{
  state.functions=[{code:'auth.accounts.manage',active:true}];
  state.access.mghpSpecialist=true;
  await login();
  await expect(page.getByRole('button',{name:'Miễn giảm học phí'})).toBeVisible();
  state.menuStatus=503;
  await openNav(page,'Chính sách MGHP');
  await expect(page.getByText('Chưa tải được quyền CTSV. Hãy tải lại trang để thử lại.',{exact:true})).toBeVisible();
  await expect(page.getByRole('button',{name:'Miễn giảm học phí'})).toHaveCount(0);
  await expect(page.getByRole('button',{name:'Quản trị',exact:true})).toBeVisible();
 });
 await check('Student, specialist and Auth capabilities combine without duplicate groups',async({page,state,login})=>{
  state.functions=[{code:'auth.accounts.manage',active:true}];
  state.access={student:true,mghpSpecialist:true,ctsvRoleAdmin:false};
  await login();
  await expect(page.getByRole('button',{name:'Miễn giảm học phí'})).toHaveCount(1);
  await expect(page.getByRole('button',{name:'Dữ liệu nền',exact:true})).toHaveCount(1);
  await expect(page.getByRole('button',{name:'Quản trị',exact:true})).toHaveCount(1);
 });
 assert.equal(cases.length,6);
}finally{await browser.close();}
