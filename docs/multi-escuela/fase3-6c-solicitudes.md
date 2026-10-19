# Entrega 6c (fase 3) — solicitudes al propietario y encargos

Diseño para planificar · 19/10/2026 · base `develop` en `de7bad89`. Solo diseño: no hay código ni migraciones escritas.
Rutas relativas a `src-api/src/main/java/com/taemoi/project/` (backend), `src-frontend/src/app/` (frontend) y
`src-api/src/main/resources/db/migration/` (migraciones). Parte de: `fase3-sedes-permisos.md` §7 (decisiones 180 y 184),
`entrega6-plan.md` §7 y §8 (D6-15, D6-20, D6-22, decisión 169 y 172) y `matriz-endpoints.md`.

Abreviaturas: P = PROPIETARIO (`ROLE_ADMIN`), G = GESTOR (`ROLE_MANAGER`), R = RESPONSABLE_SEDE
(`ROLE_RESPONSABLE_SEDE`), Pr = PROFESOR (`ROLE_PROFESOR`), F = FAMILIA (`ROLE_USER`). A1–A17 son las acciones
sensibles de hoy (§2). «Al constructor» = lo decide quien lo implemente. Las decisiones técnicas del diseñador (§4.3)
están aceptadas; el constructor puede cambiarlas solo donde se indica.

**Resumen.** Quien no puede hacer un cambio sensible lo pide, con motivo, y el propietario lo acepta o lo deniega; al
aceptar, la aplicación lo ejecuta sola con los servicios de siempre. Además, el profesor pide encargos de productos
del catálogo para sus alumnos y sigue su recorrido hasta dárselos. La 6c cierra dos agujeros (A3, A13; decisiones 186,
187 y 194) y define la devolución como un movimiento enlazado al cargo que no anula el cobro. Una tabla nueva (`solicitud`) y columnas
nuevas en `movimiento_caja`, ambas en V31_8. Sin avisos en tiempo real ni correos.

---

## 1. Intención y porqué

**Sierra Sur** (un club, seis sedes, tarifas comunes) trabaja con responsables de sede y profesores que cobran en
efectivo; el dueño no está delante. Hoy, cuando un gestor o un profesor se equivoca con un cobro ya cerrado, la
aplicación responde 403 o 409 y no ofrece salida: el arreglo depende de una llamada y de que el propietario repita la
operación a mano. Tampoco hay forma de que un profesor pida material (un dobok de una talla) para un alumno: solo
vende lo marcado «Lo puede vender el profesor» y lo cobra en el acto.

**Moiskimdo** (organización 1, producción, 800+ alumnos) solo tiene propietario y gestión. El gestor ya choca con el
mismo límite: revertir un cobro en la tesorería o en la ficha es solo del propietario (`ProductoAlumnoServiceImpl.java:661-662`).

La idea: **lo sensible pasa por el propietario sin frenar el día a día.** Lo que hoy se hace directamente sigue igual
(salvo los bloqueos de las decisiones 186, 187 y 194); lo que hoy se rechaza pasa a poder pedirse.

No hay ningún incidente real que lo motive. Es preparación para Sierra Sur.

## 2. Estado actual (resumen del inventario)

| # | Acción | Quién puede hoy | Qué cambia en la 6c |
|---|---|---|---|
| A1 | Revertir un cobro desde tesorería o ficha (`PUT /api/productos-alumno/{id}` con `pagado:false`) | Solo P (servicio, `ProductoAlumnoServiceImpl.java:661-662`); cobro en una entrega → 409 (`CobroService.java:198-212`) | G y R lo **piden** (tipo 1), también sobre cargos pagados antes de la 6b-1 sin fila `cobro` (191) |
| A2 | Desmarcar el pago del examen en la convocatoria (`PUT /api/convocatorias/alumno/{id}`) | P, G, R (`ConvocatoriaServiceImpl.java:238-298`) | Desmarcar, nada (decisión 154). Cambiar la cuantía con el examen pagado, **bloqueado** (194) |
| A3 | Quitar a un alumno de una convocatoria (`DELETE /api/alumnos/{convocatoriaId}/alumno/{alumnoId}`): borra el cargo del examen aunque esté pagado (`AlumnoServiceImpl.java:2207-2222`) | P, G, R | **Bloqueado** si el examen está pagado (186) |
| A4 | Deshacer un cobro del profesor sin entregar (`POST /api/caja/profesor/cobros/{cobroId}/deshacer`) | Pr, su cobro, sin entrega | Nada |
| A5 | Deshacer un cobro del profesor ya entregado | Nadie (exige anular la entrega) | Pr lo **pide** (tipo 2) |
| A7 / A9 | Anular una entrega de caja / un movimiento (`POST /api/caja/entregas/{id}/anular`, `/api/caja/movimientos/{id}/anular`) | Solo P | G y R lo **piden** (tipo 3) |
| A8 | Movimiento de caja (DEVOLUCION, ERROR, SOBRANTE, FALTANTE, OTRO) | P, G, R (`CajaService.java:470-538`) | La DEVOLUCION puede enlazar un cobro (153) |
| A10 / A12 | Cancelar / borrar un cargo | Cancelar P, G, R; borrar solo P y sin cobros | 409 si el cargo es de un encargo en curso (§6.3) |
| A13 | Cambiar precio o cantidad de un cargo cobrado | P y G (R solo notas, D6-20) | **Bloqueado** con cobro activo (187) |
| A17 | Vender un producto no marcado | Pr no puede | Pr lo **encarga** (tipo 4) |

**No existe** ninguna entidad de aviso, bandeja ni notificación (sin SSE ni WebSocket). Las devoluciones solo son el
movimiento DEVOLUCION (A8), que no toca cargo ni cobro. No hay pagos parciales: un cobro ACTIVO por cargo
(`uk_cobro_activo_por_cargo`, V31_5).

**Dato que lo condiciona:** el responsable de sede **todavía no entra en la aplicación** (D6-15): tiene API, sin
pantallas hasta la E9. Hasta entonces, quien atiende con pantalla es P o G.

## 3. Objetivos, no-objetivos y anti-objetivos

**Objetivos**
1. Que G, R y Pr puedan pedir al propietario los cambios que hoy les dan 403/409, con motivo, y ver la respuesta.
2. Que el propietario los acepte (y la aplicación los ejecute) o los deniegue desde una bandeja.
3. Encargos de productos del catálogo pedidos por el profesor, con su cargo y su recorrido hasta el alumno.
4. Devolución que deja en el historial el cobro y la devolución, y el cargo como «devuelto» (153).
5. Cerrar A3 (186) y A13 (187 y 194).

**No-objetivos**
- Avisos en tiempo real, polling o SSE: son de la fase «Panel en tiempo real». El contador se refresca al entrar y al
  navegar.
