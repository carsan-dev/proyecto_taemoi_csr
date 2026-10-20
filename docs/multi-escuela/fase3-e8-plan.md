# Entrega 8 (miembros, sedes y permisos de la sesión): plan de implementación

> **Ejecutores:** agentes savvy-* orquestados por el router. Cada pieza E8-n es un agente en su rama `feature/e8-<pieza>`, con
> revisión independiente (`savvy-reviewer`) antes de la PR. Las piezas backend (E8-1, E8-2, E8-3, E8-4) tocan
> permisos, cuentas o tablas sin `@TenantId`: las ejecuta `savvy-careful` y llevan la revisión en opus; E8-5 la
> ejecuta `savvy-medium` y E8-6 `savvy-medium` en opus. Los pasos usan casillas (`- [ ]`). Este plan no detalla las
> tareas TDD de cada pieza: cada implementador añade su sección detallada (§2 en adelante) al lanzarse, como en la E7.

**Objetivo:** API completa de sedes y miembros (roles con cargo, sedes del responsable, grupos del profesor,
invitaciones por correo), `GET /api/auth/permisos` y «Mis grupos» para propietario y gestor con grupos asignados
(decisiones 216-220 y 136), con retoques pequeños de pantalla.

**Arquitectura:** rutas nuevas `/api/sedes/**` y `/api/miembros/**` con regla de URL ADMIN/MANAGER y la D8 aplicada en
el servicio; toda consulta a `membresia`, `asignacion_profesor` y `membresia_sede` con `organizacion_id` explícito;
registro explícito en los módulos `miembros` y `sedes`. `GET /api/auth/permisos` en la lista blanca de la sesión
solo-profesor. El alcance de P y G con `ROLE_PROFESOR` calcula también sus grupos y las rutas del profesor filtran solo
por ellos. Frontend: servicio de permisos, menú y guard de «Mis grupos», retoque de «Profesores» y página «Crea tu
contraseña».

**Tecnología:** Spring Boot + JPA/Hibernate (MySQL 8, Flyway), Angular (standalone, Karma/Jasmine), SweetAlert2.

**Spec:** `docs/multi-escuela/fase3-e8-miembros.md` (decisiones 216-220 y 136, 20/10/2026). El ejecutor lee también
ese documento. Las citas `§n` y `D8-n` remiten a él.

Rutas abreviadas: `J/` = `src-api/src/main/java/com/taemoi/project/`, `T/` = `src-api/src/test/java/com/taemoi/project/`,
`F/` = `src-frontend/src/app/`.

## Restricciones globales

- **TDD.** Cada pieza escribe primero los tests que fallan, los ve fallar y luego implementa. La lista de tests va en
  la sección detallada de la pieza (§2 en adelante), que añade su implementador antes de empezar.
- **Sin migración prevista.** Solo E8-4 puede necesitarla (D8-13: si `usuario.contrasena` no admite NULL o el login no
  rechaza una cuenta sin contraseña). Entonces es **V31_9**, aditiva (la última es
  `V31_8__solicitudes_y_devoluciones.sql`; V32 queda reservada para la contracción); se anota en la PR y se para a
  preguntar.
- Aislamiento por escuela: `sede` es `EntidadOrganizacion` (`@TenantId`). `membresia`, `asignacion_profesor` y
  `membresia_sede` no lo son: **toda** consulta y escritura lleva `organizacion_id` explícito (la escuela activa,
  `TenantContext`), y las sedes de `membresia_sede` se validan contra la escuela de la membresía (D8-10).
- Matriz: cada ruta nueva lleva su línea (y su `HEAD` si es `GET`) en
  `src-api/src/test/resources/seguridad/matriz-permisos.txt` y su nota en `docs/multi-escuela/matriz-endpoints.md`.
  **Ninguna casilla existente cambia; el diff de la matriz solo añade líneas.** `GET /api/auth/permisos` entra en la
  lista blanca (`SesionSoloProfesorAuthorizationManager.listaBlanca()`) y en `PROFESOR_SESION_180` de
  `MatrizPermisosTest` con su porqué.
- Registro de acciones: solo ids, enumeraciones y booleanos en el resumen; nunca email, nombre, apellidos, cargo,
  dirección, teléfono ni token (§7). Una fila por escritura: una ruta entra en
  `AuditoriaRequestFilter.EscrituraConRegistroPropio` solo si todos sus caminos 2xx registran, con su caso en
  `AuditoriaRequestFilterRegistroPropioTest` y en un IT contra MySQL.
- Las rutas con datos personales en el cuerpo (email, nombre, apellidos, cargo, direccion, telefono…) entran en
  `CLAVES_REDACTADAS_POR_RUTA`, con su test.
- Moiskimdo (organización 1) se comporta igual que hoy salvo §8. Los dorados (`MoiskimdoTesoreriaIdenticaIntegrationTest`
  y el JSON dorado de alumnos) no se tocan y pasan en verde.
- IT: asserts independientes del orden (los IT comparten un MySQL por JVM y en Linux corren en otro orden); sin
  números aleatorios que choquen (expediente `MAX+1`, como en la paridad de tarifas); los datos incoherentes que se
  fuercen por SQL se deshacen en `finally`.
- Karma solo ejecuta los specs que figuran en `src-frontend/tsconfig.spec.json` («files») **y** en `angular.json`
  (test «include»). Cada spec nuevo se añade a las dos listas.
- Baterías, en primer plano: `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion` en `src-api`;
  `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build:production` **sin aviso de presupuesto** (el
  inicial ronda 1,50 MB; el aviso salta en 1.572.864 bytes). Si se cuelga un proceso, se limpia al cerrar.
