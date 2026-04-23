package com.cliente.application.service;

import com.arquitectura.mensajeria.Mensaje;
import com.arquitectura.mensajeria.Respuesta;
import com.arquitectura.mensajeria.enums.Accion;
import com.arquitectura.mensajeria.enums.Estado;
import com.arquitectura.mensajeria.enums.Protocolo;
import com.cliente.domain.model.LogEntry;
import com.cliente.domain.model.PaginatedResult;
import com.cliente.infrastructure.protocol.ServerJsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LogService {

    private static LogService instance;

    private LogService() {}

    public static LogService getInstance() {
        if (instance == null) instance = new LogService();
        return instance;
    }

    public PaginatedResult<LogEntry> getLogs(int pagina, int tamanoPagina) throws Exception {
        Protocolo proto = resolveProtocolo();

        Map<String, Object> payload = new HashMap<>();
        payload.put("pagina", pagina);
        payload.put("tamanoPagina", tamanoPagina);

        Mensaje<?> msg = ServerJsonUtil.buildRequest(
                Accion.LISTAR_LOGS, payload,
                ConnectionService.getInstance().getClientId(), proto);

        @SuppressWarnings("unchecked")
        Respuesta<?> resp = ConnectionService.getInstance().send((Mensaje<Object>) msg);

        if (resp.getEstado() == Estado.ERROR
                || resp.getMensaje() == null
                || resp.getMensaje().getPayload() == null) {
            return new PaginatedResult<>(List.of(), pagina, tamanoPagina, 0, 0);
        }

        Object raw = resp.getMensaje().getPayload();

        // The server returns a paginated envelope: {registros, pagina, tamanoPagina, totalRegistros, totalPaginas}
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (raw instanceof Map<?,?> m)
                ? (Map<String, Object>) m
                : new ObjectMapper().convertValue(raw, Map.class);

        Object rawRegistros = envelope.get("registros");
        List<LogEntry> registros = ServerJsonUtil.convertList(rawRegistros, LogEntry.class);

        int paginaResp = toInt(envelope.get("pagina"), pagina);
        int tamanoResp = toInt(envelope.get("tamanoPagina"), tamanoPagina);
        long totalRegistros = toLong(envelope.get("totalRegistros"), 0L);
        int totalPaginas = toInt(envelope.get("totalPaginas"), 1);

        return new PaginatedResult<>(registros, paginaResp, tamanoResp, totalRegistros, totalPaginas);
    }

    private int toInt(Object val, int def) {
        if (val instanceof Number n) return n.intValue();
        return def;
    }

    private long toLong(Object val, long def) {
        if (val instanceof Number n) return n.longValue();
        return def;
    }

    private Protocolo resolveProtocolo() {
        return ConnectionService.getInstance().getProtocol() == com.cliente.domain.enums.Protocol.TCP
                ? Protocolo.TCP : Protocolo.UDP;
    }
}

