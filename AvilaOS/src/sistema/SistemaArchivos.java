/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package sistema;
import avilaos.estructuras.ListaEnlazada;
import enums.ModoUsuario;
import enums.OperacionIO;
import java.awt.Color;
import java.util.concurrent.Semaphore;
import persistencia.CargadorJSON.EntradaCargada;
import persistencia.CargadorJSON.Permiso;
import persistencia.CargadorJSON.SistemaCargado;
import persistencia.CargadorJSON.TipoEntrada;
import sistema.Disco.Asignacion;

/**
 *
 * @author ispam
 */
public class SistemaArchivos {
    private static final Color[] PALETA_ARCHIVOS = {
        new Color(0x60A5FA), new Color(0x34D399), new Color(0xFBBF24),
        new Color(0xF87171), new Color(0xA78BFA), new Color(0xF472B6),
        new Color(0x2DD4BF), new Color(0xFB923C), new Color(0x818CF8)
    };

    private final Disco disco;
    private final int posicionesPorCiclo;
    private final NodoFS raiz;
    private final Semaphore mutexFS = new Semaphore(1, true);
    private ModoUsuario modoUsuarioActual = ModoUsuario.ADMINISTRADOR;
    private String usuarioActivo = "user1";
    private int contadorColores = 0;
    private volatile int versionEstructura = 0;

    public SistemaArchivos(SistemaCargado cargado) {
        this.disco = cargado.disco();
        this.posicionesPorCiclo = cargado.posicionesPorCiclo();
        this.raiz = new NodoFS("/", "/", TipoEntrada.DIRECTORY, "admin",
                Permiso.PUBLIC, null, "Kernel", Color.LIGHT_GRAY, null);

        for (EntradaCargada entrada : cargado.entradas()) {
            if ("/".equals(entrada.ruta())) {
                continue;
            }
            insertarEntradaInicial(entrada);
        }
    }

    private void insertarEntradaInicial(EntradaCargada entrada) {
        String ruta = entrada.ruta();
        int idx = ruta.lastIndexOf('/');
        String rutaPadre = idx == 0 ? "/" : ruta.substring(0, idx);
        String nombre = ruta.substring(idx + 1);
        NodoFS padre = buscarNodoSinLock(rutaPadre);
        if (padre == null) {
            throw new IllegalArgumentException("Directorio padre inexistente para: " + ruta);
        }
        Color color = entrada.tipo() == TipoEntrada.FILE ? siguienteColor() : Color.LIGHT_GRAY;
        NodoFS nodo = new NodoFS(nombre, ruta, entrada.tipo(), entrada.dueno(),
                entrada.permiso(), entrada.asignacion(), "JSON Inicial", color, padre);
        padre.agregarHijo(nodo);
    }

    private Color siguienteColor() {
        Color c = PALETA_ARCHIVOS[contadorColores % PALETA_ARCHIVOS.length];
        contadorColores++;
        return c;
    }

    public Disco getDisco() { return disco; }
    public int getPosicionesPorCiclo() { return posicionesPorCiclo; }
    public ModoUsuario getModoUsuarioActual() { return modoUsuarioActual; }
    public void setModoUsuarioActual(ModoUsuario modo) { this.modoUsuarioActual = modo; }
    public String getUsuarioActivo() { return usuarioActivo; }
    public void setUsuarioActivo(String usuarioActivo) {
        if (usuarioActivo != null && !usuarioActivo.isBlank()) {
            this.usuarioActivo = usuarioActivo.trim();
        }
    }
    public int getVersionEstructura() { return versionEstructura; }
    public NodoFS getRaiz() { return raiz; }

