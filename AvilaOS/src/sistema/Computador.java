/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package sistema;
import avilaos.estructuras.Cola;
import avilaos.estructuras.ListaEnlazada;
import enums.EstadoProcesos;
import enums.ModoCPU;
import enums.MotivoBloqueo;
import enums.OperacionIO;
import enums.PasoBuffer;
import enums.PoliticaPlanificacion;
import enums.TipoProceso;
import interfaces.IClockListener;
import interfaces.IScheduler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import modelo.MetricasComputador;
import modelo.Proceso;
import planificadores.EDFScheduler;
import planificadores.FCFSScheduler;
import planificadores.PrioridadesScheduler;
import planificadores.RRScheduler;
import sistema.Buffer.ResultadoPasoBuffer;
import sistema.Disco.Asignacion;


/**
 *
 * @author ispam
 */
public class Computador implements Runnable, IClockListener{
    private final int idComputador;
    private final String nombre;
    private final int ramTotal;
    private int ramUsada;
    private Proceso procesoEnCPU;
    private ModoCPU modoCPU;
    private IScheduler planificadorActual;
    private PoliticaPlanificacion politicaActual;
    private int quantumConfigurado;
    private int quantumRestante;

    private final Cola<Proceso> colaNuevos;
    private final ListaEnlazada<Proceso> colaListos;
    private final ListaEnlazada<Proceso> colaBloqueados;
    private final ListaEnlazada<Proceso> colaListosSuspendidos;
    private final ListaEnlazada<Proceso> colaBloqueadosSuspendidos;
    private final ListaEnlazada<Proceso> colaTerminados;
    private final MetricasComputador metricas;

    private int totalSuspensiones;
    private int totalReactivaciones;

    private Thread hiloComputador;
    private volatile boolean ejecucionActiva;
    private final Semaphore semaforoPasoCiclo;
    private CountDownLatch latchSincronizacionReloj;
    private int cicloActualPaso;

    private int duracionBloqueoIOCiclos;
    private ClusterManager clusterManager;
    private SesionProyecto2 contextoOS;

    public Computador(int idComputador, String nombre, int ramTotal, PoliticaPlanificacion politicaInicial, int quantumInicial) {
        this.idComputador = idComputador;
        this.nombre = nombre;
        this.ramTotal = ramTotal;
        this.ramUsada = 0;
        this.procesoEnCPU = null;
        this.modoCPU = ModoCPU.SISTEMA_OPERATIVO;
        this.quantumConfigurado = quantumInicial;
        this.quantumRestante = quantumInicial;
        this.colaNuevos = new Cola<>();
        this.colaListos = new ListaEnlazada<>();
        this.colaBloqueados = new ListaEnlazada<>();
        this.colaListosSuspendidos = new ListaEnlazada<>();
        this.colaBloqueadosSuspendidos = new ListaEnlazada<>();
        this.colaTerminados = new ListaEnlazada<>();
        this.metricas = new MetricasComputador();
        this.totalSuspensiones = 0;
        this.totalReactivaciones = 0;
        this.duracionBloqueoIOCiclos = 3;
        this.semaforoPasoCiclo = new Semaphore(0);
        this.ejecucionActiva = false;
        this.setPoliticaPlanificacion(politicaInicial);
    }

    public void setClusterManager(ClusterManager cm) {
        this.clusterManager = cm;
    }

    public void setContextoOS(SesionProyecto2 contextoOS) {
        this.contextoOS = contextoOS;
    }

    public synchronized void setModoCPU(ModoCPU modo) {
        this.modoCPU = modo;
    }

    public synchronized void iniciarHilo() {
        if (!ejecucionActiva) {
            ejecucionActiva = true;
            hiloComputador = new Thread(this, "Computador-Thread-" + idComputador);
            hiloComputador.start();
        }
    }

    public synchronized void detenerHilo() {
        ejecucionActiva = false;
        if (hiloComputador != null) {
            hiloComputador.interrupt();
        }
    }

    public void dispararCicloParalelo(int cicloActual, CountDownLatch latch) {
        this.latchSincronizacionReloj = latch;
        onClockTick(cicloActual);
    }

    @Override
    public void onClockTick(int cicloActual) {
        this.cicloActualPaso = cicloActual;
        this.semaforoPasoCiclo.release();
    }

