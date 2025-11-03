package com.arquitectura.servicios;

import com.arquitectura.repositorios.RepositorioMensajes;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Servicio de casos de uso para enviar y registrar mensajes locales.
 * Ahora usa el protocolo del servidor (SEND_USER / SEND_CHANNEL) y persiste en H2.
 */
public class ServicioMensajes {
    private final ServicioConexionChat conexion;
    private final RepositorioMensajes repositorio = new RepositorioMensajes();

    public ServicioMensajes(ServicioConexionChat conexion) {
        this.conexion = conexion;
    }

    private boolean asegurarConexion() throws IOException {
        if (conexion == null) {
            return false;
        }
        if (!conexion.estaConectado()) {
            conexion.conectar();
        }
        return true;
    }

    private ServicioComandosChat crearComandos() {
        if (conexion == null) {
            return null;
        }
        return new ServicioComandosChat(conexion);
    }

    public boolean enviarTextoACanal(Long emisorId, String emisorNombre, Long canalId, String contenido, String tipo) throws IOException {
        if (!asegurarConexion()) {
            return false;
        }
        ServicioComandosChat comandos = crearComandos();
        boolean enviado = comandos != null && comandos.enviarTextoACanal(canalId, contenido);
        if (enviado) {
            registrarTextoLocal(emisorId, emisorNombre, null, null, canalId, contenido, tipo);
            System.out.println("[ServicioMensajes] Texto canal enviado canal=" + canalId + " contenido=" + contenido);
        }
        return enviado;
    }

    public boolean enviarTextoAPrivado(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, String contenido, String tipo) throws IOException {
        if (!asegurarConexion()) {
            return false;
        }
        ServicioComandosChat comandos = crearComandos();
        boolean enviado = comandos != null && comandos.enviarTextoAUsuario(receptorId, contenido);
        if (enviado) {
            registrarTextoLocal(emisorId, emisorNombre, receptorId, receptorNombre, null, contenido, tipo);
            System.out.println("[ServicioMensajes] Texto privado enviado receptor=" + receptorId + " contenido=" + contenido);
        }
        return enviado;
    }

    public boolean enviarAudioTranscritoACanal(Long emisorId, String emisorNombre, Long canalId, String transcripcion, String tipo) throws IOException {
        if (!asegurarConexion()) {
            return false;
        }
        ServicioComandosChat comandos = crearComandos();
        boolean enviado = comandos != null && comandos.enviarTextoACanal(canalId, transcripcion);
        if (enviado) {
            registrarTextoLocal(emisorId, emisorNombre, null, null, canalId, transcripcion, tipo);
            System.out.println("[ServicioMensajes] Audio transcrito canal enviado canal=" + canalId);
        }
        return enviado;
    }

    public boolean enviarAudioTranscritoAPrivado(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, String transcripcion, String tipo) throws IOException {
        if (!asegurarConexion()) {
            return false;
        }
        ServicioComandosChat comandos = crearComandos();
        boolean enviado = comandos != null && comandos.enviarTextoAUsuario(receptorId, transcripcion);
        if (enviado) {
            registrarTextoLocal(emisorId, emisorNombre, receptorId, receptorNombre, null, transcripcion, tipo);
            System.out.println("[ServicioMensajes] Audio transcrito privado enviado receptor=" + receptorId);
        }
        return enviado;
    }

    public boolean enviarAudioArchivoACanal(Long emisorId, String emisorNombre, Long canalId, String rutaArchivo, String mime, Integer duracionSeg) throws IOException {
        if (!asegurarConexion()) {
            return false;
        }
        ServicioComandosChat comandos = crearComandos();
        boolean enviado = comandos != null && comandos.enviarAudioACanal(canalId, rutaArchivo, mime, duracionSeg);
        if (enviado) {
            registrarAudioLocal(emisorId, emisorNombre, null, null, canalId, null, "AUDIO", rutaArchivo, null, mime, duracionSeg);
            System.out.println("[ServicioMensajes] Audio canal enviado canal=" + canalId + " ruta=" + rutaArchivo);
        }
        return enviado;
    }

    public boolean enviarAudioArchivoAPrivado(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, String rutaArchivo, String mime, Integer duracionSeg) throws IOException {
        if (!asegurarConexion()) {
            return false;
        }
        ServicioComandosChat comandos = crearComandos();
        boolean enviado = comandos != null && comandos.enviarAudioAUsuario(receptorId, rutaArchivo, mime, duracionSeg);
        if (enviado) {
            registrarAudioLocal(emisorId, emisorNombre, receptorId, receptorNombre, null, null, "AUDIO", rutaArchivo, null, mime, duracionSeg);
            System.out.println("[ServicioMensajes] Audio privado enviado receptor=" + receptorId + " ruta=" + rutaArchivo);
        }
        return enviado;
    }

    private void registrarTextoLocal(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String contenido, String tipo) {
        if (emisorId == null) {
            return;
        }
        try {
            repositorio.insertarMensajeTexto(emisorId, emisorNombre, receptorId, receptorNombre, canalId, contenido, tipo);
        } catch (SQLException e) {
            System.out.println("[ServicioMensajes] No se pudo registrar mensaje de texto localmente: " + e.getMessage());
        }
    }

    private void registrarAudioLocal(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, Long canalId, String transcripcion, String tipo, String rutaArchivo, String audioBase64, String mime, Integer duracionSeg) {
        if (emisorId == null) {
            return;
        }
        try {
            repositorio.insertarMensajeAudioConRuta(emisorId, emisorNombre, receptorId, receptorNombre, canalId, transcripcion, tipo, rutaArchivo, audioBase64, mime, duracionSeg);
        } catch (SQLException e) {
            System.out.println("[ServicioMensajes] No se pudo registrar mensaje de audio localmente: " + e.getMessage());
        }
    }
}
