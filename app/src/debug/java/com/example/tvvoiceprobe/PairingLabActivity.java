package com.example.tvvoiceprobe;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DEBUG-BUILD TV QA only: safe local crypto self-test and an explicit entry
 * to the existing two-screen LAN discovery preview. No production launcher,
 * live OAuth, HA token, credential store, microphone, or background service.
 */
public final class PairingLabActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean testing = new AtomicBoolean(false);
    private TextView output;
    private Button testButton;
    private final int textColor = 0xfff4e9d8;

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout layout = new LinearLayout(this);
        layout.setBackgroundColor(0xff181512);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(44), dp(24), dp(44), dp(24));
        scroll.addView(layout);

        layout.addView(text("KELLIE'S VOICE | PAIRING LAB", 25, 0xffecb991));
        layout.addView(text("DEBUG BUILD • TEST-ONLY • NO LOGIN CHANGES", 17, 0xff90deb2));
        layout.addView(text(
                "This screen lets you test secure pairing encryption on the TV, " +
                "without using Home Assistant credentials. Automatic OAuth pairing " +
                "is NOT connected yet. The current Kellie's Voice app and its " +
                "saved login are untouched.", 17, textColor));

        testButton = new Button(this);
        testButton.setText("RUN LOCAL SECURE PAIRING SELF-TEST");
        testButton.setOnClickListener(v -> selfTest());
        layout.addView(testButton);

        Button discovery = new Button(this);
        discovery.setText("OPEN LAN TWO-SCREEN CODE PREVIEW");
        discovery.setOnClickListener(v ->
                startActivity(new Intent(this, PairingSetupActivity.class)));
        layout.addView(discovery);

        Button exit = new Button(this);
        exit.setText("CLOSE PAIRING LAB");
        exit.setOnClickListener(v -> finish());
        layout.addView(exit);

        output = text("Ready. Select RUN to test local P-256 key agreement, " +
                "AES-GCM encryption, receipts, replay rejection and credential binding.", 17,
                0xffecb991);
        layout.addView(output);
        setContentView(scroll);
        testButton.requestFocus();
    }

    private void selfTest() {
        if (!testing.compareAndSet(false, true)) return;
        testButton.setEnabled(false);
        output.setText("Running local encrypted-handoff checks...");
        new Thread(() -> {
            String result;
            try {
                result = runChecks();
            } catch (Exception error) {
                // Do not render the exception or any raw security material.
                result = "FAIL: One or more cryptographic checks rejected the flow.\n" +
                        "Do not use this APK for credential pairing.";
            }
            String finalResult = result;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                output.setText(finalResult);
                testButton.setEnabled(true);
                testing.set(false);
            });
        }, "KVPairingLab").start();
    }

    private static String publicHash(String session, byte[] tvKey, byte[] haKey)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("KVPair/v1/transcript".getBytes(StandardCharsets.US_ASCII));
        part(digest, Base64.getUrlDecoder().decode(session));
        part(digest, tvKey);
        part(digest, haKey);
        byte[] bytes = digest.digest();
        StringBuilder out = new StringBuilder(64);
        for (byte item : bytes) out.append(String.format(Locale.US, "%02x", item & 255));
        return out.toString();
    }

    private static void part(MessageDigest digest, byte[] value) {
        digest.update((byte) (value.length >>> 8));
        digest.update((byte) value.length);
        digest.update(value);
    }

    private static JSONObject frame(String type, String session, String uuid,
                                    String hash, long expiry) throws Exception {
        return new JSONObject()
                .put("v", 1).put("type", type)
                .put("tv_uuid", uuid).put("session", session)
                .put("transcript", hash).put("expires_at", expiry);
    }

    private static String runChecks() throws Exception {
        StringBuilder status = new StringBuilder();
        final String uuid = "123e4567-e89b-42d3-a456-426614174000";
        final long now = System.currentTimeMillis() / 1000L;
        byte[] randomSession = new byte[16];
        new SecureRandom().nextBytes(randomSession);
        String session = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(randomSession);
        KeyPair tv = PairingVerificationCode.freshKeyPair();
        KeyPair ha = PairingVerificationCode.freshKeyPair();
        byte[] tvDer = tv.getPublic().getEncoded();
        byte[] haDer = ha.getPublic().getEncoded();

        String tvCode = PairingVerificationCode.calculate(
                tv, true, session, tvDer, haDer);
        String haCode = PairingVerificationCode.calculate(
                ha, false, session, tvDer, haDer);
        ensure(tvCode.equals(haCode) && tvCode.matches("[0-9]{8}"));
        status.append("PASS  1. Both P-256 peers derive the same eight-digit code\n");

        String tvApproval = PairingVerificationCode.approvedProof(
                tv, true, session, tvDer, haDer, uuid);
        String haApproval = PairingVerificationCode.approvedProof(
                ha, false, session, tvDer, haDer, uuid);
        ensure(tvApproval.equals(haApproval));
        String otherApproval = PairingVerificationCode.approvedProof(
                tv, true, session, tvDer, haDer,
                "223e4567-e89b-42d3-a456-426614174000");
        ensure(!tvApproval.equals(otherApproval));
        status.append("PASS  2. Physical-OK MAC binds the exact TV UUID\n");

        String transcript = publicHash(session, tvDer, haDer);
        long expiry = now + 110;
        byte[] synthetic = "SYNTHETIC NON-SECRET TV SELF TEST"
                .getBytes(StandardCharsets.US_ASCII);
        TvSealedHandoff.Clock clock = new TvSealedHandoff.Clock() {
            @Override public long unixSeconds() {
                return System.currentTimeMillis() / 1000L;
            }
            @Override public long elapsedMillis() {
                return SystemClock.elapsedRealtime();
            }
        };
        try (PairingSealedChannel haChannel =
                     new PairingSealedChannel(ha, false, session, tvDer, haDer);
             TvSealedHandoff receiver =
                     new TvSealedHandoff(tv, session, uuid, tvDer, haDer, clock)) {
            JSONObject offer = frame("credential_offer", session, uuid, transcript, expiry)
                    .put("candidate_b64", Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(synthetic));
            byte[] sealedOffer = haChannel.seal(
                    offer.toString().getBytes(StandardCharsets.US_ASCII));
            receiver.onSyntheticOffer(sealedOffer);
            ensure(receiver.stage() == TvSealedHandoff.Stage.WAIT_CAPABILITIES);
            status.append("PASS  3. TV decrypts authenticated HA-to-TV synthetic offer\n");

            // ONLY a simulated local capability state, never a real HA result.
            PairingAssistProofState proof = new PairingAssistProofState();
            ensure(proof.challenge());
            ensure(proof.authenticated());
            ensure(proof.pipelineResult(
                    PairingAssistProofState.PIPELINES_REQUEST_ID, true, true));
            ensure(proof.scopedAlertResult(
                    PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));
            JSONObject tvReceipt = new JSONObject(new String(
                    haChannel.open(receiver.syntheticTvProof(proof)),
                    StandardCharsets.US_ASCII));
            ensure(tvReceipt.getBoolean("assist_ok")
                    && tvReceipt.getBoolean("alerts_ok")
                    && uuid.equals(tvReceipt.getString("tv_uuid")));
            status.append("PASS  4. Synthetic Assist + TV-alert proof receipt authenticates\n");

            JSONObject commit = frame("ha_commit_authorization",
                    session, uuid, transcript, expiry)
                    .put("authorize_commit", true);
            byte[] sealedCommit = haChannel.seal(
                    commit.toString().getBytes(StandardCharsets.US_ASCII));
            final AtomicBoolean fakeWrite = new AtomicBoolean(false);
            byte[] persisted = receiver.onHaCommitForSynthetic(
                    sealedCommit, data -> {
                        if (!Arrays.equals(data, synthetic)) return false;
                        fakeWrite.set(true);
                        return true;
                    });
            ensure(fakeWrite.get());
            JSONObject tvPersisted = new JSONObject(new String(
                    haChannel.open(persisted), StandardCharsets.US_ASCII));
            ensure(tvPersisted.getBoolean("verified_store_committed")
                    && "tv_persisted_receipt".equals(tvPersisted.getString("type")));
            status.append("PASS  5. Authenticated commit + in-memory storage receipt\n");

            boolean replayRejected = false;
            try {
                haChannel.open(persisted);
            } catch (IllegalArgumentException expected) {
                replayRejected = true;
            }
            ensure(replayRejected);
            status.append("PASS  6. Replayed encrypted receipt rejected\n");
        }

        byte[] fingerprintA = PairingCredentialCandidateCodec.fingerprint(synthetic);
        byte[] fingerprintB = PairingCredentialCandidateCodec.fingerprint(
                "SYNTHETIC NON-SECRET DIFFERENT TV CANDIDATE"
                        .getBytes(StandardCharsets.US_ASCII));
        ensure(!MessageDigest.isEqual(fingerprintA, fingerprintB));
        status.append("PASS  7. Different candidate cannot share the binding hash\n");
        status.append("\nLOCAL SELF-TEST PASSED.\n\n");
        status.append("This did NOT authenticate to Home Assistant, transfer " +
                "a real token or change your saved login. For actual TV/HA " +
                "discovery, use OPEN LAN TWO-SCREEN CODE PREVIEW and confirm " +
                "only matching digits.");
        return status.toString();
    }

    private static void ensure(boolean good) {
        if (!good) throw new IllegalStateException("Pairing check failed");
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setPadding(0, 0, 0, dp(18));
        return view;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
