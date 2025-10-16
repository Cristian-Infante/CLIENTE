package com.arquitectura.infra.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public class ConfiguracionClienteChat {
    private final String host;
    private final int puerto;
    private final int tiempoEsperaConexionMs;
    private final int tiempoEsperaLecturaMs;

    public ConfiguracionClienteChat(String host, int puerto, int tiempoEsperaConexionMs, int tiempoEsperaLecturaMs) {
        this.host = Objects.requireNonNullElse(host, "127.0.0.1");
        this.puerto = puerto <= 0 ? 5000 : puerto;
        this.tiempoEsperaConexionMs = Math.max(0, tiempoEsperaConexionMs);
        this.tiempoEsperaLecturaMs = Math.max(0, tiempoEsperaLecturaMs);
    }

    public static ConfiguracionClienteChat cargarDesdeRecursos() {
        Properties props = new Properties();
        try (InputStream in = ConfiguracionClienteChat.class.getClassLoader().getResourceAsStream("chat-client.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
        }

        String host = props.getProperty("server.host", "127.0.0.1");
        int puerto = parsearEntero(props.getProperty("server.port"), 5000);
        int tiempoConexion = parsearEntero(props.getProperty("connect.timeout.ms"), 3000);
        int tiempoLectura = parsearEntero(props.getProperty("read.timeout.ms"), 0);
        return new ConfiguracionClienteChat(host, puerto, tiempoConexion, tiempoLectura);
    }

    private static int parsearEntero(String valor, int porDefecto) {
        try {
            return Integer.parseInt(valor);
        } catch (Exception e) {
            return porDefecto;
        }
    }

    public String obtenerHost() {
        return host;
    }

    public int obtenerPuerto() {
        return puerto;
    }

    public int obtenerTiempoEsperaConexionMs() {
        return tiempoEsperaConexionMs;
    }

    public int obtenerTiempoEsperaLecturaMs() {
        return tiempoEsperaLecturaMs;
    }
}
