package pbl6.ctsv.foundation.units;
import org.junit.jupiter.api.Test;
import pbl6.ctsv.foundation.units.dto.*;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;
import static org.assertj.core.api.Assertions.*;
class UnitSourceMapperTest {
    private final UnitSourceMapper mapper=new UnitSourceMapper();
    UnitChangeDto row(String archive,String version){return new UnitChangeDto("9007199254740993","upsert",version,
            new UnitDataDto("DV",null,null,"2026-11-11T00:00:00Z",true,archive,"raw"));}
    @Test void sa011_013_sae03_mappingPreservesRawNullIdentityAndStableHash() {
        var m=mapper.map(row(null,"123"));assertThat(m).isNotNull();
        assertThat(m.sourceId()).isEqualTo("9007199254740993");assertThat(m.archiveStatus()).isNull();
        assertThat(m.unitClassification()).isEqualTo("raw");assertThat(m.active()).isTrue();
        assertThat(mapper.map(row(null,"124")).hash()).isEqualTo(m.hash());
        assertThat(mapper.map(row("changed","124")).hash()).isNotEqualTo(m.hash());
    }
    @Test void sa020_deleteDoesNotInventSourceData() {
        var m=mapper.map(new UnitChangeDto("1","delete","123",null));assertThat(m).isNotNull();
        assertThat(m.deleted()).isTrue();assertThat(m.name()).isNull();assertThat(m.code()).isNull();
    }
}

