# Entrega 8 (fase 3) — miembros, sedes y permisos de la sesión

Diseño para planificar · 20/10/2026 · base `feature/multi-escuela` en `13129fc3`. Solo diseño: no hay código ni
migraciones escritas. Rutas relativas a `src-api/src/main/java/com/taemoi/project/` (backend),
`src-frontend/src/app/` (frontend) y `src-api/src/main/resources/db/migration/` (migraciones). Parte de:
`fase3-sedes-permisos.md` §3 (matriz), §4 (diseño de permisos), §7 (D3, D8, D9) y §8 (paso 8),
`fase3-e7-profesor.md` (asignación de profesores, decisiones 208, 212-214) y `matriz-endpoints.md`.

Abreviaturas: P = PROPIETARIO (`ROLE_ADMIN`), G = GESTOR (`ROLE_MANAGER`, rol ADMINISTRADOR), R = RESPONSABLE_SEDE
(`ROLE_RESPONSABLE_SEDE`), Pr = PROFESOR (`ROLE_PROFESOR`), F = FAMILIA (`ROLE_USER`). «Al constructor» = lo decide
quien lo implemente. Las decisiones técnicas del diseñador (§4.3) están aceptadas; el constructor puede cambiarlas
solo donde se indica.

**Resumen.** La E8 es la API completa de sedes y miembros: listar el personal de la escuela, dar y quitar roles con
cargo, fijar las sedes de un responsable y los grupos de un profesor, invitar por correo a quien aún no tiene cuenta, y
crear, editar y desactivar sedes. Añade `GET /api/auth/permisos` (roles, sedes visibles y capacidades de la sesión) y
abre «Mis grupos» a propietario y gestor que también son profesores, limitado a sus grupos asignados. En pantalla solo
hay retoques pequeños: el enlace «Mis grupos» en el menú de gestión, «También en estos grupos» e invitación en la
sección «Profesores» de la ficha del grupo, y la página «Crea tu contraseña». Las pantallas Miembros y Sedes llegan en
la E9 con el diseño de la E8b. Sin migración prevista.

---

## 1. Intención y porqué

**Sierra Sur** (un club, seis sedes) necesita dar de alta a su personal sin pasar por SQL: profesores, responsables
de sede y gestores que no tienen alumno con su email y, por tanto, hoy no pueden crearse cuenta (§2.4). También
necesita editar sus sedes (aforo, datos de contacto, código de club) y asignar un profesor a varios grupos de una vez.

**Moiskimdo** (organización 1, producción, 800+ alumnos) tiene una sola sede y ningún profesor con cuenta, salvo el
propietario, que da clase en Umbrete. La E8 no debe cambiarle nada salvo lo listado en §8.

La idea: **una API de miembros y sedes que aplique la D8 en el servidor**, con invitaciones que nunca tomen una
cuenta existente, y un `GET /api/auth/permisos` que la E9 pueda usar para pintar menús y pantallas sin repetir reglas
en el frontend.

No hay ningún incidente real que lo motive. Cierra el paso 8 de `fase3-sedes-permisos.md` §8.

## 2. Estado actual (resumen del inventario)

### 2.1 Modelo de datos

| Qué | Dónde | Estado |
|---|---|---|
| Membresía | `membresia` (V20; `entities/Membresia.java:22`): `rol` ENUM de cinco valores, `cargo VARCHAR(100) NULL`, `activa`; clave única `(usuario_id, organizacion_id, rol)` | Una fila por rol. **No** es `EntidadOrganizacion` (`Membresia.java:14-21`): toda consulta filtra a mano. `rol`, `usuario_id` y `organizacion_id` son `updatable=false` |
| Cargo | `Membresia.java:75-81` (getter y setter) | Existe la columna, pero ningún código la lee, la escribe ni la expone |
| Sedes del responsable | `membresia_sede` (V31:187-197; `entities/MembresiaSede.java`): PK `(membresia_id, sede_id)`, FK con cascada | Sin `organizacion_id` ni restricción que impida mezclar escuelas: lo debe validar el servicio |
| Sede | `sede` (V31:63-78; `entities/Sede.java`, `EntidadOrganizacion`): nombre, dirección, localidad, código postal, teléfono, email, código de club, `aforo_turno` (NULL = límite de la escuela), `activa`; única `(organizacion_id, nombre)` | Sí tiene `@TenantId`. `SedeRepository`: `findAllByOrderByIdAsc`, `findByActivaTrueOrderByIdAsc`, `buscarEnEscuelaActiva` |
| Asignación de profesores | `asignacion_profesor` (V31:200-212): PK `(usuario_id, grupo_id)`, rol TITULAR/AYUDANTE, lleva `organizacion_id` | Sin `@TenantId`; el repositorio filtra explícito |
| Cuenta | `usuario` (`entities/Usuario.java:32`): email único global, `contrasena`, `auth_provider` LOCAL/GOOGLE, `reset_token_*` | Global entre escuelas; sin campo «activa» |
| Borrado de sede | FK sin cascada (RESTRICT) desde `grupo`, `tarifa_sede`, `entrega_caja` y `movimiento_caja` | Borrar una sede con historial falla en BD; lo coherente es desactivarla |

`RolMembresia` (`entities/RolMembresia.java`): `gestionadoPorApiDeRoles()` (l. 44) solo admite PROPIETARIO,
ADMINISTRADOR y FAMILIA, con el comentario de que RESPONSABLE_SEDE y PROFESOR los asignará la API de miembros
(entrega 8); `desdeAutoridad` (l. 58) rechaza RESPONSABLE_SEDE y PROFESOR; `sesionSoloProfesor` (l. 96) = tiene
PROFESOR y no tiene ADMIN, MANAGER ni USER.

