package pbl6.ctsv.mghp.application;
import java.util.*;
import org.junit.jupiter.api.Test;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class SigningSupportAccessTest {
 @Test void requiresSeparateActiveGlobalBusinessGrant(){
  var foundation=mock(FoundationAccess.class);var roles=mock(FoundationRoleAccess.class);
  var p=new AuthAccess.Principal("local",UUID.randomUUID(),1);var identity=new FoundationAccess.Identity(71,null,p);
  when(foundation.identify(p,true)).thenReturn(identity);
  when(roles.findActiveGlobalRole(71,"MGHP","mghp_quan_tri_ky")).thenReturn(Optional.empty());
  var access=new SigningSupportAccess(foundation,roles,mock(AuthAccess.class));
  assertThatThrownBy(()->access.require(p)).hasMessage("FORBIDDEN");
  when(roles.findActiveGlobalRole(71,"MGHP","mghp_quan_tri_ky")).thenReturn(Optional.of(new FoundationRoleAccess.ActiveRole(88)));
  assertThat(access.require(p)).isEqualTo(identity);
  verify(foundation,times(2)).identify(p,true);
 }
}
