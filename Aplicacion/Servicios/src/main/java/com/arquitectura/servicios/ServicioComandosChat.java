package com.arquitectura.servicios;

import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.infra.net.ProtocoloChat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio que construye y envía comandos al servidor según el protocolo.
 * Usa JSON por línea: {"command":"...","payload":{...}}
 */
public class ServicioComandosChat {
    private final ServicioConexionChat conexion;

    public ServicioComandosChat(ServicioConexionChat conexion) {
        this.conexion = conexion;
    }

    private boolean enviar(String comando, Map<String, Object> payload) {
        String linea = ProtocoloChat.construir(comando, payload);
        try {
            if (!conexion.estaConectado()) conexion.conectar();
            conexion.enviarLinea(linea);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void imprimirRespuesta(String prefijo, String linea) {
        String contenido = linea != null ? SanitizadorBase64Logs.truncarCamposBase64(linea) : "null";
        System.out.println(prefijo + contenido);
    }

    // Autenticación
    public boolean registrar(String usuario, String email, String contrasenia, String fotoBase64, String fotoPath, String ip) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("usuario", usuario);
        p.put("email", email);
        p.put("contrasenia", contrasenia);
        if (fotoBase64 != null) p.put("fotoBase64", fotoBase64);
        if (fotoPath != null) p.put("fotoPath", fotoPath);
        if (ip != null) p.put("ip", ip);
        return enviar("REGISTER", p);
    }

    public boolean iniciarSesion(String email, String contrasenia, String ip) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("email", email);
        p.put("contrasenia", contrasenia);
        if (ip != null) p.put("ip", ip);
        return enviar("LOGIN", p);
    }

