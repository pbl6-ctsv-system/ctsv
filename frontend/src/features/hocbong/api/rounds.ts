import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import type {CreatedRound,CreationLookup,CreateRoundCommand,DefaultSemester,RoundContext,RoundDetail,RoundFilters,RoundPage,RoundSummary,RoundView,ScholarshipGroups,ScopeCatalogFilters,ScopeCatalogPage,ScopeCatalogSnapshot,ScopeGroup,ScopeSelection,ScopeVersion,ScopeView,SourceSemester,SourceSemesterFilters,SourceSemesterPage,UpdateRoundCommand} from '../types/round';

const BASE='/api/ctsv/hoc-bong';
const ROUNDS=BASE+'/kkht/dot-xet';
const invalid=()=>new Error('Phản hồi Học bổng không hợp lệ. Vui lòng thử lại.');
const record=(value:unknown):Record<string,unknown>|null=>typeof value==='object'&&value!==null?value as Record<string,unknown>:null;
const integer=(value:unknown,min:number,max:number):value is number=>Number.isSafeInteger(value)&&Number(value)>=min&&Number(value)<=max;
const id=(value:unknown):value is string=>typeof value==='string'&&/^[1-9][0-9]*$/.test(value)&&value.length<=19&&BigInt(value)<=9223372036854775807n;
const nullableText=(value:unknown)=>value===null||typeof value==='string';
const requestSignal=(signal?:AbortSignal)=>signal
 ?AbortSignal.any([signal,AbortSignal.timeout(20000)]):AbortSignal.timeout(20000);

function summary(value:unknown):value is RoundSummary{
 const v=record(value);
 return !!v&&id(v.id)&&id(v.sourceSemesterId)&&id(v.semesterId)&&typeof v.academicYear==='string'
  &&integer(v.semesterOrder,1,2)&&integer(v.number,1,2147483647)&&typeof v.name==='string'&&typeof v.status==='string';
}
function detail(value:unknown):value is RoundDetail{
 const v=record(value),s=record(v?.semester);
 return !!v&&!!s&&id(v.id)&&id(v.sourceSemesterId)&&id(v.createdBy)
  &&integer(v.number,1,2147483647)&&typeof v.name==='string'&&typeof v.status==='string'
  &&id(s.id)&&typeof s.academicYear==='string'&&integer(s.semesterOrder,1,2)
  &&typeof s.startDate==='string'&&nullableText(s.endDate)&&typeof s.status==='string'
  &&nullableText(v.opensAt)&&nullableText(v.closedAt)&&nullableText(v.tuitionSnapshotAt)
  &&(v.ruleVersionId===null||id(v.ruleVersionId));
}

export async function readScholarshipGroups(client:AuthClient,signal?:AbortSignal):Promise<ScholarshipGroups>{
 const response=await client.request(BASE+'/toi/nhom-chuc-nang',{signal:requestSignal(signal)});
 const body=record(await response.json());
 if(!body||typeof body.student!=='boolean'||typeof body.specialist!=='boolean')throw invalid();
 return{student:body.student,specialist:body.specialist};
}

export async function listRounds(client:AuthClient,filters:RoundFilters={},signal?:AbortSignal):Promise<RoundPage>{
 const {page=0,size=20,number,semesterId,sourceSemesterId}=filters;
 if(!integer(page,0,100000)||!integer(size,1,100)||page*size>100000)throw new Error('Trang dữ liệu không hợp lệ.');
 if(number!==undefined&&!integer(number,1,2147483647))throw new Error('Số đợt phải là số nguyên từ 1 đến 2147483647.');
 if(semesterId!==undefined&&!id(semesterId))throw new Error('Học kỳ không hợp lệ.');
 if(sourceSemesterId!==undefined&&(!id(sourceSemesterId)||semesterId!==undefined))throw new Error('Chỉ chọn một loại học kỳ để lọc.');
 const query=new URLSearchParams({page:String(page),size:String(size)});
 if(number!==undefined)query.set('number',String(number));
 if(semesterId!==undefined)query.set('semesterId',semesterId);
 if(sourceSemesterId!==undefined)query.set('sourceSemesterId',sourceSemesterId);
 const response=await client.request(ROUNDS+'?'+query,{signal:requestSignal(signal)});
 const body=record(await response.json());
 if(!body||!Array.isArray(body.items)||!body.items.every(summary)||!integer(body.page,0,100000)
  ||!integer(body.size,1,100)||!integer(body.totalElements,0,Number.MAX_SAFE_INTEGER))throw invalid();
 return body as RoundPage;
}

