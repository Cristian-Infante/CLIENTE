package com.arquitectura.repositorios;

import com.arquitectura.config.ProveedorConexionCliente;

import java.sql.*;

public class RepositorioMensajes {

    public long insertarMensajeTexto(Long emisorId, Long receptorId, Long canalId, String contenido, String tipo) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, receptor_id, canal_id, es_audio, texto) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, FALSE, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipo != null ? tipo : "TEXTO");
            ps.setLong(2, emisorId);
            if (receptorId != null) ps.setLong(3, receptorId); else ps.setNull(3, Types.BIGINT);
            if (canalId != null) ps.setLong(4, canalId); else ps.setNull(4, Types.BIGINT);
            ps.setString(5, contenido);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo obtener el id generado del mensaje de texto");
    }

    public long insertarMensajeAudio(Long emisorId, Long receptorId, Long canalId, String transcripcion, String tipo) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, receptor_id, canal_id, es_audio, texto, ruta_audio) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, TRUE, ?, NULL)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipo != null ? tipo : "AUDIO");
            ps.setLong(2, emisorId);
            if (receptorId != null) ps.setLong(3, receptorId); else ps.setNull(3, Types.BIGINT);
            if (canalId != null) ps.setLong(4, canalId); else ps.setNull(4, Types.BIGINT);
            ps.setString(5, transcripcion);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo obtener el id generado del mensaje de audio");
    }

    public long insertarMensajeAudioConRuta(Long emisorId, Long receptorId, Long canalId, String transcripcion, String tipo, String rutaArchivo) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, receptor_id, canal_id, es_audio, texto, ruta_audio) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, TRUE, ?, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipo != null ? tipo : "AUDIO");
            ps.setLong(2, emisorId);
            if (receptorId != null) ps.setLong(3, receptorId); else ps.setNull(3, Types.BIGINT);
            if (canalId != null) ps.setLong(4, canalId); else ps.setNull(4, Types.BIGINT);
            if (transcripcion != null) ps.setString(5, transcripcion); else ps.setNull(5, Types.VARCHAR);
            ps.setString(6, rutaArchivo);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo obtener el id generado del mensaje de audio con ruta");
    }

    private void validarDestino(Long receptorId, Long canalId) throws SQLException {
        if (canalId == null && receptorId == null) {
            throw new SQLException("Debe especificarse canalId (mensaje a canal) o receptorId (chat privado)");
        }
        if (canalId != null && receptorId != null) {
            // Permitido: algunos flujos podrían almacenar ambos; si quieres, se puede restringir.
        }
    }
    public java.util.List<com.arquitectura.entidades.MensajeLocal> listarMensajesDeCanal(Long canalId, Integer limit) throws SQLException {
        if (canalId == null) return java.util.List.of();
        String sql = "SELECT id, fecha_envio, tipo, emisor_id, receptor_id, canal_id, es_audio, texto, ruta_audio " +
                     "FROM mensajes WHERE canal_id = ? ORDER BY fecha_envio ASC" + (limit != null ? " LIMIT ?" : "");
        java.util.List<com.arquitectura.entidades.MensajeLocal> res = new java.util.ArrayList<>();
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            if (limit != null) ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    java.sql.Timestamp ts = rs.getTimestamp(2);
                    String tipo = rs.getString(3);
                    Long emisor = rs.getObject(4) != null ? rs.getLong(4) : null;
                    Long receptor = rs.getObject(5) != null ? rs.getLong(5) : null;
                    Long canal = rs.getObject(6) != null ? rs.getLong(6) : null;
                    boolean esAudio = rs.getBoolean(7);
                    if (esAudio) {
                        String ruta = rs.getString(9);
                        com.arquitectura.entidades.AudioMensajeLocal a = new com.arquitectura.entidades.AudioMensajeLocal();
                        a.setId(id);
                        a.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
                        a.setTipo(tipo);
                        a.setEmisor(emisor);
                        a.setReceptor(receptor);
                        a.setCanalId(canal);
                        a.setRutaArchivo(ruta);
                        res.add(a);
                    } else {
                        String texto = rs.getString(8);
                        com.arquitectura.entidades.TextoMensajeLocal t = new com.arquitectura.entidades.TextoMensajeLocal();
                        t.setId(id);
                        t.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
                        t.setTipo(tipo);
                        t.setEmisor(emisor);
                        t.setReceptor(receptor);
                        t.setCanalId(canal);
                        t.setContenido(texto);
                        res.add(t);
                    }
                }
            }
        }
        return res;
    }

    public java.util.List<com.arquitectura.entidades.MensajeLocal> listarMensajesPrivados(Long miId, Long otroId, Integer limit) throws SQLException {
        if (miId == null || otroId == null) return java.util.List.of();
        String sql = "SELECT id, fecha_envio, tipo, emisor_id, receptor_id, canal_id, es_audio, texto, ruta_audio " +
                     "FROM mensajes WHERE canal_id IS NULL AND ((emisor_id = ? AND receptor_id = ?) OR (emisor_id = ? AND receptor_id = ?)) " +
                     "ORDER BY fecha_envio ASC" + (limit != null ? " LIMIT ?" : "");
        java.util.List<com.arquitectura.entidades.MensajeLocal> res = new java.util.ArrayList<>();
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, miId);
            ps.setLong(2, otroId);
            ps.setLong(3, otroId);
            ps.setLong(4, miId);
            if (limit != null) ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    java.sql.Timestamp ts = rs.getTimestamp(2);
                    String tipo = rs.getString(3);
                    Long emisor = rs.getObject(4) != null ? rs.getLong(4) : null;
                    Long receptor = rs.getObject(5) != null ? rs.getLong(5) : null;
                    boolean esAudio = rs.getBoolean(7);
                    if (esAudio) {
                        String ruta = rs.getString(9);
                        com.arquitectura.entidades.AudioMensajeLocal a = new com.arquitectura.entidades.AudioMensajeLocal();
                        a.setId(id);
                        a.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
                        a.setTipo(tipo);
                        a.setEmisor(emisor);
                        a.setReceptor(receptor);
                        a.setRutaArchivo(ruta);
                        res.add(a);
                    } else {
                        String texto = rs.getString(8);
                        com.arquitectura.entidades.TextoMensajeLocal t = new com.arquitectura.entidades.TextoMensajeLocal();
                        t.setId(id);
                        t.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
                        t.setTipo(tipo);
                        t.setEmisor(emisor);
                        t.setReceptor(receptor);
                        t.setContenido(texto);
                        res.add(t);
                    }
                }
            }
        }
        return res;
    }

    // ---- Inserciones desde servidor (con deduplicación por server_id y/o por campos + timestamp) ----
    public boolean existePorServerId(Long serverId) throws SQLException {
        if (serverId == null) return false;
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement("SELECT 1 FROM mensajes WHERE server_id = ? LIMIT 1")) {
            ps.setLong(1, serverId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean existePorCampos(Long emisorId, Long receptorId, Long canalId, boolean esAudio, String texto, String rutaArchivo, java.sql.Timestamp ts) throws SQLException {
        String sql = "SELECT 1 FROM mensajes WHERE emisor_id = ? AND " +
                (receptorId != null ? "receptor_id = ?" : "receptor_id IS NULL") +
                (canalId != null ? " AND canal_id = ?" : " AND canal_id IS NULL") +
                " AND es_audio = ? AND fecha_envio = ? AND " + (esAudio ? "ruta_audio = ?" : "texto = ?") + " LIMIT 1";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, emisorId != null ? emisorId : 0L);
            if (receptorId != null) ps.setLong(idx++, receptorId);
            if (canalId != null) ps.setLong(idx++, canalId);
            ps.setBoolean(idx++, esAudio);
            ps.setTimestamp(idx++, ts);
            if (esAudio) ps.setString(idx++, rutaArchivo); else ps.setString(idx++, texto);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public long insertarDesdeServidorTexto(Long serverId, java.sql.Timestamp serverTs, Long emisorId, Long receptorId, Long canalId, String contenido, String tipo) throws SQLException {
        if (existePorServerId(serverId)) return 0L;
        if (existePorCampos(emisorId, receptorId, canalId, false, contenido, null, serverTs)) return 0L;
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, receptor_id, canal_id, es_audio, texto, ruta_audio, server_id, server_ts) " +
                "VALUES (?, ?, ?, ?, ?, FALSE, ?, NULL, ?, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, serverTs);
            ps.setString(2, tipo != null ? tipo : "TEXTO");
            ps.setLong(3, emisorId != null ? emisorId : 0L);
            if (receptorId != null) ps.setLong(4, receptorId); else ps.setNull(4, Types.BIGINT);
            if (canalId != null) ps.setLong(5, canalId); else ps.setNull(5, Types.BIGINT);
            ps.setString(6, contenido != null ? contenido : "");
            if (serverId != null) ps.setLong(7, serverId); else ps.setNull(7, Types.BIGINT);
            if (serverTs != null) ps.setTimestamp(8, serverTs); else ps.setNull(8, Types.TIMESTAMP);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return 0L;
    }

    public long insertarDesdeServidorAudioConRuta(Long serverId, java.sql.Timestamp serverTs, Long emisorId, Long receptorId, Long canalId, String transcripcion, String tipo, String rutaArchivo) throws SQLException {
        if (existePorServerId(serverId)) return 0L;
        if (existePorCampos(emisorId, receptorId, canalId, true, null, rutaArchivo, serverTs)) return 0L;
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, receptor_id, canal_id, es_audio, texto, ruta_audio, server_id, server_ts) " +
                "VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, serverTs);
            ps.setString(2, tipo != null ? tipo : "AUDIO");
            ps.setLong(3, emisorId != null ? emisorId : 0L);
            if (receptorId != null) ps.setLong(4, receptorId); else ps.setNull(4, Types.BIGINT);
            if (canalId != null) ps.setLong(5, canalId); else ps.setNull(5, Types.BIGINT);
            if (transcripcion != null) ps.setString(6, transcripcion); else ps.setNull(6, Types.VARCHAR);
            ps.setString(7, rutaArchivo);
            if (serverId != null) ps.setLong(8, serverId); else ps.setNull(8, Types.BIGINT);
            if (serverTs != null) ps.setTimestamp(9, serverTs); else ps.setNull(9, Types.TIMESTAMP);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return 0L;
    }
}