    public void validarOperacion(OperacionIO operacion, String rutaObjetivo, TipoEntrada tipoCrear, int tamanoBloques, String nuevoNombre) {
        mutexFS.acquireUninterruptibly();
        try {
            if (rutaObjetivo == null || !rutaObjetivo.startsWith("/")) {
                throw new IllegalArgumentException("La ruta debe comenzar con '/'.");
            }
            NodoFS objetivo = buscarNodoSinLock(rutaObjetivo);

            switch (operacion) {
                case CREATE -> {
                    if (objetivo != null) {
                        throw new IllegalArgumentException("Ya existe un elemento en la ruta: " + rutaObjetivo);
                    }
                    int idx = rutaObjetivo.lastIndexOf('/');
                    String rutaPadre = idx == 0 ? "/" : rutaObjetivo.substring(0, idx);
                    NodoFS padre = buscarNodoSinLock(rutaPadre);
                    if (padre == null || !padre.esDirectorio()) {
                        throw new IllegalArgumentException("El directorio padre no existe: " + rutaPadre);
                    }
                    if (modoUsuarioActual == ModoUsuario.USUARIO) {
                        if (tipoCrear == TipoEntrada.DIRECTORY) {
                            throw new IllegalArgumentException("Acceso denegado: En Modo Usuario no se pueden crear directorios.");
                        }
                        if (!padre.getDueno().equalsIgnoreCase(usuarioActivo)) {
                            throw new IllegalArgumentException("Acceso denegado: Solo puede crear archivos dentro de sus propios directorios (Dueño de " + rutaPadre + ": " + padre.getDueno() + ").");
                        }
                    }
                    if (tipoCrear == TipoEntrada.FILE) {
                        if (tamanoBloques <= 0) {
                            throw new IllegalArgumentException("El tamaño del archivo debe ser mayor a 0 bloques.");
                        }
                        if (contarBloquesLibresArchivo() < tamanoBloques) {
                            throw new IllegalArgumentException("Espacio insuficiente en disco: se requieren " + tamanoBloques + " bloques libres.");
                        }
                    }
                }
                case READ -> {
                    if (objetivo == null) {
                        throw new IllegalArgumentException("El archivo o directorio no existe: " + rutaObjetivo);
                    }
                    if (modoUsuarioActual == ModoUsuario.USUARIO) {
                        boolean esPropio = objetivo.getDueno().equalsIgnoreCase(usuarioActivo);
                        boolean esPublico = objetivo.getPermiso() == Permiso.PUBLIC;
                        if (!esPropio && !esPublico) {
                            throw new IllegalArgumentException("Acceso denegado: El elemento es privado y pertenece a '" + objetivo.getDueno() + "'.");
                        }
                    }
                }
                case UPDATE -> {
                    if (objetivo == null || "/".equals(rutaObjetivo)) {
                        throw new IllegalArgumentException("Elemento inválido o inexistente para actualizar: " + rutaObjetivo);
                    }
                    if (nuevoNombre == null || nuevoNombre.isBlank() || nuevoNombre.contains("/")) {
                        throw new IllegalArgumentException("El nuevo nombre no es válido.");
                    }
                    if (modoUsuarioActual == ModoUsuario.USUARIO) {
                        throw new IllegalArgumentException("Acceso denegado: Solo el Administrador puede renombrar archivos o directorios.");
                    }
                    if (objetivo.getPadre() != null && objetivo.getPadre().buscarHijoPorNombre(nuevoNombre) != null) {
                        throw new IllegalArgumentException("Ya existe otro elemento llamado '" + nuevoNombre + "' en ese directorio.");
                    }
                }
                case DELETE -> {
                    if (objetivo == null || "/".equals(rutaObjetivo)) {
                        throw new IllegalArgumentException("No se puede eliminar la raíz o un elemento inexistente: " + rutaObjetivo);
                    }
                    if (modoUsuarioActual == ModoUsuario.USUARIO) {
                        throw new IllegalArgumentException("Acceso denegado: En Modo Usuario no se pueden eliminar archivos ni directorios.");
                    }
                }
                default -> { }
            }
        } finally {
            mutexFS.release();
        }
    }

