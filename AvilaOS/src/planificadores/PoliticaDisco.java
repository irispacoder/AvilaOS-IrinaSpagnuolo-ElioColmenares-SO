package planificadores;

import interfaces.IDiskScheduler;
import modelo.SolicitudIO;
import sistema.Disco.Direccion;

public enum PoliticaDisco implements IDiskScheduler {
    FIFO, SSTF, SCAN, C_SCAN;

    @Override
    public String toString() {
        return this == C_SCAN ? "C-SCAN" : name();
    }

    @Override
    public Movimiento elegir(SolicitudIO[] pendientes, int posicion, int ultimoBloque, Direccion barrido) {
        return switch (this) {
            case FIFO -> atender(pendientes[0], barrido);
            case SSTF -> atender(masCercana(pendientes, posicion, null), barrido);
            case SCAN, C_SCAN -> elegirBarrido(pendientes, posicion, ultimoBloque, barrido);
        };
    }

    private Movimiento elegirBarrido(SolicitudIO[] pendientes, int posicion, int ultimoBloque, Direccion barrido) {
        SolicitudIO siguiente = masCercana(pendientes, posicion, barrido);
        if (siguiente != null) {
            return atender(siguiente, barrido);
        }

        int extremo = barrido == Direccion.ASCENDENTE ? ultimoBloque : 0;
        Direccion inversa = barrido == Direccion.ASCENDENTE ? Direccion.DESCENDENTE : Direccion.ASCENDENTE;
        if (posicion != extremo) {
            return new Movimiento(extremo, null, this == SCAN ? inversa : barrido);
        }
        if (this == C_SCAN) {
            return new Movimiento(ultimoBloque - extremo, null, barrido, true);
        }
        return atender(masCercana(pendientes, posicion, inversa), inversa);
    }

    private static SolicitudIO masCercana(SolicitudIO[] pendientes, int posicion, Direccion barrido) {
        SolicitudIO elegida = null;
        int menorDistancia = Integer.MAX_VALUE;
        for (SolicitudIO solicitud : pendientes) {
            int desplazamiento = solicitud.bloque() - posicion;
            if ((barrido == Direccion.ASCENDENTE && desplazamiento < 0)
                    || (barrido == Direccion.DESCENDENTE && desplazamiento > 0)) {
                continue;
            }
            int distancia = Math.abs(desplazamiento);
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                elegida = solicitud;
            }
        }
        return elegida;
    }

    private static Movimiento atender(SolicitudIO solicitud, Direccion barrido) {
        return new Movimiento(solicitud.bloque(), solicitud, barrido);
    }
}
