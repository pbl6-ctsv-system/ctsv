package pbl6.ctsv.foundation.units;

import java.sql.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pbl6.p01.FaultDatabase;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** UNIT/MOCK only: guard refusal proof, not database runtime evidence. */
class FaultDatabaseGuardTest {
    @ParameterizedTest
    @ValueSource(strings={"protectedDB","wrongInstance","wrongMarker","wrongOwner","noOwnerRole"})
    void refusesUnexpectedTargetBeforeAnyMutation(String variant)throws Exception {
        Connection c=mock(Connection.class);Statement statement=mock(Statement.class);ResultSet result=mock(ResultSet.class);
        when(c.createStatement()).thenReturn(statement);when(statement.executeQuery(anyString())).thenReturn(result);when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn(variant.equals("protectedDB")?"ctsv_test_20261111":FaultDatabase.NAME);
        when(result.getString(2)).thenReturn(variant.equals("wrongInstance")?"SQLEXPRESS":FaultDatabase.INSTANCE);
        when(result.getString(3)).thenReturn(variant.equals("wrongMarker")?"unowned":FaultDatabase.MARKER);
        when(result.getString(4)).thenReturn(variant.equals("wrongOwner")?"someone-else":"fixture-owner");
        when(result.getString(5)).thenReturn("fixture-owner");when(result.getInt(6)).thenReturn(variant.equals("noOwnerRole")?0:1);
        String previous=System.getProperty("p01.fault.enabled");
        try{
            System.setProperty("p01.fault.enabled","true");
            assertThatThrownBy(()->FaultDatabase.guard(c)).isInstanceOf(IllegalStateException.class);
            verify(statement,never()).execute(anyString());verify(statement,never()).executeUpdate(anyString());
        }finally{
            if(previous==null)System.clearProperty("p01.fault.enabled");else System.setProperty("p01.fault.enabled",previous);
        }
    }
}
