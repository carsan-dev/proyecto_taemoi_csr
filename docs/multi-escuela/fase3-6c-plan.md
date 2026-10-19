# Entrega 6c (solicitudes y encargos): plan de implementación

> **Ejecutores:** agentes savvy-* orquestados por el router. Cada pieza 6c-n es un agente en su propio worktree, con
> revisión independiente (`savvy-reviewer`) antes de la PR. Las piezas de dinero, permisos o migración (6c-1, 6c-2,
> 6c-3, 6c-4, 6c-5, 6c-6) llevan la revisión en opus. Los pasos usan casillas (`- [ ]`). Este plan detalla la 6c-1
> (§2), la 6c-2 (§3), la 6c-3 (§4) y la 6c-7 (§5); las demás se detallan justo antes de lanzarlas, a partir del mapa (§1).

**Objetivo:** cerrar los agujeros A3 y A13 (decisiones 186, 187 y 194) y montar las solicitudes al propietario, los
encargos del profesor y la devolución enlazada (decisiones 153, 155, 183, 188 a 195).

**Arquitectura:** las solicitudes se guardan en una tabla nueva, `solicitud`, acotada por `@TenantId`. Al aceptar, se
ejecutan con los servicios de siempre (`CobroService`, `CajaService`, `ProductoAlumnoServiceImpl`) en la misma
transacción y con sus bloqueos. La devolución es un `movimiento_caja` DEVOLUCION enlazado al cobro y no anula nada.
El frontend añade una bandeja de gestión con contador y la pestaña «Pedidos» de Mi caja.

**Tecnología:** Spring Boot + JPA/Hibernate (MySQL 8, Flyway), Angular (standalone, Karma/Jasmine), SweetAlert2.

**Spec:** `docs/multi-escuela/fase3-6c-solicitudes.md`, aprobado por el propietario el 19/10/2026. El ejecutor lee
también ese documento. Las citas `§n` remiten a él.

Rutas abreviadas: `J/` = `src-api/src/main/java/com/taemoi/project/`, `T/` = `src-api/src/test/java/com/taemoi/project/`,
`F/` = `src-frontend/src/app/`.

## Restricciones globales

- Flyway: la siguiente versión es **V31_8**, porque la última es `V31_7__producto_vendible_profesor.sql`. V32 queda
  reservada para la contracción. Solo se admiten migraciones de **ampliación**: tablas nuevas vacías y columnas NULL
  o con valor por defecto.
- Aislamiento por escuela: las entidades nuevas heredan `EntidadOrganizacion` (`@TenantId`). Las consultas nativas no
  filtran por escuela y deben llevar `organizacion_id` explícito.
- Moiskimdo (organización 1) se comporta igual que hoy, salvo en las decisiones explícitas 186, 187, 190, 191 y 194.
- Nunca se suben datos reales al repo: ni nombres ni DNI de alumnos en fixtures, SQL ni capturas.
- Batería del backend en dos mitades: `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion`, en
  primer plano. Si se cuelga un proceso, se limpia al cerrar.
- Frontend: `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build:production` **sin aviso de
  presupuesto** (el inicial ronda 1,49 MB y el límite de aviso es 1,5 MB).
- Karma solo ejecuta los specs que figuran en `src-frontend/tsconfig.spec.json` («files») **y** en `angular.json`
  (test «include»). Cada spec nuevo se añade a las dos listas.
- Finales de línea mixtos: antes de cada commit, `git diff --stat` debe coincidir con
  `git diff --ignore-cr-at-eol --stat`. No se reescriben ficheros enteros.
- Commits convencionales en español, sin `Co-Authored-By` ni atribución a IA.
- UI: se trabaja con las skills `impeccable` y `frontend-design` y se adjuntan capturas antes de la PR. Lo visual lo
  decide quien construye el frontend; este plan no lo fija.
- Cada ruta nueva lleva su línea en `src-api/src/test/resources/seguridad/matriz-permisos.txt` y su nota en
  `docs/multi-escuela/matriz-endpoints.md`. Ninguna casilla existente cambia (§7.2).
- El motivo es texto libre y no va nunca al resumen del registro de acciones: allí solo van ids (§8).
- Las piezas de encargos (6c-5 y 6c-8) no salen a producción sin la 6c-6 (§10).

## Puntos de revisión

Los cinco casos que más pueden morder. Cada uno tiene su test en la pieza que lo posee.

1. **Cargos pagados antes de la 6b-1, sin fila `cobro`.** La 187 no los bloquea porque no tienen cobro activo; la 186
   y la 194 sí, porque el examen está pagado. El tipo 1 los admite por `productoAlumnoId` (191).
   - 6c-1: `cambiarPrecioPagadoAntesDe6b1SinCobro_200` y `quitarDeConvocatoriaPagadoSinCobroAntesDe6b1_409`.
   - 6c-2 y 6c-3: `revertirCargoSinCobro_pideAceptaYSoloDesmarca`.
2. **Cobros ya entregados a la sede.**
   - 6c-1: quitar de la convocatoria daba 204 y dejaba el cobro ACTIVO sin cargo; ahora da 409
     (`quitarDeConvocatoriaConCobroEntregado_409_sinCambios`). Cambiar el precio también da 409
     (`cambiarPrecioConCobroEntregado_409`).
   - 6c-3: si el cobro se entrega entre pedir y aceptar, la solicitud queda `NO_APLICABLE`
     (`aceptarTrasEntregarElCobro_NO_APLICABLE_sinEfectos`).
3. **Concurrencia entre aceptar y otra acción.**
   - 6c-1: pagar y quitar de la convocatoria a la vez; nunca queda un cobro ACTIVO sin cargo
     (`pagarYQuitarALaVez_nuncaCobroActivoSinCargo`).
   - 6c-3: aceptar y retirar a la vez; uno gana y el otro recibe 409 «La solicitud ya no está pendiente»
     (`aceptarYRetirarALaVez_unoGana409ElOtro`).
   - 6c-6: registrar la devolución y entregar el cobro a la vez (`devolverYEntregarALaVez_saldoCuadra`).
4. **Sesión solo-profesor y su lista blanca.** `/api/solicitudes/**` da DENIEGA al PROFESOR, porque está fuera de la
   lista blanca. Las rutas del profesor van solo bajo `/api/caja/profesor/**`.
   - 6c-2: líneas de la matriz y `sesionSoloProfesorNoEntraEnSolicitudes`.
   - 6c-4 y 6c-5: `sesionSoloProfesorLaCierraLaCapaDeUrlFueraDeSuListaBlanca` sigue en verde y cada ruta nueva lleva
     su caso en la matriz.
5. **Moiskimdo, sin sedes.** Una solicitud con `sede_id` NULL la ven y la cuentan P y G, porque tienen alcance total.
   R no la ve.
   - 6c-2: `solicitudSinSede_laVenYCuentanPyG_noR`.
   - 6c-1: los dorados de Moiskimdo (`MoiskimdoTesoreriaIdenticaIntegrationTest` y el JSON dorado de alumnos) pasan
     sin cambios.

---

## 1. Mapa de piezas

Orden de lanzamiento (§12): **6c-1** primero y sola. Después, para Moiskimdo, 6c-2 → 6c-3 → 6c-7. Para Sierra Sur,
6c-5 y 6c-4 (independientes entre sí) → 6c-6 → 6c-8 → 6c-9.

Los nombres de esta tabla fijan los contratos entre piezas. Quien construye puede renombrarlos (§11), pero entonces lo
anota en la PR y actualiza este mapa.

