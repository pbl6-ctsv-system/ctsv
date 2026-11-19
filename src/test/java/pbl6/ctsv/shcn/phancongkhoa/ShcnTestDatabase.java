package pbl6.ctsv.shcn.phancongkhoa;

import java.util.Objects;
import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/** Test-only target guard. No DEV fallback, database creation, clean or global fixture cleanup. */
final class ShcnTestDatabase {
    static final String UPGRADE = "ctsv_shcn_persistence_test_20261118";
    static final String FRESH = "ctsv_shcn_fresh_test_20261118";

    static DataSource source(String name) throws Exception {
        if (!Boolean.getBoolean("shcn.test.enabled")) throw new IllegalStateException("SHCN test opt-in required");
        if (!UPGRADE.equals(name) && !FRESH.equals(name)) throw new IllegalArgumentException("SHCN test target not allowed");
        String port = System.getenv("P00_SQL_PORT");
        if (port == null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port) > 65535)
            throw new IllegalStateException("Verified SQL TCP port required");
        var options = new Properties();
        options.setProperty("integratedSecurity", "true");
        options.setProperty("encrypt", "true");
        options.setProperty("trustServerCertificate", "true");
        var ds = new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
            "jdbc:sqlserver://localhost:" + port + ";databaseName=" + name
                + ";connectRetryCount=0;loginTimeout=5;socketTimeout=30000", options);
        try (var c = ds.getConnection(); var s = c.createStatement(); var r = s.executeQuery("""
            SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),
              SUSER_SNAME(owner_sid),SUSER_SNAME(),compatibility_level,collation_name,
              (SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties
               WHERE class=0 AND name=N'shcn_persistence_owner')
            FROM sys.databases WHERE database_id=DB_ID()
            """)) {
            if (!r.next() || !name.equals(r.getString(1)) || !"DEV2022".equals(r.getString(2))
                || !Objects.equals(r.getString(3), r.getString(4)) || r.getInt(5) != 160
                || !"Vietnamese_100_CI_AS_SC".equals(r.getString(6))
                || !"ctsv-shcn-step2-20261118".equals(r.getString(7)))
                throw new IllegalStateException("SHCN exact target/owner/marker mismatch");
        }
        return ds;
    }

    static Flyway flyway(DataSource source, String target) {
        var config = Flyway.configure().dataSource(source).cleanDisabled(true).baselineOnMigrate(false);
        if (target != null) config.target(target);
        return config.load();
    }

    private ShcnTestDatabase() {}
}
