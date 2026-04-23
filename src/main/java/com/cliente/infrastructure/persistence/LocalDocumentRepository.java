package com.cliente.infrastructure.persistence;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalDocumentRepository {

    public void guardarMensajeEnviado(String id, String contenido, String servidorHost, int servidorPuerto) {
        String sql = "INSERT INTO mensajes_enviados (id, contenido, fecha_envio, servidor_host, servidor_puerto) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = H2DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, contenido);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(4, servidorHost);
            ps.setInt(5, servidorPuerto);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error guardando mensaje enviado en H2: " + e.getMessage());
        }
    }

    public void guardarArchivoEnviado(String id, String nombreArchivo, String extension,
                                      long tamano, String servidorHost, int servidorPuerto) {
        String sql = "INSERT INTO archivos_enviados " +
                     "(id, nombre_archivo, extension, tamano, fecha_envio, servidor_host, servidor_puerto) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = H2DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, nombreArchivo);
            ps.setString(3, extension);
            ps.setLong(4, tamano);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(6, servidorHost);
            ps.setInt(7, servidorPuerto);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error guardando archivo enviado en H2: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listarMensajesEnviados() {
        String sql = "SELECT id, contenido, fecha_envio, servidor_host, servidor_puerto " +
                     "FROM mensajes_enviados ORDER BY fecha_envio DESC";
        List<Map<String, Object>> resultado = new ArrayList<>();
        try (Connection conn = H2DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> fila = new HashMap<>();
                fila.put("id", rs.getString("id"));
                fila.put("contenido", rs.getString("contenido"));
                fila.put("fechaEnvio", rs.getTimestamp("fecha_envio").toLocalDateTime());
                fila.put("servidorHost", rs.getString("servidor_host"));
                fila.put("servidorPuerto", rs.getInt("servidor_puerto"));
                resultado.add(fila);
            }
        } catch (SQLException e) {
            System.err.println("Error listando mensajes enviados desde H2: " + e.getMessage());
        }
        return resultado;
    }

    public List<Map<String, Object>> listarArchivosEnviados() {
        String sql = "SELECT id, nombre_archivo, extension, tamano, fecha_envio, servidor_host, servidor_puerto " +
                     "FROM archivos_enviados ORDER BY fecha_envio DESC";
        List<Map<String, Object>> resultado = new ArrayList<>();
        try (Connection conn = H2DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> fila = new HashMap<>();
                fila.put("id", rs.getString("id"));
                fila.put("nombreArchivo", rs.getString("nombre_archivo"));
                fila.put("extension", rs.getString("extension"));
                fila.put("tamano", rs.getLong("tamano"));
                fila.put("fechaEnvio", rs.getTimestamp("fecha_envio").toLocalDateTime());
                fila.put("servidorHost", rs.getString("servidor_host"));
                fila.put("servidorPuerto", rs.getInt("servidor_puerto"));
                resultado.add(fila);
            }
        } catch (SQLException e) {
            System.err.println("Error listando archivos enviados desde H2: " + e.getMessage());
        }
        return resultado;
    }
}
