package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import modelo.SolicitudIO;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import planificadores.PoliticaDisco;
import sistema.Disco;
import sistema.Disco.DetalleBloque;
import sistema.Disco.EstadoBloque;
import sistema.PlanificadorDisco;

/** Vista del disco y su cola. Crear y actualizar en el hilo de eventos de Swing. */
public final class PanelDisco extends JPanel {
    private static final int COLUMNAS = 20;
    private static final Border BORDE_CABEZAL = BorderFactory.createLineBorder(new Color(0x172554), 2);
    private static final Border BORDE_NORMAL = BorderFactory.createEmptyBorder(2, 2, 2, 2);

    private final Disco disco;
    private final PlanificadorDisco planificador;
    private final JLabel posicionCabezal = new JLabel();
    private final JLabel metricas = new JLabel();
    private final JTextArea cola = new JTextArea(8, 26);
    private final JLabel[] celdas;
    private final XYSeries recorrido = new XYSeries("Desplazamiento", true, false);

    public PanelDisco(Disco disco, PlanificadorDisco planificador) {
        super(new BorderLayout(8, 8));
        this.disco = disco;
        this.planificador = planificador;
        celdas = new JLabel[disco.getCantidadBloques()];
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel leyenda = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
        leyenda.add(posicionCabezal);
        for (EstadoBloque estado : EstadoBloque.values()) {
            JLabel clave = new JLabel(descripcion(estado));
            clave.setOpaque(true);
            clave.setForeground(Color.BLACK);
            clave.setBackground(color(estado));
            clave.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            leyenda.add(clave);
        }
        JComboBox<PoliticaDisco> politicas = new JComboBox<>(PoliticaDisco.values());
        politicas.setSelectedItem(planificador.obtenerEstado().politica());
        politicas.addActionListener(e -> {
            if (politicas.getSelectedItem() instanceof PoliticaDisco politica) {
                planificador.cambiarPolitica(politica);
            }
        });
        leyenda.add(new JLabel("Política de disco"));
        leyenda.add(politicas);
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.add(leyenda, BorderLayout.NORTH);
        encabezado.add(metricas, BorderLayout.SOUTH);
        add(encabezado, BorderLayout.NORTH);

        JPanel mapa = new JPanel(new GridLayout(0, COLUMNAS, 2, 2));
        for (int i = 0; i < celdas.length; i++) {
            JLabel celda = new JLabel(Integer.toString(i), SwingConstants.CENTER);
            celda.setOpaque(true);
            celda.setForeground(Color.BLACK);
            celda.setPreferredSize(new Dimension(25, 21));
            celdas[i] = celda;
            mapa.add(celda);
        }
        JScrollPane panelCola = new JScrollPane(cola);
        panelCola.setBorder(BorderFactory.createTitledBorder("Cola de E/S"));
        cola.setEditable(false);
        JSplitPane contenido = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(mapa), panelCola);
        contenido.setResizeWeight(0.75);
        add(contenido, BorderLayout.CENTER);

        recorrido.setMaximumItemCount(500);
        JFreeChart grafico = ChartFactory.createXYLineChart(
                "Desplazamiento acumulado del cabezal", "Ciclo", "Bloques recorridos",
                new XYSeriesCollection(recorrido), PlotOrientation.VERTICAL, false, true, false);
        NumberAxis bloques = (NumberAxis) grafico.getXYPlot().getRangeAxis();
        bloques.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        NumberAxis ciclos = (NumberAxis) grafico.getXYPlot().getDomainAxis();
        ciclos.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        ChartPanel panelGrafico = new ChartPanel(grafico);
        panelGrafico.setPreferredSize(new Dimension(600, 230));
        add(panelGrafico, BorderLayout.SOUTH);
        actualizar();
    }

    public void actualizar() {
        PlanificadorDisco.Estado estado = planificador.obtenerEstado();
        int cabezal = estado.cabezal();
        DetalleBloque[] mapa = disco.obtenerDetalleBloques();
        posicionCabezal.setText("Cabezal " + cabezal + (estado.direccion() == Disco.Direccion.ASCENDENTE ? " ↑" : " ↓"));
        for (int i = 0; i < celdas.length; i++) {
            JLabel celda = celdas[i];
            DetalleBloque bloque = mapa[i];
            celda.setBackground(color(bloque.estado()));
            celda.setBorder(i == cabezal ? BORDE_CABEZAL : BORDE_NORMAL);
            String detalle = "Bloque " + i + " · " + descripcion(bloque.estado())
                    + (bloque.ocupante() == null ? "" : " · " + bloque.ocupante())
                    + (i == cabezal ? " · Cabezal" : "");
            celda.setToolTipText(detalle);
            celda.getAccessibleContext().setAccessibleName(detalle);
        }
        PlanificadorDisco.Metricas medidas = estado.metricas();
        recorrido.addOrUpdate(Long.valueOf(medidas.ciclo()), Long.valueOf(medidas.desplazamiento()));
        StringBuilder texto = new StringBuilder("Distancia %d bloques · Espera media %.2f ciclos · Atendidas %d"
                .formatted(medidas.desplazamiento(), medidas.esperaPromedio(), medidas.solicitudesAtendidas()));
        for (PlanificadorDisco.Atenciones atenciones : estado.porPolitica()) {
            texto.append(" · ").append(atenciones.politica()).append(' ').append(atenciones.cantidad());
        }
        metricas.setText(texto.toString());

        StringBuilder peticiones = new StringBuilder();
        if (estado.activa() != null) {
            peticiones.append("En curso  ").append(describirSolicitud(estado.activa())).append('\n');
        }
        for (SolicitudIO pendiente : estado.pendientes()) {
            peticiones.append(describirSolicitud(pendiente)).append('\n');
        }
        String nuevaCola = peticiones.isEmpty() ? "Sin solicitudes" : peticiones.toString();
        if (!nuevaCola.equals(cola.getText())) {
            cola.setText(nuevaCola);
        }
    }

    private static String describirSolicitud(SolicitudIO solicitud) {
        return "%s · %s · bloque %d".formatted(solicitud.operacion(), solicitud.origen(), solicitud.bloque());
    }

    private static Color color(EstadoBloque estado) {
        return switch (estado) {
            case LIBRE -> new Color(0xE5E7EB);
            case ARCHIVO -> new Color(0x93C5FD);
            case SWAP_LIBRE -> new Color(0xE9D5FF);
            case SWAP_OCUPADO -> new Color(0xC084FC);
        };
    }

    private static String descripcion(EstadoBloque estado) {
        return switch (estado) {
            case LIBRE -> "Libre";
            case ARCHIVO -> "Archivo";
            case SWAP_LIBRE -> "Swap libre";
            case SWAP_OCUPADO -> "Swap ocupado";
        };
    }
}
