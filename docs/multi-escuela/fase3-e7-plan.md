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

---

## 2. Pieza E7-1: base `/api/profesor/**` y lectura (detallada)

Rama `feature/e7-1` desde `feature/e7-diseno`. Sin migración: atajo `desplegar`. Solo lectura; ninguna escritura.

Criterios del constructor (no los fija el diseño; se anotan en la PR):
- **403 sin revelar existencia.** Un grupo que no está en `alcance.grupos()` (de otro profesor, de otra escuela o
  inexistente) da **403** en `@acceso.grupoDeMisGrupos`, igual que un alumno fuera de sus grupos en
  `@acceso.alumnoDeMisGrupos`. El 404 de otra escuela no se puede dar sin consultar fuera del alcance, y el 403
  uniforme no distingue «existe pero no es tuyo» de «no existe». El test se llama `grupoDeOtraEscuela_403`.
- **Alumno de baja** (D7-13): si sigue en un grupo suyo pasa `@acceso.alumnoDeMisGrupos` y el servicio responde
  **404**; no sale en el listado del grupo ni cuenta en «nº de alumnos activos».
- **Defensa en el servicio.** `ProfesorService` vuelve a filtrar por `alcance.grupos()` (403 si el grupo no es suyo; la
  ficha solo con los grupos que comparte, 403 si no comparte ninguno), aunque el `@PreAuthorize` ya lo haya hecho.
- **DTO en `J/dtos/response/ProfesorDTOs.java`** (junto a `CajaDTOs`), no en `J/dtos/`. Records: `Grupo`, `Sede`,
  `Disciplina`, `Grado`, `AlumnoDeGrupo`, `DeporteEnLista`, `Ficha`, `GrupoCompartido`, `Contacto`, `DeporteDelAlumno`.
- **Contacto** (D7-2): una fila por teléfono no vacío: el alumno (`telefono` y `telefono2`, `tipo` = `ALUMNO`) y cada
  tutor con teléfono (`tipo` = `TUTOR`, nombre, apellidos y parentesco), por `orden`. Un tutor sin teléfono no sale.
- **Deportes**: solo los `alumno_deporte` activos. En la lista del grupo, `delGrupo` marca el deporte de la disciplina
  del grupo (para el filtro y la línea por deporte de §5.2). En la ficha, `deMisGrupos` marca las disciplinas de los
  grupos que comparte (§11, pregunta 2, cerrada con la recomendación) y `admitePases` = `deMisGrupos` y disciplina con
  grados y grado actual y siguiente grado. El apto va aparte (`apto`); los pases solo salen con los dos (§5.3).
  `siguienteGrado` (código y nombre) sirve para la confirmación «Amarillo → Naranja». `pideRojoBordado` = escuela con
  `comportamiento_legado_taemoi` y siguiente grado `ROJO`, la misma regla que gestión (`esSiguienteGradoRojo`). El
  siguiente grado se calcula como `AlumnoServiceImpl.pasarGradoInterno` (grupo de edad por
  `CatalogoDisciplinasService.esMenorPorAnioNatural` y `ProgresionGradosService.obtenerSiguienteGrado`).
- **Edad** en años cumplidos a hoy; nunca la fecha de nacimiento.
- **P+Pr y G+Pr** (D7-10): `GET /api/profesor/grupos` exige `ROLE_PROFESOR` **sin** `ROLE_ADMIN` ni `ROLE_MANAGER`;
  las dos rutas con id van por `@acceso.*DeMisGrupos`, que ya exige `!alcance.total()`.

### Tarea 1: tests que fallan

