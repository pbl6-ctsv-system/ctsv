package pbl6.ctsv.mghp.configuration;

import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;
import pbl6.ctsv.mghp.configuration.entity.*;
import static org.assertj.core.api.Assertions.*;

class ConfigurationMapperTest {
    @Test void policyProjectionKeepsDecimalStringAndNullableDates() {
        var p=new MghpPhienBanChinhSach();p.setTyLeGiam(new BigDecimal("0.500000000"));p.setNgayBatDauHieuLuc(LocalDate.of(2026,9,1));
        var dto=ConfigurationMapper.policy(p);
        assertThat(dto.get("rate")).isEqualTo("0.500000000");
        assertThat(dto.get("effectiveFrom")).isEqualTo("2026-09-01");
        assertThat(dto).containsEntry("effectiveUntil",null);
        assertThat(dto).doesNotContainKey("phienBanKyThuat");
    }
    @Test void publicRoundProjectionDoesNotExposeInternalLock() {
        var r=new MghpDotXet();r.setThoiDiemKhoaCauHinh(OffsetDateTime.parse("2026-11-01T10:00:00+07:00"));
        assertThat(ConfigurationMapper.round(r,false)).doesNotContainKey("configLockedAt");
        assertThat(ConfigurationMapper.round(r,true)).containsEntry("configLockedAt","2026-11-01T03:00:00Z");
    }
}
