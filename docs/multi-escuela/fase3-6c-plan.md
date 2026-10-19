# Entrega 6c (solicitudes y encargos): plan de implementación

> **Ejecutores:** agentes savvy-* orquestados por el router. Cada pieza 6c-n es un agente en su propio worktree, con
> revisión independiente (`savvy-reviewer`) antes de la PR. Las piezas de dinero, permisos o migración (6c-1, 6c-2,
> 6c-3, 6c-4, 6c-5, 6c-6) llevan la revisión en opus. Los pasos usan casillas (`- [ ]`). Este plan detalla solo la
> 6c-1; las demás se detallan justo antes de lanzarlas, a partir del mapa (§1).

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
| **6c-3** backend | Aceptar tipos 1 (con y sin cobro) y 3, con revalidación y `NO_APLICABLE` (§6.5) | `SolicitudService`, `ProductoAlumnoServiceImpl` (camino interno de A1), `CajaService.anularEntrega:411` y `anularMovimiento:543` | `SolicitudDTOs.Solicitud SolicitudService.aceptar(Long id)`; el estado final es ACEPTADA o NO_APLICABLE, con la `respuesta`; `ProductoAlumnoServiceImpl.revertirPorSolicitud(Long cargoId, long solicitudId, String motivo)`, con motivo «Revertido por solicitud #n: {motivo}» | 6c-2 | 6c-2 | Alto (ejecuta reversiones y anulaciones); opus | Por tipo: el estado cambia entre pedir y aceptar y la solicitud queda `NO_APLICABLE` sin efectos; saldo de la sede igual; `aceptarYRetirarALaVez_unoGana409ElOtro`; `aceptarTrasEntregarElCobro_NO_APLICABLE_sinEfectos`; registro `SOLICITUD_ACEPTADA` y `ENTREGA_ANULADA … solicitud=#n` |
| **6c-7** frontend | Contador en la cabecera, bandeja (tres segmentos), diálogo «Pedir al propietario» en tesorería, ficha y caja de sede (190, 191) | `F/core/constants/navigation.constants.ts:11-18`, `F/componentes/vistas/layout/header/header.component.html:232`, ruta nueva en `F/app.routes.ts`, servicio `F/servicios/endpoints/solicitudes.service.ts`, `tesoreria-cobros.component.ts:280`, `productos-alumno.component.html:97`, `caja-sede` | Componente de bandeja y diálogo reutilizable (D6c-8) | Rutas de 6c-2 y 6c-3 | 6c-3 | Medio (visible para Moiskimdo) | Specs registrados en las dos listas: el contador se pide al entrar y al navegar, sin `interval`; G desmarca un cargo pagado → se abre el diálogo y el checkbox vuelve a marcado; con una pendiente no deja pedir otra. Build sin aviso y capturas |
| **6c-5** backend | Encargos: productos encargables (192), pedir con cargo y sede (D6c-9), transiciones, caída del encargo, guardas en cancelar y borrar el cargo, rutas del profesor | `SolicitudService` (o `EncargoService`), `ProductoAlumnoServiceImpl` (cargo por el código de `asignarProductoAAlumno:186` sin el `@PreAuthorize` de gestión), `cancelar:852`, `borrar:816`, `J/controllers/CajaController.java` (`/api/caja/profesor/…`) | `List<CajaDTOs.ProductoVendible> productosEncargables()`; `pedirEncargo(Long alumnoId, PedirEncargo{productoId, nota, sedeId?})`; `marcarEncargado`, `marcarRecibido`, `rechazar(id, motivo)`, `retirarEncargo`, `marcarEntregado`; constante `ENCARGO_EN_CURSO` = «Tiene un encargo en curso: recházalo desde Solicitudes»; `devolucion_pendiente` a true al caer un encargo pagado | 6c-2, `@acceso.alumnoDeMisGrupos:209` | 6c-2 | Alto (crea y cancela cargos); opus | El cargo creado tiene `vendido_por_usuario_id` NULL y aparece en «Por cobrar»; si cae sin cobrar, el cargo se cancela; si cae pagado, `devolucion_pendiente` a true; un cargo en curso da 409 al cancelar y al borrar; la sede se exige solo con varias; matriz con lista blanca |
| **6c-4** backend | Tipo 2: `cobros-entregados-recientes` (30 días, máximo 50, D6c-14), `pedir-deshacer` y aceptación con la 193 | `CajaController`, `CajaService`, `CobroService` (método nuevo), `CajaDTOs.EntregaCaja.movimientoId` (`J/dtos/response/CajaDTOs.java:72-76`; devuelve el SOBRANTE o FALTANTE, no el ERROR) | `CobroService.deshacerEntregado(Long cobroId, CajaRequests.MotivoDeshacer, long solicitudId)`, que anula el cobro como A4 sin comprobar la entrega y crea un ERROR por +importe en esa entrega con `cobro_id` | 6c-2, 6c-3 (`aceptar`) | 6c-3 | Alto (caja); opus | El saldo es igual tras aceptar (anulado + ERROR); anular después la entrega anula también el ERROR (`CajaService.java:444-447`); el estado cambia y la solicitud queda `NO_APLICABLE`; registro `COBRO_ENTREGADO_DESHECHO` |
| **6c-6** backend | Devolución enlazada (153): `crearMovimiento` con `cobroId`; «Registrar devolución» por la sede y por el profesor (195, D6c-10); el saldo suma solo EFECTIVO (D6c-13); «devuelto»; invariantes «no se anula» y «devolución pendiente → 409»; tesorería `devuelto` e `importeDevuelto` (D6c-12); desvincular el examen devuelto y el aviso completo de la 186 (D6c-15) | `CajaService.crearMovimiento:470`, `registrarEntrega:310`, `CobroService.anular:198` y `alBorrarCargos:254`, consultas de saldo de `CobroRepository`, tesorería, `AlumnoServiceImpl.eliminarAlumnoDeConvocatoria` | `CobroService.exigirSinDevolucion(Long cobroId)`; `registrarDevolucionEncargo(Long solicitudId, String nota)` (sede) y `registrarDevolucionProfesor(Long solicitudId, String nota)`; DTO de tesorería `devuelto` e `importeDevuelto`; `EXAMEN_PAGADO_REVIERTE_ANTES` pasa a «El examen está pagado: haz la devolución o revierte el cobro antes» | 6c-2, 6c-5 | 6c-2, 6c-5 | Alto (caja y tesorería); opus | La devolución del profesor deja el saldo a 0 y el cobro fuera de «Llevas encima»; un cobro con devolución da 409 por cada vía (A1, A2, A4, tipo 2) y `alBorrarCargos` lo salta; los totales de tesorería de Moiskimdo son idénticos; `devolverYEntregarALaVez_saldoCuadra` |
| **6c-8** frontend | Mi caja: pestaña «Pedidos» (encargar, con sede si hace falta; retirar; marcar entregado; solicitudes de deshacer) y «Entregados recientes» en «Encima» | `F/componentes/endpoints/mi-caja/*` (patrón de pestañas y `.contador`, `mi-caja.component.html:27-41`) | — | Rutas de 6c-4 y 6c-5 | 6c-4, 6c-5 | Medio | Specs registrados, capturas en móvil, build sin aviso |
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

## 3. Auto-revisión

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
