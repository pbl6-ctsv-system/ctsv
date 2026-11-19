package pbl6.ctsv.shcn.phancongkhoa;

import java.time.Clock;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pbl6.ctsv.shcn.foundation.*;
import pbl6.ctsv.shcn.phancongkhoa.config.PhanCongKhoaSettings;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests.PageQuery;
import static org.assertj.core.api.Assertions.*;

/** Component readiness gate: absent staff mapping must fail before any JDBC source query. */
class PhanCongKhoaReadinessTest {
    @Test void ownerApprovedMappingDefaultsToMaCb(){
        var config=new PhanCongKhoaSettings(Set.of(),Set.of(),null,false,null,Set.of(),null);
        assertThat(config.staffCodeColumn()).isEqualTo(PhanCongKhoaSettings.StaffCodeColumn.MA_CB);
    }
    @Test void alternativeTeacherCodeCannotOverrideOwnerApprovedMapping(){
        var config=new PhanCongKhoaSettings(Set.of("READ"),Set.of("PREPARE"),"SIS",true,
            PhanCongKhoaSettings.StaffCodeColumn.MA_GV,Set.of("ACTIVE"),true);
        var adapter=new JdbcPhanCongKhoaFoundation(new JdbcTemplate(),config,Clock.systemUTC());
        assertThatThrownBy(()->adapter.giangVien(new PhanCongKhoaFoundationPort.Khoa(1,"001","Synthetic",true,true,true),new PageQuery(0,25,0,"")))
            .isInstanceOfSatisfying(PhanCongKhoaFailure.class,e->assertThat(e.status()).isEqualTo(503));
    }
    @Test void unverifiedStaffMappingDoesNotGuessMaCb(){
        var config=new PhanCongKhoaSettings(Set.of("READ"),Set.of("PREPARE"),"SIS",false,
            PhanCongKhoaSettings.StaffCodeColumn.MA_CB,Set.of("ACTIVE"),true);
        var adapter=new JdbcPhanCongKhoaFoundation(new JdbcTemplate(),config,Clock.systemUTC());
        assertThatThrownBy(()->adapter.giangVien(new PhanCongKhoaFoundationPort.Khoa(1,"001","Synthetic",true,true,true),new PageQuery(0,25,0,"")))
            .isInstanceOfSatisfying(PhanCongKhoaFailure.class,e->{assertThat(e.status()).isEqualTo(503);assertThat(e.code()).isEqualTo("DEPENDENCY_UNAVAILABLE");});
    }
}
