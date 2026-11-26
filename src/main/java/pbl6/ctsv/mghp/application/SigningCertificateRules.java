package pbl6.ctsv.mghp.application;
import java.time.*;
import pbl6.ctsv.shared.ca.CaProviderDtos.*;

final class SigningCertificateRules {
 private SigningCertificateRules(){}
 record Observation(String state,Certificate selected){}
 static Observation observe(Certificates certificates,Instant now){
  var active=certificates.items().stream().filter(c->"active".equals(c.status())).toList();
  if(active.isEmpty())return new Observation("not_found",null);
  var usable=active.stream().filter(c->c.valid()&&c.hasPrivateKey()&&c.validFrom()!=null&&c.validTo()!=null&&!now.isBefore(c.validFrom())&&now.isBefore(c.validTo())).toList();
  if(usable.size()>1||usable.isEmpty()&&active.size()>1)return new Observation("ambiguous",null);
  var selected=usable.isEmpty()?active.getFirst():usable.getFirst();
  if(selected.validTo()!=null&&!now.isBefore(selected.validTo()))return new Observation("expired",selected);
  if(usable.isEmpty())return new Observation("not_ready",selected);
  return new Observation(Duration.between(now,selected.validTo()).compareTo(Duration.ofDays(10))<0?"soon":"valid",selected);
 }
}
