# Fase 3 — sedes, permisos por sede, profesores y administrador de plataforma

Propuesta para revisión · 14/10/2026 · rama `feature/multi-escuela` (HEAD `979ca8dd`). Solo diseño: no
hay código ni migraciones escritas. Rutas relativas a `src-api/src/main/java/com/taemoi/project/` y a
`src-frontend/src/app/`. Base: `modelo-datos.md` (decisiones 9, 10, 11 y 14), `informe-2026-10-15.md`.

## 1. Estado actual

**Membresías y autoridades**
- `membresia.rol` ya admite los cinco roles (`entities/Membresia.java:36-39`, V20). Desde la entrega 5b-1,
  `RESPONSABLE_SEDE` y `PROFESOR` tienen autoridades propias (`ROLE_RESPONSABLE_SEDE`, `ROLE_PROFESOR`) que fallan
  cerradas: no pasan ninguna regla y, solas, no abren sesión (`entities/RolMembresia.java`). Antes se traducían
  a `ROLE_MANAGER` y lo veían todo.
  Ningún código ni migración los crea (`RolMembresia.desdeAutoridad` solo da
  PROPIETARIO/ADMINISTRADOR/FAMILIA).
- Autoridades por petición: `IdentificadorSesionJwt.verificar` (`organizacion/IdentificadorSesionJwt.java`)
  lee el claim `org`, llama a `MembresiaServiceImpl.autoridadesEn` (`services/impl/MembresiaServiceImpl.java:50-55`)
  y guarda `SesionVerificada`; `OrganizacionRequestFilter` (`organizacion/OrganizacionRequestFilter.java:68-78`)
  fija la escuela y `config/JwtAuthenticationFilter.java:38-56` convierte las autoridades en `GrantedAuthority`.
- Cambio de escuela: `GET /api/auth/organizaciones` y `POST /api/auth/organizacion`
  (`controllers/AuthenticationController.java:369-404`, `services/impl/AuthenticationServiceImpl.java:144-158`).
  El frontend no tiene selector de escuela (ninguna llamada a `/auth/organizacion`).
- Restos de `usuario_rol`: mapeo `@Deprecated` sin lecturas (`entities/Usuario.java:78-87`, getters `:175-185`).
  La pantalla de roles escribe membresías (`controllers/AdminController.java:150-167` → `reemplazarAutoridades`,
  `MembresiaServiceImpl.java:116-152`).

**Superficie de autorización** — 188 `@PreAuthorize` en 18 controladores (Alumno 66, PDF 21,
ProductoAlumno 15, Preinscripcion 15, Grupo 14, Convocatoria 10, Evento 9, Authentication 7, Turno/Tesoreria/
Producto/Admin 6, Grado 2, y 1 de clase en Tutor, AlumnoSalud, Consentimiento, Auditoria, DniCompartido):

| Expresión | Nº |
|---|---|
| `ROLE_MANAGER \|\| ROLE_ADMIN` (+12 `hasAnyAuthority(ADMIN,MANAGER)` en preinscripciones) | 153 |
| `... \|\| ROLE_USER` (portal familia) | 22 |
| solo `ROLE_ADMIN` (usuarios, contraseñas, límite de turno, auditoría, DNI compartidos, plantillas) | 11 |
| `isAuthenticated()` (organizaciones) | 2 |

Además `config/SecurityConfiguration.java:99-291` repite reglas por URL y método con las mismas autoridades;
`/api/admin/**` es solo `ROLE_ADMIN` (`:290`). Frontend: `guards/role.guard.ts` (cualquiera de
`requiredRoles`) y tres conjuntos en `app.routes.ts:20-23` (`management`, `adminOnly`, `userPortal`);
`auditoriaSistema` y `configuracion-sistema` son `adminOnly` (`app.routes.ts:366-383`).

**Acceso de familias**
- `AlumnoAccessControlServiceImpl.java:33-47`: gestión = todo; `ROLE_USER` = alumno **activo** con el mismo
  email que la cuenta. Lo usan las 22 rutas de familia vía `usuarioPuedeAccederAlumno`
  (`AlumnoController.java:2445-2448`, `ConvocatoriaController.java:146`, `GrupoController.java:136`).
