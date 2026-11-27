# ÁvilaOS · Proyecto 2: Sistema de Archivos, Planificación de Disco y Suspensión de Procesos

Simulador de Sistema Operativo desarrollado en **Java 21+ (NetBeans / Swing)** para la asignatura **Sistemas Operativos** (Trimestre 2627-1). Extiende el núcleo de planificación de CPU y concurrencia del Proyecto 1 incorporando un disco simulado local, un sistema de archivos jerárquico con asignación encadenada, planificación de solicitudes de E/S y gestión de memoria virtual mediante el modelo de siete estados[cite: 1, 2].

---

## Características Principales

* **Modelo de 7 Estados y Planificador a Mediano Plazo:**
  * Implementa los estados `NUEVO`, `LISTO`, `EJECUCION`, `BLOQUEADO`, `LISTO_SUSPENDIDO`, `BLOQUEADO_SUSPENDIDO` y `TERMINADO`[cite: 1, 2].
  * Cuando la memoria principal (RAM) es insuficiente para admitir un proceso nuevo, el planificador a mediano plazo selecciona el proceso `BLOQUEADO` con mayor tiempo en espera, lo escribe en el área de intercambio (*swap*) del disco y libera su RAM. Al liberarse memoria, los procesos en `LISTO_SUSPENDIDO` son reactivados mediante solicitudes de E/S[cite: 1, 2].
* **Disco Simulado (SD) y Asignación Encadenada:**
  * Disco configurable (100 a 500 bloques) con reserva dinámica de área *swap* al final del espacio de bloques[cite: 1, 2].
  * Gestión de archivos mediante **asignación encadenada**, permitiendo fragmentación no contigua, liberación recursiva de bloques y visualización en tiempo real de ocupación (Libre, Archivo, Swap Libre, Swap Ocupado), posición y dirección del cabezal[cite: 1, 2].
* **Planificación de Disco (Interfaz `IDiskScheduler`):**
  * Soporta las políticas **FIFO**, **SSTF**, **SCAN** y **C-SCAN**, intercambiables en caliente durante la ejecución[cite: 1, 2].
  * Incluye gráfica en tiempo real (JFreeChart) del desplazamiento acumulado del cabezal y métricas de tiempo promedio de espera y solicitudes atendidas por política[cite: 1, 2].
* **Sistema de Archivos Jerárquico y Control de Acceso:**
  * Visualización mediante `JTree` y tabla de asignación (`JTable`) sincronizada en tiempo real con códigos de color por archivo[cite: 1, 2].
  * Operaciones CRUD (`CREATE`, `READ`, `UPDATE`, `DELETE`) ejecutadas exclusivamente a través de procesos de E/S (`IO_BOUND`) en modo Kernel (`SISTEMA_OPERATIVO`), nunca directamente desde la interfaz[cite: 1, 2].
  * Control de permisos (`PUBLIC` / `PRIVATE`) y modos de operación (**Administrador** y **Usuario**) con validaciones de seguridad[cite: 1, 2].
* **Persistencia en JSON:**
  * Carga inicial obligatoria mediante dos archivos JSON independientes (configuración del disco/sistema de archivos y configuración global/procesos) y exportación atómica del estado actualizado del disco[cite: 1, 2].

---

## Arquitectura del Proyecto

El sistema sigue una arquitectura desacoplada donde la interfaz gráfica observa el estado de la simulación sin contener lógica del sistema operativo[cite: 1, 2]:

* `avilaos` — Punto de entrada de la aplicación (`AvilaOS`)[cite: 1].
* `avilaos.estructuras` — Estructuras de datos propias sincronizadas (`ListaEnlazada`, `Cola`, `Nodo`), sin uso de colecciones de `java.util`[cite: 1, 2].
* `enums` — Definiciones de estados (`EstadoProcesos`), modos de CPU y usuario (`ModoCPU`, `ModoUsuario`), motivos de bloqueo (`MotivoBloqueo`), operaciones de E/S (`OperacionIO`) y políticas de CPU (`PoliticaPlanificacion`)[cite: 1, 2].
* `interfaces` — Contratos de desacoplamiento (`IScheduler`, `IDiskScheduler`, `IClockListener`, `IConsumidor`)[cite: 1, 2].
* `modelo` — Entidades del núcleo (`Proceso`, `SolicitudIO`, `MetricasComputador`)[cite: 1].
* `planificadores` — Algoritmos de CPU (`FCFSScheduler`, `RRScheduler`, `EDFScheduler`, `PrioridadesScheduler`) y de disco (`PoliticaDisco`)[cite: 1].
* `sistema` — Orquestación y hardware simulado (`RelojGlobal`, `ClusterManager`, `Computador`, `Disco`, `PlanificadorDisco`, `SistemaArchivos`, `NodoFS`, `Semaforo`, `SesionProyecto2`)[cite: 1].
* `persistencia` — Lectura, validación y guardado atómico de configuraciones (`CargadorJSON`, `ConfigManager`)[cite: 1].
* `gui` — Vistas en Swing (`VentanaPrincipal`, `PanelComputadorView`, `PanelDisco`, `PanelSistemaArchivos`, `PanelGraficoMetricas`)[cite: 1].

---

## Requisitos y Ejecución

* **Java:** JDK 21 o superior[cite: 2].
* **IDE:** Apache NetBeans[cite: 2].
* **Librerías incluidas:** `Gson` (manejo de JSON) y `JFreeChart` (gráficas de métricas en tiempo real)[cite: 1, 2].

### Pasos para ejecutar
1. Abrir el proyecto en **NetBeans** y verificar que la clase principal sea `avilaos.AvilaOS`[cite: 1, 2].
2. Ejecutar la aplicación[cite: 2].
3. En la barra superior, cargar ambos archivos de configuración:
   * **Cargar disco JSON** (ej. `sistema-archivos.json`)[cite: 1, 2].
   * **Cargar procesos JSON** (ej. `procesos.json`)[cite: 1, 2].
4. Presionar **Iniciar / Reanudar** para comenzar la simulación[cite: 1, 2].

Irina Spagnuolo - Elio Colmenares - 2627-1
