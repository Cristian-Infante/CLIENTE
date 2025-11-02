package com.arquitectura.servicios;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServicioEventosMensajes {
    private final List<OyenteActualizacionMensajes> oyentes = new CopyOnWriteArrayList<>();

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

    public void notificarPrivado(Long usuarioId) {
        System.out.println("[ServicioEventosMensajes] notificarPrivado usuarioId=" + usuarioId + " listeners=" + oyentes.size());
        for (OyenteActualizacionMensajes o : oyentes) {
            try { o.onPrivadoActualizado(usuarioId); } catch (Exception ex) { System.out.println("[ServicioEventosMensajes] error al notificar privado: " + ex); }
        }
    }
}

