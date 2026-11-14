package pbl6.ctsv.foundation.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FoundationEntityMappingTest {
    @Test void everyBoundResourceHasAnEntityWithLocalIdentityAndSafeRelations() throws Exception {
        var tables = new LinkedHashSet<String>();
        tables.add("don_vi");
        try (var in = getClass().getResourceAsStream("/contracts/foundation-resources.json")) {
            for (var resource : new ObjectMapper().readTree(in)) tables.add(resource.path("target").asText());
        }
        assertThat(tables).hasSize(24);
        for (String table : tables) {
            String name = Arrays.stream(table.split("_")).map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)).reduce("", String::concat);
            String pkg = table.equals("don_vi") ? "pbl6.ctsv.foundation.units.entity." : "pbl6.ctsv.foundation.entity.";
            Class<?> entity = Class.forName(pkg + name);
            assertThat(entity.isAnnotationPresent(Entity.class)).as(table).isTrue();
            assertThat(entity.getAnnotation(Table.class).name()).isEqualTo(table);
            assertThat(entity.getDeclaredField("id").getType()).isEqualTo(Long.class);
            assertThat(entity.getDeclaredField("id").getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
            for (var field : entity.getDeclaredFields()) {
                var relation = field.getAnnotation(ManyToOne.class);
                if (relation != null) {
                    assertThat(relation.fetch()).isEqualTo(FetchType.LAZY);
                    assertThat(relation.cascade()).isEmpty();
                    assertThat(field.getAnnotation(JoinColumn.class).insertable()).isFalse();
                    assertThat(field.getAnnotation(JoinColumn.class).updatable()).isFalse();
                }
            }
        }
    }

    @Test void representativeRelationsUseCtsvForeignKeysRatherThanSisIds() throws Exception {
        Class<?> student = Class.forName("pbl6.ctsv.foundation.entity.SinhVien");
        assertThat(student.getDeclaredField("lopHienTai").getAnnotation(JoinColumn.class).name()).isEqualTo("lop_hien_tai_id");
        Class<?> semester = Class.forName("pbl6.ctsv.foundation.entity.HocKy");
        var version = semester.getDeclaredField("phienBan").getAnnotation(Column.class);
        assertThat(version.insertable()).isFalse();
        assertThat(version.updatable()).isFalse();
        assertThat(semester.getDeclaredField("phienBan").isAnnotationPresent(Version.class)).isFalse();
    }
}
