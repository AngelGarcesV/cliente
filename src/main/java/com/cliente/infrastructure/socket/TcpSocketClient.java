package com.cliente.infrastructure.socket;

import com.cliente.infrastructure.protocol.ProtocolConstants;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Opens a fresh TCP connection for every sendAndReceive call, matching the
 * server's one-request-per-connection model.
 */
public class TcpSocketClient implements SocketClient {

    private String host;
    private int port;
    private boolean initialized;

    @Override
    public void connect(String host, int port) throws Exception {
        this.host = host;
        this.port = port;
        this.initialized = true;
    }

    @Override
    public String sendAndReceive(String json) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), ProtocolConstants.CONNECT_TIMEOUT);
            socket.setSoTimeout(ProtocolConstants.READ_TIMEOUT);

            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));

            writer.write(json);
            writer.newLine();
            writer.flush();

            String response = reader.readLine();
            if (response == null) throw new IOException("Conexión cerrada por el servidor.");
            return response;
        }
    }

    @Override
    public void disconnect() {
        initialized = false;
    }

    @Override
    public boolean isConnected() {
        return initialized;
    }
}
