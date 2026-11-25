package pbl6.ctsv.shcn.dotsinhhoat;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pbl6.ctsv.shcn.dotsinhhoat.validation.*;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
class MauRequestsTest {
 final ObjectMapper json=new ObjectMapper();final MauRequests parser=new MauRequests();
 ObjectNode input(){var n=json.createObjectNode().put("expectedVersion","AAAAAAAAAAE=").put("tenMau","Biên bản SHCN");ArrayNode a=n.putArray("muc");for(var m:TemplateDictionary.defaults()){var v=(ObjectNode)json.valueToTree(m);v.remove("laMucHeThong");a.add(v);}return n;}
 ObjectNode field(ObjectNode n){return (ObjectNode)n.withArray("muc").get(1).withArray("cauHinhTruong").get(0);}
 @Test void acceptsConfigurationAndComputesSystemFlags(){var result=parser.parse(input());assertEquals(5,result.muc().size());assertTrue(result.muc().get(3).laMucHeThong());assertFalse(result.muc().get(3).batBuoc());assertFalse(result.muc().get(1).laMucHeThong());}
 @Test void preservesStableKeysAndOptionalRecommendationsAfterReorder(){var n=input();var a=n.withArray("muc");var moved=a.remove(3);a.insert(1,moved);for(int i=0;i<a.size();i++)((ObjectNode)a.get(i)).put("thuTuHienThi",i+1);assertEquals("KIEN_NGHI_SV",parser.parse(n).muc().get(1).maMuc());}
 @Test void cannotDeleteAttendanceOrEitherRecommendation(){for(int index:new int[]{0,3,4}){var n=input();n.withArray("muc").remove(index);for(int i=0;i<n.withArray("muc").size();i++)((ObjectNode)n.withArray("muc").get(i)).put("thuTuHienThi",i+1);assertEquals("SYSTEM_SECTION_IMMUTABLE",assertThrows(DotFailure.class,()->parser.parse(n)).code());}}
 @Test void cannotRenameOrMakeRecommendationRequired(){for(String key:new String[]{"tieuDe","batBuoc"}){var n=input();var section=(ObjectNode)n.withArray("muc").get(3);if(key.equals("tieuDe"))section.put(key,"Khác");else section.put(key,true);assertEquals("SYSTEM_SECTION_IMMUTABLE",assertThrows(DotFailure.class,()->parser.parse(n)).code());}}
 @Test void rejectsClientSystemFlagAndUnknownProperties(){for(String where:new String[]{"root","section","field"}){var n=input();if(where.equals("root"))n.put("dotId","7");else if(where.equals("section"))((ObjectNode)n.withArray("muc").get(1)).put("laMucHeThong",true);else field(n).put("sql","SELECT");assertEquals("VALIDATION_ERROR",assertThrows(DotFailure.class,()->parser.parse(n)).code());}}
 @Test void rejectsUnlistedSourceAndMismatchedTypeAtFieldPath(){var n=input();field(n).put("nguonDien","HE_THONG").put("maNguonHeThong","sinh_vien.so_cccd");var e=assertThrows(DotFailure.class,()->parser.parse(n));assertEquals("muc[1].cauHinhTruong[0].maNguonHeThong",e.errors().getFirst().field());field(n).put("maNguonHeThong","NGAY_HOP");assertThrows(DotFailure.class,()->parser.parse(n));field(n).put("kieuDuLieu","NGAY");assertDoesNotThrow(()->parser.parse(n));}
 @Test void rejectsManualSourceBinding(){var n=input();field(n).put("maNguonHeThong","DOT_TEN");assertThrows(DotFailure.class,()->parser.parse(n));}
 @Test void rejectsEmptyFieldsDuplicatesAndInvalidOrder(){for(int kind=0;kind<3;kind++){var n=input();var section=(ObjectNode)n.withArray("muc").get(1);if(kind==0)section.putArray("cauHinhTruong");if(kind==1)section.withArray("cauHinhTruong").add(field(n).deepCopy());if(kind==2)section.put("thuTuHienThi",1);assertThrows(DotFailure.class,()->parser.parse(n));}}
 @Test void repeatedFieldKeysAcrossDifferentSectionsAreAllowed(){assertDoesNotThrow(()->parser.parse(input()));}
 @Test void requiredSectionMustHaveRequiredField(){var n=input();field(n).put("batBuoc",false);assertThrows(DotFailure.class,()->parser.parse(n));}
 @Test void rejectsInvalidTokenWhitespaceAndUtf16Length(){for(String token:new String[]{"","AA==","AAAAAAAAAAE","!!!!!!!!!!!!"}){var n=input();n.put("expectedVersion",token);assertThrows(DotFailure.class,()->parser.parse(n));}var n=input();n.put("tenMau"," ");assertThrows(DotFailure.class,()->parser.parse(n));n.put("tenMau","😀".repeat(101));assertThrows(DotFailure.class,()->parser.parse(n));}
 @Test void emptyArrayAndNullAreNotAValidTemplate(){for(JsonNode a:new JsonNode[]{json.createArrayNode(),NullNode.instance}){var n=input();n.set("muc",a);assertThrows(DotFailure.class,()->parser.parse(n));}}
}
