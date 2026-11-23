package pbl6.ctsv.mghp.application;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import pbl6.ctsv.shared.ca.*;
import pbl6.ctsv.shared.ca.CaProviderDtos.*;

@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningProviderConfiguration {
 @Bean @ConditionalOnMissingBean(SigningProvider.class)
 SigningProvider signingProvider(ObjectProvider<CaDutClient> clients){return new SigningProvider(){
  CaDutClient client(){var c=clients.getIfAvailable();if(c==null)throw new CaFailure("CA_NOT_CONFIGURED");return c;}
  public Certificates certificates(String email){return client().certificates(email);}
  public Uploaded upload(CaPdfPreflight.Input in,String title,UUID category){return client().upload(in,title,category);}
  public Receipt sign(UUID doc,UUID cert,String email,String name,CaPdfPreflight.Box box){return client().sign(doc,cert,email,name,box);}
  public byte[] download(UUID doc){return client().download(doc);}
 };}
}
