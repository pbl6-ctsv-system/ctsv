// Integrate an implemented feature here; empty module folders deliberately have no runtime menu.
export const navigationItems=Object.freeze([
 {id:'lookup',label:'Danh mục',feature:'foundation',view:'lookup',path:'/du-lieu-nen/danh-muc',group:'reference',allow:['mghpSpecialist']},
 {id:'students',label:'Sinh viên',feature:'foundation',view:'students',path:'/du-lieu-nen/sinh-vien',group:'reference',allow:['mghpSpecialist']},
 {id:'manage',label:'Xác minh dữ liệu',feature:'foundation',view:'manage',path:'/du-lieu-nen/xac-minh',group:'verification',allow:['mghpSpecialist']},
 {id:'roles',label:'Phân quyền MGHP',feature:'foundation',view:'roles',path:'/quan-tri/phan-quyen-mghp',group:'business-access',allow:['ctsvRoleAdmin']},
 {id:'auth',label:'Quản trị tài khoản',feature:'auth',view:'auth',path:'/quan-tri/tai-khoan',group:'auth',allow:['auth.accounts.manage']},
 {id:'auth-rbac',label:'Danh mục quyền Auth',feature:'auth',view:'rbac',path:'/quan-tri/danh-muc-quyen',group:'auth',allow:['auth.rbac.read']},
 {id:'mghp-policies',label:'Chính sách MGHP',feature:'mghp',view:'policies',path:'/mghp/chinh-sach',group:'policy',allow:['mghpSpecialist']},
 {id:'mghp-rounds',label:'Đợt xét MGHP',feature:'mghp',view:'rounds',studentView:'public-rounds',path:'/mghp/dot-xet',group:'intake',allow:['student','mghpSpecialist']},
 {id:'mghp-applications',label:'Hồ sơ MGHP',feature:'mghp',view:'staff-applications',studentView:'applications',path:'/mghp/ho-so',group:'intake',allow:['student','mghpSpecialist']},
 {id:'mghp-inbox',label:'Thông báo',feature:'mghp',view:'inbox',path:'/thong-bao',group:'notifications',allow:['ctsvIdentity']},
].map(item=>Object.freeze(item)));

/**
 * @param {{code:string,active:boolean}[]} functions
 * @param {{student:boolean,mghpSpecialist:boolean,ctsvRoleAdmin:boolean}|null} access
 */
export function visibleNavigation(functions,access) {
 const capabilities=new Set(functions.filter(fn=>fn?.active===true&&typeof fn.code==='string'&&fn.code.startsWith('auth.')).map(fn=>fn.code));
 if(access?.student===true)capabilities.add('student');
 if(access?.mghpSpecialist===true)capabilities.add('mghpSpecialist');
 if(access?.ctsvRoleAdmin===true)capabilities.add('ctsvRoleAdmin');
 if(access)capabilities.add('ctsvIdentity');
 return navigationItems.filter(item=>item.allow.some(permission=>capabilities.has(permission)))
  .map(item=>access?.student===true&&item.studentView?{...item,view:item.studentView}:item);
}

const sections=[
 {id:'home',label:'Trang chủ',kind:'link',path:'/'},
 {id:'drl',label:'Điểm rèn luyện',kind:'disabled'},
 {id:'mghp',label:'Miễn giảm học phí',kind:'dropdown',groups:[
  {id:'policy',label:'Cấu hình xét'},
  {id:'intake',label:'Đợt xét và hồ sơ'},
 ]},
 {id:'shcn',label:'Lịch SHCN',kind:'disabled'},
 {id:'notifications',label:'Thông báo',kind:'link',entryId:'mghp-inbox'},
 {id:'scholarships',label:'Học bổng',kind:'disabled'},
 {id:'foundation',label:'Dữ liệu nền',kind:'dropdown',groups:[
  {id:'reference',label:'Tra cứu'},
  {id:'verification',label:'Quản lý dữ liệu'},
 ]},
 {id:'admin',label:'Quản trị',kind:'dropdown',groups:[
  {id:'auth',label:'Tài khoản và quyền hệ thống'},
  {id:'business-access',label:'Quyền nghiệp vụ'},
 ]},
];

/** @typedef {Parameters<typeof visibleNavigation>} NavigationAccess */
/** @typedef {(typeof navigationItems)[number]} PageEntry */
/** @typedef {{id:string,label:string,items:PageEntry[]}} MenuGroup */
/** @typedef {{id:string,label:string,kind:'link',path:string}|{id:string,label:string,kind:'disabled'}|{id:string,label:string,kind:'dropdown',groups:MenuGroup[]}} NavSection */

/** @param {NavigationAccess} permissions @returns {NavSection[]} */
export function navigationSections(...permissions) {
 const entries=visibleNavigation(...permissions);
 /** @type {NavSection[]} */
 const result=[];
 for(const section of sections){
  const {id,label}=section;
  if(section.kind==='disabled'){
   if(permissions[1]?.student)result.push({id,label,kind:'disabled'});
  }else if(section.kind==='dropdown'){
   const groups=(section.groups??[]).map(group=>({...group,items:entries.filter(item=>item.group===group.id)}))
    .filter(group=>group.items.length>0);
   if(groups.length)result.push({id,label,kind:'dropdown',groups});
  }else{
   const path=section.path??entries.find(entry=>entry.id===section.entryId)?.path;
   if(path)result.push({id,label,kind:'link',path});
  }
 }
 return result;
}

/** @param {string} path */
export function normalizePath(path) {
 return path.replace(/\/+$/,'')||'/';
}

/** @param {string} path @param {NavigationAccess} permissions
 * @returns {{kind:'home'|'denied'|'missing'}|{kind:'page',entry:PageEntry}}
 */
export function resolveRoute(path,...permissions) {
 const normalized=normalizePath(path);
 if(normalized==='/')return {kind:'home'};
 const configured=navigationItems.find(entry=>entry.path===normalized);
 if(!configured)return {kind:'missing'};
 const entry=visibleNavigation(...permissions).find(entry=>entry.id===configured.id);
 return entry?{kind:'page',entry}:{kind:'denied'};
}
