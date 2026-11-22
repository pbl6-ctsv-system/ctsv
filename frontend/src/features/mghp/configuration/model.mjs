export const POLICY_FIELDS='name,rate,scopeCode,effectiveFrom,effectiveUntil,legalBasis,ruleVersion,benefitDuration,tiePriority,documentNumber,article,sourceUrl,evidenceUpdateMode,evidenceUpdateGuidance,minimumAge,maximumAge,ageReferenceRule,firstDegreeRequired,criteria,documentGroups'.split(',');
export const ROUND_FIELDS='code,semesterId,name,description,opensAt,submissionDeadline,reviewDeadline,allowRetroactive,retroactiveMode,midSemesterMode,policyVersions'.split(',');
export const CRITERION_FIELDS='code,name,assessmentGuidance,required,conditional,applicabilityGuidance,order'.split(',');
export const GROUP_FIELDS='code,name,minimumRequired,order,documents'.split(',');
export const DOCUMENT_FIELDS='code,name,type,hardCopyRequired,maxAgeDays,order,certifiedCopyRequired,stampRequired,confirmationContent,confirmingAuthority,reviewGuidance,evidenceSubject'.split(',');
const pick=(value,fields)=>Object.fromEntries(fields.map(k=>[k,value[k]??null]));
export function policyContent(value){const p=pick(value,POLICY_FIELDS);p.criteria=(value.criteria??[]).map(c=>pick(c,CRITERION_FIELDS));p.documentGroups=(value.documentGroups??[]).map(g=>({...pick(g,GROUP_FIELDS),documents:(g.documents??[]).map(d=>pick(d,DOCUMENT_FIELDS))}));return p;}
export const PLAN_FIELDS='supplementPlannedStartAt,supplementPlannedEndAt,reviewPlannedStartAt,councilPlannedDate,signingPlannedStartAt,signingPlannedEndAt,publicationPlannedStartAt,publicationPlannedEndAt'.split(',');
export const emptySchedule=()=>Object.fromEntries(PLAN_FIELDS.map(f=>[f,null]));
// These forms always edit Vietnam wall time, regardless of the browser's timezone.
export const vietnamInput=value=>value?new Date(new Date(value).getTime()+7*3600000).toISOString().slice(0,19):'';
export const vietnamInstant=value=>value?new Date(value+'+07:00').toISOString():null;
export function roundContent(value){return {...pick(value,ROUND_FIELDS),retroactiveMode:'theo_phap_luat',midSemesterMode:'khoang_ngay_chuyen_vien_xac_nhan',policyVersions:(value.policyVersions??[]).map(v=>pick(v,['policyVersionId','applicationTemplateFileId','order'])),schedule:pick(value.schedule??{},PLAN_FIELDS)};}
export function emptyPolicy(){return {...Object.fromEntries(POLICY_FIELDS.map(f=>[f,null])),rate:'1.000000000',scopeCode:'hoc_phi_chuong_trinh_chinh',criteria:[],documentGroups:[]};}
export function emptyRound(){return {...Object.fromEntries(ROUND_FIELDS.map(f=>[f,null])),code:'',semesterId:'',allowRetroactive:true,retroactiveMode:'theo_phap_luat',midSemesterMode:'khoang_ngay_chuyen_vien_xac_nhan',policyVersions:[],schedule:emptySchedule()};}
