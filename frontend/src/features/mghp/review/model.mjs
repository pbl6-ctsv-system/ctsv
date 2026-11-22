export const evaluationFields=['applicationId','submissionId','benefitId','updateSubmissionId','processId','semesterId','policyVersionId','scopeCode','result','reason','timeEvidence','ageReferenceDate','birthDateUsed','statusHistoryId','criteria','evidence','eligiblePeriods'];
export const resultLabels={dat:'Đạt',khong_dat:'Không đạt',can_xac_minh:'Cần xác minh',chua_xet:'Chưa xét',khong_ap_dung:'Không áp dụng'};
export function evaluationContent(source={}){
 return Object.fromEntries(evaluationFields.map(key=>[key,source[key]??(['criteria','evidence','eligiblePeriods'].includes(key)?[]:null)]));
}
export function reviewDocumentLabel(document,source){
 const name=source?.documentRequirements?.find(r=>r.id===document.requirementId)?.name||document.documentNumber||'Giấy chưa có tên';
 const revision=source?.submittedVersions?.find(v=>v.id===document.submissionId)?.revision;
 return name+' · '+(revision?'Bản nộp '+revision:'Bản nộp cần đối chiếu');
}
export function reviewDateErrors(value){
 return [...(value.evidence??[]).flatMap((r,i)=>r.from&&r.to&&r.to<r.from?['Khoảng ngày của giấy '+(i+1)+' bị đảo thứ tự.']:[]),...(value.eligiblePeriods??[]).flatMap((r,i)=>r.from&&r.to&&r.to<r.from?['Khoảng đủ điều kiện '+(i+1)+' bị đảo thứ tự.']:[])];
}
export function reviewFormErrors(value){
 return [...reviewDateErrors(value),...(value.evidence??[]).flatMap((r,i)=>r.result&&r.result!=='dat'&&!r.reason?.trim()?['Giấy '+(i+1)+': nhập lý do khi chưa kết luận Đạt.']:[])];
}
