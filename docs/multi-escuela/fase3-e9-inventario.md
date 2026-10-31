# Inventario E9 (solo lectura) · develop 944eee32 · 2026-10-31

Rutas relativas a la raíz del repo. `API/` = `src-api/src/main/java/com/taemoi/project/`; `FE/` = `src-frontend/src/app/`; `MP` = `src-api/src/test/resources/seguridad/matriz-permisos.txt`; `SC` = `API/config/SecurityConfiguration.java`. Los documentos sin ruta están en `docs/multi-escuela/`. Citas de código comprobadas contra el checkout en 944eee32, salvo lo listado en E. `roadmap.md` es el export parado del 24/10: solo vale como referencia de decisiones.

Hallazgos que condicionan todo lo demás:

1. **R solo no puede entrar y la API no basta para abrirlo.** `RolMembresia.abreSesion` lo rechaza en login y en cada petición; además `/api/auth/roles`, `/user` y `/permisos` niegan a R (MP:155, 161, 165), `/api/escuela/marca` también (MP:228) y el frontend no conoce el rol.
2. **Con varias sedes activas, gestión crea alumnos y grupos sin sede sin ningún error.** `sede_principal_id` y `grupo.sede_id` quedan NULL. El panel pide sede en el alta de alumno, pero **no en la de grupo** (`FE/panel-nuevo/grupos/crear-grupo-panel.component.ts:49`).
3. **`AlumnoRepository.asignarSedePrincipal` solo se llama desde tests.** Un alumno que nace sin sede o en la sede equivocada no se puede corregir por API ni por panel (detalle en C3).
4. **Dinero:** un cargo de un alumno sin sede nace con `sede_id` NULL, el cobro hereda ese NULL y ninguna caja de sede lo ve ni lo puede recibir (C4).
5. **Un grupo sin sede detiene V32** (`fase3-sedes-permisos.md:128-129`): el hallazgo 2 acumula bloqueos para la E10.

## A. Alcance consolidado

| # | Pieza | Qué se prometió | Cita | Nota |
|---|---|---|---|---|
| A1 | Pantalla Sedes | Nace en la E9 con el diseño de la E8b; contrato `/api/sedes/**` | `fase3-e8-miembros.md:20,109,130`; `fase3-e8-plan.md:385`; decisión 216 (`roadmap.md:358`); `fase3-sedes-permisos.md:334` | Título de la entrega |
| A2 | Pantalla Miembros | Igual; contrato `/api/miembros/**` | `fase3-e8-miembros.md:20,109,130`; `fase3-e8-plan.md:550`; decisión 216 | Título de la entrega |
| A3 | Sustituir «Gestión de Roles» | La E9 la sustituye; Moiskimdo la usa hoy | D8-17 (`fase3-e8-miembros.md:186`) | Hoy vive en Configuración, ya en el panel nuevo |
| A4 | Selector de escuela | Frontend de selector; quien añada `POST /api/auth/organizacion` llama a `reiniciar()` junto a los roles | `fase3-sedes-permisos.md:25,334`; `fase3-e8-plan.md:787-789` («E9/E10»); `roadmap.md:85` | La E8 lo deja en «E9/E10»; el resto dice E9. Tiene interacción con la decisión 61 (`roadmap.md:422`), ya matizada por 180 y 184 |
| A5 | Selector de sede en altas de alumno y grupo | E9; con varias sedes, gestión opcional hasta la E9 | `fase3-e8-miembros.md:110`; D8-3 (`:172`); D6-2 (`entrega6-plan.md:520`) | **Alumno ya hecho** en la 8b (decisión 285; `fase3-e8b-ronda4.md:311,327`). Grupo pendiente |
| A6 | Filtros por sede | Filtro en alumnos, grupos, preinscripciones, convocatorias y tesorería | `fase3-sedes-permisos.md:334`; título `roadmap.md:85` | |
| A7 | Desglose de totales por sede | Con una sede la interfaz no muestra desglose; API lista | D6-12 (`entrega6-plan.md:530`) | Inicio ya pinta «Por sede» del vencido (solo gestión) |
| A8 | Entrada de R al panel | Puerta cerrada hasta la E9 | D6-15 (`entrega6-plan.md:533`); `entrega5-plan.md:110`; `fase3-sedes-permisos.md:310-316`; `PRODUCT.md:27`; `fase3-e8b-diseno.md:142` | |
| A9 | Menú y pantallas de R | «Gestión acotada a sus sedes»; rutas denegadas a abrir | Supuesto `fase3-e8b-diseno.md:142`; `fase3-e7-profesor.md:103`; `entrega6-plan.md:144` | No hay decisión sobre qué pantallas ve R |
| A10 | Cuenta R + Pr | Entra como profesor; su parte de responsable, hasta la E9 | Decisión 184 (`fase3-sedes-permisos.md:313-316`) | Decidir cómo se combina (D) |
| A11 | Marca para R | `GET /api/escuela/marca` DENIEGA hasta la E9 | `fase3-e8b-plan.md:295` | Requisito de A8: la cabecera la pide |
| A12 | Inicio y vencido para R | `pendiente-vencido` DENIEGA hasta la E9 | Decisión 267; `fase3-e8b-plan.md:528`; `matriz-endpoints.md:389,397` | |
| A13 | Bandeja de R en solicitudes | Pantallas en la E9; API desde la 6c | `fase3-6c-solicitudes.md:57,76,93,179,397,496`; `fase3-6c-plan.md:1239,1419` | |
| A14 | Asignación de profesores por R | DENIEGA hasta la E9 | D7-19 (`fase3-e7-profesor.md:175,318`), `:103` | |
| A15 | API de sedes y miembros a R | «Abrir la API de miembros o de sedes a R, Pr o F (E9)» | `fase3-e8-miembros.md:114`, D8-1 (`:170`), `:296` | Prometido solo como revisión de permisos; no hay decisión de qué abrir |
| A16 | Tarifas de sede | Pantalla en la E9 | D6-30 (`entrega6-plan.md:537`), `roadmap.md:469` | Hoy Sierra Sur tiene tarifas comunes |
| A17 | Cuantía en la ficha | La ficha no cambia hasta la E9; opción `cuantiaEfectiva` | D6-35 (`entrega6-plan.md:542`), `roadmap.md:474` | Solo importa si hay filas de A16 |
| A18 | Aforo por sede | Turno con aforo de su sede o límite común; sin migración | Decisión 245 (`roadmap.md:375`); `entrega6-plan.md:551`; `fase3-e8b-plan.md:558`; `DESIGN.md:352`; `FE/panel-nuevo/inicio/inicio.component.html:196` | Texto en pantalla y DESIGN.md prometen retirarlo |
| A19 | Informes por sede | PDF de asistencia: filtro de alumnos (142); pasar nombres a ids | Decisión 142 (`roadmap.md:338`); `entrega6-plan.md:137,553-554` | Ver desmentido en B-A19 |
| A20 | Ajuste por escuela del día de vencimiento | Día 5 como constante; ajuste por escuela en la E9 | Decisión 267 (`roadmap.md:390`); `fase3-e8b-plan.md:527` | |
| A21 | Pasar lista y Grupos para R | R con API pero sin ruta | Decisión 345; `continuacion-2026-10-30.md` (pendientes de pasar lista) | |
| A22 | Eventos para R | R DENEGADO hasta la E9 | `fase3-e8c-inventario.md:99` | Sin decisión de abrirlo |
| A23 | Email de alumno sin cuenta por R | Decidir antes de dar responsables a escuelas con acceso por email | Decisión 96 (`roadmap.md:329`) | Pendiente |
| A24 | Grupos de otra sede en la ficha | R los ve en lectura y solo cambia los de sus sedes | D6-16 (`entrega6-plan.md:473-474`) | Ya decidido |
| A25 | Profesor que se va con encargos RECIBIDOS | «Si pasa, decidir en la E9» | `fase3-6c-solicitudes.md:497` | Pendiente |
| A26 | Vistas de profesor | En el título | `roadmap.md:85`; `fase3-sedes-permisos.md:334` | **Ya hecho** (ver reasignaciones) |
| A27 | Despliegue sin interruptor | Cada pieza se despliega apagada | Decisiones 255 y 341 (pendiente) | Condición para desplegar la E9 |

