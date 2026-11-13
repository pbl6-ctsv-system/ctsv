package pbl6.ctsv.foundation.units.service;
import java.sql.*;
import java.util.Locale;
import javax.sql.DataSource;

/** A dedicated physical SQL connection owns both the Session applock and all page writes. */
public final class UnitSyncGuard {
    private final DataSource source;
    private final String database,instance;
    public UnitSyncGuard(DataSource source,String database,String instance){this.source=source;this.database=database;this.instance=instance;}
    public Handle acquire(long sourceId)throws SQLException {
        if(sourceId<1)throw new IllegalArgumentException("Source system is required");
        Connection c=source.getConnection();
        try {
            if(!c.getAutoCommit())throw new IllegalStateException("Dedicated sync connection must start outside a transaction");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))")){
                if(!r.next()||!database.equals(r.getString(1))||!instance.equalsIgnoreCase(r.getString(2)))
                    throw new IllegalStateException("Unexpected sync database target");
            }
            // Transparent idle reconnection would lose the Session lock while keeping the Java handle.
            if(!c.getMetaData().getURL().toLowerCase(Locale.ROOT).contains("connectretrycount=0;"))
                throw new IllegalStateException("SQL idle reconnect must be disabled for the guarded connection");
            String resource="ctsv.foundation.units:"+sourceId;
            try(var s=c.prepareStatement("DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=?,@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0; SELECT @r;")){
                s.setString(1,resource);try(var r=s.executeQuery()){
                    if(!r.next()||r.getInt(1)<0)throw new BusyException();
                }
            }
            return new Handle(c,resource);
        }catch(SQLException|RuntimeException e){
            try{c.abort(Runnable::run);}finally{c.close();}
            throw e;
        }
    }
    public static final class BusyException extends IllegalStateException {public BusyException(){super("Units sync is already running");}}
    public static final class Handle implements AutoCloseable {
        private final Connection connection;private final String resource;
        private Handle(Connection connection,String resource){this.connection=connection;this.resource=resource;}
        public Connection connection(){return connection;}
        public void beforeHttp()throws SQLException {
            if(connection.isClosed()||!connection.getAutoCommit())throw new IllegalStateException("Sync connection unavailable or transaction open before HTTP");
            try(var s=connection.prepareStatement("SELECT APPLOCK_MODE('public',?,'Session'),@@TRANCOUNT")){
                s.setString(1,resource);try(var r=s.executeQuery()){
                    if(!r.next()||!"Exclusive".equals(r.getString(1))||r.getInt(2)!=0)
                        throw new IllegalStateException("Sync lock lost or transaction open before HTTP");
                }
            }
        }
        public void begin()throws SQLException {
            if(!connection.getAutoCommit())throw new IllegalStateException("Previous sync transaction is still open");
            connection.setAutoCommit(false);
            try(var s=connection.prepareStatement("SELECT APPLOCK_MODE('public',?,'Session')")){
                s.setString(1,resource);try(var r=s.executeQuery()){
                    if(!r.next()||!"Exclusive".equals(r.getString(1)))throw new IllegalStateException("Sync lock was lost");
                }
            }
        }
        @Override public void close()throws SQLException {
            try {
                if(!connection.isClosed()){
                    if(!connection.getAutoCommit()){connection.rollback();connection.setAutoCommit(true);}
                    try(var s=connection.prepareStatement("DECLARE @r int; EXEC @r=sys.sp_releaseapplock @Resource=?,@LockOwner='Session'; SELECT @r;")){
                        s.setString(1,resource);try(var r=s.executeQuery()){
                            if(!r.next()||r.getInt(1)<0)connection.abort(Runnable::run);
                        }
                    }catch(SQLException e){connection.abort(Runnable::run);}
                }
            } finally {connection.close();}
        }
    }
}

