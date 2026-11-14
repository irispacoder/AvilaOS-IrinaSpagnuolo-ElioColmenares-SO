package sistema;

import avilaos.estructuras.ListaEnlazada;
import enums.OperacionIO;
import interfaces.IDiskScheduler;
import interfaces.IDiskScheduler.Movimiento;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import modelo.SolicitudIO;
import sistema.Disco.Direccion;

/** Ejecuta una solicitud a la vez. Buscar y transferir un bloque son pasos distintos. */
public final class PlanificadorDisco {
    private final Disco disco;
    private final int velocidad;
    private final BiConsumer<SolicitudIO, IDiskScheduler> alAtender;
    private final Semaphore acceso = new Semaphore(1, true);
    private final ListaEnlazada<SolicitudIO> pendientes = new ListaEnlazada<>();
    private final ListaEnlazada<ConteoPolitica> conteos = new ListaEnlazada<>();
    private IDiskScheduler politica;
    private IDiskScheduler politicaEnCurso;
    private Direccion barrido;
    private Movimiento movimiento;
    private SolicitudIO activa;
    private int bloqueEnCurso;
    private long esperaActiva;
    private long ciclo;
    private long ultimoId;
    private long desplazamiento;
    private long atendidas;
    private long esperaAcumulada;

    public PlanificadorDisco(Disco disco, IDiskScheduler politica) {
        this(disco, politica, 1, (solicitud, aplicada) -> {});
    }

    public PlanificadorDisco(Disco disco, IDiskScheduler politica, int velocidad,
            BiConsumer<SolicitudIO, IDiskScheduler> alAtender) {
        if (velocidad <= 0) {
            throw new IllegalArgumentException("La velocidad del cabezal debe ser positiva");
        }
        this.disco = Objects.requireNonNull(disco);
        this.politica = Objects.requireNonNull(politica);
        this.velocidad = velocidad;
        this.alAtender = Objects.requireNonNull(alAtender);
        barrido = disco.getDireccion();
        disco.conectarPlanificador(this);
    }

    public void cambiarPolitica(IDiskScheduler nueva) {
        acceso.acquireUninterruptibly();
        try {
            politica = Objects.requireNonNull(nueva);
        } finally {
            acceso.release();
        }
    }

    public SolicitudIO encolar(int bloque) {
        return encolar(new int[]{bloque}, OperacionIO.READ, "Prueba");
    }

    public SolicitudIO encolar(int[] bloques, OperacionIO operacion, String origen) {
        acceso.acquireUninterruptibly();
        try {
            SolicitudIO solicitud = new SolicitudIO(ultimoId + 1, bloques, ciclo,
                    Objects.requireNonNull(operacion), Objects.requireNonNull(origen));
            if (solicitud.cantidadBloques() == 0) {
                throw new IllegalArgumentException("La solicitud debe indicar al menos un bloque");
            }
            for (int bloque : solicitud.bloques()) {
                if (bloque < 0 || bloque >= disco.getCantidadBloques()) {
                    throw new IllegalArgumentException("Bloque fuera del disco: " + bloque);
                }
            }
            ultimoId++;
            pendientes.agregarAlFinal(solicitud);
            return solicitud;
        } finally {
            acceso.release();
        }
    }

    /** Devuelve la solicitud que terminó en este ciclo, o null si sigue en curso. */
    public SolicitudIO avanzarCiclo() {
        SolicitudIO completada;
        IDiskScheduler aplicada;
        acceso.acquireUninterruptibly();
        try {
            ciclo++;
            completada = ejecutarTrabajo();
            aplicada = politicaEnCurso;
        } finally {
            acceso.release();
        }
        if (completada != null) {
            alAtender.accept(completada, aplicada);
        }
        return completada;
    }

