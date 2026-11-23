package pbl6.ctsv.mghp.application;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.shared.ca.*;
import pbl6.ctsv.shared.ca.CaProviderDtos.Receipt;
import pbl6.ctsv.shared.files.ManagedFiles;

@Component @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningArtifacts {
 final ManagedFiles files;
 SigningArtifacts(ManagedFiles files){this.files=files;}
 ManagedFiles.Info store(UUID operation,long actor,byte[] input,byte[] output,Receipt receipt,String name){
  if(!CaPdfPreflight.sha256(input).equals(receipt.documentHash())||!CaPdfPreflight.sha256(output).equals(receipt.signedPdfHash()))throw new CaFailure("CA_SIGNED_HASH_MISMATCH");
  if(output.length>CaPdfPreflight.MAX_BYTES)throw new CaFailure("PDF_TOO_LARGE");
  if(output.length<=input.length||!Arrays.equals(input,Arrays.copyOf(output,input.length)))throw new CaFailure("CA_REVISION_MISMATCH");
  var before=CaPdfSignatureVerifier.verify(input);var after=CaPdfSignatureVerifier.verify(output);
  if(!before.signatures().isEmpty()&&!before.cryptoVerified()||!after.cryptoVerified()||after.signatures().size()!=before.signatures().size()+1||!after.signatures().subList(0,before.signatures().size()).equals(before.signatures()))throw new CaFailure("CA_CRYPTO_NOT_VERIFIED");
  return files.store(operation,actor,output,name);
 }
}
