package pbl6.ctsv.foundation.units;
import org.junit.jupiter.api.Test;
import pbl6.p01.FaultDatabase;
import static org.assertj.core.api.Assertions.assertThat;
class UnitFaultProjectionFT {
    @org.junit.jupiter.api.BeforeAll static void setup()throws Exception{FaultSyncFixture.projection();}
    @Test void consumerFixtureUsesTheExactTargetColumnsAndImmutableAudit()throws Exception {
        assertThat(FaultDatabase.number("SELECT COUNT(*) FROM sys.tables WHERE name IN(N'don_vi',N'he_thong_ngoai',N'dot_dong_bo',N'nguoi_dung',N'nhat_ky_thao_tac',N'chi_tiet_nhat_ky_thao_tac')")).isEqualTo(6);
        assertThat(FaultDatabase.number("SELECT COUNT(*) FROM sys.triggers WHERE name IN(N'tr_nhat_ky_thao_tac_immutable',N'tr_chi_tiet_nhat_ky_thao_tac_immutable')")).isEqualTo(2);
        assertThat(FaultDatabase.number("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.don_vi') AND name=N'dang_hoat_dong_nguon' AND is_nullable=1")).isEqualTo(1);
    }
}
