package pbl6.ctsv.shcn.dotsinhhoat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.shcn.dotsinhhoat.controller.*;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.reference.*;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService;

class DotAudiencePreviewHttpTest {
    final DotServiceTest f=new DotServiceTest(); final AuthAccess auth=mock(AuthAccess.class);
    Quyen rights=new Quyen(true,true,true);MockMvc mvc;
    final List<DoiTuong> groups=List.of(new DoiTuong("KHOA_HOC","K2024","OFFLINE",LocalDate.of(2026,12,1),LocalDate.of(2026,12,7),null));
    @BeforeEach void setup(){
        f.setup();when(auth.verifyAccessToken("fixture")).thenReturn(f.principal);
        var m=new SemesterAudienceResolver.Member(1,"SV-VD","Synthetic",10,"L10","Class 10",1L,"K2024","2024","THUONG","0","0","0");
        var projection=SemesterAudienceResolver.resolve(List.of(m),List.of());
        DotReference reference=mock(DotReference.class,inv->switch(inv.getMethod().getName()){
            case "permissions"->rights;case "projection"->projection;default->RETURNS_DEFAULTS.answer(inv);
        });
        var service=new DotService(f.manager,f.access,reference,f.repository,f.clock);
        when(f.repository.find(7L,false)).thenReturn(f.root());when(f.repository.audiences(7L)).thenReturn(groups);
        mvc=MockMvcBuilders.standaloneSetup(new DotController(auth,service,new DotRequests(new ObjectMapper()))).setControllerAdvice(new DotErrors()).build();
    }
    @Test void previewIncludesRootVersionAndConfiguredClassWithoutEmbeddingStudents()throws Exception{
        mvc.perform(get("/api/shcn/dot-sinh-hoat/7/doi-tuong").header("Authorization","Bearer fixture"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.dotVersion").value("AAAAAAAAAAE="))
            .andExpect(jsonPath("$.tongHop.tongSoLop").value(1)).andExpect(jsonPath("$.lopApDung.items[0].maLop").value("L10"))
            .andExpect(jsonPath("$.trangThaiPhanNhom").value("CAN_XAC_MINH"))
            .andExpect(jsonPath("$.lopApDung.items[0].sinhVien").doesNotExist());
        verify(f.repository,never()).audit(anyLong(),any(),anyLong());
    }
    @Test void previewPermissionIsReadNotPermissionToWrite()throws Exception{
        rights=new Quyen(true,false,false);
        mvc.perform(get("/api/shcn/dot-sinh-hoat/7/doi-tuong").header("Authorization","Bearer fixture"))
            .andExpect(status().isOk()).andExpect(header().string("X-SHCN-Can-Prepare","false"));
    }
    @Test void unauthorizedPreviewNeverReadsRoot()throws Exception{
        rights=new Quyen(false,false,true);
        mvc.perform(get("/api/shcn/dot-sinh-hoat/7/doi-tuong").header("Authorization","Bearer fixture")).andExpect(status().isForbidden());
        verify(f.repository,never()).find(anyLong(),anyBoolean());
    }
    @Test void previewRejectsDuplicateUnknownAndInvalidPagination()throws Exception{
        for(String query:List.of("?page=0&page=1","?scope=all","?size=101"))
            mvc.perform(get("/api/shcn/dot-sinh-hoat/7/doi-tuong"+query).header("Authorization","Bearer fixture")).andExpect(status().isBadRequest());
    }
}
