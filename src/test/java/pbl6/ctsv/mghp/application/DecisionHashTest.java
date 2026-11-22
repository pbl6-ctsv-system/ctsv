package pbl6.ctsv.mghp.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DecisionHashTest {
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 final DecisionReads reads=new DecisionReads(null,null,json);
 @Test void objectKeyOrderDoesNotChangeCanonicalDigest()throws Exception{
  assertThat(reads.hash(json.readTree("{\"metadata\":{\"date\":null,\"number\":\"123\"},\"a\":1}")))
   .isEqualTo(reads.hash(json.readTree("{\"a\":1,\"metadata\":{\"number\":\"123\",\"date\":null}}")));
 }
 @Test void intervalsArePartOfTheDigestAndArrayOrderIsNotSilentlyDiscarded(){
  assertThat(reads.hash(List.of("2026-09-01","2026-10-31"))).isNotEqualTo(reads.hash(List.of("2026-09-01","2027-01-31")));
  assertThat(reads.hash(List.of(1,2))).isNotEqualTo(reads.hash(List.of(2,1)));
 }
}