- Correos (internos o a familias).
- Que la familia vea sus cargos o encargos. Hoy la familia **no** ve sus cargos: ningún `@PreAuthorize` de
  `ProductoAlumnoController` admite `ROLE_USER` (`controllers/ProductoAlumnoController.java:45-296`). Sería una
  entrega aparte.
- Cambios en la decisión 154: desmarcar pagos desde la convocatoria sigue igual, directo para P, G y R.
- Pantallas del responsable de sede (E9). Su API sí se declara (§7).
- Solicitudes para borrar cargos, cambiar precios o hacer devoluciones sueltas: siguen siendo directas para quien ya
  puede (188).

**Anti-objetivos**
- Que Moiskimdo pierda algo de lo que hace hoy (fuera de 186, 187 y 194, aceptadas).
- Que la caja de sede o la tesorería dejen de cuadrar: cada ejecución pasa por los servicios de siempre y conserva sus
  invariantes (cancelado ⇒ sin cobro ACTIVO; entregado ⇒ 409; un cobro ACTIVO por cargo).

## 4. Decisiones

### 4.1 Del propietario

- **Decisión 155.** Quien no puede hacer un cambio sensible lo pide y el propietario o quien corresponda lo acepta o
  deniega.
- **Decisión 183 (encargos, concretada el 19/10/2026).** El profesor pide un encargo de un producto del catálogo
  (aunque no esté marcado «Lo puede vender el profesor»), para un alumno de sus grupos, con nota libre (talla…). Lo
  atiende el responsable de la sede del profesor; hasta la E9 (el responsable aún no entra en la app, D6-15) lo
  atienden propietario y gestión, y la solicitud guarda su sede para pasar al responsable después sin más cambios.
  Estados: pedido → encargado (lo marca quien atiende) → recibido (quien atiende se lo da al profesor) → entregado (lo
  marca el profesor al dárselo al alumno). Quien atiende puede rechazar con motivo; el profesor puede retirar su
  pedido mientras no esté encargado. Al PEDIR se crea el cargo pendiente del alumno (precio de catálogo), que aparece
  en «Por cobrar» de Mi caja y se cobra como cualquier cargo. Si el encargo se cae sin cobrar → el cargo se cancela
  (regla 169: solo se borran cargos nunca cobrados; si no, se cancelan). Si ya estaba pagado → devolución por quien
  tenga el dinero (el profesor si aún lo lleva encima; si ya se entregó a la sede, propietario/gestión/responsable
  desde la caja de sede).
- **Decisión 153 (tomada el 19/10/2026).** Una devolución NO anula ni revierte el cobro; el cobro se queda y se añade
  un movimiento negativo de devolución enlazado al cargo, para que ambos queden en el historial; el cargo se ve como
  «devuelto».
- **Decisión 186.** Quitar un alumno de una convocatoria con el examen ya pagado se BLOQUEA para todos (P, G, R):
  aviso «El examen está pagado: haz la devolución o revierte el cobro antes» (agujero A3). Cambio visible para
  Moiskimdo aceptado.
- **Decisión 187.** Con un cobro activo no se puede cambiar precio ni cantidad de un cargo (sí concepto, fechas y
  notas) (agujero A13). Cambio visible para Moiskimdo aceptado.
- **Decisión 188.** Tipos de solicitud: (1) revertir un cobro (pide gestión o responsable, resuelve propietario);
  (2) deshacer un cobro del profesor ya entregado a la sede (pide profesor, resuelve propietario); (3) anular una
  entrega de caja o un movimiento (hoy solo propietario; pide gestión/responsable); (4) encargos. Borrados, cambios de
  precio y devoluciones sueltas siguen siendo directos para quien ya puede.
- **Decisión 189.** Al aceptar, la app ejecuta el cambio sola con los servicios de siempre, revalidando que el estado
  no cambió (si ya no aplica, avisa y la cierra); el historial enlaza quién pidió, quién aceptó y el motivo.
- **Decisión 190.** En Moiskimdo, donde hoy la gestión recibe 403 (revertir un cobro en la ficha o en tesorería),
  verá «Pedir al propietario» con motivo.
- **Decisión 191.** Revertir también se puede pedir sobre cargos pagados ANTES de la 6b-1 (sin fila `cobro`): la
  solicitud de tipo 1 admite referenciar el cargo cuando no hay cobro; unicidad «una pendiente por cargo» además de
  por cobro (columna única virtual en V31_8); al aceptar, se desmarca como hoy (`CobroService.anular` con `Optional`
  vacío). Cumple la 190 para todo el historial de Moiskimdo.
- **Decisión 192.** Solo se encargan productos de tipo OTRO sin grado (material y ropa), aunque no estén marcados para
  Mi caja.
- **Decisión 193.** Al aceptar deshacer un cobro ya entregado, el cobro se anula y en esa misma entrega se añade un
  movimiento ERROR +importe; la entrega no se anula. Si luego hay que dárselo a la familia, devolución (153).
- **Decisión 194.** La 187 se aplica también al cambiar la cuantía del examen desde la convocatoria
  (`ConvocatoriaServiceImpl.java:259`): a los alumnos con examen pagado no se les cambia (aviso), a los demás sí.
  Cambio visible para Moiskimdo aceptado.
- **Decisión 195.** La devolución de un encargo caído y pagado se registra en el propio encargo: «Registrar
  devolución» le sale al profesor en «Pedidos» si aún lleva el dinero; si ya se entregó, al propietario/gestión en la
  bandeja «Encargos en curso». Hasta registrarla, el encargo queda como «devolución pendiente» (cuenta para el
  contador de quien la debe hacer).

### 4.2 Del diseñador (aceptadas por el propietario)

- **D6c-1.** Motivo obligatorio al pedir y al denegar o rechazar (máx. 500, como las cancelaciones). La respuesta la ve
  quien pidió.
- **D6c-2.** Una sola solicitud pendiente por recurso (cobro, entrega, movimiento; y cargo, por la 191). Un encargo no
  tiene recurso previo.
- **D6c-3.** Quien pide puede retirarla mientras está pendiente.
- **D6c-4.** Contador en la cabecera de gestión con lo que espera acción del usuario; se refresca al entrar y al
  navegar (sin `interval`).
- **D6c-5.** Sin correos.
- **D6c-6.** Al profesor, sus pedidos le salen en una 4.ª pestaña «Pedidos» de Mi caja.
- **D6c-7.** El profesor pide deshacer un cobro entregado desde una sección plegable «Entregados recientes» de la
  pestaña «Encima».
- **D6c-8.** Entregas y movimientos se piden desde la Caja de sede con el mismo diálogo.

### 4.3 Del diseñador, técnicas (aceptadas)

