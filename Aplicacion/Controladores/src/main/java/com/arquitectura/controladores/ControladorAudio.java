package com.arquitectura.controladores;

import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.servicios.ServicioConexionChat;
import com.arquitectura.servicios.ServicioMensajes;
import com.arquitectura.repositorios.RepositorioMensajes;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ControladorAudio {
    private final ClienteLocal clienteActual;
    private final ServicioConexionChat conexion;
    private TargetDataLine lineaEntrada;
    private Thread hiloGrabacion;
    private volatile boolean grabando;
    private ByteArrayOutputStream bufferAudio;
    private AudioFormat formato;

    public ControladorAudio(ClienteLocal clienteActual, ServicioConexionChat conexion) {
        this.clienteActual = clienteActual;
        this.conexion = conexion;
    }

    public boolean iniciarGrabacion() {
        if (grabando) return false;
        try {
            formato = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100f, 16, 1, 2, 44100f, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
            if (!AudioSystem.isLineSupported(info)) return false;
            lineaEntrada = (TargetDataLine) AudioSystem.getLine(info);
            lineaEntrada.open(formato);
            lineaEntrada.start();
            bufferAudio = new ByteArrayOutputStream();
            grabando = true;
            hiloGrabacion = new Thread(() -> {
                byte[] buf = new byte[4096];
                while (grabando) {
                    int leidos = lineaEntrada.read(buf, 0, buf.length);
                    if (leidos > 0) bufferAudio.write(buf, 0, leidos);
                }
            }, "ctrl-audio-grab");
            hiloGrabacion.setDaemon(true);
            hiloGrabacion.start();
            return true;
        } catch (LineUnavailableException e) {
            return false;
        }
    }

    public boolean estaGrabando() { return grabando; }

    public byte[] detenerGrabacion() {
        if (!grabando) return null;
        grabando = false;
        try { if (hiloGrabacion != null) hiloGrabacion.join(200); } catch (InterruptedException ignored) {}
        if (lineaEntrada != null) { try { lineaEntrada.stop(); } catch (Exception ignored) {} try { lineaEntrada.close(); } catch (Exception ignored) {} }
        return bufferAudio != null ? bufferAudio.toByteArray() : null;
    }

    public boolean enviarAudioUsuario(Long usuarioId, byte[] audioPcm, String etiqueta) {
        try {
            if (audioPcm == null || audioPcm.length == 0) return false;
            GuardadoAudio g = guardarComoWav(audioPcm, false, usuarioId);
            ServicioMensajes sm = new ServicioMensajes(new RepositorioMensajes(), conexion);
            sm.enviarAudioArchivoAPrivado(clienteActual.getId(), usuarioId, g.ruta, "audio/wav", g.duracionSeg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean enviarAudioCanal(Long canalId, String nombreCanal, byte[] audioPcm, String etiqueta) {
        try {
            if (audioPcm == null || audioPcm.length == 0) return false;
            GuardadoAudio g = guardarComoWav(audioPcm, true, canalId);
            ServicioMensajes sm = new ServicioMensajes(new RepositorioMensajes(), conexion);
            sm.enviarAudioArchivoACanal(clienteActual.getId(), canalId, g.ruta, "audio/wav", g.duracionSeg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static class GuardadoAudio { String ruta; int duracionSeg; }

    private GuardadoAudio guardarComoWav(byte[] pcm, boolean esCanal, Long destinoId) throws IOException {
        int sampleRate = 44100; int channels = 1; int bitsPerSample = 16; int byteRate = sampleRate * channels * (bitsPerSample/8);
        int blockAlign = channels * (bitsPerSample/8);
        int dataLen = pcm.length;
        int riffLen = 36 + dataLen;

        java.nio.file.Path base = java.nio.file.Paths.get("media","audio", esCanal ? "canales" : "usuarios", String.valueOf(destinoId != null ? destinoId : 0));
        java.nio.file.Files.createDirectories(base);
        String nombre = "rec_" + System.currentTimeMillis() + ".wav";
        java.nio.file.Path path = base.resolve(nombre);

        try (java.io.OutputStream os = java.nio.file.Files.newOutputStream(path)) {
            // RIFF header
            escribir(os, "RIFF");
            escribirIntLE(os, riffLen);
            escribir(os, "WAVE");
            // fmt chunk
            escribir(os, "fmt ");
            escribirIntLE(os, 16); // Subchunk1Size for PCM
            escribirShortLE(os, (short)1); // PCM
            escribirShortLE(os, (short)channels);
            escribirIntLE(os, sampleRate);
            escribirIntLE(os, byteRate);
            escribirShortLE(os, (short)blockAlign);
            escribirShortLE(os, (short)bitsPerSample);
            // data chunk
            escribir(os, "data");
            escribirIntLE(os, dataLen);
            os.write(pcm);
        }

        GuardadoAudio g = new GuardadoAudio();
        g.ruta = path.toString();
        g.duracionSeg = (int)Math.ceil((double)dataLen / (double)byteRate);
        return g;
    }

    private void escribir(java.io.OutputStream os, String s) throws IOException { os.write(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
    private void escribirIntLE(java.io.OutputStream os, int v) throws IOException { os.write(new byte[]{ (byte)(v), (byte)(v>>8), (byte)(v>>16), (byte)(v>>24) }); }
    private void escribirShortLE(java.io.OutputStream os, short v) throws IOException { os.write(new byte[]{ (byte)(v), (byte)(v>>8) }); }
}
