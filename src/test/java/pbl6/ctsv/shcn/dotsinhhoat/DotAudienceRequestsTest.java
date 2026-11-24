package pbl6.ctsv.shcn.dotsinhhoat;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;
import pbl6.ctsv.shcn.dotsinhhoat.exception.DotFailure;

class DotAudienceRequestsTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final DotRequests requests=new DotRequests(json);
    final String root="""
      {"maHocKy":"0010","tenDot":"Đợt thực hành","khungNgayBatDau":"2026-12-01","khungNgayKetThuc":"2027-03-04","khungTietChoPhep":[{"tietDau":11,"tietCuoi":12}],"hanChotDangKyLich":"2026-11-29T10:00:00Z"}
      """;
    final String group="""
      {"loaiDoiTuong":"KHOA_HOC","maDoiTuong":"2023","hinhThucChoPhep":"OFFLINE","khungNgayBatDau":"2026-12-01","khungNgayKetThuc":"2026-12-07","khungTietChoPhep":null}
      """;
    JsonNode input(String value)throws Exception{var n=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(root);n.set("doiTuongApDung",json.readTree(value));return n;}
    @Test void savesIndependentDatesAndModesWithoutChangingSemesterCode()throws Exception{
        var c=requests.create(input("["+group+","+group.replace("2023","1022113").replace("KHOA_HOC","LOP").replace("OFFLINE","ONLINE")+"]"));
        var out=json.valueToTree(c);assertEquals(2,out.path("doiTuongApDung").size());
        assertEquals("ONLINE",out.at("/doiTuongApDung/1/hinhThucChoPhep").textValue());
        assertEquals("0010",out.path("maHocKy").textValue());
    }
    @Test void rejectsNewDraftWithoutAnyAudienceAndExplicitEmptyArray()throws Exception{
        assertThrows(DotFailure.class,()->requests.create(json.readTree(root)));assertThrows(DotFailure.class,()->requests.create(input("[]")));
    }
    @Test void rejectsDuplicateSelectorInsteadOfSilentlyPickingLast()throws Exception{
        var e=assertThrows(DotFailure.class,()->requests.create(input("["+group+","+group+"]")));assertEquals(400,e.status());
    }
    @Test void rejectsInvalidModeMissingDateAndReversedDates()throws Exception{
        for(String row:new String[]{group.replace("OFFLINE","invalid"),group.replace("OFFLINE","CA_HAI"),group.replace("\"2026-12-07\"","null"),group.replace("2026-12-07","2026-11-30")}){
            assertEquals("VALIDATION_ERROR",assertThrows(DotFailure.class,()->requests.create(input("["+row+"]"))).code());
        }
    }
    @Test void rejectsNullArrayUnknownFieldsAndUnsupportedSelectorType()throws Exception{
        for(String value:new String[]{"null","["+group.replace("KHOA_HOC","REN_LUYEN")+"]","["+group.replace("\"loaiDoiTuong\"","\"unknown\":true,\"loaiDoiTuong\"")+"]"}){
            assertThrows(DotFailure.class,()->requests.create(input(value)));
        }
    }
    @Test void rejectsInheritedDatesForNewAudienceWhileNullPeriodsStillInherit()throws Exception{
        assertThrows(DotFailure.class,()->requests.create(input("["+group.replace("\"2026-12-01\"","null").replace("\"2026-12-07\"","null")+"]")));
        assertNull(requests.create(input("["+group+"]")).doiTuongApDung().getFirst().khungTietChoPhep());
    }
}
