package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.payload.PayloadFinalizarStream;
import com.arquitectura.mensajeria.payload.PayloadIniciarDescarga;
import com.arquitectura.mensajeria.payload.PayloadIniciarStream;
import com.arquitectura.mensajeria.payload.PayloadSolicitarStream;
import com.cliente.domain.enums.DownloadMode;
import com.cliente.infrastructure.persistence.LocalDocumentRepository;
import com.cliente.domain.model.Document;
import com.cliente.infrastructure.protocol.ProtocolConstants;
import com.cliente.infrastructure.protocol.ServerJsonUtil;
import com.cliente.infrastructure.socket.TcpSocketClient;
import com.cliente.infrastructure.socket.UdpSocketClient;
import javafx.concurrent.Task;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileService {

    private static final Logger LOG = Logger.getLogger(FileService.class.getName());
    private static FileService instance;

    private FileService() {}

    public static FileService getInstance() {
        if (instance == null) instance = new FileService();
        return instance;
    }

    public List<Document> listDocuments() throws Exception {
        Protocolo proto = resolveProtocolo();
        Mensaje<?> msg = ServerJsonUtil.buildRequest(
                Accion.LISTAR_DOCUMENTOS, null,
                ConnectionService.getInstance().getClientId(), proto);

        @SuppressWarnings("unchecked")
        Respuesta<?> resp = ConnectionService.getInstance().send((Mensaje<Object>) msg);

        if (resp.getEstado() == Estado.ERROR
                || resp.getMensaje() == null
                || resp.getMensaje().getPayload() == null) {
            return List.of();
        }
        return ServerJsonUtil.convertList(resp.getMensaje().getPayload(), Document.class);
    }

    /**
     * Crea una tarea de subida de archivo que usa streaming real por chunks binarios.
     *
     * Flujo de tres fases:
     *
     * FASE 1 — INICIAR_STREAM (JSON de control)
     *   El cliente notifica al servidor el nombre, tamaño, y cantidad de chunks.
     *   El servidor crea el archivo temporal y responde con el transferId confirmado.
     *
     * FASE 2 — Chunks binarios
     *   TCP: conexión persistente, frame por chunk, ACK por chunk.
     *   UDP: datagrama por chunk, ACK stop-and-wait con reintentos.
     *   En paralelo se calcula el SHA-256 incremental del archivo completo.
     *
     * FASE 3 — FINALIZAR_STREAM (JSON de control)
     *   El cliente envía el hash SHA-256 final. El servidor valida, mueve el archivo
     *   temporal a su nombre definitivo y persiste en base de datos.
     */
    public Task<Void> createUploadTask(File file) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                ConnectionService conn = ConnectionService.getInstance();
                String clientId = conn.getClientId();
                Protocolo proto = resolveProtocolo();
                long fileSize = file.length();
                String ext = getExtension(file.getName());
                String transferId = UUID.randomUUID().toString();

                boolean esTcp = conn.getProtocol() == com.cliente.domain.enums.Protocol.TCP;
                int chunkSize = esTcp ? ProtocolConstants.CHUNK_SIZE : ProtocolConstants.UDP_CHUNK_SIZE;
                long totalChunks = Math.max(1L, (fileSize + chunkSize - 1) / chunkSize);

                updateMessage("Iniciando envío de " + file.getName() + "...");
                updateProgress(0, fileSize);

                // ── FASE 1: INICIAR_STREAM ─────────────────────────────────────────────
                PayloadIniciarStream iniciarPayload = new PayloadIniciarStream(
                        transferId, file.getName(), ext, fileSize, totalChunks, chunkSize);
                Mensaje<PayloadIniciarStream> iniciarMsg = ServerJsonUtil.buildRequest(
                        Accion.INICIAR_STREAM, iniciarPayload, clientId, proto);

                Respuesta<?> iniciarResp = conn.send(iniciarMsg);
                if (iniciarResp.getEstado() == Estado.ERROR) {
                    String err = iniciarResp.getError() != null
                            ? iniciarResp.getError().getMensaje() : "Error desconocido";
                    throw new IOException("El servidor rechazó INICIAR_STREAM: " + err);
                }

                // ── FASE 2: Chunks binarios con SHA-256 incremental ───────────────────
                updateMessage("Enviando " + file.getName() + "...");
                String hashFinal = enviarChunks(conn, transferId, file, fileSize, chunkSize, esTcp,
                        (enviados, total) -> {
                            updateProgress(enviados, total);
                            updateMessage(String.format("Enviando %s... %.1f%%",
                                    file.getName(), (enviados * 100.0) / total));
                        });

                if (isCancelled()) return null;

                // ── FASE 3: FINALIZAR_STREAM ──────────────────────────────────────────
                updateMessage("Verificando integridad de " + file.getName() + "...");
                PayloadFinalizarStream finalizarPayload = new PayloadFinalizarStream(
                        transferId, hashFinal, totalChunks);
                Mensaje<PayloadFinalizarStream> finalizarMsg = ServerJsonUtil.buildRequest(
                        Accion.FINALIZAR_STREAM, finalizarPayload, clientId, proto);

                Respuesta<?> finalizarResp = conn.send(finalizarMsg);
                if (finalizarResp.getEstado() == Estado.ERROR) {
                    String err = finalizarResp.getError() != null
                            ? finalizarResp.getError().getMensaje() : "Error desconocido";
                    throw new IOException("El servidor rechazó FINALIZAR_STREAM: " + err);
                }

                updateMessage("Completado: " + file.getName());
                updateProgress(fileSize, fileSize);

                // Persistir localmente en H2 de forma asíncrona — no bloquea el hilo de la tarea
                String snapshotId     = transferId;
                String snapshotName   = file.getName();
                String snapshotExt    = ext;
                long   snapshotSize   = fileSize;
                String snapshotHost   = conn.getHost();
                int    snapshotPort   = conn.getPort();
                String snapshotHash   = hashFinal;
                String snapshotUser   = conn.getUsername();

                CompletableFuture.runAsync(() -> {
                    try {
                        new LocalDocumentRepository().guardarArchivoEnviado(
                                snapshotId,
                                snapshotUser,
                                null,           // ip_remitente: no disponible en el cliente
                                snapshotName,
                                snapshotExt,
                                null,           // ruta_archivo: el cliente no conoce la ruta en el servidor
                                snapshotHash,
                                null,           // contenido_cifrado: no disponible en el cliente
                                snapshotSize,
                                snapshotHost,
                                snapshotPort
                        );
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error persistiendo archivo en H2 (no-bloqueante): " + e.getMessage(), e);
                    }
                });

                return null;
            }
        };
    }

    /**
     * Envía los chunks del archivo y calcula el SHA-256 en un solo paso.
     *
     * Estrategia: DigestInputStream envuelve el FileInputStream original.
     * Los sockets reciben un FileInputStream anónimo que delega al DigestInputStream,
     * de modo que cada byte leído alimenta el digest automáticamente.
     * No se lee el archivo dos veces.
     *
     * @return hash SHA-256 del archivo completo en Base64
     */
    private String enviarChunks(ConnectionService conn, String transferId, File file,
                                long fileSize, int chunkSize, boolean esTcp,
                                TcpSocketClient.StreamProgressCallback progressCb) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // DigestInputStream sobre el FIS real — UN solo flujo de lectura
        FileInputStream fis = new FileInputStream(file);
        DigestInputStream dis = new DigestInputStream(fis, digest);

        // FileInputStream "puente" que delega todas las lecturas al DigestInputStream
        FileInputStream puente = new FileInputStream(file) {
            @Override public int read() throws IOException                         { return dis.read(); }
            @Override public int read(byte[] b) throws IOException                 { return dis.read(b); }
            @Override public int read(byte[] b, int off, int len) throws IOException { return dis.read(b, off, len); }
            @Override public void close() throws IOException                       { dis.close(); }
        };

        try (puente) {
            if (esTcp) {
                TcpSocketClient tcpClient = new TcpSocketClient();
                tcpClient.connect(conn.getHost(), conn.getPort());
                tcpClient.sendFileStream(transferId, puente, chunkSize, fileSize, progressCb);
            } else {
                UdpSocketClient udpClient = new UdpSocketClient();
                udpClient.connect(conn.getHost(), conn.getPort());
                udpClient.sendFileStreamUdp(transferId, puente, fileSize, progressCb);
            }
        }

        return Base64.getEncoder().encodeToString(digest.digest());
    }

    /**
     * Descarga un archivo del servidor usando streaming real por chunks binarios.
     *
     * Flujo de dos fases:
     *
     * FASE 1 — SOLICITAR_STREAM (JSON de control)
     *   El cliente pide el archivo por ID. El servidor responde con los metadatos:
     *   transferId, nombre, tamaño, totalChunks, hash SHA-256.
     *
     * FASE 2 — Chunks binarios
     *   TCP: el cliente abre una segunda conexión con señal 0x03 + transferId.
     *        El servidor envía los chunks y el cliente responde ACK por cada uno.
     *   UDP: el cliente envía un datagrama con señal 0x03 + transferId.
     *        El servidor envía cada chunk, el cliente responde ACK.
     *
     * Al terminar, valida el hash SHA-256 contra el que devolvió el servidor.
     */
    public Task<File> createDownloadTask(Document document, DownloadMode mode, Path destination) {
        return new Task<>() {
            @Override
            protected File call() throws Exception {
                ConnectionService conn = ConnectionService.getInstance();
                String clientId = conn.getClientId();
                Protocolo proto = resolveProtocolo();
                boolean esTcp = conn.getProtocol() == com.cliente.domain.enums.Protocol.TCP;

                updateMessage("Solicitando " + document.getName() + "...");
                updateProgress(0, 1);

                // ── FASE 1: SOLICITAR_STREAM ───────────────────────────────────────────
                PayloadSolicitarStream solicitarPayload = new PayloadSolicitarStream(document.getId());
                Mensaje<PayloadSolicitarStream> solicitarMsg = ServerJsonUtil.buildRequest(
                        Accion.SOLICITAR_STREAM, solicitarPayload, clientId, proto);

                Respuesta<?> solicitarResp = conn.send(solicitarMsg);
                if (solicitarResp.getEstado() == Estado.ERROR) {
                    String err = solicitarResp.getError() != null
                            ? solicitarResp.getError().getMensaje() : "Error desconocido";
                    throw new IOException("El servidor rechazó la descarga: " + err);
                }

                PayloadIniciarDescarga meta = ServerJsonUtil.convert(
                        solicitarResp.getMensaje().getPayload(), PayloadIniciarDescarga.class);

                String transferId  = meta.getTransferId();
                long   totalBytes  = meta.getTamanoTotal();
                long   totalChunks = meta.getTotalChunks();
                String hashServidor = meta.getHashSha256();
                String nombreBase  = meta.getNombreArchivo();
                String extension   = meta.getExtension();

                // Reconstruir nombre completo con extensión si no la trae ya incluida
                String nombreArchivo = (extension != null && !extension.isBlank()
                        && !nombreBase.endsWith("." + extension))
                        ? nombreBase + "." + extension
                        : nombreBase;

                updateMessage("Descargando " + nombreArchivo + "...");
                updateProgress(0, totalBytes);

                // ── FASE 2: Chunks binarios ────────────────────────────────────────────
                Path archivoDestino = destination.resolve(nombreArchivo);

                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                if (esTcp) {
                    TcpSocketClient tcpClient = new TcpSocketClient();
                    tcpClient.connect(conn.getHost(), conn.getPort());
                    tcpClient.receiveFileStream(transferId, totalChunks, archivoDestino, totalBytes,
                            (recibidos, total) -> {
                                updateProgress(recibidos, total);
                                updateMessage(String.format("Descargando %s... %.1f%%",
                                        nombreArchivo, (recibidos * 100.0) / total));
                            });
                } else {
                    UdpSocketClient udpClient = new UdpSocketClient();
                    udpClient.connect(conn.getHost(), conn.getPort());
                    udpClient.receiveFileStreamUdp(transferId, totalChunks, archivoDestino, totalBytes,
                            (recibidos, total) -> {
                                updateProgress(recibidos, total);
                                updateMessage(String.format("Descargando %s (UDP)... %.1f%%",
                                        nombreArchivo, (recibidos * 100.0) / total));
                            });
                }

                // ── Validar hash SHA-256 ───────────────────────────────────────────────
                if (hashServidor != null && !hashServidor.isBlank()) {
                    updateMessage("Verificando integridad...");
                    String hashLocal = calcularHash(archivoDestino);
                    if (!hashLocal.equals(hashServidor)) {
                        Files.deleteIfExists(archivoDestino);
                        throw new IOException("El archivo descargado está corrupto " +
                                "(hash SHA-256 no coincide). Se eliminó el archivo parcial.");
                    }
                }

                // ── Modo HASH — guardar archivo .sha256 adicional ─────────────────────
                if (mode == DownloadMode.HASH && hashServidor != null && !hashServidor.isBlank()) {
                    Files.writeString(destination.resolve(nombreArchivo + ".sha256"), hashServidor);
                }

                updateMessage("Completado: " + nombreArchivo);
                updateProgress(totalBytes, totalBytes);
                return archivoDestino.toFile();
            }
        };
    }

    /** Calcula SHA-256 de un archivo en disco en Base64, leyendo en chunks de 2MB. */
    private String calcularHash(Path archivo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[2 * 1024 * 1024];
        try (InputStream is = Files.newInputStream(archivo)) {
            int n;
            while ((n = is.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    private Protocolo resolveProtocolo() {
        return ConnectionService.getInstance().getProtocol() == com.cliente.domain.enums.Protocol.TCP
                ? Protocolo.TCP : Protocolo.UDP;
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1) : "";
    }
}
