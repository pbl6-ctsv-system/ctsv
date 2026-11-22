const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const routes={'/':'index.html','/index.html':'index.html','/app.js':'app.js','/style.css':'style.css','/tokens.css':'../../../frontend/src/shared/styles/tokens.css'};
routes['/gallery.html']='gallery.html';
routes['/review-manifest.json']='review-manifest.json';
// Exact screenshot names from the local capture manifest; no arbitrary filesystem route.
const manifest=JSON.parse(fs.readFileSync(path.join(__dirname,'review-manifest.json'),'utf8'));
for(const capture of manifest.captures){
 if(!/^review-[0-9]{2}-[a-z0-9-]+\.png$/.test(capture.file))throw new Error('Invalid screenshot filename');
 routes['/'+capture.file]=capture.file;
}
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.png':'image/png','.json':'application/json; charset=utf-8'};
http.createServer((req,res)=>{const file=routes[req.url.split('?')[0]];if(!file){res.writeHead(404);res.end();return;}
fs.readFile(path.resolve(__dirname,file),(err,data)=>{if(err){res.writeHead(500);res.end('Prototype asset unavailable');return;}res.writeHead(200,{'Content-Type':types[path.extname(file)],'Cache-Control':'no-store'});res.end(data);});}).listen(5180,'127.0.0.1',()=>console.log('P10 prototype: http://127.0.0.1:5180'));

