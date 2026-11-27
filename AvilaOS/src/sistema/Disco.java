package sistema;

import interfaces.IClockListener;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Disco de bloques numerados desde cero. */
public final class Disco implements IClockListener {
    public enum Direccion { ASCENDENTE, DESCENDENTE }

    public enum EstadoBloque { LIBRE, ARCHIVO, SWAP_LIBRE, SWAP_OCUPADO }

    public record DetalleBloque(EstadoBloque estado, String ocupante) {
    }

    /** Identifica una cadena activa, incluso si su primer bloque se reutiliza después. */
    public static final class Asignacion {
        private final int primerBloque;
        private final int cantidadBloques;
        private String ocupante;

        private Asignacion(int primerBloque, int cantidadBloques, String ocupante) {
            this.primerBloque = primerBloque;
            this.cantidadBloques = cantidadBloques;
            this.ocupante = ocupante;
        }

        public int primerBloque() { return primerBloque; }
        public int cantidadBloques() { return cantidadBloques; }
    }

    private static final int FIN_CADENA = -1;
    private final Semaphore mapa = new Semaphore(1, true);
    private final int[] siguientes;
    private final Asignacion[] asignaciones;
    private final boolean[] swap;
    private int posicionCabezal;
    private Direccion direccion = Direccion.ASCENDENTE;
    private volatile PlanificadorDisco planificador;

    public Disco(int cantidadBloques, int bloquesSwap) {
        this(cantidadBloques, bloquesSwap, 0);
    }

    public Disco(int cantidadBloques, int bloquesSwap, int cabezalInicial) {
        if (cantidadBloques < 100 || cantidadBloques > 500) {
            throw new IllegalArgumentException("El disco debe tener entre 100 y 500 bloques");
        }
        if (cabezalInicial < 0 || cabezalInicial >= cantidadBloques) {
            throw new IllegalArgumentException("Posición inicial del cabezal fuera del disco");
        }
        siguientes = new int[cantidadBloques];
        asignaciones = new Asignacion[cantidadBloques];
        swap = new boolean[cantidadBloques];
        posicionCabezal = cabezalInicial;
        configurarAreaSwap(bloquesSwap);
    }

    public int getCantidadBloques() { return siguientes.length; }
    public synchronized int getPosicionCabezal() { return posicionCabezal; }
    public synchronized Direccion getDireccion() { return direccion; }

    void conectarPlanificador(PlanificadorDisco nuevo) {
        planificador = nuevo;
    }

    @Override
    public void onClockTick(int cicloActual) {
        PlanificadorDisco activo = planificador;
        if (activo != null) {
            activo.avanzarCiclo();
        }
    }

    /** Devuelve la distancia física recorrida, en bloques. */
    public synchronized int moverCabezalA(int destino) {
        Direccion sentido = destino == posicionCabezal ? direccion
                : destino > posicionCabezal ? Direccion.ASCENDENTE : Direccion.DESCENDENTE;
        return moverCabezalA(destino, sentido);
    }

    synchronized int moverCabezalA(int destino, Direccion sentido) {
        if (destino < 0 || destino >= siguientes.length) {
            throw new IllegalArgumentException("Bloque fuera del disco: " + destino);
        }
        int distancia = Math.abs(destino - posicionCabezal);
        posicionCabezal = destino;
        direccion = sentido;
        return distancia;
    }

    /** Reserva los bloques libres de mayor índice. Mantiene las asignaciones ya cargadas. */
    public void configurarAreaSwap(int cantidad) {
        if (cantidad < 0 || cantidad >= swap.length) {
            throw new IllegalArgumentException("El tamaño de swap debe ser menor que el disco");
        }
        conMapa(() -> {
            int libres = 0;
            for (int i = 0; i < swap.length; i++) {
                if (asignaciones[i] == null) {
                    libres++;
                } else if (swap[i]) {
                    throw new IllegalStateException("Hay procesos en el área de swap");
                }
            }
            if (libres < cantidad) {
                throw new IllegalArgumentException("Los archivos dejan menos de " + cantidad + " bloques para swap");
            }
            Arrays.fill(swap, false);
            int porReservar = cantidad;
            for (int i = swap.length - 1; porReservar > 0 && i >= 0; i--) {
                if (asignaciones[i] == null) {
                    swap[i] = true;
                    porReservar--;
                }
            }
            return null;
        });
    }

    public Asignacion asignarArchivo(int cantidad) {
        return asignarArchivo(cantidad, "Archivo");
    }

    /** Devuelve null si la zona de archivos carece de espacio suficiente. */
    public Asignacion asignarArchivo(int cantidad, String ruta) {
        return asignar(cantidad, false, ruta);
    }

