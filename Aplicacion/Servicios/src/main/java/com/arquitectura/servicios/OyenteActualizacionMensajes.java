package com.arquitectura.servicios;

import com.arquitectura.entidades.ClienteLocal;

public interface OyenteActualizacionMensajes {
    default void onCanalActualizado(Long canalId) {}
    default void onPrivadoActualizado(Long usuarioId) {}
    default void onEstadoUsuarioActualizado(ClienteLocal usuario, Integer sesionesActivas, String timestampIso) {}
    default void onSincronizacionMensajesIniciada(Long totalEsperado) {}
    default void onSincronizacionMensajesFinalizada(int insertados, Long totalEsperado, boolean exito, String mensajeError) {}
    default void onInvitacionActualizada(ServicioEventosMensajes.EventoInvitacion evento) {}
}

