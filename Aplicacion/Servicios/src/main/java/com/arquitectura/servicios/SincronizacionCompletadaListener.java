package com.arquitectura.servicios;

/**
 * Listener para eventos de completado de sincronización de mensajes
 */
public interface SincronizacionCompletadaListener {
    /**
     * Se llama cuando la sincronización de mensajes se completa
     * @param insertados número de mensajes insertados exitosamente
     * @param exito true si la sincronización fue exitosa, false si hubo errores
     * @param mensajeError mensaje de error si exito es false, null si exito es true
     */
    void onSincronizacionCompletada(int insertados, boolean exito, String mensajeError);
}