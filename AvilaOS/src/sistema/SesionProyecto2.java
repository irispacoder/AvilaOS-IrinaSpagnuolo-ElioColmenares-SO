package sistema;

import avilaos.estructuras.ListaEnlazada;
import enums.EstadoProcesos;
import enums.MotivoBloqueo;
import enums.OperacionIO;
import enums.TipoProceso;
import java.util.Objects;
import java.util.function.Consumer;
import modelo.Proceso;
import modelo.SolicitudIO;
import persistencia.CargadorJSON;
import persistencia.CargadorJSON.ArchivoDisco;
import persistencia.CargadorJSON.ArchivoProcesos;
import persistencia.CargadorJSON.ConfigProceso;
import persistencia.CargadorJSON.Permiso;
import persistencia.CargadorJSON.SistemaCargado;
import persistencia.CargadorJSON.TipoEntrada;

public final class SesionProyecto2 {
    private record VinculoIO(long idSolicitud, Proceso proceso) {}

    private final ClusterManager cluster;
    private final Computador computador;
    private final SistemaCargado sistemaArchivos;
    private final SistemaArchivos gestorArchivos;
    private final PlanificadorDisco planificador;
    private final ConfigProceso[] procesosIO;
    private boolean procesosIODespachados = false;
    private final ListaEnlazada<VinculoIO> solicitudesActivas = new ListaEnlazada<>();
    private volatile Consumer<SolicitudIO> alCompletarIO = solicitud -> {};

