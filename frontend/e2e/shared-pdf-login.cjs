// Reuse existing DEV browser contexts; DPAPI credentials and sessions stay in RAM.
const {execFileSync}=require('node:child_process');
const path=require('node:path');
const {chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE);
const command=String.raw`
. ./tools/dev/Dev.Common.ps1
$localHome=Get-DevHome (Get-Location).Path
$cfg=Read-DevConfig $localHome
$values=@{}
foreach($pair in @(@('cv','specialist'),@('sv','student'),@('admin','admin'))){$account=$cfg.Accounts.($pair[1]);$values[$pair[0]]=@{username=$account.Username;password=(Open-DevSecret $account.Password)}}
$extra=Get-Content (Join-Path $localHome 'p11-accounts.dpapi.json') -Raw|ConvertFrom-Json -AsHashtable
foreach($role in @('PP','PHT')){$values[$role.ToLowerInvariant()]=@{username=$extra[$role].Username;password=(Open-DevSecret $extra[$role].Password)}}
$values|ConvertTo-Json -Compress
`;
let credentials;
(async()=>{
 credentials=JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',command],{cwd:path.resolve(__dirname,'../..'),encoding:'utf8',windowsHide:true}));
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9337');const roles={};
 for(const p of browser.contexts().flatMap(c=>c.pages())){const name=await p.evaluate(()=>window.name);if(name.startsWith('p11-'))roles[name.slice(4)]=p;}
 let logins=0;
 for(const name of ['cv','sv','pht','admin','pp']){
  const p=roles[name];if(!p)throw Error('Missing named DEV context');
  await p.goto('https://127.0.0.1:5173/');
  await p.getByRole('button',{name:/^(Đăng nhập|Đăng xuất)$/}).first().waitFor();
  if(await p.getByRole('textbox',{name:'Tên đăng nhập',exact:true}).count()){
   await p.getByRole('textbox',{name:'Tên đăng nhập',exact:true}).fill(credentials[name].username);
   await p.getByLabel('Mật khẩu',{exact:true}).fill(credentials[name].password);
   await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();
   await p.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();logins++;
  }
 }
 credentials=null;console.log('READY: five existing DEV contexts; '+logins+' expired sessions logged in once; no state file.');process.exit(0);
})().catch(error=>{
 let message=String(error.message).split('\n')[0];if(credentials)for(const c of Object.values(credentials))message=message.replaceAll(c.password,'[REDACTED]');
 credentials=null;console.error('Shared PDF login: '+message+'; existing contexts retained.');process.exit(1);
});
