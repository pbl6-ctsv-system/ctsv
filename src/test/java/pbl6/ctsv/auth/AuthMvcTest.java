package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthMvcTest {
    @Test void databaseAndCommitFailuresNeverReturnTokensOrCookies()throws Exception {
        var service=mock(AuthService.class);
        var admin=mock(AuthAdministration.class);
        var config=new AuthSettings("issuer","aud","kid",Path.of("test"),Map.of(),Set.of("https://web.mghp.test"),Set.of(),Map.of(),false);
        var mvc=MockMvcBuilders.standaloneSetup(new AuthController(new ObjectMapper(),service,admin,config))
            .setControllerAdvice(new AuthErrors()).build();
        doThrow(new org.springframework.transaction.TransactionSystemException("Synthetic commit acknowledgement loss")).when(service).refresh(any());
        mvc.perform(post("/api/xac-thuc/lam-moi-token").header("Origin","https://web.mghp.test").header("X-CSRF-Protection","1"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"))
            .andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("$.accessToken").doesNotExist());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("Synthetic SQL fault")).when(service).refresh(any());
        mvc.perform(post("/api/xac-thuc/lam-moi-token").header("Origin","https://web.mghp.test").header("X-CSRF-Protection","1"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("$.accessToken").doesNotExist());
    }
    @Test void cookieOperationsRejectWrongOriginBeforeCallingService()throws Exception{
        var service=mock(AuthService.class);
        var admin=mock(AuthAdministration.class);
        var config=new AuthSettings("issuer","aud","kid",Path.of("test"),Map.of(),Set.of("https://web.mghp.test"),Set.of(),Map.of(),false);
        var json=new ObjectMapper();
        var mvc=MockMvcBuilders.standaloneSetup(new AuthController(json,service,admin,config))
            .setControllerAdvice(new AuthErrors()).build();
        for(var path:List.of("dang-nhap","lam-moi-token","dang-xuat"))
            mvc.perform(post("/api/xac-thuc/"+path).header("Origin","https://other.test").header("X-CSRF-Protection","1"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        verifyNoInteractions(service,admin);
    }
    @Test void duplicateJsonAndFormLoginAreRejectedWithoutInvokingAuthentication()throws Exception{
        var service=mock(AuthService.class);
        var admin=mock(AuthAdministration.class);
        var config=new AuthSettings("issuer","aud","kid",Path.of("test"),Map.of(),Set.of("https://web.mghp.test"),Set.of(),Map.of(),false);
        var mvc=MockMvcBuilders.standaloneSetup(new AuthController(new ObjectMapper(),service,admin,config))
            .setControllerAdvice(new AuthErrors()).build();
        mvc.perform(post("/api/xac-thuc/dang-nhap").header("Origin","https://web.mghp.test").header("X-CSRF-Protection","1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"a\",\"username\":\"b\",\"password\":\"synthetic\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/xac-thuc/dang-nhap").header("Origin","https://web.mghp.test").header("X-CSRF-Protection","1")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("username=a&password=synthetic"))
            .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service,admin);
    }
}
