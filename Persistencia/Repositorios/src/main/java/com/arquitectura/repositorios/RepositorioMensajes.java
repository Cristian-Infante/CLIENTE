package com.arquitectura.repositorios;

import com.arquitectura.config.ProveedorConexionCliente;

import java.sql.*;

public class RepositorioMensajes {

    public long insertarMensajeTexto(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String contenido, String tipo) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, FALSE, ?, NULL)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipo != null ? tipo : "TEXTO");
            ps.setLong(2, emisorId);
            if (emisorNombre != null) ps.setString(3, emisorNombre); else ps.setNull(3, Types.VARCHAR);
            if (receptorId != null) ps.setLong(4, receptorId); else ps.setNull(4, Types.BIGINT);
            if (receptorNombre != null) ps.setString(5, receptorNombre); else ps.setNull(5, Types.VARCHAR);
            if (canalId != null) ps.setLong(6, canalId); else ps.setNull(6, Types.BIGINT);
            ps.setString(7, contenido);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo obtener el id generado del mensaje de texto");
    }

    public long insertarMensajeAudio(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String transcripcion, String tipo) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, TRUE, ?, NULL, NULL, NULL, NULL)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipo != null ? tipo : "AUDIO");
            ps.setLong(2, emisorId);
            if (emisorNombre != null) ps.setString(3, emisorNombre); else ps.setNull(3, Types.VARCHAR);
            if (receptorId != null) ps.setLong(4, receptorId); else ps.setNull(4, Types.BIGINT);
            if (receptorNombre != null) ps.setString(5, receptorNombre); else ps.setNull(5, Types.VARCHAR);
            if (canalId != null) ps.setLong(6, canalId); else ps.setNull(6, Types.BIGINT);
            if (transcripcion != null) ps.setString(7, transcripcion); else ps.setNull(7, Types.CLOB);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo obtener el id generado del mensaje de audio");
    }

    public long insertarMensajeAudioConRuta(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String transcripcion, String tipo, String rutaArchivo, String audioBase64, String mime, Integer duracionSeg) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipo != null ? tipo : "AUDIO");
            ps.setLong(2, emisorId);
            if (emisorNombre != null) ps.setString(3, emisorNombre); else ps.setNull(3, Types.VARCHAR);
            if (receptorId != null) ps.setLong(4, receptorId); else ps.setNull(4, Types.BIGINT);
            if (receptorNombre != null) ps.setString(5, receptorNombre); else ps.setNull(5, Types.VARCHAR);
            if (canalId != null) ps.setLong(6, canalId); else ps.setNull(6, Types.BIGINT);
            if (transcripcion != null) ps.setString(7, transcripcion); else ps.setNull(7, Types.CLOB);
            ps.setString(8, rutaArchivo);
            if (audioBase64 != null) ps.setString(9, audioBase64); else ps.setNull(9, Types.CLOB);
            if (mime != null) ps.setString(10, mime); else ps.setNull(10, Types.VARCHAR);
            if (duracionSeg != null) ps.setInt(11, duracionSeg); else ps.setNull(11, Types.INTEGER);
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
        String sql = "SELECT id, fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg " +
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
                    String emisorNombre = rs.getString(5);
                    Long receptor = rs.getObject(6) != null ? rs.getLong(6) : null;
                    String receptorNombre = rs.getString(7);
                    Long canal = rs.getObject(8) != null ? rs.getLong(8) : null;
                    boolean esAudio = rs.getBoolean(9);
                    if (esAudio) {
                        String ruta = rs.getString(11);
                        String transcripcion = rs.getString(10);
                        String audioBase64 = rs.getString(12);
                        String audioMime = rs.getString(13);
                        Integer duracion = rs.getObject(14) != null ? rs.getInt(14) : null;
                        com.arquitectura.entidades.AudioMensajeLocal a = new com.arquitectura.entidades.AudioMensajeLocal();
                        a.setId(id);
                        a.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
                        a.setTipo(tipo);
                        a.setEmisor(emisor);
                        a.setEmisorNombre(emisorNombre);
                        a.setReceptor(receptor);
                        a.setReceptorNombre(receptorNombre);
                        a.setCanalId(canal);
                        a.setRutaArchivo(ruta);
                        a.setTranscripcion(transcripcion);
                        if (audioBase64 != null) a.setAudioBase64(audioBase64);
                        if (audioMime != null) a.setMime(audioMime);
                        if (duracion != null) a.setDuracionSeg(duracion);
                        res.add(a);
                    } else {
                        String texto = rs.getString(10);
                        com.arquitectura.entidades.TextoMensajeLocal t = new com.arquitectura.entidades.TextoMensajeLocal();
                        t.setId(id);
                        t.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
                        t.setTipo(tipo);
                        t.setEmisor(emisor);
                        t.setEmisorNombre(emisorNombre);
                        t.setReceptor(receptor);
                        t.setReceptorNombre(receptorNombre);
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
        String sql = "SELECT id, fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg " +
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
                    String emisorNombre = rs.getString(5);
                    Long receptor = rs.getObject(6) != null ? rs.getLong(6) : null;
                    String receptorNombre = rs.getString(7);
                    boolean esAudio = rs.getBoolean(9);
                    if (esAudio) {
                        String ruta = rs.getString(11);
                        String transcripcion = rs.getString(10);
                        String audioBase64 = rs.getString(12);
                        String audioMime = rs.getString(13);
                        Integer duracion = rs.getObject(14) != null ? rs.getInt(14) : null;
                        com.arquitectura.entidades.AudioMensajeLocal a = new com.arquitectura.entidades.AudioMensajeLocal();
                        a.setId(id);
                        a.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
                        a.setTipo(tipo);
                        a.setEmisor(emisor);
                        a.setEmisorNombre(emisorNombre);
                        a.setReceptor(receptor);
                        a.setReceptorNombre(receptorNombre);
                        a.setRutaArchivo(ruta);
                        a.setTranscripcion(transcripcion);
                        if (audioBase64 != null) a.setAudioBase64(audioBase64);
                        if (audioMime != null) a.setMime(audioMime);
                        if (duracion != null) a.setDuracionSeg(duracion);
                        res.add(a);
                    } else {
                        String texto = rs.getString(10);
                        com.arquitectura.entidades.TextoMensajeLocal t = new com.arquitectura.entidades.TextoMensajeLocal();
                        t.setId(id);
                        t.setTimeStamp(ts != null ? ts.toLocalDateTime() : null);
                        t.setTipo(tipo);
                        t.setEmisor(emisor);
                        t.setEmisorNombre(emisorNombre);
                        t.setReceptor(receptor);
                        t.setReceptorNombre(receptorNombre);
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

    public long insertarDesdeServidorTexto(Long serverId, java.sql.Timestamp serverTs, Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String contenido, String tipo) throws SQLException {
        if (serverId != null && intentarActualizarCoincidenciaLocal(serverId, serverTs, emisorId, emisorNombre, receptorId, receptorNombre, canalId, false, contenido, null, null, null, null)) {
            return 0L;
        }
        if (existePorServerId(serverId)) return 0L;
        if (existePorCampos(emisorId, receptorId, canalId, false, contenido, null, serverTs)) return 0L;
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, server_id, server_ts) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, ?, NULL, ?, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, serverTs);
            ps.setString(2, tipo != null ? tipo : "TEXTO");
            ps.setLong(3, emisorId != null ? emisorId : 0L);
            if (emisorNombre != null) ps.setString(4, emisorNombre); else ps.setNull(4, Types.VARCHAR);
            if (receptorId != null) ps.setLong(5, receptorId); else ps.setNull(5, Types.BIGINT);
            if (receptorNombre != null) ps.setString(6, receptorNombre); else ps.setNull(6, Types.VARCHAR);
            if (canalId != null) ps.setLong(7, canalId); else ps.setNull(7, Types.BIGINT);
            ps.setString(8, contenido != null ? contenido : "");
            if (serverId != null) ps.setLong(9, serverId); else ps.setNull(9, Types.BIGINT);
            if (serverTs != null) ps.setTimestamp(10, serverTs); else ps.setNull(10, Types.TIMESTAMP);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return 0L;
    }

    public long insertarDesdeServidorAudioConRuta(Long serverId, java.sql.Timestamp serverTs, Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String transcripcion, String tipo, String rutaArchivo, String audioBase64, String mime, Integer duracionSeg) throws SQLException {
        if (serverId != null && intentarActualizarCoincidenciaLocal(serverId, serverTs, emisorId, emisorNombre, receptorId, receptorNombre, canalId, true, transcripcion, rutaArchivo, audioBase64, mime, duracionSeg)) {
            return 0L;
        }
        if (existePorServerId(serverId)) return 0L;
        if (existePorCampos(emisorId, receptorId, canalId, true, null, rutaArchivo, serverTs)) return 0L;
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg, server_id, server_ts) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, serverTs);
            ps.setString(2, tipo != null ? tipo : "AUDIO");
            ps.setLong(3, emisorId != null ? emisorId : 0L);
            if (emisorNombre != null) ps.setString(4, emisorNombre); else ps.setNull(4, Types.VARCHAR);
            if (receptorId != null) ps.setLong(5, receptorId); else ps.setNull(5, Types.BIGINT);
            if (receptorNombre != null) ps.setString(6, receptorNombre); else ps.setNull(6, Types.VARCHAR);
            if (canalId != null) ps.setLong(7, canalId); else ps.setNull(7, Types.BIGINT);
            if (transcripcion != null) ps.setString(8, transcripcion); else ps.setNull(8, Types.CLOB);
            ps.setString(9, rutaArchivo);
            if (audioBase64 != null) ps.setString(10, audioBase64); else ps.setNull(10, Types.CLOB);
            if (mime != null) ps.setString(11, mime); else ps.setNull(11, Types.VARCHAR);
            if (duracionSeg != null) ps.setInt(12, duracionSeg); else ps.setNull(12, Types.INTEGER);
            if (serverId != null) ps.setLong(13, serverId); else ps.setNull(13, Types.BIGINT);
            if (serverTs != null) ps.setTimestamp(14, serverTs); else ps.setNull(14, Types.TIMESTAMP);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return 0L;
    }

    private boolean intentarActualizarCoincidenciaLocal(Long serverId, java.sql.Timestamp serverTs, Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, boolean esAudio, String texto, String rutaArchivo, String audioBase64, String mime, Integer duracionSeg) throws SQLException {
        boolean intentoPorRuta = esAudio && rutaArchivo != null && !rutaArchivo.isEmpty();
        int intentos = intentoPorRuta ? 2 : 1;
        for (int intento = 0; intento < intentos; intento++) {
            boolean usarRuta = intento == 0 && intentoPorRuta;
            String campoComparacion = usarRuta ? "ruta_audio" : "texto";
            String valorComparacion = usarRuta ? rutaArchivo : texto;

            StringBuilder sql = new StringBuilder("SELECT id FROM mensajes WHERE server_id IS NULL AND emisor_id = ? AND ");
            if (receptorId != null) {
                sql.append("receptor_id = ? AND ");
            } else {
                sql.append("receptor_id IS NULL AND ");
            }
            if (canalId != null) {
                sql.append("canal_id = ? AND ");
            } else {
                sql.append("canal_id IS NULL AND ");
            }
            sql.append("es_audio = ? AND ");
            if (valorComparacion == null) {
                sql.append(campoComparacion).append(" IS NULL ");
            } else {
                sql.append(campoComparacion).append(" = ? ");
            }
            sql.append("ORDER BY fecha_envio DESC LIMIT 1");

            try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
                 PreparedStatement ps = cn.prepareStatement(sql.toString())) {
                int idx = 1;
                ps.setLong(idx++, emisorId != null ? emisorId : 0L);
                if (receptorId != null) ps.setLong(idx++, receptorId);
                if (canalId != null) ps.setLong(idx++, canalId);
                ps.setBoolean(idx++, esAudio);
                if (valorComparacion != null) ps.setString(idx++, valorComparacion);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) continue;
                    long id = rs.getLong(1);
                    try (PreparedStatement up = cn.prepareStatement(
                            "UPDATE mensajes SET server_id = ?, server_ts = ?, emisor_nombre = COALESCE(emisor_nombre, ?), receptor_nombre = COALESCE(receptor_nombre, ?), texto = COALESCE(texto, ?), audio_base64 = COALESCE(audio_base64, ?), audio_mime = COALESCE(audio_mime, ?), audio_duracion_seg = COALESCE(audio_duracion_seg, ?) WHERE id = ?")) {
                        up.setLong(1, serverId);
                        if (serverTs != null) up.setTimestamp(2, serverTs); else up.setNull(2, Types.TIMESTAMP);
                        if (emisorNombre != null) up.setString(3, emisorNombre); else up.setNull(3, Types.VARCHAR);
                        if (receptorNombre != null) up.setString(4, receptorNombre); else up.setNull(4, Types.VARCHAR);
                        if (texto != null) up.setString(5, texto); else up.setNull(5, Types.CLOB);
                        if (audioBase64 != null) up.setString(6, audioBase64); else up.setNull(6, Types.CLOB);
                        if (mime != null) up.setString(7, mime); else up.setNull(7, Types.VARCHAR);
                        if (duracionSeg != null) up.setInt(8, duracionSeg); else up.setNull(8, Types.INTEGER);
                        up.setLong(9, id);
                        return up.executeUpdate() > 0;
                    }
                }
            }
        }
        return false;
    }
}
