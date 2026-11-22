package pbl6.ctsv.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;import java.util.function.Supplier;
import static org.mockito.Mockito.*;import static org.assertj.core.api.Assertions.*;
class AuthP1DirectorySecurityTest {
 @Test void sourceDirectoryRequiresFreshAccountManagementAndReturnsOnlyRegisteredSourceNames(){
  var auth=mock(AuthService.class);var p=new AuthAccess.Principal("issuer",UUID.randomUUID(),3);
  when(auth.transaction(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
  var admin=new AuthAdministration(auth,Set.of("SIS"),new ObjectMapper());
  assertThat(admin.profileSources(p)).containsExactly("SIS");verify(auth).actor(p,"auth.accounts.manage",true);
 }
 @Test void revokedOrUnauthorizedActorCannotEnumerateSourcesOrAccounts(){
  for(int status:List.of(401,403)){
   var auth=mock(AuthService.class);var p=new AuthAccess.Principal("issuer",UUID.randomUUID(),3);
   when(auth.transaction(any())).thenAnswer(i->((Supplier<?>)i.getArgument(0)).get());
   when(auth.actor(p,"auth.accounts.manage",true)).thenThrow(new AuthFailure(status,status==401?"TOKEN_REVOKED":"FORBIDDEN"));
   var admin=new AuthAdministration(auth,Set.of("SIS"),new ObjectMapper());
   assertThatThrownBy(()->admin.profileSources(p)).isInstanceOf(AuthFailure.class);
   assertThatThrownBy(()->admin.search(p,"dev",0,20)).isInstanceOf(AuthFailure.class);
  }
 }
}
