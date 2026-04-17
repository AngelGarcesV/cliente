package com.cliente.infrastructure.protocol;

import com.google.gson.JsonElement;

public class ProtocolMessage {
    private int command;
    private String clientId;
    private String status;
    private JsonElement payload;
    private JsonElement data;
    private String error;

    public ProtocolMessage() {}

    public ProtocolMessage(int command, JsonElement payload) {
        this.command = command;
        this.payload = payload;
    }

    public ProtocolMessage(int command, String clientId, JsonElement payload) {
        this.command = command;
        this.clientId = clientId;
        this.payload = payload;
    }

    public int getCommand() { return command; }
    public void setCommand(int command) { this.command = command; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public JsonElement getPayload() { return payload; }
    public void setPayload(JsonElement payload) { this.payload = payload; }
    public JsonElement getData() { return data; }
    public void setData(JsonElement data) { this.data = data; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public boolean isOk() {
        return ProtocolConstants.STATUS_OK.equals(status);
    }

    public boolean isServerFull() {
        return ProtocolConstants.STATUS_FULL.equals(status);
    }
}
