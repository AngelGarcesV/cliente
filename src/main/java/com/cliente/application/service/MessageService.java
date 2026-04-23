package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.arquitectura.mensajeria.payload.PayloadEnviarMensaje;
import com.cliente.domain.model.Message;
import com.cliente.infrastructure.persistence.LocalDocumentRepository;
import com.cliente.infrastructure.protocol.ServerJsonUtil;

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
                Accion.LISTAR_MENSAJES, null,
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
        String username = ConnectionService.getInstance().getUsername();
        Protocolo proto = resolveProtocolo();

        PayloadEnviarMensaje payload = new PayloadEnviarMensaje(username, content);

        Mensaje<PayloadEnviarMensaje> msg = ServerJsonUtil.buildRequest(
                Accion.ENVIAR_MENSAJE, payload, username, proto);

        ConnectionService.getInstance().send(msg);

        // Persistir localmente en H2
        new LocalDocumentRepository().guardarMensajeEnviado(
                msg.getMetadata().getIdMensaje(),
                content,
                ConnectionService.getInstance().getHost(),
                ConnectionService.getInstance().getPort()
        );
    }

    private Protocolo resolveProtocolo() {
        return ConnectionService.getInstance().getProtocol() == com.cliente.domain.enums.Protocol.TCP
                ? Protocolo.TCP : Protocolo.UDP;
    }
}
