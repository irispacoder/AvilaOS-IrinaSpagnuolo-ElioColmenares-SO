# Guía de estudio y pruebas de ÁvilaOS Proyecto 2

Tu parte del proyecto simula el disco local de un computador. El disco administra bloques para archivos y para intercambio de memoria, recibe solicitudes de entrada y salida (E/S) y decide cuál atender según la política seleccionada. La aplicación carga la configuración y los datos desde dos archivos JSON. En la ventana puedes observar cómo cambian el cabezal, la cola y las métricas a medida que avanza el reloj.

## El disco y sus bloques

El disco está dividido en bloques numerados. En un disco de 200 bloques, las posiciones van de `0` a `199`. El **cabezal** es la posición desde la que el disco puede leer o escribir. Si está en `50` y necesita acceder a `82`, primero debe recorrer 32 posiciones.

Un archivo puede ocupar bloques dispersos. En la **asignación encadenada**, cada bloque guarda el número del siguiente. Por ejemplo, con la cadena `31 → 4 → 26`, la entrada del archivo identifica `31` como primer bloque. A partir de ahí, el disco puede recorrer o liberar la cadena completa. En el código, `Disco.asignarArchivo()` reúne los bloques libres que necesita antes de enlazarlos. Si el espacio es insuficiente, el mapa conserva su estado.

El **swap** es la zona reservada para guardar temporalmente procesos que salen de la RAM. ÁvilaOS restaura primero las cadenas de archivos y luego reserva los bloques libres de mayor índice para swap. En el ejemplo oficial se reservan 100 bloques y se conservan los archivos de `119`, `123` y `180` en sus posiciones originales. [Código de Disco](AvilaOS/src/sistema/Disco.java)

**Pregunta de defensa.** ¿Cómo puede un archivo de tres bloques ocupar `31`, `4` y `26`? El mapa almacena el siguiente bloque de cada eslabón. El primer bloque y esos enlaces permiten recuperar el orden completo.

## La cola de E/S y las políticas

Una `SolicitudIO` identifica la operación, su origen, los bloques que requiere y su ciclo de llegada. La interfaz `IDiskScheduler` permite que `PoliticaDisco` decida el siguiente destino a partir del cabezal y las peticiones pendientes. `PlanificadorDisco` ejecuta esa decisión y atiende una solicitud a la vez. [SolicitudIO](AvilaOS/src/modelo/SolicitudIO.java), [Políticas](AvilaOS/src/planificadores/PoliticaDisco.java), [Planificador](AvilaOS/src/sistema/PlanificadorDisco.java)

| Política | Cómo elige |
|---|---|
| FIFO | Atiende por orden de llegada. |
| SSTF | Elige el primer bloque más cercano al cabezal. Ante un empate, elige la solicitud más antigua. |
| SCAN | Atiende en la dirección del barrido. Cuando se acaban las solicitudes en esa dirección y quedan otras al lado contrario, llega al extremo e invierte el sentido. |
| C-SCAN | Atiende en una dirección fija. Cuando debe continuar al otro lado, llega al extremo, salta al extremo opuesto y reanuda el barrido. El salto queda fuera de la distancia acumulada. |

Un ciclo de disco mueve el cabezal hasta el límite configurado por `headPositionsPerCycle` o transfiere un bloque si el cabezal ya está en su posición al empezar el ciclo. El valor por defecto es una posición por ciclo. La solicitud se completa al transferir el último bloque. Si cambias la política con una solicitud en curso, esta termina bajo la política que la seleccionó. La nueva política decide a partir de la próxima selección.

| Métrica | Qué cuenta |
|---|---|
| Desplazamiento acumulado | Suma de las posiciones recorridas durante las búsquedas y los barridos. |
| Espera media | Promedio, entre las solicitudes completadas, de los ciclos que cada una pasó pendiente antes de ser seleccionada. |
| Solicitudes atendidas por política | Operaciones completas atribuidas a la política que las seleccionó. |

**Pregunta de defensa.** Si una lectura se elige de inmediato y su bloque está a 32 posiciones, ¿cuánto esperó en cola? Cero ciclos. Con velocidad de una posición por ciclo, la búsqueda toma 32 ciclos y la transferencia ocupa un ciclo adicional.

## Los JSON, el reloj y la ventana

| Archivo | Qué inicializa |
|---|---|
| [sistema-archivos.json](AvilaOS/ejemplos/sistema-archivos.json) | Capacidad del disco, posición del cabezal, rutas, tipos de entrada, dueños, permisos y bloques de los archivos. |
| [procesos.json](AvilaOS/ejemplos/procesos.json) | Duración del ciclo, tamaño del swap, política de disco inicial, RAM y política de CPU, y descriptores de procesos. |

`CargadorJSON` valida los datos y reconstruye el mapa. Al guardar, escribe `blockChain` con el orden exacto de los bloques para recuperar archivos fragmentados. Si la entrada importada solo tiene `startBlock` y `blocks`, conserva el primero y completa la cadena con bloques disponibles, respetando las posiciones explícitas de los demás archivos. [CargadorJSON](AvilaOS/src/persistencia/CargadorJSON.java)

