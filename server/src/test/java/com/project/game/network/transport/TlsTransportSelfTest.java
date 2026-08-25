package com.project.game.network.transport;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * N13 executable self-test for the TLS transport.
 *
 * <p>The certificate is generated in a temporary PKCS12 keystore and is never
 * stored in the repository. The client trust manager is intentionally unsafe
 * because this is only a local transport test.</p>
 */
public final class TlsTransportSelfTest {
    private static final char[] STORE_PASSWORD = "self-test-password".toCharArray();

    private TlsTransportSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path keystore = createTemporaryKeystore();
        try {
            SSLContext serverContext = TlsContextFactory.fromKeyStore(
                    keystore, "PKCS12", STORE_PASSWORD, "TLSv1.3");
            SSLContext clientContext = trustAllClientContext();
            runEcho(serverContext, clientContext);
            System.out.println("TlsTransportSelfTest: PASS");
        } finally {
            Files.deleteIfExists(keystore);
        }
    }

    private static void runEcho(SSLContext serverContext, SSLContext clientContext) throws Exception {
        try (var server = serverContext.getServerSocketFactory()
                .createServerSocket(0, 16, java.net.InetAddress.getLoopbackAddress())) {
            var sslServer = (javax.net.ssl.SSLServerSocket) server;
            AtomicReference<Throwable> serverFailure = new AtomicReference<>();
            Thread worker = Thread.ofVirtual().name("tls-self-test-server").start(() -> {
                try (TlsTcpTransport transport = TlsTcpTransport.accept(sslServer, 5000)) {
                    int value = transport.input().read();
                    if (value != 0x2A) {
                        throw new AssertionError("unexpected TLS payload: " + value);
                    }
                    transport.output().write(0x5A);
                    transport.output().flush();
                } catch (Throwable failure) {
                    serverFailure.set(failure);
                }
            });

            try (TlsTcpTransport client = TlsTcpTransport.connect(
                    "127.0.0.1", sslServer.getLocalPort(), clientContext, 5000)) {
                client.output().write(0x2A);
                client.output().flush();
                if (client.input().read() != 0x5A) {
                    throw new AssertionError("unexpected TLS response");
                }
            }
            worker.join();
            if (serverFailure.get() != null) {
                throw new AssertionError("TLS server transport failed", serverFailure.get());
            }
        }
    }

    private static Path createTemporaryKeystore() throws IOException, InterruptedException {
        Path keystore = Files.createTempFile("vutrurongthan-tls-", ".p12");
        Files.deleteIfExists(keystore);
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(List.of(
                keytool, "-genkeypair",
                "-alias", "server",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "1",
                "-storetype", "PKCS12",
                "-keystore", keystore.toString(),
                "-storepass", new String(STORE_PASSWORD),
                "-keypass", new String(STORE_PASSWORD),
                "-dname", "CN=localhost",
                "-noprompt"))
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IOException("keytool failed: " + output);
        }
        return keystore;
    }

    private static SSLContext trustAllClientContext() throws GeneralSecurityException {
        TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(null, new TrustManager[]{trustManager}, new SecureRandom());
        return context;
    }
}
