async(page)=>{
 const x=page.__p1,a=x?.accounts?.admin;if(!a)throw Error('Admin context required');
 if(x.createdAccountUsername)throw Error('P1 account already exists; use p1-final-dev.js to read it instead of creating another');
 const assert=(v,m)=>{if(!v)throw Error(m);};
 await a.goto('https://127.0.0.1:5173/quan-tri/tai-khoan');
 await a.getByRole('button',{name:'Nhập tài khoản mới',exact:true}).click();
 const username='p1.ux.20261122.'+Date.now();let creates=0;const observe=r=>{if(r.url().endsWith('/api/xac-thuc/tai-khoan')&&r.method()==='POST')creates++;};a.on('request',observe);
 await a.getByRole('textbox',{name:'Tên đăng nhập',exact:true}).fill(username);await a.getByLabel('Mật khẩu',{exact:true}).fill('short');await a.getByRole('button',{name:'Tạo tài khoản',exact:true}).click();await a.getByText('Mật khẩu mới cần từ 12–1024 ký tự Unicode.',{exact:true}).waitFor();assert(creates===0,'Invalid password was sent to Backend');
 await a.getByRole('button',{name:'Thêm liên kết đã xác minh',exact:true}).click();await a.getByRole('combobox',{name:'Người liên kết 1',exact:true}).waitFor();assert(await a.getByRole('textbox',{name:'Định danh hồ sơ nguồn',exact:true}).count()===0,'Raw profile source ID remains');
 const verified=a.getByRole('checkbox',{name:'Tôi đã đối chiếu đúng người và hồ sơ nguồn trước khi cấp liên kết.',exact:true});await verified.check();await a.getByRole('combobox',{name:'Loại hồ sơ liên kết 1',exact:true}).selectOption('staff');assert(!await verified.isChecked(),'Source changes must invalidate the previous identity confirmation');
 await a.getByRole('button',{name:'Bỏ liên kết 1',exact:true}).click();
 // Password lives only in this invocation, never evidence/storage state.
 await a.getByLabel('Mật khẩu',{exact:true}).fill('Synthetic P1 '+Math.random().toString(36)+Math.random().toString(36));const pending=a.waitForResponse(r=>r.url().endsWith('/api/xac-thuc/tai-khoan')&&r.request().method()==='POST');await a.getByRole('button',{name:'Tạo tài khoản',exact:true}).click();const r=await pending;assert(r.status()===201,'Create account failed');await a.getByRole('heading',{name:username,exact:true}).waitFor();x.createdAccountUsername=username;
 await a.getByRole('button',{name:'Vai trò',exact:true}).click();await a.getByRole('combobox',{name:'Vai trò hệ thống',exact:true}).selectOption({label:'Người dùng CTSV'});
 let mutations=0;const writes=r=>{if(r.url().includes('/api/xac-thuc/tai-khoan/')&&['PUT','DELETE'].includes(r.method()))mutations++;};a.on('request',writes);
 let confirmation='';a.once('dialog',async d=>{confirmation=d.message();await d.dismiss();});await a.getByRole('button',{name:'Thu hồi vai trò',exact:true}).click();assert(confirmation.includes(username)&&confirmation.includes('Người dùng CTSV'),'Confirmation has the wrong target');assert(mutations===0,'Cancel still mutated a role');
 await a.getByRole('button',{name:'Khóa / mở khóa',exact:true}).click();await a.getByRole('textbox',{name:'Lý do thay đổi trạng thái',exact:true}).fill('Synthetic P1: chỉ kiểm hủy xác nhận, không khóa tài khoản.');let lockMessage='';a.once('dialog',async d=>{lockMessage=d.message();await d.dismiss();});await a.getByRole('button',{name:'Khóa',exact:true}).click();assert(lockMessage.includes(username),'Lock confirmation missing account name');assert(mutations===0,'Cancel still locked an account');a.off('request',writes);a.off('request',observe);
 x.cases.push({id:'P1-07-REAL_CREATE_VALIDATION_NAMED_CONFIRM_CANCEL',status:'PASS',database:'ctsv_dev',username,creates});
 return {username,creates,cancelledMutations:mutations};
}
