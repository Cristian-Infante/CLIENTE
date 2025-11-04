package com.arquitectura.vistas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import com.arquitectura.controladores.ControladorLogin;
import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.infra.net.OyenteMensajesChat;
import com.arquitectura.servicios.ObservadorEventosChat;
import com.arquitectura.servicios.ServicioConexionChat;

public class VistaLogin extends JFrame {
    private final ControladorLogin controladorLogin;
    private OyenteMensajesChat oyenteEventos;
    private volatile boolean notificadoDesconexion = false;
    private volatile boolean sincronizacionIniciada = false;
    private volatile Long totalMensajesEsperados = null;

    // Componentes comunes
    private JTabbedPane panelPestanas;
    private JLabel etiquetaEstado;

    // Login
    private JTextField campoUsuarioLogin;
    private JPasswordField campoContrasenaLogin;
    private JButton botonIniciarSesion;

    // Registro
    private JTextField campoUsuarioRegistro;
    private JTextField campoEmailRegistro;
    private JPasswordField campoContrasenaRegistro;
    private JPasswordField campoConfirmarContrasena;
    private JButton botonSeleccionarFoto;
    private JLabel etiquetaFotoPreview;
    private JButton botonRegistrar;
    private byte[] bytesFoto;

    public VistaLogin() {
        this(null);
    }

    public VistaLogin(ServicioConexionChat conexionCompartida) {
        this.controladorLogin = (conexionCompartida != null)
                ? new ControladorLogin(conexionCompartida)
                : new ControladorLogin();
        inicializarComponentes();
        configurarVentana();
        // Intentar conectar al servidor al abrir la aplicacion (en segundo plano)
        conectarAlIniciar();
        // Inicializar base de datos local H2 y loguear URL efectiva
        inicializarBaseLocal();
        registrarOyenteEventos();
    }

    private void inicializarComponentes() {
        panelPestanas = new JTabbedPane();
        panelPestanas.addTab("Iniciar Sesion", crearPanelInicioSesion());
        panelPestanas.addTab("Registrarse", crearPanelRegistro());

        etiquetaEstado = new JLabel(" ", SwingConstants.CENTER);
        etiquetaEstado.setForeground(Color.RED);

        setLayout(new BorderLayout(10, 10));
        add(panelPestanas, BorderLayout.CENTER);
        add(etiquetaEstado, BorderLayout.SOUTH);
    }

    private void inicializarBaseLocal() {
        // Defer DB initialization until after login to avoid opening a shared file DB
        // before the application has determined the user-specific DB file.
        System.out.println("[VistaLogin] DB initialization deferred until user login.");
    }

    private JPanel crearPanelInicioSesion() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int fila = 0;

        JLabel etiquetaTitulo = new JLabel("Chat Universitario", SwingConstants.CENTER);
        etiquetaTitulo.setFont(new Font("Arial", Font.BOLD, 24));
        gbc.gridx = 0; gbc.gridy = fila++; gbc.gridwidth = 2;
        panel.add(etiquetaTitulo, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = fila; panel.add(new JLabel("Usuario o email:"), gbc);
        gbc.gridx = 1; campoUsuarioLogin = new JTextField(20); panel.add(campoUsuarioLogin, gbc); fila++;

        gbc.gridx = 0; gbc.gridy = fila; panel.add(new JLabel("Contrasena:"), gbc);
        gbc.gridx = 1; campoContrasenaLogin = new JPasswordField(20); panel.add(campoContrasenaLogin, gbc); fila++;

        JPanel panelBotones = new JPanel(new FlowLayout());
        botonIniciarSesion = new JButton("Iniciar Sesion");
        botonIniciarSesion.setPreferredSize(new Dimension(130, 30));
        botonIniciarSesion.addActionListener(e -> ejecutarInicioSesion());

        JButton btnPing = new JButton("Probar conexion");
        btnPing.setPreferredSize(new Dimension(150, 30));
        btnPing.addActionListener(e -> probarConexion());

        JButton botonSalir = new JButton("Salir");
        botonSalir.setPreferredSize(new Dimension(100, 30));
        botonSalir.addActionListener(e -> System.exit(0));

        panelBotones.add(botonIniciarSesion);
        panelBotones.add(btnPing);
        panelBotones.add(botonSalir);

        gbc.gridx = 0; gbc.gridy = fila++; gbc.gridwidth = 2; panel.add(panelBotones, gbc);

        campoContrasenaLogin.addActionListener(e -> ejecutarInicioSesion());
        return panel;
    }

    private JPanel crearPanelRegistro() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int fila = 0;