### Reasignaciones y piezas mal atribuidas

- **Ya hecho, sobra del título:** «vistas de profesor». Rutas `/mi-caja` y `/mis-grupos` (`FE/app.routes.ts:485-497`), `FE/panel-nuevo/mi-caja`, `FE/panel-nuevo/mis-grupos`, navegación directa para sesión solo-profesor (`FE/panel-nuevo/navegacion/menu-panel.ts:172-175`, decisión 326). Queda un pendiente menor («Vista previa del profesor sin revisar a ojo», `continuacion-2026-10-30.md`).
- **Ya hecho:** selector de sede en el alta de alumno (`FE/panel-nuevo/alta-alumno/alta-alumno.component.html:70-82`).
- **Es de la fase 4 (web pública), no de la E9:** la web propia de cada escuela, aunque la decisión 266 (`roadmap.md:389`) diga «sigue en la E9»; `roadmap.md:45` la sitúa en la fase 4. Lo mismo el formulario público multisede (`entrega6-plan.md:549-550`, «fase 4 / E9»), que además hace falta antes de abrir la web de Sierra Sur. También la escuela de las peticiones anónimas (decisión 10, `roadmap.md:324`). Propuesta de clasificación, a confirmar por el orquestador.
- **Es de la E10 (V32), no de la E9:** `grupo.sede_id` NOT NULL, UNIQUE (`sede_id`, `nombre`), borrar `usuario_rol` y `usuario.alumno_id` (`fase3-sedes-permisos.md:124-134`). La E9 sí debe impedir que nazcan grupos sin sede.
- **«Alta del primer cliente»** es una fase propia (`roadmap.md:47`), no la E9; no encontré API para crear una organización (C8).
- **Sin dueño:** lectura de soporte del superadmin sobre miembros y sedes (D8-18, `fase3-e8-miembros.md:112,187`): fuera de la E8 sin asignar a la E9.
- **Ya cumplida:** «fase 2 de rendimiento antes de la E9» (`entrega6-plan.md:336`): `Alumno.grupos` y `Grupo.alumnos` ya son LAZY (`API/entities/Alumno.java:142`, `API/entities/Grupo.java:68`).

## B. Estado real en el código

### B0. Qué le pasa hoy a R

**R solo (sin más roles).**

