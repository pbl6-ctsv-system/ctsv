package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.*;
import org.bouncycastle.operator.jcajce.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import pbl6.ctsv.shared.ca.*;
import static org.junit.jupiter.api.Assertions.*;

public class CaPdfSignatureVerifierTest {
    public static byte[] signed(byte[] input,int serial) throws Exception {
        var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var pair=gen.generateKeyPair();
        var name=new X500Name("CN=Synthetic signer "+serial+",E=signer"+serial+"@example.test");
        var certificate=new JcaX509v3CertificateBuilder(name,BigInteger.valueOf(serial),new Date(0),new Date(4_000_000_000_000L),name,pair.getPublic()).build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate()));
        try(var doc=Loader.loadPDF(input);var out=new ByteArrayOutputStream()) {
            var sig=new PDSignature();sig.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);sig.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            doc.addSignature(sig,content->{try {
                var cms=new CMSSignedDataGenerator();cms.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate()),certificate));
                cms.addCertificate(certificate);return cms.generate(new CMSProcessableByteArray(content.readAllBytes()),false).getEncoded();
            }catch(Exception e){throw new IOException(e);}});
            doc.saveIncremental(out);return out.toByteArray();
        }
    }
    @Test void verifiesTwoRealCmsSignaturesAndPreservesFirstRevision() throws Exception {
        var first=signed(CaPdfPreflightTest.pdf(),101);var second=signed(first,202);
        var v=CaPdfSignatureVerifier.verify(second);
        assertTrue(v.cryptoVerified());assertEquals(2,v.signatures().size());assertEquals("NOT_VERIFIED",v.trustStatus());
        assertEquals(CaPdfSignatureVerifier.verify(first).signatures().getFirst().fingerprint(),v.signatures().getFirst().fingerprint());
        assertEquals("65",v.signatures().getFirst().serial());assertEquals("ca",v.signatures().getLast().serial());
    }
    @Test void rejectsTamperUnsignedPdfAndUnsignedAppend() throws Exception {
        var bytes=signed(CaPdfPreflightTest.pdf(),101);bytes[10]^=1;
        assertFalse(CaPdfSignatureVerifier.verify(bytes).cryptoVerified());
        assertFalse(CaPdfSignatureVerifier.verify(CaPdfPreflightTest.pdf()).cryptoVerified());
        var valid=signed(CaPdfPreflightTest.pdf(),101);var append=Arrays.copyOf(valid,valid.length+20);
        assertFalse(CaPdfSignatureVerifier.verify(append).cryptoVerified());
    }
    @Test void reportsOwnerPdfWithoutPretendingChainOrRevocationTrust() throws Exception {
        var v=CaPdfSignatureVerifier.verify(CaPdfPreflightTest.ownerPdf());
        assertEquals(1,v.signatures().size());assertEquals("NOT_VERIFIED",v.trustStatus());
    }
}