### 2.2 Endpoints y pantallas de hoy

| Qué | Dónde | Estado |
|---|---|---|
| API de sedes, miembros y `GET /api/auth/permisos` | — | **No existen** |
| Lista de usuarios | `GET /api/admin/users` (`AdminController.java:132`), solo P | `rol` como cadena tipo `"[ROLE_ADMIN, ROLE_USER]"`; sin cargo, sedes ni estado |
| Roles | `PUT /api/admin/users/{userId}/roles` (`AdminController.java:154`) → `MembresiaServiceImpl.reemplazarAutoridades:130` | Solo P/G/F; **sin protección del último propietario** y con conjunto vacío aceptado por la API. No toca R ni Pr |
| Pantalla «Gestión de Roles» | `componentes/endpoints/configuracion-sistema/` (ruta `configuracion-sistema`, solo P) | Lista y cambia roles de cuentas que ya están en la escuela; no añade, no invita, no muestra R ni Pr |
| Asignación de profesores (E7-4) | `GrupoProfesoresController.java:29` + `services/AsignacionProfesorService.java` (READ_COMMITTED, `usuarios.bloquearPorId`, `asegurarMembresiaProfesor:192`) | Un grupo cada vez; 404 «Que se registre primero» sin cuenta; 409 si la membresía PROFESOR está desactivada |
| Sección «Profesores» | `componentes/endpoints/editar-grupo/` + `servicios/endpoints/grupo-profesores.service.ts` | Lista, cambio de rol, quitar, alta por email |
| Sede por defecto | `services/SedeService.sedePorDefecto():26-29` | La única sede activa; vacío con 0 o con más de una. La usan `GestionPorSede.sedeDeAlta`, `GrupoServiceImpl` e `InicializadorDatos` |

### 2.3 «Mis grupos» y el alcance

- `IdentificadorSesionJwt.alcanceDe` (`organizacion/IdentificadorSesionJwt.java:146`): ADMIN o MANAGER → alcance
  TOTAL sin consulta; R o Pr sin gestión → una consulta (`repositories/AlcanceRepository`, constante `ALCANCE`); resto
  → NINGUNO. Se recalcula en cada petición. **P o G con asignaciones no tienen `grupos()`**.
- `@acceso.alumnoDeMisGrupos` y compañía (`acceso/AccesoRecursos.java:201-220`) pasan por `comoProfesor` (l. 259),
  que exige `!alcance.total()`: hoy P+Pr y G+Pr dan 403 en `/api/profesor/**` (D7-10).
- Frontend: `veMisGrupos(roles)` (`servicios/authentication/authentication.service.ts:27`) = PROFESOR sin ADMIN ni
  MANAGER; `guards/mis-grupos.guard.ts` manda a `/adminpage` a gestión con PROFESOR. El header pinta «Mi caja» con
  cualquier `ROLE_PROFESOR` y «Mis grupos» solo con `veMisGrupos`.
- Lista blanca de la sesión solo-profesor (`config/SesionSoloProfesorAuthorizationManager.listaBlanca()`) y mapa
  `PROFESOR_SESION_180` de `MatrizPermisosTest`: lo imprescindible de sesión bajo `/api/auth/**`.

### 2.4 Cómo se crea una cuenta hoy

`POST /api/auth/signup` (sin pantalla; crea FAMILIA), registro con alumno existente (`RegistroServiceImpl`), Google con
alumnos del email (`OAuth2UserServiceImpl`), el arranque (`InicializadorDatos.generarUsuarios:230`) y SQL. **P y G no
pueden crear cuentas ni invitar**: solo vinculan cuentas existentes por email (E7-4). Un profesor o gestor nuevo sin
alumno con su email no tiene cómo entrar.

### 2.5 Registro de acciones

`config/AuditoriaRequestFilter` apunta todo `/api/**` salvo `/api/auth/**`; `EscrituraConRegistroPropio`
(`AuditoriaRequestFilter.java:59`) evita la fila doble de las rutas con registro explícito (decisión 214);
`CLAVES_REDACTADAS_POR_RUTA` (l. 119) redacta campos del cuerpo por ruta. Registro explícito con
`RegistroAccionesProfesor` (módulo `profesor`), copia de `RegistroAccionesCaja`.

## 3. Objetivos, no-objetivos y anti-objetivos

**Objetivos**
1. API de sedes: listar, ver, crear, editar y activar o desactivar, con registro (D8-2).
2. API de miembros: listar el personal (y, si se pide, las familias), ver una ficha, dar, reactivar y quitar roles
   con cargo, fijar sedes del responsable y grupos del profesor, con D8 y el último propietario (D8-4 a D8-12).
3. Invitaciones por correo a emails sin cuenta, con la marca de la escuela y enlace de 7 días (219).
4. `GET /api/auth/permisos` con roles, sedes visibles y capacidades (D8-14).
5. «Mis grupos» para P y G con grupos asignados, limitado a esos grupos (217).
6. Asignar un profesor a varios grupos de una vez desde «Profesores» (218).

