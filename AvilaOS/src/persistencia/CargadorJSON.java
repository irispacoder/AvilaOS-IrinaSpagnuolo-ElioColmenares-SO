package persistencia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import enums.OperacionIO;
import enums.PoliticaPlanificacion;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import planificadores.PoliticaDisco;
import sistema.Disco;

/** Lee las dos configuraciones del Proyecto 2 y restaura las cadenas del disco. */
public final class CargadorJSON {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();

    private CargadorJSON() {
    }

    public enum TipoEntrada { DIRECTORY, FILE }
    public enum Permiso { PUBLIC, PRIVATE }

    public record EntradaPersistida(String ruta, TipoEntrada tipo, String dueno, Permiso permiso,
            int primerBloque, int cantidadBloques, int[] cadena) {
    }

    public record ArchivoDisco(int bloques, int cabezalInicial, int posicionesPorCiclo, EntradaPersistida[] entradas) {
    }

    public record ConfigProceso(String nombre, int memoria, int deadline, int instrucciones, int prioridad,
            String ruta, OperacionIO operacion, int tamanoArchivo) {
    }

    public record ArchivoProcesos(int duracionCicloMs, int bloquesSwap, PoliticaDisco politicaDisco,
            int ram, PoliticaPlanificacion politicaCPU, int quantum, ConfigProceso[] procesos) {
    }

    public record EntradaCargada(String ruta, TipoEntrada tipo, String dueno, Permiso permiso,
            Disco.Asignacion asignacion) {
    }

    public record SistemaCargado(Disco disco, int posicionesPorCiclo, EntradaCargada[] entradas) {
    }

    public static ArchivoDisco leerSistemaArchivos(Path ruta) throws IOException {
        JsonObject raiz = leerObjeto(ruta);
        JsonObject config = objeto(raiz.get("diskConfig"), "diskConfig");
        int bloques = entero(config, "totalBlocks", 100, 500);
        int cabezal = entero(config, "initialHead", 0, bloques - 1);
        int velocidad = enteroOpcional(config, "headPositionsPerCycle", 1, 1, bloques);
        JsonArray arbol = arreglo(raiz.get("fileSystem"), "fileSystem");
        EntradaPersistida[] entradas = new EntradaPersistida[arbol.size()];
        for (int i = 0; i < entradas.length; i++) {
            JsonObject entrada = objeto(arbol.get(i), "fileSystem[" + i + "]");
            String path = rutaAbsoluta(texto(entrada, "path"));
            TipoEntrada tipo = enumerado(TipoEntrada.class, texto(entrada, "type"), "type");
            String dueno = texto(entrada, "owner");
            Permiso permiso = switch (texto(entrada, "permissions")) {
                case "public" -> Permiso.PUBLIC;
                case "private" -> Permiso.PRIVATE;
                default -> throw new IllegalArgumentException("Permiso inválido en " + path);
            };
            int primero = -1;
            int cantidad = 0;
            int[] cadena = null;
            if (tipo == TipoEntrada.FILE) {
                if (path.equals("/")) {
                    throw new IllegalArgumentException("La raíz debe ser un directorio");
                }
                primero = entero(entrada, "startBlock", 0, bloques - 1);
                cantidad = entero(entrada, "blocks", 1, bloques);
                if (entrada.has("blockChain")) {
                    cadena = leerCadena(entrada, bloques, primero, cantidad);
                }
            }
            entradas[i] = new EntradaPersistida(path, tipo, dueno, permiso, primero, cantidad, cadena);
        }
        validarJerarquia(entradas);
        return new ArchivoDisco(bloques, cabezal, velocidad, entradas);
    }

    public static ArchivoProcesos leerProcesos(Path ruta) throws IOException {
        JsonObject raiz = leerObjeto(ruta);
        JsonObject global = objeto(raiz.get("globalConfig"), "globalConfig");
        int duracion = entero(global, "cycleDurationMs", 1, Integer.MAX_VALUE);
        int swap = entero(global, "swapSizeBlocks", 0, 499);
        PoliticaDisco disco = enumerado(PoliticaDisco.class,
                texto(global, "initialDiskPolicy").replace('-', '_'), "initialDiskPolicy");
        JsonObject computador = objeto(raiz.get("computer"), "computer");
        int ram = entero(computador, "ramCapacity", 1, Integer.MAX_VALUE);
        PoliticaPlanificacion cpu = enumerado(PoliticaPlanificacion.class, texto(computador, "policy"), "policy");
        int quantum = entero(computador, "quantum", cpu == PoliticaPlanificacion.ROUND_ROBIN ? 1 : 0, Integer.MAX_VALUE);
        JsonArray lista = arreglo(raiz.get("processes"), "processes");
        ConfigProceso[] procesos = new ConfigProceso[lista.size()];
        for (int i = 0; i < procesos.length; i++) {
            JsonObject p = objeto(lista.get(i), "processes[" + i + "]");
            String nombre = texto(p, "name");
            int memoria = entero(p, "memory", 1, ram);
            int deadline = entero(p, "deadline", 1, Integer.MAX_VALUE);
            int instrucciones = entero(p, "instructions", 1, Integer.MAX_VALUE);
            int prioridad = enteroOpcional(p, "priority", 1, 0, Integer.MAX_VALUE);
            OperacionIO operacion = p.has("operation")
                    ? enumerado(OperacionIO.class, texto(p, "operation"), "operation") : null;
            if (operacion == null && p.has("targetPath")) {
                throw new IllegalArgumentException("Falta operation para el proceso " + nombre);
            }
            String destino = operacion == null ? null : rutaAbsoluta(texto(p, "targetPath"));
            int tamano = enteroOpcional(p, "fileSizeBlocks", 0, 0, 500);
            procesos[i] = new ConfigProceso(nombre, memoria, deadline, instrucciones, prioridad, destino, operacion, tamano);
        }
        return new ArchivoProcesos(duracion, swap, disco, ram, cpu, quantum, procesos);
    }