- Finales de línea: ficheros nuevos en LF; antes de cada commit, `git diff --stat` debe coincidir con
  `git diff --ignore-cr-at-eol --stat`.
- Commits convencionales en español, sin `Co-Authored-By` ni atribución a IA. Ramas `feature/e8-<pieza>`.
- Frontend: se trabaja con las skills `impeccable` y `frontend-design` y se adjuntan capturas a 375 y 1280 px antes de
  la PR. Lo visual lo decide quien construye dentro del boceto (§5).
- Nunca se suben datos reales al repo ni a las capturas: ni nombres, emails, teléfonos, DNI ni datos de salud.
- La salud es dato sensible: el profesor (también P+Pr y G+Pr en «Mis grupos») solo ve la de sus alumnos y cada
  lectura queda registrada, como en la E7.

## Puntos de revisión

Los casos que más pueden morder. Cada uno tiene su test en la pieza que lo posee.

1. **«Mis grupos» de P y G solo con sus grupos** (D8-15).
   - E8-1: `propietarioProfesor_soloSusGrupos`, `gestorProfesor_alumnoDeOtroGrupo_403`,
     `gestionProfesorSinAsignaciones_listaVacia`, `gestionProfesor_saludRegistradaIgual` y
     `responsableMasProfesorNoVeSusSedes` en verde; la matriz sin casillas cambiadas.
2. **La consulta de alcance extra solo para P y G con `ROLE_PROFESOR`.**
   - E8-1: `alcanceDeGestionSinProfesor_noConsulta` (y familia, responsable y profesor como antes).
3. **`GET /api/auth/permisos` en la sesión solo-profesor y solo-plataforma.**
   - E8-1: `permisos_sesionSoloProfesor_soloProfesorYMisGrupos`, `permisos_soloPlataforma_rolesVacios`,
     `sesionSoloProfesorLaCierraLaCapaDeUrlFueraDeSuListaBlanca` en verde con la entrada nueva.
4. **Sedes sin borrar ni quedarse a cero.**
   - E8-2: `desactivarUltimaSedeActiva_409`, `nombreRepetidoEnLaEscuela_409`, `sedeDeOtraEscuela_404`,
     `desactivarSedeConservaResponsables` y `dosSedesActivas_altaDeGrupo_comportamientoActual` (D8-3, fija lo de hoy).
5. **D8: el gestor no da ni quita propietario ni gestor.**
   - E8-3: `gestorDaPropietario_403`, `gestorQuitaAdministrador_403`, `gestorDaProfesorYResponsable`.
6. **Último propietario.**
   - E8-3: `quitarUltimoPropietario_409` y `dosQuitasALaVez_quedaUnPropietario` (bloqueo D8-19). En el endpoint viejo,
     solo si se aprueba §11.1.
7. **Nada cruza escuelas.**
   - E8-3: `miembrosNoSeVenEntreEscuelas`, `fichaDeCuentaDeOtraEscuela_404`, `sedeDeOtraEscuela_400`,
     `grupoDeOtraEscuela_404`.
8. **Alta todo o nada y sin carreras.**
   - E8-3: `altaConUnGrupoAjeno_noCreaNada`, `dosAltasALaVezMismoEmail_sin500`, `darFamilia_400`,
     `rolDesactivadoSinReactivar_409`, `desactivarProfesorConservaAsignaciones_yReactivarLasRecupera`.
9. **Invitación sin toma de cuentas ni tokens filtrados.**
   - E8-4: `invitarEmailConCuenta_noTocaContrasenaNiEnviaCorreo`, `tokenDeInvitacionNoApareceEnAuditoriaNiLogs`,
     `reenviarInvalidaElTokenAnterior`, `reenviarSinPendiente_409`, `tokenCaducadoOUsado_rechazado`,
     `crearContrasenaIniciaSesionEnLaEscuela`, `cuentaPendienteNoEntraConContrasena`.
10. **Registro solo con ids y una fila por escritura.**
    - E8-2, E8-3, E8-4: `registroSoloConIds` (sin email, nombre, cargo, dirección, teléfono ni token),
      `cargoNoApareceEnElRegistro`, casos en `AuditoriaRequestFilterRegistroPropioTest` y en el IT contra MySQL, y
      `CLAVES_REDACTADAS_POR_RUTA` con las rutas nuevas.
11. **Frontend.**
    - E8-5: `misGruposGuard` deja pasar a gestión con `MIS_GRUPOS` y sin ella va a `/adminpage`; el enlace no sale sin
      la capacidad.
    - E8-6: «Añadir a N grupos» manda una sola petición; sin cuenta pide nombre y apellidos; los 409 y 404 muestran el
      mensaje del backend.
12. **Moiskimdo.** Los dorados pasan sin cambios en cada pieza backend.

---

## 1. Mapa de piezas

Orden: **E8-1 → E8-2 → E8-3 → E8-4 → E8-5 → E8-6**. E8-4 depende de E8-3; E8-5 de E8-1; E8-6 de E8-3 y E8-4.

Los nombres de esta tabla fijan los contratos entre piezas. Quien construye puede renombrarlos, pero entonces lo
anota en la PR y actualiza este mapa.

