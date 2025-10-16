package com.arquitectura.vistas;

import com.arquitectura.controladores.ControladorCanal;
import com.arquitectura.entidades.CanalLocal;
import com.arquitectura.entidades.ClienteLocal;
import com.arquitectura.servicios.ServicioConexionChat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class VistaCanal extends JFrame {
    private final ClienteLocal usuarioActual;
    private final ServicioConexionChat clienteTCP;
    private final ControladorCanal canalController;

    private JTabbedPane tabbedPane;
    private DefaultListModel<CanalLocal> modeloMisCanales;
    private JList<CanalLocal> listaMisCanales;
    private JTextField txtNombreCanal;
    private JCheckBox chkPrivado;
    // Eliminado: panel de invitaciones en creación de canal
    private DefaultListModel<CanalLocal> modeloTodosCanales;
    private JList<CanalLocal> listaTodosCanales;
    private JTextField txtBuscarCanal;

    public VistaCanal(ClienteLocal usuario, ServicioConexionChat clienteTCP) {
        this.usuarioActual = usuario;
        this.clienteTCP = clienteTCP;
        this.canalController = new ControladorCanal(usuarioActual, clienteTCP);
        inicializarComponentes();
        configurarVentana();
        cargarDatos();
    }

    private void inicializarComponentes() {
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Mis Canales", crearPanelMisCanales());
        tabbedPane.addTab("Crear Canal", crearPanelCrearCanal());
        tabbedPane.addTab("Explorar", crearPanelTodosCanales());
        add(tabbedPane);
    }

    private JPanel crearPanelMisCanales() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JLabel lblTitulo = new JLabel("Mis Canales");
        lblTitulo.setFont(new Font("Arial", Font.BOLD, 18));
        panel.add(lblTitulo, BorderLayout.NORTH);

        modeloMisCanales = new DefaultListModel<>();
        listaMisCanales = new JList<>(modeloMisCanales);
        listaMisCanales.setCellRenderer(new CanalListRenderer());
        listaMisCanales.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        panel.add(new JScrollPane(listaMisCanales), BorderLayout.CENTER);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnInvitarUsuario = new JButton("Invitar Usuario");
        btnInvitarUsuario.addActionListener(e -> invitarUsuario());
        JButton btnAbandonarCanal = new JButton("Abandonar");
        btnAbandonarCanal.addActionListener(e -> abandonarCanal());
        JButton btnEliminarCanal = new JButton("Eliminar Canal");
        btnEliminarCanal.setForeground(Color.RED);
        btnEliminarCanal.addActionListener(e -> eliminarCanal());
        panelBotones.add(btnInvitarUsuario);
        panelBotones.add(btnAbandonarCanal);
        panelBotones.add(btnEliminarCanal);
        panel.add(panelBotones, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel crearPanelCrearCanal() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        int row = 0;

        JLabel lblTitulo = new JLabel("Crear Nuevo Canal");
        lblTitulo.setFont(new Font("Arial", Font.BOLD, 18));
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        panel.add(lblTitulo, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Nombre del Canal:"), gbc);
        gbc.gridx = 1;
        txtNombreCanal = new JTextField(25);
        panel.add(txtNombreCanal, gbc);
        row++;

        // Privado toggle
        gbc.gridx = 0; gbc.gridy = row; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Privado:"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.CENTER;
        chkPrivado = new JCheckBox("Canal privado");
        chkPrivado.setSelected(true);
        panel.add(chkPrivado, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JButton btnCrear = new JButton("Crear Canal");
        btnCrear.addActionListener(e -> crearCanal());
        JPanel pie = new JPanel(); pie.add(btnCrear);
        panel.add(pie, gbc);
        return panel;
    }

    private JPanel crearPanelTodosCanales() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JPanel sup = new JPanel(new BorderLayout(5, 5));
        JLabel lblTitulo = new JLabel("Todos los Canales");
        lblTitulo.setFont(new Font("Arial", Font.BOLD, 18));
        sup.add(lblTitulo, BorderLayout.NORTH);
        JPanel buscar = new JPanel(new BorderLayout(5, 5));
        buscar.add(new JLabel("Buscar:"), BorderLayout.WEST);
        txtBuscarCanal = new JTextField();
        buscar.add(txtBuscarCanal, BorderLayout.CENTER);
        JButton btnBuscar = new JButton("Buscar");
        btnBuscar.addActionListener(e -> buscarCanales());
        buscar.add(btnBuscar, BorderLayout.EAST);
        sup.add(buscar, BorderLayout.CENTER);
        panel.add(sup, BorderLayout.NORTH);

        modeloTodosCanales = new DefaultListModel<>();
        listaTodosCanales = new JList<>(modeloTodosCanales);
        listaTodosCanales.setCellRenderer(new CanalListRenderer());
        panel.add(new JScrollPane(listaTodosCanales), BorderLayout.CENTER);
        JButton btnActualizar = new JButton("Actualizar");
        btnActualizar.addActionListener(e -> cargarTodosLosCanales());
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT)); pie.add(btnActualizar);
        panel.add(pie, BorderLayout.SOUTH);
        return panel;
    }

    private void cargarDatos() {
        cargarMisCanales();
        cargarTodosLosCanales();
    }
    private void cargarMisCanales() {
        modeloMisCanales.clear();
        java.util.List<CanalLocal> mis = canalController.obtenerMisCanales();
        if (mis != null) {
            for (CanalLocal c : mis) { if (c != null) modeloMisCanales.addElement(c); }
        }
    }
    private void cargarTodosLosCanales() { modeloTodosCanales.clear(); }
    private void crearCanal() {
        String nombre = txtNombreCanal.getText().trim();
        boolean privado = chkPrivado != null && chkPrivado.isSelected();
        if (nombre.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ingrese un nombre para el canal", "Validacion", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean ok = canalController.crearCanal(nombre, privado);
        if (ok) {
            JOptionPane.showMessageDialog(this, "Canal creado", "Exito", JOptionPane.INFORMATION_MESSAGE);
            txtNombreCanal.setText("");
            cargarMisCanales();
        } else {
            JOptionPane.showMessageDialog(this, "No se pudo crear el canal", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    // Eliminado: carga y estado de invitaciones en la vista de creación
    // Lista de usuarios ahora se consulta al servidor con LIST_USERS
    private void invitarUsuario() {
        JDialog dialog = new JDialog(this, "Invitar Usuario", true);
        dialog.setLayout(new BorderLayout(10,10));
        dialog.setSize(500, 500);
        dialog.setLocationRelativeTo(this);

        // Top: seleccionar canal
        JPanel top = new JPanel(new BorderLayout(5,5));
        top.setBorder(new EmptyBorder(10,10,0,10));
        top.add(new JLabel("Canal:"), BorderLayout.WEST);
        JComboBox<CanalLocal> comboCanales = new JComboBox<>();
        java.util.List<CanalLocal> mis = canalController.obtenerMisCanales();
        if (mis != null) { for (CanalLocal c : mis) comboCanales.addItem(c); }
        // Preseleccionar el canal de la lista si hay uno seleccionado
        CanalLocal seleccionado = listaMisCanales.getSelectedValue();
        if (seleccionado != null) {
            comboCanales.setSelectedItem(seleccionado);
        }
        comboCanales.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof CanalLocal c) {
                    setText((Boolean.TRUE.equals(c.getPrivado()) ? "[Privado] " : "[Publico] ") + c.getNombre());
                }
                return this;
            }
        });
        top.add(comboCanales, BorderLayout.CENTER);
        dialog.add(top, BorderLayout.NORTH);

        // Center: lista de usuarios
        DefaultListModel<com.arquitectura.entidades.ClienteLocal> modeloUsuarios = new DefaultListModel<>();
        JList<com.arquitectura.entidades.ClienteLocal> listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaUsuarios.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof com.arquitectura.entidades.ClienteLocal u) {
                    setText(u.getNombreDeUsuario());
                }
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(listaUsuarios);
        scroll.setBorder(new EmptyBorder(0,10,0,10));
        dialog.add(scroll, BorderLayout.CENTER);

        // Cargar usuarios registrados
        java.util.List<com.arquitectura.entidades.ClienteLocal> usuarios = canalController.listarUsuariosRegistrados();
        for (var u : usuarios) { modeloUsuarios.addElement(u); }

        // Bottom: botones
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnEnviar = new JButton("Enviar solicitud");
        JButton btnCerrar = new JButton("Regresar");
        bottom.add(btnCerrar);
        bottom.add(btnEnviar);
        dialog.add(bottom, BorderLayout.SOUTH);

        btnCerrar.addActionListener(e -> dialog.dispose());
        btnEnviar.addActionListener(e -> {
            CanalLocal canalSel = (CanalLocal) comboCanales.getSelectedItem();
            com.arquitectura.entidades.ClienteLocal userSel = listaUsuarios.getSelectedValue();
            if (canalSel == null) { JOptionPane.showMessageDialog(dialog, "Seleccione un canal", "Validacion", JOptionPane.WARNING_MESSAGE); return; }
            if (userSel == null) { JOptionPane.showMessageDialog(dialog, "Seleccione un usuario", "Validacion", JOptionPane.WARNING_MESSAGE); return; }
            boolean ok = canalController.invitarUsuarioACanal(canalSel.getId(), userSel.getId(), null);
            if (ok) { JOptionPane.showMessageDialog(dialog, "Invitacion enviada", "Exito", JOptionPane.INFORMATION_MESSAGE); dialog.dispose(); }
            else { JOptionPane.showMessageDialog(dialog, "No se pudo enviar la invitacion", "Error", JOptionPane.ERROR_MESSAGE); }
        });

        dialog.setVisible(true);
    }
    private void abandonarCanal() { JOptionPane.showMessageDialog(this, "Abandonar canal no implementado", "Info", JOptionPane.INFORMATION_MESSAGE); }
    private void eliminarCanal() { JOptionPane.showMessageDialog(this, "Eliminar canal no implementado", "Info", JOptionPane.INFORMATION_MESSAGE); }
    private void buscarCanales() { if (txtBuscarCanal.getText().trim().isEmpty()) cargarTodosLosCanales(); else modeloTodosCanales.clear(); }

    private void configurarVentana() {
        setTitle("Gestion de Canales");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    static class CanalListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof CanalLocal c) {
                String prefix = Boolean.TRUE.equals(c.getPrivado()) ? "[Privado] " : "[Publico] ";
                setText(prefix + c.getNombre());
            }
            return this;
        }
    }
}




