package persistencia;

import enums.OperacionIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import persistencia.CargadorJSON.ArchivoProcesos;
import persistencia.CargadorJSON.ConfigProceso;
import sistema.Disco.EstadoBloque;
import sistema.SesionProyecto2;

public final class CargadorJSONPrueba {
    public static void main(String[] args) throws Exception {
        Path ejemplos = Path.of(args.length == 0 ? "AvilaOS/ejemplos" : args[0]);
        probarEjemplosYReloj(ejemplos);
        Path temporal = Files.createTempDirectory("avilaos-json-");
        try {
            probarGuardadoYRecarga(temporal);
            probarValidacion(temporal, ejemplos);
        } finally {
            try (var archivos = Files.list(temporal)) {
                for (Path ruta : archivos.toArray(Path[]::new)) {
                    Files.deleteIfExists(ruta);
                }
            }
            Files.deleteIfExists(temporal);
        }
        System.out.println("JSON y reloj: ejemplos oficiales, guardado, validación y pausa/reanudación aprobados");
    }

    private static void probarEjemplosYReloj(Path ejemplos) throws Exception {
        var archivoDisco = CargadorJSON.leerSistemaArchivos(ejemplos.resolve("sistema-archivos.json"));
        var archivoProcesos = CargadorJSON.leerProcesos(ejemplos.resolve("procesos.json"));
        comprobar(archivoDisco.bloques() == 200 && archivoDisco.cabezalInicial() == 50, "Configuración de disco oficial");
        comprobar(archivoProcesos.procesos().length == 8 && archivoProcesos.quantum() == 0, "Configuración de CPU oficial");

        ConfigProceso[] procesos = Arrays.copyOf(archivoProcesos.procesos(), 9);
        procesos[8] = new ConfigProceso("CPU", 1, 100, 2, 1, null, null, 0);
        var configuracion = new ArchivoProcesos(15, archivoProcesos.bloquesSwap(), archivoProcesos.politicaDisco(),
                archivoProcesos.ram(), archivoProcesos.politicaCPU(), archivoProcesos.quantum(), procesos);
        SesionProyecto2 sesion = new SesionProyecto2(archivoDisco, configuracion);
        var disco = sesion.getSistemaArchivos().disco();
        var cluster = sesion.getCluster();
        var planificador = sesion.getPlanificador();
        comprobar(cluster.getComputadores().getTamano() == 1, "Se crea un solo computador");
        comprobar(sesion.obtenerProcesosIO().length == 8, "Se conservan los ocho descriptores de E/S");
        comprobar(sesion.obtenerProcesosIO()[2].operacion() == OperacionIO.UPDATE, "Se conserva la operación");
        comprobar(disco.obtenerDetalleBloques()[180].ocupante().equals("/usuarios/video.mp4"), "Se conserva el ocupante");
        EstadoBloque[] mapa = disco.obtenerMapaBloques();
        comprobar(mapa[119] == EstadoBloque.ARCHIVO && mapa[123] == EstadoBloque.ARCHIVO,
                "El swap no desplaza archivos del JSON");
        comprobar(Arrays.stream(mapa).filter(b -> b == EstadoBloque.SWAP_LIBRE).count() == 100, "Swap exacto");

        CountDownLatch completadas = new CountDownLatch(2);
        sesion.alCompletarIO(solicitud -> completadas.countDown());
        planificador.encolar(50);
        cluster.iniciarSimulacion();
        try {
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (completadas.getCount() == 2 && System.nanoTime() < limite) {
                Thread.sleep(5);
            }
            comprobar(completadas.getCount() == 1, "El reloj completa la transferencia de disco");
            cluster.pausarSimulacion();
            Thread.sleep(50);
            long cicloPausa = planificador.obtenerMetricas().ciclo();
            comprobar(cicloPausa == cluster.getRelojGlobal().getCicloActual(), "Un ciclo de disco por ciclo global");
            planificador.encolar(50);
            comprobar(!completadas.await(80, TimeUnit.MILLISECONDS), "El disco no trabaja durante la pausa");
            comprobar(planificador.obtenerMetricas().ciclo() == cicloPausa, "La pausa conserva el ciclo");
            cluster.iniciarSimulacion();
            comprobar(completadas.await(3, TimeUnit.SECONDS), "Reanudar vuelve a avanzar el disco");
            limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (sesion.getComputador().getColaTerminados().estaVacia() && System.nanoTime() < limite) {
                Thread.sleep(5);
            }
            comprobar(sesion.getComputador().getColaTerminados().getTamano() == 1, "El proceso CPU mantiene su recorrido");
            comprobar(cluster.obtenerUltimosLogs(5).getTamano() > 0, "Se registran eventos");
        } finally {
            cluster.detenerSimulacion();
        }
    }

