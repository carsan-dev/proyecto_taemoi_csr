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

## 2. Pieza E8-1 (la detalla su implementador con TDD antes de empezar)

Rama `feature/e8-1`. Alcance:
- `GET /api/auth/permisos` (D8-14): DTO, capacidades, sedes visibles (todas con alcance total; las suyas para R).
- Lista blanca de la sesión solo-profesor y `PROFESOR_SESION_180`; SOLO_PLATAFORMA con roles vacíos; línea en la
  matriz y nota en `matriz-endpoints.md`.
- `IdentificadorSesionJwt.alcanceDe`: grupos para P y G con `ROLE_PROFESOR` (una consulta solo para ellos).
- `/api/profesor/**` y `@acceso.*DeMisGrupos` sin la exclusión de ADMIN/MANAGER, filtrando solo por
  `alcance.grupos()` (D8-15).
- Tests de P+Pr y G+Pr; puntos de revisión 1-3.

---

## 3. Pieza E8-2 (la detalla su implementador con TDD antes de empezar)

Rama `feature/e8-2`. Alcance:
- Controlador y servicio de sedes (D8-2); regla de URL `/api/sedes/**`.
- 409 por nombre repetido y por última sede activa; longitudes de las columnas; sin `DELETE`.
- Registro módulo `sedes`; `EscrituraConRegistroPropio` y `CLAVES_REDACTADAS_POR_RUTA` donde toque.
- D8-3: test que fija el comportamiento actual con dos sedes activas y nota en la PR.
- Punto de revisión 4; matriz y nota.

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