Ninguna cambia lo que ve Moiskimdo. Las marcadas «revisable» puede cambiarlas el constructor si encuentra algo más
simple que **no cambie las fórmulas** del saldo de caja, «Llevas encima» ni la tesorería; si lo hace, lo anota en la PR.

| # | Decisión | Por qué | Pieza |
|---|---|---|---|
| D6c-9 | Sede del encargo = sede del grupo del alumno con ese profesor; si hay varias, el profesor la elige al pedir (la API la exige solo en ese caso) | 183: la atiende el responsable de la sede del profesor, y un profesor puede tener grupos en varias sedes | 6c-5 |
| D6c-10 (revisable) | Devolución del profesor que lleva el dinero encima: una entrega de ese único cobro con contado 0 cuyo movimiento automático es la DEVOLUCION (en vez de un FALTANTE) | Reutiliza la fórmula del saldo y «Llevas encima» sin tocarlas (§6.4) | 6c-6 |
| D6c-11 (revisable) | Una devolución enlazada es por el importe entero del cobro (sin parciales) y solo una activa por cobro | No hay pagos parciales; un cobro ACTIVO por cargo | 6c-6 |
| D6c-12 | La tesorería sigue sumando el cargo devuelto como cobrado y muestra aparte `importeDevuelto` (aditivo); las filas marcan «Devuelto» | Coherente con la 153 (el cobro se queda); no toca las 9 consultas de tesorería ni los totales de Moiskimdo | 6c-6 |
| D6c-13 (revisable) | `movimiento_caja.metodo` (por defecto EFECTIVO); el saldo de caja suma solo los EFECTIVO | La devolución de un cobro por transferencia o Bizum no sale de la caja física | 6c-6 |
| D6c-14 | «Entregados recientes» = cobros del profesor entregados en los últimos 30 días, máx. 50 | Acotar la lista; lo más antiguo, por la sede | 6c-4 |
| D6c-15 | Orden de los bloqueos: la 6c-1 sale primero, sin migración, con 186, 187 y 194 **sin excepciones** y el aviso «El examen está pagado: revierte el cobro antes». Con la 6c-6, quitar de la convocatoria a un alumno con el examen **devuelto** se permite y **desvincula** el cargo (no lo borra), y el aviso pasa al texto completo de la 186 («haz la devolución o revierte el cobro antes») | Hasta que exista la devolución enlazada no se puede ofrecer; tras ella el cargo sigue pagado y borrarlo rompería la 153 | 6c-1, 6c-6 |

## 5. Pantallas (boceto aprobado)

Lo visual (estilos, iconos, chips, colores) es de quien construya el frontend, con las skills de diseño del proyecto.
Este apartado fija qué hay, no cómo se ve.

### 5.1 Cabecera de gestión y bandeja

- **Cabecera** (`componentes/vistas/layout/header/header.component.html:232`, `admin-quick-nav`): acceso
  «Solicitudes» con contador. `AdminQuickNavigationItem` (`core/constants/navigation.constants.ts:11-18`) no tiene
  campo de contador: se añade o se pinta aparte (al constructor). El contador sale de `GET /api/solicitudes/contador`
  al iniciar sesión y en cada navegación (D6c-4).
- **Qué cuenta el contador** (lo que espera acción del usuario):
  - P: solicitudes PENDIENTES de tipos 1–3 + encargos PEDIDO o ENCARGADO + encargos con devolución pendiente que
    le tocan a la sede (195, §6.3).
  - G: encargos PEDIDO o ENCARGADO + encargos con devolución pendiente que le tocan a la sede.
  - R (API, sin pantalla hasta la E9): lo mismo que G, de sus sedes.
  - Pr: no tiene cabecera de gestión; sus encargos con devolución pendiente a su cargo cuentan en el `.contador` de la
    pestaña «Pedidos» de Mi caja (195).
  - Las respuestas a lo que uno pidió no cuentan: se ven en «Resueltas».
- **Bandeja** (pantalla nueva, ruta de gestión): segmentos «Pendientes», «Encargos en curso», «Resueltas».
  - Cada fila: tipo (chip), quién y cuándo, sede, sobre qué (alumno, concepto, importe), motivo citado.
  - Acciones de P en tipos 1–3: «Aceptar y revertir» / «Aceptar y deshacer» / «Aceptar y anular» y «Denegar…»
    (pide la respuesta).
  - Encargos: línea de estados (pedido → encargado → recibido → entregado) y «Marcar encargado», «Marcar recibido»,
    «Rechazar…» (pide motivo).
  - Encargo caído con el cargo pagado y el dinero ya en la sede: sigue en «Encargos en curso» como «Devolución
    pendiente» con «Registrar devolución» para P y G (195). Si el dinero lo lleva el profesor, la fila lo dice y el
    botón le sale a él.
  - Quien pidió ve sus solicitudes pendientes con «Retirar» y las resueltas con la respuesta.
  - «Resueltas»: aceptadas, denegadas, retiradas, no aplicables, encargos entregados, rechazados y retirados.
- **Panel de gestión:** opcionalmente, «N solicitudes pendientes» en «Alertas operativas»
  (`vista-principal-admin.component.ts:381-451`). Al constructor.

### 5.2 Diálogo «Pedir al propietario que revierta este cobro»

- Muestra los datos del cobro (alumno, concepto, importe, fecha, método, quién cobró). En un cargo pagado antes de la
  6b-1 (sin cobro, 191), los del cargo (alumno, concepto, precio, fecha de pago).
- Motivo obligatorio.
- Nota: «Mientras tanto el pago sigue marcado».
- Botones: Cancelar / Enviar solicitud.
- Mismo diálogo, con su título, para anular una entrega o un movimiento (D6c-8).

**Puntos de entrada:**
- Tesorería (`componentes/endpoints/tesoreria-cobros/tesoreria-cobros.component.ts:280`): hoy «Revertir» solo sale con
  `isAdmin`. G verá «Pedir al propietario» (190).
- Ficha del alumno (`componentes/endpoints/editar-alumno/productos-alumno/productos-alumno.component.html:97`): hoy G
  desmarca el checkbox y el backend da 403. Con la 6c, desmarcar un cargo pagado como G abre el diálogo y el checkbox
  vuelve a marcado.
- Caja de sede (`componentes/endpoints/caja-sede/…`): en el historial de entregas y en los movimientos, G ve
  «Pedir anulación» donde P ve «Anular».
- Con una solicitud pendiente sobre ese recurso, el punto de entrada lo indica y no deja pedir otra (D6c-2).

### 5.3 Mi caja (móvil, profesor)

