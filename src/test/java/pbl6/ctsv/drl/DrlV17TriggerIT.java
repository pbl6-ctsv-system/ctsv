package pbl6.ctsv.drl;

import com.microsoft.sqlserver.jdbc.SQLServerException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.drl.config.DrlPhysicalNamingStrategy;
import pbl6.ctsv.drl.entity.DrlEntity;
import pbl6.ctsv.drl.entity.RlBieuMau;
import pbl6.ctsv.drl.entity.RlBieuMauCt;
import pbl6.ctsv.drl.entity.RlDot;
import pbl6.ctsv.drl.entity.RlKhoaDot;
import pbl6.ctsv.drl.entity.RlLopDot;
import pbl6.ctsv.drl.entity.RlNhomTieuChi;
import pbl6.ctsv.drl.entity.RlPhieu;
import pbl6.ctsv.drl.entity.RlPhieuDiemCt;
import pbl6.ctsv.drl.repository.DrlRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrlV17TriggerIT {
    private static final OffsetDateTime TIME = OffsetDateTime.parse("2099-05-20T08:00:00+07:00");

    private LocalContainerEntityManagerFactoryBean factoryBean;
    private TransactionTemplate tx;
    private JdbcTemplate jdbc;
    private DrlRepository repo;

    @BeforeAll
    void openValidatedRepository() throws Exception {
        var source = DrlTestDatabase.source(DrlTestDatabase.FRESH);
        DrlTestDatabase.flyway(source, DrlTestDatabase.TARGET).validate();

        factoryBean = new LocalContainerEntityManagerFactoryBean();
        factoryBean.setDataSource(source);
        factoryBean.setPackagesToScan("pbl6.ctsv.drl.entity");
        factoryBean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factoryBean.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.hbm2ddl.jdbc_metadata_extraction_strategy", "individually",
                "hibernate.physical_naming_strategy", new DrlPhysicalNamingStrategy()));
        factoryBean.afterPropertiesSet();

        var factory = factoryBean.getObject();
        jdbc = new JdbcTemplate(source);
        var transactionManager = new JpaTransactionManager(factory);
        assertThat(transactionManager.getDataSource()).isSameAs(source);
        tx = new TransactionTemplate(transactionManager);
        repo = new DrlRepository(SharedEntityManagerCreator.createSharedEntityManager(factory), jdbc);
    }

    @AfterAll
    void closeRepository() {
        if (factoryBean != null) {
            factoryBean.destroy();
        }
    }

    @Test
    void rejectsSkippingFromDraftToPublished() {
        rejectForOpenDossier(51701, "TR_RLDot_PhienBan", data -> {
            var round = find(RlDot.class, data.round());
            return () -> round.setTrangThai((short) 2);
        });
    }

    @Test
    void rejectsRoundWithNoZeroRating() {
        rejects(51702, "TR_RLDot_PhienBan", () -> {
            long form = insertUnpinnedForm();
            long term = insert("INSERT dbo.hoc_ky (ma) VALUES (?)", code());
            var round = newRound(term, form);
            return () -> repo.persist(round);
        });
    }

    @Test
    void rejectsPublicationWithoutCtsvConfirmation() {
        rejectForOpenDossier(51703, "TR_RLDot_PhienBan", data -> {
            var round = find(RlDot.class, data.round());
            round.setTrangThai((short) 1);
            return () -> round.setTrangThai((short) 2);
        });
    }

    @Test
    void rejectsChangingPinnedForm() {
        rejectForPinnedForm("TR_RLBieuMau_DaGhim", data -> {
            var form = find(RlBieuMau.class, data.form());
            return () -> form.setTenBieuMau("Changed pinned form");
        });
    }

    @Test
    void rejectsChangingPinnedCriterionGroup() {
        rejectForPinnedForm("TR_RLNhomTieuChi_DaGhim", data -> {
            var group = find(RlNhomTieuChi.class, data.group());
            return () -> group.setTenNhom("Changed pinned group");
        });
    }

    @Test
    void rejectsChangingPinnedCriterion() {
        rejectForPinnedForm("TR_RLBieuMauCT_Nhom", data -> {
            var criterion = find(RlBieuMauCt.class, data.criterion());
            return () -> criterion.setNoiDung("Changed pinned criterion");
        });
    }

    @Test
    void rejectsDeletingPinnedRating() {
        rejectForPinnedForm("TR_RLXepLoai_DaGhim", data -> {
            var rating = repo.ratingsForForm(data.form()).getFirst();
            return () -> repo.remove(rating);
        });
    }

    @Test
    void rejectsGuidanceWithParentFromDifferentGroup() {
        rejects(51705, "TR_RLBieuMauCT_Nhom", () -> {
            long form = insertUnpinnedForm();
            long parentGroup = insertGroup(form, 1, 10);
            long childGroup = insertGroup(form, 2, 10);
            long criterion = insertCriterion(parentGroup, 1);

            var guidance = new RlBieuMauCt();
            guidance.setNhomTieuChiId(childGroup);
            guidance.setMaNoiDung("GUIDANCE");
            guidance.setLoaiDong((short) 1);
            guidance.setTieuChiChaId(criterion);
            guidance.setNoiDung("Synthetic guidance");
            guidance.setThuTu(1);
            return () -> repo.persist(guidance);
        });
    }

    @Test
    void rejectsMovingFacultyWithAssignedClass() {
        rejectForOpenDossier(51706, "TR_RLKhoaDot_PhamVi", data -> {
            var faculty = find(RlKhoaDot.class, data.facultyRound());
            long replacement = insert("INSERT dbo.don_vi (ma) VALUES (?)", code());
            return () -> faculty.setKhoaId(replacement);
        });
    }

    @Test
    void rejectsMovingClassWithAssignedBallots() {
        rejectForOpenDossier(51707, "TR_RLLopDot_PhamVi", data -> {
            var root = find(RlLopDot.class, data.root());
            long destination = anotherFacultyRound(data.round());
            return () -> root.setKhoaDotId(destination);
        });
    }

    @Test
    void rejectsSameClassUnderTwoFacultiesInOneRound() {
        rejectForOpenDossier(51708, "TR_RLLopDot_PhamVi", data -> {
            long destination = anotherFacultyRound(data.round());
            long classroom = find(RlLopDot.class, data.root()).getLopId();
            var duplicate = new RlLopDot();
            duplicate.setKhoaDotId(destination);
            duplicate.setLopId(classroom);
            return () -> repo.persist(duplicate);
        });
    }

    @Test
    void rejectsChangingPublishedFacultyRoster() {
        rejectForOpenDossier(51709, "TR_RLKhoaDot_PhamVi", data -> {
            publish(data);
            var faculty = find(RlKhoaDot.class, data.facultyRound());
            return () -> faculty.setNgayGuiCtsv(TIME.plusDays(1));
        });
    }

    @Test
    void rejectsChangingPublishedClassRoster() {
        rejectForOpenDossier(51709, "TR_RLLopDot_PhamVi", data -> {
            publish(data);
            var root = find(RlLopDot.class, data.root());
            return () -> root.setNgayGuiKhoa(TIME.plusDays(1));
        });
    }

    @Test
    void rejectsDeletingPublishedBallot() {
        rejectForOpenDossier(51709, "TR_RLPhieu_PhamVi", data -> {
            publish(data);
            var ballot = find(RlPhieu.class, data.first());
            return () -> repo.remove(ballot);
        });
    }

    @Test
    void rejectsClearingIndividualConfirmation() {
        rejectForOpenDossier(51710, "TR_RLPhieu_PhamVi", data -> {
            var ballot = confirmIndividual(data.first());
            return () -> {
                ballot.setXacNhanSv(false);
                ballot.setNgayXnSv(null);
            };
        });
    }

    @Test
    void rejectsSecondBallotForSameStudentAndSemester() {
        rejectForOpenDossier(51711, "TR_RLPhieu_PhamVi", data -> {
            var duplicate = new RlPhieu();
            duplicate.setLopDotId(DrlDossierFixture.anotherRoot(jdbc, data));
            duplicate.setSinhVienId(find(RlPhieu.class, data.first()).getSinhVienId());
            return () -> repo.persist(duplicate);
        });
    }

    @Test
    void rejectsDeletingScoreAfterClassAuditClose() {
        rejectForOpenDossier(51712, "TR_RLPhieuDiemCT_Diem", data -> {
            var existing = repo.persist(score(data.first(), data.criterion(), 1));
            jdbc.update("""
                    INSERT dbo.nhat_ky_thao_tac (su_kien_id, hanh_dong, loai_doi_tuong, doi_tuong_id)
                    VALUES (NEWID(), N'RL_CHOT_LOP', N'RLLopDot', CONVERT(nvarchar(128), ?))
                    """, data.root());
            return () -> repo.remove(existing);
        });
    }

    @Test
    void rejectsChangingConfirmedIndividualScore() {
        rejectForOpenDossier(51713, "TR_RLPhieuDiemCT_Diem", data -> {
            var existing = repo.persist(score(data.first(), data.criterion(), 1));
            confirmIndividual(data.first());
            return () -> existing.setDiemCn(BigDecimal.valueOf(2));
        });
    }

    @Test
    void rejectsScoreAboveCriterionMaximum() {
        rejectForOpenDossier(51714, "TR_RLPhieuDiemCT_Diem", data -> {
            var aboveMaximum = score(data.first(), data.criterion(), 101);
            return () -> repo.persist(aboveMaximum);
        });
    }

    @Test
    void rejectsTotalAboveGroupMaximum() {
        rejects(51715, "TR_RLPhieuDiemCT_Diem", () -> {
            long form = insertUnpinnedForm();
            long group = insertGroup(form, 1, 6);
            long firstCriterion = insertCriterion(group, 1);
            long secondCriterion = insertCriterion(group, 2);
            long facultyRound = anotherFacultyRound(pinForm(form));
            long classroom = insert("INSERT dbo.lop_sinh_hoat (ma) VALUES (?)", code());
            long root = insert("INSERT dbo.RLLopDot (KhoaDotID, LopID) VALUES (?, ?)", facultyRound, classroom);
            long student = insert("INSERT dbo.sinh_vien (mssv, lop_hien_tai_id) VALUES (?, ?)", code(), classroom);
            long ballot = insert("INSERT dbo.RLPhieu (LopDotID, SinhVienID) VALUES (?, ?)", root, student);
            repo.persist(score(ballot, firstCriterion, 4));

            var secondScore = score(ballot, secondCriterion, 4);
            return () -> repo.persist(secondScore);
        });
    }

    private record PinnedForm(long form, long group, long criterion) {}

    private void rejectForPinnedForm(String trigger, Function<PinnedForm, Runnable> arrange) {
        rejects(51704, trigger, () -> {
            long form = insertUnpinnedForm();
            long group = insertGroup(form, 1, 10);
            long criterion = insertCriterion(group, 1);
            pinForm(form);
            return arrange.apply(new PinnedForm(form, group, criterion));
        });
    }

    private void rejectForOpenDossier(int code, String trigger, Function<DrlDossierFixture.ClassData, Runnable> arrange) {
        rejects(code, trigger, () -> {
            var data = DrlDossierFixture.create(jdbc);
            assertThat(find(RlLopDot.class, data.root()).getNgayLapHoSo()).isNull();
            return arrange.apply(data);
        });
    }

    private void rejects(int code, String trigger, Supplier<Runnable> arrange) {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            Runnable mutation = arrange.get();
            repo.flush();

            // IDENTITY inserts can reach SQL Server at persist(), while updates/deletes reach it at flush().
            Throwable error = catchThrowable(() -> {
                mutation.run();
                repo.flush();
            });
            assertThat(error).as("repository mutation rejected by %s", trigger).isNotNull();
            Throwable cause = error;
            while (cause != null && !(cause instanceof SQLServerException)) {
                cause = cause.getCause();
            }
            assertThat(cause).as("SQL Server exception for %s", trigger).isInstanceOf(SQLServerException.class);
            var sql = (SQLServerException) cause;
            assertThat(sql.getErrorCode()).as(sql.getMessage()).isEqualTo(code);
            var serverError = sql.getSQLServerError();
            assertThat(serverError.getProcedureName()).isIn(trigger, "dbo." + trigger);
            assertThat(serverError.getErrorMessage()).isNotBlank();
            System.out.printf("DRL_TRIGGER %d %s | %s%n",
                    sql.getErrorCode(), serverError.getProcedureName(), serverError.getErrorMessage());
        });
    }

    private void publish(DrlDossierFixture.ClassData data) {
        var round = find(RlDot.class, data.round());
        var faculty = find(RlKhoaDot.class, data.facultyRound());
        var root = find(RlLopDot.class, data.root());
        long user = insert("INSERT dbo.nguoi_dung (dia_chi_email) VALUES (?)", code() + "@example.invalid");

        faculty.setNgayGuiCtsv(TIME);
        root.setNgayGuiKhoa(TIME);
        root.setNguoiXnCtsvId(user);
        root.setNgayXnCtsv(TIME);
        round.setTrangThai((short) 1);
        repo.flush();
        round.setTrangThai((short) 2);
        repo.flush();
    }

    private RlPhieu confirmIndividual(long id) {
        var ballot = find(RlPhieu.class, id);
        ballot.setXacNhanSv(true);
        ballot.setNgayXnSv(TIME);
        return ballot;
    }

    private long anotherFacultyRound(long round) {
        long faculty = insert("INSERT dbo.don_vi (ma) VALUES (?)", code());
        return insert("INSERT dbo.RLKhoaDot (DotID, KhoaID) VALUES (?, ?)", round, faculty);
    }

    private long pinForm(long form) {
        jdbc.update("INSERT dbo.RLXepLoai (BieuMauID, DiemToiThieu, TenXepLoai) VALUES (?, 0, N'Test')", form);
        long term = insert("INSERT dbo.hoc_ky (ma) VALUES (?)", code());
        return repo.persist(newRound(term, form)).getId();
    }

    private long insertUnpinnedForm() {
        return insert("""
                INSERT dbo.RLBieuMau (MaBieuMau, SoPhienBan, TenBieuMau, DiemToiDa)
                VALUES (?, 1, N'Synthetic V17 form', 100)
                """, code());
    }

    private long insertGroup(long form, int order, int max) {
        return insert("""
                INSERT dbo.RLNhomTieuChi (BieuMauID, TenNhom, ThuTu, DiemToiDa)
                VALUES (?, N'Synthetic V17 group', ?, ?)
                """, form, order, max);
    }

    private long insertCriterion(long group, int order) {
        return insert("""
                INSERT dbo.RLBieuMauCT (NhomTieuChiID, MaNoiDung, LoaiDong, NoiDung, ThuTu, DiemToiDa)
                VALUES (?, ?, 0, N'Synthetic V17 criterion', ?, 10)
                """, group, "SCORE-" + order, order);
    }

    private RlDot newRound(long term, long form) {
        String code = code();
        var round = new RlDot();
        round.setMaDot(code);
        round.setTenDot(code);
        round.setHocKyId(term);
        round.setBieuMauId(form);
        round.setBatDauSv(TIME);
        round.setKetThucSv(TIME.plusDays(1));
        round.setBatDauLop(TIME.plusDays(2));
        round.setKetThucLop(TIME.plusDays(3));
        round.setBatDauKhoa(TIME.plusDays(4));
        round.setKetThucKhoa(TIME.plusDays(5));
        return round;
    }

    private static RlPhieuDiemCt score(long ballot, long criterion, int value) {
        var score = new RlPhieuDiemCt();
        score.setPhieuId(ballot);
        score.setBieuMauCtId(criterion);
        score.setDiemCn(BigDecimal.valueOf(value));
        return score;
    }

    private <T extends DrlEntity> T find(Class<T> type, long id) {
        return repo.find(type, id).orElseThrow();
    }

    private long insert(String sql, Object... args) {
        return DrlDossierFixture.insert(jdbc, sql, args);
    }

    private static String code() {
        return "D26-" + UUID.randomUUID();
    }
}
