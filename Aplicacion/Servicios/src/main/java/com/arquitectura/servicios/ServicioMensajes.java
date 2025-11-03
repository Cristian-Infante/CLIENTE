package com.arquitectura.servicios;

import java.io.IOException;

/**
 * Servicio de casos de uso para enviar y registrar mensajes locales.
 * Ahora usa el protocolo del servidor (SEND_USER / SEND_CHANNEL) y persiste en H2.
 */
public class ServicioMensajes {
    private final ServicioConexionChat conexion;

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
            System.out.println("[ServicioMensajes] Audio privado enviado receptor=" + receptorId + " ruta=" + rutaArchivo);
        }
        return enviado;
    }
}
