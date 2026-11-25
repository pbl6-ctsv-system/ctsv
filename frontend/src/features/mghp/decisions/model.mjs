import {vietnamInstant,vietnamInput} from '../configuration/model.mjs';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
export class DecisionInputError extends Error {}
export function decisionWorkspaceUrl(semester='',round='',version=''){
 const params=new URLSearchParams();if(semester)params.set('hoc-ky',semester);if(round)params.set('dot-xet',round);if(version)params.set('phien-ban',version);
 return '/mghp/hoi-dong-quyet-dinh'+(params.size?'?'+params:'');
}
export function decisionErrorMessage(error){
 if(error instanceof DecisionInputError)return error.message;
 const operation=error?.decisionOperation??'read';
 const tasks={semesters:'tải danh sách học kỳ',rounds:'tải đợt của học kỳ',round:'đọc thông tin đợt',source:'đọc nguồn đã đóng xét',create:'tạo bộ xét',meeting:'lưu kết quả cuộc họp',confirm:'chốt dữ liệu',read:'đọc bộ xét'};
 const task=tasks[operation]??tasks.read;
 if(!(error instanceof ApiError))return 'Chưa nhận được xác nhận khi '+task+'. Hãy đọc lại dữ liệu để kiểm tra kết quả trước khi thử tiếp.';
 if(error.code==='VALIDATION_ERROR'){
  const fields={semesterId:'Học kỳ chưa hợp lệ. Chọn lại học kỳ và đợt thuộc học kỳ đó.',roundId:'Đợt xét chưa hợp lệ. Chọn lại đợt trong học kỳ đang xem.',code:'Mã bộ quyết định không hợp lệ. Nhập mã không rỗng, tối đa 128 ký tự.',minutesCode:'Mã biên bản không hợp lệ. Nhập mã không rỗng, tối đa 128 ký tự.',meetingAt:'Thời gian họp thực tế không hợp lệ hoặc ở tương lai. Kiểm tra lại giờ Việt Nam.',reason:operation==='meeting'?'Kết luận và căn cứ cuộc họp chưa hợp lệ. Nhập nội dung không rỗng, tối đa 4.000 ký tự.':'Lý do chốt dữ liệu chưa hợp lệ. Đọc lại bộ xét trước khi tiếp tục.',reference:'Tham chiếu biên bản vượt quá 512 ký tự.',place:'Địa điểm họp vượt quá 512 ký tự.',number:'Số quyết định vượt quá 128 ký tự.',date:'Ngày quyết định không hợp lệ.',issuingUnitId:'Đơn vị ban hành chưa hợp lệ. Đọc lại thông tin bộ xét.'};
  const details=[...new Set((error.fields??[]).map(f=>fields[f.replace(/^metadata\./,'')]).filter(Boolean))];
  if(details.length)return 'Không thể '+task+': '+details.join(' ');
  return {source:'Không đọc được nguồn đã đóng xét. Kiểm tra lại học kỳ và đợt đang chọn rồi đọc lại.',rounds:'Không tải được đợt xét. Chọn lại học kỳ rồi thử lại.',semesters:'Không tải được danh sách học kỳ. Hãy thử đọc lại trang.',create:'Thông tin tạo bộ xét chưa hợp lệ. Kiểm tra mã bộ quyết định và đợt đang chọn.',meeting:'Thông tin cuộc họp chưa hợp lệ. Kiểm tra mã biên bản, kết luận, thời gian họp và thông tin văn bản.',confirm:'Chưa thể chốt dữ liệu do yêu cầu không hợp lệ. Đọc lại bộ xét trước khi tiếp tục.'}[operation]??'Không đọc được bộ xét do thông tin yêu cầu chưa hợp lệ. Chọn lại học kỳ và đợt.';
 }
 const messages={VERSION_CONFLICT:'Dữ liệu đã thay đổi. Nội dung đang nhập được giữ; đọc lại và đối chiếu trước khi gửi lại.',PRECONDITION_REQUIRED:'Chưa đọc được phiên bản mới nhất. Hãy đọc lại trước khi thao tác.',DECISION_NOT_READY:'Chưa đủ điều kiện lập hoặc chốt bộ xét. Kiểm tra nguồn đã đóng xét, đánh giá đã xác nhận và nhóm đã được công bố ở đợt khác.',DECISION_ALREADY_EXISTS:'Đợt đã có bộ quyết định. Đọc lại để xem bộ hiện hành.',DECISION_CODE_CONFLICT:'Mã bộ quyết định đã tồn tại. Chọn mã khác.',MINUTES_CODE_CONFLICT:'Mã biên bản đã được sử dụng. Kiểm tra lịch sử hoặc nhập mã mới.',IMMUTABLE:'Phiên bản này đã khóa hoặc là bản lịch sử. Chỉ được xem, không sửa trực tiếp.',FORBIDDEN:'Bạn không còn quyền thực hiện chức năng Hội đồng và quyết định.',UNAUTHENTICATED:'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',TOKEN_REVOKED:'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.',LOGIN_REQUIRED:'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.',NOT_FOUND:'Không tìm thấy học kỳ, đợt hoặc bộ xét đang yêu cầu. Hãy chọn lại từ danh sách.',DEPENDENCY_UNAVAILABLE:'Hệ thống tạm chưa sẵn sàng. Đọc lại dữ liệu trước khi thử tiếp.'};
 return messages[error.code]??'Chưa thể '+task+'. Hãy đọc lại dữ liệu để kiểm tra kết quả trước khi gửi lại.';
}
export function requiresDecisionRead(error){return !(error instanceof DecisionInputError)&&(typeof error?.status!=='number'||[409,412,428,500,502,503].includes(error.status));}
export function roundState(round){return round.reviewClosedAt?'Đã đóng xét':round.status==='closed'?'Đã đóng nhận':round.status==='completed'?'Đã hoàn tất':round.status==='open'?'Đang nhận hồ sơ':'Bản nháp';}
export function decisionContent(roundId,code){return {roundId,code:code.trim(),type:'initial',number:null,date:null,issuingUnitId:null};}
export function meetingContent(form,version){
 return {minutesCode:form.minutesCode.trim(),reference:form.reference.trim()||null,meetingAt:vietnamInstant(form.meetingAt),place:form.place.trim()||null,reason:form.reason.trim(),
  metadata:{number:form.number.trim()||null,date:form.date||null,issuingUnitId:version.issuingUnitId??null}};
}
export function meetingErrors(form,now=Date.now()){
 const errors=[];
 if(!form.minutesCode?.trim())errors.push('Nhập mã biên bản.');
 if(!form.reason?.trim())errors.push('Nhập kết luận và căn cứ cuộc họp.');
 if(!form.meetingAt)errors.push('Nhập thời gian họp thực tế.');
 else {const at=Date.parse(vietnamInstant(form.meetingAt));if(!Number.isFinite(at))errors.push('Thời gian họp không hợp lệ.');else if(at>now)errors.push('Thời gian họp thực tế không được ở tương lai.');}
 return errors;
}
export function meetingForm(version){
 return {minutesCode:version.minutes?.code??'',reference:version.minutes?.reference??'',meetingAt:version.minutes?.meetingAt?vietnamInput(version.minutes.meetingAt):'',place:version.minutes?.place??'',reason:version.reason??'',number:version.number??'',date:version.date??''};
}
export function decisionActions(version,headId){
 const editable=!!version&&version.id===headId&&version.status==='draft'&&!version.lockedAt;
 return {meeting:editable,confirm:editable&&version.stage==='after_meeting'};
}
export function decisionLabel(version){return version.status==='locked'?'Đã chốt dữ liệu':version.stage==='after_meeting'?'Đã ghi cuộc họp':'Trước cuộc họp';}
export function sameGroup(a,b){return a.processId===b.processId&&a.semesterId===b.semesterId&&a.scopeCode===b.scopeCode;}
export function shortDate(value){if(!value)return 'Chưa ghi';const [y,m,d]=value.split('-');return d+'/'+m+'/'+y;}
export function rateLabel(value){return new Intl.NumberFormat('vi-VN',{style:'percent',maximumFractionDigits:3}).format(Number(value));}