- `GET /api/auth/user/alumnos` lista por email para cualquier rol (`AuthenticationController.java:315-330`);
  `obtenerAlumnoVisibleParaUsuario` (`:333-364`) usa `usuario.alumno_id` solo para gestión o para elegir
  entre los de su email. Para una familia, `usuario.alumno_id` **no da acceso** si el email no coincide.
- Puerta de entrada: una cuenta solo familia necesita un alumno activo con su email en la escuela
  (`RolMembresia.java:58-62`, `IdentificadorSesionJwt.java:117-118`, `MembresiaServiceImpl.java:226`,
  consulta nativa `repositories/AlumnoRepository.java:200-207`).
- Hermanos con el mismo email: todos visibles sin más (por email). El tutor solo entra si su email es el del
  alumno; `tutor.email` (V25) no se usa. Escrituras de `usuario.alumno_id`: registro
  (`services/impl/RegistroServiceImpl.java:82,145-153`), Google (`OAuth2UserServiceImpl.java:88,118`, el
  primero de la lista), cambio de email (`AlumnoServiceImpl.java:607-626`), baja (`:1596-1601`). Recordatorio
  de racha por email (`RecordatorioRachaServiceImpl.java:138`). `usuario.alumno_id` es UNIQUE (V1:443).

**Estructura**
- `grupo` sin sede (`entities/Grupo.java:29-66`), `turno` → `grupo` (`Turno.java:39-42`), `alumno_grupo`
  (`Grupo.java:59-60`) y `alumno_turno` (`Alumno.java:143`) sin `organizacion_id`; ningún UNIQUE en
  `grupo.nombre`. `convocatoria` solo lleva disciplina; tesorería y `producto_alumno` cuelgan del alumno;
  `preinscripcion` lleva `grupo_id`/`turno_id`. No existe nada de `sede` en código ni migraciones.
- Aforo: `limite_turno` en `configuracion_organizacion` (36 por defecto,
  `ConfiguracionSistemaServiceImpl.java:29-30`), usado por `AforoPreinscripcionService.java:25` y expuesto en
  `GET /api/turnos/limite` (público) y `/api/admin/configuracion/limite-turno`.
- Organización 1: «Moi's Kim Do» (V18:34-35); un solo local, en Umbrete (dirección pública de la web).

**Auditoría y errores**
- `AuditoriaRequestFilter` (`config/AuditoriaRequestFilter.java:169-182, 241-243`) registra toda petición
  `/api/**` GET/POST/PUT/DELETE con 2xx o 4xx/5xx, salvo `/api/auth/**` y la propia auditoría, con payload
  saneado. `AuditoriaEvento` es `EntidadOrganizacion` (`@TenantId`); `organizacion_id` NOT NULL DEFAULT 1
  (V19:270): las peticiones anónimas (escáneres) caen en la escuela por defecto.
- Solo `ROLE_ADMIN` lo lee (`AuditoriaController.java:21-22`). «Error» = `estado_http >= 400`
  (`AuditoriaServiceImpl.java:394-406`). El panel pide los errores de 7 días y muestra la alerta «Errores
  recientes en API» (`componentes/vistas/vista-principal-admin/vista-principal-admin.component.ts:246-257,
  427-437`); a un gestor le da 403 en silencio y le enseña tarjetas «Auditoria de API» y «Roles y
  Configuracion» que le redirigen (`:115-127, 175-180`).

## 2. Migraciones

### V31 (ampliación; despliegue 3, con la aplicación parada)

Patrón de V19/V20: `information_schema` + `PREPARE`, `INSERT … WHERE NOT EXISTS`; repetible tras fallo
(`flyway repair`). Nada pasa a NOT NULL si el código anterior no lo escribe, para poder volver atrás.

1. `sede` (`modelo-datos.md`) + UNIQUE (`organizacion_id`, `nombre`), `aforo_turno` INT NULL (NULL = `limite_turno`).
2. Una sede por organización sin sede: la 1 con el nombre de la decisión D1; el resto «Sede principal».
3. `grupo.sede_id` BIGINT **NULL**, FK, índice; relleno con la única sede de su organización.
4. `alumno.sede_principal_id` NULL, FK; relleno en organizaciones con una sola sede.
5. `membresia_sede` (`membresia_id`, `sede_id`) PK compuesta, FK ON DELETE CASCADE.
6. `asignacion_profesor` (`organizacion_id`, `usuario_id`, `grupo_id`, `rol` ENUM(`TITULAR`,`AYUDANTE`)),
   PK (`usuario_id`, `grupo_id`), FK a `usuario`, `grupo`, `organizacion`.
