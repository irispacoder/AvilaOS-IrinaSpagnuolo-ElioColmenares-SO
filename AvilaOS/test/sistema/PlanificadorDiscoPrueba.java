package sistema;

import enums.OperacionIO;
import modelo.SolicitudIO;
import planificadores.PoliticaDisco;

public final class PlanificadorDiscoPrueba {
    private static final int[] ANEXO = {82, 17, 43, 140, 24, 16, 190};
    private static final int[] CASOS = {95, 180, 34, 119, 11, 123, 62, 64};

    public static void main(String[] args) {
        probarRecorrido(PoliticaDisco.FIFO, ANEXO, ANEXO, 518);
        probarRecorrido(PoliticaDisco.SSTF, ANEXO, new int[]{43, 24, 17, 16, 82, 140, 190}, 208);
        probarRecorrido(PoliticaDisco.SCAN, ANEXO, new int[]{82, 140, 190, 43, 24, 17, 16}, 332);
        probarRecorrido(PoliticaDisco.C_SCAN, ANEXO, new int[]{82, 140, 190, 16, 17, 24, 43}, 192);
        probarRecorrido(PoliticaDisco.FIFO, CASOS, CASOS, 644);
        probarRecorrido(PoliticaDisco.SSTF, CASOS, new int[]{62, 64, 34, 11, 95, 119, 123, 180}, 236);
        probarRecorrido(PoliticaDisco.SCAN, CASOS, new int[]{62, 64, 95, 119, 123, 180, 34, 11}, 337);
        probarRecorrido(PoliticaDisco.C_SCAN, CASOS, new int[]{62, 64, 95, 119, 123, 180, 11, 34}, 183);
        probarVelocidadTransferenciasYCambio();
        probarEmpate();
        probarScanDesdeElExtremo();
        probarRetornoCircularDescendente();
        System.out.println("Planificador de disco: 12 escenarios aprobados, incluidos P1–P4 y el anexo");
    }

    private static void probarRecorrido(PoliticaDisco politica, int[] cola, int[] orden, long distancia) {
        Disco disco = new Disco(200, 0, 50);
        PlanificadorDisco planificador = new PlanificadorDisco(disco, politica);
        for (int bloque : cola) {
            planificador.encolar(bloque);
        }
        int completadas = 0;
        for (int ciclo = 0; ciclo < 1000 && completadas < orden.length; ciclo++) {
            SolicitudIO solicitud = planificador.avanzarCiclo();
            if (solicitud != null) {
                comprobar(solicitud.bloque() == orden[completadas], politica + ": orden de atención");
                completadas++;
            }
        }
        PlanificadorDisco.Metricas metricas = planificador.obtenerMetricas();
        comprobar(completadas == orden.length, politica + ": deben completarse todas");
        comprobar(metricas.solicitudesAtendidas() == orden.length, politica + ": contador de atención");
        comprobar(metricas.desplazamiento() == distancia, politica + ": desplazamiento total");
        comprobar(metricas.ciclo() == distancia + orden.length, politica + ": búsqueda más una transferencia por bloque");
        comprobar(planificador.avanzarCiclo() == null, politica + ": la cola está vacía");
        comprobar(planificador.obtenerMetricas().desplazamiento() == distancia, politica + ": cabezal en reposo");
    }

