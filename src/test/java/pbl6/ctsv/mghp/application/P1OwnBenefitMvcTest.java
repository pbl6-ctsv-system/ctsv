package pbl6.ctsv.mghp.application;
import org.junit.jupiter.api.Test;import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import pbl6.ctsv.auth.AuthAccess;import pbl6.ctsv.foundation.api.FoundationAccess;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class P1OwnBenefitMvcTest {
 @Test void existingBenefitCommandUrlsRemainMapped() throws Exception {
  var mvc=MockMvcBuilders.standaloneSetup(new BenefitController(mock(AuthAccess.class),mock(FoundationAccess.class),mock(BenefitService.class),mock(PlatformTransactionManager.class),new ObjectMapper())).setControllerAdvice(new IntakeErrors()).build();
  for(String command:java.util.List.of("ghi-nhan-ra-soat","yeu-cau-cap-nhat"))
   mvc.perform(post("/api/ctsv/mghp/quyen-huong/1/"+command).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
 }
 @Test void ownDirectoryDoesNotAcceptAnAnonymousCaller() throws Exception {
  var controller=new BenefitController(mock(AuthAccess.class),mock(FoundationAccess.class),mock(BenefitService.class),mock(PlatformTransactionManager.class),new ObjectMapper());
  var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new IntakeErrors()).build();
  mvc.perform(get("/api/ctsv/mghp/toi/quyen-huong")).andExpect(status().isUnauthorized());
 }
 @Test void rejectsCallerSuppliedOwnerBeforeReadingAnyBenefit() throws Exception {
  var auth=mock(AuthAccess.class);var access=mock(FoundationAccess.class);var service=mock(BenefitService.class);
  var p=new AuthAccess.Principal("issuer",java.util.UUID.randomUUID(),0);when(auth.verifyAccessToken("test")).thenReturn(p);
  when(access.authorize(p,true,"mghp_chuyen_vien")).thenThrow(new FoundationAccess.Failure(403,"FORBIDDEN","request"));
  var controller=new BenefitController(auth,access,service,mock(PlatformTransactionManager.class),new ObjectMapper());
  var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new IntakeErrors()).build();
  mvc.perform(get("/api/ctsv/mghp/toi/quyen-huong?studentId=123").header("Authorization","Bearer test")).andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
 @Test void contextRequiresFreshSpecialistCapability() throws Exception {
  var auth=mock(AuthAccess.class);var access=mock(FoundationAccess.class);var service=mock(BenefitService.class);var manager=mock(PlatformTransactionManager.class);
  var p=new AuthAccess.Principal("issuer",java.util.UUID.randomUUID(),0);when(auth.verifyAccessToken("test")).thenReturn(p);
  when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
  when(access.authorize(p,true,"mghp_chuyen_vien")).thenThrow(new FoundationAccess.Failure(403,"FORBIDDEN","request"));
  var mvc=MockMvcBuilders.standaloneSetup(new BenefitController(auth,access,service,manager,new ObjectMapper())).setControllerAdvice(new IntakeErrors()).build();
  mvc.perform(get("/api/ctsv/mghp/quyen-huong/1/ngu-canh-xet-duyet").header("Authorization","Bearer test")).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
}
