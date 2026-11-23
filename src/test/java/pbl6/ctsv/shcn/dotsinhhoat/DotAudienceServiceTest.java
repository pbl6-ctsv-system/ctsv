package pbl6.ctsv.shcn.dotsinhhoat;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService.Action;

class DotAudienceServiceTest {
    final DotServiceTest f=new DotServiceTest();
    final List<DoiTuong> groups=List.of(new DoiTuong("KHOA_HOC","2023","ONLINE",LocalDate.of(2026,12,1),LocalDate.of(2026,12,7),null));
    @BeforeEach void setup(){f.setup();}
    CauHinh input(List<DoiTuong> g){var i=f.input;return new CauHinh(i.maHocKy(),i.tenDot(),i.khungNgayBatDau(),i.khungNgayKetThuc(),i.khungTietChoPhep(),i.hanChotDangKyLich(),g);}
    @Test void createValidatesAudienceBeforeAnyWriteAndReturnsStoredGroups(){
        var c=input(groups);when(f.repository.create(eq(c),any(),any())).thenReturn(f.root());when(f.repository.audiences(7)).thenReturn(groups);
        var saved=f.service.execute(f.principal,Action.PREPARE,a->f.service.create(a,c));
        assertEquals(groups,saved.value().doiTuongApDung());var order=inOrder(f.reference,f.repository);
        order.verify(f.reference).requireAudiences("0010",groups);order.verify(f.repository).create(eq(c),any(),any());order.verify(f.repository).replaceAudiences(eq(7L),eq(groups),any());
        assertEquals(1,f.manager.commits);
    }
    @Test void invalidRosterCannotLeaveOrphanDraft(){
        doThrow(DotFailure.invalid("doiTuongApDung")).when(f.reference).requireAudiences("0010",groups);
        assertThrows(DotFailure.class,()->f.service.execute(f.principal,Action.PREPARE,a->f.service.create(a,input(groups))));verifyNoInteractions(f.repository);assertEquals(1,f.manager.rollbacks);
    }
    @Test void omittedArrayPreservesExistingGroups(){
        when(f.repository.find(7,true)).thenReturn(f.root());when(f.repository.audiences(7)).thenReturn(groups);when(f.repository.update(eq(7L),any(),eq(f.input),eq(false),any())).thenReturn(f.root());
        var saved=f.service.execute(f.principal,Action.PREPARE,a->f.service.update(a,7,new SuaDot(f.token,f.input)));
        assertEquals(groups,saved.value().doiTuongApDung());verify(f.repository,never()).replaceAudiences(anyLong(),any(),any());verify(f.reference,never()).requireAudiences(any(),any());
    }
    @Test void explicitEmptyArrayClearsGroupsAndInvalidatesDraftAssignment(){
        var c=input(List.of());when(f.repository.find(7,true)).thenReturn(f.root());when(f.repository.audiences(7)).thenReturn(groups,List.of());when(f.repository.update(eq(7L),any(),eq(c),eq(true),any())).thenReturn(f.root());
        var saved=f.service.execute(f.principal,Action.PREPARE,a->f.service.update(a,7,new SuaDot(f.token,c)));
        assertTrue(saved.value().doiTuongApDung().isEmpty());verify(f.repository).replaceAudiences(eq(7L),eq(List.of()),any());verify(f.repository).update(eq(7L),any(),eq(c),eq(true),any());
    }
    @Test void semesterChangeRevalidatesPreservedGroupsInsteadOfReusingPreviousRoster(){
        var c=new CauHinh("0020",f.input.tenDot(),f.input.khungNgayBatDau(),f.input.khungNgayKetThuc(),f.input.khungTietChoPhep(),f.input.hanChotDangKyLich());
        when(f.repository.find(7,true)).thenReturn(f.root());when(f.repository.audiences(7)).thenReturn(groups);doThrow(DotFailure.invalid("doiTuongApDung")).when(f.reference).requireAudiences("0020",groups);
        assertThrows(DotFailure.class,()->f.service.execute(f.principal,Action.PREPARE,a->f.service.update(a,7,new SuaDot(f.token,c))));verify(f.repository,never()).update(anyLong(),any(),any(),anyBoolean(),any());
    }
    @Test void staleVersionCannotReplaceChildren(){
        when(f.repository.find(7,true)).thenReturn(f.root());
        assertEquals("VERSION_CONFLICT",assertThrows(DotFailure.class,()->f.service.execute(f.principal,Action.PREPARE,a->f.service.update(a,7,new SuaDot(new byte[8],input(groups))))).code());
        verify(f.repository,never()).replaceAudiences(anyLong(),any(),any());verify(f.reference,never()).requireAudiences(any(),any());
    }
    @Test void childWriteFailureRollsBackWholeOperationAndDoesNotAuditSuccess(){
        var c=input(groups);when(f.repository.create(eq(c),any(),any())).thenReturn(f.root());doThrow(new IllegalStateException("synthetic child failure")).when(f.repository).replaceAudiences(anyLong(),any(),any());
        assertThrows(IllegalStateException.class,()->f.service.execute(f.principal,Action.PREPARE,a->f.service.create(a,c)));assertEquals(1,f.manager.rollbacks);assertEquals(0,f.manager.commits);verify(f.repository,never()).audit(anyLong(),any(),anyLong());
    }
    @Test void revokedPermissionBlocksAudienceWrites(){
        when(f.reference.permissions(f.identity)).thenReturn(new Quyen(true,false,true));assertEquals("FORBIDDEN",assertThrows(DotFailure.class,()->f.service.execute(f.principal,Action.PREPARE,a->f.service.create(a,input(groups)))).code());verifyNoInteractions(f.repository);
    }
}
