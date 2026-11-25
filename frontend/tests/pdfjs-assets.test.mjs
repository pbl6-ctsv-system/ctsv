import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createPdfJsAssetsPlugin} from '../tools/pdfjs-assets.mjs';
import {pdfDocumentOptions} from '../src/shared/ui/pdf/pdf-options.mjs';

const require=createRequire(import.meta.url),{version}=require('pdfjs-dist/package.json');
test('document options provide same-origin versioned image, font, CMap and ICC resources',()=>{
 const data=new Uint8Array([1,2,3]),base='https://127.0.0.1:5173/pdfjs/'+version+'/';
 const options=pdfDocumentOptions(data,base);
 assert.equal(options.data,data);assert.equal(options.wasmUrl,base+'wasm/');
 assert.equal(options.standardFontDataUrl,base+'standard_fonts/');assert.equal(options.cMapUrl,base+'cmaps/');assert.equal(options.iccUrl,base+'iccs/');assert.equal(options.cMapPacked,true);
});
test('production emits the actual package decoders and their redistribution licenses at stable names',()=>{
 const assets=[];const plugin=createPdfJsAssetsPlugin();plugin.generateBundle.call({emitFile:asset=>assets.push(asset)});
 const prefix='pdfjs/'+version+'/';
 for(const file of ['wasm/jbig2.wasm','wasm/jbig2_nowasm_fallback.js','wasm/openjpeg.wasm','wasm/LICENSE_JBIG2','standard_fonts/FoxitSerif.pfb','cmaps/Adobe-GB1-UCS2.bcmap']){
  const asset=assets.find(a=>a.fileName===prefix+file);assert.ok(asset,'Missing package resource: '+file);assert.ok(asset.source.byteLength>0);
 }
});
test('DEV serves the decoder with WASM MIME; unknown/traversal paths cannot read files',()=>{
 const plugin=createPdfJsAssetsPlugin();let handler;plugin.configureServer({middlewares:{use:fn=>handler=fn}});
 const prefix='/pdfjs/'+version+'/';
 function request(path){const result={headers:{},next:false,status:200};handler({url:path},{setHeader:(k,v)=>result.headers[k]=v,set statusCode(v){result.status=v;},end:body=>result.body=body},()=>result.next=true);return result;}
 const wasm=request(prefix+'wasm/jbig2.wasm');assert.equal(wasm.status,200);assert.equal(wasm.headers['Content-Type'],'application/wasm');assert.deepEqual([...wasm.body.subarray(0,4)],[0,97,115,109]);
 for(const path of [prefix+'wasm/../../package.json',prefix+'wasm/%2e%2e/package.json',prefix+'wasm/missing.wasm']){const response=request(path);assert.equal(response.status,404);assert.equal(response.next,false);}
 assert.equal(request('/api/ctsv/tep/10028/noi-dung').next,true);
});