| Pieza | Objetivo | Ficheros principales | Produce (lo que usan otras piezas) | Consume | Depende de | Riesgo / revisión | Cómo se verifica |
|---|---|---|---|---|---|---|---|
| **6c-1** backend + mensajes | Bloqueos 186, 187 y 194 sin excepciones; convocatoria y ficha muestran el mensaje del backend. Sin migración | `J/services/impl/AlumnoServiceImpl.java:2208-2222`, `J/services/impl/ProductoAlumnoServiceImpl.java:627-685`, `J/services/impl/ConvocatoriaServiceImpl.java:239-275`, `J/services/CobroService.java`, `F/componentes/endpoints/listado-convocatorias/…component.ts:478-503,606-641`, `F/componentes/endpoints/editar-alumno/editar-alumno.component.ts:1960-1993`, `F/…/productos-alumno/productos-alumno.component.ts:418-422` | `CobroService.EXAMEN_PAGADO_REVIERTE_ANTES`, `PRECIO_CON_COBRO_ACTIVO`, `CUANTIA_EXAMEN_PAGADO` y `boolean tieneCobroActivo(Long cargoId)` | `CobroService.bloquearCargo`; `mensajeDeError` de `F/servicios/endpoints/caja.service.ts:36` | — | Medio (dinero y cambio visible aceptado); revisión opus | §2 de este plan |
| **6c-2** backend | V31_8 completa (tabla `solicitud` con sus 5 columnas únicas virtuales y columnas de `movimiento_caja`); entidad, repositorio, servicio base (crear tipos 1, con o sin cobro, y 3; retirar; denegar; listar por segmento; contador); registro de acciones; rutas de gestión; matriz | `src-api/src/main/resources/db/migration/V31_8__solicitudes_y_devoluciones.sql`, `docs/multi-escuela/postcheck-v31_8.sql`, `J/entities/Solicitud.java`, `TipoSolicitud.java`, `EstadoSolicitud.java`, `J/repositories/SolicitudRepository.java`, `J/services/SolicitudService.java`, `J/services/RegistroAccionesSolicitudes.java`, `J/controllers/SolicitudController.java`, `J/config/SecurityConfiguration.java` (regla `/api/solicitudes/**`), `J/entities/MovimientoCaja.java` (mapea `cobroId`, `productoAlumnoId` y `metodo`, por defecto EFECTIVO, sin usarlos aún), matriz, `matriz-endpoints.md`, `checklist-despliegue.md` | `Optional<Solicitud> SolicitudRepository.bloquearPorId(Long)` (FOR UPDATE y relectura, como `CobroService.bloquearCobro`); `SolicitudService.crear(SolicitudRequests.Crear)`, `retirar(Long)`, `denegar(Long, String respuesta)`, `List<SolicitudDTOs.Solicitud> listar(Segmento)`, `long contador()`; `SolicitudTransiciones.exigir(Solicitud, EstadoSolicitud destino)`: la tabla §6.2 en un solo sitio, con 409 «La solicitud ya no está pendiente»; `@acceso.solicitudEnSede(Long id)` | `AccesoRecursos`, `GestionPorSede.restriccion()`, `AuditoriaService.registrarAccesoSensible` | — | Medio (migración aditiva y rutas nuevas); revisión opus | IT en MySQL con dos sedes; test de migración `@Tag("migracion")`: V31_8 dos veces y tras un fallo a medias; postcheck con la tabla vacía, `metodo` = EFECTIVO en todas las filas y el saldo por sede igual antes y después; matriz con diff solo en líneas nuevas; `solicitudSinSede_laVenYCuentanPyG_noR`; `dosPendientesMismoCobro_409` y `dosPendientesMismoCargoSinCobro_409` (191) |
| **6c-3** backend | Aceptar tipos 1 (con y sin cobro), 2 (193) y 3, con revalidación y `NO_APLICABLE` (§6.5). Detalle en §4 | `SolicitudService`, `ProductoAlumnoServiceImpl` (camino interno de A1 y de A4), `CobroService` (método nuevo de la 193), `CajaService.anularEntrega:411` y `anularMovimiento:543` | `SolicitudDTOs.Solicitud SolicitudService.aceptar(Long id)`; el estado final es ACEPTADA o NO_APLICABLE, con la `respuesta`; `ProductoAlumnoService.revertirPorSolicitud(…)` con motivo «Revertido por solicitud #n: {motivo}», `deshacerEntregadoPorSolicitud(…)` y `CobroService.deshacerEntregado(…)` (§4) | 6c-2 | 6c-2 | Alto (ejecuta reversiones y anulaciones); opus | Por tipo: el estado cambia entre pedir y aceptar y la solicitud queda `NO_APLICABLE` sin efectos; saldo de la sede igual; `aceptarYRetirarALaVez_unoGana409ElOtro`; `aceptarTrasEntregarElCobro_NO_APLICABLE_sinEfectos`; una PENDIENTE cuyo cobro se anuló al borrar el alumno o el cargo pasa a NO_APLICABLE al aceptarla (revalidación): `aceptarConCobroAnuladoPorBorrado_NO_APLICABLE_sinEfectos`; registro `SOLICITUD_ACEPTADA` y `ENTREGA_ANULADA … solicitud=#n` |
| **6c-7** frontend | Contador en la cabecera, bandeja (tres segmentos), diálogo «Pedir al propietario» en tesorería, ficha y caja de sede (190, 191) | `F/core/constants/navigation.constants.ts:11-18`, `F/componentes/vistas/layout/header/header.component.html:232`, ruta nueva en `F/app.routes.ts`, servicio `F/servicios/endpoints/solicitudes.service.ts`, `tesoreria-cobros.component.ts:280`, `productos-alumno.component.html:97`, `caja-sede` | Componente de bandeja y diálogo reutilizable (D6c-8) | Rutas de 6c-2 y 6c-3 | 6c-3 | Medio (visible para Moiskimdo) | Specs registrados en las dos listas: el contador se pide al entrar y al navegar, sin `interval`; G desmarca un cargo pagado → se abre el diálogo y el checkbox vuelve a marcado; con una pendiente no deja pedir otra. Build sin aviso y capturas |
| **6c-5** backend | Encargos: productos encargables (192), pedir con cargo y sede (D6c-9), transiciones, caída del encargo, guardas en cancelar y borrar el cargo, rutas del profesor | `SolicitudService` (o `EncargoService`), `ProductoAlumnoServiceImpl` (cargo por el código de `asignarProductoAAlumno:186` sin el `@PreAuthorize` de gestión), `cancelar:852`, `borrar:816`, `J/controllers/CajaController.java` (`/api/caja/profesor/…`) | `List<CajaDTOs.ProductoVendible> productosEncargables()`; `pedirEncargo(Long alumnoId, PedirEncargo{productoId, nota, sedeId?})`; `marcarEncargado`, `marcarRecibido`, `rechazar(id, motivo)`, `retirarEncargo`, `marcarEntregado`; constante `ENCARGO_EN_CURSO` = «Tiene un encargo en curso: recházalo desde Solicitudes»; `devolucion_pendiente` a true al caer un encargo pagado | 6c-2, `@acceso.alumnoDeMisGrupos:209` | 6c-2 | Alto (crea y cancela cargos); opus | El cargo creado tiene `vendido_por_usuario_id` NULL y aparece en «Por cobrar»; si cae sin cobrar, el cargo se cancela; si cae pagado, `devolucion_pendiente` a true; un cargo en curso da 409 al cancelar y al borrar; la sede se exige solo con varias; matriz con lista blanca |
| **6c-4** backend | Tipo 2: `cobros-entregados-recientes` (30 días, máximo 50, D6c-14) y `pedir-deshacer` (crear la solicitud con `motivo_deshacer`). La aceptación con la 193 la hace la 6c-3 (§4) | `CajaController`, `CajaService`, `SolicitudService` (crear el tipo 2) | — | 6c-2, 6c-3 (`aceptar` y `deshacerEntregadoPorSolicitud`) | 6c-3 | Alto (caja); opus | Pedir valida lo que la 6c-3 revalida al aceptar (cobro ACTIVO, origen PROFESOR, suyo, en una entrega ACTIVA, una pendiente por cobro); un IT de punta a punta: pedir por la ruta del profesor y aceptar deja el saldo igual. **Pendiente de la 6c-3:** tras deshacer un cobro entregado (tipo 2), la entrega pierde ese cobro en `cobroIds` y `CajaDTOs.EntregaCaja` no enlaza el movimiento ERROR, así que «declarado» no cuadra a la vista (el saldo sí): exponer en el DTO de la entrega los ids e importe de sus ERROR |
| **6c-6** backend | Devolución enlazada (153): `crearMovimiento` con `cobroId`; «Registrar devolución» por la sede y por el profesor (195, D6c-10); el saldo suma solo EFECTIVO (D6c-13); «devuelto»; invariantes «no se anula» y «devolución pendiente → 409»; tesorería `devuelto` e `importeDevuelto` (D6c-12); desvincular el examen devuelto y el aviso completo de la 186 (D6c-15) | `CajaService.crearMovimiento:470`, `registrarEntrega:310`, `CobroService.anular:198` y `alBorrarCargos:254`, consultas de saldo de `CobroRepository`, tesorería, `AlumnoServiceImpl.eliminarAlumnoDeConvocatoria` | `CobroService.exigirSinDevolucion(Long cobroId)`; `registrarDevolucionEncargo(Long solicitudId, String nota)` (sede) y `registrarDevolucionProfesor(Long solicitudId, String nota)`; DTO de tesorería `devuelto` e `importeDevuelto`; `EXAMEN_PAGADO_REVIERTE_ANTES` pasa a «El examen está pagado: haz la devolución o revierte el cobro antes» | 6c-2, 6c-5 | 6c-2, 6c-5 | Alto (caja y tesorería); opus | La devolución del profesor deja el saldo a 0 y el cobro fuera de «Llevas encima»; un cobro con devolución da 409 por cada vía (A1, A2, A4, tipo 2) y `alBorrarCargos` lo salta; los totales de tesorería de Moiskimdo son idénticos; `devolverYEntregarALaVez_saldoCuadra` |
| **6c-8** frontend | Mi caja: pestaña «Pedidos» (encargar, con sede si hace falta; retirar; marcar entregado; solicitudes de deshacer) y «Entregados recientes» en «Encima» | `F/componentes/endpoints/mi-caja/*` (patrón de pestañas y `.contador`, `mi-caja.component.html:27-41`) | — | Rutas de 6c-4 y 6c-5 | 6c-4, 6c-5 | Medio | Specs registrados, capturas en móvil, build sin aviso. Ojo: tras deshacer un cobro entregado (tipo 2), la entrega pierde ese cobro en `cobroIds` y su DTO no enlaza el ERROR, así que «declarado» no cuadra a la vista (el saldo sí): pintar los ERROR de la entrega que expone la 6c-4 |
| **6c-9** frontend | «Registrar devolución» en «Pedidos» y en la bandeja; contador de devoluciones pendientes; «Devuelto» en tesorería, ficha y Mi caja; aviso completo de la 186 | Bandeja (6c-7), Mi caja (6c-8), tesorería, ficha | — | Rutas de 6c-6 | 6c-6, 6c-7, 6c-8 | Bajo | Specs registrados, capturas, build sin aviso |

Pruebas mínimas de cada pieza backend (§12): IT en MySQL con dos sedes; Moiskimdo idéntico con ADMIN y MANAGER,
salvo 186, 187 y 194; saldo de la sede igual antes y después en cada ejecución que no mueve dinero real.

---

## 2. Pieza 6c-1: bloqueos 186, 187 y 194 (detallada)

Rama `feature/6c-1-bloqueos` desde `develop`. Sin migración: se despliega con el atajo `desplegar`. Mensajes (los de la
186 son literales del diseño; los de la 187 y la 194 los propone este plan, §11):

| Constante en `CobroService` | Texto |
|---|---|
| `EXAMEN_PAGADO_REVIERTE_ANTES` | `El examen está pagado: revierte el cobro antes` |
| `PRECIO_CON_COBRO_ACTIVO` | `El cargo está cobrado: no se puede cambiar el precio ni la cantidad. Revierte el cobro antes.` |
| `CUANTIA_EXAMEN_PAGADO` | `El examen está pagado: no se puede cambiar la cuantía. Revierte el cobro antes.` |

Las tres se lanzan como `IllegalStateException`, que se traduce en 409 con `{"mensaje": …}`
(`J/exceptions/GlobalExceptionHandler.java:122-125`).

Criterios del plan. El diseño no los fija y se anotan en la PR:
- **«Examen pagado»** (186, 194) significa que el cargo del examen, leído con la fila bloqueada, tiene
  `pagado = true`, **o** que no hay cargo y `alumno_convocatoria.pagado = true`. Cubre los pagos anteriores a la 6b-1
  sin fila `cobro`.
- **187** se aplica solo si hay cobro ACTIVO, tal como lo pide el diseño (§9, fila 5). Un PUT que trae a la vez
  `pagado:false` y otro precio sobre un cargo con cobro activo da 409 y no revierte nada: primero se revierte y luego
  se cambia el precio.
- **Cuantía distinta** (194) significa que, comparada con `Double.compare` y tratando null como distinto, la cuantía
  no coincide con `alumno_convocatoria.cuantia_examen` o, si hay cargo, con `producto_alumno.precio`.

### Tarea 1: 186, quitar de una convocatoria con el examen pagado

**Ficheros:**
- Modificar: `J/services/CobroService.java` (las tres constantes y `tieneCobroActivo`, que usa la tarea 2).
- Modificar: `J/services/impl/AlumnoServiceImpl.java:2206-2222` (`eliminarAlumnoDeConvocatoria`).
- Crear: `T/integracion/BloqueosCargoCobradoIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`,
  `PER_CLASS`, `extends MySqlIntegrationTest`, escuela `EscuelaConSedesDePrueba.crear(…, "it-6c1-bloqueos")`).
  Preparación y ayudas copiadas de `T/integracion/CobrosIntegrationTest.java:82-101` y `:519-631`: `cargo`,
  `inscripcionConCargo`, `convocatoriaDe`, `cargoDe`, `idCobro`, `meterEnEntrega`, `pago`, `pagoConvocatoria`,
  `estado`, `peticion` y `enParalelo`. Usa los tokens `tokenGestor` y `tokenPropietario`.
- Modificar: `T/integracion/CobrosIntegrationTest.java:380-399` (`sacarAlumnoDeConvocatoria_anulaElCobroDelExamen`
  contradice la 186).