- Login con contraseña: 403 con `ROL_SIN_ACCESO`, «Tu rol en esta escuela todavía no tiene acceso a la aplicación.» (`API/services/impl/AuthenticationServiceImpl.java:47,157-162`; `API/controllers/AuthenticationController.java:476-483`).
- Google: mismo motivo (`API/config/OAuth2AuthenticationSuccessHandler.java:144-145`; `API/services/impl/OAuth2UserServiceImpl.java:88-89`).
- Reglas: `RolMembresia.abreSesion` no incluye `ROLE_RESPONSABLE_SEDE` (`API/entities/RolMembresia.java:83-88`); `puedeEntrar` es la regla única de login, cambio de escuela y cada petición (`:108-113`); `IdentificadorSesionJwt` la aplica en cada petición (`API/organizacion/IdentificadorSesionJwt.java:34-37`). Un token de R solo queda anónimo.
- Selector: su escuela no sale en `organizacionesDe` (`API/services/impl/MembresiaServiceImpl.java:116-118`).
- Invitación: la contraseña se crea y responde 403 con el motivo de signin (`fase3-e8-plan.md:649`). Documentado, no ejecutado.

**R + Pr (sin F, P ni G).** Entra como solo-profesor: `sesionSoloProfesor` (`RolMembresia.java:96-101`); la capa de URL le deniega todo fuera de la lista blanca (`API/config/SesionSoloProfesorAuthorizationManager.java:26-40`); `GET /api/auth/roles` devuelve solo `ROLE_PROFESOR` (`AuthenticationController.java:299-302`); `permisos` devuelve solo `PROFESOR` y sedes vacías (`API/services/PermisosSesionService.java:88,98-100,237-240`).

**R + F.** Entra como familia y la autoridad R **sí** queda activa: fuera de `sesionSoloProfesor`, abre las reglas de R en la API sin ninguna pantalla (D6-15 lo prevé). Frontend: `tieneAccesoUser` lo lleva a `/userpage` (`FE/servicios/authentication/authentication.service.ts:404-406,443-460`). Sin decisión sobre cómo elige panel.

**R + P o R + G.** Alcance total: `GestionPorSede.restriccion()` vacía (`API/acceso/GestionPorSede.java:77-78`), actúa como gestión. No verificado con cuenta real.

**Filtro de servicio de R.** Vive en `GestionPorSede` + `PredicadosAlcance`; alcance D9 = sede principal en sus sedes **o** algún grupo en sus sedes (`API/acceso/PredicadosAlcance.java:67-71`). La nota de cabecera avisa de que no es un control por sí solo (`GestionPorSede.java:30-34`).

**Columna R de `MP`** (337 líneas sin HEAD; **109 ALCANCE, 77 PERMITE, 151 DENIEGA**). Modela la autoridad, no la puerta: R solo no llega nunca a estas reglas.

| Prefijo | ALCANCE | PERMITE | DENIEGA |
|---|---|---|---|
| `/api/alumnos` | 58 | 8 | 12 |
| `/api/grupos` | 15 | 2 | 5 |
| `/api/turnos` | 3 | 4 | 1 |
| `/api/preinscripciones` | 9 | 7 | 5 |
| `/api/convocatorias` | 2 | 5 | 3 |
| `/api/productos-alumno` | 12 | 0 | 6 |
| `/api/productos` | 0 | 3 | 5 |
| `/api/tesoreria` | 0 | 6 | 2 |
| `/api/caja` | 6 | 0 | 17 |
| `/api/solicitudes` | 4 | 4 | 2 |
| `/api/informes` | 0 | 4 | 18 |
| `/api/tarifas-sede` | 0 | 1 | 2 |
| `/api/catalogo` | 0 | 2 | 0 |
| `/api/auth` | 0 | 14 | 6 |
| `/api/sedes`, `/api/miembros`, `/api/panel`, `/api/escuela`, `/api/admin`, `/api/profesor` | 0 | 0 | 5, 8, 2, 2, 12, 14 |
| `/api/eventos` | 0 | 7 (públicas) | 13 |

**DENIEGA que el panel usa hoy y que romperían a R si entra sin más:**

| Ruta | MP | Regla | Quién la usa en el panel |
|---|---|---|---|
| `GET /api/auth/permisos` | 155 | `API/controllers/PermisosSesionController.java:29-30` (sin R) | Casi todo: alta de alumno, caja de sede, Inicio, `gestionPanelGuard` |
| `GET /api/auth/roles`, `/user` | 161, 165 | `AuthenticationController.java:286,308` (sin R) | Roles de toda la app |
| `GET /api/escuela/marca`, `/logo` | 228, 226 | SC:239-240 | Cabecera (`FE/panel-nuevo/marca-escuela-panel.service.ts:40`) |
| `GET/PUT /api/panel/mi-perfil` | 349, 351 | SC:233-234 | Mi perfil |
| `GET /api/tesoreria/pendiente-vencido` | 478 | SC:457 | Inicio |
| `GET /api/tesoreria/pendientes-por-alumno` | 480 | SC:457 | Alumnos («Cobrar») |
| `GET /api/grupos/conteo-alumnos`, `/tipo/{tipo}/alumnos` | 262, 266 | SC:363, 378 | `FE/panel-nuevo/carga/lecturas-panel.service.ts:96,104` |
| `/api/grupos/{grupoId}/profesores` | 282-285 | SC:355 | Editar grupo |
| `GET /api/productos-alumno/{id}/recibo` | 413 | SC:476 | Recibo |
| `GET /api/alumnos/cargos-resumen` | 49 | SC:341 | Aviso al eliminar (el borrado ya es solo de gestión) |
| Generaciones masivas, `POST /api/productos*`, `DELETE /api/productos-alumno/{id}`, `POST /api/alumnos/{id}/anonimizar`, `DELETE /api/alumnos/{id}` | 399-410, 394, 107, 105 | Reservadas a gestión (D6-4, D6-10) | Intencionado |
| 18 informes (`adultosAPromocionar`, `competidores`, `licencias`, `mensualidades`…) | 294-336 | SC:510 | Herramientas de informes |
| `/imagenes/alumnos/**`, `/documentos/**` | — | SC:209-210, 217-218 | Estáticos; las fotos del panel usan `/alumnos/{id}/imagen` (R la tiene, SC:311-313) |