    private SolicitudIO ejecutarTrabajo() {
        if (activa != null) {
            return transferirOMover();
        }
        if (movimiento == null && pendientes.estaVacia()) {
            return null;
        }
        if (movimiento == null) {
            movimiento = elegirMovimiento();
            if (movimiento.salto()) {
                disco.moverCabezalA(movimiento.destino(), barrido);
                movimiento = elegirMovimiento();
            }
        }
        if (movimiento.solicitud() != null) {
            activa = movimiento.solicitud();
            pendientes.eliminar(activa);
            politicaEnCurso = politica;
            bloqueEnCurso = 0;
            esperaActiva = ciclo - 1 - activa.cicloLlegada();
            barrido = movimiento.barridoSiguiente();
            movimiento = null;
            return transferirOMover();
        }
        moverHacia(movimiento.destino());
        if (disco.getPosicionCabezal() == movimiento.destino()) {
            barrido = movimiento.barridoSiguiente();
            movimiento = null;
        }
        return null;
    }

    private Movimiento elegirMovimiento() {
        return politica.elegir(copiarPendientes(), disco.getPosicionCabezal(), disco.getCantidadBloques() - 1, barrido);
    }

    private SolicitudIO transferirOMover() {
        int destino = activa.bloque(bloqueEnCurso);
        if (disco.getPosicionCabezal() != destino) {
            moverHacia(destino);
            return null;
        }

        bloqueEnCurso++;
        if (bloqueEnCurso < activa.cantidadBloques()) {
            return null;
        }
        SolicitudIO terminada = activa;
        activa = null;
        atendidas++;
        esperaAcumulada += esperaActiva;
        for (int i = 0; i < conteos.getTamano(); i++) {
            ConteoPolitica conteo = conteos.obtener(i);
            if (conteo.politica.equals(politicaEnCurso)) {
                conteo.cantidad++;
                return terminada;
            }
        }
        conteos.agregarAlFinal(new ConteoPolitica(politicaEnCurso));
        return terminada;
    }

    private void moverHacia(int destino) {
        int origen = disco.getPosicionCabezal();
        int distancia = destino - origen;
        int paso = Integer.signum(distancia) * Math.min(velocidad, Math.abs(distancia));
        desplazamiento += disco.moverCabezalA(origen + paso);
    }

    private SolicitudIO[] copiarPendientes() {
        SolicitudIO[] copia = new SolicitudIO[pendientes.getTamano()];
        int[] indice = {0};
        pendientes.paraCadaElemento(solicitud -> copia[indice[0]++] = solicitud);
        return copia;
    }

    public Metricas obtenerMetricas() {
        acceso.acquireUninterruptibly();
        try {
            return metricas();
        } finally {
            acceso.release();
        }
    }

    private Metricas metricas() {
        double promedio = atendidas == 0 ? 0 : (double) esperaAcumulada / atendidas;
        return new Metricas(ciclo, desplazamiento, atendidas, promedio);
    }

    public Estado obtenerEstado() {
        acceso.acquireUninterruptibly();
        try {
            Atenciones[] porPolitica = new Atenciones[conteos.getTamano()];
            for (int i = 0; i < porPolitica.length; i++) {
                ConteoPolitica conteo = conteos.obtener(i);
                porPolitica[i] = new Atenciones(conteo.politica, conteo.cantidad);
            }
            return new Estado(metricas(), disco.getPosicionCabezal(), disco.getDireccion(),
                    politica, activa, copiarPendientes(), porPolitica);
        } finally {
            acceso.release();
        }
    }

    public record Metricas(long ciclo, long desplazamiento, long solicitudesAtendidas, double esperaPromedio) {
    }

    public record Estado(Metricas metricas, int cabezal, Direccion direccion, IDiskScheduler politica, SolicitudIO activa,
            SolicitudIO[] pendientes, Atenciones[] porPolitica) {
    }

    public record Atenciones(IDiskScheduler politica, long cantidad) {
    }

    private static final class ConteoPolitica {
        private final IDiskScheduler politica;
        private long cantidad = 1;

        private ConteoPolitica(IDiskScheduler politica) {
            this.politica = politica;
        }
    }
}
