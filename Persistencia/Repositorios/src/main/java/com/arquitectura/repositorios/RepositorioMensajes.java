package com.arquitectura.repositorios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Objects;

import com.arquitectura.config.ProveedorConexionCliente;

public class RepositorioMensajes {

    private long contextoActual() {
        Long ctx = ProveedorConexionCliente.instancia().obtenerContextoUsuarioId();
        return ctx != null ? ctx : 0L;
    }

    public long insertarMensajeTexto(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String contenido, String tipo) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, contexto_usuario_id) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, FALSE, ?, NULL, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipo != null ? tipo : "TEXTO");
            ps.setLong(2, emisorId);
            if (emisorNombre != null) ps.setString(3, emisorNombre); else ps.setNull(3, Types.VARCHAR);
            if (receptorId != null) ps.setLong(4, receptorId); else ps.setNull(4, Types.BIGINT);
            if (receptorNombre != null) ps.setString(5, receptorNombre); else ps.setNull(5, Types.VARCHAR);
            if (canalId != null) ps.setLong(6, canalId); else ps.setNull(6, Types.BIGINT);
            ps.setString(7, contenido);
            ps.setLong(8, contextoActual());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo obtener el id generado del mensaje de texto");
    }

    public long insertarMensajeAudio(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String transcripcion, String tipo) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg, contexto_usuario_id) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, TRUE, ?, NULL, NULL, NULL, NULL, ?)";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, tipo != null ? tipo : "AUDIO");
            ps.setLong(2, emisorId);
            if (emisorNombre != null) ps.setString(3, emisorNombre); else ps.setNull(3, Types.VARCHAR);
            if (receptorId != null) ps.setLong(4, receptorId); else ps.setNull(4, Types.BIGINT);
            if (receptorNombre != null) ps.setString(5, receptorNombre); else ps.setNull(5, Types.VARCHAR);
            if (canalId != null) ps.setLong(6, canalId); else ps.setNull(6, Types.BIGINT);
            if (transcripcion != null) ps.setString(7, transcripcion); else ps.setNull(7, Types.CLOB);
            ps.setLong(8, contextoActual());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("No se pudo obtener el id generado del mensaje de audio");
    }

    public long insertarMensajeAudioConRuta(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String transcripcion, String tipo, String rutaArchivo, String audioBase64, String mime, Integer duracionSeg) throws SQLException {
        validarDestino(receptorId, canalId);
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg, contexto_usuario_id) " +
                "VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?, ?)";
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
            ps.setLong(12, contextoActual());
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
                     "FROM mensajes WHERE canal_id = ? AND contexto_usuario_id = ? ORDER BY fecha_envio ASC" + (limit != null ? " LIMIT ?" : "");
        java.util.List<com.arquitectura.entidades.MensajeLocal> res = new java.util.ArrayList<>();
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, canalId);
            ps.setLong(2, contextoActual());
            if (limit != null) ps.setInt(3, limit);
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
        if (otroId == null) return java.util.List.of();
        boolean idPropioValido = miId != null && miId > 0;
        String sql = "SELECT id, fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg " +
                     "FROM mensajes WHERE canal_id IS NULL AND contexto_usuario_id = ? AND " +
                     (idPropioValido
                             ? "((emisor_id = ? AND receptor_id = ?) OR (emisor_id = ? AND receptor_id = ?)) "
                             : "(emisor_id = ? OR receptor_id = ?) ") +
                     "ORDER BY fecha_envio ASC" + (limit != null ? " LIMIT ?" : "");
        java.util.List<com.arquitectura.entidades.MensajeLocal> res = new java.util.ArrayList<>();
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, contextoActual());
            if (idPropioValido) {
                ps.setLong(idx++, miId);
                ps.setLong(idx++, otroId);
                ps.setLong(idx++, otroId);
                ps.setLong(idx++, miId);
            } else {
                ps.setLong(idx++, otroId);
                ps.setLong(idx++, otroId);
            }
            if (limit != null) ps.setInt(idx, limit);
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
             PreparedStatement ps = cn.prepareStatement("SELECT 1 FROM mensajes WHERE contexto_usuario_id = ? AND server_id = ? LIMIT 1")) {
            ps.setLong(1, contextoActual());
            ps.setLong(2, serverId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean existePorCampos(Long emisorId, Long receptorId, Long canalId, boolean esAudio, String texto, String rutaArchivo, java.sql.Timestamp ts) throws SQLException {
        String sql = "SELECT 1 FROM mensajes WHERE contexto_usuario_id = ? AND emisor_id = ? AND " +
                (receptorId != null ? "receptor_id = ?" : "receptor_id IS NULL") +
                (canalId != null ? " AND canal_id = ?" : " AND canal_id IS NULL") +
                " AND es_audio = ? AND fecha_envio = ? AND " + (esAudio ? "ruta_audio = ?" : "texto = ?") + " LIMIT 1";
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            int idx = 1;
            ps.setLong(idx++, contextoActual());
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

        MensajeExistente existente = obtenerMensajePorServerId(serverId);
        if (existente != null) {
            if (coincideMensajeTexto(existente, serverTs, emisorId, receptorId, canalId, contenido)) {
                return 0L;
            }
            logServerIdReutilizado(serverId, existente, emisorId, receptorId, canalId, false, serverTs);
            serverId = null;
        }

        if (existePorCampos(emisorId, receptorId, canalId, false, contenido, null, serverTs)) return 0L;
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, server_id, server_ts, contexto_usuario_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, ?, NULL, ?, ?, ?)";
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
            ps.setLong(11, contextoActual());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return 0L;
    }

    public long insertarDesdeServidorAudioConRuta(Long serverId, java.sql.Timestamp serverTs, Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String transcripcion, String tipo, String rutaArchivo, String audioBase64, String mime, Integer duracionSeg) throws SQLException {
        if (serverId != null && intentarActualizarCoincidenciaLocal(serverId, serverTs, emisorId, emisorNombre, receptorId, receptorNombre, canalId, true, transcripcion, rutaArchivo, audioBase64, mime, duracionSeg)) {
            return 0L;
        }

        MensajeExistente existente = obtenerMensajePorServerId(serverId);
        if (existente != null) {
            if (coincideMensajeAudio(existente, serverTs, emisorId, receptorId, canalId, rutaArchivo)) {
                return 0L;
            }
            logServerIdReutilizado(serverId, existente, emisorId, receptorId, canalId, true, serverTs);
            serverId = null;
        }

        if (existePorCampos(emisorId, receptorId, canalId, true, null, rutaArchivo, serverTs)) return 0L;
        String sql = "INSERT INTO mensajes (fecha_envio, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, ruta_audio, audio_base64, audio_mime, audio_duracion_seg, server_id, server_ts, contexto_usuario_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?, ?, ?, ?)";
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
            ps.setLong(15, contextoActual());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) return rs.getLong(1); }
        }
        return 0L;
    }

    private boolean intentarActualizarCoincidenciaLocal(Long serverId, java.sql.Timestamp serverTs, Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, boolean esAudio, String texto, String rutaArchivo, String audioBase64, String mime, Integer duracionSeg) throws SQLException {
        boolean intentoPorRuta = esAudio && rutaArchivo != null && !rutaArchivo.isEmpty();
        int intentos = intentoPorRuta ? 2 : 1;
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion()) {
            for (int intento = 0; intento < intentos; intento++) {
                boolean usarRuta = intento == 0 && intentoPorRuta;
                String campoComparacion = usarRuta ? "ruta_audio" : "texto";
                String valorComparacion = usarRuta ? rutaArchivo : texto;

                Long idEncontrado = buscarCoincidenciaLocal(cn, emisorId, receptorId, canalId, esAudio, campoComparacion, valorComparacion);
                if (idEncontrado == null && valorComparacion != null) {
                    // Si no se halló coincidencia exacta y el servidor nos envía información adicional
                    // (por ejemplo, transcripción disponible solo en el servidor), intentar coincidir
                    // con registros locales donde aún no se haya almacenado ese dato.
                    idEncontrado = buscarCoincidenciaLocal(cn, emisorId, receptorId, canalId, esAudio, campoComparacion, null);
                }

                if (idEncontrado != null && actualizarMensajeCoincidente(cn, idEncontrado, serverId, serverTs, emisorNombre, receptorNombre, texto, audioBase64, mime, duracionSeg)) {
                    return true;
                }
            }
        }
        if (esAudio && rutaArchivo != null && !rutaArchivo.isBlank()) {
            return intentarActualizarCoincidenciaLocalPorRutaNormalizada(serverId, serverTs, emisorId, emisorNombre, receptorId, receptorNombre, canalId, texto, rutaArchivo, audioBase64, mime, duracionSeg);
        }
        return false;
    }

    private Long buscarCoincidenciaLocal(Connection cn, Long emisorId, Long receptorId, Long canalId, boolean esAudio, String campoComparacion, String valorComparacion) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id FROM mensajes WHERE server_id IS NULL AND contexto_usuario_id = ? AND emisor_id = ? AND ");
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

        try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setLong(idx++, contextoActual());
            ps.setLong(idx++, emisorId != null ? emisorId : 0L);
            if (receptorId != null) ps.setLong(idx++, receptorId);
            if (canalId != null) ps.setLong(idx++, canalId);
            ps.setBoolean(idx++, esAudio);
            if (valorComparacion != null) ps.setString(idx++, valorComparacion);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return null;
    }

    private boolean intentarActualizarCoincidenciaLocalPorRutaNormalizada(Long serverId, java.sql.Timestamp serverTs, Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String texto, String rutaArchivo, String audioBase64, String mime, Integer duracionSeg) throws SQLException {
        String rutaNormalizada = normalizarRuta(rutaArchivo);
        if (rutaNormalizada == null || rutaNormalizada.isEmpty()) return false;

        StringBuilder sql = new StringBuilder("SELECT id, ruta_audio FROM mensajes WHERE server_id IS NULL AND contexto_usuario_id = ? AND emisor_id = ? AND ");
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
        sql.append("es_audio = ? ORDER BY fecha_envio DESC LIMIT 5");

        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion();
             PreparedStatement ps = cn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setLong(idx++, contextoActual());
            ps.setLong(idx++, emisorId != null ? emisorId : 0L);
            if (receptorId != null) ps.setLong(idx++, receptorId);
            if (canalId != null) ps.setLong(idx++, canalId);
            ps.setBoolean(idx++, true);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String rutaLocal = rs.getString(2);
                    if (rutaLocal == null || rutaLocal.isBlank()) continue;
                    String rutaLocalNorm = normalizarRuta(rutaLocal);
                    if (rutaLocalNorm == null) continue;
                    if (rutaLocalNorm.equalsIgnoreCase(rutaNormalizada) || rutaLocal.endsWith(rutaNormalizada)) {
                        if (actualizarMensajeCoincidente(cn, id, serverId, serverTs, emisorNombre, receptorNombre, texto, audioBase64, mime, duracionSeg)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean actualizarMensajeCoincidente(Connection cn, long id, Long serverId, java.sql.Timestamp serverTs, String emisorNombre, String receptorNombre, String texto, String audioBase64, String mime, Integer duracionSeg) throws SQLException {
        try (PreparedStatement up = cn.prepareStatement(
                "UPDATE mensajes SET server_id = ?, server_ts = ?, emisor_nombre = COALESCE(emisor_nombre, ?), receptor_nombre = COALESCE(receptor_nombre, ?), texto = COALESCE(texto, ?), audio_base64 = COALESCE(audio_base64, ?), audio_mime = COALESCE(audio_mime, ?), audio_duracion_seg = COALESCE(audio_duracion_seg, ?) WHERE id = ?")) {
            if (serverId != null) up.setLong(1, serverId); else up.setNull(1, Types.BIGINT);
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

    private MensajeExistente obtenerMensajePorServerId(Long serverId) throws SQLException {
        if (serverId == null) return null;
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion()) {
            return obtenerMensajePorServerId(cn, serverId);
        }
    }

    private MensajeExistente obtenerMensajePorServerId(Connection cn, Long serverId) throws SQLException {
        if (serverId == null) return null;
        String sql = "SELECT emisor_id, receptor_id, canal_id, es_audio, texto, ruta_audio, server_ts FROM mensajes WHERE contexto_usuario_id = ? AND server_id = ? LIMIT 1";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, contextoActual());
            ps.setLong(2, serverId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Long emisor = rs.getLong("emisor_id");
                    if (rs.wasNull()) emisor = null;
                    Long receptor = rs.getLong("receptor_id");
                    if (rs.wasNull()) receptor = null;
                    Long canal = rs.getLong("canal_id");
                    if (rs.wasNull()) canal = null;
                    boolean esAudio = rs.getBoolean("es_audio");
                    String texto = rs.getString("texto");
                    String ruta = rs.getString("ruta_audio");
                    java.sql.Timestamp ts = rs.getTimestamp("server_ts");
                    return new MensajeExistente(emisor, receptor, canal, esAudio, texto, ruta, ts);
                }
            }
        }
        return null;
    }

    private boolean coincideMensajeTexto(MensajeExistente existente, java.sql.Timestamp serverTs, Long emisorId, Long receptorId, Long canalId, String contenido) {
        if (existente == null || existente.esAudio) return false;
        return Objects.equals(normalizarId(existente.emisorId), normalizarId(emisorId))
                && Objects.equals(normalizarId(existente.receptorId), normalizarId(receptorId))
                && Objects.equals(existente.canalId, canalId)
                && Objects.equals(valorTextoSeguro(existente.texto), valorTextoSeguro(contenido))
                && Objects.equals(existente.serverTs, serverTs);
    }

    private boolean coincideMensajeAudio(MensajeExistente existente, java.sql.Timestamp serverTs, Long emisorId, Long receptorId, Long canalId, String rutaArchivo) {
        if (existente == null || !existente.esAudio) return false;
        return Objects.equals(normalizarId(existente.emisorId), normalizarId(emisorId))
                && Objects.equals(normalizarId(existente.receptorId), normalizarId(receptorId))
                && Objects.equals(existente.canalId, canalId)
                && Objects.equals(normalizarRuta(existente.rutaAudio), normalizarRuta(rutaArchivo))
                && Objects.equals(existente.serverTs, serverTs);
    }

    private void logServerIdReutilizado(Long serverId, MensajeExistente existente, Long nuevoEmisor, Long nuevoReceptor, Long nuevoCanal, boolean esAudio, java.sql.Timestamp nuevoServerTs) {
        if (serverId == null || existente == null) return;
        try {
            System.out.println("[RepositorioMensajes] Advertencia: server_id=" + serverId + " reutilizado para " + (esAudio ? "audio" : "texto") +
                    ". Se almacenará localmente sin server_id. existente(emisor=" + normalizarId(existente.emisorId) +
                    ", receptor=" + normalizarId(existente.receptorId) + ", canal=" + existente.canalId + ", ts=" + existente.serverTs +
                    ") nuevo(emisor=" + normalizarId(nuevoEmisor) + ", receptor=" + normalizarId(nuevoReceptor) + ", canal=" + nuevoCanal + ", ts=" + nuevoServerTs + ")");
        } catch (Exception ignored) {}
    }

    private Long normalizarId(Long id) {
        return id != null ? id : 0L;
    }

    private String valorTextoSeguro(String texto) {
        return texto != null ? texto : "";
    }

    private static class MensajeExistente {
        final Long emisorId;
        final Long receptorId;
        final Long canalId;
        final boolean esAudio;
        final String texto;
        final String rutaAudio;
        final java.sql.Timestamp serverTs;

        MensajeExistente(Long emisorId, Long receptorId, Long canalId, boolean esAudio, String texto, String rutaAudio, java.sql.Timestamp serverTs) {
            this.emisorId = emisorId;
            this.receptorId = receptorId;
            this.canalId = canalId;
            this.esAudio = esAudio;
            this.texto = texto;
            this.rutaAudio = rutaAudio;
            this.serverTs = serverTs;
        }
    }

    private String normalizarRuta(String ruta) {
        if (ruta == null) return null;
        String limpia = ruta.trim();
        if (limpia.isEmpty()) return "";
        int queryIdx = limpia.indexOf('?');
        if (queryIdx >= 0) limpia = limpia.substring(0, queryIdx);
        limpia = limpia.replace('\\', '/');
        int idx = limpia.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < limpia.length()) {
            limpia = limpia.substring(idx + 1);
        }
        return limpia;
    }

    /**
     * Verifica si ya existe un mensaje con el serverId dado usando una consulta optimizada
     */
    private boolean existeServerId(Connection cn, Long serverId) throws SQLException {
        return obtenerMensajePorServerId(cn, serverId) != null;
    }

    /**
     * Inserta múltiples mensajes del servidor de manera optimizada usando batch processing
     * @param mensajesJson Lista de objetos JSON de mensajes
     * @return número de mensajes insertados exitosamente
     */
    public int insertarMensajesDesdeServidorBatch(java.util.List<String> mensajesJson) throws SQLException {
        if (mensajesJson == null || mensajesJson.isEmpty()) return 0;

        int insertados = 0;
        long contexto = contextoActual();
        
        try (Connection cn = ProveedorConexionCliente.instancia().obtenerConexion()) {
            cn.setAutoCommit(false); // Iniciar transacción

            // Preparar statements para inserción
            String sqlTexto = "INSERT INTO mensajes (fecha_envio, server_id, server_ts, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, contexto_usuario_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?, ?)";
            String sqlAudio = "INSERT INTO mensajes (fecha_envio, server_id, server_ts, tipo, emisor_id, emisor_nombre, receptor_id, receptor_nombre, canal_id, es_audio, texto, audio_base64, audio_mime, audio_duracion_seg, contexto_usuario_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?)";

            try (PreparedStatement psTexto = cn.prepareStatement(sqlTexto);
                 PreparedStatement psAudio = cn.prepareStatement(sqlAudio)) {

                // Procesar cada mensaje
                for (String objJson : mensajesJson) {
                    try {
                        // Extraer campos básicos usando métodos simplificados
                        Long serverId = extraerLongSimple(objJson, "serverId", "mensajeId", "messageId", "id");

                        String tipoMsg = extraerTextoSimple(objJson, "tipo", "tipoMensaje");
                        Long emisor = extraerLongSimple(objJson, "emisor", "emisorId");
                        String emisorNombre = extraerTextoSimple(objJson, "emisorNombre", "nombreEmisor");
                        Long receptor = extraerLongSimple(objJson, "receptor", "receptorId");
                        String receptorNombre = extraerTextoSimple(objJson, "receptorNombre", "nombreReceptor");
                        Long canalId = extraerLongSimple(objJson, "canalId");
                        String timestamp = extraerTextoSimple(objJson, "timeStamp", "timestamp");
                        Boolean esAudio = extraerBooleanSimple(objJson, "esAudio", "audio");
                        
                        // Mejorar detección de audio basándose en el tipo de mensaje
                        if (esAudio == null && "AUDIO".equalsIgnoreCase(tipoMsg)) {
                            esAudio = true;
                        }
                        
                        java.sql.Timestamp serverTs = parseServerTimestamp(timestamp);

                        MensajeExistente existente = obtenerMensajePorServerId(cn, serverId);
                        String contenidoTexto = null;
                        String rutaAudio = null;
                        boolean esAudioMensaje = Boolean.TRUE.equals(esAudio);
                        if (!esAudioMensaje) {
                            String contenidoObjPrev = extraerObjetoContenido(objJson);
                            if (contenidoObjPrev != null) {
                                contenidoTexto = extraerTextoSimple(contenidoObjPrev, "contenido", "texto", "mensaje");
                            }
                            if (contenidoTexto == null) {
                                contenidoTexto = extraerTextoSimple(objJson, "contenido", "texto", "mensaje");
                            }
                        } else {
                            String contenidoObjPrev = extraerObjetoContenido(objJson);
                            if (contenidoObjPrev != null) {
                                rutaAudio = extraerTextoSimple(contenidoObjPrev, "rutaArchivo", "ruta", "rutaAudio");
                            }
                            if (rutaAudio == null) {
                                rutaAudio = extraerTextoSimple(objJson, "rutaArchivo", "ruta", "rutaAudio");
                            }
                        }

                        if (existente != null) {
                            boolean coincide = esAudioMensaje
                                    ? coincideMensajeAudio(existente, serverTs, emisor, receptor, canalId, rutaAudio)
                                    : coincideMensajeTexto(existente, serverTs, emisor, receptor, canalId, contenidoTexto);
                            if (coincide) {
                                continue;
                            }
                            logServerIdReutilizado(serverId, existente, emisor, receptor, canalId, esAudioMensaje, serverTs);
                            serverId = null;
                        }

                        // Decidir si es audio o texto
                        if (esAudioMensaje) {
                            // Mensaje de audio - extraer desde objeto contenido
                            String contenidoObj = extraerObjetoContenido(objJson);
                            String transcripcion = null;
                            String audioBase64 = null;
                            String mime = null;
                            Integer duracion = null;
                            
                            if (contenidoObj != null) {
                                // Extraer campos del objeto contenido
                                transcripcion = extraerTextoSimple(contenidoObj, "transcripcion", "transcripcionTexto");
                                audioBase64 = extraerTextoSimple(contenidoObj, "audioBase64", "base64", "audio");
                                mime = extraerTextoSimple(contenidoObj, "mime", "mimeType", "tipoMime");
                                duracion = extraerIntegerSimple(contenidoObj, "duracionSeg", "duracion");
                            }
                            
                            // Fallback: buscar directamente en el JSON raíz
                            if (transcripcion == null) transcripcion = extraerTextoSimple(objJson, "transcripcion", "texto");
                            if (audioBase64 == null) audioBase64 = extraerTextoSimple(objJson, "audioBase64", "base64");
                            if (mime == null) mime = extraerTextoSimple(objJson, "audioMime", "mime");
                            if (duracion == null) duracion = extraerIntegerSimple(objJson, "audioDuracionSeg", "duracion");

                            // Agregar al batch de audio
                            int idx = 1;
                            if (serverTs != null) psAudio.setTimestamp(idx++, serverTs); else psAudio.setNull(idx++, Types.TIMESTAMP);
                            if (serverId != null) psAudio.setLong(idx++, serverId); else psAudio.setNull(idx++, Types.BIGINT);
                            if (serverTs != null) psAudio.setTimestamp(idx++, serverTs); else psAudio.setNull(idx++, Types.TIMESTAMP);
                            psAudio.setString(idx++, tipoMsg != null ? tipoMsg : "AUDIO");
                            psAudio.setLong(idx++, emisor != null ? emisor : 0L);
                            if (emisorNombre != null) psAudio.setString(idx++, emisorNombre); else psAudio.setNull(idx++, Types.VARCHAR);
                            if (receptor != null) psAudio.setLong(idx++, receptor); else psAudio.setNull(idx++, Types.BIGINT);
                            if (receptorNombre != null) psAudio.setString(idx++, receptorNombre); else psAudio.setNull(idx++, Types.VARCHAR);
                            if (canalId != null) psAudio.setLong(idx++, canalId); else psAudio.setNull(idx++, Types.BIGINT);
                            if (transcripcion != null) psAudio.setString(idx++, transcripcion); else psAudio.setNull(idx++, Types.CLOB);
                            if (audioBase64 != null) psAudio.setString(idx++, audioBase64); else psAudio.setNull(idx++, Types.CLOB);
                            if (mime != null) psAudio.setString(idx++, mime); else psAudio.setNull(idx++, Types.VARCHAR);
                            if (duracion != null) psAudio.setInt(idx++, duracion); else psAudio.setNull(idx++, Types.INTEGER);
                            psAudio.setLong(idx++, contexto);
                            psAudio.addBatch();
                        } else {
                            // Mensaje de texto - extraer desde objeto contenido
                            // Agregar al batch de texto
                            int idx = 1;
                            if (serverTs != null) psTexto.setTimestamp(idx++, serverTs); else psTexto.setNull(idx++, Types.TIMESTAMP);
                            if (serverId != null) psTexto.setLong(idx++, serverId); else psTexto.setNull(idx++, Types.BIGINT);
                            if (serverTs != null) psTexto.setTimestamp(idx++, serverTs); else psTexto.setNull(idx++, Types.TIMESTAMP);
                            psTexto.setString(idx++, tipoMsg != null ? tipoMsg : "TEXTO");
                            psTexto.setLong(idx++, emisor != null ? emisor : 0L);
                            if (emisorNombre != null) psTexto.setString(idx++, emisorNombre); else psTexto.setNull(idx++, Types.VARCHAR);
                            if (receptor != null) psTexto.setLong(idx++, receptor); else psTexto.setNull(idx++, Types.BIGINT);
                            if (receptorNombre != null) psTexto.setString(idx++, receptorNombre); else psTexto.setNull(idx++, Types.VARCHAR);
                            if (canalId != null) psTexto.setLong(idx++, canalId); else psTexto.setNull(idx++, Types.BIGINT);
                            if (contenidoTexto != null) psTexto.setString(idx++, contenidoTexto); else psTexto.setNull(idx++, Types.CLOB);
                            psTexto.setLong(idx++, contexto);
                            psTexto.addBatch();
                        }
                        insertados++;
                    } catch (Exception e) {
                        // Log error pero continuar con otros mensajes
                        System.err.println("Error procesando mensaje en batch: " + e.getMessage());
                    }
                }

                // Ejecutar los batches
                psTexto.executeBatch();
                psAudio.executeBatch();
                cn.commit(); // Confirmar transacción
            } catch (SQLException e) {
                cn.rollback(); // Revertir en caso de error
                throw e;
            }
        }
        return insertados;
    }

    private java.sql.Timestamp parseServerTimestamp(String valor) {
        if (valor == null) {
            return new java.sql.Timestamp(System.currentTimeMillis());
        }
        String texto = valor.trim();
        if (texto.isEmpty()) {
            return new java.sql.Timestamp(System.currentTimeMillis());
        }
        try {
            return java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(texto));
        } catch (java.time.format.DateTimeParseException ignored) {
            try {
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(texto);
                return java.sql.Timestamp.from(odt.toInstant());
            } catch (java.time.format.DateTimeParseException ignored2) {
                try {
                    java.time.Instant inst = java.time.Instant.parse(texto);
                    return java.sql.Timestamp.from(inst);
                } catch (Exception ignored3) {
                    try {
                        String normalizado = texto.replace("Z", "").replace('T', ' ');
                        return java.sql.Timestamp.valueOf(normalizado);
                    } catch (Exception ignored4) {
                        return new java.sql.Timestamp(System.currentTimeMillis());
                    }
                }
            }
        }
    }

    // Métodos de extracción simplificados para mejor rendimiento
    private String extraerTextoSimple(String json, String... claves) {
        if (json == null) return null;
        for (String clave : claves) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + clave + "\"\\s*:\\s*\"([^\"]*)\"|\"" + clave + "\"\\s*:\\s*null");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                String valor = m.group(1);
                return valor != null && !valor.trim().isEmpty() ? valor : null;
            }
        }
        return null;
    }

    private Long extraerLongSimple(String json, String... claves) {
        if (json == null) return null;
        for (String clave : claves) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + clave + "\"\\s*:\\s*(\\d+)|\"" + clave + "\"\\s*:\\s*null");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                String valor = m.group(1);
                if (valor != null) {
                    try {
                        return Long.valueOf(valor);
                    } catch (NumberFormatException e) {
                        // Continuar con siguiente clave
                    }
                }
            }
        }
        return null;
    }

    private Boolean extraerBooleanSimple(String json, String... claves) {
        if (json == null) return null;
        for (String clave : claves) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + clave + "\"\\s*:\\s*(true|false)|\"" + clave + "\"\\s*:\\s*null");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                String valor = m.group(1);
                if (valor != null) {
                    return Boolean.valueOf(valor);
                }
            }
        }
        return null;
    }

    private Integer extraerIntegerSimple(String json, String... claves) {
        if (json == null) return null;
        for (String clave : claves) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + clave + "\"\\s*:\\s*(\\d+)|\"" + clave + "\"\\s*:\\s*null");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                String valor = m.group(1);
                if (valor != null) {
                    try {
                        return Integer.valueOf(valor);
                    } catch (NumberFormatException e) {
                        // Continuar con siguiente clave
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Extrae el objeto "contenido" del JSON principal
     */
    private String extraerObjetoContenido(String json) {
        if (json == null) return null;
        
        // Buscar el inicio del objeto contenido
        int contenidoIndex = json.indexOf("\"contenido\"");
        if (contenidoIndex == -1) return null;
        
        // Buscar el inicio del objeto después de los dos puntos
        int startIndex = json.indexOf("{", contenidoIndex);
        if (startIndex == -1) return null;
        
        // Contar llaves para encontrar el final del objeto
        int braceCount = 1;
        int endIndex = startIndex + 1;
        
        while (endIndex < json.length() && braceCount > 0) {
            char c = json.charAt(endIndex);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
            }
            endIndex++;
        }
        
        if (braceCount == 0) {
            return json.substring(startIndex, endIndex);
        }
        
        return null;
    }
}
