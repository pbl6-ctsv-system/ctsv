package pbl6.ctsv.foundation.sync;
import java.sql.*;import com.fasterxml.jackson.databind.JsonNode;
/** Resource-owned mapper: resolve local FK, preserve local columns/history, never share repositories across modules. */
public interface SyncTarget {
 String resource();
 boolean hasMappings(Connection connection,long source)throws SQLException;
 /** Return independent local PK; unknown DELETE returns null, creating no false mapping. */
 Long apply(Connection connection,long source,JsonNode change)throws SQLException;
 void markAbsent(Connection connection,long source,long sourceId)throws SQLException;
}
