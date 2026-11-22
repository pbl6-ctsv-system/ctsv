package pbl6.ctsv.foundation.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.repository.ScholarshipScopeRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HB-T024-007/011/039. Real provider grouping/hash with mocked SQL rows; not SQL query/locking evidence. */
class ScholarshipScopeSourceTest {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final AcademicSemesterSource semesters=mock(AcademicSemesterSource.class);
    final ScholarshipScopeRepository provider=new ScholarshipScopeRepository(jdbc,semesters,new ObjectMapper());
    List<List<Object>> rows=new ArrayList<>();
    @BeforeEach @SuppressWarnings("unchecked") void fixture() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(semesters.findById(21)).thenReturn(Optional.of(new AcademicSemesterSource.Semester(21,"2610",null,null,LocalDate.of(2026,9,1),LocalDate.of(2027,1,15),"verified",true,new byte[]{1})));
        when(jdbc.query(anyString(),any(RowMapper.class))).thenAnswer(call->rows);
    }
    @AfterEach void clear() {TransactionSynchronizationManager.clear();}
    @Test void usesFixedMappingsAndExcludesStudentsStartingAfterSelectedSemester() {
        rows.add(row(1,"2510"));rows.add(row(2,"2610"));rows.add(row(3,"2620"));
        var catalog=provider.catalog(21);
        assertThat(catalog.groups()).singleElement().satisfies(group->{assertThat(group.cohortId()).isEqualTo(10);assertThat(group.majorId()).isEqualTo(20);assertThat(group.studentCount()).isEqualTo(2);});
        assertThat(catalog.revision()).matches("[A-Za-z0-9_-]{43}");
    }
    @Test void revisionIncludesFullMembershipMappingPresenceAndStartSemesterRatherThanOnlyCount() {
        rows.add(row(1,"2510"));String first=provider.catalog(21).revision();
        rows.set(0,row(2,"2510"));String member=provider.catalog(21).revision();assertThat(member).isNotEqualTo(first);
        rows.getFirst().set(1,false);assertThat(provider.catalog(21).revision()).isNotEqualTo(member);
        rows.set(0,row(1,"2610"));assertThat(provider.catalog(21).revision()).isNotEqualTo(first);
    }
    @Test void missingMappingOrStartDoesNotDisappearThroughInnerJoinOrBecomeCountZero() {
        rows.add(row(1,"2510"));rows.getFirst().set(4,null);unavailable();
        rows.set(0,row(1,"2510"));rows.getFirst().set(14,null);unavailable();
        rows.set(0,row(1,"unmapped"));unavailable();
        rows.set(0,row(1,"2510"));rows.getFirst().set(13,false);unavailable();
    }
    @Test void nullableLabelsAreValidAndNoGradeTableIsRequired() {
        rows.add(row(1,"2510"));rows.getFirst().set(5,null);rows.getFirst().set(11,null);rows.getFirst().set(12,null);
        assertThat(provider.catalog(21).groups()).singleElement().satisfies(group->assertThat(group.majorName()).isNull());
    }
    @Test void noStudentsIsRealEmptyCatalogAndMissingTransactionIsRejected() {
        assertThat(provider.catalog(21).groups()).isEmpty();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThatThrownBy(()->provider.catalog(21)).isInstanceOf(IllegalStateException.class);
    }
    List<Object> row(long id,String firstCode) {
        return new ArrayList<>(Arrays.asList(id,true,10L,30L,10L,"K25",true,30L,20L,true,20L,"CNTT","Công nghệ thông tin",true,50L,firstCode,true));
    }
    void unavailable() {assertThatThrownBy(()->provider.catalog(21)).isInstanceOfSatisfying(FoundationAccess.Failure.class,e->{assertThat(e.status()).isEqualTo(503);assertThat(e.code()).isEqualTo("SCOPE_SOURCE_UNAVAILABLE");});}
}
