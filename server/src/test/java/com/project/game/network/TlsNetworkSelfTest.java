package com.project.game.network;

import com.project.game.network.codec.LegacyCipher;
import com.project.game.network.codec.LegacyPacketCodec;
import com.project.game.network.message.Message;
import com.project.game.network.message.MessageName;
import com.project.game.network.message.MessageReader;
import com.project.game.network.transport.TlsContextFactory;
import com.project.game.network.transport.TlsTcpTransport;
import com.project.game.service.AuthService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** N13 executable smoke test for TLS plus the unchanged legacy packet protocol. */
public final class TlsNetworkSelfTest {
    private static final byte[] KEY = "abc".getBytes(StandardCharsets.US_ASCII);
    private static final char[] STORE_PASSWORD = "self-test-password".toCharArray();

    private TlsNetworkSelfTest() {
    }

    public static void main(String[] args) throws Exception {
        Path keystore = createTemporaryKeystore();
        NetworkServer server = null;
        Thread serverThread = null;
        try {
            SSLContext serverContext = TlsContextFactory.fromKeyStore(
                    keystore, "PKCS12", STORE_PASSWORD, "TLSv1.3");
            int port = findFreePort();
            server = new NetworkServer("127.0.0.1", port, 20, 65535, 256, 5000,
                    KEY, new AuthService(), serverContext);
            NetworkServer runningServer = server;
            AtomicReference<Throwable> serverFailure = new AtomicReference<>();
            serverThread = Thread.ofVirtual().name("tls-network-self-test-server").start(() -> {
                try {
                    runningServer.start();
                } catch (Throwable failure) {
                    serverFailure.set(failure);
                }
            });

            SSLContext clientContext = trustAllClientContext();
            try (TlsTcpTransport client = connectWithRetry(clientContext, port)) {
                LegacyPacketCodec codec = new LegacyPacketCodec(65535);
                codec.writeClient(client.output(), null, false, new Message(MessageName.CONNECT_SERVER));
                Message handshake = codec.read(client.input(), null, false);
                byte[] key = reconstructKey(handshake);
                if (!java.util.Arrays.equals(KEY, key)) {
                    throw new AssertionError("unexpected TLS server handshake key");
                }
                LegacyCipher cipher = new LegacyCipher(key);
                Message version = codec.readServerResponse(client.input(), cipher, true);
                MessageReader reader = version.reader();
                if (version.command() != MessageName.VERSION_SOURCE
                        || !"0.9.5".equals(reader.readUtf())) {
                    throw new AssertionError("unexpected TLS server version response");
                }
            }
            if (serverFailure.get() != null) {
                throw new AssertionError("TLS network server failed", serverFailure.get());
            }
            System.out.println("TlsNetworkSelfTest: PASS");
        } finally {
            if (server != null) {
                server.stop();
            }
            if (serverThread != null) {
                serverThread.join(10_000);
            }
            Files.deleteIfExists(keystore);
        }
    }

    private static TlsTcpTransport connectWithRetry(SSLContext context, int port) throws Exception {
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                return TlsTcpTransport.connect("127.0.0.1", port, context, 500);
            } catch (IOException failure) {
                lastFailure = failure;
                Thread.sleep(100);
            }
        }
        throw new IOException("TLS network server did not start", lastFailure);
    }

    private static byte[] reconstructKey(Message message) throws IOException {
        if (message.command() != MessageName.SEND_SESSION_KEY) {
            throw new IOException("expected session key, got " + message.command());
        }
        MessageReader reader = message.reader();
        int length = reader.readUnsignedByte();
        byte[] cumulative = reader.readBytes(length);
        byte[] key = new byte[length];
        if (length > 0) {
            key[0] = cumulative[0];
            for (int i = 1; i < length; i++) {
                key[i] = (byte) (Byte.toUnsignedInt(cumulative[i]) ^ Byte.toUnsignedInt(key[i - 1]));
            }
        }
        return key;
    }

    private static int findFreePort() throws IOException {
        try (var socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static Path createTemporaryKeystore() throws IOException, InterruptedException {
        Path keystore = Files.createTempFile("vutrurongthan-tls-network-", ".p12");
        Files.deleteIfExists(keystore);
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(List.of(
                keytool, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-storetype", "PKCS12", "-keystore", keystore.toString(),
                "-storepass", new String(STORE_PASSWORD), "-keypass", new String(STORE_PASSWORD),
                "-dname", "CN=localhost", "-noprompt"))
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
