package pbl6.ctsv.shcn.dotsinhhoat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.dotsinhhoat.controller.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.reference.DotReference;
import pbl6.ctsv.shcn.dotsinhhoat.repository.DotRepository;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService;

class DotHttpTest {
 final DotServiceTest fixture=new DotServiceTest();final AuthAccess auth=mock(AuthAccess.class);MockMvc mvc;
 final String body="""
 {"maHocKy":"0010","tenDot":"Synthetic round","khungNgayBatDau":"2026-12-01","khungNgayKetThuc":"2026-12-07","khungTietChoPhep":[{"tietDau":5,"tietCuoi":6}],"hanChotDangKyLich":"2026-12-02T00:00:00Z"}
 """;
 @BeforeEach void setup(){fixture.setup();when(auth.verifyAccessToken("fixture")).thenReturn(fixture.principal);mvc=MockMvcBuilders.standaloneSetup(new DotController(auth,fixture.service,new DotRequests(new ObjectMapper()))).setControllerAdvice(new DotErrors()).build();}
 @Test void rejectsUnauthenticatedBeforeAnyLookup()throws Exception{mvc.perform(get("/api/shcn/dot-sinh-hoat")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));verifyNoInteractions(fixture.reference,fixture.repository);}
 @Test void rejectsGlobalPermissionBeforeBadPayload()throws Exception{when(fixture.reference.permissions(fixture.identity)).thenReturn(new Quyen(false,false,false));mvc.perform(post("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture").contentType("application/json").content("{")) .andExpect(status().isForbidden());verifyNoInteractions(fixture.repository);}
 @Test void createReturns201LocationDtoAndPrepareHeader()throws Exception{when(fixture.repository.create(any(),any(),any())).thenReturn(fixture.root());mvc.perform(post("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture").contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(header().string("Location","/api/shcn/dot-sinh-hoat/7")).andExpect(header().string("X-SHCN-Can-Prepare","true")).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.maHocKy").value("0010")).andExpect(jsonPath("$.khungTietChoPhep[0].tietDau").value(5)).andExpect(jsonPath("$.maNguoiTao").doesNotExist());}
 @Test void getRejectsUnknownAndRepeatedQuery()throws Exception{for(String url:List.of("/api/shcn/dot-sinh-hoat?foo=x","/api/shcn/dot-sinh-hoat?page=0&page=1"))mvc.perform(get(url).header("Authorization","Bearer fixture")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());}
 @Test void detailIsAccessibleWithoutSourceAndReadOnlyHeader()throws Exception{when(fixture.reference.permissions(fixture.identity)).thenReturn(new Quyen(true,false,false));when(fixture.repository.find(7,false)).thenReturn(fixture.root());mvc.perform(get("/api/shcn/dot-sinh-hoat/7").header("Authorization","Bearer fixture")).andExpect(status().isOk()).andExpect(header().string("X-SHCN-Can-Prepare","false")).andExpect(jsonPath("$.version").value("AAAAAAAAAAE="));verify(fixture.reference,never()).requireSemester(any());}
 @Test void patchRequiresTypedVersionInBody()throws Exception{mvc.perform(patch("/api/shcn/dot-sinh-hoat/7").header("Authorization","Bearer fixture").header("If-Match","AAAAAAAAAAE=").contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("expectedVersion"));}
 @Test void stalePatchReturns409AndNeverWrites()throws Exception{when(fixture.repository.find(7,true)).thenReturn(fixture.root());mvc.perform(patch("/api/shcn/dot-sinh-hoat/7").header("Authorization","Bearer fixture").contentType("application/json").content(body.replace("{\"maHocKy\"","{\"expectedVersion\":\"AAAAAAAAAAA=\",\"maHocKy\""))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));verify(fixture.repository,never()).audit(anyLong(),any(),anyLong());}
 @Test void semesterLookupPreservesBusinessCodeAndNullableYear()throws Exception{when(fixture.reference.semesters(any())).thenReturn(new Page<>(List.of(new HocKyDTO("0010","Synthetic semester",null)),0,25,1));mvc.perform(get("/api/shcn/tra-cuu/hoc-ky").header("Authorization","Bearer fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].ma").value("0010")).andExpect(jsonPath("$.items[0].namHoc").isEmpty());}
 @Test void missingReferencePermissionReturns403()throws Exception{when(fixture.reference.permissions(fixture.identity)).thenReturn(new Quyen(true,true,false));mvc.perform(get("/api/shcn/tra-cuu/hoc-ky").header("Authorization","Bearer fixture")).andExpect(status().isForbidden());}
 @Test void internalFailureHasSafeProblemDetail()throws Exception{when(fixture.repository.list(any())).thenThrow(new IllegalStateException("SQL/password MUST NOT LEAK"));var res=mvc.perform(get("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture")).andExpect(status().isInternalServerError()).andExpect(content().contentType("application/problem+json")).andExpect(jsonPath("$.traceId").isNotEmpty()).andReturn();assertFalse(res.getResponse().getContentAsString().contains("MUST NOT LEAK"));}

 @Test void unsupportedMediaAndCharsetReturn415()throws Exception{for(String media:List.of("text/plain","application/json;charset=not-a-real-charset"))mvc.perform(post("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture").contentType(media).content(body)).andExpect(status().isUnsupportedMediaType());}
 @Test void oversizedStreamReturns413()throws Exception{mvc.perform(post("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture").contentType("application/json").content(new byte[1048577])).andExpect(status().isPayloadTooLarge());verifyNoInteractions(fixture.repository);}
 @Test void foundationIdentityFailurePreserves401()throws Exception{when(fixture.access.identify(fixture.principal,true)).thenThrow(new FoundationAccess.Failure(401,"UNAUTHENTICATED",null));mvc.perform(get("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture")).andExpect(status().isUnauthorized());verifyNoInteractions(fixture.repository);}
 @Test void unknownSemesterReturns400FieldAndNeverInserts()throws Exception{doThrow(DotFailure.invalid("maHocKy")).when(fixture.reference).requireSemester("0010");mvc.perform(post("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture").contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("maHocKy"));verifyNoInteractions(fixture.repository);}
 @Test void ambiguousOrUnavailableSourceReturns503()throws Exception{doThrow(DotFailure.unavailable()).when(fixture.reference).requireSemester("0010");mvc.perform(post("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture").contentType("application/json").content(body)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));verifyNoInteractions(fixture.repository);}

 @Test void groupsLookupRequiresSemesterAndUsesReferencePermission()throws Exception{
  mvc.perform(get("/api/shcn/tra-cuu/nhom-lop").header("Authorization","Bearer fixture")).andExpect(status().isBadRequest());
  when(fixture.reference.groups("0010")).thenReturn(new NhomLopDTO(List.of(new KhoaHocDTO("2023","Kh?a 2023",2)),List.of(),0,"CHUA_CO_DU_LIEU"));
  mvc.perform(get("/api/shcn/tra-cuu/nhom-lop?maHocKy=0010").header("Authorization","Bearer fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.khoaHoc[0].ma").value("2023")).andExpect(jsonPath("$.trangThaiLopTreTienDo").value("CHUA_CO_DU_LIEU"));
  when(fixture.reference.permissions(fixture.identity)).thenReturn(new Quyen(true,true,false));
  mvc.perform(get("/api/shcn/tra-cuu/nhom-lop?maHocKy=0010").header("Authorization","Bearer fixture")).andExpect(status().isForbidden());
 }
 @Test void createAcceptsGroupsAndReturnsThemWithVersion()throws Exception{
  when(fixture.repository.create(any(),any(),any())).thenReturn(fixture.root());
  var groups=List.of(new DoiTuong("KHOA_HOC","2023","ONLINE",null,null,null));when(fixture.repository.audiences(7)).thenReturn(groups);
  var n=(com.fasterxml.jackson.databind.node.ObjectNode)new ObjectMapper().readTree(body);n.set("doiTuongApDung",new ObjectMapper().valueToTree(groups));
  mvc.perform(post("/api/shcn/dot-sinh-hoat").header("Authorization","Bearer fixture").contentType("application/json").content(n.toString())).andExpect(status().isCreated()).andExpect(jsonPath("$.doiTuongApDung[0].hinhThucChoPhep").value("ONLINE")).andExpect(jsonPath("$.version").value("AAAAAAAAAAE="));
 }
}
