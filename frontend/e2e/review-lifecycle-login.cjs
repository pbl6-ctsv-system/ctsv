// Local credential bridge for Playwright: DPAPI -> memory -> browser login form.
// No password, token, profile/storageState or trace is written to disk or printed.
const {execFileSync}=require('node:child_process');
const path=require('node:path');
const {chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE);
const root=path.resolve(__dirname,'../..');
const command=String.raw`
$ErrorActionPreference='Stop'
. ./tools/dev/Dev.Common.ps1
$cfg=Read-DevConfig (Get-DevHome (Get-Location).Path)
$values=@{}
foreach($role in @('specialist','student','admin')){$values[$role]=@{username=$cfg.Accounts.$role.Username;password=(Open-DevSecret $cfg.Accounts.$role.Password)}}
$values|ConvertTo-Json -Compress
`;
let credentials,browser;
(async()=>{
 credentials=JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',command],{cwd:root,encoding:'utf8',windowsHide:true}));
 browser=await chromium.launch({headless:false,channel:'chrome',args:['--remote-debugging-address=127.0.0.1','--remote-debugging-port=9337']});
 for(const role of ['specialist','student','admin']){
  const context=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1360,height:1000}});
  const page=await context.newPage();await page.goto('https://127.0.0.1:5173');
  await page.getByRole('textbox',{name:'Tên đăng nhập',exact:true}).fill(credentials[role].username);
  await page.getByLabel('Mật khẩu',{exact:true}).fill(credentials[role].password);
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();
 }
 credentials=null;console.log('READY: 3 DEV role sessions in memory; CDP loopback 9337. No state file.');
 process.stdin.resume();process.stdin.on('data',async()=>{await browser.close();process.exit(0);});
})().catch(error=>{
 let message=String(error.message).split('\n')[0];
 if(credentials)for(const value of Object.values(credentials))message=message.replaceAll(value.password,'[REDACTED]');
 credentials=null;console.log('LOGIN HARNESS FAILED: '+message);process.exitCode=1;
 if(browser){console.log('Context retained for investigation; close via stdin.');process.stdin.resume();process.stdin.on('data',async()=>{await browser.close();process.exit(1);});}
});
