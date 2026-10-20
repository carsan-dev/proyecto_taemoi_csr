# Entrega 7 (el profesor con sus grupos): plan de implementación

> **Ejecutores:** agentes savvy-* orquestados por el router. Cada pieza E7-n es un agente en su propio worktree, con
> revisión independiente (`savvy-reviewer`) antes de la PR. Las piezas de permisos, datos sensibles o dinero (E7-1,
> E7-2, E7-3, E7-4) las ejecuta `savvy-careful` y llevan la revisión en opus; E7-5 la ejecuta `savvy-medium` en opus
> y E7-6 `savvy-medium`. Los pasos usan casillas (`- [ ]`). Este plan no detalla las tareas TDD de cada pieza: cada
> implementador añade su sección detallada (§2 en adelante) al lanzarse, como en la 6c.

**Objetivo:** que el profesor vea sus grupos, sus alumnos y una ficha reducida, consulte la salud con registro,
descargue la asistencia de su grupo y gestione los grados con apto, fecha y pases (decisión 207); y que propietario y
gestor asignen profesores a un grupo (decisión 208).

**Arquitectura:** rutas propias bajo `/api/profesor/**`, con una sola entrada nueva en la lista blanca de la sesión
solo-profesor y una regla de URL `ROLE_PROFESOR`. Cada servicio filtra solo por `AlcanceSesion.grupos()` (patrón
`CajaService.alumnosDeMisGrupos`) y devuelve DTO propios reducidos. Los cambios de grado llaman a los mismos servicios
que gestión. La asignación va en `/api/grupos/{grupoId}/profesores`, solo P y G, con `organizacion_id` explícito. El
frontend añade «Mis grupos» junto a «Mi caja» y la sección «Profesores» en la ficha del grupo.

**Tecnología:** Spring Boot + JPA/Hibernate (MySQL 8, Flyway), Angular (standalone, Karma/Jasmine), SweetAlert2.

**Spec:** `docs/multi-escuela/fase3-e7-profesor.md`, aprobado por el propietario el 20/10/2026 (decisiones 207, 208
y 209). El ejecutor lee también ese documento. Las citas `§n` remiten a él.

Rutas abreviadas: `J/` = `src-api/src/main/java/com/taemoi/project/`, `T/` = `src-api/src/test/java/com/taemoi/project/`,
`F/` = `src-frontend/src/app/`.

## Restricciones globales

- **Sin migración** salvo que se demuestre necesaria (D7-22: la PK `(usuario_id, grupo_id)` de `asignacion_profesor`
  y `uk_membresia_usuario_organizacion_rol` ya imponen las reglas). Si lo fuera, la siguiente versión es **V31_9**,
  porque la última es `V31_8__solicitudes_y_devoluciones.sql`; V32 queda reservada para la contracción. Solo
  ampliación: tablas nuevas vacías y columnas NULL o con valor por defecto. Se anota en la PR y se para a preguntar.
- Aislamiento por escuela: las entidades nuevas heredan `EntidadOrganizacion` (`@TenantId`). Las consultas nativas
  y **toda** consulta a `asignacion_profesor` y `membresia` (no son `EntidadOrganizacion`) llevan `organizacion_id`
  explícito.
- Filtro del profesor: solo `alcance.grupos()` y `@acceso.alumnoDeMisGrupos` / `@acceso.grupoDeMisGrupos`. Nunca
  `GestionPorSede.restriccion()`, `PredicadosAlcance.alumnos(alcance)` ni `AccesoRecursos.alumno/grupo(id,'LEER')`,
  que conceden por sede (D7-11).
- Moiskimdo (organización 1) se comporta igual que hoy, salvo la sección «Profesores» de la 208. Los dorados
  (`MoiskimdoTesoreriaIdenticaIntegrationTest` y el JSON dorado de alumnos) pasan sin cambios.
- Nunca se suben datos reales al repo: ni nombres, teléfonos, DNI ni datos de salud de alumnos en fixtures, SQL ni
  capturas.
- Batería del backend en dos mitades: `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion`, en
  primer plano. Si se cuelga un proceso, se limpia al cerrar.
- Los IT comparten un MySQL por JVM y en Linux corren en otro orden: ningún test deja datos sucios (limpieza en
  `finally`).
- Frontend: `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build:production` **sin aviso de
  presupuesto** (el inicial ronda 1,49 MB y el límite de aviso es 1,5 MB). Las vistas nuevas van en rutas perezosas.
