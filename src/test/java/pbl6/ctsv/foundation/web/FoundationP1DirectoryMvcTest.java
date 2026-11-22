package pbl6.ctsv.foundation.web;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pbl6.ctsv.auth.AuthAccess;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FoundationP1DirectoryMvcTest {
 @Test void businessDirectoriesRequireAuthentication() throws Exception {
  var mvc=MockMvcBuilders.standaloneSetup(new FoundationController(mock(FoundationService.class),mock(AuthAccess.class),new ObjectMapper())).setControllerAdvice(new FoundationErrors()).build();
  for(String path:List.of("/nguoi-dung","/vai-tro-mghp"))mvc.perform(get("/api/ctsv/nen-tang"+path)).andExpect(status().isUnauthorized());
 }
 @Test void userDirectoryUsesFreshBusinessAdministrationCapability() throws Exception {
  var service=mock(FoundationService.class);var auth=mock(AuthAccess.class);
  var p=new AuthAccess.Principal("issuer",UUID.randomUUID(),0);when(auth.verifyAccessToken("test")).thenReturn(p);
  when(service.execute(eq(p),eq(true),eq("ctsv.roles.manage"),any())).thenReturn(Map.of("items",List.of(),"page",0,"size",20,"totalElements",0));
  var mvc=MockMvcBuilders.standaloneSetup(new FoundationController(service,auth,new ObjectMapper())).setControllerAdvice(new FoundationErrors()).build();
  mvc.perform(get("/api/ctsv/nen-tang/nguoi-dung?q=An&page=0&size=20").header("Authorization","Bearer test")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
  verify(service).execute(eq(p),eq(true),eq("ctsv.roles.manage"),any());
  mvc.perform(get("/api/ctsv/nen-tang/nguoi-dung?studentId=1").header("Authorization","Bearer test")).andExpect(status().isBadRequest());
 }
}
