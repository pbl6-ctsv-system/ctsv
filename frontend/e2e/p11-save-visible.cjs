// Export only the already visible synthetic PDF bytes. No JWT/password/storageState is written.
const fs=require('node:fs'),path=require('node:path');
const {chromium}=require(process.env.CTSV_PLAYWRIGHT_MODULE);
(async()=>{
 const browser=await chromium.connectOverCDP('http://127.0.0.1:9337');
 const pages=browser.contexts().flatMap(c=>c.pages());let page;
 for(const p of pages)if(await p.evaluate(()=>window.name)==='p11-cv')page=p;
 if(!page||!page.url().endsWith('?yeu-cau=4'))throw new Error('Expected existing page2 DEV request4; no automatic signing');
 await page.getByText('Chờ Phó Hiệu trưởng ký',{exact:true}).first().waitFor({timeout:8000});
 await page.locator('canvas[data-rendered-page="2"]').waitFor({timeout:8000});
 const download=page.waitForEvent('download');await page.getByRole('button',{name:'Tải PDF đang xem',exact:true}).click();const d=await download;
 const data=await page.evaluate(async url=>{const b=new Uint8Array(await(await fetch(url)).arrayBuffer());let s='';for(let i=0;i<b.length;i+=8192)s+=String.fromCharCode(...b.subarray(i,i+8192));return btoa(s);},d.url());
 const out=path.resolve(__dirname,'../../target/p11-evidence/dev-output-page2.pdf');fs.writeFileSync(out,Buffer.from(data,'base64'));
 console.log('Saved visible CTSV PDF for PDFBox page/geometry/CMS inspection.');process.exit(0);
})().catch(()=>{console.error('Export failed; inspect current viewer state before retry.');process.exit(1);});
