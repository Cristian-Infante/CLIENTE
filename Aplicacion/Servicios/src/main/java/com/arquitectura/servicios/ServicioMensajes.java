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
    public long enviarTextoACanal(Long emisorId, Long canalId, String contenido, String tipo) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarTextoACanal(canalId, contenido);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeTexto(emisorId, null, canalId, contenido, tipo != null ? tipo : "TEXTO");
        System.out.println("[ServicioMensajes] Texto canal insertado id=" + id + " canal=" + canalId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarCanal(canalId); } catch (Exception ignored) {}
        return id;
    }

    // 1) Enviar a servidor (SEND_USER, tipo TEXTO) 2) Guardar local
    public long enviarTextoAPrivado(Long emisorId, Long receptorId, String contenido, String tipo) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarTextoAUsuario(receptorId, contenido);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeTexto(emisorId, receptorId, null, contenido, tipo != null ? tipo : "TEXTO");
        System.out.println("[ServicioMensajes] Texto privado insertado id=" + id + " receptor=" + receptorId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarPrivado(receptorId); } catch (Exception ignored) {}
        return id;
    }

    // Para transcripción de audio, enviamos como TEXTO y persistimos como AUDIO con transcripción
    public long enviarAudioTranscritoACanal(Long emisorId, Long canalId, String transcripcion, String tipo) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarTextoACanal(canalId, transcripcion);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeAudio(emisorId, null, canalId, transcripcion, tipo != null ? tipo : "AUDIO");
        System.out.println("[ServicioMensajes] Audio canal insertado id=" + id + " canal=" + canalId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarCanal(canalId); } catch (Exception ignored) {}
        return id;
    }

    public long enviarAudioTranscritoAPrivado(Long emisorId, Long receptorId, String transcripcion, String tipo) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarTextoAUsuario(receptorId, transcripcion);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeAudio(emisorId, receptorId, null, transcripcion, tipo != null ? tipo : "AUDIO");
        System.out.println("[ServicioMensajes] Audio privado insertado id=" + id + " receptor=" + receptorId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarPrivado(receptorId); } catch (Exception ignored) {}
        return id;
    }

    // Envío de audio por archivo al canal
    public long enviarAudioArchivoACanal(Long emisorId, Long canalId, String rutaArchivo, String mime, Integer duracionSeg) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarAudioACanal(canalId, rutaArchivo, mime, duracionSeg);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeAudioConRuta(emisorId, null, canalId, null, "AUDIO", rutaArchivo);
        System.out.println("[ServicioMensajes] AudioArchivo canal insertado id=" + id + " canal=" + canalId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarCanal(canalId); } catch (Exception ignored) {}
        return id;
    }

    // Envío de audio por archivo a usuario
    public long enviarAudioArchivoAPrivado(Long emisorId, Long receptorId, String rutaArchivo, String mime, Integer duracionSeg) throws SQLException, IOException {
        if (conexion != null) {
            try {
                if (!conexion.estaConectado()) conexion.conectar();
                ServicioComandosChat comandos = new ServicioComandosChat(conexion);
                comandos.enviarAudioAUsuario(receptorId, rutaArchivo, mime, duracionSeg);
            } catch (Exception ignored) {}
        }
        long id = repositorio.insertarMensajeAudioConRuta(emisorId, receptorId, null, null, "AUDIO", rutaArchivo);
        System.out.println("[ServicioMensajes] AudioArchivo privado insertado id=" + id + " receptor=" + receptorId);
        try { com.arquitectura.servicios.ServicioEventosMensajes.instancia().notificarPrivado(receptorId); } catch (Exception ignored) {}
        return id;
    }
}
