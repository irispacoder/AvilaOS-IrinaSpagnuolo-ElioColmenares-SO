package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import persistencia.CargadorJSON;
import persistencia.CargadorJSON.ArchivoDisco;
import persistencia.CargadorJSON.ArchivoProcesos;
import sistema.ClusterManager;
import sistema.SesionProyecto2;

public class VentanaPrincipal extends JFrame {
    private final JButton cargarDisco = new JButton("Cargar disco JSON");
    private final JButton cargarProcesos = new JButton("Cargar procesos JSON");
    private final JButton guardarDisco = new JButton("Guardar disco JSON");
    private final JButton iniciar = new JButton("Iniciar / Reanudar");
    private final JButton pausar = new JButton("Pausar");
    private final JButton detener = new JButton("Detener");
    private final JLabel estado = new JLabel();
    private final JTextArea log = new JTextArea();
    private final JTabbedPane vistas = new JTabbedPane();
    private final Timer refresco;

    private ArchivoDisco archivoDisco;
    private ArchivoProcesos archivoProcesos;
    private String nombreDisco = "pendiente";
    private String nombreProcesos = "pendiente";
    private SesionProyecto2 sesion;
    private boolean reconstruirAlIniciar;
    private PanelComputadorView computador;
    private PanelGraficoMetricas graficoCPU;
    private PanelDisco disco;
    private int ultimoLog;

    public VentanaPrincipal() {
        super("ÁvilaOS · Proyecto 2");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(820, 580));
        setSize(1150, 760);
        setLocationRelativeTo(null);

        JToolBar acciones = new JToolBar();
        acciones.setFloatable(false);
        acciones.add(cargarDisco);
        acciones.add(cargarProcesos);
        acciones.add(guardarDisco);
        acciones.addSeparator();
        acciones.add(iniciar);
        acciones.add(pausar);
        acciones.add(detener);
        add(acciones, BorderLayout.NORTH);
        add(vistas, BorderLayout.CENTER);
        estado.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        add(estado, BorderLayout.SOUTH);
        log.setEditable(false);

