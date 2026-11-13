package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.dao.DataIntegrityViolationException;
import static pbl6.ctsv.auth.AuthViews.*;
import static pbl6.ctsv.auth.AuthRepository.*;

final class AuthAdministration {
    private static final String MANAGE="auth.accounts.manage";
    private final AuthService auth;
    private final AuthRepository db;
    private final Set<String> trustedSources;
    private final ObjectMapper json;
    AuthAdministration(AuthService auth, Set<String> trustedSources,ObjectMapper json){
        this.auth=auth;
        this.db=auth.db;
        this.trustedSources=Set.copyOf(trustedSources);
        this.json=json;
    }

    boolean bootstrap(String username,String password){
        var login=AuthCredentials.username(username);
        String hash=auth.credentials.hash(password);
        return auth.transaction(()->{
            // SQL Server starts the JDBC transaction on the first table read.
            long role=db.jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN' AND TrangThai=1",Long.class);
            Integer result=db.jdbc.queryForObject("""
                SET NOCOUNT ON;
                DECLARE @result int;
                EXEC @result=sys.sp_getapplock @Resource=N'ctsv.auth.bootstrap',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
                SELECT @result;
                """,Integer.class);
            if(result==null || result<0) throw AuthFailure.unavailable();
            if(db.jdbc.queryForObject("SELECT COUNT(*) FROM dbo.NhatKyAuth WHERE HanhDong='BOOTSTRAP_ADMIN'",Integer.class)>0) return false;
            if(db.jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan_VaiTro WHERE VaiTroID=?",Integer.class,role)>0) return false;
            if(db.byUsername(login.canonical())!=null) throw new AuthFailure(409,"USERNAME_CONFLICT");
            UUID id=UUID.randomUUID();
            Instant now=auth.now();
            db.create(id,login,hash,now);
            db.jdbc.update("INSERT dbo.TaiKhoan_VaiTro(TaiKhoanID,VaiTroID) VALUES(?,?)",id.toString(),role);
            db.audit(new Audit(null,"bootstrap",id,"BOOTSTRAP_ADMIN",null,role,null,null,null,null,null),now,UUID.randomUUID());
            return true;
        });
    }

    AccountResult create(AuthAccess.Principal actor,String username,String password,List<ProfileLink> links){
        var login=AuthCredentials.username(username);
        validateLinks(links);
        auth.transaction(()->auth.actor(actor,MANAGE,true));
        String hash=auth.credentials.hash(password);
        return auth.transaction(()->{
            auth.actor(actor,MANAGE,true);
            if(db.byUsername(login.canonical())!=null) throw conflict("USERNAME_CONFLICT");
            UUID id=UUID.randomUUID();
            Instant now=auth.now();
            try{db.create(id,login,hash,now);}
            catch(DataIntegrityViolationException e){if(duplicate(e))throw conflict("USERNAME_CONFLICT");throw e;}
            long audit=db.audit(new Audit(actor.accountId(),null,id,"ACCOUNT_CREATED",null,null,null,null,null,null,null),now,UUID.randomUUID());
            links.forEach(link->insertLink(id,link,now,audit));
            return result(db.account(id,false));
        });
    }

    AccountResult read(AuthAccess.Principal actor,UUID target){
        return auth.transaction(()->result(lockForAdmin(actor,target)));
    }

    AccountResult updateLinks(AuthAccess.Principal actor,UUID target,String etag,List<ProfileLink> links){
        validateLinks(links);
        return mutate(actor,target,etag,a->{
            var old=new HashSet<>(links(target));
            var next=new HashSet<>(links);
            if(old.equals(next)) return;
            Instant now=auth.now();
            UUID correlation=UUID.randomUUID();
            long audit=db.audit(new Audit(actor.accountId(),null,target,"ACCOUNT_LINKS_CHANGED","Admin-verified profile link update",
                null,null,a.version(),Math.addExact(a.version(),1),null,null),now,correlation);
            for(var link:old) if(!next.contains(link)) db.jdbc.update("""
                UPDATE dbo.LienKetHoSo SET KetThucLuc=?,NhatKyKetThucID=? WHERE TaiKhoanID=? AND KetThucLuc IS NULL
                    AND HeThongNguon=? AND LoaiHoSo=? AND DinhDanhNguon=?
                """,time(now),audit,target.toString(),link.sourceSystem(),link.profileType(),link.sourceId());
            for(var link:links) if(!old.contains(link)) insertLink(target,link,now,audit);
            auth.revoke(a,actor.accountId(),"Profile links changed",null,now,correlation);
        });
    }

    RbacView rbac(AuthAccess.Principal actor,UUID target){
        return auth.transaction(()->{
            auth.actor(actor,"auth.rbac.read",true);
            if(target!=null && db.account(target,false)==null) throw notFound();
            var roles=db.jdbc.query("SELECT ID,MaVaiTro,TenVaiTro,TrangThai FROM dbo.VaiTro ORDER BY ID",AuthRepository::role);
            var functions=db.jdbc.query("SELECT ID,MaChucNang,TenChucNang,TrangThai FROM dbo.ChucNang ORDER BY ID",AuthRepository::function);
            var edges=db.jdbc.query("SELECT VaiTroID,ChucNangID FROM dbo.VaiTro_ChucNang ORDER BY VaiTroID,ChucNangID",
                (r,n)->new RoleFunction(r.getString(1),r.getString(2)));
            List<AccountRole> assigned=target==null?List.of():db.jdbc.query(
                "SELECT VaiTroID FROM dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=? ORDER BY VaiTroID",
                (r,n)->new AccountRole(target,r.getString(1)),target.toString());
            return new RbacView(roles,functions,edges,assigned);
        });
    }

    AccountResult role(AuthAccess.Principal actor,UUID target,long role,String etag,boolean grant){
        return mutate(actor,target,etag,a->{
            if(grant && db.jdbc.queryForObject("SELECT COUNT(*) FROM dbo.VaiTro WITH(HOLDLOCK) WHERE ID=? AND TrangThai=1",Integer.class,role)==0)
                throw conflict("ROLE_UNAVAILABLE");
            int exists=db.jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=? AND VaiTroID=?",
                Integer.class,target.toString(),role);
            if(grant==(exists!=0)) return;
            if(grant) db.jdbc.update("INSERT dbo.TaiKhoan_VaiTro(TaiKhoanID,VaiTroID) VALUES(?,?)",target.toString(),role);
            else db.jdbc.update("DELETE dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=? AND VaiTroID=?",target.toString(),role);
            Instant now=auth.now();
            UUID correlation=UUID.randomUUID();
            long next=auth.revoke(a,actor.accountId(),"General role changed",null,now,correlation);
            db.audit(new Audit(actor.accountId(),null,target,grant?"ACCOUNT_ROLE_GRANTED":"ACCOUNT_ROLE_REVOKED",
                null,role,null,a.version(),next,null,null),now,correlation);
        });
    }