7. `usuario.es_admin_plataforma` BOOLEAN NOT NULL DEFAULT FALSE. **Ninguna fila se marca en la migración**
   (se hace con SQL fuera del repositorio, como los datos del firmante).
8. `usuario_alumno` (`usuario_id`, `alumno_id`, `organizacion_id`, `relacion` ENUM(`PROPIO`,`TUTOR`),
   `origen` ENUM(`MIGRACION`,`REGISTRO`,`MANUAL`), `creado_en`), PK (`usuario_id`, `alumno_id`), FK con
   CASCADE. `organizacion_id` = la del alumno (desnormalizada; la entidad, como `Membresia`, no es
   `@TenantId` porque el login la consulta antes de fijar escuela). `origen` es nuevo respecto al modelo.
   Relleno con las reglas de §5.
9. Tabla temporal `v31_aplicada_en_esta_conexion` para la guarda de V32 (como V27).

**¿Guarda como V28?** V31 no: solo añade y el código anterior sigue funcionando (columnas nuevas NULL,
tablas nuevas ignoradas). La guarda va en V32. `turno`, `preinscripcion`, `convocatoria` y tesorería no
llevan `sede_id`: la sede se deriva del grupo o del alumno (§4). `evento` queda fuera (fase 4).

Comprobación previa (nuevo `precheck-v31.sql`, solo lectura, solo recuentos):
- usuarios con `alumno_id`: total, alumno inexistente, alumno de una organización sin membresía del usuario,
  email del alumno ≠ email del usuario (separado familia / gestión);
- emails de cuenta con más de un alumno en la misma escuela (distribución), con diferencias solo de
  mayúsculas o espacios, y que coinciden con alumnos de una escuela sin membresía;
- `tutor.email` que coincide con una cuenta sin vínculo (candidatos manuales);
- cuentas solo familia sin alumno activo por email (hoy no pueden entrar);
- membresías `RESPONSABLE_SEDE`/`PROFESOR` existentes (debe ser 0);
- nombres de grupo repetidos por organización (para el UNIQUE de V32).

Comprobación posterior (`postcheck-v31.sql`): para cada cuenta familia de la organización 1, diferencia
entre «alumnos activos por email» y «alumnos activos por `usuario_alumno`» (debe ser 0 filas) y grupos
con `sede_id` NULL (debe ser 0).

### V32 (contracción; despliegue 4, posterior y con el postcheck de V31 limpio)

1. Guarda: se detiene si V31 se aplicó en la misma ejecución (`v32_no_puede_aplicarse_en_el_mismo_despliegue_que_v31`;
   placeholder `v32_permitir_mismo_despliegue_que_v31`, solo pruebas).
2. Comprobación que la detiene antes de cambiar nada (truco «tabla inexistente» de V28) si hay grupos sin
   sede o vínculos `usuario.alumno_id` que cumplen la regla R1 sin fila en `usuario_alumno`.
3. Resincronización idempotente de `usuario_alumno` desde `usuario.alumno_id` (R1).
4. `grupo.sede_id` NOT NULL; UNIQUE (`sede_id`, `nombre`) solo si el precheck no dio repetidos.
5. Borrar FK e índice único de `usuario.alumno_id`, la columna, y la tabla `usuario_rol`.
6. Mismo despliegue que el código sin `Usuario.roles`, `Usuario.alumno` ni `Alumno.usuario`.
   Vuelta atrás: restaurar la copia previa a V32.

## 3. Matriz de permisos

`H` = Taemoi hoy (PROPIETARIO = `ROLE_ADMIN`, ADMINISTRADOR = `ROLE_MANAGER`, FAMILIA = `ROLE_USER`);
`N` = nuevo; `S` = solo sus sedes; `G` = solo sus grupos; `F` = solo sus fichas (`usuario_alumno`);
`D#` = depende de la decisión; `—` = no.

