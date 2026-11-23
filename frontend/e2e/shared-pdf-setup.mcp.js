// Existing five DEV sessions are prepared by shared-pdf-login.cjs. No credential/state files.
async(page)=>{
 if(page.__sharedPdf?.harness&&!page.__sharedPdf.harness.isClosed()&&await page.__sharedPdf.harness.evaluate(()=>typeof window.__pdfHarness?.show==='function'))return {ready:true,reused:true};
 const browser=await page.context().browser().browserType().connectOverCDP('http://127.0.0.1:9337'),roles={};
 for(const p of browser.contexts().flatMap(c=>c.pages())){const name=await p.evaluate(()=>window.name);if(name.startsWith('p11-'))roles[name.slice(4)]=p;}
 if(!['cv','sv','admin','pp','pht'].every(role=>roles[role]))throw Error('Missing DEV sessions; use the RAM-only login harness first');
 const existing=page.__sharedPdf,x=existing??{roles,checks:[],errors:[],writes:[]};page.__sharedPdf=x;
 function observe(p){p.on('pageerror',e=>x.errors.push(e.message));p.on('request',r=>{if(r.method()!=='GET'&&r.method()!=='OPTIONS'&&!r.url().includes('/xac-thuc/'))x.writes.push({method:r.method(),path:new URL(r.url()).pathname});});}
 if(!existing)Object.values(roles).forEach(observe);
 const pending=roles.cv.waitForRequest(r=>r.url().includes('/nen-tang/toi/quyen-giao-dien')&&!!r.headers().authorization);
 await roles.cv.goto('https://127.0.0.1:5173/mghp/tiep-nhan-ho-so');const authorization=(await pending).headers().authorization;
 const h=existing?.harness&&!existing.harness.isClosed()?existing.harness:await roles.cv.context().newPage();x.harness=h;if(h!==existing?.harness)observe(h);
 await h.goto('https://127.0.0.1:5173/e2e/shared-pdf-harness.html');await h.waitForFunction(()=>typeof window.mountPdfHarness==='function');
 await h.evaluate(authorization=>{
  window.__pdfReads=[];
  const client={request:async(path,options)=>{window.__pdfReads.push(path);const response=await fetch(path,{...options,headers:{Authorization:authorization}});if(!response.ok)throw Object.assign(new Error('HTTP '+response.status),{status:response.status});return response;}};
  window.__pdfHarness=window.mountPdfHarness(client,{fileId:'10040',title:'Bản biên bản một chữ ký',selectable:false});
 },authorization);
 await h.locator('canvas[data-rendered-file="10040"]').waitFor({timeout:20000});return {ready:true,database:'ctsv_dev',credentials:'RAM only'};
}