    /** Restaura primero los índices de los archivos. Luego configura el swap en bloques libres. */
    public static SistemaCargado crearSistema(ArchivoDisco archivo, int bloquesSwap) {
        Disco disco = new Disco(archivo.bloques(), 0, archivo.cabezalInicial());
        EntradaPersistida[] persistidas = archivo.entradas();
        EntradaCargada[] cargadas = new EntradaCargada[persistidas.length];
        boolean[] reservados = new boolean[archivo.bloques()];
        for (EntradaPersistida entrada : persistidas) {
            if (entrada.tipo() == TipoEntrada.FILE) {
                int[] explicitos = entrada.cadena() == null ? new int[]{entrada.primerBloque()} : entrada.cadena();
                for (int bloque : explicitos) {
                    if (reservados[bloque]) {
                        throw new IllegalArgumentException("El bloque " + bloque + " se repite en " + entrada.ruta());
                    }
                    reservados[bloque] = true;
                }
            }
        }
        for (int i = 0; i < persistidas.length; i++) {
            EntradaPersistida entrada = persistidas[i];
            Disco.Asignacion asignacion = null;
            if (entrada.tipo() == TipoEntrada.FILE) {
                int[] cadena = entrada.cadena() == null ? completarCadena(entrada, reservados) : entrada.cadena();
                asignacion = disco.restaurarArchivo(cadena, entrada.ruta());
            }
            cargadas[i] = new EntradaCargada(entrada.ruta(), entrada.tipo(), entrada.dueno(), entrada.permiso(), asignacion);
        }
        disco.configurarAreaSwap(bloquesSwap);
        return new SistemaCargado(disco, archivo.posicionesPorCiclo(), cargadas);
    }

    public static ArchivoDisco capturarSistema(SistemaCargado sistema) {
        Disco disco = sistema.disco();
        EntradaCargada[] cargadas = sistema.entradas();
        EntradaPersistida[] entradas = new EntradaPersistida[cargadas.length];
        for (int i = 0; i < cargadas.length; i++) {
            EntradaCargada entrada = cargadas[i];
            int[] cadena = entrada.asignacion() == null ? null : disco.obtenerBloques(entrada.asignacion());
            entradas[i] = new EntradaPersistida(entrada.ruta(), entrada.tipo(), entrada.dueno(), entrada.permiso(),
                    cadena == null ? -1 : cadena[0], cadena == null ? 0 : cadena.length, cadena);
        }
        return new ArchivoDisco(disco.getCantidadBloques(), disco.getPosicionCabezal(), sistema.posicionesPorCiclo(), entradas);
    }

