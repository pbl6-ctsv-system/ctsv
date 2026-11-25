package pbl6.ctsv.shcn.dotsinhhoat.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pbl6.ctsv.shcn.dotsinhhoat.reference.SemesterAudienceResolver.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.Query;

class SemesterAudiencePolicyTest {
    final ObjectMapper json=new ObjectMapper();
    Member person(long id,long classroom,String kind,String stop){return new Member(id,"SV"+id,"Synthetic student "+id,classroom,"L"+classroom,"THUONG".equals(kind)?"Class "+classroom:"RenLuyen",1L,"K2024","2024",kind,stop,"0","0");}
    @Test void negativeFlagsWithoutRosterCoverageDoNotConfirmRegularMembership(){
        var p=SemesterAudienceResolver.resolve(List.of(person(1,10,"THUONG","0")),List.of());
        assertEquals("CAN_XAC_MINH",p.members().getFirst().verification());
        var dto=SemesterAudienceResolver.students(p,"L10",new Query(0,25,0,false,"",null,null,null));
        assertFalse(dto.items().getFirst().duCanCuNguon());
    }
    @Test void emptySemesterExplainsMissingAcademicAndRosterEvidence(){
        var n=json.valueToTree(SemesterAudienceResolver.resolve(List.of(),List.of()).groups());
        assertEquals("CHUA_CO_DU_LIEU",n.path("trangThaiDuLieuHocVu").asText());
        assertEquals("CAN_XAC_MINH",n.path("trangThaiPhanNhom").asText());
        assertTrue(n.path("canhBao").toString().contains("CHUA_CO_HOC_VU_DUNG_KY"));
        assertTrue(n.path("canhBao").toString().contains("CHUA_CO_PHAN_LOP_DUNG_KY"));
    }
    @Test void existingRosterIsNotProofOfCompleteSemesterCoverage(){
        var p=SemesterAudienceResolver.resolve(List.of(person(1,10,"THUONG","0"),person(2,10,"THUONG","0")),List.of(person(1,30,"TRE_TIEN_DO",null)));
        var n=json.valueToTree(p.groups());assertEquals("CO_DU_LIEU",n.path("trangThaiDuLieuHocVu").asText());
        assertEquals("CO_DU_LIEU",n.path("trangThaiLopTreTienDo").asText());
        assertTrue(n.path("canhBao").toString().contains("CHUA_XAC_MINH_DO_PHU_PHAN_LOP"));
        assertEquals(1,p.groups().khoaHoc().size());assertEquals(1,p.groups().khoaHoc().getFirst().soLop());
        assertEquals(1,p.members().stream().filter(r->r.member().kind().equals("THUONG")).count());
        assertEquals("CAN_XAC_MINH",p.members().stream().filter(r->r.member().kind().equals("THUONG")).findFirst().orElseThrow().verification());
    }
    @Test void delayedOnlyCohortIsNeverReturnedEvenBeforeCheckboxSelection(){
        var p=SemesterAudienceResolver.resolve(List.of(person(1,10,"THUONG","0")),List.of(person(1,30,"TRE_TIEN_DO",null)));
        assertTrue(p.groups().khoaHoc().isEmpty());assertEquals(30,p.members().getFirst().member().classId());
    }
    @Test void classUnverifiedCountIncludesMissingRosterCoverage(){
        var p=SemesterAudienceResolver.resolve(List.of(person(1,10,"THUONG","0"),person(2,10,"THUONG",null)),List.of());
        var out=SemesterAudienceResolver.classes(p,"KHOA_HOC","K2024",new Query(0,25,0,false,"",null,null,null));
        assertEquals(2,out.items().getFirst().soSinhVienCanXacMinh());
    }
    @Test void stoppedStudentNeverCreatesAnOrdinaryCohort(){
        var p=SemesterAudienceResolver.resolve(List.of(person(1,10,"THUONG","1")),List.of());assertTrue(p.groups().khoaHoc().isEmpty());
    }
}
