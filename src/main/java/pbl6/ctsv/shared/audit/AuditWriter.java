package pbl6.ctsv.shared.audit;
import java.sql.*;
import java.util.*;
/** Generic typed audit persistence, no MGHP or Foundation business decisions. */
public final class AuditWriter {
    public long append(Connection c,long systemId,String action,String type,String objectId,String reason,Map<String,String> values)throws SQLException {
        if(c.getAutoCommit())throw new IllegalStateException("Audit event and details require one transaction");
        long id;
        try(var s=c.prepareStatement("INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,he_thong_ngoai_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao) VALUES(?,N'operator',?,?,?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')); SELECT CAST(SCOPE_IDENTITY() AS bigint);")){
            s.setString(1,UUID.randomUUID().toString());s.setLong(2,systemId);s.setString(3,action);s.setString(4,type);s.setString(5,objectId);s.setString(6,reason);
            try(var r=s.executeQuery()){r.next();id=r.getLong(1);}
        }
        try(var s=c.prepareStatement("INSERT dbo.chi_tiet_nhat_ky_thao_tac(nhat_ky_id,ten_truong,kieu_gia_tri,trang_thai_gia_tri_cu,trang_thai_gia_tri_moi,chuoi_moi) VALUES(?,?,N'chuoi',N'khong_ton_tai',?,?)")){
            for(var entry:values.entrySet()){
                s.setLong(1,id);s.setString(2,entry.getKey());s.setString(3,entry.getValue()==null?"null":"co_gia_tri");s.setString(4,entry.getValue());s.addBatch();
            }
            s.executeBatch();
        }
        return id;
    }
}
