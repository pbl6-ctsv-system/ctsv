package pbl6.ctsv.hocbong.kkht.round.entity;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.PersistentClass;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScholarshipPersistenceMappingTest {
    private static final String PACKAGE = "pbl6.ctsv.hocbong.kkht.round.entity.";

    @Test
    void mapsCanonicalPeriodWithLocalIdentityAndSmallintOrder() {
        withMapping("ScholarshipSemester", "hb_hoc_ky", (metadata, entity) -> {
            assertColumn(metadata, entity, "academicYear", "nam_hoc", "nvarchar(9)", false);
            assertColumn(metadata, entity, "semesterOrder", "thu_tu_ky", "smallint", false);
            assertColumn(metadata, entity, "startDate", "ngay_bat_dau", "date", false);
            assertColumn(metadata, entity, "endDate", "ngay_ket_thuc", "date", false);
            assertColumn(metadata, entity, "status", "trang_thai", "nvarchar(32)", false);
        });
    }

    @Test
    void mapsFullSourceContextAndLocalConfirmation() {
        withMapping("SourceSemesterMapping", "hb_anh_xa_ky_nguon", (metadata, entity) -> {
            assertColumn(metadata, entity, "sourceSystem", "he_thong_nguon", "nvarchar(128)", false);
            assertColumn(metadata, entity, "sourceKind", "loai_ma", "nvarchar(128)", false);
            assertColumn(metadata, entity, "sourceSemesterCode", "ma_ky_nguon", "nvarchar(128)", false);
            assertColumn(metadata, entity, "sourceRoundCode", "ma_dot_nguon", "nvarchar(128)", true);
            assertColumn(metadata, entity, "semesterId", "hoc_ky_id", "bigint", false);
            assertColumn(metadata, entity, "confirmedBy", "xac_nhan_boi", "bigint", false);
            assertColumn(metadata, entity, "confirmedAt", "xac_nhan_luc", "datetimeoffset(7)", false);
        });
    }

    @Test
    void mapsDraftColumnsAndLeavesRowversionOwnedBySqlServer() {
        withMapping("ScholarshipRound", "kkht_dot_xet", (metadata, entity) -> {
            assertColumn(metadata, entity, "semesterId", "hoc_ky_id", "bigint", false);
            assertColumn(metadata, entity, "sourceMappingId", "anh_xa_ky_nguon_id", "bigint", false);
            assertColumn(metadata, entity, "number", "so_dot", "int", false);
            assertColumn(metadata, entity, "name", "ten_dot", "nvarchar(512)", false);
            assertColumn(metadata, entity, "opensAt", "mo_luc", "datetimeoffset(7)", true);
            assertColumn(metadata, entity, "closedAt", "chot_luc", "datetimeoffset(7)", true);
            assertColumn(metadata, entity, "tuitionSnapshotAt", "moc_chot_hoc_phi", "datetimeoffset(7)", true);
            assertColumn(metadata, entity, "ruleVersionId", "quy_tac_id", "bigint", true);
            assertColumn(metadata, entity, "status", "trang_thai", "nvarchar(32)", false);
            assertColumn(metadata, entity, "createdBy", "nguoi_tao_id", "bigint", false);
            assertColumn(metadata, entity, "version", "phien_ban_ky_thuat", "rowversion", false);
            assertThat(entity.getProperty("version").isInsertable()).isFalse();
            assertThat(entity.getProperty("version").isUpdateable()).isFalse();
            assertThat(entity.isVersioned()).isFalse();
        });
    }

    @Test
    void mapsAuditActorAndRoleWithUnicodeSnapshotsAndNativeCorrelation() {
        withMapping("ScholarshipAudit", "hb_nhat_ky", (metadata, entity) -> {
            assertColumn(metadata, entity, "userId", "nguoi_dung_id", "bigint", false);
            assertColumn(metadata, entity, "roleId", "vai_tro_id", "bigint", false);
            assertColumn(metadata, entity, "objectType", "loai_doi_tuong", "nvarchar(128)", false);
            assertColumn(metadata, entity, "objectId", "doi_tuong_id", "bigint", false);
            assertColumn(metadata, entity, "scope", "pham_vi", "nvarchar(128)", false);
            assertColumn(metadata, entity, "action", "hanh_dong", "nvarchar(128)", false);
            assertColumn(metadata, entity, "beforeData", "du_lieu_truoc", "nvarchar(max)", true);
            assertColumn(metadata, entity, "afterData", "du_lieu_sau", "nvarchar(max)", false);
            assertColumn(metadata, entity, "reason", "ly_do", "nvarchar(max)", true);
            assertColumn(metadata, entity, "occurredAt", "thoi_diem", "datetimeoffset(7)", false);
            assertColumn(metadata, entity, "correlationId", "correlation_id", "uniqueidentifier", false);
        });
    }

    private static void withMapping(String className, String tableName, MappingAssertions assertions) {
        Class<?> type;
        try {
            type = Class.forName(PACKAGE + className);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing T01 mapping for dbo." + tableName, e);
        }

        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.SQLServerDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(type).buildMetadata();
            var entity = metadata.getEntityBinding(type.getName());
            assertThat(entity.getTable().getName()).isEqualTo(tableName);
            assertThat(entity.getTable().getSchema()).isEqualTo("dbo");
            assertColumn(metadata, entity, "id", "id", "bigint", false);
            assertThat(entity.getIdentifier().getColumns().getFirst().isIdentity()).isTrue();
            assertions.verify(metadata, entity);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private static void assertColumn(Metadata metadata, PersistentClass entity, String property,
                                     String name, String sqlType, boolean nullable) {
        var columns = entity.getProperty(property).getColumns();
        assertThat(columns).as(property).hasSize(1);
        var column = columns.getFirst();
        assertThat(column.getName()).as(property).isEqualTo(name);
        assertThat(column.getSqlType(metadata)).as(name).isEqualTo(sqlType);
        assertThat(column.isNullable()).as(name).isEqualTo(nullable);
    }

    @FunctionalInterface
    private interface MappingAssertions {
        void verify(Metadata metadata, PersistentClass entity);
    }
}
