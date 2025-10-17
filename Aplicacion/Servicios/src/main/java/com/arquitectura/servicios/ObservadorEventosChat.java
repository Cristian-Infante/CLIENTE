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
        String raw = mensaje.replace('\n',' ').replace('\r',' ');
        String payload = extraerObjetoPayload(raw);
        if (payload == null) payload = raw;
        String command = extraerCampo(compact, "command");
        registrarLogGenerico(command, payload, raw);
        // Procesar sincronizaciones masivas (log y encolar)
        if (compact.contains("\"command\":\"MESSAGE_SYNC\"")) {
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
        boolean esComandoCanal = contieneEventoCanal(compact);
        boolean esComandoPrivado = contieneEventoPrivado(compact);
        if (!(esComandoCanal || esComandoPrivado)) return;

        String payloadMensaje = extraerPayloadMensaje(compact, payload);
        // Log organizado para NEW_CHANNEL_MESSAGE recibido (incluye eventos empaquetados en EVENT)
        if (esComandoCanal) {
            Long canalId = extraerLong(payloadMensaje, "canalId");
            if (canalId == null) {
                String canalObj = extraerObjeto(payloadMensaje, "canal");
                if (canalObj != null) canalId = obtenerCampoLong(canalObj, "id", "canalId");
            }
            Long id = extraerLong(payloadMensaje, "id");
            Long emisor = obtenerCampoLong(payloadMensaje, "emisor", "emisorId");
            String tipo = obtenerCampoTexto(payloadMensaje, "tipo", "tipoMensaje");
            String timeStamp = obtenerCampoTexto(payloadMensaje, "timeStamp", "timestamp");
            String contenido = obtenerCampoTextoPermitirNulo(payloadMensaje, "contenido", "texto");
            String rutaArchivo = obtenerCampoTexto(payloadMensaje, "rutaArchivo");
            String transcripcion = obtenerCampoTextoPermitirNulo(payloadMensaje, "transcripcion");
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
            sb.append("- jsonCompleto: ").append(raw).append('\n');
            sb.append("====================================\n");
            System.out.println(sb.toString());
        } else if (esComandoPrivado) {
            Long emisor = obtenerCampoLong(payloadMensaje, "emisor", "emisorId");
            Long receptor = obtenerCampoLong(payloadMensaje, "receptor", "receptorId");
            String tipo = obtenerCampoTexto(payloadMensaje, "tipo", "tipoMensaje");
            String contenido = obtenerCampoTextoPermitirNulo(payloadMensaje, "contenido", "texto");
            StringBuilder sb = new StringBuilder();
            sb.append("\n==== NEW_MESSAGE recibido ====\n");
            sb.append("- tipo: ").append(tipo).append('\n');
            sb.append("- emisor: ").append(emisor).append('\n');
            sb.append("- receptor: ").append(receptor).append('\n');
            if (contenido != null) sb.append("- contenido: ").append(contenido).append('\n');
            sb.append("- jsonCompleto: ").append(raw).append('\n');
            sb.append("===============================\n");
            System.out.println(sb.toString());
        }
        ioPool.submit(() -> procesarEventoMensaje(mensaje));
    }

    private void registrarLogGenerico(String command, String payload, String raw) {
        if (command == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("[ObservadorEventosChat] Evento recibido command=").append(command);
        if ("EVENT".equalsIgnoreCase(command)) {
            String tipoEvento = extraerCampo(payload, "tipo");
            if (tipoEvento == null) tipoEvento = extraerCampo(raw, "tipo");
            if (tipoEvento != null) {
                sb.append(" tipo=").append(tipoEvento);
            }
        }
        sb.append(" json=").append(raw);
        System.out.println(sb.toString());
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
                String tipoMsg = obtenerCampoTexto(obj, "tipo", "tipoMensaje");
                Long emisor = obtenerCampoLong(obj, "emisor", "emisorId");
                String emisorNombre = obtenerCampoTextoPermitirNulo(obj, "emisorNombre", "nombreEmisor", "emisor_nombre", "emisorNombreUsuario", "emisorName");
                Long receptor = obtenerCampoLong(obj, "receptor", "receptorId");
                String receptorNombre = obtenerCampoTextoPermitirNulo(obj, "receptorNombre", "nombreReceptor", "receptor_nombre", "receptorNombreUsuario", "receptorName");
                Long canalId = obtenerCampoLong(obj, "canalId");
                Long serverId = obtenerCampoLong(obj, "id");
                java.sql.Timestamp serverTs = parseTimestamp(obtenerCampoTexto(obj, "timeStamp", "timestamp"));
                String tipoConversacion = obtenerCampoTexto(obj, "tipoConversacion");
                boolean esCanal = canalId != null || (tipoConversacion != null && "CANAL".equalsIgnoreCase(tipoConversacion));
                boolean esAudio = "AUDIO".equalsIgnoreCase(tipoMsg);

                String contenidoObjeto = extraerObjeto(obj, "contenido");
                String contenidoPlano = obtenerCampoTextoPermitirNulo(obj, "contenido", "texto");
                String ruta = obtenerCampoTexto(obj, "rutaArchivo");
                String transcripcion = obtenerCampoTextoPermitirNulo(obj, "transcripcion");
                if (contenidoObjeto != null) {
                    if (esAudio) {
                        String rutaInterna = obtenerCampoTexto(contenidoObjeto, "rutaArchivo");
                        if (rutaInterna != null) ruta = rutaInterna;
                        String transcripcionInterna = obtenerCampoTextoPermitirNulo(contenidoObjeto, "transcripcion");
                        if (transcripcionInterna != null || contieneCampo(contenidoObjeto, "transcripcion")) transcripcion = transcripcionInterna;
                    } else {
                        String contenidoInterno = obtenerCampoTextoPermitirNulo(contenidoObjeto, "contenido", "texto");
                        if (contenidoInterno != null || contieneCampo(contenidoObjeto, "contenido") || contieneCampo(contenidoObjeto, "texto")) contenidoPlano = contenidoInterno;
                    }
                }

                if (esAudio) {
                    if (esCanal) {
                        long idIns = repo.insertarDesdeServidorAudioConRuta(serverId, serverTs, emisor != null ? emisor : 0L, emisorNombre, null, null, canalId, transcripcion, tipoMsg != null ? tipoMsg : "AUDIO", ruta);
                        if (idIns > 0) insertados++;
                        if (canalId != null) canalesNotificar.add(canalId);
                    } else {
                        long idIns = repo.insertarDesdeServidorAudioConRuta(serverId, serverTs, emisor != null ? emisor : 0L, emisorNombre, receptor, receptorNombre, null, transcripcion, tipoMsg != null ? tipoMsg : "AUDIO", ruta);
                        if (idIns > 0) insertados++;
                        if (emisor != null) privadosNotificar.add(emisor);
                        if (receptor != null) privadosNotificar.add(receptor);
                    }
                } else {
                    if (esCanal) {
                        long idIns = repo.insertarDesdeServidorTexto(serverId, serverTs, emisor != null ? emisor : 0L, emisorNombre, null, null, canalId, contenidoPlano != null ? contenidoPlano : "", tipoMsg != null ? tipoMsg : "TEXTO");
                        if (idIns > 0) insertados++;
                        if (canalId != null) canalesNotificar.add(canalId);
                    } else {
                        long idIns = repo.insertarDesdeServidorTexto(serverId, serverTs, emisor != null ? emisor : 0L, emisorNombre, receptor, receptorNombre, null, contenidoPlano != null ? contenidoPlano : "", tipoMsg != null ? tipoMsg : "TEXTO");
                        if (idIns > 0) insertados++;
                        if (emisor != null) privadosNotificar.add(emisor);
                        if (receptor != null) privadosNotificar.add(receptor);
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
            String payloadMensaje = extraerPayloadMensaje(compact, payload);
            String tipoMsg = obtenerCampoTexto(payloadMensaje, "tipo", "tipoMensaje"); // TEXTO / AUDIO
            Long emisor = obtenerCampoLong(payloadMensaje, "emisor", "emisorId");
            String emisorNombre = obtenerCampoTextoPermitirNulo(payloadMensaje, "emisorNombre", "nombreEmisor", "emisor_nombre", "emisorNombreUsuario", "emisorName");
            Long receptor = obtenerCampoLong(payloadMensaje, "receptor", "receptorId");
            String receptorNombre = obtenerCampoTextoPermitirNulo(payloadMensaje, "receptorNombre", "nombreReceptor", "receptor_nombre", "receptorNombreUsuario", "receptorName");
            Long canalId = obtenerCampoLong(payloadMensaje, "canalId");
            if (canalId == null) {
                String canalObj = extraerObjeto(payloadMensaje, "canal");
                if (canalObj != null) canalId = obtenerCampoLong(canalObj, "id", "canalId");
            }
            Long serverId = obtenerCampoLong(payloadMensaje, "id");
            java.sql.Timestamp serverTs = parseTimestamp(obtenerCampoTexto(payloadMensaje, "timeStamp", "timestamp"));
            String tipoConversacion = obtenerCampoTexto(payloadMensaje, "tipoConversacion");
            boolean esCanal = contieneEventoCanal(compact) || "NEW_CHANNEL_MESSAGE".equalsIgnoreCase(command) || (canalId != null) || (tipoConversacion != null && "CANAL".equalsIgnoreCase(tipoConversacion));
            boolean esAudio = "AUDIO".equalsIgnoreCase(tipoMsg);

            String contenidoObjeto = extraerObjeto(payloadMensaje, "contenido");
            String contenidoPlano = obtenerCampoTextoPermitirNulo(payloadMensaje, "contenido", "texto");
            String ruta = obtenerCampoTexto(payloadMensaje, "rutaArchivo");
            String transcripcion = obtenerCampoTextoPermitirNulo(payloadMensaje, "transcripcion");
            if (contenidoObjeto != null) {
                if (esAudio) {
                    String rutaInterna = obtenerCampoTexto(contenidoObjeto, "rutaArchivo");
                    if (rutaInterna != null) ruta = rutaInterna;
                    String transcripcionInterna = obtenerCampoTextoPermitirNulo(contenidoObjeto, "transcripcion");
                    if (transcripcionInterna != null || contieneCampo(contenidoObjeto, "transcripcion")) transcripcion = transcripcionInterna;
                } else {
                    String contenidoInterno = obtenerCampoTextoPermitirNulo(contenidoObjeto, "contenido", "texto");
                    if (contenidoInterno != null || contieneCampo(contenidoObjeto, "contenido") || contieneCampo(contenidoObjeto, "texto")) contenidoPlano = contenidoInterno;
                }
            }

            if (esAudio) {
                if (esCanal) {
                    long id = repo.insertarDesdeServidorAudioConRuta(serverId, serverTs, emisor != null ? emisor : 0L, emisorNombre, null, null, canalId, transcripcion, tipoMsg != null ? tipoMsg : "AUDIO", ruta);
                    System.out.println("[ObservadorEventosChat] Audio canal insertado id=" + id + " canal=" + canalId);
                    if (canalId != null) ServicioEventosMensajes.instancia().notificarCanal(canalId);
                } else {
                    long id = repo.insertarDesdeServidorAudioConRuta(serverId, serverTs, emisor != null ? emisor : 0L, emisorNombre, receptor, receptorNombre, null, transcripcion, tipoMsg != null ? tipoMsg : "AUDIO", ruta);
                    System.out.println("[ObservadorEventosChat] Audio privado insertado id=" + id + " receptor=" + receptor);
                    if (emisor != null) ServicioEventosMensajes.instancia().notificarPrivado(emisor);
                    if (receptor != null && !receptor.equals(emisor)) ServicioEventosMensajes.instancia().notificarPrivado(receptor);
                }
            } else { // TEXTO u otros
                if (esCanal) {
                        long id = repo.insertarDesdeServidorTexto(serverId, serverTs, emisor != null ? emisor : 0L, emisorNombre, null, null, canalId, contenidoPlano != null ? contenidoPlano : "", tipoMsg != null ? tipoMsg : "TEXTO");
                    System.out.println("[ObservadorEventosChat] Texto canal insertado id=" + id + " canal=" + canalId);
                    if (canalId != null) ServicioEventosMensajes.instancia().notificarCanal(canalId);
                } else {
                    long id = repo.insertarDesdeServidorTexto(serverId, serverTs, emisor != null ? emisor : 0L, emisorNombre, receptor, receptorNombre, null, contenidoPlano != null ? contenidoPlano : "", tipoMsg != null ? tipoMsg : "TEXTO");
                    System.out.println("[ObservadorEventosChat] Texto privado insertado id=" + id + " receptor=" + receptor);
                    if (emisor != null) ServicioEventosMensajes.instancia().notificarPrivado(emisor);
                    if (receptor != null && !receptor.equals(emisor)) ServicioEventosMensajes.instancia().notificarPrivado(receptor);
                }
            }
        } catch (SQLException e) {
            System.out.println("[ObservadorEventosChat] Error insertando en BD: " + e);
        } catch (Exception e) {
            System.out.println("[ObservadorEventosChat] Error procesando evento: " + e + " json=" + json);
        }
    }

    private static boolean contieneEventoCanal(String compact) {
        if (compact == null) return false;
        return compact.contains("\"command\":\"NEW_CHANNEL_MESSAGE\"") ||
                (compact.contains("\"command\":\"EVENT\"") && compact.contains("\"tipo\":\"NEW_CHANNEL_MESSAGE\""));
    }

    private static boolean contieneEventoPrivado(String compact) {
        if (compact == null) return false;
        return compact.contains("\"command\":\"NEW_MESSAGE\"") ||
                (compact.contains("\"command\":\"EVENT\"") && compact.contains("\"tipo\":\"NEW_MESSAGE\""));
    }

    private static String extraerPayloadMensaje(String compact, String payload) {
        if (payload == null) return null;
        if (compact != null && compact.contains("\"command\":\"EVENT\"")) {
            String interno = extraerObjeto(payload, "mensaje");
            if (interno == null) interno = extraerObjeto(payload, "message");
            if (interno == null) interno = extraerObjeto(payload, "payload");
            if (interno != null) return interno;
        }
        return payload;
    }

    private static String obtenerCampoTexto(String json, String... nombres) {
        if (nombres == null) return null;
        for (String nombre : nombres) {
            if (nombre == null) continue;
            String v = extraerCampo(json, nombre);
            if (v != null) return v;
        }
        return null;
    }

    private static String obtenerCampoTextoPermitirNulo(String json, String... nombres) {
        if (nombres == null) return null;
        for (String nombre : nombres) {
            if (nombre == null) continue;
            String v = extraerCampoPermitirNulo(json, nombre);
            if (v != null) return v;
            if (contieneCampo(json, nombre)) return null;
        }
        return null;
    }

    private static Long obtenerCampoLong(String json, String... nombres) {
        if (nombres == null) return null;
        for (String nombre : nombres) {
            if (nombre == null) continue;
            Long v = extraerLong(json, nombre);
            if (v != null) return v;
        }
        return null;
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

    private static boolean contieneCampo(String json, String campo) {
        if (json == null || campo == null) return false;
        return json.contains("\"" + campo + "\"");
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

    private static String extraerObjeto(String json, String campo) {
        try {
            int idx = json.indexOf("\"" + campo + "\"");
            if (idx < 0) return null;
            int start = json.indexOf('{', idx);
            if (start < 0) return null;
            int depth = 0;
            boolean inString = false;
            for (int i = start; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '"' && (i == start || json.charAt(i - 1) != '\\')) {
                    inString = !inString;
                }
                if (inString) continue;
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) return json.substring(start, i + 1);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static java.sql.Timestamp parseTimestamp(String iso) {
        try {
            if (iso == null || iso.isEmpty()) return null;
            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(iso);
            return java.sql.Timestamp.valueOf(ldt);
        } catch (Exception e) { return null; }
    }
}
