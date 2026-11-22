package pbl6.ctsv.foundation.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pbl6.ctsv.foundation.entity.*;
import pbl6.ctsv.foundation.mapper.FoundationMapper;
import static org.assertj.core.api.Assertions.*;

class FoundationMapperTest {
    @Test void studentProjectionPreservesNullAndStringIdsWithoutSensitiveFields() throws Exception {
        var s = new SinhVien();
        var id = SinhVien.class.getDeclaredField("id"); id.setAccessible(true); id.set(s,9007199254740993L);
        s.setMssv("SYNTHETIC"); s.setHoTen("Sinh viên thử"); s.setSoCccd("DO-NOT-EXPOSE");
        var body = new ObjectMapper().valueToTree(FoundationMapper.studentSummary(s));
        assertThat(body.path("id").isTextual()).isTrue();
        assertThat(body.path("id").asText()).isEqualTo("9007199254740993");
        assertThat(body.path("currentClassId").isNull()).isTrue();
        assertThat(body.has("soCccd")).isFalse();
        // Owner22/11 read delta adds only class/unit labels; keep an exact projection whitelist.
        var fields=new java.util.HashSet<String>();body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id","studentCode","fullName","currentClassId","managementUnitId","classCode","managementUnitName");
        assertThat(body.path("classCode").isNull()).isTrue();
        assertThat(body.path("managementUnitName").isNull()).isTrue();
    }
    @Test void semesterProjectionKeepsVerifiedCalendarSeparateFromRawSource() {
        var h = new HocKy(); h.setNgayDayNguon("raw-source"); h.setNgayBatDau(java.time.LocalDate.of(2026,9,1));
        var body = new ObjectMapper().valueToTree(FoundationMapper.semester(h));
        assertThat(body.path("startDate").asText()).isEqualTo("2026-09-01");
        assertThat(body.path("order").isNull()).isTrue();
        assertThat(body.has("ngayDayNguon")).isFalse();
    }
}
