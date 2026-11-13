package pbl6.ctsv.foundation.sync;
import java.sql.*;import java.util.*;import javax.sql.DataSource;import org.flywaydb.core.Flyway;import org.springframework.jdbc.datasource.SimpleDriverDataSource;
public final class P02Database {
 public static final String NAME="p02_ctsv_test_20261112";
 public static DataSource source()throws SQLException {
  if(!"test".equals(System.getProperty("spring.profiles.active"))||!Boolean.getBoolean("p02.test.enabled"))throw new IllegalStateException("Explicit P02 test opt-in required");
  String port=System.getenv("P00_SQL_PORT");if(port==null||!port.matches("[1-9][0-9]{0,4}")||Integer.parseInt(port)>65535)throw new IllegalStateException("Verified port required");
  var p=new Properties();p.setProperty("integratedSecurity","true");p.setProperty("encrypt","true");p.setProperty("trustServerCertificate","true");p.setProperty("connectRetryCount","0");p.setProperty("socketTimeout","45000");
  var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),"jdbc:sqlserver://localhost:"+port+";databaseName="+NAME+";connectRetryCount=0;",p);
  try(var c=ds.getConnection();var s=c.createStatement();var q=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),SUSER_SNAME(owner_sid),SUSER_SNAME(),(SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name=N'p02_test_owner') FROM sys.databases WHERE database_id=DB_ID()")){
   if(!q.next()||!NAME.equals(q.getString(1))||!"DEV2022".equals(q.getString(2))||!Objects.equals(q.getString(3),q.getString(4))||!"ctsv-mghp-p02-20261112".equals(q.getString(5)))throw new IllegalStateException("P02 DB/owner/marker mismatch");
  }return ds;
 }
 public static Connection open()throws SQLException{return source().getConnection();}
 public static void baseline()throws SQLException {
  try(var c=open()){if(scalar(c,"SELECT COUNT(*) FROM sys.tables WHERE name=N'flyway_schema_history'")==0)Flyway.configure().dataSource(source()).cleanDisabled(true).target("2").load().migrate();}
 }
 public static void migrate()throws SQLException {var f=Flyway.configure().dataSource(source()).cleanDisabled(true).baselineOnMigrate(false).validateOnMigrate(true).load();f.migrate();f.validate();}
 public static long scalar(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
}
