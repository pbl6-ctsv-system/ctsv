package pbl6.ctsv.hocbong.kkht.round;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;
import java.util.Properties;

/** SQL Server test target for an HB database provisioned and owned by the user. */
public final class ScholarshipTestDatabase {
    public static final String NAME = "ctsv_hocbong_test";
    private static final String INSTANCE = "DEV2022";
    private static final String MARKER = "ctsv-hoc-bong-t01-20261119";

    private ScholarshipTestDatabase() {}

    public static DataSource source() throws Exception {
        if (!Boolean.getBoolean("hocbong.test.enabled")) {
            throw new IllegalStateException("HB SQL tests require -Dhocbong.test.enabled=true");
        }
        String port = System.getenv("P00_SQL_PORT");
        if (port == null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port) > 65535) {
            throw new IllegalStateException("HB SQL tests require a verified DEV2022 P00_SQL_PORT");
        }

        var options = new Properties();
        options.setProperty("integratedSecurity", "true");
        options.setProperty("encrypt", "true");
        options.setProperty("trustServerCertificate", "true");
        var dataSource = new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
                "jdbc:sqlserver://localhost:" + port + ";databaseName=" + NAME
                        + ";connectRetryCount=0;loginTimeout=5;socketTimeout=30000", options);

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT DB_NAME(), CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')),
                            SUSER_SNAME(owner_sid), ORIGINAL_LOGIN(), compatibility_level, collation_name,
                            (SELECT CONVERT(nvarchar(128), value) FROM sys.extended_properties
                             WHERE class = 0 AND name = N'hoc_bong_test_owner')
                     FROM sys.databases WHERE database_id = DB_ID()
                     """)) {
            if (!result.next() || !NAME.equals(result.getString(1)) || !INSTANCE.equals(result.getString(2))
                    || result.getString(3) == null || !result.getString(3).equals(result.getString(4))
                    || result.getInt(5) != 160 || !"Vietnamese_100_CI_AS_SC".equals(result.getString(6))
                    || !MARKER.equals(result.getString(7))) {
                throw new IllegalStateException("HB SQL test database, instance, owner, collation, compatibility or marker mismatch");
            }
        }
        return dataSource;
    }

    public static DataSource migrate() throws Exception {
        var dataSource = source();
        var flyway = Flyway.configure().dataSource(dataSource).cleanDisabled(true).baselineOnMigrate(false).load();
        flyway.migrate();
        flyway.validate();
        return dataSource;
    }
}