    AccountResult status(AuthAccess.Principal actor,UUID target,String etag,boolean active,String reason){
        return mutate(actor,target,etag,a->{
            if(a.active()==active) return;
            Instant now=auth.now();
            UUID correlation=UUID.randomUUID();
            long next=active?a.version():Math.addExact(a.version(),1);
            cas(db.jdbc.update("UPDATE dbo.TaiKhoan SET TrangThai=?,PhienBanToken=?,ThoiDiemCapNhat=? WHERE ID=? AND PhienBan=?",
                active,next,time(now),target.toString(),a.rowVersion()));
            if(!active) auth.recordRevocation(a,actor.accountId(),"Account locked",null,next,now,correlation);
            db.audit(new Audit(actor.accountId(),null,target,"ACCOUNT_STATUS_CHANGED",reason,null,null,
                a.version(),next,a.active(),active),now,correlation);
        });
    }

    AccountResult reset(AuthAccess.Principal actor,UUID target,String etag,String password,String reason){
        auth.transaction(()->auth.actor(actor,MANAGE,true));
        String hash=auth.credentials.hash(password);
        return mutate(actor,target,etag,a->{
            Instant now=auth.now();
            UUID correlation=UUID.randomUUID();
            long next=Math.addExact(a.version(),1);
            cas(db.jdbc.update("UPDATE dbo.TaiKhoan SET MatKhauHash=?,PhienBanToken=?,ThoiDiemCapNhat=? WHERE ID=? AND PhienBan=?",
                hash,next,time(now),target.toString(),a.rowVersion()));
            auth.recordRevocation(a,actor.accountId(),"Password reset",null,next,now,correlation);
            db.audit(new Audit(actor.accountId(),null,target,"PASSWORD_RESET",reason,null,null,a.version(),next,null,null),now,correlation);
        });
    }

