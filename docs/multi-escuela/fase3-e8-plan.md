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

**Migración:** V31_9 (E8-4, decisión 223: `usuario.contrasena` admite NULL); el despliegue de la E8 va con
`desplegar_migrar`.

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

## 4. Pieza E8-3: API de miembros con roles, cargo, sedes y grupos (detallada)

Rama `feature/e8-3` desde `feature/e8-2`. Sin migración: atajo `desplegar`. Siete rutas nuevas bajo `/api/miembros`
(dos lecturas y cinco escrituras) y un cambio en una ruta de hoy: `PUT /api/admin/users/{userId}/roles` da 409 si deja
la escuela sin propietario activo (decisión 221). Sin invitaciones (E8-4): sin cuenta, 404.

Criterios del constructor (no los fija el diseño; se anotan en la PR):
- **Rutas y permisos** (D8-1). `MiembroController` en `/api/miembros`: `GET`, `GET /{usuarioId}`, `POST`,
  `PUT /{usuarioId}/roles/{rol}`, `DELETE /{usuarioId}/roles/{rol}`, `PUT /{usuarioId}/sedes` y
  `PUT /{usuarioId}/grupos`. Regla de URL `/api/miembros`, `/api/miembros/**` con
  `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')` junto a la de `/api/sedes` y el mismo `@PreAuthorize` en cada método.
  Fuera de la lista blanca de la sesión solo-profesor. R, Pr, F, SOLO_PLATAFORMA y ANONIMO `DENIEGA`. Errores
  `{"mensaje"}` (D8-20).
- **Quién es miembro.** Igual que `MembresiaService.usuariosDeOrganizacion`/`esUsuarioDeOrganizacion`: cuenta con alguna
  membresía (activa o no) en la escuela activa y, solo en la escuela por defecto, también la cuenta legado sin ninguna
  membresía (sale sin roles, solo con `incluirFamilias=true`, y su ficha responde). Nunca una cuenta que solo es de
  otra escuela: ficha, roles, sedes y grupos dan 404 «La cuenta no es miembro de esta escuela.».
- **Lista** (D8-4). `GET /api/miembros?q&rol&incluirFamilias=false&page=0&size=20`, paginada en el servidor
  (`page` desde 0; `size` de 1 a 100, si no 400), orden apellidos, nombre, id. Por defecto solo personal (alguna
  membresía no FAMILIA en la escuela, activa o no); `incluirFamilias=true` añade familias y legado. `rol` filtra por
  tener una membresía de ese rol (activa o no; se combina con lo anterior: `rol=FAMILIA` sin `incluirFamilias` da el
  personal que también es familia); `rol` no válido, 400. `q` (recortada, ≤100) busca sin distinguir mayúsculas en
  nombre, apellidos, «nombre apellidos» y email, con `%` y `_` literales. Respuesta
  `{contenido[], pagina, tamano, total, totalPaginas}`; fila `{usuarioId, nombre, apellidos, email, authProvider,
  roles[{rol, activa, cargo}] (orden del enum), sedes[{id, nombre}] (las de su RESPONSABLE_SEDE, por id),
  numeroGrupos}` (asignaciones en la escuela, también con PROFESOR desactivado). Sin `invitacionPendiente` (E8-4).
  Sin N+1: página + recuento, membresías de la página, sedes de sus responsables y número de grupos: cinco consultas
  por página, sea cual sea su tamaño.
- **Ficha** (D8-5). `GET /api/miembros/{usuarioId}`: la fila más `grupos[{grupoId, nombre, sedeId, rol}]` (por nombre e
  id del grupo). También la devuelven las escrituras (200).
- **Alta de cuenta existente** (D8-6). `POST /api/miembros {email, rol, cargo?, sedeIds?, grupos?[{grupoId, rol}],
  reactivar?}`, `READ_COMMITTED`, una transacción. Orden: cuerpo (email vacío, rol que no es PROPIETARIO, ADMINISTRADOR,
  RESPONSABLE_SEDE ni PROFESOR, `sedeIds` sin RESPONSABLE_SEDE, `grupos` sin PROFESOR, repetidos o nulos, cargo >100:
  400; FAMILIA: 400 «La familia se vincula desde la ficha del alumno.»), D8-8 (403), cuenta por email global como la
  E7-4 (sin cuenta: 404 «No hay ninguna cuenta con ese email.»), bloqueo de la fila de la cuenta, sedes de la escuela
  (400 «La sede no existe en la escuela.»), grupos de la escuela (404 «El grupo no existe.»), y luego la membresía del
  rol: falta, se crea activa; desactivada, 409 «La cuenta tiene el rol de {propietario | gestor | responsable de sede |
  profesor} desactivado en esta escuela.» salvo `reactivar=true`; activa, se conserva. RESPONSABLE_SEDE: el rol que nace
  o se reactiva necesita sedes, las de `sedeIds` o las que ya tenía guardadas (si no, 400 «Indica al menos una sede.»);
  `sedeIds`, si llega, no vacío y reemplaza sus sedes. PROFESOR: `grupos` **añade** asignaciones (en un grupo ya
  asignado cambia el rol). Dos altas a la vez del mismo email: la segunda espera el bloqueo y ve la primera (200 y 200,
  una membresía; nunca 500).
