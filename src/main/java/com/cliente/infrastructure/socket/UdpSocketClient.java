package com.cliente.infrastructure.socket;

import com.cliente.infrastructure.protocol.ProtocolConstants;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Cliente UDP.
 *
 * Maneja dos modos:
 *
 * 1. JSON (control): sendAndReceive() — envía datagrama JSON, espera respuesta.
 * 2. Streaming (datos): sendFileStreamUdp() — envía chunks binarios con ACK stop-and-wait.
 *    Cada datagrama lleva el byte de señal 0x02 como primer byte para que el servidor
 *    lo distinga de un mensaje JSON.
 *
 * Limitación: chunk máximo 60 KB (límite seguro del MTU UDP).
 * Para archivos grandes se recomienda TCP.
 */
public class UdpSocketClient implements SocketClient {

    private InetAddress serverAddress;
    private int serverPort;
    private boolean initialized;

    private static final int JSON_BUFFER_SIZE = 65507;

    @Override
    public void connect(String host, int port) throws Exception {
        this.serverAddress = InetAddress.getByName(host);
        this.serverPort = port;
        this.initialized = true;
    }

    /**
     * Envía un mensaje JSON y espera respuesta. Un datagrama por llamada.
     */
    @Override
    public String sendAndReceive(String json) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(ProtocolConstants.READ_TIMEOUT);

