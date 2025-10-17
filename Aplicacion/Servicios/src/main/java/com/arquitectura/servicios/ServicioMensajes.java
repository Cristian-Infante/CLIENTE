package com.arquitectura.servicios;

import com.arquitectura.repositorios.RepositorioMensajes;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Servicio de casos de uso para enviar y registrar mensajes locales.
 * Ahora usa el protocolo del servidor (SEND_USER / SEND_CHANNEL) y persiste en H2.
 */
public class ServicioMensajes {
    private final RepositorioMensajes repositorio;
    private final ServicioConexionChat conexion;

    public ServicioMensajes(RepositorioMensajes repositorio, ServicioConexionChat conexion) {
        this.repositorio = repositorio;
        this.conexion = conexion;
    }

    // 1) Enviar a servidor (SEND_CHANNEL, tipo TEXTO) 2) Guardar local
    public long enviarTextoACanal(Long emisorId, String emisorNombre, Long canalId, String contenido, String tipo) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarTextoACanal(canalId, contenido);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeTexto(emisorId, emisorNombre, null, null, canalId, contenido, tipo != null ? tipo : "TEXTO");
        System.out.println("[ServicioMensajes] Texto canal insertado id=" + id + " canal=" + canalId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarCanal(canalId); } catch (Exception ignored) {}
        return id;
    }

    // 1) Enviar a servidor (SEND_USER, tipo TEXTO) 2) Guardar local
    public long enviarTextoAPrivado(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, String contenido, String tipo) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarTextoAUsuario(receptorId, contenido);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeTexto(emisorId, emisorNombre, receptorId, receptorNombre, null, contenido, tipo != null ? tipo : "TEXTO");
        System.out.println("[ServicioMensajes] Texto privado insertado id=" + id + " receptor=" + receptorId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarPrivado(receptorId); } catch (Exception ignored) {}
        return id;
    }

    // Para transcripción de audio, enviamos como TEXTO y persistimos como AUDIO con transcripción
    public long enviarAudioTranscritoACanal(Long emisorId, String emisorNombre, Long canalId, String transcripcion, String tipo) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarTextoACanal(canalId, transcripcion);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeAudio(emisorId, emisorNombre, null, null, canalId, transcripcion, tipo != null ? tipo : "AUDIO");
        System.out.println("[ServicioMensajes] Audio canal insertado id=" + id + " canal=" + canalId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarCanal(canalId); } catch (Exception ignored) {}
        return id;
    }

    public long enviarAudioTranscritoAPrivado(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, String transcripcion, String tipo) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarTextoAUsuario(receptorId, transcripcion);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeAudio(emisorId, emisorNombre, receptorId, receptorNombre, null, transcripcion, tipo != null ? tipo : "AUDIO");
        System.out.println("[ServicioMensajes] Audio privado insertado id=" + id + " receptor=" + receptorId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarPrivado(receptorId); } catch (Exception ignored) {}
        return id;
    }

    // Envío de audio por archivo al canal
    public long enviarAudioArchivoACanal(Long emisorId, String emisorNombre, Long canalId, String rutaArchivo, String mime, Integer duracionSeg, String audioBase64) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarAudioACanal(canalId, rutaArchivo, mime, duracionSeg);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeAudioConRuta(emisorId, emisorNombre, null, null, canalId, null, "AUDIO", rutaArchivo, audioBase64, mime, duracionSeg);
        System.out.println("[ServicioMensajes] AudioArchivo canal insertado id=" + id + " canal=" + canalId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarCanal(canalId); } catch (Exception ignored) {}
        return id;
    }

    // Envío de audio por archivo a usuario
    public long enviarAudioArchivoAPrivado(Long emisorId, String emisorNombre, Long receptorId, String receptorNombre, String rutaArchivo, String mime, Integer duracionSeg, String audioBase64) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarAudioAUsuario(receptorId, rutaArchivo, mime, duracionSeg);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeAudioConRuta(emisorId, emisorNombre, receptorId, receptorNombre, null, null, "AUDIO", rutaArchivo, audioBase64, mime, duracionSeg);
        System.out.println("[ServicioMensajes] AudioArchivo privado insertado id=" + id + " receptor=" + receptorId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarPrivado(receptorId); } catch (Exception ignored) {}
        return id;
    }
}