export async function readRound(client:AuthClient,roundId:string,signal?:AbortSignal):Promise<RoundView>{
 if(!id(roundId))throw new Error('Đường dẫn đợt xét không hợp lệ.');
 const response=await client.request(ROUNDS+'/'+roundId,{signal:requestSignal(signal)});
 const body:unknown=await response.json();
 if(!detail(body)||body.id!==roundId)throw invalid();
 return{round:body,etag:response.headers.get('ETag')};
}

function sourceSemester(value:unknown):value is SourceSemester{
 const v=record(value);
 if(!v||!id(v.sourceSemesterId)||typeof v.code!=='string'||!nullableText(v.name)
  ||!nullableText(v.academicYear)||!(v.semesterOrder===null||integer(v.semesterOrder,1,2))
  ||!nullableText(v.startDate)||!nullableText(v.endDate)
  ||!(v.scholarshipSemesterId===null||id(v.scholarshipSemesterId))||typeof v.selectable!=='boolean')return false;
 return v.selectable
  ?typeof v.academicYear==='string'&&integer(v.semesterOrder,1,2)&&typeof v.startDate==='string'&&v.unavailableReason===null
  :typeof v.unavailableReason==='string'&&v.unavailableReason.length>0;
}

export async function listSourceSemesters(client:AuthClient,filters:SourceSemesterFilters={},signal?:AbortSignal):Promise<SourceSemesterPage>{
 const {page=0,size=20}=filters;
 const q=filters.q?.trim()??'';
 if(Array.from(q).length>128)throw new Error('Từ khóa học kỳ tối đa 128 ký tự.');
 if(!integer(page,0,100000)||!integer(size,1,100)||page*size>100000)throw new Error('Trang học kỳ không hợp lệ.');
 const query=new URLSearchParams({page:String(page),size:String(size)});
 if(q)query.set('q',q);
 const response=await client.request(BASE+'/hoc-ky-nguon?'+query,{signal:requestSignal(signal)});
 const body=record(await response.json());
 if(!body||!Array.isArray(body.items)||!body.items.every(sourceSemester)||!integer(body.page,0,100000)
  ||!integer(body.size,1,100)||!integer(body.totalElements,0,Number.MAX_SAFE_INTEGER))throw invalid();
 return body as SourceSemesterPage;
}

export async function readDefaultSemester(client:AuthClient,signal?:AbortSignal):Promise<DefaultSemester>{
 const response=await client.request(BASE+'/hoc-ky-nguon/mac-dinh',{signal:requestSignal(signal)});
 const body=record(await response.json());
 if(!body||(body.semester===null?body.reason!=='NO_ELIGIBLE_SEMESTER'
  :!sourceSemester(body.semester)||!body.semester.selectable||body.reason!==null))throw invalid();
 return{semester:body.semester as SourceSemester|null,reason:body.reason as DefaultSemester['reason']};
}

export async function readSourceSemester(client:AuthClient,sourceId:string,signal?:AbortSignal):Promise<SourceSemester>{
 if(!id(sourceId))throw new Error('Đường dẫn học kỳ không hợp lệ. Hãy chọn lại học kỳ.');
 const response=await client.request(BASE+'/hoc-ky-nguon/'+sourceId,{signal:requestSignal(signal)});
 const body:unknown=await response.json();
 if(!sourceSemester(body)||body.sourceSemesterId!==sourceId)throw invalid();
 return body;
}

export const isRoundEtag=(value:unknown):value is string=>typeof value==='string'&&/^"[A-Za-z0-9_-]+"$/.test(value);
const catalogRevision=(value:unknown):value is string=>typeof value==='string'&&/^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/.test(value);
const requestId=(value:unknown):value is string=>typeof value==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(value);
const pairKey=(group:{cohortId:string;majorId:string})=>group.cohortId+':'+group.majorId;
const uniqueGroups=(groups:{cohortId:string;majorId:string}[])=>new Set(groups.map(pairKey)).size===groups.length;

function scopeGroup(value:unknown):value is ScopeGroup{
 const v=record(value);
 return !!v&&id(v.cohortId)&&id(v.majorId)&&nullableText(v.cohortCode)&&nullableText(v.majorCode)
  &&nullableText(v.majorName)&&integer(v.studentCount,0,2147483647);
}

