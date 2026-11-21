package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.hocbong.kkht.round.entity.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Service business rules with mocked public source/repository. Does not prove SQL rollback/races. */
class RoundServiceTest {
    final RoundRepository repo=mock(RoundRepository.class);
    final AcademicSemesterSource source=mock(AcademicSemesterSource.class);
    final ObjectMapper json=new ObjectMapper();
    final Clock clock=Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"),ZoneOffset.UTC);
    final RoundService service=new RoundService(repo,source,json,clock);
    final RoundAccess.Actor actor=new RoundAccess.Actor(301L,null,401L);
    final byte[] version={0,0,0,0,0,0,0,1};
    long nextId=500;

    @BeforeEach void generatedIds() {
        doAnswer(call -> {ReflectionTestUtils.setField(call.getArgument(0),"id",++nextId);return null;})
            .when(repo).persist(any());
    }

    @Test void sourcesAreReadOnlyAndUseCodeYearInsteadOfRawYear() {
        when(source.findPage(null,0,20)).thenReturn(new AcademicSemesterSource.Page(List.of(term(21,"2510")),0,20,1));
        var page=service.sources(null,new int[]{0,20});
        assertThat(page).isNotNull();
        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(row.academicYear()).isEqualTo("2025-2026");
            assertThat(row.semesterOrder()).isEqualTo(1); assertThat(row.selectable()).isTrue();
            assertThat(row.scholarshipSemesterId()).isNull();
        });
        verify(repo,never()).persist(any());
    }

    @Test void aFullSourcePageDoesNotIssueOneAliasAndSemesterQueryPerRow() {
        var items=java.util.stream.IntStream.range(0,100)
            .mapToObj(i -> term(i+1,String.format("%02d10",i))).toList();
        when(source.findPage(null,0,100)).thenReturn(new AcademicSemesterSource.Page(items,0,100,100));
        var page=service.sources(null,new int[]{0,100});
        assertThat(page.items()).hasSize(100);
        verify(repo).semestersBySources(anyList());
        verify(repo).semestersByPeriods(anyCollection());
        verify(repo,never()).mappingBySource(anyLong());
        verify(repo,never()).semesterByPeriod(anyString(),anyInt());
    }

    @Test void summerReadFilterResolvesMainSemesterWithoutCreatingAlias() {
        when(source.findById(22)).thenReturn(Optional.of(term(22,"2521")));
        when(repo.semesterByPeriod("2025-2026",2)).thenReturn(Optional.of(semester(502,"2025-2026",2)));
        var expected=new RoundDtos.Page<RoundDtos.RoundSummary>(List.of(),0,20,0);
        when(repo.list(502L,null,new int[]{0,20})).thenReturn(expected);
        assertThat(service.list(null,22L,null,new int[]{0,20})).isEqualTo(expected);
        verify(repo,never()).persist(any());
    }

    AcademicSemesterSource.Semester term(long id,String code) {
        return new AcademicSemesterSource.Semester(id,code,"SYN-HB","raw-is-not-authority",LocalDate.parse("2025-09-01"),LocalDate.parse("2026-01-15"),"verified",true,version);
    }
    ScholarshipSemester semester(long id,String year,int order) {
        var s=new ScholarshipSemester();ReflectionTestUtils.setField(s,"id",id);s.setAcademicYear(year);s.setSemesterOrder(order);
        s.setStartDate(LocalDate.parse("2025-09-01"));s.setEndDate(LocalDate.parse("2026-01-15"));s.setStatus("draft");return s;
    }
    SourceSemesterMapping mapping(long id,long semester,long sourceId) {
        var m=new SourceSemesterMapping();ReflectionTestUtils.setField(m,"id",id);m.setSemesterId(semester);m.setSourceSystem("CTSV");m.setSourceKind("ctsv_hoc_ky_id");m.setSourceSemesterCode(""+sourceId);return m;
    }
    ScholarshipRound stored(String state) {
        var r=new ScholarshipRound();ReflectionTestUtils.setField(r,"id",1001L);r.setSemesterId(501L);r.setSourceMappingId(601L);r.setNumber(1);r.setName("Đợt 1");r.setStatus(state);r.setCreatedBy(301L);
        when(repo.lockRound(1001)).thenReturn(version);when(repo.round(1001)).thenReturn(r);when(repo.semester(501)).thenReturn(semester(501,"2025-2026",1));when(repo.mapping(601)).thenReturn(mapping(601,501,21));return r;
    }
    void failure(Runnable action,int status,String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RoundFailure.class,e -> {assertThat(e.status).isEqualTo(status);assertThat(e.code).isEqualTo(code);});
    }
}
