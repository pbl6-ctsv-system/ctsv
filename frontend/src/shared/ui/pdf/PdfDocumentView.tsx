import {useEffect,useRef,useState} from 'react';
import type {PointerEvent as ReactPointerEvent} from 'react';
import * as pdfjs from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import {errorMessage} from '../../api/error-message';
import {readPdfBytes,pdfErrorText} from './pdf-source.mjs';
import {pdfDocumentOptions} from './pdf-options.mjs';
import {geometryFromPixels,pixelsFromGeometry,keyboardSelectionRect} from './geometry.mjs';
import type {PdfViewerProps} from './types';
import styles from './PdfViewer.module.css';

pdfjs.GlobalWorkerOptions.workerSrc=workerUrl;
type Rect={x:number;y:number;width:number;height:number};
type Drag={start:{x:number;y:number};base:Rect|null;mode:'draw'|'move'|'resize'};

export default function PdfDocumentView({client,fileId,title,fileName,selectable=false,selection=null,onSelectionChange}:PdfViewerProps){
 const container=useRef<HTMLElement>(null),canvas=useRef<HTMLCanvasElement>(null);
 const [document,setDocument]=useState<{id:string;pdf:pdfjs.PDFDocumentProxy}|null>(null);
 const [page,setPage]=useState(1),[zoom,setZoom]=useState(1),[rotation,setRotation]=useState(0),[width,setWidth]=useState(600);
 const [view,setView]=useState<{key:string;viewport:pdfjs.PageViewport}|null>(null);
 const [rect,setRect]=useState<Rect|null>(null),[choosing,setChoosing]=useState(false),[error,setError]=useState<{id:string;message:string}|null>(null),[retry,setRetry]=useState(0),[selectionError,setSelectionError]=useState('');
 const bytes=useRef<{id:string;data:Uint8Array}|null>(null),drag=useRef<Drag|null>(null),rectRef=useRef<Rect|null>(null);
 const onSelectionRef=useRef(onSelectionChange);onSelectionRef.current=onSelectionChange;
 const renderTask=useRef<pdfjs.RenderTask|null>(null);
 const pdf=document?.id===fileId?document.pdf:null;
 const renderKey=[fileId,page,width,zoom,rotation,retry].join(':');
 const ready=!!pdf&&view?.key===renderKey,viewport=ready?view.viewport:null;
 const message=error?.id===fileId?error.message:'';
 const canSelect=selectable&&!!onSelectionChange;

 useEffect(()=>{
  const el=container.current;if(!el)return;
  const observer=new ResizeObserver(entries=>setWidth(Math.max(160,entries[0].contentRect.width-24)));
  observer.observe(el);return()=>observer.disconnect();
 },[]);
 useEffect(()=>{
  let live=true;const abort=new AbortController();let task:pdfjs.PDFDocumentLoadingTask|undefined;
  setDocument(null);setView(null);setError(null);setRect(null);setChoosing(false);setSelectionError('');
  rectRef.current=null;drag.current=null;bytes.current=null;onSelectionRef.current?.(null);
  if(canvas.current){delete canvas.current.dataset.renderedPage;delete canvas.current.dataset.renderedFile;}
  void (async()=>{
   const data=await readPdfBytes(client,fileId,abort.signal);if(!live)return;
   // PDF.js transfers its input buffer to the worker; retain a separate copy for download.
   bytes.current={id:fileId,data:data.slice()};
   const resources=new URL(import.meta.env.BASE_URL+'pdfjs/'+pdfjs.version+'/',window.location.origin).href;
   task=pdfjs.getDocument(pdfDocumentOptions(data,resources));const loaded=await task.promise;
   if(!live)return;
   setPage(previous=>Math.max(1,Math.min(previous,loaded.numPages)));setDocument({id:fileId,pdf:loaded});
  })().catch(e=>{if(live){bytes.current=null;setError({id:fileId,message:pdfErrorText(e)??errorMessage(e)});}});
  return()=>{live=false;abort.abort();renderTask.current?.cancel();void task?.destroy().catch(()=>{});};
 },[client,fileId,retry]);
 useEffect(()=>{
  if(!pdf||!canvas.current)return;
  let live=true;let task:pdfjs.RenderTask|undefined;
  const previous=renderTask.current;previous?.cancel();setView(null);
  delete canvas.current.dataset.renderedPage;delete canvas.current.dataset.renderedFile;
  void (async()=>{
   // Wait for cancellation before reusing this canvas (StrictMode, rapid page/zoom/file changes).
   await previous?.promise.catch(()=>{});if(!live)return;
   const p=await pdf.getPage(page);if(!live)return;
   const angle=(p.rotate+rotation)%360,base=p.getViewport({scale:1,rotation:angle});
   const v=p.getViewport({scale:Math.min(width/base.width,1.5)*zoom,rotation:angle});
   const el=canvas.current!;const dpr=Math.min(window.devicePixelRatio||1,2);
   el.width=Math.round(v.width*dpr);el.height=Math.round(v.height*dpr);el.style.width=v.width+'px';el.style.height=v.height+'px';
   task=p.render({canvas:el,canvasContext:el.getContext('2d')!,viewport:v,transform:dpr===1?undefined:[dpr,0,0,dpr,0,0]});renderTask.current=task;
   await task.promise;if(!live)return;
   el.dataset.renderedPage=String(page);el.dataset.renderedFile=fileId;setView({key:renderKey,viewport:v});
  })().catch(e=>{if(live&&e?.name!=='RenderingCancelledException')setError({id:fileId,message:'Không hiển thị được trang PDF. Vui lòng thử mở lại.'});});
  return()=>{live=false;task?.cancel();};
 },[pdf,fileId,page,width,zoom,rotation,renderKey]);
 useEffect(()=>{
  const next=selection&&selection.page===page&&viewport?pixelsFromGeometry(viewport,selection):null;
  rectRef.current=next;setRect(next);
 },[selection,viewport,page,canSelect]);
 // A caller may temporarily disable controls while submitting. It owns the selected value;
 // clearing it here would close its confirmation dialog before an HTTP error can be handled.
 useEffect(()=>{if(!canSelect){setChoosing(false);drag.current=null;}},[canSelect]);

 function changePage(next:number){onSelectionRef.current?.(null);setRect(null);rectRef.current=null;setPage(next);setError(null);setSelectionError('');}
 function point(e:ReactPointerEvent<SVGSVGElement>){
  const b=e.currentTarget.getBoundingClientRect();
  return {x:Math.max(0,Math.min(viewport!.width,(e.clientX-b.left)*viewport!.width/b.width)),y:Math.max(0,Math.min(viewport!.height,(e.clientY-b.top)*viewport!.height/b.height))};
 }
 function updateRect(next:Rect|null){rectRef.current=next;setRect(next);}
 function down(e:ReactPointerEvent<SVGSVGElement>){
  if(!choosing||!canSelect||!viewport||e.button!==0)return;
  e.preventDefault();e.currentTarget.setPointerCapture(e.pointerId);
  const mode=(e.target as Element).getAttribute('data-mode');
  drag.current={start:point(e),base:rectRef.current,mode:mode==='move'?'move':mode==='resize'?'resize':'draw'};
  if(drag.current.mode==='draw'){updateRect(null);onSelectionRef.current?.(null);}setSelectionError('');
 }
 function move(e:ReactPointerEvent<SVGSVGElement>){
  const d=drag.current;if(!d||!viewport)return;
  const p=point(e),dx=p.x-d.start.x,dy=p.y-d.start.y,b=d.base;
  if(d.mode==='move'&&b)updateRect({...b,x:Math.max(0,Math.min(viewport.width-b.width,b.x+dx)),y:Math.max(0,Math.min(viewport.height-b.height,b.y+dy))});
  else if(d.mode==='resize'&&b)updateRect({...b,width:Math.max(8,Math.min(viewport.width-b.x,b.width+dx)),height:Math.max(8,Math.min(viewport.height-b.y,b.height+dy))});
  else updateRect({x:Math.min(p.x,d.start.x),y:Math.min(p.y,d.start.y),width:Math.abs(dx),height:Math.abs(dy)});
 }
 function finish(e:ReactPointerEvent<SVGSVGElement>){
  if(!drag.current||!viewport)return;move(e);drag.current=null;
  if(rectRef.current)try{onSelectionRef.current?.(geometryFromPixels(viewport,page,rectRef.current));}
  catch(e){updateRect(null);onSelectionRef.current?.(null);setSelectionError((e as Error).message);}
 }
 function download(){
  if(!pdf||bytes.current?.id!==fileId)return;
  const url=URL.createObjectURL(new Blob([bytes.current.data.slice().buffer],{type:'application/pdf'}));
  const a=window.document.createElement('a');a.href=url;a.download=fileName||title.replace(/\.pdf$/i,'')+'.pdf';a.click();
  setTimeout(()=>URL.revokeObjectURL(url),30000);
 }
 function selectWithKeyboard(){
  if(!viewport)return;
  try{onSelectionRef.current?.(geometryFromPixels(viewport,page,keyboardSelectionRect(viewport)));setSelectionError('');}
  catch(e){onSelectionRef.current?.(null);setSelectionError((e as Error).message);}
 }

 return <section className={styles.viewer} aria-label="PDF đang xem" aria-busy={!ready&&!message} ref={container}>
  <strong className={styles.title}>{title}</strong>
  <div className={styles.toolbar}>
   <div className={styles.row}>
    <button type="button" disabled={!ready||page<=1} onClick={()=>changePage(page-1)} aria-label="Trang PDF trước">←</button>
    <label className={styles.pageInput}>Trang <input aria-label="Trang PDF" type="number" min={1} max={pdf?.numPages??1} disabled={!ready} value={page} onChange={e=>{const n=Number(e.target.value);if(Number.isInteger(n)&&n>=1&&pdf&&n<=pdf.numPages)changePage(n);}}/> / {pdf?.numPages??'…'}</label>
    <button type="button" disabled={!ready||page>=(pdf?.numPages??1)} onClick={()=>changePage(page+1)} aria-label="Trang PDF sau">→</button>
   </div>
   <div className={styles.row}>
    <button type="button" disabled={!ready||zoom<=.5} onClick={()=>setZoom(z=>Math.max(.5,z-.25))} aria-label="Thu nhỏ PDF">−</button><span>{Math.round(zoom*100)}%</span>
    <button type="button" disabled={!ready||zoom>=2} onClick={()=>setZoom(z=>Math.min(2,z+.25))} aria-label="Phóng to PDF">+</button>
    <button type="button" disabled={!ready} onClick={()=>setRotation(v=>(v+90)%360)}>Xoay trang</button>
    <button type="button" disabled={!pdf||bytes.current?.id!==fileId} onClick={download}>Tải PDF đang xem</button>
   </div>
  </div>
  {canSelect&&<div className={styles.choose}>
   <button type="button" className={choosing?styles.primary:undefined} disabled={!ready} onClick={()=>{setChoosing(v=>!v);setSelectionError('');}} aria-pressed={choosing}>Chọn vị trí ký</button>
   <span>{selection?'Đã chọn vùng ký ở trang '+selection.page+'. Kéo khung để di chuyển; kéo góc để đổi kích thước.':choosing?'Giữ chuột hoặc chạm và kéo trên trang để tạo vùng ký.':'Chọn đúng trang, sau đó chọn vị trí ký.'}</span>
  </div>}
  {selectionError&&<p className={styles.error} role="alert">{selectionError}</p>}
  {message?<div className={styles.error} role="alert"><p>Không mở được PDF: {message}</p><button type="button" onClick={()=>setRetry(v=>v+1)}>Thử mở lại PDF</button></div>:!ready&&<p className={styles.loading} role="status">{pdf?'Đang hiển thị trang PDF…':'Đang mở PDF lưu tại CTSV…'}</p>}
  <div className={styles.scroll}>
   <div className={styles.paper} style={viewport?{width:viewport.width,height:viewport.height}:undefined}>
    <canvas ref={canvas} hidden={!ready||!!message} aria-label={'Nội dung PDF trang '+page}/>
    {canSelect&&viewport&&!message&&<svg className={styles.overlay} style={{pointerEvents:choosing?'auto':'none',touchAction:choosing?'none':'auto'}} width={viewport.width} height={viewport.height} viewBox={`0 0 ${viewport.width} ${viewport.height}`} onPointerDown={down} onPointerMove={move} onPointerUp={finish} onPointerCancel={()=>{drag.current=null;updateRect(null);onSelectionRef.current?.(null);}} aria-label="Vùng chọn vị trí ký" role="img">
     {rect&&<g><rect data-mode="move" x={rect.x} y={rect.y} width={rect.width} height={rect.height} className={styles.signatureRect}/><text x={rect.x+8} y={rect.y+20} className={styles.signatureText}>Vị trí chữ ký</text><rect data-mode="resize" x={rect.x+rect.width-10} y={rect.y+rect.height-10} width={14} height={14} className={styles.handle}/></g>}
    </svg>}
   </div>
  </div>
  {choosing&&canSelect&&viewport&&<button type="button" className={styles.keyboard} onClick={selectWithKeyboard}>Chọn vùng ký bằng bàn phím ở cuối trang</button>}
  <p className={styles.hint}>{fileName||title} · PDF từ kho CTSV.{canSelect?' Vùng chọn chỉ là xem trước, chưa thay đổi tài liệu.':''}</p>
 </section>;
}
