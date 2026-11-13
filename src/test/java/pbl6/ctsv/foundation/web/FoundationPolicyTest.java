package pbl6.ctsv.foundation.web;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FoundationPolicyTest {
 @Test void validatesIdsPagingAndIntervals(){
  assertThat(FoundationRules.id("9223372036854775807")).isEqualTo(Long.MAX_VALUE);
  for(String value:new String[]{"0","-1","1.0","9223372036854775808"})
   assertThatThrownBy(()->FoundationRules.id(value)).isInstanceOf(FoundationFailure.class);
  assertThatThrownBy(()->FoundationRules.page("10001","100",20)).isInstanceOf(FoundationFailure.class);
  assertThat(FoundationRules.page("1000","100",20).offset()).isEqualTo(100000);
  assertThatThrownBy(()->FoundationRules.dates(java.time.LocalDate.of(2026,2,1),java.time.LocalDate.of(2026,1,1))).isInstanceOf(FoundationFailure.class);
 }
}
