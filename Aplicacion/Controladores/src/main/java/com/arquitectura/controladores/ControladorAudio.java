package com.arquitectura.controladores;

import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.repositorios.RepositorioMensajes;
import com.arquitectura.servicios.ServicioConexionChat;
import com.arquitectura.servicios.ServicioMensajes;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;

public class ControladorAudio {
    private static final String MIME_WAV = "audio/wav";

    private final ClienteLocal clienteActual;
    private final ServicioConexionChat conexion;
    private final ServicioMensajes servicioMensajes;

    public ControladorAudio(ClienteLocal clienteActual, ServicioConexionChat conexion) {
        this.clienteActual = clienteActual;
        this.conexion = conexion;
        this.servicioMensajes = new ServicioMensajes(new RepositorioMensajes(), conexion);
    }

    public ResultadoEnvioAudio enviarAudioAPrivado(Long usuarioId, String usuarioNombre, File archivoWav, byte[] datosWav) {
        return procesarEnvio(archivoWav, datosWav, (ruta, duracionSeg) ->
                servicioMensajes.enviarAudioArchivoAPrivado(
                        clienteActual.getId(),
                        clienteActual.getNombreDeUsuario(),
                        usuarioId,
                        usuarioNombre,
                        ruta,
                        MIME_WAV,
                        duracionSeg
                ));
    }

    public ResultadoEnvioAudio enviarAudioACanal(Long canalId, String canalNombre, File archivoWav, byte[] datosWav) {
        return procesarEnvio(archivoWav, datosWav, (ruta, duracionSeg) ->
                servicioMensajes.enviarAudioArchivoACanal(
                        clienteActual.getId(),
                        clienteActual.getNombreDeUsuario(),
                        canalId,
                        ruta,
                        MIME_WAV,
                        duracionSeg
                ));
    }

    private ResultadoEnvioAudio procesarEnvio(File archivoWav, byte[] datosWav, RegistroLocal registroLocal) {
        if (datosWav == null || datosWav.length == 0) {
            return ResultadoEnvioAudio.fallo("No se recibió audio para enviar");
        }
        try {
            AnalisisWav analisis = analizarWav(datosWav);
            if (analisis == null) {
                return ResultadoEnvioAudio.fallo("Formato de audio no soportado");
            }

            com.arquitectura.servicios.ServicioComandosChat comandos = new com.arquitectura.servicios.ServicioComandosChat(conexion);
            String base64 = Base64.getEncoder().encodeToString(datosWav);
            String nombreArchivo = archivoWav != null ? archivoWav.getName() : null;
            var respuesta = comandos.subirAudio(base64, MIME_WAV, analisis.duracionSegundos, nombreArchivo, 10000);
            if (respuesta == null || !respuesta.exito || respuesta.rutaArchivo == null || respuesta.rutaArchivo.isBlank()) {
                String mensaje = respuesta != null && respuesta.mensaje != null ? respuesta.mensaje : "Error subiendo el audio";
                return ResultadoEnvioAudio.fallo(mensaje);
            }

            try {
                registroLocal.registrar(respuesta.rutaArchivo, analisis.duracionSegundos);
            } catch (IOException e) {
                return ResultadoEnvioAudio.fallo("Error enviando audio al servidor: " + e.getMessage());
            } catch (java.sql.SQLException e) {
                // Persistencia local falló pero el audio fue subido correctamente.
                return ResultadoEnvioAudio.exito(respuesta.rutaArchivo, analisis.duracionSegundos,
                        respuesta.mensaje != null ? respuesta.mensaje : "Audio enviado (sin registrar localmente)"
                );
            }

            return ResultadoEnvioAudio.exito(respuesta.rutaArchivo, analisis.duracionSegundos, respuesta.mensaje);
        } catch (UnsupportedAudioFileException e) {
            return ResultadoEnvioAudio.fallo("Audio WAV inválido: " + e.getMessage());
        } catch (IOException e) {
            return ResultadoEnvioAudio.fallo("Error de E/S al procesar el audio: " + e.getMessage());
        }
    }

    private AnalisisWav analizarWav(byte[] datosWav) throws IOException, UnsupportedAudioFileException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(datosWav);
             AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
            AudioFormat formato = ais.getFormat();
            if (formato == null) return null;
            if (formato.getChannels() != 1 || formato.getSampleSizeInBits() != 16) return null;
            float sampleRate = formato.getSampleRate();
            if (sampleRate <= 0) return null;
            if (Math.abs(sampleRate - 16000f) > 1f) return null;
            long frames = ais.getFrameLength();
            if (frames <= 0) {
                frames = datosWav.length / Math.max(1, formato.getFrameSize());
            }
            double segundos = frames / sampleRate;
            int duracion = (int) Math.max(1, Math.ceil(segundos));
            AnalisisWav analisis = new AnalisisWav();
            analisis.duracionSegundos = duracion;
            return analisis;
        }
    }

    @FunctionalInterface
    private interface RegistroLocal {
        void registrar(String rutaArchivo, int duracionSeg) throws IOException, java.sql.SQLException;
    }

    private static class AnalisisWav {
        int duracionSegundos;
    }

    public static class ResultadoEnvioAudio {
        public final boolean exito;
        public final String mensaje;
        public final String rutaArchivo;
        public final int duracionSegundos;

        private ResultadoEnvioAudio(boolean exito, String mensaje, String rutaArchivo, int duracionSegundos) {
            this.exito = exito;
            this.mensaje = mensaje;
            this.rutaArchivo = rutaArchivo;
            this.duracionSegundos = duracionSegundos;
        }

        public static ResultadoEnvioAudio exito(String rutaArchivo, int duracionSegundos, String mensaje) {
            return new ResultadoEnvioAudio(true, mensaje, rutaArchivo, duracionSegundos);
        }

        public static ResultadoEnvioAudio fallo(String mensaje) {
            return new ResultadoEnvioAudio(false, mensaje, null, 0);
        }
    }
}
