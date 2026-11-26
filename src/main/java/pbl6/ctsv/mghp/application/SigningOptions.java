package pbl6.ctsv.mghp.application;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Deployment-owned mapping. A TEST CA identity is never a local capability grant. */
@Component @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningOptions {
 record Signer(long user,String role,String email,String authority){}
 record Plan(String mode,long provider,UUID category,List<Signer> signers){}
 final Environment env;
 SigningOptions(Environment env){this.env=env;}
 String value(String key){String v=env.getProperty("ctsv.mghp.signing."+key);if(v==null||v.isBlank())throw new IntakeProblem(503,"SIGNING_CONFIGURATION_REQUIRED","request");return v.strip();}
 Plan plan(boolean minutes){
  String mode=value("mode");if(!Set.of("TEST","PRODUCTION").contains(mode))throw new IntakeProblem(503,"SIGNING_CONFIGURATION_REQUIRED","request");
  try{
   var first=signer(minutes?"minutes-first":"decision-first",minutes?"chuyen_vien":"pho_phong");var last=signer("last","pho_hieu_truong");
   if(first.user()==last.user()||first.email().equals(last.email()))throw new IllegalArgumentException();
   return new Plan(mode,Long.parseLong(value("provider-id")),UUID.fromString(value("category-id")),List.of(first,last));
  }catch(IllegalArgumentException e){throw new IntakeProblem(503,"SIGNING_CONFIGURATION_REQUIRED","request");}
 }
 Signer signer(String key,String role){long user=Long.parseLong(value(key+".user-id"));String email=pbl6.ctsv.shared.ca.CaDutClient.email(value(key+".email"));if(user<1)throw new IllegalArgumentException();return new Signer(user,role,email,value(key+".authority-reference"));}
 Map<String,List<String>> monitoredSigners(){
  var grouped=new LinkedHashMap<String,List<String>>();
  for(boolean minutes:List.of(true,false))for(var signer:plan(minutes).signers()){
   var uses=grouped.computeIfAbsent(signer.email(),key->new ArrayList<>());if(!uses.contains(signer.role()))uses.add(signer.role());
  }
  return grouped;
 }
}
