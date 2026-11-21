package pbl6.ctsv.shared.ca;

import java.util.*;
import org.apache.pdfbox.Loader;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;

/** Offline CMS/ByteRange integrity only. Does NOT establish PKI trust, revocation or legal validity. */
public final class CaPdfSignatureVerifier {
    private CaPdfSignatureVerifier() {}
    public record Proof(String fingerprint,String serial,long revisionEnd,boolean cryptoValid) {}
    public record Verification(boolean cryptoVerified,List<Proof> signatures,String trustStatus) {
        public Verification { signatures=List.copyOf(signatures); }
    }
    @SuppressWarnings("unchecked")
    public static Verification verify(byte[] pdf) {
        var proofs=new ArrayList<Proof>();boolean valid=true;long latestEnd=0;
        if(pdf==null || pdf.length>CaPdfPreflight.MAX_BYTES)return new Verification(false,proofs,"NOT_VERIFIED");
        try(var doc=Loader.loadPDF(pdf)) {
            for(var sig:doc.getSignatureDictionaries()) {
                int[] range=sig.getByteRange();
                if(range==null || range.length!=4 || range[0]!=0 || range[1]<1 || range[2]<=range[1] || range[3]<0
                        || (long)range[2]+range[3]>pdf.length || pdf[range[1]]!='<' || pdf[range[2]-1]!='>')
                    return new Verification(false,proofs,"NOT_VERIFIED");
                long end=(long)range[2]+range[3];latestEnd=Math.max(end,latestEnd);
                for(int offset=range[1]+1;offset<range[2]-1;offset++) {
                    int ch=pdf[offset]&255;
                    if(!((ch>='0'&&ch<='9')||(ch>='a'&&ch<='f')||(ch>='A'&&ch<='F')||ch==0||ch==9||ch==10||ch==12||ch==13||ch==32))
                        return new Verification(false,proofs,"NOT_VERIFIED");
                }
                if(!Arrays.equals(sig.getContents(),sig.getContents(pdf)))return new Verification(false,proofs,"NOT_VERIFIED");
                if(!Set.of("adbe.pkcs7.detached","ETSI.CAdES.detached").contains(sig.getSubFilter()))return new Verification(false,proofs,"NOT_VERIFIED");
                var cms=new CMSSignedData(new CMSProcessableByteArray(sig.getSignedContent(pdf)),sig.getContents(pdf));
                var signers=cms.getSignerInfos().getSigners();
                if(signers.size()!=1)return new Verification(false,proofs,"NOT_VERIFIED");
                for(var signer:signers) {
                    var certs=cms.getCertificates().getMatches(signer.getSID());
                    if(certs.size()!=1)return new Verification(false,proofs,"NOT_VERIFIED");
                    var cert=(X509CertificateHolder)certs.iterator().next();
                    // SHA-2 family; no SHA-1/MD5 signature treated as verified.
                    boolean supported=Set.of("2.16.840.1.101.3.4.2.1","2.16.840.1.101.3.4.2.2","2.16.840.1.101.3.4.2.3").contains(signer.getDigestAlgOID());
                    boolean ok=supported && signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(cert));valid &= ok;
                    proofs.add(new Proof(CaPdfPreflight.sha256(cert.getEncoded()),cert.getSerialNumber().toString(16),end,ok));
                }
            }
            proofs.sort(Comparator.comparingLong(Proof::revisionEnd));
            for(int i=1;i<proofs.size();i++)if(proofs.get(i-1).revisionEnd()>=proofs.get(i).revisionEnd())valid=false;
            return new Verification(valid && !proofs.isEmpty() && latestEnd==pdf.length,proofs,"NOT_VERIFIED");
        } catch(Exception e) {return new Verification(false,proofs,"NOT_VERIFIED");}
    }
}