- **Cargo.** Texto libre de la fila del rol, ≤100 caracteres, recortado. Campo ausente o `null`: se conserva; en blanco
  (`""`): se borra. Mismo criterio en `POST` y en `PUT …/roles/{rol}`. Nunca en el registro.
- **Dar o reactivar** (D8-7). `PUT /api/miembros/{usuarioId}/roles/{rol} {cargo?, sedeIds?}` (cuerpo opcional): falta,
  se crea; desactivada, se reactiva; activa, solo el cargo. `sedeIds` solo con RESPONSABLE_SEDE (si no, 400) y
  reemplaza sus sedes; dar o reactivar RESPONSABLE_SEDE sin sedes guardadas ni `sedeIds`, 400 «Indica al menos una
  sede.» (así se da el rol a un miembro sin pasar por `POST`). Reactivar PROFESOR recupera sus asignaciones.
- **Quitar** (D8-7). `DELETE /api/miembros/{usuarioId}/roles/{rol}`: `activa=false`, nunca borra la fila; conserva
  asignaciones y sedes (el alcance exige membresía activa). Sin esa membresía, 404 «La cuenta no tiene ese rol en esta
  escuela.». FAMILIA en `PUT` o `DELETE`: 400; `{rol}` desconocido: 400.
- **D8-8.** Con `ROLE_MANAGER` y sin `ROLE_ADMIN` (ADMIN gana): `POST` con PROPIETARIO o ADMINISTRADOR, `DELETE` de
  esos roles y `PUT` que los da o reactiva → 403 «Solo el propietario da o quita los roles de propietario y gestor.»
  (sin tocar nada). El `PUT` sobre esos roles ya activos que solo cambia el cargo sí lo hace el gestor (el cargo es de
  cualquiera, §6.1). El gestor gestiona RESPONSABLE_SEDE, PROFESOR, sedes y grupos de cualquiera.
- **Último propietario** (D8-9, D8-19, decisión 221). Quitar PROPIETARIO bloquea antes las filas PROPIETARIO activas de
  la escuela (`MembresiaRepository.bloquearPropietariosActivos`, `organizacion_id` explícito, `FOR UPDATE`, por id;
  `READ_COMMITTED`) y cuenta: si es la última, 409 «La escuela se quedaría sin propietario.». Dos quitas a la vez de dos
  propietarios distintos se ordenan y la segunda ve la primera confirmada: queda uno. `PUT
  /api/admin/users/{id}/roles` (`MembresiaServiceImpl.reemplazarAutoridades`, ahora `READ_COMMITTED`) bloquea igual al
  empezar y da el mismo 409, sin cambiar nada, si el conjunto pedido desactiva la última PROPIETARIO activa; una escuela
  que ya no tiene propietario no se bloquea por eso (el 409 es para quien la deja sin él). Todo lo demás de esa ruta,
  igual que hoy (también su fila genérica de auditoría).
- **Sedes del responsable** (D8-10). `PUT /api/miembros/{usuarioId}/sedes {sedeIds}`: exige membresía RESPONSABLE_SEDE
  (activa o no; si no, 409 «La cuenta no es responsable de sede en esta escuela.»); lista vacía o ausente, 400 «Indica
  al menos una sede.»; repetidas o nulas, 400; cada sede, de la escuela activa (consulta sobre `Sede`, `@TenantId` y
  además `organizacion_id`), si no 400 «La sede no existe en la escuela.». Reemplaza las filas de `membresia_sede`
  (borrado acotado por la membresía de esa escuela).
- **Grupos del profesor** (D8-11). `PUT /api/miembros/{usuarioId}/grupos {asignaciones[{grupoId, rol}]}` reemplaza el
  conjunto en esta escuela, todo o nada: exige PROFESOR activa (409 «La cuenta no tiene el rol de profesor activo en
  esta escuela.»); grupo ajeno o inexistente, 404 «El grupo no existe.»; grupo repetido, 400 «Hay grupos repetidos.»;
  rol distinto de TITULAR o AYUDANTE, 400. Lista vacía: quita todas (la membresía queda, D7-21). Toda consulta y
  borrado de `asignacion_profesor` con `organizacion_id`.
- **Registro** (D8-16, §7). `RegistroAccionesMiembros` (módulo `miembros`), copia de `RegistroAccionesSedes`: misma
  transacción, solo ids (también listas de ids, `sedes=#3,#4`), enumeraciones y sí/no; otro tipo,
  `IllegalStateException`. `ROL_DADO` (CREATE), `ROL_REACTIVADO`, `ROL_QUITADO`, `CARGO_CAMBIADO`, `SEDES_CAMBIADAS`
  y `GRUPOS_CAMBIADOS` (UPDATE), con `usuario=#id rol=ROL` (`SEDES_CAMBIADAS`: `usuario=#id sedes=#…`).
  Asignaciones con las acciones del módulo `profesor` (`ASIGNACION_CREADA`, `ASIGNACION_ROL_CAMBIADO`,
  `ASIGNACION_QUITADA`). Un `POST` o `PUT …/roles`
  que no cambia nada registra `ROL_DADO … sinCambios=SI`; un `DELETE` de un rol ya desactivado, `ROL_QUITADO …
  sinCambios=SI`; un `PUT …/sedes` igual, `SEDES_CAMBIADAS … sinCambios=SI`; un `PUT …/grupos` con el mismo conjunto,
  `GRUPOS_CAMBIADOS usuario=#id sinCambios=SI`. Así las cinco escrituras entran en `EscrituraConRegistroPropio`
  (`MIEMBRO_ALTA`, `MIEMBRO_ROL_DAR`, `MIEMBRO_ROL_QUITAR`, `MIEMBRO_SEDES`, `MIEMBRO_GRUPOS`): una fila por escritura
  (decisión 214), sin la genérica en el 2xx.