    private static void probarVelocidadTransferenciasYCambio() {
        Disco disco = new Disco(100, 0, 50);
        long[] notificaciones = {0};
        PlanificadorDisco planificador = new PlanificadorDisco(disco, PoliticaDisco.FIFO, 3,
                (solicitud, aplicada) -> notificaciones[0]++);
        int[] cadena = {55, 54};
        SolicitudIO primera = planificador.encolar(cadena, OperacionIO.READ, "P1");
        cadena[0] = 0;
        SolicitudIO segunda = planificador.encolar(51);
        comprobar(planificador.avanzarCiclo() == null && disco.getPosicionCabezal() == 53, "Velocidad de tres posiciones");
        comprobar(planificador.obtenerEstado().activa() == primera, "La solicitud sale de la cola al seleccionarse");
        planificador.cambiarPolitica(PoliticaDisco.SSTF);
        SolicitudIO tercera = planificador.encolar(54);
        comprobar(planificador.avanzarCiclo() == null && disco.getPosicionCabezal() == 55, "Completa la primera búsqueda");
        comprobar(planificador.avanzarCiclo() == null, "Transfiere el primer bloque");
        comprobar(planificador.avanzarCiclo() == null && disco.getPosicionCabezal() == 54, "Busca el segundo bloque");
        comprobar(planificador.avanzarCiclo() == primera, "Completa la operación de dos bloques bajo FIFO");
        comprobar(planificador.avanzarCiclo() == tercera, "SSTF atiende ahora el bloque actual");
        comprobar(planificador.avanzarCiclo() == null && disco.getPosicionCabezal() == 51, "Busca el bloque restante");
        comprobar(planificador.avanzarCiclo() == segunda, "Completa la segunda solicitud");
        PlanificadorDisco.Estado estado = planificador.obtenerEstado();
        comprobar(estado.metricas().desplazamiento() == 9, "Distancia en tres búsquedas");
        comprobar(estado.metricas().esperaPromedio() == 10.0 / 3, "La espera mide solo la permanencia en cola");
        comprobar(estado.porPolitica()[0].cantidad() == 1 && estado.porPolitica()[0].politica() == PoliticaDisco.FIFO,
                "La solicitud activa se atribuye a su política original");
        comprobar(estado.porPolitica()[1].cantidad() == 2 && estado.porPolitica()[1].politica() == PoliticaDisco.SSTF,
                "Las siguientes solicitudes se atribuyen a SSTF");
        comprobar(notificaciones[0] == 3, "Cada solicitud terminada notifica una vez");
    }

    private static void probarEmpate() {
        Disco disco = new Disco(100, 0, 50);
        PlanificadorDisco planificador = new PlanificadorDisco(disco, PoliticaDisco.SSTF);
        SolicitudIO primera = planificador.encolar(48);
        planificador.encolar(52);
        comprobar(planificador.avanzarCiclo() == null && disco.getPosicionCabezal() == 49, "Empate a favor de la más antigua");
        comprobar(planificador.avanzarCiclo() == null, "Llegar al bloque consume un ciclo de búsqueda");
        comprobar(planificador.avanzarCiclo() == primera, "Transferencia después de llegar al bloque");
        try {
            planificador.encolar(100);
            throw new AssertionError("Se esperaba un rechazo para el bloque 100");
        } catch (IllegalArgumentException esperada) {
            comprobar(planificador.obtenerEstado().pendientes().length == 1, "El bloque inválido no entró a la cola");
        }
    }

    private static void probarScanDesdeElExtremo() {
        Disco disco = new Disco(100, 0, 99);
        PlanificadorDisco planificador = new PlanificadorDisco(disco, PoliticaDisco.SCAN);
        SolicitudIO solicitud = planificador.encolar(98);
        comprobar(planificador.avanzarCiclo() == null && disco.getPosicionCabezal() == 98, "SCAN invierte al llegar al extremo");
        comprobar(planificador.avanzarCiclo() == solicitud, "Transfiere el bloque 98");
        comprobar(disco.getDireccion() == Disco.Direccion.DESCENDENTE, "SCAN baja después de invertir");
    }

    private static void probarRetornoCircularDescendente() {
        Disco disco = new Disco(100, 0, 50);
        disco.moverCabezalA(1);
        PlanificadorDisco planificador = new PlanificadorDisco(disco, PoliticaDisco.C_SCAN);
        SolicitudIO solicitud = planificador.encolar(50);
        comprobar(planificador.avanzarCiclo() == null && disco.getPosicionCabezal() == 0, "C-SCAN llega al extremo");
        comprobar(planificador.avanzarCiclo() == null && disco.getPosicionCabezal() == 98, "Salta a 99 y reanuda el barrido");
        for (int i = 0; i < 48; i++) {
            comprobar(planificador.avanzarCiclo() == null, "Busca el bloque 50 en el barrido descendente");
        }
        comprobar(planificador.avanzarCiclo() == solicitud, "La solicitud se atiende al descender");
        comprobar(planificador.obtenerMetricas().desplazamiento() == 50, "Se excluye el salto de regreso");
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
