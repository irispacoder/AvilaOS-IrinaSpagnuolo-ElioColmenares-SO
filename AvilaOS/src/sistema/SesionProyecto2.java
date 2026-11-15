package sistema;

import avilaos.estructuras.ListaEnlazada;
import enums.TipoProceso;
import java.util.Objects;
import java.util.function.Consumer;
import modelo.Proceso;
import modelo.SolicitudIO;
import persistencia.CargadorJSON;
import persistencia.CargadorJSON.ArchivoDisco;
import persistencia.CargadorJSON.ArchivoProcesos;
import persistencia.CargadorJSON.ConfigProceso;
import persistencia.CargadorJSON.SistemaCargado;

/** Construye el computador y su disco local a partir de las dos configuraciones. */
public final class SesionProyecto2 {
    private final ClusterManager cluster;
    private final Computador computador;
    private final SistemaCargado sistemaArchivos;
    private final PlanificadorDisco planificador;
    private final ConfigProceso[] procesosIO;
    private volatile Consumer<SolicitudIO> alCompletarIO = solicitud -> {};

    public SesionProyecto2(ArchivoDisco archivoDisco, ArchivoProcesos archivoProcesos) {
        sistemaArchivos = CargadorJSON.crearSistema(archivoDisco, archivoProcesos.bloquesSwap());
        cluster = new ClusterManager(archivoProcesos.duracionCicloMs(), 0);
        computador = cluster.crearComputador("ÁvilaOS", archivoProcesos.ram(),
                archivoProcesos.politicaCPU(), archivoProcesos.quantum());
        planificador = new PlanificadorDisco(sistemaArchivos.disco(), archivoProcesos.politicaDisco(),
                sistemaArchivos.posicionesPorCiclo(), (solicitud, politica) -> {
                    cluster.registrarLog("Disco: %s de %s en bloque %d (%s) atendida".formatted(
                            solicitud.operacion(), solicitud.origen(), solicitud.bloque(), politica));
                    alCompletarIO.accept(solicitud);
                });
        cluster.conectarDisco(sistemaArchivos.disco());
        ListaEnlazada<ConfigProceso> pendientes = new ListaEnlazada<>();
        for (ConfigProceso config : archivoProcesos.procesos()) {
            if (config.operacion() == null) {
                cluster.agregarProceso(new Proceso(config.nombre(), TipoProceso.CPU_BOUND, config.prioridad(),
                        config.memoria(), config.deadline(), config.instrucciones()), computador.getIdComputador());
            } else {
                pendientes.agregarAlFinal(config);
            }
        }
        procesosIO = new ConfigProceso[pendientes.getTamano()];
        for (int i = 0; i < procesosIO.length; i++) {
            procesosIO[i] = pendientes.eliminarPrimero();
        }
    }

    public ClusterManager getCluster() { return cluster; }
    public Computador getComputador() { return computador; }
    public SistemaCargado getSistemaArchivos() { return sistemaArchivos; }
    public PlanificadorDisco getPlanificador() { return planificador; }
    public ConfigProceso[] obtenerProcesosIO() { return procesosIO.clone(); }

    /** El conector de E/S recibe las solicitudes completadas en el hilo del reloj. */
    public void alCompletarIO(Consumer<SolicitudIO> accion) {
        alCompletarIO = Objects.requireNonNull(accion);
    }
}
