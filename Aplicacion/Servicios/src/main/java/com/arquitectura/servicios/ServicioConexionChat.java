package com.arquitectura.servicios;

import com.arquitectura.infra.config.ConfiguracionClienteChat;
import com.arquitectura.infra.net.ClienteChatTcp;
import com.arquitectura.infra.net.OyenteMensajesChat;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 Servicio de aplicación para gestionar la conexión TCP del chat.
 */
public class ServicioConexionChat {
    private final ClienteChatTcp cliente;

    public ServicioConexionChat() {
        var cfg = ConfiguracionClienteChat.cargarDesdeRecursos();
        this.cliente = new ClienteChatTcp(cfg);
    }

    public void conectar() throws IOException {
        cliente.conectar();
    }

    public void desconectarSilencioso() {
        try {
            cliente.close();
        } catch (IOException ignored) {
        }
    }

    public boolean estaConectado() {
        return cliente.estaConectado();
    }

    public void enviarLinea(String linea) throws IOException {
        cliente.enviar(linea);
    }

    // Esperar una respuesta que contenga un comando esperado o ERROR
    public String esperarRespuesta(String comandoEsperado, long timeoutMs) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> respuesta = new AtomicReference<>();
        OyenteMensajesChat tmp = new OyenteMensajesChat() {
            @Override public void alRecibirMensaje(String mensaje) {
                if (mensaje == null) return;
                if (mensaje.contains("\"command\":\"ERROR\"") || mensaje.contains("\"command\":\"" + comandoEsperado + "\"")) {
                    respuesta.compareAndSet(null, mensaje);
                    latch.countDown();
                }
            }
        };
        registrarOyente(tmp);
        try {
            boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            return ok ? respuesta.get() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            removerOyente(tmp);
        }
    }

    public void registrarOyente(OyenteMensajesChat oyente) {
        cliente.agregarOyente(oyente);
    }

    public void removerOyente(OyenteMensajesChat oyente) {
        cliente.removerOyente(oyente);
    }
}
