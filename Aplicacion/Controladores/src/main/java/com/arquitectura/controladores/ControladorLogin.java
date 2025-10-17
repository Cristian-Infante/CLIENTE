package com.arquitectura.controladores;

import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.servicios.ServicioConexionChat;
import com.arquitectura.servicios.ServicioComandosChat;
import com.arquitectura.servicios.ObservadorEventosChat;
import com.arquitectura.servicios.ServicioContextoDatos;

import java.util.Base64;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Controlador de autenticación del cliente (lado UI).
 * Nota: sin protocolo definido, se simula éxito y se genera un ClienteLocal en memoria.
 */
public class ControladorLogin {
    private final ServicioConexionChat servicioConexion;
    private final ServicioComandosChat comandos;
    private ClienteLocal clienteSesion;
    private String ultimoMensajeServidor;

    public ControladorLogin() {
        this(new ServicioConexionChat());
    }

    public ControladorLogin(ServicioConexionChat servicioConexion) {
        this.servicioConexion = servicioConexion;
        this.comandos = new ServicioComandosChat(servicioConexion);
    }

    public boolean iniciarSesion(String usuarioOEmail, String contrasenia) {
        try {
            if (!servicioConexion.estaConectado()) servicioConexion.conectar();
            String ip = obtenerIpLocal();
            var res = comandos.iniciarSesionYEsperarAckConMensaje(usuarioOEmail, contrasenia, ip, 5000);
            boolean ok = res.success;
            // Mensaje por defecto para login
            ultimoMensajeServidor = ok ? "Login exitoso" : "Credenciales inválidas";
            // Sobrescribir con mensaje del servidor si existe
            if (res.message != null && !res.message.isEmpty()) {
                ultimoMensajeServidor = res.message;
            }
            if (!ok) return false;
            System.out.println(ok);

            // Solo crear la sesión local cuando el servidor confirma LOGIN exitoso
            clienteSesion = new ClienteLocal();
            try {
                String raw = res.raw;
                if (raw != null) {
                    var pid = java.util.regex.Pattern.compile("\\\"id\\\"\\s*:\\s*(\\d+)");
                    var puser = java.util.regex.Pattern.compile("\\\"usuario\\\"\\s*:\\s*\\\"(.*?)\\\"");
                    var pmail = java.util.regex.Pattern.compile("\\\"email\\\"\\s*:\\s*\\\"(.*?)\\\"");
                    var mid = pid.matcher(raw);
                    var mus = puser.matcher(raw);
                    var mma = pmail.matcher(raw);
                    if (mid.find()) { try { clienteSesion.setId(Long.parseLong(mid.group(1))); } catch (Exception ignored2) {} }
                    if (mus.find()) { clienteSesion.setNombreDeUsuario(mus.group(1)); } else { clienteSesion.setNombreDeUsuario(usuarioOEmail); }
                    if (mma.find()) { clienteSesion.setEmail(mma.group(1)); }
                } else {
                    clienteSesion.setNombreDeUsuario(usuarioOEmail);
                }
            } catch (Exception ignored2) {
                clienteSesion.setNombreDeUsuario(usuarioOEmail);
            }
            clienteSesion.setContrasenia(contrasenia);
            clienteSesion.setEstado(true);
            configurarContextoLocal(clienteSesion);
            intentarCargarFotoDesdeRespuesta(res.raw, clienteSesion);
            // Registrar observador singleton de eventos de mensajes
            try { ObservadorEventosChat.instancia().registrarEn(servicioConexion); } catch (Exception ignored4) {}
            // Completar datos faltantes consultando LIST_USERS si el servidor no devolvió id
            try {
                String emailRef = (usuarioOEmail != null && usuarioOEmail.contains("@")) ? usuarioOEmail : null;
                if (clienteSesion.getEmail() == null && emailRef != null) clienteSesion.setEmail(emailRef);
                if (clienteSesion.getId() == null) {
                    java.util.List<com.arquitectura.entidades.ClienteLocal> todos = comandos.listarUsuariosYEsperar(6000);
                    if (todos != null) {
                        for (com.arquitectura.entidades.ClienteLocal u : todos) {
                            if (u == null) continue;
                            boolean coincide = false;
                            if (emailRef != null && u.getEmail() != null && emailRef.equalsIgnoreCase(u.getEmail())) coincide = true;
                            if (!coincide && u.getNombreDeUsuario() != null && usuarioOEmail != null && u.getNombreDeUsuario().equalsIgnoreCase(usuarioOEmail)) coincide = true;
                            if (coincide) {
                                clienteSesion.setId(u.getId());
                                if (clienteSesion.getNombreDeUsuario() == null) clienteSesion.setNombreDeUsuario(u.getNombreDeUsuario());
                                if (clienteSesion.getEmail() == null) clienteSesion.setEmail(u.getEmail());
                                if (clienteSesion.getFoto() == null && u.getFoto() != null && u.getFoto().length > 0) {
                                    clienteSesion.setFoto(u.getFoto());
                                }
                                break;
                            }
                        }
                    }
                }
                if (clienteSesion.getId() == null) { clienteSesion.setId(0L); }
            } catch (Exception ignored3) { if (clienteSesion.getId() == null) clienteSesion.setId(0L); }
            configurarContextoLocal(clienteSesion);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean registrar(String usuario, String email, String contrasenia, byte[] foto) {
        try {
            if (!servicioConexion.estaConectado()) servicioConexion.conectar();
            String fotoBase64 = (foto != null && foto.length > 0) ? Base64.getEncoder().encodeToString(foto) : null;
            String ip = obtenerIpLocal();
            // Enviar REGISTER y esperar respuesta del servidor
            boolean enviado = comandos.registrar(usuario, email, contrasenia, fotoBase64, null, ip);
            if (!enviado) { ultimoMensajeServidor = "No se pudo enviar solicitud"; return false; }
            String linea = servicioConexion.esperarRespuesta("REGISTER", 6000);
            if (linea == null) { ultimoMensajeServidor = "Tiempo de espera agotado"; return false; }
            String compact = linea.replaceAll("\\s+", "");
            boolean ok = compact.contains("\"command\":\"REGISTER\"") && compact.contains("\"success\":true");
            String msg = extraerMensaje(linea);
            ultimoMensajeServidor = (msg != null && !msg.isEmpty()) ? msg : (ok ? "Registro exitoso" : "Error en registro");
            return ok;
        } catch (Exception e) {
            ultimoMensajeServidor = "Error interno del cliente";
            return false;
        }
    }

    private String extraerMensaje(String jsonLinea) {
        try {
            var pat = java.util.regex.Pattern.compile("\\\"message\\\"\\s*:\\s*\\\"(.*?)\\\"");
            var m = pat.matcher(jsonLinea);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private String obtenerIpLocal() {
        try {
            // Preferir IPv4 y direcciones site-local (privadas)
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress() && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            // Fallback
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    public ClienteLocal getClienteSesion() { return clienteSesion; }

    public ServicioConexionChat getServicioConexion() { return servicioConexion; }

    public String getUltimoMensajeServidor() { return ultimoMensajeServidor; }

    private void configurarContextoLocal(ClienteLocal cliente) {
        if (cliente == null) return;
        ServicioContextoDatos.configurarUsuarioActual(cliente.getId(), cliente.getNombreDeUsuario());
    }

    private void intentarCargarFotoDesdeRespuesta(String raw, ClienteLocal cliente) {
        if (raw == null || cliente == null) return;
        try {
            var pfoto = java.util.regex.Pattern.compile("\\\"foto(?:Base64)?\\\"\\s*:\\s*\\\"(.*?)\\\"", java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
            var mfo = pfoto.matcher(raw);
            if (mfo.find()) {
                String base64 = mfo.group(1);
                if (base64 != null && !base64.isBlank()) {
                    try { cliente.setFoto(Base64.getDecoder().decode(base64)); } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }
}
