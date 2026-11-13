package pbl6.ctsv.foundation.sync;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FoundationSchemaIT {
    @Test void migratedResourceTablesExposePresenceWithoutReplacingSourceOrLocalKeys() throws Exception {
        P02Database.migrate();
        try(var c=P02Database.open()) {
            assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM sys.columns WHERE name=N'dang_hien_dien_nguon' AND object_id IN (OBJECT_ID('dbo.dan_toc'),OBJECT_ID('dbo.quoc_tich'),OBJECT_ID('dbo.dia_phuong'),OBJECT_ID('dbo.hoc_ky'),OBJECT_ID('dbo.khoa_hoc'),OBJECT_ID('dbo.nganh_quoc_gia'),OBJECT_ID('dbo.nganh_noi_bo'),OBJECT_ID('dbo.chuong_trinh_dao_tao'),OBJECT_ID('dbo.lop_sinh_hoat'),OBJECT_ID('dbo.sinh_vien'),OBJECT_ID('dbo.du_lieu_hoc_vu_dong_bo'))")).isEqualTo(11);
        }
    }
    @Test void presenceMigrationKeepsNonemptyRowsAndProbeRollbackRemovesOnlyOwnedSchema()throws Exception {
        P02Database.migrate();String probe="p02_presence_"+java.util.UUID.randomUUID().toString().replace("-","");
        String ddl=new org.springframework.core.io.ClassPathResource("db/migration/V5__foundation_resource_presence.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8).replace("dbo.[",probe+".[");
        var tables=FoundationResourceCatalog.all().stream().filter(r->java.util.Set.of("ethnicities","nationalities","localities","plans","cohorts","national-majors","internal-majors","programs","classes","students","academic-results").contains(r.resource())).toList();
        try(var c=P02Database.open()){
            c.setAutoCommit(false);try{
                try(var s=c.createStatement()){
                    s.execute("CREATE SCHEMA ["+probe+"]");
                    for(var r:tables)s.execute("CREATE TABLE ["+probe+"].["+r.target()+"](id bigint PRIMARY KEY,note nvarchar(128));INSERT ["+probe+"].["+r.target()+"] VALUES(1,N'RETAINED')");
                    s.execute(ddl);
                }
                for(var r:tables)assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM ["+probe+"].["+r.target()+"] WHERE id=1 AND note=N'RETAINED' AND dang_hien_dien_nguon=1")).isEqualTo(1);
            }finally{c.rollback();c.setAutoCommit(true);}
            assertThat(P02Database.scalar(c,"SELECT COUNT(*) FROM sys.schemas WHERE name=N'"+probe+"'")).isZero();
        }
    }
}