**Frontend: nada contempla a R.** `RouteRole` no lo incluye (`FE/app.routes.ts:20`) y `ROUTE_ROLE_SETS` solo tiene ADMIN, MANAGER y USER (`:27-31`); todas las rutas de gestión usan `management`; `roleGuard` lo envía a `/` (`FE/guards/role.guard.ts`); `resolverRutaInicioPorRoles` devuelve `/` (`authentication.service.ts:443-460`); `tieneAccesoAdmin` es ADMIN o MANAGER (`:400-402`); `pidePermisos` lo excluye (`FE/servicios/authentication/permisos-sesion.service.ts:37-39`); `gestionPanelGuard` exige PROPIETARIO o ADMINISTRADOR (`FE/panel-nuevo/no-encontrada/gestion-panel.guard.ts:18`); `Condicion` del menú no tiene R (`FE/panel-nuevo/navegacion/menu-panel.ts:18-21`); la cabecera etiquetaría a R como gestor, porque cae en `ROLE_MANAGER` (`FE/panel-nuevo/navegacion/cabecera-panel.component.ts:193-194`); el contador de solicitudes solo se pide con gestión (`FE/componentes/vistas/layout/header/header.component.ts:304-306`, cabecera antigua). `capacidades` no da nada a R (`API/services/PermisosSesionService.java:160-179`).

### B1. Por pieza

