# Informe de Pruebas

**Sistema Distribuido de Gestión de Pedidos Tolerante a Fallos**

Proyecto Final de Sistemas Distribuidos

- Integrantes: Daniel De Jesus Codina Ortiz, Moises David Zambrano y Juan Pablo Gutierez
- Docente: Helbert Fernando Valencia Barrero

## 1. Entorno de pruebas

| Elemento | Detalle |
|---|---|
| Fecha de ejecución | 8 de noviembre de 2026 |
| Sistema operativo | Windows 11 con Docker Desktop (Docker 29.7.2, Compose v5.4.0) |
| Sistema bajo prueba | Cinco contenedores levantados con `docker compose up -d --build --wait` |
| Herramientas | JUnit 6.0.3, Maven 3.9.16 (Surefire para unitarias y Failsafe para integración y fallo), JDK 26 |
| Comando | `mvnw.cmd verify` desde la carpeta `backend/` |

Las pruebas de integración y de fallo se ejecutan contra el sistema real a través de NGINX (`http://localhost:8080/api`), sin simular componentes.

## 2. Pruebas unitarias

Archivo: `backend/src/test/java/pedidos/DatosPedidoTest.java`. Verifican las reglas de validación de los pedidos sin necesidad de levantar el sistema.

| ID | Prueba | Objetivo | Resultado |
|---|---|---|---|
| PU-01 | `aceptaUnPedidoSinEstado` | Un pedido con cliente, producto y cantidad válidos y sin estado es aceptado | Aprobada |
| PU-02 | `aceptaLosCuatroEstados` (4 casos) | Se aceptan `pendiente`, `en proceso`, `enviado` y `entregado` | Aprobada (4/4) |
| PU-03 | `rechazaUnEstadoDesconocido` | Se rechaza un estado fuera de los cuatro permitidos | Aprobada |
| PU-04 | `rechazaCantidadesFueraDeRango` (2 casos) | Se rechazan las cantidades 0 y 10001 | Aprobada (2/2) |
| PU-05 | `rechazaDatosObligatoriosVaciosOAusentes` | Se rechazan un cliente con solo espacios, un producto ausente y una cantidad ausente | Aprobada |
| PU-06 | `rechazaTextosDeMasDeCienCaracteres` | Se rechaza un producto de 101 caracteres | Aprobada |
| PU-07 | `elCambioDeEstadoExigeUnEstadoPermitido` | El cambio de estado acepta `enviado` y rechaza un estado ausente o desconocido | Aprobada |

## 3. Pruebas de integración

Archivo: `backend/src/test/java/pedidos/IntegracionIT.java`. Recorren el sistema completo: NGINX, nodos backend y bases de datos.

| ID | Prueba | Objetivo | Resultado |
|---|---|---|---|
| PI-01 | `cicloCompletoDeUnPedido` | Crear (201, estado `pendiente`), actualizar cantidad y estado (200), editar sin enviar estado y comprobar que se conserva, verificar en la lista, eliminar (204) y obtener 404 al repetir la eliminación o actualizar un pedido inexistente | Aprobada |
| PI-02 | `datosInvalidosDevuelven400` | La API responde 400 ante cantidad 0, cantidad decimal, estado desconocido, campo no esperado (`id`), JSON malformado e identificador fuera de rango | Aprobada |
| PI-03 | `elBalanceadorRepartePorLosDosNodos` | En 6 peticiones responden `nodo-1` y `nodo-2` | Aprobada |
| PI-04 | `healthInformaLasDosBasesDisponibles` | `/api/health` informa la primaria y la réplica disponibles | Aprobada |
| PI-05 | `losCambiosSeReplicanEnLaReplica` | Un pedido creado aparece en la réplica y, al eliminarlo, desaparece de ella | Aprobada |
| PI-06 | `cambiarElEstadoConservaLosDemasDatos` | Tras editar la cantidad, cambiar solo el estado (`PUT /api/pedidos/{id}/estado`) responde 200 y conserva la cantidad; un estado desconocido da 400 y un pedido inexistente 404 | Aprobada |

## 4. Pruebas de fallo

Archivo: `backend/src/test/java/pedidos/FallosIT.java`. Detienen contenedores reales con Docker Compose y los vuelven a levantar al terminar.

### PF-01: caída de un nodo backend

| Paso | Resultado esperado | Resultado obtenido |
|---|---|---|
| `docker compose stop backend1` | El contenedor se detiene | Detenido |
| 4 × `GET /api/pedidos` | 200, atendidas por `nodo-2` | 200 en las 4, todas por `nodo-2` |
| `POST /api/pedidos` | 201, atendida por `nodo-2` | 201 por `nodo-2` |
| `docker compose start backend1` | `nodo-1` vuelve a atender en menos de 60 s | `nodo-1` volvió a responder |
| Eliminar el pedido de prueba | 204 | 204 |

