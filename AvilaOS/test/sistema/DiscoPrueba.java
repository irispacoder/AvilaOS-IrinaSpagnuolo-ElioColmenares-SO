package sistema;

import java.util.Arrays;
import sistema.Disco.Asignacion;
import sistema.Disco.Direccion;
import sistema.Disco.EstadoBloque;

public final class DiscoPrueba {
    public static void main(String[] args) {
        probarConfiguracionYCabezal();
        probarAsignacionFragmentada();
        probarSwapYFallosSinCambios();
        System.out.println("Disco: 3 escenarios aprobados");
    }

    private static void probarConfiguracionYCabezal() {
        rechazar(() -> new Disco(99, 0));
        rechazar(() -> new Disco(501, 0));
        rechazar(() -> new Disco(100, -1));
        rechazar(() -> new Disco(100, 100));
        Disco disco = new Disco(500, 0);
        comprobar(disco.getPosicionCabezal() == 0, "El cabezal parte del bloque 0");
        comprobar(disco.moverCabezalA(499) == 499, "Debe llegar al último bloque");
        comprobar(disco.getDireccion() == Direccion.ASCENDENTE, "Debe subir");
        comprobar(disco.moverCabezalA(450) == 49, "Debe medir la distancia al bajar");
        comprobar(disco.moverCabezalA(450) == 0, "No hay desplazamiento en el mismo bloque");
        comprobar(disco.getDireccion() == Direccion.DESCENDENTE, "Un movimiento nulo conserva la dirección");
        rechazar(() -> disco.moverCabezalA(-1));
        rechazar(() -> disco.moverCabezalA(500));
        comprobar(disco.getPosicionCabezal() == 450, "Un destino inválido no mueve el cabezal");
    }

    private static void probarAsignacionFragmentada() {
        Disco disco = new Disco(100, 10);
        Asignacion a = disco.asignarArchivo(2);
        Asignacion b = disco.asignarArchivo(2);
        disco.liberar(a);
        Asignacion c = disco.asignarArchivo(3);
        comprobar(Arrays.equals(disco.obtenerBloques(c), new int[]{0, 1, 4}), "C debe encadenar dos huecos");
        comprobar(Arrays.equals(disco.obtenerBloques(b), new int[]{2, 3}), "B debe conservar sus bloques");
        rechazar(() -> disco.liberar(a));
        rechazar(() -> new Disco(100, 10).liberar(c));
        disco.liberar(c);
        EstadoBloque[] mapa = disco.obtenerMapaBloques();
        comprobar(mapa[0] == EstadoBloque.LIBRE && mapa[1] == EstadoBloque.LIBRE
                && mapa[4] == EstadoBloque.LIBRE, "Liberar C devuelve toda su cadena");
        comprobar(mapa[2] == EstadoBloque.ARCHIVO && mapa[3] == EstadoBloque.ARCHIVO, "B sigue asignado");
    }

    private static void probarSwapYFallosSinCambios() {
        Disco disco = new Disco(100, 10);
        comprobar(disco.obtenerMapaBloques()[99] == EstadoBloque.SWAP_LIBRE, "El swap empieza libre");
        Asignacion swap = disco.reservarSwap(10);
        comprobar(swap.primerBloque() == 90 && swap.cantidadBloques() == 10, "La reserva usa toda su zona");
        comprobar(disco.obtenerMapaBloques()[99] == EstadoBloque.SWAP_OCUPADO, "Último bloque de swap ocupado");
        comprobar(disco.reservarSwap(1) == null, "Swap lleno aunque haya espacio para archivos");
        comprobar(disco.asignarArchivo(90) != null, "Los archivos pueden ocupar su zona completa");
        disco.liberar(swap);
        EstadoBloque[] mapa = disco.obtenerMapaBloques();
        comprobar(disco.asignarArchivo(1) == null, "La asignación de archivos no invade swap");
        comprobar(disco.reservarSwap(11) == null, "La solicitud demasiado grande debe fallar entera");
        rechazar(() -> disco.reservarSwap(0));
        comprobar(Arrays.equals(mapa, disco.obtenerMapaBloques()), "Los fallos dejan el mapa intacto");
        comprobar(new Disco(100, 0).reservarSwap(1) == null, "Un disco sin swap no admite reservas");
    }

    private static void rechazar(Runnable operacion) {
        try {
            operacion.run();
        } catch (IllegalArgumentException esperada) {
            return;
        }
        throw new AssertionError("Se esperaba IllegalArgumentException");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
