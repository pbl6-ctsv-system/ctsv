package pbl6.ctsv.shcn.dotsinhhoat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.shcn.dotsinhhoat.controller.*;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
class DotSemesterAudienceHttpTest {
 final DotServiceTest f=new DotServiceTest();final AuthAccess auth=mock(AuthAccess.class);MockMvc mvc;
 final String classes="/api/shcn/tra-cuu/lop-theo-ky?maHocKy=2610&loaiDoiTuong=KHOA_HOC&maDoiTuong=2023";
 final String students="/api/shcn/tra-cuu/sinh-vien-theo-ky?maHocKy=2610&maLop=23DT1";
 @BeforeEach void setup(){f.setup();when(auth.verifyAccessToken("fixture")).thenReturn(f.principal);mvc=MockMvcBuilders.standaloneSetup(new DotController(auth,f.service,new DotRequests(new ObjectMapper()))).setControllerAdvice(new DotErrors()).build();}
 @Test void classesAreScopedAndPaginated()throws Exception{
  when(f.reference.classes(eq("2610"),eq("KHOA_HOC"),eq("2023"),any())).thenReturn(new Page<>(List.of(new LopTheoKyDTO("23DT1","Class","THUONG","2023","Cohort",3,2)),0,25,1));
  mvc.perform(get(classes).header("Authorization","Bearer fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].soSinhVienCanXacMinh").value(2)).andExpect(jsonPath("$.totalElements").value(1));verify(f.reference).classes(eq("2610"),eq("KHOA_HOC"),eq("2023"),argThat(q->q.page()==0&&q.size()==25));verifyNoInteractions(f.repository);
 }
 @Test void studentResponseWhitelistsIdentityAndEvidence()throws Exception{
  when(f.reference.students(eq("2610"),eq("23DT1"),any())).thenReturn(new Page<>(List.of(new SinhVienTheoKyDTO("9007199254740999","00123","Student","23DT1","Class","THUONG","HOC_VU_DUNG_KY","LOP_HIEN_TAI_NGUON","CAN_XAC_MINH",false)),0,25,1));
  mvc.perform(get(students).header("Authorization","Bearer fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("9007199254740999")).andExpect(jsonPath("$.items[0].maSinhVien").value("00123")).andExpect(jsonPath("$.items[0].duCanCuNguon").value(false)).andExpect(jsonPath("$.items[0].email").doesNotExist()).andExpect(header().string("Cache-Control","private, no-store"));verifyNoInteractions(f.repository);
 }
 @Test void deniedReferencePermissionNeverReadsStudentData()throws Exception{
  when(f.reference.permissions(f.identity)).thenReturn(new Quyen(true,true,false));for(String url:List.of(classes,students))mvc.perform(get(url).header("Authorization","Bearer fixture")).andExpect(status().isForbidden());verify(f.reference,never()).classes(any(),any(),any(),any());verify(f.reference,never()).students(any(),any(),any());
 }
 @Test void missingAuthenticationNeverReadsSource()throws Exception{mvc.perform(get(students)).andExpect(status().isUnauthorized());verifyNoInteractions(f.reference);}
 @Test void requiredAndUnknownQueryFieldsAreRejected()throws Exception{
  for(String url:List.of("/api/shcn/tra-cuu/lop-theo-ky?maHocKy=2610",students+"&maHocKy=2520",students+"&role=admin",classes.replace("KHOA_HOC","KHOA")))mvc.perform(get(url).header("Authorization","Bearer fixture")).andExpect(status().isBadRequest());
  verify(f.reference,never()).classes(any(),any(),any(),any());verify(f.reference,never()).students(any(),any(),any());
 }
 @Test void invalidPaginationIsRejectedBeforeSourceRead()throws Exception{
  for(String suffix:List.of("&size=0","&size=101","&page=1.5","&page=2147483648","&page=100001&size=1","&sort=maSinhVien:desc"))mvc.perform(get(students+suffix).header("Authorization","Bearer fixture")).andExpect(status().isBadRequest());verify(f.reference,never()).students(any(),any(),any());
 }
 @Test void sourceMappingFailureIs409AndDoesNotLeakSql()throws Exception{
  when(f.reference.students(any(),any(),any())).thenThrow(new DotFailure(409,"SOURCE_MAPPING_INCOMPLETE"));mvc.perform(get(students).header("Authorization","Bearer fixture")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOURCE_MAPPING_INCOMPLETE"));
 }
 @Test void unavailableSourceIsNotAnEmptyPage()throws Exception{when(f.reference.classes(any(),any(),any(),any())).thenThrow(DotFailure.unavailable());mvc.perform(get(classes).header("Authorization","Bearer fixture")).andExpect(status().isServiceUnavailable());}
}
