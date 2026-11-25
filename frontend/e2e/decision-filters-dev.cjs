// Focused REAL DEV checks. Sessions/credentials/JWT remain in memory; no storage-state or HAR.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process'),{chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE);
const root=path.resolve(__dirname,'../..'),out=path.join(root,'target/decision-filters-20261125');
fs.mkdirSync(out,{recursive:true});
const origin='https://127.0.0.1:5173',backend='https://127.0.0.1:8443',round='20051',semester='1';
const evidence={database:'ctsv_dev',frontend:origin,backend,round,cases:[],requests:[],pageErrors:[],failures:[]};
let browser,context,page,accounts,token;
const save=()=>fs.writeFileSync(path.join(out,'browser.json'),JSON.stringify(evidence,null,2));
const pass=id=>{evidence.cases.push({id,result:'PASS'});save();console.log('PASS '+id);};
async function ready(p=page){await p.getByRole('region',{name:'Hội đồng và quyết định MGHP',exact:true}).waitFor();await p.waitForFunction(()=>document.querySelector('[aria-label="Hội đồng và quyết định MGHP"]')?.getAttribute('aria-busy')==='false');}
async function login(role){
 const c=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1440,height:1000},ignoreHTTPSErrors:true}),p=await c.newPage();
 p.on('pageerror',e=>evidence.pageErrors.push(e.message));
 p.on('response',async r=>{try{const u=new URL(r.url());if(u.pathname==='/api/xac-thuc/dang-nhap'&&r.status()===200&&role==='specialist')token=(await r.json()).accessToken;
  if(role==='specialist'&&u.pathname.startsWith('/api/ctsv/mghp/'))evidence.requests.push({method:r.request().method(),path:u.pathname+u.search,status:r.status()});}catch{/* navigation can discard a body */}});
 await p.goto(origin);await p.getByLabel('Tên đăng nhập',{exact:true}).fill(accounts[role].username);await p.getByLabel('Mật khẩu',{exact:true}).fill(accounts[role].password);
 await p.getByRole('button',{name:'Đăng nhập',exact:true}).click();await p.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();accounts[role]=null;return {context:c,page:p};
}
async function select(){
 await page.getByRole('combobox',{name:'Học kỳ',exact:true}).selectOption(semester);await ready();
 await page.getByRole('combobox',{name:'Đợt xét',exact:true}).selectOption(round);await ready();
 assert.equal(await page.getByText('Đánh giá đủ căn cứ trước tổng hợp',{exact:true}).locator('..').locator('strong').textContent(),'1');
 assert.equal(await page.getByText('Loại khỏi tập đủ điều kiện',{exact:true}).locator('..').locator('strong').textContent(),'1');
 assert.equal(await page.getByRole('alert').count(),0);
}
async function inspect(){
 await page.getByRole('button',{name:'Miễn giảm học phí',exact:true}).click();await page.getByRole('link',{name:'Hội đồng & quyết định',exact:true}).click();await ready();
 assert.equal(await page.getByRole('combobox',{name:'Đợt xét',exact:true}).isDisabled(),true);pass('DF01-menu-and-semester-required');
 // Same failing read as old frontend: backend rejects missing semester. No mutation.
 const old=await context.request.get(backend+'/api/ctsv/mghp/dot-xet/'+round+'/dong-xet',{headers:{Authorization:'Bearer '+token}});
 assert.equal(old.status(),400);const problem=await old.json();assert.equal(problem.code,'VALIDATION_ERROR');assert.equal(problem.errors[0].field,'semesterId');
 evidence.oldFailure={status:old.status(),code:problem.code,field:problem.errors[0].field};pass('DF02-old-request-failure-confirmed');
 await select();assert(evidence.requests.some(r=>r.path==='/api/ctsv/mghp/dot-xet/'+round+'/dong-xet?semesterId=1'&&r.status===200));
 await page.screenshot({path:path.join(out,'01-source-loaded.png'),fullPage:true});pass('DF03-real-source-loaded-1-eligible-1-excluded');
 console.log('BUTTONS '+JSON.stringify(await page.getByRole('button').allTextContents()));
}
async function filters(){
 await page.getByRole('combobox',{name:'Học kỳ',exact:true}).selectOption('4');await ready();
 assert.equal(await page.getByRole('combobox',{name:'Đợt xét',exact:true}).inputValue(),'');assert.equal(await page.getByText('Đánh giá đủ căn cứ trước tổng hợp',{exact:true}).count(),0);
 assert.equal(await page.getByRole('button',{name:'Tạo bộ xét',exact:true}).count(),0);
 assert(!await page.getByRole('combobox',{name:'Đợt xét',exact:true}).locator('option[value="'+round+'"]').count());pass('DF04-change-semester-clears-old-round-and-source');
 await select();await page.reload();await ready();assert.equal(await page.getByRole('combobox',{name:'Học kỳ',exact:true}).inputValue(),semester);assert.equal(await page.getByRole('combobox',{name:'Đợt xét',exact:true}).inputValue(),round);pass('DF05-refresh-keeps-filters');
 await page.goto(origin+'/mghp/hoi-dong-quyet-dinh?dot-xet='+round);await ready();assert.equal(await page.getByRole('combobox',{name:'Học kỳ',exact:true}).inputValue(),semester);assert.equal(new URL(page.url()).searchParams.get('hoc-ky'),semester);pass('DF06-legacy-link-resolves-server-semester');
 await page.goto(origin+'/mghp/hoi-dong-quyet-dinh?hoc-ky=4&dot-xet='+round);await ready();await page.getByRole('alert').filter({hasText:'Đợt xét không thuộc học kỳ đang chọn'}).waitFor();assert.equal(await page.getByRole('button',{name:'Tạo bộ xét',exact:true}).count(),0);pass('DF07-mismatched-deep-link-blocked');
 await select();await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);await page.screenshot({path:path.join(out,'02-mobile-filters.png'),fullPage:true});await page.setViewportSize({width:1440,height:1000});pass('DF08-mobile-no-overflow');
}
async function handoff(){
 await page.goto(origin+'/mghp/danh-gia?hoc-ky=1&dot-xet='+round);
 const link=page.getByRole('link',{name:'Đến Hội đồng & quyết định',exact:true});await link.waitFor();assert.equal(await link.getAttribute('href'),'/mghp/hoi-dong-quyet-dinh?hoc-ky=1&dot-xet='+round);
 await link.click();await ready();assert.equal(await page.getByRole('combobox',{name:'Học kỳ',exact:true}).inputValue(),semester);assert.equal(await page.getByRole('combobox',{name:'Đợt xét',exact:true}).inputValue(),round);assert.equal(await page.getByRole('alert').count(),0);pass('DF09-handoff-keeps-context');
}
async function security(){
 for(const role of ['student','admin']){const account=await login(role);await account.page.goto(origin+'/mghp/hoi-dong-quyet-dinh?hoc-ky=1&dot-xet='+round);await account.page.getByText(/không có quyền/i).first().waitFor();assert.equal(await account.page.getByRole('combobox',{name:'Học kỳ',exact:true}).count(),0);pass('DF13-direct-url-denied-'+role);await account.context.close();}
}
async function finish(){assert.deepEqual(evidence.pageErrors,[]);save();await browser.close();console.log('FINISHED '+evidence.cases.length);process.exit(0);}
const actions={inspect,filters,handoff,security,finish};
async function run(name){try{assert(actions[name]);await actions[name]();}catch(error){let message=String(error.stack);for(const a of Object.values(accounts??{}))if(a?.password)message=message.replaceAll(a.password,'[REDACTED]');if(token)message=message.replaceAll(token,'[REDACTED]');evidence.failures.push({phase:name,message});save();await page?.screenshot({path:path.join(out,'failure-'+name+'.png'),fullPage:true}).catch(()=>{});console.log('FAIL '+message.split('\n').slice(0,6).join('\n'));console.log('Context retained.');}}
(async()=>{
 const setup=JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',String.raw`. ./tools/dev/Dev.Common.ps1
$homePath=Get-DevHome (Get-Location).Path;$cfg=Read-DevConfig $homePath
$c=[Data.SqlClient.SqlConnection]::new('Data Source=localhost\DEV2022;Initial Catalog=ctsv_dev;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connect Timeout=5');$c.Open();$cmd=$c.CreateCommand();$cmd.CommandText="SELECT CONVERT(nvarchar(200),value) FROM sys.extended_properties WHERE class=0 AND name='ctsv_dev_owner'";if($cmd.ExecuteScalar() -ne 'ctsv-local-dev-v1'){throw 'DEV owner mismatch'};$cmd.CommandText='SELECT @@SERVERNAME';$instance=$cmd.ExecuteScalar();$c.Dispose()
$accounts=@{};foreach($kind in @('specialist','student','admin')){$accounts[$kind]=@{username=$cfg.Accounts.$kind.Username;password=(Open-DevSecret $cfg.Accounts.$kind.Password)}};@{accounts=$accounts;instance=$instance}|ConvertTo-Json -Compress`],{cwd:root,encoding:'utf8',windowsHide:true}));
 accounts=setup.accounts;evidence.instance=setup.instance;
 browser=await chromium.launch({channel:'chrome',headless:true});const primary=await login('specialist');context=primary.context;page=primary.page;
 await run('inspect');const lines=require('node:readline').createInterface({input:process.stdin});for await(const line of lines)if(line.trim())await run(line.trim());
})().catch(e=>{console.log('Browser setup failed: '+e.name);process.exitCode=1;});
