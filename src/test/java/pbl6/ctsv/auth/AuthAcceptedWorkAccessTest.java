package pbl6.ctsv.auth;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthAcceptedWorkAccessTest {
 final UUID account=UUID.randomUUID();
 final AuthAccess.Principal accepted=new AuthAccess.Principal("issuer",account,2);
 AuthService service(AuthAccess.State state){var service=mock(AuthService.class,CALLS_REAL_METHODS);doReturn(state).when(service).state(account);return service;}
 @Test void acceptedWorkSurvivesSessionRevocationButOldHttpPrincipalDoesNot(){
  var service=service(new AuthAccess.State(account,true,"3",true));
  assertThat(service.requireAcceptedWorkAccess(accepted,account).accountId()).isEqualTo(account);
  assertThatThrownBy(()->service.requireCtsvAccess(accepted,account)).isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status).isEqualTo(401));
 }
 @Test void acceptedWorkStillRejectsDisabledAccount(){
  var service=service(new AuthAccess.State(account,false,"3",true));
  assertThatThrownBy(()->service.requireAcceptedWorkAccess(accepted,account)).isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status).isEqualTo(401));
 }
 @Test void acceptedWorkStillRejectsLostCtsvAccess(){
  var service=service(new AuthAccess.State(account,true,"3",false));
  assertThatThrownBy(()->service.requireAcceptedWorkAccess(accepted,account)).isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status).isEqualTo(403));
 }
 @Test void acceptedWorkRejectsAccountMismatchBeforeReadingState(){
  var service=service(new AuthAccess.State(account,true,"3",true));
  assertThatThrownBy(()->service.requireAcceptedWorkAccess(accepted,UUID.randomUUID())).isInstanceOf(AuthFailure.class);verify(service,never()).state(any());
 }
 @Test void missingAccountRemainsRevoked(){
  var service=mock(AuthService.class,CALLS_REAL_METHODS);doThrow(new AuthFailure(404,"NOT_FOUND")).when(service).state(account);
  assertThatThrownBy(()->service.requireAcceptedWorkAccess(accepted,account)).isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.status).isEqualTo(401));
 }
}