    public SesionProyecto2(ArchivoDisco archivoDisco, ArchivoProcesos archivoProcesos) {
        sistemaArchivos = CargadorJSON.crearSistema(archivoDisco, archivoProcesos.bloquesSwap());
        gestorArchivos = new SistemaArchivos(sistemaArchivos);
        cluster = new ClusterManager(archivoProcesos.duracionCicloMs(), 0);
        computador = cluster.crearComputador("ÁvilaOS", archivoProcesos.ram(),
                archivoProcesos.politicaCPU(), archivoProcesos.quantum());
        computador.setContextoOS(this);

        planificador = new PlanificadorDisco(sistemaArchivos.disco(), archivoProcesos.politicaDisco(),
                sistemaArchivos.posicionesPorCiclo(), (solicitud, aplicada) -> {
                    cluster.registrarLog("El disco atiende la solicitud de %s (%s) en el bloque %d (%s)".formatted(
                            solicitud.origen(), solicitud.operacion(), solicitud.bloque(), aplicada));
                    procesarFinSolicitudDisco(solicitud);
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

    /**
     * Despacha los procesos de E/S del JSON de forma concurrente antes del primer tic del disco,
     * permitiendo evaluar las políticas (FIFO, SSTF, SCAN, C-SCAN) con toda la cola en t=0.
     */
    public synchronized void despacharProcesosIOIniciales() {
        if (procesosIODespachados) return;
        procesosIODespachados = true;
        int cicloActual = cluster.getRelojGlobal().getCicloActual();
        for (ConfigProceso config : procesosIO) {
            Proceso pIO = new Proceso(config.nombre(), TipoProceso.IO_BOUND, config.prioridad(),
                    config.memoria(), config.deadline(), config.instrucciones());
            TipoEntrada tipoCrear = config.tamanoArchivo() > 0 ? TipoEntrada.FILE : TipoEntrada.DIRECTORY;
            pIO.configurarOperacionIO(config.operacion(), config.ruta(), tipoCrear,
                    config.tamanoArchivo(), Permiso.PUBLIC, "admin", null);
            cluster.agregarProceso(pIO, computador.getIdComputador());

            // Si fue admitido a RAM (LISTO), ejecuta su llamada al sistema de inmediato y encola su E/S
            if (pIO.getEstado() == EstadoProcesos.LISTO) {
                computador.getColaListos().eliminar(pIO);
                pIO.setCicloPrimerEjecucion(cicloActual);
                pIO.ejecutarCiclo();
                pIO.setSolicitudIOEnviada(true);
                pIO.setEstado(EstadoProcesos.BLOQUEADO);
                pIO.setMotivoBloqueo(MotivoBloqueo.OPERACION_IO);
                pIO.resetearTiempoBloqueado();
                computador.getColaBloqueados().agregarAlFinal(pIO);
                enviarSolicitudArchivoAlDisco(pIO);
            }
        }
    }

    public Proceso crearProcesoIO(OperacionIO operacion, String rutaObjetivo, TipoEntrada tipoCrear,
                                  int tamanoBloques, Permiso permiso, String nuevoNombre) {
        gestorArchivos.validarOperacion(operacion, rutaObjetivo, tipoCrear, tamanoBloques, nuevoNombre);
        String nombreProc = "IO_" + operacion.name() + "_" + (System.nanoTime() % 10000);
        Proceso p = new Proceso(nombreProc, TipoProceso.IO_BOUND, 1, 16, 1000, 2);
        p.configurarOperacionIO(operacion, rutaObjetivo, tipoCrear, tamanoBloques,
                permiso, gestorArchivos.getUsuarioActivo(), nuevoNombre);
        cluster.agregarProceso(p, computador.getIdComputador());
        cluster.registrarLog("Creado proceso de E/S " + p.getNombre() + " -> " + operacion + " sobre " + rutaObjetivo);
        return p;
    }

    public void enviarSolicitudArchivoAlDisco(Proceso proceso) {
        int[] bloques = gestorArchivos.obtenerBloquesObjetivo(
                proceso.getOperacionIO(), proceso.getRutaObjetivoIO(), proceso.getTamanoBloquesIO());
        SolicitudIO sol = planificador.encolar(bloques, proceso.getOperacionIO(), proceso.getNombre());
        synchronized (solicitudesActivas) {
            solicitudesActivas.agregarAlFinal(new VinculoIO(sol.id(), proceso));
        }
        cluster.registrarLog("Proceso " + proceso.getNombre() + " bloqueado por E/S; solicitud encolada en disco (bloque " + bloques[0] + ").");
    }

    public void enviarSolicitudSwapAlDisco(Proceso proceso, OperacionIO tipoSwap, int[] bloquesSwap) {
        int[] destino = (bloquesSwap != null && bloquesSwap.length > 0)
                ? bloquesSwap : new int[]{sistemaArchivos.disco().getPosicionCabezal()};
        SolicitudIO sol = planificador.encolar(destino, tipoSwap, proceso.getNombre());
        synchronized (solicitudesActivas) {
            solicitudesActivas.agregarAlFinal(new VinculoIO(sol.id(), proceso));
        }
    }

    private void procesarFinSolicitudDisco(SolicitudIO solicitud) {
        Proceso p = extraerProcesoVinculado(solicitud.id());
        if (p == null) return;

        if (solicitud.operacion() == OperacionIO.SUSPEND) {
            p.setSuspendiendoEnDisco(false);
            if (!p.esProcesoDeArchivo() || p.isOperacionIOCompletada()
                    || p.getMotivoBloqueo() == MotivoBloqueo.SWAPPING) {
                computador.pasarBloqueadoSuspendidoAListoSuspendido(p);
            }
            return;
        }
        if (solicitud.operacion() == OperacionIO.REACTIVATE) {
            computador.completarReactivacionProceso(p);
            return;
        }

        try {
            gestorArchivos.aplicarOperacionCompletada(
                    p.getOperacionIO(), p.getRutaObjetivoIO(), p.getTipoEntradaCrear(),
                    p.getTamanoBloquesIO(), p.getPermisoCrear(), p.getDuenoCrear(),
                    p.getNuevoNombreIO(), p.getNombre());
            p.setOperacionIOCompletada(true);
        } catch (Exception ex) {
            cluster.registrarLog("Error al aplicar E/S de " + p.getNombre() + ": " + ex.getMessage());
        }

        if (p.getEstado() == EstadoProcesos.BLOQUEADO_SUSPENDIDO) {
            if (!p.isSuspendiendoEnDisco()) {
                computador.pasarBloqueadoSuspendidoAListoSuspendido(p);
            }
        } else {
            computador.reinsertarProcesoListo(p);
        }
    }

    private Proceso extraerProcesoVinculado(long idSolicitud) {
        synchronized (solicitudesActivas) {
            for (int i = 0; i < solicitudesActivas.getTamano(); i++) {
                VinculoIO v = solicitudesActivas.obtener(i);
                if (v.idSolicitud() == idSolicitud) {
                    solicitudesActivas.eliminar(v);
                    return v.proceso();
                }
            }
        }
        return null;
    }

    public ClusterManager getCluster() { return cluster; }
    public Computador getComputador() { return computador; }
    public SistemaCargado getSistemaArchivos() { return sistemaArchivos; }
    public SistemaArchivos getGestorArchivos() { return gestorArchivos; }
    public PlanificadorDisco getPlanificador() { return planificador; }
    public ConfigProceso[] obtenerProcesosIO() { return procesosIO.clone(); }

    public void alCompletarIO(Consumer<SolicitudIO> accion) {
        alCompletarIO = Objects.requireNonNull(accion);
    }
}