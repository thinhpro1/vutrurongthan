package com.project.game.network.transport;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Objects;

/** TLS 1.3 transport used by the network server when TLS mode is enabled. */
public final class TlsTcpTransport implements ClientTransport {
    private static final String[] ENABLED_PROTOCOLS = {"TLSv1.3"};
    private final SSLSocket socket;

    private TlsTcpTransport(SSLSocket socket) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket");
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    public static TlsTcpTransport accept(SSLServerSocket serverSocket, int handshakeTimeoutMillis)
            throws IOException {
        Objects.requireNonNull(serverSocket, "serverSocket");
        SSLSocket socket = (SSLSocket) serverSocket.accept();
        try {
            socket.setUseClientMode(false);
            configure(socket, handshakeTimeoutMillis);
            socket.startHandshake();
            socket.setSoTimeout(0);
            return new TlsTcpTransport(socket);
        } catch (IOException | RuntimeException failure) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw failure;
        }
    }

    public static TlsTcpTransport connect(String host, int port, SSLContext context, int timeoutMillis)
            throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(context, "context");
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setUseClientMode(true);
            configure(socket, timeoutMillis);
            socket.startHandshake();
            socket.setSoTimeout(0);
            return new TlsTcpTransport(socket);
        } catch (IOException | RuntimeException failure) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            throw failure;
        }
    }

    @Override
    public InputStream input() throws IOException {
        return socket.getInputStream();
    }

    @Override
    public OutputStream output() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public String remoteAddress() {
        SocketAddress remote = socket.getRemoteSocketAddress();
        if (remote instanceof InetSocketAddress address && address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return String.valueOf(remote);
    }

    @Override
    public void setReadTimeout(int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private static void configure(SSLSocket socket, int timeoutMillis) throws IOException {
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        socket.setEnabledProtocols(ENABLED_PROTOCOLS);
        socket.setSoTimeout(timeoutMillis);
    }
}