**Interfaces:**
- Produce: `public static final String EXAMEN_PAGADO_REVIERTE_ANTES`, `PRECIO_CON_COBRO_ACTIVO` y
  `CUANTIA_EXAMEN_PAGADO` en `CobroService`, y `public boolean tieneCobroActivo(Long cargoId)` (con
  `findFirstByProductoAlumnoIdAndEstado(cargoId, ACTIVO).isPresent()`; null da false).
- Consume: `CobroService.bloquearCargo(Long)` (`CobroService.java:121`).

- [ ] **Paso 1: escribir los tests que fallan** en `BloqueosCargoCobradoIntegrationTest`:
  - `quitarDeConvocatoriaConExamenPagado_409ConAviso_sinCambios`: se ejecuta con `tokenPropietario` y con
    `tokenGestor`, sobre inscripciones distintas. `pagoConvocatoria(i, {"cuantiaExamen":20.0,"pagado":true})` da 200;
    el DELETE `/api/alumnos/{conv}/alumno/{e.aA}` da 409 y el cuerpo trae `mensaje` ==
    `"El examen está pagado: revierte el cobro antes"`. Después, el cargo sigue existiendo con `pagado = 1`, la fila de
    `alumno_convocatoria` existe y el cobro sigue `ACTIVO` con `producto_alumno_id` = cargo.
  - `quitarDeConvocatoriaConCobroEntregado_409_sinCambios`: igual, tras `meterEnEntrega(cobro)`. Antes daba 204.
  - `quitarDeConvocatoriaPagadoSinCobroAntesDe6b1_409`: `UPDATE producto_alumno SET pagado = 1, fecha_pago = NOW(6)`
    y `UPDATE alumno_convocatoria SET pagado = 1`, sin cobro. El DELETE da 409 y no se crea ningún cobro.
  - `quitarDeConvocatoriaInscripcionPagadaSinCargo_409`: inscripción con `producto_alumno_id` NULL y `pagado = 1`. El
    DELETE da 409 y la inscripción sigue.
  - `quitarDeConvocatoriaSinPagar_204_comoHoy`: da 204, el cargo y la inscripción desaparecen y no hay cobros.
  - `quitarTrasDesmarcarElPago_204`: pagar y luego `{"cuantiaExamen":20.0,"pagado":false}` (154) da 200; el DELETE da
    204 y el cobro queda `ANULADO` con motivo `"pago revertido desde la convocatoria"`.
  - `pagarYQuitarALaVez_nuncaCobroActivoSinCargo`: con `enParalelo`, el PUT `{20.0,true}` contra el DELETE. Los
    resultados posibles son {200, 409} o {400 o 404, 204}. En ambos casos,
    `SELECT COUNT(*) FROM cobro WHERE organizacion_id = e.org AND estado = 'ACTIVO' AND producto_alumno_id IS NULL`
    == 0.
- [ ] **Paso 2: comprobar que fallan.**
  `mvn test -Dtest=BloqueosCargoCobradoIntegrationTest -DexcludedGroups=migracion`. Se espera FAIL: hoy los DELETE
  dan 204 y la constante no existe (error de compilación hasta crearla; se crea vacía primero si hace falta).
- [ ] **Paso 3: implementar** en `eliminarAlumnoDeConvocatoria`. Antes de `alBorrarCargos`, se bloquea y relee el
  cargo con `cobros.bloquearCargo(productoAlumno.getId())`. El método ya es `@Transactional`, y el orden de bloqueo
  es el de §6.2: primero el cargo. Si el examen está pagado (criterio de arriba), se lanza
  `new IllegalStateException(CobroService.EXAMEN_PAGADO_REVIERTE_ANTES)`. Sin cargo, se mira
  `alumnoConvocatoria.getPagado()`.
- [ ] **Paso 4: reescribir `CobrosIntegrationTest.sacarAlumnoDeConvocatoria_anulaElCobroDelExamen`** como
  `sacarAlumnoDeConvocatoria_conExamenPagado_409_yElCobroSigueActivo`: las dos ramas (normal y entregada) dan 409 y el
  cobro sigue ACTIVO con su cargo. La anulación «cargo borrado» de `alBorrarCargos` sigue cubierta por
  `borrarAlumno_…` y `borrarConvocatoria_…`, que no cambian (A14 y A15, §9).
- [ ] **Paso 5: comprobar que pasan.**
  `mvn test -Dtest='BloqueosCargoCobradoIntegrationTest,CobrosIntegrationTest' -DexcludedGroups=migracion`. Se espera
  PASS.
- [ ] **Paso 6: commit.** `feat(convocatorias): bloquear quitar a un alumno con el examen pagado (186)`.

### Tarea 2: 187, precio o cantidad de un cargo con cobro activo

**Ficheros:**
- Modificar: `J/services/impl/ProductoAlumnoServiceImpl.java:628-638` (`aplicarCambiosAlCargoBloqueado`, justo
  después de `exigirEdicionPermitidaAlResponsable`; así el 403 de D6-20 para R sigue saliendo antes).
- Test: `T/integracion/BloqueosCargoCobradoIntegrationTest.java`.

**Interfaces:**
- Consume: `CobroService.tieneCobroActivo(Long)` y `PRECIO_CON_COBRO_ACTIVO` (tarea 1).

- [ ] **Paso 1: escribir los tests que fallan.** El producto de la preparación vale 10.5; el cargo se cobra con
  `pago(cargo, {"pagado":true})`.
  - `cambiarPrecioConCobroActivo_409_sinCambios`: se ejecuta con P y con G. `{"precio":12.0}` da 409 y `mensaje` ==
    `PRECIO_CON_COBRO_ACTIVO`. El precio sigue en 10.5 y el cobro sigue ACTIVO con `importe` 10.50.
  - `cambiarCantidadConCobroActivo_409_sinCambios`: `{"cantidad":2}` da 409 y la cantidad sigue en 1.
  - `mismoPrecioYCantidadConCobroActivo_200`: `{"precio":10.5,"cantidad":1,"pagado":true}` da 200. La ficha envía el
    DTO entero.
  - `conceptoFechaYNotasConCobroActivo_200`:
    `{"concepto":"CAMISETA M","notas":"talla M","fechaAsignacion":"2026-10-01T10:00:00.000+00:00"}` da 200 y los tres
    campos quedan guardados.
  - `revertirYCambiarPrecioALaVez_409_sinRevertir`: P manda `{"pagado":false,"precio":12.0}` y recibe 409; el cargo
    sigue pagado y el cobro ACTIVO.
  - `cambiarPrecioConCobroEntregado_409`: tras `meterEnEntrega`, `{"precio":12.0}` da 409.
  - `cambiarPrecioPagadoAntesDe6b1SinCobro_200`: con `pagado = 1` por SQL y sin cobro, `{"precio":12.0}` da 200 y el
    precio pasa a 12.0 (§9, fila 5).
  - `cobrarConPrecioNuevoSinCobroPrevio_200`: sobre un cargo pendiente, `{"pagado":true,"precio":12.0}` da 200 y el
    cobro ACTIVO tiene `importe` 12.00.
  - `cambiarPrecioTrasRevertir_200`: P revierte (`{"pagado":false}`, 200) y luego `{"precio":12.0}` da 200.
- [ ] **Paso 2: comprobar que fallan.**
  `mvn test -Dtest=BloqueosCargoCobradoIntegrationTest -DexcludedGroups=migracion`. Se espera FAIL en los tres que
  esperan 409.
- [ ] **Paso 3: implementar.** Si `cobros.tieneCobroActivo(productoAlumno.getId())` y el DTO trae una cantidad
  distinta (`!equals`) o un precio distinto (`Double.compare` o null guardado), se lanza
  `IllegalStateException(PRECIO_CON_COBRO_ACTIVO)`. Se comprueba antes de aplicar ningún campo. Vale para cualquier
  origen, también PROFESOR: «sin excepciones», D6c-15.
- [ ] **Paso 4: comprobar que pasan.** Mismo comando, más `-Dtest=ProductoAlumnoServiceImplTest` (unitario con
  `CobroService` simulado: `tieneCobroActivo` devuelve false y no cambia nada). Se espera PASS.
- [ ] **Paso 5: commit.** `feat(cargos): bloquear cambiar precio o cantidad con cobro activo (187)`.

### Tarea 3: 194, cuantía del examen pagado desde la convocatoria

**Ficheros:**
- Modificar: `J/services/impl/ConvocatoriaServiceImpl.java:249-258` (dentro de `cobros.enTransaccion`, tras
  `bloquearCargo` y `pagadoAntes`, antes del `setPrecio` de la línea 259).
- Test: `T/integracion/BloqueosCargoCobradoIntegrationTest.java`.

**Interfaces:**
- Consume: `CUANTIA_EXAMEN_PAGADO` (tarea 1).

- [ ] **Paso 1: escribir los tests que fallan** (PUT `/api/convocatorias/alumno/{id}`):
  - `cuantiaDistintaConExamenPagado_409_sinCambios`: se ejecuta con P y con G. Pagar con `{20.0,true}` y mandar
    `{25.0,true}` da 409 y `mensaje` == `CUANTIA_EXAMEN_PAGADO`. Quedan `cuantia_examen` 20, `precio` 20 y el cobro
    ACTIVO con `importe` 20.00.
  - `mismaCuantiaConExamenPagado_200`: `{20.0,true}` sobre el examen pagado da 200 y sigue habiendo un solo cobro.
  - `cuantiaDistintaSinPagar_200_comoHoy`: `{25.0,false}` sobre un examen sin pagar da 200 y `precio` pasa a 25.
  - `pagarConCuantiaNueva_200`: de no pagado a `{25.0,true}` da 200 y el cobro tiene `importe` 25.00.
  - `desmarcarConCuantiaDistinta_200_decision154`: del examen pagado a `{25.0,false}` da 200 y el cobro queda ANULADO
    con motivo `"pago revertido desde la convocatoria"`.
  - `cuantiaDistintaPagadoSinCobroAntesDe6b1_409`: con cargo e inscripción pagados por SQL y sin cobro, `{25.0,true}`
    da 409.
  - `cuantiaDistintaInscripcionPagadaSinCargo_409`: `{25.0,true}` da 409 y la inscripción no cambia.
- [ ] **Paso 2: comprobar que fallan.** Mismo comando. Se espera FAIL en los tres que esperan 409.
- [ ] **Paso 3: implementar.** Si el examen está pagado antes, el DTO trae `pagado == true` y la cuantía es distinta
  (criterio de arriba), se lanza `IllegalStateException(CUANTIA_EXAMEN_PAGADO)` dentro de la transacción. Así se
  deshace todo y la inscripción no se guarda. El caso sin cargo se comprueba también dentro del lambda, con
  `alumnoConvocatoria.getPagado()`.
- [ ] **Paso 4: comprobar que pasan**, más `CobrosIntegrationTest` y
  `ResponsableSedePreinscripcionesConvocatoriasIntegrationTest`. Se espera PASS.
