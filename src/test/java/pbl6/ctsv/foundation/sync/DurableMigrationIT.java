package pbl6.ctsv.foundation.sync;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DurableMigrationIT {
 @Test void additiveMigrationCreatesDurableStateWithoutReplacingExistingUnits()throws Exception{
  P02Database.baseline();
  try(var c=P02Database.open()){
   long before=P02Database.scalar(c,"SELECT COUNT(*) FROM dbo.don_vi");
   P02Database.migrate();
   assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM sys.tables WHERE name IN(N'dong_bo_trang_thai',N'dong_bo_luot',N'dong_bo_da_thay',N'dong_bo_trang_tam',N'dong_bo_hien_dien')")).isEqualTo(5);
   assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM dbo.don_vi")).isEqualTo(before);
  }
 }

 @Test void exactTechnicalDdlSupportsNonemptyUpgradeAndTransactionalRollback()throws Exception {
  P02Database.migrate();String schema="p02_probe_"+java.util.UUID.randomUUID().toString().replace("-","");
  long source;try(var c=P02Database.open();var q=c.prepareStatement("INSERT dbo.he_thong_ngoai(ma,ten) VALUES(?,N'P02 migration fixture');SELECT CAST(SCOPE_IDENTITY() AS bigint)")){q.setString(1,"P02-MIG-"+schema);try(var r=q.executeQuery()){r.next();source=r.getLong(1);}}
  try(var c=P02Database.open()){
   c.setAutoCommit(false);try{
    try(var q=c.createStatement()){q.execute("CREATE SCHEMA ["+schema+"]");}
    for(String script:java.util.List.of("V3__durable_resource_sync.sql","V4__pin_sync_export_metadata.sql")){
     if(script.startsWith("V4"))try(var q=c.createStatement()){q.execute("INSERT "+schema+".dong_bo_trang_thai(he_thong_nguon_id,tai_nguyen,source_epoch,ct_version) VALUES("+source+",N'ethnicities','11111111-aaaa-4444-bbbb-111111111111',7)");}
     String ddl=new org.springframework.core.io.ClassPathResource("db/migration/"+script).getContentAsString(java.nio.charset.StandardCharsets.UTF_8).replace("dbo.dong_bo",schema+".dong_bo");
     for(String batch:ddl.split("(?im)^GO\\s*$"))if(!batch.isBlank())try(var statement=c.createStatement()){statement.execute(batch);}

    }
    assertThat(P02Database.scalar(c,"SELECT ct_version FROM "+schema+".dong_bo_trang_thai WHERE he_thong_nguon_id="+source)).isEqualTo(7);
    assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID('"+schema+"')")).isEqualTo(5);
   }finally{c.rollback();c.setAutoCommit(true);}
   assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM sys.schemas WHERE name='"+schema+"'")).isZero();
   assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM dbo.he_thong_ngoai WHERE id="+source)).isEqualTo(1);
  }
 }
}
