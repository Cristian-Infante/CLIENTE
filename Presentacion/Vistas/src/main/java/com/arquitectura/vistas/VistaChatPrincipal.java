package com.arquitectura.vistas;

import com.arquitectura.controladores.ControladorAudio;
import com.arquitectura.controladores.ControladorCanal;
import com.arquitectura.controladores.ControladorChat;
import com.arquitectura.entidades.CanalLocal;
import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.servicios.ServicioConexionChat;
import com.arquitectura.servicios.ObservadorEventosChat;
import com.arquitectura.infra.net.OyenteMensajesChat;
import com.arquitectura.servicios.OyenteActualizacionMensajes;
import com.arquitectura.servicios.ServicioEventosMensajes;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class VistaChatPrincipal extends JFrame {
    private final ClienteLocal usuarioActual;
    private final ServicioConexionChat clienteTCP;
    private volatile boolean notificadoDesconexion = false;
    private OyenteMensajesChat oyenteEventos;

    // Controladores
    private final ControladorChat chatController;
    private final ControladorCanal canalController;
    private final ControladorAudio audioController;

    // Componentes principales
    private JPanel panelIzquierdo;
    private JPanel panelCentral;
    private JPanel panelDerecho;

    // Barra de herramientas
    private JLabel lblUsuario;
    private JLabel lblEstadoConexion;
    private JButton btnCanales;
    private JButton btnSolicitudes;
    private JButton btnCerrarSesion;

    // Panel izquierdo - Lista de conversaciones
    private JTabbedPane tabsConversaciones;
    private DefaultListModel<ClienteLocal> modeloUsuarios;
    private JList<ClienteLocal> listaUsuarios;
    private DefaultListModel<CanalLocal> modeloCanales;
    private JList<CanalLocal> listaCanales;

    // Panel central - Chat
    private JTextArea areaMensajes;
    private JTextField txtMensaje;
    private JButton btnEnviar;
    private JButton btnGrabador;
    private JLabel lblDestinatario;

    // Panel derecho - Información
    private JTextArea areaInfo;

    // Estado actual de selección
    private ClienteLocal usuarioSeleccionado;
    private CanalLocal canalSeleccionado;
    private OyenteActualizacionMensajes oyenteMensajes;

    public VistaChatPrincipal(ClienteLocal usuario, ServicioConexionChat clienteTCP) {
        this.usuarioActual = usuario;
        this.clienteTCP = clienteTCP;

        this.chatController = new ControladorChat(usuarioActual, clienteTCP);
        this.canalController = new ControladorCanal(usuarioActual, clienteTCP);
        this.audioController = new ControladorAudio(usuarioActual, clienteTCP);

        try { ObservadorEventosChat.instancia().registrarEn(clienteTCP); } catch (Exception ignored) {}

        inicializarComponentes();
        configurarVentana();
        registrarOyenteEventos();
    }

    private void registrarOyenteEventos() {
        oyenteEventos = new OyenteMensajesChat() {
            @Override public void alRecibirMensaje(String mensaje) {
                if (mensaje == null || notificadoDesconexion) return;
                String compact = mensaje.replaceAll("\\s+", "");
                if (compact.contains("\"command\":\"EVENT\"")) {
                    String tipo = extraerCampo(compact, "tipo");
                    if ("KICKED".equalsIgnoreCase(tipo)) {
                        notificadoDesconexion = true;
                        String msg = extraerCampo(mensaje, "mensaje");
                        if (msg == null || msg.isEmpty()) msg = "El servidor cerró esta conexión.";
                        mostrarDialogoYSalir("Conexión expulsada", msg);
                    } else if ("SERVER_SHUTDOWN".equalsIgnoreCase(tipo)) {
                        notificadoDesconexion = true;
                        String msg = extraerCampo(mensaje, "mensaje");
                        if (msg == null || msg.isEmpty()) msg = "El servidor se está apagando. Serás desconectado.";
                        mostrarDialogoYSalir("Servidor apagándose", msg);
                    }
                }
            }

            @Override public void alCerrar() {
                if (notificadoDesconexion) return;
                notificadoDesconexion = true;
                mostrarDialogoYSalir("Servidor desconectado", "El servidor se apagó o se perdió la conexión. La aplicación se cerrará.");
            }
        };
        try { clienteTCP.registrarOyente(oyenteEventos); } catch (Exception ignored) {}
    }

    private static String extraerCampo(String jsonLinea, String campo) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(campo) + "\\\"\\s*:\\s*\\\"(.*?)\\\"");
            java.util.regex.Matcher m = p.matcher(jsonLinea);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private void mostrarDialogoYSalir(String titulo, String mensaje) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                JOptionPane.showMessageDialog(this, mensaje, titulo, JOptionPane.WARNING_MESSAGE);
            } catch (Exception ignored) {}
            try { dispose(); } catch (Exception ignored) {}
            System.exit(0);
        });
    }

    private void inicializarComponentes() {
        setLayout(new BorderLayout());
        add(crearBarraSuperior(), BorderLayout.NORTH);

        JSplitPane splitPrincipal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPrincipal.setLeftComponent(crearPanelIzquierdo());

        JSplitPane splitDerecho = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitDerecho.setLeftComponent(crearPanelCentral());
        splitDerecho.setRightComponent(crearPanelDerecho());
        splitDerecho.setDividerLocation(600);

        splitPrincipal.setRightComponent(splitDerecho);
        splitPrincipal.setDividerLocation(250);
        add(splitPrincipal, BorderLayout.CENTER);

        add(crearBarraEstado(), BorderLayout.SOUTH);
    }

    private JPanel crearBarraSuperior() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(5, 10, 5, 10));
        panel.setBackground(new Color(37, 211, 102));

        lblUsuario = new JLabel("Usuario: " + usuarioActual.getNombreDeUsuario());
        lblUsuario.setFont(new Font("Arial", Font.BOLD, 16));
        lblUsuario.setForeground(Color.WHITE);
        panel.add(lblUsuario, BorderLayout.WEST);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panelBotones.setOpaque(false);

        btnCanales = new JButton("Canales");
        btnCanales.addActionListener(e -> mostrarVentanaCanales());
        btnSolicitudes = new JButton("Solicitudes");
        btnSolicitudes.addActionListener(e -> mostrarVentanaSolicitudes());
        btnCerrarSesion = new JButton("Salir");
        btnCerrarSesion.addActionListener(e -> cerrarSesion());

        panelBotones.add(btnCanales);
        panelBotones.add(btnSolicitudes);
        panelBotones.add(btnCerrarSesion);
        panel.add(panelBotones, BorderLayout.EAST);
        return panel;
    }

    private JPanel crearPanelIzquierdo() {
        panelIzquierdo = new JPanel(new BorderLayout());
        panelIzquierdo.setBorder(BorderFactory.createTitledBorder("Conversaciones"));

        tabsConversaciones = new JTabbedPane();

        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setCellRenderer(new UsuarioListRenderer());
        listaUsuarios.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) seleccionarUsuario(); });
        JScrollPane scrollUsuarios = new JScrollPane(listaUsuarios);
        tabsConversaciones.addTab("Usuarios", scrollUsuarios);

        modeloCanales = new DefaultListModel<>();
        listaCanales = new JList<>(modeloCanales);
        listaCanales.setCellRenderer(new CanalListRenderer());
        listaCanales.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) seleccionarCanal(); });
        JScrollPane scrollCanales = new JScrollPane(listaCanales);
        tabsConversaciones.addTab("Mis Canales", scrollCanales);

        // Cargar al seleccionar pestaña y al hacer clic en el título
        tabsConversaciones.addChangeListener(e -> refrescarSegunTabSeleccionada());
        tabsConversaciones.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { refrescarSegunTabSeleccionada(); }
        });

        panelIzquierdo.add(tabsConversaciones, BorderLayout.CENTER);
        return panelIzquierdo;
    }

    private void refrescarSegunTabSeleccionada() {
        int idx = tabsConversaciones.getSelectedIndex();
        if (idx == 0) {
            cargarUsuariosConectados();
        } else if (idx == 1) {
            cargarListaMisCanales();
        }
    }

    private void cargarUsuariosConectados() {
        try {
            if (!clienteTCP.estaConectado()) clienteTCP.conectar();
            com.arquitectura.servicios.ServicioComandosChat comandos = new com.arquitectura.servicios.ServicioComandosChat(clienteTCP);
            java.util.List<ClienteLocal> conectados = comandos.listarConectadosYEsperar(6000);
            modeloUsuarios.clear();
            if (conectados != null) {
                // Excluir al usuario actual
                Long miId = usuarioActual != null ? usuarioActual.getId() : null;
                String miUser = usuarioActual != null ? usuarioActual.getNombreDeUsuario() : null;
                String miNorm = miUser != null ? miUser.trim().toLowerCase() : null;
                for (ClienteLocal u : conectados) {
                    if (u == null) continue;
                    if (miId != null && miId.equals(u.getId())) continue;
                    String uNorm = u.getNombreDeUsuario() != null ? u.getNombreDeUsuario().trim().toLowerCase() : null;
                    if (miNorm != null && miNorm.equals(uNorm)) continue;
                    modeloUsuarios.addElement(u);
                }
            }
        } catch (Exception ignored) {
            // No-op
        }
    }

    private void cargarListaMisCanales() {
        java.util.List<CanalLocal> mis = canalController.obtenerMisCanales();
        modeloCanales.clear();
        if (mis != null) for (CanalLocal c : mis) if (c != null) modeloCanales.addElement(c);
    }

    private JPanel crearPanelCentral() {
        panelCentral = new JPanel(new BorderLayout());

        JPanel panelEncabezado = new JPanel(new BorderLayout());
        panelEncabezado.setBorder(new EmptyBorder(5, 10, 5, 10));
        panelEncabezado.setBackground(new Color(240, 240, 240));
        lblDestinatario = new JLabel("Seleccione una conversacion");
        lblDestinatario.setFont(new Font("Arial", Font.BOLD, 14));
        panelEncabezado.add(lblDestinatario, BorderLayout.CENTER);

        areaMensajes = new JTextArea();
        areaMensajes.setEditable(false);
        areaMensajes.setLineWrap(true);
        areaMensajes.setWrapStyleWord(true);
        JScrollPane scrollMensajes = new JScrollPane(areaMensajes);

        JPanel panelEntrada = new JPanel(new BorderLayout(5, 5));
        panelEntrada.setBorder(new EmptyBorder(5, 5, 5, 5));
        txtMensaje = new JTextField();
        txtMensaje.addActionListener(e -> enviarMensaje());

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        btnGrabador = new JButton("Grabar audio");
        btnGrabador.setToolTipText("Grabar y enviar audio");
        btnGrabador.addActionListener(e -> abrirGrabadorAudio());

        btnEnviar = new JButton("Enviar");
        btnEnviar.addActionListener(e -> enviarMensaje());

        panelBotones.add(btnGrabador);
        panelBotones.add(btnEnviar);
        panelEntrada.add(txtMensaje, BorderLayout.CENTER);
        panelEntrada.add(panelBotones, BorderLayout.EAST);

        panelCentral.add(panelEncabezado, BorderLayout.NORTH);
        panelCentral.add(scrollMensajes, BorderLayout.CENTER);
        panelCentral.add(panelEntrada, BorderLayout.SOUTH);
        return panelCentral;
    }

    private JPanel crearPanelDerecho() {
        panelDerecho = new JPanel(new BorderLayout());
        panelDerecho.setBorder(BorderFactory.createTitledBorder("Información"));
        areaInfo = new JTextArea();
        areaInfo.setEditable(false);
        areaInfo.setLineWrap(true);
        areaInfo.setWrapStyleWord(true);
        panelDerecho.add(new JScrollPane(areaInfo), BorderLayout.CENTER);
        return panelDerecho;
    }

    private JPanel crearBarraEstado() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(4, 8, 4, 8));
        lblEstadoConexion = new JLabel("Conectado");
        panel.add(lblEstadoConexion, BorderLayout.WEST);
        return panel;
    }

    private void seleccionarUsuario() {
        usuarioSeleccionado = listaUsuarios.getSelectedValue();
        canalSeleccionado = null;
        if (usuarioSeleccionado != null) {
            lblDestinatario.setText("Destino: " + usuarioSeleccionado.getNombreDeUsuario());
            areaMensajes.setText("");
            mostrarInfoUsuario();
            // Cargar historial privado local
            java.util.List<String> hist = chatController.obtenerConversacion(usuarioSeleccionado.getId());
            if (hist != null) {
                for (String l : hist) areaMensajes.append(l + "\n");
                areaMensajes.setCaretPosition(areaMensajes.getDocument().getLength());
            }
            subscribirActualizacionesPrivado(usuarioSeleccionado.getId());
        }
    }

    private void seleccionarCanal() {
        canalSeleccionado = listaCanales.getSelectedValue();
        usuarioSeleccionado = null;
        if (canalSeleccionado != null) {
            lblDestinatario.setText("Canal: " + canalSeleccionado.getNombre());
            areaMensajes.setText("");
            mostrarInfoCanal();
            // Cargar historial local del canal desde H2
            java.util.List<String> historial = chatController.obtenerMensajesCanal(canalSeleccionado.getId());
            if (historial != null) {
                for (String linea : historial) {
                    areaMensajes.append(linea + "\n");
                }
                areaMensajes.setCaretPosition(areaMensajes.getDocument().getLength());
            }
            // Suscribir actualización en caliente para este canal
            subscribirActualizacionesCanal(canalSeleccionado.getId());
        }
    }

    private void subscribirActualizacionesCanal(Long canalId) {
        if (oyenteMensajes != null) {
            try { ServicioEventosMensajes.instancia().remover(oyenteMensajes); } catch (Exception ignored) {}
            oyenteMensajes = null;
        }
        oyenteMensajes = new OyenteActualizacionMensajes() {
            @Override public void onCanalActualizado(Long id) {
                if (id == null || canalSeleccionado == null) return;
                if (!id.equals(canalSeleccionado.getId())) return;
                java.util.List<String> hist = chatController.obtenerMensajesCanal(id);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    areaMensajes.setText("");
                    if (hist != null) for (String l : hist) areaMensajes.append(l + "\n");
                    areaMensajes.setCaretPosition(areaMensajes.getDocument().getLength());
                });
            }
        };
        ServicioEventosMensajes.instancia().registrar(oyenteMensajes);
    }

    private void subscribirActualizacionesPrivado(Long usuarioId) {
        if (oyenteMensajes != null) {
            try { ServicioEventosMensajes.instancia().remover(oyenteMensajes); } catch (Exception ignored) {}
            oyenteMensajes = null;
        }
        oyenteMensajes = new OyenteActualizacionMensajes() {
            @Override public void onPrivadoActualizado(Long id) {
                if (id == null || usuarioSeleccionado == null) return;
                if (!id.equals(usuarioSeleccionado.getId())) return;
                java.util.List<String> hist = chatController.obtenerConversacion(id);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    areaMensajes.setText("");
                    if (hist != null) for (String l : hist) areaMensajes.append(l + "\n");
                    areaMensajes.setCaretPosition(areaMensajes.getDocument().getLength());
                });
            }
        };
        ServicioEventosMensajes.instancia().registrar(oyenteMensajes);
    }

    private void enviarMensaje() {
        String texto = txtMensaje.getText().trim();
        if (texto.isEmpty()) return;
        boolean enviado = false;
        if (usuarioSeleccionado != null) {
            enviado = chatController.enviarMensajeUsuario(
                    usuarioSeleccionado.getId(),
                    usuarioSeleccionado.getNombreDeUsuario(),
                    texto
            );
        } else if (canalSeleccionado != null) {
            enviado = chatController.enviarMensajeCanal(canalSeleccionado.getId(), canalSeleccionado.getNombre(), texto);
        }
        if (enviado) {
            mostrarMensajeEnChat("Yo: " + texto);
            txtMensaje.setText("");
        } else {
            JOptionPane.showMessageDialog(this, "Error al enviar", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void abrirGrabadorAudio() {
        if (usuarioSeleccionado == null && canalSeleccionado == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un chat o canal antes de grabar audio", "Sin destino", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DialogoGrabacionAudio dlg = new DialogoGrabacionAudio(SwingUtilities.getWindowAncestor(this));
        dlg.setOyenteEnvio((archivoWav, datosWav) -> {
            dlg.dispose();
            enviarAudioGrabado(archivoWav, datosWav);
        });
        dlg.setVisible(true);
    }

    private void enviarAudioGrabado(java.io.File archivoWav, byte[] datosWav) {
        if (datosWav == null || datosWav.length == 0) {
            JOptionPane.showMessageDialog(this, "No se capturó audio para enviar", "Audio vacío", JOptionPane.ERROR_MESSAGE);
            return;
        }
        final ClienteLocal usuarioDestino = usuarioSeleccionado;
        final CanalLocal canalDestino = canalSeleccionado;
        if (usuarioDestino == null && canalDestino == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un chat o canal antes de enviar audio", "Sin destino", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Thread envio = new Thread(() -> {
            ControladorAudio.ResultadoEnvioAudio resultado;
            if (usuarioDestino != null) {
                resultado = audioController.enviarAudioAPrivado(usuarioDestino.getId(), usuarioDestino.getNombreDeUsuario(), archivoWav, datosWav);
            } else {
                resultado = audioController.enviarAudioACanal(canalDestino.getId(), canalDestino.getNombre(), archivoWav, datosWav);
            }
            final ControladorAudio.ResultadoEnvioAudio resFinal = resultado;
            SwingUtilities.invokeLater(() -> {
                if (resFinal != null && resFinal.exito) {
                    String ruta = resFinal.rutaArchivo != null ? resFinal.rutaArchivo : "";
                    mostrarMensajeEnChat("Yo: [Audio] " + ruta);
                    if (resFinal.mensaje != null && !resFinal.mensaje.isBlank()) {
                        JOptionPane.showMessageDialog(this, resFinal.mensaje, "Audio enviado", JOptionPane.INFORMATION_MESSAGE);
                    }
                } else {
                    String msg = (resFinal != null && resFinal.mensaje != null && !resFinal.mensaje.isBlank()) ? resFinal.mensaje : "No se pudo enviar el audio";
                    JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }, "envio-audio");
        envio.setDaemon(true);
        envio.start();
    }

    private void mostrarInfoUsuario() {
        if (usuarioSeleccionado == null) return;
        StringBuilder info = new StringBuilder();
        info.append("INFORMACIÓN DEL USUARIO\n\n");
        info.append("Nombre: ").append(usuarioSeleccionado.getNombreDeUsuario()).append("\n");
        info.append("Email: ").append(usuarioSeleccionado.getEmail() == null ? "-" : usuarioSeleccionado.getEmail()).append("\n");
        info.append("Estado: ").append(Boolean.TRUE.equals(usuarioSeleccionado.getEstado()) ? "Conectado" : "Desconectado").append("\n");
        areaInfo.setText(info.toString());
    }

    private void mostrarInfoCanal() {
        if (canalSeleccionado == null) return;
        StringBuilder info = new StringBuilder();
        info.append("INFORMACIÓN DEL CANAL\n\n");
        info.append("Nombre: ").append(canalSeleccionado.getNombre()).append("\n");
        info.append("Privado: ").append(Boolean.TRUE.equals(canalSeleccionado.getPrivado()) ? "Sí" : "No").append("\n");
        areaInfo.setText(info.toString());
    }

    private void mostrarVentanaCanales() {
        VistaCanal vista = new VistaCanal(usuarioActual, clienteTCP);
        vista.setVisible(true);
    }

    private void mostrarVentanaSolicitudes() {
        JDialog dialog = new JDialog(this, "Solicitudes", true);
        dialog.setLayout(new BorderLayout(10,10));
        dialog.setSize(700, 500);
        dialog.setLocationRelativeTo(this);

        JTabbedPane tabs = new JTabbedPane();

        // Panel Recibidas
        JPanel pRec = new JPanel(new BorderLayout());
        pRec.setBorder(new EmptyBorder(10,10,10,10));
        JPanel listaRec = new JPanel();
        listaRec.setLayout(new BoxLayout(listaRec, BoxLayout.Y_AXIS));
        JScrollPane spRec = new JScrollPane(listaRec);
        pRec.add(spRec, BorderLayout.CENTER);

        // Panel Enviadas
        JPanel pEnv = new JPanel(new BorderLayout());
        pEnv.setBorder(new EmptyBorder(10,10,10,10));
        JPanel listaEnv = new JPanel();
        listaEnv.setLayout(new BoxLayout(listaEnv, BoxLayout.Y_AXIS));
        JScrollPane spEnv = new JScrollPane(listaEnv);
        pEnv.add(spEnv, BorderLayout.CENTER);

        tabs.addTab("Recibidas", pRec);
        tabs.addTab("Enviadas", pEnv);
        dialog.add(tabs, BorderLayout.CENTER);

        // Pie con botones
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnActualizar = new JButton("Actualizar");
        JButton btnCerrar = new JButton("Cerrar");
        pie.add(btnActualizar); pie.add(btnCerrar);
        dialog.add(pie, BorderLayout.SOUTH);

        btnActualizar.addActionListener(e -> cargarSolicitudes(listaRec, listaEnv, dialog));
        btnCerrar.addActionListener(e -> dialog.dispose());

        cargarSolicitudes(listaRec, listaEnv, dialog);
        dialog.setVisible(true);
    }

    private void cargarSolicitudes(JPanel listaRec, JPanel listaEnv, JDialog dialog) {
        listaRec.removeAll();
        java.util.List<com.arquitectura.servicios.ServicioComandosChat.InvRecibida> rec = canalController.listarInvitacionesRecibidas();
        for (var inv : rec) {
            JPanel fila = new JPanel(new BorderLayout());
            fila.setBorder(new EmptyBorder(5,5,5,5));
            String priv = Boolean.TRUE.equals(inv.canalPrivado) ? "[Privado] " : "[Publico] ";
            JLabel lbl = new JLabel(priv + inv.canalNombre + "  — Invitado por: " + (inv.invitadorNombre != null ? inv.invitadorNombre : (inv.invitadorId != null ? ("#"+inv.invitadorId) : "?")));
            fila.add(lbl, BorderLayout.CENTER);
            JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton btnAceptar = new JButton("Aceptar");
            JButton btnRechazar = new JButton("Rechazar");
            acciones.add(btnAceptar); acciones.add(btnRechazar);
            fila.add(acciones, BorderLayout.EAST);
            btnAceptar.addActionListener(e -> {
                boolean ok = canalController.aceptarSolicitud(inv.canalId);
                JOptionPane.showMessageDialog(dialog,
                        ok ? "Canal aceptado" : "No se pudo aceptar",
                        ok ? "Exito" : "Error",
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                if (ok) cargarSolicitudes(listaRec, listaEnv, dialog);
            });
            btnRechazar.addActionListener(e -> {
                boolean ok = canalController.rechazarSolicitud(inv.canalId);
                JOptionPane.showMessageDialog(dialog,
                        ok ? "Invitacion rechazada" : "No se pudo rechazar",
                        ok ? "Exito" : "Error",
                        ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                if (ok) cargarSolicitudes(listaRec, listaEnv, dialog);
            });
            listaRec.add(fila);
        }
        listaRec.revalidate(); listaRec.repaint();

        listaEnv.removeAll();
        java.util.List<com.arquitectura.servicios.ServicioComandosChat.InvEnviada> env = canalController.listarInvitacionesEnviadas();
        for (var inv : env) {
            JPanel fila = new JPanel(new BorderLayout());
            fila.setBorder(new EmptyBorder(5,5,5,5));
            String priv = Boolean.TRUE.equals(inv.canalPrivado) ? "[Privado] " : "[Publico] ";
            JLabel lbl = new JLabel(priv + inv.canalNombre + "  — Invitado: " + (inv.invitadoNombre != null ? inv.invitadoNombre : (inv.invitadoId != null ? ("#"+inv.invitadoId) : "?")) + "  — Estado: " + (inv.estado != null ? inv.estado : "?"));
            fila.add(lbl, BorderLayout.CENTER);
            listaEnv.add(fila);
        }
        listaEnv.revalidate(); listaEnv.repaint();
    }

    private void cerrarSesion() {
        int opcion = JOptionPane.showConfirmDialog(this, "¿Está seguro que desea salir?", "Cerrar Sesión", JOptionPane.YES_NO_OPTION);
        if (opcion == JOptionPane.YES_OPTION) {
            try {
                if (!clienteTCP.estaConectado()) clienteTCP.conectar();
                com.arquitectura.servicios.ServicioComandosChat comandos = new com.arquitectura.servicios.ServicioComandosChat(clienteTCP);
                // Enviar LOGOUT (sin cerrar el socket del cliente)
                comandos.logout();
            } catch (Exception ignored) {}
            dispose();
            SwingUtilities.invokeLater(() -> new VistaLogin().setVisible(true));
        }
    }

    private void configurarVentana() {
        setTitle("Chat Universitario - " + usuarioActual.getNombreDeUsuario());
        setSize(1200, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                try { if (oyenteEventos != null) clienteTCP.removerOyente(oyenteEventos); } catch (Exception ignored) {}
                try { if (oyenteMensajes != null) ServicioEventosMensajes.instancia().remover(oyenteMensajes); } catch (Exception ignored) {}
            }
        });
    }

    private void mostrarMensajeEnChat(String linea) {
        if (linea == null) return;
        String timestamp = java.time.LocalTime.now().toString();
        if (timestamp.length() > 5) timestamp = timestamp.substring(0, 5);
        areaMensajes.append("[" + timestamp + "] " + linea + "\n");
        areaMensajes.setCaretPosition(areaMensajes.getDocument().getLength());
    }

    static class UsuarioListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof ClienteLocal usuario) {
                String estado = Boolean.TRUE.equals(usuario.getEstado()) ? "●" : "○";
                setText(estado + " " + usuario.getNombreDeUsuario());
            }
            return this;
        }
    }

    static class CanalListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof CanalLocal canal) {
                setText(canal.getNombre());
            }
            return this;
        }
    }
}
