package pbl6.p00;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
import static pbl6.p00.SliceATestDatabaseSupport.*;

class MigrationIT {
    @BeforeAll static void migrateApprovedTarget() throws Exception {
        var f=flyway();
        if(f.info().current()==null) {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource()).locations("classpath:db/migration")
                    .target("1").baselineOnMigrate(false).cleanDisabled(true).load().migrate();
            System.out.println("P01-MIGRATION CTSV applied V1 before upgrade fixture");
        }
        try(var c=open();var s=c.createStatement()){
            s.executeUpdate("IF NOT EXISTS(SELECT 1 FROM dbo.don_vi WHERE ma=N'P01-LOCAL-BASELINE') INSERT dbo.don_vi(ma,ten,luu_tru_nguon,is_us_nguon,dang_hoat_dong) VALUES(N'P01-LOCAL-BASELINE',N'Fixture P01',N'raw',NULL,1)");
        }
        migrate();
    }
    @Test void sa001_preservesInventoryAndLocalData() throws Exception {
        try(var c=open()){
            assertThat(scalar(c,"SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0 AND name<>'flyway_schema_history'")).isEqualTo(99);
            assertThat(scalar(c,"SELECT COUNT(*) FROM sys.foreign_keys")).isEqualTo(342);
            assertThat(scalar(c,"SELECT COUNT(*) FROM dbo.don_vi WHERE ma=N'P01-LOCAL-BASELINE' AND luu_tru_nguon=N'raw' AND is_us_nguon IS NULL AND dang_hoat_dong=1")).isEqualTo(1);
            assertThat(scalar(c,"SELECT max_length FROM sys.columns WHERE object_id=OBJECT_ID('dbo.don_vi') AND name='luu_tru_nguon'")).isEqualTo(1024);
        }
    }
    @Test void sa001_sourceStatusIsSeparateNullableAndUniqueIndexesPreserved() throws Exception {
        try(var c=open()){
            assertThat(scalar(c,"SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.don_vi') AND name='dang_hoat_dong_nguon' AND is_nullable=1")).as("source active column").isEqualTo(1);
            assertThat(scalar(c,"SELECT COUNT(*) FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.don_vi') AND is_unique=1")).isEqualTo(3);
            assertThat(scalar(c,"SELECT COUNT(*) FROM dbo.don_vi WHERE ma=N'P01-LOCAL-BASELINE' AND dang_hoat_dong_nguon IS NULL")).isEqualTo(1);
        }
    }
    @Test void migrationIsIdempotentAndChecksumsValidate() throws Exception {
        var f=flyway();f.validate();assertThat(f.migrate().migrationsExecuted).isZero();
        assertThat(f.info().current().getVersion().getVersion()).isEqualTo("2");
    }
}

