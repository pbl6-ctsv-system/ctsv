package pbl6.ctsv.shcn.foundation;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.phancongkhoa.config.PhanCongKhoaSettings;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.*;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests.PageQuery;

//shcn: read-only adapter authorized by the owner; no Foundation Entity/Repository imports or DML.
public final class JdbcPhanCongKhoaFoundation implements PhanCongKhoaFoundationPort {
    private final JdbcTemplate jdbc;
    private final PhanCongKhoaSettings settings;
    private final Clock clock;
    public JdbcPhanCongKhoaFoundation(JdbcTemplate jdbc,PhanCongKhoaSettings settings,Clock clock){this.jdbc=jdbc;this.settings=settings;this.clock=clock;}
    private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SHCN read adapter requires a local transaction");}

    @Override public Actor quyen(FoundationAccess.Identity identity){
        transaction();
        record Grant(boolean read,boolean prepare,Long unit,boolean children){}
        var grants=jdbc.query("""
            SELECT p.don_vi_id,p.bao_gom_don_vi_con,
              MAX(CASE WHEN c.MaChucNang=N'shcn.phanCongKhoa.read' THEN 1 ELSE 0 END) can_read,
              MAX(CASE WHEN c.MaChucNang=N'shcn.phanCongKhoa.prepare' THEN 1 ELSE 0 END) can_prepare
            FROM dbo.phan_quyen_nguoi_dung p WITH(UPDLOCK,HOLDLOCK)
            JOIN dbo.vai_tro r ON r.id=p.vai_tro_id
            JOIN dbo.VaiTro ar WITH(HOLDLOCK) ON ar.MaVaiTro=r.ma COLLATE Latin1_General_100_BIN2
            JOIN dbo.TaiKhoan_VaiTro av WITH(HOLDLOCK) ON av.VaiTroID=ar.ID
            JOIN dbo.VaiTro_ChucNang e WITH(HOLDLOCK) ON e.VaiTroID=ar.ID
            JOIN dbo.ChucNang c WITH(HOLDLOCK) ON c.ID=e.ChucNangID
            WHERE p.nguoi_dung_id=? AND av.TaiKhoanID=? AND p.ma_phan_he=N'SHCN'
              AND r.dang_hoat_dong=1 AND ar.TrangThai=1 AND c.TrangThai=1
              AND c.MaChucNang IN(N'shcn.phanCongKhoa.read',N'shcn.phanCongKhoa.prepare')
              AND (p.hieu_luc_tu IS NULL OR p.hieu_luc_tu<=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
              AND (p.hieu_luc_den IS NULL OR p.hieu_luc_den>TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
            GROUP BY p.id,p.don_vi_id,p.bao_gom_don_vi_con
            """,(r,n)->new Grant(r.getBoolean("can_read"),r.getBoolean("can_prepare"),r.getObject("don_vi_id",Long.class),r.getBoolean("bao_gom_don_vi_con")),identity.userId(),identity.principal().accountId().toString());
        boolean read=false,prepare=false;var scopes=new TreeMap<Long,boolean[]>();
        for(var grant:grants){
            boolean w=grant.prepare(),r=w||grant.read();
            read|=r;prepare|=w;if(!r||grant.unit()==null)continue;
            for(long unit:expanded(grant.unit(),grant.children())){
                var flags=scopes.computeIfAbsent(unit,key->new boolean[2]);flags[0]=true;flags[1]|=w;
            }
        }
        var faculties=new ArrayList<Khoa>();
        scopes.forEach((id,flags)->faculties.addAll(jdbc.query("SELECT id,ma,ten,dang_hoat_dong FROM dbo.don_vi WHERE id=?",(r,n)->new Khoa(r.getLong(1),r.getString(2),r.getString(3),flags[0],flags[1],r.getBoolean(4)),id)));
        return new Actor(identity.userId(),identity.principal().accountId().toString(),read,prepare,faculties);
    }
    private Set<Long> expanded(long root,boolean children){
        var seen=new LinkedHashSet<Long>();seen.add(root);if(!children)return seen;
        var queue=new ArrayDeque<Long>();queue.add(root);int depth=0;
        while(!queue.isEmpty()){
            if(++depth>10000)throw PhanCongKhoaFailure.unavailable();
            long id=queue.removeFirst();
            for(long child:jdbc.query("SELECT id FROM dbo.don_vi WHERE don_vi_cha_id=? ORDER BY id",(r,n)->r.getLong(1),id)){
                if(!seen.add(child))throw PhanCongKhoaFailure.unavailable();queue.add(child);
            }
        }
        return seen;
    }
    @Override public PhamViDTO phamVi(Actor actor){
        var counts=new HashMap<String,Integer>();actor.khoa().forEach(k->counts.merge(k.ma(),1,Integer::sum));
        return new PhamViDTO(actor.khoa().stream().map(k->{
            String reason=!k.dangHoatDong()?"KHONG_DU_DIEU_KIEN":counts.get(k.ma())>1?"CHUA_XAC_MINH_ANH_XA":!maLuuDuoc(k.ma())?"MA_KHONG_PHU_HOP_SHCN":null;
            return new KhoaDTO(Long.toString(k.id()),k.ma(),k.ten(),k.doc(),k.lap(),reason==null,reason);
        }).toList());
    }
    @Override public boolean maLuuDuoc(String value){
        transaction();if(!PhanCongKhoaRequests.selectableCode(value))return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN CONVERT(nvarchar(128),CONVERT(varchar(32),?)) COLLATE Latin1_General_100_BIN2=CONVERT(nvarchar(128),?) COLLATE Latin1_General_100_BIN2 THEN 1 ELSE 0 END",Boolean.class,value,value));
    }
    private long source(){
        transaction();if(settings.sourceCode()==null||settings.sourceCode().isBlank())throw PhanCongKhoaFailure.unavailable();
        var ids=jdbc.query("SELECT id FROM dbo.he_thong_ngoai WHERE ma=? AND dang_hoat_dong=1",(r,n)->r.getLong(1),settings.sourceCode());
        if(ids.size()!=1)throw PhanCongKhoaFailure.unavailable();return ids.getFirst();
    }
    private static String search(String... columns){
        // SQL Server CHARINDEX(N'', value) is 0: an empty search must explicitly mean no filter.
        return "(?=N'' OR "+String.join(" OR ",Arrays.stream(columns).map(c->"CHARINDEX(?,COALESCE("+c+",N''))>0").toList())+")";
    }
    @Override public Page<ChuongTrinhDTO> chuongTrinh(Khoa khoa,PageQuery query){
        long src=source();
        String from=" FROM dbo.chuong_trinh_dao_tao p WHERE p.he_thong_nguon_id=? AND p.dang_hien_dien_nguon=1 AND EXISTS(SELECT 1 FROM dbo.lop_sinh_hoat l WHERE l.chuong_trinh_dao_tao_id=p.id AND l.khoa_quan_ly_id=? AND l.he_thong_nguon_id=? AND l.dang_hien_dien_nguon=1) AND "+search("p.ma","p.ten","p.ten_nganh_moi_nguon");
        var args=new ArrayList<Object>(List.of(src,khoa.id(),src,query.q(),query.q(),query.q(),query.q()));
        long total=jdbc.queryForObject("SELECT COUNT_BIG(*)"+from,Long.class,args.toArray());
        args.add(query.offset());args.add(query.size());
        var rows=jdbc.query("SELECT p.id,p.ma,p.ten,p.ten_nganh_moi_nguon"+from+" ORDER BY p.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(r,n)->new ChuongTrinhDTO(Long.toString(r.getLong(1)),r.getString(2),r.getString(3),r.getString(4)),args.toArray());
        return new Page<>(rows,query.page(),query.size(),total);
    }
    private String classSelect(){return """
        SELECT l.id,l.ma,l.ten,d.ma faculty,p.id program_id,p.ma program_code,p.ten program_name,p.ten_nganh_moi_nguon,
          (SELECT COUNT_BIG(*) FROM dbo.lop_sinh_hoat x WHERE x.he_thong_nguon_id=l.he_thong_nguon_id AND x.dang_hien_dien_nguon=1 AND x.ma=l.ma) code_count
        """;}
    private String classFrom(){return """
         FROM dbo.lop_sinh_hoat l JOIN dbo.don_vi d ON d.id=l.khoa_quan_ly_id
         LEFT JOIN dbo.chuong_trinh_dao_tao p ON p.id=l.chuong_trinh_dao_tao_id AND p.he_thong_nguon_id=l.he_thong_nguon_id AND p.dang_hien_dien_nguon=1
         WHERE l.khoa_quan_ly_id=? AND l.he_thong_nguon_id=? AND l.dang_hien_dien_nguon=1
        """;}
    private LopDTO classRow(ResultSet r,Khoa khoa)throws SQLException{
        String ma=r.getString("ma");Long pid=r.getObject("program_id",Long.class);
        var program=pid==null?null:new ChuongTrinhDTO(pid.toString(),r.getString("program_code"),r.getString("program_name"),r.getString("ten_nganh_moi_nguon"));
        String reason=!khoa.dangHoatDong()?"KHONG_DU_DIEU_KIEN":r.getLong("code_count")!=1?"CHUA_XAC_MINH_ANH_XA":!maLuuDuoc(ma)?"MA_KHONG_PHU_HOP_SHCN":null;
        return new LopDTO(Long.toString(r.getLong("id")),ma,r.getString("ten"),r.getString("faculty"),program,reason==null,reason);
    }
    @Override public Page<LopDTO> lop(Khoa khoa,Long program,PageQuery query){
        long src=source();String from=classFrom();var args=new ArrayList<Object>(List.of(khoa.id(),src));
        if(program!=null){from+=" AND l.chuong_trinh_dao_tao_id=?";args.add(program);}
        from+=" AND "+search("l.ma","l.ten");args.add(query.q());args.add(query.q());args.add(query.q());
        long total=jdbc.queryForObject("SELECT COUNT_BIG(*)"+from,Long.class,args.toArray());
        args.add(query.offset());args.add(query.size());
        var rows=jdbc.query(classSelect()+from+" ORDER BY l.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(r,n)->classRow(r,khoa),args.toArray());
        return new Page<>(rows,query.page(),query.size(),total);
    }
    @Override public Map<String,LopDTO> lopTheoMa(Khoa khoa,Collection<String> codes){
        if(codes.isEmpty())return Map.of();long src=source();var rows=new ArrayList<LopDTO>();
        for(var part:parts(codes)){
            var args=new ArrayList<Object>(List.of(khoa.id(),src));args.addAll(part);
            rows.addAll(jdbc.query(classSelect()+classFrom()+" AND l.ma IN ("+marks(part.size())+")",(r,n)->classRow(r,khoa),args.toArray()));
        }
        return unique(rows,LopDTO::maLop);
    }
    private String staffCode(){
        if(!settings.staffMappingVerified()||settings.staffCodeColumn()!=PhanCongKhoaSettings.StaffCodeColumn.MA_CB||settings.employmentStatuses().isEmpty()||settings.employmentEndInclusive()==null)throw PhanCongKhoaFailure.unavailable();
        // HoSoCanBo.MaHS -> API staff_code -> can_bo.ma_cb, approved by the owner.
        return "c.ma_cb";
    }
    private String staffFrom(String code){return " FROM dbo.can_bo c WHERE c.he_thong_nguon_id=? AND EXISTS(SELECT 1 FROM dbo.qua_trinh_cong_tac w WHERE w.can_bo_id=c.id AND w.don_vi_id=? AND (w.hieu_luc_tu IS NULL OR w.hieu_luc_tu<=?) AND (w.hieu_luc_den IS NULL OR w.hieu_luc_den"+(settings.employmentEndInclusive()?">=":">")+"?) AND w.trang_thai_cong_tac IN ("+marks(settings.employmentStatuses().size())+"))";}
    private ArrayList<Object> staffArgs(Khoa khoa,long src){
        var date=java.sql.Date.valueOf(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))));
        var args=new ArrayList<Object>(List.of(src,khoa.id(),date,date));args.addAll(new TreeSet<>(settings.employmentStatuses()));return args;
    }
    private String staffSelect(String code){return "SELECT c.id,"+code+" teacher_code,c.ho_ten,c.la_giang_vien,(SELECT COUNT_BIG(*) FROM dbo.can_bo x WHERE x.he_thong_nguon_id=c.he_thong_nguon_id AND "+code.replace("c.","x.")+"="+code+") code_count";}
    private GiangVienDTO staffRow(ResultSet r,Khoa khoa)throws SQLException{
        String code=r.getString("teacher_code");
        String reason=!khoa.dangHoatDong()||!Boolean.TRUE.equals(r.getObject("la_giang_vien",Boolean.class))?"KHONG_DU_DIEU_KIEN":r.getLong("code_count")!=1?"CHUA_XAC_MINH_ANH_XA":!maLuuDuoc(code)?"MA_KHONG_PHU_HOP_SHCN":null;
        return new GiangVienDTO(Long.toString(r.getLong("id")),code,r.getString("ho_ten"),khoa.ma(),reason==null,reason);
    }
    @Override public Page<GiangVienDTO> giangVien(Khoa khoa,PageQuery query){
        String code=staffCode();long src=source();String from=staffFrom(code)+" AND "+search(code,"c.ho_ten");var args=staffArgs(khoa,src);args.add(query.q());args.add(query.q());args.add(query.q());
        long total=jdbc.queryForObject("SELECT COUNT_BIG(*)"+from,Long.class,args.toArray());args.add(query.offset());args.add(query.size());
        var rows=jdbc.query(staffSelect(code)+from+" ORDER BY c.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(r,n)->staffRow(r,khoa),args.toArray());
        return new Page<>(rows,query.page(),query.size(),total);
    }
    @Override public Map<String,GiangVienDTO> giangVienTheoMa(Khoa khoa,Collection<String> codes){
        if(codes.isEmpty())return Map.of();String code=staffCode();long src=source();var rows=new ArrayList<GiangVienDTO>();
        for(var part:parts(codes)){
            var args=staffArgs(khoa,src);args.addAll(part);
            rows.addAll(jdbc.query(staffSelect(code)+staffFrom(code)+" AND "+code+" IN ("+marks(part.size())+")",(r,n)->staffRow(r,khoa),args.toArray()));
        }
        return unique(rows,GiangVienDTO::maGiaoVien);
    }
    @Override public boolean trungMaLopTheoCollation(List<String> codes){
        transaction();if(codes.isEmpty())return false;
        String values=String.join(",",Collections.nCopies(codes.size(),"(CONVERT(varchar(32),?))"));
        return jdbc.queryForObject("SELECT COUNT_BIG(*)-COUNT_BIG(DISTINCT v.ma) FROM (VALUES "+values+") v(ma)",Long.class,codes.toArray())>0;
    }
    private static String marks(int count){return String.join(",",Collections.nCopies(count,"?"));}
    private static List<List<String>> parts(Collection<String> values){
        var all=new ArrayList<>(new LinkedHashSet<>(values));var parts=new ArrayList<List<String>>();
        for(int i=0;i<all.size();i+=900)parts.add(all.subList(i,Math.min(i+900,all.size())));return parts;
    }
    private static <T>Map<String,T> unique(List<T> rows,Function<T,String> code){
        var result=new HashMap<String,T>();var duplicate=new HashSet<String>();
        for(T row:rows){String key=code.apply(row);if(key==null)continue;if(result.putIfAbsent(key,row)!=null)duplicate.add(key);}
        duplicate.forEach(result::remove);return result;
    }
}
