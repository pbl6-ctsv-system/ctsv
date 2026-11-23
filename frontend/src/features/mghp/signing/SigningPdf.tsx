import {useEffect,useRef,useState} from 'react';
import type {PointerEvent as ReactPointerEvent} from 'react';
import * as pdfjs from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';
import type {AuthClient} from '../../../shared/types/api';
import {geometryFromPixels,signingError} from './model.mjs';
import type {FileView,Geometry} from './types';
import styles from './SigningWorkspace.module.css';

pdfjs.GlobalWorkerOptions.workerSrc=workerUrl;
type Rect={x:number;y:number;width:number;height:number};
type Drag={start:{x:number;y:number};base:Rect|null;mode:'draw'|'move'|'resize'};
export function SigningPdf({client,file,selectable,selection,onSelection}:{client:AuthClient;file:FileView;selectable:boolean;selection:Geometry|null;onSelection:(v:Geometry|null)=>void}){
 const container=useRef<HTMLDivElement>(null),canvas=useRef<HTMLCanvasElement>(null);
 const [pdf,setPdf]=useState<pdfjs.PDFDocumentProxy|null>(null),[page,setPage]=useState(1),[zoom,setZoom]=useState(1),[rotation,setRotation]=useState(0),[width,setWidth]=useState(600);
 const [viewport,setViewport]=useState<pdfjs.PageViewport|null>(null),[rect,setRect]=useState<Rect|null>(null),[choosing,setChoosing]=useState(false),[loading,setLoading]=useState(true),[rendering,setRendering]=useState(false),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 const bytes=useRef<Uint8Array|null>(null),drag=useRef<Drag|null>(null),onSelectionRef=useRef(onSelection);onSelectionRef.current=onSelection;
 useEffect(()=>{const el=container.current;if(!el)return;const observer=new ResizeObserver(entries=>setWidth(Math.max(200,entries[0].contentRect.width-24)));observer.observe(el);return()=>observer.disconnect();},[]);
 useEffect(()=>{
  let live=true;const abort=new AbortController();let task:pdfjs.PDFDocumentLoadingTask|undefined;
  setLoading(true);setError('');setPdf(null);setViewport(null);setRect(null);setChoosing(false);bytes.current=null;onSelectionRef.current(null);
  if(canvas.current){canvas.current.width=0;canvas.current.height=0;delete canvas.current.dataset.renderedPage;delete canvas.current.dataset.renderedFile;}
  void client.request('/api/ctsv/tep/'+file.id+'/noi-dung',{signal:abort.signal}).then(async r=>{
   const data=new Uint8Array(await r.arrayBuffer());if(!live)return;bytes.current=data.slice();task=pdfjs.getDocument({data});const doc=await task.promise;
   if(!live){void task.destroy();return;}setPdf(doc);setPage(p=>Math.min(p,doc.numPages));setLoading(false);
  }).catch(e=>{if(live){setError(signingError(e));setLoading(false);}});
  return()=>{live=false;abort.abort();void task?.destroy();};
 },[client,file.id,retry]);
 useEffect(()=>{
  if(!pdf||!canvas.current)return;let live=true;let task:pdfjs.RenderTask|undefined;
  setRendering(true);setViewport(null);
  void pdf.getPage(page).then(async p=>{
   if(!live)return;const base=p.getViewport({scale:1,rotation:(p.rotate+rotation)%360});const v=p.getViewport({scale:Math.min(width/base.width,1.5)*zoom,rotation:(p.rotate+rotation)%360});
   const el=canvas.current!;const dpr=Math.min(window.devicePixelRatio||1,2);el.width=Math.round(v.width*dpr);el.height=Math.round(v.height*dpr);el.style.width=v.width+'px';el.style.height=v.height+'px';
   task=p.render({canvas:el,canvasContext:el.getContext('2d')!,viewport:v,transform:dpr===1?undefined:[dpr,0,0,dpr,0,0]});await task.promise;
   if(live){setViewport(v);setRendering(false);el.dataset.renderedPage=String(page);el.dataset.renderedFile=file.id;}
  }).catch(e=>{if(live&&e?.name!=='RenderingCancelledException'){setError('Không hiển thị được trang PDF. Thử mở lại PDF.');setRendering(false);}});
  return()=>{live=false;task?.cancel();};
 },[pdf,page,width,zoom,rotation]);
 useEffect(()=>{
  if(!selection||selection.page!==page||!viewport){setRect(null);return;}
  const points=[[selection.x,selection.y],[selection.x+selection.width,selection.y],[selection.x,selection.y+selection.height],[selection.x+selection.width,selection.y+selection.height]].map(([x,y])=>viewport.convertToViewportPoint(x,y));
  const xs=points.map(v=>v[0]),ys=points.map(v=>v[1]);setRect({x:Math.min(...xs),y:Math.min(...ys),width:Math.max(...xs)-Math.min(...xs),height:Math.max(...ys)-Math.min(...ys)});
 },[selection,viewport,page]);
 function changePage(next:number){onSelection(null);setRect(null);setPage(next);setError('');}
 function point(e:ReactPointerEvent<SVGSVGElement>){const b=e.currentTarget.getBoundingClientRect();return {x:Math.max(0,Math.min(viewport!.width,(e.clientX-b.left)*viewport!.width/b.width)),y:Math.max(0,Math.min(viewport!.height,(e.clientY-b.top)*viewport!.height/b.height))};}
 function down(e:ReactPointerEvent<SVGSVGElement>){if(!choosing||!selectable||!viewport||e.button!==0)return;e.preventDefault();e.currentTarget.setPointerCapture(e.pointerId);const mode=(e.target as Element).getAttribute('data-mode');drag.current={start:point(e),base:rect,mode:mode==='move'?'move':mode==='resize'?'resize':'draw'};if(drag.current.mode==='draw')setRect(null);setError('');}
 function move(e:ReactPointerEvent<SVGSVGElement>){const d=drag.current;if(!d||!viewport)return;const p=point(e),dx=p.x-d.start.x,dy=p.y-d.start.y,b=d.base;
  if(d.mode==='move'&&b)setRect({...b,x:Math.max(0,Math.min(viewport.width-b.width,b.x+dx)),y:Math.max(0,Math.min(viewport.height-b.height,b.y+dy))});
  else if(d.mode==='resize'&&b)setRect({...b,width:Math.max(8,Math.min(viewport.width-b.x,b.width+dx)),height:Math.max(8,Math.min(viewport.height-b.y,b.height+dy))});
  else setRect({x:Math.min(p.x,d.start.x),y:Math.min(p.y,d.start.y),width:Math.abs(dx),height:Math.abs(dy)});
 }
 function finish(){if(!drag.current||!viewport)return;drag.current=null;if(rect)try{onSelection(geometryFromPixels(viewport,page,rect));}catch(e){setRect(null);onSelection(null);setError((e as Error).message);}}
 async function download(){if(!bytes.current)return;const url=URL.createObjectURL(new Blob([bytes.current.slice().buffer],{type:'application/pdf'}));const a=document.createElement('a');a.href=url;a.download=file.fileName;a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);}
 return <section className={styles.viewer} aria-label="PDF đang xem" ref={container}>
  <div className={styles.pdfToolbar}>
   <div className={styles.row}><button disabled={!pdf||page<=1||rendering} onClick={()=>changePage(page-1)} aria-label="Trang PDF trước">←</button><label className={styles.pageInput}>Trang <input aria-label="Trang PDF" type="number" min={1} max={pdf?.numPages??1} disabled={!pdf||rendering} value={page} onChange={e=>{const n=Number(e.target.value);if(Number.isInteger(n)&&n>=1&&pdf&&n<=pdf.numPages)changePage(n);}}/> / {pdf?.numPages??'…'}</label><button disabled={!pdf||page>=(pdf?.numPages??1)||rendering} onClick={()=>changePage(page+1)} aria-label="Trang PDF sau">→</button></div>
   <div className={styles.row}><button disabled={!pdf||rendering||zoom<=.5} onClick={()=>setZoom(z=>Math.max(.5,z-.25))} aria-label="Thu nhỏ PDF">−</button><span>{Math.round(zoom*100)}%</span><button disabled={!pdf||rendering||zoom>=2} onClick={()=>setZoom(z=>Math.min(2,z+.25))} aria-label="Phóng to PDF">+</button><button disabled={!pdf||rendering} onClick={()=>setRotation(v=>(v+90)%360)}>Xoay trang</button><button disabled={!pdf} onClick={()=>void download()}>Tải PDF đang xem</button></div>
  </div>
  {selectable&&<div className={styles.choose}><button className={choosing?styles.primary:''} disabled={!viewport||rendering} onClick={()=>{setChoosing(v=>!v);setError('');}} aria-pressed={choosing}>Chọn vị trí ký</button><span>{selection?'Đã chọn vùng ký ở trang '+selection.page+'. Kéo khung để di chuyển; kéo góc để đổi kích thước.':choosing?'Giữ chuột hoặc chạm và kéo trên trang để tạo vùng ký.':'Chọn đúng trang, sau đó chọn vị trí ký.'}</span></div>}
  {error&&<div className={styles.error} role="alert">{error}<button onClick={()=>setRetry(v=>v+1)}>Thử mở lại PDF</button></div>}
  {(loading||rendering)&&<p className={styles.pdfLoading} role="status">{loading?'Đang mở PDF lưu tại CTSV…':'Đang hiển thị trang PDF…'}</p>}
  <div className={styles.pdfScroll}><div className={styles.paper} style={viewport?{width:viewport.width,height:viewport.height}:undefined}>
   <canvas ref={canvas} aria-label={'Nội dung PDF trang '+page}/>
   {viewport&&<svg className={styles.overlay} style={{pointerEvents:choosing&&selectable?'auto':'none',touchAction:choosing?'none':'auto'}} width={viewport.width} height={viewport.height} viewBox={`0 0 ${viewport.width} ${viewport.height}`} onPointerDown={down} onPointerMove={move} onPointerUp={finish} onPointerCancel={()=>{drag.current=null;setRect(null);onSelection(null);}} aria-label="Vùng chọn vị trí ký" role="img">
    {rect&&<g><rect data-mode="move" x={rect.x} y={rect.y} width={rect.width} height={rect.height} className={styles.signatureRect}/><text x={rect.x+8} y={rect.y+20} className={styles.signatureText}>Vị trí chữ ký</text><rect data-mode="resize" x={rect.x+rect.width-10} y={rect.y+rect.height-10} width={14} height={14} className={styles.handle}/></g>}
   </svg>}
  </div></div>
  {choosing&&selectable&&viewport&&<button className={styles.textButton} onClick={()=>onSelection(geometryFromPixels(viewport,page,{x:viewport.width*.45,y:viewport.height*.75,width:Math.min(180,viewport.width*.4),height:50}))}>Chọn vùng ký bằng bàn phím ở cuối trang</button>}
  <p className={styles.hint}>{file.fileName} · PDF từ kho CTSV. Vùng chọn chỉ là xem trước, chưa thay đổi tài liệu.</p>
 </section>;
}
