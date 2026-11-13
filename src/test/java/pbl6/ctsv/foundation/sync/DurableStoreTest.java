package pbl6.ctsv.foundation.sync;
import java.util.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;import com.fasterxml.jackson.databind.node.*;import static org.assertj.core.api.Assertions.*;
class DurableStoreTest {
 @Test void validBigintBeyondJavascriptPrecisionIsPreservedAsString(){var p=DurableStoreIT.page(UUID.randomUUID(),15,null,DurableStoreIT.change(9007199254740993L,"DT"));new CtSchema("ethnicities").validate(p);assertThat(CtSchema.number(p.path("items").get(0).path("source_id"))).isEqualTo(9007199254740993L);}
 @ParameterizedTest @ValueSource(strings={"legacy","overflow","duplicate","order","window","full-delete","offset","utf16"})
 void rejectsInvalidWireBeforeDurableMutation(String variant){var p=DurableStoreIT.page(UUID.randomUUID(),15,null,DurableStoreIT.change(2,"DT"),DurableStoreIT.change(10,"OTHER"));
  switch(variant){
   case "legacy"->{p.remove("from_version");p.remove("to_version");}
   case "overflow"->p.put("to_version","9223372036854775808");
   case "duplicate"->((ObjectNode)p.path("items").get(1)).put("source_id","2");
   case "order"->((ObjectNode)p.path("items").get(1)).put("source_id","1");
   case "window"->{p.put("mode","incremental");p.put("from_version","14");for(var row:p.path("items"))((ObjectNode)row).put("change_version","14");}
   case "full-delete"->{((ObjectNode)p.path("items").get(0)).put("operation","delete").putNull("data");}
   case "offset"->p.put("as_of","2026-11-12T07:00:00+07:00");
   case "utf16"->((ObjectNode)p.path("items").get(0).path("data")).put("code","😀".repeat(65));
  }
  assertThatThrownBy(()->new CtSchema("ethnicities").validate(p)).isInstanceOf(IllegalArgumentException.class);
 }
}