        JLabel etiquetaTitulo = new JLabel("Crear Cuenta Nueva", SwingConstants.CENTER);
        etiquetaTitulo.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0; gbc.gridy = fila++; gbc.gridwidth = 2; panel.add(etiquetaTitulo, gbc); gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = fila; panel.add(new JLabel("Usuario:"), gbc);
        gbc.gridx = 1; campoUsuarioRegistro = new JTextField(20); panel.add(campoUsuarioRegistro, gbc); fila++;

        gbc.gridx = 0; gbc.gridy = fila; panel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1; campoEmailRegistro = new JTextField(20); panel.add(campoEmailRegistro, gbc); fila++;

        gbc.gridx = 0; gbc.gridy = fila; panel.add(new JLabel("Contrasena:"), gbc);
        gbc.gridx = 1; campoContrasenaRegistro = new JPasswordField(20); panel.add(campoContrasenaRegistro, gbc); fila++;

        gbc.gridx = 0; gbc.gridy = fila; panel.add(new JLabel("Confirmar:"), gbc);
        gbc.gridx = 1; campoConfirmarContrasena = new JPasswordField(20); panel.add(campoConfirmarContrasena, gbc); fila++;

        gbc.gridx = 0; gbc.gridy = fila; panel.add(new JLabel("Foto (opcional):"), gbc);
        gbc.gridx = 1;
        JPanel panelFoto = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botonSeleccionarFoto = new JButton("Seleccionar");
        botonSeleccionarFoto.addActionListener(e -> seleccionarFoto());
        panelFoto.add(botonSeleccionarFoto);
        etiquetaFotoPreview = new JLabel("Sin foto");
        etiquetaFotoPreview.setPreferredSize(new Dimension(50, 50));
        etiquetaFotoPreview.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        panelFoto.add(etiquetaFotoPreview);
        panel.add(panelFoto, gbc); fila++;

        gbc.gridx = 0; gbc.gridy = fila++; gbc.gridwidth = 2;
        botonRegistrar = new JButton("Registrarse");
        botonRegistrar.setPreferredSize(new Dimension(150, 35));
        botonRegistrar.addActionListener(e -> ejecutarRegistro());
        JPanel pie = new JPanel(); pie.add(botonRegistrar); panel.add(pie, gbc);

