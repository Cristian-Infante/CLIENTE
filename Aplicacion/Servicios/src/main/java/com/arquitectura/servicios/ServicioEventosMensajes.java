package com.arquitectura.servicios;

import com.arquitectura.entidades.ClienteLocal;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServicioEventosMensajes {
    private final List<OyenteActualizacionMensajes> oyentes = new CopyOnWriteArrayList<>();
    
    // Mapa global de estados de conexión por nombre de usuario (fuente de verdad P2P)
    // Clave: nombre de usuario en minúsculas, Valor: true=conectado, false=desconectado
    private final Map<String, Boolean> estadosGlobalesPorNombre = new ConcurrentHashMap<>();

    public static class EventoInvitacion {
        private final String tipoEvento;
        private final Long canalId;
        private final String canalUuid;
        private final String canalNombre;
        private final Boolean canalPrivado;
        private final Long invitadorId;
        private final String invitadorNombre;
        private final Long invitadoId;
        private final String invitadoNombre;
        private final String estado;
        private final Long invitacionId;
        private final String timestampIso;

        public EventoInvitacion(String tipoEvento,
                                 Long canalId,
                                 String canalUuid,
                                 String canalNombre,
                                 Boolean canalPrivado,
                                 Long invitadorId,
                                 String invitadorNombre,
                                 Long invitadoId,
                                 String invitadoNombre,
                                 String estado,
                                 Long invitacionId,
                                 String timestampIso) {
            this.tipoEvento = tipoEvento;
            this.canalId = canalId;
            this.canalUuid = canalUuid;
            this.canalNombre = canalNombre;
            this.canalPrivado = canalPrivado;
            this.invitadorId = invitadorId;
            this.invitadorNombre = invitadorNombre;
            this.invitadoId = invitadoId;
            this.invitadoNombre = invitadoNombre;
            this.estado = estado;
            this.invitacionId = invitacionId;
            this.timestampIso = timestampIso;
        }

        public String getTipoEvento() { return tipoEvento; }
        public Long getCanalId() { return canalId; }
        public String getCanalUuid() { return canalUuid; }
        public String getCanalNombre() { return canalNombre; }
        public Boolean getCanalPrivado() { return canalPrivado; }
        public Long getInvitadorId() { return invitadorId; }
        public String getInvitadorNombre() { return invitadorNombre; }
        public Long getInvitadoId() { return invitadoId; }
        public String getInvitadoNombre() { return invitadoNombre; }
        public String getEstado() { return estado; }
        public Long getInvitacionId() { return invitacionId; }
        public String getTimestampIso() { return timestampIso; }

        @Override
        public String toString() {
            return "EventoInvitacion{" +
                    "tipoEvento='" + tipoEvento + '\'' +
                    ", canalId=" + canalId +
                    ", canalUuid='" + canalUuid + '\'' +
                    ", canalNombre='" + canalNombre + '\'' +
                    ", canalPrivado=" + canalPrivado +
                    ", invitadorId=" + invitadorId +
                    ", invitadoId=" + invitadoId +
                    ", estado='" + estado + '\'' +
                    ", invitacionId=" + invitacionId +
                    ", timestampIso='" + timestampIso + '\'' +
                    '}';
        }
    }

    private static class Holder { private static final ServicioEventosMensajes INST = new ServicioEventosMensajes(); }
    public static ServicioEventosMensajes instancia() { return Holder.INST; }

    public void registrar(OyenteActualizacionMensajes o) { if (o != null) oyentes.add(o); }
    public void remover(OyenteActualizacionMensajes o) { oyentes.remove(o); }

    public void registrar(OyenteActualizacionMensajes o, boolean debug) {
        registrar(o);
        if (debug) System.out.println("[ServicioEventosMensajes] registrar oyente, total=" + oyentes.size());
    }

    public void remover(OyenteActualizacionMensajes o, boolean debug) {
        remover(o);
        if (debug) System.out.println("[ServicioEventosMensajes] remover oyente, total=" + oyentes.size());
    }

    public void notificarCanal(Long canalId) {
        System.out.println("[ServicioEventosMensajes] notificarCanal canalId=" + canalId + " listeners=" + oyentes.size());
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onCanalActualizado(canalId); } catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error al notificar canal: " + ex); }
        }
    }

    /** Notifica canal usando UUID (para compatibilidad P2P con IDs diferentes entre servidores) */
    public void notificarCanalPorUuid(String canalUuid, Long canalIdRemoto) {
        System.out.println("[ServicioEventosMensajes] notificarCanalPorUuid uuid=" + canalUuid + " idRemoto=" + canalIdRemoto + " listeners=" + oyentes.size());
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onCanalActualizadoPorUuid(canalUuid, canalIdRemoto); } catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error al notificar canal por uuid: " + ex); }
        }
    }

    public void notificarPrivado(Long usuarioId) {
        System.out.println("[ServicioEventosMensajes] notificarPrivado usuarioId=" + usuarioId + " listeners=" + oyentes.size());
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onPrivadoActualizado(usuarioId); } catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error al notificar privado: " + ex); }
        }
    }

    /** Notifica chat privado usando nombre de usuario (para compatibilidad P2P con IDs diferentes entre servidores) */
    public void notificarPrivadoPorNombre(String nombreUsuario, Long idRemoto) {
        System.out.println("[ServicioEventosMensajes] notificarPrivadoPorNombre nombre=" + nombreUsuario + " idRemoto=" + idRemoto + " listeners=" + oyentes.size());
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onPrivadoActualizadoPorNombre(nombreUsuario, idRemoto); } catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error al notificar privado por nombre: " + ex); }
        }
    }

    public void notificarEstadoUsuarioActualizado(ClienteLocal usuario, Integer sesionesActivas, String timestampIso) {
        System.out.println("[ServicioEventosMensajes] notificarEstadoUsuario usuarioId="
                + (usuario != null ? usuario.getId() : null) + " listeners=" + oyentes.size());
        
        // Guardar estado en mapa global (fuente de verdad P2P - usa nombre, no ID)
        if (usuario != null && usuario.getNombreDeUsuario() != null) {
            String nombreNorm = usuario.getNombreDeUsuario().trim().toLowerCase();
            Boolean estado = usuario.getEstado();
            estadosGlobalesPorNombre.put(nombreNorm, Boolean.TRUE.equals(estado));
            System.out.println("[ServicioEventosMensajes] Estado global guardado: " + nombreNorm + " -> " + estado);
        }
        
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onEstadoUsuarioActualizado(usuario, sesionesActivas, timestampIso); }
            catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error al notificar estado de usuario: " + ex); }
        }
    }
    
    /**
     * Obtiene el mapa de estados globales por nombre de usuario.
     * Este mapa se actualiza con cada evento USER_STATUS_CHANGED y es la fuente de verdad
     * para el estado de conexión en entornos P2P donde los IDs pueden diferir entre servidores.
     * @return Mapa inmutable de estados (clave: nombre en minúsculas, valor: conectado)
     */
    public Map<String, Boolean> obtenerEstadosGlobalesPorNombre() {
        return new ConcurrentHashMap<>(estadosGlobalesPorNombre);
    }
    
    /**
     * Obtiene el estado de conexión de un usuario por nombre.
     * @param nombreUsuario Nombre del usuario
     * @return true si está conectado, false si está desconectado, null si no hay información
     */
    public Boolean obtenerEstadoPorNombre(String nombreUsuario) {
        if (nombreUsuario == null) return null;
        return estadosGlobalesPorNombre.get(nombreUsuario.trim().toLowerCase());
    }
    
    /**
     * Actualiza el estado de conexión de un usuario por nombre.
     * Usado para poblar el mapa desde LIST_CONNECTED en entornos P2P.
     * @param nombreUsuario Nombre del usuario (se normaliza a minúsculas)
     * @param conectado Estado de conexión
     */
    public void actualizarEstadoGlobalPorNombre(String nombreUsuario, boolean conectado) {
        if (nombreUsuario == null) return;
        String nombreNorm = nombreUsuario.trim().toLowerCase();
        estadosGlobalesPorNombre.put(nombreNorm, conectado);
        System.out.println("[ServicioEventosMensajes] Estado global actualizado manualmente: " + nombreNorm + " -> " + conectado);
    }

    public void notificarSincronizacionIniciada(Long totalEsperado) {
        System.out.println("[ServicioEventosMensajes] notificarSincronizacionIniciada total=" + totalEsperado
                + " listeners=" + oyentes.size());
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onSincronizacionMensajesIniciada(totalEsperado); }
            catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error notificando inicio de sincronizacion: " + ex); }
        }
    }

    public void notificarSincronizacionFinalizada(int insertados, Long totalEsperado, boolean exito, String mensajeError) {
        System.out.println("[ServicioEventosMensajes] notificarSincronizacionFinalizada insertados=" + insertados
                + ", total=" + totalEsperado + ", exito=" + exito + " listeners=" + oyentes.size());
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onSincronizacionMensajesFinalizada(insertados, totalEsperado, exito, mensajeError); }
            catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error notificando fin de sincronizacion: " + ex); }
        }
    }

    public void notificarInvitacionActualizada(EventoInvitacion evento) {
        System.out.println("[ServicioEventosMensajes] notificarInvitacionActualizada evento=" + evento
                + " listeners=" + oyentes.size());
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onInvitacionActualizada(evento); }
            catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error al notificar invitacion: " + ex); }
        }
    }
}
