package com.arquitectura.controladores;

import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.repositorios.RepositorioMensajes;
import com.arquitectura.servicios.ServicioComandosChat;
import com.arquitectura.servicios.ServicioConexionChat;
import com.arquitectura.servicios.ServicioMensajes;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ControladorChat {
    private final ClienteLocal clienteActual;
    private final ServicioMensajes servicioMensajes;
    private final ServicioConexionChat conexion;
    private final Map<Long, String> cacheNombres;

    public ControladorChat(ClienteLocal clienteActual, ServicioConexionChat conexion) {
        this.clienteActual = clienteActual;
        this.conexion = conexion;
        this.servicioMensajes = new ServicioMensajes(new RepositorioMensajes(), conexion);
        this.cacheNombres = new java.util.concurrent.ConcurrentHashMap<>();
        if (clienteActual != null && clienteActual.getId() != null && clienteActual.getNombreDeUsuario() != null) {
            cacheNombres.put(clienteActual.getId(), clienteActual.getNombreDeUsuario());
        }
    }

    public boolean enviarMensajeUsuario(Long receptorId, String receptorNombre, String texto) {
        try {
            servicioMensajes.enviarTextoAPrivado(
                    clienteActual.getId(),
                    clienteActual.getNombreDeUsuario(),
                    receptorId,
                    receptorNombre,
                    texto,
                    "TEXTO"
            );
            return true;
        } catch (IOException e) {
            return false; // error real de IO hacia servidor
        } catch (SQLException e) {
            // Fallo de persistencia local no debe impedir marcar envío como exitoso si ya se intentó enviar
            return true;
        }
    }

    public boolean enviarMensajeCanal(Long canalId, String nombreCanal, String texto) {
        try {
            servicioMensajes.enviarTextoACanal(
                    clienteActual.getId(),
                    clienteActual.getNombreDeUsuario(),
                    canalId,
                    texto,
                    "TEXTO"
            );
            return true;
        } catch (IOException e) {
            return false;
        } catch (SQLException e) {
            return true;
        }
    }

    public List<String> obtenerConversacion(Long usuarioId) {
        try {
            var repo = new com.arquitectura.repositorios.RepositorioMensajes();
            java.util.List<com.arquitectura.entidades.MensajeLocal> msgs = repo.listarMensajesPrivados(clienteActual.getId(), usuarioId, null);
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (msgs != null) {
                for (var m : msgs) {
                    String ts = m.getTimeStamp() != null ? m.getTimeStamp().toLocalTime().toString() : "";
                    if (ts.length() > 5) ts = ts.substring(0, 5);
                    boolean soyYo = m.getEmisor() != null && m.getEmisor().equals(clienteActual.getId());
                    String prefix;
                    if (soyYo) {
                        prefix = "Yo";
                    } else {
                        String nombre = normalizarNombreEmisor(m.getEmisor(), m.getEmisorNombre());
                        prefix = (nombre != null && !nombre.isBlank()) ? nombre : (m.getEmisor() != null ? ("#" + m.getEmisor()) : "?");
                    }
                    if (m instanceof com.arquitectura.entidades.TextoMensajeLocal t) {
                        lines.add("[" + ts + "] " + prefix + ": " + (t.getContenido() != null ? t.getContenido() : ""));
                    } else if (m instanceof com.arquitectura.entidades.AudioMensajeLocal a) {
                        StringBuilder sb = new StringBuilder("[" + ts + "] " + prefix + ": [Audio]");
                        if (a.getRutaArchivo() != null && !a.getRutaArchivo().isBlank()) {
                            sb.append(' ').append(a.getRutaArchivo());
                        }
                        if (a.getTranscripcion() != null && !a.getTranscripcion().isBlank()) {
                            sb.append(" — \"").append(a.getTranscripcion()).append("\"");
                        }
                        lines.add(sb.toString());
                    } else {
                        lines.add("[" + ts + "] " + prefix + ": (" + (m.getTipo() != null ? m.getTipo() : "MSG") + ")");
                    }
                }
            }
            return lines;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public List<String> obtenerMensajesCanal(Long canalId) {
        try {
            var repo = new com.arquitectura.repositorios.RepositorioMensajes();
            java.util.List<com.arquitectura.entidades.MensajeLocal> msgs = repo.listarMensajesDeCanal(canalId, null);
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (msgs != null) {
                for (var m : msgs) {
                    String ts = m.getTimeStamp() != null ? m.getTimeStamp().toLocalTime().toString() : "";
                    if (ts.length() > 5) ts = ts.substring(0, 5);
                    boolean soyYo = m.getEmisor() != null && m.getEmisor().equals(clienteActual.getId());
                    String prefix;
                    if (soyYo) {
                        prefix = "Yo";
                    } else {
                        String nombre = normalizarNombreEmisor(m.getEmisor(), m.getEmisorNombre());
                        prefix = (nombre != null && !nombre.isBlank()) ? nombre : (m.getEmisor() != null ? ("#" + m.getEmisor()) : "?");
                    }
                    if (m instanceof com.arquitectura.entidades.TextoMensajeLocal t) {
                        lines.add("[" + ts + "] " + prefix + ": " + (t.getContenido() != null ? t.getContenido() : ""));
                    } else if (m instanceof com.arquitectura.entidades.AudioMensajeLocal a) {
                        StringBuilder sb = new StringBuilder("[" + ts + "] " + prefix + ": [Audio]");
                        if (a.getRutaArchivo() != null && !a.getRutaArchivo().isBlank()) {
                            sb.append(' ').append(a.getRutaArchivo());
                        }
                        if (a.getTranscripcion() != null && !a.getTranscripcion().isBlank()) {
                            sb.append(" — \"").append(a.getTranscripcion()).append("\"");
                        }
                        lines.add(sb.toString());
                    } else {
                        lines.add("[" + ts + "] " + prefix + ": (" + (m.getTipo() != null ? m.getTipo() : "MSG") + ")");
                    }
                }
            }
            return lines;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    private String normalizarNombreEmisor(Long emisorId, String nombreOriginal) {
        if (nombreOriginal != null && !nombreOriginal.isBlank()) {
            if (emisorId != null) {
                cacheNombres.putIfAbsent(emisorId, nombreOriginal);
            }
            return nombreOriginal;
        }
        if (emisorId == null) return null;
        String cached = cacheNombres.get(emisorId);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        if (clienteActual != null && emisorId.equals(clienteActual.getId())) {
            String propio = clienteActual.getNombreDeUsuario();
            if (propio != null && !propio.isBlank()) {
                cacheNombres.put(emisorId, propio);
                return propio;
            }
        }
        String obtenido = buscarNombreEnServidor(emisorId);
        if (obtenido != null && !obtenido.isBlank()) {
            cacheNombres.put(emisorId, obtenido);
        }
        return cacheNombres.get(emisorId);
    }

    private String buscarNombreEnServidor(Long emisorId) {
        if (conexion == null || emisorId == null) return null;
        try {
            if (!conexion.estaConectado()) {
                try { conexion.conectar(); } catch (Exception ignored) { return null; }
            }
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            java.util.List<ClienteLocal> todos = comandos.listarUsuariosYEsperar(4000);
            if (todos != null) {
                for (ClienteLocal u : todos) {
                    if (u.getId() != null && u.getNombreDeUsuario() != null && !u.getNombreDeUsuario().isBlank()) {
                        cacheNombres.put(u.getId(), u.getNombreDeUsuario());
                    }
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return cacheNombres.get(emisorId);
    }
}
