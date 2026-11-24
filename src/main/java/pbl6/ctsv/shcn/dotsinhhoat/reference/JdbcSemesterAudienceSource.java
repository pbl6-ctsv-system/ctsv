package pbl6.ctsv.shcn.dotsinhhoat.reference;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.reference.SemesterAudienceResolver.*;

/** SHCN read-only adapter over the existing CTSV projection; never connects to SIS. */
final class JdbcSemesterAudienceSource {
 private final JdbcTemplate jdbc;
 JdbcSemesterAudienceSource(JdbcTemplate jdbc){this.jdbc=jdbc;}
 Projection load(long source,long semester){
  // An unresolved source period must not masquerade as an empty selected semester.
  if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.du_lieu_hoc_vu_dong_bo WITH(HOLDLOCK) WHERE he_thong_nguon_id=? AND dang_hien_dien_nguon=1 AND (hoc_ky_id IS NULL OR (hoc_ky_id=? AND sinh_vien_id IS NULL))) THEN 1 ELSE 0 END",Boolean.class,source,semester)))throw new DotFailure(409,"SOURCE_MAPPING_INCOMPLETE");
  String columns="s.id,s.mssv,s.ho_ten,l.id,l.ma,l.ten,k.id,k.ma,k.nam_tuyen_sinh_nguon,s.khoa_hoc_hien_tai_id,u.ma,u.ten,c.ma,c.ten";
  var academic=jdbc.query("SELECT TOP(200001) "+columns+",h.thoi_hoc_nguon,h.ngung_hoc_nguon,h.dung_hoc_nguon FROM dbo.du_lieu_hoc_vu_dong_bo h WITH(HOLDLOCK) LEFT JOIN dbo.sinh_vien s WITH(HOLDLOCK) ON s.id=h.sinh_vien_id AND s.he_thong_nguon_id=h.he_thong_nguon_id AND s.dang_hien_dien_nguon=1 LEFT JOIN dbo.lop_sinh_hoat l WITH(HOLDLOCK) ON l.id=s.lop_hien_tai_id AND l.he_thong_nguon_id=h.he_thong_nguon_id AND l.dang_hien_dien_nguon=1 LEFT JOIN dbo.khoa_hoc k WITH(HOLDLOCK) ON k.id=l.khoa_hoc_id AND k.he_thong_nguon_id=h.he_thong_nguon_id AND k.dang_hien_dien_nguon=1 LEFT JOIN dbo.don_vi u WITH(HOLDLOCK) ON u.id=l.khoa_quan_ly_id AND u.he_thong_nguon_id=l.he_thong_nguon_id LEFT JOIN dbo.chuong_trinh_dao_tao c WITH(HOLDLOCK) ON c.id=l.chuong_trinh_dao_tao_id AND c.he_thong_nguon_id=l.he_thong_nguon_id AND c.dang_hien_dien_nguon=1 WHERE h.he_thong_nguon_id=? AND h.hoc_ky_id=? AND h.dang_hien_dien_nguon=1",(r,n)->new Member(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),(Long)r.getObject(7),r.getString(8),r.getString(9),"THUONG",r.getString(15),r.getString(16),r.getString(17),(Long)r.getObject(10),r.getString(11),r.getString(12),r.getString(13),r.getString(14)),source,semester);
  var delayed=jdbc.query("SELECT TOP(200001) "+columns+" FROM dbo.phan_lop_ren_luyen p WITH(HOLDLOCK) LEFT JOIN dbo.sinh_vien s WITH(HOLDLOCK) ON s.id=p.sinh_vien_id AND s.he_thong_nguon_id=p.he_thong_nguon_id AND s.dang_hien_dien_nguon=1 LEFT JOIN dbo.lop_sinh_hoat l WITH(HOLDLOCK) ON l.id=p.lop_ren_luyen_id AND l.he_thong_nguon_id=p.he_thong_nguon_id AND l.dang_hien_dien_nguon=1 LEFT JOIN dbo.khoa_hoc k WITH(HOLDLOCK) ON k.id=l.khoa_hoc_id AND k.he_thong_nguon_id=p.he_thong_nguon_id AND k.dang_hien_dien_nguon=1 LEFT JOIN dbo.don_vi u WITH(HOLDLOCK) ON u.id=l.khoa_quan_ly_id AND u.he_thong_nguon_id=l.he_thong_nguon_id LEFT JOIN dbo.chuong_trinh_dao_tao c WITH(HOLDLOCK) ON c.id=l.chuong_trinh_dao_tao_id AND c.he_thong_nguon_id=l.he_thong_nguon_id AND c.dang_hien_dien_nguon=1 WHERE p.he_thong_nguon_id=? AND p.hoc_ky_id=? AND p.dang_hien_dien_nguon=1 AND (l.id IS NULL OR l.ten COLLATE Vietnamese_CI_AS LIKE N'%renluyen%')",(r,n)->new Member(r.getLong(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),(Long)r.getObject(7),r.getString(8),r.getString(9),"TRE_TIEN_DO",null,null,null,(Long)r.getObject(10),r.getString(11),r.getString(12),r.getString(13),r.getString(14)),source,semester);
  if(academic.size()>200000||delayed.size()>200000)throw DotFailure.unavailable();
  return SemesterAudienceResolver.resolve(academic,delayed);
 }
}
