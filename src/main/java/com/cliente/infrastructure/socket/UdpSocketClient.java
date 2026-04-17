package com.cliente.infrastructure.socket;

import com.cliente.infrastructure.protocol.ProtocolConstants;

import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Sends each message as a single UDP datagram and waits for one datagram back.
 */
public class UdpSocketClient implements SocketClient {

    private InetAddress serverAddress;
    private int serverPort;
    private boolean initialized;

    private static final int BUFFER_SIZE = 65507;

    @Override
    public void connect(String host, int port) throws Exception {
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
        this.initialized = true;
    }

    @Override
    public String sendAndReceive(String json) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(ProtocolConstants.READ_TIMEOUT);

            byte[] buf = json.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, serverPort);
            socket.send(packet);

            byte[] respBuf = new byte[BUFFER_SIZE];
            DatagramPacket response = new DatagramPacket(respBuf, respBuf.length);
            socket.receive(response);

            return new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);
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
