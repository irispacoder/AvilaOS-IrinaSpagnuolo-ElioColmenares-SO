# ÁvilaOS

Simulador de sistemas operativos en Java. El Proyecto 2 conserva la planificación de CPU, la RAM y las métricas del computador del Proyecto 1, y agrega un disco local con planificación de E/S y bloques de intercambio.

[Guía de estudio, pruebas y defensa de la Persona 1](GUIA-PERSONA-1.md)

## Ejecutar en NetBeans

1. Abre la carpeta `AvilaOS` como proyecto de NetBeans y ejecuta con JDK 25.
2. Carga [sistema-archivos.json](AvilaOS/ejemplos/sistema-archivos.json) con **Cargar disco JSON** y [procesos.json](AvilaOS/ejemplos/procesos.json) con **Cargar procesos JSON**. Son los ejemplos del enunciado.
3. Pulsa **Iniciar / Reanudar**. En **Disco local** puedes ver el mapa, la cola y el desplazamiento acumulado. Pasa el cursor sobre un bloque para ver su ocupante. Puedes cambiar la política mientras corre el reloj.
4. Usa **Guardar disco JSON** para exportar el mapa y cargarlo en otra ejecución. Para cargar otra configuración en la misma ventana, pulsa **Detener** primero.

El ejemplo importa ocho descriptores de E/S. Su creación como PCB, el sistema de archivos activo y la suspensión de procesos se conectan desde el módulo de la Persona 2. Mientras se completa esa integración, la ventana de prueba ejecuta el anexo directamente en el disco.

## Pruebas de la Persona 1

Desde la raíz del repositorio, con JDK 25, en macOS o Linux:

```sh
clases_avila=$(mktemp -d)
librerias_avila='jfreechart-1.0.19.jar:jcommon-1.0.23.jar:AvilaOS/lib/gson-2.13.2.jar'
find AvilaOS/src AvilaOS/test -name '*.java' -print0 |
  xargs -0 javac -encoding UTF-8 -cp "$librerias_avila" -d "$clases_avila"
cp_avila="$clases_avila:$librerias_avila"
java -cp "$cp_avila" sistema.DiscoPrueba
java -cp "$cp_avila" sistema.PlanificadorDiscoPrueba
java -cp "$cp_avila" persistencia.CargadorJSONPrueba
java -Djava.awt.headless=true -cp "$cp_avila" gui.PanelDiscoPrueba
java -cp "$cp_avila" gui.VentanaPrincipalPrueba
```

Las pruebas verifican los casos oficiales P1–P4, el anexo, la recarga de cadenas fragmentadas, entradas inválidas, cambio de política, pausa y reanudación. La última prueba abre y cierra la ventana principal y requiere una sesión gráfica.

Para practicar, ejecuta `java -cp "$cp_avila" gui.PanelDiscoPrueba --visual`. Selecciona una política y avanza ciclo a ciclo o ejecuta la cola del anexo. Reabre la ventana para comparar desde la misma posición inicial.

## Convenciones del disco

- La espera mide los ciclos desde que se encola una solicitud hasta que se selecciona. La búsqueda mueve hasta `headPositionsPerCycle` bloques por ciclo (por defecto, uno). Cada bloque transferido consume otro ciclo.
- C-SCAN omite el salto circular de la distancia acumulada. Un cambio de política se aplica al elegir la siguiente solicitud. Una solicitud en curso termina bajo la política que la seleccionó.
- Al cargar el disco se restauran los bloques de los archivos y se reservan para swap los bloques libres con mayores índices. Así se respetan los archivos del ejemplo ubicados en 119, 123 y 180.
- El JSON exportado usa `blockChain` para conservar los índices exactos de archivos fragmentados. Si un archivo importado solo indica `startBlock` y `blocks`, se conserva el primer bloque y se completa la cadena con los primeros bloques disponibles, respetando las ubicaciones explícitas de los demás archivos.

## Contrato con procesos de E/S y swap

`SesionProyecto2.obtenerProcesosIO()` expone los descriptores cargados para crear los PCB. El conector puede encolar una operación con sus bloques físicos, tipo y nombre de proceso usando `PlanificadorDisco.encolar(...)`. La solicitud devuelta tiene un ID para asociarla al PCB. `SesionProyecto2.alCompletarIO(...)` notifica la finalización en el hilo del reloj, después de liberar el semáforo de la cola. El conector aplica la operación y actualiza el estado del proceso. Para los cambios de estado persistentes, pasa las entradas actuales del sistema de archivos a `CargadorJSON.guardarSistemaArchivos(...)`.

Irina Spagnuolo · Sistemas Operativos 2627-1