    @Override
    public void run() {
        while (ejecucionActiva) {
            try {
                semaforoPasoCiclo.acquire();
                if (!ejecucionActiva) break;
                ejecutarCicloComputador(cicloActualPaso);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } finally {
                if (latchSincronizacionReloj != null) {
                    latchSincronizacionReloj.countDown();
                }
            }
        }
    }

    private synchronized void ejecutarCicloComputador(int cicloActual) {
        admitirProcesosNuevos();
        intentarReactivarSuspendidos();
        actualizarEstadoProcesos(cicloActual);
        limpiarProcesosExpirados(cicloActual);
        evaluarDesbloqueosIO();
        evaluarPlanificacion(cicloActual);
        ejecutarCicloCPU(cicloActual);
        metricas.registrarCiclo(procesoEnCPU != null);
    }

    public synchronized void setPoliticaPlanificacion(PoliticaPlanificacion politica) {
        this.politicaActual = politica;
        switch (politica) {
            case ROUND_ROBIN -> this.planificadorActual = new RRScheduler();
            case EDF -> this.planificadorActual = new EDFScheduler();
            case PRIORIDADES -> this.planificadorActual = new PrioridadesScheduler();
            case FCFS -> this.planificadorActual = new FCFSScheduler();
            default -> this.planificadorActual = new FCFSScheduler();
        }
    }

    public synchronized void agregarProcesoNuevo(Proceso proceso, int cicloActual) {
        proceso.setComputadorAsignado(this.idComputador);
        proceso.setCicloLlegada(cicloActual);
        proceso.setEstado(EstadoProcesos.NUEVO);
        colaNuevos.encolar(proceso);
        admitirProcesosNuevos();
    }

    public synchronized boolean reservarMemoriaBuffer(int capacidad) {
        if (ramUsada + capacidad <= ramTotal) {
            ramUsada += capacidad;
            return true;
        }
        return false;
    }

    /**
     * Planificador a largo y mediano plazo:
     * Si no hay RAM para un proceso NUEVO, suspende un proceso BLOQUEADO hacia el área Swap del disco.
     */
    public synchronized void admitirProcesosNuevos() {
        while (!colaNuevos.estaVacia()) {
            Proceso candidato = colaNuevos.frente();
            if (ramUsada + candidato.getMemoriaRequerida() <= ramTotal) {
                colaNuevos.desencolar();
                candidato.setMemoriaAsignada(true);
                ramUsada += candidato.getMemoriaRequerida();
                candidato.setEstado(EstadoProcesos.LISTO);
                colaListos.agregarAlFinal(candidato);
            } else {
                if (!suspenderBloqueadoParaHacerEspacio(candidato)) {
                    break;
                }
            }
        }
    }

    private boolean suspenderBloqueadoParaHacerEspacio(Proceso procesoPorAdmitir) {
        if (contextoOS == null || colaBloqueados.estaVacia()) {
            return false;
        }
        // Criterio de selección: proceso BLOQUEADO con mayor tiempo bloqueado
        Proceso victima = null;
        int mayorTiempo = -1;
        for (int i = 0; i < colaBloqueados.getTamano(); i++) {
            Proceso p = colaBloqueados.obtener(i);
            if (p.isMemoriaAsignada() && p.getTiempoBloqueado() > mayorTiempo) {
                mayorTiempo = p.getTiempoBloqueado();
                victima = p;
            }
        }
        if (victima == null) {
            return false;
        }

        Disco disco = contextoOS.getSistemaArchivos().disco();
        Asignacion asigSwap = disco.reservarSwap(victima.getMemoriaRequerida(), victima.getNombre());
        if (asigSwap == null) {
            return false;
        }

        int[] bloquesSwap = disco.obtenerBloques(asigSwap);
        colaBloqueados.eliminar(victima);
        ramUsada -= victima.getMemoriaRequerida();
        victima.setMemoriaAsignada(false);
        victima.setAsignacionSwap(asigSwap);
        victima.setSuspendiendoEnDisco(true);
        victima.setEstado(EstadoProcesos.BLOQUEADO_SUSPENDIDO);
        colaBloqueadosSuspendidos.agregarAlFinal(victima);
        totalSuspensiones++;

        registrarLogLocal("Se suspende al proceso " + victima.getNombre()
                + " para admitir a " + procesoPorAdmitir.getNombre()
                + " (Swap bloques: " + bloquesSwap.length + ")");
        contextoOS.enviarSolicitudSwapAlDisco(victima, OperacionIO.SUSPEND, bloquesSwap);
        return true;
    }

