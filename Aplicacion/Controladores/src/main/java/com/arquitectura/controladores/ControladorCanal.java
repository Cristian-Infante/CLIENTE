package com.arquitectura.controladores;

import com.arquitectura.entidades.CanalLocal;
import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.servicios.ServicioConexionChat;
import com.arquitectura.servicios.ServicioComandosChat;

import java.util.Collections;
import java.util.List;

public class ControladorCanal {
    private final ClienteLocal clienteActual;
    private final ServicioConexionChat conexion;

    public ControladorCanal(ClienteLocal clienteActual, ServicioConexionChat conexion) {
        this.clienteActual = clienteActual;
        this.conexion = conexion;
    }

    public List<CanalLocal> obtenerMisCanales() {
        try {
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            java.util.List<com.arquitectura.entidades.CanalLocal> lista = comandos.listarCanalesYEsperar(6000);
            if (lista == null) return java.util.Collections.emptyList();
            return lista;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Object> obtenerSolicitudesPendientes() {
        return Collections.emptyList();
    }

    public boolean aceptarSolicitud(Long canalId) {
        try {
            if (canalId == null) return false;
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            return comandos.aceptarInvitacion(canalId);
        } catch (Exception e) { return false; }
    }

    public boolean rechazarSolicitud(Long canalId) {
        try {
            if (canalId == null) return false;
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            return comandos.rechazarInvitacion(canalId);
        } catch (Exception e) { return false; }
    }

    public int contarSolicitudesPendientes() {
        try {
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            java.util.List<com.arquitectura.servicios.ServicioComandosChat.InvRecibida> rec =
                    comandos.listarInvitacionesRecibidasYEsperar(6000);
            return rec != null ? rec.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean invitarUsuarioACanal(Long canalId, Long usuarioId, String mensaje) {
        try {
            if (canalId == null || usuarioId == null) return false;
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            return comandos.invitarUsuarioACanal(canalId, usuarioId);
        } catch (Exception e) {
            return false;
        }
    }

    public java.util.List<com.arquitectura.servicios.ServicioComandosChat.InvRecibida> listarInvitacionesRecibidas() {
        try {
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            return comandos.listarInvitacionesRecibidasYEsperar(6000);
        } catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    public java.util.List<com.arquitectura.servicios.ServicioComandosChat.InvEnviada> listarInvitacionesEnviadas() {
        try {
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            return comandos.listarInvitacionesEnviadasYEsperar(6000);
        } catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    public boolean abandonarCanal(Long canalId) { return false; }

    public boolean crearCanal(String nombre, boolean privado) {
        try {
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            return comandos.crearCanal(nombre, privado);
        } catch (Exception e) {
            return false;
        }
    }

    public java.util.List<ClienteLocal> listarUsuariosRegistrados() {
        try {
            if (!conexion.estaConectado()) conexion.conectar();
            ServicioComandosChat comandos = new ServicioComandosChat(conexion);
            java.util.List<ClienteLocal> lista = comandos.listarUsuariosYEsperar(6000);
            if (lista == null) return java.util.Collections.emptyList();
            Long miId = clienteActual != null ? clienteActual.getId() : null;
            String miUsuario = clienteActual != null ? clienteActual.getNombreDeUsuario() : null;
            String miEmail = clienteActual != null ? clienteActual.getEmail() : null;
            String miUsuarioNorm = miUsuario != null ? miUsuario.trim().toLowerCase() : null;
            String miEmailNorm = miEmail != null ? miEmail.trim().toLowerCase() : null;
            java.util.List<ClienteLocal> filtrada = new java.util.ArrayList<>();
            for (ClienteLocal u : lista) {
                if (u == null) continue;
                // Excluirse por id cuando sea posible
                if (miId != null && miId.equals(u.getId())) continue;
                // Excluirse por nombre de usuario (normalizado)
                String uUsuarioNorm = u.getNombreDeUsuario() != null ? u.getNombreDeUsuario().trim().toLowerCase() : null;
                if (miUsuarioNorm != null && miUsuarioNorm.equals(uUsuarioNorm)) continue;
                // Excluirse por email (normalizado), si se dispone
                String uEmailNorm = u.getEmail() != null ? u.getEmail().trim().toLowerCase() : null;
                if (miEmailNorm != null && miEmailNorm.equals(uEmailNorm)) continue;
                filtrada.add(u);
            }
            return filtrada;
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }
}
