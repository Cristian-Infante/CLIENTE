package com.arquitectura.servicios;

import com.arquitectura.config.ProveedorConexionCliente;

public final class ServicioContextoDatos {
    private ServicioContextoDatos() {}

    public static void configurarUsuarioActual(Long usuarioId, String nombreUsuario) {
        try {
            ProveedorConexionCliente.instancia().configurarContextoUsuario(usuarioId, nombreUsuario);
        } catch (Exception ignored) {
        }
    }
}

