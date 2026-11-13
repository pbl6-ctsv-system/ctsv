package pbl6.ctsv.foundation.units;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import pbl6.p01.FaultDatabase;
import static org.assertj.core.api.Assertions.*;

/** Test-only admin actions. All SQL targets are the single constant approved fault DB. */
final class FaultAdmin {
    record Ticket(int databaseId,String ownerSid,String path) {}
    static Connection acquireSuite()throws Exception{
        Connection c=master();
        try(var s=c.createStatement();var r=s.executeQuery("DECLARE @result int; EXEC @result=sys.sp_getapplock @Resource=N'p01_sis_fault_20261111:suite',@LockMode=N'Exclusive',@LockOwner=N'Session',@LockTimeout=0; SELECT @result;")){
            if(!r.next()||r.getInt(1)<0)throw new IllegalStateException("Another fault/G8 suite owns this database");
            return c;
        }catch(Exception e){c.close();throw e;}
    }
    static Connection master()throws Exception{
        FaultDatabase.dataSource(); // Explicit opt-in and online identity before a new admin operation.
        return connectMaster();
    }
    private static Connection connectMaster()throws Exception{
        var p=new Properties();p.setProperty("integratedSecurity","true");p.setProperty("encrypt","true");
        p.setProperty("trustServerCertificate","true");p.setProperty("connectRetryCount","0");p.setProperty("loginTimeout","5");
        var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
            "jdbc:sqlserver://localhost:"+System.getenv("P00_SQL_PORT")+";databaseName=master;",p);
        Connection c=ds.getConnection();
        try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))")){
            if(!r.next()||!"master".equals(r.getString(1))||!FaultDatabase.INSTANCE.equals(r.getString(2))){c.close();throw new IllegalStateException("Unexpected admin context");}
        }
        return c;
    }
    static Ticket identity(Connection c,boolean requireOnlineMarker)throws Exception{
        try(var s=c.createStatement();var r=s.executeQuery("SELECT database_id,CONVERT(varchar(128),owner_sid,2),SUSER_SNAME(owner_sid),SUSER_SNAME() FROM sys.databases WHERE name=N'"+FaultDatabase.NAME+"'")){
            if(!r.next()||r.getInt(1)<=4||!Objects.equals(r.getString(3),r.getString(4)))throw new IllegalStateException("Fault admin owner mismatch");
            var ticket=new Ticket(r.getInt(1),r.getString(2),null);
            if(requireOnlineMarker)try(var check=FaultDatabase.open()){FaultDatabase.guard(check);}
            return ticket;
        }
    }
    static Ticket backup()throws Exception{
        try(var c=master()){
            Ticket ticket=identity(c,true);String root;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT CONVERT(nvarchar(260),SERVERPROPERTY('InstanceDefaultBackupPath'))")){
                if(!r.next()||r.getString(1)==null)throw new IllegalStateException("No verified instance backup directory");root=r.getString(1);
            }
            String path=root+"\\"+FaultDatabase.NAME+"-"+UUID.randomUUID()+".bak";
            FaultSyncFixture.record("GUARD:BACKUP",Map.of("dbId",ticket.databaseId(),"ownerSid",ticket.ownerSid(),"path",path));
            try(var s=c.createStatement()){
                s.setQueryTimeout(60);s.execute("BACKUP DATABASE ["+FaultDatabase.NAME+"] TO DISK=N'"+path.replace("'","''")+"' WITH COPY_ONLY,CHECKSUM;");
                s.execute("RESTORE VERIFYONLY FROM DISK=N'"+path.replace("'","''")+"' WITH CHECKSUM;");
            }
            return new Ticket(ticket.databaseId(),ticket.ownerSid(),path);
        }
    }
    static void restore(Ticket backup)throws Exception{
        try(var c=master()){
            Ticket current=identity(c,true);
            assertThat(current.databaseId()).isEqualTo(backup.databaseId());assertThat(current.ownerSid()).isEqualTo(backup.ownerSid());
            if(backup.path()==null||!backup.path().contains("\\"+FaultDatabase.NAME+"-")||!backup.path().endsWith(".bak"))
                throw new IllegalArgumentException("Not an owned fault backup");
            FaultSyncFixture.record("GUARD:RESTORE",Map.of("dbId",current.databaseId(),"ownerSid",current.ownerSid(),"path",backup.path()));
            try(var s=c.createStatement()){
                s.setQueryTimeout(90);s.execute("ALTER DATABASE ["+FaultDatabase.NAME+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;");
                try{s.execute("RESTORE DATABASE ["+FaultDatabase.NAME+"] FROM DISK=N'"+backup.path().replace("'","''")+"' WITH REPLACE,RECOVERY,CHECKSUM;");}
                finally{
                    Ticket after=identity(c,false);
                    assertThat(after.databaseId()).isEqualTo(backup.databaseId());assertThat(after.ownerSid()).isEqualTo(backup.ownerSid());
                    s.execute("ALTER DATABASE ["+FaultDatabase.NAME+"] SET MULTI_USER;");
                }
            }
            try(var restored=FaultDatabase.open()){FaultDatabase.guard(restored);}
        }
    }
    static void offline(CheckedAction action)throws Exception{
        Ticket ticket;
        try(var c=master()){ticket=identity(c,true);
            FaultSyncFixture.record("GUARD:OFFLINE",Map.of("dbId",ticket.databaseId(),"ownerSid",ticket.ownerSid()));
            try(var s=c.createStatement()){s.execute("ALTER DATABASE ["+FaultDatabase.NAME+"] SET OFFLINE WITH ROLLBACK IMMEDIATE;");}
        }
        try{action.run();}
        finally{
            // The marker cannot be queried while offline. Match the captured DB ID+owner before ONLINE.
            try(var c=connectMaster()){
                Ticket current=identity(c,false);assertThat(current.databaseId()).isEqualTo(ticket.databaseId());assertThat(current.ownerSid()).isEqualTo(ticket.ownerSid());
                try(var s=c.createStatement()){s.execute("ALTER DATABASE ["+FaultDatabase.NAME+"] SET ONLINE;");}
            }
            try(var c=FaultDatabase.open()){FaultDatabase.guard(c);}
        }
    }
    @FunctionalInterface interface CheckedAction{void run()throws Exception;}
}