### PF-02: caída de la base de datos primaria

| Paso | Resultado esperado | Resultado obtenido |
|---|---|---|
| Crear un pedido y esperar que llegue a la réplica | Presente en la réplica | Presente |
| `docker compose stop db-primary` | La primaria se detiene | Detenida |
| `GET /api/pedidos` | 200 con el pedido, leído desde la réplica | 200, pedido presente |
| `POST /api/pedidos` | 503 con `Base de datos primaria no disponible: modo solo lectura` | 503 con ese mensaje |
| `PUT /api/pedidos/{id}/estado` | 503 con el mismo mensaje | 503 con ese mensaje |
| `GET /api/health` | `primaria: false`, `replica: true` | `false` y `true` |
| `docker compose start db-primary` y eliminar el pedido | 204 cuando la primaria vuelve | 204 |
| Consultar la réplica | El pedido eliminado ya no está | Ya no está |

### Validación de la prueba de fallo

Para confirmar que PF-02 detecta un fallo real, se desactivó temporalmente la lectura desde la réplica en `PedidoRepositorio.java`, se reconstruyeron los nodos y se repitió la prueba. Falló con `expected: <200> but was: <503>` en la consulta de pedidos: sin el mecanismo de tolerancia, el sistema no responde. Luego se restauró el código y la prueba volvió a aprobarse.

## 5. Resultados

### 5.1 Ejecución automatizada

Extracto de la salida de `mvnw.cmd verify`:

```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.382 s -- in pedidos.DatosPedidoTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 43.68 s -- in pedidos.FallosIT
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.642 s -- in pedidos.IntegracionIT
[INFO] BUILD SUCCESS
```

| Tipo | Pruebas | Aprobadas |
|---|---|---|
| Unitarias | 11 casos (7 pruebas) | 11 |
| Integración | 6 | 6 |
| Fallo | 2 | 2 |
| **Total** | **19** | **19** |

### 5.2 Verificaciones complementarias

| Verificación | Resultado |
|---|---|
| Compilación del backend con todas las advertencias de `javac` tratadas como errores (`-Xlint:all -Werror`) | Sin advertencias |
| Compilación del frontend en modo estricto de TypeScript y plantillas (`ng build`) | Sin errores ni advertencias |
| Vulnerabilidades en dependencias del backend (49 dependencias consultadas en la base pública OSV) | No se encontraron vulnerabilidades conocidas |
| Vulnerabilidades en dependencias del frontend (`npm audit`) | 0 vulnerabilidades |
| Peticiones maliciosas a la API (inyección SQL, campos no esperados, decimales, JSON malformado, métodos no permitidos, cuerpo de 20 KB, recorrido de rutas) | Todas rechazadas con 400, 405, 413 o 415; los textos con código SQL o HTML se guardan como texto sin ejecutarse |
| Uso de la interfaz en navegador (Microsoft Edge a 1440, 1024 y 375 px de ancho) | Creación, cambio de estado, edición, validación y eliminación funcionaron; la página no se desborda en ninguno de los tres anchos |
| Dos ventanas sobre el mismo pedido: se edita la cantidad en una y se cambia el estado en la otra, sin recargarla | Se guardan los dos cambios: la cantidad editada se conserva |
| Accesibilidad de la interfaz (contraste, teclado y foco) | Contraste mínimo de 6,24:1; todos los controles se usan con teclado y muestran el foco; al eliminar un pedido el foco pasa al botón **Deshacer** y, cuando el aviso desaparece, al título de la lista |

## 6. Conclusiones

- Las 19 pruebas se aprobaron: la gestión de pedidos cumple con crear, listar, actualizar, eliminar y controlar los cuatro estados.
- El cambio de estado modifica solo el estado, por lo que no sobrescribe los datos que otro usuario haya editado en el mismo pedido.
- El balanceador NGINX reparte la carga entre los dos nodos y, ante la caída de uno, el otro atiende todas las peticiones sin errores visibles para el usuario. El nodo caído se reincorpora solo al volver.
- La replicación por streaming mantiene la réplica sincronizada con la primaria. Ante la caída de la primaria, el sistema sigue mostrando los pedidos desde la réplica y bloquea las modificaciones con un mensaje claro, sin perder ni corromper datos.
- Al restablecer la primaria, las escrituras y la replicación se recuperan sin reiniciar el resto de los componentes.
- Limitación: la réplica no se promueve automáticamente a primaria, por lo que las modificaciones quedan suspendidas mientras la primaria esté detenida.
