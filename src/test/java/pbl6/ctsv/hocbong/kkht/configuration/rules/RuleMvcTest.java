package pbl6.ctsv.hocbong.kkht.configuration.rules;

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
import pbl6.ctsv.hocbong.kkht.round.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RuleMvcTest {
    private static final String BASE=RuleController.BASE;
    private final AuthAccess auth=mock(AuthAccess.class);
    private final FoundationAccess identities=mock(FoundationAccess.class);
    private final FoundationRoleAccess roles=mock(FoundationRoleAccess.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private final RuleService service=mock(RuleService.class);
    private final AuthAccess.Principal principal=new AuthAccess.Principal("synthetic",UUID.randomUUID(),0);
    private MockMvc mvc;
    private RuleDtos.Detail detail;
    @BeforeEach void setup() {
        when(auth.verifyAccessToken("synthetic")).thenReturn(principal);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(identities.identify(principal,true)).thenReturn(new FoundationAccess.Identity(10,null,principal));
        when(roles.findActiveGlobalRole(10,"HOC_BONG","hoc_bong_chuyen_vien")).thenReturn(Optional.of(new FoundationRoleAccess.ActiveRole(20)));
        mvc=MockMvcBuilders.standaloneSetup(new RuleController(auth,new RoundAccess(manager,identities,roles),service,RuleFixtures.JSON)).setControllerAdvice(new RoundErrors(),new RoundErrors.MethodErrors()).build();
        var c=RuleFixtures.command();detail=new RuleDtos.Detail("9007199254740993",3,c.legalBasis(),c.effectiveFrom().toString(),null,c.conditions(),c.levels(),c.priority(),c.tuitionBasis(),c.tiePolicy(),c.lastSlot(),"draft",null);
    }
    @Test void HB_RULE_04_requiresBearerBeforeService() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"GET","POST","PUT"})
    void HB_RULE_04_studentOrUnrelatedRoleCannotUseManagementApi(String method) throws Exception {
        when(roles.findActiveGlobalRole(anyLong(),anyString(),anyString())).thenReturn(Optional.empty());
        var request=switch(method){case "POST"->post(BASE);case "PUT"->put(BASE+"/7");default->get(BASE);};
        mvc.perform(request.header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(RuleFixtures.body().toString())).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));verifyNoInteractions(service);
    }
    @Test void HB_RULE_05_createUsesTrustedActorAndStrictDtoStringDecimals() throws Exception {
        when(service.create(any(),any())).thenReturn(new RuleDtos.View(detail,"\"v1\""));
        mvc.perform(post(BASE).header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(RuleFixtures.body().toString()))
                .andExpect(status().isCreated()).andExpect(header().string("Location",BASE+"/9007199254740993")).andExpect(header().string("ETag","\"v1\""))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.id").value("9007199254740993"))
                .andExpect(jsonPath("$.levels.A.tuitionMultiplier").value("1.5000")).andExpect(jsonPath("$.lastSlot.comparison").value("GT"));
        verify(service).create(argThat(c->c.priority().fallback().equals("HIGHEST_ELIGIBLE")),eq(new RoundAccess.Actor(10,null,20L)));
    }
    @Test void HB_RULE_04_listsConsistentlyAndReadsRepresentationVersion() throws Exception {
        when(service.list(any())).thenReturn(new RuleDtos.Page(List.of(detail),2,10,21));when(service.get(9007199254740993L)).thenReturn(new RuleDtos.View(detail,"\"v1\""));
        mvc.perform(get(BASE).header("Authorization","Bearer synthetic").param("page","2").param("size","10")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].version").value(3)).andExpect(jsonPath("$.totalElements").value(21));
        verify(manager).getTransaction(argThat(def->def.isReadOnly()));
        mvc.perform(get(BASE+"/9007199254740993").header("Authorization","Bearer synthetic")).andExpect(status().isOk()).andExpect(header().string("ETag","\"v1\""));
    }
    @ParameterizedTest @ValueSource(strings={"{","[]","{} {}","{\"conditions\":{},\"conditions\":{}}"})
    void HB_RULE_12_rejectsParseDuplicateAndTrailingToken(String body) throws Exception {
        mvc.perform(post(BASE).header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"id","version","status","usedAt","actor"})
    void HB_RULE_12_clientCannotForgeMetadata(String field) throws Exception {
        var body=RuleFixtures.body();body.put(field,"forged");mvc.perform(post(BASE).header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void HB_RULE_12_rejectsQueryBodyOnGetInvalidIdsAndUnsupportedMedia() throws Exception {
        mvc.perform(get(BASE).header("Authorization","Bearer synthetic").param("q","forged")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).header("Authorization","Bearer synthetic").param("page","0","1")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).header("Authorization","Bearer synthetic").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"/01").header("Authorization","Bearer synthetic")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE).header("Authorization","Bearer synthetic").contentType(MediaType.TEXT_PLAIN).content(RuleFixtures.body().toString())).andExpect(status().isUnsupportedMediaType());verifyNoInteractions(service);
    }
    @Test void HB_RULE_06_keepsPreconditionAndInUseErrors() throws Exception {
        when(service.update(eq(7L),any(),isNull(),any())).thenThrow(new RoundFailure(428,"PRECONDITION_REQUIRED","If-Match"));
        mvc.perform(put(BASE+"/7").header("Authorization","Bearer synthetic").contentType(MediaType.APPLICATION_JSON).content(RuleFixtures.body().toString())).andExpect(status().isPreconditionRequired()).andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));
        when(service.update(eq(7L),any(),eq("\"v1\""),any())).thenThrow(new RoundFailure(409,"RULE_VERSION_IN_USE"));
        mvc.perform(put(BASE+"/7").header("Authorization","Bearer synthetic").header("If-Match","\"v1\"").contentType(MediaType.APPLICATION_JSON).content(RuleFixtures.body().toString())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RULE_VERSION_IN_USE"));
    }
    @Test void HB_RULE_09_dependencyAndUnknownFailuresNeverLeakSql() throws Exception {
        when(service.get(7)).thenThrow(new org.springframework.dao.QueryTimeoutException("SYN SQL secret"));
        mvc.perform(get(BASE+"/7").header("Authorization","Bearer synthetic")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SYN SQL"))));
        doThrow(new IllegalStateException("SYN SQL secret")).when(service).get(7);mvc.perform(get(BASE+"/7").header("Authorization","Bearer synthetic")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
    @Test void HB_RULE_12_noDeletePublishAssignOrClientEngineCommands() throws Exception {
        mvc.perform(delete(BASE+"/7").header("Authorization","Bearer synthetic")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(patch(BASE+"/7").header("Authorization","Bearer synthetic")).andExpect(status().isMethodNotAllowed());verifyNoInteractions(service);
    }
}
