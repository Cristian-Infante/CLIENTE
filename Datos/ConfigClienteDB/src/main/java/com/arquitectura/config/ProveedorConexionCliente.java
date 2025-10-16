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

    private void inicializarEsquemaSiHaceFalta() throws SQLException {
        if (esquemaInicializado.compareAndSet(false, true)) {
            configuracion.inicializarEsquemaSiEsNecesario();
        }
    }
}

