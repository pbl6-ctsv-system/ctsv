package pbl6.ctsv.shcn.phancongkhoa.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** SHCN user events in the existing common audit table, using the caller's transaction. */
public class PhanCongKhoaAudit {
    private final JdbcTemplate jdbc;
    public PhanCongKhoaAudit(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void ghi(long user,String action,long listId,String reason){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SHCN audit requires a local transaction");
        jdbc.update("""
            INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao)
            VALUES(?,N'user',?,?,N'shcn_danh_sach_phan_cong_khoa',?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
            """,UUID.randomUUID().toString(),user,action,Long.toString(listId),reason);
    }
}
