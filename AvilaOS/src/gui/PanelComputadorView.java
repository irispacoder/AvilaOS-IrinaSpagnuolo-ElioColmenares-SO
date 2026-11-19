/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gui;

import enums.PoliticaPlanificacion;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import modelo.MetricasComputador.SnapshotMetricas;
import modelo.Proceso;
import sistema.Computador;

/**
 *
 * @author ispam
 */
public class PanelComputadorView extends JPanel{
    private final Computador computador;

    private final JLabel lblCpuEstado;
    private final JLabel lblPC;
    private final JLabel lblModoCPU;
    private final JLabel lblRam;
    private final JComboBox<PoliticaPlanificacion> cbPolitica;
    private final JSpinner spQuantum;

    private final DefaultListModel<String> modelNuevos;
    private final DefaultListModel<String> modelListos;
    private final DefaultListModel<String> modelBloqueados;
    private final DefaultListModel<String> modelListosSusp;
    private final DefaultListModel<String> modelBloqueadosSusp;
    private final DefaultListModel<String> modelTerminados;

    private final JLabel lblThroughput;
    private final JLabel lblUtilizaciónCpu;
    private final JLabel lblTiempoRespuesta;
    private final JLabel lblCumplimientoDeadlines;
    private final JLabel lblIndiceEquidad;
    private final JLabel lblSwapping;

