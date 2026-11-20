package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.AcademicSemesterSource.Semester;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipSemester;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HB-T024-001–005: real HB assessment, mocked public source/repository; not SQL evidence. */
class SemesterSourceServiceTest {
    private final RoundRepository repo = mock(RoundRepository.class);
    private final AcademicSemesterSource source = mock(AcademicSemesterSource.class);
    private final LocalDate today = LocalDate.of(2026, 11, 20);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), ZoneOffset.UTC);
    private final RoundService service = new RoundService(repo, source, new ObjectMapper(), clock);

    @AfterEach void readsNeverCreateBusinessFacts() {
        verify(repo, never()).persist(any());
        verify(repo, never()).persistAudit(any());
        verify(repo, never()).serializeWrites();
        verify(repo, never()).changeRound(anyLong(), any(), anyInt(), anyString());
    }

    @Test void nearestUsesVerifiedStartDateRatherThanHighestCodeOrId() {
        candidates(0, List.of(term(2, "2610", "2026-09-01"), term(900, "9910", "2025-09-01")), 2);
        var result = service.defaultSemester();
        assertThat(result.semester()).isNotNull();
        assertThat(result.semester().sourceSemesterId()).isEqualTo("2");
        assertThat(result.semester().academicYear()).isEqualTo("2026-2027");
        assertThat(result.reason()).isNull();
    }

    @Test void eligibleCandidateBeyondFirstHundredRowsIsNotLost() {
        candidates(0, IntStream.rangeClosed(1,100).mapToObj(i -> term(i,"2621","2026-11-19")).toList(), 101);
        candidates(1, List.of(term(101,"2610","2026-09-01")), 101);
        assertThat(service.defaultSemester().semester()).isNotNull()
            .extracting(RoundDtos.SourceSemesterDto::sourceSemesterId).isEqualTo("101");
    }

    @Test void examinesEqualDateCandidatesAcrossPageBoundaryBeforeCanonicalTieBreak() {
        candidates(0, IntStream.rangeClosed(1,100).mapToObj(i -> term(i,"2610","2026-09-01")).toList(), 101);
        candidates(1, List.of(term(101,"2620","2026-09-01")), 101);
        assertThat(service.defaultSemester().semester()).isNotNull()
            .extracting(RoundDtos.SourceSemesterDto::sourceSemesterId).isEqualTo("101");
    }

    @Test void equalDatesUseCanonicalYearThenSemesterAndNumericAliasId() {
        candidates(0, List.of(term(20,"2510","2026-09-01"), term(30,"2610","2026-09-01"),
            term(2,"2620","2026-09-01"), term(10,"2620","2026-09-01")), 4);
        assertThat(service.defaultSemester().semester()).isNotNull()
            .extracting(RoundDtos.SourceSemesterDto::sourceSemesterId).isEqualTo("2");
    }

    @Test void skipsAbsentUnverifiedUnknownCodeAndInvalidDates() {
        var absent = new Semester(1,"2610",null,null,today,today.plusMonths(1),"verified",false,new byte[8]);
        var unverified = new Semester(2,"2610",null,null,today,today.plusMonths(1),null,true,new byte[8]);
        var missingEnd = new Semester(3,"2610",null,null,today,null,"verified",true,new byte[8]);
        var reversed = new Semester(4,"2610",null,null,today,today.minusDays(1),"verified",true,new byte[8]);
        candidates(0,List.of(absent,unverified,missingEnd,reversed,term(5,"unknown","2026-11-20"),
            term(6,"2610","2026-09-01")),6);
        assertThat(service.defaultSemester().semester()).isNotNull()
            .extracting(RoundDtos.SourceSemesterDto::sourceSemesterId).isEqualTo("6");
    }

    @Test void skipsCandidateWhosePersistedPeriodConflictsWithSourceDates() {
        var persisted = new ScholarshipSemester();
        ReflectionTestUtils.setField(persisted,"id",501L);
        persisted.setAcademicYear("2026-2027");persisted.setSemesterOrder(1);
        persisted.setStartDate(LocalDate.of(2026,8,1));persisted.setEndDate(today.plusMonths(1));
        when(repo.semestersBySources(anyCollection())).thenReturn(Map.of(1L,persisted));
        candidates(0,List.of(term(1,"2610","2026-11-20"),term(2,"2520","2026-01-01")),2);
        assertThat(service.defaultSemester().semester()).isNotNull()
            .extracting(RoundDtos.SourceSemesterDto::sourceSemesterId).isEqualTo("2");
    }

    @Test void includesTodayUsingVietnamDateEvenBeforeUtcMidnight() {
        var vietnamMidnight = new RoundService(repo,source,new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-11-19T17:00:00Z"),ZoneOffset.UTC));
        candidates(0,List.of(term(7,"2610","2026-11-20")),1);
        assertThat(vietnamMidnight.defaultSemester().semester()).isNotNull()
            .extracting(RoundDtos.SourceSemesterDto::sourceSemesterId).isEqualTo("7");
    }

    @Test void clockBeforeVietnamMidnightUsesPreviousDate() {
        when(source.findStartedPage(LocalDate.of(2026,11,19),0,100))
            .thenReturn(new AcademicSemesterSource.Page(List.of(),0,100,0));
        var beforeMidnight = new RoundService(repo,source,new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-11-19T16:59:59Z"),ZoneOffset.UTC));
        var result = beforeMidnight.defaultSemester();
        assertThat(result.semester()).isNull();
        assertThat(result.reason()).isEqualTo("NO_ELIGIBLE_SEMESTER");
        verify(source).findStartedPage(LocalDate.of(2026,11,19),0,100);
    }

    @Test void noDatedCandidatesReturnsAnExplicitEmptyDefault() {
        candidates(0,List.of(),0);
        var result = service.defaultSemester();
        assertThat(result.semester()).isNull();
        assertThat(result.reason()).isEqualTo("NO_ELIGIBLE_SEMESTER");
    }

    @Test void noSelectableCandidateAlsoReturnsAnExplicitEmptyDefault() {
        candidates(0,List.of(term(1,"2621","2026-09-01")),1);
        assertThat(service.defaultSemester().reason()).isEqualTo("NO_ELIGIBLE_SEMESTER");
    }

    @Test void dependencyFailureIsNotReportedAsAnEmptyDefault() {
        when(source.findStartedPage(today,0,100)).thenThrow(new DataAccessResourceFailureException("synthetic outage"));
        assertThatThrownBy(service::defaultSemester).isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test void directReadKeepsSummerExplanationAndNullableNameWithoutWritingAlias() {
        var summer = new Semester(90,"2621",null,null,today,today.plusMonths(1),"verified",true,new byte[8]);
        when(source.findById(90)).thenReturn(Optional.of(summer));
        var result = service.sourceById(90);
        assertThat(result.sourceSemesterId()).isEqualTo("90");
        assertThat(result.name()).isNull();
        assertThat(result.academicYear()).isEqualTo("2026-2027");
        assertThat(result.semesterOrder()).isEqualTo(2);
        assertThat(result.selectable()).isFalse();
        assertThat(result.unavailableReason()).isEqualTo("SUMMER_MERGED_INTO_SEMESTER_2");
    }

    @Test void directReadOfFutureRegularSemesterRemainsSelectable() {
        when(source.findById(91)).thenReturn(Optional.of(term(91,"2710","2027-09-01")));
        assertThat(service.sourceById(91).selectable()).isTrue();
    }

    @Test void missingSourceIs404RatherThanEmptyDefault() {
        assertThatThrownBy(() -> service.sourceById(999)).isInstanceOfSatisfying(RoundFailure.class, e -> {
            assertThat(e.status).isEqualTo(404);assertThat(e.code).isEqualTo("NOT_FOUND");
        });
    }

    private void candidates(int page,List<Semester> items,long total) {
        when(source.findStartedPage(today,page,100)).thenReturn(new AcademicSemesterSource.Page(items,page,100,total));
    }
    private Semester term(long id,String code,String start) {
        var date=LocalDate.parse(start);
        return new Semester(id,code,"SYN-HB-T025","raw-year-ignored",date,date.plusMonths(4),"verified",true,new byte[8]);
    }
}