- Karma solo ejecuta los specs que figuran en `src-frontend/tsconfig.spec.json` («files») **y** en `angular.json`
  (test «include»). Cada spec nuevo se añade a las dos listas.
- Finales de línea: antes de cada commit, `git diff --stat` debe coincidir con `git diff --ignore-cr-at-eol --stat`.
  Un fichero mixto que se normalice se restaura en binario desde `HEAD` y se edita sin reescribirlo. Ficheros nuevos
  en LF.
- Commits convencionales en español, sin `Co-Authored-By` ni atribución a IA.
- Matriz: cada ruta nueva lleva su línea (y su `HEAD` si es `GET`) en
  `src-api/src/test/resources/seguridad/matriz-permisos.txt` y su nota en `docs/multi-escuela/matriz-endpoints.md`.
  Ninguna casilla existente cambia; el diff de la matriz solo añade líneas.
- Las rutas del profesor entran por la lista blanca (`/api/profesor/**`) y cada una lleva su caso en
  `MatrizPermisosTest` (mapa `PROFESOR_CAJA_6B2` o uno análogo, `PROFESOR_E7`, con el test que la prueba).
- Registro de acciones: solo ids y valores de enumeración en el resumen; nunca texto libre (§7).
- UI: se trabaja con las skills `impeccable` y `frontend-design` y se adjuntan capturas a 375 y 1280 px antes de la
  PR, con datos ficticios. Lo visual lo decide quien construye el frontend dentro del boceto (§5).

## Puntos de revisión

Los casos que más pueden morder. Cada uno tiene su test en la pieza que lo posee.

1. **Ninguna ruta de gestión se abre al profesor** (fail-open de `GestionPorSede`).
   - E7-1: `profesorNoEntraEnRutasDeGestion_403` (listado de alumnos, ficha, tutores, salud de gestión, PDF de
     asistencia de gestión, `PUT .../grado`) y la matriz con diff solo en líneas nuevas.
2. **Solo sus grupos, por id y en listados.**
   - E7-1: `alumnoDeOtroGrupo_403`, `grupoDeOtroProfesor_403`, `grupoDeOtraEscuela_404` (o 403 sin revelar
     existencia; el constructor lo fija y lo anota) y `responsableMasProfesorNoVeSusSedes` (sesión 184).
   - E7-1: `familiaMasProfesorUsaSusGrupos`.
3. **Sesión solo-profesor y lista blanca.**
   - E7-1: `sesionSoloProfesorFueraDeListaBlanca_403` y `sesionSoloProfesorLaCierraLaCapaDeUrlFueraDeSuListaBlanca`
     en verde con el prefijo nuevo.
4. **DTO sin datos de más.**
   - E7-1: `fichaSinNifEmailDireccion` (tampoco observaciones, tarifa ni cobros; se comprueban las claves del JSON).
5. **Salud registrada y sin caché.**
   - E7-2: `lecturaSaludRegistraEvento_yNoStore` (evento en `auditoria_evento`, módulo `salud`, con el usuario del
     profesor; `Cache-Control: no-store`) y que la salud no sale en listados ni en el PDF.
6. **Asistencia solo del grupo.**
   - E7-2: `pdfAsistenciaSoloAlumnosDelGrupo` (dos grupos con el mismo nombre en sedes distintas).
7. **Pases y dinero.**
   - E7-3: `paseCreaCargoYNoSubeGrado`, `paseSinApto_400_comoGestion` (mismo mensaje que gestión),
     `dosPasesSeguidos_409_unSoloCargo` (D7-17) y `paseRegistraAccionConIds`.
8. **Asignación.**
   - E7-4: `asignarEnOtraEscuela_404`, `emailSinCuenta_404_conMensaje`, `asignarCreaMembresiaProfesor`,
     `asignarDosVecesCambiaRol`, `quitarAsignacionConservaMembresia`, `quitarAsignacionCierraElAccesoEnLaSiguientePeticion`
     y `responsableDeSedeNoAsigna_403`.
9. **Moiskimdo.** Los dorados pasan sin cambios en cada pieza backend.

---

## 1. Mapa de piezas

Orden: **E7-1 → E7-2 → E7-3** (comparten controlador, regla de URL y matriz). **E7-4** puede ir tras E7-1, en
paralelo con E7-2 y E7-3. **E7-5** tras E7-3. **E7-6** tras E7-4.

Los nombres de esta tabla fijan los contratos entre piezas. Quien construye puede renombrarlos, pero entonces lo
anota en la PR y actualiza este mapa.

