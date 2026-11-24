package pbl6.ctsv.shcn.dotsinhhoat.repository;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.shcn.dotsinhhoat.dto.MauDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.entity.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;

public class MauRepository {
 private final EntityManager em;private final JdbcTemplate jdbc;private final ObjectMapper json;
 public MauRepository(EntityManager em,JdbcTemplate jdbc,ObjectMapper json){this.em=em;this.jdbc=jdbc;this.json=json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);}
 private static void tx(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("SHCN template requires transaction");}
 public MauBienBan find(long dot){tx();return em.createQuery("select m from MauBienBan m where m.maDot=:id",MauBienBan.class).setParameter("id",dot).getResultList().stream().findFirst().orElse(null);}
 private List<MucBienBan> rows(long template){return em.createQuery("select m from MucBienBan m where m.maMau=:id order by m.thuTu",MucBienBan.class).setParameter("id",template).getResultList();}
 public List<Muc> sections(long template){tx();return rows(template).stream().map(m->{try{return m.dto(List.of(json.readValue(m.fields(),Truong[].class)));}catch(java.io.IOException e){throw new IllegalStateException("Invalid stored SHCN template configuration",e);}}).toList();}
 public void compareAndTouch(long dot,byte[] version,OffsetDateTime now){
  tx();em.flush();int changed=jdbc.update("""
   UPDATE shcn.shcn_dot_sinh_hoat SET ngay_cap_nhat=?
   WHERE id=? AND phien_ban_ghi=? AND trang_thai IN('NHAP','BI_TRA_LAI')
    AND trang_thai_phan_cong NOT IN('CHO_PHO_PHONG_DUYET','CHO_BGH_KY','CO_HIEU_LUC')
    AND ma_ho_so_duyet_dot IS NULL AND ma_ho_so_tai_lieu_phan_cong IS NULL AND ma_ho_so_trinh_lich IS NULL
    AND ngay_phat_hanh IS NULL AND ngay_huy IS NULL AND ngay_cong_bo_lich IS NULL
   """,now,dot,version);
  if(changed!=1)throw new DotFailure(409,"VERSION_CONFLICT");em.clear();
 }
 public void save(long dot,Input input,String actor,OffsetDateTime now){
  tx();var template=find(dot);if(template==null){template=MauBienBan.create(dot,input.tenMau(),actor,now);em.persist(template);em.flush();}
  else{if("DA_KHOA".equals(template.state()))throw new DotFailure(409,"STATE_CONFLICT");template.configure(input.tenMau(),actor,now);em.flush();}
  var old=rows(template.id());var byKey=new HashMap<String,MucBienBan>();int max=old.stream().mapToInt(MucBienBan::order).max().orElse(0);
  if((long)max+old.size()+1>Integer.MAX_VALUE)throw new IllegalStateException("Invalid stored section order");
  for(int i=0;i<old.size();i++){var row=old.get(i);row.temporaryOrder(max+i+1);em.flush();byKey.put(row.key(),row);}
  var wanted=new HashSet<String>();for(var m:input.muc())wanted.add(m.maMuc());for(var row:old)if(!wanted.contains(row.key()))em.remove(row);em.flush();
  for(var m:input.muc()){
   final String fields;try{fields=json.writeValueAsString(m.cauHinhTruong());}catch(java.io.IOException e){throw new IllegalStateException("Cannot serialize template",e);}
   var row=byKey.get(m.maMuc());if(row==null)em.persist(MucBienBan.create(template.id(),m,fields,now));else row.configure(m,fields,now);
  }
  em.flush();em.clear();
 }
}