function savedScope(value:unknown):value is ScopeVersion{
 const v=record(value);
 return !!v&&id(v.id)&&integer(v.revision,1,2147483647)&&(v.mode==='all'||v.mode==='groups')
  &&catalogRevision(v.catalogRevision)&&Array.isArray(v.groups)&&v.groups.length>=1&&v.groups.length<=2000
  &&v.groups.every(scopeGroup)&&uniqueGroups(v.groups)&&id(v.createdBy)
  &&typeof v.createdAt==='string'&&/^\d{4}-\d{2}-\d{2}T/.test(v.createdAt)&&Number.isFinite(Date.parse(v.createdAt));
}

function scopeCommand(command:UpdateRoundCommand):UpdateRoundCommand{
 if(!catalogRevision(command.catalogRevision))throw new Error('Danh mục phạm vi không hợp lệ. Hãy tải lại danh mục.');
 const scope=record(command.scope);
 if(!scope)throw new Error('Hãy chọn phạm vi xét học bổng.');
 let selection:ScopeSelection;
 if(scope.mode==='all'&&!('groups' in scope))selection={mode:'all'};
 else if(scope.mode==='groups'&&Array.isArray(scope.groups)&&scope.groups.length>=1&&scope.groups.length<=2000){
  const groups=scope.groups.map(value=>{
   const group=record(value);
   if(!group||!id(group.cohortId)||!id(group.majorId))throw new Error('Nhóm Khóa + Ngành không hợp lệ.');
   return{cohortId:group.cohortId,majorId:group.majorId};
  });
  if(!uniqueGroups(groups))throw new Error('Phạm vi có nhóm Khóa + Ngành trùng nhau.');
  selection={mode:'groups',groups};
 }else throw new Error('Phạm vi cần từ 1 đến 2000 nhóm hợp lệ hoặc toàn trường.');
 return{catalogRevision:command.catalogRevision,scope:selection};
}

export async function listScopeCatalog(client:AuthClient,filters:ScopeCatalogFilters,signal?:AbortSignal):Promise<ScopeCatalogPage>{
 const {sourceSemesterId,cohortId,majorId,page=0,size=20}=filters;
 if(!id(sourceSemesterId)||cohortId!==undefined&&!id(cohortId)||majorId!==undefined&&!id(majorId))throw new Error('Học kỳ hoặc nhóm phạm vi không hợp lệ.');
 const q=filters.q?.trim()??'';
 if(Array.from(q).length>128)throw new Error('Từ khóa phạm vi tối đa 128 ký tự.');
 if(!integer(page,0,100000)||!integer(size,1,100)||page*size>100000)throw new Error('Trang phạm vi không hợp lệ.');
 const query=new URLSearchParams({sourceSemesterId,page:String(page),size:String(size)});
 if(cohortId!==undefined)query.set('cohortId',cohortId);
 if(majorId!==undefined)query.set('majorId',majorId);
 if(q)query.set('q',q);
 const response=await client.request(BASE+'/kkht/pham-vi-nguon?'+query,{signal:requestSignal(signal)});
 const body=record(await response.json());
 if(response.status!==200||!body||!catalogRevision(body.catalogRevision)||!Array.isArray(body.items)
  ||!body.items.every(scopeGroup)||!uniqueGroups(body.items)||body.page!==page||body.size!==size
  ||!integer(body.totalElements,0,2147483647)
  ||body.items.length!==Math.min(size,Math.max(0,body.totalElements-page*size)))throw invalid();
 return body as ScopeCatalogPage;
}

export async function loadScopeCatalog(client:AuthClient,sourceSemesterId:string,signal?:AbortSignal):Promise<ScopeCatalogSnapshot>{
 const groups:ScopeGroup[]=[],seen=new Set<string>();
 let revision:string|undefined,total:number|undefined;
 for(let page=0;page*100<=100000;page++){
  const result=await listScopeCatalog(client,{sourceSemesterId,page,size:100},signal);
  if(revision===undefined){revision=result.catalogRevision;total=result.totalElements;}
  if(result.catalogRevision!==revision||result.totalElements!==total)throw new Error('Danh mục phạm vi đã thay đổi. Hãy tải lại danh mục.');
  if(result.totalElements>100100)throw new Error('Danh mục phạm vi vượt giới hạn có thể tải đầy đủ.');
  for(const group of result.items){
   const key=pairKey(group);
   if(seen.has(key))throw invalid();
   seen.add(key);groups.push(group);
  }
  if(groups.length===total)return{catalogRevision:revision,groups};
 }
 throw new Error('Chưa tải được đầy đủ danh mục phạm vi.');
}