- **4.ª pestaña «Pedidos»** (patrón de pestañas y `.contador` de `componentes/endpoints/mi-caja/mi-caja.component.html:27-41`):
  - botón «Encargar un producto»: elegir alumno de sus grupos, producto encargable (192), nota libre (talla…) y, solo
    si el alumno está con él en grupos de varias sedes, la sede (D6c-9);
  - sus encargos: línea de estados y estado del cobro del cargo (pendiente, cobrado, devuelto);
  - «Marcar entregado al alumno» cuando el encargo está RECIBIDO;
  - «Retirar» mientras está PEDIDO;
  - «Registrar devolución» en un encargo caído cuyo cobro aún lleva encima (195); hasta entonces, «Devolución
    pendiente»;
  - sus solicitudes de deshacer: «Esperando al propietario · Retirar», o la respuesta.
- **Pestaña «Encima»:** sección plegable «Entregados recientes» (D6c-14) con «Pedir deshacer» en cada cobro; pide
  motivo y, como en A4, si el alumno aún no ha pagado o no hubo venta (`MotivoDeshacer`).

«Registrar devolución» (profesor o bandeja) confirma alumno, concepto e importe (el del cobro entero, D6c-11) y pide
una nota opcional; no pide importe ni tipo. Lo visual, al constructor.

## 6. Modelo de datos (V31_8)

Ampliación pura: tabla nueva vacía y columnas nuevas NULL o con valor por defecto. El código anterior la ignora. V32
sigue reservada para la contracción. Patrón de V31_5 (`CREATE TABLE IF NOT EXISTS`) y de V31_6 (columnas con guarda en
`information_schema`). Con su `docs/multi-escuela/postcheck-v31_8.sql` (solo recuentos).

### 6.1 Tabla `solicitud`

Entidad `Solicitud extends EntidadOrganizacion` (`@TenantId`), ids simples sin relaciones, como `Cobro` o
`MovimientoCaja`.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | BIGINT AUTO_INCREMENT | |
| `organizacion_id` | BIGINT NOT NULL, FK | `@TenantId` |
| `sede_id` | BIGINT NULL, FK sede | Del cobro, la entrega o el movimiento; en un cargo sin cobro (191), `producto_alumno.sede_id`; del encargo, D6c-9. NULL solo si el recurso no tiene sede |
| `tipo` | VARCHAR(30) NOT NULL | `REVERTIR_COBRO`, `DESHACER_COBRO_ENTREGADO`, `ANULAR_ENTREGA`, `ANULAR_MOVIMIENTO`, `ENCARGO` |
| `estado` | VARCHAR(20) NOT NULL | Tipos 1–3: `PENDIENTE`, `ACEPTADA`, `DENEGADA`, `RETIRADA`, `NO_APLICABLE`. Encargo: `PEDIDO`, `ENCARGADO`, `RECIBIDO`, `ENTREGADO`, `RECHAZADO`, `RETIRADA` |
| `solicitado_por_usuario_id` | BIGINT NULL, FK usuario ON DELETE SET NULL | Obligatorio en el servicio |
| `solicitado_en` | DATETIME(6) NOT NULL | |
| `motivo` | VARCHAR(500) NOT NULL | D6c-1. En el encargo, la nota libre (talla…) |
| `resuelto_por_usuario_id` | BIGINT NULL, FK ON DELETE SET NULL | Quien acepta, deniega, rechaza (o el solicitante al retirar) |
| `resuelto_en` | DATETIME(6) NULL | |
| `respuesta` | VARCHAR(500) NULL | Motivo de la denegación o del rechazo; texto fijo en `NO_APLICABLE` |
| `cobro_id` | BIGINT NULL, FK cobro | Tipos 1 y 2. NULL en el tipo 1 sobre un cargo pagado antes de la 6b-1 (191) |
| `entrega_caja_id` | BIGINT NULL, FK entrega_caja | Tipo 3 (entrega) |
| `movimiento_caja_id` | BIGINT NULL, FK movimiento_caja | Tipo 3 (movimiento) |
| `producto_alumno_id` | BIGINT NULL, FK ON DELETE SET NULL | Cargo del cobro (tipos 1 y 2), cargo sin cobro (tipo 1, 191) o cargo creado por el encargo. Siempre lleno en el tipo 1 |
| `producto_id` | BIGINT NULL, FK producto | Encargo |
| `alumno_id` | BIGINT NULL, FK ON DELETE SET NULL | Instantánea para la bandeja |
| `concepto` | VARCHAR(255) NULL | Instantánea |
| `importe` | DECIMAL(10,2) NULL | Instantánea (cobro, entrega declarada, movimiento o precio del catálogo) |
| `motivo_deshacer` | VARCHAR(20) NULL | Tipo 2: `AUN_NO_PAGADO`, `NO_HUBO_VENTA` (`CajaRequests.MotivoDeshacer`) |
| `encargado_en`, `encargado_por_usuario_id` | NULL | Encargo |
| `recibido_en`, `recibido_por_usuario_id` | NULL | Encargo |
| `entregado_en` | DATETIME(6) NULL | Encargo (lo marca el solicitante) |
| `devolucion_pendiente` | BOOLEAN NOT NULL DEFAULT FALSE | Encargo caído con el cargo pagado y sin devolución (195, §6.3) |
| `movimiento_devolucion_id` | BIGINT NULL, FK movimiento_caja | La devolución que cierra el encargo caído (195) |
| `creado_en`, `version` | | Como V31_5; `@Version` |
| `cobro_pendiente_id` | GENERATED `IF(estado='PENDIENTE', cobro_id, NULL)` VIRTUAL, UNIQUE | D6c-2. VIRTUAL por lo mismo que `cobro.producto_alumno_activo_id` (V31_5) |
| `entrega_pendiente_id`, `movimiento_pendiente_id` | Igual, sobre su columna | D6c-2 |
| `cargo_pendiente_id` | GENERATED `IF(estado='PENDIENTE' AND tipo='REVERTIR_COBRO', producto_alumno_id, NULL)` VIRTUAL, UNIQUE | 191: una pendiente por cargo, haya cobro o no |

Índices: (`organizacion_id`, `estado`, `tipo`), (`organizacion_id`, `sede_id`, `estado`), (`solicitado_por_usuario_id`,
`estado`), `producto_alumno_id`. Un encargo no tiene clave de unicidad: el profesor puede pedir dos camisetas.

Al constructor: enum Java por tipo y estado, la tabla de transiciones permitidas (§6.2) en un único sitio del servicio,
y si las FK a `cobro`, `entrega_caja` y `movimiento_caja` llevan acción (esas filas no se borran hoy).

### 6.2 Transiciones

