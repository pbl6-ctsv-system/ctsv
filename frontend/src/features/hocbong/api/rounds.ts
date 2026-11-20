import type {AuthClient} from '../../../shared/types/api';
import type {CreateRoundCommand,DefaultSemester,RoundDetail,RoundFilters,RoundPage,RoundSummary,RoundView,ScholarshipGroups,SourceSemester,SourceSemesterFilters,SourceSemesterPage,UpdateRoundCommand} from '../types/round';

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
  &&typeof s.startDate==='string'&&typeof s.endDate==='string'&&typeof s.status==='string'
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
  ?typeof v.academicYear==='string'&&integer(v.semesterOrder,1,2)&&typeof v.startDate==='string'&&typeof v.endDate==='string'&&v.unavailableReason===null
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

export async function createRound(client:AuthClient,command:CreateRoundCommand):Promise<RoundView>{
 if(!id(command.sourceSemesterId))throw new Error('Học kỳ nguồn không hợp lệ.');
 if(!integer(command.number,1,2147483647))throw new Error('Số đợt phải là số nguyên từ 1 đến 2147483647.');
 if(typeof command.name!=='string'||!command.name.trim()||command.name.trim().length>512)throw new Error('Tên đợt cần từ 1 đến 512 ký tự.');
 const response=await client.request(ROUNDS,{
  method:'POST',headers:{'Content-Type':'application/json'},
  body:JSON.stringify({sourceSemesterId:command.sourceSemesterId,number:command.number,name:command.name}),
  signal:requestSignal(),
 });
 const body:unknown=await response.json();
 if(response.status!==201||!detail(body)||body.sourceSemesterId!==command.sourceSemesterId||body.number!==command.number||body.status!=='draft'){
  throw new Error('Chưa xác nhận được kết quả tạo đợt. Hãy kiểm tra đợt đã lưu trước khi gửi lại.');
 }
 return{round:body,etag:response.headers.get('ETag')};
}

export const isRoundEtag=(value:unknown):value is string=>typeof value==='string'&&/^"[A-Za-z0-9_-]+"$/.test(value);

export async function updateRound(client:AuthClient,roundId:string,command:UpdateRoundCommand,ifMatch:string|null):Promise<RoundView>{
 if(!id(roundId))throw new Error('Đường dẫn đợt xét không hợp lệ.');
 if(!isRoundEtag(ifMatch))throw new Error('Chưa có phiên dữ liệu hợp lệ. Hãy đọc bản mới trước khi lưu.');
 if(!integer(command.number,1,2147483647))throw new Error('Số đợt phải là số nguyên từ 1 đến 2147483647.');
 if(typeof command.name!=='string'||!command.name.trim()||command.name.trim().length>512)throw new Error('Tên đợt cần từ 1 đến 512 ký tự.');
 const response=await client.request(ROUNDS+'/'+roundId,{
  method:'PUT',headers:{'Content-Type':'application/json','If-Match':ifMatch},
  body:JSON.stringify({number:command.number,name:command.name}),signal:requestSignal(),
 });
 const body:unknown=await response.json();
 if(response.status!==200||!detail(body)||body.id!==roundId||body.number!==command.number||body.status!=='draft'){
  throw new Error('Chưa xác nhận được kết quả sửa đợt. Hãy kiểm tra bản đang lưu trước khi gửi lại.');
 }
 return{round:body,etag:response.headers.get('ETag')};
}
