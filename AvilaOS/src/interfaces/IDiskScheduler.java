package interfaces;

import modelo.SolicitudIO;
import sistema.Disco.Direccion;

public interface IDiskScheduler {
    /**
     * Elige el siguiente tramo del recorrido. La cola recibida contiene al menos
     * una solicitud en orden de llegada.
     */
    Movimiento elegir(SolicitudIO[] pendientes, int posicion, int ultimoBloque, Direccion barrido);

    /** Una solicitud nula indica un tramo de barrido sin atención de E/S. */
    record Movimiento(int destino, SolicitudIO solicitud, Direccion barridoSiguiente, boolean salto) {
        public Movimiento(int destino, SolicitudIO solicitud, Direccion barridoSiguiente) {
            this(destino, solicitud, barridoSiguiente, false);
        }
    }
}
