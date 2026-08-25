package com.project.game.network.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class LegacyTcpTransport implements ClientTransport {
    private final Socket socket;

    public LegacyTcpTransport(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    public static LegacyTcpTransport connect(String host, int port, int timeoutMillis) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        return new LegacyTcpTransport(socket);
    }

    public Socket socket() {
        return socket;
    }

    public void setReadTimeout(int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
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
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