    public static void guardarSistemaArchivos(Path ruta, SistemaCargado sistema) throws IOException {
        ArchivoDisco estado = capturarSistema(sistema);
        JsonObject raiz = new JsonObject();
        JsonObject config = new JsonObject();
        config.addProperty("totalBlocks", estado.bloques());
        config.addProperty("initialHead", estado.cabezalInicial());
        config.addProperty("headPositionsPerCycle", estado.posicionesPorCiclo());
        raiz.add("diskConfig", config);
        JsonArray arbol = new JsonArray();
        for (EntradaPersistida entrada : estado.entradas()) {
            JsonObject objeto = new JsonObject();
            objeto.addProperty("path", entrada.ruta());
            objeto.addProperty("type", entrada.tipo().name());
            objeto.addProperty("owner", entrada.dueno());
            objeto.addProperty("permissions", entrada.permiso() == Permiso.PUBLIC ? "public" : "private");
            if (entrada.tipo() == TipoEntrada.FILE) {
                objeto.addProperty("startBlock", entrada.primerBloque());
                objeto.addProperty("blocks", entrada.cantidadBloques());
                JsonArray bloques = new JsonArray();
                for (int bloque : entrada.cadena()) {
                    bloques.add(bloque);
                }
                objeto.add("blockChain", bloques);
            }
            arbol.add(objeto);
        }
        raiz.add("fileSystem", arbol);
        Path destino = ruta.toAbsolutePath();
        Path temporal = Files.createTempFile(destino.getParent(), "avilaos-", ".json");
        try {
            try (var salida = Files.newBufferedWriter(temporal)) {
                JSON.toJson(raiz, salida);
            }
            try {
                Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    private static int[] leerCadena(JsonObject entrada, int capacidad, int primero, int cantidad) {
        JsonArray lista = arreglo(entrada.get("blockChain"), "blockChain");
        if (lista.size() != cantidad) {
            throw new IllegalArgumentException("blockChain debe tener tantos elementos como blocks");
        }
        int[] cadena = new int[cantidad];
        for (int i = 0; i < cadena.length; i++) {
            cadena[i] = entero(lista.get(i), "blockChain[" + i + "]", 0, capacidad - 1);
        }
        if (cadena[0] != primero) {
            throw new IllegalArgumentException("El primer elemento de blockChain debe coincidir con startBlock");
        }
        return cadena;
    }

    private static int[] completarCadena(EntradaPersistida entrada, boolean[] reservados) {
        int[] cadena = new int[entrada.cantidadBloques()];
        cadena[0] = entrada.primerBloque();
        int indice = 1;
        for (int bloque = 0; bloque < reservados.length && indice < cadena.length; bloque++) {
            if (!reservados[bloque]) {
                reservados[bloque] = true;
                cadena[indice++] = bloque;
            }
        }
        if (indice != cadena.length) {
            throw new IllegalArgumentException("Espacio insuficiente para " + entrada.ruta());
        }
        return cadena;
    }

    private static void validarJerarquia(EntradaPersistida[] entradas) {
        for (int i = 0; i < entradas.length; i++) {
            String ruta = entradas[i].ruta();
            String padre = ruta.substring(0, ruta.lastIndexOf('/'));
            boolean padreValido = padre.isEmpty();
            for (int j = 0; j < entradas.length; j++) {
                String candidata = entradas[j].ruta();
                if (i != j && candidata.equals(ruta)) {
                    throw new IllegalArgumentException("Ruta duplicada: " + ruta);
                }
                if (candidata.equals(padre) && entradas[j].tipo() == TipoEntrada.DIRECTORY) {
                    padreValido = true;
                }
            }
            if (!padreValido) {
                throw new IllegalArgumentException("No existe el directorio padre de " + ruta);
            }
        }
    }

    private static String rutaAbsoluta(String ruta) {
        if (ruta.equals("/")) {
            return ruta;
        }
        if (!ruta.startsWith("/") || ruta.endsWith("/") || ruta.contains("//") || ruta.indexOf(0) >= 0) {
            throw new IllegalArgumentException("Ruta del sistema de archivos inválida: " + ruta);
        }
        for (String segmento : ruta.substring(1).split("/")) {
            if (segmento.equals(".") || segmento.equals("..")) {
                throw new IllegalArgumentException("Ruta del sistema de archivos inválida: " + ruta);
            }
        }
        return ruta;
    }

    private static JsonObject leerObjeto(Path ruta) throws IOException {
        try (var entrada = Files.newBufferedReader(ruta)) {
            return objeto(JSON.fromJson(entrada, JsonElement.class), ruta.getFileName().toString());
        } catch (JsonParseException e) {
            throw new IOException("JSON inválido en " + ruta.getFileName() + " (" + e.getMessage() + ")", e);
        }
    }

    private static JsonObject objeto(JsonElement valor, String campo) {
        if (valor == null || !valor.isJsonObject()) {
            throw new IllegalArgumentException("Se esperaba un objeto JSON en " + campo);
        }
        return valor.getAsJsonObject();
    }

    private static JsonArray arreglo(JsonElement valor, String campo) {
        if (valor == null || !valor.isJsonArray()) {
            throw new IllegalArgumentException("Se esperaba un arreglo JSON en " + campo);
        }
        return valor.getAsJsonArray();
    }

    private static String texto(JsonObject objeto, String campo) {
        JsonElement valor = objeto.get(campo);
        if (valor == null || !valor.isJsonPrimitive() || !valor.getAsJsonPrimitive().isString()
                || valor.getAsString().isBlank()) {
            throw new IllegalArgumentException("Se esperaba texto en " + campo);
        }
        return valor.getAsString();
    }

    private static int enteroOpcional(JsonObject objeto, String campo, int defecto, int minimo, int maximo) {
        return objeto.has(campo) ? entero(objeto, campo, minimo, maximo) : defecto;
    }

    private static int entero(JsonObject objeto, String campo, int minimo, int maximo) {
        return entero(objeto.get(campo), campo, minimo, maximo);
    }

    private static int entero(JsonElement valor, String campo, int minimo, int maximo) {
        if (valor != null && valor.isJsonPrimitive() && valor.getAsJsonPrimitive().isNumber()) {
            try {
                int numero = valor.getAsBigDecimal().intValueExact();
                if (numero >= minimo && numero <= maximo) {
                    return numero;
                }
            } catch (ArithmeticException | NumberFormatException e) {
                // El mismo mensaje cubre decimales y valores fuera del rango de int.
            }
        }
        throw new IllegalArgumentException(campo + " debe ser un entero entre " + minimo + " y " + maximo);
    }

    private static <E extends Enum<E>> E enumerado(Class<E> tipo, String valor, String campo) {
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valor desconocido en " + campo + ": " + valor, e);
        }
    }
}
