package sistema;

import enums.EstadoProcesos;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import persistencia.CargadorJSON;

public final class DeadlineIOPrueba {
    public static void main(String[] args) throws Exception {
        Path ejemplos = Path.of("AvilaOS/ejemplos");
        SesionProyecto2 sesion = new SesionProyecto2(
                CargadorJSON.leerSistemaArchivos(ejemplos.resolve("sistema-archivos.json")),
                CargadorJSON.leerProcesos(ejemplos.resolve("procesos.json")));
        sesion.despacharProcesosIOIniciales();
        Computador computador = sesion.getComputador();
        computador.iniciarHilo();
        try {
            for (int ciclo = 1; ciclo <= 700; ciclo++) {
                CountDownLatch fin = new CountDownLatch(1);
                computador.dispararCicloParalelo(ciclo, fin);
                if (!fin.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Ciclo de CPU detenido");
                }
                sesion.getCluster().onClockTick(ciclo);
                if (ciclo == 500) {
                    comprobar(computador.getColaTerminados().getTamano() == 8,
                            "Todos deben haber terminado o expirado en el ciclo 500");
                }
            }
            comprobar(computador.getColaTerminados().getTamano() == 8,
                    "Una finalización tardía no debe duplicar la terminación");
            computador.getColaTerminados().paraCadaElemento(p ->
                    comprobar(p.getEstado() == EstadoProcesos.TERMINADO, "El proceso debe seguir terminado"));
            comprobar(computador.getColaListos().estaVacia(), "No debe reinsertar procesos vencidos");
            comprobar(computador.getRamUsada() == 0, "La RAM debe quedar liberada");
            comprobar(sesion.getPlanificador().obtenerMetricas().solicitudesAtendidas() == 8,
                    "Las ocho solicitudes físicas deben completarse");
            comprobar(sesion.getPlanificador().obtenerMetricas().desplazamiento() == 644,
                    "FIFO debe conservar su recorrido");
            System.out.println("Deadline E/S: P1 conserva ocho terminaciones únicas y 644 bloques");
        } finally {
            computador.detenerHilo();
        }
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) throw new AssertionError(mensaje);
    }
}