```text
Dos JSON → CargadorJSON → SesionProyecto2 → computador y disco local
RelojGlobal → ClusterManager → Disco.onClockTick → PlanificadorDisco
PlanificadorDisco y mapa del disco → PanelDisco → cola, cabezal y gráfica
```

El computador trabaja en su propio hilo. El reloj espera a que termine el ciclo del computador y luego avanza el disco. Los semáforos protegen el acceso al mapa y a la cola cuando el simulador y la interfaz consultan o cambian sus datos. `PanelDisco` muestra el estado y envía al planificador la política elegida en el selector. [Reloj global](AvilaOS/src/sistema/RelojGlobal.java), [Sesión](AvilaOS/src/sistema/SesionProyecto2.java), [Vista del disco](AvilaOS/src/gui/PanelDisco.java)

**Pregunta de defensa.** ¿Qué ocurre con `Proc_95` al cargar el JSON? La sesión conserva su descriptor con operación `READ` y ruta `/sistema/config.sys`. La Persona 2 conecta ese descriptor al PCB para que, al obtener CPU, el proceso genere la solicitud, se bloquee y regrese a Listo cuando el disco termine. Para probar el disco ahora, la ventana de práctica coloca las solicitudes directamente en la cola.

## Pruebas que puedes ejecutar

### Carga y guardado en NetBeans

1. Abre `AvilaOS` con JDK 25 y pulsa **Run**. Comprueba que **Iniciar / Reanudar** aparece deshabilitado.
2. Carga solo `sistema-archivos.json`. Comprueba que el botón sigue deshabilitado. Carga también `procesos.json`.
3. En **Disco local**, verifica 200 bloques, cabezal en `50` y política FIFO. Pasa el cursor por `180`. Debe aparecer `/usuarios/video.mp4`. La cola aparece vacía mientras la conexión con los PCB está pendiente.
4. Pulsa **Iniciar / Reanudar**. Comprueba que sube el contador de ciclos. Pulsa **Pausar** y observa que se estabiliza. Reanuda para comprobar que sigue avanzando.
5. Usa **Guardar disco JSON** con un nombre nuevo, detén el reloj y carga esa copia. Verifica que los archivos conservan sus bloques. Si abres la copia con un editor, busca `blockChain`.

### Comparación del anexo

Prepara las clases y ejecuta las pruebas automáticas con los comandos del README. En la misma terminal abre la demostración visual. [Comandos de compilación y pruebas](README.md#pruebas-de-la-persona-1)

```sh
java -cp "$cp_avila" gui.PanelDiscoPrueba --visual
```

El cabezal empieza en `50`, en dirección ascendente. Las solicitudes llegan en el orden `82, 17, 43, 140, 24, 16, 190`. Selecciona una política antes de pulsar **Avanzar un ciclo** o **Ejecutar**. Abre una ventana nueva para cada política y compara el resultado al terminar las siete solicitudes.

| Política | Orden de atención esperado | Distancia |
|---|---|---:|
| FIFO | 82 → 17 → 43 → 140 → 24 → 16 → 190 | 518 |
| SSTF | 43 → 24 → 17 → 16 → 82 → 140 → 190 | 208 |
| SCAN | 82 → 140 → 190 → 43 → 24 → 17 → 16 | 332 |
| C-SCAN | 82 → 140 → 190 → 16 → 17 → 24 → 43 | 192 |

En SCAN, suma `(199 − 50) + (199 − 16)`. En C-SCAN, suma `(199 − 50) + (43 − 0)`. El bloque `199` es el extremo del barrido en los dos casos.

## Simulacro de defensa

1. El cabezal parte de `50`, con velocidad de tres posiciones por ciclo. Llega una lectura de la cadena `55 → 54`. Anota posición, movimiento y transferencia por ciclo. ¿Cuándo termina y qué distancia suma?
2. En el mismo escenario, una segunda solicitud llega justo después del ciclo 2. ¿Cuántos ciclos pasa en cola si se selecciona al comenzar el ciclo 6?
3. En una copia del JSON del disco, cambia el `startBlock` de `/usuarios/notas.txt` de `34` a `11` y cárgala junto con el JSON de procesos. Predice qué debería mostrar la ventana y explica por qué.

### Respuestas para comprobar después

1. Ciclo 1, avance a `53`. Ciclo 2, avance a `55`. Ciclo 3, lectura en `55`. Ciclo 4, avance a `54`. Ciclo 5, lectura en `54` y fin de la solicitud. La distancia acumulada es `3 + 2 + 1 = 6` bloques.
2. Espera tres ciclos completos, el 3, el 4 y el 5.
3. La ventana debe señalar que el bloque `11` se repite y deshabilitar el inicio con esa combinación. Ya está asignado a `/sistema/boot.bin`.
