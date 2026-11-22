import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(import.meta.url);
const {yauzl,yazl}=require('./node_modules/playwright-core/lib/zipBundle.js');
const directory=process.argv[2];assert.ok(directory,'Owned evidence directory required');
const secret=/^(authorization|cookie|set-cookie|password|accesstoken|refreshtoken|client_secret|api_secret|secret)$/i;
function text(s){return s.replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g,'[REDACTED_JWT]').replace(/(Bearer\s+)[^\s"'\\]+/gi,'$1[REDACTED]').replace(/([?&](?:token|access_token|signature|sig)=)[^&"\s]+/gi,'$1[REDACTED]');}
function clean(v,k=''){
 if(k==='cookies'&&Array.isArray(v))return v.map(cookie=>cookie&&typeof cookie==='object'?{...clean(cookie),value:'[REDACTED]'}:{name:'redacted',value:'[REDACTED]'});
 if(secret.test(k))return '[REDACTED]';
 if(Array.isArray(v))return v.map(x=>clean(x,k));
 if(v&&typeof v==='object'){
  if(secret.test(v.name??''))return {...v,value:'[REDACTED]'};
  return Object.fromEntries(Object.entries(v).map(([key,value])=>[key,clean(value,key)]));
 }
 return typeof v==='string'?text(v):v;
}
for(const file of fs.readdirSync(directory).filter(n=>n.endsWith('-raw-trace.zip')||n.endsWith('-sanitized-trace.zip'))){
 const input=path.join(directory,file),finalOutput=input.replace('-raw-trace.zip','-sanitized-trace.zip'),output=finalOutput+'.tmp';
 const zip=await new Promise((resolve,reject)=>yauzl.open(input,{lazyEntries:true},(e,z)=>e?reject(e):resolve(z)));
 const result=new yazl.ZipFile();let entries=0;
 const completed=new Promise((resolve,reject)=>result.outputStream.pipe(fs.createWriteStream(output)).on('close',resolve).on('error',reject));
 await new Promise((resolve,reject)=>{
  zip.on('error',reject);zip.on('end',resolve);zip.on('entry',entry=>{
   if(entry.fileName.endsWith('/')){zip.readEntry();return;}
   zip.openReadStream(entry,(err,stream)=>{if(err){reject(err);return;}const chunks=[];stream.on('data',b=>chunks.push(b));stream.on('error',reject);stream.on('end',()=>{
    let buffer=Buffer.concat(chunks);const s=buffer.toString('utf8');
    if(!buffer.subarray(0,1000).includes(0)&&!entry.fileName.endsWith('.jpeg')&&!entry.fileName.endsWith('.png')){
     const rewritten=s.split('\n').map(line=>{if(!line.trim())return line;try{return JSON.stringify(clean(JSON.parse(line)));}catch{return text(line);}}).join('\n');
     buffer=Buffer.from(rewritten);
     assert.ok(!/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(rewritten),'Unredacted JWT in '+entry.fileName);
    }
    result.addBuffer(buffer,entry.fileName);entries++;zip.readEntry();
   });});
  });zip.readEntry();
 });result.end();await completed;fs.renameSync(output,finalOutput);console.log(file+' -> sanitized '+entries+' entries');
 // Remove only this owned raw trace after successful replacement; it must never be handed over.
 if(input!==finalOutput)fs.unlinkSync(input);
}