**No-objetivos**
- Pantallas Miembros y Sedes (E9, con el diseño de la E8b).
- Selector de sede en altas de alumno y de grupo (E9; ver D8-3).
- Colores y pantalla de marca de la escuela (E8b).
- Lectura de soporte del superadmin sobre miembros y sedes (D8-18).
- Cambiar `GET/PUT /api/admin/users` o «Gestión de Roles» (D8-17), salvo D8-9 si se aprueba.
- Abrir la API de miembros o de sedes a R, Pr o F (E9).
- Vincular familias desde esta API.

**Anti-objetivos**
- Que una invitación tome una cuenta existente o le cambie la contraseña.
- Que un gestor dé o quite PROPIETARIO o ADMINISTRADOR (D8).
- Que una escuela se quede sin propietario activo o sin sedes activas.
- Que P o G vean en «Mis grupos» más que los grupos que tienen asignados.
- Que el registro guarde email, nombre, cargo, dirección, teléfono o el token de la invitación.
- Que Moiskimdo cambie fuera de §8 o que una casilla existente de la matriz cambie.

## 4. Decisiones

### 4.1 Del propietario

- **Decisión 216 (20/10/2026).** La E8 es la API completa (sedes, miembros, `GET /api/auth/permisos`, asignación de
  profesores también a varios grupos) más retoques pequeños de pantalla. Las pantallas Miembros y Sedes van en la E9
  con el diseño de la E8b.
- **Decisión 217 (20/10/2026, corrige la D7-10).** «Mis grupos» se abre a P y G con grupos asignados como profesores:
  ven solo esos grupos (filtro por asignaciones, nunca por su alcance total). El enlace va en el menú de gestión bajo
  «Mi caja»; sin asignaciones no se ve. Los grados siguen subiendo solo al cobrar el pase.
- **Decisión 218 (20/10/2026).** Asignar un profesor a varios grupos de una vez: API y retoque en «Profesores» de la
  ficha del grupo («También en estos grupos», un guardado, todo o nada).
- **Decisión 219 (20/10/2026).** Invitaciones por correo: P o G añaden un email sin cuenta; se crea la cuenta con ese
  rol y llega un correo con la marca de la escuela y un enlace para poner contraseña (caduca a los 7 días, se puede
  reenviar). En «Profesores», «Enviar invitación» si no hay cuenta.
- **Decisión 136 (tomada en esta E8, 20/10/2026).** La marca de cada escuela (logo; con la E8b, colores) la edita solo
  el superadmin. Es su única escritura en una escuela, acotada a la marca y registrada. El propietario la ve y no la
  cambia. Pantalla y colores, en la E8b.
- **Decisión 220 (20/10/2026).** Boceto aprobado (§5) con las decisiones del diseñador (§4.2 y §4.3).
- **Siguen vigentes:** D3 (superadmin de solo lectura salvo la marca); D8 (el gestor gestiona sedes, aforo y
  asignación de profesores; dar o quitar PROPIETARIO/ADMINISTRADOR, solo el propietario); D9; 65 (responsable sin sedes
  no ve ninguna); 180 y 184 (sesión solo-profesor con lista blanca); 208 y D7-21 (quitar una asignación conserva la
  membresía); 212 y 213; 214 (una fila de auditoría por escritura con registro explícito); D2.

### 4.2 Del diseñador (aceptadas con el boceto)

- **Menú.** «Mis grupos» en el menú de gestión, bajo «Mi caja», solo con la capacidad `MIS_GRUPOS`; en móvil, el mismo
  enlace en el menú de cuenta (§5.1).
- **«También en estos grupos».** En el alta de «Profesores», chips marcables con los demás grupos de la escuela y un
  solo botón «Añadir a N grupos»; el mismo rol de asignación en todos (§5.2, D8-12).
- **Invitación en el alta.** Sin cuenta, el formulario pide nombre y apellidos y ofrece «Enviar invitación»; con el
  rol de profesor desactivado, «Reactivar y añadir» (§5.2).
- **Pendiente.** Un profesor invitado que aún no ha creado su contraseña sale con el chip «Invitación pendiente» y
  «Reenviar».
- **Correo y página.** Correo con logo y nombre de la escuela y botón «Crear mi contraseña»; página «Crea tu
  contraseña» que reutiliza la de restablecer (§5.3).

### 4.3 Del diseñador, técnicas (aceptadas)

Ninguna cambia lo que ve Moiskimdo fuera de §8. Lo marcado «al constructor» lo fija quien implemente y lo anota en la
PR. `membresia`, `asignacion_profesor` y `membresia_sede` no son `EntidadOrganizacion`: toda consulta a ellas lleva
`organizacion_id` explícito; `sede` sí es tenant.

