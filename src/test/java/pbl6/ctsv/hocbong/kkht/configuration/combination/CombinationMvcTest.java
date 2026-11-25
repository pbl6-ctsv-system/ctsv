package pbl6.ctsv.hocbong.kkht.configuration.combination;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource;
import pbl6.ctsv.hocbong.kkht.round.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CombinationMvcTest {
    private static final String BASE=CombinationController.BASE;
    private static final String BODY="""
            {"programId":"1","cohortId":"2","majorId":"3","curriculumCode":"SYN-KHUNG","standardSemesters":8,
             "programType":"CN","effectiveFrom":"2024-09-01","effectiveTo":null}
            """;
    private final AuthAccess auth=mock(AuthAccess.class);
    private final FoundationAccess identities=mock(FoundationAccess.class);
    private final FoundationRoleAccess roles=mock(FoundationRoleAccess.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final CombinationService service=mock(CombinationService.class);
    private final AuthAccess.Principal principal=new AuthAccess.Principal("synthetic",UUID.randomUUID(),0);
    private MockMvc mvc;
    private final CombinationDtos.Detail detail=new CombinationDtos.Detail("7","1","P","CTĐT nguồn","2","K","3","N",
            "SYN-KHUNG",8,"CN",LocalDate.of(2024,9,1),null);

    @BeforeEach void setup() {
        when(auth.verifyAccessToken("synthetic")).thenReturn(principal);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(identities.identify(principal,true)).thenReturn(new FoundationAccess.Identity(10,null,principal));
        when(roles.findActiveGlobalRole(10,"HOC_BONG","hoc_bong_chuyen_vien"))
                .thenReturn(Optional.of(new FoundationRoleAccess.ActiveRole(20)));
        mvc=MockMvcBuilders.standaloneSetup(new CombinationController(auth,new RoundAccess(manager,identities,roles),
                        service,new ObjectMapper().findAndRegisterModules()))
                .setControllerAdvice(new RoundErrors(),new RoundErrors.MethodErrors()).build();
    }

    @Test void HB_COMB_05_requiresBearerBeforeAnyAction() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings={"GET","POST","PUT","DELETE"})
    void HB_COMB_05_studentCannotCallManagementOperations(String method) throws Exception {
        when(roles.findActiveGlobalRole(anyLong(),anyString(),anyString())).thenReturn(Optional.empty());
        var request=switch(method) {
            case "POST" -> post(BASE); case "PUT" -> put(BASE+"/7"); case "DELETE" -> delete(BASE+"/7"); default -> get(BASE);
        };
        mvc.perform(request.header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test void HB_COMB_04_createUsesActorAndWhitelistedDtoStringIds() throws Exception {
        when(service.create(any(),any())).thenReturn(new CombinationDtos.View(detail,"\"tag\""));
        mvc.perform(post(BASE).header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string("Location",BASE+"/7"))
                .andExpect(header().string("ETag","\"tag\""))
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.programId").value("1")).andExpect(jsonPath("$.programName").value("CTĐT nguồn"))
                .andExpect(jsonPath("$.version").doesNotExist());
        verify(service).create(argThat(c -> c.majorId()==3),eq(new RoundAccess.Actor(10,null,20L)));
    }

    @Test void HB_COMB_01_lookupUsesReadonlyTransactionAndReturnsCodes() throws Exception {
        when(service.sources(eq(ScholarshipCombinationSource.Kind.CURRICULA),eq("KHUNG"),any()))
                .thenReturn(new CombinationDtos.Page<>(List.of(new ScholarshipCombinationSource.Option("KHUNG","KHUNG",null,null)),0,20,1));
        mvc.perform(get(BASE+"/nguon").header("Authorization","Bearer synthetic").param("kind","CURRICULA").param("q","KHUNG"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("KHUNG"))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(manager).getTransaction(argThat(def -> def.isReadOnly()));
    }

    @ParameterizedTest @ValueSource(strings={"{","[]","{\"programId\":\"1\",\"programId\":\"2\"}","{} {}"})
    void HB_COMB_06_rejectsMalformedDuplicateOrTrailingBody(String body) throws Exception {
        mvc.perform(post(BASE).header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test void HB_COMB_06_rejectsUnknownQueryAndBodyOnGet() throws Exception {
        mvc.perform(get(BASE).header("Authorization","Bearer synthetic").param("unknown","x")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).header("Authorization","Bearer synthetic").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"/nguon").header("Authorization","Bearer synthetic").param("kind","BAD"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void HB_COMB_07_preservesPreconditionErrorsAndDeleteIsNoContent() throws Exception {
        when(service.update(eq(7L),any(),isNull(),any())).thenThrow(new RoundFailure(428,"PRECONDITION_REQUIRED","If-Match"));
        mvc.perform(put(BASE+"/7").header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
        mvc.perform(delete(BASE+"/7").header("Authorization","Bearer synthetic").header("If-Match","\"tag\""))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(7,"\"tag\"",new RoundAccess.Actor(10,null,20L));
    }

    @Test void HB_COMB_06_keepsUnsupportedMediaAndUnknownMethodErrors() throws Exception {
        mvc.perform(post(BASE).header("Authorization","Bearer synthetic").contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(patch(BASE+"/7").header("Authorization","Bearer synthetic"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
