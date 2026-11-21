package pbl6.ctsv.shcn.dotsinhhoat.reference;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;

//shcn: owner-authorized read-only adapter. Never imports/writes Foundation entities or source data.
public final class JdbcDotReference implements DotReference {
    private final JdbcTemplate jdbc;private final AcademicSemesterSource semesters;private final String sourceCode;
    public JdbcDotReference(JdbcTemplate jdbc,AcademicSemesterSource semesters,String sourceCode){this.jdbc=jdbc;this.semesters=semesters;this.sourceCode=sourceCode;}
    private static void tx(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SHCN read adapter needs transaction");}
    @Override public Quyen permissions(FoundationAccess.Identity identity){
        tx();var codes=jdbc.query("""
            SELECT DISTINCT c.MaChucNang FROM dbo.phan_quyen_nguoi_dung p WITH(UPDLOCK,HOLDLOCK)
            JOIN dbo.vai_tro r WITH(HOLDLOCK) ON r.id=p.vai_tro_id
            JOIN dbo.VaiTro ar WITH(HOLDLOCK) ON ar.MaVaiTro=r.ma COLLATE Latin1_General_100_BIN2
            JOIN dbo.TaiKhoan_VaiTro av WITH(HOLDLOCK) ON av.VaiTroID=ar.ID
            JOIN dbo.VaiTro_ChucNang e WITH(HOLDLOCK) ON e.VaiTroID=ar.ID
            JOIN dbo.ChucNang c WITH(HOLDLOCK) ON c.ID=e.ChucNangID
            WHERE p.nguoi_dung_id=? AND av.TaiKhoanID=? AND p.ma_phan_he=N'SHCN' AND p.don_vi_id IS NULL
              AND r.dang_hoat_dong=1 AND ar.TrangThai=1 AND c.TrangThai=1
              AND c.MaChucNang IN(N'shcn.dot.read',N'shcn.dot.prepare',N'shcn.reference.read')
              AND (p.hieu_luc_tu IS NULL OR p.hieu_luc_tu<=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
              AND (p.hieu_luc_den IS NULL OR p.hieu_luc_den>TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
            """,(r,n)->r.getString(1),identity.userId(),identity.principal().accountId().toString());
        return new Quyen(codes.contains("shcn.dot.read"),codes.contains("shcn.dot.prepare"),codes.contains("shcn.reference.read"));
    }
    private long source(){
        tx();if(sourceCode==null||sourceCode.isBlank())throw DotFailure.unavailable();
        var sources=jdbc.query("SELECT id FROM dbo.he_thong_ngoai WITH(HOLDLOCK) WHERE ma=? AND dang_hoat_dong=1",(r,n)->r.getLong(1),sourceCode);
        if(sources.size()!=1)throw DotFailure.unavailable();return sources.getFirst();
    }
    private void validCode(String code){
        try{DotRequests.code(code,"maHocKy");}catch(DotFailure e){throw DotFailure.unavailable();}
        boolean roundTrip=Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN CONVERT(nvarchar(128),CONVERT(varchar(32),?)) COLLATE Latin1_General_100_BIN2=CONVERT(nvarchar(128),?) COLLATE Latin1_General_100_BIN2 THEN 1 ELSE 0 END",Boolean.class,code,code));
        if(!roundTrip)throw DotFailure.unavailable();
    }
    @Override public void requireSemester(String code){
        long src=source();var ids=jdbc.query("SELECT id FROM dbo.hoc_ky WITH(UPDLOCK,HOLDLOCK) WHERE he_thong_nguon_id=? AND dang_hien_dien_nguon=1 AND ma=?",(r,n)->r.getLong(1),src,code);
        if(ids.isEmpty())throw DotFailure.invalid("maHocKy");if(ids.size()!=1)throw DotFailure.unavailable();validCode(code);
        var semester=semesters.findById(ids.getFirst()).orElseThrow(DotFailure::unavailable);
        if(!semester.present()||!Objects.equals(code,semester.code()))throw DotFailure.invalid("maHocKy");
        // SHCN drafts need no verified start/end milestones from MGHP.
    }
    @Override public Page<HocKyDTO> semesters(Query q){
        long src=source();
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN EXISTS(SELECT ma FROM dbo.hoc_ky WITH(HOLDLOCK) WHERE he_thong_nguon_id=? AND dang_hien_dien_nguon=1 GROUP BY ma HAVING COUNT_BIG(*)>1) THEN 1 ELSE 0 END",Boolean.class,src)))throw DotFailure.unavailable();
        String where=" FROM dbo.hoc_ky WITH(HOLDLOCK) WHERE he_thong_nguon_id=? AND dang_hien_dien_nguon=1 AND (?=N'' OR CHARINDEX(?,COALESCE(ma,N''))>0 OR CHARINDEX(?,COALESCE(ten,N''))>0)";
        Object[] params={src,q.q(),q.q(),q.q()};long total=Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT_BIG(*)"+where,Long.class,params));
        var args=new ArrayList<Object>(Arrays.asList(params));args.add(q.offset());args.add(q.size());
        var items=jdbc.query("SELECT ma,ten,nam_hoc_nguon"+where+" ORDER BY id "+(q.descending()?"DESC":"ASC")+" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(r,n)->new HocKyDTO(r.getString(1),r.getString(2),r.getString(3)),args.toArray());
        items.forEach(s->validCode(s.ma()));return new Page<>(items,q.page(),q.size(),total);
    }
}
