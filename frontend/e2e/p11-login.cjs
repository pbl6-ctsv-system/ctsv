// DPAPI -> RAM -> browser. Never persist passwords, JWTs, storageState or login traces.
const {execFileSync}=require('node:child_process');
const path=require('node:path');
const {chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE);
const root=path.resolve(__dirname,'../..');
const command=String.raw`
$ErrorActionPreference='Stop'
. ./tools/dev/Dev.Common.ps1
$localHome=Get-DevHome (Get-Location).Path
$cfg=Read-DevConfig $localHome
$values=@{}
foreach($role in @('specialist','student','admin')){$values[$role]=@{username=$cfg.Accounts.$role.Username;password=(Open-DevSecret $cfg.Accounts.$role.Password)}}
$extra=Get-Content -LiteralPath (Join-Path $localHome 'p11-accounts.dpapi.json') -Raw|ConvertFrom-Json -AsHashtable
foreach($role in @('PP','PHT')){$values[$role.ToLowerInvariant()]=@{username=$extra[$role].Username;password=(Open-DevSecret $extra[$role].Password)}}
$values|ConvertTo-Json -Compress
`;
let credentials,browser;
(async()=>{
 credentials=JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',command],{cwd:root,encoding:'utf8',windowsHide:true}));
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--remote-debugging-address=127.0.0.1','--remote-debugging-port=9337']});
 for(const role of ['specialist','pp','pht','student','admin']){
  const context=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1360,height:1000}});
  const page=await context.newPage();await page.goto('https://127.0.0.1:5173');
  await page.evaluate(name=>window.name=name,'p11-'+({specialist:'cv',student:'sv'}[role]??role));
  await page.getByRole('textbox',{name:'Tên đăng nhập',exact:true}).fill(credentials[role].username);
  await page.getByLabel('Mật khẩu',{exact:true}).fill(credentials[role].password);
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
 }
 credentials=null;console.log('READY P11: five DEV sessions in RAM; CDP loopback 9337; no state file.');
 process.stdin.resume();process.stdin.on('data',async()=>{await browser.close();process.exit(0);});
})().catch(error=>{
 let message=String(error.message).split('\n')[0];if(credentials)for(const value of Object.values(credentials))message=message.replaceAll(value.password,'[REDACTED]');
 credentials=null;console.log('P11 LOGIN FAILED: '+message);process.exitCode=1;
 if(browser){console.log('Context retained for diagnosis; close via stdin.');process.stdin.resume();process.stdin.on('data',async()=>{await browser.close();process.exit(1);});}
});