| Capacidad / pantalla | PROPIETARIO | ADMINISTRADOR | RESP. SEDE | PROFESOR | FAMILIA | ADMIN PLATAF. |
|---|---|---|---|---|---|---|
| Alumnos: listar y ver ficha | H | H | N·S | N·G lectura (D5) | — | — |
| Alumnos: alta, edición, baja, documentos | H | H | N·S | — | — | — |
| Salud del alumno | H | H | N·S (D4) | N·G (D5) | — | — |
| Tutores y consentimientos | H | H | N·S | N·G lectura (D5) | — | — |
| Grupos y turnos: ver | H | H | N·S | N·G | F (H) | — |
| Grupos y turnos: crear, editar, asignar alumnos | H | H | N·S | — | — | — |
| Asistencia (PDF por grupo) | H | H | N·S | N·G | — | — |
| Convocatorias, aptos, actualizar grado | H | H | N·S (D4) | N·G ver aptos (D5) | F ver (H) | — |
| Tesorería, cobros, productos del alumno | H | H | — (D4; registrar cobros en efectivo, pendiente) | — | — | — |
| Catálogo: productos, tarifas, disciplinas, grados | H | H | lectura | lectura | — | — |
| Preinscripciones: gestionar | H | H | N·S (por grupo) | — | — | — |
| Plantillas de preinscripción | H | — | — | — | — | — |
| Eventos | H | H | D4 | — | público | — |
| Portal familia (reto, material, documentos) | H | H | — | — | F (H) | — |
| Sedes: gestionar | N | N (D8) | — | — | — | — |
| Miembros y roles de la escuela | H (roles) → N | N sin PROP./ADMIN. (D8) | — | — | — | — |
| Asignación de profesores | N | N | N·S | — | — | — |
| Límite de turno / aforo por sede | H / N | — / N (D8) | — | — | — | — |
| DNI compartidos | H | — | — | — | — | — |
| Registro de acciones de su escuela | H → sin errores (D2) | — | — | — | — | N todas |
| Errores técnicos de la API | H → — (autorizado) | — | — | — | — | N todas |
| Configuración de plataforma (escuelas) | — | — | — | — | — | N |

Un usuario con varios roles en la misma escuela (p. ej. profesor y familia) recibe la unión.

## 4. Diseño de la aplicación de permisos

**Recomendación: autoridades nuevas que fallan cerradas + seguridad por método con un bean de acceso para
los recursos por id + filtro explícito en servicio para los listados.**

- `PROPIETARIO`, `ADMINISTRADOR` y `FAMILIA` conservan exactamente `ROLE_ADMIN`/`ROLE_MANAGER`/`ROLE_USER`:
  ninguna de las 188 anotaciones cambia de significado para Taemoi.
- `RESPONSABLE_SEDE` → `ROLE_RESPONSABLE_SEDE`, `PROFESOR` → `ROLE_PROFESOR` (dejan de ser `ROLE_MANAGER`).
  Sin abrir nada, no pasan ninguna regla: cada endpoint se abre a propósito. El mapeo `Usuario.roles` se
  quita ya en V31 (no se lee) para que ampliar el enum `Roles` no choque con la columna ENUM de `usuario_rol`.
- `SesionVerificada` gana un `AlcanceSesion` (sedes visibles o «todas», grupos del profesor), calculado en
  `IdentificadorSesionJwt.verificar` con una consulta (`membresia_sede` + `asignacion_profesor`).
- Un bean `@acceso` sustituye a las tres copias de `usuarioPuedeAccederAlumno`:
  `@PreAuthorize("@acceso.alumno(#id, 'EDITAR')")`, `grupo`, `turno`, `preinscripcion`, `productoAlumno`.
  Para alcance total devuelve `true` sin consultar (mismo coste que hoy).
- Listados: el servicio recibe el alcance y añade el predicado (`grupo.sede_id IN …`; alumno visible si su
  `sede_principal_id` o alguno de sus grupos está en sus sedes, D9). Con alcance total no se añade nada.
- Reglas por URL de `SecurityConfiguration`: se añaden las autoridades nuevas solo en las rutas abiertas.
  Nuevas rutas de gestión fuera de `/api/admin/**` (hoy solo `ROLE_ADMIN`); plataforma en `/api/plataforma/**`.
- Test de matriz: recorre `RequestMappingHandlerMapping` y exige una política declarada por endpoint y rol;
  un endpoint nuevo sin política hace fallar la suite.