| # | API | Frontend | R hoy |
|---|---|---|---|
| A1 Sedes | **Sí**: `API/controllers/SedeController.java:41-69`, `API/services/GestionSedesService.java:67-130` (lista, ver, crear, editar, activar; 409 si es la última activa, `:139-143`). Capacidad `SEDES` | **Nada**. Ruta `sedes`: no existe. `sedes` solo llega por `/auth/permisos` | DENIEGA (MP:446-452; SC:224) |
| A2 Miembros | **Sí**: `API/controllers/MiembroController.java:45-102`, `API/services/GestionMiembrosService.java`. Lista, ficha, alta, invitación, roles, sedes, grupos. Capacidades `MIEMBROS`, `DAR_GESTION` | **Parcial**: `FE/servicios/endpoints/miembros.service.ts` solo sirve a «Profesores» en editar grupo (`FE/panel-nuevo/grupos/editar-grupo.component.ts:161,211`). Sin ruta ni pantalla | DENIEGA (MP:339-348; SC:228) |
| A3 Gestión de Roles | `GET/PUT /api/admin/users` (`API/controllers/AdminController.java:132,151`) solo P | Vive en `FE/panel-nuevo/escuela/configuracion/` (ruta `configuracion-sistema`, `FE/app.routes.ts:459`). R y Pr se ven pero no se ofrecen (`FE/panel-nuevo/escuela/configuracion/configuracion-vista.ts:17`). `fase3-e8-miembros.md:65` cita una ruta de componente anterior | DENIEGA (SC:547). `PUT .../password` también vive en esa API (`AdminController.java:179`): revisar antes de retirar |
| A4 Selector de escuela | **Sí**: `GET /api/auth/organizaciones` y `POST /api/auth/organizacion` (`AuthenticationController.java:426,438`), `MembresiaServiceImpl.java:113-118`. Reglas: `elegirOrganizacionParaLogin` (`:79-99`) | **Nada**: ninguna llamada a `/auth/organizacion` en `FE/` | MP:150-151 PERMITE, pero R solo no llega. Moiskimdo y Sierra Sur solo lo necesitan si una cuenta pertenece a dos escuelas (E) |
| A5 Selector de sede | Alumno: `AlumnoDTO.sedePrincipalId` (`API/dtos/AlumnoDTO.java:66`), `GestionPorSede.sedeDeAlta`. Grupo: `GrupoConAlumnosDTO.sedeId`, alta y edición (`API/services/impl/GrupoServiceImpl.java:156,181-182`). **Sin obligatoriedad para gestión** | Alumno: **hecho** (`alta-alumno.component.ts:162-163`, `alta-alumno-vista.ts:300`), validado **solo en cliente**. Grupo: **nada**, envía `{nombre, deporte}` (`crear-grupo-panel.component.ts:49`) | R con una sede: automática; con varias sin sede: 400 «Indica la sede.» (`GestionPorSede.java:98-101`); de otra sede: 403 |
| A6 Filtros por sede | **Solo tesorería** (`sedeId`, `API/controllers/TesoreriaController.java:54,95,128,152`; `GestionPorSede.filtroTesoreria`) y caja por ruta. Alumnos, grupos, turnos, preinscripciones y convocatorias: **sin parámetro**. Tampoco devuelven sede `GrupoResumenDTO`, `TurnoResumenDTO` ni los alumnos (`sedePrincipalId` es `@JsonIgnore`, `API/entities/Alumno.java:188`). `AlumnoConGruposDTO` sí lleva la sede de sus grupos (`:130`) | **Nada**: sin menciones de sede en `FE/panel-nuevo/{alumnos,tesoreria,preinscripciones,convocatorias}` ni en `grupos`. Solo `caja-sede` tiene selector real (`caja-sede.component.html:30-34`) | Filtrado implícito por alcance |
| A7 Desglose | **Sí**: `TesoreriaResumenSedeDTO`, `PendienteVencidoDTO.porSede` | Inicio pinta «Por sede» (`inicio.component.html:143-150`). El de tesorería no se pinta (ninguna mención en `FE/panel-nuevo/tesoreria`) | Resumen PERMITE filtrado; vencido DENIEGA |
| A8 Entrada de R | **No**: ver B0 | **No**: ver B0 | Cerrado |
| A9 Menú de R | N/A | **Nada** | — |
| A10 R + Pr | `sesionSoloProfesor` y lista blanca. `ProfesorService` documenta que las sedes no abren nada ahí (`API/services/ProfesorService.java:49`) | `esSoloProfesor` (`authentication.service.ts:23`) | Solo Pr |
| A11 Marca | `GET /api/escuela/marca` con `gestionOSesionSoloProfesor` | Cabecera | DENIEGA (MP:226-228) |
| A12 Vencido | `API/services/PendienteVencidoService.java:91,106` ya usa `filtroTesoreria`: un responsable quedaría filtrado por sus sedes si se abre la regla (no ejercido) | Inicio | DENIEGA (MP:478) |
| A13 Bandeja R | **Sí**: `API/controllers/SolicitudController.java:39,50-121`, MP:453-464. Aceptar y denegar, solo P (MP:458-459). Limitación 6c-6: la bandeja va por sede de la solicitud y la devolución por la del cobro (`fase3-6c-plan.md:1416-1419`) | Pantalla existe (`/solicitudes`, ruta `management`), sin caso R; `DEVOLUCION_OTRA_SEDE` ya explica el 403 | API abierta, sin entrada |
| A14 Profesores por R | `API/controllers/GrupoProfesoresController.java:39-60`, solo P y G | Sección en editar grupo | DENIEGA (MP:282-285; SC:355). Sin `ALCANCE` para abrirla: hay que tocar `SC`, `@PreAuthorize` y matriz |
| A15 Sedes/miembros a R | Cerradas | — | `permisos` ya da a R sus sedes (`PermisosSesionService.java:98-100`), pero la ruta niega |
| A16 Tarifas de sede | **Sí**: `API/controllers/TarifaSedeController.java:37-50`, `API/services/impl/TarifaSedeServiceImpl.java`; ya aplica en cobros (`API/services/ResolutorCuantiaMensualidad.java`, usos en `ProductoAlumnoServiceImpl.java:1348,1426,1536,2100,2213`, `AlumnoServiceImpl.java:2665`, `PDFServiceImpl.java:2323`) | **Nada** (ninguna llamada a `/tarifas-sede`) | GET PERMITE filtrado (`TarifaSedeServiceImpl.java:79`); POST y DELETE DENIEGA |
| A17 Cuantía en ficha | **No existe** `cuantiaEfectiva` en `API/` | Ficha sin sede | — |
| A18 Aforo | **No**: `Sede.aforoTurno` se guarda (`API/services/GestionSedesService.java:223,238`), pero `AforoPreinscripcionService.ocupacion` usa un único `obtenerLimiteTurno()` (`API/services/AforoPreinscripcionService.java:73`) y `Ocupacion` tiene un solo `limite`. `GET /api/turnos/limite` (público) devuelve un entero (`API/controllers/TurnoController.java:169`) | `FE/panel-nuevo/inicio/ocupacion.ts:8` usa el límite común y `/turnos/resumen` no trae sede | Pública |
| A19 Informes por sede | Asistencia: `API/controllers/PDFController.java:268-273`; R filtra **turnos** por sede (`API/services/impl/PDFServiceImpl.java:1092,1149-1150`), no alumnos (142 sigue pendiente). Gestión mezcla todas las sedes: sin parámetro. Deudas y listado mensual filtrados para R (`PDFServiceImpl.java:1493,2309-2314`). **Desmentido:** `grupo` en ese PDF es un **día** (`DiaSemanaUtils.mapGrupoToDayOfWeek`, `:1090`; compara con `turno.getDiaSemana()`, `:1128,1143`), no un nombre de grupo: la nota de nombres repetidos de `entrega6-plan.md:137,553` no aplica a este PDF. `contarAlumnosPorGrupo` del servicio tampoco va por nombre de grupo, sino por tipos fijos de Moiskimdo (`API/services/impl/GrupoServiceImpl.java:310-352`). `AlumnoRepository.contarAlumnosPorGrupo(nombre)` (`:310-311`) sí, pero no tiene llamadas en `API/` | Sin filtro de sede | 4 PERMITE; 18 DENIEGA |
| A20 Día de vencimiento | Constante `DIA_LIMITE_MENSUALIDAD = 5` (`API/services/PendienteVencidoService.java:60`) | Sin ajuste | — |
| A21 Pasar lista y Grupos | API para R (SC:53-55, 112-113) | Rutas `gruposListar`, `gruposListar/:grupoId/lista` en `management` (`FE/app.routes.ts:293-306`) | API sí, sin ruta |
| A22 Eventos | Solo P y G (SC:419-426) | `management` | DENIEGA 13 |
| A23 Email (96) | R **puede** cambiar el email de un alumno sin cuenta: la guarda solo aplica con `usuario != null` (`API/services/impl/AlumnoServiceImpl.java:759-769,786`) | Ficha | Abierto en API |
| A24 Grupos en ficha | `AlumnoConGruposDTO` sigue serializando todos los grupos (`entrega6-plan.md:338`; no filtrado en el código leído), como prevé D6-16 | — | Ya decidido |
| A25 Encargos | Sin código específico | — | Pregunta abierta |
| A26 Profesor | Hecho | Hecho | — |
| A27 Interruptor | Retirado (0a10bf31, V31_13) | Retirado | Decisión 341 abierta |

