package pbl6.ctsv.auth;

import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static pbl6.ctsv.auth.AuthViews.*;

final class AuthService implements AuthAccess {
    private record Rotation(TokenIssue issued,boolean reused){}
    private final SecureRandom random = new SecureRandom();
    final AuthRepository db;
    final AuthCredentials credentials;
    final Clock clock;
    private final AuthJwt jwt;
    private final AuthLoginLimiter limiter;
    private final TransactionTemplate tx;

    AuthService(AuthRepository db,AuthCredentials credentials,Clock clock,AuthJwt jwt,AuthLoginLimiter limiter,PlatformTransactionManager manager) {
        this.db=db;
        this.credentials=credentials;
        this.clock=clock;
        this.jwt=jwt;
        this.limiter=limiter;
        this.tx=new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    <T> T transaction(Supplier<T> work){return tx.execute(s->work.get());}
    Instant now(){return clock.instant().truncatedTo(ChronoUnit.MICROS);}

    TokenIssue login(String username,String password,String ip) {
        var login=AuthCredentials.username(username);
        AuthCredentials.validatePassword(password,false);
        limiter.check(login.canonical(),ip,clock.instant());
        var candidate=db.byUsername(login.canonical());
        boolean matches=credentials.matches(password,candidate==null?null:candidate.hash());
        if(!matches || !candidate.active()) throw loginFailed();
        return transaction(()->{
            var locked=db.account(candidate.id(),true);
            if(locked==null || !locked.active() || locked.version()!=candidate.version() || !locked.hash().equals(candidate.hash()))
                throw loginFailed();
            var now=now();
            return issue(locked,now,now.plus(7,ChronoUnit.DAYS),"LOGIN_SUCCEEDED");
        });
    }

    TokenIssue refresh(String cookie){
        byte[] hash=hashCookie(cookie);
        UUID owner=hash==null?null:db.tokenOwner(hash);
        if(owner==null) throw invalidRefresh();
        var result=transaction(()->{
            var account=db.account(owner,true);
            var token=db.refresh(hash);
            Instant now=now();
            if(!validToken(account,token,now)) throw invalidRefresh();
            if(token.used()){
                revoke(account,null,"Refresh token reuse",token.id(),now,UUID.randomUUID());
                return new Rotation(null,true);
            }
            db.consume(token.id(),now);
            return new Rotation(issue(account,now,token.expiry(),"REFRESH_ROTATED"),false);
        });
        if(result.reused()) throw new AuthFailure(401,"REFRESH_REUSED");
        return result.issued();
    }

    boolean logout(String cookie){
        byte[] hash=hashCookie(cookie);
        UUID owner=hash==null?null:db.tokenOwner(hash);
        if(owner==null) return false;
        return transaction(()->{
            var account=db.account(owner,true);
            var token=db.refresh(hash);
            Instant now=now();
            if(!validToken(account,token,now)) return false;
            revoke(account,null,"Cookie logout",token.id(),now,UUID.randomUUID());
            return true;
        });
    }

    private boolean validToken(AuthRepository.Account account,AuthRepository.Refresh token,Instant now){
        return account!=null && account.active() && token!=null && token.account().equals(account.id())
            && token.version()==account.version() && !token.revoked() && now.isBefore(token.expiry());
    }

    private TokenIssue issue(AuthRepository.Account account,Instant now,Instant deadline,String action){
        byte[] secret=new byte[32];
        random.nextBytes(secret);
        String cookie=Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        byte[] hash=sha256(secret);
        Arrays.fill(secret,(byte)0);
        UUID token=UUID.randomUUID();
        db.insertRefresh(token,account,hash,now,deadline);
        db.audit(new AuthRepository.Audit(null,"auth",account.id(),action,null,null,token,null,null,null,null),now,UUID.randomUUID());
        return new TokenIssue(new TokenResponse(jwt.issue(account.id(),account.version(),now),"Bearer",900,deadline,account.id()),cookie);
    }

    long revoke(AuthRepository.Account account,UUID actor,String reason,UUID token,Instant now,UUID correlation){
        long version=db.bump(account,now);
        recordRevocation(account,actor,reason,token,version,now,correlation);
        return version;
    }

    void recordRevocation(AuthRepository.Account account,UUID actor,String reason,UUID token,long version,Instant now,UUID correlation){
        db.revoke(account.id(),now);
        db.audit(new AuthRepository.Audit(actor,actor==null?"auth":null,account.id(),"TOKENS_REVOKED",reason,null,token,
            account.version(),version,null,null),now,correlation);
    }

    @Override public Principal verifyAccessToken(String token){return jwt.verify(token);}

    AuthRepository.Account actor(Principal principal,String capability,boolean lock){
        var account=db.account(principal.accountId(),lock);
        if(account==null || !account.active() || account.version()!=principal.tokenVersion())
            throw new AuthFailure(401,"TOKEN_REVOKED");
        if(capability!=null && db.functions(account.id()).stream().noneMatch(f->f.code().equals(capability)))
            throw new AuthFailure(403,"FORBIDDEN");
        return account;
    }

    AccountSelf self(Principal principal){
        return transaction(()->{
            var a=actor(principal,null,true);
            return new AccountSelf(a.id(),a.username(),true,db.roles(a.id()),db.functions(a.id()));
        });
    }

    State state(UUID id){
        return transaction(()->{
            var a=db.account(id,true);
            if(a==null) throw new AuthFailure(404,"NOT_FOUND");
            return new State(id,a.active(),Long.toString(a.version()),a.active()&&db.functions(id).stream().anyMatch(f->f.code().equals("ctsv.access")));
        });
    }

    @Override public State requireCtsvAccess(Principal principal,UUID mappedAccountId){
        return requireAccountAccess(principal,mappedAccountId,true);
    }

    @Override public String accountLabel(Principal principal){
        return transaction(()->{var account=db.account(principal.accountId(),false);return account==null?principal.accountId().toString():account.username();});
    }

    @Override public State requireAcceptedWorkAccess(Principal accepted,UUID mappedAccountId){
        return requireAccountAccess(accepted,mappedAccountId,false);
    }

    private State requireAccountAccess(Principal principal,UUID mappedAccountId,boolean requireSessionVersion){
        if(!principal.accountId().equals(mappedAccountId)) throw AuthFailure.unavailable();
        try {
            State state=state(mappedAccountId);
            if(!state.active() || requireSessionVersion&&!state.tokenVersion().equals(Long.toString(principal.tokenVersion())))
                throw new AuthFailure(401,"TOKEN_REVOKED");
            if(!state.ctsvAccess()) throw new AuthFailure(403,"FORBIDDEN");
            return state;
        } catch (AuthFailure e){
            if(e.status==404) throw new AuthFailure(401,"TOKEN_REVOKED");
            throw e;
        } catch(org.springframework.dao.DataAccessException | TransactionException e){throw AuthFailure.unavailable();}
    }

    private static byte[] hashCookie(String raw){
        if(raw==null || !raw.matches("[A-Za-z0-9_-]{43}")) return null;
        try {
            byte[] bytes=Base64.getUrlDecoder().decode(raw);
            if(!raw.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))) return null;
            return sha256(bytes);
        } catch(IllegalArgumentException e){return null;}
    }
    private static byte[] sha256(byte[] bytes){
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private static AuthFailure loginFailed(){return new AuthFailure(401,"AUTHENTICATION_FAILED");}
    private static AuthFailure invalidRefresh(){return new AuthFailure(401,"REFRESH_INVALID");}
}
