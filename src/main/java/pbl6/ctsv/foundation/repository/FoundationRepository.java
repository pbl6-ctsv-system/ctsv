package pbl6.ctsv.foundation.repository;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.FoundationAccess.Failure;
import pbl6.ctsv.foundation.dto.FoundationDtos;
import pbl6.ctsv.foundation.dto.FoundationDtos.*;
import pbl6.ctsv.foundation.entity.*;
import pbl6.ctsv.foundation.mapper.FoundationMapper;

/** JPA for ordinary persistence; SQL Server locking, rowversion fencing and audit stay explicit. Caller owns transaction. */
public final class FoundationRepository implements AcademicSemesterSource {
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private static final Set<String> ROOTS = Set.of("hoc_ky","qua_trinh_theo_hoc","nguoi_dung");
    public FoundationRepository(EntityManager em,JdbcTemplate jdbc) { this.em=em; this.jdbc=jdbc; }

    public <T> T find(Class<T> type,long id) {
        T value=em.find(type,id);
        if(value==null) throw new Failure(404,"NOT_FOUND","id");
        return value;
    }
    public <T> void save(T entity) {
        // FK IDs are writable; read-only LAZY associations must be reloaded after an insert.
        em.persist(entity); em.flush(); em.refresh(entity);
    }
    public FoundationDtos.Page<LookupItem> users(String q,int page,int size,int offset) {
        String where=" where u.dangHoatDong=true and u.thoiDiemXoa is null and (:q is null or u.hoTen like :pattern escape '~' or u.diaChiEmail like :pattern escape '~')";
        var rows=em.createQuery("from NguoiDung u"+where+" order by u.hoTen,u.id",NguoiDung.class).setParameter("q",q).setParameter("pattern",like(q)).setFirstResult(offset).setMaxResults(size).getResultList();
        long count=em.createQuery("select count(u) from NguoiDung u"+where,Long.class).setParameter("q",q).setParameter("pattern",like(q)).getSingleResult();
        return new FoundationDtos.Page<>(rows.stream().map(u->new LookupItem(u.getId().toString(),u.getDiaChiEmail(),u.getHoTen())).toList(),page,size,count);
    }
    public List<Map<String,Object>> roleChoices() {
        return em.createQuery("from VaiTro where dangHoatDong=true and ma in ('mghp_chuyen_vien','mghp_pho_phong') order by ma,id",VaiTro.class).getResultList().stream().map(r->Map.<String,Object>of("id",r.getId().toString(),"code",r.getMa(),"name",r.getTen()==null?r.getMa():r.getTen(),"active",true)).toList();
    }
    public FoundationDtos.Page<Map<String,Object>> profileChoices(String type,String q,Set<String> sources,int page,int size,int offset){
        if(sources.isEmpty())return new FoundationDtos.Page<>(List.of(),page,size,0);
        boolean student="student".equals(type);
        String table=student?"sinh_vien":"can_bo",code=student?"mssv":"ma_cb",external=student?"ma_dinh_danh_ben_ngoai":"can_bo_ben_ngoai_id";
        String placeholders=String.join(",",Collections.nCopies(sources.size(),"?"));
        String where=" FROM dbo."+table+" s JOIN dbo.he_thong_ngoai h ON h.id=s.he_thong_nguon_id WHERE h.ma IN ("+placeholders+") AND s."+external+" IS NOT NULL AND s."+external+"<>N''"+(student?" AND s.thoi_diem_xoa IS NULL":"")+" AND (? IS NULL OR s."+code+" LIKE ? ESCAPE '~' OR s.ho_ten LIKE ? ESCAPE '~')";
        var args=new ArrayList<Object>(sources);String pattern=like(q);args.add(q);args.add(pattern);args.add(pattern);
        long total=jdbc.queryForObject("SELECT COUNT_BIG(*)"+where,Long.class,args.toArray());args.add(offset);args.add(size);
        var items=jdbc.query("SELECT h.ma,s."+external+",s."+code+",s.ho_ten"+where+" ORDER BY s."+code+",s."+external+",h.ma OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(r,n)->{
            var row=new LinkedHashMap<String,Object>();row.put("sourceSystem",r.getString(1));row.put("profileType",type);row.put("sourceId",r.getString(2));row.put("code",r.getString(3));row.put("name",r.getString(4));return (Map<String,Object>)row;
        },args.toArray());return new FoundationDtos.Page<>(items,page,size,total);
    }
    public void flush() { em.flush(); }
    public void refresh(Object entity) { em.flush(); em.refresh(entity); }
    public FoundationDtos.Page<StudentSummary> students(String q,int page,int size,int offset) {
        String where=" where (:q is null or s.mssv like :pattern escape '~' or s.hoTen like :pattern escape '~')";
        var rows=em.createQuery("select s from SinhVien s"+where+" order by s.mssv,s.id",SinhVien.class)
            .setParameter("q",q).setParameter("pattern",like(q)).setFirstResult(offset).setMaxResults(size).getResultList();
        long count=em.createQuery("select count(s) from SinhVien s"+where,Long.class).setParameter("q",q).setParameter("pattern",like(q)).getSingleResult();
        return new FoundationDtos.Page<>(rows.stream().map(FoundationMapper::studentSummary).toList(),page,size,count);
    }
    public FoundationDtos.Page<LookupItem> lookup(String kind,String q,int page,int size,int offset) {
        String entity=switch(kind) {
            case "ethnicities" -> "DanToc"; case "cohorts" -> "KhoaHoc"; case "classes" -> "LopSinhHoat";
            case "units" -> "DonVi"; case "semesters" -> "HocKy"; default -> throw new Failure(400,"VALIDATION_ERROR","kind");
        };
        String name=kind.equals("cohorts")?"cast(null as string)":"e.ten";
        String where=" where (:q is null or e.ma like :pattern escape '~' or "+name+" like :pattern escape '~')";
        var rows=em.createQuery("select e.id,e.ma,"+name+" from "+entity+" e"+where+" order by case when e.ma is null then 1 else 0 end,e.ma,e.id",Object[].class)
            .setParameter("q",q).setParameter("pattern",like(q)).setFirstResult(offset).setMaxResults(size).getResultList();
        long total=em.createQuery("select count(e) from "+entity+" e"+where,Long.class).setParameter("q",q).setParameter("pattern",like(q)).getSingleResult();
        return new FoundationDtos.Page<>(rows.stream().map(r -> new LookupItem(r[0].toString(),(String)r[1],(String)r[2])).toList(),page,size,total);
    }
    public FoundationDtos.Page<LookupItem> lookupIds(String kind,List<Long> ids){
        String entity=switch(kind){case "ethnicities"->"DanToc";case "cohorts"->"KhoaHoc";case "classes"->"LopSinhHoat";case "units"->"DonVi";case "semesters"->"HocKy";default->throw new Failure(400,"VALIDATION_ERROR","kind");};
        String name=kind.equals("cohorts")?"cast(null as string)":"e.ten";
        var items=em.createQuery("select e.id,e.ma,"+name+" from "+entity+" e where e.id in :ids order by e.id",Object[].class).setParameter("ids",ids).getResultList().stream().map(r->new LookupItem(r[0].toString(),(String)r[1],(String)r[2])).toList();
        return new FoundationDtos.Page<>(items,0,ids.size(),items.size());
    }

    @Override
    public Page findPage(String q, int page, int size) {
        requireSemesterSourceTransaction();
        String pattern = q == null || q.isBlank() ? null : like(q.strip());
        String where = pattern == null ? "" : " where h.ma like :pattern escape '~' or h.ten like :pattern escape '~'";
        var rows = em.createQuery("""
            select h.id, h.ma, h.ten, h.namHocNguon, h.ngayBatDau, h.ngayKetThuc,
                   h.trangThaiXacMinhMoc, h.dangHienDienNguon, h.phienBan
            from HocKy h
            """ + where + " order by h.ma,h.id", Object[].class);
        var count = em.createQuery("select count(h) from HocKy h" + where, Long.class);
        if (pattern != null) {
            rows.setParameter("pattern", pattern);
            count.setParameter("pattern", pattern);
        }
        var items = rows.setFirstResult(Math.toIntExact((long) page * size)).setMaxResults(size).getResultList()
            .stream().map(row -> new Semester((Long) row[0], (String) row[1], (String) row[2], (String) row[3],
                (LocalDate) row[4], (LocalDate) row[5], (String) row[6], Boolean.TRUE.equals(row[7]),
                (byte[]) row[8])).toList();
        return new Page(items, page, size, count.getSingleResult());
    }

    @Override
    public Page findStartedPage(LocalDate cutoff, int page, int size) {
        requireSemesterSourceTransaction();
        Objects.requireNonNull(cutoff, "cutoff");
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid semester candidate page");
        }
        int offset = Math.toIntExact((long) page * size);
        var rows = em.createQuery("""
            select h.id, h.ma, h.ten, h.namHocNguon, h.ngayBatDau, h.ngayKetThuc,
                   h.trangThaiXacMinhMoc, h.dangHienDienNguon, h.phienBan
            from HocKy h where h.ngayBatDau<=:cutoff
            order by h.ngayBatDau desc,h.ma,h.id
            """, Object[].class).setParameter("cutoff", cutoff);
        var count = em.createQuery("select count(h) from HocKy h where h.ngayBatDau<=:cutoff", Long.class)
            .setParameter("cutoff", cutoff);
        var items = rows.setFirstResult(offset).setMaxResults(size).getResultList().stream()
            .map(row -> new Semester((Long) row[0], (String) row[1], (String) row[2], (String) row[3],
                (LocalDate) row[4], (LocalDate) row[5], (String) row[6], Boolean.TRUE.equals(row[7]),
                (byte[]) row[8])).toList();
        return new Page(items, page, size, count.getSingleResult());
    }

    @Override
    public Optional<Semester> findById(long id) {
        requireSemesterSourceTransaction();
        em.flush();
        // Read the projected values and rowversion under the same source lock.
        return jdbc.query("""
            SELECT id,ma,ten,nam_hoc_nguon,ngay_bat_dau,ngay_ket_thuc,
                   trang_thai_xac_minh_moc,dang_hien_dien_nguon,phien_ban
            FROM dbo.hoc_ky WITH(UPDLOCK,HOLDLOCK) WHERE id=?
            """, (row, index) -> {
                var start = row.getDate("ngay_bat_dau");
                var end = row.getDate("ngay_ket_thuc");
                return new Semester(row.getLong("id"), row.getString("ma"), row.getString("ten"),
                    row.getString("nam_hoc_nguon"), start == null ? null : start.toLocalDate(),
                    end == null ? null : end.toLocalDate(), row.getString("trang_thai_xac_minh_moc"),
                    row.getBoolean("dang_hien_dien_nguon"), row.getBytes("phien_ban"));
            }, id).stream().findFirst();
    }

    private static void requireSemesterSourceTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Foundation semester source requires a local transaction");
        }
    }

    public List<QuaTrinhTheoHoc> processes(long student) { return em.createQuery("from QuaTrinhTheoHoc where sinhVienId=:id order by id",QuaTrinhTheoHoc.class).setParameter("id",student).getResultList(); }
    public List<LichSuTrangThaiSv> statuses(long process) { return em.createQuery("from LichSuTrangThaiSv where quaTrinhHocId=:id order by ngayBatDauHieuLuc,id",LichSuTrangThaiSv.class).setParameter("id",process).getResultList(); }
    public List<QuaTrinhThuocLop> classes(long process) { return em.createQuery("from QuaTrinhThuocLop where quaTrinhHocId=:id order by hieuLucTu,id",QuaTrinhThuocLop.class).setParameter("id",process).getResultList(); }
    public List<PhanQuyenNguoiDung> roles(long user,boolean current) {
        String where=current?" and p.vaiTro.dangHoatDong=true and (p.hieuLucTu is null or p.hieuLucTu<=:now)":"";
        var query=em.createQuery("select p from PhanQuyenNguoiDung p join fetch p.vaiTro where p.nguoiDungId=:id and p.maPhanHe='MGHP' and (p.hieuLucDen is null or p.hieuLucDen>:now)"+where+" order by p.vaiTroId,p.id",PhanQuyenNguoiDung.class);
        return query.setParameter("id",user).setParameter("now",now()).getResultList();
    }
    public boolean applicableRole(long role) {
        var r=em.find(VaiTro.class,role);
        return r!=null&&Boolean.TRUE.equals(r.getDangHoatDong())&&Set.of("mghp_chuyen_vien","mghp_pho_phong").contains(r.getMa());
    }
    public byte[] version(String table,long id) {
        root(table); em.flush();
        var rows=jdbc.query("SELECT phien_ban FROM dbo."+table+" WITH(UPDLOCK,HOLDLOCK) WHERE id=?",(r,n)->r.getBytes(1),id);
        if(rows.isEmpty()) throw new Failure(404,"NOT_FOUND","id");
        return rows.getFirst();
    }
    public void touch(String table,long id,byte[] version) {
        root(table); em.flush();
        if(jdbc.update("UPDATE dbo."+table+" SET thoi_diem_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00') WHERE id=? AND phien_ban=?",id,version)!=1)
            throw new Failure(412,"VERSION_CONFLICT","request");
    }
    public boolean semesterOrderTaken(int order,long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.hoc_ky WITH(UPDLOCK,HOLDLOCK) WHERE so_thu_tu=? AND id<>?",Integer.class,order,id)>0; }
    public boolean overlap(long id,LocalDate from,LocalDate until,boolean status) {
        String table=status?"lich_su_trang_thai_sv":"qua_trinh_thuoc_lop",start=status?"ngay_bat_dau_hieu_luc":"hieu_luc_tu",end=status?"ngay_ket_thuc_hieu_luc":"hieu_luc_den";
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WITH(UPDLOCK,HOLDLOCK) WHERE qua_trinh_hoc_id=? AND ("+end+" IS NULL OR "+end+">=?) AND (? IS NULL OR "+start+"<=?)",Integer.class,id,from,until,until)>0;
    }
    public void foreignKey(String table,Long id) {
        if(!Set.of("chuong_trinh_dao_tao","khoa_hoc","hoc_ky","tep_tin","lop_sinh_hoat").contains(table)) throw new IllegalArgumentException("Unsupported FK table");
        if(id!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WHERE id=?",Integer.class,id)==0) throw new Failure(400,"VALIDATION_ERROR","id");
    }
    public OffsetDateTime now() { return jdbc.queryForObject("SELECT TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')",OffsetDateTime.class); }
    public void audit(long user,String action,String type,long id,String reason) {
        em.flush();
        jdbc.update("INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao) VALUES(?,N'user',?,?,?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",UUID.randomUUID().toString(),user,action,type,Long.toString(id),reason);
    }
    private static String like(String q) { return q==null?null:"%"+q.replace("~","~~").replace("%","~%").replace("_","~_").replace("[","~[")+"%"; }
    private static void root(String table) { if(!ROOTS.contains(table)) throw new IllegalArgumentException("Unsupported concurrency root"); }
}
