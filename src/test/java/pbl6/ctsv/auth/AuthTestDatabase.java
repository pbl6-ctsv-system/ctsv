package pbl6.ctsv.auth;

import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

final class AuthTestDatabase {
    static final String NAME = "p03_ctsv_test_20261112";
    static DataSource source() throws SQLException {
        if (!Boolean.getBoolean("p03.test.enabled")) throw new IllegalStateException("P03 test opt-in required");
        String port = System.getenv("P00_SQL_PORT");
        if (port == null || !port.matches("[0-9]{1,5}") || Integer.parseInt(port) > 65535)
            throw new IllegalStateException("Local SQL port required");
        var properties = new Properties();
        properties.setProperty("integratedSecurity", "true");
        properties.setProperty("encrypt", "true");
        properties.setProperty("trustServerCertificate", "true");
        var source = new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
            "jdbc:sqlserver://localhost:" + port + ";databaseName=" + NAME + ";connectRetryCount=0;loginTimeout=5;socketTimeout=30000;", properties);
        try (var c = source.getConnection(); var s = c.createStatement(); var r = s.executeQuery("""
            SELECT DB_NAME(), CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')), IS_MEMBER('db_owner'),
              (SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name=N'p03_test_owner'),
              SUSER_SNAME(owner_sid), SUSER_SNAME()
            FROM sys.databases WHERE database_id=DB_ID()
            """)) {
            if (!r.next() || !NAME.equals(r.getString(1)) || !"DEV2022".equals(r.getString(2))
                || r.getInt(3) != 1 || !"ctsv-mghp-p03-20261112".equals(r.getString(4))
                || !java.util.Objects.equals(r.getString(5),r.getString(6)))
                throw new IllegalStateException("P03 database identity or ownership mismatch");
        }
        return source;
    }
    static DataSource migrate() throws SQLException {
        var ds = source();
        var f = Flyway.configure().dataSource(ds).cleanDisabled(true).baselineOnMigrate(false).load();
        f.migrate();
        f.validate();
        return ds;
    }
}
