package gui;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import modelo.SolicitudIO;
import org.jfree.chart.ChartPanel;
import org.jfree.data.xy.XYDataset;
import planificadores.PoliticaDisco;
import sistema.Disco;
import sistema.PlanificadorDisco;

public final class PanelDiscoPrueba {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--visual")) {
            SwingUtilities.invokeLater(PanelDiscoPrueba::abrirDemostracion);
            return;
        }
        BufferedImage[] imagen = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> imagen[0] = verificarPanel());
        if (args.length == 1) {
            ImageIO.write(imagen[0], "png", new File(args[0]));
        }
        System.out.println("Vista de disco: mapa, cabezal y gráfico verificados");
    }

    private static BufferedImage verificarPanel() {
        Disco disco = prepararDisco();
        PlanificadorDisco planificador = prepararCola(disco, PoliticaDisco.SCAN);
        PanelDisco vista = new PanelDisco(disco, planificador);
        JSplitPane contenido = (JSplitPane) vista.getComponent(1);
        JScrollPane scroll = (JScrollPane) contenido.getLeftComponent();
        Container mapa = (Container) scroll.getViewport().getView();
        comprobar(mapa.getComponentCount() == 100, "Se muestran los 100 bloques");
        comprobar(detalle(mapa, 0).contains("Archivo"), "Estado de archivo");
        comprobar(detalle(mapa, 10).contains("Libre"), "Estado libre");
        comprobar(detalle(mapa, 90).contains("Swap ocupado"), "Estado de swap ocupado");
        comprobar(detalle(mapa, 99).contains("Swap libre"), "Estado de swap libre");
        comprobar(detalle(mapa, 50).contains("Cabezal"), "Ubicación inicial");

        comprobar(detalle(mapa, 0).contains("/sistema/boot.bin"), "Identifica el archivo ocupante");
        comprobar(detalle(mapa, 90).contains("P7"), "Identifica el proceso suspendido");
        for (int ciclo = 1; ciclo <= 85; ciclo++) {
            planificador.avanzarCiclo();
            vista.actualizar();
        }
        vista.actualizar();
        comprobar(detalle(mapa, 65).contains("Cabezal"), "Ubicación después de invertir");
        comprobar(!detalle(mapa, 50).contains("Cabezal"), "Se retiró la marca anterior");

        ChartPanel grafico = (ChartPanel) vista.getComponent(2);
        XYDataset datos = grafico.getChart().getXYPlot().getDataset();
        comprobar(datos.getItemCount(0) == 86, "Una muestra por ciclo, incluso con refrescos repetidos");
        comprobar(datos.getYValue(0, 51) == 49, "La gráfica acumula 49 bloques al llegar al extremo");
        comprobar(datos.getYValue(0, 85) == 83, "La gráfica sigue acumulando después de invertir");

        vista.setSize(1000, 520);
        distribuir(vista);
        BufferedImage imagen = new BufferedImage(1000, 520, BufferedImage.TYPE_INT_RGB);
        Graphics2D pincel = imagen.createGraphics();
        vista.printAll(pincel);
        pincel.dispose();
        return imagen;
    }

    private static String detalle(Container mapa, int bloque) {
        return ((JLabel) mapa.getComponent(bloque)).getToolTipText();
    }

    private static void distribuir(Container contenedor) {
        contenedor.doLayout();
        for (var componente : contenedor.getComponents()) {
            if (componente instanceof Container hijo) {
                distribuir(hijo);
            }
        }
    }

    private static Disco prepararDisco() {
        Disco disco = new Disco(100, 10);
        disco.asignarArchivo(8, "/sistema/boot.bin");
        disco.reservarSwap(3, "P7");
        disco.moverCabezalA(50);
        return disco;
    }

    private static PlanificadorDisco prepararCola(Disco disco, PoliticaDisco politica) {
        PlanificadorDisco planificador = new PlanificadorDisco(disco, politica);
        for (int bloque : new int[]{10, 90, 45, 70}) {
            planificador.encolar(bloque);
        }
        return planificador;
    }

    private static void abrirDemostracion() {
        Disco disco = new Disco(200, 20, 50);
        disco.asignarArchivo(8, "/sistema/boot.bin");
        disco.reservarSwap(3, "P7");
        PlanificadorDisco planificador = new PlanificadorDisco(disco, PoliticaDisco.FIFO);
        int[] anexo = {82, 17, 43, 140, 24, 16, 190};
        for (int bloque : anexo) {
            planificador.encolar(bloque);
        }
        PanelDisco vista = new PanelDisco(disco, planificador);
        JFrame ventana = new JFrame("Prueba de disco · Anexo oficial");
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventana.add(vista, BorderLayout.CENTER);
        JButton paso = new JButton("Avanzar un ciclo");
        JButton correr = new JButton("Ejecutar");
        JLabel estado = new JLabel("Cabezal 50 ↑ · Cola 82, 17, 43, 140, 24, 16, 190");
        StringBuilder atendidas = new StringBuilder();
        Timer timer = new Timer(25, null);
        Runnable avanzar = () -> {
            SolicitudIO solicitud = planificador.avanzarCiclo();
            if (solicitud != null) {
                if (!atendidas.isEmpty()) {
                    atendidas.append(", ");
                }
                atendidas.append(solicitud.bloque());
            }
            PlanificadorDisco.Metricas metricas = planificador.obtenerMetricas();
            vista.actualizar();
            if (!atendidas.isEmpty()) {
                estado.setText("Atendidas " + atendidas);
            }
            if (metricas.solicitudesAtendidas() == anexo.length) {
                timer.stop();
                paso.setEnabled(false);
                correr.setEnabled(false);
            }
        };
        timer.addActionListener(e -> avanzar.run());
        paso.addActionListener(e -> avanzar.run());
        correr.addActionListener(e -> {
            if (timer.isRunning()) {
                timer.stop();
                correr.setText("Ejecutar");
                paso.setEnabled(true);
            } else {
                timer.start();
                correr.setText("Pausar");
                paso.setEnabled(false);
            }
        });
        JPanel controles = new JPanel();
        controles.add(paso);
        controles.add(correr);
        controles.add(estado);
        ventana.add(controles, BorderLayout.SOUTH);
        ventana.setSize(1050, 620);
        ventana.setLocationRelativeTo(null);
        ventana.setVisible(true);
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
