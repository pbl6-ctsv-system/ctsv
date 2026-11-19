package pbl6.ctsv.shcn.phancongkhoa;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class ShcnSchemaIT {
    @Test void freshMigrationCreatesBothFacultyAssignmentTables() throws Exception {
        var source = ShcnTestDatabase.source(ShcnTestDatabase.FRESH);
        var flyway = ShcnTestDatabase.flyway(source, null);
        flyway.migrate();
        flyway.validate();
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
            WHERE s.name='shcn' AND t.name IN ('shcn_danh_sach_phan_cong_khoa','shcn_phan_cong_gvcn_khoa')
            """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='14' AND success=1", Integer.class)).isEqualTo(1);
    }

    @Test void upgradeFromV13PreservesAllExistingDboColumnMappings() throws Exception {
        var source = ShcnTestDatabase.source(ShcnTestDatabase.UPGRADE);
        var jdbc = new JdbcTemplate(source);
        boolean alreadyUpgraded = jdbc.queryForObject("SELECT CASE WHEN OBJECT_ID('shcn.shcn_danh_sach_phan_cong_khoa') IS NULL THEN 0 ELSE 1 END", Integer.class) == 1;
        if (!alreadyUpgraded) ShcnTestDatabase.flyway(source, "13").migrate();
        String query = """
            SELECT t.name table_name,c.name column_name,c.column_id,ty.name type_name,
              c.max_length,c.precision,c.scale,c.is_nullable,c.is_identity,c.is_computed
            FROM sys.tables t JOIN sys.schemas s ON s.schema_id=t.schema_id
            JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types ty ON ty.user_type_id=c.user_type_id
            WHERE s.name='dbo' AND t.name<>'flyway_schema_history' ORDER BY t.name,c.column_id
            """;
        var before = jdbc.queryForList(query);
        assertThat(before).isNotEmpty();
        var flyway = ShcnTestDatabase.flyway(source, null);
        flyway.migrate(); flyway.validate();
        assertThat(jdbc.queryForList(query)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='14' AND success=1", Integer.class)).isEqualTo(1);
    }
}
