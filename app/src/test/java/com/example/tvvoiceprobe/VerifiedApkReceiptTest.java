package com.example.tvvoiceprobe;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Locale;

import static org.junit.Assert.*;

public class VerifiedApkReceiptTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private byte[] payload() {
        byte[] bytes = new byte[8192];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)(i % 251);
        return bytes;
    }

    private String hash(byte[] data) throws Exception {
        byte[] sum = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder result = new StringBuilder();
        for (byte b : sum) result.append(String.format(Locale.US, "%02x", b & 255));
        return result.toString();
    }

    private void write(File file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    @Test public void deferredInstallationRereadsReceiptAndRehashesCache() throws Exception {
        byte[] bytes = payload();
        File apk = folder.newFile("app-release.apk");
        File receiptFile = new File(folder.getRoot(), "app-release.verified");
        write(apk, bytes);
        VerifiedApkReceipt expected = new VerifiedApkReceipt(
                55, "0.16.15", hash(bytes), bytes.length, UpdateChannel.STABLE);
        expected.save(receiptFile);

        VerifiedApkReceipt restored = VerifiedApkReceipt.read(receiptFile);
        assertEquals(55, restored.code);
        assertEquals("0.16.15", restored.name);
        restored.verifyFile(apk, UpdateChannel.STABLE);
    }

    @Test public void corruptOrTruncatedCachedApkIsNeverAccepted() throws Exception {
        byte[] bytes = payload();
        File apk = folder.newFile("app-release.apk");
        write(apk, bytes);
        VerifiedApkReceipt expected = new VerifiedApkReceipt(
                55, "0.16.15", hash(bytes), bytes.length, UpdateChannel.STABLE);
        File receipt = new File(folder.getRoot(), "app-release.verified");
        expected.save(receipt);

        bytes[0] ^= 127; // same size, but a changed digest
        write(apk, bytes);
        reject(() -> VerifiedApkReceipt.read(receipt).verifyFile(apk, UpdateChannel.STABLE));

        byte[] shorter = new byte[5000];
        write(apk, shorter);
        reject(() -> VerifiedApkReceipt.read(receipt).verifyFile(apk, UpdateChannel.STABLE));
    }

    @Test public void changingUpdateChannelInvalidatesDeferredInstall() throws Exception {
        byte[] bytes = payload();
        File apk = folder.newFile("app-release.apk");
        write(apk, bytes);
        VerifiedApkReceipt receipt = new VerifiedApkReceipt(
                55, "0.16.15-dev.1", hash(bytes), bytes.length, UpdateChannel.DEVELOPMENT);
        receipt.verifyFile(apk, UpdateChannel.DEVELOPMENT);
        reject(() -> receipt.verifyFile(apk, UpdateChannel.STABLE));
    }

    @Test public void missingOrMalformedReceiptRequiresNewDownload() throws Exception {
        File receipt = new File(folder.getRoot(), "app-release.verified");
        reject(() -> VerifiedApkReceipt.read(receipt));
        write(receipt, new byte[]{1, 2, 3});
        reject(() -> VerifiedApkReceipt.read(receipt));
        reject(() -> new VerifiedApkReceipt(0, "0.16.15",
                "bad", 8192, UpdateChannel.STABLE));
    }

    private interface Dangerous {
        void run() throws Exception;
    }

    private static void reject(Dangerous action) throws Exception {
        try {
            action.run();
            fail("Unsafe update cache accepted");
        } catch (IllegalStateException expected) {
            // Expected: the installer must not be opened.
        }
    }
}
