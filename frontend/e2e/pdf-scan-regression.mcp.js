// Run against the existing RAM-only DEV sessions in page.__pdfRepair.roles.
// Known owned fixture root10028/file10032; do not enumerate or alter other dossiers.
async(page)=>{
 const x=page.__pdfRepair,p=x?.roles?.cv;if(!p)throw Error('Prepare DEV role sessions first');
 const out='D:/7rd_semester/pbl6_src/ctsv/target/pdf-render-fix-20261124/';
 x.messages=[];const pages=[],assets=[],writes=[];
 const observe=r=>{if(!['GET','OPTIONS'].includes(r.method())&&!r.url().includes('/xac-thuc/'))writes.push(new URL(r.url()).pathname);};
 const asset=r=>{if(r.url().includes('/pdfjs/'))assets.push({path:new URL(r.url()).pathname,status:r.status()});};p.on('request',observe);p.on('response',asset);
 try{
  await p.goto('https://127.0.0.1:5173/mghp/tiep-nhan-ho-so');await p.locator('[data-application-id="10028"]').click();
  await p.locator('canvas[data-rendered-file="10032"][data-rendered-page="1"]').waitFor({timeout:20000});
  for(let n=1;n<=35;n++){
   await p.getByRole('spinbutton',{name:'Trang PDF',exact:true}).fill(String(n));
   await p.locator('canvas[data-rendered-file="10032"][data-rendered-page="'+n+'"]').waitFor({timeout:20000});
   const stats=await p.locator('canvas').evaluate(c=>{
    const d=c.getContext('2d').getImageData(0,0,c.width,c.height).data;let dark=0;
    for(let y=Math.floor(c.height*.2);y<c.height*.9;y++)for(let x=0;x<c.width;x++){const i=(y*c.width+x)*4;if(d[i]<170&&d[i+1]<170&&d[i+2]<170&&d[i+3]>0)dark++;}
    return {page:Number(c.dataset.renderedPage),bodyDarkPixels:dark};
   });
   if(stats.bodyDarkPixels<500)throw Error('Page '+n+' has no readable body content; inspect against Poppler reference');pages.push(stats);
   if([1,2,17,35].includes(n))await p.getByRole('region',{name:'PDF đang xem',exact:true}).screenshot({path:out+'after-page-'+n+'.png'});
  }
  const pending=p.waitForEvent('download');await p.getByRole('button',{name:'Tải PDF đang xem',exact:true}).click();const d=await pending;
  const downloaded=await p.evaluate(async url=>{const b=await(await fetch(url)).arrayBuffer();return {bytes:b.byteLength,sha256:Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',b)),x=>x.toString(16).padStart(2,'0')).join('')};},d.url());
  if(downloaded.sha256!=='b9bb871fa524482a1f61d48ba953c0ae5f776326df34292298cf7ce0d2bf5c46')throw Error('Download changed original PDF');
  const decodeErrors=x.messages.filter(m=>/Unable to decode|JBig2 failed|wasmUrl.*provided|Dependent image/.test(m.text));
  if(decodeErrors.length)throw Error('Image decode warnings remain');
  if(!assets.some(a=>a.path.endsWith('/jbig2.wasm')&&a.status===200))throw Error('No real WASM decoder200 response');
  if(writes.length)throw Error('Unexpected business write');
  const result={database:'ctsv_dev',fixture:{rootId:'10028',fileId:'10032'},mode:'REAL frontend/backend/SQL/Cloudinary; no stub',pages,downloaded,assets,decodeErrors,businessWrites:writes};
  x.scanResult=result;return result;
 }finally{p.off('request',observe);p.off('response',asset);}
}