| Tipo | Desde | A | Quién | Efecto |
|---|---|---|---|---|
| 1–3 | — | PENDIENTE | Solicitante (§7) | Nada más: «mientras tanto el pago sigue marcado» |
| 1–3 | PENDIENTE | RETIRADA | Solicitante | — |
| 1–3 | PENDIENTE | DENEGADA | P, con respuesta | — |
| 1–3 | PENDIENTE | ACEPTADA | P | Revalida y ejecuta (§6.5) en la misma transacción |
| 1–3 | PENDIENTE | NO_APLICABLE | P al aceptar, si la revalidación falla | Nada; `respuesta` dice por qué (p. ej. «El cobro ya va en una entrega») |
| 4 | — | PEDIDO | Pr | Crea el cargo pendiente (precio de catálogo, nota en `notas`); `producto_alumno_id` |
| 4 | PEDIDO | RETIRADA | Pr solicitante | Cae el encargo (§6.3) |
| 4 | PEDIDO | ENCARGADO | P, G; R de la sede (API) | — |
| 4 | ENCARGADO | RECIBIDO | P, G; R de la sede | — |
| 4 | RECIBIDO | ENTREGADO | Pr solicitante | — (no exige el cargo cobrado: puede cobrarlo después desde «Por cobrar») |
| 4 | PEDIDO, ENCARGADO, RECIBIDO | RECHAZADO | P, G; R de la sede, con motivo | Cae el encargo (§6.3) |
| 4 | RETIRADA o RECHAZADO con `devolucion_pendiente` | (mismo estado) `devolucion_pendiente = false` | Quien tiene el dinero (§6.3, 195) | Registra la devolución enlazada (§6.4) |

Cada transición bloquea la fila de la solicitud (`SELECT … FOR UPDATE` y relectura, como `CobroService.bloquearCobro`) y
la ejecución bloquea después cargo → cobro con los métodos de siempre (orden de `CajaService`, cabecera). Dos clics a la
vez: uno gana, el otro 409 «La solicitud ya no está pendiente».

### 6.3 Encargo y cargo

- **Al pedir:** el cargo se crea con el mismo código que la gestión (`ProductoAlumnoServiceImpl.asignarProductoAAlumno`,
  `:186`), por un método interno sin el `@PreAuthorize` de gestión. `vendido_por_usuario_id` queda NULL: no es su venta
  (decisión 172), así que su `deshacer` con NO_HUBO_VENTA no lo cancela. El cargo entra solo en «Por cobrar»
  (`CajaService.java:127-150`) y en el circuito de caja: **cero cambios en la caja**.
- **Guardas nuevas en el cargo de un encargo en curso** (PEDIDO, ENCARGADO o RECIBIDO): cancelar (A10) y borrar (A12)
  → 409 «Tiene un encargo en curso: recházalo desde Solicitudes». Solo afecta a cargos nuevos de encargos.
