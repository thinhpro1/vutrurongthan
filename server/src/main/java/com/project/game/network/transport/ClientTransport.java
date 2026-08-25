package com.project.game.network.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface ClientTransport extends AutoCloseable {
    InputStream input() throws IOException;

    OutputStream output() throws IOException;

    String remoteAddress();

    @Override
    void close() throws IOException;
}
