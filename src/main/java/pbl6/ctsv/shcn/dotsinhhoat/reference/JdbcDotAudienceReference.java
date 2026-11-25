package pbl6.ctsv.shcn.dotsinhhoat.reference;
import org.springframework.jdbc.core.JdbcTemplate;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.*;
final class JdbcDotAudienceReference {
 private final JdbcTemplate jdbc;
 JdbcDotAudienceReference(JdbcTemplate jdbc){this.jdbc=jdbc;}
 NhomLopDTO groups(long source,long semester){return new JdbcSemesterAudienceSource(jdbc).load(source,semester).groups();}
}
