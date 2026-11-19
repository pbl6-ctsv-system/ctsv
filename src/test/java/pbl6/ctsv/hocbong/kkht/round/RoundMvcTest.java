package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletContext;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.auth.AuthFailure;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HB-T00-001–010, 013, 017, 023, 027–033, 038, 049 and 054 HTTP assertions. */
class RoundMvcTest {
    private static final String BASE = "/api/ctsv/hoc-bong";
    private static final String GROUPS = BASE + "/toi/nhom-chuc-nang";
    private static final String SOURCES = BASE + "/hoc-ky-nguon";
    private static final String ROUNDS = BASE + "/kkht/dot-xet";
    private static final String DETAIL = ROUNDS + "/1001";
    private static final String OTHER_ROUTE = "/api/ctsv/nen-tang/mock-method-scope";
    private static final String ORIGIN = "https://ctsv.example.test";
    private static final String TAG = "\"round_1001\"";
    private static final String NEW_TAG = "\"round_1001_changed\"";
    private static final String CREATE = "{\"sourceSemesterId\":\"21\",\"number\":1,\"name\":\"  SYN-HB  \"}";
    private static final String UPDATE = "{\"number\":2,\"name\":\"SYN-HB update\"}";

    // MOCK: public Auth and Foundation boundaries, local transaction manager and round service.
    private final AuthAccess auth = mock(AuthAccess.class);
    private final FoundationAccess identities = mock(FoundationAccess.class);
    private final FoundationRoleAccess roles = mock(FoundationRoleAccess.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final RoundService service = mock(RoundService.class);
    private final AuthAccess.Principal principal = new AuthAccess.Principal(
            "synthetic", UUID.fromString("23456789-1234-1234-1234-123456789012"), 0);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(auth.verifyAccessToken("synthetic")).thenReturn(principal);
        when(manager.getTransaction(any())).thenReturn(transaction);
        identity(null, true);
        var access = new RoundAccess(manager, identities, roles);
        var cors = new ScholarshipConfiguration().scholarshipCors(
                new MockEnvironment().withProperty("ctsv.auth.origins[0]", ORIGIN));
        mvc = MockMvcBuilders.standaloneSetup(new RoundController(auth, access, service, new ObjectMapper()),
                        new OtherController())
                .setControllerAdvice(new RoundErrors(), new RoundErrors.MethodErrors())
                .addFilter(cors.getFilter(), BASE + "/*")
                .build();
    }

    @Test
    void groupsRequiresBearer() throws Exception {
        problem(mvc.perform(get(GROUPS)), 401, "UNAUTHENTICATED")
                .andExpect(jsonPath("$.instance").value(GROUPS));
        verifyNoInteractions(auth, manager, identities, roles, service);
    }

    @ParameterizedTest
    @CsvSource({"GET, /hoc-ky-nguon", "GET, /kkht/dot-xet", "POST, /kkht/dot-xet",
            "GET, /kkht/dot-xet/bad-id", "PUT, /kkht/dot-xet/bad-id"})
    void managerOperationsCheckAuthAndGroupBeforeBindingInvalidRequest(String method, String route)
            throws Exception {
        identity(71L, false);
        var malformed = request(HttpMethod.valueOf(method), BASE + route + "?unexpected=true")
                .contentType(MediaType.TEXT_PLAIN).content("invalid");

        problem(mvc.perform(malformed), 401, "UNAUTHENTICATED");
        problem(mvc.perform(bearer(malformed)), 403, "FORBIDDEN");
        verifyNoInteractions(service);
    }

    @Test
    void preservesAuthAndFoundationFailureStatusAndCode() throws Exception {
        var revoked = mock(AuthFailure.class);
        when(revoked.status()).thenReturn(401);
        when(revoked.code()).thenReturn("TOKEN_REVOKED");
        when(identities.identify(principal, true)).thenThrow(revoked)
                .thenThrow(new FoundationAccess.Failure(403, "FORBIDDEN", "request"));

        problem(mvc.perform(bearer(put(ROUNDS + "/bad-id").content("bad"))), 401, "TOKEN_REVOKED");
        problem(mvc.perform(bearer(put(ROUNDS + "/bad-id").content("bad"))), 403, "FORBIDDEN");
        verifyNoInteractions(roles, service);
    }

    @ParameterizedTest
    @CsvSource({"false, false", "true, false", "false, true", "true, true"})
    void groupsReflectVerifiedLocalIdentityAndSpecialistRole(boolean student, boolean specialist) throws Exception {
        identity(student ? 71L : null, specialist);

        mvc.perform(bearer(get(GROUPS)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"student\":" + student + ",\"specialist\":" + specialist + "}", JsonCompareMode.STRICT));
        verify(identities).identify(principal, true);
        verify(roles).findActiveGlobalRole(301, "HOC_BONG", "hoc_bong_chuyen_vien");
    }

