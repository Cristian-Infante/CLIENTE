package com.arquitectura.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton de conexiones a la base de datos H2 del cliente.
 */
public final class ProveedorConexionCliente {
    private final ConfigClienteDB configuracion;
    private final AtomicBoolean esquemaInicializado = new AtomicBoolean(false);
    private volatile Long contextoUsuarioId;
    private volatile String contextoUsuarioNombre;
    private volatile String sessionToken;

    private ProveedorConexionCliente() {
        this.configuracion = ConfigClienteDB.cargarDesdeRecursos();
    }

    private static class Holder {
        private static final ProveedorConexionCliente INSTANCIA = new ProveedorConexionCliente();
    }

    public static ProveedorConexionCliente instancia() {
        return Holder.INSTANCIA;
    }

    /**
     * Obtiene una nueva conexión. La primera vez inicializa el esquema si está habilitado en properties.
     */
    public Connection obtenerConexion() throws SQLException {
        inicializarEsquemaSiHaceFalta();
        return configuracion.obtenerConexion();
    }

    public String obtenerUrl() { return configuracion.obtenerUrl(); }

    public void configurarContextoUsuario(Long usuarioId, String nombreUsuario) {
        this.contextoUsuarioId = usuarioId;
        this.contextoUsuarioNombre = nombreUsuario;
        try {
            // New flow to avoid locking and unsafe bulk-updates on a shared DB:
            // 1) Capture old DB url (shared) so we can migrate from it later if needed
            String oldUrl = configuracion.obtenerUrl();
            try { System.out.println("[ProveedorConexionCliente] configurarContextoUsuario oldUrl=" + oldUrl + " usuarioId=" + usuarioId + " nombreUsuario=" + nombreUsuario); } catch (Exception ignored) {}
            // 1.5) Ensure we have a stable per-session token for this app session
            if (sessionToken == null || sessionToken.isBlank()) {
                sessionToken = crearTokenSesion();
            }
            // Make the token available to configuracion so it can build a per-session DB URL
            try { System.setProperty("arquitectura.config.client.db.sessionToken", sessionToken); } catch (Exception ignored) {}
            // 2) Switch configuration to per-user DB early (sets overrideUrl system property)
            configuracion.cambiarUsuarioNombre(nombreUsuario != null ? nombreUsuario.trim() : null);
            try { System.out.println("[ProveedorConexionCliente] configurarContextoUsuario after cambiarUsuarioNombre newUrl=" + configuracion.obtenerUrl()); } catch (Exception ignored) {}
            // 3) Force re-initialization for the new DB URL (create schema in per-user DB)
            esquemaInicializado.set(false);
            inicializarEsquemaSiHaceFalta();
            // 4) Migrate messages that belong to this contexto from the old shared DB into the new per-user DB
            String newUrl = System.getProperty("arquitectura.config.client.db.overrideUrl", configuracion.obtenerUrl());
            try { System.out.println("[ProveedorConexionCliente] configurarContextoUsuario migrating from " + oldUrl + " to " + newUrl); } catch (Exception ignored) {}
            if (oldUrl != null && !oldUrl.equals(newUrl)) {
                configuracion.migrarMensajesDesdeUrl(oldUrl, usuarioId);
            }
            // Note: We intentionally avoid performing an in-place reassignment on the shared DB here
            // because bulk UPDATEs caused unique-index violations and race conditions when multiple
            // clients accessed the same file. Migration (copying rows) is safer and avoids locking.
        } catch (Exception ignored) {
        }
    }

    private static String crearTokenSesion() {
        try {
            long pid = -1;
            try { pid = ProcessHandle.current().pid(); } catch (Throwable ignored) {}
            String base = Long.toString(Math.abs(System.currentTimeMillis()));
            String tail = base.substring(Math.max(0, base.length() - 6));
            return (pid > 0 ? ("p" + pid + "_") : "p_") + tail;
        } catch (Exception e) {
            return Long.toHexString(System.nanoTime());
        }
    }

    public Long obtenerContextoUsuarioId() {
        return contextoUsuarioId;
    }

    public String obtenerContextoUsuarioNombre() {
        return contextoUsuarioNombre;
    }

    private void inicializarEsquemaSiHaceFalta() throws SQLException {
        if (esquemaInicializado.get()) {
            return;
        }
        synchronized (esquemaInicializado) {
            if (esquemaInicializado.get()) {
                return;
            }
            try {
                configuracion.inicializarEsquemaSiEsNecesario();
                esquemaInicializado.set(true);
            } catch (SQLException e) {
                esquemaInicializado.set(false);
                throw e;
            }
        }
    }
}

