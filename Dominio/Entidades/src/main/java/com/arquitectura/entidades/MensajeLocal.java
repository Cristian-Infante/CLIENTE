package com.arquitectura.entidades;

import java.time.LocalDateTime;

public abstract class MensajeLocal {
    private Long id;
    private LocalDateTime timeStamp;
    private String tipo;
    private Long emisor;
    private String emisorNombre;
    private Long receptor;
    private String receptorNombre;
    private Long canalId;

    public MensajeLocal() {
    }

    public MensajeLocal(Long id, LocalDateTime timeStamp, String tipo, Long emisor, String emisorNombre, Long receptor, String receptorNombre, Long canalId) {
        this.id = id;
        this.timeStamp = timeStamp;
        this.tipo = tipo;
        this.emisor = emisor;
        this.emisorNombre = emisorNombre;
        this.receptor = receptor;
        this.receptorNombre = receptorNombre;
        this.canalId = canalId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(LocalDateTime timeStamp) {
        this.timeStamp = timeStamp;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getEmisor() {
        return emisor;
    }

    public void setEmisor(Long emisor) {
        this.emisor = emisor;
    }

    public String getEmisorNombre() {
        return emisorNombre;
    }

    public void setEmisorNombre(String emisorNombre) {
        this.emisorNombre = emisorNombre;
    }

    public Long getReceptor() {
        return receptor;
    }

    public void setReceptor(Long receptor) {
        this.receptor = receptor;
    }

    public String getReceptorNombre() {
        return receptorNombre;
    }

    public void setReceptorNombre(String receptorNombre) {
        this.receptorNombre = receptorNombre;
    }

    public Long getCanalId() {
        return canalId;
    }

    public void setCanalId(Long canalId) {
        this.canalId = canalId;
    }
}
