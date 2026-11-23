package pbl6.ctsv.shcn.dotsinhhoat.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.entity.DotSinhHoat;
import pbl6.ctsv.shcn.dotsinhhoat.entity.DoiTuongApDung;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;

public class DotRepository {
    private final EntityManager em;private final JdbcTemplate jdbc;private final ObjectMapper json;
    public DotRepository(EntityManager em,JdbcTemplate jdbc,ObjectMapper json){this.em=em;this.jdbc=jdbc;this.json=json;}
    private static void tx(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SHCN repository needs transaction");}
    public DotSinhHoat find(long id,boolean lock){
        tx();if(lock){em.flush();var ids=jdbc.query("SELECT id FROM shcn.shcn_dot_sinh_hoat WITH(UPDLOCK,HOLDLOCK) WHERE id=?",(r,n)->r.getLong(1),id);if(ids.isEmpty())throw new DotFailure(404,"NOT_FOUND");em.clear();}
        var row=em.find(DotSinhHoat.class,id);if(row==null)throw new DotFailure(404,"NOT_FOUND");return row;
    }
    public Page<DotSinhHoat> list(Query q){
        tx();if(q.maKhoa()!=null)throw DotFailure.unavailable(); // Target resolver belongs to CV-03/05; never ignore the filter.
        var where=new StringBuilder(" where (:q='' or locate(:q,d.tenDot)>0)");var params=new LinkedHashMap<String,Object>();params.put("q",q.q());
        if(q.maHocKy()!=null){where.append(" and d.maHocKy=:ky");params.put("ky",q.maHocKy());}
        if(q.trangThai()!=null){where.append(" and d.trangThai=:state");params.put("state",q.trangThai());}
        var count=em.createQuery("select count(d) from DotSinhHoat d"+where,Long.class);
        var rows=em.createQuery("select d from DotSinhHoat d"+where+" order by d.id "+(q.descending()?"desc":"asc"),DotSinhHoat.class);
        params.forEach((k,v)->{count.setParameter(k,v);rows.setParameter(k,v);});
        long total=count.getSingleResult();return new Page<>(rows.setFirstResult(q.offset()).setMaxResults(q.size()).getResultList(),q.page(),q.size(),total);
    }
    public String ranges(List<KhoangTiet> value){try{return json.writeValueAsString(value);}catch(java.io.IOException e){throw new IllegalStateException("Cannot serialize SHCN periods",e);}}
    public List<KhoangTiet> ranges(String value){try{return List.of(json.readValue(value,KhoangTiet[].class));}catch(java.io.IOException e){throw new IllegalStateException("Invalid stored SHCN periods",e);}}
    public DotSinhHoat create(CauHinh input,String actor,OffsetDateTime now){
        tx();var entity=DotSinhHoat.nhap(input,ranges(input.khungTietChoPhep()),actor,now);em.persist(entity);em.flush();em.refresh(entity);return entity;
    }
    public DotSinhHoat update(long id,byte[] token,CauHinh input,boolean resetDraft,OffsetDateTime now){
        tx();em.flush();
        int changed=jdbc.update("""
            UPDATE shcn.shcn_dot_sinh_hoat SET ma_hoc_ky=?,ten_dot=?,khung_ngay_bat_dau=?,khung_ngay_ket_thuc=?,khung_tiet_cho_phep=?,han_chot_dang_ky_lich=?,ngay_cap_nhat=?,
              trang_thai_phan_cong=CASE WHEN ?=1 AND trang_thai_phan_cong='NHAP' THEN 'CHUA_DONG_BO' ELSE trang_thai_phan_cong END,
              phien_ban_phan_cong_ap_dung=CASE WHEN ?=1 AND trang_thai_phan_cong='NHAP' THEN NULL ELSE phien_ban_phan_cong_ap_dung END
            WHERE id=? AND phien_ban_ghi=? AND trang_thai IN('NHAP','BI_TRA_LAI')
              AND trang_thai_phan_cong NOT IN('CHO_PHO_PHONG_DUYET','CHO_BGH_KY','CO_HIEU_LUC')
              AND ma_ho_so_duyet_dot IS NULL AND ma_ho_so_tai_lieu_phan_cong IS NULL AND ma_ho_so_trinh_lich IS NULL
              AND ngay_phat_hanh IS NULL AND ngay_huy IS NULL AND ngay_cong_bo_lich IS NULL
            """,input.maHocKy(),input.tenDot(),input.khungNgayBatDau(),input.khungNgayKetThuc(),ranges(input.khungTietChoPhep()),input.hanChotDangKyLich(),now,resetDraft?1:0,resetDraft?1:0,id,token);
        if(changed!=1)throw new DotFailure(409,"VERSION_CONFLICT");em.clear();return find(id,false);
    }
    public List<DoiTuong> audiences(long id){
        tx();return em.createQuery("select a from DoiTuongApDung a where a.maDot=:id order by a.id",DoiTuongApDung.class).setParameter("id",id).getResultList().stream()
            .map(a->a.dto(a.periods()==null?null:ranges(a.periods()))).toList();
    }
    public void replaceAudiences(long id,List<DoiTuong> values,OffsetDateTime now){
        tx();em.flush();em.createQuery("delete from DoiTuongApDung a where a.maDot=:id").setParameter("id",id).executeUpdate();em.clear();
        for(var v:values)em.persist(DoiTuongApDung.create(id,v,v.khungTietChoPhep()==null?null:ranges(v.khungTietChoPhep()),now));em.flush();
    }
    public void audit(long actor,String action,long id){
        tx();jdbc.update("INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,thoi_diem_tao) VALUES(?,N'user',?,?,N'shcn_dot_sinh_hoat',?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",UUID.randomUUID().toString(),actor,action,Long.toString(id));
    }
}