## C. Multi-sede

Modelo: `turno`, `preinscripcion`, `convocatoria` y tesorería no llevan `sede_id`; la sede se deriva del grupo o del alumno (`fase3-sedes-permisos.md:107`). Únicas columnas con sede: `grupo.sede_id`, `alumno.sede_principal_id`, `producto_alumno.sede_id` y derivadas de caja. Todas admiten NULL.

**C1. Usos de `sedePorDefecto`** (`API/services/SedeService.java:26-29`: la única activa, o vacío con 0 o con 2 o más).

| Punto | Con una sede | Con 2 o más activas |
|---|---|---|
| `GestionPorSede.sedeDeAlta` (`:96`), gestión sin sede | La única | **NULL, sin error** |
| Alta alumno (`AlumnoServiceImpl.java:462`) | Vía `sedeDeAlta` | Alumno 201 con NULL. Test `dosSedesActivas_altaDeAlumno_comportamientoActual` (`src-api/src/test/java/com/taemoi/project/integracion/SedesApiIntegrationTest.java:593`) |
| Alta grupo (`GrupoServiceImpl.java:156`) | Vía `sedeDeAlta` | Grupo 201 con NULL. Test `dosSedesActivas_altaDeGrupo_comportamientoActual` (`:570`) |
| Editar grupo (`GrupoServiceImpl.java:184,198-201`) | Rellena si falta | Sigue NULL |
| Finalizar preinscripción (`API/services/PreinscripcionService.java:167,314`) | La del grupo | Sede del grupo; si es NULL, alumno NULL para gestión; 400 para R con varias |
| `InicializadorDatos.java:200` | Solo con base sin grupos (`:136`) | Sin efecto en producción |

Consecuencia de `sedeDeAlta` para R: nunca NULL. Para P y G: NULL. **`fase3-e8b-ronda4.md:195,327,416` afirma que «con varias sedes el servidor responde "Indica la sede."»: solo es cierto para R** (la línea 416 lo distingue). El servidor no exige sede a gestión; solo el panel, en cliente.

**C2. Alumno y grupo sin sede.** Quedan fuera de R: `PredicadosAlcance` compara con `IN` (`:67-71`), `exigirGrupoPropio` niega. Solo gestión los ve; tesorería los agrupa en «Sin sede».

**C3. Corregir un alumno.** `sede_principal_id` es `updatable = false` (`API/entities/Alumno.java:189`). El cambio previsto es `AlumnoRepository.asignarSedePrincipal` (`:290`), que **solo se llama desde tests** (`AccesoRecursosAlcanceIntegrationTest`, `ResponsableSedeTesoreriaIntegrationTest`), nunca desde `API/`. No hay ruta ni pantalla. Un alumno que cambia de pueblo no cambia de sede. La sede del grupo sí se puede editar por `PUT /api/grupos/{id}`, solo por API.

**C4. Dinero con sede NULL.**

- Cargo: `ProductoAlumno.fijarSedeAlCrear` copia la sede principal del alumno y **no cambia nunca** (`API/entities/ProductoAlumno.java:108-116`, `V31_3__sede_de_los_cargos.sql:8`). Un alumno que se corrija después deja los cargos anteriores en NULL.
- Cobro: `cobro.setSedeId(cargo.getSedeId())` (`API/services/CobroService.java:215`).
- Caja: `pendientesDeEntregaEnSede`, `sumarPendienteDeEntregaEnSede` y `sumarEfectivoEnCajaDeSede` usan `c.sedeId = :sedeId` (`API/repositories/CobroRepository.java:126-146`): el NULL no entra en ninguna. `registrarEntrega` rechaza un cobro de otra sede (`API/services/CajaService.java:519`). **Un cobro en efectivo de un profesor sobre un alumno sin sede no se puede entregar en ninguna caja.**
- Devoluciones: `devolverDelProfesor` y su par fallan con cobro sin sede (`CajaService.java:859,896`).
- Tarifa: sin sede principal se cobra la cuantía de la inscripción, sin aviso (`API/services/ResolutorCuantiaMensualidad.java:80` y su Javadoc). Inocuo con tarifas comunes; no lo será con Herrera en 2027.
- Encargos: sin sedes de grupo, solicitud sin sede (`API/services/EncargoService.java:192`).

