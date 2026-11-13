export const POLICY_FIELDS='name,rate,scopeCode,effectiveFrom,effectiveUntil,legalBasis,ruleVersion,benefitDuration,tiePriority,documentNumber,article,sourceUrl,evidenceUpdateMode,evidenceUpdateGuidance,minimumAge,maximumAge,ageReferenceRule,firstDegreeRequired,criteria,documentGroups'.split(',');
export const ROUND_FIELDS='code,semesterId,name,description,opensAt,submissionDeadline,reviewDeadline,declarationGuidance,lateSubmissionGuidance,allowRetroactive,retroactiveMode,midSemesterMode,policyVersions'.split(',');
export const CRITERION_FIELDS='code,name,assessmentGuidance,required,conditional,applicabilityGuidance,order'.split(',');
export const GROUP_FIELDS='code,name,minimumRequired,order,documents'.split(',');
export const DOCUMENT_FIELDS='code,name,type,hardCopyRequired,maxAgeDays,order,certifiedCopyRequired,stampRequired,confirmationContent,confirmingAuthority,reviewGuidance,evidenceSubject'.split(',');
const pick=(value,fields)=>Object.fromEntries(fields.map(k=>[k,value[k]??null]));
export function policyContent(value){const p=pick(value,POLICY_FIELDS);p.criteria=(value.criteria??[]).map(c=>pick(c,CRITERION_FIELDS));p.documentGroups=(value.documentGroups??[]).map(g=>({...pick(g,GROUP_FIELDS),documents:(g.documents??[]).map(d=>pick(d,DOCUMENT_FIELDS))}));return p;}
export function roundContent(value){return pick(value,ROUND_FIELDS);}
export function emptyPolicy(){return {...Object.fromEntries(POLICY_FIELDS.map(f=>[f,null])),rate:'1.000000000',scopeCode:'hoc_phi_chuong_trinh_chinh',criteria:[],documentGroups:[]};}
export function emptyRound(){return {...Object.fromEntries(ROUND_FIELDS.map(f=>[f,null])),code:'',semesterId:'',allowRetroactive:true,retroactiveMode:'theo_phap_luat',midSemesterMode:'khoang_ngay_chuyen_vien_xac_nhan',policyVersions:[]};}
