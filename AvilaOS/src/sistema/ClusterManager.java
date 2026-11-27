/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package sistema;
import avilaos.estructuras.ListaEnlazada;
import enums.PoliticaPlanificacion;
import interfaces.IClockListener;
import modelo.Proceso;

/**
 *
 * @author ispam
 */
public class ClusterManager implements IClockListener{
    private final RelojGlobal relojGlobal;
    private final ListaEnlazada<Computador> computadores;
    private final ListaEnlazada<Buffer> buffers;
    private final ListaEnlazada<String> logEventosGlobal;
    private int latenciaRedCiclos;
    private volatile Disco discoLocal;
    
    private static final int MAX_LOGS_MEMORIA = 1000;
    private volatile int contadorTotalLogsGenerados;

    public ClusterManager(int duracionCicloMs, int latenciaRedCiclos) {
        this.relojGlobal = new RelojGlobal(duracionCicloMs);
        this.computadores = new ListaEnlazada<>();
        this.buffers = new ListaEnlazada<>();
        this.logEventosGlobal = new ListaEnlazada<>();
        this.latenciaRedCiclos = latenciaRedCiclos;
        this.contadorTotalLogsGenerados = 0;
        this.relojGlobal.setSuscriptorClusterManager(this);
    }

    public synchronized Computador crearComputador(String nombre, int ramTotal, PoliticaPlanificacion politicaInicial, int quantumInicial) {
        int id = computadores.getTamano() + 1;
        Computador comp = new Computador(id, nombre, ramTotal, politicaInicial, quantumInicial);
        comp.setClusterManager(this);
        computadores.agregarAlFinal(comp);
        relojGlobal.agregarComputador(comp);
        registrarLog("Sistema: Creado Computador " + id + " [" + nombre + "] con " + ramTotal + " MB de RAM.");
        return comp;
    }

    public synchronized Buffer crearBuffer(String idBuffer, int capacidad, int idComputadorAnfitrion) {
        Computador anfitrion = buscarComputadorPorId(idComputadorAnfitrion);
        if (anfitrion == null) {
            registrarLog("Error: No se pudo crear el buffer " + idBuffer + ". Computador anfitrión no existe.");
            return null;
        }
        if (!anfitrion.reservarMemoriaBuffer(capacidad)) {
            registrarLog("Error: Memoria insuficiente en Computador " + idComputadorAnfitrion + " para el buffer.");
            return null;
        }
        Buffer buffer = new Buffer(idBuffer, capacidad, idComputadorAnfitrion);
        buffers.agregarAlFinal(buffer);
        registrarLog("Sistema: Creado Buffer " + idBuffer + " (Capacidad: " + capacidad + ") en Computador " + idComputadorAnfitrion);
        return buffer;
    }

    public synchronized void agregarProceso(Proceso proceso, int idComputadorDestino) {
        Computador destino = null;
        if (idComputadorDestino > 0) {
            destino = buscarComputadorPorId(idComputadorDestino);
        } else {
            destino = seleccionarComputadorMenosCargado();
        }

        if (destino != null) {
            destino.agregarProcesoNuevo(proceso, relojGlobal.getCicloActual());
            registrarLog("Proceso " + proceso.getNombre() + " (ID: " + proceso.getId() + ") asignado a " + destino.getNombre());
        } else {
            registrarLog("Error: No se pudo asignar el proceso " + proceso.getNombre() + ". Sin computadores disponibles.");
        }
    }

    public synchronized void removerProcesoDeSemaforos(Proceso p) {
        if (p == null || buffers == null) return;
    
    for (int i = 0; i < buffers.getTamano(); i++) {
        Buffer b = buffers.obtener(i);
        if (b != null) {
            if (b.getMutex() != null) b.getMutex().eliminarProceso(p);
            if (b.getLleno() != null) b.getLleno().eliminarProceso(p);
            if (b.getVacio() != null) b.getVacio().eliminarProceso(p);
        }
    }
    }

    public void conectarDisco(Disco disco) {
        discoLocal = disco;
    }

    @Override
    public void onClockTick(int cicloActual) {
        Disco disco = discoLocal;
        if (disco != null) {
            disco.onClockTick(cicloActual);
        }
    }

    private Computador seleccionarComputadorMenosCargado() {
        if (computadores.estaVacia()) return null;
        Computador mejor = computadores.obtener(0);
        double menorPorcentajeRAM = (double) mejor.getRamUsada() / mejor.getRamTotal();
        for (int i = 1; i < computadores.getTamano(); i++) {
            Computador comp = computadores.obtener(i);
            double porcentajeRAM = (double) comp.getRamUsada() / comp.getRamTotal();
            if (porcentajeRAM < menorPorcentajeRAM) {
                menorPorcentajeRAM = porcentajeRAM;
                mejor = comp;
            }
        }
        return mejor;
    }

    public Computador buscarComputadorPorId(int id) {
        for (int i = 0; i < computadores.getTamano(); i++) {
            Computador comp = computadores.obtener(i);
            if (comp.getIdComputador() == id) return comp;
        }
        return null;
    }

    public Buffer buscarBufferPorId(String idBuffer) {
        if (idBuffer == null || buffers == null) return null;
    
    for (int i = 0; i < buffers.getTamano(); i++) {
        Buffer b = buffers.obtener(i);
        if (b != null && b.getIdBuffer() != null && b.getIdBuffer().equalsIgnoreCase(idBuffer)) {
            return b;
        }
    }
    return null;
    }

    public synchronized void registrarLog(String mensaje) {
        if (logEventosGlobal.getTamano() >= MAX_LOGS_MEMORIA) {
            logEventosGlobal.eliminarPrimero();
        }
        logEventosGlobal.agregarAlFinal(mensaje);
        contadorTotalLogsGenerados++;
    }

    public synchronized ListaEnlazada<String> obtenerNuevosLogs(int ultimoIndiceLeido) {
        ListaEnlazada<String> delta = new ListaEnlazada<>();
        int tamanoActual = logEventosGlobal.getTamano();
        
        if (ultimoIndiceLeido < tamanoActual) {
            for (int i = ultimoIndiceLeido; i < tamanoActual; i++) {
                delta.agregarAlFinal(logEventosGlobal.obtener(i));
            }
        }
        return delta;
    }

    public synchronized ListaEnlazada<String> obtenerUltimosLogs(int cantidad) {
        ListaEnlazada<String> ultimos = new ListaEnlazada<>();
        int tamanoActual = logEventosGlobal.getTamano();
        int inicio = Math.max(0, tamanoActual - cantidad);

        for (int i = inicio; i < tamanoActual; i++) {
            ultimos.agregarAlFinal(logEventosGlobal.obtener(i));
        }
        return ultimos;
    }

    public void iniciarSimulacion() { relojGlobal.iniciar(); }
    public void pausarSimulacion() { relojGlobal.pausar(); }
    public void detenerSimulacion() { relojGlobal.detener(); }
    
    public RelojGlobal getRelojGlobal() { return relojGlobal; }
    public ListaEnlazada<Computador> getComputadores() { return computadores; }
    public ListaEnlazada<Buffer> getBuffers() { return buffers; }
    public ListaEnlazada<String> getLogEventosGlobal() { return logEventosGlobal; }
    public int getContadorTotalLogsGenerados() { return contadorTotalLogsGenerados; }
    public int getLatenciaRedCiclos() { return latenciaRedCiclos; }
    public void setLatenciaRedCiclos(int latencia) { this.latenciaRedCiclos = latencia; }
}