    @Test
    void roleIsCheckedAgainForEachRequest() throws Exception {
        var noResults = new RoundDtos.Page<RoundDtos.SourceSemesterDto>(List.of(), 0, 20, 0);
        when(service.sources(eq(null), aryEq(new int[]{0, 20}))).thenReturn(noResults);
        mvc.perform(bearer(get(SOURCES))).andExpect(status().isOk());

        identity(null, false);
        problem(mvc.perform(bearer(get(SOURCES))), 403, "FORBIDDEN");
        verify(service).sources(eq(null), aryEq(new int[]{0, 20}));
    }

    @Test
    void rejectsDuplicateAuthorizationBeforeVerifyingAnyBearer() throws Exception {
        problem(mvc.perform(get(GROUPS).header(HttpHeaders.AUTHORIZATION, "Bearer synthetic", "Bearer another")),
                400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].field").value("Authorization"));
        verifyNoInteractions(auth, manager, service);
    }

    @Test
    void sourcesPassesLiteralQueryToServiceAndSerializesNullFields() throws Exception {
        var row = new RoundDtos.SourceSemesterDto("21", "2610", null, "2026-2027", 1,
                "2026-09-01", "2027-01-15", null, true, null);
        when(service.sources(eq("a%_ Tiếng Việt"), aryEq(new int[]{1, 10})))
                .thenReturn(new RoundDtos.Page<>(List.of(row), 1, 10, 16));

        mvc.perform(bearer(get(SOURCES).queryParam("q", "  a%_ Tiếng Việt  ")
                        .queryParam("page", "1").queryParam("size", "10")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().json("""
                        {"items":[{"sourceSemesterId":"21","code":"2610","name":null,
                          "academicYear":"2026-2027","semesterOrder":1,"startDate":"2026-09-01",
                          "endDate":"2027-01-15","scholarshipSemesterId":null,
                          "selectable":true,"unavailableReason":null}],"page":1,"size":10,"totalElements":16}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void listPassesCanonicalFiltersAndDefaultsWithoutEtag() throws Exception {
        var row = new RoundDtos.RoundSummary("1001", "21", "501", "2026-2027", 1, 1, "SYN-HB", "draft");
        when(service.list(eq(null), eq(21L), eq(1), aryEq(new int[]{0, 20})))
                .thenReturn(new RoundDtos.Page<>(List.of(row), 0, 20, 1));

        mvc.perform(bearer(get(ROUNDS).queryParam("sourceSemesterId", "21").queryParam("number", "1")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.items[0].semesterId").value("501"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void queryBodyAndUrlValidationRunsAfterGroupCheck() throws Exception {
        problem(mvc.perform(bearer(get(SOURCES).queryParam("q", "one", "two"))), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].field").value("q"));
        problem(mvc.perform(bearer(get(GROUPS).queryParam("userId", "301"))), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].field").value("userId"));
        problem(mvc.perform(bearer(get(SOURCES).content("unexpected"))), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].field").value("body"));
        problem(mvc.perform(bearer(get(SOURCES).queryParam("size", "101"))), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        problem(mvc.perform(bearer(get(ROUNDS).queryParam("semesterId", "501").queryParam("sourceSemesterId", "21"))),
                400, "VALIDATION_ERROR");
        problem(mvc.perform(bearer(get(ROUNDS).queryParam("number", "2147483648"))), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].field").value("number"));
        problem(mvc.perform(bearer(get(ROUNDS + "/01001"))), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].field").value("idDotXet"));
        problem(mvc.perform(bearer(post(ROUNDS).queryParam("extra", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].field").value("query"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/xml", "application/*", "application/json; charset=ISO-8859-1"})
    void rejectsUnsupportedMedia(String mediaType) throws Exception {
        problem(mvc.perform(bearer(post(ROUNDS).contentType(mediaType).content(CREATE))),
                415, "UNSUPPORTED_MEDIA_TYPE");
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnsupportedEncodingAndMultipleSingletonHeaders() throws Exception {
        problem(mvc.perform(jsonPost(CREATE).header(HttpHeaders.CONTENT_ENCODING, "gzip")),
                415, "UNSUPPORTED_MEDIA_TYPE");
        problem(mvc.perform(new RepeatedContentTypeRequest().uri(ROUNDS)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer synthetic")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE)),
                400, "VALIDATION_ERROR").andExpect(jsonPath("$.errors[0].field").value("Content-Type"));
        problem(mvc.perform(bearer(put(DETAIL).contentType(MediaType.APPLICATION_JSON).content(UPDATE)
                        .header(HttpHeaders.IF_MATCH, TAG, NEW_TAG))),
                400, "VALIDATION_ERROR").andExpect(jsonPath("$.errors[0].field").value("If-Match"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"number\":1,\"number\":2,\"sourceSemesterId\":\"21\",\"name\":\"SYN\"}",
            "{\"number\":1,\"sourceSemesterId\":\"21\",\"name\":\"SYN\"} {}", "[]", "null", "",
            "{\"number\":1,\"sourceSemesterId\":\"21\",\"name\":\"SYN\",\"status\":\"draft\"}"})
    void rejectsJsonOutsideApprovedObject(String body) throws Exception {
        problem(mvc.perform(jsonPost(body)), 400, "VALIDATION_ERROR")
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedUtf8BeforeCommandParsing() throws Exception {
        byte[] invalid = CREATE.getBytes(StandardCharsets.UTF_8);
        invalid[CREATE.indexOf("SYN")] = (byte) 0xC3;
        problem(mvc.perform(bearer(post(ROUNDS).contentType(MediaType.APPLICATION_JSON).content(invalid))),
                400, "VALIDATION_ERROR").andExpect(jsonPath("$.errors[0].field").value("body"));
        verifyNoInteractions(service);
    }

    @Test
    void bodySizeLimitIsInclusiveAndCheckedBeforeJsonParsing() throws Exception {
        byte[] tooLarge = new byte[1_048_577];
        Arrays.fill(tooLarge, (byte) ' ');
        problem(mvc.perform(bearer(post(ROUNDS).contentType(MediaType.APPLICATION_JSON).content(tooLarge))),
                413, "PAYLOAD_TOO_LARGE");
        problem(mvc.perform(bearer(post(ROUNDS).contentType(MediaType.APPLICATION_JSON)
                        .content(Arrays.copyOf(tooLarge, 1_048_576)))), 400, "VALIDATION_ERROR");
        verifyNoInteractions(service);
    }

    @Test
    void createReturnsLocationAndTagWithinAuthorizedTransaction() throws Exception {
        var command = new RoundDtos.CreateCommand(21, 1, "SYN-HB");
        var actor = new RoundAccess.Actor(301, null, 74L);
        when(service.create(command, actor)).thenReturn(view(1, "SYN-HB", TAG));

        mvc.perform(bearer(post(ROUNDS).contentType("application/json; charset=UTF-8")
                        .header(HttpHeaders.CONTENT_ENCODING, "identity").content(CREATE)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, DETAIL))
                .andExpect(header().string(HttpHeaders.ETAG, TAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().json("""
                        {"id":"1001","sourceSemesterId":"21","number":1,"name":"SYN-HB","status":"draft",
                          "createdBy":"301","semester":{"id":"501","academicYear":"2026-2027","semesterOrder":1,
                          "startDate":"2026-09-01","endDate":"2027-01-15","status":"draft"},
                          "opensAt":null,"closedAt":null,"tuitionSnapshotAt":null,"ruleVersionId":null}
                        """, JsonCompareMode.STRICT));

        var ordered = inOrder(auth, manager, identities, roles, service);
        ordered.verify(auth).verifyAccessToken("synthetic");
        ordered.verify(manager).getTransaction(any());
        ordered.verify(identities).identify(principal, true);
        ordered.verify(roles).findActiveGlobalRole(301, "HOC_BONG", "hoc_bong_chuyen_vien");
        ordered.verify(service).create(command, actor);
        ordered.verify(manager).commit(transaction);
    }

    @Test
    void detailAndUpdateReturnTagOfServiceRepresentation() throws Exception {
        when(service.detail(1001)).thenReturn(view(1, "SYN-HB", TAG));
        when(service.update(1001, new RoundDtos.UpdateCommand(2, "SYN-HB update"), TAG,
                new RoundAccess.Actor(301, null, 74L))).thenReturn(view(2, "SYN-HB update", NEW_TAG));

        mvc.perform(bearer(get(DETAIL))).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, TAG));
        mvc.perform(bearer(put(DETAIL).contentType(MediaType.APPLICATION_JSON).content(UPDATE)
                        .header(HttpHeaders.IF_MATCH, TAG)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, NEW_TAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.name").value("SYN-HB update"));
    }

    @Test
    void updatePreservesPreconditionFailureAfterServiceLookup() throws Exception {
        var command = new RoundDtos.UpdateCommand(2, "SYN-HB update");
        var actor = new RoundAccess.Actor(301, null, 74L);
        when(service.update(1001, command, null, actor))
                .thenThrow(new RoundFailure(428, "PRECONDITION_REQUIRED"));
        when(service.update(1001, command, TAG, actor))
                .thenThrow(new RoundFailure(412, "VERSION_CONFLICT"));

        problem(mvc.perform(bearer(put(DETAIL).contentType(MediaType.APPLICATION_JSON).content(UPDATE))),
                428, "PRECONDITION_REQUIRED");
        problem(mvc.perform(bearer(put(DETAIL).contentType(MediaType.APPLICATION_JSON).content(UPDATE)
                        .header(HttpHeaders.IF_MATCH, TAG))), 412, "VERSION_CONFLICT");
        verify(manager, times(2)).rollback(transaction);
    }

    @ParameterizedTest
    @CsvSource({"2627, uq_kkht_dot_xet_hoc_ky_so_dot, 409, ROUND_EXISTS",
            "2601, uq_hb_anh_xa_ky_nguon_context, 409, SEMESTER_MAPPING_CONFLICT",
            "2601, uq_hb_hoc_ky_nam_hoc_thu_tu_ky, 409, SEMESTER_MAPPING_CONFLICT",
            "1205, synthetic_sql_diagnostic, 503, DEPENDENCY_UNAVAILABLE",
            "1222, synthetic_sql_diagnostic, 503, DEPENDENCY_UNAVAILABLE",
            "2627, unrelated_constraint, 500, INTERNAL_ERROR"})
    void mapsOnlyKnownDependencyOrConstraintErrors(int sqlCode, String diagnostic, int status, String code)
            throws Exception {
        when(service.create(any(), any())).thenThrow(new DataIntegrityViolationException("SQL",
                new SQLException(diagnostic, "23000", sqlCode)));

        problem(mvc.perform(jsonPost(CREATE)), status, code)
                .andExpect(content().string(not(containsString(diagnostic))));
        verify(service).create(any(), any());
        verify(manager).rollback(transaction);
    }

    @Test
    void corsUsesConfiguredOriginsAndDoesNotAuthenticatePreflight() throws Exception {
        mvc.perform(options(DETAIL).header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type,If-Match"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PUT")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("If-Match")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(options(DETAIL).header(HttpHeaders.ORIGIN, "https://other.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PUT"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verifyNoInteractions(auth, service);

        when(service.detail(1001)).thenReturn(view(1, "SYN-HB", TAG));
        mvc.perform(bearer(get(DETAIL)).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("ETag")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, containsString("Location")));
    }

    @Test
    void unsupportedMethodOnKnownRoundRouteReturnsProblem() throws Exception {
        problem(mvc.perform(delete(DETAIL)), 405, "METHOD_NOT_ALLOWED")
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("PUT")))
                .andExpect(jsonPath("$.instance").value(DETAIL));
        verifyNoInteractions(auth, service);
    }

    @Test
    void unsupportedMethodOutsideScholarshipKeepsDefaultMvcHandling() throws Exception {
        mvc.perform(delete(OTHER_ROUTE))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(header().doesNotExist(HttpHeaders.CACHE_CONTROL))
                .andExpect(content().string(""));
    }

    private void identity(Long student, boolean specialist) {
        when(identities.identify(principal, true)).thenReturn(new FoundationAccess.Identity(301, student, principal));
        when(roles.findActiveGlobalRole(301, "HOC_BONG", "hoc_bong_chuyen_vien"))
                .thenReturn(specialist ? Optional.of(new FoundationRoleAccess.ActiveRole(74)) : Optional.empty());
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer synthetic");
    }

    private static MockHttpServletRequestBuilder jsonPost(String body) {
        return bearer(post(ROUNDS).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RoundDtos.View view(int number, String name, String tag) {
        var period = new RoundDtos.SemesterDto("501", "2026-2027", 1, "2026-09-01", "2027-01-15", "draft");
        return new RoundDtos.View(new RoundDtos.RoundDetail("1001", "21", number, name, "draft", "301",
                period, null, null, null, null), tag);
    }

    private static ResultActions problem(ResultActions response, int statusCode, String code) throws Exception {
        return response.andExpect(status().is(statusCode))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.instance").value(startsWith(BASE)))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @RestController
    static class OtherController {
        @GetMapping(OTHER_ROUTE)
        String get() {
            return "other controller";
        }
    }

    // MockHttpServletRequest.addHeader replaces existing Content-Type values.
    private static class RepeatedContentTypeRequest
            extends AbstractMockHttpServletRequestBuilder<RepeatedContentTypeRequest> {
        RepeatedContentTypeRequest() {
            super(HttpMethod.POST);
        }

        @Override
        protected MockHttpServletRequest createServletRequest(ServletContext context) {
            return new MockHttpServletRequest(context) {
                @Override
                public Enumeration<String> getHeaders(String name) {
                    if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                        return Collections.enumeration(List.of("application/json", "application/json"));
                    }
                    return super.getHeaders(name);
                }
            };
        }
    }
}
