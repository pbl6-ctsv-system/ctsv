// Only DOT03's same/cross-semester rejection on owner-approved DEV fixtures. No signing/upload.
// DPAPI credentials and browser session remain in RAM; no storageState or login traces.
const {execFileSync}=require('node:child_process');
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE);
const root=path.resolve(__dirname,'../..');
const output=path.join(root,'target/round-sequence-20261125');
const fixtures=JSON.parse(fs.readFileSync(path.join(output,'dev-fixtures.json'),'utf8').replace(/^\uFEFF/,''));
const secretCommand=String.raw`
$ErrorActionPreference='Stop'
. ./tools/dev/Dev.Common.ps1
$sequenceHome=Get-DevHome (Get-Location).Path
$sequenceConfig=Read-DevConfig $sequenceHome
@{username=$sequenceConfig.Accounts.specialist.Username;password=(Open-DevSecret $sequenceConfig.Accounts.specialist.Password)}|ConvertTo-Json -Compress
`;
let credential,browser;
const evidence={database:'ctsv_dev',origin:'https://127.0.0.1:5173',services:'React/Spring/SQL REAL; no mocks',cases:[],pageErrors:[]};
(async()=>{
 credential=JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',secretCommand],{cwd:root,encoding:'utf8',windowsHide:true}));
 browser=await chromium.launch({channel:'chrome',headless:true});
 const context=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1360,height:950}});
 const page=await context.newPage();page.on('pageerror',error=>evidence.pageErrors.push(error.message));
 await page.goto(evidence.origin);
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(credential.username);
 await page.getByLabel('Mật khẩu',{exact:true}).fill(credential.password);
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();credential=null;
 for(const [code,mobile] of [['SEQ25-DEV-HK1-A',false],['SEQ25-DEV-HK4-A',true]].filter(([code])=>!process.env.ROUND_SEQUENCE_CASE||process.env.ROUND_SEQUENCE_CASE===code)){
  const fixture=fixtures.find(item=>item.code===code);assert.ok(fixture);assert.equal(fixture.status,'draft');
  await page.setViewportSize(mobile?{width:390,height:844}:{width:1360,height:950});
  const list=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/ctsv/mghp/dot-xet'&&response.request().method()==='GET');
  await page.goto(evidence.origin+'/mghp/dot-xet');assert.equal((await list).status(),200);
  const row=page.getByRole('row').filter({hasText:code});await row.waitFor();
  assert.equal(await row.count(),1);
  const detail=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/ctsv/mghp/dot-xet/'+fixture.id&&response.request().method()==='GET');
  await row.getByRole('button',{name:'Xem đợt',exact:true}).click();assert.equal((await detail).status(),200);
  const publish=page.getByRole('button',{name:'Công khai đợt',exact:true});await publish.waitFor();assert.equal(await publish.isEnabled(),true);
  const responsePromise=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/ctsv/mghp/dot-xet/'+fixture.id+'/mo'&&response.request().method()==='POST');
  await publish.click();const response=await responsePromise;assert.equal(response.status(),409);
  assert.equal((await response.json()).code,'ROUND_PREVIOUS_NOT_COMPLETED');
  const notice=page.getByRole('alert').filter({hasText:'còn đợt MGHP chưa hoàn tất'});await notice.waitFor();assert.equal(await notice.isVisible(),true);
  await page.screenshot({path:path.join(output,mobile?'cross-semester-mobile.png':'same-semester-desktop.png'),fullPage:true});
  const freshList=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/ctsv/mghp/dot-xet'&&response.request().method()==='GET');
  await page.reload();const fresh=await freshList;assert.equal(fresh.status(),200);
  const saved=(await fresh.json()).items.find(item=>item.id===String(fixture.id));assert.ok(saved);assert.equal(saved.status,'draft');
  const refreshedRow=page.getByRole('row').filter({hasText:code});await refreshedRow.waitFor();assert.equal(await refreshedRow.count(),1);
  evidence.cases.push({id:mobile?'DOT-SEQ-DEV02':'DOT-SEQ-DEV01',code,status:'PASS',http:409,error:'ROUND_PREVIOUS_NOT_COMPLETED',persisted:'draft',viewport:mobile?'mobile':'desktop'});
 }
 assert.deepEqual(evidence.pageErrors,[]);
 fs.writeFileSync(path.join(output,process.env.ROUND_SEQUENCE_CASE?'browser-retry.json':'browser.json'),JSON.stringify(evidence,null,2));
 console.log(JSON.stringify({cases:evidence.cases,pageErrors:evidence.pageErrors}));
 await browser.close();
})().catch(error=>{
 let message=String(error.stack);if(credential)message=message.replaceAll(credential.password,'[REDACTED]');credential=null;
 evidence.failure=message;fs.writeFileSync(path.join(output,'browser-failure.json'),JSON.stringify(evidence,null,2));
 console.log('ROUND SEQUENCE BROWSER FAIL: '+message);
 if(browser){console.log('Test context retained. Inspect before closing via stdin.');process.stdin.resume();process.stdin.on('data',async()=>{await browser.close();process.exit(1);});}else process.exitCode=1;
});