    public Asignacion reservarSwap(int cantidad) {
        return reservarSwap(cantidad, "Proceso suspendido");
    }

    /** Devuelve null si la reserva de swap carece de espacio suficiente. */
    public Asignacion reservarSwap(int cantidad, String proceso) {
        return asignar(cantidad, true, proceso);
    }

    private Asignacion asignar(int cantidad, boolean enSwap, String ocupante) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad de bloques debe ser positiva");
        }
        if (cantidad > siguientes.length) {
            return null;
        }
        return conMapa(() -> {
            int[] bloques = new int[cantidad];
            int libres = 0;
            for (int bloque = 0; bloque < siguientes.length && libres < cantidad; bloque++) {
                if (swap[bloque] == enSwap && asignaciones[bloque] == null) {
                    bloques[libres++] = bloque;
                }
            }
            return libres == cantidad ? encadenar(bloques, ocupante) : null;
        });
    }

    /** Restaura una cadena de archivo con sus índices exactos, validando antes de escribir. */
    public Asignacion restaurarArchivo(int[] cadena, String ruta) {
        int[] bloques = cadena.clone();
        return conMapa(() -> {
            if (bloques.length == 0) {
                throw new IllegalArgumentException("Cadena de archivo vacía: " + ruta);
            }
            boolean[] vistos = new boolean[siguientes.length];
            for (int bloque : bloques) {
                if (bloque < 0 || bloque >= siguientes.length || swap[bloque]
                        || asignaciones[bloque] != null || vistos[bloque]) {
                    throw new IllegalArgumentException("Bloque repetido, reservado o inválido: " + bloque);
                }
                vistos[bloque] = true;
            }
            return encadenar(bloques, ruta);
        });
    }

    private Asignacion encadenar(int[] bloques, String ocupante) {
        Asignacion asignacion = new Asignacion(bloques[0], bloques.length, ocupante);
        for (int i = 0; i < bloques.length; i++) {
            int bloque = bloques[i];
            asignaciones[bloque] = asignacion;
            siguientes[bloque] = i + 1 < bloques.length ? bloques[i + 1] : FIN_CADENA;
        }
        return asignacion;
    }

    public int[] obtenerBloques(Asignacion asignacion) {
        return conMapa(() -> {
            validarAsignacion(asignacion);
            int[] bloques = new int[asignacion.cantidadBloques];
            int bloque = asignacion.primerBloque;
            for (int i = 0; i < bloques.length; i++) {
                bloques[i] = bloque;
                bloque = siguientes[bloque];
            }
            return bloques;
        });
    }

    public void renombrarOcupante(Asignacion asignacion, String ocupante) {
        conMapa(() -> {
            validarAsignacion(asignacion);
            asignacion.ocupante = ocupante;
            return null;
        });
    }

    public void liberar(Asignacion asignacion) {
        conMapa(() -> {
            validarAsignacion(asignacion);
            int bloque = asignacion.primerBloque;
            while (bloque != FIN_CADENA) {
                asignaciones[bloque] = null;
                bloque = siguientes[bloque];
            }
            return null;
        });
    }

    private void validarAsignacion(Asignacion asignacion) {
        if (asignacion == null || asignacion.primerBloque >= asignaciones.length
                || asignaciones[asignacion.primerBloque] != asignacion) {
            throw new IllegalArgumentException("La asignación no pertenece a este disco o ya fue liberada");
        }
    }

    public EstadoBloque[] obtenerMapaBloques() {
        return conMapa(() -> {
            EstadoBloque[] estados = new EstadoBloque[siguientes.length];
            for (int i = 0; i < estados.length; i++) {
                estados[i] = estadoDe(i);
            }
            return estados;
        });
    }

    public DetalleBloque[] obtenerDetalleBloques() {
        return conMapa(() -> {
            DetalleBloque[] detalles = new DetalleBloque[siguientes.length];
            for (int i = 0; i < detalles.length; i++) {
                Asignacion asignacion = asignaciones[i];
                detalles[i] = new DetalleBloque(estadoDe(i), asignacion == null ? null : asignacion.ocupante);
            }
            return detalles;
        });
    }

    private EstadoBloque estadoDe(int bloque) {
        boolean libre = asignaciones[bloque] == null;
        if (swap[bloque]) {
            return libre ? EstadoBloque.SWAP_LIBRE : EstadoBloque.SWAP_OCUPADO;
        }
        return libre ? EstadoBloque.LIBRE : EstadoBloque.ARCHIVO;
    }

    private <T> T conMapa(Supplier<T> operacion) {
        mapa.acquireUninterruptibly();
        try {
            return operacion.get();
        } finally {
            mapa.release();
        }
    }
}