    public int[] obtenerBloquesObjetivo(OperacionIO operacion, String rutaObjetivo, int tamanoCrear) {
        mutexFS.acquireUninterruptibly();
        try {
            NodoFS nodo = buscarNodoSinLock(rutaObjetivo);
            if (operacion == OperacionIO.CREATE) {
                int[] libres = buscarPrimerosBloquesLibres(Math.max(1, tamanoCrear));
                return libres.length > 0 ? libres : new int[]{disco.getPosicionCabezal()};
            }
            if (nodo == null) {
                return new int[]{disco.getPosicionCabezal()};
            }
            if (!nodo.esDirectorio() && nodo.getAsignacion() != null) {
                return disco.obtenerBloques(nodo.getAsignacion());
            }
            int bloqueRef = buscarPrimerBloqueEnSubarbol(nodo);
            return new int[]{bloqueRef >= 0 ? bloqueRef : disco.getPosicionCabezal()};
        } finally {
            mutexFS.release();
        }
    }

    public void aplicarOperacionCompletada(OperacionIO operacion, String rutaObjetivo, TipoEntrada tipoCrear,
                                           int tamanoBloques, Permiso permisoCrear, String duenoCrear,
                                           String nuevoNombre, String nombreProcesoCreador) {
        mutexFS.acquireUninterruptibly();
        try {
            switch (operacion) {
                case CREATE -> {
                    if (buscarNodoSinLock(rutaObjetivo) != null) return;
                    int idx = rutaObjetivo.lastIndexOf('/');
                    String rutaPadre = idx == 0 ? "/" : rutaObjetivo.substring(0, idx);
                    String nombre = rutaObjetivo.substring(idx + 1);
                    NodoFS padre = buscarNodoSinLock(rutaPadre);
                    if (padre == null) return;

                    Asignacion asig = null;
                    Color color = Color.LIGHT_GRAY;
                    if (tipoCrear == TipoEntrada.FILE) {
                        asig = disco.asignarArchivo(tamanoBloques, rutaObjetivo);
                        if (asig == null) {
                            throw new IllegalStateException("Sin bloques libres en disco al completar CREATE de " + rutaObjetivo);
                        }
                        color = siguienteColor();
                    }
                    NodoFS nuevo = new NodoFS(nombre, rutaObjetivo, tipoCrear,
                            duenoCrear != null ? duenoCrear : "admin",
                            permisoCrear != null ? permisoCrear : Permiso.PUBLIC,
                            asig, nombreProcesoCreador, color, padre);
                    padre.agregarHijo(nuevo);
                    versionEstructura++;
                }
                case UPDATE -> {
                    NodoFS objetivo = buscarNodoSinLock(rutaObjetivo);
                    if (objetivo != null && nuevoNombre != null && !nuevoNombre.isBlank()) {
                        objetivo.setNombre(nuevoNombre);
                        actualizarRutasRecursivo(objetivo);
                        versionEstructura++;
                    }
                }
                case DELETE -> {
                    NodoFS objetivo = buscarNodoSinLock(rutaObjetivo);
                    if (objetivo != null && objetivo.getPadre() != null) {
                        liberarRecursivo(objetivo);
                        objetivo.getPadre().eliminarHijo(objetivo);
                        versionEstructura++;
                    }
                }
                case READ -> { }
                default -> { }
            }
        } finally {
            mutexFS.release();
        }
    }

    private void liberarRecursivo(NodoFS nodo) {
        ListaEnlazada<NodoFS> hijos = nodo.getHijos();
        for (int i = 0; i < hijos.getTamano(); i++) {
            liberarRecursivo(hijos.obtener(i));
        }
        if (!nodo.esDirectorio() && nodo.getAsignacion() != null) {
            disco.liberar(nodo.getAsignacion());
            nodo.setAsignacion(null);
        }
    }

    private void actualizarRutasRecursivo(NodoFS nodo) {
        String rutaPadre = nodo.getPadre() == null ? "" : nodo.getPadre().getRutaCompleta();
        String nuevaRuta = "/".equals(rutaPadre) ? "/" + nodo.getNombre() : rutaPadre + "/" + nodo.getNombre();
        nodo.setRutaCompleta(nuevaRuta);
        if (!nodo.esDirectorio() && nodo.getAsignacion() != null) {
            disco.renombrarOcupante(nodo.getAsignacion(), nuevaRuta);
        }
        ListaEnlazada<NodoFS> hijos = nodo.getHijos();
        for (int i = 0; i < hijos.getTamano(); i++) {
            actualizarRutasRecursivo(hijos.obtener(i));
        }
    }

