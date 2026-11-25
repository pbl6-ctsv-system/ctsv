package pbl6.ctsv.mghp.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RoundSequenceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ConfigurationRepository repository = spy(new ConfigurationRepository(jdbc, json, mock(EntityManager.class)));
    private final RoundService service = new RoundService(repository, json, Clock.fixed(Instant.parse("2026-11-25T03:00:00Z"), ZoneOffset.UTC));
    private final Map<String,Object> body = new LinkedHashMap<>();

    RoundSequenceTest() {
        body.putAll(Map.of("id", "20", "semesterId", "10", "status", "draft", "allowRetroactive", true,
                "retroactiveMode", "theo_phap_luat", "midSemesterMode", "khoang_ngay_chuyen_vien_xac_nhan",
                "opensAt", "2026-11-26T00:00:00Z", "submissionDeadline", "2026-12-01T00:00:00Z", "reviewDeadline", "2026-12-05T00:00:00Z"));
        body.put("policyVersions", List.of(Map.of("policyVersionId", "1")));
        doReturn(body).when(repository).roundView(20L, true);
        doReturn(new byte[]{1}).when(repository).fence(anyString(), anyLong());
        doReturn(Map.of("state", "verified", "evidence", "SYNTHETIC", "startDate", "2026-09-01", "endDate", "2027-01-31"))
                .when(repository).semester(10L);
        doReturn(Map.of("status", "locked", "lockedAt", "2026-01-01T00:00:00Z", "ruleVerificationStatus", "verified", "effectiveFrom", "2026-01-01"))
                .when(repository).policyView(1L);
        doNothing().when(repository).cas(anyString(), anyLong(), any(), any());
        doNothing().when(repository).audit(anyLong(), anyString(), anyString(), anyLong(), nullable(String.class));
        doReturn(99L).when(repository).insert(eq("thong_bao"), any());
        doNothing().when(repository).publish(anyLong(), anyLong(), any());
    }

    @Test void unfinishedPublishedRoundBlocksOpenWithoutPublishingOrMutation() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L))).thenReturn(1L);
        assertThatThrownBy(() -> service.transition(20, "open", service.detail(20, true).tag(), null, 7))
                .isInstanceOf(ConfigFailure.class).hasMessage("ROUND_PREVIOUS_NOT_COMPLETED");
        verify(repository, never()).cas(anyString(), anyLong(), any(), any());
        verify(repository, never()).insert(eq("thong_bao"), any());
        verify(repository, never()).audit(anyLong(), anyString(), anyString(), anyLong(), nullable(String.class));
    }

    @Test void canPublishBeforeOpensAtWhenAllPreviousPublishedRoundsAreCompleted() {
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(20L))).thenReturn(0L);
        service.transition(20, "open", service.detail(20, true).tag(), null, 7);
        verify(repository).cas(eq("mghp_dot_xet"), eq(20L), any(), argThat(values -> "open".equals(values.get("trang_thai"))));
        verify(repository).publish(eq(99L), eq(20L), any());
    }

    @Test void repeatOpenKeepsCurrentEtagSemanticsAndDoesNotPublishAgain() {
        body.put("status", "open");
        service.transition(20, "open", service.detail(20, true).tag(), null, 7);
        verifyNoInteractions(jdbc);
        verify(repository, never()).cas(anyString(), anyLong(), any(), any());
        verify(repository, never()).insert(eq("thong_bao"), any());
    }

    @Test void staleEtagIsRejectedBeforeSequenceCheck() {
        assertThatThrownBy(() -> service.transition(20, "open", "\"stale\"", null, 7)).hasMessage("VERSION_CONFLICT");
        verifyNoInteractions(jdbc);
    }

    @Test void completionRemainsGatedOnLaterPhases() {
        body.put("status", "closed");
        assertThatThrownBy(() -> service.transition(20, "complete", service.detail(20, true).tag(), "SYNTHETIC", 7))
                .hasMessage("ROUND_STATE_CONFLICT");
        verify(repository, never()).cas(anyString(), anyLong(), any(), any());
    }
}