            byte[] buf = json.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, serverAddress, serverPort);
            socket.send(packet);

            byte[] respBuf = new byte[JSON_BUFFER_SIZE];
            DatagramPacket response = new DatagramPacket(respBuf, respBuf.length);
            socket.receive(response);

            return new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Transfiere un archivo al servidor usando datagramas UDP con protocolo
     * stop-and-wait (espera ACK antes de enviar el siguiente chunk).
     *
     * Protocolo por datagrama:
     * - Byte 0:      STREAM_SIGNAL (0x02) — distingue chunks de mensajes JSON
     * - Bytes 1-36:  transferId (UTF-8, 36 bytes fijos)
     * - Bytes 37-44: chunkIndex (long, big-endian)
     * - Bytes 45-48: chunkSize (int, big-endian)
     * - Bytes 49+:   datos del chunk
     *
     * ACK del servidor: 9 bytes = 0x01 + chunkIndex (long, big-endian)
     * NACK del servidor: 9 bytes = 0x00 + chunkIndex (long, big-endian)
     *
     * En caso de timeout o NACK, reintenta hasta UDP_MAX_RETRIES veces.
     *
     * @param transferId  UUID de la transferencia (debe coincidir con INICIAR_STREAM)
     * @param fis         FileInputStream del archivo a enviar
     * @param totalBytes  tamaño total del archivo en bytes
     * @param progressCb  callback invocado con (bytesEnviados, totalBytes) por chunk
     * @throws Exception  si se superan los reintentos o hay error de red
     */
    public void sendFileStreamUdp(String transferId, FileInputStream fis,
                                  long totalBytes,
                                  TcpSocketClient.StreamProgressCallback progressCb) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(ProtocolConstants.UDP_ACK_TIMEOUT);

            int chunkSize = ProtocolConstants.UDP_CHUNK_SIZE;
            byte[] buffer = new byte[chunkSize];
            byte[] idBytes = padTransferId(transferId);

            long chunkIndex = 0;
            long totalEnviado = 0;
            int read;

            while ((read = fis.read(buffer)) != -1) {
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);

                boolean confirmado = false;
                int intentos = 0;

                while (!confirmado && intentos < ProtocolConstants.UDP_MAX_RETRIES) {
                    // Construir datagrama: señal(1) + id(36) + index(8) + size(4) + datos
                    ByteBuffer frame = ByteBuffer.allocate(1 + 36 + 8 + 4 + read);
                    frame.put(ProtocolConstants.STREAM_SIGNAL);
                    frame.put(idBytes);
                    frame.putLong(chunkIndex);
                    frame.putInt(read);
                    frame.put(chunk);

                    byte[] frameBytes = frame.array();
                    DatagramPacket paquete = new DatagramPacket(
                            frameBytes, frameBytes.length, serverAddress, serverPort);
                    socket.send(paquete);

                    // Esperar ACK
                    try {
                        byte[] ackBuf = new byte[9];
                        DatagramPacket ackPaquete = new DatagramPacket(ackBuf, ackBuf.length);
                        socket.receive(ackPaquete);

                        ByteBuffer ackBuf2 = ByteBuffer.wrap(ackBuf);
                        byte tipo = ackBuf2.get();
                        long ackIndex = ackBuf2.getLong();

                        if (tipo == 0x01 && ackIndex == chunkIndex) {
                            confirmado = true;
                        } else if (tipo == 0x00) {
                            // NACK — reintentar
                            intentos++;
                        }
                    } catch (SocketTimeoutException e) {
                        intentos++;
                    }
                }

                if (!confirmado) {
                    throw new IOException("No se recibió ACK para el chunk " + chunkIndex
                            + " después de " + ProtocolConstants.UDP_MAX_RETRIES + " intentos.");
                }

                totalEnviado += read;
                chunkIndex++;

                if (progressCb != null) {
                    progressCb.onProgress(totalEnviado, totalBytes);
                }
            }
        }
    }

    /**
     * Recibe un archivo del servidor por streaming UDP con stop-and-wait.
     *
     * Protocolo:
     * 1. Envía datagrama: 0x03 + transferId (36 bytes).
     * 2. Por cada chunk que llega del servidor:
     *    a. Recibe datagrama: 0x03 + id(36) + chunkIndex(8) + chunkSize(4) + datos.
     *    b. Escribe datos en el archivo destino.
     *    c. Envía ACK: 0x01 + chunkIndex (9 bytes).
     * 3. Termina cuando llegan totalChunks chunks.
     */
    public void receiveFileStreamUdp(String transferId, long totalChunks,
                                     java.nio.file.Path destino, long totalBytes,
                                     TcpSocketClient.StreamProgressCallback progressCb) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(ProtocolConstants.UDP_ACK_TIMEOUT);

            // Señal inicial: 0x03 + transferId
            byte[] idBytes = padTransferId(transferId);
            ByteBuffer señal = ByteBuffer.allocate(1 + 36);
            señal.put((byte) 0x03);
            señal.put(idBytes);
            byte[] señalBytes = señal.array();
            socket.send(new DatagramPacket(señalBytes, señalBytes.length, serverAddress, serverPort));

            long totalRecibido = 0;

            try (java.nio.channels.FileChannel fc = java.nio.channels.FileChannel.open(destino,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {

                for (long i = 0; i < totalChunks; i++) {
                    // Recibir chunk con reintentos
                    byte[] buf = new byte[65535];
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);

                    byte[] data = pkt.getData();
                    int    len  = pkt.getLength();

                    if (len < 1 + 36 + 8 + 4) throw new IOException("Datagrama demasiado corto en chunk " + i);

                    // Parsear: señal(1) + id(36) + chunkIndex(8) + chunkSize(4) + datos
                    ByteBuffer frame = ByteBuffer.wrap(data, 0, len);
                    byte señalByte = frame.get();            // 0x03
                    byte[] idRecibido = new byte[36];
                    frame.get(idRecibido);
                    long chunkIndex = frame.getLong();
                    int  chunkSize  = frame.getInt();

                    byte[] chunkData = new byte[chunkSize];
                    frame.get(chunkData);

                    // Escribir a disco
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(chunkData);
                    while (bb.hasRemaining()) fc.write(bb);

                    // ACK: 0x01 + chunkIndex
                    byte[] ack = ByteBuffer.allocate(9).put((byte) 0x01).putLong(chunkIndex).array();
                    socket.send(new DatagramPacket(ack, ack.length, serverAddress, serverPort));

                    totalRecibido += chunkSize;
                    if (progressCb != null) progressCb.onProgress(totalRecibido, totalBytes);
                }
            }
        }
    }
    private byte[] padTransferId(String transferId) {
        byte[] raw = transferId.getBytes(StandardCharsets.UTF_8);
        if (raw.length == 36) return raw;
        byte[] padded = new byte[36];
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, 36));
        return padded;
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
