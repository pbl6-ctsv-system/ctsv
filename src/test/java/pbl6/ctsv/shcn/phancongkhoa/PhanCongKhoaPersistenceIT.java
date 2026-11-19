package pbl6.ctsv.shcn.phancongkhoa;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.shcn.phancongkhoa.entity.*;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaRepository;

import static org.assertj.core.api.Assertions.*;

/** SQL_SERVER_REAL + JPA/JDBC_REAL. No HTTP, IAM or Foundation projection is mocked as implemented. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhanCongKhoaPersistenceIT {
    private static final OffsetDateTime FROM=OffsetDateTime.parse("2026-08-01T00:00:00+07:00");
    private static final OffsetDateTime NOW=OffsetDateTime.parse("2026-11-18T02:00:00Z");
    private LocalContainerEntityManagerFactoryBean bean;
    private EntityManagerFactory factory;
    private EntityManager em;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private PhanCongKhoaRepository repository;

    @BeforeAll void open() throws Exception {
        var source=ShcnTestDatabase.source(ShcnTestDatabase.UPGRADE);
        var flyway=ShcnTestDatabase.flyway(source,null);flyway.migrate();flyway.validate();
        bean=new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(source);bean.setPackagesToScan("pbl6.ctsv.shcn.phancongkhoa.entity");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.show_sql","false"));
        bean.afterPropertiesSet();factory=Objects.requireNonNull(bean.getObject());
        em=SharedEntityManagerCreator.createSharedEntityManager(factory);
        var manager=new JpaTransactionManager(factory);manager.setDataSource(source);
        tx=new TransactionTemplate(manager);jdbc=new JdbcTemplate(source);
        repository=new PhanCongKhoaRepository(em,jdbc);
    }
    @AfterAll void close(){if(bean!=null)bean.destroy();}
    private <T>T inTransaction(Supplier<T> action){return tx.execute(status->action.get());}
    private String code(){return "TK"+UUID.randomUUID().toString().replace("-","").substring(0,28);}
    private DanhSachPhanCongKhoa draft(String khoa,int version,Long previous){
        return repository.taoDanhSach(DanhSachPhanCongKhoa.nhap(khoa,"2026-2027",version,previous,FROM,null,"synthetic-truong-khoa","Phân công giả lập",NOW));
    }
    private DanhSachPhanCongKhoa create(){return inTransaction(()->draft(code(),1,null));}
    private DanhSachPhanCongKhoa read(long id){return inTransaction(()->repository.timDanhSach(id).orElseThrow());}
    private List<PhanCongGvcnKhoa> lines(long id){return inTransaction(()->repository.cacPhanCong(id));}
    private DanhSachPhanCongKhoa save(DanhSachPhanCongKhoa root,PhanCongKhoaRepository.DongNhap... rows){
        return inTransaction(()->repository.luuNhap(root.getId(),root.getPhienBanGhi(),FROM,null,"Điều chỉnh giả lập",List.of(rows)));
    }
    private PhanCongKhoaRepository.DongNhap line(String lop,String gv){return new PhanCongKhoaRepository.DongNhap(lop,gv,null);}

    @Test void unicodeNullableOffsetIdentityAndLazyRelationRoundTrip(){
        var root=create();
        assertThat(root.getId()).isPositive();assertThat(root.getPhienBanGhi()).hasSize(8);
        inTransaction(()->{
            var changed=repository.luuNhap(root.getId(),root.getPhienBanGhi(),FROM,null,"Thay đổi giảng viên",List.of(new PhanCongKhoaRepository.DongNhap("0001","0007","Giảng viên chủ nhiệm — dữ liệu giả")));
            assertThat(changed.getHieuLucTu().toInstant()).isEqualTo(Instant.parse("2026-07-31T17:00:00Z"));
            assertThat(changed.getHieuLucDen()).isNull();assertThat(changed.getLyDoDieuChinh()).isEqualTo("Thay đổi giảng viên");
            assertThat(changed.getMaHoSoTaiLieu()).isNull();assertThat(changed.getNgayGuiChuyenVien()).isNull();
            // The root above was already loaded; use a fresh context to observe LAZY fetching.
            em.clear();
            var child=repository.cacPhanCong(root.getId()).getFirst();
            assertThat(child.getId()).isPositive();assertThat(child.getMaLop()).isEqualTo("0001");assertThat(child.getMaGiaoVien()).isEqualTo("0007");
            assertThat(child.getGhiChu()).isEqualTo("Giảng viên chủ nhiệm — dữ liệu giả");
            assertThat(factory.getPersistenceUnitUtil().isLoaded(child,"danhSachKhoa")).isFalse();
            assertThat(child.getDanhSachKhoa().getId()).isEqualTo(root.getId());return null;
        });
    }

    @Test void entityColumnsMatchSqlNullabilityLengthIdentityAndSchema(){
        assertThat(factory.getMetamodel().getEntities()).hasSize(2);
        for(var entity:factory.getMetamodel().getEntities()){
            var type=entity.getJavaType();var table=type.getAnnotation(Table.class);
            assertThat(table.schema()).isEqualTo("shcn");
            var actual=jdbc.queryForList("SELECT c.name,c.max_length,c.is_nullable,c.is_identity FROM sys.columns c WHERE c.object_id=OBJECT_ID(?)","shcn."+table.name());
            var names=new HashSet<String>();
            for(var field:type.getDeclaredFields()){
                var column=field.getAnnotation(Column.class);if(column==null)continue;names.add(column.name());
                var sql=actual.stream().filter(row->column.name().equals(row.get("name"))).findFirst().orElseThrow();
                assertThat(sql.get("is_nullable")).as(table.name()+"."+column.name()).isEqualTo(column.nullable());
                assertThat(sql.get("is_identity")).isEqualTo(field.isAnnotationPresent(GeneratedValue.class));
                if(field.getType()==String.class && !column.columnDefinition().contains("(max)"))
                    assertThat(((Number)sql.get("max_length")).intValue()).isEqualTo(column.length()*(field.isAnnotationPresent(org.hibernate.annotations.Nationalized.class)?2:1));
                if(field.getType()==byte[].class){assertThat(column.insertable()).isFalse();assertThat(column.updatable()).isFalse();assertThat(((Number)sql.get("max_length")).intValue()).isEqualTo(8);}
            }
            assertThat(names).containsExactlyInAnyOrderElementsOf(actual.stream().map(row->row.get("name").toString()).toList());
        }
    }

    @Test void duplicateFacultyYearVersionIsRejectedBySql(){
        String khoa=code();inTransaction(()->draft(khoa,1,null));
        assertThatThrownBy(()->inTransaction(()->draft(khoa,1,null))).hasStackTraceContaining("uq_shcn_danh_sach_khoa_phien_ban");
        assertThat(inTransaction(()->repository.cacDanhSach(khoa,"2026-2027"))).hasSize(1);
    }

    @Test void sqlRejectsZeroBusinessVersion(){
        assertThatThrownBy(()->inTransaction(()->draft(code(),0,null))).hasStackTraceContaining("ck_shcn_danh_sach_khoa_phien_ban");
    }

    @Test void sqlRejectsReversedEffectiveInterval(){
        assertThatThrownBy(()->inTransaction(()->repository.taoDanhSach(DanhSachPhanCongKhoa.nhap(code(),"2026-2027",1,null,FROM,FROM.minusDays(1),"synthetic",null,NOW))))
            .hasStackTraceContaining("ck_shcn_danh_sach_khoa_hieu_luc");
    }

    @Test void predecessorMustExistAndBelongToSameFaculty(){
        var previous=create();
        assertThatThrownBy(()->inTransaction(()->draft(code(),1,previous.getId()))).hasStackTraceContaining("fk_shcn_danh_sach_khoa_truoc");
        var next=inTransaction(()->draft(previous.getMaKhoa(),2,previous.getId()));
        inTransaction(()->{
            var loaded=repository.timDanhSach(next.getId()).orElseThrow();
            assertThat(factory.getPersistenceUnitUtil().isLoaded(loaded,"danhSachTruoc")).isFalse();
            assertThat(loaded.getDanhSachTruoc().getId()).isEqualTo(previous.getId());return null;
        });
        assertThatThrownBy(()->inTransaction(()->{jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET ma_danh_sach_truoc=id WHERE id=?",next.getId());return null;}))
            .hasStackTraceContaining("ck_shcn_danh_sach_khoa_truoc");
    }

    @Test void fullReplacementUpdatesRetainedRowsWithoutRecreatingTheirIds(){
        var root=save(create(),line("A","GV1"),line("B","GV2"));
        long retained=lines(root.getId()).stream().filter(row->row.getMaLop().equals("A")).findFirst().orElseThrow().getId();
        var changed=save(root,line("A","GV3"),line("C","GV4"));
        var rows=lines(root.getId());assertThat(rows).extracting(PhanCongGvcnKhoa::getMaLop).containsExactly("A","C");
        assertThat(rows.getFirst().getId()).isEqualTo(retained);assertThat(rows.getFirst().getMaGiaoVien()).isEqualTo("GV3");
        assertThat(changed.getPhienBanGhi()).isNotEqualTo(root.getPhienBanGhi());assertThat(changed.getPhienBan()).isEqualTo(1);
    }

    @Test void emptyReplacementClearsOnlyDraftLines(){
        var root=save(create(),line("A","GV1"));var cleared=save(root);
        assertThat(lines(root.getId())).isEmpty();assertThat(cleared.getTrangThai()).isEqualTo("NHAP");
        assertThat(read(root.getId()).getId()).isEqualTo(root.getId());
    }

    @Test void staleRootTokenCannotOverwriteAnyDraftContent(){
        var initial=create();var current=save(initial,line("A","GV1"));
        assertThatThrownBy(()->save(initial,line("B","GV2"))).isInstanceOf(PhanCongKhoaRepository.XungDot.class).hasMessage("VERSION_CONFLICT");
        assertThat(lines(initial.getId())).extracting(PhanCongGvcnKhoa::getMaLop).containsExactly("A");
        assertThat(read(initial.getId()).getPhienBanGhi()).isEqualTo(current.getPhienBanGhi());
    }

    @Test void closedDraftCannotBeSavedAndCasIsCheckedBeforeState(){
        var initial=save(create(),line("A","GV1"));
        inTransaction(()->{jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='CHO_KY' WHERE id=?",initial.getId());return null;});
        var closed=read(initial.getId());
        assertThatThrownBy(()->save(initial,line("A","GV2"))).hasMessage("VERSION_CONFLICT");
        assertThatThrownBy(()->save(closed,line("A","GV2"))).hasMessage("SHCN_KHONG_CON_LA_NHAP");
        assertThatThrownBy(()->inTransaction(()->{jdbc.update("UPDATE shcn.shcn_phan_cong_gvcn_khoa SET ma_giao_vien='GV2' WHERE ma_danh_sach_khoa=?",initial.getId());return null;}))
            .hasStackTraceContaining("writable only while draft");
        assertThatThrownBy(()->inTransaction(()->{jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET ly_do_dieu_chinh=N'changed' WHERE id=?",initial.getId());return null;}))
            .hasStackTraceContaining("cannot be edited or reopened");
        assertThat(lines(initial.getId()).getFirst().getMaGiaoVien()).isEqualTo("GV1");
    }

    @Test void directChildMutationAlsoConsumesTheAggregateToken(){
        var initial=save(create(),line("A","GV1"));
        inTransaction(()->{jdbc.update("UPDATE shcn.shcn_phan_cong_gvcn_khoa SET ghi_chu=N'Synthetic SQL change' WHERE ma_danh_sach_khoa=?",initial.getId());return null;});
        assertThat(read(initial.getId()).getPhienBanGhi()).isNotEqualTo(initial.getPhienBanGhi());
        assertThatThrownBy(()->save(initial,line("A","GV2"))).hasMessage("VERSION_CONFLICT");
    }

    @Test void failureAfterRootAndChildWritesRollsBackTheWholeDraft(){
        var root=save(create(),line("A","GV1"));var before=lines(root.getId()).getFirst();
        assertThatThrownBy(()->save(root,line("A","GV2"),line("B","GV3"),line("C","X".repeat(33))))
            .hasStackTraceContaining("truncated");
        var after=read(root.getId());var rows=lines(root.getId());
        assertThat(after.getPhienBanGhi()).isEqualTo(root.getPhienBanGhi());
        assertThat(rows).hasSize(1);assertThat(rows.getFirst().getId()).isEqualTo(before.getId());
        assertThat(rows.getFirst().getMaGiaoVien()).isEqualTo("GV1");
    }

    @Test void sqlCollationDuplicateClassesCannotPartiallySave(){
        var root=create();
        assertThatThrownBy(()->save(root,line("A","GV1"),line("a","GV2"))).hasStackTraceContaining("uq_shcn_phan_cong_khoa_lop");
        assertThat(lines(root.getId())).isEmpty();assertThat(read(root.getId()).getPhienBanGhi()).isEqualTo(root.getPhienBanGhi());
    }

    @Test void duplicateClassInRequestIsRejectedBeforeWriting(){
        var root=create();assertThatThrownBy(()->save(root,line("A","GV1"),line("A","GV2"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(lines(root.getId())).isEmpty();assertThat(read(root.getId()).getPhienBanGhi()).isEqualTo(root.getPhienBanGhi());
    }

    @Test void twoWritersWithOneTokenHaveExactlyOneWinner() throws Exception {
        var root=create();var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<String>>();
            for(String gv:List.of("GV1","GV2"))futures.add(pool.submit(()->{
                ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Race barrier timeout");
                try{save(root,line("A",gv));return "OK";}catch(PhanCongKhoaRepository.XungDot failure){return failure.code();}
            }));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("OK","VERSION_CONFLICT");
        }
        assertThat(lines(root.getId())).hasSize(1);
    }

    @Test void concurrentVersionAllocationSerializesTheFacultyYearRange() throws Exception {
        String khoa=code();var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var futures=new ArrayList<Future<Integer>>();
            for(int n=0;n<2;n++)futures.add(pool.submit(()->{
                ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Race barrier timeout");
                return inTransaction(()->{int version=repository.phienBanTiepTheo(khoa,"2026-2027");draft(khoa,version,null);return version;});
            }));
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(1,2);
        }
        assertThat(inTransaction(()->repository.cacDanhSach(khoa,"2026-2027"))).extracting(DanhSachPhanCongKhoa::getPhienBan).containsExactly(2,1);
    }

    @Test void identicalDraftSaveConsumesTokenWithoutChangingBusinessVersion(){
        var initial=create();var changed=save(initial);assertThat(changed.getPhienBan()).isEqualTo(initial.getPhienBan());
        assertThat(changed.getPhienBanGhi()).isNotEqualTo(initial.getPhienBanGhi());
    }

    @Test void repositoryCannotRunOutsideCallerTransaction(){
        assertThatThrownBy(()->repository.timDanhSach(1)).isInstanceOf(IllegalStateException.class).hasMessageContaining("caller's local transaction");
    }

    @Test void closedRootCannotBeDeletedOrReopened(){
        var root=create();inTransaction(()->{jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='DA_KY' WHERE id=?",root.getId());return null;});
        assertThatThrownBy(()->inTransaction(()->{jdbc.update("DELETE FROM shcn.shcn_danh_sach_phan_cong_khoa WHERE id=?",root.getId());return null;}))
            .hasStackTraceContaining("cannot be deleted");
        assertThatThrownBy(()->inTransaction(()->{jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='NHAP' WHERE id=?",root.getId());return null;}))
            .hasStackTraceContaining("cannot be edited or reopened");
        assertThat(read(root.getId()).getTrangThai()).isEqualTo("DA_KY");
    }
}
