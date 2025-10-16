package com.arquitectura.servicios;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServicioEventosMensajes {
    private final List<OyenteActualizacionMensajes> oyentes = new CopyOnWriteArrayList<>();

    private static class Holder { private static final ServicioEventosMensajes INST = new ServicioEventosMensajes(); }
    public static ServicioEventosMensajes instancia() { return Holder.INST; }

    public void registrar(OyenteActualizacionMensajes o) { if (o != null) oyentes.add(o); }
    public void remover(OyenteActualizacionMensajes o) { oyentes.remove(o); }

    public void notificarCanal(Long canalId) {
        for (OyenteActualizacionMensajes o : oyentes) try { o.onCanalActualizado(canalId); } catch (Exception ignored) {}
    }

    public void notificarPrivado(Long usuarioId) {
        for (OyenteActualizacionMensajes o : oyentes) try { o.onPrivadoActualizado(usuarioId); } catch (Exception ignored) {}
    }
}

