package pbl6.ctsv.mghp.configuration;
import java.sql.*;import java.util.*;import javax.sql.DataSource;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.flywaydb.core.Flyway;
public final class P05Database {
 public static final String NAME="p05_ctsv_test_20261113";
 public static DataSource source()throws Exception{
  if(!Boolean.getBoolean("p05.test.enabled"))throw new IllegalStateException("P05 opt-in required");
  String port=System.getenv("P00_SQL_PORT");if(port==null||!port.matches("[1-9][0-9]{0,4}")||Integer.parseInt(port)>65535)throw new IllegalStateException("Verified SQL TCP port required");
  var props=new Properties();props.setProperty("integratedSecurity","true");props.setProperty("encrypt","true");props.setProperty("trustServerCertificate","true");
  var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),"jdbc:sqlserver://localhost:"+port+";databaseName="+NAME+";connectRetryCount=0;loginTimeout=5;socketTimeout=30000",props);
  try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),SUSER_SNAME(owner_sid),SUSER_SNAME(),compatibility_level,collation_name,(SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name='p05_test_owner') FROM sys.databases WHERE database_id=DB_ID()")){
   if(!r.next()||!NAME.equals(r.getString(1))||!"DEV2022".equals(r.getString(2))||!Objects.equals(r.getString(3),r.getString(4))||r.getInt(5)!=160||!"Vietnamese_100_CI_AS_SC".equals(r.getString(6))||!"ctsv-mghp-p05-20261113".equals(r.getString(7)))throw new IllegalStateException("P05 exact target/owner/marker mismatch");
  }return ds;
 }
 public static DataSource migrate()throws Exception{var ds=source();var f=Flyway.configure().dataSource(ds).cleanDisabled(true).baselineOnMigrate(false).load();f.migrate();f.validate();return ds;}
}
