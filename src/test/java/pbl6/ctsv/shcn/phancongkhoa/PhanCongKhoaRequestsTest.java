package pbl6.ctsv.shcn.phancongkhoa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import static org.assertj.core.api.Assertions.*;

/** Parser boundary tests, no Spring context/DB; these are not evidence of HTTP/security integration. */
class PhanCongKhoaRequestsTest {
    final ObjectMapper json=new ObjectMapper();
    final PhanCongKhoaRequests parser=new PhanCongKhoaRequests(json);
    ObjectNode body(){return json.createObjectNode().put("maKhoa","001").put("namHoc","2026-2027").put("phienBanDuKien",1)
        .putNull("maDanhSachTruoc").put("hieuLucTu","2026-09-01T00:00:00Z").putNull("hieuLucDen").putNull("lyDoDieuChinh");}
    @ParameterizedTest @ValueSource(strings={"0","01","+1"," 1","1 ","1.0","-1","9223372036854775808","10000000000000000000"})
    void bigintIdMustBePositiveCanonicalAndInRange(String value){assertThatThrownBy(()->PhanCongKhoaRequests.id(value,"id")).isInstanceOf(PhanCongKhoaFailure.class);}
    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings={"  ","Kế thừa năm trước"})
    void inheritanceReasonIsOptionalButSourceIdIsPreserved(String reason){
        var request=body().put("maDanhSachTruoc","1");
        if(reason==null)request.putNull("lyDoDieuChinh");else request.put("lyDoDieuChinh",reason);
        var parsed=parser.tao(request);
        assertThat(parsed.maDanhSachTruoc()).isEqualTo(1L);
        assertThat(parsed.lyDoDieuChinh()).isEqualTo(reason);
    }
    @Test void maximumIdLeadingCodeZeroAndOffsetsAreLossless(){
        assertThat(PhanCongKhoaRequests.id("9223372036854775807","id")).isEqualTo(Long.MAX_VALUE);
        var parsed=parser.tao(body().put("hieuLucTu","2026-09-01T07:00:00.1234567+07:00"));
        assertThat(parsed.maKhoa()).isEqualTo("001");assertThat(parsed.hieuLucTu().toInstant().toString()).isEqualTo("2026-09-01T00:00:00.123456700Z");
        assertThat(parsed.hieuLucDen()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"2026-02-30T00:00:00Z","2026-01-01T00:00:00.12345678Z","2026-01-01T00:00:00","2026-01-01T00:00:00+14:01","0001-01-01T00:00:00+07:00","9999-12-31T23:59:59-07:00","0000-01-01T00:00:00Z"})
    void invalidSqlDatetimeOrMissingOffsetIsRejected(String value){assertThatThrownBy(()->parser.tao(body().put("hieuLucTu",value))).isInstanceOf(PhanCongKhoaFailure.class);}
    @Test void utf16LengthAndSurrogateValidationDoNotTruncate(){
        parser.tao(body().put("lyDoDieuChinh","😀".repeat(2000)));
        assertThatThrownBy(()->parser.tao(body().put("lyDoDieuChinh","😀".repeat(2001)))).isInstanceOf(PhanCongKhoaFailure.class);
        assertThatThrownBy(()->parser.tao(body().put("lyDoDieuChinh","\uD83D"))).isInstanceOf(PhanCongKhoaFailure.class);
        assertThatThrownBy(()->PhanCongKhoaRequests.requiredCode("\u00a0001","maKhoa")).isInstanceOf(PhanCongKhoaFailure.class);
    }
    @Test void strictUtf8AndDuplicateKeysApplyOnlyToShcnParser()throws Exception{
        var request=new MockHttpServletRequest();request.setContentType("application/json");request.setContent(new byte[]{(byte)0xC3,(byte)0x28});
        assertThatThrownBy(()->parser.body(request)).isInstanceOf(PhanCongKhoaFailure.class);
        request.setContent("{\"x\":1,\"x\":2}".getBytes(StandardCharsets.UTF_8));assertThatThrownBy(()->parser.body(request)).isInstanceOf(PhanCongKhoaFailure.class);
        assertThat(json.readTree("{\"x\":1,\"x\":2}").path("x").asInt()).isEqualTo(2);
    }
    @Test void rowversionIsCanonicalAndDefensivelyCopied(){
        var value=json.createObjectNode().put("expectedVersion","AAAAAAAAAAA=").put("hieuLucTu","2026-09-01T00:00:00Z").putNull("hieuLucDen").putNull("lyDoDieuChinh");value.putArray("cacPhanCong");
        var parsed=parser.luu(value);byte[] changed=parsed.expectedVersion();changed[0]=42;assertThat(parsed.expectedVersion()[0]).isZero();
        for(String invalid:List.of("AAAAAAAAAAA","AAAAAAAAAAB=","AAAAAAAAAAAA","AAAA-AAAAAA=",""))assertThatThrownBy(()->parser.luu(value.deepCopy().put("expectedVersion",invalid))).isInstanceOf(PhanCongKhoaFailure.class);
    }
    @Test void offsetUses64BitMultiplicationAndLiteralSearchIsPreserved(){
        assertThatThrownBy(()->PhanCongKhoaRequests.page("2147483647","100",null)).isInstanceOf(PhanCongKhoaFailure.class);
        var page=PhanCongKhoaRequests.page("1000","100","%_' OR");assertThat(page.offset()).isEqualTo(100000);assertThat(page.q()).isEqualTo("%_' OR");
    }
}