Descartado: **filtro de Hibernate por sede** — no se aplica a `find`/carga por id, sería un segundo tenant
oculto sobre una relación varios-a-varios (alumno–grupo) y depende del rol, no de la escuela; el
aislamiento duro ya lo da `@TenantId`. **`PermissionEvaluator`** — `hasPermission(#id,'Alumno','leer')` es
texto sin tipos con un despachador central; el bean explícito es más legible y testeable (puede envolverse
después). **Solo filtrado en servicio** — los endpoints por id quedarían sin comprobar si se olvida.

**Frontend.** `/api/auth/roles` no cambia. Nuevo `GET /api/auth/permisos`: roles de membresía, sedes,
`esAdminPlataforma` y capacidades. Conjuntos de rutas nuevos (`sede`, `profesor`, `plataforma`); el guard
sigue siendo «cualquiera de». Selector y filtros de sede ocultos si la escuela tiene una sede. La autorización
real es siempre del backend.

**Administrador de plataforma.** Marca en `usuario`; en `verificar` se añade `ROLE_ADMIN_PLATAFORMA` en
cualquier escuela. Si no tiene membresías, la sesión se abre en la organización por defecto **solo** con esa
autoridad (ningún dato de escuela). Sus lecturas entre escuelas van por un repositorio de solo lectura con
`JdbcTemplate`, columnas explícitas y paquete propio (excepción documentada, como
`AlumnoRepository.java:200-207`), usado solo por `/api/plataforma/**` y vigilado por un test de
arquitectura. Descartado `CurrentTenantIdentifierResolver.isRoot` (Hibernate 6.6): desactiva el filtro en
toda la sesión, también para escrituras. Errores técnicos = eventos con 4xx/5xx o sin sesión; el
propietario ve el registro de su escuela sin ellos (`AuditoriaController` filtra en servidor, no en pantalla).

## 5. `usuario_alumno` y reglas de relleno

Acceso familia = alumno **activo** con fila en `usuario_alumno` y membresía en la escuela de la sesión
(igual que hoy, «activo» se comprueba al leer). Reglas de V31:

- **R1 (vínculo directo):** `usuario.alumno_id` existente → fila si el email del alumno coincide (y el
  usuario tiene alguna membresía en esa escuela, R3) o si el usuario tiene membresía de gestión **activa** en
  esa escuela. Si es solo familia y el email no coincide, no se crea (hoy no le da acceso) y sale en el
  precheck. Una membresía de gestión inactiva no cuenta: la pantalla de roles degrada desactivándola y solo
  las activas dan autoridades, así que un gestor degradado ya no ve ese alumno.
- **Comparación de email (implementada en V31, aceptada 15/10/2026):** email de la cuenta con `TRIM` +
  `LOWER` (como `EmailUtils.normalizeEmail`) contra `LOWER(alumno.email)` **sin** `TRIM`, con la colación de
  la columna: exactamente la comparación del acceso de familia de hoy (`existsByIdAndEmailIgnoreCaseAndActivoTrue`,
  `contarActivosPorEmailEnOrganizacion`). Recortar también el email del alumno daría acceso a alumnos con
  espacios sobrantes que hoy no son visibles; el precheck los cuenta (`alumnos_email_solo_espacios`) para
  corregirlos a mano.
- **R2 (email):** cada alumno, activo o no, cuyo email normalizado sea el de una cuenta con alguna membresía
  (activa o no) en la escuela del alumno.
- **R3:** emails que coinciden con alumnos de una escuela sin membresía → sin fila; solo informe.
- **R4:** `tutor.email` → nunca automático (ampliaría el acceso); lista de candidatos.
- **`relacion`:** `PROPIO` si el alumno tiene 18 años o más hoy, `TUTOR` si no o sin fecha (D7). Solo
  informativa en esta fase.
- Altas nuevas: registro y Google escriben `usuario_alumno` (y `alumno_id` hasta V32); Google vincula
  todos los alumnos del email **que no tengan ya otra cuenta**, no solo el primero (decisión del
  propietario, 15/10/2026, opción A: un alumno ya vinculado a otra cuenta solo se comparte con un
  vínculo MANUAL que crea la gestión).
- Cambio de email del alumno o alta de un hermano en Taemoi: ver D6.
- **Gestor degradado después de V31:** una fila creada por R1 para un gestor activo (alumno de otro email)
  deja de corresponder a un acceso de hoy si después se le degrada (membresía de gestión desactivada). La
  entrega 2 debe revocarla o ignorarla en ese caso (p. ej. al quitar la membresía de gestión, o no
  contando filas R1 de alumnos de otro email sin gestión activa), y la resincronización R1 de V32 debe
  seguir la misma regla (gestión activa).
