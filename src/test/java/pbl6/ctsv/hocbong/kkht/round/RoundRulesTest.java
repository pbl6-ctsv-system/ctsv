package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/** HB-T00-013/016/023/033/034/038/059/060; pure contract rules, no SQL. */
class RoundRulesTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void derivesYearAndSemesterFromCodeNotSourcePkOrGlobalOrder() {
        assertThat(RoundRules.period("2510")).contains(new RoundRules.Period("2025-2026", 1, false));
        assertThat(RoundRules.period("2520")).contains(new RoundRules.Period("2025-2026", 2, false));
        assertThat(RoundRules.period("2521")).contains(new RoundRules.Period("2025-2026", 2, true));
        assertThat(RoundRules.period("9910")).contains(new RoundRules.Period("2099-2100", 1, false));
    }

    @ParameterizedTest @ValueSource(strings={"", "21", "251A", "25100", "2530", " 2510", "2510 "})
    void unsupportedCodesDoNotGuessAMapping(String code) {
        assertThat(RoundRules.period(code)).isEmpty();
    }

    @Test void absentCodeCannotBeMapped() { assertThat(RoundRules.period(null)).isEmpty(); }

    @Test void acceptsTheFullPositiveBigintRangeAsStrings() {
        assertThat(RoundRules.id("id", "1")).isEqualTo(1);
        assertThat(RoundRules.id("id", "9223372036854775807")).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest @ValueSource(strings={"", "0", "01", "-1", "+1", " 1", "1.0", "9223372036854775808"})
    void rejectsNonCanonicalIds(String id) { invalid(() -> RoundRules.id("id", id), "id"); }

    @Test void enforcesPageBoundsWithoutIntegerOverflow() {
        assertThat(RoundRules.page(null,null)).containsExactly(0,20);
        assertThat(RoundRules.page("1000","100")).containsExactly(1000,100);
        invalid(() -> RoundRules.page("1001","100"),"page");
        invalid(() -> RoundRules.page("01","20"),"page");
        invalid(() -> RoundRules.page("2147483647","100"),"page");
        invalid(() -> RoundRules.page("0","0"),"size");
        invalid(() -> RoundRules.page("0","101"),"size");
    }

    @Test void keepsPreconditionFailuresDistinctAndBindsTagToRootAndRepresentation() {
        String first=RoundRules.tag(1,new byte[]{1,2},"before",json);
        assertThat(first).matches("\"[A-Za-z0-9_-]+\"");
        assertThat(RoundRules.tag(2,new byte[]{1,2},"before",json)).isNotEqualTo(first);
        assertThat(RoundRules.tag(1,new byte[]{1,2},"after",json)).isNotEqualTo(first);
        assertThatThrownBy(() -> RoundRules.match(first,null)).isInstanceOfSatisfying(RoundFailure.class,e -> assertThat(e.status).isEqualTo(428));
        invalid(() -> RoundRules.match(first,"W/"+first),"If-Match");
        assertThatThrownBy(() -> RoundRules.match(first,"\"stale\"")).isInstanceOfSatisfying(RoundFailure.class,e -> assertThat(e.status).isEqualTo(412));
        assertThatCode(() -> RoundRules.match(first,first)).doesNotThrowAnyException();
    }

    @Test void trimsSearchWithoutChangingLiteralWildcards() {
        assertThat(RoundRules.queryText("  %[đợt]_  ")).isEqualTo("%[đợt]_");
        assertThat(RoundRules.queryText(" ")).isNull();
        invalid(() -> RoundRules.queryText("a".repeat(129)),"q");
    }

    @Test void queryLimitCountsUnicodeCharactersWhileNamesKeepTheirUtf16Limit() {
        String boundary="😀".repeat(128);
        assertThat(RoundRules.queryText(boundary)).isEqualTo(boundary);
        invalid(() -> RoundRules.queryText(boundary+"😀"),"q");
    }

    private void invalid(Throwing action,String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(RoundFailure.class,e -> {
            assertThat(e.status).isEqualTo(400); assertThat(e.code).isEqualTo("VALIDATION_ERROR"); assertThat(e.field).isEqualTo(field);
        });
    }
    private interface Throwing { void run() throws Exception; }
}
