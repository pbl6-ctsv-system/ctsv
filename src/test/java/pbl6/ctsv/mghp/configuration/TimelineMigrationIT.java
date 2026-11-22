package pbl6.ctsv.mghp.configuration;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class TimelineMigrationIT {
    @Test void upgradePreservesLegacyRoundAndDoesNotInventSchedule() throws Exception {
        var ds=TimelineDatabase.source();var j=new JdbcTemplate(ds);
        if(TimelineDatabase.flyway(ds).info().current()==null)
            Flyway.configure().dataSource(ds).cleanDisabled(true).baselineOnMigrate(false).target("16").load().migrate();
        var before=j.queryForList("SELECT version,checksum FROM dbo.flyway_schema_history WHERE success=1 ORDER BY installed_rank");
        String code="TL-LEGACY-"+UUID.randomUUID();
        long semester=j.queryForObject("INSERT dbo.hoc_ky(ma) OUTPUT inserted.id VALUES(?)",Long.class,code);
        long round=j.queryForObject("INSERT dbo.mghp_dot_xet(ma_dot_xet,hoc_ky_id,ten,trang_thai) OUTPUT inserted.id VALUES(?,?,N'Đợt học phí trước nâng cấp',N'draft')",Long.class,code,semester);
        var f=TimelineDatabase.flyway(ds);f.migrate();f.validate();
        assertThat(j.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.mghp_dot_xet') AND name=N'bo_sung_du_kien_tu'",Integer.class)).isEqualTo(1);
        assertThat(j.queryForObject("SELECT ten FROM dbo.mghp_dot_xet WHERE id=?",String.class,round)).isEqualTo("Đợt học phí trước nâng cấp");
        assertThat(j.queryForObject("SELECT bo_sung_du_kien_tu FROM dbo.mghp_dot_xet WHERE id=?",java.time.OffsetDateTime.class,round)).isNull();
        assertThat(j.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lich_su_lich_trinh WHERE dot_xet_id=?",Integer.class,round)).isZero();
        var after=j.queryForList("SELECT version,checksum FROM dbo.flyway_schema_history WHERE success=1 ORDER BY installed_rank");
        assertThat(after).containsAll(before);
        int count=after.size();f.migrate();assertThat(f.info().applied()).hasSize(count);
    }
}
