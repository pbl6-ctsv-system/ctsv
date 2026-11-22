package pbl6.ctsv.mghp.configuration;

import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import static org.assertj.core.api.Assertions.*;

/** Owner-approved isolated target; no DEV writes, cleanup, repair or reset. */
class MergeMigrationIT {
    @Test void mainV19UpgradesToV21WithoutLosingRowsAndAllEntitiesValidate() throws Exception {
        assertThat(TimelineDatabase.NAME).isEqualTo("ctsv_merge_test_20261122");
        var ds=TimelineDatabase.source();var jdbc=new JdbcTemplate(ds);
        var main=Flyway.configure().dataSource(ds).cleanDisabled(true).baselineOnMigrate(false).target("19").load();
        var current=TimelineDatabase.flyway(ds).info().current();
        if(current==null||current.getVersion().compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("19"))<0){main.migrate();main.validate();}
        var before=jdbc.queryForList("SELECT version,checksum,script FROM dbo.flyway_schema_history ORDER BY installed_rank");
        assertThat(before.size()).isIn(19,21);
        String code="MERGE-SYNTHETIC-"+java.util.UUID.randomUUID();
        long semester=jdbc.queryForObject("INSERT dbo.hoc_ky(ma) OUTPUT inserted.id VALUES(?)",Long.class,code);
        long round=jdbc.queryForObject("INSERT dbo.mghp_dot_xet(ma_dot_xet,hoc_ky_id,ten,trang_thai) OUTPUT inserted.id VALUES(?,?,N'Đợt kiểm tương thích migration',N'draft')",Long.class,code,semester);
        var migrated=TimelineDatabase.flyway(ds);assertThat(migrated.migrate().migrationsExecuted).isEqualTo(21-before.size());migrated.validate();
        assertThat(jdbc.queryForObject("SELECT ten FROM dbo.mghp_dot_xet WHERE id=?",String.class,round)).isEqualTo("Đợt kiểm tương thích migration");
        assertThat(jdbc.queryForObject("SELECT bo_sung_du_kien_tu FROM dbo.mghp_dot_xet WHERE id=?",java.time.OffsetDateTime.class,round)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.mghp_dot_xet') AND name IN (N'huong_dan_ke_khai',N'huong_dan_can_cu_nop_muon')",Integer.class)).isZero();
        var after=jdbc.queryForList("SELECT version,checksum,script FROM dbo.flyway_schema_history ORDER BY installed_rank");assertThat(after).hasSize(21).containsAll(before);
        assertThat(migrated.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT version,checksum,script FROM dbo.flyway_schema_history ORDER BY installed_rank")).isEqualTo(after);
        var emf=new LocalContainerEntityManagerFactoryBean();emf.setDataSource(ds);emf.setPackagesToScan("pbl6.ctsv");emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        emf.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.show_sql","false"));
        try {emf.afterPropertiesSet();assertThat(emf.getObject()).isNotNull();}finally{emf.destroy();}
    }
}