- **Cae el encargo** (RETIRADA o RECHAZADO), en la misma transacción:
  - cargo pendiente → se cancela (servicio de A10) con motivo «Encargo retirado/rechazado (solicitud #n)»;
  - cargo pagado → no se toca; `devolucion_pendiente = true`. La solicitud sigue en «Encargos en curso» como «Devolución
    pendiente» (195). Quién la registra lo decide dónde está el dinero: el profesor si el cobro es suyo (origen
    PROFESOR) y sin entrega; si no, P, G o R de la sede.
  - «Registrar devolución» (§6.4) crea la devolución enlazada, guarda `movimiento_devolucion_id` y pone
    `devolucion_pendiente = false`, en la misma transacción.
  - Mientras la devolución está pendiente, revertir o deshacer ese cobro (A1, A2, A4, tipos 1 y 2) → 409 «El encargo
    tiene una devolución pendiente: regístrala antes». Así no hay dos caminos para el mismo dinero.
  - Si el profesor entrega el cobro a la sede antes de devolverlo, la devolución pasa a la sede sin más: la bandeja
    mira el cobro al pintar la fila.
- **Profesor dado de baja con encargos en curso:** quedan visibles en la bandeja; P/G pueden rechazarlos. ENTREGADO solo
  lo marca el solicitante (riesgo §10).

### 6.4 Devolución enlazada (153)

`movimiento_caja` gana, en V31_8 (columnas con guarda en `information_schema`, `foreign_key_checks = 0` como V31_6):

| Columna | Tipo | Notas |
|---|---|---|
| `cobro_id` | BIGINT NULL, FK cobro | Cobro devuelto (DEVOLUCION) o deshecho (ERROR de la 193) |
| `producto_alumno_id` | BIGINT NULL, FK ON DELETE SET NULL | Cargo (153: «enlazado al cargo»); el del cobro |
| `metodo` | VARCHAR(20) NOT NULL DEFAULT 'EFECTIVO' | D6c-13. Todas las filas actuales quedan EFECTIVO: el saldo de hoy no cambia |
| `cobro_devuelto_activo_id` | GENERATED `IF(estado='ACTIVO' AND tipo='DEVOLUCION', cobro_id, NULL)` VIRTUAL, UNIQUE | Una devolución activa por cobro (D6c-11) |

¿Basta con una columna? Para enlazar, sí (`cobro_id`; el cargo se deduce del cobro). Se añade también
`producto_alumno_id` porque `cobro.producto_alumno_id` pasa a NULL si el cargo se borra y la 153 pide el enlace al
cargo. Al constructor: quedarse solo con `cobro_id` si prefiere unir siempre por el cobro.

**Reglas de la devolución enlazada** (`crearMovimiento` con `cobroId` opcional en `CajaRequests.CrearMovimiento`):
- tipo DEVOLUCION; cobro ACTIVO de la sede del movimiento; importe = −`cobro.importe`; alumno = `cobro.alumno_id`;
  `metodo` = el del cobro; sin otra devolución activa (409);
- desde la caja de sede, solo si el cobro no es del profesor pendiente de entrega (origen ≠ PROFESOR o ya entregado).
  Si el profesor aún lleva el dinero, devuelve él (D6c-10);
- sin `cobroId`, la devolución suelta de hoy, sin cambios (188).

En la 6c la única pantalla que crea devoluciones enlazadas es «Registrar devolución» del encargo (195). El `cobroId`
opcional de `POST /api/caja/sedes/{sedeId}/movimientos` queda en la API (lo usa la bandeja); enlazar una devolución
suelta desde el formulario de la caja de sede no entra en la 6c.

**Devolución del profesor que lleva el dinero encima** (D6c-10). Ruta nueva del profesor sobre SU encargo caído, cuyo
cobro es suyo, ACTIVO y sin entrega. En una transacción, con los bloqueos de `registrarEntrega`:
1. entrega de ese cobro con quien entrega = quien recibe = el profesor, declarado = importe, contado = 0;
2. su movimiento automático es la DEVOLUCION (−importe, `cobro_id`, `entrega_caja_id`) en vez del FALTANTE.

Resultado:
- saldo de la sede: +importe (cobro entregado) −importe (devolución) = 0;
- el cobro deja de estar en «Llevas encima»;
- la fórmula del saldo y las consultas del profesor no cambian;
- si el propietario anula esa entrega (A7), cobro y devolución vuelven atrás juntos.

Registro `CAJA DEVOLUCION_PROFESOR`. La entrega se distingue en el historial porque su movimiento es una DEVOLUCION.

**«Devuelto».** Un cargo está devuelto si su cobro ACTIVO tiene una DEVOLUCION activa enlazada. Sigue `pagado = true`
(153). Invariantes nuevas:
- un cobro con devolución activa **no se anula** por ninguna vía: revertir (A1, A2), deshacer (A4, tipo 2) y
  `alBorrarCargos` → 409, o se salta como hoy los entregados;
- borrar un cargo devuelto (A12 ya lo impide: tiene cobro) o quitarlo de la convocatoria (D6c-15, desde la 6c-6: se
  desvincula, no se borra) no lo deja sin historial.

Tesorería (D6c-12): filas con `devuelto: true` y resumen con `importeDevuelto` (una consulta de agregado nueva). Los
totales actuales no cambian.

### 6.5 Ejecución al aceptar (189)

Con la identidad de P (anulado por = P) y en la misma transacción que el cambio de estado.

| Tipo | Revalida | Ejecuta con |
|---|---|---|
| 1 Revertir cobro | Con cobro: cobro ACTIVO, sin entrega, sin devolución ni devolución pendiente; cargo existe y pagado. Sin cobro (191): cargo existe, pagado y sigue sin cobro ACTIVO | El camino de A1: `PUT` interno con `pagado:false` y motivo «Revertido por solicitud #n: {motivo}», que anula el cobro (`CobroService.anular`; sin cobro devuelve `Optional` vacío y solo se desmarca, como hoy) y sincroniza convocatoria y derecho a examen |
| 2 Deshacer cobro entregado | Cobro ACTIVO, origen PROFESOR, cobrado por el solicitante, en una entrega ACTIVA, sin devolución ni devolución pendiente | Decisión 193: anula el cobro como A4 (con su `motivo_deshacer`: pendiente o cancelado si NO_HUBO_VENTA y era su venta) saltándose solo la comprobación de entrega, y crea el ERROR +importe en esa entrega, enlazado al cobro; la entrega no se anula. Método nuevo en `CobroService`, no SQL propio |
| 3 Anular entrega | Entrega ACTIVA | `CajaService.anularEntrega` con motivo «Solicitud #n: {motivo}» |
| 3 Anular movimiento | Movimiento ACTIVO, no automático | `CajaService.anularMovimiento`, ídem |

Si la revalidación falla: estado `NO_APLICABLE`, respuesta con la razón y nada más. P ve el aviso; quien pidió, la
respuesta. Lo que el servicio de siempre ya rechaza (p. ej. 409 por entrega) se traduce a `NO_APLICABLE`. No se deja
la solicitud pendiente.

Decisión 193 y el automático de la entrega: `anularEntrega` ya anula **todos** los movimientos ACTIVOS de la entrega
(`CajaService.java:444-447`), así que anular después esa entrega anula también el ERROR. `CajaDTOs.EntregaCaja.movimientoId`
supone uno solo (`CajaDTOs.java:72-76`): el constructor debe devolver el SOBRANTE/FALTANTE ahí y no el ERROR.

## 7. Permisos

### 7.1 Tabla rol × acción

`S` = sus sedes; `suya` = la pidió él; `—` = no. R tiene la API desde la 6c (`ALCANCE` por sede) y pantallas en la E9.

| Acción | P | G | R (API) | Pr | F |
|---|---|---|---|---|---|
| Crear tipo 1 (revertir cobro; también cargo sin cobro, 191) | — (lo hace directo) | Sí | S (sede del cobro o del cargo) | — | — |
| Crear tipo 2 (deshacer cobro entregado) | — | — | — | Su cobro | — |
| Crear tipo 3 (anular entrega/movimiento) | — (directo) | Sí | S | — | — |
| Crear tipo 4 (encargo) | — | — | — | Alumno de sus grupos, producto OTRO sin grado (192) | — |
| Retirar | Suya | Suya | Suya | Suya (tipo 2; encargo en PEDIDO) | — |
| Aceptar / denegar tipos 1–3 | Sí | — | — | — | — |
| Encargado / recibido / rechazar encargo | Sí | Sí | S | — | — |
| Marcar encargo entregado | — | — | — | Suyo, en RECIBIDO | — |
| Ver la bandeja | Todo | Encargos + las suyas | Encargos de S + las suyas | — | — |
| Ver sus pedidos (Mi caja) | — | — | — | Los suyos | — |
| Registrar devolución de un encargo caído, dinero en la sede (195) | Sí | Sí | S | — | — |
| Registrar devolución de un encargo caído, dinero encima (195, D6c-10) | — | — | — | Su encargo, su cobro sin entregar | — |

Una cuenta con varios roles recibe la unión (p. ej. PROFESOR + ADMINISTRADOR ve la bandeja y sus pedidos).

### 7.2 Rutas (nombres al constructor)

**Gestión** (`/api/solicitudes/**`, regla de URL nueva para ADMIN, MANAGER y RESPONSABLE_SEDE; `@PreAuthorize` por
ruta; R con `@acceso` por la sede de la solicitud o del recurso):

| Ruta | P | G | R |
|---|---|---|---|
| `GET /api/solicitudes?segmento=PENDIENTES\|ENCARGOS\|RESUELTAS` | PERMITE | PERMITE (filtra el servicio) | PERMITE (filtra el servicio, lista declarada) |
| `GET /api/solicitudes/contador` | PERMITE | PERMITE | PERMITE (filtra) |
| `POST /api/solicitudes` `{tipo, cobroId\|productoAlumnoId\|entregaId\|movimientoId, motivo}` (`productoAlumnoId` solo en el tipo 1 de un cargo sin cobro, 191) | DENIEGA en servicio (400: hazlo directo) | PERMITE | PERMITE (servicio comprueba la sede del recurso) |
| `POST /api/solicitudes/{id}/retirar` | Suya | Suya | Suya |
| `POST /api/solicitudes/{id}/aceptar`, `/denegar {respuesta}` | PERMITE (`hasAuthority('ROLE_ADMIN')`) | DENIEGA | DENIEGA |
| `POST /api/solicitudes/{id}/encargado`, `/recibido`, `/rechazar {motivo}` | PERMITE | PERMITE | ALCANCE (`@acceso.solicitudEnSede`) |
| `POST /api/solicitudes/{id}/devolucion {nota?}` (195; el servicio exige que el dinero esté en la sede) | PERMITE | PERMITE | ALCANCE (`@acceso.solicitudEnSede`) |
| `POST /api/caja/sedes/{sedeId}/movimientos` | Igual que hoy; el cuerpo gana `cobroId` opcional (§6.4) | | |

**Profesor** (bajo `/api/caja/profesor/**`: hereda la `LISTA_BLANCA` de la sesión solo-profesor,
`config/SesionSoloProfesorAuthorizationManager.java:136-143`, y la regla de URL `ROLE_PROFESOR`,
`config/SecurityConfiguration.java:489`, sin tocarlas):

| Ruta | Expresión |
|---|---|
| `GET /api/caja/profesor/solicitudes` | `hasAuthority('ROLE_PROFESOR')`; el servicio filtra por solicitante |
| `GET /api/caja/profesor/productos-encargables` | `hasAuthority('ROLE_PROFESOR')` (192) |
| `POST /api/caja/profesor/alumnos/{alumnoId}/encargos {productoId, nota, sedeId?}` (`sedeId` obligatorio solo si hay varias, D6c-9) | `@acceso.alumnoDeMisGrupos(#alumnoId)` |
| `GET /api/caja/profesor/cobros-entregados-recientes` | `hasAuthority('ROLE_PROFESOR')` (D6c-14) |
| `POST /api/caja/profesor/cobros/{cobroId}/pedir-deshacer {motivo, motivoDeshacer}` | `@acceso.cobroPropioDelProfesor(#cobroId)`; entregado lo comprueba el servicio |
| `POST /api/caja/profesor/solicitudes/{id}/retirar`, `/entregado`, `/devolucion {nota?}` (195, D6c-10) | `hasAuthority('ROLE_PROFESOR')`; solicitante = él, en el servicio con la fila bloqueada |

Ojo: cualquier otra ruta bajo `/api/caja` es `denyAll` (`SecurityConfiguration.java:495`). Por eso la bandeja va en
`/api/solicitudes/**` y no bajo `/api/caja`.

**Matriz:** cada ruta nueva lleva su línea en `src-api/src/test/resources/seguridad/matriz-permisos.txt` y su nota en
`matriz-endpoints.md`. Ninguna casilla existente cambia (el `cobroId` opcional en movimientos no
cambia quién puede llamar). Las rutas de `/api/solicitudes/**` son `DENIEGA` para PROFESOR (fuera de la lista blanca), FAMILIA, ANONIMO y
SOLO_PLATAFORMA. Los listados de R entran en `RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO` con su test.

## 8. Auditoría e historial

- **Registro de acciones:** `RegistroAccionesSolicitudes` (módulo `solicitudes`), copia de `RegistroAccionesCaja`
  (`services/RegistroAccionesCaja.java`), sobre `AuditoriaService.registrarAccesoSensible`, en la MISMA transacción y
  solo con ids (`"SOLICITUDES SOLICITUD_ACEPTADA sede=#3 solicitud=#7 cobro=#45"`).
  - Acciones: SOLICITUD_CREADA, RETIRADA, DENEGADA, ACEPTADA, NO_APLICABLE, ENCARGO_PEDIDO, ENCARGO_ENCARGADO,
    ENCARGO_RECIBIDO, ENCARGO_ENTREGADO, ENCARGO_RECHAZADO.
- **Acción ejecutada:** deja además su registro de siempre (ENTREGA_ANULADA, MOVIMIENTO_ANULADO, CARGO_CANCELADO…)
  con `solicitud=#n` entre los ids. A1 no tiene registro propio hoy: queda cubierto por SOLICITUD_ACEPTADA.
- **Acciones de caja nuevas:** `DEVOLUCION_CREADA` (o MOVIMIENTO_CREADO con `cobro=#`), `DEVOLUCION_PROFESOR` y
  `COBRO_ENTREGADO_DESHECHO` (193). En solicitudes, además, ENCARGO_DEVOLUCION_REGISTRADA (195).
- **Historial de negocio:** la propia fila `solicitud` (quién pidió, quién resolvió, motivo, respuesta, fechas) y los
  campos de anulación de cobro, entrega o movimiento (`anulado_por`, `motivo_anulacion` con «solicitud #n»).
- **Datos personales:** `AuditoriaRequestFilter` sigue registrando cada petición con el payload saneado. El motivo es
  texto libre: no debe ir al resumen del registro de acciones.

## 9. Moiskimdo: qué cambia

| # | Cambio | Para quién | Estado |
|---|---|---|---|
| 1 | Quitar de una convocatoria a un alumno con el examen pagado → aviso «El examen está pagado: revierte el cobro antes»; desde la 6c-6, «haz la devolución o revierte el cobro antes» (D6c-15) | P y G | Aceptado (186) |
| 2 | Cambiar precio o cantidad de un cargo con cobro activo → 409 con mensaje (sí concepto, fechas y notas) | P y G | Aceptado (187) |
| 2b | Cambiar la cuantía del examen desde la convocatoria (`PUT /api/convocatorias/alumno/{id}`, `ConvocatoriaServiceImpl.java:259`): con el examen pagado (antes y después) y otra cuantía → 409 con aviso y no se cambia; sin pagar, como hoy. Es por alumno (`listado-convocatorias.component.ts:478-495` envía uno a uno). Desmarcar el pago (154), igual | P y G | Aceptado (194) |
| 3 | Donde hoy da 403 (revertir en tesorería y ficha; anular entregas o movimientos), «Pedir al propietario» | G | Aceptado (190) |
| 4 | «Solicitudes» con contador en la cabecera y bandeja | P (resuelve), G (las suyas) | Aceptado (D6c-4) |
| 5 | Cargos pagados antes de la 6b-1 sin fila `cobro`: G puede pedir revertirlos (191); 187 no los bloquea (no hay cobro activo) y no admiten devolución enlazada (no hay cobro al que enlazar) | P y G | Aceptado (191) / mantener legado |
| 6 | Encargos, Mi caja «Pedidos», devolución del profesor | — | Sin efecto (no hay profesores) |
| 7 | `movimiento_caja.metodo` = EFECTIVO en todas las filas; el saldo no cambia | — | Invisible |

Lo que sigue igual: A2 (154); borrar alumno (A14) y convocatoria (A15), que borran cargos pagados por su camino de hoy;
cancelar, reactivar y generar cargos; todo lo que P hace directo.

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Ejecutar al aceptar por un camino distinto al de siempre rompe invariantes de caja | Solo métodos de servicio existentes (o nuevos en `CobroService`/`CajaService` que reutilizan sus bloqueos); test por tipo con el estado cambiado entre pedir y aceptar → NO_APLICABLE |
| Doble clic o dos P a la vez | Bloqueo de la fila `solicitud` + `@Version`; las claves únicas virtuales impiden dos pendientes por recurso |
| Decisión 193 y D6c-10: la entrega con más de un movimiento, o con contado 0 | `anularEntrega` ya recorre todos; revisar los DTO y la pantalla de caja que suponen uno |
| Devolución + anulación del cobro descuadran (doble negativo) | Invariante «cobro con devolución activa no se anula»: guardas en A1, A2, A4, tipo 2 y `alBorrarCargos`, con test por vía |
| A14/A15 borran un cargo devuelto | `alBorrarCargos` no anula cobros con devolución activa (como hoy con los entregados) |
| 186, 187 y 194 rompen flujos de Moiskimdo | Aceptados; mensajes claros; test de que el desmarcar de la 154 y el cambio de concepto, fechas y notas siguen igual |
| La lista blanca del profesor deja pasar algo nuevo | Solo rutas bajo `/api/caja/profesor/**`, cada una con `@PreAuthorize` concreto; `sesionSoloProfesorLaCierraLaCapaDeUrlFueraDeSuListaBlanca` |
| R sin pantalla acumula encargos de su sede | Hasta la E9 los atienden P y G (183); el contador de R solo existe en la API |
| Profesor que deja la escuela con encargos RECIBIDOS | P/G los ven en la bandeja; ENTREGADO solo lo marca el solicitante. Si pasa, decidir en la E9 |
| Motivos con datos personales | No van al resumen del registro; se guardan solo en `solicitud` (acotada por escuela) |
| Encargo caído y pagado sin forma de devolverlo (6c-5 sin 6c-6) | No desplegar encargos (6c-5, 6c-8) en producción sin la 6c-6; en Moiskimdo no hay profesores, así que solo importa al abrir Sierra Sur |
| Revertir o deshacer un cobro con devolución de encargo pendiente | 409 (§6.3); test por vía |
| Contador que pesa en cada navegación | Una consulta `COUNT` por índice (`organizacion_id, estado, tipo`) y sin cargar entidades |

## 11. Qué queda al constructor

- Nombres finales de rutas, DTO y enums; mensajes de error exactos (salvo el de la 186, que es literal).
- Si `movimiento_caja.producto_alumno_id` se guarda o se deduce del cobro (§6.4).
- Acción de las FK de `solicitud` a cobro, entrega y movimiento.
- Si «N solicitudes pendientes» va también en «Alertas operativas» del panel.
- Todo lo visual.
- D6c-10, D6c-11 y D6c-13, si encuentra algo más simple que no cambie las fórmulas (§4.3).
- `postcheck-v31_8.sql`: tabla vacía, columnas nuevas, `metodo` = EFECTIVO en todas las filas, saldo por sede igual
  antes y después; y la línea en `checklist-despliegue.md` (con migración: atajo `desplegar_migrar`).

## 12. Troceado sugerido

| Pieza | Contenido | Depende de | Riesgo |
|---|---|---|---|
| **6c-1** backend + mensajes | Bloqueos sin excepciones: 186 (`eliminarAlumnoDeConvocatoria`, examen pagado → 409 «El examen está pagado: revierte el cobro antes»), 187 (`PUT` del cargo con cobro activo: precio o cantidad → 409) y 194 (cuantía del examen pagado en `actualizarAlumnoConvocatoria`). Que convocatoria y ficha muestren el mensaje del backend. Sin migración | — | Medio (dinero; cambio visible aceptado) |
| **6c-2** backend | V31_8 (tabla `solicitud` con sus claves virtuales, también `cargo_pendiente_id`; columnas de `movimiento_caja`), entidad, repositorio, servicio base (crear tipos 1 —con o sin cobro, 191— y 3, retirar, denegar, listar por segmento, contador), `RegistroAccionesSolicitudes`, rutas de gestión, matriz | — | Medio (migración aditiva; seguridad de rutas nuevas) |
| **6c-3** backend | Aceptar tipos 1 (con y sin cobro, 191) y 3 con revalidación y NO_APLICABLE | 6c-2 | Alto (ejecuta reversiones y anulaciones) |
| **6c-7** frontend | Cabecera con contador, bandeja (tres segmentos), diálogo «Pedir al propietario» en tesorería, ficha y caja de sede (190, 191) | 6c-3 | Medio (visible para Moiskimdo) |
| **6c-5** backend | Encargos: productos encargables (192), pedir con cargo y sede (D6c-9), transiciones, caída del encargo (cancela o deja `devolucion_pendiente`), guardas en cancelar/borrar el cargo, rutas del profesor | 6c-2 | Alto (crea y cancela cargos) |
| **6c-4** backend | Tipo 2: rutas del profesor `cobros-entregados-recientes` (D6c-14) y `pedir-deshacer`; aceptación con la 193 | 6c-3 | Alto (caja) |
| **6c-6** backend | Devolución enlazada (153): columnas en uso, «Registrar devolución» del encargo por la sede y por el profesor (195, D6c-10), `metodo` en el saldo (D6c-13), «devuelto», invariantes «no se anula» y «devolución pendiente → 409», tesorería `devuelto`/`importeDevuelto` (D6c-12), desvinculación del examen devuelto y aviso completo de la 186 (D6c-15) | 6c-2, 6c-5 | Alto (caja y tesorería) |
| **6c-8** frontend | Mi caja: pestaña «Pedidos» (encargar con sede si hace falta, retirar, marcar entregado, solicitudes de deshacer); «Entregados recientes» en «Encima» | 6c-4, 6c-5 | Medio |
| **6c-9** frontend | «Registrar devolución» en «Pedidos» y en la bandeja (195), contador de devoluciones pendientes, «Devuelto» en tesorería, ficha y Mi caja, aviso completo de la 186 | 6c-6, 6c-7, 6c-8 | Bajo |

Orden:
1. **6c-1** sola y primero (cierra A3 y A13 en Moiskimdo).
2. **Moiskimdo (190, 191):** 6c-2 → 6c-3 → 6c-7. Se puede desplegar sin lo demás: no crea encargos ni devoluciones.
3. **Sierra Sur:** 6c-5 → 6c-4 → 6c-6 → 6c-8 → 6c-9. Los encargos (6c-5, 6c-8) no salen a producción sin la 6c-6:
   sin ella, un encargo caído y pagado no se podría devolver. 6c-4 y 6c-5 son independientes entre sí.
4. V31_8 sale con la 6c-2; ninguna pieza necesita V32.

**Pruebas mínimas por pieza backend:** IT en MySQL con dos sedes; Moiskimdo idéntico con ADMIN y MANAGER (todo salvo
186, 187 y 194); por cada tipo, estado cambiado entre pedir y aceptar → NO_APLICABLE sin efectos; tipo 1 sobre un cargo
sin cobro (191); saldo de caja de la sede igual antes y después en cada ejecución que no mueve dinero real (193:
anulado + ERROR = mismo saldo); devolución del profesor (D6c-10): saldo 0 y fuera de «Llevas encima»; matriz con diff
solo en las líneas nuevas; migración V31_8 dos veces y tras un fallo a medias.