export async function readRoundScope(client:AuthClient,roundId:string,signal?:AbortSignal):Promise<ScopeView>{
 if(!id(roundId))throw new Error('Đường dẫn đợt xét không hợp lệ.');
 const response=await client.request(ROUNDS+'/'+roundId+'/pham-vi',{signal:requestSignal(signal)});
 const body=record(await response.json());
 if(response.status!==200||!body||body.roundId!==roundId||!isRoundEtag(body.roundEtag)
  ||!(body.scope===null||savedScope(body.scope)))throw invalid();
 return body as ScopeView;
}

export async function readRoundContext(client:AuthClient,roundId:string,signal?:AbortSignal):Promise<RoundContext>{
 for(let attempt=0;attempt<3;attempt++){
  const view=await readRound(client,roundId,signal);
  if(!isRoundEtag(view.etag))throw invalid();
  const saved=await readRoundScope(client,roundId,signal);
  if(view.etag===saved.roundEtag)return{...view,scope:saved.scope};
 }
 throw new Error('Đợt xét đang thay đổi. Hãy đọc lại để lấy đúng phiên phạm vi.');
}

export async function lookupCreation(client:AuthClient,key:string,signal?:AbortSignal):Promise<CreationLookup|null>{
 if(!requestId(key))throw new Error('Mã lệnh tạo đợt không hợp lệ.');
 let response:Response;
 try{response=await client.request(BASE+'/kkht/lenh-tao-dot/'+key,{signal:requestSignal(signal)});}
 catch(error){if(error instanceof ApiError&&error.status===404)return null;throw error;}
 if(response.status===404)return null;
 const body=record(await response.json());
 if(response.status!==200||!body||body.requestId!==key||!id(body.roundId))throw invalid();
 return{requestId:key,roundId:body.roundId};
}

export async function createRound(client:AuthClient,command:CreateRoundCommand,key:string):Promise<CreatedRound>{
 if(!id(command.sourceSemesterId))throw new Error('Học kỳ nguồn không hợp lệ.');
 if(!requestId(key))throw new Error('Mã lệnh tạo đợt không hợp lệ.');
 const payload={sourceSemesterId:command.sourceSemesterId,...scopeCommand(command)};
 const response=await client.request(ROUNDS,{
  method:'POST',headers:{'Content-Type':'application/json','Idempotency-Key':key},
  body:JSON.stringify(payload),
  signal:requestSignal(),
 });
 const body:unknown=await response.json();
 const replayed=response.headers.get('Idempotency-Replayed'),etag=response.headers.get('ETag');
 if(!(response.status===201&&replayed==='false'||response.status===200&&replayed==='true')
  ||!detail(body)||body.sourceSemesterId!==payload.sourceSemesterId||!isRoundEtag(etag)){
  throw new Error('Chưa xác nhận được kết quả tạo đợt. Hãy kiểm tra đợt đã lưu trước khi gửi lại.');
 }
 return{round:body,etag,replayed:replayed==='true'};
}

export async function updateRound(client:AuthClient,roundId:string,command:UpdateRoundCommand,ifMatch:string|null):Promise<RoundView>{
 if(!id(roundId))throw new Error('Đường dẫn đợt xét không hợp lệ.');
 if(!isRoundEtag(ifMatch))throw new Error('Chưa có phiên dữ liệu hợp lệ. Hãy đọc bản mới trước khi lưu.');
 const payload=scopeCommand(command);
 const response=await client.request(ROUNDS+'/'+roundId,{
  method:'PUT',headers:{'Content-Type':'application/json','If-Match':ifMatch},
  body:JSON.stringify(payload),signal:requestSignal(),
 });
 const body:unknown=await response.json();
 const etag=response.headers.get('ETag');
 if(response.status!==200||!detail(body)||body.id!==roundId||body.status!=='draft'||!isRoundEtag(etag)){
  throw new Error('Chưa xác nhận được kết quả sửa đợt. Hãy kiểm tra bản đang lưu trước khi gửi lại.');
 }
 return{round:body,etag};
}