    private AccountResult mutate(AuthAccess.Principal actor,UUID target,String etag,Consumer<Account> operation){
        return auth.transaction(()->{
            var a=lockForAdmin(actor,target);
            if(etag==null) throw new AuthFailure(428,"PRECONDITION_REQUIRED");
            if(!etag.matches("\"[A-Za-z0-9_-]+\"")) throw AuthFailure.invalid();
            if(!result(a).etag().equals(etag)) throw new AuthFailure(412,"VERSION_CONFLICT");
            operation.accept(a);
            return result(db.account(target,false));
        });
    }

    private Account lockForAdmin(AuthAccess.Principal actor,UUID target){
        Account account;
        if(target.compareTo(actor.accountId())<0){
            account=db.account(target,true);
            auth.actor(actor,MANAGE,true);
        }else{
            auth.actor(actor,MANAGE,true);
            account=db.account(target,true);
        }
        if(account==null) throw notFound();
        return account;
    }

    private AccountResult result(Account a){
        var roles=a.active()?db.roles(a.id()):List.<Role>of();
        var functions=a.active()?db.functions(a.id()):List.<Function>of();
        var view=new AccountAdmin(a.id(),a.username(),a.active(),roles,functions,links(a.id()));
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update(a.rowVersion());
            return new AccountResult(view,"\""+Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(json.writeValueAsBytes(view)))+"\"");
        }catch(java.io.IOException|java.security.GeneralSecurityException e){throw new IllegalStateException(e);}
    }

    private List<ProfileLink> links(UUID id){
        return db.jdbc.query("""
            SELECT HeThongNguon,LoaiHoSo,DinhDanhNguon FROM dbo.LienKetHoSo
            WHERE TaiKhoanID=? AND KetThucLuc IS NULL ORDER BY HeThongNguon,LoaiHoSo,DinhDanhNguon
            """,(r,n)->new ProfileLink(r.getString(1),r.getString(2),r.getString(3)),id.toString());
    }

    private void validateLinks(List<ProfileLink> links){
        if(new HashSet<>(links).size()!=links.size()) throw AuthFailure.invalid();
        for(var link:links){
            if(!trustedSources.contains(link.sourceSystem()) || !Set.of("student","staff").contains(link.profileType())
                || link.sourceId().contains("@")) throw AuthFailure.invalid();
        }
    }

    private void insertLink(UUID id,ProfileLink link,Instant now,long audit){
        try {
            db.jdbc.update("""
                INSERT dbo.LienKetHoSo(TaiKhoanID,HeThongNguon,LoaiHoSo,DinhDanhNguon,TaoLuc,XacMinhLuc,CanCuXacMinh,NhatKyTaoID)
                VALUES(?,?,?,?,?,?,?,?)
                """,id.toString(),link.sourceSystem(),link.profileType(),link.sourceId(),time(now),time(now),
                "Admin-verified source list; audit "+audit,audit);
        }catch(DataIntegrityViolationException e){if(duplicate(e))throw conflict("PROFILE_LINK_CONFLICT");throw e;}
    }

    private static boolean duplicate(Throwable e){
        for(Throwable cause=e;cause!=null;cause=cause.getCause())
            if(cause instanceof java.sql.SQLException sql && Set.of(2601,2627,51103).contains(sql.getErrorCode())) return true;
        return false;
    }

    private static AuthFailure conflict(String code){return new AuthFailure(409,code);}
    private static AuthFailure notFound(){return new AuthFailure(404,"NOT_FOUND");}
}