**Ficheros:**
- Crear: `T/integracion/ProfesorGruposIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `PER_CLASS`,
  `extends MySqlIntegrationTest`; escuelas `EscuelaConSedesDePrueba.crear(…, "it-e7-1")` y `"it-e7-1-otra"`, datos
  inventados). Cuentas: `profA` (solo profesor; `gA` titular y `gSin` ayudante), `profB` (solo profesor; `gB`), `profRP`
  (responsable de la sede A + profesor de `gB`, sesión 184), `profFP` (familia + profesor de `gA2`), `gestorProf`
  (ADMINISTRADOR + PROFESOR asignado a `gA`), `profOtra` (otra escuela, su `gA`), gestor y propietario. `aGrupoA` está
  en `gA` y `gB` (alumno compartido). Sistema de grados propio de la escuela (Blanco, Amarillo, Rojo, Negro) y
  `comportamiento_legado_taemoi` a TRUE solo en esa escuela sintética.
- Modificar: `T/acceso/AccesoRecursosTest.java`, `T/config/SesionSoloProfesorAuthorizationManagerTest.java`,
  `T/seguridad/MatrizPermisosTest.java` (mapa `PROFESOR_E7` y prefijo en `listaBlanca`),
  `src-api/src/test/resources/seguridad/matriz-permisos.txt` (6 líneas nuevas).
- Crear: `T/services/ProfesorServiceTest.java` (regla de `pideRojoBordado`, sin Spring).

- [x] **Paso 1: escribir los tests**:
  - IT: `misGrupos_soloLosSuyos_conSedeRolDisciplinaYAlumnosActivos`, `misGrupos_sinGrupos_listaVacia`,
    `alumnosDelGrupo_activosConDeportesGradoYApto_sinDatosSensibles`, `grupoDeOtroProfesor_403`,
    `grupoDeOtraEscuela_403`, `ficha_reducidaConEdadGruposCompartidosContactoYDeportes`, `fichaSinNifEmailDireccion`
    (claves exactas del JSON en cada nivel y ningún valor sensible en el cuerpo), `alumnoDeOtroGrupo_403`,
    `alumnoDeOtraEscuela_403`, `alumnoDeBaja_404_yFueraDeLaLista`, `responsableMasProfesorNoVeSusSedes`,
    `familiaMasProfesorUsaSusGrupos`, `sesionSoloProfesorFueraDeListaBlanca_403`,
    `profesorNoEntraEnRutasDeGestion_403` (listado de alumnos, ficha, tutores, salud, PDF de asistencia y
    `PUT .../grado`, con las tres clases de cuenta de profesor), `gestionNoUsaLasRutasDelProfesor_403` (ADMIN, MANAGER y
    ADMINISTRADOR + PROFESOR con asignación).
  - `AccesoRecursosTest`: `grupoDeMisGrupos_soloLaRamaDeProfesor`, `grupoDeMisGrupos_niGestionNiResponsableNiFamilia`,
    `grupoDeMisGrupos_profesorSinGrupos_noConsulta`.
  - `SesionSoloProfesorAuthorizationManagerTest`: las rutas `/api/profesor/...` pasan a la regla de siempre en
    `soloProfesorPasaPorLasReglasDeSiempreEnSuListaBlanca`; `/api/profesorx` no pasa.
  - `ProfesorServiceTest`: `pideRojoBordado_soloConLegadoYSiguienteRojo`.
  - Matriz: las 6 líneas (`GET`/`HEAD` de las tres rutas: PROFESOR `PERMITE` en `/grupos`, `ALCANCE` en las otras dos;
    resto `DENIEGA`), declaradas en `PROFESOR_E7` con su test.
- [x] **Paso 2: comprobar que fallan.** `mvn test -Dtest='ProfesorGruposIntegrationTest,AccesoRecursosTest,SesionSoloProfesorAuthorizationManagerTest,MatrizPermisosTest,ProfesorServiceTest' -DexcludedGroups=migracion`:
  no compila hasta crear `grupoDeMisGrupos`, `ProfesorService` y los DTO; con los esqueletos, las rutas dan 403/404.

### Tarea 2: implementar

**Ficheros:**
- Modificar: `J/acceso/AccesoRecursos.java` (`grupoDeMisGrupos(Long)`, copia de `alumnoDeMisGrupos`).
- Modificar: `J/config/SesionSoloProfesorAuthorizationManager.java` (`rutas.matcher("/api/profesor/**")`).
- Modificar: `J/config/SecurityConfiguration.java` (`/api/profesor/**` → `ROLE_PROFESOR`, tras el bloque de `/api/caja`).
- Crear: `J/controllers/ProfesorController.java`, `J/services/ProfesorService.java`, `J/dtos/response/ProfesorDTOs.java`.

**Interfaces:**
- Produce: `GET /api/profesor/grupos`, `GET /api/profesor/grupos/{grupoId}/alumnos`, `GET /api/profesor/alumnos/{alumnoId}`;
  `@acceso.grupoDeMisGrupos(Long grupoId)`; `ProfesorService.misGrupos()`, `alumnosDelGrupo(Long)`, `ficha(Long)`.
- Consume: `AlcanceActual.obtener().grupos()`, `AsignacionProfesorRepository.findByUsuarioIdAndOrganizacionId`
  (con `TenantContext.get()`), `TutorRepository.findByAlumnoIdOrderByOrdenAscIdAsc`, `ProgresionGradosService`,
  `CatalogoDisciplinasService.esMenorPorAnioNatural`, `ComportamientoLegadoService.activo()`.

- [x] **Paso 1:** las tres capas (lista blanca, regla de URL, `@PreAuthorize`) y `grupoDeMisGrupos`.
- [x] **Paso 2:** servicio y DTO; consultas JPQL acotadas por `@TenantId` (Grupo, Alumno, AlumnoDeporte, Tutor, Sede) y
  `asignacion_profesor` con `organizacion_id` explícito. Sin N+1 en la lista del grupo (dos consultas).
- [x] **Paso 3:** los tests de la tarea 1 en verde.

### Tarea 3: matriz, documentación y batería

- [x] `docs/multi-escuela/matriz-endpoints.md`: nota de las 6 líneas y recuento de la cabecera.
- [x] `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion` en `src-api`, en primer plano, en verde;
  dorados de Moiskimdo sin tocar.
- [x] `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`.

**Nota de la revisión (20/10/2026).** `grupo.disciplina_id` es NOT NULL desde V26, así que los «grupos sin disciplina» de §11.2 no pueden darse: E7-3 no los reimplementa. Queda para E7-3 un caso de un alumno que estuvo en un grupo del profesor y se le quitó (sin fila en `alumno_grupo`): 403.

---

## 3. Pieza E7-2: salud a petición y hoja de asistencia del grupo (detallada)

Rama `feature/e7-2` desde `feature/e7-1`. Sin migración: atajo `desplegar`. Solo lectura; ninguna escritura salvo el
evento de la lectura de salud.

Criterios del constructor (no los fija el diseño; se anotan en la PR):
- **Salud** (D7-14): `ProfesorService.salud` vuelve a comprobar `alcance.grupos()` (403) y que el alumno está activo
  (404, como E7-1) **antes** de leer, y llama a `AlumnoSaludService.obtenerComoProfesor`, que es `obtener` con la ruta
  del profesor en el `endpoint` del evento y el resumen «Lectura de datos de salud del alumno #id (profesor)» (solo el
  id y un texto fijo). Mismo módulo (`salud`), misma transacción, mismo DTO (`AlumnoSaludDTO`, sin campos de más:
  `origen` y `actualizadoEn` no son datos de salud) y `Cache-Control: no-store`. El método del servicio del profesor es
  `@Transactional` de escritura (una transacción `readOnly` no admitiría el evento). Un 403/404 no deja evento de salud.
- **El filtro HTTP no duplica la lectura**: `AuditoriaRequestFilter.RUTA_SALUD` reconoce también
  `/api/profesor/alumnos/{id}/salud` (como la de gestión: las lecturas 2xx no se registran dos veces y nunca se guarda
  el cuerpo). Es la regla de §7 («las lecturas 2xx de salud no se duplican»).
- **Asistencia** (D7-15): `ProfesorService.asistencia` comprueba el grupo (403), valida `year` (1900-2999) y `month`
  (1-12) con 400 (gestión no los valida: un mes 13 le da 500; no se cambia) y resuelve los turnos **de ese grupo por
  id**. `PDFService.generarListadoAsistenciaPorTurnos(int, int, List<Long>)` pinta, por cada turno (por día de la semana
  y hora), los alumnos **activos** del turno que además están en el grupo del turno (`alumno_grupo`), con su inscripción
  activa en la disciplina del grupo: la misma página que gestión (el PDF de siempre, D7-5). La firma por `String` no
  cambia: su HTML sale de los mismos métodos extraídos, idéntico. El generador no lee `alumno_salud`.
- **Nombre del fichero** del PDF del profesor: `Asistencia-grupo-<id>-<yyyy>-<MM>.pdf` (sin el nombre del grupo, que
  podría llevar comillas o acentos en la cabecera). `Content-Type: application/pdf` y `attachment`, como gestión.

### Tarea 1: tests que fallan

**Ficheros:**
- Crear: `T/integracion/ProfesorSaludAsistenciaIntegrationTest.java` (patrón `ProfesorGruposIntegrationTest`; escuelas
  `"it-e7-2"` y `"it-e7-2-otra"`; grupo homónimo de `gA` en la sede B con turno el mismo lunes; datos inventados).
- Modificar: `T/services/AlumnoSaludServiceTest.java`, `T/seguridad/MatrizPermisosTest.java` (`PROFESOR_E7`),
  `src-api/src/test/resources/seguridad/matriz-permisos.txt` (4 líneas nuevas).

- [x] **Paso 1: escribir los tests**:
  - IT: `lecturaSaludRegistraEvento_yNoStore` (evento `READ`, módulo `salud`, recurso = alumno, usuario = el profesor,
    endpoint del profesor; uno solo; `Cache-Control: no-store`), `saludDeAlumnoDeOtroGrupo_403`,
    `saludDeAlumnoDeOtraEscuela_403`, `saludAlumnoDeBaja_404` (sin evento de salud en los tres),
    `saludResponsableMasProfesorNoVeSusSedes`, `gestionNoUsaSaludDelProfesor_403` (ADMIN, MANAGER y ADMINISTRADOR +
    PROFESOR con asignación, en las dos rutas), `saludNoSaleEnListadosNiFicha`, `pdfAsistenciaSoloAlumnosDelGrupo`
    (grupo homónimo en otra sede el mismo lunes, alumno del turno sin `alumno_grupo`, alumno de baja; sin salud en el
    texto), `pdfAsistenciaGrupoAjeno_403`, `pdfAsistenciaMesInvalido_400` y `pdfAsistenciaGestionNoCambia` (texto
    exacto del PDF de gestión, fijado antes del cambio).
  - `AlumnoSaludServiceTest`: `comoProfesor_siNoSePuedeAuditarNoSeLee` y `comoProfesor_registraLaRutaDelProfesor`.
  - Matriz: `GET`/`HEAD` de las dos rutas (PROFESOR `ALCANCE`; resto `DENIEGA`), declaradas en `PROFESOR_E7`.
- [x] **Paso 2: comprobar que fallan.** `pdfAsistenciaGestionNoCambia` pasa antes del cambio (fija el contenido); el
  resto da 403/404 o no compila hasta crear las rutas.

### Tarea 2: implementar

- [x] `AlumnoSaludService.obtenerComoProfesor(Long)` (comparte la lectura auditada con `obtener`, que no cambia).
- [x] `ProfesorService.salud(Long)` y `asistencia(Long, int, int)`; rutas en `ProfesorController` con
  `@acceso.alumnoDeMisGrupos` / `@acceso.grupoDeMisGrupos`.
- [x] `PDFService.generarListadoAsistenciaPorTurnos` y extracción en `PDFServiceImpl` de la cabecera HTML, la página
  de un turno, las fechas del mes y el render (los usa también la firma por `String`).
- [x] `AuditoriaRequestFilter.RUTA_SALUD` con la ruta del profesor.

### Tarea 3: matriz, documentación y batería

- [x] `docs/multi-escuela/matriz-endpoints.md`: nota de las 4 líneas y recuento de la cabecera.
- [x] `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion` en `src-api`, en primer plano, en verde;
  dorados de Moiskimdo sin tocar.
- [x] `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`; ficheros nuevos en LF.

---

## 4. Pieza E7-3: apto, fecha de grado y pases del profesor (detallada)

Rama `feature/e7-3` desde `feature/e7-2`. Sin migración: atajo `desplegar`. Escrituras: el apto, la fecha de grado y
el cargo pendiente del pase (dinero); el grado **no** cambia aquí (sube al cobrar el cargo, camino E).

Criterios del constructor (no los fija el diseño; se anotan en la PR):
- **Comprobaciones en el servicio, por este orden**, además de `@acceso.alumnoDeMisGrupos(#alumnoId)`: el alumno
  comparte algún grupo con el profesor (`alcance.grupos()`; si no, **403** sin revelar existencia), está activo (**404**,
  D7-13) y el deporte es el código de la disciplina de alguno de esos grupos compartidos (**403**; el mismo criterio que
  `deMisGrupos` de la ficha, §11 pregunta 2). Un código de deporte desconocido también da 403. Nunca por sede.
- **Mismos servicios que gestión** (D7-16): `AlumnoDeporteService.actualizarAptoParaExamen` y `actualizarFechaGrado`;
  los pases van por `AlumnoServiceImpl.pasarGradoInterno` (el de `pasarGradoConDerechoExamen` /
  `pasarGradoPorRecompensa`) a través de un método nuevo `AlumnoService.crearCargoDePase`, que devuelve el
  `ProductoAlumno` creado en vez del `AlumnoDeporte` (gestión sigue llamando a los dos de siempre, sin cambios). Mismas
  validaciones y mensajes (400): sin apto, sin grado, sin siguiente grado, sede principal inactiva, producto de grado
  inexistente. El cuerpo de error del profesor es `{"mensaje": ...}` (el `GlobalExceptionHandler`, como el resto de
  `/api/profesor` y de Mi caja); gestión responde el mismo texto en plano.
- **Cuerpos**: `PUT .../apto` `{"aptoParaExamen": true|false}` (sin el campo, 400 «El campo aptoParaExamen es
  requerido», como gestión); `PUT .../fecha-grado` `{"fechaGrado": "yyyy-MM-dd"}` (el nombre de gestión y de §6.2; sin
  el campo, 400 «La fecha de grado es requerida»; con otro formato, 400 «La fecha de grado no es válida», donde gestión
  daba 500); `POST .../pase-examen` y `.../pase-recompensa` con cuerpo opcional `{"rojoBordado": true|false}` (§11
  pregunta 3; el servicio de gestión solo lo usa cuando el siguiente grado es `ROJO`).
- **Respuestas**: apto y fecha devuelven `ProfesorDTOs.DeporteDelAlumno` (el de la ficha, recalculado: apto, fecha,
  `admitePases`, `siguienteGrado`, `pideRojoBordado`); los pases, `ProfesorDTOs.PaseCreado` = `{deporte:
  DeporteDelAlumno, cargo: {id, concepto, importe}}` (el grado sigue siendo el de antes; el cargo es el que sale en
  «Por cobrar» de Mi caja).
- **D7-17, doble pase**: la transacción del pase es `READ_COMMITTED` y bloquea primero la fila de `alumno_deporte`
  (`AlumnoDeporteRepository.bloquearPorId`, `SELECT ... FOR UPDATE` por id, sin joins); con la fila bloqueada busca un
  cargo de pase pendiente del mismo `alumno_deporte_id` (`(pagado IS NULL OR pagado = FALSE) AND cancelado_en IS NULL`
  y su producto concede grado según `ProductosCatalogoService.buscarCodigoGradoConcedido`): si lo hay, **409**
  `ProfesorService.PASE_PENDIENTE` = «Ya hay un pase pendiente de cobro para este deporte» y no crea nada. La segunda
  petición simultánea espera al bloqueo y, con READ COMMITTED, ve el cargo ya confirmado. Un cargo pendiente que no
  concede grado o un pase cancelado no bloquean. Gestión no cambia (dos pulsaciones siguen creando dos cargos).
- **Registro** (D7-18, §7): `RegistroAccionesProfesor` (módulo `profesor`, copia de `RegistroAccionesCaja`, misma
  transacción, sin capturar errores). El resumen solo admite ids (`#n`), enumeraciones y sí/no; cualquier otro valor
  es un error de programación (nunca texto libre). `APTO_CAMBIADO alumno=#a alumnoDeporte=#ad apto=SI|NO`;
  `FECHA_GRADO_CAMBIADA alumno=#a alumnoDeporte=#ad apto=SI|NO` (el apto que recalcula gestión; **sin la fecha**:
  solo ids y enumeraciones); `PASE_EXAMEN` y `PASE_RECOMPENSA alumno=#a alumnoDeporte=#ad gradoActual=#g
  gradoSiguiente=#g2 cargo=#c` (ids de `grado`; el siguiente es el que concede el producto del cargo). El enum deja
  sitio a las acciones de asignación de la E7-4 (§7), que las añade al usarlas.
- **Sin «Cambiar grado»** (207): no hay `PUT /api/profesor/.../grado` (404/405); el de gestión sigue en 403.

### Tarea 1: tests que fallan

**Ficheros:**
- Crear: `T/integracion/ProfesorGradosIntegrationTest.java` (patrón `ProfesorGruposIntegrationTest`; escuelas
  `"it-e7-3"` (con `comportamiento_legado_taemoi` y un sistema de grados propio Blanco, Amarillo, Rojo, Negro; productos
  de examen y recompensa a Rojo, normal y bordado) y `"it-e7-3-otra"`; datos inventados; un alumno nuevo por test).
- Modificar: `T/seguridad/MatrizPermisosTest.java` (`PROFESOR_E7`), `src-api/src/test/resources/seguridad/matriz-permisos.txt`
  (4 líneas nuevas).

- [x] **Paso 1: escribir los tests**:
  - IT: `paseCreaCargoYNoSubeGrado`, `paseSinApto_400_comoGestion`, `dosPasesSeguidos_409_unSoloCargo`,
    `dosPasesALaVez_unSoloCargo` (con la fila bloqueada desde otra conexión y en carrera libre),
    `soloBloqueaUnPasePendiente` (cargo pendiente sin grado y pase cancelado no bloquean), `paseRegistraAccionConIds`,
    `aptoYFechaRegistranAccion`, `paseEnDisciplinaNoCompartida_403`, `alumnoQuitadoDelGrupo_403` (seguimiento de la
    E7-1; restaura en `finally`), `alumnoDeOtroGrupoOEscuela_403_yDeBaja_404`, `cobrarElPaseEnMiCajaSubeElGrado`,
    `deshacerElCobroNoRevierteElGrado` (comportamiento actual), `pasePorRecompensaConRojoBordado`,
    `gestionSigueCreandoDosCargos`, `cambiarGradoDirectoNoExisteParaElProfesor`, `gestionNoUsaLasRutasDelProfesor_403`
    (ADMIN, MANAGER y ADMINISTRADOR + PROFESOR), `sesionSoloProfesorAlcanzaLasRutas` y `cuerposInvalidos_400`.
  - Matriz: `PUT .../apto`, `PUT .../fecha-grado`, `POST .../pase-examen`, `POST .../pase-recompensa` (PROFESOR
    `ALCANCE`; resto `DENIEGA`), declaradas en `PROFESOR_E7`.
- [x] **Paso 2: comprobar que fallan**: no compila hasta crear `RegistroAccionesProfesor` y `PASE_PENDIENTE`; con los
  esqueletos, las rutas dan 404.

### Tarea 2: implementar

- [x] `AlumnoDeporteRepository.bloquearPorId`; `AlumnoService.crearCargoDePase` (lo que hacía `pasarGradoInterno`,
  ahora devolviendo el cargo; los dos métodos de gestión devuelven su `AlumnoDeporte` como antes).
- [x] `RegistroAccionesProfesor`; `ProfesorService.actualizarApto`, `actualizarFechaGrado`, `pasarGrado`;
  `ProfesorRequests` (`Apto`, `FechaGrado`, `Pase`) y `ProfesorDTOs.PaseCreado` / `CargoDelPase`; las cuatro rutas en
  `ProfesorController` con `@acceso.alumnoDeMisGrupos(#alumnoId)`.

### Tarea 3: matriz, documentación y batería

- [x] `docs/multi-escuela/matriz-endpoints.md`: nota de las 4 líneas y recuento de la cabecera.
- [x] `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion` en `src-api`, en primer plano, en verde;
  dorados de Moiskimdo sin tocar.
- [x] `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`; ficheros nuevos en LF.
