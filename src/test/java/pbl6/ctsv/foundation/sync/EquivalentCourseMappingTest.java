package pbl6.ctsv.foundation.sync;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pbl6.ctsv.foundation.entity.DiemKyHoc;
import pbl6.ctsv.foundation.entity.DiemMien;
import pbl6.ctsv.foundation.entity.HocPhan;
import pbl6.ctsv.foundation.entity.KhungChuongTrinh;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EquivalentCourseMappingTest {
    record Mapping(String resource, String table, String column, String wireField, Class<?> entity) {
        @Override public String toString() { return resource; }
    }

    static Stream<Mapping> mappings() {
        return Stream.of(
                new Mapping("curriculum-items", "khung_chuong_trinh", "hoc_phan_duoc_thay_the_id",
                        "replaced_course_source_id", KhungChuongTrinh.class),
                new Mapping("course-results", "diem_ky_hoc", "hoc_phan_tuong_duong_id",
                        "equivalent_course_source_id", DiemKyHoc.class),
                new Mapping("recognized-course-results", "diem_mien", "hoc_phan_tuong_duong_id",
                        "equivalent_course_source_id", DiemMien.class));
    }

    @ParameterizedTest
    @MethodSource("mappings")
    void HB_COMB_10_catalogPermitsNullWhileRetainingTheCourseParent(Mapping mapping) {
        var resource = FoundationResourceCatalog.resource(mapping.resource());
        assertThat(resource.target()).isEqualTo(mapping.table());
        assertThat(resource.fields()).filteredOn(field -> field.target().equals(mapping.column()))
                .singleElement().satisfies(field -> {
                    assertThat(field.json()).isEqualTo(mapping.wireField());
                    assertThat(field.nullable()).as(mapping.column()).isTrue();
                    assertThat(field.parent()).isEqualTo("hoc_phan");
                });
    }

    @ParameterizedTest
    @MethodSource("mappings")
    void HB_COMB_11_entityMapsAnOptionalCourseForeignKey(Mapping mapping) {
        assertThat(Arrays.asList(mapping.entity().getDeclaredFields()))
                .filteredOn(field -> field.isAnnotationPresent(Column.class)
                        && field.getAnnotation(Column.class).name().equals(mapping.column()))
                .singleElement().satisfies(field -> {
                    assertThat(field.getType()).isEqualTo(Long.class);
                    assertThat(field.getAnnotation(Column.class).nullable()).as(mapping.column()).isTrue();
                });
        assertThat(Arrays.asList(mapping.entity().getDeclaredFields()))
                .filteredOn(field -> field.isAnnotationPresent(JoinColumn.class)
                        && field.getAnnotation(JoinColumn.class).name().equals(mapping.column()))
                .singleElement().satisfies(field -> {
                    assertThat(field.getType()).isEqualTo(HocPhan.class);
                    assertThat(field.getAnnotation(ManyToOne.class).optional()).isTrue();
                });
    }

    @ParameterizedTest
    @MethodSource("mappings")
    void HB_COMB_10_ctUpsertAcceptsExplicitNullAndRealSourceId(Mapping mapping) throws Exception {
        var validator = new CtSchema(mapping.resource());
        var data = ResourceSchemaTest.fixture(mapping.resource());
        validator.validate(ResourceSchemaTest.page(data.deepCopy().put(mapping.wireField(), "1202")));
        validator.validate(ResourceSchemaTest.page(data.deepCopy().putNull(mapping.wireField())));

        var missingField = data.deepCopy();
        missingField.remove(mapping.wireField());
        assertThatThrownBy(() -> validator.validate(ResourceSchemaTest.page(missingField)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(ResourceSchemaTest.page(data.deepCopy().put(mapping.wireField(), 1202))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("mappings")
    void HB_COMB_10_legacyListItemAcceptsExplicitNullAndRealSourceId(Mapping mapping) throws Exception {
        ObjectNode root;
        try (var stream = getClass().getResourceAsStream("/contracts/sis-ct26.schema.json")) {
            root = (ObjectNode) ResourceSchemaTest.JSON.readTree(stream);
        }
        root.put("$ref", "#/$defs/" + mapping.resource() + "_item");
        var schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root);
        var item = ResourceSchemaTest.fixture(mapping.resource()).put("source_id", "1401");
        assertThat(schema.validate(item.deepCopy().put(mapping.wireField(), "1202"))).isEmpty();
        assertThat(schema.validate(item.deepCopy().putNull(mapping.wireField()))).isEmpty();

        var missingField = item.deepCopy();
        missingField.remove(mapping.wireField());
        assertThat(schema.validate(missingField)).isNotEmpty();
        assertThat(schema.validate(item.deepCopy().put(mapping.wireField(), 1202))).isNotEmpty();
    }
}
