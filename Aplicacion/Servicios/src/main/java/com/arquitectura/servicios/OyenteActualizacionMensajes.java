package com.arquitectura.servicios;

public interface OyenteActualizacionMensajes {
    default void onCanalActualizado(Long canalId) {}
    default void onPrivadoActualizado(Long usuarioId) {}
}

