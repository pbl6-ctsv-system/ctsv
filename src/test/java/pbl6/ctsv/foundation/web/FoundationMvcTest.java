package pbl6.ctsv.foundation.web;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import pbl6.ctsv.auth.AuthAccess;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class FoundationMvcTest {
 @Test void menuAccessUsesCallerAndRejectsTargetId()throws Exception{
  var service=mock(FoundationService.class);var auth=mock(AuthAccess.class);
  var principal=new AuthAccess.Principal("issuer",java.util.UUID.randomUUID(),0);
  when(auth.verifyAccessToken("test")).thenReturn(principal);
  when(service.menuAccess(principal)).thenReturn(new FoundationSecurity.MenuAccess(true,false,false));
  var mvc=MockMvcBuilders.standaloneSetup(new FoundationController(service,auth,new ObjectMapper())).setControllerAdvice(new FoundationErrors()).build();
  String path="/api/ctsv/nen-tang/toi/quyen-giao-dien";
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(get(path+"?userId=123").header("Authorization","Bearer test")).andExpect(status().isBadRequest());
  mvc.perform(get(path).header("Authorization","Bearer test")).andExpect(status().isOk())
   .andExpect(header().string("Cache-Control","no-store"))
   .andExpect(jsonPath("$.student").value(true)).andExpect(jsonPath("$.mghpSpecialist").value(false))
   .andExpect(jsonPath("$.ctsvRoleAdmin").value(false));
  verify(service).menuAccess(principal);
  verifyNoMoreInteractions(service);
 }
 @Test void malformedJwtDoesNotReachFoundationService()throws Exception{
  var service=mock(FoundationService.class);var auth=mock(AuthAccess.class);
  var mvc=MockMvcBuilders.standaloneSetup(new FoundationController(service,auth,new ObjectMapper())).setControllerAdvice(new FoundationErrors()).build();
  mvc.perform(get("/api/ctsv/nen-tang/sinh-vien")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  verifyNoInteractions(service,auth);
 }
 @Test void unknownQueryAndMalformedBodyAreRejectedBeforeTransaction()throws Exception{
  var service=mock(FoundationService.class);var auth=mock(AuthAccess.class);
  when(auth.verifyAccessToken("test")).thenReturn(new AuthAccess.Principal("issuer",java.util.UUID.randomUUID(),0));
  var mvc=MockMvcBuilders.standaloneSetup(new FoundationController(service,auth,new ObjectMapper())).setControllerAdvice(new FoundationErrors()).build();
  mvc.perform(get("/api/ctsv/nen-tang/danh-muc?kind=semesters&unknown=1").header("Authorization","Bearer test"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  mvc.perform(put("/api/ctsv/nen-tang/hoc-ky/1/xac-minh").header("Authorization","Bearer test").contentType("application/json").content("{\"order\":1,\"order\":2}"))
   .andExpect(status().isBadRequest());
  verifyNoInteractions(service);
 }
}
