package pbl6.ctsv.mghp.configuration;
import java.time.Instant;import org.junit.jupiter.api.*;import static org.assertj.core.api.Assertions.*;
class RoundRulesTest {
 @Test void DOT_N04_inclusiveServerTimeWindow(){var a=Instant.parse("2026-11-01T00:00:00Z");var b=a.plusSeconds(3600);assertThat(RoundRules.receives("open",a,b,a.minusMillis(1))).isFalse();assertThat(RoundRules.receives("open",a,b,a)).isTrue();assertThat(RoundRules.receives("open",a,b,b)).isTrue();assertThat(RoundRules.receives("open",a,b,b.plusMillis(1))).isFalse();assertThat(RoundRules.receives("closed",a,b,a)).isFalse();}
 @Test void paginationAndOffsetBounds(){assertThat(ConfigRules.page("0","20")).containsExactly(0,20);assertThatThrownBy(()->ConfigRules.page("100000","100")).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->ConfigRules.page("2147483648","100")).hasMessage("VALIDATION_ERROR");}
}