- **Integridad entre escuelas (entregas 6–8):** `asignacion_profesor` y `membresia_sede` no son
  `@TenantId` y la base solo tiene FK simples. El servicio debe comprobar al escribir que
  `asignacion_profesor.organizacion_id` es la organización del grupo (y de la membresía del profesor) y que
  la sede de cada `membresia_sede` pertenece a la organización de la membresía.

## 6. Cambios visibles para Taemoi

| # | Cambio | Estado |
|---|---|---|
| 1 | El propietario deja de ver errores técnicos: sin alerta «Errores recientes en API» ni filtro «Error» | Autorizado (decisión 11) |
| 2 | El registro de acciones excluye peticiones anónimas y 4xx/5xx | Necesita decisión (D2) |
| 3 | Familia: acceso por `usuario_alumno`; con D6 recomendada, se conserva el acceso por email | Mantener legado |
| 4 | Pantalla de roles pasa a «Miembros» (mismos roles para Taemoi) | Necesita decisión de diseño (UI) |
| 5 | Grupos con sede; selector y columna ocultos con una sola sede | Mantener legado |
| 6 | Aforo: `sede.aforo_turno` NULL → sigue valiendo `limite_turno` | Mantener legado |
| 7 | El gestor deja de ver tarjetas del panel que le redirigen (auditoría, configuración) | Necesita decisión (D10) |
| 8 | Google: una cuenta nueva queda vinculada a todos los alumnos de su email (hoy al primero; por email ya los ve) | Mantener legado (sin efecto en lo que ve) |
| 9 | Se borran `usuario_rol` y `usuario.alumno_id` (invisible; volver atrás exige copia) | Mantener legado |

## 7. Decisiones del propietario

1. **Nombre de la sede de Taemoi.** Recomendación: «Umbrete» (dirección pública: Calle Parada de la
   Cigüeña 36). Alternativa: «Sede principal».
2. **Qué incluye el registro de acciones del propietario.** Recomendación: operaciones con sesión
   (altas, cambios, bajas con 2xx) y lecturas de salud; sin 4xx/5xx, sin anónimas y sin payload crudo
   (solo resumen). El administrador de plataforma ve todo.
3. **Quién es administrador de plataforma y si puede entrar en una escuela.** Recomendación: marca puesta
   por SQL fuera del repositorio; sin acceso a datos de escuelas salvo membresía concedida por el propietario.
4. **Alcance del responsable de sede.** Recomendación: tesorería y convocatorias de los alumnos de sus
   sedes, salud de sus alumnos, catálogo en lectura, eventos no.
5. **Alcance del profesor.** Recomendación: lectura de sus grupos y alumnos (con teléfono de contacto),
   asistencia, salud de sus alumnos (decisión 7) y ver aptos; sin cobros, sin editar fichas ni grados.
6. **Vínculo familia–alumno por email.** Recomendación: Taemoi (marca `comportamiento_legado_taemoi`)
   conserva el acceso por email además de `usuario_alumno`; las escuelas nuevas, solo vínculo explícito.
   Quitar el email en Taemoi cuando el postcheck dé 0 diferencias durante un tiempo.
7. **`relacion` en el relleno.** Recomendación: por edad (18), corregible a mano.
8. **Qué puede el administrador.** Recomendación: sedes, aforo y asignación de profesores sí; dar o quitar
   PROPIETARIO/ADMINISTRADOR, solo el propietario.
9. **Alumno visible para un responsable de sede.** Recomendación: sede principal o cualquier grupo en sus sedes.
10. **Tarjetas del panel que hoy redirigen al gestor.** Recomendación: ocultarlas.

### Decisiones tomadas (14/10/2026)

- Nombres en pantalla: «Superadmin» (plataforma), «Propietario», «Gestor» (rol ADMINISTRADOR, hoy
  ROLE_MANAGER), «Responsable de sede», «Profesor», «Familia». Los códigos internos no cambian.
- D1: sede de Taemoi «Umbrete». D2: según la recomendación. D6, D7, D8, D9, D10: según la recomendación.
- D3 (cambia la recomendación): el superadmin tiene **acceso de soporte de solo lectura a todas las
  escuelas**, sin membresía. Cada acceso queda registrado (quién, cuándo, escuela) y lo ve el propietario
  en su registro de acciones; el contrato de encargo con cada escuela lo recoge.
