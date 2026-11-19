package pbl6.ctsv.shcn.phancongkhoa.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit read configuration; absence never grants permissions or invents a staff mapping. */
@ConfigurationProperties("ctsv.shcn.phan-cong-khoa")
public record PhanCongKhoaSettings(Set<String> readRoleCodes,Set<String> prepareRoleCodes,String sourceCode,
        boolean staffMappingVerified,StaffCodeColumn staffCodeColumn,Set<String> employmentStatuses,
        Boolean employmentEndInclusive) {
    public enum StaffCodeColumn { MA_CB, MA_GV }
    public PhanCongKhoaSettings {
        // Owner-approved mapping: HoSoCanBo.MaHS -> staff_code -> can_bo.ma_cb.
        staffCodeColumn=staffCodeColumn==null?StaffCodeColumn.MA_CB:staffCodeColumn;
        readRoleCodes=readRoleCodes==null?Set.of():Set.copyOf(readRoleCodes);
        prepareRoleCodes=prepareRoleCodes==null?Set.of():Set.copyOf(prepareRoleCodes);
        employmentStatuses=employmentStatuses==null?Set.of():Set.copyOf(employmentStatuses);
    }
}