| Pieza | Objetivo | Decisiones | Depende de | Riesgo / ejecutor | Commit (orientativo) |
|---|---|---|---|---|---|
| **E8-1** backend | `GET /api/auth/permisos` (roles, `esAdminPlataforma`, sedes visibles, `variasSedes`, `tieneGrupos`, capacidades) y «Mis grupos» para P y G: alcance con grupos para P/G con `ROLE_PROFESOR`, `/api/profesor/**` y `@acceso.*DeMisGrupos` solo por `alcance.grupos()` | D8-14, D8-15 | — | Alto (permisos); `savvy-careful`, revisión opus | `feat(permisos): GET /api/auth/permisos y Mis grupos para propietario y gestor (E8-1)` |
| **E8-2** backend | API de sedes (`GET`, `GET {id}`, `POST`, `PUT {id}`, `PUT {id}/activa`) con registro (módulo `sedes`); documentar en la PR `sedePorDefecto` con dos sedes activas | D8-1, D8-2, D8-3, D8-16, D8-20 | — | Alto; `savvy-careful`, revisión opus | `feat(sedes): API de sedes con registro (E8-2)` |
| **E8-3** backend | API de miembros: lista paginada, ficha, alta de cuentas existentes con grupos y sedes, dar/reactivar/quitar roles con D8 y último propietario, cargo, sedes, grupos; registro (módulo `miembros`) | D8-1, D8-4 a D8-12, D8-16, D8-19, D8-20 | — | Alto (permisos, tablas sin `@TenantId`, concurrencia); `savvy-careful`, revisión opus | `feat(miembros): API de miembros con roles, cargo, sedes y grupos (E8-3)` |
| **E8-4** backend | Invitaciones: alta sin cuenta con nombre y apellidos, correo con la marca, token de 7 días, reenviar, crear contraseña e iniciar sesión | D8-13, D8-16, D8-19 | E8-3 | Alto (cuentas y tokens); `savvy-careful`, revisión opus | `feat(miembros): invitaciones por correo (E8-4)` |
| **E8-5** frontend | Servicio de permisos (`GET /api/auth/permisos`), `veMisGrupos` = `MIS_GRUPOS`, enlace «Mis grupos» en el menú de gestión y de cuenta móvil, `misGruposGuard` | D8-14, D8-15, §5.1 | E8-1 | Medio; `savvy-medium` | `feat(permisos): menú y guard de Mis grupos con la capacidad MIS_GRUPOS (E8-5)` |
| **E8-6** frontend | Retoque de «Profesores» (también en estos grupos, invitación, pendiente y reenviar, reactivar y añadir) y página «Crea tu contraseña» | D8-12, D8-13, §5.2, §5.3 | E8-3, E8-4 | Medio (visible para Moiskimdo); `savvy-medium` en opus | `feat(grupos): profesores en varios grupos e invitación desde la ficha (E8-6)` |

Pruebas mínimas de cada pieza backend: IT en MySQL con dos escuelas (cada una con su propietario), dos sedes y un
gestor; una cuenta P+Pr y otra G+Pr con asignaciones; cuentas nuevas por test con datos inventados; Moiskimdo idéntico;
matriz con diff solo en líneas nuevas.

**Migración:** ninguna prevista (V31_9 solo si E8-4 la necesita).

**Despliegue (lo decide el propietario al aprobar el plan).** Propuesta: una sola PR al final, como la E7, con su
línea en `checklist-despliegue.md` (con o sin migración según E8-4).

**Decisión 221 (20/10/2026):** D8-9 se aplica también a `PUT /api/admin/users/{id}/roles` (pantalla «Gestión de
Roles»): 409 si deja la escuela sin propietario activo. Lo hace E8-3, con su test en el endpoint viejo.

---

## 2. Pieza E8-1: `GET /api/auth/permisos` y «Mis grupos» para P y G (detallada)

Rama `feature/e8-1` desde `feature/e8-docs`. Sin migración: atajo `desplegar`. Una ruta nueva de solo lectura
(`GET /api/auth/permisos`) y un cambio de alcance; ninguna escritura nueva.

Criterios del constructor (no los fija el diseño; se anotan en la PR):
- **Alcance de P y G con `ROLE_PROFESOR`** (D8-15). `IdentificadorSesionJwt.alcanceDe`: con `ROLE_ADMIN` o
  `ROLE_MANAGER` y además `ROLE_PROFESOR`, la consulta de siempre (`AlcanceRepository.filasDeAlcance`, con
  `organizacion_id` explícito y membresía PROFESOR activa) y `AlcanceSesion(total = true, sedes = ∅, grupos = sus
  grupos)`: las sedes de un P+R no se guardan (el total ya lo ve todo). Gestión sin `ROLE_PROFESOR`: `TOTAL` sin
  consulta, como hoy. `AlcanceActual` devuelve el alcance guardado de una `AutenticacionEscuela` de gestión solo si es
  total (así lleva sus grupos); si no, `TOTAL` como hoy.
- **Un total con grupos no da más que un total.** Toda comprobación que concede con `total()` (`AccesoRecursos.decidir`,
  `GestionPorSede.restriccion`, `PredicadosAlcance`) concede igual que antes; las que miran solo grupos de la caja
  (`AccesoRecursos.cargoDeMisGrupos` y `alumnoDeMisGrupos`, `CajaService` y `EncargoService.gruposDelProfesor`) siguen
  exigiendo alcance no total: **Mi caja no cambia para nadie** (P+Pr y G+Pr siguen con la caja vacía y 403 al cobrar).
- **Rutas del profesor solo por `alcance.grupos()`.** `@acceso.grupoDeMisGrupos` deja de exigir `!total()` y mira solo
  los grupos. Como `@acceso.alumnoDeMisGrupos` lo comparten la caja y `/api/profesor/**`, las rutas del profesor pasan a
  `@acceso.alumnoDeMisGruposAsignados` (nuevo, solo grupos) y la caja conserva el suyo sin cambios. `GET
  /api/profesor/grupos` exige solo `ROLE_PROFESOR`; `ProfesorService.gruposDelProfesor()` devuelve `alcance.grupos()`
  sin atajo por total. R+Pr sigue filtrado solo por grupos.