| # | Decisión | Por qué | Pieza |
|---|---|---|---|
| D8-1 | Rutas nuevas `/api/sedes/**` y `/api/miembros/**` con regla de URL `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')`; R, Pr, F y SOLO_PLATAFORMA, DENIEGA hasta la E9. Más `GET /api/auth/permisos` | Fuera de `/api/admin/**` (solo P) porque G también entra (D8); prefijos nuevos que nacen cerrados al resto | E8-1, E8-2, E8-3 |
| D8-2 | Sedes: `GET` lista (incluye inactivas), `GET {id}`, `POST`, `PUT {id}` (nombre, direccion, localidad, codigo_postal, telefono, email, codigo_club_federacion, aforo_turno; `null` = límite de la escuela), `PUT {id}/activa {activa}`. Sin `DELETE`. 409 si el nombre se repite en la escuela; 409 «La escuela se quedaría sin sedes activas.» al desactivar la última activa. Longitudes = las de las columnas (400). Desactivar no quita a sus responsables (D5-5) | FK RESTRICT desde grupo, tarifa, entrega y movimiento; el historial se conserva | E8-2 |
| D8-3 | Una segunda sede activa deja vacía `SedeService.sedePorDefecto()`. En la E8 no se cambia ese comportamiento: el constructor de E8-2 documenta en la PR qué pasa hoy en el alta de alumno y de grupo con dos sedes activas. Sin pantalla para crear sedes hasta la E9 | Riesgo para altas y grupos sin selector hasta la E9 (§9) | E8-2 |
| D8-4 | `GET /api/miembros?q&rol&incluirFamilias=false&page&size`: por defecto solo personal (cuentas con alguna membresía no FAMILIA en la escuela, activa o no); con `incluirFamilias`, también familias. Paginado y búsqueda en servidor; orden apellidos, nombre, id. Fila: `usuarioId, nombre, apellidos, email, authProvider, invitacionPendiente, roles[{rol, activa, cargo}], sedes[{id, nombre}]` (de su RESPONSABLE_SEDE), `numeroGrupos` | Moiskimdo tiene 800+ familias: la lista completa en cliente no escala | E8-3 |
| D8-5 | `GET /api/miembros/{usuarioId}`: lo anterior + `grupos[{grupoId, nombre, sedeId, rol TITULAR\|AYUDANTE}]`. 404 si la cuenta no tiene membresía en la escuela | No revela cuentas de otras escuelas | E8-3 |
| D8-6 | `POST /api/miembros {email, rol, cargo?, sedeIds?, grupos?[{grupoId, rol}], nombre?, apellidos?, reactivar?}`. `sedeIds` para RESPONSABLE_SEDE (al menos una); `grupos` para PROFESOR. Cuenta existente → asegura la membresía del rol (la crea; si está desactivada, 409 «La cuenta tiene el rol de … desactivado en esta escuela» salvo `reactivar=true`) y **añade** asignaciones (en un grupo ya asignado cambia el rol). Sin cuenta: con nombre y apellidos crea cuenta + membresía + asignaciones + invitación (D8-13); sin ellos, 404 «No hay ninguna cuenta con ese email.» (la pantalla pide nombre y apellidos). Una transacción, todo o nada. FAMILIA no se da por esta API (400 «La familia se vincula desde la ficha del alumno.»). `rol` ∈ PROPIETARIO, ADMINISTRADOR, RESPONSABLE_SEDE, PROFESOR | Un solo alta para cuentas existentes e invitaciones; la familia tiene su propio vínculo con el alumno | E8-3, E8-4 |
| D8-7 | `PUT /api/miembros/{usuarioId}/roles/{rol} {cargo?}` da o reactiva el rol y fija el cargo (texto libre ≤100, `membresia.cargo`, por rol). `DELETE .../roles/{rol}` lo desactiva (`activa=false`, nunca borra). Desactivar PROFESOR conserva sus asignaciones (sin efecto: el alcance exige membresía activa) y reactivarlo las recupera; desactivar RESPONSABLE conserva sus sedes | Baja lógica, como hoy en `reemplazarAutoridades`; el rol no se cambia en sitio | E8-3 |
| D8-8 | D8 aplicada: G no da ni quita PROPIETARIO ni ADMINISTRADOR (403 «Solo el propietario da o quita los roles de propietario y gestor.»); G sí gestiona RESPONSABLE_SEDE, PROFESOR, cargos, sedes y grupos | D8 | E8-3 |
| D8-9 | Último propietario: no se puede desactivar la última membresía PROPIETARIO activa de la escuela (409 «La escuela se quedaría sin propietario.»), con bloqueo para la concurrencia (D8-19). Aplicarla también a `PUT /api/admin/users/{id}/roles` es una **pregunta abierta** (§11.1; cambio visible para Moiskimdo); hasta la respuesta, el plan la marca pendiente | Hoy nada lo impide (§2.2) | E8-3 |
| D8-10 | `PUT /api/miembros/{usuarioId}/sedes {sedeIds}` reemplaza las sedes del responsable. Exige membresía RESPONSABLE_SEDE (409 si no la tiene); sedes de la escuela activa (400 «La sede no existe en la escuela.»); lista vacía, 400 | `membresia_sede` no impide mezclar escuelas: lo valida el servicio. Responsable sin sedes = no ve nada (65) | E8-3 |
| D8-11 | `PUT /api/miembros/{usuarioId}/grupos {asignaciones[{grupoId, rol}]}` reemplaza el conjunto, todo o nada. Exige PROFESOR activa (409); grupo ajeno, 404 «El grupo no existe.» | Mismo mensaje que la E7-4 | E8-3 |
| D8-12 | El retoque de la ficha del grupo usa `POST /api/miembros` con rol PROFESOR y `grupos` = este + los marcados (mismo rol de asignación en todos). Cambiar rol y quitar siguen con las rutas de la E7-4, que no cambian (su 409 sigue igual) | Una sola ruta de alta con todo o nada | E8-3, E8-6 |
| D8-13 | Invitación: cuenta LOCAL sin contraseña («pendiente» = LOCAL y sin contraseña). Sin migración si la columna admite NULL y el login la rechaza; si no, V31_9 aditiva (al constructor de E8-4: comprobarlo primero). Token de un solo uso, 7 días, reutilizando el mecanismo de restablecer contraseña o uno propio (al constructor); nunca en el registro ni en logs. Correo con `MarcaEscuelaService` (nombre y logo de la escuela) y texto según el rol; misma base de enlaces que restablecer contraseña. `POST /api/miembros/{usuarioId}/invitacion` reenvía (token nuevo, invalida el anterior) solo si sigue pendiente (409 si no). Crear la contraseña inicia sesión en esa escuela; la página «Crea tu contraseña» reutiliza la de restablecer con su texto. Una cuenta existente nunca recibe invitación ni cambio de contraseña (sin toma de cuentas). Entrar con Google con ese email: al constructor comprobar el comportamiento; recomendación, aceptarlo y que deje de estar pendiente. Si hace falta una ruta pública nueva, `permitAll` y ANONIMO PERMITE en la matriz | 219 | E8-4 |
| D8-14 | `GET /api/auth/permisos` → `{roles[RolMembresia activos], esAdminPlataforma, sedes[{id, nombre, activa}] visibles (todas con alcance total; las suyas para R), variasSedes, tieneGrupos, capacidades[...]}`. Capacidades: `MIEMBROS`, `SEDES`, `ASIGNAR_PROFESORES` (P y G); `DAR_GESTION` (P); `MIS_GRUPOS` (sesión solo-profesor siempre; P, G y F+Pr solo con grupos); `MI_CAJA` (`ROLE_PROFESOR`). En la sesión solo-profesor entra en la lista blanca y en `PROFESOR_SESION_180` y devuelve solo PROFESOR; SOLO_PLATAFORMA PERMITE con roles vacíos. `/api/auth/roles` no cambia. `/api/auth/**` no se audita | Una sola fuente para menús y guards; la autorización real sigue en el servidor | E8-1 |
| D8-15 | (217) El alcance de P y G con `ROLE_PROFESOR` calcula también sus grupos (una consulta solo para ellos; los demás, sin cambios). `/api/profesor/**` exige `ROLE_PROFESOR` y filtra siempre por `alcance.grupos()`; deja de excluir ADMIN/MANAGER; `@acceso.*DeMisGrupos` dejan de usar `!alcance.total()` y miran solo los grupos. Ninguna casilla de la matriz cambia: las columnas GESTOR y PROPIETARIO son sesiones sin `ROLE_PROFESOR` y siguen DENIEGA. Tests nuevos para P+Pr y G+Pr (solo sus grupos; alumno de otro grupo, 403; sin asignaciones, lista vacía; salud registrada igual). Frontend: `veMisGrupos` = capacidad `MIS_GRUPOS`; `misGruposGuard` deja pasar a gestión con `MIS_GRUPOS` y, sin ella, manda a `/adminpage` | 217: filtro por asignaciones, nunca por alcance total | E8-1, E8-5 |
| D8-16 | Registro de acciones: módulo `miembros` y módulo `sedes` (§7); asignaciones con las acciones ya existentes del módulo `profesor`. Solo ids, enumeraciones y booleanos; nunca email, nombre, cargo, dirección ni teléfono. Misma transacción. Una ruta entra en `AuditoriaRequestFilter.EscrituraConRegistroPropio` solo si **todos** sus caminos 2xx registran, con su caso en `AuditoriaRequestFilterRegistroPropioTest` y en un IT contra MySQL. Las rutas con datos personales en el cuerpo entran en `CLAVES_REDACTADAS_POR_RUTA` (email, nombre, apellidos, cargo, direccion, telefono…) | D2 y decisión 214 | E8-2, E8-3, E8-4 |
| D8-17 | `GET/PUT /api/admin/users` y la pantalla «Gestión de Roles» no cambian (salvo D8-9 si se aprueba). La E9 la sustituye | Moiskimdo la usa hoy | — |
| D8-18 | Superadmin: nada nuevo en la E8 (su lectura de soporte de miembros y sedes queda fuera); la marca, solo superadmin (136) | D3 | — |
| D8-19 | Concurrencia: `READ_COMMITTED` y bloqueo de la cuenta (como `AsignacionProfesorService`); al quitar un propietario, bloqueo de las filas PROPIETARIO de la escuela (D8-9) | Unicidad `(usuario, organizacion, rol)` y último propietario sin carreras | E8-3, E8-4 |
| D8-20 | Errores con `{"mensaje"}`: 400 validación, 403 permiso, 404 ajeno o inexistente, 409 estado | `GlobalExceptionHandler` ya lo hace así | Todas |

