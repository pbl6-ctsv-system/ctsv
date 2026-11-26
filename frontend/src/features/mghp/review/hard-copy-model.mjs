import {currentCheck} from '../intake/review-model.mjs';
const DAY=86400000;
const same=(a,b)=>String(a)===String(b);
const day=value=>{if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(value))return null;const n=Date.parse(value+'T00:00:00Z');return Number.isFinite(n)&&new Date(n).toISOString().slice(0,10)===value?n:null;};
const iso=value=>new Date(value).toISOString().slice(0,10);
export function changeCriterion(form,id,patch){
 const previous=form.criteria.find(c=>same(c.criterionId,id));if(!previous)return form;const next={...previous,...patch};
 return {...form,criteria:form.criteria.map(c=>same(c.criterionId,id)?next:c),evidence:form.evidence.map(e=>same(e.criterionId,id)?{...e,result:['dat','khong_dat','can_xac_minh','khong_ap_dung'].includes(next.result)?next.result:'can_xac_minh',reason:next.result==='dat'?null:next.note?.trim()||'Chưa xác nhận điều kiện này.'}:e)};
}
export function linkPaper(form,documentId,criterionId,enabled,dates={}){
 const existing=e=>same(e.documentId,documentId)&&same(e.criterionId,criterionId);
 if(!enabled)return {...form,evidence:form.evidence.filter(e=>!existing(e))};if(form.evidence.some(existing))return form;
 const c=criterionId==null?{result:'dat',note:null}:form.criteria.find(c=>same(c.criterionId,criterionId));if(!c)return form;
 const previous=form.evidence.find(e=>same(e.documentId,documentId));
 const result=['dat','khong_dat','can_xac_minh','khong_ap_dung'].includes(c.result)?c.result:'can_xac_minh';
 return {...form,evidence:[...form.evidence,{documentId,criterionId,from:dates.from??previous?.from??null,to:dates.to??previous?.to??null,result,reason:result==='dat'?null:c.note?.trim()||'Chưa xác nhận điều kiện này.'}]};
}
export function changePaperDates(form,id,from,to){return {...form,evidence:form.evidence.map(e=>same(e.documentId,id)?{...e,from:from||null,to:to||null}:e)};}
export function paperChecks(document,requirement={}){
 const labels={form:'Hình thức và thẩm quyền',content:'Nội dung giấy',validity:'Hiệu lực giấy',subject:'Đúng người được chứng minh',hard_copy:'Đã nhận bản cứng'};
 const kinds=['form','content','validity','subject',...(requirement.hardCopyRequired?['hard_copy']:[])];
 const missing=kinds.filter(k=>!(k==='hard_copy'?['received']:['pass','not_applicable']).includes(currentCheck({...document,checks:document.checks??[]},k)?.result));
 return {ready:missing.length===0,missing:missing.map(k=>labels[k])};
}
export function reviewBounds(policy,term,process){
 const issues=[];
 if(!term||term.verificationStatus!=='verified'||day(term.startDate)==null||day(term.endDate)==null)issues.push('Mốc học kỳ chưa được xác minh đầy đủ.');
 if(!process||process.verificationStatus!=='verified'||day(process.actualStart)==null)issues.push('Mốc quá trình học chưa được xác minh đầy đủ.');
 const verified=policy?.ruleVerificationStatus==='da_doi_chieu_nguon'||(policy?.policyCode??policy?.code)==='M9'&&policy?.ruleVerificationStatus==='quy_uoc_do_an';
 if(!verified||day(policy?.effectiveFrom)==null)issues.push('Nguồn hoặc ngày hiệu lực chính sách chưa được xác minh.');
 if(policy?.effectiveUntil&&day(policy.effectiveUntil)==null||process?.actualEnd&&day(process.actualEnd)==null)issues.push('Ngày kết thúc nguồn chưa hợp lệ.');
 if(issues.length)return {issues,from:null,to:null};
 const from=Math.max(day(term.startDate),day(process.actualStart),day(policy.effectiveFrom));
 const to=Math.min(day(term.endDate),policy.effectiveUntil?day(policy.effectiveUntil):Infinity,process.actualEnd?day(process.actualEnd):Infinity);
 if(from>to)return {issues:['Không có thời gian giao nhau giữa học kỳ, quá trình học và chính sách.'],from:null,to:null};
 return {issues:[],from,to};
}
export function proposePeriods(form,policy,documents,term,process){
 const bounds=reviewBounds(policy,term,process);if(bounds.issues.length)return {...bounds,periods:[]};
 const groups=policy.documentGroups??[],requirements=groups.flatMap(g=>g.documents??[]),windows=[];
 for(const use of form.evidence??[]){
  if(use.result!=='dat'||day(use.from)==null||day(use.to)==null||use.to<use.from)continue;
  const d=documents.find(d=>same(d.id,use.documentId)),requirement=requirements.find(r=>same(r.id,d?.requirementId));
  if(!d||!requirement||form.submissionId&&!same(d.submissionId,form.submissionId)||!paperChecks(d,requirement).ready||d.expiresOn&&day(d.expiresOn)==null)continue;
  const from=Math.max(bounds.from,day(use.from)),to=Math.min(bounds.to,day(use.to),d.expiresOn?day(d.expiresOn):Infinity);
  if(from<=to)windows.push({from,to,requirement:String(d.requirementId)});
 }
 const points=[...new Set([bounds.from,bounds.to+DAY,...windows.flatMap(w=>[w.from,w.to+DAY])])].sort((a,b)=>a-b),periods=[];
 const covered=(from,to)=>{
  const active=new Set(windows.filter(w=>w.from<=from&&w.to>=to).map(w=>w.requirement));
  return active.size>0&&groups.every(g=>(g.documents??[]).filter(r=>active.has(String(r.id))).length>=Number(g.minimumRequired));
 };
 for(let i=0;i<points.length-1;i++){
  const from=points[i],to=points[i+1]-DAY;if(from>bounds.to||to<bounds.from)continue;
  if(!covered(from,to))continue;
  const previous=periods.at(-1);if(previous&&day(previous.to)+DAY===from&&covered(day(previous.from),to))previous.to=iso(to);else periods.push({from:iso(from),to:iso(to)});
 }
 return {...bounds,periods};
}
export function applyPeriods(form,periods,basis){return {...form,timeEvidence:basis,eligiblePeriods:periods.map(p=>({...p,evidence:basis}))};}
export function timeBasis(form,documents,source){
 const names=[...new Set(form.evidence.filter(e=>e.result==='dat').map(e=>{
  const d=documents.find(d=>same(d.id,e.documentId));const name=source?.documentRequirements?.find(r=>same(r.id,d?.requirementId))?.name??d?.documentNumber??'Giấy đã chọn';return name+' ('+(e.from??'chưa có ngày')+' — '+(e.to??'chưa có ngày')+')';
 }))];return names.length?'Khoảng hưởng đối chiếu theo '+names.join('; ')+', trong học kỳ đang xét.':'';
}
export function reviewReadiness(form,policy,documents,term,process){
 const issues=[],suggested=proposePeriods(form,policy,documents,term,process);
 for(const c of policy?.criteria??[]){const v=form.criteria.find(x=>same(x.criterionId,c.id));
  if(c.required&&(!v||v.result==='chua_xet'||!v.result))issues.push('Chưa kết luận điều kiện: '+c.name+'.');
  else if(c.required&&['khong_dat','can_xac_minh'].includes(v?.result))issues.push('Điều kiện '+c.name+' chưa đạt để duyệt.');
  if(v?.result==='khong_ap_dung'&&!c.conditional||v&&v.result!=='dat'&&v.result!=='chua_xet'&&!v.note?.trim())issues.push('Điều kiện '+c.name+' cần nhận xét phù hợp.');
 }
 if(!form.evidence.length)issues.push('Chưa chọn giấy làm căn cứ cho điều kiện.');
 const requirements=(policy?.documentGroups??[]).flatMap(g=>g.documents??[]);
 for(const id of [...new Set(form.evidence.map(e=>e.documentId))]){
  const uses=form.evidence.filter(e=>same(e.documentId,id)),d=documents.find(d=>same(d.id,id));
  const name=requirements.find(r=>same(r.id,d?.requirementId))?.name??d?.documentNumber??'Giấy đã chọn';
  if(!d||form.submissionId&&!same(d.submissionId,form.submissionId)){issues.push('Có giấy không thuộc đúng bản nộp đang xét.');continue;}
  if(uses.some(e=>e.result==='dat')){
   const checks=paperChecks(d,requirements.find(r=>same(r.id,d.requirementId)));if(!checks.ready)issues.push(name+' còn thiếu: '+checks.missing.join(', ')+'.');
   if(uses.some(e=>e.result==='dat'&&(day(e.from)==null||day(e.to)==null||e.from>e.to)))issues.push(name+' chưa có khoảng thời gian chứng minh hợp lệ.');
  }
 }
 issues.push(...suggested.issues);
 if(form.evidence.some(e=>e.result==='dat')&&!suggested.issues.length&&!suggested.periods.length)issues.push('Các giấy đã chọn chưa chứng minh đủ yêu cầu của mọi nhóm trong cùng khoảng thời gian.');
 if(!form.eligiblePeriods.length)issues.push('Chưa xác nhận thời gian được hưởng.');
 let end=null;for(const p of form.eligiblePeriods){
  if(day(p.from)==null||day(p.to)==null||p.from>p.to||end&&p.from<=end)issues.push('Khoảng hưởng chưa hợp lệ hoặc chồng lấn.');end=p.to;
  if(!p.evidence?.trim())issues.push('Chưa có căn cứ thời gian hưởng.');
  if(!suggested.issues.length&&!suggested.periods.some(s=>p.from>=s.from&&p.to<=s.to))issues.push('Khoảng hưởng vượt thời gian được các giấy đã đối chiếu chứng minh.');
 }
 if(form.eligiblePeriods.length&&!form.timeEvidence?.trim())issues.push('Chưa có căn cứ thời gian hưởng.');
 if((policy?.policyCode??policy?.code)==='M3'&&(!form.birthDateUsed||!form.ageReferenceDate))issues.push('Chưa xác nhận ngày sinh và ngày đối chiếu tuổi.');
 return [...new Set(issues)];
}