- [ ] **Paso 5: commit.** `feat(convocatorias): bloquear cambiar la cuantía de un examen pagado (194)`.

### Tarea 4: mensajes del backend en convocatoria y ficha

**Ficheros:**
- Modificar: `F/componentes/endpoints/listado-convocatorias/listado-convocatorias.component.ts`:
  - `actualizarAlumnoConvocatoria` (`:478-503`): el error muestra `mensajeDeError(e) ?? 'No se pudo actualizar el
    alumno.'` y recarga con `seleccionarConvocatoria(this.convocatoriaSeleccionada)`, para que la cuantía vuelva a la
    guardada.
  - `eliminarAlumnoDeConvocatoria` (`:606-641`): `mensajeDeError(e) ?? 'No se pudo eliminar al alumno.'`.
- Modificar: `F/componentes/endpoints/editar-alumno/editar-alumno.component.ts:1960-1993`:
  `mensajeDeError(e) ?? 'No se pudo eliminar al alumno de la convocatoria.'`.
- Modificar: `F/componentes/endpoints/editar-alumno/productos-alumno/productos-alumno.component.ts:418-422`: el
  `catchError((e) => …)` muestra `showErrorToast(mensajeDeError(e) ?? 'No se pudo actualizar un producto')`. El
  `complete` ya recarga los productos (`:476`), así que el precio vuelve al guardado.
- Crear: `listado-convocatorias/listado-convocatorias.bloqueos.spec.ts`, `editar-alumno/editar-alumno.bloqueos.spec.ts`
  y `editar-alumno/productos-alumno/productos-alumno.bloqueos.spec.ts`, registrados en `tsconfig.spec.json` («files»)
  **y** en `angular.json` (test «include»). Se instancian como `editar-alumno.tarifas.spec.ts`
  (`TestBed.runInInjectionContext` con dobles del servicio que devuelven `throwError(() => new
  HttpErrorResponse({status: 409, error: {mensaje: …}}))`). Se espían `Swal.fire`, que devuelve
  `Promise.resolve({isConfirmed: true})`, y `Toast.fire` (`F/utils/toast.util.ts`).

**Interfaces:**
- Consume: `mensajeDeError(error: unknown): string | null` (`F/servicios/endpoints/caja.service.ts:36`).

- [ ] **Paso 1: escribir los specs que fallan:**
  - Listado: «quitar con el examen pagado muestra el aviso del backend». `Swal.fire` recibe
    `jasmine.objectContaining({ icon: 'error', text: 'El examen está pagado: revierte el cobro antes' })`.
  - Listado: «sin mensaje del backend, el texto de siempre». Con 500 y cuerpo vacío, `text` es
    `'No se pudo eliminar al alumno.'`.
  - Listado: «cambiar la cuantía de un examen pagado muestra el aviso y recarga». `text` es el `CUANTIA_EXAMEN_PAGADO`
    del backend y `seleccionarConvocatoria` se llama con la convocatoria seleccionada.
  - Ficha: «quitar de la convocatoria con examen pagado: aviso del backend» (`Swal.fire` con el texto de la 186).
  - Cargos: «guardar un precio nuevo en un cargo cobrado: toast con el mensaje del backend». `Toast.fire` recibe
    `jasmine.objectContaining({ icon: 'error', title: <PRECIO_CON_COBRO_ACTIVO> })`.
- [ ] **Paso 2: comprobar que fallan.** `npm test -- --watch=false --browsers=ChromeHeadless`. Se espera FAIL en los
  cinco: hoy el texto es fijo.
- [ ] **Paso 3: implementar** los cuatro manejadores de error descritos en «Ficheros».
- [ ] **Paso 4: comprobar que pasan** y que el build no da aviso. `npm test -- --watch=false
  --browsers=ChromeHeadless` y `npm run build:production`. Se espera PASS, sin «budget» en la salida.
- [ ] **Paso 5: capturas** (skills `impeccable` y `frontend-design` cargadas): el aviso de la 186 en la convocatoria y
  en la ficha, y el de la 187 en la ficha, con datos de prueba.
- [ ] **Paso 6: commit.** `feat(convocatorias): mostrar el aviso del backend al quitar o cambiar un examen pagado`.

### Tarea 5: batería completa y PR

- [ ] **Paso 1:** `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion`. Se espera BUILD SUCCESS en
  las dos. Si falla un dorado de Moiskimdo (`MoiskimdoTesoreriaIdenticaIntegrationTest`, el JSON dorado de alumnos),
  se para y se avisa: la 6c-1 no debe cambiarlos.
- [ ] **Paso 2:** comprobar que `git diff --stat develop` coincide con `git diff --ignore-cr-at-eol --stat develop`.
- [ ] **Paso 3:** comprobar que la matriz de permisos no cambia (no hay rutas nuevas) y que no hay migración: se
  despliega con el atajo `desplegar`.
- [ ] **Paso 4:** PR a `develop` con los criterios del plan (examen pagado, PUT combinado, cuantía distinta), los
  mensajes propuestos para la 187 y la 194, y las capturas.

---

## 3. Pieza 6c-2: solicitudes base (detallada)

Rama `feature/6c2-solicitudes-base` desde `feature/multi-escuela`. **Con migración** (V31_8): se despliega con el atajo
`desplegar_migrar`. Sin pantallas: Moiskimdo no ve ningún cambio hasta la 6c-7. No acepta solicitudes (6c-3), no crea
encargos (6c-5) ni devoluciones (6c-6).

Criterios del plan. El diseño no los fija y se anotan en la PR:
- **Pedir por el cargo.** `POST /api/solicitudes` de tipo 1 admite `cobroId` **o** `productoAlumnoId` (uno solo). Con
  `productoAlumnoId`, si el cargo tiene cobro ACTIVO la solicitud enlaza ese cobro (como si llegara `cobroId`); si no lo
  tiene, es la 191 (cargo pagado sin cobro). Así la ficha y la tesorería, que conocen el cargo, no necesitan el cobro.
- **Qué se valida al pedir** (al aceptar se revalida, §6.5): tipo 1 con cobro, cobro ACTIVO, sin entrega y con su cargo;
  tipo 1 sin cobro, cargo pagado; entrega ACTIVA; movimiento ACTIVO y no automático. Si no, 409 y no se crea nada.
- **Orden de los errores:** 400 (datos), 404 (no existe en la escuela), 403 (R fuera de sus sedes, o retirar la ajena),
  409 (estado o una pendiente ya abierta).
- **El propietario no pide:** `POST /api/solicitudes` con `ROLE_ADMIN` da 400 «El propietario lo hace directamente: no
  necesita pedirlo.».
- **Retirar en la gestión** vale para los tipos 1 a 3. Un encargo se retira por la ruta del profesor (6c-5): aquí, 400.
- **Denegar** es solo de P (`hasAuthority('ROLE_ADMIN')`) y solo desde PENDIENTE. Un encargo no se deniega, se
  rechaza (6c-5): denegarlo por esta ruta da 400, como retirarlo.
- **Red de la clave única al crear:** una violación de integridad al insertar nunca da 500. La clave única (por su
  nombre o por el error 1062 de MySQL) da 409 «Ya hay una solicitud pendiente sobre este recurso.»; cualquier otra
  (p. ej. la FK de un cargo borrado a la vez), 409 «El recurso ha cambiado mientras se pedía: vuelve a intentarlo.».
  El manejador global no cambia.
- **Bandeja** (`GET /api/solicitudes?segmento=`):
  - `PENDIENTES`: tipos 1–3 en PENDIENTE; P las ve todas, G y R solo las suyas.
  - `ENCARGOS`: encargos PEDIDO, ENCARGADO o RECIBIDO, o caídos con `devolucion_pendiente`; P y G todos, R los de
    sus sedes (sin sede, no).
  - `RESUELTAS`: el resto (tipos 1–3 no pendientes y encargos cerrados), con la misma visibilidad, las 200 más
    recientes por fecha de resolución.
  - Pendientes y encargos, de la más antigua a la más nueva. Segmento desconocido o vacío: 400.
- **Contador** (`GET /api/solicitudes/contador` → `{"total": n}`), dos `COUNT` por índice como mucho:
  - P: tipos 1–3 PENDIENTES + encargos PEDIDO o ENCARGADO.
  - G: encargos PEDIDO o ENCARGADO.
  - R: lo mismo que G, de sus sedes.
  - El término «encargos con devolución pendiente que le tocan a la sede» (§5.1) depende de dónde está el dinero y lo
    añade la 6c-6.
- **Registro:** módulo `solicitudes`, resumen `SOLICITUDES <ACCION> sede=#S solicitud=#N cobro=#C cargo=#G …` (solo
  ids; `sede=-` si la solicitud no tiene sede). Ni el motivo ni la respuesta.
- **FK de `solicitud`:** a cobro, entrega y movimiento, sin acción (esas filas no se borran); a usuario, alumno,
  cargo y producto, ON DELETE SET NULL (borrar un producto del catálogo o un cargo no falla); a sede, SET NULL como en
  `cobro`. `movimiento_caja.producto_alumno_id` se guarda (no se deduce del cobro): la 153 pide el enlace al cargo y
  `cobro.producto_alumno_id` pasa a NULL si el cargo se borra.

### Tarea 1: V31_8, postcheck y tests de migración

**Ficheros:**
- Crear: `src-api/src/main/resources/db/migration/V31_8__solicitudes_y_devoluciones.sql`.
  - `CREATE TABLE IF NOT EXISTS solicitud` con las columnas de §6.1, sus cuatro columnas VIRTUAL con clave única
    (`uk_solicitud_cobro_pendiente`, `uk_solicitud_entrega_pendiente`, `uk_solicitud_movimiento_pendiente`,
    `uk_solicitud_cargo_pendiente`), los índices de §6.1 y 13 FK.
  - En `movimiento_caja`, con guarda en `information_schema` y una sentencia por cambio (patrón de V31_6), cuatro
    columnas al final: `cobro_id`, `producto_alumno_id`, `metodo` (`VARCHAR(20) NOT NULL DEFAULT 'EFECTIVO'`) y
    `cobro_devuelto_activo_id` (VIRTUAL). Después, los índices `idx_movimiento_caja_cobro`,
    `idx_movimiento_caja_producto_alumno` y `uk_movimiento_caja_cobro_devuelto_activo`, y las FK
    `fk_movimiento_caja_cobro` (sin acción) y `fk_movimiento_caja_producto_alumno` (SET NULL), con
    `foreign_key_checks = 0` solo si no hay huérfanos.
- Crear: `docs/multi-escuela/postcheck-v31_8.sql` (solo lectura, solo recuentos; ver la tarea).
- Crear: `T/integracion/SolicitudesV31_8MigracionIntegrationTest.java` (`@Tag("migracion")`, esquema
  `taemoi_v31_8_it`, base en V31_7 con cobros, entregas y movimientos de dos sedes y dos escuelas).