## 5. Pantallas (boceto aprobado)

Lo visual (estilos, iconos, chips, colores) es de quien construya el frontend, con las skills `impeccable` y
`frontend-design`, en el estilo actual del panel. Este apartado fija qué hay, no cómo se ve. Móvil primero (375 px);
en escritorio, el mismo contenido con más aire.

### 5.1 Menú de gestión

```
┌─────────────────────────────────────┐
│  …                                  │
│  Solicitudes                        │
│  Mi caja                            │
│  Mis grupos                         │  ← nuevo; solo con MIS_GRUPOS
└─────────────────────────────────────┘

Móvil: el mismo enlace «Mis grupos» en el menú de cuenta, junto a «Mi caja».
```

### 5.2 Ficha del grupo › Profesores

```
┌─────────────────────────────────────┐
│  Profesores                         │
│  ┌───────────────────────────────┐  │
│  │ Carlos Díaz                   │  │
│  │ carlos@ejemplo.test           │  │
│  │ [Titular]           [ Quitar ]│  │
│  ├───────────────────────────────┤  │
│  │ Lucía Martín                  │  │
│  │ lucia@ejemplo.test            │  │
│  │ [Ayudante] [Invitación        │  │
│  │  pendiente]  [ Reenviar ]     │  │
│  │                     [ Quitar ]│  │
│  └───────────────────────────────┘  │
│                                     │
│  Añadir profesor                    │
│  Email [_____________________]      │
│  Rol   ( Titular ▾ )                │
│  También en estos grupos            │
│  ( Infantil martes ) ( Adultos )    │  ← chips marcables, varias líneas
│  ( Competición ✓ )                  │
│               [ Añadir a 2 grupos ] │
└─────────────────────────────────────┘

Email sin cuenta:
│  No hay ninguna cuenta con ese      │
│  email. Te pedimos su nombre para   │
│  invitarle.                         │
│  Nombre    [__________________]     │
│  Apellidos [__________________]     │
│                [ Enviar invitación ]│

Rol de profesor desactivado:
│  Tiene el rol de profesor           │
│  desactivado en esta escuela.       │
│              [ Reactivar y añadir ] │
```

