package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.payload.PayloadEnviarArchivo;
import com.cliente.domain.model.Message;
import com.cliente.infrastructure.protocol.ServerJsonUtil;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

public class MessageService {

    private static MessageService instance;

    private MessageService() {}

    public static MessageService getInstance() {
        if (instance == null) instance = new MessageService();
        return instance;
    }

    public List<Message> getMessages() throws Exception {
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
        return ServerJsonUtil.convertList(resp.getMensaje().getPayload(), Message.class);
    }

    public void sendMessage(String content) throws Exception {
        String clientId = ConnectionService.getInstance().getClientId();
        Protocolo proto = resolveProtocolo();

        // Encode message text as base64 content inside a document payload
        String encoded = Base64.getEncoder().encodeToString(content.getBytes());
        PayloadEnviarArchivo payload = new PayloadEnviarArchivo(
                "mensaje_" + LocalDateTime.now(), encoded, "txt",
                content.length(), clientId);

        Mensaje<PayloadEnviarArchivo> msg = ServerJsonUtil.buildRequest(
                Accion.ENVIAR_DOCUMENTO, payload, clientId, proto);

        ConnectionService.getInstance().send(msg);
    }

    private Protocolo resolveProtocolo() {
        return ConnectionService.getInstance().getProtocol() == com.cliente.domain.enums.Protocol.TCP
                ? Protocolo.TCP : Protocolo.UDP;
    }
}
