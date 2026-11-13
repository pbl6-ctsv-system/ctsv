// Integrate an implemented feature here; empty module folders deliberately have no runtime menu.
export const navigationItems=Object.freeze([
 {id:'lookup',label:'Danh mục',feature:'foundation',view:'lookup'},
 {id:'students',label:'Sinh viên',feature:'foundation',view:'students'},
 {id:'manage',label:'Xác minh & quyền',feature:'foundation',view:'manage'},
 {id:'auth',label:'Quản trị tài khoản',feature:'auth',view:'auth'},
].map(item=>Object.freeze(item)));