        cargarDisco.addActionListener(e -> cargarJSON(true));
        cargarProcesos.addActionListener(e -> cargarJSON(false));
        guardarDisco.addActionListener(e -> guardarJSON());
        iniciar.addActionListener(e -> iniciarSesion());
        pausar.addActionListener(e -> {
            sesion.getCluster().pausarSimulacion();
            actualizarControles();
        });
        detener.addActionListener(e -> detenerSesion());
        refresco = new Timer(100, e -> refrescarGUI());
        refresco.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                refresco.stop();
                if (sesion != null) {
                    sesion.getCluster().detenerSimulacion();
                }
            }
        });
        mostrarCargaPendiente("Carga el sistema de archivos y los procesos para preparar la simulación.");
    }

    private void cargarJSON(boolean paraDisco) {
        JFileChooser selector = selectorJSON();
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path ruta = selector.getSelectedFile().toPath();
        try {
            aplicarArchivoJSON(ruta, paraDisco);
        } catch (IOException | IllegalArgumentException e) {
            mostrarError(e);
        }
    }

    void aplicarArchivoJSON(Path ruta, boolean paraDisco) throws IOException {
        if (paraDisco) {
            archivoDisco = CargadorJSON.leerSistemaArchivos(ruta);
            nombreDisco = ruta.getFileName().toString();
        } else {
            archivoProcesos = CargadorJSON.leerProcesos(ruta);
            nombreProcesos = ruta.getFileName().toString();
        }
        prepararSesion();
    }

    private void prepararSesion() {
        if (archivoDisco == null || archivoProcesos == null) {
            mostrarCargaPendiente("Carga el JSON restante para preparar la simulación.");
            return;
        }
        SesionProyecto2 nueva;
        try {
            nueva = new SesionProyecto2(archivoDisco, archivoProcesos);
        } catch (IllegalArgumentException e) {
            mostrarCargaPendiente("Las configuraciones no se pueden combinar. Revisa el mapa y el tamaño de swap.");
            throw e;
        }

        sesion = nueva;
        reconstruirAlIniciar = false;
        computador = new PanelComputadorView(sesion.getComputador());
        graficoCPU = new PanelGraficoMetricas(sesion.getCluster());
        disco = new PanelDisco(sesion.getSistemaArchivos().disco(), sesion.getPlanificador());
        log.setText("");
        ultimoLog = -1;

        JPanel vistaCPU = new JPanel(new BorderLayout());
        vistaCPU.add(computador, BorderLayout.CENTER);
        vistaCPU.add(graficoCPU, BorderLayout.SOUTH);
        vistas.removeAll();
        vistas.addTab("Disco local", disco);
        vistas.addTab("Computador", vistaCPU);
        vistas.addTab("Eventos", new JScrollPane(log));
        actualizarControles();
        refrescarGUI();
    }

    private void mostrarCargaPendiente(String texto) {
        sesion = null;
        vistas.removeAll();
        vistas.addTab("Configuración", new JLabel(texto, SwingConstants.CENTER));
        actualizarControles();
    }

    private void iniciarSesion() {
        if (sesion == null) {
            return;
        }
        try {
            if (reconstruirAlIniciar) {
                prepararSesion();
            }
            sesion.getCluster().iniciarSimulacion();
            actualizarControles();
        } catch (IllegalArgumentException e) {
            mostrarError(e);
        }
    }

    private void detenerSesion() {
        sesion.getCluster().detenerSimulacion();
        archivoDisco = CargadorJSON.capturarSistema(sesion.getSistemaArchivos());
        reconstruirAlIniciar = true;
        actualizarControles();
    }

    private void guardarJSON() {
        JFileChooser selector = selectorJSON();
        if (selector.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path ruta = selector.getSelectedFile().toPath();
            if (java.nio.file.Files.exists(ruta) && JOptionPane.showConfirmDialog(this,
                    "¿Reemplazar " + ruta.getFileName() + "?", "Guardar disco",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                CargadorJSON.guardarSistemaArchivos(ruta, sesion.getSistemaArchivos());
            } catch (IOException | IllegalArgumentException e) {
                mostrarError(e);
            }
        }
    }

    private JFileChooser selectorJSON() {
        JFileChooser selector = new JFileChooser();
        selector.setFileFilter(new FileNameExtensionFilter("Archivos JSON", "json"));
        return selector;
    }

    private void actualizarControles() {
        boolean enMarcha = sesion != null && sesion.getCluster().getRelojGlobal().isEnEjecucion();
        boolean enPausa = enMarcha && sesion.getCluster().getRelojGlobal().isPausado();
        iniciar.setEnabled(sesion != null && (!enMarcha || enPausa));
        pausar.setEnabled(enMarcha && !enPausa);
        detener.setEnabled(enMarcha);
        cargarDisco.setEnabled(!enMarcha);
        cargarProcesos.setEnabled(!enMarcha);
        guardarDisco.setEnabled(sesion != null);
        actualizarEstado();
    }

    private void actualizarEstado() {
        String texto = "Disco " + nombreDisco + " · Procesos " + nombreProcesos;
        if (sesion != null) {
            texto += " · Ciclo " + sesion.getPlanificador().obtenerMetricas().ciclo();
            int pendientes = sesion.obtenerProcesosIO().length;
            if (pendientes > 0) {
                texto += " · " + pendientes + " procesos de E/S importados, pendientes de conexión al PCB";
            }
        }
        estado.setText(texto);
    }

    private void refrescarGUI() {
        if (sesion == null) {
            return;
        }
        actualizarEstado();
        computador.actualizarRefrescoGUI();
        if (!reconstruirAlIniciar) {
            graficoCPU.actualizarGrafico();
        }
        disco.actualizar();
        ClusterManager cluster = sesion.getCluster();
        int cantidad = cluster.getContadorTotalLogsGenerados();
        if (cantidad != ultimoLog) {
            StringBuilder texto = new StringBuilder();
            cluster.obtenerUltimosLogs(300).paraCadaElemento(linea -> texto.append(linea).append('\n'));
            log.setText(texto.toString());
            log.setCaretPosition(log.getDocument().getLength());
            ultimoLog = cantidad;
        }
    }

    private void mostrarError(Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), "Configuración inválida", JOptionPane.ERROR_MESSAGE);
    }
}
