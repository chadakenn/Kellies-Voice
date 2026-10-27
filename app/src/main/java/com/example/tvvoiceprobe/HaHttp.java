package com.example.tvvoiceprobe;

import android.content.Context;

import java.io.IOException;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

/**
 * Shared Home Assistant client. Cleartext is refused except on a private
 * host. A certificate Android does not trust can be accepted once, for that
 * host only, after the user confirms its fingerprint.
 */
final class HaHttp {
    private HaHttp() { }

    static OkHttpClient client(Context context, int connectTimeoutSeconds) {
        HaCertificatePin.attach(context);
        X509TrustManager system = systemTrust();
        PinnedTrust trust = new PinnedTrust(system);
        try {
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[] { trust }, null);
            return new OkHttpClient.Builder()
                    .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                    .pingInterval(25, TimeUnit.SECONDS)
                    .sslSocketFactory(ssl.getSocketFactory(), trust)
                    .hostnameVerifier((hostname, session) -> pinnedOrDefault(hostname, session))
                    .addInterceptor(chain -> {
                        HttpUrl url = chain.request().url();
                        if ("http".equals(url.scheme()) && !LanAddress.cleartextAllowed(url.host())) {
                            throw new IOException("Cleartext is only allowed on a private network");
                        }
                        return chain.proceed(chain.request());
                    })
                    .build();
        } catch (Exception error) {
            throw new IllegalStateException("Couldn't prepare the Home Assistant connection");
        }
    }

    static boolean untrusted(Throwable error) {
        while (error != null) {
            if (error instanceof CertificateException) return true;
            error = error.getCause();
        }
        return false;
    }

    private static boolean pinnedOrDefault(String hostname, SSLSession session) {
        if (HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)) return true;
        try {
            java.security.cert.Certificate[] chain = session.getPeerCertificates();
            if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) return false;
            String fingerprint = HaCertificatePin.fingerprint((X509Certificate) chain[0]);
            return !fingerprint.isEmpty() && fingerprint.equals(HaCertificatePin.pinFor(hostname));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static X509TrustManager systemTrust() {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
            }
        } catch (Exception ignored) { }
        throw new IllegalStateException("No system trust manager");
    }

    private static final class PinnedTrust extends X509ExtendedTrustManager {
        private final X509TrustManager system;

        PinnedTrust(X509TrustManager system) { this.system = system; }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {
            throw new UnsupportedOperationException("client certificates are not used");
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
            throw new UnsupportedOperationException("client certificates are not used");
        }

        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            throw new UnsupportedOperationException("client certificates are not used");
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (CertificateException rejected) {
                acceptOrOffer(chain, null, rejected);
            }
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            try {
                if (system instanceof X509ExtendedTrustManager) {
                    ((X509ExtendedTrustManager) system).checkServerTrusted(chain, authType, socket);
                } else {
                    system.checkServerTrusted(chain, authType);
                }
            } catch (CertificateException rejected) {
                String host = null;
                if (socket instanceof javax.net.ssl.SSLSocket) {
                    SSLSession session = ((javax.net.ssl.SSLSocket) socket).getHandshakeSession();
                    if (session != null) host = session.getPeerHost();
                }
                acceptOrOffer(chain, host, rejected);
            }
        }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            try {
                if (system instanceof X509ExtendedTrustManager) {
                    ((X509ExtendedTrustManager) system).checkServerTrusted(chain, authType, engine);
                } else {
                    system.checkServerTrusted(chain, authType);
                }
            } catch (CertificateException rejected) {
                acceptOrOffer(chain, engine == null ? null : engine.getPeerHost(), rejected);
            }
        }

        @Override public X509Certificate[] getAcceptedIssuers() {
            return system.getAcceptedIssuers();
        }

        private void acceptOrOffer(X509Certificate[] chain, String host, CertificateException rejected)
                throws CertificateException {
            if (chain != null && chain.length > 0 && host != null) {
                String fingerprint = HaCertificatePin.fingerprint(chain[0]);
                if (!fingerprint.isEmpty() && fingerprint.equals(HaCertificatePin.pinFor(host))) return;
                HaCertificatePin.offer(host, fingerprint);
            }
            throw rejected;
        }
    }
}
