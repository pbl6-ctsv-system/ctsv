package pbl6.ctsv.mghp.application;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.flywaydb.core.Flyway;

/** Fail-closed target guard. Never creates, cleans, drops or falls back to DEV. */
public final class SigningDatabase {
 public static DataSource source()throws Exception{
  if(!Boolean.getBoolean("p11.test.enabled"))throw new IllegalStateException("P11 test opt-in required");
  String port=System.getenv("P00_SQL_PORT");if(port==null||!port.matches("[1-9][0-9]{0,4}")||Integer.parseInt(port)>65535)throw new IllegalStateException("Verified port required");
  var props=new Properties();props.setProperty("integratedSecurity","true");props.setProperty("encrypt","true");props.setProperty("trustServerCertificate","true");
  var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),"jdbc:sqlserver://localhost:"+port+";databaseName=ctsv_p11_test_20261123;connectRetryCount=0;loginTimeout=5;socketTimeout=30000",props);
  try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("""
   SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),IIF(owner_sid=SUSER_SID(),1,0),compatibility_level,collation_name,
   (SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name=N'p11_test_owner')
   FROM sys.databases WHERE database_id=DB_ID()
   """)){
   if(!r.next()||!"ctsv_p11_test_20261123".equals(r.getString(1))||!"DEV2022".equals(r.getString(2))||r.getInt(3)!=1||r.getInt(4)!=160||!"Vietnamese_100_CI_AS_SC".equals(r.getString(5))||!"ctsv-p11-20261123".equals(r.getString(6)))throw new IllegalStateException("P11 target/instance/owner/marker mismatch");
  }return ds;
 }
 public static DataSource migrate()throws Exception{var ds=source();var f=Flyway.configure().dataSource(ds).cleanDisabled(true).baselineOnMigrate(false).load();f.migrate();f.validate();return ds;}
}
