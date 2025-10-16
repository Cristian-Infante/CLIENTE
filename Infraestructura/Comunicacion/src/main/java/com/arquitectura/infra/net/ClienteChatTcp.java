package com.arquitectura.infra.net;

import com.arquitectura.infra.config.ConfiguracionClienteChat;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClienteChatTcp implements Closeable {
    private final ConfiguracionClienteChat configuracion;
    private final AtomicBoolean conectado = new AtomicBoolean(false);
    private final List<OyenteMensajesChat> oyentes = new CopyOnWriteArrayList<>();

    private Socket socket;
    private BufferedReader lector;
    private BufferedWriter escritor;
    private Thread hiloLector;

    public ClienteChatTcp(ConfiguracionClienteChat configuracion) {
        this.configuracion = configuracion;
    }

    public synchronized void conectar() throws IOException {
        if (conectado.get()) return;
        socket = new Socket();
        socket.connect(new InetSocketAddress(configuracion.obtenerHost(), configuracion.obtenerPuerto()), configuracion.obtenerTiempoEsperaConexionMs());
        if (configuracion.obtenerTiempoEsperaLecturaMs() > 0) {
            socket.setSoTimeout(configuracion.obtenerTiempoEsperaLecturaMs());
        }
        lector = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        escritor = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        conectado.set(true);
        iniciarBucleLectura();
    }

    private void iniciarBucleLectura() {
        hiloLector = new Thread(() -> {
            try {
                String linea;
                StringBuilder buffer = new StringBuilder();
                int nivelEstructura = 0;
                boolean enCadena = false;
                boolean escape = false;
                while (conectado.get() && (linea = lector.readLine()) != null) {
                    if (linea.isEmpty() && nivelEstructura == 0 && buffer.length() == 0) {
                        continue;
                    }
                    for (int i = 0; i < linea.length(); i++) {
                        char ch = linea.charAt(i);
                        if (nivelEstructura == 0 && !enCadena && buffer.length() == 0 && Character.isWhitespace(ch)) {
                            continue;
                        }
                        buffer.append(ch);
                        if (enCadena) {
                            if (escape) {
                                escape = false;
                            } else if (ch == '\\') {
                                escape = true;
                            } else if (ch == '"') {
                                enCadena = false;
                            }
                            continue;
                        }
                        if (ch == '"') {
                            enCadena = true;
                            continue;
                        }
                        if (ch == '{' || ch == '[') {
                            nivelEstructura++;
                        } else if (ch == '}' || ch == ']') {
                            if (nivelEstructura > 0) nivelEstructura--;
                            if (nivelEstructura == 0) {
                                String mensajeCompleto = buffer.toString();
                                buffer.setLength(0);
                                for (OyenteMensajesChat o : oyentes) o.alRecibirMensaje(mensajeCompleto);
                            }
                        }
                    }
                    if (nivelEstructura > 0 || enCadena) {
                        buffer.append('\n');
                    } else if (buffer.length() > 0) {
                        String mensajeCompleto = buffer.toString().trim();
                        if (!mensajeCompleto.isEmpty()) {
                            for (OyenteMensajesChat o : oyentes) o.alRecibirMensaje(mensajeCompleto);
                        }
                        buffer.setLength(0);
                    }
                }
                if (buffer.length() > 0 && nivelEstructura == 0 && !enCadena) {
                    String mensajeCompleto = buffer.toString().trim();
                    if (!mensajeCompleto.isEmpty()) {
                        for (OyenteMensajesChat o : oyentes) o.alRecibirMensaje(mensajeCompleto);
                    }
                }
            } catch (IOException e) {
                for (OyenteMensajesChat o : oyentes) o.alError(e);
            } finally {
                conectado.set(false);
                for (OyenteMensajesChat o : oyentes) o.alCerrar();
                try { close(); } catch (IOException ignored) {}
            }
        }, "cliente-chat-tcp-lector");
        hiloLector.setDaemon(true);
        hiloLector.start();
    }

    public boolean estaConectado() {
        return conectado.get();
    }

    public synchronized void enviar(String mensaje) throws IOException {
        if (!conectado.get()) throw new IOException("No conectado");
        escritor.write(mensaje);
        escritor.write('\n');
        escritor.flush();
    }

    public void agregarOyente(OyenteMensajesChat oyente) {
        if (oyente != null) oyentes.add(oyente);
    }

    public void removerOyente(OyenteMensajesChat oyente) {
        oyentes.remove(oyente);
    }

    @Override
    public synchronized void close() throws IOException {
        conectado.set(false);
        if (hiloLector != null) {
            hiloLector.interrupt();
            hiloLector = null;
        }
        if (lector != null) {
            try { lector.close(); } catch (IOException ignored) {}
            lector = null;
        }
        if (escritor != null) {
            try { escritor.close(); } catch (IOException ignored) {}
            escritor = null;
        }
        if (socket != null && !socket.isClosed()) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
    }
}