- A 375 px los chips van en varias líneas y los campos apilados.
- «Añadir a N grupos» cuenta este grupo y los marcados; el mismo rol en todos (D8-12). Todo o nada.
- Cambiar el rol y quitar siguen como en la E7-6 (rutas de la E7-4).

### 5.3 Correo de invitación y «Crea tu contraseña»

```
Correo:
┌─────────────────────────────────────┐
│  [logo]  {nombre de la escuela}     │
│                                     │
│  Hola, {nombre}.                    │
│  Te han dado acceso como {rol} en   │
│  {escuela}.                         │
│                                     │
│       [ Crear mi contraseña ]       │
│                                     │
│  El enlace caduca en 7 días.        │
└─────────────────────────────────────┘

Página «Crea tu contraseña»:
┌─────────────────────────────────────┐
│  Crea tu contraseña                 │
│  lucia@ejemplo.test · {escuela}     │
│                                     │
│  Contraseña nueva [_____________]   │
│  Repítela         [_____________]   │
│                                     │
│     [ Crear contraseña y entrar ]   │
└─────────────────────────────────────┘

Enlace caducado o ya usado:
│  Este enlace ya no sirve.           │  ← texto al constructor, claro
│        [ Pide que te la reenvíen ]  │
```

Sin logo, solo el nombre de la escuela (decisión 211). El texto del correo cambia según el rol (al constructor).

## 6. Permisos por rol y por ruta

### 6.1 Tabla rol × acción

`G` = solo sus grupos; `—` = no.

| Acción | P | G (gestor) | R | Pr | F |
|---|---|---|---|---|---|
| Ver, crear, editar, activar o desactivar sedes | Sí | Sí | — (hasta la E9) | — | — |
| Listar miembros y ver su ficha | Sí | Sí | — | — | — |
| Dar o quitar PROPIETARIO o ADMINISTRADOR | Sí | **—** (D8, 403) | — | — | — |
| Dar o quitar RESPONSABLE_SEDE o PROFESOR; cargo | Sí | Sí | — | — | — |
| Fijar sedes de un responsable | Sí | Sí | — | — | — |
| Fijar grupos de un profesor; asignar a varios grupos | Sí | Sí | — | — | — |
| Invitar por correo y reenviar | Sí | Sí | — | — | — |
| `GET /api/auth/permisos` | Sí | Sí | — (R solo no abre sesión) | Sí | Sí |
| «Mis grupos» | G con asignaciones (217) | G con asignaciones (217) | — | G | — |
| Marca de la escuela | — (la ve) | — | — | — | — |

Superadmin: solo la marca (136); `GET /api/auth/permisos` con roles vacíos. Cuentas mixtas F+Pr y R+Pr, como en la E7
(sus grupos). P+Pr y G+Pr, sus grupos en «Mis grupos» (D8-15).

### 6.2 Rutas nuevas (nombres al constructor)

Columnas de la matriz: ANONIMO, FAMILIA, GESTOR, PROPIETARIO, SOLO_PLATAFORMA, RESPONSABLE_SEDE, PROFESOR. Cada `GET`
lleva además su línea `HEAD` igual.

**Sedes** (`/api/sedes/**`; regla de URL `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')`):

| Ruta | Expresión | Matriz |
|---|---|---|
| `GET /api/sedes` | `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')` | GESTOR y PROPIETARIO PERMITE; resto DENIEGA |
| `GET /api/sedes/{id}` | ídem | ídem |
| `POST /api/sedes` | ídem | ídem |
| `PUT /api/sedes/{id}` | ídem | ídem |
| `PUT /api/sedes/{id}/activa` `{activa}` | ídem | ídem |

**Miembros** (`/api/miembros/**`; misma regla de URL). La restricción D8 sobre PROPIETARIO/ADMINISTRADOR la aplica el
servicio (403), no la URL, porque depende del rol del cuerpo o de la ruta:

| Ruta | Expresión | Matriz |
|---|---|---|
| `GET /api/miembros?q&rol&incluirFamilias&page&size` | `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')` | GESTOR y PROPIETARIO PERMITE; resto DENIEGA |
| `GET /api/miembros/{usuarioId}` | ídem | ídem |
| `POST /api/miembros` | ídem (+ D8-8 en el servicio) | ídem |
| `PUT /api/miembros/{usuarioId}/roles/{rol}` `{cargo?}` | ídem (+ D8-8) | ídem |
| `DELETE /api/miembros/{usuarioId}/roles/{rol}` | ídem (+ D8-8, D8-9) | ídem |
| `PUT /api/miembros/{usuarioId}/sedes` `{sedeIds}` | ídem | ídem |
| `PUT /api/miembros/{usuarioId}/grupos` `{asignaciones}` | ídem | ídem |
| `POST /api/miembros/{usuarioId}/invitacion` | ídem | ídem |