- **Cuerpos y parámetros redactados.** `CLAVES_REDACTADAS_POR_RUTA`: `/api/miembros` (`email`, `cargo`, y ya `nombre` y
  `apellidos` para la E8-4) y `/api/miembros/{id}/roles/{rol}` (`cargo`). `PARAMETROS_REDACTADOS_POR_RUTA` (nuevo, solo
  esta ruta): `q` de `GET /api/miembros` se guarda como `[redactado]` en `query_params_json` (también en los 400). Las
  búsquedas de otras rutas (`GET /api/alumnos?nombre`, `GET /api/preinscripciones?q`) siguen como hoy: deuda aparte.
- **Página.** `page × size` por encima de `Integer.MAX_VALUE`: 400 «La página no es válida.» (antes de llegar a la
  base).

### Tarea 1: tests que fallan

**Ficheros:**
- Crear: `T/integracion/MiembrosApiIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `PER_CLASS`,
  `extends MySqlIntegrationTest`). Escuelas `EscuelaConSedesDePrueba.crear(…, "it-e8-3")` y `"it-e8-3-otra"`; escuelas
  mínimas por SQL con sus propietarios para el último propietario y la concurrencia. Cuentas nuevas por test con la
  marca en el email, datos inventados; nada en Moiskimdo.
- Crear: `T/services/RegistroAccionesMiembrosTest.java` (sin Spring).
- Modificar: `T/config/AuditoriaRequestFilterRegistroPropioTest.java` (cinco entradas y rutas vecinas fuera),
  `T/config/AuditoriaRequestFilterClavesRedactadasTest.java`, `T/integracion/RegistroUnicoProfesorIntegrationTest.java`
  (un caso por entrada) y `src-api/src/test/resources/seguridad/matriz-permisos.txt` (9 líneas nuevas).

- [x] **Paso 1: escribir los tests**:
  - IT `MiembrosApiIntegrationTest`: `listaPorDefectoSinFamilias`, `listaConFamilias`, `filtroPorRol`,
    `busquedaYPaginacionEnServidor` (y el número de sentencias por página no crece con el tamaño),
    `miembrosNoSeVenEntreEscuelas`, `fichaDeCuentaDeOtraEscuela_404`, `fichaConGruposYSedes`,
    `altaCuentaExistente_profesorConVariosGrupos_todoONada`, `altaConUnGrupoAjeno_noCreaNada`,
    `altaResponsableConSedes`, `altaSinCuenta_404`, `darFamilia_400`, `validaciones_400`,
    `rolDesactivadoSinReactivar_409`, `reactivar_ok`, `dosAltasALaVezMismoEmail_sin500`, `gestorDaPropietario_403`,
    `gestorQuitaAdministrador_403`, `gestorDaProfesorYResponsable`, `gestorCambiaCargoDeUnGestorActivo`,
    `darRolPorPut_yCargo`, `responsableSinSedesPorPut_400`, `quitarUltimoPropietario_409`,
    `dosQuitasALaVez_quedaUnPropietario`, `endpointViejo_dejarSinPropietario_409`,
    `endpointViejo_conOtroPropietario_comoHoy`, `sedeDeOtraEscuela_400`, `sedesVacias_400`,
    `sedesSinRolDeResponsable_409`, `grupoDeOtraEscuela_404`, `reemplazarGrupos_todoONada`,
    `grupos_sinProfesorActivo_409`, `desactivarProfesorConservaAsignaciones_yReactivarLasRecupera`,
    `cargoNoApareceEnElRegistro`, `registroSoloConIds`, `unaFilaPorEscritura`, `busquedaNoQuedaEnElRegistro`,
    `clavesRedactadas_4xx`,
    `responsableProfesorFamiliaYPlataforma_403`.
  - `RegistroAccionesMiembrosTest`: `resumenSoloConIds`, `listaDeIds`, `sinCambiosComoSiNo`,
    `textoLibre_IllegalStateException`.
  - `AuditoriaRequestFilterRegistroPropioTest`: ejemplos `POST /api/miembros`, `PUT /api/miembros/21/roles/PROFESOR`,
    `DELETE /api/miembros/21/roles/PROFESOR`, `PUT /api/miembros/21/sedes`, `PUT /api/miembros/21/grupos`; fuera:
    `GET /api/miembros`, `GET /api/miembros/21`, `POST /api/miembros/21/grupos`, `POST /api/miembros/21`,
    `PUT /api/miembros/21/roles`.
  - `AuditoriaRequestFilterClavesRedactadasTest`: `miembros_altaYRoles_redactanEmailYCargo`, `miembros_resto_noRedacta`,
    `miembros_q_redactada`.
  - `RegistroUnicoProfesorIntegrationTest`: `MIEMBRO_ALTA` (200 / sin cuenta 404), `MIEMBRO_ROL_DAR` (200 / FAMILIA
    400), `MIEMBRO_ROL_QUITAR` (200 / sin el rol 404), `MIEMBRO_SEDES` (200 / lista vacía 400), `MIEMBRO_GRUPOS` (200
    / grupo inexistente 404).
  - Matriz: `GET`/`HEAD /api/miembros`, `POST /api/miembros`, `GET`/`HEAD /api/miembros/{usuarioId}`,
    `PUT /api/miembros/{usuarioId}/grupos`, `PUT` y `DELETE /api/miembros/{usuarioId}/roles/{rol}`,
    `PUT /api/miembros/{usuarioId}/sedes`: GESTOR y PROPIETARIO `PERMITE`, resto `DENIEGA`.
- [x] **Paso 2: comprobar que fallan.** `mvn test -Dtest='MiembrosApiIntegrationTest,RegistroAccionesMiembrosTest,AuditoriaRequestFilterClavesRedactadasTest,AuditoriaRequestFilterRegistroPropioTest,RegistroUnicoProfesorIntegrationTest,MatrizPermisosTest' -DexcludedGroups=migracion`:
  no compila hasta crear `RegistroAccionesMiembros` y las entradas del enum; con los esqueletos, las rutas dan 404/403.

### Tarea 2: implementar

**Ficheros:**
- Crear: `J/controllers/MiembroController.java`, `J/services/GestionMiembrosService.java`,
  `J/services/RegistroAccionesMiembros.java`, `J/repositories/MiembrosRepository.java`,
  `J/dtos/request/MiembrosRequests.java`, `J/dtos/response/MiembrosDTOs.java`.
- Modificar: `J/repositories/MembresiaRepository.java` (`bloquearPropietariosActivos`),
  `J/services/impl/MembresiaServiceImpl.java` (`reemplazarAutoridades`: bloqueo, 409 y `READ_COMMITTED`),
  `J/config/SecurityConfiguration.java` (regla de `/api/miembros/**`), `J/config/AuditoriaRequestFilter.java` (cinco
  entradas, `CLAVES_REDACTADAS_POR_RUTA` y `PARAMETROS_REDACTADOS_POR_RUTA`).

**Interfaces:**
- Produce: `/api/miembros/**` (contrato de la pantalla Miembros de la E9 y del retoque de E8-6), `MiembrosDTOs`, módulo
  de registro `miembros`. E8-4 añade el alta sin cuenta e `invitacionPendiente`.
- Consume: `MembresiaRepository`, `MembresiaSedeRepository`, `AsignacionProfesorRepository`,
  `UsuarioRepository#bloquearPorId`, `MembresiaService#esUsuarioDeOrganizacion`,
  `RegistroAccionesProfesor`, `TenantContext`.

- [x] **Paso 1:** `RegistroAccionesMiembros` y sus tests en verde.
- [x] **Paso 2:** servicio, controlador y regla de URL; IT de miembros en verde.
- [x] **Paso 3:** último propietario en `reemplazarAutoridades`; `SesionPorOrganizacionIntegrationTest`,
  `AdminControllerTest` y `MembresiaServiceImplTest` sin cambios y en verde.
- [x] **Paso 4:** `EscrituraConRegistroPropio`, `CLAVES_REDACTADAS_POR_RUTA` y matriz; sus tests en verde.

### Tarea 3: matriz, documentación y batería

- [x] `docs/multi-escuela/matriz-endpoints.md`: nota de las 9 líneas y recuento de la cabecera.
- [x] `MatrizPermisosTest`, `AuditoriaRequestFilterRegistroPropioTest`, `RegistroUnicoProfesorIntegrationTest`,
  `AsignacionProfesoresIntegrationTest`, `SecurityAuthorizationWebMvcTest` y `mvn test -DexcludedGroups=migracion` en
  `src-api`, en primer plano, en verde; dorados de Moiskimdo sin tocar.
- [x] `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`.

---

## 5. Pieza E8-4: invitaciones por correo (detallada)

Rama `feature/e8-4` desde `feature/e8-3`. **Con migración: V31_9** (decisión 223, 20/10/2026), aditiva: solo
`usuario.contrasena` pasa a admitir NULL. Despliegue con el atajo `desplegar_migrar` (línea en
`checklist-despliegue.md`). Tres rutas nuevas (`POST /api/miembros/{usuarioId}/invitacion`,
`POST /api/auth/invitacion/datos` y `POST /api/auth/invitacion/aceptar`) y cambios en tres respuestas
(`invitacionPendiente`).

Comprobación previa (paso 0, antes de escribir nada):
- `usuario.contrasena` es `VARCHAR(255) NOT NULL` desde `V1__baseline_schema.sql:432` (ninguna migración posterior la
  cambia) y la entidad lleva `@NotBlank`; con `spring-boot-starter-validation`, Hibernate rechaza un NULL al guardar.
  «Pendiente = LOCAL y sin contraseña» (D8-13) necesita, por tanto, V31_9 y quitar el `@NotBlank`.
- Login LOCAL con contraseña NULL: el codificador es `BCryptPasswordEncoder`; `matches(raw, null)` devuelve `false`
  (con un aviso «Empty encoded password»), `DaoAuthenticationProvider` lanza `BadCredentialsException` y `signin`
  responde 401 «Credenciales invalidas.» contando el intento fallido. Sin NPE ni 500.
- Google con una cuenta LOCAL existente (`OAuth2UserServiceImpl.processOAuth2Login`): con el email verificado y alguna
  escuela en la que pueda entrar, pasa la cuenta a GOOGLE y entra sin tocar la contraseña. Con la definición de D8-13,
  eso ya acepta la invitación; faltaba borrar el token (decisión 223: se borra).

Criterios del constructor (no los fija el diseño; se anotan en la PR):
- **V31_9** (`V31_9__usuario_contrasena_opcional.sql`). Lee de `information_schema` el tipo, el juego de caracteres y
  la colación actuales de `usuario.contrasena` y la redefine igual pero `NULL` (si ya admite NULL, no hace nada:
  repetible). Ninguna fila cambia. MySQL la hace INPLACE con escrituras concurrentes (`usuario` es pequeña). El código
  anterior funciona sobre este esquema (siempre escribe la contraseña); volver atrás con invitaciones pendientes exige
  antes ponerles una contraseña o borrarlas para restaurar el NOT NULL.
- **Entidad.** `Usuario.contrasena` sin `@NotBlank`; conserva `@Size(min = 8)` (una cadena vacía sigue sin poder
  guardarse). Las reglas de contraseña de signup, registro y restablecer siguen en sus DTO (`@NotBlank`, `@Size`,
  `@Pattern`): un test fija que siguen rechazando la contraseña en blanco. `Usuario#invitacionPendiente()` = LOCAL (o
  sin proveedor) y contraseña NULL; no es una propiedad JSON.
- **Token: el de restablecer contraseña.** Mismas columnas (`reset_token_hash`, SHA-256 en hex, y
  `reset_token_expires_at`) y mismo generador (32 bytes aleatorios en Base64 URL): solo se guarda el hash. Caduca a los
  **7 días**. Un solo token vivo por cuenta: reenviar lo reemplaza (el anterior deja de valer) y un «olvidé mi
  contraseña» sobre una cuenta pendiente también (enlace de 1 hora: quien lo usa demuestra igual el email). El token en
  claro solo existe en memoria hasta el correo: el evento que lo lleva no lo imprime (`toString` sin él) y nunca va en
  una respuesta, una fila del registro ni un log.
- **Alta sin cuenta** (D8-6, D8-13). `POST /api/miembros` con un email sin cuenta: sin `nombre` ni `apellidos`, 404
  «No hay ninguna cuenta con ese email.» (como hoy); con uno solo, en blanco tras recortar o de más de 50 caracteres
  (las columnas), 400; email sin formato válido, 400. Si no, en la MISMA transacción que la E8-3 (`READ_COMMITTED`):
  sedes y grupos validados antes de escribir, cuenta LOCAL sin contraseña con nombre y apellidos recortados,
  `ultima_organizacion_id` = la escuela activa (la de la invitación) y el token; fila `MIEMBROS MIEMBRO_INVITADO
  usuario=#id rol=ROL`; después, lo de siempre (membresía, cargo, sedes, grupos, con sus filas). Todo o nada: un grupo
  ajeno deshace también la cuenta y no sale correo. D8-8 igual que la E8-3 (el gestor no invita propietarios ni
  gestores, 403). La respuesta es la ficha (200) con `invitacionPendiente=true`.
- **Cuenta existente** (anti-objetivo 1): `nombre` y `apellidos` se ignoran; ni token, ni contraseña, ni nombre, ni
  correo: exactamente la E8-3.
- **Dos invitaciones a la vez del mismo email.** La clave única del email decide: la segunda espera en el índice y
  falla al confirmar la primera; `AltaDeMiembro` (sin transacción) repite el alta UNA vez ante un
  `DataIntegrityViolationException`, que entonces encuentra la cuenta y sigue el camino de la cuenta existente (200,
  sin segundo correo). Otro fallo de integridad se repite igual y se propaga como antes.
- **Correo tras el commit** (`InvitacionCorreoListener`, `@TransactionalEventListener(AFTER_COMMIT)`, como los de
  preinscripción). Una transacción deshecha no envía nada; un fallo del correo nunca deshace la cuenta ni da 500: se
  apunta en el log con el id de la cuenta y el tipo del error (sin token ni mensaje) y se puede reenviar. Envío por
  `EmailService` con su remitente de la escuela (`EmailService.enviarConImagen`, nueva sobrecarga asíncrona con una
  imagen en línea `cid:`: el logo como adjunto en línea, que Gmail sí muestra). Texto (`InvitacionCorreo`): cabecera
  con el logo (`MarcaEscuela.logoInformes`: el de siempre de Moiskimdo salvo que el superadmin suba otro; sin logo, solo
  el nombre, decisión 211) y el nombre de correo de cuenta de la escuela (`clubCorreoCuentaHtml`), «Hola, {nombre}.»,
  «Te han dado acceso como {propietario | gestor | responsable de sede | profesor} en {escuela}.», botón «Crear mi
  contraseña», «El enlace caduca en 7 días.» y el enlace en texto. Todo lo que viene del alta, escapado. Enlace:
  `app.frontend.base-url` (la misma base que restablecer contraseña; pendiente conocido del dominio único, riesgo 12)
  + `/crear-contrasena?token=…` (ruta de la página de la E8-6).
- **Reenviar** (`POST /api/miembros/{usuarioId}/invitacion`, sin cuerpo). Regla de URL de `/api/miembros/**` y el
  mismo `@PreAuthorize`. Orden: no es miembro de la escuela activa, 404 «La cuenta no es miembro de esta escuela.»;
  gestor (sin `ROLE_ADMIN`) y la cuenta solo tiene en esta escuela membresías PROPIETARIO o ADMINISTRADOR, 403 «Solo el
  propietario da o quita los roles de propietario y gestor.» (D8-8: esa invitación solo pudo salir del propietario);
  ya no pendiente, 409 «La invitación ya se aceptó.». El token nuevo se escribe con un `UPDATE` condicionado a que siga
  pendiente (si otro la aceptó entre medias, 409 igual), junto con `ultima_organizacion_id` = la escuela que reenvía
  (el correo lleva su marca). Fila `MIEMBROS INVITACION_REENVIADA usuario=#id`; correo tras el commit. Responde la
  ficha (200). Todo 2xx registra: entra en `EscrituraConRegistroPropio` (`MIEMBRO_INVITACION`).
- **Aceptar** (`POST /api/auth/invitacion/aceptar {token, nuevaContrasena}`, ruta pública nueva). No se reutiliza
  `POST /api/auth/password/reset`: hacer que inicie sesión cambiaría el restablecimiento de todas las cuentas
  (Moiskimdo incluido) y su enlace es de 1 hora. Cuerpo `PasswordResetUpdateRequest` (las mismas reglas: obligatoria, 8
  o más, mayúsculas, minúsculas y números; si no, 400). Token vacío, desconocido, caducado, de una cuenta ya no
  pendiente o ya usado: 400 «El enlace ha caducado o ya se usó. Pide que te lo reenvíen.». Un solo uso garantizado por un
  `UPDATE` condicionado (hash, pendiente y sin caducar): de dos usos a la vez, uno gana. Después: la cuenta deja de
  estar pendiente, el token se borra y se abre sesión con la cookie `jwt` de siempre (sesión sin «recordarme») y la
  respuesta de `signin`, en la escuela de la invitación (`ultima_organizacion_id`, vía
  `MembresiaService.elegirOrganizacionParaLogin`). Si ninguna escuela abre sesión (p. ej. invitado solo como
  responsable de sede, que no entra hasta la E9, o el rol ya quitado), la contraseña queda creada y responde 403 con el
  motivo de `signin`. `permitAll` en `SecurityConfiguration`; ANONIMO PERMITE en la matriz (y por ser pública, el resto
  de columnas también, sin tocar la lista blanca de la sesión solo-profesor). Bajo `/api/auth/**`: no se audita, como
  hoy (D8-14); el registro no gana nada y el cuerpo lleva token y contraseña.
- **Datos de la página** (`POST /api/auth/invitacion/datos {token}`, ronda 1 de la revisión): lo que «Crea tu
  contraseña» enseña antes de escribirla, `{email, nombre, escuela}` (la escuela de la invitación, con el nombre del
  correo); token en el cuerpo, nunca en la URL. Vacío, desconocido, caducado, usado o de una cuenta ya no pendiente:
  400 con el mismo mensaje. Pública (`permitAll`, todas las columnas `PERMITE`), no se audita.
- **Contraseña puesta por el propietario** (`PUT /api/admin/users/{id}/password`, ronda 1): si la cuenta estaba
  pendiente, en el mismo guardado se borra su token (el enlace deja de servir); una cuenta con contraseña conserva su
  token de restablecer, como siempre.
- **Google** (decisión 223). En `processOAuth2Login`, una cuenta pendiente que entra con Google pasa a GOOGLE (como
  hoy) y además pierde el token: deja de estar pendiente y el enlace ya no sirve. Una cuenta LOCAL con contraseña, como
  hoy (su token de restablecer no se toca).
- **`invitacionPendiente`** (boolean, aditivo): fila de `GET /api/miembros` (tras `authProvider`, D8-4; sin consulta
  nueva: va en la de la página), ficha `GET /api/miembros/{usuarioId}` (y la respuesta de las escrituras) y
  `GrupoProfesoresDTOs.Profesor` de `GET /api/grupos/{grupoId}/profesores` (al final; el resto del JSON igual).
- **Registro** (D8-16, §7): `MIEMBRO_INVITADO` (CREATE) e `INVITACION_REENVIADA` (UPDATE) en `RegistroAccionesMiembros`,
  solo ids y enumeraciones. `CLAVES_REDACTADAS_POR_RUTA` de `/api/miembros` ya redacta `nombre` y `apellidos` (E8-3);
  `token` y las claves de contraseña ya las redacta siempre `AuditoriaPayloadSanitizer`.
- **Reenviar exige un rol activo** (ronda 2): sin ningún rol activo en la escuela, 409 «La cuenta no tiene ningún rol
  activo en esta escuela.»; el texto del correo y la regla D8-8 del reenvío miran solo los roles activos.

Notas para la PR (comportamiento conocido, sin cambiar):
- **Escuela tras aceptar.** La sesión se abre donde la abriría el login (`elegirOrganizacionParaLogin`): con varias
  escuelas válidas, la de la invitación (es la última). Si el rol de la escuela de la invitación no abre sesión (p. ej.
  solo responsable de sede, hasta la E9) pero el de otra escuela sí, entra en esa otra.
- **«Olvidé mi contraseña» sobre una cuenta pendiente** (`POST /api/auth/password/forgot`, pública): sustituye el token
  de la invitación por uno de restablecer (1 hora) que va al mismo email; el enlace de la invitación deja de servir. No
  es una toma de cuenta: solo quien lee ese buzón la usa. Basta con reenviar la invitación.
- **Logs en DEBUG.** El volcado de entidades de Hibernate (`EntityPrinter`, solo en DEBUG) imprime `reset_token_hash`
  al guardar, como los hashes de las contraseñas (ya pasaba antes de la E8-4). El token en claro nunca sale; a partir de
  INFO, nada. Se deja así.

### Tarea 1: tests que fallan

**Ficheros:**
- Crear: `T/integracion/InvitacionesIntegrationTest.java` (`@SpringBootTest`, `@AutoConfigureMockMvc`, `PER_CLASS`,
  `extends MySqlIntegrationTest`, `@MockitoBean EmailService` para leer el correo sin enviar nada). Escuelas
  `EscuelaConSedesDePrueba.crear(…, "it-e8-4")` y `"it-e8-4-otra"`, cuentas y emails nuevos por test con la marca de la
  escuela, datos inventados; nada en Moiskimdo.
- Crear: `T/integracion/UsuarioContrasenaV31_9MigracionIntegrationTest.java` (`@Tag("migracion")`, esquema propio).
- Crear: `T/services/InvitacionCorreoTest.java` (sin Spring).
- Modificar: `T/services/RegistroAccionesMiembrosTest.java`, `T/servicios/OAuth2UserServiceImplTest.java`,
  `T/config/AuditoriaRequestFilterRegistroPropioTest.java`, `T/integracion/RegistroUnicoProfesorIntegrationTest.java`
  (caso `MIEMBRO_INVITACION`), `T/integracion/MiembrosApiIntegrationTest.java` (claves de la fila y la ficha),
  `T/integracion/MySqlSmokeIntegrationTest.java` y `T/integracion/MembresiaMigracionIntegrationTest.java` (última
  versión 31.9) y `src-api/src/test/resources/seguridad/matriz-permisos.txt` (3 líneas nuevas).

- [x] **Paso 1: escribir los tests**:
  - IT `InvitacionesIntegrationTest`: `altaSinCuenta_conNombre_creaCuentaPendienteMembresiaYEnviaCorreoTrasCommit`
    (cuenta LOCAL sin contraseña, hash de 64 y caducidad a 7 días, membresía, asignación y cargo; filas
    `MIEMBRO_INVITADO`, `ROL_DADO` y `ASIGNACION_CREADA` sin la genérica; correo al email con el texto del rol;
    dentro de la transacción aún no ha salido; con la transacción deshecha, ni cuenta ni correo),
    `altaSinCuenta_sinNombre_404`, `altaSinCuenta_nombreNoValido_400` (solo uno, en blanco, 51 caracteres, email sin
    formato; nada creado), `altaSinCuenta_grupoAjeno_noCreaNada`, `gestorInvitaPropietario_403`,
    `invitarEmailConCuenta_noTocaContrasenaNiEnviaCorreo`, `cuentaPendienteNoEntraConContrasena` (401, también con
    contraseña vacía; nunca 500), `tokenDeInvitacionNoApareceEnAuditoriaNiLogs` (alta, reenvío y aceptación con la
    salida capturada y todas las filas de auditoría de la escuela), `reenviarInvalidaElTokenAnterior`,
    `reenviarSinPendiente_409` (también tras aceptar), `reenviarSinRolActivo_409_yElCorreoUsaSoloLosActivos`,
    `dosAceptacionesALaVezMismoToken_unaGana`, `reenviarOtraEscuela_404`, `gestorReenviaPropietario_403` (y
    sí reenvía la de un profesor), `tokenCaducadoOUsado_rechazado` (caducado, usado, desconocido, vacío; contraseña
    débil 400 y sigue pendiente), `crearContrasenaIniciaSesionEnLaEscuela` (cookie `jwt` con la escuela de la
    invitación, roles de esa escuela, ya no pendiente y entra con la contraseña nueva),
    `aceptarSinEscuelaQueAbraSesion_403ConContrasenaCreada`, `fallaElCorreo_noDeshaceLaCuenta` (alta y reenvío),
    `dosInvitacionesALaVezMismoEmail_sin500` (varias rondas: `[200, 200]`, una cuenta, una fila `MIEMBRO_INVITADO`, un
    correo), `correoConMarcaDeLaEscuela` (nombre y remitente de la escuela 2, sin logo, sin nada de Moiskimdo),
    `invitacionPendienteEnLasTresRespuestas`, `aceptarNoSeAudita`, `datosDeLaInvitacion` (vigente, caducada, usada,
    desconocida, cuenta aceptada), `adminPoneContrasenaAPendiente_borraElToken`, `contrasenaEnBlancoSigueRechazada` (signup,
    registro y restablecer, 400; la entidad no guarda una cadena vacía).
  - `UsuarioContrasenaV31_9MigracionIntegrationTest`: sobre una base en V31_8 con cuentas: llega a 31.9, la columna
    admite NULL con el mismo tipo, juego de caracteres y colación, las demás columnas y todas las filas sin cambios, un
    INSERT con contraseña NULL funciona y repetirla no hace nada.
  - `InvitacionCorreoTest`: `moiskimdo_suNombreYSuLogoDeSiempre`, `otraEscuela_suNombreEscapadoYSuLogo`,
    `sinLogo_soloElNombre`, `textoSegunElRol`, `escapaElNombre`.
  - `RegistroAccionesMiembrosTest`: `invitacion_resumenSoloConIds`.
  - `OAuth2UserServiceImplTest`: `cuentaPendienteEntraConGoogle_dejaDeEstarPendiente`,
    `cuentaLocalConContrasenaEntraConGoogle_conservaSuToken`.
  - `AuditoriaRequestFilterRegistroPropioTest`: ejemplo `POST /api/miembros/21/invitacion`; fuera
    `GET /api/miembros/21/invitacion`, `POST /api/miembros/21/invitacion/otra`, `PUT /api/miembros/21/invitacion`.
  - `RegistroUnicoProfesorIntegrationTest`: `MIEMBRO_INVITACION` (200 / cuenta con contraseña 409).
  - Matriz: `POST /api/miembros/{usuarioId}/invitacion` (GESTOR y PROPIETARIO `PERMITE`, resto `DENIEGA`),
    `POST /api/auth/invitacion/datos` y `POST /api/auth/invitacion/aceptar` (todas `PERMITE`).
- [x] **Paso 2: comprobar que fallan.** `mvn test -Dtest='InvitacionesIntegrationTest,InvitacionCorreoTest,RegistroAccionesMiembrosTest,OAuth2UserServiceImplTest,AuditoriaRequestFilterRegistroPropioTest,RegistroUnicoProfesorIntegrationTest,MatrizPermisosTest' -DexcludedGroups=migracion`
  y `mvn test -Dtest=UsuarioContrasenaV31_9MigracionIntegrationTest -Dgroups=migracion`: no compila hasta crear
  `InvitacionCorreo`, las acciones y la entrada del enum; la migración no existe.

### Tarea 2: implementar

**Ficheros:**
- Crear: `src-api/src/main/resources/db/migration/V31_9__usuario_contrasena_opcional.sql`.
- Crear: `J/services/InvitacionService.java` (crear la cuenta pendiente, reenviar, aceptar), `J/services/AltaDeMiembro.java`
  (repite el alta una vez ante la clave única), `J/services/InvitacionCorreo.java`,
  `J/services/InvitacionCorreoListener.java`, `J/events/InvitacionEnviableEvent.java`.
- Modificar: `J/entities/Usuario.java`, `J/repositories/UsuarioRepository.java` (dos `UPDATE` condicionados),
  `J/repositories/MiembrosRepository.java` (`sinContrasena` en la fila), `J/services/GestionMiembrosService.java`,
  `J/services/RegistroAccionesMiembros.java`, `J/services/AsignacionProfesorService.java`,
  `J/services/EmailService.java` y `J/services/impl/EmailServiceImpl.java` (`enviarConImagen`),
  `J/services/impl/OAuth2UserServiceImpl.java`, `J/controllers/MiembroController.java`,
  `J/controllers/AuthenticationController.java`, `J/dtos/request/MiembrosRequests.java` (`nombre`, `apellidos`),
  `J/dtos/response/MiembrosDTOs.java`, `J/dtos/response/GrupoProfesoresDTOs.java`,
  `J/config/SecurityConfiguration.java` (`permitAll`), `J/config/AuditoriaRequestFilter.java` (`MIEMBRO_INVITACION`).

**Interfaces:**
- Produce: `POST /api/miembros` sin cuenta (con `nombre`, `apellidos`), `POST /api/miembros/{usuarioId}/invitacion`,
  `POST /api/auth/invitacion/aceptar {token, nuevaContrasena}` → cookie `jwt` y `{token}` como `signin`;
  `invitacionPendiente` en las tres respuestas; enlace `/crear-contrasena?token=…` (contrato de la página de la E8-6).
- Consume: `GestionMiembrosService` (E8-3), `MarcaEscuelaService#de`, `EmailService`, `MembresiaService`,
  `JwtService`, `RegistroAccionesMiembros`.

- [x] **Paso 1:** V31_9 y la entidad; test de migración en verde.
- [x] **Paso 2:** alta sin cuenta, correo tras el commit y reintento; sus IT en verde.
- [x] **Paso 3:** reenviar y aceptar con la sesión; Google; `invitacionPendiente`.
- [x] **Paso 4:** `EscrituraConRegistroPropio`, matriz y `permitAll`; sus tests en verde.

### Tarea 3: matriz, documentación y batería

- [x] `docs/multi-escuela/matriz-endpoints.md`: nota de las 3 líneas y recuento de la cabecera.
- [x] `docs/multi-escuela/checklist-despliegue.md`: línea de la E8 con V31_9 (`desplegar_migrar`) y su comprobación.
- [x] Tests de miembros, auth, restablecer contraseña y OAuth, `MatrizPermisosTest`, los del filtro de auditoría,
  `RegistroUnicoProfesorIntegrationTest`, `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion` en
  `src-api`, en primer plano, en verde; dorados de Moiskimdo sin tocar.
- [x] `git diff --stat` igual a `git diff --ignore-cr-at-eol --stat`.

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