    // Variante síncrona: espera ACK LOGIN o ERROR
    public boolean iniciarSesionYEsperarAck(String email, String contrasenia, String ip, long timeoutMs) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("email", email);
        p.put("contrasenia", contrasenia);
        if (ip != null) p.put("ip", ip);
        if (!enviar("LOGIN", p)) return false;
        String linea = conexion.esperarRespuesta("LOGIN", timeoutMs);
        if (linea == null) return false;
        return esRespuestaLoginExitosa(linea);
    }

    // Envío de mensajes
    public boolean enviarTextoAUsuario(Long receptorId, String contenido) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("tipo", "TEXTO");
        p.put("contenido", contenido);
        p.put("receptor", receptorId);
        return enviar("SEND_USER", p);
    }

    public boolean enviarTextoACanal(Long canalId, String contenido) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("tipo", "TEXTO");
        p.put("contenido", contenido);
        p.put("canalId", canalId);
        return enviar("SEND_CHANNEL", p);
    }

    public boolean enviarAudioAUsuario(Long receptorId, String rutaArchivo, String mime, Integer duracionSeg) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("tipo", "AUDIO");
        if (rutaArchivo != null) p.put("rutaArchivo", rutaArchivo);
        if (mime != null) p.put("mime", mime);
        if (duracionSeg != null) p.put("duracionSeg", duracionSeg);
        p.put("receptor", receptorId);
        return enviar("SEND_USER", p);
    }

    public boolean enviarAudioACanal(Long canalId, String rutaArchivo, String mime, Integer duracionSeg) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("tipo", "AUDIO");
        if (rutaArchivo != null) p.put("rutaArchivo", rutaArchivo);
        if (mime != null) p.put("mime", mime);
        if (duracionSeg != null) p.put("duracionSeg", duracionSeg);
        p.put("canalId", canalId);
        return enviar("SEND_CHANNEL", p);
    }

    public RespuestaUploadAudio subirAudio(String audioBase64, String mime, Integer duracionSeg, String nombreArchivo, long timeoutMs) {
        Map<String, Object> p = ProtocoloChat.mapa();
        if (audioBase64 != null) p.put("audioBase64", audioBase64);
        if (mime != null) p.put("mime", mime);
        if (duracionSeg != null) p.put("duracionSeg", duracionSeg);
        if (nombreArchivo != null && !nombreArchivo.isBlank()) p.put("nombreArchivo", nombreArchivo);
        if (!enviar("UPLOAD_AUDIO", p)) {
            return RespuestaUploadAudio.error("No se pudo enviar el audio al servidor");
        }
        String respuesta = conexion.esperarRespuesta("UPLOAD_AUDIO", timeoutMs);
        if (respuesta == null) {
            return RespuestaUploadAudio.error("El servidor no respondió al subir el audio");
        }
        if (respuesta.contains("\"command\":\"ERROR\"")) {
            String mensajeError = extraerCampoTexto(respuesta, "message");
            if (mensajeError == null) mensajeError = extraerCampoTexto(respuesta, "mensaje");
            return RespuestaUploadAudio.error(mensajeError != null ? mensajeError : "Servidor devolvió un error");
        }
        boolean esRespuestaEsperada = respuesta.contains("\"command\":\"UPLOAD_AUDIO\"");
        if (!esRespuestaEsperada) {
            return RespuestaUploadAudio.error("Respuesta inesperada del servidor al subir audio");
        }
        Boolean exito = extraerCampoBooleano(respuesta, "exito");
        String ruta = extraerCampoTexto(respuesta, "rutaArchivo");
        String mensaje = extraerCampoTexto(respuesta, "mensaje");
        return new RespuestaUploadAudio(Boolean.TRUE.equals(exito), ruta, mensaje);
    }

    // Canales
    public boolean crearCanal(String nombre, boolean privado) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("nombre", nombre);
        p.put("privado", privado);
        return enviar("CREATE_CHANNEL", p);
    }

    public boolean invitarUsuarioACanal(Long canalId, String canalUuid, Long invitadoId) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("canalId", canalId);
        if (canalUuid != null) p.put("canalUuid", canalUuid);
        p.put("invitadoId", invitadoId);
        return enviar("INVITE", p);
    }

    public boolean aceptarInvitacion(Long canalId, String canalUuid) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("canalId", canalId);
        if (canalUuid != null) p.put("canalUuid", canalUuid);
        return enviar("ACCEPT", p);
    }

    public boolean rechazarInvitacion(Long canalId, String canalUuid) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("canalId", canalId);
        if (canalUuid != null) p.put("canalUuid", canalUuid);
        return enviar("REJECT", p);
    }

    // Listados / reportes
    public boolean listarUsuarios() { return enviar("LIST_USERS", null); }
    public List<ClienteLocal> listarUsuariosYEsperar(long timeoutMs) {
        if (!enviar("LIST_USERS", null)) return List.of();
        String linea = conexion.esperarRespuesta("LIST_USERS", timeoutMs);
        imprimirRespuesta("Respuesta LIST_USERS: ", linea);
        if (linea == null) return List.of();
        return parsearUsuariosDeRespuesta(linea);
    }
    public boolean listarCanales() { return enviar("LIST_CHANNELS", null); }
    public List<com.arquitectura.entidades.CanalLocal> listarCanalesYEsperar(long timeoutMs) {
        if (!enviar("LIST_CHANNELS", null)) return List.of();
        String linea = conexion.esperarRespuesta("LIST_CHANNELS", timeoutMs);
        imprimirRespuesta("Respuesta LIST_CHANNELS: ", linea);
        if (linea == null) return List.of();
        return parsearCanalesDeRespuesta(linea);
    }
    public List<ClienteLocal> listarConectadosYEsperar(long timeoutMs) {
        if (!enviar("LIST_CONNECTED", null)) return List.of();
        String linea = conexion.esperarRespuesta("LIST_CONNECTED", timeoutMs);
        imprimirRespuesta("Respuesta LIST_CONNECTED: ", linea);
        if (linea == null) return List.of();
        return parsearUsuariosDeRespuesta(linea);
    }
    // Invitaciones
    public boolean listarInvitacionesRecibidas() { return enviar("LIST_RECEIVED_INVITATIONS", null); }
    public boolean listarInvitacionesEnviadas() { return enviar("LIST_SENT_INVITATIONS", null); }
    public java.util.List<InvRecibida> listarInvitacionesRecibidasYEsperar(long timeoutMs) {
        if (!enviar("LIST_RECEIVED_INVITATIONS", null)) return java.util.List.of();
        String linea = conexion.esperarRespuesta("LIST_RECEIVED_INVITATIONS", timeoutMs);
        imprimirRespuesta("Respuesta LIST_RECEIVED_INVITATIONS: ", linea);
        if (linea == null) return java.util.List.of();
        return parsearInvRecibidas(linea);
    }
    public java.util.List<InvEnviada> listarInvitacionesEnviadasYEsperar(long timeoutMs) {
        if (!enviar("LIST_SENT_INVITATIONS", null)) return java.util.List.of();
        String linea = conexion.esperarRespuesta("LIST_SENT_INVITATIONS", timeoutMs);
        imprimirRespuesta("Respuesta LIST_SENT_INVITATIONS: ", linea);
        if (linea == null) return java.util.List.of();
        return parsearInvEnviadas(linea);
    }
    public boolean listarConectados() { return enviar("LIST_CONNECTED", null); }

    // Ping (verificación de conectividad)
    public boolean ping() { return enviar("PING", null); }

    // Broadcast
    public boolean broadcast(String mensaje) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("message", mensaje);
        return enviar("BROADCAST", p);
    }

    // Cerrar conexión
    public boolean cerrarConexion() { return enviar("CLOSE_CONN", null); }
    public boolean logout() { return enviar("LOGOUT", null); }

    private static byte[] decodificarBase64Seguro(String base64) {
        if (base64 == null || base64.isBlank()) {
            return new byte[0];
        }
        try {
            return java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static List<ClienteLocal> parsearUsuariosDeRespuesta(String jsonLinea) {
        List<ClienteLocal> usuarios = new ArrayList<>();
        if (jsonLinea == null) return usuarios;
        try {
            // Extraer el arreglo del payload tolerando espacios y saltos
            int idx = jsonLinea.indexOf("\"payload\"");
            if (idx < 0) return usuarios;
            int startArr = jsonLinea.indexOf('[', idx);
            if (startArr < 0) return usuarios;
            int depth = 0; int endArr = -1;
            for (int i = startArr; i < jsonLinea.length(); i++) {
                char ch = jsonLinea.charAt(i);
                if (ch == '[') depth++;
                else if (ch == ']') { depth--; if (depth == 0) { endArr = i; break; } }
            }
            if (endArr < 0) return usuarios;
            String arrayText = jsonLinea.substring(startArr + 1, endArr);
            // Separar objetos por llaves balanceadas
            List<String> objetos = new ArrayList<>();
            depth = 0; int objStart = -1;
            for (int i = 0; i < arrayText.length(); i++) {
                char ch = arrayText.charAt(i);
                if (ch == '{') { if (depth == 0) objStart = i; depth++; }
                else if (ch == '}') { depth--; if (depth == 0 && objStart >= 0) { objetos.add(arrayText.substring(objStart, i+1)); objStart = -1; } }
            }
            Pattern pid = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"?(\\d+)\\\"?");
            Pattern puser = Pattern.compile("\\\"usuario\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pmail = Pattern.compile("\\\"email\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pcon = Pattern.compile("\\\"conectado\\\"\\s*:\\s*(true|false)");
            Pattern pfoto = Pattern.compile("\\\"foto(?:Base64)?\\\"\\s*:\\s*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            for (String obj : objetos) {
                Matcher mid = pid.matcher(obj);
                Matcher mus = puser.matcher(obj);
                Matcher mma = pmail.matcher(obj);
                Matcher mco = pcon.matcher(obj);
                Matcher mfo = pfoto.matcher(obj);
                if (mid.find() && mus.find()) {
                    ClienteLocal c = new ClienteLocal();
                    try { c.setId(Long.parseLong(mid.group(1))); } catch (Exception ignored) {}
                    c.setNombreDeUsuario(mus.group(1));
                    if (mma.find()) c.setEmail(mma.group(1));
                    if (mco.find()) c.setEstado(Boolean.parseBoolean(mco.group(1)));
                    if (mfo.find()) {
                        byte[] foto = decodificarBase64Seguro(mfo.group(1));
                        if (foto.length > 0) c.setFoto(foto);
                    }
                    usuarios.add(c);
                }
            }
        } catch (Exception ignored) {}
        return usuarios;
    }

    private static List<com.arquitectura.entidades.CanalLocal> parsearCanalesDeRespuesta(String jsonLinea) {
        List<com.arquitectura.entidades.CanalLocal> canales = new ArrayList<>();
        if (jsonLinea == null) return canales;
        try {
            int idx = jsonLinea.indexOf("\"payload\"");
            if (idx < 0) return canales;
            int startArr = jsonLinea.indexOf('[', idx);
            if (startArr < 0) return canales;
            int depth = 0; int endArr = -1;
            for (int i = startArr; i < jsonLinea.length(); i++) {
                char ch = jsonLinea.charAt(i);
                if (ch == '[') depth++; else if (ch == ']') { depth--; if (depth == 0) { endArr = i; break; } }
            }
            if (endArr < 0) return canales;
            String arrayText = jsonLinea.substring(startArr + 1, endArr);
            java.util.List<String> objetos = new java.util.ArrayList<>();
            depth = 0; int objStart = -1;
            for (int i = 0; i < arrayText.length(); i++) {
                char ch = arrayText.charAt(i);
                if (ch == '{') { if (depth == 0) objStart = i; depth++; }
                else if (ch == '}') { depth--; if (depth == 0 && objStart >= 0) { objetos.add(arrayText.substring(objStart, i+1)); objStart = -1; } }
            }
            Pattern pid = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"?(\\d+)\\\"?");
            Pattern puuid = Pattern.compile("\\\"uuid\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pnom = Pattern.compile("\\\"nombre\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern ppriv = Pattern.compile("\\\"privado\\\"\\s*:\\s*(true|false)");
            Pattern pidUsuario = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"?(\\d+)\\\"?");
            Pattern puser = Pattern.compile("\\\"usuario\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pmail = Pattern.compile("\\\"email\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pcon = Pattern.compile("\\\"conectado\\\"\\s*:\\s*(true|false)");
            for (String obj : objetos) {
                Matcher mid = pid.matcher(obj);
                Matcher mnom = pnom.matcher(obj);
                Matcher mpriv = ppriv.matcher(obj);
                com.arquitectura.entidades.CanalLocal c = new com.arquitectura.entidades.CanalLocal();
                if (mid.find()) { try { c.setId(Long.parseLong(mid.group(1))); } catch (Exception ignored) {} }
                Matcher muuid = puuid.matcher(obj);
                if (muuid.find()) { c.setUuid(muuid.group(1)); }
                if (mnom.find()) { c.setNombre(mnom.group(1)); }
                if (mpriv.find()) { c.setPrivado(Boolean.parseBoolean(mpriv.group(1))); }

                int idxUsuarios = obj.indexOf("\"usuarios\"");
                if (idxUsuarios >= 0) {
                    int startUsuarios = obj.indexOf('[', idxUsuarios);
                    if (startUsuarios >= 0) {
                        int depthUsuarios = 0;
                        int endUsuarios = -1;
                        for (int i = startUsuarios; i < obj.length(); i++) {
                            char ch = obj.charAt(i);
                            if (ch == '[') depthUsuarios++;
                            else if (ch == ']') {
                                depthUsuarios--;
                                if (depthUsuarios == 0) { endUsuarios = i; break; }
                            }
                        }
                        if (endUsuarios > startUsuarios) {
                            String usuariosArray = obj.substring(startUsuarios + 1, endUsuarios);
                            java.util.List<String> usuariosJson = extraerObjetosDeArrayBruto(usuariosArray);
                            java.util.List<com.arquitectura.entidades.ClienteLocal> miembros = new java.util.ArrayList<>();
                            for (String u : usuariosJson) {
                                if (u == null || u.isBlank()) continue;
                                Matcher midU = pidUsuario.matcher(u);
                                Matcher muser = puser.matcher(u);
                                Matcher mmail = pmail.matcher(u);
                                Matcher mcon = pcon.matcher(u);
                                com.arquitectura.entidades.ClienteLocal cli = new com.arquitectura.entidades.ClienteLocal();
                                if (midU.find()) { try { cli.setId(Long.parseLong(midU.group(1))); } catch (Exception ignored) {} }
                                if (muser.find()) cli.setNombreDeUsuario(muser.group(1));
                                if (mmail.find()) cli.setEmail(mmail.group(1));
                                if (mcon.find()) cli.setEstado(Boolean.parseBoolean(mcon.group(1)));
                                miembros.add(cli);
                            }
                            if (!miembros.isEmpty()) {
                                c.setMiembros(miembros);
                            }
                        }
                    }
                }

                if (c.getNombre() != null || c.getId() != null) canales.add(c);
            }
        } catch (Exception ignored) {}
        return canales;
    }

    private static java.util.List<String> extraerObjetosDeArrayBruto(String arrayText) {
        java.util.List<String> objetos = new java.util.ArrayList<>();
        if (arrayText == null) return objetos;
        int depth = 0;
        int objStart = -1;
        for (int i = 0; i < arrayText.length(); i++) {
            char ch = arrayText.charAt(i);
            if (ch == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    objetos.add(arrayText.substring(objStart, i + 1));
                    objStart = -1;
                }
            }
        }
        return objetos;
    }

    // Modelos simples para invitaciones
    public static class InvRecibida {
        public Long canalId; public String canalUuid; public String canalNombre; public Boolean canalPrivado; public Long invitadorId; public String invitadorNombre;
    }
    public static class InvEnviada {
        public Long canalId; public String canalUuid; public String canalNombre; public Boolean canalPrivado; public Long invitadoId; public String invitadoNombre; public String estado;
    }

    private static java.util.List<InvRecibida> parsearInvRecibidas(String jsonLinea) {
        java.util.List<InvRecibida> res = new java.util.ArrayList<>();
        if (jsonLinea == null) return res;
        try {
            int idx = jsonLinea.indexOf("\"payload\""); if (idx < 0) return res;
            int startArr = jsonLinea.indexOf('[', idx); if (startArr < 0) return res;
            int depth = 0, endArr = -1; for (int i = startArr; i < jsonLinea.length(); i++) { char ch = jsonLinea.charAt(i); if (ch=='[') depth++; else if (ch==']') { depth--; if (depth==0) { endArr=i; break; } } }
            if (endArr < 0) return res;
            String arrayText = jsonLinea.substring(startArr+1, endArr);
            java.util.List<String> objs = new java.util.ArrayList<>(); depth=0; int os=-1; for (int i=0;i<arrayText.length();i++){ char ch=arrayText.charAt(i); if(ch=='{'){ if(depth==0) os=i; depth++; } else if(ch=='}'){ depth--; if(depth==0&&os>=0){ objs.add(arrayText.substring(os,i+1)); os=-1; } } }
            Pattern pCanalId = Pattern.compile("\\\"canalId\\\"\\s*:\\s*\\\"?(\\d+)\\\"?");
            Pattern pCanalUuid = Pattern.compile("\\\"canalUuid\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pCanalNom = Pattern.compile("\\\"canalNombre\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pCanalPriv = Pattern.compile("\\\"canalPrivado\\\"\\s*:\\s*(true|false)");
            Pattern pInvId = Pattern.compile("\\\"invitadorId\\\"\\s*:\\s*\\\"?(\\d+)\\\"?");
            Pattern pInvNom = Pattern.compile("\\\"invitadorNombre\\\"\\s*:\\s*\\\"(.*?)\\\"");
            for (String obj : objs) {
                InvRecibida ir = new InvRecibida();
                Matcher m;
                m = pCanalId.matcher(obj); if (m.find()) { try { ir.canalId = Long.parseLong(m.group(1)); } catch (Exception ignored) {} }
                m = pCanalUuid.matcher(obj); if (m.find()) ir.canalUuid = m.group(1);
                m = pCanalNom.matcher(obj); if (m.find()) ir.canalNombre = m.group(1);
                m = pCanalPriv.matcher(obj); if (m.find()) ir.canalPrivado = Boolean.parseBoolean(m.group(1));
                m = pInvId.matcher(obj); if (m.find()) { try { ir.invitadorId = Long.parseLong(m.group(1)); } catch (Exception ignored) {} }
                m = pInvNom.matcher(obj); if (m.find()) ir.invitadorNombre = m.group(1);
                res.add(ir);
            }
        } catch (Exception ignored) {}
        return res;
    }

    private static java.util.List<InvEnviada> parsearInvEnviadas(String jsonLinea) {
        java.util.List<InvEnviada> res = new java.util.ArrayList<>();
        if (jsonLinea == null) return res;
        try {
            int idx = jsonLinea.indexOf("\"payload\""); if (idx < 0) return res;
            int startArr = jsonLinea.indexOf('[', idx); if (startArr < 0) return res;
            int depth = 0, endArr = -1; for (int i = startArr; i < jsonLinea.length(); i++) { char ch = jsonLinea.charAt(i); if (ch=='[') depth++; else if (ch==']') { depth--; if (depth==0) { endArr=i; break; } } }
            if (endArr < 0) return res;
            String arrayText = jsonLinea.substring(startArr+1, endArr);
            java.util.List<String> objs = new java.util.ArrayList<>(); depth=0; int os=-1; for (int i=0;i<arrayText.length();i++){ char ch=arrayText.charAt(i); if(ch=='{'){ if(depth==0) os=i; depth++; } else if(ch=='}'){ depth--; if(depth==0&&os>=0){ objs.add(arrayText.substring(os,i+1)); os=-1; } } }
            Pattern pCanalId = Pattern.compile("\\\"canalId\\\"\\s*:\\s*\\\"?(\\d+)\\\"?");
            Pattern pCanalUuid = Pattern.compile("\\\"canalUuid\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pCanalNom = Pattern.compile("\\\"canalNombre\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pCanalPriv = Pattern.compile("\\\"canalPrivado\\\"\\s*:\\s*(true|false)");
            Pattern pInvId = Pattern.compile("\\\"invitadoId\\\"\\s*:\\s*\\\"?(\\d+)\\\"?");
            Pattern pInvNom = Pattern.compile("\\\"invitadoNombre\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Pattern pEstado = Pattern.compile("\\\"estado\\\"\\s*:\\s*\\\"(.*?)\\\"");
            for (String obj : objs) {
                InvEnviada ie = new InvEnviada();
                Matcher m;
                m = pCanalId.matcher(obj); if (m.find()) { try { ie.canalId = Long.parseLong(m.group(1)); } catch (Exception ignored) {} }
                m = pCanalUuid.matcher(obj); if (m.find()) ie.canalUuid = m.group(1);
                m = pCanalNom.matcher(obj); if (m.find()) ie.canalNombre = m.group(1);
                m = pCanalPriv.matcher(obj); if (m.find()) ie.canalPrivado = Boolean.parseBoolean(m.group(1));
                m = pInvId.matcher(obj); if (m.find()) { try { ie.invitadoId = Long.parseLong(m.group(1)); } catch (Exception ignored) {} }
                m = pInvNom.matcher(obj); if (m.find()) ie.invitadoNombre = m.group(1);
                m = pEstado.matcher(obj); if (m.find()) ie.estado = m.group(1);
                res.add(ie);
            }
        } catch (Exception ignored) {}
        return res;
    }

    // Variante robusta: imprime y valida tolerando espacios en JSON
    public boolean iniciarSesionYEsperarAckRobusto(String email, String contrasenia, String ip, long timeoutMs) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("email", email);
        p.put("contrasenia", contrasenia);
        if (ip != null) p.put("ip", ip);
        if (!enviar("LOGIN", p)) return false;
        String linea = conexion.esperarRespuesta("LOGIN", timeoutMs);
        imprimirRespuesta("Respuesta LOGIN: ", linea);
        if (linea == null) return false;
        return esRespuestaLoginExitosa(linea);
    }

    public ResultadoLogin iniciarSesionYEsperarAckConMensaje(String email, String contrasenia, String ip, long timeoutMs) {
        Map<String, Object> p = ProtocoloChat.mapa();
        p.put("email", email);
        p.put("contrasenia", contrasenia);
        if (ip != null) p.put("ip", ip);
        if (!enviar("LOGIN", p)) return new ResultadoLogin(false, "No se pudo enviar solicitud", null);
        String linea = conexion.esperarRespuesta("LOGIN", timeoutMs);
        imprimirRespuesta("Respuesta LOGIN: ", linea);
        if (linea == null) return new ResultadoLogin(false, "Tiempo de espera agotado", null);
        boolean ok = esRespuestaLoginExitosa(linea);
        String msg = extraerMensajeDeLinea(linea);
        return new ResultadoLogin(ok, msg, linea);
    }

    private static String extraerMensajeDeLinea(String jsonLinea) {
        try {
            java.util.regex.Pattern pat = java.util.regex.Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"(.*?)\\\"");
            java.util.regex.Matcher m = pat.matcher(jsonLinea);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean esRespuestaLoginExitosa(String jsonLinea) {
        if (jsonLinea == null) return false;
        try {
            String compact = jsonLinea.replaceAll("\\s+", "");
            if (!compact.contains("\"command\":\"LOGIN\"")) return false;

            String mensaje = extraerCampoTexto(jsonLinea, "message");
            if (mensaje != null) {
                String normalizado = mensaje.trim();
                if (normalizado.equalsIgnoreCase("Login exitoso") || normalizado.toLowerCase(Locale.ROOT).contains("login exitoso")) {
                    return true;
                }
            }

            java.util.regex.Pattern pat = java.util.regex.Pattern.compile("\\\"success\\\"\\s*:\\s*(true|false)", java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pat.matcher(jsonLinea);
            if (matcher.find()) {
                String valor = matcher.group(1);
                if ("true".equalsIgnoreCase(valor)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static class RespuestaUploadAudio {
        public final boolean exito;
        public final String rutaArchivo;
        public final String mensaje;

        public RespuestaUploadAudio(boolean exito, String rutaArchivo, String mensaje) {
            this.exito = exito;
            this.rutaArchivo = rutaArchivo;
            this.mensaje = mensaje;
        }

        public static RespuestaUploadAudio error(String mensaje) {
            return new RespuestaUploadAudio(false, null, mensaje);
        }
    }

    private static String extraerCampoTexto(String jsonLinea, String campo) {
        if (jsonLinea == null || campo == null) return null;
        try {
            Pattern p = Pattern.compile("\\\"" + Pattern.quote(campo) + "\\\"\\s*:\\s*\\\"(.*?)\\\"");
            Matcher m = p.matcher(jsonLinea);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private static Boolean extraerCampoBooleano(String jsonLinea, String campo) {
        if (jsonLinea == null || campo == null) return null;
        try {
            Pattern p = Pattern.compile("\\\"" + Pattern.quote(campo) + "\\\"\\s*:\\s*(true|false)");
            Matcher m = p.matcher(jsonLinea);
            if (m.find()) return Boolean.parseBoolean(m.group(1));
        } catch (Exception ignored) {}
        return null;
    }

    public static class ResultadoLogin {
        public final boolean success;
        public final String message;
        public final String raw;
        public ResultadoLogin(boolean success, String message, String raw) {
            this.success = success;
            this.message = message;
            this.raw = raw;
        }
    }
}


