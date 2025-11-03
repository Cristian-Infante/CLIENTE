package com.arquitectura.entidades;

public class CanalLocal {
    private Long id;
    private String nombre;
    private Boolean privado;
    private java.util.List<ClienteLocal> miembros;

    public CanalLocal() {
    }

    public CanalLocal(Long id, String nombre, Boolean privado) {
        this.id = id;
        this.nombre = nombre;
        this.privado = privado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Boolean getPrivado() {
        return privado;
    }

    public void setPrivado(Boolean privado) {
        this.privado = privado;
    }

    public java.util.List<ClienteLocal> getMiembros() {
        return miembros;
    }

    public void setMiembros(java.util.List<ClienteLocal> miembros) {
        this.miembros = miembros;
    }
}