    public NodoFS buscarNodo(String ruta) {
        mutexFS.acquireUninterruptibly();
        try {
            return buscarNodoSinLock(ruta);
        } finally {
            mutexFS.release();
        }
    }

    private NodoFS buscarNodoSinLock(String ruta) {
        if (ruta == null || ruta.isBlank()) return null;
        if ("/".equals(ruta)) return raiz;
        String[] partes = ruta.substring(1).split("/");
        NodoFS actual = raiz;
        for (String parte : partes) {
            if (actual == null) return null;
            actual = actual.buscarHijoPorNombre(parte);
        }
        return actual;
    }

    public ListaEnlazada<NodoFS> listarTodosLosArchivos() {
        mutexFS.acquireUninterruptibly();
        try {
            ListaEnlazada<NodoFS> archivos = new ListaEnlazada<>();
            recolectarArchivos(raiz, archivos);
            return archivos;
        } finally {
            mutexFS.release();
        }
    }

    private void recolectarArchivos(NodoFS actual, ListaEnlazada<NodoFS> acumulador) {
        if (!actual.esDirectorio()) {
            acumulador.agregarAlFinal(actual);
        }
        ListaEnlazada<NodoFS> hijos = actual.getHijos();
        for (int i = 0; i < hijos.getTamano(); i++) {
            recolectarArchivos(hijos.obtener(i), acumulador);
        }
    }

    public SistemaCargado exportarSistemaCargado() {
        mutexFS.acquireUninterruptibly();
        try {
            ListaEnlazada<EntradaCargada> lista = new ListaEnlazada<>();
            recolectarEntradas(raiz, lista);
            EntradaCargada[] res = new EntradaCargada[lista.getTamano()];
            for (int i = 0; i < res.length; i++) {
                res[i] = lista.obtener(i);
            }
            return new SistemaCargado(disco, posicionesPorCiclo, res);
        } finally {
            mutexFS.release();
        }
    }

    private void recolectarEntradas(NodoFS actual, ListaEnlazada<EntradaCargada> lista) {
        if (!"/".equals(actual.getRutaCompleta())) {
            lista.agregarAlFinal(new EntradaCargada(
                    actual.getRutaCompleta(), actual.getTipo(),
                    actual.getDueno(), actual.getPermiso(), actual.getAsignacion()));
        }
        ListaEnlazada<NodoFS> hijos = actual.getHijos();
        for (int i = 0; i < hijos.getTamano(); i++) {
            recolectarEntradas(hijos.obtener(i), lista);
        }
    }

    private int contarBloquesLibresArchivo() {
        Disco.EstadoBloque[] estados = disco.obtenerMapaBloques();
        int libres = 0;
        for (Disco.EstadoBloque e : estados) {
            if (e == Disco.EstadoBloque.LIBRE) libres++;
        }
        return libres;
    }

    private int[] buscarPrimerosBloquesLibres(int cantidad) {
        Disco.EstadoBloque[] estados = disco.obtenerMapaBloques();
        int[] encontrados = new int[cantidad];
        int count = 0;
        for (int i = 0; i < estados.length && count < cantidad; i++) {
            if (estados[i] == Disco.EstadoBloque.LIBRE) {
                encontrados[count++] = i;
            }
        }
        if (count == cantidad) return encontrados;
        int[] parcial = new int[count];
        System.arraycopy(encontrados, 0, parcial, 0, count);
        return parcial;
    }

    private int buscarPrimerBloqueEnSubarbol(NodoFS nodo) {
        if (!nodo.esDirectorio() && nodo.getPrimerBloque() >= 0) {
            return nodo.getPrimerBloque();
        }
        ListaEnlazada<NodoFS> hijos = nodo.getHijos();
        for (int i = 0; i < hijos.getTamano(); i++) {
            int b = buscarPrimerBloqueEnSubarbol(hijos.obtener(i));
            if (b >= 0) return b;
        }
        return -1;
    }
}