    /** Revisa si hay memoria RAM libre para reactivar procesos en LISTO_SUSPENDIDO. */
    public synchronized void intentarReactivarSuspendidos() {
        if (contextoOS == null || colaListosSuspendidos.estaVacia()) {
            return;
        }
        Proceso candidato = null;
        for (int i = 0; i < colaListosSuspendidos.getTamano(); i++) {
            Proceso p = colaListosSuspendidos.obtener(i);
            if (!p.isMemoriaAsignada() && !p.isSuspendiendoEnDisco()
                    && ramUsada + p.getMemoriaRequerida() <= ramTotal) {
                candidato = p;
                break;
            }
        }
        if (candidato == null) {
            return;
        }

        colaListosSuspendidos.eliminar(candidato);
        ramUsada += candidato.getMemoriaRequerida();
        candidato.setMemoriaAsignada(true);

        Disco disco = contextoOS.getSistemaArchivos().disco();
        int[] bloquesSwap = null;
        if (candidato.getAsignacionSwap() != null) {
            bloquesSwap = disco.obtenerBloques(candidato.getAsignacionSwap());
            disco.liberar(candidato.getAsignacionSwap());
            candidato.setAsignacionSwap(null);
        }
        totalReactivaciones++;
        registrarLogLocal("Planificador mediano plazo: Reactivando proceso " + candidato.getNombre() + " desde Swap a RAM.");
        contextoOS.enviarSolicitudSwapAlDisco(candidato, OperacionIO.REACTIVATE, bloquesSwap);
    }

    public synchronized void pasarBloqueadoSuspendidoAListoSuspendido(Proceso proceso) {
        if (proceso != null && colaBloqueadosSuspendidos.eliminar(proceso)) {
            proceso.setEstado(EstadoProcesos.LISTO_SUSPENDIDO);
            proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            proceso.resetearTiempoBloqueado();
            colaListosSuspendidos.agregarAlFinal(proceso);
            registrarLogLocal("Proceso " + proceso.getNombre() + " pasó de BLOQUEADO/SUSPENDIDO a LISTO/SUSPENDIDO.");
            intentarReactivarSuspendidos();
        }
    }

    public synchronized void completarReactivacionProceso(Proceso proceso) {
        if (proceso != null && proceso.getEstado() != EstadoProcesos.TERMINADO) {
            proceso.setEstado(EstadoProcesos.LISTO);
            proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
            proceso.resetearTiempoBloqueado();
            colaListos.agregarAlFinal(proceso);
        }
    }

    private void actualizarEstadoProcesos(int cicloActual) {
        colaListos.paraCadaElemento(Proceso::incrementarTiempoEsperaListos);
        if (procesoEnCPU != null) {
            procesoEnCPU.decrementarDeadline();
        }
        colaListos.paraCadaElemento(Proceso::decrementarDeadline);
        colaBloqueados.paraCadaElemento(Proceso::decrementarDeadline);
        colaListosSuspendidos.paraCadaElemento(Proceso::decrementarDeadline);
        colaBloqueadosSuspendidos.paraCadaElemento(Proceso::decrementarDeadline);

        colaBloqueados.paraCadaElemento(p -> {
            if (p.getMotivoBloqueo() == MotivoBloqueo.ESPERA_SEMAFORO) {
                p.incrementarTiempoBloqueadoSemaforo();
            } else if (p.getMotivoBloqueo() == MotivoBloqueo.LATENCIA_RED) {
                p.incrementarCiclosLatenciaPendientes();
            } else {
                p.incrementarTiempoBloqueado();
            }
        });
        colaBloqueadosSuspendidos.paraCadaElemento(Proceso::incrementarTiempoBloqueado);
    }

