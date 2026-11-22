export function declarationDefaults(context,initial,target) {
 return {
  process:target?.studyProcessId??(context.studyProcesses.length===1?context.studyProcesses[0].id:''),
  policy:target?.policyVersionId??(context.policyVersions.length===1?context.policyVersions[0].id:''),
  semesters:initial?initial.requestedSemesters.map(row=>row.semesterId):[context.round.semesterId],
  statements:{firstDegree:null,alreadyBenefitedAtSameLevel:null,salariedWhileStudying:null,...initial?.statements},
 };
}

export function processLabel(value){return [value.programName,value.cohortCode,value.code].filter(Boolean).join(' · ')||'Quá trình học chưa có tên';}
export function relatedPersonFields(policy){
 const documents=policy?.documentGroups?.flatMap(g=>g.documents)??[];
 if(!documents.length||documents.every(d=>d.evidenceSubject==='sinh_vien'))return [];
 return ['relation','fullName','citizenId','ethnicityId','permanentAddress'];
}
export function removeRelatedPerson(people,docs,index){
 return {people:people.filter((_,i)=>i!==index),docs:Object.fromEntries(Object.entries(docs).map(([key,doc])=>[key,{...doc,relatedPersonIndex:doc.relatedPersonIndex===index?null:doc.relatedPersonIndex>index?doc.relatedPersonIndex-1:doc.relatedPersonIndex}]))};
}
export function attachUploadedFile(docs,requirementId,file){
 const current=docs[requirementId]??{requirementId,documentNumber:null,issuer:null,issuedOn:null,expiresOn:null,relatedPersonIndex:null};
 return {...docs,[requirementId]:{...current,fileId:file.id}};
}