- Modificar: `T/integracion/MySqlSmokeIntegrationTest.java` y `T/integracion/MembresiaMigracionIntegrationTest.java`
  («hasta la última» → `31.8`); `T/integracion/BorradoAlumnoIntegrationTest.java` (inventario de FK hacia `alumno`:
  `solicitud.alumno_id` SET NULL; `eliminarAlumno` no cambia).

- [ ] **Paso 1: escribir los tests que fallan** en `SolicitudesV31_8MigracionIntegrationTest`:
  - `llegaAV31_8`: la última versión aplicada es `31.8`.
  - `creaSolicitudVacia_conClavesVirtualesYFk`: 0 filas; columnas exactas en orden; las cuatro `*_pendiente_id` son
    `VIRTUAL GENERATED` y tienen su clave única; las 13 FK con su regla (SET NULL las de usuario, alumno, cargo,
    producto y sede; NO ACTION las de cobro, entrega y movimiento); `utf8mb4_0900_ai_ci`.
  - `movimientoCaja_cuatroColumnasAlFinal_metodoEfectivo_yNadaMasCambia`: columnas = las de antes + las cuatro
    nuevas; `metodo` = `EFECTIVO` en todas las filas; el resto de cada fila, igual que antes.
  - `saldoPorSede_igualAntesYDespues`: Σ cobros (fórmula de `CajaService`) + Σ movimientos ACTIVOS por sede, igual antes
    y después; y también con solo los movimientos EFECTIVO (D6c-13).
  - `postcheck_tablaVaciaColumnasYVersion`: los valores de la tarea, con `movimientos_no_efectivo` = 0.
  - `clavesUnicas_unaPendientePorRecurso_ylasResueltasNoCuentan`: dos PENDIENTE del mismo cobro, entrega,
    movimiento o (tipo 1) cargo sin cobro → `DuplicateKeyException`; con la primera RETIRADA, la segunda entra; dos
    ENCARGO del mismo producto entran; dos DEVOLUCION ACTIVAS del mismo cobro → `DuplicateKeyException`, una ANULADA
    y otra ACTIVA conviven.
  - `insertarMovimientoSinLasColumnas_quedaEfectivo`: el INSERT del código anterior deja `metodo` = `EFECTIVO`.
  - `borrarCargoOCuenta_dejaLasReferenciasANull`: borrar el cargo pone a NULL `solicitud.producto_alumno_id` y
    `movimiento_caja.producto_alumno_id`; borrar la cuenta, `solicitado_por_usuario_id`.
  - `repetirla_tambienTrasUnFalloAMedias_noCambiaNada`: dos ejecuciones más no cambian filas ni esquema. Fallo a
    medias: sin `solicitud` y sin las FK, índices y columnas nuevas de `movimiento_caja`; las cuatro columnas se
    vuelven a añadir con `ALGORITHM=INSTANT` (prueba de coste) y dos ejecuciones dejan el esquema completo y el
    postcheck limpio.
- [ ] **Paso 2: comprobar que fallan.** `mvn test -Dtest=SolicitudesV31_8MigracionIntegrationTest -Dgroups=migracion`.
  Se espera FAIL: no existe V31_8.
- [ ] **Paso 3: implementar** V31_8 y el postcheck. Recuentos del postcheck:
  - `tabla_solicitud` 1, `filas_solicitud` 0, `columnas_virtuales_solicitud` 4, `uk_solicitud` 4, `fk_solicitud` 13
    (`fk_solicitud_set_null` 8, `fk_solicitud_sin_accion` 5);
  - `columnas_movimiento_caja` 4, `metodo_no_nulo_efectivo` 1, `uk_cobro_devuelto_activo` 1, `fk_movimiento_caja` 8
    (`fk_movimiento_caja_set_null` 4, `fk_movimiento_caja_sin_accion` 4);
  - `flyway_31_8` 1, `movimientos_no_efectivo` 0, `sedes_con_saldo_distinto_solo_efectivo` 0;
  - informativas: `movimientos_con_cobro` 0 y `solicitudes_pendientes` 0 hasta la 6c-6 y la 6c-7.
- [ ] **Paso 4: comprobar que pasan**, más `MySqlSmokeIntegrationTest`, `MembresiaMigracionIntegrationTest` y
  `BorradoAlumnoIntegrationTest`. Se espera PASS.

### Tarea 2: entidades, repositorio, transiciones y `MovimientoCaja`

**Ficheros:**
- Crear: `J/entities/Solicitud.java` (`extends EntidadOrganizacion`, ids simples, `@Version`, sin mapear las columnas
  virtuales), `J/entities/TipoSolicitud.java`, `J/entities/EstadoSolicitud.java`.
- Crear: `J/repositories/SolicitudRepository.java` (`JpaRepository` + `JpaSpecificationExecutor`).
- Crear: `J/services/SolicitudTransiciones.java` (la tabla §6.2 en un solo sitio).
- Modificar: `J/entities/MovimientoCaja.java`: `cobroId`, `productoAlumnoId` y `metodo` (`MetodoPago`, por defecto
  `EFECTIVO`). Ni `CajaService` ni los DTO los usan aún.
- Crear: `T/services/SolicitudTransicionesTest.java` (unitario).

**Interfaces:**
- Produce: `Optional<Solicitud> SolicitudRepository.bloquearPorId(Long id)` (`@Lock(PESSIMISTIC_WRITE)`; la relectura la
  hace el servicio, como `CobroService.bloquearCobro`).
- Produce: `SolicitudTransiciones.exigir(Solicitud s, EstadoSolicitud destino)` (409 `NO_PENDIENTE` = «La solicitud ya
  no está pendiente»), `boolean permitida(TipoSolicitud, EstadoSolicitud desde, EstadoSolicitud destino)` y
  `EstadoSolicitud estadoInicial(TipoSolicitud)` (PENDIENTE o PEDIDO).

- [ ] **Paso 1: escribir los tests que fallan** en `SolicitudTransicionesTest`:
  - `tipos1a3_desdePendiente_aRetiradaDenegadaAceptadaONoAplicable`: para cada tipo de 1 a 3 y cada par de estados,
    `permitida` es cierto solo en esas cuatro.
  - `encargo_tablaDeLa6_2`: PEDIDO → RETIRADA, ENCARGADO o RECHAZADO; ENCARGADO → RECIBIDO o RECHAZADO; RECIBIDO →
    ENTREGADO o RECHAZADO; nada más.
  - `exigir_fueraDeLaTabla_409NoPendiente_sinCambiarElEstado`: `IllegalStateException` con `NO_PENDIENTE` y el estado
    sigue igual; dentro de la tabla no lanza.
  - `estadoInicial_pendienteOPedido`.
- [ ] **Paso 2: comprobar que fallan.** `mvn test -Dtest=SolicitudTransicionesTest`. Se espera error de compilación.
- [ ] **Paso 3: implementar** enums, entidad, repositorio, transiciones y el mapeo de `MovimientoCaja`.
- [ ] **Paso 4: comprobar que pasan**, más `CajaSedeIntegrationTest` (el mapeo nuevo no cambia la caja). Se espera
  PASS; el arranque con `ddl-auto=validate` comprueba el mapeo contra V31_8.

### Tarea 3: servicio, registro, rutas de gestión y seguridad

**Ficheros:**
- Crear: `J/dtos/request/SolicitudRequests.java` (`Crear{tipo, cobroId, productoAlumnoId, entregaId, movimientoId,
  motivo}`, `Denegar{respuesta}`), `J/dtos/response/SolicitudDTOs.java` (`Solicitud`, `Contador`).
- Crear: `J/services/SolicitudService.java`, `J/services/RegistroAccionesSolicitudes.java` (módulo `solicitudes`,
  copia de `RegistroAccionesCaja`, acciones de §8), `J/controllers/SolicitudController.java`.
- Modificar: `J/config/SecurityConfiguration.java`: `/api/solicitudes` y `/api/solicitudes/**` para ADMIN, MANAGER y
  RESPONSABLE_SEDE, antes de `/api/admin/**`. Fuera de la lista blanca de la sesión solo-profesor (no se toca).
- Crear: `T/integracion/SolicitudesIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `PER_CLASS`,
  `extends MySqlIntegrationTest`; dos escuelas `EscuelaConSedesDePrueba` con sedes A y B, propietario, gestor,
  responsable de A, profesor de gA; ayudas de `CajaSedeIntegrationTest`).

**Interfaces:**
- Produce: `SolicitudDTOs.Solicitud SolicitudService.crear(SolicitudRequests.Crear)`, `retirar(Long id)`,
  `denegar(Long id, String respuesta)`, `List<SolicitudDTOs.Solicitud> listar(SolicitudService.Segmento)`,
  `long contador()`, y `Solicitud bloquear(Long id)` (bloquea y relee; 404 si no existe).
- Rutas: `GET /api/solicitudes?segmento=`, `GET /api/solicitudes/contador`, `POST /api/solicitudes`,
  `POST /api/solicitudes/{id}/retirar` (P, G y R; el servicio filtra o comprueba) y `POST /api/solicitudes/{id}/denegar`
  (solo P).
- Consume: `GestionPorSede.restriccion()` y `exigirSedePropia`, `AuditoriaService.registrarAccesoSensible`,
  `CobroService.usuarioActualId`, `alumnoDe` e `importeDe`.

- [ ] **Paso 1: escribir los tests que fallan** en `SolicitudesIntegrationTest`:
  - `gestor_pideRevertirUnCobro_pendienteConInstantanea_sinTocarElCobroNiLaCaja`: G pide sobre un cobro de tesorería
    (201). La fila queda PENDIENTE, REVERTIR_COBRO, con la sede, el alumno, el concepto, el importe y el cargo del cobro
    y `solicitado_por` = G. El cobro sigue ACTIVO, el cargo pagado y el `saldoCaja` de la sede no cambia.
  - `pedirPorElCargoConCobroActivo_enlazaElCobro`.
  - `revertirCargoSinCobro_pideAceptaYSoloDesmarca` (parte de la 6c-2: pedir; la 6c-3 añade aceptar): un cargo pagado
    por SQL sin cobro, pedido por `productoAlumnoId`, da 201 con `cobro_id` NULL y `producto_alumno_id` = el cargo.
    No se crea ningún cobro y el cargo sigue pagado.
  - `dosPendientesMismoCobro_409`: la segunda (por `cobroId` o por el cargo) da 409 y no se crea; tras retirar la
    primera, se puede pedir otra vez.
  - `dosCrearALaVezMismoCobro_201Y409_unaSolaPendiente` (con `enParalelo`) y
    `claveUnicaComoUltimaRed_insertEnCurso_409`: otra transacción tiene un INSERT sin confirmar del mismo cobro; la
    petición espera a la clave y, al confirmar la otra, da 409 sin registro. `SolicitudServiceErroresTest` (unitario):
    clave por nombre, por el 1062 o `DuplicateKeyException` → «ya pendiente»; otra violación → «el recurso ha cambiado».
  - `dosPendientesMismoCargoSinCobro_409` (191).
  - `pedirAnularEntregaOMovimiento_tipo3`: entrega ACTIVA (importe = el declarado) y movimiento suelto ACTIVO dan 201;
    la segunda pendiente, 409; el movimiento automático de una entrega, 409; una entrega anulada, 409.
  - `crear_validaciones`: P da 400; motivo vacío o de 501 caracteres, 400; tipo 2 o encargo, 400; sin recurso o con
    dos, 400; cobro inexistente o de otra escuela, 404; cobro anulado o entregado, 409; cargo no pagado, 409.
  - `responsable_soloPideSobreRecursosDeSusSedes`: R de A pide sobre A (201); sobre B o un cargo sin sede, 403.
  - `retirar_soloLaSuya_403ParaOtros`: otro (G o P) da 403; quien pidió, 200 (RETIRADA, `resuelto_por` = él); otra
    vez, 409 «La solicitud ya no está pendiente»; inexistente, 404.
  - `denegar_soloPropietario_conRespuesta`: G y R, 403; P sin respuesta, 400; P, 200 (DENEGADA, con `respuesta` y
    `resuelto_por`); otra vez, 409; retirarla después, 409; un encargo, 400 y sigue PEDIDO.
  - `denegarYRetirarALaVez_unoGana409ElOtro`: con `enParalelo`, resultados {200, 409} y un solo estado final.
  - `listarPorSegmento_propietarioTodo_gestorLasSuyas`: segmentos y orden; RESUELTAS con la respuesta; segmento
    desconocido, 400.
  - `responsable_veYCuentaSoloLoSuyoYLosEncargosDeSusSedes`: encargos insertados por SQL (la 6c-5 aún no existe) en
    A, en B y sin sede.
  - `contador_propietarioPendientesYEncargos_gestorEncargos`: valores exactos.
  - `solicitudSinSede_laVenYCuentanPyG_noR`: tipo 1 sobre un cargo sin sede (G) y encargo PEDIDO sin sede. P las ve y
    cuenta las dos; G ve las dos y cuenta el encargo; R no ve ninguna y cuenta 0.
  - `sesionSoloProfesorNoEntraEnSolicitudes`: solo PROFESOR, RESPONSABLE_SEDE + PROFESOR, FAMILIA + PROFESOR y FAMILIA
    reciben 403 en las cinco rutas.
  - `registroDeAcciones_soloIds_sinMotivo`: `SOLICITUDES SOLICITUD_CREADA sede=#A solicitud=#n cobro=#c cargo=#g`,
    `SOLICITUD_RETIRADA` y `SOLICITUD_DENEGADA`, con quién y `recurso_id`. Ni el motivo ni la respuesta; un intento
    fallido no deja registro.
  - `aislamientoDeEscuela`: la gestión de otra escuela no la ve (lista y contador) y recibe 404 al retirarla o
    denegarla.
  - `movimientoNuevo_quedaEfectivoSinCobroNiCargo_yLaRespuestaNoCambia`.
