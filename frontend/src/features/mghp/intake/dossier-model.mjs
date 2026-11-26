export function applicationListUrl(page=0,status='',semesterId='',roundId='',stage='') {
 const query=new URLSearchParams({page:String(page)});
 if(status)query.set('status',status);
 if(semesterId)query.set('semesterId',semesterId);
 if(roundId)query.set('roundId',roundId);
 if(stage)query.set('stage',stage);
 return '/api/ctsv/mghp/ho-so?'+query;
}
export function noticeAction(url){
 if(typeof url!=='string')return null;
 if(/^\/mghp\/ky-so\?yeu-cau=[1-9]\d*$/.test(url))return {href:url,label:'Xem tài liệu ký số'};
 if(/^\/mghp\/ho-so\?(bo-sung|ho-so)=[1-9]\d*$/.test(url))return {href:url,label:url.includes('bo-sung=')?'Xem yêu cầu bổ sung':'Xem hồ sơ liên quan'};
 return null;
}

export function declarationChanges(before,after) {
 const fields=(a={},b={})=>[...new Set([...Object.keys(a),...Object.keys(b)])]
  .filter(field=>(a[field]??null)!==(b[field]??null))
  .map(field=>({field,before:a[field]??null,after:b[field]??null}));
 const a=new Map((before.documents??[]).map(d=>[d.requirementId,d]));
 const b=new Map((after.documents??[]).map(d=>[d.requirementId,d]));
 const comparable=doc=>doc?Object.fromEntries(['requirementId','fileId','documentNumber','issuer','issuedOn','expiresOn','relatedPersonIndex'].map(key=>[key,doc[key]??null])):null;
 const documents=[...new Set([...a.keys(),...b.keys()])]
  .filter(id=>JSON.stringify(comparable(a.get(id)))!==JSON.stringify(comparable(b.get(id))))
  .map(requirementId=>({requirementId,before:a.get(requirementId)??null,after:b.get(requirementId)??null}));
 const relatedPeople=Array.from({length:Math.max(before.relatedPeople?.length??0,after.relatedPeople?.length??0)},(_,index)=>({index,fields:fields(before.relatedPeople?.[index],after.relatedPeople?.[index])})).filter(person=>person.fields.length);
 return {claims:fields(before.claims,after.claims),documents,statements:fields(before.statements,after.statements),relatedPeople};
}
