package pbl6.ctsv.shcn.dotsinhhoat.reference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
class JdbcSemesterAudienceSourceTest {
 @Test void unresolvedPeriodIsNotReportedAsAnEmptyValidSemester(){var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(4L),eq(7L))).thenReturn(true);var e=assertThrows(DotFailure.class,()->new JdbcSemesterAudienceSource(jdbc).load(4,7));assertEquals("SOURCE_MAPPING_INCOMPLETE",e.code());verify(jdbc).queryForObject(anyString(),eq(Boolean.class),eq(4L),eq(7L));verifyNoMoreInteractions(jdbc);}
}
