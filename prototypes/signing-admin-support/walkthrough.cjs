// UI-only browser review. No credentials, backend, SQL, CA or Cloudinary.
const fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url'),assert=require('node:assert/strict');
const {chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE||'C:/Users/USER/AppData/Local/npm-cache/_npx/9833c18b2d85bc59/node_modules/playwright');
const dir=__dirname,out=path.join(dir,'screenshots'),origin='http://127.0.0.1:5182';fs.mkdirSync(out,{recursive:true});
const evidence={mode:'PROTOTYPE_SIMULATION_ONLY',revision:2,origin,steps:[],cases:[],pageErrors:[],externalRequests:[],failures:[]};
let browser,context,page,runId=0;
const save=()=>{fs.writeFileSync(path.join(dir,'review.json'),JSON.stringify(evidence,null,2));};
function safeName(text){return text.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d').replace(/Đ/g,'D').replace(/[^a-zA-Z0-9]+/g,'-').toLowerCase().slice(0,70);}
async function capture(group,title,action){
 const step=evidence.steps.length+1,file='r2-'+String(step).padStart(2,'0')+'-'+safeName(title)+'.png';
 await page.screenshot({path:path.join(out,file),fullPage:true});evidence.steps.push({step,group,title,action,file:'screenshots/'+file});save();console.log('IMAGE '+step+' '+title);
}
function pass(name){evidence.cases.push({name,result:'PASS'});save();console.log('PASS '+name);}
async function settled(){await page.locator('.busy-bar').waitFor({state:'hidden'});}
async function act(action,id){let l=page.locator('[data-action="'+action+'"]');if(id)l=l.filter({has:page.locator(':scope')});if(id)l=page.locator('[data-action="'+action+'"][data-id="'+id+'"]');await l.click();}
async function openIncident(id){await page.getByRole('button',{name:'Xử lý sự cố ký số',exact:true}).click();await page.getByRole('tab',{name:/^Tất cả/}).click();await act('open-incident',id);}
async function scenario(value){await page.getByRole('combobox',{name:'Kịch bản demo',exact:true}).selectOption(value);}
async function role(value){await page.getByRole('combobox',{name:'Góc nhìn demo',exact:true}).selectOption(value);}
const state=()=>page.evaluate(()=>JSON.parse(localStorage.getItem('ctsv.prototype.signing-admin.v1')));
const helpers={assert,capture,pass,settled,act,openIncident,scenario,role,state,origin};
async function run(name){try{
 const steps=await import(pathToFileURL(path.join(dir,'review-simplified-steps.mjs')).href+'?run='+(++runId));assert.equal(typeof steps[name],'function');await steps[name](page,helpers);save();console.log('PHASE PASS '+name);
 }catch(error){evidence.failures.push({phase:name,message:error.stack});save();await page.screenshot({path:path.join(out,'failure-'+name+'.png'),fullPage:true}).catch(()=>{});console.log('PHASE FAIL '+name+' '+error.stack.split('\n').slice(0,6).join('\n'));console.log('Context retained.');}}
(async()=>{
 browser=await chromium.launch({channel:'chrome',headless:true});context=await browser.newContext({locale:'vi-VN',timezoneId:'Asia/Ho_Chi_Minh',viewport:{width:1440,height:1050},permissions:['clipboard-read','clipboard-write']});page=await context.newPage();
 await context.route('**/*',route=>{const url=route.request().url();if(url.startsWith(origin+'/'))return route.continue();evidence.externalRequests.push(url);return route.abort();});
 page.on('pageerror',error=>evidence.pageErrors.push(error.message));await page.goto(origin);await page.getByRole('heading',{name:'Xử lý sự cố ký số',exact:true}).waitFor();
 await run('inspect');
 const lines=require('node:readline').createInterface({input:process.stdin});for await(const line of lines){const name=line.trim();if(name==='finish'){assert.deepEqual(evidence.externalRequests,[]);assert.deepEqual(evidence.pageErrors,[]);save();fs.writeFileSync(path.join(dir,'verification.json'),JSON.stringify({mode:evidence.mode,revision:2,images:evidence.steps.length,passedCases:evidence.cases.length,pageErrors:evidence.pageErrors.length,externalRequests:evidence.externalRequests.length,openFailures:evidence.failures.filter(f=>!f.resolved).length,productionIntegration:false},null,2));await browser.close();console.log('FINISHED '+JSON.stringify({images:evidence.steps.length,cases:evidence.cases.length,pageErrors:evidence.pageErrors.length}));process.exit(0);}if(name)await run(name);}
})().catch(error=>{console.log(error.stack);process.exitCode=1;});