**Sesión:**

| Ruta | Expresión | Matriz |
|---|---|---|
| `GET /api/auth/permisos` | como `/api/auth/roles`, más SOLO_PLATAFORMA | FAMILIA, GESTOR, PROPIETARIO, SOLO_PLATAFORMA y PROFESOR PERMITE; ANONIMO y RESPONSABLE_SEDE DENIEGA (al constructor confirmar R contra `/api/auth/roles`) |

**Invitación (si hace falta ruta pública nueva, D8-13):** `permitAll` y ANONIMO PERMITE; nombre al constructor. Si
reutiliza `/api/auth/password/reset`, no hay línea nueva.

**Ninguna casilla existente cambia.** `/api/profesor/**` conserva sus líneas: las columnas GESTOR y PROPIETARIO son
sesiones sin `ROLE_PROFESOR` (D8-15). `/api/grupos/{grupoId}/profesores` y `/api/admin/users*` no cambian.

### 6.3 Las capas a la vez

1. Lista blanca: `GET /api/auth/permisos` en `config/SesionSoloProfesorAuthorizationManager.listaBlanca()`; ninguna
   ruta de `/api/sedes/**` ni `/api/miembros/**` entra.
2. Reglas de URL en `config/SecurityConfiguration.java` para `/api/sedes/**` y `/api/miembros/**` (ADMIN, MANAGER),
   antes de las del responsable de sede y del `denyAll` final; `GET /api/auth/permisos` con las de sesión.
3. `@PreAuthorize` en cada método y, en el servicio, D8-8, D8-9 y el filtro de la escuela activa.
4. `MatrizPermisosTest`: `GET`/`HEAD /api/auth/permisos` en `PROFESOR_SESION_180` con su porqué; las rutas nuevas, en
   `matriz-permisos.txt` con su nota en `matriz-endpoints.md`.
5. Rutas del profesor (D8-15): `@acceso.*DeMisGrupos` y `/api/profesor/**` sin la exclusión de ADMIN/MANAGER; la
   matriz no cambia y lo prueban los tests nuevos de P+Pr y G+Pr.

## 7. Registro de acciones

- **Automático:** `AuditoriaRequestFilter` sigue apuntando las peticiones `/api/**`; `/api/auth/**` (y con ella
  `GET /api/auth/permisos` y la ruta de crear contraseña si cae ahí) no se audita. Las rutas nuevas con registro
  explícito entran en `EscrituraConRegistroPropio` solo si todos sus caminos 2xx registran (decisión 214); si alguno
  responde 2xx sin cambiar nada, la ruta se queda fuera y la apunta el filtro genérico. Las que llevan datos personales
  en el cuerpo entran en `CLAVES_REDACTADAS_POR_RUTA`.
- **Explícito** (módulos `miembros` y `sedes`, copia de `RegistroAccionesProfesor`, `@Transactional(MANDATORY)`, misma
  transacción). Resumen **solo con ids**, enumeraciones y sí/no; nunca email, nombre, apellidos, cargo, dirección,
  teléfono ni token.

| Módulo | Acción | Resumen (ejemplo) | Pieza |
|---|---|---|---|
| `miembros` | `MIEMBRO_INVITADO` | `MIEMBROS MIEMBRO_INVITADO usuario=#21 rol=PROFESOR` | E8-4 |
| `miembros` | `INVITACION_REENVIADA` | `MIEMBROS INVITACION_REENVIADA usuario=#21` | E8-4 |
| `miembros` | `ROL_DADO` / `ROL_REACTIVADO` / `ROL_QUITADO` | `MIEMBROS ROL_DADO usuario=#21 rol=RESPONSABLE_SEDE` | E8-3 |
| `miembros` | `CARGO_CAMBIADO` | `MIEMBROS CARGO_CAMBIADO usuario=#21 rol=PROFESOR` (sin el texto) | E8-3 |
| `miembros` | `SEDES_CAMBIADAS` | `MIEMBROS SEDES_CAMBIADAS usuario=#21 sedes=#3,#4` (formato al constructor, solo ids) | E8-3 |
| `sedes` | `SEDE_CREADA` / `SEDE_EDITADA` | `SEDES SEDE_CREADA sede=#5` | E8-2 |
| `sedes` | `SEDE_DESACTIVADA` / `SEDE_ACTIVADA` | `SEDES SEDE_DESACTIVADA sede=#5` | E8-2 |
| `profesor` | `ASIGNACION_CREADA` / `ASIGNACION_ROL_CAMBIADO` / `ASIGNACION_QUITADA` / `MEMBRESIA_PROFESOR_CREADA` | Las de la E7-4, sin cambios | E8-3 |

## 8. Qué cambia para Moiskimdo

| # | Cambio | Para quién | Estado |
|---|---|---|---|
| 1 | El propietario ve «Mis grupos» con sus grupos de Umbrete (ya es profesor real allí; no se tocan sus asignaciones) | P | Aceptado (217) |
| 2 | «Profesores» de la ficha del grupo con «También en estos grupos», invitación y reactivar | P y G | Aceptado (218-220) |
| 3 | Correo nuevo de invitación con su marca | Invitados | Aceptado (219) |
| 4 | API nueva de sedes, miembros y permisos | — | Sin pantalla |
| 5 | Último propietario en `PUT /api/admin/users/{id}/roles`: 409 si deja la escuela sin propietario | P | Aceptado (221) |

