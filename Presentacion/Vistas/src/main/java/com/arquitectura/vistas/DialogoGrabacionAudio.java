package com.arquitectura.vistas;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;

/**
 * Diálogo básico para grabar, reproducir y enviar audio.
 * - Graba audio PCM 16-bit mono 16kHz.
 * - Guarda en un archivo WAV temporal para facilitar reproducción/envío.
 */
public class DialogoGrabacionAudio extends JDialog {
    public interface OyenteEnvioAudio {
        void alEnviarAudio(File archivoWav, byte[] datosWav);
    }

    private JButton botonGrabar;
    private JButton botonDetener;
    private JButton botonReproducir;
    private JButton botonEnviar;
    private JButton botonCancelar;
    private JLabel etiquetaEstado;

    private transient TargetDataLine lineaEntrada;
    private transient Thread hiloGrabacion;
    private volatile boolean grabando;
    private ByteArrayOutputStream bufferAudio;
    private File archivoTemporalWav;
    private AudioFormat formato;

    private OyenteEnvioAudio oyenteEnvio;

    public DialogoGrabacionAudio(Window padre) {
        super(padre, "Grabar audio", ModalityType.APPLICATION_MODAL);
        configurarUI();
        configurarEventos();
        pack();
        setLocationRelativeTo(padre);
    }

    public void setOyenteEnvio(OyenteEnvioAudio oyenteEnvio) {
        this.oyenteEnvio = oyenteEnvio;
    }

    private void configurarUI() {
        botonGrabar = new JButton("Grabar");
        botonDetener = new JButton("Detener");
        botonReproducir = new JButton("Reproducir");
        botonEnviar = new JButton("Enviar");
        botonCancelar = new JButton("Cancelar");
        etiquetaEstado = new JLabel("Listo");

        botonDetener.setEnabled(false);
        botonReproducir.setEnabled(false);
        botonEnviar.setEnabled(false);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        panelBotones.add(botonGrabar);
        panelBotones.add(botonDetener);
        panelBotones.add(botonReproducir);
        panelBotones.add(botonEnviar);
        panelBotones.add(botonCancelar);

        JPanel panelEstado = new JPanel(new BorderLayout());
        panelEstado.add(etiquetaEstado, BorderLayout.CENTER);
        panelEstado.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(panelBotones, BorderLayout.CENTER);
        getContentPane().add(panelEstado, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(520, 150));
    }

    private void configurarEventos() {
        botonGrabar.addActionListener(e -> iniciarGrabacion());
        botonDetener.addActionListener(e -> detenerGrabacion());
        botonReproducir.addActionListener(e -> reproducir());
        botonEnviar.addActionListener(e -> enviar());
        botonCancelar.addActionListener(e -> cerrar());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });
    }

    private void iniciarGrabacion() {
        try {
            formato = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000f, 16, 1, 2, 16000f, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, formato);
            if (!AudioSystem.isLineSupported(info)) {
                mostrarError("Entrada de audio no soportada");
                return;
            }
            lineaEntrada = (TargetDataLine) AudioSystem.getLine(info);
            lineaEntrada.open(formato);
            lineaEntrada.start();

            bufferAudio = new ByteArrayOutputStream();
            grabando = true;
            hiloGrabacion = new Thread(() -> {
                byte[] buf = new byte[4096];
                while (grabando) {
                    int leidos = lineaEntrada.read(buf, 0, buf.length);
                    if (leidos > 0) {
                        bufferAudio.write(buf, 0, leidos);
                    }
                }
            }, "grabacion-audio");
            hiloGrabacion.setDaemon(true);
            hiloGrabacion.start();

            botonGrabar.setEnabled(false);
            botonDetener.setEnabled(true);
            botonReproducir.setEnabled(false);
            botonEnviar.setEnabled(false);
            etiquetaEstado.setText("Grabando...");
        } catch (LineUnavailableException ex) {
            mostrarError("No se pudo abrir el micrófono: " + ex.getMessage());
        }
    }

    private void detenerGrabacion() {
        if (!grabando) return;
        grabando = false;
        try {
            if (hiloGrabacion != null) hiloGrabacion.join(200);
        } catch (InterruptedException ignored) {}
        if (lineaEntrada != null) {
            try { lineaEntrada.stop(); } catch (Exception ignored) {}
            try { lineaEntrada.close(); } catch (Exception ignored) {}
        }
        convertirABufferWavTemporal();
        botonGrabar.setEnabled(true);
        botonDetener.setEnabled(false);
        botonReproducir.setEnabled(true);
        botonEnviar.setEnabled(true);
        etiquetaEstado.setText("Grabación lista");
    }

    private void convertirABufferWavTemporal() {
        try {
            byte[] datosPcm = bufferAudio != null ? bufferAudio.toByteArray() : new byte[0];
            long frames = datosPcm.length / formato.getFrameSize();
            try (ByteArrayInputStream bais = new ByteArrayInputStream(datosPcm);
                 AudioInputStream ais = new AudioInputStream(bais, formato, frames)) {
                archivoTemporalWav = File.createTempFile("grabacion-", ".wav");
                archivoTemporalWav.deleteOnExit();
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, archivoTemporalWav);
            }
        } catch (IOException ex) {
            mostrarError("Error convirtiendo a WAV: " + ex.getMessage());
        }
    }

    private void reproducir() {
        if (archivoTemporalWav == null) return;
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(archivoTemporalWav)) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.start();
        } catch (Exception ex) {
            mostrarError("No se pudo reproducir: " + ex.getMessage());
        }
    }

    private void enviar() {
        if (archivoTemporalWav == null) {
            mostrarError("No hay audio para enviar");
            return;
        }
        try {
            byte[] datos = leerBytes(archivoTemporalWav);
            if (oyenteEnvio != null) {
                oyenteEnvio.alEnviarAudio(archivoTemporalWav, datos);
            }
            etiquetaEstado.setText("Audio enviado (callback)");
            // Opcional: cerrar después de enviar
            // cerrar();
        } catch (IOException ex) {
            mostrarError("Error leyendo WAV: " + ex.getMessage());
        }
    }

    private byte[] leerBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private void cerrar() {
        try {
            grabando = false;
            if (hiloGrabacion != null) hiloGrabacion.interrupt();
            if (lineaEntrada != null) {
                try { lineaEntrada.stop(); } catch (Exception ignored) {}
                try { lineaEntrada.close(); } catch (Exception ignored) {}
            }
        } finally {
            dispose();
        }
    }

    private void mostrarError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        etiquetaEstado.setText(msg);
    }
}