**C5. Turnos.** Sin sede propia. `POST /api/turnos/crear` sin grupo: no abierto a R (MP:484). **Cambiar el día de un turno aplica la lógica heredada de Moiskimdo** (`API/services/impl/TurnoServiceImpl.java:221-248`): lunes y miércoles lo reasignan al primer grupo de la sede por índice, martes y jueves al segundo, y cualquier otro día da 400 «Día de la semana no válido»; con un solo grupo en la sede y martes o jueves, 400 «No hay suficientes grupos…» (`:297-302`). Con seis sedes y horarios reales, el turno puede saltar de grupo sin que nadie lo pida. La comprobación de varias sedes cuenta también inactivas (`:225`).

**C6. Código con Moiskimdo dentro** (no rompe, pero engaña en Sierra Sur).

- `contarAlumnosPorGrupo` usa cinco tipos fijos (`GrupoServiceImpl.java:313-314`); `/conteo-alumnos` es global y no filtra sede.
- `obtenerAlumnosPorTipo` (`:355`), igual.
- Asistencia: lista de deportes fija (`PDFServiceImpl.java:1095-1100`).
- Certificado: «Umbrete» solo en la rama `marca.legado()` (`API/services/impl/TesoreriaServiceImpl.java:770-773`); la otra usa datos legales: correcto.
- `/api/turnos/dto` y `/api/preinscripciones/public/*` listan todas las sedes sin nombre de sede (`entrega6-plan.md:549`; `API/controllers/PreinscripcionController.java:20-21`): fase 4.

**C7. Lo que rompería Sierra Sur con seis sedes** (ordenado por gravedad).

1. Grupos creados desde el panel: todos sin sede. Invisibles para R, sin caja, y bloquean V32.
2. Alumno con sede equivocada: sin vía de corrección.
3. Alumno sin sede por llamada directa o preinscripción sobre grupo NULL: efectivo no entregable.
4. Cambiar el día de un turno: posible salto de grupo o 400.
5. Sin pantalla de Sedes: las seis solo existen con llamadas a la API.
6. Sin pantalla de Miembros: R y profesores sin alta completa, y R no entra.
7. Sin filtros: listados de las seis sedes juntas para P y G; sin sede visible en alumnos ni en grupos.
8. Aforo: 36 común aunque una sede lo tenga distinto.
9. Asistencia en PDF: gestión mezcla sedes en la misma tirada.

Sí está preparado: caja por sede y su selector, `variasSedes`, sede en el alta de alumno, tesorería por sede en API, tarifas por sede en cobros, sede del grupo en Mis grupos, sedes inactivas saltadas en generaciones (`GestionPorSede.java:136-139`), última sede activa protegida (`GestionSedesService.java:139-143`).

**C8. Primera sede.** V31 crea sede solo para organizaciones existentes (`V31__sedes_y_vinculos.sql:81-85`: «Umbrete» para la 1, «Sede principal» para las demás). `API/plataforma/PlataformaController.java` no tiene alta de escuela; la única inserción de organización en migraciones es `V18__organizacion.sql:34`. Por tanto, una escuela nueva nace sin sede salvo lo que haga quien la cree (E).

**C9. Moiskimdo.** Desde el repo: V31 le crea una sede, «Umbrete» (`V31__sedes_y_vinculos.sql:81-85`, `aforo_turno` NULL salvo cambio posterior, `:71`); comentarios `API/entities/Sede.java:11` y `GestionPorSede.java:117`. Protección: no hay guarda que impida una segunda; se protege porque `sedePorDefecto` devuelve la única, `pedirSede` exige `variasSedes` (`alta-alumno.component.ts:163`), `caja-sede` no enseña selector con una (`caja-sede.component.html:30,43`) y el resolutor es identidad sin filas (D6-29). **Tras la E9, la pantalla Sedes permite a P o G crear la segunda con un clic y activar todo lo anterior en Moiskimdo.** Si la tiene hoy en producción: E.

## D. Dependencias y orden

**Flyway.** Última: `V31_16__fotos_evento.sql` (47 ficheros). **Siguiente libre: `V31_17`.** V32 reservada (`fase3-e8b-plan.md:166-167`: tampoco V32.x).

| Pieza | Migración |
|---|---|
| Abrir R (reglas, puerta, front) | No |
| Sedes, Miembros, selector de escuela, filtros | No, salvo índices si el rendimiento lo pide (sin medir) |
| Aforo por sede | No (decisión 245), con comprobación previa en producción |
| Día de vencimiento por escuela | No: `ConfiguracionOrganizacion` es clave y valor (`API/entities/ConfiguracionOrganizacion.java`) |
| Tarifas de sede, cuantía | No |
| Sede obligatoria en alta para gestión | No: validación en servicio |
| Corregir sede de alumno | No, la columna existe; sí habría que decidir qué pasa con cargos previos |
| Relleno de datos sin sede | Solo si la base real los tiene (E); sería V31_17 o script aparte |

**Dependencias técnicas.**

