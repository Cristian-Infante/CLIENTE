package com.arquitectura.servicios;

import com.arquitectura.entidades.ClienteLocal;

public interface OyenteActualizacionMensajes {
    default void onCanalActualizado(Long canalId) {}
    /** Notifica actualización de canal por UUID (útil en entornos P2P con IDs inconsistentes) */
    default void onCanalActualizadoPorUuid(String canalUuid, Long canalIdRemoto) {}
    default void onPrivadoActualizado(Long usuarioId) {}
    /** Notifica actualización de chat privado por nombre de usuario (útil en entornos P2P con IDs inconsistentes) */
    default void onPrivadoActualizadoPorNombre(String nombreUsuario, Long idRemoto) {}
    default void onEstadoUsuarioActualizado(ClienteLocal usuario, Integer sesionesActivas, String timestampIso) {}
    default void onSincronizacionMensajesIniciada(Long totalEsperado) {}
    default void onSincronizacionMensajesFinalizada(int insertados, Long totalEsperado, boolean exito, String mensajeError) {}
    default void onInvitacionActualizada(ServicioEventosMensajes.EventoInvitacion evento) {}
}

