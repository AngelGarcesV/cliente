package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.payload.OpcionesArchivo;
import com.arquitectura.mensajeria.payload.PayloadEnviarArchivo;
import com.arquitectura.mensajeria.payload.PayloadObtenerArchivo;
import com.cliente.domain.enums.DownloadMode;
import com.cliente.domain.model.Document;
import com.cliente.infrastructure.protocol.ProtocolConstants;
import com.cliente.infrastructure.protocol.ServerJsonUtil;
import javafx.concurrent.Task;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class FileService {

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

    public Task<Void> createUploadTask(File file) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                ConnectionService conn = ConnectionService.getInstance();
                String clientId = conn.getClientId();
                Protocolo proto = resolveProtocolo();
                long fileSize = file.length();
                long uploaded = 0;
                int chunkSize = ProtocolConstants.CHUNK_SIZE;
                int chunkIndex = 0;
                String ext = getExtension(file.getName());

                updateMessage("Iniciando envío de " + file.getName() + "...");

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[chunkSize];
                    int read;

                    while ((read = fis.read(buffer)) != -1) {
                        if (isCancelled()) break;

                        String encoded = Base64.getEncoder()
                                .encodeToString(Arrays.copyOf(buffer, read));

                        String chunkName = file.getName() + ".part" + chunkIndex++;
                        PayloadEnviarArchivo payload = new PayloadEnviarArchivo(
                                chunkName, encoded, ext, fileSize, null);

                        Mensaje<PayloadEnviarArchivo> req = ServerJsonUtil.buildRequest(
                                Accion.ENVIAR_DOCUMENTO, payload, clientId, proto);
                        conn.send(req);

                        uploaded += read;
                        updateProgress(uploaded, fileSize);
                        updateMessage(String.format("Enviando %s... %.0f%%",
                                file.getName(), (uploaded * 100.0) / fileSize));
                    }
                }

                updateMessage("Completado: " + file.getName());
                return null;
            }
        };
    }

    public Task<File> createDownloadTask(Document document, DownloadMode mode, Path destination) {
        return new Task<>() {
            @Override
            protected File call() throws Exception {
                Protocolo proto = resolveProtocolo();
                String clientId = ConnectionService.getInstance().getClientId();

                OpcionesArchivo opciones = new OpcionesArchivo();
                opciones.setIncluirHash(mode == DownloadMode.HASH);
                opciones.setEncriptado(mode == DownloadMode.ENCRYPTED);

                PayloadObtenerArchivo payload = new PayloadObtenerArchivo(
                        document.getId(), opciones);

                Mensaje<PayloadObtenerArchivo> req = ServerJsonUtil.buildRequest(
                        Accion.OBTENER_DOCUMENTO, payload, clientId, proto);

                Respuesta<?> resp = ConnectionService.getInstance().send(req);

                if (resp.getEstado() == Estado.ERROR || resp.getMensaje() == null
                        || resp.getMensaje().getPayload() == null) {
                    String err = resp.getError() != null ? resp.getError().getMensaje()
                            : "El servidor no devolvió datos.";
                    throw new IOException(err);
                }

                // Payload is expected to be a map with a "data" base64 field
                @SuppressWarnings("unchecked")
                Map<String, Object> data = ServerJsonUtil.convert(
                        resp.getMensaje().getPayload(), Map.class);
                byte[] bytes = Base64.getDecoder().decode((String) data.get("data"));

                String filename = document.getName();
                if (mode == DownloadMode.HASH) {
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    byte[] hash = md.digest(bytes);
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hash) sb.append(String.format("%02x", b));
                    Files.writeString(destination.resolve(filename + ".sha256"), sb.toString());
                }

                File outFile = destination.resolve(filename).toFile();
                Files.write(outFile.toPath(), bytes);
                return outFile;
            }
        };
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