        return panel;
    }

    private void ejecutarInicioSesion() {
        String usuario = campoUsuarioLogin.getText().trim();
        String contrasena = new String(campoContrasenaLogin.getPassword());
        if (usuario.isEmpty() || contrasena.isEmpty()) { mostrarError("Complete todos los campos"); return; }

        botonIniciarSesion.setEnabled(false);
        etiquetaEstado.setText("Conectando..."); etiquetaEstado.setForeground(Color.BLUE);

        new Thread(() -> {
            boolean ok = controladorLogin.iniciarSesion(usuario, contrasena);
            String msg = controladorLogin.getUltimoMensajeServidor();
            SwingUtilities.invokeLater(() -> {
                botonIniciarSesion.setEnabled(true);
                if (ok) { mostrarExito((msg != null && !msg.isEmpty()) ? msg : "Login exitoso"); abrirVentanaPrincipal(); }
                else { mostrarError((msg != null && !msg.isEmpty()) ? msg : "Usuario o contrasena incorrectos"); }
            });
        }).start();
    }

    private void ejecutarRegistro() {
        String usuario = campoUsuarioRegistro.getText().trim();
        String email = campoEmailRegistro.getText().trim();
        String contrasena = new String(campoContrasenaRegistro.getPassword());
        String confirmar = new String(campoConfirmarContrasena.getPassword());
        if (usuario.isEmpty() || email.isEmpty() || contrasena.isEmpty()) { mostrarError("Campos obligatorios"); return; }
        if (!contrasena.equals(confirmar)) { mostrarError("Las contrasenas no coinciden"); return; }
        if (contrasena.length() < 6) { mostrarError("Contrasena minima 6 caracteres"); return; }
        if (!email.contains("@") || !email.contains(".")) { mostrarError("Email invalido"); return; }

        botonRegistrar.setEnabled(false); etiquetaEstado.setText("Registrando..."); etiquetaEstado.setForeground(Color.BLUE);
        new Thread(() -> {
            boolean ok = controladorLogin.registrar(usuario, email, contrasena, bytesFoto);
            String msg = controladorLogin.getUltimoMensajeServidor();
            SwingUtilities.invokeLater(() -> {
                botonRegistrar.setEnabled(true);
                if (ok) {
                    mostrarExito((msg != null && !msg.isEmpty()) ? msg : "Registro exitoso. Puede iniciar sesion");
                    limpiarFormularioRegistro();
                    panelPestanas.setSelectedIndex(0);
                } else {
                    mostrarError((msg != null && !msg.isEmpty()) ? msg : "Error en registro");
                }
            });
        }).start();
    }

    private void seleccionarFoto() {
        JFileChooser selector = new JFileChooser();
        int res = selector.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File archivo = selector.getSelectedFile();
            try {
                bytesFoto = Files.readAllBytes(archivo.toPath());
                ImageIcon icono = new ImageIcon(bytesFoto);
                Image img = icono.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
                etiquetaFotoPreview.setIcon(new ImageIcon(img));
                etiquetaFotoPreview.setText("");
                mostrarExito("Foto: " + archivo.getName());
            } catch (Exception e) { mostrarError("Error al cargar foto"); }
        }
    }

    private void abrirVentanaPrincipal() {
        ClienteLocal usuarioActual = controladorLogin.getClienteSesion();
        final boolean haySync = sincronizacionIniciada;
        final Long totalEsperado = totalMensajesEsperados;
        dispose();
        SwingUtilities.invokeLater(() -> {
            ServicioConexionChat conexion = controladorLogin.getServicioConexion();
            VistaChatPrincipal main = new VistaChatPrincipal(usuarioActual, conexion);
            // Si hubo sincronización durante el login, mostrar el modal
            if (haySync) {
                main.mostrarModalSincronizacionInicial(totalEsperado);
            }
            main.setVisible(true);
        });
    }

    private void limpiarFormularioRegistro() {
        campoUsuarioRegistro.setText(""); campoEmailRegistro.setText("");
        campoContrasenaRegistro.setText(""); campoConfirmarContrasena.setText("");
        bytesFoto = null; etiquetaFotoPreview.setIcon(null); etiquetaFotoPreview.setText("Sin foto");
    }

    private void mostrarError(String msg) { etiquetaEstado.setText(msg); etiquetaEstado.setForeground(Color.RED); }
    private void mostrarExito(String msg) { etiquetaEstado.setText(msg); etiquetaEstado.setForeground(new Color(0,150,0)); }

    private void configurarVentana() {
        setTitle("Chat Universitario - Inicio de Sesion");
        setSize(450, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
    }

    private void conectarAlIniciar() {
        etiquetaEstado.setText("Conectando con el servidor...");
        etiquetaEstado.setForeground(Color.BLUE);
        new Thread(() -> {
            boolean ok;
            try {
                ServicioConexionChat sc = controladorLogin.getServicioConexion();
                if (!sc.estaConectado()) sc.conectar();
                // Asegurar registro del observador singleton (por si llega mensajes antes/depués de login)
                try { ObservadorEventosChat.instancia().registrarEn(sc); } catch (Exception ignored2) {}
                ok = sc.estaConectado();
            } catch (Exception ex) {
                ok = false;
            }
            boolean finalOk = ok;
            SwingUtilities.invokeLater(() -> {
                if (finalOk) { mostrarExito("Conectado al servidor"); }
                else { mostrarError("No se pudo conectar al servidor"); }
            });
        }).start();
    }

    private void registrarOyenteEventos() {
        oyenteEventos = new OyenteMensajesChat() {
            @Override public void alRecibirMensaje(String mensaje) {
                if (mensaje == null || notificadoDesconexion) return;
                String compact = mensaje.replaceAll("\\s+", "");
                
                // Detectar MESSAGE_SYNC para sincronización
                if (compact.contains("\"command\":\"MESSAGE_SYNC\"")) {
                    sincronizacionIniciada = true;
                    try {
                        String totalStr = extraerCampo(mensaje, "totalMensajes");
                        if (totalStr != null && !totalStr.isEmpty()) {
                            totalMensajesEsperados = Long.parseLong(totalStr);
                        }
                    } catch (NumberFormatException ignored) {}
                    return;
                }
                
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
        try { controladorLogin.getServicioConexion().registrarOyente(oyenteEventos); } catch (Exception ignored) {}
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
            try { JOptionPane.showMessageDialog(this, mensaje, titulo, JOptionPane.WARNING_MESSAGE); } catch (Exception ignored) {}
            try { dispose(); } catch (Exception ignored) {}
            System.exit(0);
        });
    }

    private void probarConexion() {
        etiquetaEstado.setText("Probando conexion..."); etiquetaEstado.setForeground(Color.BLUE);
        new Thread(() -> {
            boolean ok;
            try {
                ServicioConexionChat sc = controladorLogin.getServicioConexion();
                if (!sc.estaConectado()) sc.conectar();
                com.arquitectura.servicios.ServicioComandosChat comandos = new com.arquitectura.servicios.ServicioComandosChat(sc);
                ok = comandos.ping();
            } catch (Exception ex) {
                ok = false;
            }
            boolean res = ok;
            SwingUtilities.invokeLater(() -> {
                if (res) { mostrarExito("Conectado (PONG)"); }
                else { mostrarError("No se pudo conectar"); }
            });
        }).start();
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new VistaLogin().setVisible(true));
    }
}