| Pieza | Objetivo | Ficheros principales | Produce (lo que usan otras piezas) | Consume | Depende de | Riesgo / revisión | Cómo se verifica |
|---|---|---|---|---|---|---|---|
| **E7-1** backend | Base `/api/profesor/**` y lectura: mis grupos, alumnos de un grupo (deportes, grado, apto), ficha reducida. Sin escrituras | `J/config/SesionSoloProfesorAuthorizationManager.java:132-147` (entrada `/api/profesor/**`), `J/config/SecurityConfiguration.java` (regla junto a la l. 489), `J/acceso/AccesoRecursos.java` (`grupoDeMisGrupos`, junto a `alumnoDeMisGrupos:209`), `J/controllers/ProfesorController.java` (nuevo), `J/services/ProfesorService.java` (nuevo), `J/dtos/ProfesorDTOs.java` (nuevo), `T/seguridad/MatrizPermisosTest.java` (condición `listaBlanca` y mapa), `T/config/SesionSoloProfesorAuthorizationManagerTest.java`, `src-api/src/test/resources/seguridad/matriz-permisos.txt`, `docs/multi-escuela/matriz-endpoints.md` | `ProfesorController` con `@RequestMapping("/api/profesor")`; `@acceso.grupoDeMisGrupos(Long grupoId)`; `ProfesorDTOs.Grupo`, `AlumnoDeGrupo`, `Ficha`, `DeporteDelAlumno` (grado con nombre, `fechaGrado`, apto, `admitePases`, `pideRojoBordado`), `Contacto`; `ProfesorService.misGrupos()`, `alumnosDelGrupo(Long)`, `ficha(Long)`; mapa `PROFESOR_E7` en la matriz | `AlcanceActual.obtener().grupos()`, `CajaService.alumnosDeMisGrupos` (patrón), `ProgresionGrados` para el nombre y el siguiente grado | — | Alto (permisos y datos personales); `savvy-careful`, revisión opus | IT en MySQL con dos profesores, dos sedes y otra escuela (patrón `MiCajaProfesorIntegrationTest`); puntos de revisión 1-4; matriz con diff solo en líneas nuevas |
| **E7-2** backend | Salud a petición y PDF de asistencia del grupo | `ProfesorController`, `ProfesorService`, `J/services/AlumnoSaludService.java:45-52` (reutilizar `obtener`; revisable `obtenerComoProfesor`), `J/services/PDFService.java:49` y `J/services/impl/PDFServiceImpl.java:1196` (sobrecarga por ids de turno; la firma por `String` no cambia), matriz | `GET /api/profesor/alumnos/{alumnoId}/salud` (`no-store`); `GET /api/profesor/grupos/{grupoId}/asistencia?year&month`; `PDFService.generarListadoAsistenciaPorTurnos(int, int, List<Long>)` (nombre al constructor) | E7-1 | E7-1 | Alto (salud); `savvy-careful`, revisión opus | Puntos de revisión 5 y 6; el PDF de gestión sale idéntico byte a byte (o en contenido) antes y después |
| **E7-3** backend | Apto, fecha de grado y pases por rutas propias + `RegistroAccionesProfesor`; 409 en el segundo pase pendiente (D7-17) | `ProfesorController`, `ProfesorService`, `J/services/impl/AlumnoServiceImpl.java:2259-2328` (`pasarGradoConDerechoExamen`, `pasarGradoPorRecompensa`), `J/services/impl/AlumnoDeporteServiceImpl.java:404-437` (`actualizarAptoParaExamen`, `actualizarFechaGrado`), `J/services/RegistroAccionesProfesor.java` (nuevo, copia de `RegistroAccionesCaja`), matriz | `PUT .../apto`, `PUT .../fecha-grado`, `POST .../pase-examen`, `POST .../pase-recompensa` bajo `/api/profesor/alumnos/{alumnoId}/deportes/{deporte}/`; `RegistroAccionesProfesor.Accion` y `registrar(...)`; constante `PASE_PENDIENTE` = «Ya hay un pase pendiente de cobro para este deporte» | E7-1; el cargo pendiente se cobra con `POST /api/caja/profesor/cargos/{cargoId}/cobrar` (ya existe) | E7-2 | Alto (crea cargos); `savvy-careful`, revisión opus | Punto de revisión 7; el cargo aparece en «Por cobrar» de Mi caja; cobrarlo sube el grado (camino E, test de punta a punta); deshacer ese cobro no revierte el grado (se fija el comportamiento actual con un test); gestión sin cambios. Pase pendiente = `producto_alumno` del mismo `alumno_deporte_id`, sin pagar ni cancelar, cuyo producto concede grado (`ProductosCatalogoService.buscarCodigoGradoConcedido`) |
| **E7-4** backend | Asignación de profesores (208) + registro | `J/controllers/GrupoController.java` (o `GrupoProfesoresController` nuevo bajo `/api/grupos/{grupoId}/profesores`), `J/services/AsignacionProfesorService.java` (nuevo), `J/repositories/AsignacionProfesorRepository.java` (`findByGrupoIdAndOrganizacionId` ya existe), `J/repositories/MembresiaRepository.java`, `J/repositories/UsuarioRepository.java` (`findByEmailIgnoreCase`), `RegistroAccionesProfesor` (de E7-3, o lo crea E7-4 si va antes), matriz | `GET`, `POST {email, rol}` y `DELETE /{usuarioId}`; `AsignacionProfesorService.listar(Long grupoId)`, `asignar(Long grupoId, String email, RolAsignacionProfesor)`, `quitar(Long grupoId, Long usuarioId)`; DTO con id de usuario, nombre, email y rol; mensaje `EMAIL_SIN_CUENTA` = «No hay ninguna cuenta con ese email. Que se registre primero.» | `GrupoRepository` (acotado por `@TenantId`) | E7-1 | Alto (permisos, tablas sin `@TenantId`); `savvy-careful`, revisión opus | Punto de revisión 8; IT con dos escuelas; la membresía creada abre la sesión solo-profesor; R da 403 |
| **E7-5** frontend | Vistas del profesor: Mis grupos, grupo (filtro Todos/Aptos, hoja de asistencia con mes), ficha (contacto, fecha de grado, apto, pases con confirmación, salud plegada); pestañas «Mis grupos \| Mi caja»; aterrizaje tras el login en «Mis grupos»; enlaces de las cuentas mixtas | `F/app.routes.ts` (rutas con `profesorGuard`, junto a `mi-caja` l. 413-416), `F/componentes/endpoints/mis-grupos/*` (nuevo), `F/servicios/endpoints/profesor.service.ts` (nuevo), `F/interfaces/profesor.ts` (nuevo), `F/componentes/endpoints/mi-caja/mi-caja.component.html` (pestañas superiores), `F/servicios/authentication.service.ts:378`, `F/componentes/vistas/layout/header/header.component.html:501-506, 604-606, 733-736, 830-832` | — | Rutas de E7-1, E7-2 y E7-3 | E7-3 | Medio; `savvy-medium` en opus | Specs registrados en las dos listas: la salud solo se pide al abrir la sección; el botón del pase se desactiva durante la petición; el 409 y los 400 muestran el mensaje del backend; el solo-profesor aterriza en «Mis grupos». Build sin aviso y capturas a 375 y 1280 px |
| **E7-6** frontend | Sección «Profesores» en la ficha del grupo (lista, alta por email y rol, quitar con confirmación) | `gruposEditar/:id` (§11.1): `F/componentes/endpoints/editar-grupo/`, `F/servicios/endpoints/` (servicio de asignación) | — | Rutas de E7-4 | E7-4 | Medio (visible para Moiskimdo); `savvy-medium` | Specs registrados: lista vacía, alta, cambio de rol, quitar con confirmación, email sin cuenta con el mensaje del backend. Build sin aviso y capturas |

Pruebas mínimas de cada pieza backend: IT en MySQL con dos profesores, dos sedes y una segunda escuela; un alumno
compartido por dos grupos; una cuenta F+Pr y otra R+Pr; Moiskimdo idéntico con ADMIN y MANAGER; matriz con diff solo
en líneas nuevas.

**Despliegue (lo decide el propietario).** Recomendación: una sola PR al final, sin migración → atajo `desplegar`,
con su línea en `checklist-despliegue.md` («E7: sin migración; prueba manual con datos ficticios en Sierra Sur:
asignar un profesor, entrar como él, pase y cobro»). Alternativa: E7-4 + E7-6 primero (la escuela puede ir asignando
profesores, que seguirían viendo solo «Mi caja») y el resto después.

**Estado (20/10/2026).**

| Pieza | Estado | Commit | Migración | Notas |
|---|---|---|---|---|
| E7-1 | Pendiente | — | No | |
| E7-2 | Pendiente | — | No | |
| E7-3 | Pendiente | — | No | |
| E7-4 | Pendiente | — | No | |
| E7-5 | Pendiente | — | No | |
| E7-6 | Pendiente | — | No | |
