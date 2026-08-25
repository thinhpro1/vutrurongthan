package com.project.game.network.transport;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

/** Builds the server TLS context from an external keystore configuration. */
public final class TlsContextFactory {
    public static final String DEFAULT_PROTOCOL = "TLSv1.3";

    private TlsContextFactory() {
    }

    public static SSLContext fromProperties(Properties properties)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(properties, "properties");
        String keyStorePath = required(properties, "game.network.tls.keystore");
        String keyStoreType = properties.getProperty("game.network.tls.keystore-type", "PKCS12");
        String passwordEnvironment = properties.getProperty(
                "game.network.tls.keystore-password-env", "GAME_TLS_KEYSTORE_PASSWORD");
        String password = System.getenv(passwordEnvironment);
        if (password == null) {
            throw new IllegalStateException(
                    "TLS keystore password environment variable is not set: " + passwordEnvironment);
        }
        String protocol = properties.getProperty("game.network.tls.protocol", DEFAULT_PROTOCOL);
        char[] passwordChars = password.toCharArray();
        try {
            return fromKeyStore(Path.of(keyStorePath), keyStoreType, passwordChars, protocol);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    public static SSLContext fromKeyStore(Path path, String type, char[] password, String protocol)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(protocol, "protocol");
        if (!DEFAULT_PROTOCOL.equalsIgnoreCase(protocol)) {
            throw new IllegalArgumentException("only TLSv1.3 is supported");
        }
        KeyStore keyStore = KeyStore.getInstance(type);
        try (InputStream input = Files.newInputStream(path)) {
            keyStore.load(input, password);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, password);
        SSLContext context = SSLContext.getInstance(protocol);
        context.init(keyManagers.getKeyManagers(), null, new SecureRandom());
        return context;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing TLS configuration: " + key);
        }
        return value.trim();
    }
}
