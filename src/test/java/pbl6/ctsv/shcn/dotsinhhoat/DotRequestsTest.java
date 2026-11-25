package pbl6.ctsv.shcn.dotsinhhoat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;
import static org.assertj.core.api.Assertions.*;

class DotRequestsTest {
    final ObjectMapper json=new ObjectMapper();
    final DotRequests parser=new DotRequests(json);
    ObjectNode body()throws Exception{return (ObjectNode)json.readTree("""
      {"maHocKy":"0010","tenDot":"Sinh hoạt đầu kỳ","khungNgayBatDau":"2026-12-01","khungNgayKetThuc":"2026-12-07",
       "khungTietChoPhep":[{"tietDau":5,"tietCuoi":6},{"tietDau":11,"tietCuoi":12}],"hanChotDangKyLich":"2026-12-02T17:00:00+07:00","doiTuongApDung":[{"loaiDoiTuong":"KHOA_HOC","maDoiTuong":"2023","hinhThucChoPhep":"OFFLINE","khungNgayBatDau":"2026-12-01","khungNgayKetThuc":"2026-12-07","khungTietChoPhep":null}]}
      """);}
    @Test void keepsCodeIntervalsAndAllowsDeadlineAfterStart()throws Exception{
        var value=parser.create(body());assertThat(value.maHocKy()).isEqualTo("0010");assertThat(value.khungTietChoPhep()).hasSize(2);
        assertThat(value.hanChotDangKyLich().toInstant().toString()).isEqualTo("2026-12-02T10:00:00Z");
    }
    @Test void equalDatesAndPastDatesAreValid()throws Exception{
        parser.create(body().put("khungNgayBatDau","2000-01-01").put("khungNgayKetThuc","2000-01-01"));
    }
    @ParameterizedTest @ValueSource(strings={"ma_nguoi_tao","trangThai","namHoc","maKhoa","requestId","id"})
    void rejectsClientMetadata(String field)throws Exception{var value=body().put(field,"injected");assertThatThrownBy(()->parser.create(value)).isInstanceOf(DotFailure.class);}
    @ParameterizedTest @ValueSource(strings={"maHocKy","tenDot","khungNgayBatDau","khungNgayKetThuc","khungTietChoPhep","hanChotDangKyLich"})
    void requiresEveryConfigurationField(String field)throws Exception{var value=body();value.remove(field);assertThatThrownBy(()->parser.create(value)).isInstanceOf(DotFailure.class);}
    @ParameterizedTest @ValueSource(strings={"2026-02-30","2026-2-01","0000-01-01","2026-12-08"})
    void rejectsInvalidOrReversedDates(String date)throws Exception{var value=body().put("khungNgayBatDau",date);assertThatThrownBy(()->parser.create(value)).isInstanceOf(DotFailure.class);}
    @Test void typedPatchRequiresTokenAndAllSixFields()throws Exception{
        var value=body().put("expectedVersion","AAAAAAAAAAE=");assertThat(parser.update(value).expectedVersion()).hasSize(8);
        value.remove("tenDot");assertThatThrownBy(()->parser.update(value)).isInstanceOf(DotFailure.class);
    }
    @ParameterizedTest @ValueSource(strings={"AAAAAAAAAAE","AAAAAAAAAAB=","","AAAA-AAAAAA="})
    void rejectsNonCanonicalTokens(String token)throws Exception{var value=body().put("expectedVersion",token);assertThatThrownBy(()->parser.update(value)).isInstanceOf(DotFailure.class);}
    @Test void validatesIntervalShapeAndDoesNotInventUpperPeriod12()throws Exception{
        var value=body();value.withArray("khungTietChoPhep").removeAll().addObject().put("tietDau",13).put("tietCuoi",15);parser.create(value);
        value.withArray("khungTietChoPhep").get(0).deepCopy();((ObjectNode)value.withArray("khungTietChoPhep").get(0)).put("tietDau",16);
        assertThatThrownBy(()->parser.create(value)).isInstanceOf(DotFailure.class);
    }
    @Test void unicodeLengthAndWhitespaceAreNotSilentlyRepaired()throws Exception{
        parser.create(body().put("tenDot","😀".repeat(100)));
        for(String name:new String[]{"😀".repeat(101)," "," đầu","cuối "}){var value=body().put("tenDot",name);assertThatThrownBy(()->parser.create(value)).isInstanceOf(DotFailure.class);}
    }
    @Test void paginationUsesLongAndLiteralFilter() {
        assertThat(DotRequests.query("0","25","id:desc","%_'",null,null,null).q()).isEqualTo("%_'");
        assertThatThrownBy(()->DotRequests.query("2147483647","100",null,null,null,null,null)).isInstanceOf(DotFailure.class);
        assertThatThrownBy(()->DotRequests.query("0","25","tenDot:asc",null,null,null,null)).isInstanceOf(DotFailure.class);
    }
    @Test void bodyIsStrictUtf8JsonAndHasByteLimit()throws Exception{
        var r=new MockHttpServletRequest();r.setContentType("application/json");r.setContent(new byte[]{(byte)0xc3,(byte)0x28});
        assertThatThrownBy(()->parser.body(r)).isInstanceOf(DotFailure.class);
        r.setContent("{\"x\":1,\"x\":2}".getBytes(java.nio.charset.StandardCharsets.UTF_8));assertThatThrownBy(()->parser.body(r)).isInstanceOf(DotFailure.class);
        r.setContent(new byte[1048577]);assertThatThrownBy(()->parser.body(r)).isInstanceOf(DotFailure.class);
    }
}