- **Salud y pases de P/G por estas rutas**: los mismos servicios que el profesor (`AlumnoSaludService.obtenerComoProfesor`
  y `RegistroAccionesProfesor`, una fila por escritura, decisión 214; `EscrituraConRegistroPropio` sin cambios).
- **`GET /api/auth/permisos`** (D8-14): `PermisosSesionController` + `PermisosSesionService`, DTO
  `PermisosSesionDTO {roles, esAdminPlataforma, sedes[{id, nombre, activa}], variasSedes, tieneGrupos, capacidades}`.
  Roles: los `RolMembresia` de las autoridades de la sesión (sin consulta), por el orden del enum; en la sesión
  solo-profesor (también R+Pr), solo `PROFESOR`. Sedes: alcance total → todas las de la escuela, también inactivas
  (`SedeRepository.findAllByOrderByIdAsc`, `@TenantId`); `ROLE_RESPONSABLE_SEDE` fuera de la sesión solo-profesor → las
  suyas (`alcance.sedes()`); resto, ninguna. `variasSedes` = más de una sede activa en la escuela (de la lista ya
  cargada con total; si no, un `COUNT`). `tieneGrupos` = `alcance.grupos()` no vacío. Capacidades: `MIEMBROS`,
  `SEDES`, `ASIGNAR_PROFESORES` con `ROLE_ADMIN` o `ROLE_MANAGER`; `DAR_GESTION` con `ROLE_ADMIN`; `MIS_GRUPOS` en la
  sesión solo-profesor siempre y, fuera de ella, con `ROLE_PROFESOR` y grupos; `MI_CAJA` con `ROLE_PROFESOR`. Sesión
  solo-plataforma (sin autoridades de escuela): 200 con roles, sedes y capacidades vacíos, `esAdminPlataforma` y sin
  consultar la escuela. `@PreAuthorize` como `/api/auth/roles` más `ROLE_ADMIN_PLATAFORMA`; sin regla de URL nueva (cae
  en `authenticated()`); `/api/auth/**` no se audita. Sin N+1: reutiliza el alcance de la petición; como mucho dos
  consultas de sedes.

### Tarea 1: tests que fallan

