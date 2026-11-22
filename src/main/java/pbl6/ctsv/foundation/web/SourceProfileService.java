package pbl6.ctsv.foundation.web;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.auth.AuthDirectoryAccess;
import pbl6.ctsv.foundation.repository.FoundationRepository;
@Service @ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
final class SourceProfileService {
 private final AuthDirectoryAccess directory;private final FoundationRepository repository;private final TransactionTemplate tx;
 SourceProfileService(AuthDirectoryAccess directory,FoundationRepository repository,PlatformTransactionManager manager){this.directory=directory;this.repository=repository;tx=new TransactionTemplate(manager);}
 Object list(AuthAccess.Principal actor,String type,String q,FoundationRules.PageInput p){
  var sources=directory.profileSources(actor);
  return tx.execute(s->repository.profileChoices(type,q,sources,p.page(),p.size(),Math.toIntExact(p.offset())));
 }
}
