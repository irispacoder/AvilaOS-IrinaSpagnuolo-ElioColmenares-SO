# Pruebas manuales del Proyecto 2

Desde NetBeans abre AvilaOS y ejecuta el proyecto con JDK 25. Los archivos de esta guía están en AvilaOS/ejemplos/casos-prueba dentro del repositorio.

Carga disco-oficial.json con Cargar disco JSON y P1-FIFO.json con Cargar procesos JSON antes de iniciar.

## Primera ejecución

1. Abre Disco local y confirma cabezal 50, dirección ascendente, política FIFO y cero solicitudes atendidas.
2. Pulsa Iniciar / Reanudar. Al empezar debe aparecer el trabajo para los bloques 95, 180, 34, 119, 11, 123, 62 y 64. La petición seleccionada se muestra como En curso.
3. Pulsa Pausar a los pocos segundos. Verifica que el cabezal y el ciclo se estabilizan. Reanuda para continuar.
4. Espera hasta ver ocho solicitudes atendidas y pulsa Pausar. Con la configuración oficial de 300 ms por ciclo, P1 tarda aproximadamente 3 minutos y 16 segundos.
5. Abre Eventos. Anota el orden de los mensajes del disco, la distancia final y cualquier expulsión por deadline o error.

## Casos oficiales

Las páginas 16 y 17 del enunciado fijan la cola inicial 95, 180, 34, 119, 11, 123, 62 y 64, con cabezal en 50 y barrido ascendente. Las distancias de esta tabla se calcularon a partir del recorrido.

| Caso | Archivo de procesos | Orden esperado | Distancia en bloques |
|---|---|---|---:|
| P1 FIFO | P1-FIFO.json | 95 → 180 → 34 → 119 → 11 → 123 → 62 → 64 | 644 |
| P2 SSTF | P2-SSTF.json | 62 → 64 → 34 → 11 → 95 → 119 → 123 → 180 | 236 |
| P3 SCAN | P3-SCAN.json | 62 → 64 → 95 → 119 → 123 → 180 → 34 → 11 | 337 |
| P4 C-SCAN | P4-C-SCAN.json | 62 → 64 → 95 → 119 → 123 → 180 → 11 → 34 | 183 |

Para cada caso, pulsa Detener y vuelve a cargar disco-oficial.json y el archivo de procesos correspondiente antes de iniciar. Esto recupera el mismo mapa inicial, incluido el archivo que la operación DELETE elimina en la ejecución anterior. C-SCAN excluye el salto de regreso de la distancia.

## Casos borde para que los compruebes

Usa los archivos de esta carpeta. Detén la ejecución antes de cargar otra combinación.

| Prueba | Qué cargar o hacer | Qué observar |
|---|---|---|
| Cabezal fuera del disco | Cargar borde-cabezal-invalido.json como disco. | Debe rechazar initialHead 200 en un disco con índices 0 a 199 y permitir seguir usando la ventana. |
| Bloque compartido por dos archivos | Cargar borde-bloque-duplicado.json y P1-FIFO.json. | Debe rechazar el bloque 11 repetido. El inicio debe quedar deshabilitado con esa combinación. |
| Reserva de swap excesiva | Cargar disco-oficial.json y borde-swap-sin-espacio.json. | Debe rechazar 193 bloques de swap porque los ocho archivos solo dejan 192 libres. |
| Cola vacía | Cargar disco-oficial.json y borde-cola-vacia.json, e iniciar. | El reloj avanza con cero solicitudes atendidas y cero desplazamiento. |
| Disco lleno para archivos | Cargar borde-disco-lleno.json y borde-swap10-sin-procesos.json. | Hay 90 bloques de archivo y 10 reservados para swap. |
| Crear sin espacio | Con el caso anterior, seleccionar la raíz en Sistema de Archivos, pulsar Crear, elegir FILE, nombre extra.txt y tamaño 1. | Debe rechazar la creación por espacio insuficiente. |
| Liberación de bloques | En ese mismo disco, iniciar, seleccionar lleno.bin y Eliminar. Esperar la finalización de la E/S y volver a Crear extra.txt. | Debe liberar el archivo y permitir crear después. La eliminación también consume ciclos. |
| Protección de la raíz | En modo administrador seleccionar la raíz y pulsar Eliminar. | Debe rechazar la operación y conservar el árbol. |
| Archivo privado de otro usuario | Cargar disco-oficial.json con borde-cola-vacia.json. Elegir modo USUARIO, escribir user2, pulsar Enter, seleccionar notas.txt y Leer. | Debe rechazar la lectura porque pertenece a user1 y es privado. |
| Cambio de política | Durante una petición en curso, cambiar de FIFO a SSTF. | Debe terminar la activa y aplicar SSTF a la siguiente selección. Este ensayo cambia el orden de P1. Reinicia el caso oficial para compararlo de nuevo. |
| Guardar y recargar | Crear un archivo, esperar a que termine la E/S, guardar con un nombre nuevo y volver a cargar junto con borde-cola-vacia.json. | Debe conservar rutas y cadenas de bloques. |

## Regresión de procesos expirados

Con los valores oficiales, P1 supera el deadline de 500 ciclos de los últimos tres procesos. La corrección e92d78c descarta sus finalizaciones tardías para que permanezcan terminados. Las solicitudes físicas completan el recorrido FIFO de 644 bloques y ocho atenciones. La prueba sistema.DeadlineIOPrueba comprueba ocho terminaciones únicas, cola de Listos vacía y RAM liberada al final.

## Registro de verificación manual

El estudiante confirmó el orden FIFO, 644 bloques y ocho solicitudes atendidas el 25 de noviembre de 2026. P2, P3, P4, los casos borde y la repetición manual de P1 con la corrección de deadline quedan pendientes de confirmación del estudiante.
