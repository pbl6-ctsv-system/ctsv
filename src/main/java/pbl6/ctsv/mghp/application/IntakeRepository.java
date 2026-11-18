package pbl6.ctsv.mghp.application;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static pbl6.ctsv.mghp.application.ApplicationEntities.*;

@Repository
class IntakeRepository {
    @jakarta.persistence.PersistenceContext private EntityManager em;
    private final JdbcTemplate jdbc;

    IntakeRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    <T extends Row> T save(T row) { em.persist(row); return row; }
    void flush() { em.flush(); }
    <T> T find(Class<T> type,long id) { return em.find(type,id); }
    <T> jakarta.persistence.TypedQuery<T> query(String statement,Class<T> type) {
        return em.createQuery(statement,type);
    }

    void lockOperation(UUID operation) {
        int result=jdbc.queryForObject("""
            DECLARE @result int;
            EXEC @result=sys.sp_getapplock @Resource=?, @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=5000;
            SELECT @result
            """,Integer.class,"ctsv.mghp.submit."+operation);
        if(result<0) throw new IntakeProblem(503,"DEPENDENCY_UNAVAILABLE","request");
    }

    Application root(long id) {
        var locks=jdbc.query("SELECT id FROM dbo.mghp_ho_so WITH(UPDLOCK,HOLDLOCK) WHERE id=?",(r,n)->r.getLong(1),id);
        if(locks.isEmpty()) throw IntakeProblem.missing();
        return em.find(Application.class,id);
    }

    Application root(Long id,long round,long process,long policy) {
        if(id!=null) return root(id.longValue());
        var ids=jdbc.query("""
            SELECT id FROM dbo.mghp_ho_so WITH(UPDLOCK,HOLDLOCK)
            WHERE dot_xet_id=? AND qua_trinh_hoc_id=? AND chinh_sach_id=?
            """,(r,n)->r.getLong(1),round,process,policy);
        return ids.isEmpty()?null:em.find(Application.class,ids.getFirst());
    }

    void change(Application root,String next,OffsetDateTime now,String reason) {
        em.flush();
        if(jdbc.update("""
            UPDATE dbo.mghp_ho_so SET trang_thai=?,thoi_diem_cap_nhat=?,
                ly_do_rut_ho_so=CASE WHEN ?='withdrawn' THEN ? ELSE ly_do_rut_ho_so END
            WHERE id=? AND phien_ban_ky_thuat=?
            """,next,now,next,reason,root.id,root.fence)!=1) throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");
        em.refresh(root);
    }

    List<Submission> versions(long id) {
        return children(Submission.class,"applicationId",id,"revision desc,id desc");
    }

    Supplement supplement(long id) {
        if(rows("SELECT id FROM dbo.mghp_yeu_cau_bo_sung WITH(UPDLOCK,HOLDLOCK) WHERE id=?",id).isEmpty())
            throw IntakeProblem.missing();
        return em.find(Supplement.class,id);
    }

    void changeSupplement(Supplement row,String state,OffsetDateTime deadline,OffsetDateTime respondedAt,OffsetDateTime closedAt) {
        em.flush();
        if(jdbc.update("""
            UPDATE dbo.mghp_yeu_cau_bo_sung SET trang_thai=?,han_xu_ly=?,thoi_diem_nhan_phan_hoi=?,thoi_diem_dong=?
            WHERE id=? AND phien_ban_ky_thuat=?
            """,state,deadline,respondedAt,closedAt,row.id,row.fence)!=1)
            throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");
        em.refresh(row);
    }

    Submission replay(UUID operation) {
        return em.createQuery("from P06Submission where operationId=:operation",Submission.class)
            .setParameter("operation",operation).getResultList().stream().findFirst().orElse(null);
    }

    <T extends Row> List<T> children(Class<T> type,String field,long id,String order) {
        return em.createQuery("from P06"+type.getSimpleName()+" where "+field+"=:id order by "+order,type)
            .setParameter("id",id).getResultList();
    }

    Long fileId(Document document) {
        var links=children(EvidenceLink.class,"documentId",document.id,"id");
        if(links.size()!=1) throw new IllegalStateException("Invalid evidence link count");
        return links.getFirst().fileId;
    }

