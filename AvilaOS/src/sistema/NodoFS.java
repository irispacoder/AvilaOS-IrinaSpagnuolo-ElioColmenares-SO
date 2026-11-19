/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package sistema;

import avilaos.estructuras.ListaEnlazada;
import java.awt.Color;
import persistencia.CargadorJSON.Permiso;
import persistencia.CargadorJSON.TipoEntrada;
import sistema.Disco.Asignacion;
/**
 *
 * @author ispam
 */
public class NodoFS {
    private String nombre;
    private String rutaCompleta;
    private final TipoEntrada tipo;
    private final String dueno;
    private Permiso permiso;
    private Asignacion asignacion;
    private final String procesoCreador;
    private final Color color;
    private final NodoFS padre;
    private final ListaEnlazada<NodoFS> hijos;

    public NodoFS(String nombre, String rutaCompleta, TipoEntrada tipo, String dueno,
                  Permiso permiso, Asignacion asignacion, String procesoCreador,
                  Color color, NodoFS padre) {
        this.nombre = nombre;
        this.rutaCompleta = rutaCompleta;
        this.tipo = tipo;
        this.dueno = dueno;
        this.permiso = permiso;
        this.asignacion = asignacion;
        this.procesoCreador = procesoCreador != null ? procesoCreador : "JSON Inicial";
        this.color = color;
        this.padre = padre;
        this.hijos = new ListaEnlazada<>();
    }

    public boolean esDirectorio() {
        return tipo == TipoEntrada.DIRECTORY;
    }

    public int getTamanoBloques() {
        return asignacion == null ? 0 : asignacion.cantidadBloques();
    }

    public int getPrimerBloque() {
        return asignacion == null ? -1 : asignacion.primerBloque();
    }

    public void agregarHijo(NodoFS hijo) {
        hijos.agregarAlFinal(hijo);
    }

    public boolean eliminarHijo(NodoFS hijo) {
        return hijos.eliminar(hijo);
    }

    public NodoFS buscarHijoPorNombre(String nombreBuscado) {
        for (int i = 0; i < hijos.getTamano(); i++) {
            NodoFS h = hijos.obtener(i);
            if (h.getNombre().equals(nombreBuscado)) {
                return h;
            }
        }
        return null;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRutaCompleta() { return rutaCompleta; }
    public void setRutaCompleta(String rutaCompleta) { this.rutaCompleta = rutaCompleta; }
    public TipoEntrada getTipo() { return tipo; }
    public String getDueno() { return dueno; }
    public Permiso getPermiso() { return permiso; }
    public void setPermiso(Permiso permiso) { this.permiso = permiso; }
    public Asignacion getAsignacion() { return asignacion; }
    public void setAsignacion(Asignacion asignacion) { this.asignacion = asignacion; }
    public String getProcesoCreador() { return procesoCreador; }
    public Color getColor() { return color; }
    public NodoFS getPadre() { return padre; }
    public ListaEnlazada<NodoFS> getHijos() { return hijos; }

    @Override
    public String toString() {
        return nombre;
    }
}
