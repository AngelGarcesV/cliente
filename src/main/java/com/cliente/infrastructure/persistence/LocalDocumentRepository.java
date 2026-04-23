package com.cliente.infrastructure.persistence;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LocalDocumentRepository {

    private static final Logger LOG = Logger.getLogger(LocalDocumentRepository.class.getName());

    public void guardarMensajeEnviado(String id, String autor, String ipRemitente,
                                      String contenido, String hashSha256, String contenidoCifrado,
                                      String servidorHost, int servidorPuerto) {
        String sql = "INSERT INTO mensajes_enviados " +
                     "(id, autor, ip_remitente, contenido, hash_sha256, contenido_cifrado, " +
                     "fecha_envio, servidor_host, servidor_puerto) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            Connection conn = H2DatabaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.setString(2, autor);
                ps.setString(3, ipRemitente);
                ps.setString(4, contenido);
                ps.setString(5, hashSha256);
                ps.setString(6, contenidoCifrado);
                ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(8, servidorHost);
                ps.setInt(9, servidorPuerto);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error guardando mensaje enviado en H2: " + e.getMessage(), e);
        }
    }

    public void guardarArchivoEnviado(String id, String remitente, String ipRemitente,
                                      String nombreArchivo, String extension, String rutaArchivo,
                                      String hashSha256, String contenidoCifrado,
                                      long tamano, String servidorHost, int servidorPuerto) {
        String sql = "INSERT INTO archivos_enviados " +
                     "(id, remitente, ip_remitente, nombre_archivo, extension, ruta_archivo, " +
                     "hash_sha256, contenido_cifrado, tamano, fecha_envio, servidor_host, servidor_puerto) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            Connection conn = H2DatabaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.setString(2, remitente);
                ps.setString(3, ipRemitente);
                ps.setString(4, nombreArchivo);
                ps.setString(5, extension);
                ps.setString(6, rutaArchivo);
                ps.setString(7, hashSha256);
                ps.setString(8, contenidoCifrado);
                ps.setLong(9, tamano);
                ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
                ps.setString(11, servidorHost);
                ps.setInt(12, servidorPuerto);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error guardando archivo enviado en H2: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listarMensajesEnviados() {
        String sql = "SELECT id, autor, ip_remitente, contenido, hash_sha256, " +
                     "fecha_envio, servidor_host, servidor_puerto " +
                     "FROM mensajes_enviados ORDER BY fecha_envio DESC";
        List<Map<String, Object>> resultado = new ArrayList<>();
        try {
            Connection conn = H2DatabaseManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> fila = new HashMap<>();
                    fila.put("id", rs.getString("id"));
                    fila.put("autor", rs.getString("autor"));
                    fila.put("ipRemitente", rs.getString("ip_remitente"));
                    fila.put("contenido", rs.getString("contenido"));
                    fila.put("hashSha256", rs.getString("hash_sha256"));
                    fila.put("fechaEnvio", rs.getTimestamp("fecha_envio").toLocalDateTime());
                    fila.put("servidorHost", rs.getString("servidor_host"));
                    fila.put("servidorPuerto", rs.getInt("servidor_puerto"));
                    resultado.add(fila);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error listando mensajes enviados desde H2: " + e.getMessage(), e);
        }
        return resultado;
    }

    public List<Map<String, Object>> listarArchivosEnviados() {
        String sql = "SELECT id, remitente, ip_remitente, nombre_archivo, extension, ruta_archivo, " +
                     "hash_sha256, tamano, fecha_envio, servidor_host, servidor_puerto " +
                     "FROM archivos_enviados ORDER BY fecha_envio DESC";
        List<Map<String, Object>> resultado = new ArrayList<>();
        try {
            Connection conn = H2DatabaseManager.getConnection();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    Map<String, Object> fila = new HashMap<>();
                    fila.put("id", rs.getString("id"));
                    fila.put("remitente", rs.getString("remitente"));
                    fila.put("ipRemitente", rs.getString("ip_remitente"));
                    fila.put("nombreArchivo", rs.getString("nombre_archivo"));
                    fila.put("extension", rs.getString("extension"));
                    fila.put("rutaArchivo", rs.getString("ruta_archivo"));
                    fila.put("hashSha256", rs.getString("hash_sha256"));
                    fila.put("tamano", rs.getLong("tamano"));
                    fila.put("fechaEnvio", rs.getTimestamp("fecha_envio").toLocalDateTime());
                    fila.put("servidorHost", rs.getString("servidor_host"));
                    fila.put("servidorPuerto", rs.getInt("servidor_puerto"));
                    resultado.add(fila);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error listando archivos enviados desde H2: " + e.getMessage(), e);
        }
        return resultado;
    }
}
