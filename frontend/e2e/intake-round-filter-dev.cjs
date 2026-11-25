// Read-only DEV regression: semester + round + status filtering. Credentials/session stay in RAM.
const {execFileSync}=require('node:child_process');
const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const {chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE);
const root=path.resolve(__dirname,'../..'),out=path.join(root,'target/intake-round-filter-20261125');fs.mkdirSync(out,{recursive:true});
let browser,page,credential;
const report={database:'ctsv_dev',services:'React/Spring/SQL REAL, no API mocks',cases:[],errors:[]};
const command=String.raw`
$ErrorActionPreference='Stop'
. ./tools/dev/Dev.Common.ps1
$filterHome=Get-DevHome (Get-Location).Path
$filterConfig=Read-DevConfig $filterHome
@{username=$filterConfig.Accounts.specialist.Username;password=(Open-DevSecret $filterConfig.Accounts.specialist.Password)}|ConvertTo-Json -Compress
`;
async function result(change,expected){
 const pending=page.waitForResponse(r=>{
  const url=new URL(r.url());
  return url.pathname==='/api/ctsv/mghp/ho-so'&&r.request().method()==='GET'
   &&Object.entries(expected).every(([key,value])=>url.searchParams.get(key)===value);
 });
 await change();const response=await pending;assert.equal(response.status(),200);
 const query=new URL(response.url()).searchParams;
 for(const [key,value]of Object.entries(expected))assert.equal(query.get(key),value,key);
 const body=await response.json();
 await page.getByRole('heading',{name:'Tiếp nhận & bổ sung hồ sơ MGHP',exact:true}).waitFor();
 await page.waitForFunction(()=>document.querySelector('section[aria-busy="true"]')===null);
 const ids=await page.locator('table[aria-label="Danh sách tiếp nhận hồ sơ"] button[data-application-id]').evaluateAll(nodes=>nodes.map(n=>n.dataset.applicationId));
 assert.deepEqual(ids,body.items.map(item=>item.id));
 assert.equal(await page.getByText(body.totalElements+' hồ sơ phù hợp',{exact:true}).count(),1);
 return body;
}
(async()=>{
 credential=JSON.parse(execFileSync('pwsh',['-NoProfile','-Command',command],{cwd:root,encoding:'utf8',windowsHide:true}));
 browser=await chromium.launch({headless:true,channel:'chrome'});
 const context=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1360,height:950}});
 page=await context.newPage();page.on('pageerror',e=>report.errors.push(e.message));
 await page.goto('https://127.0.0.1:5173');
 await page.getByLabel('Tên đăng nhập',{exact:true}).fill(credential.username);await page.getByLabel('Mật khẩu',{exact:true}).fill(credential.password);
 await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.getByRole('button',{name:'Đăng xuất',exact:true}).waitFor();credential=null;
 const roundsReply=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/ctsv/mghp/dot-xet'&&new URL(r.url()).searchParams.get('size')==='100');
 const applicationsReply=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/ctsv/mghp/ho-so');
 await page.goto('https://127.0.0.1:5173/mghp/tiep-nhan-ho-so');
 const allRounds=await(await roundsReply).json(),initial=await(await applicationsReply).json();
 const populated=allRounds.items.find(round=>initial.items.some(item=>item.roundId===round.id));
 assert.ok(populated,'Need an existing DEV round with applications');
 const empty=allRounds.items.find(round=>round.semesterId===populated.semesterId&&round.status==='draft');
 const other=allRounds.items.find(round=>round.semesterId!==populated.semesterId);
 assert.ok(empty,'Need another draft in the same semester');assert.ok(other,'Need a round in another semester');
 const semester=page.getByLabel('Học kỳ',{exact:true}),round=page.getByLabel('Đợt xét',{exact:true});
 await page.waitForFunction(()=>document.querySelector('section[aria-busy="true"]')===null);
 let body=await result(()=>semester.selectOption(populated.semesterId),{semesterId:populated.semesterId,roundId:null,page:'0'});
 await page.waitForFunction(()=>!document.querySelector('select[aria-label="Đợt xét"]').disabled);
 const expectedRoundIds=allRounds.items.filter(r=>r.semesterId===populated.semesterId).map(r=>r.id).sort();
 assert.deepEqual((await round.locator('option').evaluateAll(nodes=>nodes.map(n=>n.value).filter(Boolean))).sort(),expectedRoundIds);
 report.cases.push({id:'FILTER01',result:'PASS',semester:populated.semesterId,count:body.totalElements});
 body=await result(()=>round.selectOption(populated.id),{semesterId:populated.semesterId,roundId:populated.id,page:'0'});
 assert.ok(body.items.length>0);assert.ok(body.items.every(item=>item.roundId===populated.id));
 if(initial.totalElements<=initial.items.length){
  assert.deepEqual(body.items.map(item=>item.id),initial.items.filter(item=>item.roundId===populated.id).map(item=>item.id));
  assert.equal(body.totalElements,initial.items.filter(item=>item.roundId===populated.id).length);
 }
 report.cases.push({id:'FILTER02',result:'PASS',round:populated.id,count:body.totalElements});
 await page.screenshot({path:path.join(out,'semester-round.png'),fullPage:true});
 body=await result(()=>page.getByRole('button',{name:'Đã nộp',exact:true}).click(),{semesterId:populated.semesterId,roundId:populated.id,status:'submitted',page:'0'});
 assert.ok(body.items.every(item=>item.roundId===populated.id&&item.status==='submitted'));
 report.cases.push({id:'FILTER03',result:'PASS',status:'submitted',count:body.totalElements});
 await result(()=>page.getByRole('button',{name:'Tất cả',exact:true}).click(),{roundId:populated.id,status:null});
 body=await result(()=>round.selectOption(empty.id),{semesterId:populated.semesterId,roundId:empty.id,page:'0'});
 assert.equal(body.totalElements,0);await page.getByText('Không có hồ sơ phù hợp với học kỳ, đợt xét và trạng thái đã chọn.',{exact:true}).waitFor();
 report.cases.push({id:'FILTER04',result:'PASS',emptyRound:empty.id});
 await result(()=>semester.selectOption(other.semesterId),{semesterId:other.semesterId,roundId:null,page:'0'});
 await page.waitForFunction(()=>!document.querySelector('select[aria-label="Đợt xét"]').disabled);
 assert.equal(await round.inputValue(),'');
 assert.deepEqual((await round.locator('option').evaluateAll(nodes=>nodes.map(n=>n.value).filter(Boolean))).sort(),allRounds.items.filter(r=>r.semesterId===other.semesterId).map(r=>r.id).sort());
 report.cases.push({id:'FILTER05',result:'PASS',changedSemester:other.semesterId,oldRoundCleared:true});
 await page.setViewportSize({width:390,height:844});await semester.scrollIntoViewIfNeeded();
 assert.equal(await semester.isVisible(),true);assert.equal(await round.isVisible(),true);
 await page.screenshot({path:path.join(out,'mobile-filters.png'),fullPage:true});
 assert.deepEqual(report.errors,[]);fs.writeFileSync(path.join(out,'result.json'),JSON.stringify(report,null,2));
 console.log(JSON.stringify(report));await browser.close();
})().catch(async error=>{
 let message=String(error.stack);if(credential)message=message.replaceAll(credential.password,'[REDACTED]');credential=null;
 report.failure=message;fs.writeFileSync(path.join(out,'failure.json'),JSON.stringify(report,null,2));console.log(message);
 if(page)await page.screenshot({path:path.join(out,'failure.png'),fullPage:true}).catch(()=>{});
 if(browser){console.log('Context kept; stdin inspect or close');process.stdin.on('data',async input=>{if(input.toString().trim()==='inspect')console.log((await page.locator('main').innerText()).slice(0,2500));else{await browser.close();process.exit(1);}});process.stdin.resume();}
 else process.exitCode=1;
});
