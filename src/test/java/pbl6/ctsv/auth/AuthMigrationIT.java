package pbl6.ctsv.auth;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class AuthMigrationIT {
    @Test void authSchemaAndSeedUseTheExistingCtsvHistory() throws Exception {
        var jdbc = new JdbcTemplate(AuthTestDatabase.migrate());
        assertThat(count(jdbc,"SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=1")).isEqualTo(6);
        String tables = "('TaiKhoan','VaiTro','ChucNang','TaiKhoan_VaiTro','VaiTro_ChucNang','RefreshToken','NhatKyAuth','LienKetHoSo')";
        // Inventory the migrated dbo objects, excluding temporary schemas used by SQL guard probes.
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.tables WHERE SCHEMA_NAME(schema_id)='dbo' AND name COLLATE Latin1_General_100_BIN2 IN "+tables)).isEqualTo(8);
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.foreign_keys WHERE OBJECT_SCHEMA_NAME(parent_object_id)='dbo' AND is_not_trusted=0 AND OBJECT_NAME(parent_object_id) COLLATE Latin1_General_100_BIN2 IN "+tables)).isEqualTo(12);
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.columns WHERE OBJECT_SCHEMA_NAME(object_id)='dbo' AND OBJECT_NAME(object_id) COLLATE Latin1_General_100_BIN2 IN "+tables)).isEqualTo(56);
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.indexes WHERE OBJECT_SCHEMA_NAME(object_id)='dbo' AND is_primary_key=1 AND OBJECT_NAME(object_id) COLLATE Latin1_General_100_BIN2 IN "+tables)).isEqualTo(8);
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.indexes WHERE OBJECT_SCHEMA_NAME(object_id)='dbo' AND is_unique_constraint=1 AND OBJECT_NAME(object_id) COLLATE Latin1_General_100_BIN2 IN "+tables)).isEqualTo(6);
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.indexes WHERE OBJECT_SCHEMA_NAME(object_id)='dbo' AND name='UX_Link_HienHanh' AND has_filter=1 AND is_unique=1")).isEqualTo(1);
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.check_constraints WHERE OBJECT_SCHEMA_NAME(parent_object_id)='dbo' AND is_not_trusted=1 AND OBJECT_NAME(parent_object_id) COLLATE Latin1_General_100_BIN2 IN "+tables)).isZero();
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.triggers WHERE OBJECT_SCHEMA_NAME(parent_id)='dbo' AND is_disabled=0 AND OBJECT_NAME(parent_id) COLLATE Latin1_General_100_BIN2 IN "+tables)).isEqualTo(6);
        assertThat(count(jdbc,"SELECT COUNT(*) FROM dbo.VaiTro_ChucNang e JOIN dbo.VaiTro r ON r.ID=e.VaiTroID JOIN dbo.ChucNang f ON f.ID=e.ChucNangID WHERE r.MaVaiTro IN ('AUTH_ADMIN','CTSV_USER') AND f.MaChucNang IN ('auth.accounts.manage','auth.rbac.read','ctsv.access')")).isEqualTo(3);
        assertThat(count(jdbc,"SELECT COUNT(*) FROM sys.tables WHERE name=N'PhienDangNhap'")).isZero();
    }
    private int count(JdbcTemplate jdbc,String sql){return jdbc.queryForObject(sql,Integer.class);}
}
