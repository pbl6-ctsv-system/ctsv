package pbl6.ctsv.mghp.configuration;
import com.fasterxml.jackson.databind.*;import java.sql.*;import java.time.*;import java.util.*;import javax.sql.DataSource;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DataSourceTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
/** SYNTHETIC ONLY. Resolves source aliases to actual PKs; never copies these rows into dev/production. */
public final class P05Fixtures {
 static final ObjectMapper JSON=new ObjectMapper();
 public static final List<String> TABLES=List.of("mghp_chinh_sach","mghp_phien_ban_chinh_sach","mghp_nhom_yeu_cau_giay_to","mghp_giay_to_bat_buoc","mghp_chinh_sach_dot_xet","mghp_dot_xet","mghp_tieu_chi_chinh_sach");
 public static JsonNode seed(DataSource ds)throws Exception{
  P05Database.source();return seedVerified(ds,false);
 }
 public static JsonNode seedForJpaRefactor()throws Exception{
  return seedVerified(pbl6.ctsv.foundation.persistence.RefactorDatabase.source(),true);
 }
 private static JsonNode seedVerified(DataSource ds,boolean appendRefactor)throws Exception{
  var j=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  return tx.execute(status->{try{
   j.queryForObject("DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'P05 fixture ownership',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000; SELECT @r",Integer.class);
   var old=j.queryForList("SELECT CONVERT(nvarchar(max),value) value FROM sys.extended_properties WHERE class=0 AND name=N'p05_cfg_aliases'");if(!old.isEmpty())return JSON.readTree(old.getFirst().get("value").toString());
   if(!appendRefactor&&j.queryForObject("SELECT COUNT(*) FROM dbo.mghp_phien_ban_chinh_sach",Integer.class)!=0)throw new IllegalStateException("Unowned existing P05 config; no reset");
   JsonNode data;try(var in=P05Fixtures.class.getResourceAsStream("/fixtures/p05/configuration.json")){data=JSON.readTree(in);}
   var maps=new LinkedHashMap<String,Map<Long,Long>>();for(String key:TABLES)maps.put(key,new LinkedHashMap<>());maps.put("users",new LinkedHashMap<>());maps.put("semesters",new LinkedHashMap<>());maps.put("notifications",new LinkedHashMap<>());
   for(long alias:new long[]{11,12})maps.get("users").put(alias,id(j,"nguoi_dung",new LinkedHashMap<>(Map.of("dia_chi_email","p05.fixture."+alias+"@example.invalid","ho_ten","SYNTHETIC fixture actor","dang_hoat_dong",true))));
   int orderBase=appendRefactor?j.queryForObject("SELECT COALESCE(MAX(so_thu_tu),0) FROM dbo.hoc_ky",Integer.class):0;
   for(long alias:new long[]{901,902,903}){var v=new LinkedHashMap<String,Object>();v.put("ma","P05-FIXTURE-H"+alias);v.put("ten","SYNTHETIC semester");v.put("so_thu_tu",orderBase+(int)(alias-900));v.put("ngay_bat_dau",java.sql.Date.valueOf("2026-09-01"));v.put("ngay_ket_thuc",java.sql.Date.valueOf("2027-01-31"));v.put("trang_thai_xac_minh_moc","verified");v.put("can_cu_xac_minh_moc","SYNTHETIC TEST-CALENDAR-01; no real school verification");maps.get("semesters").put(alias,id(j,"hoc_ky",v));}
   for(var row:data.path("mghp_dot_xet"))if(!row.path("thong_bao_dot_xet_id").isNull()){long alias=row.path("thong_bao_dot_xet_id").asLong();if(!maps.get("notifications").containsKey(alias))maps.get("notifications").put(alias,id(j,"thong_bao",new LinkedHashMap<>(Map.of("ma_phan_he","MGHP","tieu_de","SYNTHETIC fixture notice","trang_thai","published"))));}
   for(var row:data.path("mghp_chinh_sach"))maps.get("mghp_chinh_sach").put(row.path("id").asLong(),j.queryForObject("SELECT id FROM dbo.mghp_chinh_sach WHERE ma=?",Long.class,row.path("ma").asText()));
   for(String table:List.of("mghp_phien_ban_chinh_sach","mghp_nhom_yeu_cau_giay_to","mghp_giay_to_bat_buoc","mghp_tieu_chi_chinh_sach","mghp_dot_xet","mghp_chinh_sach_dot_xet")){
    for(var row:data.path(table)){var v=new LinkedHashMap<String,Object>();for(var fields=row.fields();fields.hasNext();){var e=fields.next();String key=e.getKey();if(key.equals("id"))continue;var n=e.getValue();Object value=n.isNull()?null:n.isBoolean()?n.booleanValue():n.isNumber()?n.decimalValue():n.asText();
     String parent=switch(key){case "chinh_sach_id"->"mghp_chinh_sach";case "phien_ban_chinh_sach_id"->"mghp_phien_ban_chinh_sach";case "nhom_id"->"mghp_nhom_yeu_cau_giay_to";case "dot_xet_id"->"mghp_dot_xet";case "hoc_ky_id"->"semesters";case "thong_bao_dot_xet_id"->"notifications";case "nguoi_tao_id","nguoi_khoa_id","nguoi_khoa_cau_hinh_id","nguoi_chot_hoi_dong_id"->"users";default->null;};
     if(value!=null&&parent!=null){value=maps.get(parent).get(n.asLong());if(value==null)throw new IllegalStateException("Fixture FK alias missing: "+key);}
     if(table.equals("mghp_phien_ban_chinh_sach")&&(key.equals("thoi_diem_khoa")||key.equals("tinh_trang_xac_nhan_quy_tac")))value=null;
     if(table.equals("mghp_dot_xet")&&(key.equals("thoi_diem_khoa_cau_hinh")||key.equals("nguoi_khoa_cau_hinh_id")))value=null;
     if(key.equals("trang_thai"))value="draft";
     v.put(key,value);
    }
    if(appendRefactor&&table.equals("mghp_phien_ban_chinh_sach"))v.put("phien_ban",j.queryForObject("SELECT COALESCE(MAX(phien_ban),0)+1 FROM dbo.mghp_phien_ban_chinh_sach WHERE chinh_sach_id=?",Integer.class,v.get("chinh_sach_id")));
    maps.get(table).put(row.path("id").asLong(),id(j,table,v));}
   }
   for(String table:List.of("mghp_phien_ban_chinh_sach","mghp_dot_xet"))for(var row:data.path(table)){
    long id=maps.get(table).get(row.path("id").asLong());if(table.equals("mghp_phien_ban_chinh_sach"))j.update("UPDATE dbo.mghp_phien_ban_chinh_sach SET trang_thai=?,thoi_diem_khoa=?,tinh_trang_xac_nhan_quy_tac=? WHERE id=?",row.path("trang_thai").asText(),nullable(row,"thoi_diem_khoa"),nullable(row,"tinh_trang_xac_nhan_quy_tac"),id);
    else j.update("UPDATE dbo.mghp_dot_xet SET trang_thai=?,thoi_diem_khoa_cau_hinh=?,nguoi_khoa_cau_hinh_id=? WHERE id=?",row.path("trang_thai").asText(),nullable(row,"thoi_diem_khoa_cau_hinh"),row.path("nguoi_khoa_cau_hinh_id").isNull()?null:maps.get("users").get(row.path("nguoi_khoa_cau_hinh_id").asLong()),id);
   }
   String serialized=JSON.writeValueAsString(maps);j.update("EXEC sys.sp_addextendedproperty @name=N'p05_cfg_aliases',@value=?",serialized);return JSON.readTree(serialized);
  }catch(Exception e){throw new IllegalStateException("P05 synthetic seed failed",e);}});
 }
 static Object nullable(JsonNode n,String k){return n.path(k).isNull()?null:n.path(k).asText();}
 public static long id(JdbcTemplate j,String table,LinkedHashMap<String,Object> values){String cols=String.join(",",values.keySet().stream().map(s->"["+s+"]").toList());String marks=String.join(",",Collections.nCopies(values.size(),"?"));return j.queryForObject("DECLARE @i TABLE(id bigint);INSERT dbo."+table+"("+cols+") OUTPUT INSERTED.id INTO @i VALUES("+marks+");SELECT id FROM @i",Long.class,values.values().toArray());}
 public static long alias(JsonNode aliases,String table,long sourceId){long id=aliases.path(table).path(Long.toString(sourceId)).asLong();if(id<1)throw new IllegalStateException("Missing alias");return id;}
}
