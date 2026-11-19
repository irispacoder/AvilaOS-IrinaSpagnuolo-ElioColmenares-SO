/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gui;

import avilaos.estructuras.ListaEnlazada;
import enums.ModoUsuario;
import enums.OperacionIO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import persistencia.CargadorJSON.Permiso;
import persistencia.CargadorJSON.TipoEntrada;
import sistema.NodoFS;
import sistema.SesionProyecto2;
import sistema.SistemaArchivos;
/**
 *
 * @author ispam
 */
public class PanelSistemaArchivos extends JPanel{
   private final SesionProyecto2 sesion;
    private final SistemaArchivos fs;
    private final JTree arbol;
    private final DefaultTreeModel modeloArbol;
    private final JLabel lblInfoNodo = new JLabel("Seleccione un archivo o directorio en el árbol.");
    private final DefaultTableModel modeloTabla;
    private final JComboBox<ModoUsuario> cbModo = new JComboBox<>(ModoUsuario.values());
    private final JTextField txtUsuario = new JTextField("user1", 8);
    private int ultimaVersion = -1;

    public PanelSistemaArchivos(SesionProyecto2 sesion) {
        super(new BorderLayout(8, 8));
        this.sesion = sesion;
        this.fs = sesion.getGestorArchivos();
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel barraSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        barraSuperior.setBorder(BorderFactory.createTitledBorder("Control de Acceso y Operaciones CRUD (Vía Procesos de E/S)"));

        cbModo.setSelectedItem(fs.getModoUsuarioActual());
        cbModo.addActionListener(e -> {
            ModoUsuario sel = (ModoUsuario) cbModo.getSelectedItem();
            if (sel != null) {
                fs.setModoUsuarioActual(sel);
                txtUsuario.setEnabled(sel == ModoUsuario.USUARIO);
            }
        });
        txtUsuario.setEnabled(false);
        txtUsuario.addActionListener(e -> fs.setUsuarioActivo(txtUsuario.getText()));

        JButton btnCrear = new JButton("Crear");
        JButton btnLeer = new JButton("Leer");
        JButton btnRenombrar = new JButton("Actualizar (Renombrar)");
        JButton btnEliminar = new JButton("Eliminar");

        btnCrear.addActionListener(e -> dialogoCrear());
        btnLeer.addActionListener(e -> ejecutarCRUDSeleccionado(OperacionIO.READ));
        btnRenombrar.addActionListener(e -> ejecutarCRUDSeleccionado(OperacionIO.UPDATE));
        btnEliminar.addActionListener(e -> ejecutarCRUDSeleccionado(OperacionIO.DELETE));

        barraSuperior.add(new JLabel("Modo:"));
        barraSuperior.add(cbModo);
        barraSuperior.add(new JLabel("Usuario:"));
        barraSuperior.add(txtUsuario);
        barraSuperior.add(btnCrear);
        barraSuperior.add(btnLeer);
        barraSuperior.add(btnRenombrar);
        barraSuperior.add(btnEliminar);
        add(barraSuperior, BorderLayout.NORTH);

        modeloArbol = new DefaultTreeModel(construirNodoVisual(fs.getRaiz()));
        arbol = new JTree(modeloArbol);
        arbol.addTreeSelectionListener(e -> mostrarDetalleSeleccionado());

        JPanel panelIzquierdo = new JPanel(new BorderLayout(4, 4));
        panelIzquierdo.setBorder(BorderFactory.createTitledBorder("Jerarquía de Directorios y Archivos"));
        panelIzquierdo.add(new JScrollPane(arbol), BorderLayout.CENTER);
        lblInfoNodo.setBorder(BorderFactory.createTitledBorder("Propiedades del Elemento"));
        panelIzquierdo.add(lblInfoNodo, BorderLayout.SOUTH);

        String[] columnas = {"Nombre", "Bloques", "Primer Bloque", "Dueño", "Proceso Creador", "Color"};
        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable tablaArchivos = new JTable(modeloTabla);
        tablaArchivos.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
                lbl.setOpaque(true);
                if (value instanceof Color c) {
                    lbl.setBackground(c);
                }
                return lbl;
            }
        });

        JPanel panelDerecho = new JPanel(new BorderLayout());
        panelDerecho.setBorder(BorderFactory.createTitledBorder("Tabla de Asignación de Archivos"));
        panelDerecho.add(new JScrollPane(tablaArchivos), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelIzquierdo, panelDerecho);
        split.setResizeWeight(0.42);
        add(split, BorderLayout.CENTER);

        refrescar();
    }

    public void refrescar() {
        int version = fs.getVersionEstructura();
        if (version != ultimaVersion) {
            modeloArbol.setRoot(construirNodoVisual(fs.getRaiz()));
            for (int i = 0; i < arbol.getRowCount(); i++) {
                arbol.expandRow(i);
            }
            actualizarTabla();
            ultimaVersion = version;
        }
    }

    private void actualizarTabla() {
        modeloTabla.setRowCount(0);
        ListaEnlazada<NodoFS> archivos = fs.listarTodosLosArchivos();
        for (int i = 0; i < archivos.getTamano(); i++) {
            NodoFS a = archivos.obtener(i);
            modeloTabla.addRow(new Object[]{
                a.getRutaCompleta(),
                a.getTamanoBloques(),
                a.getPrimerBloque(),
                a.getDueno(),
                a.getProcesoCreador(),
                a.getColor()
            });
        }
    }

    private DefaultMutableTreeNode construirNodoVisual(NodoFS nodoFS) {
        DefaultMutableTreeNode visual = new DefaultMutableTreeNode(nodoFS);
        ListaEnlazada<NodoFS> hijos = nodoFS.getHijos();
        for (int i = 0; i < hijos.getTamano(); i++) {
            visual.add(construirNodoVisual(hijos.obtener(i)));
        }
        return visual;
    }

    private NodoFS obtenerNodoSeleccionado() {
        if (arbol.getLastSelectedPathComponent() instanceof DefaultMutableTreeNode dmt
                && dmt.getUserObject() instanceof NodoFS nodo) {
            return nodo;
        }
        return fs.getRaiz();
    }

    private void mostrarDetalleSeleccionado() {
        NodoFS n = obtenerNodoSeleccionado();
        lblInfoNodo.setText(String.format(
            "<html><b>Nombre:</b> %s &nbsp;|&nbsp; <b>Tipo:</b> %s &nbsp;|&nbsp; <b>Bloques:</b> %d<br>" +
            "<b>Dueño:</b> %s &nbsp;|&nbsp; <b>Permiso:</b> %s &nbsp;|&nbsp; <b>1er Bloque:</b> %s</html>",
            n.getNombre(), n.esDirectorio() ? "Directorio" : "Archivo", n.getTamanoBloques(),
            n.getDueno(), n.getPermiso(), n.getPrimerBloque() >= 0 ? n.getPrimerBloque() : "N/A"
        ));
    }

    private void dialogoCrear() {
        fs.setUsuarioActivo(txtUsuario.getText());
        NodoFS sel = obtenerNodoSeleccionado();
        String base = sel.esDirectorio() ? sel.getRutaCompleta()
                : (sel.getPadre() != null ? sel.getPadre().getRutaCompleta() : "/");

        JTextField txtNombre = new JTextField();
        JComboBox<TipoEntrada> cbTipo = new JComboBox<>(TipoEntrada.values());
        JSpinner spBloques = new JSpinner(new SpinnerNumberModel(4, 1, 500, 1));
        JComboBox<Permiso> cbPermiso = new JComboBox<>(Permiso.values());

        JPanel form = new JPanel(new GridLayout(0, 2, 5, 5));
        form.add(new JLabel("Directorio padre:"));
        form.add(new JLabel(base));
        form.add(new JLabel("Nombre:"));
        form.add(txtNombre);
        form.add(new JLabel("Tipo:"));
        form.add(cbTipo);
        form.add(new JLabel("Tamaño (bloques):"));
        form.add(spBloques);
        form.add(new JLabel("Permiso:"));
        form.add(cbPermiso);

        if (JOptionPane.showConfirmDialog(this, form, "Crear Archivo / Directorio",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            String nombre = txtNombre.getText().trim();
            if (nombre.isEmpty() || nombre.contains("/")) {
                JOptionPane.showMessageDialog(this, "Nombre inválido.", "Error de validación", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String rutaNueva = "/".equals(base) ? "/" + nombre : base + "/" + nombre;
            TipoEntrada tipo = (TipoEntrada) cbTipo.getSelectedItem();
            int bloques = tipo == TipoEntrada.FILE ? (Integer) spBloques.getValue() : 0;
            try {
                sesion.crearProcesoIO(OperacionIO.CREATE, rutaNueva, tipo, bloques,
                        (Permiso) cbPermiso.getSelectedItem(), null);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Operación rechazada", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void ejecutarCRUDSeleccionado(OperacionIO op) {
        fs.setUsuarioActivo(txtUsuario.getText());
        NodoFS sel = obtenerNodoSeleccionado();
        String nuevoNombre = null;
        if (op == OperacionIO.UPDATE) {
            nuevoNombre = JOptionPane.showInputDialog(this, "Nuevo nombre para " + sel.getNombre() + ":", sel.getNombre());
            if (nuevoNombre == null) return;
        }
        try {
            sesion.crearProcesoIO(op, sel.getRutaCompleta(), sel.getTipo(), 0, sel.getPermiso(), nuevoNombre);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Operación rechazada", JOptionPane.WARNING_MESSAGE);
        }
    }
}