    private void limpiarProcesosExpirados(int cicloActual) {
        ListaEnlazada<Proceso> expListos = new ListaEnlazada<>();
        ListaEnlazada<Proceso> expBloq = new ListaEnlazada<>();
        ListaEnlazada<Proceso> expListosSusp = new ListaEnlazada<>();
        ListaEnlazada<Proceso> expBloqSusp = new ListaEnlazada<>();

        colaListos.paraCadaElemento(p -> { if (p.haExpiradoDeadline()) expListos.agregarAlFinal(p); });
        colaBloqueados.paraCadaElemento(p -> { if (p.haExpiradoDeadline()) expBloq.agregarAlFinal(p); });
        colaListosSuspendidos.paraCadaElemento(p -> { if (p.haExpiradoDeadline()) expListosSusp.agregarAlFinal(p); });
        colaBloqueadosSuspendidos.paraCadaElemento(p -> { if (p.haExpiradoDeadline()) expBloqSusp.agregarAlFinal(p); });

        expListos.paraCadaElemento(p -> {
            colaListos.eliminar(p);
            registrarLogLocal("Ciclo " + cicloActual + ": [EXPULSIÓN POR DEADLINE] Proceso " + p.getNombre() + " expiró en LISTOS.");
            finalizarProcesoExpirado(p, cicloActual);
        });
        expBloq.paraCadaElemento(p -> {
            colaBloqueados.eliminar(p);
            registrarLogLocal("Ciclo " + cicloActual + ": [EXPULSIÓN POR DEADLINE] Proceso " + p.getNombre() + " expiró en BLOQUEADOS.");
            finalizarProcesoExpirado(p, cicloActual);
        });
        expListosSusp.paraCadaElemento(p -> {
            colaListosSuspendidos.eliminar(p);
            registrarLogLocal("Ciclo " + cicloActual + ": [EXPULSIÓN POR DEADLINE] Proceso " + p.getNombre() + " expiró en LISTO/SUSPENDIDO.");
            finalizarProcesoExpirado(p, cicloActual);
        });
        expBloqSusp.paraCadaElemento(p -> {
            colaBloqueadosSuspendidos.eliminar(p);
            registrarLogLocal("Ciclo " + cicloActual + ": [EXPULSIÓN POR DEADLINE] Proceso " + p.getNombre() + " expiró en BLOQUEADO/SUSPENDIDO.");
            finalizarProcesoExpirado(p, cicloActual);
        });
    }

    private void finalizarProcesoExpirado(Proceso p, int cicloActual) {
        p.setEstado(EstadoProcesos.TERMINADO);
        if (p.isMemoriaAsignada()) {
            ramUsada -= p.getMemoriaRequerida();
            p.setMemoriaAsignada(false);
        }
        if (p.getAsignacionSwap() != null && contextoOS != null) {
            contextoOS.getSistemaArchivos().disco().liberar(p.getAsignacionSwap());
            p.setAsignacionSwap(null);
        }
        if (clusterManager != null) {
            clusterManager.removerProcesoDeSemaforos(p);
        }
        metricas.registrarProcesoFinalizado(p, cicloActual);
        colaTerminados.agregarAlFinal(p);
        admitirProcesosNuevos();
        intentarReactivarSuspendidos();
    }

    private void evaluarDesbloqueosIO() {
        ListaEnlazada<Proceso> desbloqueados = new ListaEnlazada<>();

        colaBloqueados.paraCadaElemento(p -> {
            if (p.getMotivoBloqueo() == MotivoBloqueo.OPERACION_IO && !p.esProcesoDeArchivo()) {
                if (p.getTiempoBloqueado() >= duracionBloqueoIOCiclos) {
                    desbloqueados.agregarAlFinal(p);
                }
            } else if (p.getMotivoBloqueo() == MotivoBloqueo.LATENCIA_RED && clusterManager != null) {
                if (p.haCumplidoLatenciaRed(clusterManager.getLatenciaRedCiclos())) {
                    desbloqueados.agregarAlFinal(p);
                }
            }
        });

        desbloqueados.paraCadaElemento(this::reinsertarProcesoListo);
    }

    private void evaluarPlanificacion(int cicloActual) {
        if (procesoEnCPU != null && planificadorActual.debeDesalojar(procesoEnCPU, colaListos, quantumRestante, cicloActual)) {
            procesoEnCPU.setEstado(EstadoProcesos.LISTO);
            colaListos.agregarAlFinal(procesoEnCPU);
            procesoEnCPU = null;
            quantumRestante = quantumConfigurado;
        }

        if (procesoEnCPU == null && !colaListos.estaVacia()) {
            procesoEnCPU = planificadorActual.seleccionarSiguiente(colaListos, procesoEnCPU, quantumRestante, cicloActual);
            if (procesoEnCPU != null) {
                procesoEnCPU.setEstado(EstadoProcesos.EJECUCION);
                quantumRestante = quantumConfigurado;
            }
        } else if (procesoEnCPU != null && quantumRestante <= 0) {
            quantumRestante = quantumConfigurado;
        }
    }

