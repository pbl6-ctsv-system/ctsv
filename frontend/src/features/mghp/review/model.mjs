export const evaluationFields=['applicationId','submissionId','benefitId','updateSubmissionId','processId','semesterId','policyVersionId','scopeCode','result','reason','timeEvidence','ageReferenceDate','birthDateUsed','statusHistoryId','criteria','evidence','eligiblePeriods'];
export const resultLabels={dat:'Đạt',khong_dat:'Không đạt',can_xac_minh:'Cần xác minh',chua_xet:'Chưa xét',khong_ap_dung:'Không áp dụng'};
export function evaluationContent(source={}){
 return Object.fromEntries(evaluationFields.map(key=>[key,source[key]??(['criteria','evidence','eligiblePeriods'].includes(key)?[]:null)]));
}
