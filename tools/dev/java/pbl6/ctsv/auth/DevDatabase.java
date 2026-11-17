package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit local-dev setup/check command. Not an application bean, endpoint, test host, or migration. */
public final class DevDatabase {
    static final String DB="ctsv_dev", MARKER="ctsv-local-dev-v1";
    static final ObjectMapper JSON=new ObjectMapper();
    static String stage="config";
    public static void main(String[] args) {
        try { run(args); }
        catch(Exception e) {
            // Never dump exception messages/SQL params/credentials into launcher logs.
            System.err.println("Dev DB failed at "+stage+" ["+e.getClass().getSimpleName()+"]. Inspect target/history/permissions; no test DB fallback or reset.");
            if(e instanceof SQLException s)System.err.println("SQL error code="+s.getErrorCode()+", state="+s.getSQLState());
            System.exit(1);
        }
    }
    static void require(boolean value,String code){if(!value)throw new IllegalStateException(code);}
    static void run(String[] args)throws Exception {
        require(args.length==1&&Set.of("setup","migrate","check","snapshot","seed-student").contains(args[0]),"mode");
        boolean setup=args[0].equals("setup");
        boolean migrate=setup||args[0].equals("migrate");
        Path home=Path.of(Objects.requireNonNull(System.getenv("CTSV_DEV_HOME")));
        var properties=new Properties();try(var in=Files.newInputStream(home.resolve("application-dev.properties"))){properties.load(in);}
        String url=properties.getProperty("spring.datasource.url");
        require(url!=null&&url.matches("jdbc:sqlserver://localhost:[0-9]+;databaseName=ctsv_dev;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;connectRetryCount=0"),"target");
        var login=new Properties();
        stage="server identity";
        try(var c=DriverManager.getConnection(url.replace("databaseName=ctsv_dev;","databaseName=master;"),login);var s=c.createStatement()){
            try(var r=s.executeQuery("SELECT CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),CONVERT(int,SERVERPROPERTY('ProductMajorVersion')),DB_ID(N'ctsv_dev')")){
                require(r.next()&&"DEV2022".equals(r.getString(1))&&r.getInt(2)==16,"instance");
                boolean exists=r.getObject(3)!=null;
                if(!exists){require(setup,"missing dev DB");r.close();stage="create named dev DB";s.execute("CREATE DATABASE [ctsv_dev] COLLATE Vietnamese_100_CI_AS_SC");}
            }
        }
        var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),url,login);
        var jdbc=new JdbcTemplate(ds);
        stage="dev DB ownership/schema guard";
        try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("""
            SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),SUSER_SNAME(owner_sid),SUSER_SNAME(),
             compatibility_level,collation_name,(SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name='ctsv_dev_owner')
            FROM sys.databases WHERE database_id=DB_ID()
            """)){
            require(r.next()&&DB.equals(r.getString(1))&&"DEV2022".equals(r.getString(2))&&r.getString(3).equals(r.getString(4))
                &&r.getInt(5)==160&&"Vietnamese_100_CI_AS_SC".equals(r.getString(6)),"database owner/options");
            String marker=r.getString(7);
            if(marker==null){
                require(setup&&jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",Integer.class)==0,"unmanaged/nonempty database");
                jdbc.execute("EXEC sys.sp_addextendedproperty @name=N'ctsv_dev_owner',@value=N'ctsv-local-dev-v1'");
            }else require(MARKER.equals(marker),"owner marker");
        }
        boolean history=jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name='flyway_schema_history' AND schema_id=SCHEMA_ID('dbo')",Integer.class)==1;
        if(!history)require(setup&&jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",Integer.class)==0,"nonempty without Flyway history");
        var flyway=Flyway.configure().dataSource(ds).cleanDisabled(true).baselineOnMigrate(false).validateOnMigrate(true).load();
        stage="Flyway validate existing history";
        // Explicit setup lets migrate validate applied checksums and accept pending versions atomically.
        // Read-only check/snapshot must still reject a pending migration instead of applying it.
        if(history&&!migrate)flyway.validate();
        if(migrate){stage="approved migrations only";flyway.migrate();}
        flyway.validate();
        require(flyway.info().pending().length==0,"pending migration; run setup explicitly");
        if(setup){stage="synthetic dev identities";seed(ds,jdbc,properties,home);stage="owner-approved DEV password";presetPassword(ds,jdbc,properties,home);}
        if(setup||args[0].equals("seed-student")){
            stage="P06 synthetic student lookup data";
            new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(tx->seedIntakeStudent(jdbc));
        }
        stage="snapshot";
        var accounts=jdbc.queryForList("SELECT TenDangNhap AS username,CONVERT(varchar(36),ID) AS accountId,PhienBanToken AS tokenVersion FROM dbo.TaiKhoan WHERE TenDangNhap IN ('dev.admin','dev.sinhvien','dev.chuyenvien') ORDER BY TenDangNhap");
        var counts=new TreeMap<String,Long>();
        for(String table:List.of("TaiKhoan","TaiKhoan_VaiTro","LienKetHoSo","nguoi_dung","dinh_danh_dang_nhap","sinh_vien","can_bo","phan_quyen_nguoi_dung"))
            counts.put(table,jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo."+table,Long.class));
        var snapshot=new LinkedHashMap<String,Object>();
        snapshot.put("database",DB);snapshot.put("instance","DEV2022");snapshot.put("mode",args[0]);snapshot.put("counts",counts);snapshot.put("accounts",accounts);
        snapshot.put("flyway",Arrays.stream(flyway.info().applied()).map(m->Map.of("version",m.getVersion().toString(),"checksum",m.getChecksum(),"state",m.getState().name())).toList());
        Files.writeString(home.resolve("db-"+args[0]+"-snapshot.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot));
        System.out.println("DEV DB "+args[0]+" OK: localhost\\DEV2022 / ctsv_dev; owner/marker/Flyway validated. No test DB accessed.");
    }
    static String password(String kind){return Objects.requireNonNull(System.getenv("CTSV_DEV_"+kind.toUpperCase(Locale.ROOT)+"_PASSWORD"));}
    static void seed(DataSource ds,JdbcTemplate jdbc,Properties config,Path home)throws Exception {
        var repo=new AuthRepository(jdbc);var credentials=new AuthCredentials();var manager=new DataSourceTransactionManager(ds);
        var auth=new AuthService(repo,credentials,Clock.systemUTC(),null,new AuthLoginLimiter(),manager);
        var admin=new AuthAdministration(auth,Set.of("SIS_MOCK"),JSON);
        String issuer=config.getProperty("ctsv.auth.issuer");
        String initial=password("admin");
        var first=repo.byUsername("dev.admin");
        if(first==null){admin.bootstrap("dev.admin",initial);first=repo.byUsername("dev.admin");require(first!=null,"existing different admin; no takeover");}
        require(credentials.matches(initial,first.hash()),"admin credentials differ; no password reset");
        var ids=new LinkedHashMap<String,Object>();
        for(String kind:List.of("admin","student","specialist")){
            String name=switch(kind){case "student"->"dev.sinhvien";case "specialist"->"dev.chuyenvien";default->"dev.admin";};
            var a=repo.byUsername(name);
            if(a==null){
                var actor=new AuthAccess.Principal(issuer,first.id(),repo.account(first.id(),false).version());
                var links=List.of(new AuthViews.ProfileLink("SIS_MOCK",kind.equals("student")?"student":"staff",kind.equals("student")?"DEV-STUDENT-001":"DEV-STAFF-001"));
                admin.create(actor,name,password(kind),links);a=repo.byUsername(name);
            }
            require(a.active()&&credentials.matches(password(kind),a.hash()),"credentials changed; no overwrite");
            long role=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro='CTSV_USER'",Long.class);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=? AND VaiTroID=?",Integer.class,a.id().toString(),role)==0){
                var actor=new AuthAccess.Principal(issuer,first.id(),repo.account(first.id(),false).version());
                var current=admin.read(actor,a.id());admin.role(actor,a.id(),role,current.etag(),true);
            }
            UUID account=a.id();
            String email="dev."+kind+"@example.invalid";
            long user=new TransactionTemplate(manager).execute(tx->{
                var users=jdbc.query("SELECT id FROM dbo.nguoi_dung WITH(UPDLOCK,HOLDLOCK) WHERE dia_chi_email=?",(r,n)->r.getLong(1),email);
                boolean fresh=users.isEmpty();
                long u=fresh?insert(jdbc,"INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) VALUES(?,1)",email):users.getFirst();
                if(fresh){
                    jdbc.update("INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",u,issuer,account.toString());
                    if(kind.equals("student"))jdbc.update("INSERT dbo.sinh_vien(mssv,nguoi_dung_id,ho_ten,ma_dinh_danh_ben_ngoai) VALUES(N'DEV-SV-001',?,N'Sinh viên DEV giả lập',N'DEV-STUDENT-001')",u);
                    else jdbc.update("INSERT dbo.can_bo(ma_cb,nguoi_dung_id,ho_ten) VALUES(?,?,N'Cán bộ DEV giả lập')","DEV-"+kind.toUpperCase(Locale.ROOT),u);
                    if(!kind.equals("student"))jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,? FROM dbo.vai_tro WHERE ma=?",u,kind.equals("admin")?"CTSV":"MGHP",kind.equals("admin")?"ctsv.roles.manage":"mghp_chuyen_vien");
                }else require(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.dinh_danh_dang_nhap WHERE nguoi_dung_id=? AND ma_nha_cung_cap=? AND ma_chu_the_dang_nhap=? AND thoi_diem_xac_minh IS NOT NULL",Integer.class,u,issuer,account.toString())==1,"identity mismatch; no rebind");
                return u;
            });
            ids.put(kind,Map.of("username",name,"accountId",account,"userId",Long.toString(user)));
        }
        new TransactionTemplate(manager).executeWithoutResult(tx->{
            if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.don_vi WITH(UPDLOCK,HOLDLOCK) WHERE ma=N'DEV-UNIT'",Integer.class)==0)
                jdbc.update("INSERT dbo.don_vi(ma,ten) VALUES(N'DEV-UNIT',N'Đơn vị DEV giả lập')");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.hoc_ky WITH(UPDLOCK,HOLDLOCK) WHERE ma=N'DEV-2026-1'",Integer.class)==0)
                jdbc.update("INSERT dbo.hoc_ky(ma,ten) VALUES(N'DEV-2026-1',N'Học kỳ DEV giả lập')");
            long sv=jdbc.queryForObject("SELECT id FROM dbo.sinh_vien WHERE mssv=N'DEV-SV-001'",Long.class);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.qua_trinh_theo_hoc WHERE ma_qua_trinh_hoc=N'DEV-PROCESS-001'",Integer.class)==0)
                jdbc.update("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,ma_qua_trinh_hoc) VALUES(?,N'DEV-PROCESS-001')",sv);
            ids.put("studentId",Long.toString(sv));
            ids.put("semesterId",jdbc.queryForObject("SELECT CONVERT(varchar(20),id) FROM dbo.hoc_ky WHERE ma=N'DEV-2026-1'",String.class));
            ids.put("processId",jdbc.queryForObject("SELECT CONVERT(varchar(20),id) FROM dbo.qua_trinh_theo_hoc WHERE ma_qua_trinh_hoc=N'DEV-PROCESS-001'",String.class));
        });
        Files.writeString(home.resolve("identities.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(ids));
    }
    static long insert(JdbcTemplate jdbc,String sql,Object...args){return jdbc.queryForObject(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,args);}
    static void seedIntakeStudent(JdbcTemplate jdbc) {
        var students=jdbc.queryForList("""
            SELECT s.id FROM dbo.sinh_vien s WITH (UPDLOCK,HOLDLOCK)
            JOIN dbo.nguoi_dung u ON u.id=s.nguoi_dung_id
            WHERE s.mssv=N'DEV-SV-001' AND s.ma_dinh_danh_ben_ngoai=N'DEV-STUDENT-001'
              AND s.he_thong_nguon_id IS NULL AND s.thoi_diem_xoa IS NULL
              AND u.dia_chi_email=N'dev.student@example.invalid'
            """,Long.class);
        require(students.size()==1,"unexpected synthetic student identity");
        var units=jdbc.queryForList("SELECT id FROM dbo.don_vi WHERE ma=N'DEV-UNIT' AND he_thong_nguon_id IS NULL",Long.class);
        require(units.size()==1,"missing synthetic DEV unit; run setup for a new checkout");
        long unit=units.getFirst();
        long ethnicity=fixtureId(jdbc,"dan_toc","DEV-P06-ETHNICITY","INSERT dbo.dan_toc(ma,ten) VALUES(?,N'Dân tộc DEV giả lập')");
        long birthplace=fixtureId(jdbc,"dia_phuong","DEV-P06-PLACE","INSERT dbo.dia_phuong(ma,ten) VALUES(?,N'Nơi sinh DEV giả lập')");
        long cohort=fixtureId(jdbc,"khoa_hoc","DEV-P06-COHORT","INSERT dbo.khoa_hoc(ma) VALUES(?)");
        long schoolClass=fixtureId(jdbc,"lop_sinh_hoat","DEV-P06-CLASS",
            "INSERT dbo.lop_sinh_hoat(ma,ten,khoa_hoc_id,khoa_quan_ly_id) VALUES(?,N'Lớp DEV giả lập',?,?)",cohort,unit);
        jdbc.update("""
            UPDATE dbo.sinh_vien SET
                so_cccd=COALESCE(so_cccd,N'DEV-SYNTHETIC-ID'), ngay_sinh=COALESCE(ngay_sinh,CONVERT(date,'20050101')),
                noi_sinh_id=COALESCE(noi_sinh_id,?), gioi_tinh=COALESCE(gioi_tinh,N'Nam'),
                dan_toc_id=COALESCE(dan_toc_id,?), lop_hien_tai_id=COALESCE(lop_hien_tai_id,?),
                khoa_hoc_hien_tai_id=COALESCE(khoa_hoc_hien_tai_id,?), khoa_quan_ly_hien_tai_id=COALESCE(khoa_quan_ly_hien_tai_id,?),
                so_dien_thoai=COALESCE(so_dien_thoai,N'0000000000')
            WHERE id=? AND (so_cccd IS NULL OR ngay_sinh IS NULL OR noi_sinh_id IS NULL OR gioi_tinh IS NULL
                  OR dan_toc_id IS NULL OR lop_hien_tai_id IS NULL OR khoa_hoc_hien_tai_id IS NULL
                  OR khoa_quan_ly_hien_tai_id IS NULL OR so_dien_thoai IS NULL)
            """,birthplace,ethnicity,schoolClass,cohort,unit,students.getFirst());
    }
    private static long fixtureId(JdbcTemplate jdbc,String table,String code,String insertSql,Object... values) {
        var rows=jdbc.queryForList("SELECT id,he_thong_nguon_id FROM dbo."+table+" WITH (UPDLOCK,HOLDLOCK) WHERE ma=?",code);
        if(!rows.isEmpty()){
            require(rows.size()==1&&rows.getFirst().get("he_thong_nguon_id")==null,"fixture code conflict");
            return ((Number)rows.getFirst().get("id")).longValue();
        }
        var args=new ArrayList<Object>();args.add(code);args.addAll(Arrays.asList(values));
        return insert(jdbc,insertSql,args.toArray());
    }
    static void presetPassword(DataSource ds,JdbcTemplate jdbc,Properties config,Path home)throws Exception {
        // Public synthetic DEV fixture; use the existing Auth hashing and password policy.
        String preset="CtsvDev@2026!";
        var credentials=new AuthCredentials();
        var identities=JSON.readTree(Files.readString(home.resolve("identities.json")));
        var repo=new AuthRepository(jdbc);
        var auth=new AuthService(repo,new AuthCredentials(),Clock.systemUTC(),null,new AuthLoginLimiter(),new DataSourceTransactionManager(ds));
        auth.transaction(()->{
            UUID actor=UUID.fromString(identities.path("admin").path("accountId").asText());
            for(String kind:List.of("admin","student","specialist")){
                UUID id=UUID.fromString(identities.path(kind).path("accountId").asText());
                var a=repo.account(id,true);
                String expected=switch(kind){case "student"->"dev.sinhvien";case "specialist"->"dev.chuyenvien";default->"dev.admin";};
                require(a!=null&&a.username().equals(expected)&&a.active(),"synthetic account mismatch");
                require(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.dinh_danh_dang_nhap WHERE ma_nha_cung_cap=? AND ma_chu_the_dang_nhap=? AND nguoi_dung_id=? AND thoi_diem_xac_minh IS NOT NULL",Integer.class,
                    config.getProperty("ctsv.auth.issuer"),id.toString(),Long.parseLong(identities.path(kind).path("userId").asText()))==1,"dev mapping mismatch");
                if(credentials.matches(preset,a.hash()))continue;
                var now=auth.now();var correlation=UUID.randomUUID();
                long next=Math.addExact(a.version(),1);
                require(jdbc.update("UPDATE dbo.TaiKhoan SET MatKhauHash=?,PhienBanToken=?,ThoiDiemCapNhat=? WHERE ID=? AND PhienBan=?",credentials.hash(preset),next,AuthRepository.time(now),id.toString(),a.rowVersion())==1,"dev password CAS");
                auth.recordRevocation(a,actor,"Owner-approved synthetic DEV password",null,next,now,correlation);
                repo.audit(new AuthRepository.Audit(actor,null,id,"PASSWORD_RESET","Owner-approved DEV fixture; not production policy",null,null,a.version(),next,null,null),now,correlation);
            }
            return null;
        });
    }
}