    public PanelComputadorView(Computador computador) {
        this.computador = computador;
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 2),
                "Computador " + computador.getIdComputador() + ": " + computador.getNombre(),
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 14)));

        JPanel pnlHeader = new JPanel(new GridLayout(3, 2, 5, 5));
        pnlHeader.setBorder(BorderFactory.createTitledBorder("Estado Procesador & RAM"));

        lblCpuEstado = new JLabel("CPU: LIBRE");
        lblCpuEstado.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblPC = new JLabel("PC: 0x0000");
        lblModoCPU = new JLabel("Modo: SISTEMA_OPERATIVO");
        lblRam = new JLabel("RAM: 0 / 0 MB");

        JPanel pnlPolitica = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        pnlPolitica.add(new JLabel("Pol:"));
        cbPolitica = new JComboBox<>(PoliticaPlanificacion.values());
        cbPolitica.setSelectedItem(computador.getPoliticaActual());
        spQuantum = new JSpinner(new SpinnerNumberModel(computador.getQuantumConfigurado(), 0, null, 1));
        cbPolitica.addActionListener(e -> {
            PoliticaPlanificacion sel = (PoliticaPlanificacion) cbPolitica.getSelectedItem();
            if (sel == PoliticaPlanificacion.ROUND_ROBIN && (Integer) spQuantum.getValue() == 0) {
                spQuantum.setValue(1);
            }
            if (sel != null) {
                computador.setPoliticaPlanificacion(sel);
            }
        });
        pnlPolitica.add(cbPolitica);

        pnlPolitica.add(new JLabel("Q:"));
        spQuantum.addChangeListener(e -> {
            int quantum = (Integer) spQuantum.getValue();
            if (quantum == 0 && computador.getPoliticaActual() == PoliticaPlanificacion.ROUND_ROBIN) {
                spQuantum.setValue(1);
            } else {
                computador.setQuantumConfigurado(quantum);
            }
        });
        pnlPolitica.add(spQuantum);

        pnlHeader.add(lblCpuEstado);
        pnlHeader.add(lblPC);
        pnlHeader.add(lblModoCPU);
        pnlHeader.add(lblRam);
        pnlHeader.add(pnlPolitica);

        add(pnlHeader, BorderLayout.NORTH);

        JPanel pnlColas = new JPanel(new GridLayout(1, 6, 3, 3));
        pnlColas.setBorder(BorderFactory.createTitledBorder("Colas del Sistema (Modelo de 7 Estados)"));

        modelNuevos = new DefaultListModel<>();
        modelListos = new DefaultListModel<>();
        modelBloqueados = new DefaultListModel<>();
        modelListosSusp = new DefaultListModel<>();
        modelBloqueadosSusp = new DefaultListModel<>();
        modelTerminados = new DefaultListModel<>();

        pnlColas.add(crearPanelCola("NUEVOS", modelNuevos));
        pnlColas.add(crearPanelCola("LISTOS", modelListos));
        pnlColas.add(crearPanelCola("BLOQUEADOS", modelBloqueados));
        pnlColas.add(crearPanelCola("LISTO/SUSP", modelListosSusp));
        pnlColas.add(crearPanelCola("BLOQ/SUSP", modelBloqueadosSusp));
        pnlColas.add(crearPanelCola("TERMINADOS", modelTerminados));

        add(pnlColas, BorderLayout.CENTER);

        JPanel pnlMetricas = new JPanel(new GridLayout(2, 3, 5, 2));
        pnlMetricas.setBorder(BorderFactory.createTitledBorder("Métricas de Rendimiento & Swapping"));

        lblUtilizaciónCpu = new JLabel("CPU: 0.0%");
        lblThroughput = new JLabel("Throughput: 0.0");
        lblTiempoRespuesta = new JLabel("T. Resp: 0.0 c");
        lblCumplimientoDeadlines = new JLabel("Deadlines: 100.0%");
        lblIndiceEquidad = new JLabel("Equidad (Jain): 1.00");
        lblSwapping = new JLabel("Susp: 0 | React: 0");

        pnlMetricas.add(lblUtilizaciónCpu);
        pnlMetricas.add(lblThroughput);
        pnlMetricas.add(lblTiempoRespuesta);
        pnlMetricas.add(lblCumplimientoDeadlines);
        pnlMetricas.add(lblIndiceEquidad);
        pnlMetricas.add(lblSwapping);

        add(pnlMetricas, BorderLayout.SOUTH);
    }

    private JPanel crearPanelCola(String titulo, DefaultListModel<String> model) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(titulo));
        JList<String> list = new JList<>(model);
        list.setFont(new Font("Monospaced", Font.PLAIN, 10));
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        return p;
    }

    public void actualizarRefrescoGUI() {
        Proceso cpuP = computador.getProcesoEnCPU();
        if (cpuP != null) {
            lblCpuEstado.setText("CPU: " + cpuP.getNombre() + " (Prio:" + cpuP.getPrioridad() + ")");
            lblCpuEstado.setForeground(Color.BLUE);
            lblPC.setText(cpuP.getFormatoPC());
        } else {
            lblCpuEstado.setText("CPU: LIBRE / IDLE");
            lblCpuEstado.setForeground(Color.DARK_GRAY);
            lblPC.setText("PC: 0x0000 (SISTEMA)");
        }

        lblModoCPU.setText("Modo: " + computador.getModoCPU().name());
        lblRam.setText("RAM: " + computador.getRamUsada() + " / " + computador.getRamTotal() + " MB");

        actualizarModeloLista(modelNuevos, computador.getColaNuevos().getListaInterna());
        actualizarModeloLista(modelListos, computador.getColaListos());
        actualizarModeloListaBloqueados(modelBloqueados, computador.getColaBloqueados());
        actualizarModeloLista(modelListosSusp, computador.getColaListosSuspendidos());
        actualizarModeloListaBloqueados(modelBloqueadosSusp, computador.getColaBloqueadosSuspendidos());
        actualizarModeloLista(modelTerminados, computador.getColaTerminados());

        SnapshotMetricas snap = computador.getMetricas().obtenerSnapshot();
        lblUtilizaciónCpu.setText(String.format("CPU: %.1f%%", snap.utilizacionCPU));
        lblThroughput.setText(String.format("Thruput: %.3f p/c", snap.throughput));
        lblTiempoRespuesta.setText(String.format("T.Resp: %.1f c", snap.tiempoRespuestaPromedio));
        lblCumplimientoDeadlines.setText(String.format("Deadlines: %.1f%%", snap.tasaCumplimientoDeadlines));
        lblIndiceEquidad.setText(String.format("Equidad: %.2f", snap.indiceEquidad));
        lblSwapping.setText("Susp: " + computador.getTotalSuspensiones() + " | React: " + computador.getTotalReactivaciones());
    }

    private void actualizarModeloLista(DefaultListModel<String> model, avilaos.estructuras.ListaEnlazada<Proceso> lista) {
        model.clear();
        lista.paraCadaElemento(p ->
                model.addElement("[" + p.getId() + "] " + p.getNombre() + " (" + p.getMemoriaRequerida() + "MB)"));
    }

    private void actualizarModeloListaBloqueados(DefaultListModel<String> model, avilaos.estructuras.ListaEnlazada<Proceso> lista) {
        model.clear();
        lista.paraCadaElemento(p ->
                model.addElement("[" + p.getId() + "] " + p.getNombre() + " -> " + p.getMotivoBloqueo()));
    }
}
