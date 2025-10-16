package com.arquitectura.entidades;

public class TextoMensajeLocal extends MensajeLocal {
    private String contenido;

    public TextoMensajeLocal() {
    }

    public TextoMensajeLocal(String contenido) {
        this.contenido = contenido;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }
}

