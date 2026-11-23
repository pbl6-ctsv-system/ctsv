export const evaluationFields=['applicationId','submissionId','benefitId','updateSubmissionId','processId','semesterId','policyVersionId','scopeCode','result','reason','timeEvidence','ageReferenceDate','birthDateUsed','statusHistoryId','criteria','evidence','eligiblePeriods'];
export const resultLabels={dat:'Đạt',khong_dat:'Không đạt',can_xac_minh:'Cần xác minh',chua_xet:'Chưa xét',khong_ap_dung:'Không áp dụng'};
export function canStartApplicationReview(source){return source?.status==='approved'&&source.evaluationAllowed===true&&source.workflowStatus!=='hard_copy_failed';}
export function canReviseApplicationReview(record){return !(record?.applicationId&&record.result==='khong_dat'&&record.status==='confirmed');}
export function reviewComparisonValue(field,value,names){return value?(names[field]?.[value]??(['ethnicityId','classId','cohortId','managementUnitId'].includes(field)?'Chưa xác minh tên danh mục':value)):'Chưa có nguồn';}
export function evaluationContent(source={}){
 return Object.fromEntries(evaluationFields.map(key=>[key,source[key]??(['criteria','evidence','eligiblePeriods'].includes(key)?[]:null)]));
}
export function applicationReviewHead(evaluations,policyVersionId,semesterId){
 return evaluations.filter(e=>String(e.policyVersionId)===String(policyVersionId)&&String(e.semesterId)===String(semesterId))
  .reduce((head,e)=>!head||e.revision>head.revision||e.revision===head.revision&&BigInt(e.id)>BigInt(head.id)?e:head,null);
}
export function applicationReviewDecision(form,result,reason=''){
 if(result==='khong_dat'&&!reason.trim())throw new Error('Lý do từ chối bản cứng là bắt buộc.');
 return {...evaluationContent(form),result,reason:result==='dat'?null:reason.trim(),eligiblePeriods:result==='dat'?form.eligiblePeriods:[]};
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
