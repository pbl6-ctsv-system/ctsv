package pbl6.ctsv.shcn.dotsinhhoat.reference;

import org.springframework.jdbc.core.JdbcTemplate;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;

//shcn: read-only projection of existing Foundation tables, using LOCAL IDs and one verified source.
final class JdbcDotAudienceReference {
    private final JdbcTemplate jdbc;
    JdbcDotAudienceReference(JdbcTemplate jdbc){this.jdbc=jdbc;}
    NhomLopDTO groups(long source,long semester){
        for(String table:new String[]{"khoa_hoc","lop_sinh_hoat"}){
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN EXISTS(SELECT ma FROM dbo."+table+" WITH(HOLDLOCK) WHERE he_thong_nguon_id=? AND dang_hien_dien_nguon=1 GROUP BY ma HAVING COUNT_BIG(*)>1) THEN 1 ELSE 0 END",Boolean.class,source)))throw DotFailure.unavailable();
        }
        var cohorts=jdbc.query("""
            SELECT TOP(1001) k.ma,COALESCE(NULLIF(k.nam_tuyen_sinh_nguon,N''),k.ma),COUNT_BIG(*)
            FROM dbo.khoa_hoc k WITH(HOLDLOCK)
            JOIN dbo.lop_sinh_hoat l WITH(HOLDLOCK) ON l.khoa_hoc_id=k.id AND l.he_thong_nguon_id=k.he_thong_nguon_id AND l.dang_hien_dien_nguon=1
            WHERE k.he_thong_nguon_id=? AND k.dang_hien_dien_nguon=1 AND COALESCE(l.ten,N'') COLLATE Vietnamese_CI_AS NOT LIKE N'%renluyen%'
            GROUP BY k.id,k.ma,k.nam_tuyen_sinh_nguon ORDER BY k.ma
            """,(r,n)->new KhoaHocDTO(r.getString(1),"Khóa "+r.getString(2),r.getLong(3)),source);
        var delayed=jdbc.query("""
            SELECT TOP(1001) l.ma,l.ten,COUNT_BIG(DISTINCT p.sinh_vien_id)
            FROM dbo.phan_lop_ren_luyen p WITH(HOLDLOCK)
            JOIN dbo.lop_sinh_hoat l WITH(HOLDLOCK) ON l.id=p.lop_ren_luyen_id AND l.he_thong_nguon_id=p.he_thong_nguon_id AND l.dang_hien_dien_nguon=1
            WHERE p.he_thong_nguon_id=? AND p.hoc_ky_id=? AND p.dang_hien_dien_nguon=1
              AND l.ten COLLATE Vietnamese_CI_AS LIKE N'%renluyen%'
            GROUP BY l.id,l.ma,l.ten ORDER BY l.ma
            """,(r,n)->new LopTreTienDoDTO(r.getString(1),r.getString(2),r.getLong(3)),source,semester);
        if(cohorts.size()>1000||delayed.size()>1000)throw DotFailure.unavailable();
        long missing=java.util.Objects.requireNonNull(jdbc.queryForObject("""
            SELECT COUNT_BIG(*) FROM dbo.lop_sinh_hoat l WITH(HOLDLOCK)
            WHERE l.he_thong_nguon_id=? AND l.dang_hien_dien_nguon=1 AND COALESCE(l.ten,N'') COLLATE Vietnamese_CI_AS NOT LIKE N'%renluyen%'
              AND NOT EXISTS(SELECT 1 FROM dbo.khoa_hoc k WITH(HOLDLOCK) WHERE k.id=l.khoa_hoc_id AND k.he_thong_nguon_id=l.he_thong_nguon_id AND k.dang_hien_dien_nguon=1)
            """,Long.class,source));
        return new NhomLopDTO(cohorts,delayed,missing,delayed.isEmpty()?"CHUA_CO_DU_LIEU":"CO_DU_LIEU");
    }
}
