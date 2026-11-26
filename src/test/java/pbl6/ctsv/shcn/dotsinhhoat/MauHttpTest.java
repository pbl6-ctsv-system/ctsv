package pbl6.ctsv.shcn.dotsinhhoat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.shcn.dotsinhhoat.controller.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.Quyen;
import pbl6.ctsv.shcn.dotsinhhoat.service.MauService;
import pbl6.ctsv.shcn.dotsinhhoat.repository.MauRepository;
import pbl6.ctsv.shcn.dotsinhhoat.validation.*;
class MauHttpTest {
 final DotServiceTest f=new DotServiceTest();final AuthAccess auth=mock(AuthAccess.class);final ObjectMapper json=new ObjectMapper();final MauRepository repo=mock(MauRepository.class);MockMvc mvc;
 final String url="/api/shcn/dot-sinh-hoat/7/mau-bien-ban";
 @BeforeEach void setup(){f.setup();when(auth.verifyAccessToken("fixture")).thenReturn(f.principal);when(f.repository.find(7,false)).thenReturn(f.root());when(f.repository.find(7,true)).thenReturn(f.root());mvc=MockMvcBuilders.standaloneSetup(new MauController(auth,f.service,new MauService(f.repository,repo,new MauRequests(),f.clock),new DotRequests(json),new MauRequests())).setControllerAdvice(new DotErrors()).build();}
 String body(){var n=json.createObjectNode().put("expectedVersion","AAAAAAAAAAE=").put("tenMau","Biên bản SHCN");var a=n.putArray("muc");for(var m:TemplateDictionary.defaults()){var item=(ObjectNode)json.valueToTree(m);item.remove("laMucHeThong");a.add(item);}return n.toString();}
 @Test void emptyDtoIs200WithDictionaryAndNoSideEffects()throws Exception{mvc.perform(get(url).header("Authorization","Bearer fixture")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store")).andExpect(jsonPath("$.daCoMau").value(false)).andExpect(jsonPath("$.id").isEmpty()).andExpect(jsonPath("$.dotVersion").value("AAAAAAAAAAE=")).andExpect(jsonPath("$.danhMuc.mucHeThong.length()").value(3));verify(repo,never()).save(anyLong(),any(),any(),any());}
 @Test void noTokenDoesNotReachIdentityOrRepository()throws Exception{mvc.perform(get(url)).andExpect(status().isUnauthorized());verifyNoInteractions(repo);}
 @Test void readOnlyCannotPutEvenWithValidBody()throws Exception{when(f.reference.permissions(f.identity)).thenReturn(new Quyen(true,false,true));mvc.perform(put(url).header("Authorization","Bearer fixture").contentType("application/json").content(body())).andExpect(status().isForbidden());verify(repo,never()).save(anyLong(),any(),any(),any());}
 @Test void malformedOrDuplicateBodyReturns400()throws Exception{String duplicate="{\"tenMau\":\"x\","+body().substring(1);for(String s:new String[]{"{}",duplicate,body().replace("NGUOI_NHAP","OTHER")})mvc.perform(put(url).header("Authorization","Bearer fixture").contentType("application/json").content(s)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));}
 @Test void rejectsOversizedAndWrongContentType()throws Exception{mvc.perform(put(url).header("Authorization","Bearer fixture").contentType("text/plain").content(body())).andExpect(status().isUnsupportedMediaType());mvc.perform(put(url).header("Authorization","Bearer fixture").contentType("application/json").content(" ".repeat(1048577))).andExpect(status().isPayloadTooLarge());}
 @Test void getHasNoQueryOrBody()throws Exception{mvc.perform(get(url).header("Authorization","Bearer fixture").param("q","x")).andExpect(status().isBadRequest());mvc.perform(get(url).header("Authorization","Bearer fixture").content("{}")).andExpect(status().isBadRequest());}
 @Test void systemMutationHasSpecificProblemCode()throws Exception{mvc.perform(put(url).header("Authorization","Bearer fixture").contentType("application/json").content(body().replace("Điểm danh","Tùy biến"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_SECTION_IMMUTABLE"));}
 @Test void successfulPutReturnsRootTokenAndStoredTemplate()throws Exception{
  var template=mock(pbl6.ctsv.shcn.dotsinhhoat.entity.MauBienBan.class);when(template.id()).thenReturn(19L);when(template.name()).thenReturn("Biên bản SHCN");when(template.state()).thenReturn("HOP_LE");when(repo.find(7)).thenReturn(null,template);when(repo.sections(19)).thenReturn(TemplateDictionary.defaults());
  doAnswer(i->{var root=f.root();org.springframework.test.util.ReflectionTestUtils.setField(root,"phienBanGhi",new byte[]{0,0,0,0,0,0,0,2});when(f.repository.find(7,false)).thenReturn(root);return null;}).when(repo).compareAndTouch(eq(7L),any(),any());
  mvc.perform(put(url).header("Authorization","Bearer fixture").contentType("application/json").content(body())).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("19")).andExpect(jsonPath("$.dotVersion").value("AAAAAAAAAAI=")).andExpect(jsonPath("$.muc[3].laMucHeThong").value(true)).andExpect(jsonPath("$.muc[3].batBuoc").value(false));
 }
 @Test void corsAllowsTemplatePutOnlyFromConfiguredOrigin()throws Exception{
  var environment=new org.springframework.mock.env.MockEnvironment().withProperty("ctsv.auth.origins[0]","https://fixture.invalid");
  var registration=(org.springframework.boot.web.servlet.FilterRegistrationBean<?>)org.springframework.test.util.ReflectionTestUtils.invokeMethod(new pbl6.ctsv.shcn.dotsinhhoat.config.DotConfiguration(),"shcnDotCors",environment);
  var filtered=MockMvcBuilders.standaloneSetup(new MauController(auth,f.service,new MauService(f.repository,repo,new MauRequests(),f.clock),new DotRequests(json),new MauRequests())).addFilters(registration.getFilter()).build();
  filtered.perform(options(url).header("Origin","https://fixture.invalid").header("Access-Control-Request-Method","PUT").header("Access-Control-Request-Headers","authorization,content-type,x-csrf-protection")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Methods",org.hamcrest.Matchers.containsString("PUT")));
  filtered.perform(options(url).header("Origin","https://untrusted.invalid").header("Access-Control-Request-Method","PUT")).andExpect(status().isForbidden());
 }
 @Test void catalogIsAvailableBeforeAnyRoundAndDoesNotReadTemplateData()throws Exception{mvc.perform(get("/api/shcn/tra-cuu/cau-truc-mau-bien-ban").header("Authorization","Bearer fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.mucHeThong.length()").value(3)).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(repo);}
 @Test void catalogStillRequiresRbacAndRejectsQuery()throws Exception{mvc.perform(get("/api/shcn/tra-cuu/cau-truc-mau-bien-ban").header("Authorization","Bearer fixture").param("sql","x")).andExpect(status().isBadRequest());when(f.reference.permissions(f.identity)).thenReturn(new Quyen(false,false,false));mvc.perform(get("/api/shcn/tra-cuu/cau-truc-mau-bien-ban").header("Authorization","Bearer fixture")).andExpect(status().isForbidden());verifyNoInteractions(repo);}

}
