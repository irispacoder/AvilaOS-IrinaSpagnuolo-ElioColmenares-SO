/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gui;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import sistema.ClusterManager;
import sistema.Computador;

import javax.swing.*;
import java.awt.*;

/**
 *
 * @author ispam
 */
public class PanelGraficoMetricas extends JPanel{
    private final ClusterManager clusterManager;
    private final XYSeriesCollection dataset;
    private final avilaos.estructuras.ListaEnlazada<XYSeries> seriesPorComputador;

    public PanelGraficoMetricas(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
        this.dataset = new XYSeriesCollection();
        this.seriesPorComputador = new avilaos.estructuras.ListaEnlazada<>();

        setLayout(new BorderLayout());

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Utilización de CPU vs. Tiempo (Ciclos)",
                "Ciclo de Reloj Global",
                "Utilización CPU (%)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();
        org.jfree.chart.axis.NumberAxis domainAxis = (org.jfree.chart.axis.NumberAxis) plot.getDomainAxis();
        domainAxis.setNumberFormatOverride(new java.text.DecimalFormat("0"));
        plot.getRangeAxis().setRange(0.0, 100.0);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(500, 220));
        add(chartPanel, BorderLayout.CENTER);

        reconstruirSeries();
    }

    public synchronized void reconstruirSeries() {
        dataset.removeAllSeries();
        seriesPorComputador.vaciar();

        for (int i = 0; i < clusterManager.getComputadores().getTamano(); i++) {
            Computador comp = clusterManager.getComputadores().obtener(i);
            XYSeries serie = new XYSeries("Comp " + comp.getIdComputador() + ": " + comp.getNombre(), true, false);
            serie.setMaximumItemCount(100);
            dataset.addSeries(serie);
            seriesPorComputador.agregarAlFinal(serie);
        }
    }

    public synchronized void actualizarGrafico() {
        int cicloActual = clusterManager.getRelojGlobal().getCicloActual();
        int numComp = clusterManager.getComputadores().getTamano();

        if (numComp != seriesPorComputador.getTamano()) {
            reconstruirSeries();
            return;
        }

        for (int i = 0; i < numComp; i++) {
            Computador comp = clusterManager.getComputadores().obtener(i);
            XYSeries serie = seriesPorComputador.obtener(i);
            double cpuUso = comp.getMetricas().getUtilizacionCPU();
            serie.addOrUpdate((Number) cicloActual, (Number) cpuUso);
        }
    }
}