**Ficheros:**
- Crear: `T/integracion/MisGruposGestionIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `PER_CLASS`,
  `extends MySqlIntegrationTest`; escuelas `EscuelaConSedesDePrueba.crear(…, "it-e8-1")` y `"it-e8-1-otra"`, datos
  inventados, sistema de grados y productos de grado propios). Cuentas: `propProf` (PROPIETARIO + PROFESOR de `gA`),
  `gestorProf` (ADMINISTRADOR + PROFESOR de `gB`), `gestorProfSinGrupos` (ADMINISTRADOR + PROFESOR sin asignaciones),
  propietario y gestor sin PROFESOR, `profOtra` (otra escuela).
- Crear: `T/integracion/PermisosSesionIntegrationTest.java` (mismas escuelas sintéticas, `"it-e8-1p"` y
  `"it-e8-1p-otra"`, esta con una sola sede activa).
- Crear: `T/services/PermisosSesionServiceTest.java` (reglas de capacidades, sin Spring).
- Modificar: `T/organizacion/IdentificadorSesionJwtTest.java`, `T/organizacion/AlcanceActualTest.java`,
  `T/acceso/AccesoRecursosTest.java`, `T/config/SesionSoloProfesorAuthorizationManagerTest.java`,
  `T/seguridad/MatrizPermisosTest.java` (`PROFESOR_SESION_180`), `src-api/src/test/resources/seguridad/matriz-permisos.txt`
  (2 líneas nuevas), y los tres IT de la E7 que daban 403 a G+Pr (`ProfesorGruposIntegrationTest`,
  `ProfesorGradosIntegrationTest`, `ProfesorSaludAsistenciaIntegrationTest`: G+Pr sale de los bucles de
  «gestión no usa las rutas del profesor»; P y G sin PROFESOR siguen en 403) y `AlcanceSesionIntegrationTest`
  (`identificarDejaElAlcanceEnLaSesion`: P+Pr pasa de `TOTAL` a total con su grupo; P sin PROFESOR, `TOTAL`).

- [x] **Paso 1: escribir los tests**:
  - IT `MisGruposGestionIntegrationTest`: `propietarioProfesor_soloSusGrupos` (lista = `gA`; alumnos, ficha y PDF de
    `gA` 200; `gB` y sus alumnos 403), `gestorProfesor_alumnoDeOtroGrupo_403` (alumno de otro grupo, sin grupo, de otra
    escuela o inexistente: 403; grupo de otra escuela: 403), `gestionProfesorSinAsignaciones_listaVacia` (lista vacía y
    403 en un grupo y en un alumno), `gestionProfesor_saludRegistradaIgual` (un evento `salud` con la ruta, el resumen
    «(profesor)» y su usuario; 403 sin evento), `gestionProfesor_paseRegistraUnaFila` (200, una sola fila
    `PROFESOR PASE_EXAMEN` con su usuario y sin la genérica; el grado no cambia), `gestionSinProfesor_sigueDenegada`
    (P y G sin PROFESOR: 403 en las rutas del profesor), `cajaProfesorSinCambios_gestionConProfesor` (P+Pr con grupos:
    alumnos y cargos de Mi caja vacíos, cobrar a un alumno de su grupo 403).
  - IT `PermisosSesionIntegrationTest`: `permisos_propietario` (todos los roles de la escuela, todas las sedes también
    la inactiva, `variasSedes`, MIEMBROS/DAR_GESTION/SEDES/ASIGNAR_PROFESORES), `permisos_gestor` (sin DAR_GESTION),
    `permisos_familia` (FAMILIA, sin sedes ni capacidades), `permisos_familiaResponsable_susSedes`,
    `permisos_familiaProfesor_misGruposYMiCaja`, `permisos_sesionSoloProfesor_soloProfesorYMisGrupos` (con y sin
    grupos: roles `[PROFESOR]`, MIS_GRUPOS y MI_CAJA, sin sedes), `permisos_responsableMasProfesor_sinSedes`,
    `permisos_propietarioProfesorConGrupos_misGrupos`, `permisos_gestorProfesorSinGrupos_sinMisGrupos`,
    `permisos_soloPlataforma_rolesVacios`, `permisos_anonimo_401`, `permisos_unaSedeActiva_variasSedesFalse`,
    `permisos_noCruzaEscuelas`, `permisos_noSeAudita`.
  - `IdentificadorSesionJwtTest`: `alcanceDeGestionSinProfesor_noConsulta` (sustituye a
    `gestionTieneAlcanceTotalSinConsultarElAlcance` sin el caso G+Pr+R), `alcanceDeGestionConProfesor_totalYSusGrupos`
    (una consulta; sin sedes), `alcanceDeGestionConProfesorSinAsignaciones_totalSinGrupos`.
  - `AlcanceActualTest`: `gestionConAlcanceTotalGuardadoConservaSusGrupos`.
  - `AccesoRecursosTest`: `grupoDeMisGrupos_gestionConProfesor_soloSusGrupos`,
    `alumnoDeMisGruposAsignados_soloSusGrupos_nuncaPorTotalNiPorSede`, `cajaDelProfesor_totalConGrupos_noConcede`,
    `decidir_totalConGrupos_igualQueTotal`.
  - `PermisosSesionServiceTest`: `capacidades_porRol`, `capacidades_sesionSoloProfesorSiempreMisGrupos`.
  - `SesionSoloProfesorAuthorizationManagerTest`: `GET`/`HEAD /api/auth/permisos` pasan a la regla de siempre;
    `POST /api/auth/permisos` no pasa.
  - Matriz: `GET`/`HEAD /api/auth/permisos` (ANONIMO y RESPONSABLE_SEDE `DENIEGA`, resto `PERMITE`), en
    `PROFESOR_SESION_180` con su porqué.
- [x] **Paso 2: comprobar que fallan.** `mvn test -Dtest='MisGruposGestionIntegrationTest,PermisosSesionIntegrationTest,PermisosSesionServiceTest,IdentificadorSesionJwtTest,AlcanceActualTest,AccesoRecursosTest,SesionSoloProfesorAuthorizationManagerTest,MatrizPermisosTest' -DexcludedGroups=migracion`:
  no compila hasta crear el servicio, el DTO y `alumnoDeMisGruposAsignados`; con los esqueletos, las rutas dan 403/404.

### Tarea 2: implementar

**Ficheros:**
- Modificar: `J/organizacion/IdentificadorSesionJwt.java` (`alcanceDe`), `J/organizacion/AlcanceActual.java`.
- Modificar: `J/acceso/AccesoRecursos.java` (`grupoDeMisGrupos` y `alumnoDeMisGruposAsignados` por `soloPorMisGrupos`;
  `comoProfesor` de la caja sin cambios).
- Modificar: `J/controllers/ProfesorController.java` (`@PreAuthorize` sin la exclusión; `alumnoDeMisGruposAsignados`),
  `J/services/ProfesorService.java` (`gruposDelProfesor`).
- Modificar: `J/config/SesionSoloProfesorAuthorizationManager.java` (`GET /api/auth/permisos`),
  `J/config/SecurityConfiguration.java` (solo el comentario de `/api/profesor/**`).
- Crear: `J/controllers/PermisosSesionController.java`, `J/services/PermisosSesionService.java`,
  `J/dtos/response/PermisosSesionDTO.java`. Modificar: `J/repositories/SedeRepository.java` (`countByActivaTrue`).

**Interfaces:**
- Produce: `GET /api/auth/permisos` → `PermisosSesionDTO`; capacidades `MIEMBROS`, `DAR_GESTION`, `SEDES`,
  `ASIGNAR_PROFESORES`, `MIS_GRUPOS`, `MI_CAJA` (contrato de E8-5); `@acceso.alumnoDeMisGruposAsignados(Long)`.
- Consume: `AlcanceActual.obtener()`, `RolMembresia.sesionSoloProfesor`, `SedeRepository`.

- [x] **Paso 1:** alcance de P/G con PROFESOR y `AlcanceActual`; tests de unidad en verde.
- [x] **Paso 2:** `@acceso`, controlador y servicio del profesor; IT de P+Pr y G+Pr en verde y los de la E7 ajustados.
- [x] **Paso 3:** `GET /api/auth/permisos`, lista blanca y matriz; sus tests en verde.

### Tarea 3: matriz, documentación y batería

- [x] `docs/multi-escuela/matriz-endpoints.md`: nota de las 2 líneas, recuento de la cabecera y la línea de P+Pr/G+Pr en
  la sección del profesor.
- [x] Los IT de la E7, `AuditoriaRequestFilterRegistroPropioTest`, `RegistroUnicoProfesorIntegrationTest`,
  `SecurityAuthorizationWebMvcTest` y `mvn test -DexcludedGroups=migracion` en `src-api`, en primer plano, en verde;
  dorados de Moiskimdo sin tocar.
- [x] `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`.

---

## 3. Pieza E8-2: API de sedes con registro (detallada)

Rama `feature/e8-2` desde `feature/e8-1`. Sin migración: atajo `desplegar`. Cinco rutas nuevas bajo `/api/sedes`
(dos lecturas y tres escrituras); ningún cambio en las rutas de hoy.

Criterios del constructor (no los fija el diseño; se anotan en la PR):
- **Rutas y permisos** (D8-1, D8-2). `SedeController` en `/api/sedes`: `GET`, `GET /{id}`, `POST` (201), `PUT /{id}` y
  `PUT /{id}/activa {activa}`. Regla de URL `/api/sedes`, `/api/sedes/**` con
  `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')` justo después de `OPTIONS /api/**` (antes de toda regla del
  responsable de sede) y el mismo `@PreAuthorize` en cada método. Fuera de la lista blanca de la sesión solo-profesor.
  R, Pr, F, SOLO_PLATAFORMA y ANONIMO `DENIEGA`.
- **Lecturas.** La lista: todas las sedes de la escuela activa, activas e inactivas, por id (`findAllByOrderByIdAsc`,
  `@TenantId`). La ficha: `SedeRepository.buscarEnEscuelaActiva`; de otra escuela o inexistente, 404 «La sede no
  existe.». DTO `SedeDTO {id, nombre, direccion, localidad, codigoPostal, telefono, email, codigoClubFederacion,
  aforoTurno, activa}` (`aforoTurno` `null` = límite de la escuela). Sin contadores.
- **Alta y edición.** Cuerpo `SedeRequest` con los campos editables menos `activa` (la sede nueva nace activa). `PUT`
  es un reemplazo: lo que no llega queda `null`. Cadenas recortadas; las opcionales en blanco pasan a `null`.
  Longitudes en caracteres (puntos de código, como `VARCHAR` en utf8mb4): nombre obligatorio ≤100, dirección ≤255,
  localidad ≤100, código postal ≤10, teléfono ≤20, email ≤180 y con formato válido, código de club ≤40; `aforoTurno`
  `null` o ≥1. Fallo: 400 `{"mensaje"}`.
- **Nombre repetido** (UNIQUE `organizacion_id, nombre`): 409 «Ya hay una sede con ese nombre.». Se compara en la
  base (`existsByNombre` / `existsByNombreAndIdNot`, con la colación de la columna: sin distinguir mayúsculas ni
  tildes, igual que la clave única); renombrar una sede a su propio nombre con otras mayúsculas vale. Dos altas a la
  vez con el mismo nombre: la clave única decide y el `DataIntegrityViolationException` de
  `uk_sede_organizacion_nombre` se convierte en el mismo 409 (nunca 500); otro fallo de integridad se propaga.
- **Activar y desactivar.** `@Transactional(isolation = READ_COMMITTED)`. Primero se bloquean TODAS las sedes de la
  escuela (`SedeRepository.bloquearDeLaEscuela`, `PESSIMISTIC_WRITE`, filtrada por `@TenantId`, por id); con las filas
  bloqueadas se busca la sede (404 si no está) y se cuentan las activas. Desactivar la última activa: 409 «La escuela
  se quedaría sin sedes activas.». Dos desactivaciones a la vez esperan una a la otra y la segunda ve la primera ya
  confirmada: queda una activa. Desactivar no toca `membresia_sede` (D5-5): el responsable conserva la sede en su
  alcance. Sin `DELETE` (FK RESTRICT desde grupo, tarifa_sede, entrega_caja y movimiento_caja; el historial queda).
- **Registro** (D8-16, §7). `RegistroAccionesSedes` (módulo `sedes`), copia de `RegistroAccionesProfesor`: misma
  transacción (`registrarAccesoSensible` es `MANDATORY`), resumen solo con ids, enumeraciones y sí/no; cualquier otro
  tipo, `IllegalStateException`. Acciones `SEDE_CREADA` (CREATE), `SEDE_EDITADA`, `SEDE_DESACTIVADA` y
  `SEDE_ACTIVADA` (UPDATE), con `sede=#id`. Nunca nombre, dirección, teléfono ni email.
- **Una fila por escritura.** Todo 2xx de las tres escrituras registra: un `PUT` que no cambia nada (mismos datos, o
  `activa` igual a la actual) responde 200 y registra su acción con `sinCambios=SI`
  (`SEDES SEDE_DESACTIVADA sede=#5 sinCambios=SI`). Así las tres entran en `EscrituraConRegistroPropio`
  (`SEDE_CREAR`, `SEDE_EDITAR`, `SEDE_ACTIVA`) y sus 4xx los apunta el filtro genérico como siempre.
- **Cuerpos redactados.** `CLAVES_REDACTADAS_POR_RUTA` para `/api/sedes` y `/api/sedes/{id}`: `nombre`, `direccion`,
  `localidad`, `codigoPostal`, `telefono`, `email` y `codigoClubFederacion` (las filas 4xx del filtro genérico no los
  guardan).
- **D8-3: dos sedes activas.** No se cambia. Un IT fija lo de hoy en una escuela sintética propia y el resultado va
  en la PR (alta de grupo y de alumno sin sede por la gestión).

### Tarea 1: tests que fallan

**Ficheros:**
- Crear: `T/integracion/SedesApiIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `PER_CLASS`,
  `extends MySqlIntegrationTest`). Escuelas `EscuelaConSedesDePrueba.crear(…, "it-e8-2")` (A y B activas, C
  inactiva), `"it-e8-2-otra"` y `"it-e8-2-d83"` (solo para D8-3: B se cierra al empezar, queda A); escuelas mínimas
  por SQL con una o dos sedes para la última activa y la concurrencia. Cuentas nuevas, datos inventados, nombres con
  la marca de la escuela; nada en Moiskimdo.
- Crear: `T/services/RegistroAccionesSedesTest.java` (sin Spring).
- Crear: `T/config/AuditoriaRequestFilterClavesRedactadasTest.java`.
- Modificar: `T/config/AuditoriaRequestFilterRegistroPropioTest.java` (ejemplos de las tres entradas y rutas vecinas
  fuera de la lista), `T/integracion/RegistroUnicoProfesorIntegrationTest.java` (un caso por entrada) y
  `src-api/src/test/resources/seguridad/matriz-permisos.txt` (7 líneas nuevas).

- [x] **Paso 1: escribir los tests**:
  - IT `SedesApiIntegrationTest`:
    - `listarSedes_soloDeSuEscuela` (A, B y C de la escuela, también la inactiva con `activa=false`; ninguna de la
      otra; orden por id; claves exactas del DTO).
    - `sedeDeOtraEscuela_404` (sede de la otra escuela e id inexistente: 404 «La sede no existe.» en `GET`, `PUT` y
      `PUT …/activa`; la de la otra escuela no cambia).
    - `crearSede_valida_201_yRegistra` (201, campos recortados, opcionales en blanco a `null`, `activa=true`, fila en
      la escuela activa; una sola fila de auditoría `SEDES SEDE_CREADA sede=#id`, módulo `sedes`, del propietario y
      sin la genérica).
    - `nombreRepetidoEnLaEscuela_409` (alta con el nombre de A con otras mayúsculas y espacios, y renombrar B como A:
      409 con el mensaje; renombrar A con otras mayúsculas: 200).
    - `mismoNombreEnOtraEscuela_ok`.
    - `validaciones_400` (cada campo por encima de su límite, email sin formato, aforo 0, nombre vacío o ausente; en
      `POST` y en `PUT`; nada se crea ni cambia).
    - `editarSede_registraUnaFila` (200, datos cambiados, una fila `SEDES SEDE_EDITADA sede=#id`; repetir el mismo
      cuerpo: 200 y una fila con `sinCambios=SI`).
    - `desactivarUltimaSedeActiva_409` (escuela con una activa: 409 con el mensaje, sigue activa, sin fila
      explícita).
    - `desactivarConOtraActiva_ok_yRegistra` (200, `activa=false`, una fila `SEDE_DESACTIVADA`; repetir: 200 con
      `sinCambios=SI`).
    - `reactivar_ok` (`SEDE_ACTIVADA`).
    - `dosDesactivacionesALaVez_quedaUnaActiva` (escuela con dos activas, las dos desactivaciones a la vez, varias
      rondas: estados `[200, 409]` y siempre una activa).
    - `dosAltasALaVezMismoNombre_sin500` (`[201, 409]`, una sola sede con ese nombre).
    - `desactivarSedeConservaResponsables` (la fila de `membresia_sede` sigue y `GET /api/auth/permisos` del
      responsable la sigue listando, con `activa=false`).
    - `responsableYProfesorYFamilia_403` (también SOLO_PLATAFORMA; anónimo 401; nada se crea).
    - `gestorPuede` (las cinco rutas; la fila lleva al gestor).
    - `registroSoloConIds` (ninguna fila de la escuela posterior al test contiene nombre, dirección, teléfono ni
      email de la sede).
    - `clavesRedactadas_4xx` (la fila genérica de un 400 de `POST` y de `PUT` guarda `[redactado]` en esas claves y
      no los valores).
    - `dosSedesActivas_altaDeGrupo_comportamientoActual` y `dosSedesActivas_altaDeAlumno_comportamientoActual` (D8-3:
      con una activa, el grupo y el alumno sin sede reciben esa sede; tras crear otra por la API, la gestión sigue
      dando de alta sin sede, 201, con `sede_id` / `sede_principal_id` NULL; con sede explícita, la pedida; al
      desactivar la nueva, vuelve la sede por defecto).
  - `RegistroAccionesSedesTest`: `resumenSoloConIds` (`SEDES SEDE_CREADA sede=#5`, recurso 5, acción CREATE),
    `sinCambiosComoSiNo`, `textoLibre_IllegalStateException` (un `String` no se registra).
  - `AuditoriaRequestFilterClavesRedactadasTest`: `sedes_altaYEdicion_redactanSusDatos` (también con barra final y
    mayúsculas), `sedes_activa_noRedacta`, `rutasDeSiempre_igual`.
  - `AuditoriaRequestFilterRegistroPropioTest`: ejemplos `POST /api/sedes`, `PUT /api/sedes/5`,
    `PUT /api/sedes/5/activa`; fuera: `GET /api/sedes`, `GET /api/sedes/5`, `POST /api/sedes/5`,
    `PUT /api/sedes/5/activa/otra`, `DELETE /api/sedes/5`.
  - `RegistroUnicoProfesorIntegrationTest`: casos `SEDE_CREAR` (201 / nombre repetido 409), `SEDE_EDITAR` (200 / sin
    nombre 400) y `SEDE_ACTIVA` (200 / sin `activa` 400).
  - Matriz: `GET`/`HEAD /api/sedes`, `GET`/`HEAD /api/sedes/{id}`, `POST /api/sedes`, `PUT /api/sedes/{id}` y
    `PUT /api/sedes/{id}/activa`: GESTOR y PROPIETARIO `PERMITE`, resto `DENIEGA`.
- [x] **Paso 2: comprobar que fallan.** `mvn test -Dtest='SedesApiIntegrationTest,RegistroAccionesSedesTest,AuditoriaRequestFilterClavesRedactadasTest,AuditoriaRequestFilterRegistroPropioTest,RegistroUnicoProfesorIntegrationTest,MatrizPermisosTest' -DexcludedGroups=migracion`:
  no compila hasta crear `RegistroAccionesSedes` y las entradas del enum; con los esqueletos, las rutas dan 404/403.

### Tarea 2: implementar

**Ficheros:**
- Crear: `J/controllers/SedeController.java`, `J/services/GestionSedesService.java`,
  `J/services/RegistroAccionesSedes.java`, `J/dtos/request/SedeRequest.java`, `J/dtos/request/SedeActivaRequest.java`,
  `J/dtos/response/SedeDTO.java`.
- Modificar: `J/repositories/SedeRepository.java` (`bloquearDeLaEscuela`, `existsByNombre`, `existsByNombreAndIdNot`),
  `J/config/SecurityConfiguration.java` (regla de `/api/sedes/**`), `J/config/AuditoriaRequestFilter.java` (tres
  entradas y `CLAVES_REDACTADAS_POR_RUTA`).

**Interfaces:**
- Produce: `/api/sedes/**` (contrato de la pantalla Sedes de la E9), `SedeDTO`, módulo de registro `sedes`.
- Consume: `SedeRepository`, `AuditoriaService#registrarAccesoSensible`, `TenantContext`.

- [x] **Paso 1:** `RegistroAccionesSedes` y sus tests en verde.
- [x] **Paso 2:** servicio, controlador y regla de URL; IT de sedes en verde.
- [x] **Paso 3:** `EscrituraConRegistroPropio`, `CLAVES_REDACTADAS_POR_RUTA` y matriz; sus tests en verde.

### Tarea 3: matriz, documentación y batería

- [x] `docs/multi-escuela/matriz-endpoints.md`: nota de las 7 líneas y recuento de la cabecera.
- [x] `MatrizPermisosTest`, `AuditoriaRequestFilterRegistroPropioTest`, `RegistroUnicoProfesorIntegrationTest`,
  `SecurityAuthorizationWebMvcTest` y `mvn test -DexcludedGroups=migracion` en `src-api`, en primer plano, en verde;
  dorados de Moiskimdo sin tocar.
- [x] `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`.

---

## 4. Pieza E8-3 (la detalla su implementador con TDD antes de empezar)

Rama `feature/e8-3`. Alcance:
- `GET /api/miembros` (paginado, búsqueda en servidor, sin familias por defecto) y `GET /api/miembros/{usuarioId}`.
- `POST /api/miembros` para cuentas existentes (sin cuenta: 404 hasta E8-4), con grupos o sedes, todo o nada.
- `PUT/DELETE /api/miembros/{usuarioId}/roles/{rol}` con cargo, D8-8 y D8-9 (bloqueo de las filas PROPIETARIO).
- `PUT /api/miembros/{usuarioId}/sedes` y `PUT /api/miembros/{usuarioId}/grupos`.
- Registro módulo `miembros` y acciones existentes del módulo `profesor`; `CLAVES_REDACTADAS_POR_RUTA`.
- Puntos de revisión 5-8 y 10; matriz y nota.

---

## 5. Pieza E8-4 (la detalla su implementador con TDD antes de empezar)

Rama `feature/e8-4`. Alcance:
- Comprobar primero si `usuario.contrasena` admite NULL y si el login rechaza una cuenta sin contraseña; si no, V31_9
  aditiva y parar a preguntar.
- Alta sin cuenta en `POST /api/miembros` (con nombre y apellidos): cuenta LOCAL pendiente + membresía + asignaciones
  + invitación.
- Token de un solo uso, 7 días (mecanismo de restablecer contraseña o uno propio; al constructor), fuera del registro y
  de los logs.
- Correo con `MarcaEscuelaService` y texto según el rol; `POST /api/miembros/{usuarioId}/invitacion` para reenviar.
- `invitacionPendiente` en la respuesta de `GET /api/grupos/{grupoId}/profesores` (campo añadido; el resto del JSON
  igual), para el chip de E8-6.
- Crear la contraseña inicia sesión en esa escuela; comportamiento con Google comprobado y anotado.
- Ruta pública nueva solo si hace falta (`permitAll`, ANONIMO PERMITE).
- Puntos de revisión 9 y 10; matriz y nota.

---

## 6. Pieza E8-5 (la detalla su implementador con TDD antes de empezar)

Rama `feature/e8-5`. Alcance:
- Servicio de permisos en el frontend (`GET /api/auth/permisos`), pedido una vez y compartido.
- `veMisGrupos` = capacidad `MIS_GRUPOS`; enlace «Mis grupos» bajo «Mi caja» en el menú de gestión y en el menú de
  cuenta móvil (§5.1).
- `misGruposGuard`: gestión con `MIS_GRUPOS` pasa; sin ella, `/adminpage`.
- Specs en las dos listas de Karma; punto de revisión 11; build sin aviso y capturas a 375 y 1280 px.

---

## 7. Pieza E8-6 (la detalla su implementador con TDD antes de empezar)

Rama `feature/e8-6`. Alcance:
- «Profesores» de la ficha del grupo: «También en estos grupos» y «Añadir a N grupos» con `POST /api/miembros`
  (D8-12); cambiar rol y quitar siguen con las rutas de la E7-4.
- Sin cuenta: aviso, nombre, apellidos y «Enviar invitación»; rol desactivado: «Reactivar y añadir».
- Chip «Invitación pendiente» y «Reenviar».
- Página «Crea tu contraseña» reutilizando la de restablecer; enlace caducado o usado con «Pide que te la reenvíen».
- Specs en las dos listas de Karma; punto de revisión 11; build sin aviso y capturas a 375 y 1280 px.
