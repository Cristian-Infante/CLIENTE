package com.arquitectura.servicios;

import com.arquitectura.infra.net.OyenteMensajesChat;
import com.arquitectura.repositorios.RepositorioMensajes;

import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Observa EVENTs del servidor (nuevos mensajes) y los persiste en la BD local,
 * notificando a la capa de presentación a través de ServicioEventosMensajes.
 */
public class ObservadorEventosChat implements OyenteMensajesChat {
    private final RepositorioMensajes repo = new RepositorioMensajes();
    private final ExecutorService ioPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "obs-eventos-chat-io");
        t.setDaemon(true);
        return t;
    });

    private static final ObservadorEventosChat INSTANCE = new ObservadorEventosChat();
    private static final java.util.Map<ServicioConexionChat, Boolean> REGISTRADOS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static ObservadorEventosChat instancia() { return INSTANCE; }
    public void registrarEn(ServicioConexionChat sc) {
        if (sc == null) return;
        synchronized (REGISTRADOS) {
            if (!Boolean.TRUE.equals(REGISTRADOS.get(sc))) {
                try { sc.registrarOyente(this); REGISTRADOS.put(sc, true); System.out.println("[ObservadorEventosChat] Registrado sobre la conexion TCP (" + sc + ")"); } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void alRecibirMensaje(String mensaje) {
        if (mensaje == null) return;
        String compact = mensaje.replaceAll("\\s+", "");
        // Procesar sincronizaciones masivas (log y encolar)
        if (compact.contains("\"command\":\"MESSAGE_SYNC\"")) {
            String raw = mensaje.replace('\n',' ').replace('\r',' ');
            String payload = extraerObjetoPayload(raw);
            if (payload == null) payload = raw;
            String ultima = extraerCampo(payload, "ultimaSincronizacion");
            Long total = null;
            try { String t = extraerCampo(payload, "totalMensajes"); if (t != null) total = Long.parseLong(t); } catch (Exception ignored) {}
            java.util.List<String> arr = extraerObjetosDeArreglo(payload, "mensajes");
            StringBuilder sb = new StringBuilder();
            sb.append("\n==== MESSAGE_SYNC recibido ====\n");
            sb.append("- totalMensajes: ").append(total).append('\n');
            sb.append("- itemsEnArray: ").append(arr != null ? arr.size() : 0).append('\n');
            sb.append("- ultimaSincronizacion: ").append(ultima).append('\n');
            sb.append("- payload: ").append(payload).append('\n');
            sb.append("================================\n");
            System.out.println(sb.toString());
            ioPool.submit(() -> procesarMessageSync(mensaje));
            return;
        }
        // Solo procesar comandos de mensaje nuevos
        if (!(compact.contains("\"command\":\"NEW_MESSAGE\"") || compact.contains("\"command\":\"NEW_CHANNEL_MESSAGE\""))) return;
        // Log organizado para NEW_CHANNEL_MESSAGE recibido
        if (compact.contains("\"command\":\"NEW_CHANNEL_MESSAGE\"")) {
            String raw = mensaje.replace('\n',' ').replace('\r',' ');
            String payload = extraerObjetoPayload(raw);
            if (payload == null) payload = raw;
            Long canalId = extraerLong(payload, "canalId");
            Long id = extraerLong(payload, "id");
            Long emisor = extraerLong(payload, "emisor");
            String tipo = extraerCampo(payload, "tipo");
            String timeStamp = extraerCampo(payload, "timeStamp");
            String contenido = extraerCampoPermitirNulo(payload, "contenido");
            String rutaArchivo = extraerCampo(payload, "rutaArchivo");
            String transcripcion = extraerCampoPermitirNulo(payload, "transcripcion");
            StringBuilder sb = new StringBuilder();
            sb.append("\n==== NEW_CHANNEL_MESSAGE recibido ====\n");
            sb.append("- id: ").append(id).append('\n');
            sb.append("- canalId: ").append(canalId).append('\n');
            sb.append("- tipo: ").append(tipo).append('\n');
            sb.append("- emisor: ").append(emisor).append('\n');
            sb.append("- timeStamp: ").append(timeStamp).append('\n');
            if (contenido != null) sb.append("- contenido: ").append(contenido).append('\n');
            if (rutaArchivo != null) sb.append("- rutaArchivo: ").append(rutaArchivo).append('\n');
            if (transcripcion != null) sb.append("- transcripcion: ").append(transcripcion).append('\n');
            sb.append("====================================\n");
            System.out.println(sb.toString());
        }
        ioPool.submit(() -> procesarEventoMensaje(mensaje));
    }

    private void procesarMessageSync(String json) {
        try {
            String compact = json.replace('\n',' ').replace('\r',' ');
            String payload = extraerObjetoPayload(compact);
            if (payload == null) payload = compact;
            // Log al inicio del procesamiento para asegurar visibilidad en cualquier ruta
            String ultima = extraerCampo(payload, "ultimaSincronizacion");
            Long total = null; try { String t = extraerCampo(payload, "totalMensajes"); if (t != null) total = Long.parseLong(t); } catch (Exception ignored) {}
            java.util.List<String> prev = extraerObjetosDeArreglo(payload, "mensajes");
            StringBuilder preSb = new StringBuilder();
            preSb.append("\n==== MESSAGE_SYNC (procesando) ====\n");
            preSb.append("- totalMensajes: ").append(total).append('\n');
            preSb.append("- itemsEnArray: ").append(prev != null ? prev.size() : 0).append('\n');
            preSb.append("- ultimaSincronizacion: ").append(ultima).append('\n');
            preSb.append("================================\n");
            System.out.println(preSb.toString());
            java.util.List<String> objetos = extraerObjetosDeArreglo(payload, "mensajes");
            java.util.Set<Long> canalesNotificar = new java.util.HashSet<>();
            java.util.Set<Long> privadosNotificar = new java.util.HashSet<>();
            int insertados = 0;
            for (String obj : objetos) {
                String tipoMsg = extraerCampo(obj, "tipo");
                Long emisor = extraerLong(obj, "emisor");
                Long receptor = extraerLong(obj, "receptor");
                Long canalId = extraerLong(obj, "canalId");
                Long serverId = extraerLong(obj, "id");
                java.sql.Timestamp serverTs = parseTimestamp(extraerCampo(obj, "timeStamp"));
                boolean esCanal = canalId != null;
                if ("AUDIO".equalsIgnoreCase(tipoMsg)) {
                    String ruta = extraerCampo(obj, "rutaArchivo");
                    String transcripcion = extraerCampo(obj, "transcripcion");
                    if (esCanal) {
                        long idIns = repo.insertarDesdeServidorAudioConRuta(serverId, serverTs, emisor != null ? emisor : 0L, null, canalId, transcripcion, "AUDIO", ruta);
                        if (idIns > 0) insertados++;
                        canalesNotificar.add(canalId);
                    } else {
                        Long chatOtro = emisor != null ? emisor : receptor;
                        long idIns = repo.insertarDesdeServidorAudioConRuta(serverId, serverTs, emisor != null ? emisor : 0L, receptor, null, transcripcion, "AUDIO", ruta);
                        if (idIns > 0) insertados++;
                        if (chatOtro != null) privadosNotificar.add(chatOtro);
                    }
                } else {
                    String contenido = extraerCampoPermitirNulo(obj, "contenido");
                    if (esCanal) {
                        long idIns = repo.insertarDesdeServidorTexto(serverId, serverTs, emisor != null ? emisor : 0L, null, canalId, contenido != null ? contenido : "", "TEXTO");
                        if (idIns > 0) insertados++;
                        canalesNotificar.add(canalId);
                    } else {
                        Long chatOtro = emisor != null ? emisor : receptor;
                        long idIns = repo.insertarDesdeServidorTexto(serverId, serverTs, emisor != null ? emisor : 0L, receptor, null, contenido != null ? contenido : "", "TEXTO");
                        if (idIns > 0) insertados++;
                        if (chatOtro != null) privadosNotificar.add(chatOtro);
                    }
                }
            }
            for (Long c : canalesNotificar) ServicioEventosMensajes.instancia().notificarCanal(c);
            for (Long u : privadosNotificar) ServicioEventosMensajes.instancia().notificarPrivado(u);
            System.out.println("[ObservadorEventosChat] MESSAGE_SYNC procesado. Insertados=" + insertados + ", canalesNotificar=" + canalesNotificar.size() + ", privadosNotificar=" + privadosNotificar.size());
        } catch (SQLException e) {
            System.out.println("[ObservadorEventosChat] Error insertando en BD (sync): " + e);
        } catch (Exception e) {
            System.out.println("[ObservadorEventosChat] Error procesando MESSAGE_SYNC: " + e + " json=" + json);
        }
    }

    private void procesarEventoMensaje(String json) {
        try {
            String compact = json.replace('\n',' ').replace('\r',' ');
            String command = extraerCampo(compact, "command");
            String payload = extraerObjetoPayload(compact);
            if (payload == null) payload = compact; // fallback
            String tipoMsg = extraerCampo(payload, "tipo"); // TEXTO / AUDIO
            Long emisor = extraerLong(payload, "emisor");
            Long receptor = extraerLong(payload, "receptor");
            Long canalId = extraerLong(payload, "canalId");
            Long serverId = extraerLong(payload, "id");
            java.sql.Timestamp serverTs = parseTimestamp(extraerCampo(payload, "timeStamp"));
            boolean esCanal = "NEW_CHANNEL_MESSAGE".equalsIgnoreCase(command) || (canalId != null);

            if ("AUDIO".equalsIgnoreCase(tipoMsg)) {
                String ruta = extraerCampo(payload, "rutaArchivo");
                String transcripcion = extraerCampo(payload, "transcripcion");
                if (esCanal) {
                    long id = repo.insertarDesdeServidorAudioConRuta(serverId, serverTs, emisor != null ? emisor : 0L, null, canalId, transcripcion, "AUDIO", ruta);
                    System.out.println("[ObservadorEventosChat] Audio canal insertado id=" + id + " canal=" + canalId);
                    ServicioEventosMensajes.instancia().notificarCanal(canalId);
                } else {
                    Long chatOtro = emisor != null ? emisor : receptor; // notificar por el otro lado del privado
                    long id = repo.insertarDesdeServidorAudioConRuta(serverId, serverTs, emisor != null ? emisor : 0L, receptor, null, transcripcion, "AUDIO", ruta);
                    System.out.println("[ObservadorEventosChat] Audio privado insertado id=" + id + " receptor=" + receptor);
                    if (chatOtro != null) ServicioEventosMensajes.instancia().notificarPrivado(chatOtro);
                }
            } else { // TEXTO u otros
                String contenido = extraerCampoPermitirNulo(payload, "contenido");
                if (esCanal) {
                    long id = repo.insertarDesdeServidorTexto(serverId, serverTs, emisor != null ? emisor : 0L, null, canalId, contenido != null ? contenido : "", "TEXTO");
                    System.out.println("[ObservadorEventosChat] Texto canal insertado id=" + id + " canal=" + canalId);
                    ServicioEventosMensajes.instancia().notificarCanal(canalId);
                } else {
                    Long chatOtro = emisor != null ? emisor : receptor;
                    long id = repo.insertarDesdeServidorTexto(serverId, serverTs, emisor != null ? emisor : 0L, receptor, null, contenido != null ? contenido : "", "TEXTO");
                    System.out.println("[ObservadorEventosChat] Texto privado insertado id=" + id + " receptor=" + receptor);
                    if (chatOtro != null) ServicioEventosMensajes.instancia().notificarPrivado(chatOtro);
                }
            }
        } catch (SQLException e) {
            System.out.println("[ObservadorEventosChat] Error insertando en BD: " + e);
        } catch (Exception e) {
            System.out.println("[ObservadorEventosChat] Error procesando evento: " + e + " json=" + json);
        }
    }

    private static String extraerCampo(String json, String campo) {
        try {
            Pattern p = Pattern.compile("\\\"" + Pattern.quote(campo) + "\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private static String extraerCampoPermitirNulo(String json, String campo) {
        // Permite null: "campo":null
        String v = extraerCampo(json, campo);
        if (v != null) return v;
        try {
            Pattern p = Pattern.compile("\\\"" + Pattern.quote(campo) + "\\\"\\s*:\\s*null");
            Matcher m = p.matcher(json);
            if (m.find()) return null;
        } catch (Exception ignored) {}
        return null;
    }

    private static Long extraerLong(String json, String campo) {
        try {
            Pattern p = Pattern.compile("\\\"" + Pattern.quote(campo) + "\\\"\\s*:\\s*(-?\\d+)");
            Matcher m = p.matcher(json);
            if (m.find()) return Long.parseLong(m.group(1));
        } catch (Exception ignored) {}
        return null;
    }

    private static String extraerObjetoPayload(String json) {
        try {
            int idx = json.indexOf("\"payload\"");
            if (idx < 0) return null;
            int start = json.indexOf('{', idx);
            if (start < 0) return null;
            int depth = 0;
            for (int i = start; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '{') depth++;
                else if (ch == '}') { depth--; if (depth == 0) { return json.substring(start, i+1); } }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static java.util.List<String> extraerObjetosDeArreglo(String json, String campoArray) {
        java.util.List<String> objs = new java.util.ArrayList<>();
        try {
            int idx = json.indexOf("\"" + campoArray + "\"");
            if (idx < 0) return objs;
            int startArr = json.indexOf('[', idx);
            if (startArr < 0) return objs;
            int braceDepth = 0; int objStart = -1;
            for (int i = startArr + 1; i < json.length(); i++) { // iniciar después del '['
                char ch = json.charAt(i);
                if (ch == '{') { if (braceDepth == 0) objStart = i; braceDepth++; }
                else if (ch == '}') { braceDepth--; if (braceDepth == 0 && objStart >= 0) { objs.add(json.substring(objStart, i+1)); objStart = -1; } }
                else if (ch == ']' && braceDepth == 0) { break; }
            }
        } catch (Exception ignored) {}
        return objs;
    }

    private static java.sql.Timestamp parseTimestamp(String iso) {
        try {
            if (iso == null || iso.isEmpty()) return null;
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(iso);
            return java.sql.Timestamp.valueOf(ldt);
        } catch (Exception e) { return null; }
    }
}
