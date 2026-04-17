package com.cliente.domain.model;

public class Message {
    private String clientId;
    private String content;
    private String timestamp;

    public Message() {}

    public Message(String clientId, String content, String timestamp) {
        this.clientId = clientId;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