    private static void probarGuardadoYRecarga(Path temporal) throws Exception {
        Path original = temporal.resolve("fragmentado.json");
        Files.writeString(original, """
                {
                  "diskConfig":{"totalBlocks":100,"initialHead":9,"headPositionsPerCycle":4},
                  "fileSystem":[
                    {"path":"/docs","type":"DIRECTORY","owner":"admin","permissions":"public"},
                    {"path":"/docs/a","type":"FILE","startBlock":31,"blocks":3,"blockChain":[31,4,26],
                     "owner":"user1","permissions":"private"},
                    {"path":"/docs/b","type":"FILE","startBlock":97,"blocks":2,"owner":"user2","permissions":"public"}
                  ]
                }
                """);
        var primera = CargadorJSON.crearSistema(CargadorJSON.leerSistemaArchivos(original), 10);
        comprobar(Arrays.equals(primera.disco().obtenerBloques(primera.entradas()[2].asignacion()), new int[]{97, 0}),
                "Una cadena implícita conserva su primer bloque");
        primera.disco().moverCabezalA(18);
        Path copia = temporal.resolve("copia.json");
        CargadorJSON.guardarSistemaArchivos(copia, primera);
        var segunda = CargadorJSON.crearSistema(CargadorJSON.leerSistemaArchivos(copia), 10);
        var archivo = segunda.entradas()[1];
        comprobar(Arrays.equals(segunda.disco().obtenerBloques(archivo.asignacion()), new int[]{31, 4, 26}),
                "El guardado conserva el orden exacto de la cadena fragmentada");
        comprobar(archivo.dueno().equals("user1") && archivo.permiso() == CargadorJSON.Permiso.PRIVATE,
                "Se conservan dueño y permisos");
        comprobar(segunda.disco().getPosicionCabezal() == 18 && segunda.posicionesPorCiclo() == 4,
                "Se conservan posición y velocidad del cabezal");
    }

    private static void probarValidacion(Path temporal, Path ejemplos) throws Exception {
        Path invalido = temporal.resolve("invalido.json");
        String datos = Files.readString(ejemplos.resolve("sistema-archivos.json"));
        Files.writeString(invalido, datos.replace("\"startBlock\": 34", "\"startBlock\": 11"));
        var conflicto = CargadorJSON.leerSistemaArchivos(invalido);
        rechazar(() -> CargadorJSON.crearSistema(conflicto, 100));
        Files.writeString(invalido, datos.replace("\"totalBlocks\": 200", "\"totalBlocks\": 200.5"));
        rechazar(() -> CargadorJSON.leerSistemaArchivos(invalido));
        Files.writeString(invalido, datos.replace("/usuarios/notas.txt", "/faltante/notas.txt"));
        rechazar(() -> CargadorJSON.leerSistemaArchivos(invalido));
        Files.writeString(invalido, datos + "{}");
        rechazar(() -> CargadorJSON.leerSistemaArchivos(invalido));
        var correcto = CargadorJSON.leerSistemaArchivos(ejemplos.resolve("sistema-archivos.json"));
        rechazar(() -> CargadorJSON.crearSistema(correcto, 193));
        String procesos = Files.readString(ejemplos.resolve("procesos.json"));
        Files.writeString(invalido, procesos.replace("\"policy\": \"FCFS\"", "\"policy\": \"ROUND_ROBIN\""));
        rechazar(() -> CargadorJSON.leerProcesos(invalido));
    }

    private static void rechazar(Operacion operacion) throws Exception {
        try {
            operacion.ejecutar();
        } catch (IllegalArgumentException | IOException esperada) {
            return;
        }
        throw new AssertionError("Se esperaba un error de validación");
    }

    @FunctionalInterface
    private interface Operacion {
        void ejecutar() throws Exception;
    }

    private static void comprobar(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new AssertionError(mensaje);
        }
    }
}