- [ ] **Paso 2: comprobar que fallan.** `mvn test -Dtest=SolicitudesIntegrationTest -DexcludedGroups=migracion`. Se
  espera error de compilación o 404 en las rutas.
- [ ] **Paso 3: implementar.** Cada escritura va en una `TransactionTemplate` READ_COMMITTED, con su registro en la
  misma transacción. Retirar y denegar bloquean la fila (`bloquear`) antes de `SolicitudTransiciones.exigir`. La
  clave única es la última red: un `DataIntegrityViolationException` al crear da 409 «Ya hay una solicitud pendiente
  sobre este recurso.».
- [ ] **Paso 4: comprobar que pasan.** Mismo comando. Se espera PASS.

### Tarea 4: `@acceso.solicitudEnSede`

**Ficheros:** `J/acceso/AccesoRecursos.java`, `ConsultaAccesoRecursos.java`, `ConsultaAccesoRecursosJpa.java`,
`T/seguridad/RegistradorConsultaAcceso.java`, `T/acceso/AccesoRecursosTest.java`.

**Interfaces:**
- Produce: `boolean AccesoRecursos.solicitudEnSede(Long id)`: alcance total → `true` sin consultar; R → la sede de la
  solicitud es una de las suyas (sin sede, `false`); nunca la rama de profesor ni la de familia. Puerto
  `solicitudEnSedes(Long solicitudId, Set<Long> sedes)`. Ninguna ruta de la 6c-2 lo usa: lo consumen la 6c-5 y la 6c-6.

- [ ] **Paso 1: escribir los tests que fallan:**
  - `AccesoRecursosTest.solicitudEnSede_gestionSinConsultar_responsablePorSusSedes_profesorYFamiliaNunca`.
  - `SolicitudesIntegrationTest.consultaJpa_solicitudEnSedes_porLaSedeDeLaSolicitud`: por la sede, otra escuela
    `false`, sin sede `false`.
- [ ] **Paso 2: comprobar que fallan** (compilación) e **implementar**.
- [ ] **Paso 3: comprobar que pasan.**

### Tarea 5: matriz, documentación y batería completa

- [ ] **Paso 1:** añadir a `matriz-permisos.txt` las siete líneas nuevas (GET y HEAD de la lista y del contador, POST de
  crear, retirar y denegar), con la decisión que sugiere `MatrizPermisosTest`. PROFESOR, FAMILIA, ANONIMO y
  SOLO_PLATAFORMA dan DENIEGA; denegar, solo PROPIETARIO. Las de R con PERMITE van en
  `RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO` con su test. Ninguna casilla existente cambia: el diff solo añade líneas.
- [ ] **Paso 2:** nota en `matriz-endpoints.md` y línea de V31_8 en `checklist-despliegue.md` (atajo
  `desplegar_migrar`).
- [ ] **Paso 3:** `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion`. Se espera BUILD SUCCESS en las
  dos, con los dorados de Moiskimdo sin tocar.
- [ ] **Paso 4:** `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`. Commit
  `feat(solicitudes): tabla, rutas de gestión y registro de las solicitudes al propietario (6c-2)`.

---

## 4. Pieza 6c-3: aceptar (detallada)

Rama `feature/6c3-aceptar` desde `feature/multi-escuela` (tras la 6c-2). **Sin migración**: se despliega con el atajo
`desplegar`. Sin pantallas (la bandeja es de la 6c-7): Moiskimdo no ve ningún cambio. Acepta los tipos 1 (con y sin
cobro, 191), 2 (193) y 3. El tipo 2 aún no se puede pedir (la ruta es de la 6c-4): sus tests insertan la solicitud por
SQL, como la 6c-2 con los encargos.

Criterios del plan. El diseño no los fija y se anotan en la PR:
- **Ruta:** `POST /api/solicitudes/{id}/aceptar`, sin cuerpo, solo P (`hasAuthority('ROLE_ADMIN')` y el servicio lo
  vuelve a exigir). Responde 200 con la solicitud: `ACEPTADA` (respuesta vacía) o `NO_APLICABLE` (respuesta con la
  razón, para que P vea el aviso). 409 `NO_PENDIENTE` si ya no está pendiente; 404 si no existe en la escuela; 400 si es
  un encargo («Un encargo no se acepta: se marca encargado.»).
- **Orden de bloqueos:** primero la fila de la `solicitud` y después los de siempre de la acción. Tipo 1: solicitud →
  cargo → cobro (el de A1). Tipo 2: solicitud → cargo → entrega → cobro (la anulación de una entrega bloquea entrega →
  cobros; registrar una entrega, solo cobros). Tipo 3: solicitud → entrega → cobros (A7) o solicitud → movimiento (A9).
  Crear, retirar y denegar no bloquean una solicitud después de esas filas. **Excepción:** borrar un cargo (quitar de
  una convocatoria, borrar un alumno o una convocatoria) pasa por las FK ON DELETE SET NULL de `solicitud` (V31_8) y
  bloquea sus filas después del cargo (y, al borrar un alumno o una convocatoria, el UPDATE masivo de
  `alBorrarCargos` toma los cobros antes que los cargos). Ese cruce con aceptar puede producir un interbloqueo:
  InnoDB lo detecta y elige una víctima, `GlobalExceptionHandler` la devuelve como 409 (concurrencia) y su
  transacción se deshace entera. Si la víctima es aceptar, la solicitud sigue PENDIENTE y al reintentar queda
  `NO_APLICABLE` (el cargo ya no existe). Sin efecto en el dinero.
- **Revalidación sin excepciones:** con las filas ya bloqueadas, se comprueba todo antes de ejecutar y, si no aplica,
  se cierra `NO_APLICABLE` con la razón y sin tocar nada más. Así los servicios de siempre no lanzan dentro de la
  transacción (un error dentro de una transacción en curso la marca para deshacer). **Red:** si aun así la ejecución
  lanza un error de negocio (400, 404 o 409), se deshace todo y, en otra transacción, con la fila bloqueada y si sigue
  PENDIENTE, se cierra `NO_APLICABLE` con ese mensaje: la solicitud no se queda pendiente (§6.5).
- **Razones** (`respuesta` de `NO_APLICABLE`, texto fijo, constantes de `EjecucionSolicitud`): «El cargo ya no
  existe.», «El cargo ya no está pagado.», «El cobro ya no está activo.», «El cobro ya va en una entrega de caja.»,
  «El cargo tiene ahora un cobro activo: pide revertir ese cobro.», «El cobro tiene una devolución registrada.», «El
  encargo tiene una devolución pendiente: regístrala antes.», «El cobro ya no va en una entrega: el profesor puede
  deshacerlo él mismo.», «El cobro ha pasado a otra entrega mientras se aceptaba: vuelve a intentarlo.», «El cobro no
  es del profesor que lo pidió.», «No fue una venta suya: no se deshace como «no
  hubo venta».», «La entrega ya está anulada.», «El movimiento ya está anulado.», «Es la diferencia automática de una
  entrega: pide anular la entrega.» (SOBRANTE o FALTANTE) y «Es el ERROR de un cobro deshecho en una entrega: se anula
  con la entrega; pide anular la entrega.» (el ERROR de la 193; mismo texto al pedirlo).
- **Tipo 1** (`ProductoAlumnoService.revertirPorSolicitud`): el camino de A1 (`aplicarCambiosAlCargoBloqueado` con
  `pagado:false`, el cargo ya bloqueado), motivo de la anulación «Revertido por solicitud #n: {motivo}» (500 como
  mucho). Sin cobro (191) solo desmarca. La sincronización con la convocatoria y el derecho a examen va **después** de
  confirmar, como en A1 (`aplicarEfectosDelCambioDePago` fuera de la transacción): así el orden de bloqueos es el de A1
  y no se cruza con el borrado del alumno.
