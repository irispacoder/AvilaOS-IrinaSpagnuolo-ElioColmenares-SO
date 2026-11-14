package gui;

import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

public final class VentanaPrincipalPrueba {
    public static void main(String[] args) throws Exception {
        Path ejemplos = Path.of(args.length == 0 ? "AvilaOS/ejemplos" : args[0]);
        SwingUtilities.invokeAndWait(() -> probarVentana(ejemplos));
        System.out.println("Ventana principal: inicio condicionado a los JSON y controles de reloj verificados");
    }

    private static void probarVentana(Path ejemplos) {
        VentanaPrincipal ventana = new VentanaPrincipal();
        try {
            ventana.setVisible(true);
            JToolBar barra = (JToolBar) ventana.getContentPane().getComponent(0);
            JButton iniciar = boton(barra, "Iniciar / Reanudar");
            JButton pausar = boton(barra, "Pausar");
            JButton detener = boton(barra, "Detener");
            JButton guardar = boton(barra, "Guardar disco JSON");
            comprobar(!iniciar.isEnabled() && !guardar.isEnabled(), "Sin archivos, el reloj no se puede iniciar");
            ventana.aplicarArchivoJSON(ejemplos.resolve("procesos.json"), false);
            comprobar(!iniciar.isEnabled(), "Un solo JSON es insuficiente");
            ventana.aplicarArchivoJSON(ejemplos.resolve("sistema-archivos.json"), true);
            comprobar(iniciar.isEnabled() && guardar.isEnabled(), "Ambos JSON habilitan el inicio");
            iniciar.doClick(0);
            comprobar(!iniciar.isEnabled() && pausar.isEnabled(), "El reloj está en marcha");
            pausar.doClick(0);
            comprobar(iniciar.isEnabled() && !pausar.isEnabled(), "El reloj está pausado");
            iniciar.doClick(0);
            comprobar(!iniciar.isEnabled() && pausar.isEnabled(), "El reloj se reanuda");
            detener.doClick(0);
            comprobar(iniciar.isEnabled() && !detener.isEnabled(), "Se puede preparar otra ejecución");
            iniciar.doClick(0);
            comprobar(!iniciar.isEnabled() && detener.isEnabled(), "Reinicia con un reloj nuevo");
            detener.doClick(0);
        } catch (Exception e) {
            throw new AssertionError("No se pudo probar la ventana con los JSON oficiales", e);
        } finally {
            ventana.dispose();
        }
    }

    private static JButton boton(JToolBar barra, String texto) {
        for (var componente : barra.getComponents()) {
            if (componente instanceof JButton boton && boton.getText().equals(texto)) {
                return boton;
            }
        }
        throw new AssertionError("No se encontró el botón " + texto);
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
