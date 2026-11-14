/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gui;

import enums.PoliticaPlanificacion;
import modelo.MetricasComputador.SnapshotMetricas;
import modelo.Proceso;
import sistema.Computador;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

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
    private final DefaultListModel<String> modelTerminados;

    private final JLabel lblThroughput;
    private final JLabel lblUtilizaciónCpu;
    private final JLabel lblTiempoRespuesta;
    private final JLabel lblCumplimientoDeadlines;
    private final JLabel lblIndiceEquidad;

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

        JPanel pnlColas = new JPanel(new GridLayout(1, 4, 3, 3));
        pnlColas.setBorder(BorderFactory.createTitledBorder("Colas del Sistema"));

        modelNuevos = new DefaultListModel<>();
        modelListos = new DefaultListModel<>();
        modelBloqueados = new DefaultListModel<>();
        modelTerminados = new DefaultListModel<>();

        pnlColas.add(crearPanelCola("NUEVOS", modelNuevos));
        pnlColas.add(crearPanelCola("LISTOS", modelListos));
        pnlColas.add(crearPanelCola("BLOQUEADOS", modelBloqueados));
        pnlColas.add(crearPanelCola("TERMINADOS", modelTerminados));

        add(pnlColas, BorderLayout.CENTER);

        JPanel pnlMetricas = new JPanel(new GridLayout(2, 3, 5, 2));
        pnlMetricas.setBorder(BorderFactory.createTitledBorder("Métricas de Rendimiento"));

        lblUtilizaciónCpu = new JLabel("CPU: 0.0%");
        lblThroughput = new JLabel("Throughput: 0.0");
        lblTiempoRespuesta = new JLabel("T. Resp: 0.0 c");
        lblCumplimientoDeadlines = new JLabel("Deadlines: 100.0%");
        lblIndiceEquidad = new JLabel("Equidad (Jain): 1.00");

        pnlMetricas.add(lblUtilizaciónCpu);
        pnlMetricas.add(lblThroughput);
        pnlMetricas.add(lblTiempoRespuesta);
        pnlMetricas.add(lblCumplimientoDeadlines);
        pnlMetricas.add(lblIndiceEquidad);

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
        actualizarModeloLista(modelTerminados, computador.getColaTerminados());

        SnapshotMetricas snap = computador.getMetricas().obtenerSnapshot();
        lblUtilizaciónCpu.setText(String.format("CPU: %.1f%%", snap.utilizacionCPU));
        lblThroughput.setText(String.format("Thruput: %.3f p/c", snap.throughput));
        lblTiempoRespuesta.setText(String.format("T.Resp: %.1f c", snap.tiempoRespuestaPromedio));
        lblCumplimientoDeadlines.setText(String.format("Deadlines: %.1f%%", snap.tasaCumplimientoDeadlines));
        lblIndiceEquidad.setText(String.format("Equidad: %.2f", snap.indiceEquidad));
    }

    private void actualizarModeloLista(DefaultListModel<String> model, avilaos.estructuras.ListaEnlazada<Proceso> lista) {
        model.clear();
        lista.paraCadaElemento(p ->
                model.addElement("[" + p.getId() + "] " + p.getNombre() + " (Dead:" + p.getDeadline() + ")"));
    }

    private void actualizarModeloListaBloqueados(DefaultListModel<String> model, avilaos.estructuras.ListaEnlazada<Proceso> lista) {
        model.clear();
        lista.paraCadaElemento(p ->
                model.addElement("[" + p.getId() + "] " + p.getNombre() + " -> " + p.getMotivoBloqueo()));
    }
}
