package com.arquitectura.controladores;

import com.arquitectura.entidades.AudioMensajeLocal;
import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.entidades.MensajeLocal;
import com.arquitectura.entidades.TextoMensajeLocal;
import com.arquitectura.repositorios.RepositorioMensajes;
import com.arquitectura.servicios.ServicioComandosChat;
import com.arquitectura.servicios.ServicioConexionChat;
import com.arquitectura.servicios.ServicioMensajes;

import java.io.IOException;
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
        this.servicioMensajes = new ServicioMensajes(conexion);
        this.cacheNombres = new java.util.concurrent.ConcurrentHashMap<>();
        if (clienteActual != null && clienteActual.getId() != null && clienteActual.getNombreDeUsuario() != null) {
            cacheNombres.put(clienteActual.getId(), clienteActual.getNombreDeUsuario());
        }
    }

    public boolean enviarMensajeUsuario(Long receptorId, String receptorNombre, String texto) {
        try {
            return servicioMensajes.enviarTextoAPrivado(
                    clienteActual.getId(),
                    clienteActual.getNombreDeUsuario(),
                    receptorId,
                    receptorNombre,
                    texto,
                    "TEXTO"
            );
        } catch (IOException e) {
            return false; // error real de IO hacia servidor
        }
    }

    public boolean enviarMensajeCanal(Long canalId, String nombreCanal, String texto) {
        try {
            return servicioMensajes.enviarTextoACanal(
                    clienteActual.getId(),
                    clienteActual.getNombreDeUsuario(),
                    canalId,
                    texto,
                    "TEXTO"
            );
        } catch (IOException e) {
            return false;
        }
    }

    public List<String> obtenerConversacion(Long usuarioId) {
        return convertirMensajesAString(obtenerConversacionDetallada(usuarioId));
    }

    public List<String> obtenerMensajesCanal(Long canalId) {
        return convertirMensajesAString(obtenerMensajesCanalDetallados(canalId));
    }

    public List<MensajeLocal> obtenerConversacionDetallada(Long usuarioId) {
        try {
            var repo = new RepositorioMensajes();
            List<MensajeLocal> mensajes = repo.listarMensajesPrivados(clienteActual.getId(), usuarioId, null);
            return completarNombres(mensajes);
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /**
     * Obtiene la conversación con otro usuario identificado por nombre.
     * Útil en entornos P2P donde los IDs pueden diferir entre servidores.
     */
    public List<MensajeLocal> obtenerConversacionDetalladaPorNombre(String nombreOtroUsuario) {
        try {
            var repo = new RepositorioMensajes();
            String miNombre = clienteActual != null ? clienteActual.getNombreDeUsuario() : null;
            // Si miNombre es un email, no lo usamos para filtrar (buscar solo por otroNombre)
            if (miNombre != null && miNombre.contains("@")) {
                miNombre = null;
            }
            List<MensajeLocal> mensajes = repo.listarMensajesPrivadosPorNombre(miNombre, nombreOtroUsuario, null);
            return completarNombres(mensajes);
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public List<MensajeLocal> obtenerMensajesCanalDetallados(Long canalId) {
        try {
            var repo = new RepositorioMensajes();
            List<MensajeLocal> mensajes = repo.listarMensajesDeCanal(canalId, null);
            return completarNombres(mensajes);
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /** Obtener mensajes de un canal por su UUID (útil en entornos P2P con IDs diferentes entre servidores) */
    public List<MensajeLocal> obtenerMensajesCanalDetalladosPorUuid(String canalUuid) {
        try {
            var repo = new RepositorioMensajes();
            List<MensajeLocal> mensajes = repo.listarMensajesDeCanalPorUuid(canalUuid, null);
            return completarNombres(mensajes);
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    public void actualizarIdentidadCliente(Long nuevoId, String nuevoNombre) {
        if (clienteActual == null) {
            return;
        }
        Long idAnterior = clienteActual.getId();
        boolean cambioId = nuevoId != null && nuevoId > 0 && !nuevoId.equals(idAnterior);
        if (cambioId) {
            if (idAnterior != null) {
                cacheNombres.remove(idAnterior);
            }
            clienteActual.setId(nuevoId);
        }

        if (nuevoNombre != null && !nuevoNombre.isBlank()) {
            clienteActual.setNombreDeUsuario(nuevoNombre);
        }

        Long idActual = clienteActual.getId();
        String nombreActual = clienteActual.getNombreDeUsuario();
        if (idActual != null && nombreActual != null && !nombreActual.isBlank()) {
            cacheNombres.put(idActual, nombreActual);
        }
    }

    private List<String> convertirMensajesAString(List<MensajeLocal> mensajes) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (mensajes == null) {
            return lines;
        }
        for (MensajeLocal m : mensajes) {
            if (m == null) continue;
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
            if (m instanceof TextoMensajeLocal t) {
                lines.add("[" + ts + "] " + prefix + ": " + (t.getContenido() != null ? t.getContenido() : ""));
            } else if (m instanceof AudioMensajeLocal a) {
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
        return lines;
    }

    private List<MensajeLocal> completarNombres(List<MensajeLocal> mensajes) {
        if (mensajes == null) {
            return java.util.List.of();
        }
        for (MensajeLocal mensaje : mensajes) {
            if (mensaje == null) {
                continue;
            }
            if (mensaje.getEmisorNombre() != null && !mensaje.getEmisorNombre().isBlank()) {
                if (mensaje.getEmisor() != null) {
                    cacheNombres.putIfAbsent(mensaje.getEmisor(), mensaje.getEmisorNombre());
                }
            } else {
                String nombre = normalizarNombreEmisor(mensaje.getEmisor(), mensaje.getEmisorNombre());
                if (nombre != null && !nombre.isBlank()) {
                    mensaje.setEmisorNombre(nombre);
                }
            }
        }
        return mensajes;
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
