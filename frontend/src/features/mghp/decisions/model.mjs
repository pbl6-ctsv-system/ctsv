import {vietnamInstant,vietnamInput} from '../configuration/model.mjs';
export class DecisionInputError extends Error {}
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

