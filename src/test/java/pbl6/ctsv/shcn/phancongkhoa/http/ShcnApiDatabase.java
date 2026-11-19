package pbl6.ctsv.shcn.phancongkhoa.http;

import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

final class ShcnApiDatabase {
    static final String NAME="ctsv_shcn_api_test_20261119";
    static DataSource source() throws Exception {
        if(!Boolean.getBoolean("shcn.api.test.enabled"))throw new IllegalStateException("SHCN API test opt-in required");
        String port=System.getenv("P00_SQL_PORT");
        if(port==null||!port.matches("[1-9][0-9]{0,4}")||Integer.parseInt(port)>65535)throw new IllegalStateException("Verified SQL port required");
        var props=new Properties();props.setProperty("integratedSecurity","true");props.setProperty("encrypt","true");props.setProperty("trustServerCertificate","true");
        var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),"jdbc:sqlserver://localhost:"+port+";databaseName="+NAME+";connectRetryCount=0;loginTimeout=5;socketTimeout=30000",props);
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),SUSER_SNAME(owner_sid),SUSER_SNAME(),compatibility_level,collation_name,(SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name='shcn_api_owner') FROM sys.databases WHERE database_id=DB_ID()")){
            if(!r.next()||!NAME.equals(r.getString(1))||!"DEV2022".equals(r.getString(2))||!Objects.equals(r.getString(3),r.getString(4))||r.getInt(5)!=160||!"Vietnamese_100_CI_AS_SC".equals(r.getString(6))||!"ctsv-shcn-step3-20261119".equals(r.getString(7)))throw new IllegalStateException("SHCN API exact target/owner/marker mismatch");
        }
        var f=Flyway.configure().dataSource(ds).cleanDisabled(true).baselineOnMigrate(false).load();f.migrate();f.validate();return ds;
    }
    private ShcnApiDatabase(){}
}
