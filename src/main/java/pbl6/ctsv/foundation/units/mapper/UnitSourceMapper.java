package pbl6.ctsv.foundation.units.mapper;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import pbl6.ctsv.foundation.units.dto.UnitChangeDto;
public final class UnitSourceMapper {
    private final ObjectMapper json=new ObjectMapper();
    public record MappedUnit(String sourceId,boolean deleted,String code,String name,String department,
            OffsetDateTime updatedAt,boolean active,String archiveStatus,String unitClassification,String hash) {}
    public MappedUnit map(UnitChangeDto change){
        try {
            boolean deleted="delete".equals(change.operation());
            var data=change.data();
            String canonical=change.sourceId()+"\n"+change.operation()+"\n"+json.writeValueAsString(data);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
            return deleted?new MappedUnit(change.sourceId(),true,null,null,null,null,false,null,null,hash)
                :new MappedUnit(change.sourceId(),false,data.unitCode(),data.unitName(),data.departmentCode(),
                    OffsetDateTime.parse(data.updatedAt()),data.isActive(),data.archiveStatus(),data.unitClassification(),hash);
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException("Cannot map source unit",e);}
    }
}

