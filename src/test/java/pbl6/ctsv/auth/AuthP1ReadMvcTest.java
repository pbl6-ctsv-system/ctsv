package pbl6.ctsv.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthP1ReadMvcTest {
 @Test void accountDirectoryIsNotAnonymous() throws Exception {
  var mvc=MockMvcBuilders.standaloneSetup(new AuthController(new ObjectMapper(),mock(AuthService.class),mock(AuthAdministration.class),mock(AuthSettings.class))).setControllerAdvice(new AuthErrors()).build();
  mvc.perform(get("/api/xac-thuc/tai-khoan?q=dev")).andExpect(status().isUnauthorized());
 }
}
