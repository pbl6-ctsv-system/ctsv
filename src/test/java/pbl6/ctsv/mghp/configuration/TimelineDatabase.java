package pbl6.ctsv.mghp.configuration;

import java.util.Properties;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/** Owner-approved test target only. Never creates, cleans, resets or falls back to another DB. */
public final class TimelineDatabase {
    public static final String NAME=System.getProperty("timeline.test.database","ctsv_timeline_test_20261120");
    public static DataSource source() throws Exception {
        if(!Boolean.getBoolean("timeline.test.enabled"))throw new IllegalStateException("Timeline opt-in required");
        boolean merge=NAME.equals("ctsv_merge_test_20261122");
        if(!merge&&!NAME.equals("ctsv_timeline_test_20261120"))throw new IllegalStateException("Unapproved timeline database");
        var port=System.getenv("P00_SQL_PORT");
        if(port==null||!port.matches("[1-9][0-9]{0,4}")||Integer.parseInt(port)>65535)throw new IllegalStateException("Verified SQL TCP port required");
        var props=new Properties();props.setProperty("integratedSecurity","true");
        props.setProperty("encrypt","true");props.setProperty("trustServerCertificate","true");
        var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
            "jdbc:sqlserver://localhost:"+port+";databaseName="+NAME+";connectRetryCount=0;loginTimeout=5;socketTimeout=30000",props);
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("""
            SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),
              IIF(owner_sid=SUSER_SID(),1,0),compatibility_level,collation_name,
              (SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties
               WHERE class=0 AND name=N'timeline_test_owner')
            FROM sys.databases WHERE database_id=DB_ID()
            """)){
            if(!r.next()||!NAME.equals(r.getString(1))||!"DEV2022".equals(r.getString(2))||r.getInt(3)!=1
               ||r.getInt(4)!=160||!"Vietnamese_100_CI_AS_SC".equals(r.getString(5))
               ||!(merge?"ctsv-merge-test-20261122":"ctsv-mghp-timeline-20261120").equals(r.getString(6)))throw new IllegalStateException("Timeline target/instance/owner/marker guard failed");
        }
        // Reject the old private numbering before a test can insert fixtures.
        // Never repair or reset that evidence database to fit the merged chain.
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("""
            IF OBJECT_ID(N'dbo.flyway_schema_history',N'U') IS NOT NULL
              SELECT COUNT(*) FROM dbo.flyway_schema_history
              WHERE script IN (N'V17__mghp_round_timeline.sql',N'V18__mghp_retire_round_guidance.sql')
            ELSE SELECT 0
            """)){
            if(r.next()&&r.getInt(1)>0)throw new IllegalStateException("Legacy private migration numbering: keep this DB intact and use the owner-approved merge test target");
        }
        return ds;
    }
    public static Flyway flyway(DataSource source){return Flyway.configure().dataSource(source).cleanDisabled(true).baselineOnMigrate(false).load();}
    public static DataSource migrate() throws Exception {var ds=source();var f=flyway(ds);f.migrate();f.validate();return ds;}
    private TimelineDatabase() {}
}