    boolean confirmed(Application root) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM dbo.mghp_danh_gia_dieu_kien WITH(UPDLOCK,HOLDLOCK)
            WHERE trang_thai_xu_ly='confirmed' AND (ho_so_nguon_id=? OR phien_ban_ho_so_nguon_id IN
              (SELECT id FROM dbo.mghp_phien_ban_ho_so WHERE ho_so_id=?))
            """,Integer.class,root.id,root.id)>0;
    }

    Map<String,Object> row(String sql,Object... parameters) {
        var rows=rows(sql,parameters);
        if(rows.isEmpty()) throw IntakeProblem.missing();
        return rows.getFirst();
    }

    List<Map<String,Object>> rows(String sql,Object... parameters) {
        return jdbc.query(sql,(r,n)->{
            var result=new LinkedHashMap<String,Object>();
            for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                Object value=r.getObject(i);
                if(value instanceof java.sql.Date date) value=date.toLocalDate();
                else if(value!=null && "datetimeoffset".equalsIgnoreCase(r.getMetaData().getColumnTypeName(i))) {
                    value=r.getObject(i,OffsetDateTime.class);
                }
                result.put(r.getMetaData().getColumnLabel(i),value);
            }
            return result;
        },parameters);
    }

    Map<String,Object> student(long student) {
        return row("""
            SELECT s.id,s.mssv studentCode,s.ho_ten fullName,s.so_cccd citizenId,s.ngay_sinh birthDate,
                d.ten birthPlace,s.gioi_tinh gender,s.dan_toc_id ethnicityId,s.lop_hien_tai_id classId,
                s.khoa_hoc_hien_tai_id cohortId,s.khoa_quan_ly_hien_tai_id managementUnitId,
                CAST(NULL AS nvarchar(max)) permanentAddress,s.so_dien_thoai phoneNumber,s.thoi_diem_dong_bo observedAt
            FROM dbo.sinh_vien s LEFT JOIN dbo.dia_phuong d ON d.id=s.noi_sinh_id WHERE s.id=? AND s.thoi_diem_xoa IS NULL
            """,student);
    }

    List<Map<String,Object>> processes(long student) {
        return rows("""
            SELECT id,ma_qua_trinh_hoc code,chuong_trinh_dao_tao_id programId,khoa_hoc_id cohortId,hoc_ky_bat_dau_id startSemesterId,
                trang_thai_doi_chieu verificationStatus
            FROM dbo.qua_trinh_theo_hoc WHERE sinh_vien_id=? ORDER BY id
            """,student);
    }

    String fileHash(long owner,long file,Application root) {
        var stored=row("""
            SELECT f.nguoi_so_huu_id owner,f.trang_thai_luu_tru state,f.kieu_noi_dung mime,f.sha256 hash,
                f.thoi_diem_xoa deleted,f.kich_thuoc_tep size,
                (SELECT COUNT(*) FROM dbo.luot_tai_tep u WHERE u.tep_id=f.id AND u.loai_tep='evidence'
                   AND u.trang_thai='committed' AND u.nguoi_dung_id=f.nguoi_so_huu_id) evidence
            FROM dbo.tep_dinh_kem f WITH(UPDLOCK,HOLDLOCK) WHERE f.id=?
            """,file);
        if(!Objects.equals(stored.get("owner"),owner)) throw IntakeProblem.missing();
        if(!"available".equals(stored.get("state")) || !"application/pdf".equals(stored.get("mime"))
            || stored.get("deleted")!=null || !(stored.get("size") instanceof Long size) || size<1 || size>10_000_000
            || !(stored.get("hash") instanceof String hash) || !hash.matches("[0-9a-f]{64}")
            || ((Number)stored.get("evidence")).intValue()!=1) throw IntakeProblem.conflict("SUBMISSION_NOT_ALLOWED");
        int other=jdbc.queryForObject("""
            SELECT COUNT(*) FROM dbo.mghp_tep_minh_chung l
            JOIN dbo.mghp_giay_to_ho_so g ON g.id=l.giay_to_ho_so_id
            JOIN dbo.mghp_phien_ban_ho_so v ON v.id=g.phien_ban_ho_so_id
            WHERE l.tep_dinh_kem_id=? AND v.ho_so_id<>?
            """,Integer.class,file,root.id);
        if(other>0) throw IntakeProblem.conflict("SUBMISSION_NOT_ALLOWED");
        return hash;
    }
}
