package com.project.game.network;

import com.project.game.network.transport.ClientTransport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class TestTransport implements ClientTransport {
    private final InputStream input;
    private final OutputStream output;
    private final String remoteAddress;
    private boolean closed;

    TestTransport() {
        this(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), "127.0.0.1");
    }

    TestTransport(InputStream input, OutputStream output, String remoteAddress) {
        this.input = input;
        this.output = output;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public InputStream input() {
        return input;
    }

    @Override
    public OutputStream output() {
        return output;
    }

    @Override
    public String remoteAddress() {
        return remoteAddress;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        input.close();
        output.close();
    }

    boolean isClosed() {
        return closed;
    }
}