- D4 (cambia): el responsable de sede gestiona sus sedes **sin tesorería ni cobros**.
- D5 (cambia): el profesor, además de consulta, asistencia, salud y aptos, **gestiona los grados de sus
  alumnos** (exámenes); sigue sin cobros ni edición de fichas. Comprobar qué genera hoy un cambio de grado
  (productos, convocatorias) antes de abrirlo.

## 8. Entregas en orden

Despliegue 3 = V31 + entregas 1–9 (pueden ir en varias versiones; V31 va con la 1). Despliegue 4 = entrega 10.

| # | Entrega | Nivel | Pruebas | UI al propietario |
|---|---|---|---|---|
| 0 | `precheck-v31.sql` y ejecución en producción | medio | Fixtures MySQL con cada caso de §5 | — |
| 1 | V31 + entidades `Sede`, `MembresiaSede`, `AsignacionProfesor`, `UsuarioAlumno`, marca de plataforma; grupo escribe `sede_id`; quitar mapeo `Usuario.roles` | careful | Migración en MySQL de prueba, dos veces y tras fallo a medias; reglas R1–R4 una por una | — |
| 2 | Cambio a `usuario_alumno`: control de acceso, `/user/alumnos`, puerta de login, registro, Google, recordatorio; respaldo por email bajo la marca (D6) | careful | Equivalencia email ↔ vínculo; las 22 rutas de familia en WebMvc; `postcheck-v31.sql` | — |
| 3 | Administrador de plataforma: autoridad, sesión sin membresía, `/api/plataforma/errores`; auditoría del propietario filtrada (D2) | careful | Aislamiento: escuela 2 no ve la 1; plataforma ve ambas; test de arquitectura del repositorio | — |
| 4 | Frontend auditoría: «Registro de acciones» para el propietario, pantalla de errores para plataforma, sin alerta | medio | Specs de componentes y rutas | **Sí** |
| 5 | Autoridades nuevas que fallan cerradas, `AlcanceSesion`, bean `@acceso`, test de matriz | careful | Matriz por endpoint y rol; Taemoi: mismas respuestas para los tres roles actuales | — |
| 6 | Abrir endpoints al responsable de sede con filtro por sede (alumnos/grupos/turnos; preinscripciones/convocatorias; sin tesorería por D4): dos PR | careful | Por módulo: ve lo suyo, 403/vacío en lo ajeno, por id y en listados | — |
| 7 | Abrir endpoints al profesor (solo lectura por grupo, asistencia, salud) | careful | Igual que 6 | — |
| 8 | API de sedes, miembros (rol, cargo, sedes) y asignación de profesores; `GET /api/auth/permisos` | careful | Servicio y WebMvc; D8 | — |
| 9 | Frontend: sedes, miembros, asignación de profesores, selector de escuela, filtro de sede en alumnos, grupos, preinscripciones, convocatorias y tesorería; vistas de profesor | medio | Specs; e2e por rol | **Sí** (antes de construir) |
| 10 | `postcheck-v31.sql` limpio → V32 con guarda + código sin `Usuario.roles`, `Usuario.alumno`, `Alumno.usuario` | careful | Guarda con y sin placeholder; precheck que detiene; arranque con `ddl-auto=validate` | — |

Pantallas existentes afectadas: `listado-alumnos`, `listado-grupos`, `crear/editar-grupo` (campo sede),
`crear/editar-turno`, `preinscripciones-admin`, `listado-convocatorias`, `tesoreria-cobros`,
`vista-principal-admin` (estadísticas según alcance), `configuracion-sistema` (roles → Miembros; el límite
queda como aforo por defecto), `auditoria-sistema`. El portal de familia no cambia.

El alcance (`AlcanceSesion`) es el mismo que filtrará los avisos SSE por sede de la decisión 14.

## 9. Lista de despliegue (añadir a `checklist-despliegue.md` cuando se implemente)

- Antes de V31: copia de seguridad, `precheck-v31.sql` guardado; decidir D1 y D6.
- Tras V31: `postcheck-v31.sql`; marcar el administrador de plataforma por SQL fuera del repositorio.
- V32 nunca en el mismo despliegue que V31; antes, copia de seguridad (única vuelta atrás).
