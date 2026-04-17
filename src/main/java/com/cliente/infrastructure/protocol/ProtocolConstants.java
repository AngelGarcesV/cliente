package com.cliente.infrastructure.protocol;

public final class ProtocolConstants {
    private ProtocolConstants() {}

    public static final int CMD_CONNECT       = 0;
    public static final int CMD_LIST_CLIENTS  = 1;
    public static final int CMD_LIST_DOCS     = 2;
    public static final int CMD_DOWNLOAD_DOC  = 3;
    public static final int CMD_SEND_FILE     = 4;
    public static final int CMD_GET_LOGS      = 5;
    public static final int CMD_SEND_MESSAGE  = 6;
    public static final int CMD_GET_MESSAGES  = 7;

    public static final String STATUS_OK    = "ok";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_FULL  = "full";

    public static final int CHUNK_SIZE      = 65536; // 64 KB
    public static final int CONNECT_TIMEOUT = 5000;  // ms
    public static final int READ_TIMEOUT    = 10000; // ms
}