    private void ejecutarCicloCPU(int cicloActual) {
        if (procesoEnCPU != null) {
            procesoEnCPU.setCicloPrimerEjecucion(cicloActual);

            if (procesoEnCPU.esProcesoDeArchivo() && !procesoEnCPU.isSolicitudIOEnviada() && contextoOS != null) {
                modoCPU = ModoCPU.SISTEMA_OPERATIVO;
                procesoEnCPU.ejecutarCiclo();
                quantumRestante--;
                procesoEnCPU.setSolicitudIOEnviada(true);
                Proceso pIO = procesoEnCPU;
                desalojarProcesoPorBloqueo(MotivoBloqueo.OPERACION_IO);
                contextoOS.enviarSolicitudArchivoAlDisco(pIO);
                return;
            }

            if (procesoEnCPU.leTocaTocarBuffer() && clusterManager != null) {
                Buffer target = clusterManager.buscarBufferPorId(procesoEnCPU.getIdBuffer());
                if (target != null) {
                    boolean esRemoto = target.esAccesoRemoto(procesoEnCPU);
                    if (esRemoto && !procesoEnCPU.haCumplidoLatenciaRed(clusterManager.getLatenciaRedCiclos())) {
                        String nombreProc = procesoEnCPU.getNombre();
                        desalojarProcesoPorBloqueo(MotivoBloqueo.LATENCIA_RED);
                        registrarLogLocal("Ciclo " + cicloActual + ": Proceso " + nombreProc + " desalojado a BLOQUEADO por Latencia de Red.");
                        return;
                    }

                    modoCPU = ModoCPU.SISTEMA_OPERATIVO;
                    procesoEnCPU.ejecutarCiclo();
                    quantumRestante--;

                    ResultadoPasoBuffer resPaso = target.ejecutarCicloPasoBuffer(procesoEnCPU, cicloActual);
                    registrarLogLocal("Ciclo " + cicloActual + ": [MODO SO] " + resPaso.getMensajeLog());

                    if (resPaso.getProcesoDesbloqueado() != null) {
                        Proceso desb = resPaso.getProcesoDesbloqueado();
                        if (desb.getPasoBufferActual() == PasoBuffer.WAIT_CONTEO) {
                            desb.setPasoBufferActual(PasoBuffer.WAIT_MUTEX);
                        } else if (desb.getPasoBufferActual() == PasoBuffer.WAIT_MUTEX) {
                            desb.setPasoBufferActual(PasoBuffer.OPERACION);
                        }
                        Computador compDueno = clusterManager.buscarComputadorPorId(desb.getComputadorAsignado());
                        if (compDueno != null) {
                            compDueno.reinsertarProcesoListo(desb);
                        }
                    }

                    if (resPaso.isQuedoBloqueado()) {
                        procesoEnCPU.setEstado(EstadoProcesos.BLOQUEADO);
                        procesoEnCPU.setMotivoBloqueo(MotivoBloqueo.ESPERA_SEMAFORO);
                        colaBloqueados.agregarAlFinal(procesoEnCPU);
                        procesoEnCPU = null;
                        return;
                    }

                    evaluarFinalizacionOExpiracion(cicloActual);
                    return;
                }
            }

            modoCPU = procesoEnCPU.esProcesoDeArchivo() ? ModoCPU.SISTEMA_OPERATIVO : ModoCPU.USUARIO;
            procesoEnCPU.ejecutarCiclo();
            quantumRestante--;
            evaluarFinalizacionOExpiracion(cicloActual);
        } else {
            modoCPU = ModoCPU.SISTEMA_OPERATIVO;
        }
    }

