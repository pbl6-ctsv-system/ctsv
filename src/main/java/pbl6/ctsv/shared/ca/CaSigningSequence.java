package pbl6.ctsv.shared.ca;

import java.io.IOException;
import java.time.Clock;
import java.util.*;
import static pbl6.ctsv.shared.ca.CaProviderDtos.*;

/** Technical spike sequence. P11 must supply durable journal, approved authority and PKI trust policy. */
public final class CaSigningSequence {
    public record Step(CaSignerBinding.Signer signer,CaPdfPreflight.Box box) {}
    public record Event(String type,int step,UUID document,Receipt receipt,String hash,Uploaded upload) {
        public Event(String type,int step,UUID document,Receipt receipt,String hash) {this(type,step,document,receipt,hash,null);}
    }
    @FunctionalInterface public interface Journal {
        void record(Event event) throws IOException;
        default void artifact(int step,byte[] bytes) throws IOException {}
    }
    @FunctionalInterface public interface ConsentProvider {
        CaSignerBinding.Consent consent(CaSignerBinding.Signer signer,String inputHash,int step);
    }
    public record StepEvidence(int step,Receipt receipt,boolean caReportedSuccess,boolean pdfRetrieved,
                               boolean hashVerified,boolean cryptoVerified,String trustStatus) {}
    public record Result(UUID document,String status,List<StepEvidence> steps,String failureCode) {
        public Result {steps=List.copyOf(steps);}
    }
    private final CaDutClient client;
    private final Clock clock;
    public CaSigningSequence(CaDutClient client,Clock clock) {this.client=client;this.clock=clock;}

    public Result run(CaPdfPreflight.Input input,String title,UUID category,List<Step> steps,ConsentProvider authority,Journal journal) {
        var evidence=new ArrayList<StepEvidence>();UUID document=null;boolean sideEffect=false;
        try {
            if(steps==null || steps.isEmpty() || steps.size()>2 || authority==null || journal==null)throw new CaFailure("CA_PLAN_INVALID");
            steps=List.copyOf(steps);
            var checked=CaPdfPreflight.inspect(input.bytes(),steps.getFirst().box());
            if(!checked.sha256().equals(input.sha256()))throw new CaFailure("CA_INPUT_METADATA_INVALID");
            input=checked;
            CaSignerBinding.requireDistinct(steps.stream().map(Step::signer).toList());
            for(var step:steps) {CaPdfPreflight.inspect(input.bytes(),step.box());recheck(step.signer());}
            if(steps.size()==2 && overlap(steps.get(0).box(),steps.get(1).box()))throw new CaFailure("CA_GEOMETRY_OVERLAP");
            CaSignerBinding.requireConsent(authority.consent(steps.getFirst().signer(),input.sha256(),1),steps.getFirst().signer(),input.sha256(),1);
            var prior=CaPdfSignatureVerifier.verify(input.bytes());
            if(input.existingSignatures()>0 && !prior.cryptoVerified())throw new CaFailure("CA_SOURCE_SIGNATURE_INVALID");
            journal.record(new Event("UPLOAD_DISPATCHED",0,null,null,input.sha256()));sideEffect=true;
            var upload=client.upload(input,title,category);document=upload.id();
            journal.record(new Event("UPLOAD_RECEIVED",0,document,null,input.sha256(),upload));
            var current=input;
            for(int index=0;index<steps.size();index++) {
                int number=index+1;var step=steps.get(index);var signer=recheck(step.signer());
                CaSignerBinding.requireConsent(authority.consent(signer,current.sha256(),number),signer,current.sha256(),number);
                CaPdfPreflight.inspect(current.bytes(),step.box());
                journal.record(new Event("SIGN_DISPATCHED",number,document,null,current.sha256()));
                var receipt=client.sign(document,signer.certificate().id(),signer.email(),signer.name(),signer.template(),step.box());
                journal.record(new Event("SIGN_RECEIVED",number,document,receipt,current.sha256()));
                boolean correlated=document.equals(receipt.document()) && signer.certificate().id().equals(receipt.certificate())
                        && signer.certificate().serialNumber().equals(receipt.certificateSerial())
                        && signer.email().equals(receipt.signerEmail()) && signer.name().equals(receipt.signerName())
                        && current.sha256().equals(receipt.documentHash()) && "valid".equals(receipt.status());
                evidence.add(new StepEvidence(number,receipt,correlated,false,false,false,"NOT_VERIFIED"));
                if(!correlated)throw new CaFailure("CA_RECEIPT_MISMATCH",201,true);
                var bytes=client.download(document);var next=CaPdfPreflight.inspect(bytes,step.box());
                evidence.set(index,new StepEvidence(number,receipt,true,true,false,false,"NOT_VERIFIED"));
                if(!receipt.signedPdfHash().equals(next.sha256()))throw new CaFailure("CA_SIGNED_HASH_MISMATCH");
                evidence.set(index,new StepEvidence(number,receipt,true,true,true,false,"NOT_VERIFIED"));
                var previousBytes=current.bytes();
                if(bytes.length<=previousBytes.length || !Arrays.equals(previousBytes,Arrays.copyOf(bytes,previousBytes.length)))throw new CaFailure("CA_REVISION_MISMATCH");
                var verified=CaPdfSignatureVerifier.verify(bytes);
                if(!verified.cryptoVerified() || verified.signatures().size()!=prior.signatures().size()+1
                        || !verified.signatures().subList(0,prior.signatures().size()).equals(prior.signatures()))
                    throw new CaFailure("CA_CRYPTO_NOT_VERIFIED");
                evidence.set(index,new StepEvidence(number,receipt,true,true,true,true,verified.trustStatus()));
                journal.artifact(number,bytes);
                // CA's serial_number is an opaque provider identifier, not necessarily X.509 hex.
                // Without a comparable X.509 serial/fingerprint, do not invent a cryptographic cert link.
                var signedCert=verified.signatures().getLast();
                if(signer.expectedFingerprint()==null || !signer.expectedFingerprint().equals(signedCert.fingerprint()))
                    throw new CaFailure("CA_CERTIFICATE_LINK_NOT_VERIFIED");
                journal.record(new Event("STEP_VERIFIED",number,document,receipt,next.sha256()));
                current=next;prior=verified;
            }
            return new Result(document,"COMPLETED",evidence,null);
        }catch(CaFailure f){return new Result(document==null?f.document():document,f.uncertain()?"UNKNOWN":evidence.isEmpty()?"FAILED":"PARTIAL",evidence,f.code());}
        catch(IOException e){return new Result(document,sideEffect?"UNKNOWN":"FAILED",evidence,"CA_EVIDENCE_WRITE_FAILED");}
    }
    private CaSignerBinding.Signer recheck(CaSignerBinding.Signer signer) {
        var current=CaSignerBinding.bind(signer.email(),signer.certificate().user(),signer.certificate().id(),signer.template(),client.certificates(signer.email()),client.templates(signer.email()),clock.instant());
        if(!current.certificate().serialNumber().equals(signer.certificate().serialNumber()) || !current.name().equals(signer.name()))throw new CaFailure("CA_BINDING_CHANGED");
        return new CaSignerBinding.Signer(current.email(),current.name(),current.certificate(),current.template(),signer.expectedFingerprint());
    }
    private static boolean overlap(CaPdfPreflight.Box a,CaPdfPreflight.Box b) {
        return a.page()==b.page() && a.x()<b.x()+b.width() && b.x()<a.x()+a.width() && a.y()<b.y()+b.height() && b.y()<a.y()+a.height();
    }
}
