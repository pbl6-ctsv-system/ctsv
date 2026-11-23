import * as pdfjs from './node_modules/pdfjs-dist/build/pdf.mjs';
pdfjs.GlobalWorkerOptions.workerSrc='./node_modules/pdfjs-dist/build/pdf.worker.mjs';
const $=(s)=>document.querySelector(s),app=$('#app'),dialog=$('#dialog');
const names={cv:'Nguyễn Minh Thu',pp:'Trần Quốc Huy',pht:'Lê Hoàng Nam'},roleNames={cv:'Chuyên viên MGHP',pp:'Phó phòng',pht:'Phó Hiệu trưởng'};
const KEY='ctsv-p11-prototype-v1';
const fresh=()=>({role:'cv',filter:'all',notices:[],docs:{bb:{kind:'bb',title:'Biên bản xét MGHP Hội đồng',version:1,status:'draft',source:null,steps:[],versions:[],events:[]},qd:{kind:'qd',title:'Quyết định miễn giảm học phí',version:1,status:'draft',source:null,steps:[],versions:[],events:[]}}});
let state;try{state=JSON.parse(localStorage.getItem(KEY))||fresh()}catch{state=fresh()}
let selected=null,selecting=false,pageNo=1,zoom=1,rotation=0,tab='pdf',fault='none',pdfDocument,viewport,renderId=0;
const save=()=>localStorage.setItem(KEY,JSON.stringify(state));
const esc=(s)=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const now=()=>new Date().toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh',hour:'2-digit',minute:'2-digit',day:'2-digit',month:'2-digit',year:'numeric'});
const stepRoles=d=>[d.kind==='bb'?'cv':'pp','pht'];
const active=d=>d.steps.length;
const turn=d=>stepRoles(d)[active(d)];
const title=d=>d.kind==='bb'?'Biên bản':'Quyết định';
const statuses={draft:'Chưa có PDF',ready:'Sẵn sàng ký',waiting_first:'Chờ Phó phòng ký',waiting_next:'Chờ Phó Hiệu trưởng ký',completed:'Đã ký xong',refused:'Bị từ chối ký',unknown:'Chưa xác định kết quả ký',failed:'Chưa thể ký',download:'Chưa nhận được PDF đã ký',storage:'Chưa lưu xong kết quả',signing:'Đang xử lý ký'};
const badge=d=>`<span class="badge ${d.status==='completed'?'done':['refused','unknown','failed'].includes(d.status)?'error':['download','storage','signing'].includes(d.status)?'busy':'waiting'}">${esc(statuses[d.status])}</span>`;
const ready=d=>['ready','waiting_first','waiting_next'].includes(d.status)&&d.source&&(d.kind==='bb'||state.docs.bb.status==='completed');
const canSign=d=>ready(d)&&turn(d)===state.role&&(d.kind==='bb'||d.status!=='ready');
const stepCaption=(d,i)=>d.steps[i]?'Đã ký · '+d.steps[i].at:i!==active(d)?'Chưa thực hiện':({download:'Đang nhận PDF đã ký',storage:'Đang lưu PDF tại CTSV',unknown:'Chờ xác minh kết quả ký',failed:'Chưa gửi ký · cần khắc phục',signing:'Đang xử lý ký'}[d.status]||'Chưa ký');
const canReplace=d=>state.role==='cv'&&['draft','ready','refused'].includes(d.status);
const docRoute=(k)=>`#document/${k}`;
const event=(d,text)=>{d.events.unshift({text,at:now(),actor:roleNames[state.role]});save()};
function toast(message,error=false){const t=$('#toast');t.className=error?'error':'';t.innerHTML=`${esc(message)}<button aria-label="Đóng thông báo">×</button>`;t.hidden=false;t.querySelector('button').onclick=()=>t.hidden=true;}
function modal(html,setup){dialog.innerHTML=html;dialog.showModal();dialog.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>dialog.close());setup?.();}
function meta(){return `<div class="key-value"><span>Đợt xét</span><strong>MGHP học kỳ I · 2026–2027</strong><span>Dữ liệu Hội đồng</span><span>Đã chốt · Phiên bản 2</span><span>Lịch ký dự kiến</span><span>23–27/11/2026 <span class="muted">· kế hoạch mềm</span></span></div>`;}
function top(titleText,subtitle,action=''){return `<div class="breadcrumb"><a href="#list">MGHP</a> / Tài liệu ký số</div><div class="page-heading"><div><h1>${titleText}</h1><p class="muted">${subtitle}</p></div>${action}</div>`;}
function render(){
 save();$('#role').value=state.role;$('#actor').textContent=`${names[state.role]} · ${roleNames[state.role]}`;$('#notice-count').textContent=state.notices.filter(n=>!n.read&&n.role===state.role).length;
 const hash=location.hash||'#list';if(hash.startsWith('#document/'))renderDocument(hash.split('/')[1]);else if(hash==='#bundle')renderBundle();else if(hash==='#inbox')renderInbox();else renderList();
}
function renderList(){
 const docs=Object.values(state.docs);const waiting=docs.filter(canSign).length,done=docs.filter(d=>d.steps.some(s=>s.role===state.role)).length;
 app.innerHTML=top('Tài liệu ký số MGHP',state.role==='cv'?'Chuẩn bị, chuyển ký và thu bản đã ký của từng bộ văn bản.':'Xem đúng tài liệu được giao, ký bước của bạn và theo dõi kết quả.',state.role==='cv'?'<button class="primary" id="prepare">Chuẩn bị bộ văn bản</button>':'')+
 `<div class="stats"><div class="stat"><strong>${waiting}</strong><span>Đang chờ bạn ký</span></div><div class="stat"><strong>${docs.filter(d=>d.status==='completed').length}</strong><span>Văn bản đã ký xong</span></div><div class="stat"><strong>${docs.filter(d=>['unknown','failed','download','storage','refused'].includes(d.status)).length}</strong><span>Cần xử lý</span></div></div>
 <div class="card"><div class="toolbar"><div class="tabs" role="tablist" aria-label="Danh sách tài liệu">${[['all','Tất cả'],['mine','Chờ tôi ký'],['handled','Tôi đã xử lý']].map(([key,label])=>`<button role="tab" data-filter="${key}" aria-selected="${state.filter===key}">${label}${key==='mine'?` (${waiting})`:key==='handled'?` (${done})`:''}</button>`).join('')}</div><label>Học kỳ <select id="semester"><option>Học kỳ I · 2026–2027</option></select></label></div>
 <div class="table-scroll"><table><thead><tr><th>VĂN BẢN / ĐỢT XÉT</th><th>PHIÊN BẢN</th><th>TRẠNG THÁI</th><th>ĐANG CHỜ</th><th></th></tr></thead><tbody>${docs.filter(d=>state.filter==='mine'?canSign(d):state.filter==='handled'?d.steps.some(s=>s.role===state.role)||d.refusal?.role===state.role:true).filter(d=>state.role==='cv'||d.source&&(d.kind==='bb'?state.role==='pht'||state.docs.bb.status==='completed'&&state.docs.qd.status!=='draft':d.status!=='draft'&&d.status!=='ready')).map(d=>`<tr><td><strong>${d.title}</strong><div class="muted">MGHP học kỳ I · 2026–2027</div></td><td>Bản ${d.version}</td><td>${badge(d)}</td><td>${d.status==='completed'?'—':d.status==='draft'?'Chuẩn bị PDF':d.status==='refused'?'Chuyên viên sửa bản':roleNames[turn(d)]||'—'}</td><td><a href="${docRoute(d.kind)}"><button>Xem tài liệu</button></a></td></tr>`).join('')||'<tr><td colspan="5" class="empty">Chưa có tài liệu trong danh sách này.</td></tr>'}</tbody></table></div></div><p class="muted small">Mỗi người tự ký đúng bước. Quyết định đã ký xong chưa đồng nghĩa đã công bố.</p>`;
 $('#prepare')?.addEventListener('click',()=>modal(`<h2>Chuẩn bị bộ văn bản</h2><p class="muted">Chọn dữ liệu Hội đồng đã chốt. Tài liệu PDF được soạn ngoài CTSV.</p><div class="field"><label for="source">Bộ dữ liệu đã chốt</label><select id="source"><option>MGHP học kỳ I · 2026–2027 — Bản 2 đã chốt</option></select></div><div class="notice success">Nguồn đã đóng xét, họp Hội đồng và chốt dữ liệu. Không thay đổi danh sách ở bước ký.</div><div class="actions"><button data-close>Hủy</button><button class="primary" id="open-bundle">Mở bộ văn bản</button></div>`,()=>$('#open-bundle').onclick=()=>{dialog.close();location.hash='#bundle'}));
 app.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>{state.filter=b.dataset.filter;render()});
}
function renderBundle(){
 app.innerHTML=top('Bộ văn bản MGHP học kỳ I','Biên bản ký hoàn tất trước khi chuyển quyết định cho Phó phòng.','<a href="#list"><button>Về danh sách</button></a>')+`<div class="card">${meta()}</div><div class="flow"><span class="${state.docs.bb.status==='completed'?'done':''}">1. Biên bản · Chuyên viên → PHT</span><span>→</span><span>2. Quyết định · Phó phòng → PHT</span></div><div class="document-cards">${Object.values(state.docs).map(d=>`<section class="document-card card"><div class="row"><div class="doc-icon">PDF</div>${badge(d)}</div><h2>${d.title}</h2><p class="muted">${d.source?`Bản ${d.version} · ${esc(d.fileName||d.title+'.pdf')} · ${d.steps.length}/2 người đã ký`:'Upload PDF đã đối chiếu với bộ dữ liệu chốt.'}</p>${d.kind==='qd'&&state.docs.bb.status!=='completed'?'<p class="muted">Bạn có thể chuẩn bị PDF trước; chỉ gửi ký khi biên bản đã hoàn tất.</p>':''}<div class="row"><a href="${docRoute(d.kind)}"><button class="${!d.source?'primary':''}">${!d.source?'Chuẩn bị PDF':'Xem tài liệu'}</button></a></div></section>`).join('')}</div>`;
}
async function renderDocument(kind){
 const d=state.docs[kind];if(!d){location.hash='#list';return}
 if(state.role!=='cv'&&(!d.source||d.kind==='qd'&&['draft','ready'].includes(d.status)||state.role==='pp'&&d.kind==='bb'&&state.docs.bb.status!=='completed')){app.innerHTML=top('Tài liệu chưa được giao cho bạn','Quay lại danh sách tài liệu trong phạm vi của bạn.','<a href="#list"><button>Về danh sách</button></a>');return}
 app.innerHTML=top(d.title,`MGHP học kỳ I · 2026–2027 / Bản ${d.version}`,'<a href="#list"><button>Về danh sách</button></a>')+
 `<div class="row" style="justify-content:space-between;margin-bottom:18px">${badge(d)}<a href="#bundle">Xem bộ biên bản & quyết định →</a></div>
 <div class="section-tabs" role="tablist" aria-label="Chi tiết tài liệu"><button role="tab" data-tab="pdf" aria-selected="${tab==='pdf'}">Tài liệu & ký số</button><button role="tab" data-tab="history" aria-selected="${tab==='history'}">Lịch sử & phiên bản (${d.versions.length+1})</button></div>
 ${tab==='history'?history(d):!d.source?upload(d):detail(d)}
 <details class="test-lab"><summary>Kịch bản mô phỏng để review lỗi</summary><p>Chỉ điều khiển prototype, không phải cấu hình ứng dụng chính.</p><label>Kết quả ở lần ký tiếp theo <select id="fault"><option value="none">Thành công</option><option value="certificate">Chứng thư chưa sẵn sàng</option><option value="unknown">Timeout ký — chưa rõ kết quả</option><option value="download">CA đã ký — tải PDF lỗi</option><option value="storage">Đã nhận PDF — lưu CTSV lỗi</option><option value="stale">Phiên bản thay đổi khi xác nhận</option></select></label></details>`;
 app.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>{tab=b.dataset.tab;render()});$('#fault').value=fault;$('#fault').onchange=e=>fault=e.target.value;
 bindUpload(d);bindActions(d);
 if(d.source&&tab==='pdf')await loadPdf(d);
}
function upload(d){return `<div class="card"><h2>${d.status==='refused'?'Upload phiên bản đã sửa':'Chuẩn bị PDF '+title(d).toLowerCase()}</h2><p class="muted">PDF soạn bên ngoài, tối đa 10 MB. Kiểm tra nội dung khớp bộ dữ liệu Hội đồng đã chốt.</p>${meta()}<div class="upload-box"><label for="pdf-file">Chọn file ${title(d).toLowerCase()}</label><input type="file" id="pdf-file" accept="application/pdf"><p id="file-label">Chưa chọn file.</p></div><div class="field"><label for="doc-title">Tên tài liệu</label><input id="doc-title" value="${esc(d.title)}"></div><label class="check"><input type="checkbox" id="content-checked">Tôi đã đối chiếu PDF với dữ liệu Hội đồng đã chốt.</label><div class="row" style="margin-top:20px"><button class="primary" id="save-pdf">Lưu PDF & xem tài liệu</button><a href="#bundle"><button>Quay lại bộ văn bản</button></a></div></div>`;}
function detail(d){
 let message='';if(d.status==='refused')message=`<div class="notice error"><strong>${roleNames[d.refusal.role]} đã từ chối ký</strong><p>${esc(d.refusal.reason)}</p><span class="small">${d.refusal.at} · Chuyên viên cần chuẩn bị bản sửa. Bản và chữ ký cũ vẫn được giữ.</span></div>`;
 if(d.status==='unknown')message='<div class="notice error"><strong>Chưa xác định kết quả ký.</strong><p>CA chưa trả kết quả rõ ràng. Tạm dừng ký và chuyển bước; không bấm ký lại. Chuyên viên phối hợp kỹ thuật kiểm tra kết quả.</p></div>';
 if(['download','storage'].includes(d.status))message=`<div class="notice info"><strong>${d.status==='download'?'CA đã báo ký thành công, nhưng tải PDF chưa được.':'Đã nhận PDF, nhưng chưa lưu xong bên CTSV.'}</strong><p>Chưa chuyển người tiếp theo. Chỉ khôi phục việc tải/lưu kết quả; không gửi ký lại.</p></div>`;
 if(d.status==='failed')message='<div class="notice error"><strong>Chứng thư chưa sẵn sàng.</strong><p>Chưa gửi lệnh ký. Chuyên viên phối hợp CA DUT kiểm tra chứng thư của người ký.</p></div>';
 if(d.status==='completed')message=`<div class="notice success"><strong>Đã ký đủ hai người và lưu PDF bên CTSV.</strong><p>${d.kind==='bb'?'Chuyên viên có thể chuyển quyết định cho Phó phòng.':'Đây là kết quả ký. Công bố được thực hiện ở bước riêng.'}</p></div>`;
 return `${message}<div class="detail-grid"><section><div class="viewer"><div class="viewer-toolbar"><div class="row"><button id="prev" aria-label="Trang trước">‹</button><label>Trang <input id="page" type="number" value="${pageNo}" min="1" max="5" aria-label="Trang PDF"> / <span id="pages">5</span></label><button id="next" aria-label="Trang sau">›</button></div><div class="row"><button id="zoom-out" aria-label="Thu nhỏ">−</button><span id="zoom-label">${Math.round(zoom*100)}%</span><button id="zoom-in" aria-label="Phóng to">+</button><button id="rotate" aria-label="Xoay trang">↻</button>${canSign(d)?'<button id="choose">Chọn vị trí ký</button>':''}</div></div><div class="canvas-scroll"><div class="paper"><canvas id="pdf-canvas" aria-label="Nội dung PDF"></canvas><div id="overlay" class="overlay"></div></div></div><div class="viewer-caption" id="viewer-caption">${d.steps.length?'Bản đã lưu sau người ký '+d.steps.length:'Bản PDF chuẩn bị'} · Trang ${pageNo} · Chữ ký prototype là mô phỏng</div></div></section><aside>
 <div class="side-card"><h3>Tiến trình ký</h3>${stepRoles(d).map((role,i)=>{const finished=d.steps[i];return `<div class="step ${finished?'finished':i===active(d)?'active':''}"><span class="circle">${finished?'✓':i+1}</span><div><strong>${roleNames[role]}</strong><div class="muted">${stepCaption(d,i)}</div><div class="muted">${names[role]}</div></div></div>`}).join('')}
 ${canSign(d)?`<p class="hint" id="region-hint">Chọn trang, nhấn “Chọn vị trí ký” rồi kéo một khung trên PDF.</p><button class="primary" id="sign">Ký số</button>`:d.status==='ready'&&d.kind==='qd'&&state.role==='cv'?state.docs.bb.status==='completed'?'<button class="primary" id="send-pp">Gửi Phó phòng ký</button>':'<div class="notice">Chờ biên bản ký xong. Chưa đủ điều kiện gửi quyết định.</div>':!['completed','refused','unknown','failed','download','storage'].includes(d.status)?`<div class="notice">${canSign(d)?'':'Đang chờ '+roleNames[turn(d)]+' tự thực hiện ký.'}</div>`:''}
 ${state.role==='cv'&&['download','storage'].includes(d.status)?'<button class="primary" id="recover">Nhận & lưu lại kết quả</button>':''}
 ${state.role==='cv'&&d.status==='failed'?'<button class="primary" id="release">Cho phép người ký thử lại</button><p class="hint">Chỉ sau khi đã khắc phục chứng thư. Người ký phải xem, chọn vùng và tự xác nhận lại.</p>':''}
 ${canReplace(d)?'<button id="replace" style="width:100%;margin-top:10px">'+(d.status==='refused'?'Upload bản đã sửa':'Thay PDF chuẩn bị')+'</button>':''}
 </div><div class="side-card"><h3>PDF lưu tại CTSV</h3><p class="muted">${d.steps.length?`Đang xem bản sau ${d.steps.length} người ký. Giữ cả các bản trước.`:'PDF gốc đã lưu. Chữ ký được lưu thành từng bản riêng.'}</p><button id="download">Tải PDF đang xem</button><button id="view-history" class="text-button">Xem lịch sử & phiên bản</button></div>${d.kind==='qd'&&state.role!=='cv'&&state.docs.bb.status==='completed'?'<div class="side-card"><h3>Biên bản liên quan</h3><p class="muted">Đã ký xong · Cùng bộ dữ liệu</p><a href="#document/bb"><button>Xem biên bản</button></a></div>':''}
 </aside></div>`;
}
function history(d){return `<div class="card"><h2>Lịch sử & phiên bản</h2><p class="muted">Bản gốc, bản sau từng người ký và lý do từ chối luôn được giữ.</p><div class="history-item"><strong>Bản ${d.version} · hiện hành</strong><div class="muted">${d.title}</div><div class="row"><button data-preview="original">Xem PDF gốc</button>${d.steps.map((s,i)=>`<button data-preview="${i}">Sau ${roleNames[s.role]} ký</button>`).join('')}</div></div>${d.events.map(e=>`<div class="history-item"><strong>${esc(e.text)}</strong><div class="muted">${esc(e.actor)} · ${e.at}</div></div>`).join('')}${d.versions.map((v,i)=>`<div class="history-item"><strong>Bản ${v.version} · Đã có bản thay thế</strong><div class="muted">${v.refusal?esc(v.refusal.reason):'Bản PDF chuẩn bị trước đó'}</div><div class="row"><button data-old="${i}">Xem bản cũ</button></div></div>`).join('')}${d.events.length===0?'<p class="muted">Chưa phát sinh thao tác ký.</p>':''}</div>`;}
function bindUpload(d){let picked;
 $('#pdf-file')?.addEventListener('change',e=>{picked=e.target.files[0];$('#file-label').textContent=picked?`${picked.name} · ${(picked.size/1024).toFixed(1)} KB`:'Chưa chọn file.'});
 $('#save-pdf')?.addEventListener('click',async()=>{
  if(!picked||picked.type!=='application/pdf'||picked.size>10000000){toast('Chọn file PDF hợp lệ, không quá 10 MB.',true);return}
  if(!$('#content-checked').checked){toast('Vui lòng xác nhận đã đối chiếu PDF với dữ liệu Hội đồng.',true);return}
  const newTitle=$('#doc-title').value.trim();if(!newTitle){toast('Nhập tên tài liệu.',true);return}
  if(d._replacing){d.versions.unshift(d._previous);d.version++;delete d._replacing;delete d._previous}
  d.title=newTitle;d.fileName=picked.name;d.source=await new Promise(resolve=>{const f=new FileReader();f.onload=()=>resolve(f.result);f.readAsDataURL(picked)});d.originalSource=d.source;d.status='ready';d.steps=[];delete d.refusal;selected=null;pageNo=1;event(d,'Lưu PDF chuẩn bị · Bản '+d.version);toast('Đã lưu PDF. Bạn có thể xem và chọn vị trí ký.');render();
 });
}
function bindActions(d){
 $('#replace')?.addEventListener('click',()=>{d._previous=structuredClone(d);d._replacing=true;d.source=null;selected=null;save();render()});
 $('#sign')?.addEventListener('click',()=>confirmSign(d));
 $('#send-pp')?.addEventListener('click',()=>modal(`<h2>Gửi Phó phòng ký quyết định</h2><p>Biên bản đã ký xong và lưu tại CTSV. Quyết định bản ${d.version} sẽ được chuyển vào danh sách chờ của Phó phòng.</p><div class="actions"><button data-close>Hủy</button><button class="primary" id="confirm-send">Gửi Phó phòng</button></div>`,()=>$('#confirm-send').onclick=()=>{d.status='waiting_first';state.notices.unshift({role:'pp',text:'Quyết định MGHP học kỳ I đang chờ bạn ký.',kind:'qd',at:now()});event(d,'Chuyển Phó phòng ký');dialog.close();toast('Đã chuyển quyết định cho Phó phòng.');render()}));
 $('#recover')?.addEventListener('click',()=>completeSigning(d,true));
 $('#release')?.addEventListener('click',()=>modal('<h2>Cho phép người ký thử lại?</h2><p>Chỉ thực hiện sau khi đã kiểm tra và khắc phục chứng thư. Chưa có lệnh ký gửi sang CA ở lần lỗi này.</p><p>Hệ thống không tự ký. Người được giao phải mở tài liệu, chọn vùng và xác nhận lại.</p><div class="actions"><button data-close>Hủy</button><button id="confirm-release" class="primary">Cho phép thử lại</button></div>',()=>$('#confirm-release').onclick=()=>{dialog.close();d.status=d.steps.length?'waiting_next':d.kind==='bb'?'ready':'waiting_first';delete d.pendingSigner;delete d.pendingGeometry;selected=null;event(d,'Đã khắc phục lỗi trước dispatch; chờ người ký xác nhận lại');toast('Đã cho phép thử lại. Người ký phải tự xác nhận, chưa có chữ ký mới.');render()}));
 $('#download')?.addEventListener('click',()=>download(d.source,d.fileName||d.title+'.pdf'));
 $('#view-history')?.addEventListener('click',()=>{tab='history';render()});
 app.querySelectorAll('[data-preview]').forEach(b=>b.onclick=()=>previewHistory(d,b.dataset.preview));
 app.querySelectorAll('[data-old]').forEach(b=>b.onclick=()=>previewHistory(d.versions[Number(b.dataset.old)],'original',true));
}
function download(url,name){const a=document.createElement('a');a.href=url;a.download='MO-PHONG-'+name;a.click();toast('Đã tải PDF mô phỏng đang xem.');}
async function previewHistory(d,step,old=false){
 modal(`<h2>${old?'Phiên bản cũ · ':''}${d.title} — Bản ${d.version}</h2><p class="muted">${step==='original'?'PDF gốc':`Bản sau ${roleNames[d.steps[Number(step)].role]} ký (mô phỏng)`} · Chỉ xem, không sửa</p><canvas id="history-canvas" style="width:100%;border:1px solid var(--border)"></canvas><div class="actions"><button data-close>Đóng</button><button id="history-download">Tải PDF bản này</button></div>`,()=>$('#history-download').onclick=()=>download(d.source,d.title+'.pdf'));
 const source=step==='original'?d.originalSource||d.source:d.steps[Number(step)].source;
 $('#history-download').onclick=()=>download(source,d.title+'.pdf');
 const p=await pdfjs.getDocument({url:source}).promise,pg=await p.getPage(Math.min(2,p.numPages)),v=pg.getViewport({scale:.65}),canvas=$('#history-canvas');canvas.width=v.width;canvas.height=v.height;await pg.render({canvasContext:canvas.getContext('2d'),viewport:v}).promise;
}
async function loadPdf(d){
 const ticket=++renderId;try{pdfDocument=await pdfjs.getDocument({url:d.source}).promise;if(ticket!==renderId)return;pageNo=Math.max(1,Math.min(pageNo,pdfDocument.numPages));$('#pages').textContent=pdfDocument.numPages;$('#page').value=pageNo;
  $('#prev').onclick=()=>changePage(d,pageNo-1);$('#next').onclick=()=>changePage(d,pageNo+1);$('#page').onchange=e=>changePage(d,Number(e.target.value));
  $('#zoom-in').onclick=()=>{zoom=Math.min(1.6,zoom+.2);drawPage(d)};$('#zoom-out').onclick=()=>{zoom=Math.max(.5,zoom-.2);drawPage(d)};$('#rotate').onclick=()=>{rotation=(rotation+90)%360;drawPage(d)};
  $('#choose')?.addEventListener('click',()=>{selecting=true;$('#overlay').classList.add('selecting');$('#choose').textContent='Kéo để chọn vùng';$('#region-hint').textContent='Giữ chuột hoặc chạm và kéo một khung trên trang PDF.'});
  await drawPage(d);
 }catch(e){toast('Không mở được PDF. Hãy kiểm tra file và thử chọn lại.',true)}
}
function changePage(d,value){if(!Number.isInteger(value)||value<1||value>pdfDocument.numPages){toast('Chọn trang từ 1 đến '+pdfDocument.numPages+'.',true);$('#page').value=pageNo;return}pageNo=value;selecting=false;drawPage(d)}
async function drawPage(d){
 const ticket=++renderId;const page=await pdfDocument.getPage(pageNo);if(ticket!==renderId)return;
 const available=$('.canvas-scroll').clientWidth-48;const base=Math.min(1,available/page.getViewport({scale:1,rotation:page.rotate+rotation}).width);
 viewport=page.getViewport({scale:base*zoom,rotation:page.rotate+rotation});const ratio=window.devicePixelRatio||1,canvas=$('#pdf-canvas');canvas.width=Math.round(viewport.width*ratio);canvas.height=Math.round(viewport.height*ratio);canvas.style.width=viewport.width+'px';canvas.style.height=viewport.height+'px';$('.paper').style.width=viewport.width+'px';$('.paper').style.height=viewport.height+'px';
 $('#page').value=pageNo;$('#zoom-label').textContent=Math.round(zoom*100)+'%';$('#prev').disabled=pageNo===1;$('#next').disabled=pageNo===pdfDocument.numPages;
 await page.render({canvasContext:canvas.getContext('2d'),viewport,transform:ratio===1?undefined:[ratio,0,0,ratio,0,0]}).promise;if(ticket!==renderId)return;
 canvas.dataset.renderedPage=String(pageNo);
 $('#viewer-caption').textContent=`${d.steps.length?'Bản lưu sau '+d.steps.length+' người ký':'PDF chuẩn bị'} · Trang ${pageNo} · Chữ ký prototype là mô phỏng`;
 renderOverlay(d);bindDrag(d);
}
function viewportRect(g){const a=viewport.convertToViewportPoint(g.x,g.y),b=viewport.convertToViewportPoint(g.x+g.width,g.y+g.height);return {left:Math.min(a[0],b[0]),top:Math.min(a[1],b[1]),width:Math.abs(a[0]-b[0]),height:Math.abs(a[1]-b[1])};}
function renderOverlay(d){const layer=$('#overlay');layer.innerHTML='';layer.classList.toggle('selecting',selecting);
 if(selected&&selected.page===pageNo){const b=viewportRect(selected),box=document.createElement('div');box.className='signature-box';Object.assign(box.style,Object.fromEntries(Object.entries(b).map(([k,v])=>[k,v+'px'])));box.innerHTML='<span>Vị trí ký của bạn</span><i class="resize" aria-label="Đổi kích thước vùng ký"></i>';layer.append(box)}
 if($('#region-hint'))$('#region-hint').textContent=selected?`Đã chọn vùng ký ở trang ${selected.page}. Có thể kéo khung để di chuyển hoặc kéo góc để đổi kích thước.`:'Chọn trang và kéo một khung để đặt chữ ký.';
 if($('#choose'))$('#choose').textContent=selected?'Chọn lại vị trí ký':'Chọn vị trí ký';
}
function bindDrag(d){const layer=$('#overlay');let drag;
 const point=e=>{const rect=layer.getBoundingClientRect();return {x:Math.max(0,Math.min(viewport.width,e.clientX-rect.left)),y:Math.max(0,Math.min(viewport.height,e.clientY-rect.top))}};
 layer.onpointerdown=e=>{if(!canSign(d))return;const target=e.target.closest('.signature-box');if(!selecting&&!target)return;e.preventDefault();layer.setPointerCapture(e.pointerId);const p=point(e);drag={start:p,previous:selected?{...selected}:null,type:selecting?'new':e.target.closest('.resize')?'resize':'move',rect:selected?viewportRect(selected):null};};
 layer.onpointermove=e=>{if(!drag)return;const p=point(e),dx=p.x-drag.start.x,dy=p.y-drag.start.y;let box;
  if(drag.type==='new')box={left:Math.min(drag.start.x,p.x),top:Math.min(drag.start.y,p.y),width:Math.abs(dx),height:Math.abs(dy)};
  else if(drag.type==='move')box={...drag.rect,left:Math.max(0,Math.min(viewport.width-drag.rect.width,drag.rect.left+dx)),top:Math.max(0,Math.min(viewport.height-drag.rect.height,drag.rect.top+dy))};
  else box={...drag.rect,width:Math.max(20,Math.min(viewport.width-drag.rect.left,drag.rect.width+dx)),height:Math.max(15,Math.min(viewport.height-drag.rect.top,drag.rect.height+dy))};
  const a=viewport.convertToPdfPoint(box.left,box.top),b=viewport.convertToPdfPoint(box.left+box.width,box.top+box.height);selected={page:pageNo,x:Math.min(a[0],b[0]),y:Math.min(a[1],b[1]),width:Math.abs(a[0]-b[0]),height:Math.abs(a[1]-b[1])};renderOverlay(d);
 };
 layer.onpointerup=e=>{if(!drag)return;if(selected&&(selected.width<20||selected.height<15)){selected=drag.previous;toast('Vùng ký quá nhỏ. Hãy kéo khung lớn hơn.',true)}else if(selected)$('#toast').hidden=true;drag=null;selecting=false;renderOverlay(d)};
 layer.onpointercancel=()=>{if(drag)selected=drag.previous;drag=null;selecting=false;renderOverlay(d)};
}
function confirmSign(d){if(!selected){toast('Bạn chưa chọn vị trí ký. Nhấn “Chọn vị trí ký” rồi kéo một khung trên PDF.',true);$('#choose')?.focus();return}
 modal(`<h2>Xác nhận ký ${title(d).toLowerCase()}</h2><p class="muted">Bạn đang ký bước của chính mình. Không tự ký thay người tiếp theo.</p><dl><dt>Tài liệu</dt><dd>${esc(d.title)} · Bản ${d.version}</dd><dt>Người ký</dt><dd>${names[state.role]} · ${roleNames[state.role]}</dd><dt>Vị trí</dt><dd>Trang ${selected.page} / ${pdfDocument.numPages} · Vùng đã chọn trên PDF</dd></dl>${state.role==='pp'?'<label class="check"><input type="checkbox" id="pp-ack">Tôi đã xem và xác nhận đúng phiên bản quyết định đang hiển thị.</label><div id="ack-error" class="notice error" hidden>Vui lòng xác nhận đã xem đúng quyết định.</div>':''}<p class="hint">Chỉ hoàn tất khi PDF ký đã được kiểm tra và lưu tại CTSV.</p><div class="actions"><button data-close>Quay lại</button><button id="confirm-sign" class="primary">Xác nhận ký</button></div>`,()=>$('#confirm-sign').onclick=()=>{if(state.role==='pp'&&!$('#pp-ack').checked){$('#ack-error').hidden=false;return}dialog.close();completeSigning(d,false)});
}
async function completeSigning(d,recovery){
 const signer=recovery?d.pendingSigner:state.role,geometry=recovery?d.pendingGeometry:{...selected};if(!signer)return;
 if(fault==='stale'&&!recovery){fault='none';selected=null;toast('PDF đã thay đổi. Hãy đọc lại tài liệu và chọn lại vị trí ký; chưa gửi lệnh ký.',true);render();return}
 const failure=fault;fault='none';d.status='signing';d.pendingSigner=signer;d.pendingGeometry=geometry;save();
 modal(`<h2>${recovery?'Đang khôi phục PDF đã ký':'Đang xử lý bước ký của bạn'}</h2><p>Vui lòng chờ. Không cần bấm ký lại hoặc mở website CA.</p><div class="progress-line"><span></span></div><ol class="busy-steps"><li id="phase">${recovery?'Nhận lại PDF đã ký':'Kiểm tra quyền và chứng thư'}</li><li>Kiểm tra PDF và lưu tại CTSV</li><li>Ghi nhận kết quả bước ký</li></ol><p class="hint">${recovery?'Chỉ tải/lưu lại; không gửi lệnh ký mới.':'Một lần xác nhận chỉ ký cho chính bạn.'}</p>`);
 await new Promise(r=>setTimeout(r,500));if(failure==='certificate'&&!recovery){dialog.close();d.status='failed';event(d,'Chứng thư chưa sẵn sàng · chưa gửi ký');toast('Chưa gửi ký vì chứng thư chưa sẵn sàng.',true);render();return}
 $('#phase').textContent=recovery?'Đang nhận PDF kết quả đã có':'Đang gửi yêu cầu ký số';await new Promise(r=>setTimeout(r,500));
 if(failure==='unknown'&&!recovery){dialog.close();d.status='unknown';event(d,'Kết quả ký chưa xác định · dừng chuyển bước');selected=null;toast('Chưa xác định kết quả ký. Không ký lại; chuyên viên cần phối hợp kỹ thuật kiểm tra.',true);render();return}
 $('#phase').textContent='Đang nhận và lưu PDF đã ký tại CTSV';await new Promise(r=>setTimeout(r,500));
 if(['download','storage'].includes(failure)&&!recovery){dialog.close();d.status=failure;event(d,'CA báo thành công; chờ '+(failure==='download'?'nhận PDF':'lưu PDF'));selected=null;toast('Chưa hoàn tất bước ký. Kết quả chưa được lưu đủ bên CTSV.',true);render();return}
 // A visible demonstration stamp in a new PDF; this does not produce a digital signature.
 const document=await window.PDFLib.PDFDocument.load(d.source),signedPage=document.getPage(geometry.page-1);
 const stampName=names[signer].normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/Đ/g,'D').replace(/đ/g,'d');
 signedPage.drawRectangle({x:geometry.x,y:geometry.y,width:geometry.width,height:geometry.height,borderWidth:1,borderColor:window.PDFLib.rgb(.09,.42,.33),color:window.PDFLib.rgb(.94,.98,.96),opacity:.85});
 const fontSize=Math.min(10,geometry.width/14);signedPage.drawText('CHU KY MO PHONG',{x:geometry.x+5,y:geometry.y+geometry.height-14,size:fontSize,color:window.PDFLib.rgb(.09,.42,.33)});signedPage.drawText(stampName,{x:geometry.x+5,y:geometry.y+7,size:fontSize});
 d.source=await document.saveAsBase64({dataUri:true});
 d.steps.push({role:signer,geometry,at:now(),source:d.source});d.status=d.steps.length===2?'completed':'waiting_next';delete d.pendingSigner;delete d.pendingGeometry;event(d,(recovery?'Khôi phục và lưu PDF sau ':'Đã ký và lưu PDF sau ')+roleNames[signer]);
 if(d.steps.length===1)state.notices.unshift({role:'pht',text:`${d.title} đang chờ bạn ký bước 2. Bản người trước đã lưu tại CTSV.`,kind:d.kind,at:now()});else state.notices.unshift({role:'cv',text:`${d.title} đã ký đủ hai người và lưu tại CTSV.`,kind:d.kind,at:now()});
 save();dialog.close();selected=null;toast(d.status==='completed'?'Đã ký đủ hai người và lưu PDF.':'Đã ký và lưu PDF. Đang chờ Phó Hiệu trưởng.');render();
}
function renderInbox(){const notices=state.notices.filter(n=>n.role===state.role);app.innerHTML=top('Thông báo ký số','Thông báo đúng tài liệu và bước cần bạn xử lý.','<a href="#list"><button>Về tài liệu ký số</button></a>')+`<div class="card">${notices.map((n,i)=>`<div class="history-item"><strong>${esc(n.text)}</strong><div class="muted">${n.at}</div><a href="${docRoute(n.kind)}"><button>Xem tài liệu</button></a></div>`).join('')||'<p class="empty">Chưa có thông báo ký số.</p>'}</div>`;notices.forEach(n=>n.read=true);save();}
$('#role').onchange=e=>{state.role=e.target.value;selected=null;tab='pdf';location.hash='#list';render()};$('#reset').onclick=()=>modal('<h2>Đặt lại bản demo?</h2><p>Chỉ xóa dữ liệu mô phỏng trong trình duyệt này. Không tác động database CTSV.</p><div class="actions"><button data-close>Hủy</button><button id="confirm-reset" class="primary">Đặt lại</button></div>',()=>$('#confirm-reset').onclick=()=>{dialog.close();state=fresh();selected=null;tab='pdf';pageNo=1;zoom=1;rotation=0;location.hash='#list';render()});
window.addEventListener('hashchange',()=>{selected=null;tab='pdf';pageNo=1;rotation=0;render()});render();
// Read-only geometry hook for assertions; contains synthetic prototype data only.
window.p11Prototype={get state(){return structuredClone(state)},get geometry(){return selected?{...selected}:null},get viewport(){return viewport?.transform},get page(){return pageNo}};
