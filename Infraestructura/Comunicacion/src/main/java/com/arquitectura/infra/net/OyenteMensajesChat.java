package com.arquitectura.infra.net;

public interface OyenteMensajesChat {
    void alRecibirMensaje(String mensaje);

    default void alCerrar() {}

    default void alError(Exception e) {}
}
