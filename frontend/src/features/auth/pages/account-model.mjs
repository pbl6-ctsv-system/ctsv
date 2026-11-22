export function accountFormErrors(value,creating=true,profilesOnly=false){
 const errors={};
 if(creating){const username=String(value.username??'').normalize('NFC').replace(/^ +| +$/g,'');if(!username||username.length>128||/[\p{Cc}\uD800-\uDFFF]/u.test(username))errors.username='Tên đăng nhập cần từ 1–128 ký tự hợp lệ.';}
 if(!profilesOnly){const password=value[creating?'password':'newPassword']??'',count=[...password].length;if(count<12||count>1024||/[\uD800-\uDFFF]/u.test(password))errors.password='Mật khẩu mới cần từ 12–1024 ký tự Unicode.';if(!creating&&!String(value.reason??'').trim())errors.reason='Nhập lý do đặt lại mật khẩu.';}
 if(value.profileLinks){const links=value.profileLinks;if(links.some(p=>!p.sourceSystem||!['student','staff'].includes(p.profileType)||!p.sourceId||p.sourceId.includes('@'))||new Set(links.map(p=>JSON.stringify(p))).size!==links.length)errors.profileLinks='Chọn đúng hồ sơ nguồn, không để trống hoặc chọn trùng liên kết.';}
 return errors;
}
