package pbl6.ctsv.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

final class AuthRepository {
    record Account(UUID id,String username,String hash,boolean active,long version,byte[] rowVersion) {
        @Override public String toString(){return "Account[id="+id+",active="+active+"]";}
    }
    record Refresh(UUID id,UUID account,long version,Instant expiry,boolean used,boolean revoked) {}
    record Audit(UUID actor,String system,UUID target,String action,String reason,Long role,
                 UUID token,Long previous,Long next,Boolean before,Boolean after) {}
    private static final String ACCOUNT_COLUMNS="ID,TenDangNhap,MatKhauHash,TrangThai,PhienBanToken,PhienBan";
    private static final RowMapper<Account> ACCOUNT=(r,n)->new Account(uuid(r,"ID"),r.getString("TenDangNhap"),
        r.getString("MatKhauHash"),r.getBoolean("TrangThai"),r.getLong("PhienBanToken"),r.getBytes("PhienBan"));
    final JdbcTemplate jdbc;
    AuthRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    Account byUsername(String canonical) {
        return one(jdbc.query("SELECT "+ACCOUNT_COLUMNS+" FROM dbo.TaiKhoan WHERE TenDangNhapChuanHoa=?",ACCOUNT,canonical));
    }

    Account account(UUID id,boolean lock) {
        return one(jdbc.query("SELECT "+ACCOUNT_COLUMNS+" FROM dbo.TaiKhoan"+(lock?" WITH(UPDLOCK,HOLDLOCK)":"")+" WHERE ID=?",ACCOUNT,id.toString()));
    }

    void create(UUID id,AuthCredentials.Username username,String hash,Instant now) {
        jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash,ThoiDiemTao,ThoiDiemCapNhat) VALUES(?,?,?,?,?,?)",
            id.toString(),username.display(),username.canonical(),hash,time(now),time(now));
    }

    UUID tokenOwner(byte[] hash) {
        return one(jdbc.query("SELECT TaiKhoanID FROM dbo.RefreshToken WHERE TokenHash=?",(r,n)->uuid(r,"TaiKhoanID"),hash));
    }

    Refresh refresh(byte[] hash) {
        return one(jdbc.query("SELECT ID,TaiKhoanID,PhienBanToken,HetHanLuc,SuDungLuc,ThuHoiLuc FROM dbo.RefreshToken WITH(UPDLOCK,HOLDLOCK) WHERE TokenHash=?",
            (r,n)->new Refresh(uuid(r,"ID"),uuid(r,"TaiKhoanID"),r.getLong("PhienBanToken"),
                r.getObject("HetHanLuc",OffsetDateTime.class).toInstant(),r.getObject("SuDungLuc")!=null,r.getObject("ThuHoiLuc")!=null),hash));
    }

    void insertRefresh(UUID id,Account account,byte[] hash,Instant now,Instant deadline) {
        jdbc.update("INSERT dbo.RefreshToken(ID,TaiKhoanID,TokenHash,PhienBanToken,TaoLuc,HetHanLuc) VALUES(?,?,?,?,?,?)",
            id.toString(),account.id().toString(),hash,account.version(),time(now),time(deadline));
    }

    void consume(UUID token,Instant now) {
        if(jdbc.update("UPDATE dbo.RefreshToken SET SuDungLuc=? WHERE ID=? AND SuDungLuc IS NULL",time(now),token.toString())!=1)
            throw AuthFailure.unavailable();
    }

    long bump(Account account,Instant now) {
        long next=Math.addExact(account.version(),1);
        cas(jdbc.update("UPDATE dbo.TaiKhoan SET PhienBanToken=?,ThoiDiemCapNhat=? WHERE ID=? AND PhienBan=?",
            next,time(now),account.id().toString(),account.rowVersion()));
        return next;
    }

    static void cas(int rows){
        if(rows!=1) throw new AuthFailure(412,"VERSION_CONFLICT");
    }

    void revoke(UUID account,Instant now) {
        jdbc.update("UPDATE dbo.RefreshToken SET ThuHoiLuc=? WHERE TaiKhoanID=? AND ThuHoiLuc IS NULL AND HetHanLuc>?",
            time(now),account.toString(),time(now));
    }

    long audit(Audit event,Instant now,UUID correlation) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT dbo.NhatKyAuth(SuKienID,MaTuongQuan,ThoiDiem,ActorID,TacNhanHeThong,TaiKhoanDichID,HanhDong,LyDo,
                  VaiTroID,TokenID,PhienBanTokenTruoc,PhienBanTokenSau,TrangThaiTruoc,TrangThaiSau)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            id.toString(),correlation.toString(),time(now),string(event.actor),event.system,
            string(event.target),event.action,event.reason,event.role,string(event.token),event.previous,event.next,event.before,event.after);
        return jdbc.queryForObject("SELECT ID FROM dbo.NhatKyAuth WHERE SuKienID=?",Long.class,id.toString());
    }

    List<AuthViews.Role> roles(UUID account) {
        return jdbc.query("""
            SELECT r.ID,r.MaVaiTro,r.TenVaiTro,r.TrangThai FROM dbo.VaiTro r
            JOIN dbo.TaiKhoan_VaiTro ar ON ar.VaiTroID=r.ID WHERE ar.TaiKhoanID=? AND r.TrangThai=1 ORDER BY r.ID
            """,AuthRepository::role,account.toString());
    }

    List<AuthViews.Function> functions(UUID account) {
        return jdbc.query("""
            SELECT DISTINCT f.ID,f.MaChucNang,f.TenChucNang,f.TrangThai FROM dbo.ChucNang f
            JOIN dbo.VaiTro_ChucNang rf ON rf.ChucNangID=f.ID JOIN dbo.VaiTro r ON r.ID=rf.VaiTroID
            JOIN dbo.TaiKhoan_VaiTro ar ON ar.VaiTroID=r.ID
            WHERE ar.TaiKhoanID=? AND r.TrangThai=1 AND f.TrangThai=1 ORDER BY f.ID
            """,AuthRepository::function,account.toString());
    }

    static AuthViews.Role role(ResultSet r,int n)throws SQLException {
        return new AuthViews.Role(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4));
    }

    static AuthViews.Function function(ResultSet r,int n)throws SQLException {
        return new AuthViews.Function(r.getString(1),r.getString(2),r.getString(3),r.getBoolean(4));
    }

    static UUID uuid(ResultSet r,String col)throws SQLException {return UUID.fromString(r.getString(col));}
    static String string(UUID id){return id==null?null:id.toString();}
    static OffsetDateTime time(Instant t){return t.atOffset(ZoneOffset.UTC);}
    private static <T> T one(List<T> values){return values.isEmpty()?null:values.getFirst();}
}
