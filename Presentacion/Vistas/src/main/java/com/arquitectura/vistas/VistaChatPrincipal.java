package com.arquitectura.vistas;

import com.arquitectura.controladores.ControladorAudio;
import com.arquitectura.controladores.ControladorCanal;
import com.arquitectura.controladores.ControladorChat;
import com.arquitectura.entidades.AudioMensajeLocal;
import com.arquitectura.entidades.CanalLocal;
import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.entidades.MensajeLocal;
import com.arquitectura.entidades.TextoMensajeLocal;
import com.arquitectura.servicios.ServicioConexionChat;
import com.arquitectura.servicios.ObservadorEventosChat;
import com.arquitectura.infra.net.OyenteMensajesChat;
import com.arquitectura.servicios.OyenteActualizacionMensajes;
import com.arquitectura.servicios.ServicioEventosMensajes;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VistaChatPrincipal extends JFrame {
    private final ClienteLocal usuarioActual;
    private final ServicioConexionChat clienteTCP;
    private volatile boolean notificadoDesconexion = false;
    private OyenteMensajesChat oyenteEventos;
    private final Map<Long, ClienteLocal> usuariosPorId = new ConcurrentHashMap<>();
    private OyenteActualizacionMensajes oyenteEventosGlobal;
    private JDialog dialogoSincronizacion;
    private JLabel lblSincronizacion;
    private JProgressBar barraSincronizacion;

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
    private JLabel lblFotoPerfil;
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
    private JPanel contenedorMensajes;
    private JScrollPane scrollMensajes;
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
        registrarOyenteActualizacionesGlobales();
        SwingUtilities.invokeLater(this::refrescarSegunTabSeleccionada);
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

    private void registrarOyenteActualizacionesGlobales() {
        if (oyenteEventosGlobal != null) {
            try { ServicioEventosMensajes.instancia().remover(oyenteEventosGlobal); } catch (Exception ignored) {}
        }
        oyenteEventosGlobal = new OyenteActualizacionMensajes() {
            @Override
            public void onEstadoUsuarioActualizado(ClienteLocal usuario, Integer sesionesActivas, String timestampIso) {
                if (usuario == null || usuario.getId() == null) {
                    return;
                }
                ClienteLocal copia = new ClienteLocal();
                copia.setId(usuario.getId());
                copia.setNombreDeUsuario(usuario.getNombreDeUsuario());
                copia.setEmail(usuario.getEmail());
                copia.setEstado(usuario.getEstado());
                copia.setSesionesActivas(usuario.getSesionesActivas());
                SwingUtilities.invokeLater(() -> aplicarActualizacionEstadoUsuario(copia,
                        sesionesActivas != null ? sesionesActivas : usuario.getSesionesActivas()));
            }

            @Override
            public void onSincronizacionMensajesIniciada(Long totalEsperado) {
                SwingUtilities.invokeLater(() -> mostrarDialogoSincronizacion(totalEsperado));
            }

            @Override
            public void onSincronizacionMensajesFinalizada(int insertados, Long totalEsperado, boolean exito, String mensajeError) {
                SwingUtilities.invokeLater(() -> finalizarDialogoSincronizacion(insertados, totalEsperado, exito, mensajeError));
            }
        };
        ServicioEventosMensajes.instancia().registrar(oyenteEventosGlobal);
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

        lblFotoPerfil = new JLabel();
        lblFotoPerfil.setPreferredSize(new Dimension(48, 48));
        lblFotoPerfil.setHorizontalAlignment(SwingConstants.CENTER);
        lblFotoPerfil.setVerticalAlignment(SwingConstants.CENTER);
        lblFotoPerfil.setOpaque(false);
        lblFotoPerfil.setBorder(new LineBorder(new Color(255, 255, 255, 160), 1, true));
        actualizarFotoPerfil();

        JPanel panelUsuario = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panelUsuario.setOpaque(false);
        panelUsuario.add(lblFotoPerfil);
        panelUsuario.add(lblUsuario);
        panel.add(panelUsuario, BorderLayout.WEST);

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

    private void actualizarFotoPerfil() {
        if (lblFotoPerfil == null) {
            return;
        }
        ImageIcon icono = crearIconoPerfil(usuarioActual != null ? usuarioActual.getFoto() : null);
        if (icono != null) {
            lblFotoPerfil.setIcon(icono);
            lblFotoPerfil.setText("");
        } else {
            lblFotoPerfil.setIcon(null);
            lblFotoPerfil.setText("👤");
            lblFotoPerfil.setForeground(Color.WHITE);
        }
    }

    private ImageIcon crearIconoPerfil(byte[] datos) {
        if (datos == null || datos.length == 0) {
            return null;
        }
        try {
            ImageIcon icono = new ImageIcon(datos);
            Image img = icono.getImage();
            if (img == null) {
                return null;
            }
            Image escalada = img.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
            return new ImageIcon(escalada);
        } catch (Exception e) {
            return null;
        }
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
            cargarUsuariosDisponibles();
        } else if (idx == 1) {
            cargarListaMisCanales();
        }
    }

    private void cargarUsuariosDisponibles() {
        try {
            if (!clienteTCP.estaConectado()) clienteTCP.conectar();
            com.arquitectura.servicios.ServicioComandosChat comandos = new com.arquitectura.servicios.ServicioComandosChat(clienteTCP);
            java.util.List<ClienteLocal> usuarios = comandos.listarUsuariosYEsperar(6000);
            usuariosPorId.clear();
            if (usuarios != null) {
                for (ClienteLocal u : usuarios) {
                    if (u == null || u.getId() == null) continue;
                    usuariosPorId.put(u.getId(), u);
                }
            }
            if (usuarioActual != null && usuarioActual.getId() != null) {
                usuariosPorId.remove(usuarioActual.getId());
            }
            reconstruirListaUsuarios();
        } catch (Exception ignored) {
            // No-op
        }
    }

    private void cargarListaMisCanales() {
        java.util.List<CanalLocal> mis = canalController.obtenerMisCanales();
        modeloCanales.clear();
        if (mis != null) for (CanalLocal c : mis) if (c != null) modeloCanales.addElement(c);
    }

    private void reconstruirListaUsuarios() {
        if (modeloUsuarios == null || listaUsuarios == null) {
            return;
        }
        java.util.List<ClienteLocal> usuarios = new java.util.ArrayList<>(usuariosPorId.values());
        usuarios.sort((a, b) -> {
            boolean aCon = Boolean.TRUE.equals(a.getEstado());
            boolean bCon = Boolean.TRUE.equals(b.getEstado());
            if (aCon != bCon) {
                return aCon ? -1 : 1;
            }
            String na = a.getNombreDeUsuario() != null ? a.getNombreDeUsuario() : "";
            String nb = b.getNombreDeUsuario() != null ? b.getNombreDeUsuario() : "";
            int cmp = na.compareToIgnoreCase(nb);
            if (cmp != 0) return cmp;
            Long ida = a.getId();
            Long idb = b.getId();
            if (ida != null && idb != null) return ida.compareTo(idb);
            return 0;
        });
        Long seleccionadoId = usuarioSeleccionado != null ? usuarioSeleccionado.getId() : null;
        modeloUsuarios.clear();
        for (ClienteLocal u : usuarios) {
            if (esUsuarioActual(u)) {
                continue;
            }
            modeloUsuarios.addElement(u);
        }
        if (seleccionadoId != null) {
            for (int i = 0; i < modeloUsuarios.size(); i++) {
                ClienteLocal u = modeloUsuarios.getElementAt(i);
                if (seleccionadoId.equals(u.getId())) {
                    listaUsuarios.setSelectedIndex(i);
                    usuarioSeleccionado = u;
                    mostrarInfoUsuario();
                    break;
                }
            }
        }
        listaUsuarios.repaint();
    }

    private boolean esUsuarioActual(ClienteLocal usuario) {
        if (usuario == null) {
            return false;
        }
        Long miId = usuarioActual != null ? usuarioActual.getId() : null;
        if (miId != null && miId.equals(usuario.getId())) {
            return true;
        }
        String miNombre = usuarioActual != null ? usuarioActual.getNombreDeUsuario() : null;
        String otroNombre = usuario.getNombreDeUsuario();
        if (miNombre != null && otroNombre != null) {
            return miNombre.trim().equalsIgnoreCase(otroNombre.trim());
        }
        return false;
    }

    private void aplicarActualizacionEstadoUsuario(ClienteLocal usuarioActualizado, Integer sesionesActivas) {
        if (usuarioActualizado == null || usuarioActualizado.getId() == null) {
            return;
        }
        Long id = usuarioActualizado.getId();
        if (usuarioActual != null && id.equals(usuarioActual.getId())) {
            usuarioActual.setEstado(usuarioActualizado.getEstado());
            usuarioActual.setSesionesActivas(sesionesActivas != null ? sesionesActivas : usuarioActualizado.getSesionesActivas());
            actualizarEtiquetaEstadoConexion(usuarioActual.getEstado());
            return;
        }
        ClienteLocal existente = usuariosPorId.get(id);
        if (existente == null) {
            existente = new ClienteLocal();
            existente.setId(id);
            usuariosPorId.put(id, existente);
        }
        if (usuarioActualizado.getNombreDeUsuario() != null && !usuarioActualizado.getNombreDeUsuario().isBlank()) {
            existente.setNombreDeUsuario(usuarioActualizado.getNombreDeUsuario());
        }
        if (usuarioActualizado.getEmail() != null && !usuarioActualizado.getEmail().isBlank()) {
            existente.setEmail(usuarioActualizado.getEmail());
        }
        existente.setEstado(usuarioActualizado.getEstado());
        if (sesionesActivas != null) {
            existente.setSesionesActivas(sesionesActivas);
        } else {
            existente.setSesionesActivas(usuarioActualizado.getSesionesActivas());
        }
        reconstruirListaUsuarios();
        if (usuarioSeleccionado != null && usuarioSeleccionado.getId() != null && usuarioSeleccionado.getId().equals(id)) {
            usuarioSeleccionado = existente;
            mostrarInfoUsuario();
        }
        actualizarEstadoEnCanalSeleccionado(id, existente.getEstado(), existente.getSesionesActivas());
    }

    private void actualizarEstadoEnCanalSeleccionado(Long usuarioId, Boolean estado, Integer sesionesActivas) {
        if (canalSeleccionado == null || usuarioId == null) {
            return;
        }
        java.util.List<ClienteLocal> miembros = canalSeleccionado.getMiembros();
        if (miembros == null || miembros.isEmpty()) {
            return;
        }
        boolean actualizado = false;
        for (ClienteLocal miembro : miembros) {
            if (miembro != null && usuarioId.equals(miembro.getId())) {
                miembro.setEstado(estado);
                if (sesionesActivas != null) {
                    miembro.setSesionesActivas(sesionesActivas);
                }
                actualizado = true;
            }
        }
        if (actualizado) {
            mostrarInfoCanal();
        }
    }

    private void actualizarEtiquetaEstadoConexion(Boolean conectado) {
        if (lblEstadoConexion == null) {
            return;
        }
        if (Boolean.TRUE.equals(conectado)) {
            lblEstadoConexion.setText("Conectado");
            lblEstadoConexion.setForeground(new Color(0, 128, 0));
        } else if (Boolean.FALSE.equals(conectado)) {
            lblEstadoConexion.setText("Desconectado");
            lblEstadoConexion.setForeground(new Color(178, 34, 34));
        } else {
            lblEstadoConexion.setText("Estado desconocido");
            lblEstadoConexion.setForeground(Color.DARK_GRAY);
        }
    }

    private void mostrarDialogoSincronizacion(Long totalEsperado) {
        if (dialogoSincronizacion == null) {
            dialogoSincronizacion = new JDialog(this, "Sincronizando mensajes", Dialog.ModalityType.MODELESS);
            dialogoSincronizacion.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialogoSincronizacion.setResizable(false);
            JPanel contenido = new JPanel(new BorderLayout(10, 10));
            contenido.setBorder(new EmptyBorder(12, 16, 12, 16));
            lblSincronizacion = new JLabel("Sincronizando mensajes...");
            barraSincronizacion = new JProgressBar();
            barraSincronizacion.setIndeterminate(true);
            contenido.add(lblSincronizacion, BorderLayout.NORTH);
            contenido.add(barraSincronizacion, BorderLayout.CENTER);
            dialogoSincronizacion.getContentPane().add(contenido);
            dialogoSincronizacion.pack();
        }
        if (lblSincronizacion != null) {
            if (totalEsperado != null && totalEsperado > 0) {
                lblSincronizacion.setText("Sincronizando mensajes (" + totalEsperado + ")...");
            } else {
                lblSincronizacion.setText("Sincronizando mensajes...");
            }
        }
        if (barraSincronizacion != null) {
            barraSincronizacion.setIndeterminate(true);
        }
        if (dialogoSincronizacion != null && !dialogoSincronizacion.isVisible()) {
            dialogoSincronizacion.setLocationRelativeTo(this);
            dialogoSincronizacion.setVisible(true);
        }
    }

    private void finalizarDialogoSincronizacion(int insertados, Long totalEsperado, boolean exito, String mensajeError) {
        if (dialogoSincronizacion != null) {
            dialogoSincronizacion.setVisible(false);
        }
        if (!isDisplayable()) {
            return;
        }
        if (exito) {
            StringBuilder msg = new StringBuilder("Sincronización completada.");
            if (insertados > 0) {
                msg.append(" Se incorporaron ").append(insertados).append(insertados == 1 ? " mensaje." : " mensajes.");
            } else {
                msg.append(" No hubo mensajes nuevos.");
            }
            JOptionPane.showMessageDialog(this, msg.toString(), "Sincronización completada", JOptionPane.INFORMATION_MESSAGE);
        } else {
            String detalle = (mensajeError != null && !mensajeError.isBlank())
                    ? mensajeError
                    : "Ocurrió un problema al sincronizar los mensajes.";
            JOptionPane.showMessageDialog(this, detalle, "Sincronización incompleta", JOptionPane.WARNING_MESSAGE);
        }
    }

    private JPanel crearPanelCentral() {
        panelCentral = new JPanel(new BorderLayout());

        JPanel panelEncabezado = new JPanel(new BorderLayout());
        panelEncabezado.setBorder(new EmptyBorder(5, 10, 5, 10));
        panelEncabezado.setBackground(new Color(240, 240, 240));
        lblDestinatario = new JLabel("Seleccione una conversacion");
        lblDestinatario.setFont(new Font("Arial", Font.BOLD, 14));
        panelEncabezado.add(lblDestinatario, BorderLayout.CENTER);

        contenedorMensajes = new JPanel();
        contenedorMensajes.setLayout(new BoxLayout(contenedorMensajes, BoxLayout.Y_AXIS));
        contenedorMensajes.setBackground(Color.WHITE);
        contenedorMensajes.setBorder(new EmptyBorder(10, 10, 10, 10));

        scrollMensajes = new JScrollPane(contenedorMensajes);
        scrollMensajes.getVerticalScrollBar().setUnitIncrement(16);
        scrollMensajes.getViewport().setBackground(Color.WHITE);

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
            limpiarMensajes();
            mostrarInfoUsuario();
            // Cargar historial privado local
            java.util.List<MensajeLocal> hist = chatController.obtenerConversacionDetallada(usuarioSeleccionado.getId());
            mostrarMensajes(hist);
            subscribirActualizacionesPrivado(usuarioSeleccionado.getId());
        }
    }

    private void seleccionarCanal() {
        canalSeleccionado = listaCanales.getSelectedValue();
        usuarioSeleccionado = null;
        if (canalSeleccionado != null) {
            lblDestinatario.setText("Canal: " + canalSeleccionado.getNombre());
            limpiarMensajes();
            mostrarInfoCanal();
            // Cargar historial local del canal desde H2
            java.util.List<MensajeLocal> historial = chatController.obtenerMensajesCanalDetallados(canalSeleccionado.getId());
            mostrarMensajes(historial);
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
                java.util.List<MensajeLocal> hist = chatController.obtenerMensajesCanalDetallados(id);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    limpiarMensajes();
                    mostrarMensajes(hist);
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
                // Debug log to trace private notification delivery
                try { System.out.println("[VistaChatPrincipal] onPrivadoActualizado called id=" + id + " usuarioSeleccionado=" + (usuarioSeleccionado==null?"null":usuarioSeleccionado.getId())); } catch (Exception ignored) {}
                if (id == null || usuarioSeleccionado == null) return;
                if (!id.equals(usuarioSeleccionado.getId())) return;
                java.util.List<MensajeLocal> hist = chatController.obtenerConversacionDetallada(id);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    limpiarMensajes();
                    mostrarMensajes(hist);
                });
            }
        };
        // prefer the simple registrar; logging already done in ServicioEventosMensajes.notificar* calls
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
            txtMensaje.setText("");
            refrescarMensajesActuales();
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
                    refrescarMensajesActuales();
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
        Integer sesiones = usuarioSeleccionado.getSesionesActivas();
        if (sesiones != null) {
            info.append("Sesiones activas: ").append(sesiones).append("\n");
        }
        areaInfo.setText(info.toString());
    }

    private void mostrarInfoCanal() {
        if (canalSeleccionado == null) return;
        StringBuilder info = new StringBuilder();
        info.append("INFORMACIÓN DEL CANAL\n\n");
        info.append("Nombre: ").append(canalSeleccionado.getNombre()).append("\n");
        info.append("Privado: ").append(Boolean.TRUE.equals(canalSeleccionado.getPrivado()) ? "Sí" : "No").append("\n");
        java.util.List<com.arquitectura.entidades.ClienteLocal> miembros = canalSeleccionado.getMiembros();
        if ((miembros == null || miembros.isEmpty()) && canalSeleccionado.getId() != null) {
            try {
                java.util.List<com.arquitectura.entidades.CanalLocal> actualizados = canalController.obtenerMisCanales();
                if (actualizados != null) {
                    for (com.arquitectura.entidades.CanalLocal canal : actualizados) {
                        if (canal != null && canalSeleccionado.getId().equals(canal.getId())) {
                            miembros = canal.getMiembros();
                            canalSeleccionado.setMiembros(miembros);
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (miembros != null && !miembros.isEmpty()) {
            info.append("Miembros (" + miembros.size() + "):\n");
            for (com.arquitectura.entidades.ClienteLocal miembro : miembros) {
                if (miembro == null) continue;
                String nombre = miembro.getNombreDeUsuario();
                if (nombre == null || nombre.isBlank()) {
                    nombre = miembro.getEmail();
                }
                if (nombre == null || nombre.isBlank()) {
                    nombre = miembro.getId() != null ? ("#" + miembro.getId()) : "Desconocido";
                }
                info.append(" - ").append(nombre);
                if (miembro.getEmail() != null && (miembro.getNombreDeUsuario() == null || !miembro.getNombreDeUsuario().equalsIgnoreCase(miembro.getEmail()))) {
                    info.append(" <").append(miembro.getEmail()).append('>');
                }
                if (miembro.getEstado() != null) {
                    info.append(" - ").append(Boolean.TRUE.equals(miembro.getEstado()) ? "Conectado" : "Desconectado");
                }
                if (miembro.getSesionesActivas() != null) {
                    info.append(" (sesiones: ").append(miembro.getSesionesActivas()).append(')');
                }
                info.append('\n');
            }
        } else {
            info.append("Miembros: No disponibles\n");
        }
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
                try {
                    if (oyenteEventos != null) clienteTCP.removerOyente(oyenteEventos);
                } catch (Exception ignored) {}
                try {
                    if (oyenteMensajes != null) ServicioEventosMensajes.instancia().remover(oyenteMensajes);
                } catch (Exception ignored) {}
                try {
                    if (oyenteEventosGlobal != null) ServicioEventosMensajes.instancia().remover(oyenteEventosGlobal);
                } catch (Exception ignored) {}
                oyenteEventos = null;
                oyenteMensajes = null;
                oyenteEventosGlobal = null;
                if (!clienteTCP.estaConectado()) {
                    try { clienteTCP.conectar(); } catch (Exception ignored) {}
                }
                if (clienteTCP.estaConectado()) {
                    com.arquitectura.servicios.ServicioComandosChat comandos = new com.arquitectura.servicios.ServicioComandosChat(clienteTCP);
                    comandos.logout();
                }
            } catch (Exception ignored) {}
            dispose();
            SwingUtilities.invokeLater(() -> new VistaLogin(clienteTCP).setVisible(true));
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
                try { if (oyenteEventosGlobal != null) ServicioEventosMensajes.instancia().remover(oyenteEventosGlobal); } catch (Exception ignored) {}
            }
        });
    }

    private void limpiarMensajes() {
        if (contenedorMensajes == null) return;
        contenedorMensajes.removeAll();
        contenedorMensajes.revalidate();
        contenedorMensajes.repaint();
    }

    private void mostrarMensajes(java.util.List<? extends MensajeLocal> mensajes) {
        if (contenedorMensajes == null) return;
        contenedorMensajes.removeAll();
        if (mensajes != null) {
            for (MensajeLocal mensaje : mensajes) {
                agregarMensajeVisual(mensaje);
            }
        }
        contenedorMensajes.revalidate();
        contenedorMensajes.repaint();
        desplazarAlFinal();
    }

    private void agregarMensajeVisual(MensajeLocal mensaje) {
        if (mensaje == null || contenedorMensajes == null) return;
        JPanel fila = construirPanelMensaje(mensaje);
        contenedorMensajes.add(fila);
        contenedorMensajes.add(Box.createVerticalStrut(8));
    }

    private JPanel construirPanelMensaje(MensajeLocal mensaje) {
        String hora = mensaje.getTimeStamp() != null ? mensaje.getTimeStamp().toLocalTime().toString() : java.time.LocalTime.now().toString();
        if (hora.length() > 5) hora = hora.substring(0, 5);
        String nombre = obtenerNombreEmisor(mensaje);

        JPanel fila = new JPanel(new BorderLayout());
        fila.setOpaque(false);
        fila.setBorder(new EmptyBorder(0, 5, 0, 5));

        JPanel burbuja = new JPanel();
        burbuja.setLayout(new BoxLayout(burbuja, BoxLayout.Y_AXIS));
        burbuja.setOpaque(true);
        burbuja.setBackground(new Color(245, 245, 245));
        burbuja.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                new LineBorder(new Color(210, 210, 210), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        burbuja.setAlignmentX(Component.LEFT_ALIGNMENT);
        burbuja.setMaximumSize(new Dimension(600, Integer.MAX_VALUE));

        JLabel lblEncabezado = new JLabel("[" + hora + "] " + nombre);
        lblEncabezado.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblEncabezado.setFont(lblEncabezado.getFont().deriveFont(Font.BOLD, 12f));
        burbuja.add(lblEncabezado);

        if (mensaje instanceof TextoMensajeLocal texto) {
            burbuja.add(Box.createVerticalStrut(4));
            burbuja.add(crearTextoMultilinea(texto.getContenido()));
        } else if (mensaje instanceof AudioMensajeLocal audio) {
            burbuja.add(Box.createVerticalStrut(6));
            MensajeAudioPanel panelAudio = new MensajeAudioPanel(audio);
            panelAudio.setAlignmentX(Component.LEFT_ALIGNMENT);
            burbuja.add(panelAudio);
            if (audio.getTranscripcion() != null && !audio.getTranscripcion().isBlank()) {
                burbuja.add(Box.createVerticalStrut(4));
                JTextArea trans = crearTextoMultilinea("Transcripción: " + audio.getTranscripcion());
                trans.setFont(trans.getFont().deriveFont(Font.ITALIC, trans.getFont().getSize2D()));
                burbuja.add(trans);
            }
        } else {
            burbuja.add(Box.createVerticalStrut(4));
            burbuja.add(crearTextoMultilinea("Tipo " + (mensaje.getTipo() != null ? mensaje.getTipo() : "desconocido")));
        }

        fila.add(burbuja, BorderLayout.WEST);

        return fila;
    }

    private String obtenerNombreEmisor(MensajeLocal mensaje) {
        if (mensaje == null) return "?";
        String nombre = mensaje.getEmisorNombre();
        if (nombre != null && !nombre.isBlank()) return nombre;
        Long emisor = mensaje.getEmisor();
        return emisor != null ? ("#" + emisor) : "?";
    }

    private JTextArea crearTextoMultilinea(String texto) {
        JTextArea area = new JTextArea(texto != null ? texto : "");
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setFont(area.getFont().deriveFont(13f));
        return area;
    }

    private void refrescarMensajesActuales() {
        if (usuarioSeleccionado != null) {
            java.util.List<MensajeLocal> hist = chatController.obtenerConversacionDetallada(usuarioSeleccionado.getId());
            limpiarMensajes();
            mostrarMensajes(hist);
        } else if (canalSeleccionado != null) {
            java.util.List<MensajeLocal> historial = chatController.obtenerMensajesCanalDetallados(canalSeleccionado.getId());
            limpiarMensajes();
            mostrarMensajes(historial);
        }
    }

    private void desplazarAlFinal() {
        if (scrollMensajes == null) return;
        SwingUtilities.invokeLater(() -> {
            JScrollBar barra = scrollMensajes.getVerticalScrollBar();
            if (barra != null) {
                barra.setValue(barra.getMaximum());
            }
        });
    }

    private static String formatearDuracion(Integer duracionSeg) {
        if (duracionSeg == null || duracionSeg <= 0) return "";
        int minutos = duracionSeg / 60;
        int segundos = duracionSeg % 60;
        return String.format("%02d:%02d", minutos, segundos);
    }

    private class MensajeAudioPanel extends JPanel {
        private Clip clip;
        private final JButton btnReproducir;
        private final byte[] audioBytes;
        private boolean deteniendo = false;

        MensajeAudioPanel(AudioMensajeLocal audio) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
            setOpaque(false);
            this.audioBytes = decodificarAudio(audio != null ? audio.getAudioBase64() : null);
            btnReproducir = new JButton("▶ Reproducir");
            btnReproducir.addActionListener(e -> alternarReproduccion());
            add(btnReproducir);

            String duracion = formatearDuracion(audio != null ? audio.getDuracionSeg() : null);
            JLabel lblDuracion = new JLabel(duracion.isEmpty() ? "" : ("Duración: " + duracion));
            lblDuracion.setForeground(Color.DARK_GRAY);
            add(lblDuracion);

            if (audio != null && audio.getRutaArchivo() != null && !audio.getRutaArchivo().isBlank()) {
                JLabel lblRuta = new JLabel(audio.getRutaArchivo());
                lblRuta.setForeground(Color.GRAY);
                add(lblRuta);
            }

            if (audioBytes == null || audioBytes.length == 0) {
                btnReproducir.setEnabled(false);
                btnReproducir.setText("Audio no disponible");
            }
        }

        private void alternarReproduccion() {
            if (clip != null) {
                detener();
            } else {
                reproducir();
            }
        }

        private void reproducir() {
            if (audioBytes == null || audioBytes.length == 0) {
                return;
            }
            detener();
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(audioBytes)))) {
                clip = AudioSystem.getClip();
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
                        if (!deteniendo) {
                            SwingUtilities.invokeLater(this::detener);
                        }
                    }
                });
                clip.open(ais);
                clip.start();
                btnReproducir.setText("■ Detener");
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
                detener();
                JOptionPane.showMessageDialog(VistaChatPrincipal.this,
                        "No se pudo reproducir el audio: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void detener() {
            if (clip != null) {
                deteniendo = true;
                try {
                    clip.stop();
                } catch (Exception ignored) {}
                try {
                    clip.close();
                } catch (Exception ignored) {}
                clip = null;
                deteniendo = false;
            }
            if (audioBytes != null && audioBytes.length > 0) {
                btnReproducir.setText("▶ Reproducir");
            } else {
                btnReproducir.setText("Audio no disponible");
            }
        }

        private byte[] decodificarAudio(String base64) {
            if (base64 == null || base64.isBlank()) {
                return new byte[0];
            }
            try {
                return Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                return new byte[0];
            }
        }
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
