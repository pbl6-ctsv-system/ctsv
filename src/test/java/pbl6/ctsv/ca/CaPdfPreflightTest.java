package pbl6.ctsv.ca;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import pbl6.ctsv.shared.ca.*;
import static org.junit.jupiter.api.Assertions.*;

class CaPdfPreflightTest {
    static byte[] pdf() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage(PDRectangle.A4)); doc.save(out); return out.toByteArray();
        }
    }
    static byte[] ownerPdf() throws Exception {
        try(var stream=CaPdfPreflightTest.class.getResourceAsStream("/ca/file-ky-thu.pdf")) {
            assertNotNull(stream,"Owner-approved synthetic PDF fixture must be packaged");return stream.readAllBytes();
        }
    }
    @Test void suppliedPdfHasExpectedBytesAndDefaultGeometry() throws Exception {
        var bytes = ownerPdf();
        var input = CaPdfPreflight.inspect(bytes, new CaPdfPreflight.Box(1,100,100,200,60));
        assertEquals(32124, input.bytes().length);
        assertEquals("d8d6256816ad520de5fbcbe14359d14c163f503ab93cdbe62234a0ba06798f38", input.sha256());
        // Source inspection found one existing signature; never rewrite the owner's PDF fixture.
        assertTrue(input.pages() > 0); assertEquals(1,input.existingSignatures());
    }
    @Test void rejectsHtmlAndTruncatedPdf() {
        assertThrows(CaFailure.class, () -> CaPdfPreflight.inspect("<html/>".getBytes(), new CaPdfPreflight.Box(1,0,0,20,20)));
        assertThrows(CaFailure.class, () -> CaPdfPreflight.inspect("%PDF-1.7\ntruncated".getBytes(), new CaPdfPreflight.Box(1,0,0,20,20)));
    }
    @Test void rejectsInvalidGeometry() throws Exception {
        var bytes = pdf();
        for (var box : new CaPdfPreflight.Box[]{new CaPdfPreflight.Box(2,0,0,20,20),new CaPdfPreflight.Box(1,-1,0,20,20),new CaPdfPreflight.Box(1,0,0,999,20),new CaPdfPreflight.Box(1,0,0,0,20),new CaPdfPreflight.Box(1,Double.NaN,0,20,20)})
            assertThrows(CaFailure.class, () -> CaPdfPreflight.inspect(bytes,box));
    }
    @Test void rejectsEncryptedPdfEvenWithEmptyUserPassword() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage()); doc.protect(new StandardProtectionPolicy("owner","",new AccessPermission()));doc.save(out);
            assertThrows(CaFailure.class,()->CaPdfPreflight.inspect(out.toByteArray(),new CaPdfPreflight.Box(1,100,100,200,60)));
        }
    }
    @Test void rejectsOverProjectLimitBeforeParsing() {
        var failure = assertThrows(CaFailure.class,()->CaPdfPreflight.inspect(new byte[10_000_001],new CaPdfPreflight.Box(1,0,0,1,1)));
        assertEquals("PDF_TOO_LARGE",failure.code());
    }
    @Test void handlesCropOffsetAndRotationInPdfUserCoordinates() throws Exception {
        try (var doc = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage();page.setCropBox(new PDRectangle(40,50,300,400));page.setRotation(90);doc.addPage(page);doc.save(out);
            var input = CaPdfPreflight.inspect(out.toByteArray(),new CaPdfPreflight.Box(1,50,60,200,60));
            assertEquals(90,input.rotation());
            assertThrows(CaFailure.class,()->CaPdfPreflight.inspect(out.toByteArray(),new CaPdfPreflight.Box(1,0,0,20,20)));
        }
    }
    @Test void inputBytesAreImmutable() throws Exception {
        var bytes=pdf(); var input=CaPdfPreflight.inspect(bytes,new CaPdfPreflight.Box(1,0,0,20,20));
        bytes[0]=0;var copy=input.bytes();copy[0]=0;assertEquals('%',input.bytes()[0]);
    }
}
