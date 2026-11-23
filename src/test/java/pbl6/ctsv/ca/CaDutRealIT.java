package pbl6.ctsv.ca;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import pbl6.ctsv.shared.ca.*;
import static pbl6.ctsv.shared.ca.CaProviderDtos.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, synthetic PDF only. No SQL. Never dumps auth payloads or delivery URLs. */
class CaDutRealIT {
    static final String E="officer.e@dut.udn.vn", C="staff.c@dut.udn.vn";
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    static Path runDir() throws Exception {
        var allowed=Path.of(System.getenv("LOCALAPPDATA"),"CTSV","ca-dut").toRealPath();
        var path=Path.of(Objects.requireNonNull(System.getenv("CA_DUT_RUN_DIR"),"CA_DUT_RUN_DIR required")).toAbsolutePath().normalize();
        if(!path.startsWith(allowed) || path.equals(allowed))throw new IllegalStateException("Unsafe evidence directory");
        Files.createDirectories(path);if(!path.toRealPath().startsWith(allowed))throw new IllegalStateException("Unsafe evidence directory");return path;
    }
    static void save(Path path,Object value) throws Exception {
        var temp=Files.createTempFile(path.getParent(),"ca-",".tmp");
        Files.write(temp,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static CaSignerBinding.Signer lookup(CaDutClient client,String email,String alias) {
        var certs=client.certificates(email);
        var preferred=System.getenv("CA_DUT_SIGNER_"+alias+"_CERTIFICATE_ID");
        var now=Instant.now();var candidates=certs.items().stream().filter(c->"active".equals(c.status()) && c.valid() && c.hasPrivateKey() && !now.isBefore(c.validFrom()) && now.isBefore(c.validTo()))
                .filter(c->preferred==null || c.id().toString().equals(preferred)).toList();
        if(candidates.size()!=1)throw new CaFailure("CA_CERTIFICATE_SELECTION_REQUIRED");
        var chosen=candidates.getFirst();
        var bound=CaSignerBinding.bind(email,chosen.user(),chosen.id(),null,certs,new Templates(email,List.of()),now);
        return new CaSignerBinding.Signer(bound.email(),bound.name(),bound.certificate(),null,System.getenv("CA_DUT_SIGNER_"+alias+"_CERTIFICATE_SHA256"));
    }
    static UUID authorizedDocument(Receipt receipt,CaSigningSequence.Event upload,CaSigningSequence.Event dispatch,CaSigningSequence.Result result,String inputHash) {
        if(receipt==null || upload==null || dispatch==null || result==null || !"UPLOAD_RECEIVED".equals(upload.type()) || !"SIGN_DISPATCHED".equals(dispatch.type())
                || upload.document()==null || !upload.document().equals(dispatch.document()) || !upload.document().equals(receipt.document()) || !upload.document().equals(result.document())
                || !inputHash.equals(upload.hash()) || !inputHash.equals(dispatch.hash()) || !inputHash.equals(receipt.documentHash())
                || result.steps().size()!=1 || !result.steps().getFirst().caReportedSuccess() || !receipt.equals(result.steps().getFirst().receipt()))
            throw new CaFailure("CA_DOWNLOAD_SCOPE_NOT_VERIFIED");
        return upload.document();
    }
    @Test void realSpikeWithinExplicitMode() throws Exception {
        var mode=System.getenv("CA_DUT_REAL_MODE");
        Assumptions.assumeTrue(mode!=null,"CA REAL opt-in required; use tools/ca/Invoke-CaSpike.ps1");
        assertTrue(Set.of("READ_ONLY","SIGN_E_ONCE","VERIFY_DOWNLOAD").contains(mode),"Explicit supported mode required");
        var dir=runDir();
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("spike",Map.of("ca-dut.enabled","true")));
            context.register(CaDutConfiguration.class);context.refresh();var client=context.getBean(CaDutClient.class);
            var suppliedPath=System.getenv("CA_DUT_TEST_PDF");
            var pdf=suppliedPath==null?CaPdfPreflightTest.ownerPdf():Files.readAllBytes(Path.of(suppliedPath));
            var input=CaPdfPreflight.inspect(pdf,new CaPdfPreflight.Box(1,100,100,200,60));
            assertEquals("d8d6256816ad520de5fbcbe14359d14c163f503ab93cdbe62234a0ba06798f38",input.sha256());
            if(mode.equals("VERIFY_DOWNLOAD")) {
                var receipt=JSON.readValue(dir.resolve("receipt-e.json").toFile(),Receipt.class);
                var upload=JSON.readValue(dir.resolve("event-02-UPLOAD_RECEIVED.json").toFile(),CaSigningSequence.Event.class);
                var dispatch=JSON.readValue(dir.resolve("event-03-SIGN_DISPATCHED.json").toFile(),CaSigningSequence.Event.class);
                var result=JSON.readValue(dir.resolve("result.json").toFile(),CaSigningSequence.Result.class);
                var document=authorizedDocument(receipt,upload,dispatch,result,input.sha256());
                var bytes=client.download(document);var inspected=CaPdfPreflight.inspect(bytes,new CaPdfPreflight.Box(1,100,100,200,60));
                assertEquals(receipt.signedPdfHash(),inspected.sha256());var crypto=CaPdfSignatureVerifier.verify(bytes);
                save(dir.resolve("download-verification.json"),Map.of("at",Instant.now(),"pdfRetrieved",true,"hashVerified",true,"crypto",crypto));
                assertTrue(bytes.length>pdf.length && Arrays.equals(pdf,Arrays.copyOf(bytes,pdf.length)),"Source revision must be preserved");
                assertEquals(input.existingSignatures()+1,crypto.signatures().size());
                Files.write(dir.resolve("signed-e.pdf"),bytes);assertTrue(crypto.cryptoVerified());
                System.out.println("CA REAL download-only: scoped document, PDF/hash/CMS/source revision verified; no POST. Trust/exact certificate link remain separate.");return;
            }
            var categories=client.categories();var e=lookup(client,E,"E");var c=lookup(client,C,"C");
            CaSignerBinding.requireDistinct(List.of(e,c));
            if(mode.equals("READ_ONLY")){client.templates(E);client.templates(C);}
            save(dir.resolve("read-only.json"),Map.of("at",Instant.now(),"service","CA REAL","mode",mode,"input",Map.of("sha256",input.sha256(),"bytes",pdf.length,"pages",input.pages(),"existingSignatures",input.existingSignatures(),"rotation",input.rotation(),"cropWidth",input.cropWidth(),"cropHeight",input.cropHeight()),"categories",categories,"signers",List.of(e,c),"sourceCrypto",CaPdfSignatureVerifier.verify(pdf)));
            if(mode.equals("READ_ONLY")) {
                System.out.println("CA REAL read-only: PDF preflight, categories, two distinct certificates/templates verified. No upload/sign performed.");return;
            }
            // Fixed global guard across RunId values. Do not remove/recreate to bypass an unknown attempt.
            var intent=dir.getParent().resolve("p09-sign-e-once.intent.json");
            var lockChannel=java.nio.channels.FileChannel.open(dir.getParent().resolve("p09-execution.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
            try(lockChannel;var lock=lockChannel.tryLock()) {
            assertNotNull(lock,"Another CA spike owns the execution lock");
            if(Files.exists(intent)) {
                // Recovery of the observed local preflight defect ONLY; no event = no POST could dispatch.
                var old=JSON.readTree(dir.resolve("result.json").toFile());var oldIntent=JSON.readTree(intent.toFile());
                try(var names=Files.list(dir)) {
                    assertTrue(names.noneMatch(p->p.getFileName().toString().startsWith("event-")),"Existing external dispatch: never replay");
                }
                assertEquals("FAILED",old.path("status").asText());assertTrue(old.path("document").isNull());assertEquals(0,old.path("steps").size());
                assertEquals("CA_CERTIFICATE_SERIAL_INVALID",old.path("failureCode").asText());
                assertEquals(input.sha256(),oldIntent.path("inputHash").asText());assertEquals(dir.getFileName().toString(),oldIntent.path("runId").asText());
                save(dir.resolve("pre-dispatch-recovery.json"),Map.of("at",Instant.now(),"reason","Opaque serial preflight fixed; prior run sent zero POST"));
            } else Files.write(intent,JSON.writeValueAsBytes(Map.of("at",Instant.now(),"inputHash",input.sha256(),"runId",dir.getFileName().toString(),"scope","owner-20261119-one-signature-E")),StandardOpenOption.CREATE_NEW);
            var category=categories.stream().filter(cat->"quyet_dinh".equals(cat.code())).toList();
            assertEquals(1,category.size(),"Explicit category selection required");
            var journal=new CaSigningSequence.Journal() {
                int eventIndex;
                public void record(CaSigningSequence.Event event) throws java.io.IOException {
                    try {
                        save(dir.resolve(String.format("event-%02d-%s.json",++eventIndex,event.type())),event);
                        if(event.type().equals("SIGN_RECEIVED"))save(dir.resolve("receipt-e.json"),event.receipt());
                    }catch(Exception failure){throw new java.io.IOException("Evidence write failed");}
                }
                public void artifact(int step,byte[] bytes) throws java.io.IOException {Files.write(dir.resolve("signed-e.pdf"),bytes,StandardOpenOption.CREATE_NEW);}
            };
            var result=new CaSigningSequence(client,java.time.Clock.systemUTC()).run(input,"TEST CTSV P09 — ký thử kỹ thuật, không hiệu lực",category.getFirst().id(),
                List.of(new CaSigningSequence.Step(e,new CaPdfPreflight.Box(1,100,100,200,60))),
                (signer,hash,step)->new CaSignerBinding.Consent("owner-20261119-one-signature-E",signer.email(),signer.certificate().id(),hash,step),journal);
            save(dir.resolve("result.json"),result);
            System.out.println("CA REAL one-signer result: "+result.status()+"; diagnostic="+result.failureCode()+". No signer C dispatched.");
            assertEquals("COMPLETED",result.status(),"Real CA PDF retrieval/hash/crypto must actually succeed; inspect safe evidence for provider limitation");
            }
        } catch(CaFailure failure) {
            save(dir.resolve("safe-error.json"),Map.of("at",Instant.now(),"phase",mode,"code",failure.code(),"httpStatus",failure.httpStatus(),"uncertain",failure.uncertain()));throw failure;
        }
    }
}
