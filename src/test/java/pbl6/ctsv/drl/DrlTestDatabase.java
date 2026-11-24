package pbl6.ctsv.drl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/** SQL Server databases provisioned for the DRL integration tests on DEV2022. */
final class DrlTestDatabase {
    static final String TARGET = "26";
    static final String FRESH = "ctsv_drl_fresh_test";
    static final String UPGRADE = "ctsv_drl_upgrade_test";

    private DrlTestDatabase() {}

    static DataSource source(String name) throws SQLException {
        if (!Boolean.getBoolean("drl.test.enabled")) {
            throw new IllegalStateException("Enable DRL SQL tests with -Ddrl.test.enabled=true");
        }
        if (!FRESH.equals(name) && !UPGRADE.equals(name)) {
            throw new IllegalArgumentException("Unexpected DRL test database: " + name);
        }
        String port = System.getenv("P00_SQL_PORT");
        if (port == null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port) > 65535) {
            throw new IllegalStateException("Set P00_SQL_PORT to the verified DEV2022 TCP port");
        }
        Properties options = new Properties();
        options.setProperty("integratedSecurity", "true");
        options.setProperty("encrypt", "true");
        options.setProperty("trustServerCertificate", "true");
        String url = "jdbc:sqlserver://localhost:" + port + ";databaseName=" + name
                + ";connectRetryCount=0;loginTimeout=5;socketTimeout=30000";
        var ds = new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(), url, options);
        try (Connection c = ds.getConnection(); var statement = c.createStatement();
             var result = statement.executeQuery("""
                     SELECT DB_NAME(), CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')),
                            SUSER_SNAME(owner_sid), ORIGINAL_LOGIN()
                     FROM sys.databases WHERE database_id = DB_ID()
                     """)) {
            if (!result.next() || !name.equals(result.getString(1)) || !"DEV2022".equalsIgnoreCase(result.getString(2))
                    || result.getString(3) == null || !result.getString(3).equalsIgnoreCase(result.getString(4))) {
                throw new IllegalStateException("DRL test database, instance or owner mismatch");
            }
        }
        return ds;
    }

    static Flyway flyway(DataSource source, String version) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(version).baselineOnMigrate(false).cleanDisabled(true).load();
    }
}
