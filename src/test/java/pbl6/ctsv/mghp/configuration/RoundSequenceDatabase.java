package pbl6.ctsv.mghp.configuration;

import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/** Exact owner-approved target. Never creates, cleans, resets or falls back to DEV/evidence. */
public final class RoundSequenceDatabase {
    public static final String NAME = "ctsv_round_sequence_test_20261125";
    public static DataSource migrate() throws Exception {
        if (!Boolean.getBoolean("round.sequence.test.enabled")) throw new IllegalStateException("Round sequence opt-in required");
        String port = System.getenv("P00_SQL_PORT");
        if (port == null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port) > 65535)
            throw new IllegalStateException("Verified SQL port required");
        var properties = new Properties();
        properties.setProperty("integratedSecurity", "true");
        properties.setProperty("encrypt", "true");
        properties.setProperty("trustServerCertificate", "true");
        var source = new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
                "jdbc:sqlserver://localhost:" + port + ";databaseName=" + NAME + ";connectRetryCount=0;loginTimeout=5;socketTimeout=30000", properties);
        try (var connection = source.getConnection(); var statement = connection.createStatement(); var row = statement.executeQuery("""
                SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),IIF(owner_sid=SUSER_SID(),1,0),
                    compatibility_level,collation_name,(SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties
                    WHERE class=0 AND name=N'round_sequence_test_owner') FROM sys.databases WHERE database_id=DB_ID()
                """)) {
            if (!row.next() || !NAME.equals(row.getString(1)) || !"DEV2022".equals(row.getString(2)) || row.getInt(3) != 1
                    || row.getInt(4) != 160 || !"Vietnamese_100_CI_AS_SC".equals(row.getString(5))
                    || !"ctsv-mghp-round-sequence-20261125".equals(row.getString(6)))
                throw new IllegalStateException("Round sequence database/instance/owner/marker guard failed");
        }
        var flyway = Flyway.configure().dataSource(source).cleanDisabled(true).baselineOnMigrate(false).load();
        flyway.migrate();
        flyway.validate();
        return source;
    }
    private RoundSequenceDatabase() {}
}