    private void evaluarFinalizacionOExpiracion(int cicloActual) {
        if (procesoEnCPU == null) return;
        boolean completoElementos = (procesoEnCPU.getTipo() == TipoProceso.PRODUCTOR || procesoEnCPU.getTipo() == TipoProceso.CONSUMIDOR)
                && procesoEnCPU.getElementosProcesados() >= procesoEnCPU.getElementosRequeridos()
                && procesoEnCPU.getPasoBufferActual() == PasoBuffer.NINGUNO;

        if (procesoEnCPU.haExpiradoDeadline()) {
            Proceso exp = procesoEnCPU;
            procesoEnCPU = null;
            registrarLogLocal("Ciclo " + cicloActual + ": [EXPULSIÓN EN CPU] Proceso " + exp.getNombre() + " superó su deadline.");
            finalizarProcesoExpirado(exp, cicloActual);
        } else if (procesoEnCPU.getTiempoRestante() <= 0 || completoElementos) {
            finalizarProcesoNormal(cicloActual);
        } else if (procesoEnCPU.requiereIO()) {
            desalojarProcesoPorBloqueo(MotivoBloqueo.OPERACION_IO);
        }
    }

    private void finalizarProcesoNormal(int cicloActual) {
        procesoEnCPU.setEstado(EstadoProcesos.TERMINADO);
        if (procesoEnCPU.isMemoriaAsignada()) {
            ramUsada -= procesoEnCPU.getMemoriaRequerida();
            procesoEnCPU.setMemoriaAsignada(false);
        }
        if (clusterManager != null) {
            clusterManager.removerProcesoDeSemaforos(procesoEnCPU);
        }
        metricas.registrarProcesoFinalizado(procesoEnCPU, cicloActual);
        colaTerminados.agregarAlFinal(procesoEnCPU);
        procesoEnCPU = null;
        admitirProcesosNuevos();
        intentarReactivarSuspendidos();
    }

    public synchronized void desalojarProcesoPorBloqueo(MotivoBloqueo motivo) {
        if (procesoEnCPU != null) {
            procesoEnCPU.setEstado(EstadoProcesos.BLOQUEADO);
            procesoEnCPU.setMotivoBloqueo(motivo);
            procesoEnCPU.resetearTiempoBloqueado();
            colaBloqueados.agregarAlFinal(procesoEnCPU);
            procesoEnCPU = null;
        }
    }

    public synchronized void reinsertarProcesoListo(Proceso proceso) {
        if (proceso == null) return;
        if (proceso.getEstado() == EstadoProcesos.BLOQUEADO_SUSPENDIDO) {
            if (!proceso.isSuspendiendoEnDisco()) {
                pasarBloqueadoSuspendidoAListoSuspendido(proceso);
            }
            return;
        }
        colaBloqueados.eliminar(proceso);
        proceso.setEstado(EstadoProcesos.LISTO);
        proceso.setMotivoBloqueo(MotivoBloqueo.NINGUNO);
        proceso.resetearTiempoBloqueado();
        colaListos.agregarAlFinal(proceso);
    }

    private void registrarLogLocal(String msj) {
        if (clusterManager != null) {
            clusterManager.registrarLog(msj);
        }
    }

    public int getDuracionBloqueoIOCiclos() { return duracionBloqueoIOCiclos; }
    public void setDuracionBloqueoIOCiclos(int duracion) { this.duracionBloqueoIOCiclos = duracion; }
    public int getIdComputador() { return idComputador; }
    public String getNombre() { return nombre; }
    public int getRamTotal() { return ramTotal; }
    public synchronized int getRamUsada() { return ramUsada; }
    public synchronized int getRamLibre() { return ramTotal - ramUsada; }
    public synchronized Proceso getProcesoEnCPU() { return procesoEnCPU; }
    public synchronized ModoCPU getModoCPU() { return modoCPU; }
    public PoliticaPlanificacion getPoliticaActual() { return politicaActual; }
    public int getQuantumConfigurado() { return quantumConfigurado; }
    public void setQuantumConfigurado(int quantum) { this.quantumConfigurado = quantum; }
    public Cola<Proceso> getColaNuevos() { return colaNuevos; }
    public ListaEnlazada<Proceso> getColaListos() { return colaListos; }
    public ListaEnlazada<Proceso> getColaBloqueados() { return colaBloqueados; }
    public ListaEnlazada<Proceso> getColaListosSuspendidos() { return colaListosSuspendidos; }
    public ListaEnlazada<Proceso> getColaBloqueadosSuspendidos() { return colaBloqueadosSuspendidos; }
    public ListaEnlazada<Proceso> getColaTerminados() { return colaTerminados; }
    public synchronized int getTotalSuspensiones() { return totalSuspensiones; }
    public synchronized int getTotalReactivaciones() { return totalReactivaciones; }
    public MetricasComputador getMetricas() { return metricas; }
}
