package com.cliente.infrastructure.persistence;

import java.sql.*;

public final class H2DatabaseManager {

    private static final String URL = "jdbc:h2:./cliente-data/documentos;AUTO_SERVER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    private H2DatabaseManager() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void giinicializar() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mensajes_enviados (
                    id VARCHAR(36) PRIMARY KEY,
                    destinatario VARCHAR(255),
                    contenido TEXT NOT NULL,
                    fecha_envio TIMESTAMP NOT NULL,
                    servidor_host VARCHAR(255),
                    servidor_puerto INT
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS archivos_enviados (
                    id VARCHAR(36) PRIMARY KEY,
                    nombre_archivo VARCHAR(255) NOT NULL,
                    extension VARCHAR(50),
                    tamano BIGINT NOT NULL,
                    fecha_envio TIMESTAMP NOT NULL,
                    servidor_host VARCHAR(255),
                    servidor_puerto INT
                )
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Error inicializando H2", e);
        }
    }

    public static void cerrar() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("SHUTDOWN");
        } catch (SQLException ignored) {}
    }
}
