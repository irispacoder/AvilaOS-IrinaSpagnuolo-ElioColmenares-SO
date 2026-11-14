package modelo;

import enums.OperacionIO;

/** Una operación indivisible sobre uno o más bloques físicos. */
public record SolicitudIO(long id, int[] bloques, long cicloLlegada, OperacionIO operacion, String origen) {
    public SolicitudIO {
        bloques = bloques.clone();
    }

    @Override
    public int[] bloques() { return bloques.clone(); }

    public int bloque() { return bloques[0]; }
    public int bloque(int indice) { return bloques[indice]; }
    public int cantidadBloques() { return bloques.length; }
}
