import {useEffect,useMemo,useRef,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {Alert} from '../../../shared/ui/Alert';
import {createDecisionApi} from '../decisions/api.mjs';
import type {Round,Version} from '../decisions/types';
import {createSigningApi} from './api.mjs';
import {signingError,uploadResultUncertain,uploadFence} from './model.mjs';
import {SigningDialog} from './SigningDialog';
import type {DocumentView} from './types';
import styles from './SigningWorkspace.module.css';

type Bound=DocumentView&{signingRequestId:string|null;signingStatus:string|null};
export function SigningBundle({client,initialVersion,onOpen,onBack}:{client:AuthClient;initialVersion:string|null;onOpen:(doc:Bound)=>Promise<void>;onBack:()=>void}){
 const decision=useMemo(()=>createDecisionApi(client),[client]),api=useMemo(()=>createSigningApi(client),[client]);
 const [rounds,setRounds]=useState<Round[]>([]),[round,setRound]=useState(''),[version,setVersion]=useState<Version|null>(null),[sourceTag,setSourceTag]=useState(''),[stale,setStale]=useState(false),[docs,setDocs]=useState<Bound[]>([]),[more,setMore]=useState(false),[roundPage,setRoundPage]=useState(0);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[kind,setKind]=useState<'bien_ban'|'quyet_dinh'|null>(null),[file,setFile]=useState<File|null>(null),[uploaded,setUploaded]=useState<{id:string}|null>(null),[checked,setChecked]=useState(false),[title,setTitle]=useState('');
 const gate=useRef(false),live=useRef(true);
 const [uploadUnknown,setUploadUnknown]=useState(false);
 async function run(action:()=>Promise<void>){if(gate.current)return;gate.current=true;setBusy(true);setError('');try{await action();}catch(e){if(live.current){setError(signingError(e));if((e as {status?:number}).status===412)setStale(true);}}finally{gate.current=false;if(live.current)setBusy(false);}}
 async function load(id:string){const v=await decision.version(id);if(!live.current)return;if(v.data.status!=='locked'||!v.data.minutes)throw new Error('Bộ dữ liệu chưa chốt. Hoàn tất Hội đồng và chốt dữ liệu trước khi chuẩn bị PDF.');const source=v.data as Version;const loaded=await Promise.all(source.documents.map(l=>api.document(l.documentVersionId)));if(live.current){setVersion(source);setSourceTag(v.tag);setDocs(loaded.map(v=>v.data));setStale(false);}}
 useEffect(()=>{live.current=true;let cancelled=false;const abort=new AbortController();setBusy(true);void(async()=>{try{const rs=await decision.rounds(0,abort.signal);if(cancelled)return;setRounds(rs.data.items);setMore(rs.data.hasMore);if(initialVersion)await load(initialVersion);}catch(e){if(!cancelled)setError(signingError(e));}finally{if(!cancelled)setBusy(false);}})();return()=>{cancelled=true;live.current=false;abort.abort();};},[api,decision,initialVersion]);
 function current(type:string){const link=version?.documents.find(l=>l.documentType===type&&l.status==='current');return docs.find(d=>d.id===link?.documentVersionId);}
 async function save(){
  if(!version||!kind||!file||!checked||!title.trim())throw new Error('Chọn PDF, nhập tên và xác nhận đã đối chiếu với dữ liệu Hội đồng.');
  if(file.size>10000000||!file.name.toLowerCase().endsWith('.pdf'))throw new Error('Chọn PDF hợp lệ, tối đa 10 MB.');
  // Once upload is known, retain its file ID across a bind error; never upload again blindly.
  const fence=uploadFence(sessionStorage,version.id,kind);
  if(uploadUnknown||(!uploaded&&fence.pending())){setUploadUnknown(true);throw new Error('Chưa xác định kết quả tải PDF. Không tải lại; chuyên viên cần kiểm tra với bộ phận kỹ thuật.');}
  let f=uploaded;
  if(!f){fence.start();try{f=await api.upload(file);setUploaded(f);fence.finish();}catch(e){if(uploadResultUncertain(e)){setUploadUnknown(true);throw new Error('Chưa xác định PDF đã lưu hay chưa. Không tải lại. Giữ nguyên màn hình và liên hệ kỹ thuật kiểm tra lượt tải này.');}fence.finish();throw e;}}
  if(!f)throw new Error('Chưa nhận được thông tin PDF đã lưu. Không gửi lại lượt tải.');
  const bb=kind==='bien_ban';
  // Bind with the version the coordinator actually reviewed. Never silently replace
  // an intervening upload by fetching a new ETag immediately before the write.
  const saved=await api.bind(bb,bb?version.minutes!.id:version.id,{fileId:f.id,title:title.trim(),evidence:'Chuyên viên xác nhận đã đối chiếu PDF với dữ liệu Hội đồng đã chốt.'},bb?version.minutes!.etag:sourceTag);
  await load(version.id);setKind(null);setFile(null);setUploaded(null);await onOpen(saved.data);
 }
 return <div className={styles.page}>
  <div className={styles.heading}><div><button className={styles.textButton} onClick={onBack} disabled={busy}>← Danh sách tài liệu</button><h1>Chuẩn bị bộ văn bản</h1><p className={styles.muted}>Chọn bộ dữ liệu Hội đồng đã chốt. PDF được soạn ngoài CTSV.</p></div></div>
  {error&&<><Alert>{error}</Alert><p className={styles.error} role="alert">{error}</p></>}
  {busy&&<p role="status">Đang đọc hoặc lưu bộ văn bản…</p>}
  <section className={styles.card}><label>Đợt xét<select aria-label="Đợt xét để chuẩn bị văn bản" value={round} disabled={busy} onChange={e=>{const id=e.target.value;setRound(id);setVersion(null);setDocs([]);if(id)void run(async()=>{const v=await decision.list(id);const locked=v.data.items.find((v:Version)=>v.status==='locked');if(!locked)throw new Error('Đợt chưa có bộ dữ liệu đã chốt. Hoàn tất tại Hội đồng & quyết định.');await load(locked.id);});}}><option value="">Chọn đợt xét</option>{rounds.map(r=><option key={r.id} value={r.id}>{r.name??r.code}</option>)}</select></label>{more&&<button disabled={busy} onClick={()=>void run(async()=>{const r=await decision.rounds(roundPage+1);setRounds(v=>[...v,...r.data.items]);setRoundPage(p=>p+1);setMore(r.data.hasMore);})}>Thêm đợt để chọn</button>}
   {version&&<p className={styles.notice}><strong>{version.code} · Dữ liệu Hội đồng bản {version.revision} đã chốt.</strong> Không sửa danh sách hoặc mức hưởng tại bước ký.</p>}
  </section>
  {version?<><div className={styles.flow}><span>1. Biên bản · Chuyên viên → PHT</span><span>→</span><span>2. Quyết định · Phó phòng → PHT</span></div><div className={styles.bundleGrid}>{(['bien_ban','quyet_dinh'] as const).map(k=>{const doc=current(k),caption=k==='bien_ban'?'Biên bản':'Quyết định';const replace=!doc?.signingStatus||doc.signingStatus==='prepared';return <section className={styles.card} key={k}><h2>{caption} xét MGHP</h2><p className={styles.muted}>{k==='bien_ban'?'Chuyên viên ký đầu, sau đó Phó Hiệu trưởng.':'Biên bản phải hoàn tất trước khi gửi Phó phòng ký.'}</p>{doc?<><p><strong>{doc.title}</strong><br/>Bản PDF {doc.version} · {doc.file.fileName}</p><button className={styles.primary} disabled={busy} onClick={()=>void run(()=>onOpen(doc))}>Xem {caption.toLowerCase()}</button></>:<p className={styles.empty}>Chưa có PDF của {caption.toLowerCase()}.</p>}{replace&&<button disabled={busy} onClick={()=>{setKind(k);setTitle(doc?.title??caption+' xét MGHP · '+version.code);setFile(null);setUploaded(null);setChecked(false);setError('');}}>{doc?'Thay PDF chuẩn bị':'Tải PDF '+caption.toLowerCase()+' lên'}</button>}</section>;})}</div><section className={styles.card}><h2>Lịch sử PDF của bộ văn bản</h2>{docs.length?docs.map(d=><div className={styles.history} key={d.id}><span><strong>{d.title} · Bản {d.version}</strong><small>{current(version.documents.find(l=>l.documentVersionId===d.id)?.documentType??'')?.id===d.id?'Hiện hành':'Bản trước, chỉ xem'}</small></span><button disabled={busy} onClick={()=>void run(()=>onOpen(d))}>Xem bản PDF {d.version}</button></div>):<p>Chưa có PDF được tải lên.</p>}</section></>:<p className={styles.empty}>Chọn đợt để bắt đầu. <a href="/mghp/hoi-dong-quyet-dinh">Đến Hội đồng &amp; quyết định</a></p>}
  {kind&&<SigningDialog title={(current(kind)?'Thay PDF chuẩn bị':'Tải PDF lên')+' · '+(kind==='bien_ban'?'Biên bản':'Quyết định')} busy={busy} onClose={()=>setKind(null)}><form onSubmit={e=>{e.preventDefault();void run(save);}}>
   {error&&<p className={styles.error} role="alert">{error}</p>}{uploadUnknown&&<p className={styles.warning}>Kết quả tải PDF chưa rõ. Không gửi lại lượt tải này; cần kỹ thuật đối chiếu kho tệp/nhật ký.</p>}{stale&&<button type="button" disabled={busy} onClick={()=>void run(async()=>{await load(version!.id);setChecked(false);})}>Đọc lại bộ văn bản để đối chiếu</button>}<label>Tên tài liệu<input required maxLength={512} aria-label="Tên tài liệu" value={title} disabled={busy} onChange={e=>setTitle(e.target.value)}/></label><label>File PDF<input type="file" accept="application/pdf,.pdf" required={!uploaded} disabled={busy||!!uploaded||uploadUnknown} onChange={e=>{setFile(e.target.files?.[0]??null);setUploaded(null);}}/></label><p className={styles.hint}>{uploaded?'PDF đã tải lên CTSV, tiếp tục gắn vào đúng bộ; không tải lại.':file?.name??'Chọn PDF, tối đa 10 MB.'}</p><label className={styles.check}><input type="checkbox" checked={checked} disabled={busy} onChange={e=>setChecked(e.target.checked)}/>Tôi đã đối chiếu PDF với đúng dữ liệu Hội đồng đã chốt.</label><div className={styles.actions}><button type="button" disabled={busy} onClick={()=>setKind(null)}>Hủy</button><button type="submit" className={styles.primary} disabled={busy||stale||uploadUnknown||!checked||!file}>{busy?'Đang lưu PDF…':'Lưu PDF'}</button></div>
  </form></SigningDialog>}
 </div>;
}
