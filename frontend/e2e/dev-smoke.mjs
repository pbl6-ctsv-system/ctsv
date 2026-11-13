import {chromium} from '@playwright/test';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
const evidence=path.resolve('../../../nghiepvu/.analysis/runtime/p04/20261112/wasm');
await mkdir(evidence,{recursive:true});
const css=path.resolve('../src/style.css'),original=await readFile(css);
const browser=await chromium.launch({channel:'chrome',headless:true});
const result={browser:browser.version(),origin:'http://127.0.0.1:5173',tls:'NOT_APPLICABLE: dev/HMR smoke only; HTTPS E2E separate'};
try{
 const page=await browser.newPage();let navigations=0;
 page.on('framenavigated',frame=>{if(frame===page.mainFrame())navigations++});
 await page.goto(result.origin);
 await page.getByRole('heading',{name:'Đăng nhập CTSV'}).waitFor();
 const before=navigations,marker='probe'+Date.now();
 await writeFile(css,Buffer.concat([original,Buffer.from('\n:root { --p04-hmr-probe: '+marker+'; }\n')]));
 await page.waitForFunction(value=>getComputedStyle(document.documentElement).getPropertyValue('--p04-hmr-probe').trim()===value,marker);
 assert.equal(navigations,before,'CSS HMR must not reload the document');
 result.dev='PASS';result.hmr='PASS';result.fullPageReloads=navigations-before;
 await page.screenshot({path:path.join(evidence,'dev-login.png'),fullPage:true});
}finally{await writeFile(css,original);await browser.close();}
await writeFile(path.join(evidence,'dev-hmr.json'),JSON.stringify(result,null,2));
console.log(JSON.stringify(result));
