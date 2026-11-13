package pbl6.ctsv.mghp.configuration;
import java.util.function.*;import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;import pbl6.ctsv.auth.AuthAccess;import pbl6.ctsv.foundation.api.FoundationAccess;
final class ConfigurationAccess {
 final TransactionTemplate tx;final FoundationAccess access;final ConfigurationRepository repo;
 ConfigurationAccess(PlatformTransactionManager manager,FoundationAccess access,ConfigurationRepository repo){tx=new TransactionTemplate(manager);this.access=access;this.repo=repo;}
 <T>T manager(AuthAccess.Principal principal,boolean write,Function<Long,T> action){return tx.execute(s->{long user=access.authorize(principal,true,"mghp_chuyen_vien").userId();if(write)repo.serializeWrites();return action.apply(user);});}
 <T>T reader(AuthAccess.Principal principal,BiFunction<Boolean,Long,T> action){boolean manager=tx.execute(s->access.authorize(principal,false,null).studentId()==null);return tx.execute(s->{var i=access.authorize(principal,manager,manager?"mghp_chuyen_vien":null);return action.apply(manager,i.userId());});}
}
