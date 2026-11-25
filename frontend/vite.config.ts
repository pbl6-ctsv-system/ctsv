import {defineConfig} from 'vite';
import {readFileSync} from 'node:fs';
import {createPdfJsAssetsPlugin} from './tools/pdfjs-assets.mjs';
export default defineConfig({
 plugins:[createPdfJsAssetsPlugin()],
 server:{host:'127.0.0.1',https:process.env.P04_TLS_CERT&&process.env.P04_TLS_KEY?{
  cert:readFileSync(process.env.P04_TLS_CERT),key:readFileSync(process.env.P04_TLS_KEY)
 }:undefined,proxy:{'/api':{target:process.env.P04_BACKEND_URL??'https://127.0.0.1:8443',secure:true,changeOrigin:false}}},
 build:{outDir:'dist'}
});
