import {createRequire} from 'node:module';
import {dirname,join} from 'node:path';
import {readFileSync,readdirSync} from 'node:fs';

// Publish resources from the same installed package as the renderer/worker, without
// checking vendor binaries into Git or changing their decoder-required filenames.
export function createPdfJsAssetsPlugin(){
 const require=createRequire(import.meta.url),root=dirname(require.resolve('pdfjs-dist/package.json'));
 const {version}=JSON.parse(readFileSync(join(root,'package.json'),'utf8'));
 const namespace='pdfjs/'+version+'/',files=new Map();let prefix='/'+namespace;
 for(const directory of ['wasm','standard_fonts','cmaps','iccs'])for(const file of readdirSync(join(root,directory),{withFileTypes:true})){
  if(file.isFile())files.set(directory+'/'+file.name,join(root,directory,file.name));
 }
 const mime=file=>file.endsWith('.wasm')?'application/wasm':file.endsWith('.js')?'text/javascript':file.endsWith('.ttf')?'font/ttf':'application/octet-stream';
 return {
  name:'ctsv-pdfjs-assets',
  configResolved(config){prefix=new URL(config.base,'http://localhost').pathname+namespace;},
  configureServer(server){server.middlewares.use((request,response,next)=>{
   const path=request.url?.split('?')[0];if(!path?.startsWith(prefix))return next();
   const file=path.slice(prefix.length),source=files.get(file);
   if(!source){response.statusCode=404;response.end();return;}
   response.setHeader('Content-Type',mime(file));response.setHeader('Cache-Control','no-cache');
   response.end(readFileSync(source));
  });},
  generateBundle(){for(const [file,source]of files)this.emitFile({type:'asset',fileName:namespace+file,source:readFileSync(source)});}
 };
}
