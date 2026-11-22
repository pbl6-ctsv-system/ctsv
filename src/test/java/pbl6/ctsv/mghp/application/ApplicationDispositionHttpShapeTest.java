package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import static org.assertj.core.api.Assertions.*;

/** Controller/JSON unit test with boundary stubs, not real HTTP/SQL. No JVM attach agent. */
class ApplicationDispositionHttpShapeTest {
 @Test void jsonPostBodyIsAcceptedRatherThanTreatedAsAGetBody()throws Exception{
  invoke("{\"result\":\"approved\",\"reason\":\"Đã kiểm tra PDF\"}","Đã kiểm tra PDF");
 }
 @Test void approvalDoesNotRequireAReason()throws Exception{invoke("{\"result\":\"approved\"}",null);}
 @Test void approvalAcceptsExplicitNullReason()throws Exception{invoke("{\"result\":\"approved\",\"reason\":null}",null);}
 @Test void rejectionStillRequiresAReason(){
  for(var body:new String[]{"{\"result\":\"rejected\"}","{\"result\":\"rejected\",\"reason\":null}","{\"result\":\"rejected\",\"reason\":\" \"}"})
   assertThatThrownBy(()->invoke(body,null)).isInstanceOf(IntakeProblem.class);
 }
 private void invoke(String body,String expectedReason)throws Exception{
  var principal=new AuthAccess.Principal("synthetic",UUID.randomUUID(),1);
  AuthAccess auth=new AuthAccess(){
   public Principal verifyAccessToken(String token){assertThat(token).isEqualTo("synthetic-test-token");return principal;}
   public State requireCtsvAccess(Principal p,UUID id){throw new AssertionError("Not used by this unit boundary");}
  };
  var actor=new FoundationAccess.Identity(1L,null,principal);
  FoundationAccess access=(p,online,capability)->{assertThat(online).isTrue();assertThat(capability).isEqualTo("mghp_chuyen_vien");return actor;};
  var now=Instant.parse("2026-11-21T02:00:00Z");
  var writes=new ApplicationService(null,null,null,new ObjectMapper(),Clock.fixed(now,ZoneOffset.UTC),null){
   @Override Object conclude(FoundationAccess.Identity who,long id,String tag,String result,String reason,Instant received){
    assertThat(who).isSameAs(actor);assertThat(id).isEqualTo(17L);assertThat(tag).isEqualTo("\"snapshot\"");
    assertThat(result).isEqualTo("approved");assertThat(reason).isEqualTo(expectedReason);assertThat(received).isEqualTo(now);
    return Map.of("id","17","status","approved");
   }
  };
  PlatformTransactionManager manager=new PlatformTransactionManager(){
   public TransactionStatus getTransaction(TransactionDefinition definition){return new SimpleTransactionStatus();}
   public void commit(TransactionStatus status){}
   public void rollback(TransactionStatus status){}
  };
  var controller=new ApplicationController(auth,access,null,writes,null,null,null,manager,new ObjectMapper());
  var request=new MockHttpServletRequest("POST","/api/ctsv/mghp/ho-so/17/ket-luan");
  request.addHeader("Authorization","Bearer synthetic-test-token");request.addHeader("If-Match","\"snapshot\"");
  request.setContentType("application/json");request.setContent(body.getBytes(StandardCharsets.UTF_8));
  assertThat(controller.conclude("17",request).getStatusCode().value()).isEqualTo(200);
 }
}
