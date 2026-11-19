package pbl6.ctsv.shcn.phancongkhoa.repository;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.shcn.phancongkhoa.entity.DanhSachPhanCongKhoa;
import pbl6.ctsv.shcn.phancongkhoa.entity.PhanCongGvcnKhoa;

/** Internal persistence only. Caller must authorize RBAC/scope and validate reference codes first. */
@Repository
@ConditionalOnProperty(name="ctsv.shcn.persistence.enabled",havingValue="true")
public class PhanCongKhoaRepository {
    public record DongNhap(String maLop,String maGiaoVien,String ghiChu) {}
    public static final class XungDot extends RuntimeException {
        private final String code;
        public XungDot(String code){super(code);this.code=code;}
        public String code(){return code;}
    }
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    public PhanCongKhoaRepository(EntityManager em,JdbcTemplate jdbc){this.em=em;this.jdbc=jdbc;}

    private void transaction(){
        if(!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("SHCN repository requires the caller's local transaction");
    }
    public DanhSachPhanCongKhoa taoDanhSach(DanhSachPhanCongKhoa row){
        transaction();
        if(row.getId()!=null || !"NHAP".equals(row.getTrangThai())) throw new IllegalArgumentException("New draft required");
        em.persist(row);em.flush();em.refresh(row);return row;
    }
    public Optional<DanhSachPhanCongKhoa> timDanhSach(long id){
        transaction();return Optional.ofNullable(em.find(DanhSachPhanCongKhoa.class,id));
    }
    public List<DanhSachPhanCongKhoa> cacDanhSach(String khoa,String namHoc){
        transaction();return em.createQuery("from DanhSachPhanCongKhoa where maKhoa=:khoa and namHoc=:nam order by phienBan desc,id desc",DanhSachPhanCongKhoa.class)
            .setParameter("khoa",khoa).setParameter("nam",namHoc).getResultList();
    }
    public List<PhanCongGvcnKhoa> cacPhanCong(long danhSach){
        transaction();return em.createQuery("from PhanCongGvcnKhoa where maDanhSachKhoa=:id",PhanCongGvcnKhoa.class)
            .setParameter("id",danhSach).getResultList().stream()
            .sorted(Comparator.comparing(PhanCongGvcnKhoa::getMaLop).thenComparing(PhanCongGvcnKhoa::getId)).toList();
    }
    public record TrangDanhSach(List<DanhSachPhanCongKhoa> items,long total){}
    public TrangDanhSach trangDanhSach(String khoa,String namHoc,Integer version,int offset,int size){
        transaction();
        String where=" where maKhoa=:khoa and namHoc=:nam"+(version==null?"":" and phienBan=:version");
        var query=em.createQuery("from DanhSachPhanCongKhoa"+where+" order by phienBan desc,id desc",DanhSachPhanCongKhoa.class)
            .setParameter("khoa",khoa).setParameter("nam",namHoc).setFirstResult(offset).setMaxResults(size);
        var count=em.createQuery("select count(id) from DanhSachPhanCongKhoa"+where,Long.class).setParameter("khoa",khoa).setParameter("nam",namHoc);
        if(version!=null){query.setParameter("version",version);count.setParameter("version",version);}
        return new TrangDanhSach(query.getResultList(),count.getSingleResult());
    }
    public Map<Long,Integer> demCacPhanCong(Collection<Long> ids){
        transaction();if(ids.isEmpty())return Map.of();
        String marks=String.join(",",Collections.nCopies(ids.size(),"?"));var result=new HashMap<Long,Integer>();
        jdbc.query("SELECT ma_danh_sach_khoa,COUNT_BIG(*) FROM shcn.shcn_phan_cong_gvcn_khoa WHERE ma_danh_sach_khoa IN ("+marks+") GROUP BY ma_danh_sach_khoa",
            rs->{result.put(rs.getLong(1),Math.toIntExact(rs.getLong(2)));},ids.toArray());
        return result;
    }
    /** Hold the aggregate stable while composing header and all lines, including GET detail. */
    public Optional<DanhSachPhanCongKhoa> khoaDanhSach(long id){
        transaction();em.flush();
        var ids=jdbc.query("SELECT id FROM shcn.shcn_danh_sach_phan_cong_khoa WITH(UPDLOCK,HOLDLOCK) WHERE id=?",(r,n)->r.getLong(1),id);
        if(ids.isEmpty())return Optional.empty();em.clear();return timDanhSach(id);
    }
    public void saoChepCacDong(long destination,List<PhanCongGvcnKhoa> source,OffsetDateTime now){
        transaction();for(var row:source)em.persist(new PhanCongGvcnKhoa(destination,row.getMaLop(),row.getMaGiaoVien(),row.getGhiChu(),now));
        em.flush();em.clear();
    }
    /** Range lock uses the existing unique faculty/year/version index and lasts until caller commit. */
    public int phienBanTiepTheo(String khoa,String namHoc){
        transaction();em.flush();
        Long last=jdbc.queryForObject("""
            SELECT COALESCE(MAX(CONVERT(bigint,phien_ban)),0)
            FROM shcn.shcn_danh_sach_phan_cong_khoa WITH(UPDLOCK,HOLDLOCK)
            WHERE ma_khoa=? AND nam_hoc=?
            """,Long.class,khoa,namHoc);
        if(last==null || last>=Integer.MAX_VALUE)throw new XungDot("SHCN_HET_MIEN_PHIEN_BAN");
        return Math.toIntExact(last+1);
    }

    /** Full draft replacement: keep retained line IDs. Does not perform Foundation writes or activate a version. */
    public DanhSachPhanCongKhoa luuNhap(long id,byte[] expected,OffsetDateTime tu,OffsetDateTime den,
            String lyDo,List<DongNhap> cacDong){
        transaction();
        if(expected==null || expected.length!=8)throw new IllegalArgumentException("Eight-byte expectedVersion required");
        if(tu==null || den!=null && !den.toInstant().isAfter(tu.toInstant()))throw new IllegalArgumentException("Invalid effective interval");
        Objects.requireNonNull(cacDong,"Complete draft lines required");
        var selected=new LinkedHashMap<String,DongNhap>();
        for(var row:cacDong){
            Objects.requireNonNull(row,"Null assignment line");
            if(row.maLop()==null || row.maGiaoVien()==null || selected.putIfAbsent(row.maLop(),row)!=null)
                throw new IllegalArgumentException("Each selected class requires one teacher and one line");
        }
        em.flush();
        var roots=jdbc.query("""
            SELECT phien_ban_ghi,trang_thai FROM shcn.shcn_danh_sach_phan_cong_khoa WITH(UPDLOCK,HOLDLOCK) WHERE id=?
            """,(rs,n)->new Object[]{rs.getBytes(1),rs.getString(2)},id);
        if(roots.isEmpty())throw new XungDot("NOT_FOUND");
        if(!Arrays.equals(expected,(byte[])roots.getFirst()[0]))throw new XungDot("VERSION_CONFLICT");
        if(!"NHAP".equals(roots.getFirst()[1]))throw new XungDot("SHCN_KHONG_CON_LA_NHAP");
        int changed=jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET hieu_luc_tu=?,hieu_luc_den=?,ly_do_dieu_chinh=?,
              ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND phien_ban_ghi=? AND trang_thai='NHAP'
            """,tu,den,lyDo,id,expected);
        if(changed!=1)throw new XungDot("VERSION_CONFLICT");
        // Native CAS/child triggers change aggregate state. Never return a cached root/token.
        em.clear();
        var existing=new HashMap<String,PhanCongGvcnKhoa>();
        for(var row:cacPhanCong(id))existing.put(row.getMaLop(),row);
        for(var row:existing.values())if(!selected.containsKey(row.getMaLop()))em.remove(row);
        // Flush removals before inserts to honor SQL unique keys without recreating retained IDs.
        em.flush();
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        for(var wanted:selected.values()){
            var current=existing.get(wanted.maLop());
            if(current==null)em.persist(new PhanCongGvcnKhoa(id,wanted.maLop(),wanted.maGiaoVien(),wanted.ghiChu(),now));
            else current.capNhat(wanted.maGiaoVien(),wanted.ghiChu(),now);
        }
        em.flush();em.clear();
        return timDanhSach(id).orElseThrow(()->new XungDot("NOT_FOUND"));
    }
}
