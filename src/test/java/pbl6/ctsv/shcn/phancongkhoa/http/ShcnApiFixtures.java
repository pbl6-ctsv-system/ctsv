package pbl6.ctsv.shcn.phancongkhoa.http;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Writes only synthetic fixtures in the exactly guarded SHCN API test DB. Never used in production. */
final class ShcnApiFixtures {
    static final String RUN=UUID.randomUUID().toString().replace("-","").substring(0,10);
    static final String SOURCE="SYNTH_SHCN_API_"+RUN;
    static final String PREPARE="SYNTH_SHCN_PREPARE",READ="SYNTH_SHCN_READ";
    record Actor(UUID account,long user,String token){}
    private final JdbcTemplate jdbc;
    private final ShcnApiTestKeys keys;
    final String khoaA="KA"+RUN,khoaB="KB"+RUN;
    final String lopA1="A1"+RUN,lopA2="A2"+RUN,lopA3="A3"+RUN,lopB="B1"+RUN;
    final String gvA="GV_A_"+RUN,gvB="GV_B_"+RUN,gvNull="GV_NULL_"+RUN;
    long source,facultyA,facultyB,programX,programY,programZ,classA1,teacherA;
    Actor a,a2,b,reader,admin,noScope,multi;
    ShcnApiFixtures(JdbcTemplate jdbc,ShcnApiTestKeys keys){this.jdbc=jdbc;this.keys=keys;}
    void seed(){
        for(String permission:List.of("shcn.phanCongKhoa.read","shcn.phanCongKhoa.prepare"))
            jdbc.update("IF NOT EXISTS(SELECT 1 FROM dbo.ChucNang WHERE MaChucNang=?) INSERT dbo.ChucNang(MaChucNang,TenChucNang,TrangThai) VALUES(?,?,1)",permission,permission,"Synthetic SHCN permission");
        source=id("INSERT dbo.he_thong_ngoai(ma,ten,loai_he_thong,dang_hoat_dong) VALUES(?,N'Synthetic SHCN API source',N'SIS',1)",SOURCE);
        facultyA=id("INSERT dbo.don_vi(ma,ten,loai_don_vi,dang_hoat_dong,he_thong_nguon_id) VALUES(?,N'Khoa A giả lập',N'khoa',1,?)",khoaA,source);
        facultyB=id("INSERT dbo.don_vi(ma,ten,loai_don_vi,dang_hoat_dong,he_thong_nguon_id) VALUES(?,N'Khoa B giả lập',N'khoa',1,?)",khoaB,source);
        programX=program("X");programY=program("Y");programZ=program("Z");
        classA1=lop(lopA1,"Lớp A1 giả lập",facultyA,programX);lop(lopA2,"Lớp A2 giả lập",facultyA,programX);
        lop(lopA3,"Lớp A3 giả lập",facultyA,programY);lop(lopB,"Lớp B giả lập",facultyB,programZ);
        teacherA=teacher(gvA,"Giảng viên A giả lập",true,facultyA);teacher(gvB,"Giảng viên B giả lập",true,facultyB);teacher(gvNull,"Chưa xác minh giảng viên",null,facultyA);
        a=actor("A",PREPARE,facultyA);a2=actor("A2",PREPARE,facultyA);b=actor("B",PREPARE,facultyB);reader=actor("READ",READ,facultyA);
        admin=actor("ADMIN",null,null);grant(admin.user(),role("ctsv.roles.manage"),null,"CTSV");
        noScope=actor("NO_SCOPE",READ,null);multi=actor("MULTI",PREPARE,facultyA);grant(multi.user(),role(READ),facultyB,"SHCN");
    }
    long id(String sql,Object...args){return jdbc.queryForObject(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,args);}
    long role(String code){
        var ids=jdbc.query("SELECT id FROM dbo.vai_tro WHERE ma=?",(r,n)->r.getLong(1),code);
        return ids.isEmpty()?id("INSERT dbo.vai_tro(ma,ten,dang_hoat_dong) VALUES(?,?,1)",code,"Synthetic role "+code):ids.getFirst();
    }
    void grant(long user,long role,Long unit,String module){
        jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he,don_vi_id,hieu_luc_tu) VALUES(?,?,?,?, '2020-01-01')",user,role,module,unit);
        String code=jdbc.queryForObject("SELECT ma FROM dbo.vai_tro WHERE id=?",String.class,role);
        if(!module.equals("SHCN")||!Set.of(READ,PREPARE).contains(code))return;
        jdbc.update("IF NOT EXISTS(SELECT 1 FROM dbo.VaiTro WHERE MaVaiTro=?) INSERT dbo.VaiTro(MaVaiTro,TenVaiTro,TrangThai) VALUES(?,?,1)",code,code,"Synthetic SHCN role");
        long authRole=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=?",Long.class,code);
        for(String permission:code.equals(PREPARE)?List.of("shcn.phanCongKhoa.read","shcn.phanCongKhoa.prepare"):List.of("shcn.phanCongKhoa.read"))
            jdbc.update("INSERT dbo.VaiTro_ChucNang(VaiTroID,ChucNangID) SELECT ?,ID FROM dbo.ChucNang c WHERE MaChucNang=? AND NOT EXISTS(SELECT 1 FROM dbo.VaiTro_ChucNang e WHERE e.VaiTroID=? AND e.ChucNangID=c.ID)",authRole,permission,authRole);
        String account=jdbc.queryForObject("SELECT ma_chu_the_dang_nhap FROM dbo.dinh_danh_dang_nhap WHERE nguoi_dung_id=? AND ma_nha_cung_cap=?",String.class,user,ShcnApiTestKeys.ISSUER);
        jdbc.update("IF NOT EXISTS(SELECT 1 FROM dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=? AND VaiTroID=?) INSERT dbo.TaiKhoan_VaiTro(TaiKhoanID,VaiTroID) VALUES(?,?)",account,authRole,account,authRole);
    }
    Actor actor(String name,String role,Long unit){
        var account=UUID.randomUUID();String username="shcn-"+RUN+"-"+name+"-"+UUID.randomUUID().toString().substring(0,6);
        String hash=new org.springframework.security.crypto.argon2.Argon2PasswordEncoder(16,32,1,19456,2).encode(UUID.randomUUID().toString());
        jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash) VALUES(?,?,?,?)",account.toString(),username,username,hash);
        jdbc.update("INSERT dbo.TaiKhoan_VaiTro(TaiKhoanID,VaiTroID) SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",account.toString());
        long user=id("INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) VALUES(?,?,1)",username+"@example.invalid","Synthetic "+name);
        jdbc.update("INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,SYSUTCDATETIME())",user,ShcnApiTestKeys.ISSUER,account.toString());
        if(role!=null)grant(user,role(role),unit,"SHCN");
        return new Actor(account,user,keys.token(account,0,Instant.now()));
    }
    long program(String suffix){return id("INSERT dbo.chuong_trinh_dao_tao(ma,ten,ten_nganh_moi_nguon,he_thong_nguon_id) VALUES(?,?,?,?)","CT"+suffix+RUN,"Chương trình "+suffix+" giả lập","Ngành "+suffix+" giả lập",source);}
    long lop(String code,String name,long faculty,Long program){return id("INSERT dbo.lop_sinh_hoat(ma,ten,khoa_quan_ly_id,chuong_trinh_dao_tao_id,he_thong_nguon_id) VALUES(?,?,?,?,?)",code,name,faculty,program,source);}
    long teacher(String code,String name,Boolean lecturer,long faculty){
        long id=id("INSERT dbo.can_bo(ma_cb,ma_gv,ho_ten,he_thong_nguon_id,la_giang_vien) VALUES(?,?,?,?,?)",code,"OTHER_"+code,name,source,lecturer);
        jdbc.update("INSERT dbo.qua_trinh_cong_tac(can_bo_id,don_vi_id,trang_thai_cong_tac,hieu_luc_tu,he_thong_nguon_id) VALUES(?,?,N'SYNTH_ACTIVE','2020-01-01',?)",id,faculty,source);return id;
    }
    String year(){return "Y"+UUID.randomUUID().toString().substring(0,12);}
}
