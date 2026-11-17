package pbl6.ctsv.auth;

import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

final class P06Database {
    private static final String NAME = "ctsv_p06_test_20261114";

    static DataSource migrate() throws Exception {
        if (!Boolean.getBoolean("p06.test.enabled")) throw new IllegalStateException("P06 opt-in required");
        String port=System.getenv("P00_SQL_PORT");
        if (port==null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port)>65535) {
            throw new IllegalStateException("Verified SQL TCP port required");
        }
        var properties=new Properties();
        properties.setProperty("integratedSecurity","true");
        properties.setProperty("encrypt","true");
        properties.setProperty("trustServerCertificate","true");
        var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
            "jdbc:sqlserver://localhost:"+port+";databaseName="+NAME+";connectRetryCount=0;loginTimeout=5;socketTimeout=30000",properties);
        try (var c=ds.getConnection(); var s=c.createStatement(); var r=s.executeQuery("""
            SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),
                   IIF(owner_sid=SUSER_SID(),1,0),compatibility_level,collation_name,
                   (SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name='p06_test_owner')
            FROM sys.databases WHERE database_id=DB_ID()
            """)) {
            if (!r.next() || !NAME.equals(r.getString(1)) || !"DEV2022".equals(r.getString(2)) || r.getInt(3)!=1
                || r.getInt(4)!=160 || !"Vietnamese_100_CI_AS_SC".equals(r.getString(5))
                || !"ctsv-mghp-p06-20261114".equals(r.getString(6))) throw new IllegalStateException("P06 SQL guard failed");
        }
        var current=Flyway.configure().dataSource(ds).baselineOnMigrate(false).cleanDisabled(true).load();
        // On first use, provision V8 explicitly so V9 is exercised as an upgrade.
        if (current.info().current()==null) {
            Flyway.configure().dataSource(ds).target("8").baselineOnMigrate(false).cleanDisabled(true).load().migrate();
        }
        current.migrate();
        current.validate();
        return ds;
    }
}