1. Antes de cualquier pantalla de R: `abreSesion`, `permisos`, `roles`, `user`, marca, `RouteRole`, guards, `MatrizPermisosTest` (`src-api/src/test/java/com/taemoi/project/seguridad/MatrizPermisosTest.java`) y dorados (`maven-completo-antes-de-pr`).
2. La regla de sesión es **única** por diseño: cambiar `abreSesion` y `sesionSoloProfesor` altera login, cambio de escuela y cada petición a la vez. Afecta `SesionSoloProfesorAuthorizationManagerTest` y a `ProfesorService`.
3. A5 (sede obligatoria) y C3 (corregir sede) antes de dar de alta datos reales de Sierra Sur; A1 antes de A5 porque sin sedes no hay selector útil.
4. Filtros (A6): piden cambiar `GrupoResumenDTO`, `TurnoResumenDTO` y DTO de alumnos: JSON aditivo, cuidado con dorados y con D6-35.
5. A18: `Ocupacion` necesita sede por turno; afecta a `/api/turnos/limite`, que hoy es público y un solo número.
6. Desplegar por piezas exige resolver antes la decisión 341.

**Decisiones del propietario antes de construir** (consultar una a una; las pendientes ya constan en el roadmap).

1. **341**: cómo desplegar sin que se vea antes de tiempo. Afecta a todo lo demás.
2. **Sede obligatoria para P y G con varias sedes**: si el servidor responde 400 (D6-2 B) o queda en cliente; incluye grupos.
3. **Corrección de sede de alumno**: quién puede y qué pasa con cargos y cobros previos.
4. **Qué pantallas ve R**: alcance real. D9, D6-x y `fase3-e8b-diseno.md:142` solo lo suponen. Incluye eventos, productos, informes, 345.
5. **Cuenta R + Pr**: seguir como profesor, gestión, o ambas; y R + F.
6. **96**: email de alumno sin cuenta por R.
7. **142**: filtro de alumnos del PDF. Y si gestión filtra por sede.
8. **Abrir a R** `/api/sedes` (lectura), `/api/miembros`, profesores de grupo, marca, vencido, pendientes por alumno, recibo.
9. Grupos de otra sede en la ficha: resuelto por D6-16 (`entrega6-plan.md:473-474`), sin decisión pendiente.
10. **Sustitución de Gestión de Roles**: si desaparece, dónde queda el cambio de contraseña de `/api/admin/users/{id}/password`; cómo se migra Moiskimdo.
11. **Selector de escuela**: si entra ya o espera (Sierra Sur no lo necesita si nadie está en dos escuelas).
12. **Cambio de día de turno**: si se retira la reasignación heredada. Toca también a Moiskimdo.
13. **Tarifas de sede y cuantía en ficha**: si entran ahora (Herrera es 2027) o se aplazan.
14. **245**: comprobar en producción `aforo_turno` de Moiskimdo antes de construir. Requiere comando al propietario.
15. **Desglose de tesorería** con varias sedes (D6-12) y filtros: qué pantallas y en qué orden.
16. Encargos del profesor que se va (`fase3-6c-solicitudes.md:497`) y día de vencimiento por escuela: si entran o se aplazan.

**Pantallas sin boceto.** `fase3-e8-miembros.md` §5 fija solo 5.1 (menú), 5.2 (Profesores) y 5.3 (crear contraseña); ni `fase3-e8b-ronda4.md` ni el plan e8b tienen boceto de:

- Sedes (lista, crear, editar, activar).
- Miembros (lista, ficha, invitar, roles, sedes, grupos) y sustitución de Gestión de Roles.
- Selector de escuela.
- Selector de sede en alta de grupo (ronda 4 solo anota el hueco, `fase3-e8b-ronda4.md:223`) y corrección de sede del alumno.
- Filtros por sede y columnas de sede en alumnos, grupos, preinscripciones, convocatorias, tesorería.
- Menú y panel de R, Inicio de R; bandeja de solicitudes para R (la existente no contempla el caso).
- Tarifas de sede y cuantía en la ficha.
- Aviso de aforo por sede (`DESIGN.md:352` solo prevé retirar el rótulo).

Con boceto: alta de alumno con Sede (ronda 4, P-1). `caja-sede` ya existe y su selector no necesita boceto nuevo.

## E. No verificado

- Número de sedes reales de Moiskimdo en producción, su `aforo_turno`, y si hay alumnos, grupos o cargos con sede NULL: no se consulta producción. Solo se sabe lo que dicen V31 y comentarios.
- Si existen cuentas R, R + Pr, R + F o en dos escuelas en producción.
- Cómo se dará de alta la organización Sierra Sur y su primera sede (`roadmap.md:47`): sin API encontrada; quizá SQL o migración. No busqué en scripts fuera de `src-api`.
- Valor de `limite_turno` de Moiskimdo: `roadmap.md:375` dice 36, no comprobado.
- Comportamiento real de R + F, R + P y R + G: leído, no ejecutado. No se ha lanzado ningún test ni la app.
- Si `AlumnoConGruposDTO` se filtra en alguna capa para R: no recorrí todas sus construcciones.
- Que `GET /api/alumnos`, `/api/grupos/resumen` y el resto de lecturas de panel funcionen completas para R: la matriz solo fija permisos, no qué falla en cada pantalla. El listado de rutas denegadas de B0 sale de Grep en `FE/`, no de una navegación real; pueden faltar llamadas por composición dinámica de URL.
- Coste y rendimiento de filtros por sede con 800 alumnos: sin medir.
- CodeGraph: usado para `SedeService`; el resto se leyó con Read, Grep y shell porque las rutas ya estaban localizadas y la herramienta MCP no estaba disponible en este agente (solo el CLI).
- Datos de `roadmap.md`: export del 24/10; decisiones posteriores pueden haber cambiado (se contrastó con `continuacion-2026-10-30.md` lo citado).
