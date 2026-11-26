import {initial,roles,date,stamp} from './data.mjs';
import {frame,incidentList,incidentDetail,certificateList,certificateDetail,signerView,esc,btn,badge} from './views.mjs';
const key='ctsv.prototype.signing-admin.v1',root=document.querySelector('#app'),dialog=document.querySelector('#dialog');
let s;try{s=JSON.parse(localStorage.getItem(key));}catch{}if(!s?.incidents||s.revision!==2)s=initial();
let busy='',modal=null,focusBack=null,toastTimer;
const save=()=>localStorage.setItem(key,JSON.stringify(s));
const current=()=>s.incidents.find(i=>i.id===s.incident),cert=()=>s.certificates.find(c=>c.id===s.certificate);
function at(){s.ticks=(s.ticks??0)+1;return new Date(Date.parse(stamp)+s.ticks*60000).toISOString();}
function addHistory(i,text){i.history.push({text,at:at()});}
function toast(text,error=false){clearTimeout(toastTimer);document.querySelector('#feedback').innerHTML=`<div class="toast ${error?'error':''}" role="${error?'alert':'status'}"><span>${esc(text)}</span>${btn('×','dismiss-toast','',`aria-label="Đóng thông báo"`)}</div>`;toastTimer=setTimeout(()=>document.querySelector('#feedback').replaceChildren(),6500);}
function render(){
 let content;
 if(s.role!=='admin')content=signerView(s);
 else if(s.page==='certificates')content=cert()?certificateDetail(s,cert()):certificateList(s);
 else content=current()?incidentDetail(s,current()):incidentList(s);
 root.innerHTML=frame(s,content,busy);
 if(busy)root.querySelectorAll('button,select,input').forEach(el=>el.disabled=true);
 const hash=s.role!=='admin'?'user/'+(s.incident??''):s.page==='certificates'?'certificates/'+(s.certificate??''):s.tab==='files'?'files':'incidents/'+(s.incident??'');
 history.replaceState(null,'','#'+hash);save();
}
function go(fn){closeModal(false);fn();render();window.scrollTo({top:0,behavior:'instant'});}
async function simulate(label,work){if(busy)return;busy=label;render();await new Promise(r=>setTimeout(r,700));try{await work();}finally{busy='';render();}}
function openModal(kind,id){
 modal={kind,id};focusBack=document.activeElement?.dataset?{action:document.activeElement.dataset.action,id:document.activeElement.dataset.id}:null;
 const i=s.incidents.find(x=>x.id===id),c=s.certificates.find(x=>x.id===id),f=s.files.find(x=>x.id===id);
 let title='',sub='',body='',actions='';
 const cancel=btn('Quay lại','close-modal');
 if(kind==='copy'){
  title='Thông tin gửi hỗ trợ';sub='Admin có thể sao chép và gửi cho CA hoặc kỹ thuật qua ứng dụng khác.';
  body=`<textarea class="copy-text" aria-label="Thông tin hỗ trợ" readonly>${esc(report(i))}</textarea><p class="small muted">Đã lược bỏ mật khẩu, token và URL có quyền. Không tự kết luận bên chịu trách nhiệm.</p>`;
  actions=cancel+btn('Sao chép nội dung','copy-text','primary');
 }else if(kind==='note'||kind==='cert-note'){
  title=kind==='note'?'Thêm ghi chú xử lý':'Ghi nhận liên hệ CA';sub=kind==='note'?i.title:c.email;
  body=`<form id="note-form"><label class="field">Nội dung<textarea name="note" aria-label="Nội dung ghi chú" required maxlength="2000" placeholder="Đã kiểm tra gì, trao đổi với ai, đang chờ xử lý gì…"></textarea></label><label class="field">Tham chiếu trao đổi (không bắt buộc)<input name="reference" aria-label="Tham chiếu trao đổi" maxlength="200" placeholder="Ví dụ: CA xác nhận đang sửa storage"></label><p class="small muted">Ghi chú lưu vào lịch sử; không tự làm tài liệu hoàn tất hoặc chứng thư có hiệu lực.</p><div class="actions">${cancel}<button type="submit" class="primary">Lưu ghi chú</button></div></form>`;
 }else if(kind==='delete'){
  const issue=s.incidents.find(x=>x.id===f.incident),complete=issue.cloud&&issue.sql;
  title='Xóa bản PDF lưu chờ?';sub='Chỉ tác động bản trên server CTSV, không xóa PDF trên Cloudinary hoặc lịch sử ký.';
  body=`<p><strong>${esc(f.name)}</strong></p><p class="small muted">${f.size} · ${date(f.saved)}</p><div class="notice ${complete?'':'warn'}"><strong>${complete?'Cloudinary và SQL đã lưu đủ.':'Cloudinary / SQL chưa lưu đủ.'}</strong>${complete?'Có thể dọn bản chờ. PDF và lịch sử đã lưu vẫn giữ nguyên.':'Xóa lúc này có thể mất bản phục hồi đang giữ trên server. Bạn vẫn có quyền xóa theo cơ chế linh hoạt đã chọn.'}</div><p class="small muted">Prototype chỉ xóa dữ liệu giả lập. Không có file thật bị xóa.</p>`;
  actions=btn('Giữ bản chờ','close-modal')+btn('Xóa bản chờ','confirm-delete','danger solid',`data-id="${f.id}"`);
 }else if(kind==='renew-help'){
  title='Gia hạn chứng thư như thế nào?';sub=c.email;
  body=`<ol class="steps-help"><li><strong>Gửi đề nghị cho CA DUT.</strong> Liên hệ chủ chứng thư/CA bên ngoài, hoặc gửi yêu cầu từ CTSV nếu đã được cấp quyền.</li><li><strong>Chờ CA xử lý và duyệt.</strong> Ghi chú đã liên hệ không làm ngày hết hạn thay đổi.</li><li><strong>Kiểm tra lại chứng thư.</strong> CTSV đọc dữ liệu mới từ CA; chỉ ghi nhận hiệu lực khi có chứng thư đúng người, hợp lệ.</li><li><strong>Người ký tiếp tục đúng bước.</strong> Không tự ký sau gia hạn, không thay chứng thư trong lịch sử cũ.</li></ol><div class="notice">“Kiểm tra lại” chỉ đọc dữ liệu. “Gửi yêu cầu gia hạn” chỉ gửi đề nghị, không tự gia hạn thêm 365 ngày.</div>`;
  actions=btn('Đã hiểu','close-modal','primary');
 }else if(kind==='renew'){
  title='Gửi yêu cầu gia hạn chứng thư';sub=c.email;
  body=`<form id="renew-form"><dl class="meta"><div><dt>Chứng thư gần nhất</dt><dd>${c.serial}</dd></div><div><dt>Hạn hiện tại · giả lập</dt><dd>${c.validTo}</dd></div></dl><div class="notice warn"><strong>CTSV không tự duyệt gia hạn.</strong>Yêu cầu gửi thành công vẫn cần CA duyệt. Khi được duyệt, chứng thư có thể được thay thế; thông tin các lần ký cũ được giữ.</div><label class="field">Lý do đề nghị<textarea name="reason" aria-label="Lý do đề nghị gia hạn" required maxlength="1000">${c.state==='expired'?'Chứng thư đã hết hạn, đề nghị CA kiểm tra và gia hạn.':'Chứng thư sắp hết hạn, đề nghị CA xem xét gia hạn.'}</textarea></label><p class="small muted">Mô phỏng giao diện, không gửi API gia hạn thật.</p><div class="actions">${cancel}<button type="submit" class="primary">Xác nhận gửi yêu cầu</button></div></form>`;
 }else if(kind==='user-sign'){
  title='Xác nhận ký số';sub=i.title;
  body=`<dl class="meta"><div><dt>Bước của bạn</dt><dd>${roles[s.role].name}</dd></div><div><dt>Vị trí</dt><dd>Trang 1 · vùng đã chọn</dd></div></dl><p>Chỉ ký bước của bạn. Sau khi được tiếp nhận, hệ thống tự xử lý; bạn có thể rời trang.</p>${s.role==='pp'?'<label class="check"><input id="ack" type="checkbox">Tôi đã xem đúng phiên bản quyết định này và đồng ý ký.</label>':''}<p class="small muted">Đây là mô phỏng, không có chữ ký hoặc request thật.</p>`;
  actions=cancel+btn('Xác nhận ký','confirm-user-sign','primary',`${s.role==='pp'?'disabled':''}`);
 }else if(kind==='reset'){
  title='Đặt lại dữ liệu demo?';body='<p>Chỉ đặt lại dữ liệu giả lập của prototype tại địa chỉ này. Không ảnh hưởng CTSV, DB hoặc file trên máy chủ thật.</p>';actions=cancel+btn('Đặt lại demo','confirm-reset','primary');
 }
 dialog.innerHTML=`<div class="dialog-head"><div><h2 id="dialog-title">${title}</h2>${sub?`<p class="dialog-sub">${esc(sub)}</p>`:''}</div>${btn('×','close-modal','close','aria-label="Đóng hộp thoại"')}</div>${body}${actions?`<div class="actions">${actions}</div>`:''}`;
 dialog.showModal();dialog.querySelector('textarea:not([readonly]),input:not([type=checkbox]),button')?.focus();
}
function closeModal(focus=true){if(dialog.open)dialog.close();modal=null;if(focus&&focusBack){const element=[...root.querySelectorAll('[data-action]')].find(el=>el.dataset.action===focusBack.action&&el.dataset.id===focusBack.id);element?.focus();}}
function report(i){return `YÊU CẦU HỖ TRỢ KÝ SỐ — ${i.id} [DỮ LIỆU MÔ PHỎNG]\nVăn bản: ${i.title}\nMã / phiên bản: ${i.document} / v${i.version}\nĐợt / học kỳ: ${i.round}\nNgười thao tác: ${i.who}\nVai trò: ${roles[i.role].name}\nTài khoản CTSV: ${i.account}\nThời điểm (Việt Nam): ${date(i.time)}\nBước gặp lỗi: ${i.stage}\nAPI / tác vụ: ${i.api}\nHTTP / kết quả kiểm tra: ${i.http}\nMã lỗi: ${i.code}\nNội dung: ${i.message}\nCA báo ký: ${i.ca==='success'?'Thành công':'Chưa có kết quả ký thành công'}\nPDF: tải ${i.download?'được':'chưa được'}; Cloudinary ${i.cloud?'đã lưu':'chưa lưu'}; SQL ${i.sql?'đã ghi đủ':'chưa ghi đủ'}\nTài liệu CA: ${i.ca==='success'?'DEMO-DOC-'+i.id.slice(-3):'Chưa có'}\nBiên nhận: ${i.ca==='success'?'DEMO-SIGN-'+i.id.slice(-3):'Chưa có'}\nBên cần xử lý: Admin xác minh, chưa tự quy trách nhiệm.\nĐề nghị kiểm tra: ${i.ca==='success'?'Khôi phục đúng PDF đã ký / bước lưu còn thiếu, không ký lại.':'Kiểm tra nguyên nhân trước khi mở lại cho người ký.'}\nKhông bao gồm token, mật khẩu hoặc URL có quyền.`;}
function updateCert(c){
 c.checked=at();c.checkFailed=false;
 if(s.scenario==='fixed'){const old=c.serial;c.state='valid';c.days=365;c.validTo='25/11/2027';c.serial='DEMO-'+c.id.toUpperCase()+'-2027';c.notes.push({text:'CA đã cập nhật chứng thư trong kịch bản mô phỏng. Giữ serial cũ '+old+' trong lịch sử.',at:at()});}
 else c.notes.push({text:'Đã kiểm tra dữ liệu CA mô phỏng. Hạn vẫn '+c.validTo+'; kiểm tra lại không tự gia hạn.',at:at()});
}
const adminOnly=new Set(['page-incidents','page-certificates','incident-tab','open-incident','start','finish','note','copy','copy-text','cert-filter','open-cert','check-cert','check-all','renew','check-renew','cert-note','delete-file','confirm-delete']);
document.addEventListener('click',async e=>{
 const el=e.target.closest('[data-action]');if(!el||el.disabled)return;const action=el.dataset.action,id=el.dataset.id;e.preventDefault();
 if(busy)return;if(adminOnly.has(action)&&s.role!=='admin'){toast('Góc nhìn người ký không có chức năng quản trị.',true);return;}
 if(action==='close-modal'){closeModal();return;}
 if(action==='dismiss-toast'){document.querySelector('#feedback').replaceChildren();return;}
 if(action==='reset'){openModal('reset');return;}
 if(action==='confirm-reset'){closeModal(false);localStorage.removeItem(key);s=initial();render();toast('Đã đặt lại dữ liệu mô phỏng.');return;}
 if(action==='page-incidents'||action==='back-incidents')go(()=>{s.page='incidents';s.incident=null;s.tab='waiting';});
 else if(action==='page-certificates'||action==='back-certificates')go(()=>{s.page='certificates';s.certificate=null;});
 else if(action==='incident-tab')go(()=>{s.tab=id;s.incident=null;s.query='';});
 else if(action==='clear-search'){s.query='';render();}
 else if(action==='open-incident')go(()=>{s.page='incidents';s.incident=id;});
 else if(action==='start'){const i=current();if(!i||i.status!=='waiting')return;i.status='working';i.startedAt=at();addHistory(i,'Admin bắt đầu xử lý sự cố.');render();toast('Đã bắt đầu xử lý sự cố.');}
 else if(action==='finish'){const i=current();if(!i||i.status!=='working')return;i.status='resolved';i.closedAt=at();addHistory(i,'Admin kết thúc xử lý sự cố.');render();toast('Đã kết thúc xử lý sự cố.');}
 else if(action==='note'||action==='copy'||action==='cert-note'||action==='renew-help'||action==='renew')openModal(action,id);
 else if(action==='copy-text'){const text=dialog.querySelector('textarea');try{await navigator.clipboard.writeText(text.value);toast('Đã sao chép thông tin hỗ trợ.');}catch{text.select();toast('Nội dung đã được chọn. Nhấn Ctrl+C để sao chép.');}}
 else if(action==='delete-file')openModal('delete',id);
 else if(action==='confirm-delete'){const f=s.files.find(f=>f.id===id);f.deleted=true;f.deletedAt=at();addHistory(s.incidents.find(i=>i.id===f.incident),'Admin đã xóa bản chờ '+f.name+' trên server (mô phỏng).');closeModal(false);render();toast('Đã xóa bản chờ mô phỏng. Lịch sử và bản Cloudinary giữ nguyên.');}
 else if(action==='cert-filter'){s.certificateFilter=id;render();}
 else if(action==='open-cert')go(()=>{s.certificate=id;s.page='certificates';});
 else if(action==='check-cert'||action==='check-all')await simulate('Đang đọc thông tin chứng thư từ CA — mô phỏng…',()=>{
  const selected=action==='check-all'?s.certificates:[s.certificates.find(c=>c.id===id)];
  if(s.scenario==='offline'){selected.forEach(c=>c.checkFailed=true);toast('Chưa kết nối được CA. Giữ kết quả lần kiểm trước, không đổi thành Hết hạn.',true);}
  else{selected.forEach(updateCert);toast('Đã kiểm tra dữ liệu mô phỏng. Không tự cộng ngày hết hạn.');}
 });
 else if(action==='check-renew')await simulate('Đang kiểm tra kết quả yêu cầu gia hạn mô phỏng…',()=>{
  const c=s.certificates.find(c=>c.id===id);
  if(s.scenario==='fixed'){c.renew.state='approved';updateCert(c);toast('CA đã duyệt và có chứng thư mới trong kịch bản mô phỏng.');}
  else{c.notes.push({text:'Kiểm tra yêu cầu gia hạn: vẫn đang chờ CA DUT duyệt.',at:at()});toast('Yêu cầu vẫn đang chờ CA DUT duyệt. Hạn hiện tại chưa thay đổi.');}
 });
 else if(action==='view-signer')go(()=>{s.role=s.incidents.find(i=>i.id===id).role;s.incident=id;s.userBox=false;s.userError=null;s.userAccepted=false;});
 else if(action==='page-user'||action==='user-back')go(()=>{s.incident=null;s.userBox=false;s.userError=null;s.userAccepted=false;});
 else if(action==='user-open')go(()=>{s.incident=id;s.userBox=false;s.userError=null;s.userAccepted=false;s.userCorrected=false;});
 else if(action==='user-refresh'){render();toast('Đã cập nhật trạng thái tài liệu mô phỏng.');}
 else if(action==='user-box'){if(s.userError)s.userCorrected=true;s.userBox=true;s.userError=null;render();}
 else if(action==='user-sign')openModal('user-sign',s.incident);
 else if(action==='confirm-user-sign'){
  if(s.role==='pp'&&!dialog.querySelector('#ack')?.checked)return;closeModal(false);
  await simulate('Đang tiếp nhận xác nhận ký — mô phỏng…',()=>{
   if(s.scenario==='input'&&!s.userCorrected){s.userError='Vùng ký đang vượt ra ngoài trang PDF. Hãy chọn lại vùng nằm trọn trong trang rồi thử lại.';toast(s.userError,true);}
   else{s.userAccepted=true;s.userError=null;toast('Đã tiếp nhận yêu cầu ký mô phỏng. Bạn có thể tiếp tục công việc khác.');}
  });
 }
});
document.addEventListener('change',e=>{
 const el=e.target;
 if(el.id==='role')go(()=>{s.role=el.value;s.incident=null;s.certificate=null;s.page=s.role==='admin'?'incidents':'user';s.tab='waiting';s.userBox=false;s.userError=null;s.userAccepted=false;});
 else if(el.id==='scenario'){s.scenario=el.value;if(s.scenario==='input'){s.userBox=false;s.userError=null;s.userCorrected=false;s.userAccepted=false;}render();toast('Đã đổi kịch bản demo. Thao tác này không thay đổi kết quả xử lý sự cố.');}
 else if(el.id==='file-filter'){s.fileFilter=el.value;render();}
 else if(el.id==='ack')dialog.querySelector('[data-action="confirm-user-sign"]').disabled=!el.checked;
});
document.addEventListener('submit',async e=>{
 e.preventDefault();if(busy)return;const form=e.target,data=new FormData(form);
 if(form.id==='search-form'){s.query=String(data.get('query')??'').trim();render();return;}
 if(form.id==='note-form'){
  const text=String(data.get('note')??'').trim(),reference=String(data.get('reference')??'').trim();if(!text)return;
  if(modal.kind==='cert-note'){const c=s.certificates.find(c=>c.id===modal.id);c.notes.push({text:text+(reference?' · '+reference:''),at:at()});}
  else{const i=s.incidents.find(i=>i.id===modal.id);i.notes.push({text,reference,at:at()});addHistory(i,'Admin đã ghi chú xử lý.');}
  closeModal(false);render();toast('Đã lưu ghi chú vào lịch sử mô phỏng.');
 }else if(form.id==='renew-form'){
  const reason=String(data.get('reason')??'').trim(),c=s.certificates.find(c=>c.id===modal.id);if(!reason||c.renew)return;closeModal(false);
  await simulate('Đang gửi đề nghị gia hạn — chỉ mô phỏng…',()=>{c.renew={id:'DEMO-RENEW-'+c.id.toUpperCase(),at:at(),state:'pending'};c.notes.push({text:'Đã gửi đề nghị gia hạn mô phỏng: '+reason,at:at()});toast('Đã gửi yêu cầu mô phỏng — chờ CA DUT duyệt. Ngày hết hạn chưa đổi.');});
 }
});
dialog.addEventListener('cancel',e=>{e.preventDefault();closeModal();});
dialog.addEventListener('click',e=>{if(e.target===dialog){const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)closeModal();}});
render();
