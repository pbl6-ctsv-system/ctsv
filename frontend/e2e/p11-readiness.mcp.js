async (page)=>{
 const browser=await page.context().browser().browserType().connectOverCDP('http://127.0.0.1:9337');
 const pages=browser.contexts().flatMap(c=>c.pages());let p;
 for(const candidate of pages)if((await candidate.locator('body').innerText()).includes('dev.chuyenvien'))p=candidate;
 if(!p)throw new Error('Existing specialist session required; do not save tokens or log in again.');
 await p.reload();
 await p.getByRole('button',{name:'Chuẩn bị bộ văn bản',exact:true}).waitFor({timeout:6000});
 if(await p.getByText('Đang kiểm tra quyền ký số…',{exact:true}).count())throw new Error('Permission loading stuck');
 if(await p.getByRole('alert').count())throw new Error('Unexpected alert while opening signing');
 return {checks:3,scope:'REAL DEV initial load + StrictMode + refresh',url:p.url()};
}
