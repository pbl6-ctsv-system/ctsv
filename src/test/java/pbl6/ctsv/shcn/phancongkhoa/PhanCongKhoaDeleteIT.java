package pbl6.ctsv.shcn.phancongkhoa;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.shcn.phancongkhoa.entity.DanhSachPhanCongKhoa;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaRepository;
import static org.assertj.core.api.Assertions.*;
/** Existing owned test DB only. No Flyway, migration, schema creation, reset or DEV fallback. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhanCongKhoaDeleteIT {
 LocalContainerEntityManagerFactoryBean bean;PhanCongKhoaRepository repo;JdbcTemplate jdbc;TransactionTemplate tx;
 @BeforeAll void open()throws Exception{
  var ds=ShcnTestDatabase.source(ShcnTestDatabase.UPGRADE);
  bean=new LocalContainerEntityManagerFactoryBean();bean.setDataSource(ds);bean.setPackagesToScan("pbl6.ctsv.shcn.phancongkhoa.entity");bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());bean.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.hbm2ddl.jdbc_metadata_extraction_strategy","individually"));bean.afterPropertiesSet();
  var factory=Objects.requireNonNull(bean.getObject());var manager=new JpaTransactionManager(factory);manager.setDataSource(ds);tx=new TransactionTemplate(manager);jdbc=new JdbcTemplate(ds);repo=new PhanCongKhoaRepository(SharedEntityManagerCreator.createSharedEntityManager(factory),jdbc);
 }
 @AfterAll void close(){if(bean!=null)bean.destroy();}
 DanhSachPhanCongKhoa create(){return tx.execute(s->repo.taoDanhSach(DanhSachPhanCongKhoa.nhap("DEL"+UUID.randomUUID().toString().replace("-","").substring(0,24),"SYNTH-DELETE",1,null,OffsetDateTime.parse("2026-08-01T00:00:00Z"),null,"synthetic-delete",null,OffsetDateTime.now(ZoneOffset.UTC))));}
 DanhSachPhanCongKhoa read(long id){return tx.execute(s->repo.timDanhSach(id).orElseThrow());}
 @Test void deletionRetainsLinesAndReservesVersionWhileHidingFromList(){
  var a=create();var saved=tx.execute(s->repo.luuNhap(a.getId(),a.getPhienBanGhi(),a.getHieuLucTu(),null,null,List.of(new PhanCongKhoaRepository.DongNhap("CLASS-DELETE","GV-DELETE",null))));
  tx.executeWithoutResult(s->repo.xoaNhap(saved.getId(),saved.getPhienBanGhi()));assertThat(read(a.getId()).getTrangThai()).isEqualTo("DA_HUY");
  tx.executeWithoutResult(s->{assertThat(repo.cacPhanCong(a.getId())).hasSize(1);assertThat(repo.trangDanhSach(a.getMaKhoa(),a.getNamHoc(),null,0,25).total()).isZero();assertThat(repo.phienBanLonNhat(a.getMaKhoa(),a.getNamHoc())).isEqualTo(1);assertThat(repo.phienBanTiepTheo(a.getMaKhoa(),a.getNamHoc())).isEqualTo(2);});
 }
 @Test void staleVersionRollsBackDeletion(){var a=create();var fresh=tx.execute(s->repo.luuNhap(a.getId(),a.getPhienBanGhi(),a.getHieuLucTu(),null,null,List.of()));assertThatThrownBy(()->tx.executeWithoutResult(s->repo.xoaNhap(a.getId(),a.getPhienBanGhi()))).hasMessage("VERSION_CONFLICT");assertThat(read(fresh.getId()).getTrangThai()).isEqualTo("NHAP");}
 @Test void signedRecordCannotBeDeleted(){var a=create();jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='DA_KY' WHERE id=?",a.getId());var signed=read(a.getId());assertThatThrownBy(()->tx.executeWithoutResult(s->repo.xoaNhap(a.getId(),signed.getPhienBanGhi()))).hasMessage("SHCN_KHONG_CON_LA_NHAP");assertThat(read(a.getId()).getTrangThai()).isEqualTo("DA_KY");}
 @Test void laterFailureRollsBackLogicalDelete(){var a=create();assertThatThrownBy(()->tx.executeWithoutResult(s->{repo.xoaNhap(a.getId(),a.getPhienBanGhi());throw new IllegalStateException("Synthetic later failure");})).hasMessage("Synthetic later failure");assertThat(read(a.getId()).getTrangThai()).isEqualTo("NHAP");}
}