Lo demás sigue igual: «Gestión de Roles», tesorería, listados y el resto de pantallas. Los dorados
(`MoiskimdoTesoreriaIdenticaIntegrationTest` y el JSON dorado de alumnos) pasan sin tocarlos.

## 9. Riesgos

| # | Riesgo | Mitigación | Test (nombre orientativo) |
|---|---|---|---|
| 1 | Toma de cuenta por invitación | Una cuenta existente nunca recibe invitación ni cambio de contraseña (D8-13); el alta sobre una cuenta existente solo añade membresía | `invitarEmailConCuenta_noTocaContrasenaNiEnviaCorreo` |
| 2 | Token filtrado (logs, registro, URL en auditoría) | Token de un solo uso y 7 días; nunca en el registro ni en logs; `/api/auth/**` sin auditar; reenviar invalida el anterior | `tokenDeInvitacionNoApareceEnAuditoriaNiLogs`, `reenviarInvalidaElTokenAnterior` |
| 3 | Enumeración de emails | Ya existe en la E7-4; solo P y G; la ficha da 404 si la cuenta no es de la escuela (D8-5) | `fichaDeCuentaDeOtraEscuela_404` |
| 4 | Quedarse sin propietario | D8-9 con bloqueo de las filas PROPIETARIO (D8-19) | `quitarUltimoPropietario_409`, `dosQuitasALaVez_quedaUnPropietario` |
| 5 | Mezclar sedes de otra escuela en `membresia_sede` | Validación en el servicio (D8-10), 400 | `sedeDeOtraEscuela_400` |
| 6 | Tablas sin `@TenantId` | `organizacion_id` explícito en toda consulta a `membresia`, `asignacion_profesor` y `membresia_sede` | `miembrosNoSeVenEntreEscuelas` |
| 7 | Coste de la consulta extra de alcance | Solo P y G con `ROLE_PROFESOR` (D8-15); los demás sin cambios | `alcanceDeGestionSinProfesor_noConsulta` |
| 8 | Sesión R+Pr: el responsable abriría por sede | Filtro solo por `alcance.grupos()` (D8-15) | `responsableMasProfesorNoVeSusSedes` (sigue en verde) |
| 9 | Segunda sede activa y `sedePorDefecto` vacío | D8-3: documentado en la PR de E8-2, sin cambiar el comportamiento; sin pantalla de sedes hasta la E9 | `dosSedesActivas_altaDeGrupo_comportamientoActual` |
| 10 | FAMILIA tocada por la API de miembros | 400 al darla (D8-6); la lista la oculta por defecto (D8-4) | `darFamilia_400`, `listaPorDefectoSinFamilias` |
| 11 | El cargo es texto libre | Fuera del registro (§7); longitud ≤100 | `cargoNoApareceEnElRegistro` |
| 12 | Correos con enlaces `moiskimdo.es` | Pendiente conocido de dominio único: la misma base de enlaces que restablecer contraseña (D8-13) | — |
| 13 | Doble envío del alta | Todo o nada y bloqueo de la cuenta (D8-19) | `dosAltasALaVezMismoEmail_sin500` |
| 14 | P o G en rutas del profesor viendo más que sus grupos | `@acceso.*DeMisGrupos` y `/api/profesor/**` solo por `alcance.grupos()` (D8-15) | `propietarioProfesor_soloSusGrupos`, `gestorProfesor_alumnoDeOtroGrupo_403`, `gestionProfesorSinAsignaciones_listaVacia` |

## 10. Troceado en piezas

| Pieza | Contenido | Depende de | Riesgo |
|---|---|---|---|
| **E8-1** backend | `GET /api/auth/permisos` y «Mis grupos» para P y G (D8-14, D8-15) | — | Alto (permisos); `savvy-careful` |
| **E8-2** backend | API de sedes y su registro (D8-2, D8-3) | — | Alto (datos de la escuela); `savvy-careful` |
| **E8-3** backend | Miembros: lista, ficha, alta de cuentas existentes con grupos, dar y quitar roles con D8 y último propietario, cargo, sedes y grupos (D8-4 a D8-12, D8-16, D8-19) | — | Alto (permisos, tablas sin `@TenantId`); `savvy-careful` |
| **E8-4** backend | Invitaciones (D8-13) | E8-3 | Alto (cuentas y tokens); `savvy-careful` |
| **E8-5** frontend | Servicio de permisos, menú y guard de «Mis grupos» | E8-1 | Medio; `savvy-medium` |
| **E8-6** frontend | Retoque de «Profesores» y página «Crea tu contraseña» | E8-3, E8-4 | Medio (visible para Moiskimdo); `savvy-medium` en opus |

Orden: E8-1, E8-2, E8-3, E8-4, E8-5, E8-6. Migración: ninguna prevista (V31_9 solo si E8-4 la necesita). Despliegue:
lo decide el propietario al aprobar el plan (propuesta: una sola PR al final, como la E7). Plan en `fase3-e8-plan.md`.

## 11. Preguntas abiertas

1. **Último propietario en el endpoint viejo (D8-9).** ¿Se aplica también a `PUT /api/admin/users/{id}/roles`?
   Cambia lo que ve Moiskimdo en «Gestión de Roles» (un 409 donde hoy se acepta). **Cerrada: sí (decisión 221,
   20/10/2026).**
2. **Forma de despliegue.** Recomendación: una sola PR al final, como la E7.