- **Tipo 2** (`ProductoAlumnoService.deshacerEntregadoPorSolicitud` + `CobroService.deshacerEntregado`): revalida cobro
  ACTIVO de su cargo, origen PROFESOR, cobrado por quien pidió, en una entrega ACTIVA, sin devolución ni devolución
  pendiente, cargo pagado y, con `NO_HUBO_VENTA`, que fue su venta (`esVentaDelProfesor`). Ejecuta: el cobro pasa a
  ANULADO (anulado por P, motivo «Deshecho por solicitud #n: {motivo}») y **sale de la entrega** (`entrega_caja_id`
  NULL: la 6b-2 exige que ningún cobro ANULADO quede en una entrega ACTIVA), y en esa entrega se apunta un ERROR por
  +importe (`cobro_id`, `producto_alumno_id`, alumno y `metodo` del cobro, nota «Cobro #c deshecho por la solicitud
  #n», creado por P), que guarda el rastro; la entrega no se anula. El cargo queda pendiente por el camino
  de A4 (sin cobro activo que anular) y, con `NO_HUBO_VENTA`, cancelado (por P, motivo «no hubo venta (solicitud
  #n)»). Registro `CAJA COBRO_ENTREGADO_DESHECHO sede=#S cobro=#c cargo=#g entrega=#e movimiento=#m solicitud=#n`.
  `CajaDTOs.EntregaCaja.movimientoId` pasa a ser solo el SOBRANTE o FALTANTE (no el ERROR).
- **Tipo 3** (`CajaService.anularEntregaPorSolicitud` y `anularMovimientoPorSolicitud`): el mismo cuerpo que A7 y A9
  (se extrae a un método común), motivo «Solicitud #n: {motivo}» y `solicitud=#n` en `ENTREGA_ANULADA` y
  `MOVIMIENTO_ANULADO`.
- **Devolución** (153): «sin devolución» = ningún movimiento DEVOLUCION ACTIVO con ese `cobro_id`; «devolución
  pendiente» = un encargo con `devolucion_pendiente` sobre ese cargo. Hoy no existen (6c-5 y 6c-6): los tests los
  insertan por SQL.
- **Registro:** `SOLICITUDES SOLICITUD_ACEPTADA …` o `SOLICITUD_NO_APLICABLE …` (solo ids, en la misma transacción),
  más el de la acción ejecutada con `solicitud=#n` (`CARGO_CANCELADO` lo gana como sufijo). A1 sigue sin registro
  propio: lo cubre `SOLICITUD_ACEPTADA` (§8).
- **Dinero:** el saldo de la sede tras aceptar es el que deja la acción directa de P: tipo 1, −importe si el cobro era
  EFECTIVO fuera de pendiente de entrega (igual que A1); tipo 2, igual (anulado + ERROR); tipo 3, −contado de la entrega
  o −importe del movimiento (igual que A7 y A9). `NO_APLICABLE`, igual.

### Tarea 1: tests de aceptar (fallan)

**Ficheros:**
- Crear: `T/integracion/AceptarSolicitudesIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`,
  `PER_CLASS`, `extends MySqlIntegrationTest`; escena de `SolicitudesIntegrationTest` copiada: escuela con sedes A y B,
  propietario, gestor, responsable de A y profesor de gA; otra escuela para el aislamiento).
- Modificar: `T/integracion/SolicitudesIntegrationTest.java` (`revertirCargoSinCobro_pideAceptaYSoloDesmarca`: la parte
  de aceptar).
- Modificar: `src-api/src/test/resources/seguridad/matriz-permisos.txt` (una línea:
  `POST /api/solicitudes/{id}/aceptar`, solo PROPIETARIO `PERMITE`).

- [ ] **Paso 1: escribir los tests que fallan.** En cada uno, el `saldoCaja` de la sede antes y después:
  - `revertirCargoSinCobro_pideAceptaYSoloDesmarca`: P acepta → 200 `ACEPTADA`, `resuelto_por` = P, respuesta NULL; el
    cargo queda `pagado = 0` y `fecha_pago` NULL; ningún cobro; el saldo no cambia.
  - `aceptarRevertirCobro_anulaElCobroYDesmarca_comoA1`: G pide sobre un cobro de tesorería (EFECTIVO); P acepta → 200
    `ACEPTADA`; el cobro queda `ANULADO`, `anulado_por` = P y motivo «Revertido por solicitud #n: {motivo}»; el cargo,
    no pagado; saldo = antes − 10,50 (lo que deja A1).
  - `aceptarRevertirExamen_sincronizaConvocatoriaYDerechoAExamen`: examen pagado desde la convocatoria; tras aceptar,
    la inscripción queda `pagado = 0` y el alumno sin derecho a examen.
  - `aceptarTrasEntregarElCobro_NO_APLICABLE_sinEfectos`: G pide revertir un cobro del profesor sin entregar; el gestor
    recibe la entrega; P acepta → 200 `NO_APLICABLE`, respuesta «El cobro ya va en una entrega de caja.», `resuelto_por`
    = P; el cobro sigue ACTIVO en su entrega, el cargo pagado y el saldo igual.
  - `aceptarTrasCambiarElEstado_NO_APLICABLE_porTipo`: tipo 1 con cobro (P revierte antes por el PUT → «El cobro ya no
    está activo.»), tipo 1 sin cobro (P desmarca antes → «El cargo ya no está pagado.»; P desmarca y vuelve a cobrar →
    «El cargo tiene ahora un cobro activo…»), tipo 3 entrega (P la anula antes → «La entrega ya está anulada.») y tipo 3
    movimiento (P lo anula antes → «El movimiento ya está anulado.»). En todos, ningún efecto y el saldo igual al de
    después del cambio.
  - `aceptarConCobroAnuladoPorBorrado_NO_APLICABLE_sinEfectos`: G pide revertir el cobro de un alumno; P borra al alumno
    (`DELETE /api/alumnos/{id}`: el cobro pasa a ANULADO «cargo borrado» y el cargo desaparece); P acepta → 200
    `NO_APLICABLE` con «El cargo ya no existe.»; el cobro no cambia (sigue con el motivo «cargo borrado»).
  - `aceptarConDevolucion_NO_APLICABLE`: una DEVOLUCION ACTIVA con ese `cobro_id` (SQL) → «El cobro tiene una devolución
    registrada.»; un encargo con `devolucion_pendiente` sobre el cargo (SQL) → «El encargo tiene una devolución
    pendiente: regístrala antes.». El cobro sigue ACTIVO.
  - `aceptarDeshacerEntregado_anulaElCobroYApuntaErrorEnLaEntrega_saldoIgual` (193): el profesor cobra, el gestor recibe
    la entrega (contado = declarado), tipo 2 por SQL con `motivo_deshacer` AUN_NO_PAGADO; P acepta → `ACEPTADA`; el
    cobro, ANULADO y fuera de la entrega (ningún cobro ANULADO en una entrega ACTIVA); un ERROR ACTIVO de +10,50 en esa
    entrega con `cobro_id`,
    `producto_alumno_id` y alumno; la entrega sigue ACTIVA y su `movimientoId` en el historial es NULL (no el ERROR);
    el cargo, pendiente; saldo igual; registro `COBRO_ENTREGADO_DESHECHO … solicitud=#n`. Si P anula después la
    entrega, el ERROR se anula con ella y el saldo es el de antes − 10,50 (lo que deja anular una entrega sin el cobro).
  - `aceptarDeshacerEntregado_noHuboVenta_cancelaElCargo`: venta del profesor (apuntar y cobrar), entrega, tipo 2
    NO_HUBO_VENTA → el cargo queda cancelado y no pagado, sin cobro ACTIVO; saldo igual.
  - `aceptarDeshacerEntregado_estadoCambiado_NO_APLICABLE`: P anula la entrega antes → «El cobro ya no va en una
    entrega…»; NO_HUBO_VENTA sobre un cargo que no es su venta → «No fue una venta suya…». Ningún ERROR; cobro ACTIVO.
  - `aceptarAnularEntrega_comoA7_conLaSolicitudEnElRegistro`: entrega contada 11,00 (SOBRANTE); P acepta → la entrega
    ANULADA con motivo «Solicitud #n: {motivo}», el cobro vuelve a pendiente y el SOBRANTE se anula; saldo = antes −
    11,00; registro `ENTREGA_ANULADA … solicitud=#n`.
  - `aceptarAnularMovimiento_comoA9`: saldo = antes − 1,00; registro `MOVIMIENTO_ANULADO movimiento=#m solicitud=#n`.
  - `aceptarYRetirarALaVez_unoGana409ElOtro` (punto de revisión 3): con `enParalelo`, P acepta y G retira la misma; los
    estados son {200, 409} y el 409 trae «La solicitud ya no está pendiente»; ACEPTADA ⇒ cobro ANULADO; RETIRADA ⇒ cobro
    ACTIVO y cargo pagado. Tres vueltas.
  - `aceptar_soloPropietario_unaVez`: G y R, 403; sesión solo-profesor, 403; P, 200; otra vez, 409 `NO_PENDIENTE`;
    denegada, 409; inexistente u otra escuela, 404; un encargo, 400 y sigue PEDIDO. Los rechazos no cambian nada ni
    dejan registro.
  - `registro_aceptadaYNoAplicable_soloIds`: `SOLICITUDES SOLICITUD_ACEPTADA sede=#A solicitud=#n cobro=#c cargo=#g` y
    `SOLICITUD_NO_APLICABLE …`, con `usuario_id` = P; ni el motivo ni la respuesta.
  - `T/services/SolicitudServiceAceptarTest` (unitario, la red): `ejecucionQueLanza_seDeshaceYQuedaNoAplicableConElMensaje`
    (la ejecución lanza 409 → una transacción deshecha, otra confirmada, `NO_APLICABLE` con ese mensaje, sin efectos
    posteriores) y `yaResuelta_409SinEjecutarNiRegistrar`.
- [ ] **Paso 2: comprobar que fallan.**
  `mvn test -Dtest='AceptarSolicitudesIntegrationTest,SolicitudesIntegrationTest' -DexcludedGroups=migracion`. Se espera
  FAIL: la ruta no existe (404/405) y la matriz no tiene la línea.

### Tarea 2: implementar

**Ficheros:**
- Crear: `J/services/EjecucionSolicitud.java` (record `noAplicable` + cargo y `pagadoAntes` para los efectos de después,
  y las razones).
- Modificar: `J/services/SolicitudService.java` (`aceptar`), `J/controllers/SolicitudController.java` (la ruta).
- Modificar: `J/services/ProductoAlumnoService.java` y `impl/ProductoAlumnoServiceImpl.java`
  (`revertirPorSolicitud`, `deshacerEntregadoPorSolicitud`, `aplicarEfectosTrasSolicitud`).
- Modificar: `J/services/CobroService.java` (`bloquearEntrega`, `deshacerEntregado`, `razonDeDevolucion`; recibe los
  repositorios de entregas y movimientos), `J/services/CajaService.java` (`anularEntregaPorSolicitud`,
  `anularMovimientoPorSolicitud` y `movimientoId` solo SOBRANTE o FALTANTE), `RegistroAccionesCaja`
  (`COBRO_ENTREGADO_DESHECHO`), `RegistroAccionesCargos` (sufijo `solicitud=#n`), `MovimientoCajaRepository`
  (`existsByCobroIdAndTipoAndEstado`).
- Modificar: `T/servicios/CobroServiceTest.java` (constructor nuevo).

**Interfaces:**
- Produce: `SolicitudDTOs.Solicitud SolicitudService.aceptar(Long id)`.
- Produce: `EjecucionSolicitud ProductoAlumnoService.revertirPorSolicitud(Long cargoId, Long cobroId, long solicitudId,
  String motivo)`, `EjecucionSolicitud deshacerEntregadoPorSolicitud(Long cobroId, Long cargoId, Long profesorId,
  CajaRequests.MotivoDeshacer motivo, long solicitudId, String motivoSolicitud)` y `void
  aplicarEfectosTrasSolicitud(EjecucionSolicitud)`; las dos primeras, dentro de la transacción del llamador.
- Produce: `MovimientoCaja CobroService.deshacerEntregado(Cobro cobro, EntregaCaja entrega, Long usuarioId, String
  motivo, long solicitudId)` (cobro y entrega ya bloqueados y revalidados), `Optional<EntregaCaja>
  bloquearEntrega(Long)` y `Optional<String> razonDeDevolucion(Long cobroId, Long cargoId)` (lo usa también la 6c-6).
- Produce: `EjecucionSolicitud CajaService.anularEntregaPorSolicitud(Long entregaId, long solicitudId, String
  motivo)` y `EjecucionSolicitud anularMovimientoPorSolicitud(Long movimientoId, long solicitudId, String motivo)`
  (`hecha()` si se anuló; `noAplica(razón)` si no, sin tocar nada); dentro de la transacción del llamador.

- [ ] **Paso 1: implementar** lo de los criterios.
- [ ] **Paso 2: comprobar que pasan.** El comando de la tarea 1, más `CajaSedeIntegrationTest`, `CobrosIntegrationTest`,
  `MatrizPermisosTest` y `CobroServiceTest`. Se espera PASS.

### Tarea 3: matriz, documentación y batería

- [ ] **Paso 1:** nota en `matriz-endpoints.md` (una línea nueva; ninguna casilla existente cambia).
- [ ] **Paso 2:** `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion`: BUILD SUCCESS, con los dorados
  de Moiskimdo sin tocar.
- [ ] **Paso 3:** `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`. Commit
  `feat(solicitudes): aceptar las solicitudes al propietario con revalidación (6c-3)`.

---

## 5. Pieza 6c-7: bandeja, contador y «Pedir al propietario» (detallada)

Rama `feature/6c7-bandeja` desde `feature/multi-escuela` (tras la 6c-3). **Sin migración y sin cambios de backend**:
consume las rutas de la 6c-2 y la 6c-3 tal cual. Se despliega con el atajo `desplegar`. Moiskimdo la ve (190 y D6c-4,
aceptadas).

Criterios del plan. El diseño no los fija y se anotan en la PR:
- **Contador:** `GET /api/solicitudes/contador` al resolver los roles y en cada `NavigationEnd`, nunca con `interval`
  ni temporizador. Lo piden las sesiones con ADMIN, MANAGER o RESPONSABLE_SEDE; la familia y la sesión solo-profesor
  no. Un error deja el último valor y no abre el diálogo global (`ERROR_HANDLED_LOCALLY`). Vive en un servicio mínimo
  (`SolicitudesContadorService`, con una `signal`) para no pesar en el paquete inicial; la bandeja y los diálogos usan
  `SolicitudesService`, que solo cargan las rutas perezosas.
- **Dónde se pinta:** acceso «Solicitudes» en `ADMIN_HEADER_QUICK_LINKS` (campo nuevo `contador: 'solicitudes'`) y en
  la barra lateral `#admin-sidebar`, con un distintivo rojo con el número; con 0, sin distintivo.
- **Bandeja** (`/solicitudes`, perezosa, `roleGuard` con ADMIN y MANAGER): segmentos «Pendientes (n)», «Encargos en
  curso (n)» y «Resueltas». Al entrar se piden Pendientes y Encargos (para los dos números); Resueltas, al elegirla.
  Los encargos solo se listan (sus acciones son de la 6c-5 y la 6c-9).
- **Aceptar:** confirmación previa con el dato del recurso; `ACEPTADA` → aviso breve; `NO_APLICABLE` → diálogo de aviso
  con la `respuesta` de la API. Tras aceptar, denegar o retirar se recargan los segmentos y el contador.
- **Denegar:** diálogo con respuesta obligatoria (500 como máximo). **Retirar:** solo en las propias (`propia`) y
  pendientes.
- **Diálogo «Pedir al propietario»** (función reutilizable sobre SweetAlert2, como el resto de diálogos de la gestión):
  datos del recurso, motivo obligatorio (500), la nota «Mientras tanto … sigue …», Cancelar / Enviar solicitud. Un 409
  (ya hay una pendiente) o cualquier error muestra el `mensaje` del servidor.
  - Tesorería: G ve «Pedir al propietario» donde P ve «Revertir» (pide por `productoAlumnoId`, sirve con y sin cobro,
    191).
  - Ficha: G desmarca un cargo pagado → el checkbox vuelve a marcado y se abre el diálogo (por `productoAlumnoId`).
  - Caja de sede: G ve «Pedir anulación» donde P ve «Anular», en entregas y movimientos sueltos.
  - Los datos que no trae la pantalla (método, quién cobró) no se muestran: la tesorería y la ficha no los tienen.

### Tarea 1: servicios y contador en la cabecera

**Ficheros:**
- Crear: `F/interfaces/solicitud.ts`, `F/servicios/endpoints/solicitudes.service.ts` (`SolicitudesService`) y
  `F/servicios/endpoints/solicitudes-contador.service.ts` (`SolicitudesContadorService`).
- Modificar: `F/core/constants/navigation.constants.ts`, `F/componentes/vistas/layout/header/header.component.{ts,html,scss}`
  y su spec (proveer el contador simulado).
- Crear: `F/servicios/endpoints/solicitudes.service.spec.ts` y `F/componentes/vistas/layout/header/header.solicitudes.spec.ts`.

**Interfaces:**
- Produce: `SolicitudesContadorService.total: Signal<number>`, `refrescar(): void`, `reiniciar(): void`.
- Produce: `SolicitudesService.listar(segmento)`, `crear(peticion)`, `retirar(id)`, `aceptar(id)`, `denegar(id,
  respuesta)` (fechas normalizadas con `fechaApi`, importes a número, errores locales).

- [ ] **Paso 1: specs que fallan.**
  - `solicitudes.service.spec.ts`: «listar pide el segmento y normaliza fechas e importes»; «crear, retirar, aceptar
    y denegar llaman a sus rutas»; «el contador pide una vez por refresco y nunca solo» (`jasmine.clock().tick(600000)`
    sin peticiones nuevas); «un error del contador deja el último valor».
  - `header.solicitudes.spec.ts`: «gestión pide el contador al entrar y en cada NavigationEnd»; «familia y sesión
    solo-profesor no lo piden»; «con 3 pinta el distintivo en el acceso rápido y en la barra lateral; con 0, sin
    distintivo»; «al cerrar sesión vuelve a 0».
- [ ] **Paso 2:** `npm test -- --watch=false --browsers=ChromeHeadless`: fallan (no existen).
- [ ] **Paso 3:** implementar y registrar los specs en `tsconfig.spec.json` y `angular.json`.

### Tarea 2: bandeja

**Ficheros:** crear `F/componentes/endpoints/solicitudes-bandeja/solicitudes-bandeja.component.{ts,html,scss,spec.ts}`;
modificar `F/app.routes.ts` (ruta `solicitudes`).

- [ ] **Paso 1: specs que fallan:** «al entrar pide Pendientes y Encargos y pinta los dos números»; «Resueltas se
  pide al elegirla»; «la fila muestra tipo, quién, sede, alumno, concepto, importe y motivo»; «P ve Aceptar y
  revertir / deshacer / anular y Denegar…; G no»; «Retirar solo en las propias»; «aceptar NO_APLICABLE muestra la
  razón de la API»; «denegar exige respuesta»; «tras resolver se recarga y se refresca el contador»; «los encargos
  no tienen acciones».
- [ ] **Paso 2:** fallan. **Paso 3:** implementar. **Paso 4:** pasan.

### Tarea 3: diálogo «Pedir al propietario» y puntos de entrada

**Ficheros:** crear `F/componentes/generales/pedir-al-propietario/pedir-al-propietario.ts` y su spec; modificar
tesorería, ficha (`productos-alumno`) y caja de sede (ts y html), y `src/styles.scss` (estilos del diálogo, que
SweetAlert2 pinta fuera de los componentes). Crear `tesoreria-cobros.solicitudes.spec.ts`,
`productos-alumno.solicitudes.spec.ts` y `caja-sede.solicitudes.spec.ts`.

- [ ] **Paso 1: specs que fallan:** diálogo: «muestra los datos, la nota y exige motivo»; «envía la petición con el
  motivo»; «un 409 muestra el mensaje del servidor». Ficha: «G desmarca un cargo pagado → se abre el diálogo y el
  checkbox vuelve a marcado»; «P desmarca como hoy». Tesorería: «G ve Pedir al propietario y no Revertir».
  Caja: «G pide anular una entrega y un movimiento suelto; no el automático».
- [ ] **Paso 2:** fallan. **Paso 3:** implementar. **Paso 4:** pasan.

### Tarea 4: verificación

- [ ] `npm test -- --watch=false --browsers=ChromeHeadless` (0 fallos, el total sube) y `npm run build:production`
  sin aviso de presupuesto.
- [ ] Capturas a 1280 y 375 (cabecera con contador, cada segmento con datos de ejemplo y el diálogo) con un mock de la
  API; `impeccable detect` sobre lo cambiado.
- [ ] `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`. Commit
  `feat(solicitudes): bandeja, contador y «Pedir al propietario» en la gestión (6c-7)`.

---

## 6. Auto-revisión

- **Cobertura:**
  - §3 objetivo 5 → 6c-1.
  - Objetivos 1 y 2 → 6c-2, 6c-3, 6c-4 y 6c-7.
  - Objetivo 3 → 6c-5 y 6c-8.
  - Objetivo 4 → 6c-6 y 6c-9.
  - §8 (registro de acciones) → 6c-2, 6c-3, 6c-4 y 6c-6.
  - §9 → los dorados de la tarea 5 y la fila 2b en la tarea 3.
  - §11, postcheck y checklist → 6c-2.
- **Nombres:** `EXAMEN_PAGADO_REVIERTE_ANTES` se reutiliza en la 6c-6 con el texto completo. `tieneCobroActivo` solo
  lo usa la 6c-1. `SolicitudRepository.bloquearPorId` y `SolicitudTransiciones.exigir` son de la 6c-2 y los consumen
  la 6c-3, la 6c-4 y la 6c-5.
- **Proporción:** el mapa son 9 filas; la 6c-1 son 5 tareas con nombres de test, valores y firmas, sin cuerpos.
