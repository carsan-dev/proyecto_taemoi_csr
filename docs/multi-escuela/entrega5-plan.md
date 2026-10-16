# Entrega 5b/5c (fase 3) — autoridades que fallan cerradas, `AlcanceSesion` y bean `@acceso`

Plan de implementación · 16/10/2026 · base `feature/multi-escuela` en `9c8e18fb`. Solo lectura: no hay código
escrito. Rutas relativas a `src-api/src/main/java/com/taemoi/project/` (backend), `src-api/src/test/java/com/taemoi/project/`
(tests) y `src-frontend/src/app/` (frontend). Diseño de partida: `fase3-sedes-permisos.md` §3, §4, §7 y §8 fila 5;
red de seguridad: `matriz-endpoints.md`.

**Resumen.** La 5b cambia dos líneas del mapeo de roles y cierra la puerta de entrada a quien solo tenga esos roles.
Para Taemoi no cambia nada: PROPIETARIO, ADMINISTRADOR y FAMILIA conservan sus autoridades, y la matriz lo demuestra
porque sus columnas no cambian. La 5b-2 calcula el alcance (sedes y grupos) una vez por petición, solo para quien lo
necesita. La 5c añade el bean `@acceso` y amplía el arnés de la matriz para que sepa evaluarlo. Ningún endpoint se
abre a los roles nuevos hasta las entregas 6 y 7.

---

## 1. Estado actual (dónde se calculan las autoridades)

| Pieza | Dónde | Qué hace |
|---|---|---|
| `Roles` | `entities/Roles.java:3-5` | `ROLE_USER, ROLE_ADMIN, ROLE_MANAGER`. |
| `RolMembresia` | `entities/RolMembresia.java:20-25` | PROPIETARIO→ADMIN, ADMINISTRADOR→MANAGER, **RESPONSABLE_SEDE→MANAGER, PROFESOR→MANAGER** (provisional), FAMILIA→USER. |
| `desdeAutoridad` | `RolMembresia.java:38-44` | `switch` exhaustivo sobre `Roles`: si se añaden valores, deja de compilar (bien: obliga a decidir). |
| `requiereAlumnoActivo` | `RolMembresia.java:58-62` | Solo familia = tiene USER y no tiene ADMIN ni MANAGER. |
| `autoridadesEn` | `services/impl/MembresiaServiceImpl.java:49-55` | `findRolesActivos` (`repositories/MembresiaRepository.java`, JPQL: membresía activa y organización activa) → `RolMembresia.autoridades`. |
| `autoridadesPorOrganizacion` | `MembresiaServiceImpl.java:234-241` | Igual, para todas las escuelas del usuario (login, `/api/auth/organizaciones`, pantalla de roles). |
| `puedeEntrarCon` | `MembresiaServiceImpl.java:222-231` | Puerta de entrada: alguna autoridad y, si es solo familia, algún alumno accesible. La usan `elegirOrganizacionParaLogin` (`:67-89`) y `puedeEntrarEn` (`:62-65`, cambio de escuela, `AuthenticationServiceImpl.java:147`). |
| `IdentificadorSesionJwt.verificar` | `organizacion/IdentificadorSesionJwt.java:96-136` | Por petición: usuario, organización del claim, `autoridadesEn`, misma puerta (`:119-134`, repetida a mano), `SesionVerificada`. |
| `SesionVerificada` | `organizacion/SesionVerificada.java:20-35` | `record(usuario, organizacionId, Set<Roles> autoridades, token, adminPlataforma)`. |
| `JwtAuthenticationFilter` | `config/JwtAuthenticationFilter.java:40-60` | `Roles.name()` → `SimpleGrantedAuthority`, más `ROLE_ADMIN_PLATAFORMA`. Token `UsernamePasswordAuthenticationToken(usuario, null, autoridades)`, sin `details`. |
| `GET /api/auth/roles` | `controllers/AuthenticationController.java:246-262` | `@PreAuthorize(MANAGER‖ADMIN‖USER)`. Devuelve un array JSON de nombres (`["ROLE_ADMIN", …]`) filtrando las autoridades de la sesión por `Roles.values()` (`autoridadesDeSesion`, `:416-428`). Con valores nuevos en `Roles` los devolvería sin tocar nada. |
| Login | `services/impl/AuthenticationServiceImpl.java:122-130`, `config/OAuth2AuthenticationSuccessHandler.java:79-86, 118-124` | Rechazo con `SIN_ESCUELA` / `SIN_ALUMNOS_ACTIVOS` (`:41-42`). Google redirige a `/adminpage` con ADMIN o MANAGER; si no, a `/userpage`. |

**`usuario_rol` y la columna ENUM: verificado, no hay riesgo.** `Usuario` ya no mapea `roles`
(`entities/Usuario.java:76-78`, comentario «fase 3, entrega 1»). Ningún `@Enumerated` ni `@Column` usa `Roles`: lo
único persistido es `membresia.rol` (`RolMembresia`), y ese ENUM ya tiene los cinco valores (V20). La tabla
`usuario_rol` solo aparece en V31 (FK con CASCADE) y en comentarios. **Ampliar `Roles` no toca la base de datos.**
El único punto de entrada externo de `Roles` es el JSON de `PUT /api/admin/users/{userId}/roles`
(`controllers/AdminController.java:150-167`, `@RequestBody Set<Roles>`), y hay que cerrarlo (§2).

---

## 2. Autoridades que fallan cerradas (5b-1)

### 2.1 Cambio mínimo

- `Roles`: añadir `ROLE_RESPONSABLE_SEDE` y `ROLE_PROFESOR`.
- `RolMembresia`: `RESPONSABLE_SEDE(Roles.ROLE_RESPONSABLE_SEDE)` y `PROFESOR(Roles.ROLE_PROFESOR)`. Actualizar el
  Javadoc de `:7-19`.

Con eso, y sin tocar ninguna regla, los roles nuevos no pasan ninguna regla de gestión, por estos motivos:
- todas las reglas de `config/SecurityConfiguration.java:108-288` piden `ROLE_ADMIN`/`ROLE_MANAGER` (más `ROLE_USER` en
  las de familia);
- los 188 `@PreAuthorize` piden `ROLE_*` antiguos;
- las comprobaciones programáticas buscan las cadenas antiguas (tabla 2.3).

**Consecuencia:** quien solo tenga esas membresías pierde todo acceso de gestión hasta las entregas 6 y 7. Taemoi no
cambia porque las tres autoridades actuales son exactamente las mismas.

### 2.2 Cambios obligados por el compilador o por la coherencia (misma PR)

| # | Dónde | Cambio | Por qué |
|---|---|---|---|
| 1 | `RolMembresia.desdeAutoridad` (`:38-44`) | `ROLE_RESPONSABLE_SEDE`/`ROLE_PROFESOR` → `IllegalArgumentException` | Hoy esos roles no se asignan por la API de roles; los asigna la entrega 8 (API de miembros). |
| 2 | `AdminController.actualizarRolesUsuario` (`:150-167`) | Rechazar con 400 si el cuerpo trae un rol nuevo, antes de llamar al servicio | Si no, el propietario podría crear `PROFESOR` desde la pantalla vieja, o daría un 500. |
| 3 | `MembresiaServiceImpl.reemplazarAutoridades` (`:117-155`) | Gestionar solo las membresías PROPIETARIO, ADMINISTRADOR y FAMILIA; **no desactivar** RESPONSABLE_SEDE ni PROFESOR | Hoy, al guardar desde la pantalla de roles (que solo envía los tres antiguos), una membresía PROFESOR (ya sin `ROLE_MANAGER`) se desactivaría en silencio. |
| 4 | `RolMembresia.requiereAlumnoActivo` (`:58-62`) y la puerta | Ver §2.4 (decisión D5-1) | Hoy «PROFESOR + FAMILIA» no necesita alumno activo porque PROFESOR da MANAGER. |
| 5 | `IdentificadorSesionJwt.verificar` (`:119-134`) | Usar la misma regla de puerta que `MembresiaServiceImpl.puedeEntrarCon`, centralizada en `RolMembresia` (p. ej. `RolMembresia.abreSesion(Set<Roles>)` + `requiereAlumnoActivo`) | Hoy está duplicada a mano. Con la puerta de §2.4 no debe divergir. |
| 6 | `AuthenticationServiceImpl:127-129` y `OAuth2AuthenticationSuccessHandler:80-84` | Un tercer motivo de rechazo si la cuenta tiene membresías activas pero solo de roles aún sin acceso (texto: decisión D5-1) | Si no, verían «No hay alumnos activos asociados a esta cuenta», que es falso. |
| 7 | `repositories/AlumnoRepository.java:237-242` (`ACCESO_FAMILIA`) | Decisión D5-3: quitar `'RESPONSABLE_SEDE', 'PROFESOR'` de la lista de «gestión» | Hoy una fila `MIGRACION` de otro email cuenta para la familia si la cuenta es gestor. Con roles limitados, ser profesor no debería ampliar lo que ve como familia. Sin efecto en datos: V31 no creó filas para esos roles (precheck = 0). |
| 8 | `componentes/endpoints/configuracion-sistema/configuracion-sistema.component.ts:386-410` | `getRoleDisplayName`: «Responsable de sede» y «Profesor» (nombres decididos el 14/10); `getRoleBadgeClass`: clase neutra existente | Sin esto la pantalla enseña `ROLE_PROFESOR` en crudo. Nada más cambia: `parseRoles` ignora los nuevos y el backend ya no los toca (fila 3). |

### 2.3 Comprobaciones programáticas que hoy deciden por `ROLE_ADMIN`/`ROLE_MANAGER` (fallan cerradas, no se tocan en 5b)

En las entregas 6 y 7 habrá que pasarlas a `@acceso` o a capacidades. Listado para no olvidarlas:

| Dónde | Uso |
|---|---|
| `services/impl/AlumnoAccessControlServiceImpl.java:39-45` | Acceso a alumno: gestión = todo; USER = familia. Lo usan 16 llamadas (§4). |
| `archivos/AccesoArchivos.java:150-161` (`esPersonal`) | Ficheros de alumnos y documentos (además de la regla de URL `SecurityConfiguration:108-117`). |
| `controllers/CatalogoController.java:33, 57-67` (`esGestion`) | Tarifas visibles solo para gestión. |
| `controllers/EventoController.java:311-321` | Eventos ocultos visibles para gestión. |
| `services/impl/ProductoAlumnoServiceImpl.java:222-233` | Operación reservada a `ROLE_ADMIN`. |
| `controllers/AuthenticationController.java:332-345` | `!contains(ROLE_USER)` se trata como gestión. Debe pasar a `contains(ADMIN)‖contains(MANAGER)` cuando se abra `/api/auth/user` a los roles nuevos. |
| `config/OAuth2AuthenticationSuccessHandler.java:120` | Redirección tras Google. |
| Frontend `servicios/authentication/authentication.service.ts:293-302` (`tieneAccesoAdmin/User`), `app.routes.ts:14, 21-25` (`RouteRole`, conjuntos), `guards/role.guard.ts:10-34`, `componentes/vistas/layout/header/header.component.ts:243-266` | Solo conocen los tres roles. No hace falta tocarlos en 5b. Los conjuntos `sede`/`profesor` llegan en la entrega 9. |

Las reglas de URL y los `@PreAuthorize` que abrirán las entregas 6 y 7 se añadirán allí, endpoint a endpoint, con
la matriz como revisión.

### 2.4 Qué ve un usuario solo RESPONSABLE_SEDE o solo PROFESOR tras la 5b

Comportamiento actual del frontend con una sesión sin roles conocidos (verificado en el código, no en el navegador):
- `GET /api/auth/roles` y `GET /api/auth/user` dan 403 (`@PreAuthorize`). `obtenerRoles` (`authentication.service.ts:214-228`)
  lo convierte en `[]`, y el interceptor no muestra diálogo con `/api/auth/` (`core/interceptors/error.interceptor.ts:51-54, 141-143`).
- `resolverRutaInicioPorRoles` (`:327-340`) devuelve `/`. No hay bucle: el `roleGuard` manda a `/` y `/` no tiene
  guard.
- Pero la cabecera no se pinta. La anónima pide `!usuarioLogueado` (`header.component.html:1`); la de gestión pide
  `isAdmin` (`header.component.ts:252-257`); la de familia, `isUser`. El usuario queda dentro, en la portada, **sin
  botón de cerrar sesión**, hasta que caduca la cookie (10 h o 30 días). Hoy le pasa lo mismo a la sesión «solo
  plataforma».

Dos opciones (decisión D5-1):
- **A. Cerrar la puerta en el backend (recomendada).** Un rol que todavía no da ninguna autoridad útil no abre
  sesión.
  - Regla: `RolMembresia.abreSesion(autoridades)` = contiene ADMIN, MANAGER o USER. Con solo USER (aunque haya también
    PROFESOR), se aplica la regla de familia.
  - Efecto: el login responde con un mensaje propio (fila 6 de 2.2) y el frontend no cambia.
  - Las cuentas mixtas (PROFESOR + FAMILIA) entran como familia. Las que también son PROPIETARIO o ADMINISTRADOR
    entran como siempre.
  - La puerta se abre en la entrega 9, junto con las vistas de profesor y responsable. La matriz no se ve afectada:
    evalúa tokens sintéticos.
- **B. Dejar entrar y dar una página mínima.** Hace falta:
  - abrir `GET /api/auth/roles` y `GET /api/auth/user` a los roles nuevos;
  - una ruta tipo `/sin-acceso`, con un texto y «Cerrar sesión»;
  - que `resolverRutaInicioPorRoles` la devuelva para esas sesiones.

  Es trabajo de UI (texto y diseño) que no aporta nada hasta la entrega 9.

---

## 3. `AlcanceSesion` (5b-2)

### 3.1 Fuentes (V31, `src-api/src/main/resources/db/migration/V31__sedes_y_vinculos.sql`)

- `membresia_sede (membresia_id, sede_id)`: PK compuesta, índice `sede_id`, FK con CASCADE (`:190-197`). Entidad
  `entities/MembresiaSede.java` (no `@TenantId`). Repositorio `MembresiaSedeRepository` (solo `findByMembresiaId`).
- `asignacion_profesor (organizacion_id, usuario_id, grupo_id, rol)`: PK (`usuario_id`, `grupo_id`), índices
  `grupo_id` y `organizacion_id` (`:202-213`). Entidad `entities/AsignacionProfesor.java` (no `@TenantId`).
  Repositorio con `findByUsuarioIdAndOrganizacionId`.
- `sede` (`:60-76`, `Sede extends EntidadOrganizacion`), `grupo.sede_id` (`entities/Grupo.java:57-58`, solo id).
- `alumno.sede_principal_id` existe en la base (V31 §4) pero **`Alumno` no lo mapea**. Para la regla D9 hay que
  añadir un `Long sedePrincipalId` de solo lectura (`insertable = false, updatable = false`) en la 5c, para no pisar
  el valor hasta que la entrega 8 o 9 lo escriba.
- `membresia` tiene `UNIQUE (usuario_id, organizacion_id, rol)` (V20:21), que sirve de índice para la consulta.

### 3.2 Representación

```java
public record AlcanceSesion(boolean total, Set<Long> sedes, Set<Long> grupos) {
    public static final AlcanceSesion TOTAL   = new AlcanceSesion(true,  Set.of(), Set.of());
    public static final AlcanceSesion NINGUNO = new AlcanceSesion(false, Set.of(), Set.of());
    // copias inmutables en el constructor; veSede(id), veGrupo(id), esVacio()
}
```

- `sedes`: solo de membresías **RESPONSABLE_SEDE** activas. Dan gestión por sede (§3 «N·S»).
- `grupos`: solo de `asignacion_profesor` con membresía **PROFESOR** activa. Dan lectura por grupo y lo que abra la
  entrega 7 («N·G»).
- Se guardan por separado, no se mezclan: con los dos roles, la operación decide qué conjunto vale (EDITAR solo por
  sede; LEER por cualquiera de los dos).
- `TOTAL` para quien tenga ROLE_ADMIN o ROLE_MANAGER. `NINGUNO` para familia o plataforma solas: el acceso de familia
  no es por alcance, sigue en `AccesoFamiliaService`.

### 3.3 Cálculo en `verificar`, con una sola consulta y solo cuando hace falta

Después de `autoridadesEn` (`IdentificadorSesionJwt.java:118`):
1. Si contiene ADMIN o MANAGER → `TOTAL`, **sin consulta** (Taemoi: coste cero).
2. Si contiene RESPONSABLE_SEDE o PROFESOR → una consulta.
3. Si no → `NINGUNO`.

`verificar` corre **antes** de fijar la escuela (`OrganizacionRequestFilter`), así que no puede depender de
`@TenantId`. La consulta es nativa y filtra la organización de forma explícita en cada tabla, como
`AlumnoRepository.ACCESO_FAMILIA` (`AlumnoRepository.java:199-243`). Se documenta como la segunda excepción,
en un repositorio propio (p. ej. `AlcanceRepository`):

```sql
SELECT 'S' AS tipo, ms.sede_id AS id
FROM membresia m
JOIN membresia_sede ms ON ms.membresia_id = m.id
JOIN sede s ON s.id = ms.sede_id AND s.organizacion_id = m.organizacion_id
WHERE m.usuario_id = :usuarioId AND m.organizacion_id = :organizacionId
  AND m.activa = TRUE AND m.rol = 'RESPONSABLE_SEDE'
UNION ALL
SELECT 'G', ap.grupo_id
FROM asignacion_profesor ap
JOIN grupo g ON g.id = ap.grupo_id AND g.organizacion_id = ap.organizacion_id
WHERE ap.usuario_id = :usuarioId AND ap.organizacion_id = :organizacionId
  AND EXISTS (SELECT 1 FROM membresia p
              WHERE p.usuario_id = ap.usuario_id AND p.organizacion_id = ap.organizacion_id
                AND p.activa = TRUE AND p.rol = 'PROFESOR')
```

- Los `JOIN` con `sede`/`grupo` filtrados por organización son la defensa de §5 «Integridad entre escuelas»:
  `membresia_sede` y `asignacion_profesor` no son `@TenantId` y solo tienen FK simples.
- La organización activa ya la exige `findRolesActivos`.
- `sede.activa` no se filtra (decisión menor D5-5): el responsable de una sede cerrada sigue viendo su histórico.

**Coste:** una consulta por índice (PK o UNIQUE en las cuatro tablas), solo en peticiones de esos roles.
**Caché:** ninguna entre peticiones (recomendado). Una caché por usuario y escuela retrasaría las revocaciones
(quitar una sede o una asignación) sin ganar nada medible. Dentro de la petición ya se calcula una sola vez:
`OrganizacionRequestFilter` llama a `verificar` una vez y deja `SesionVerificada` en el atributo.

### 3.4 Cómo llega a los servicios

- `SesionVerificada` gana `AlcanceSesion alcance`. El constructor corto y `soloPlataforma` usan `NINGUNO`; los tests
  que la construyen se actualizan.
- `JwtAuthenticationFilter` (`:51-52`) crea un token propio `AutenticacionEscuela extends UsernamePasswordAuthenticationToken`
  con `alcance()` tipado. Al extender el mismo tipo, siguen valiendo los `instanceof` y el principal `Usuario` de hoy.
  Alternativa equivalente: `setDetails(alcance)`. Hoy nadie usa `details` (verificado por grep).
- Bean `AlcanceActual` (singleton, sin estado) con `obtener()`:
  1. Si las autoridades del `SecurityContext` contienen ADMIN o MANAGER → `TOTAL`. Así los cientos de tests WebMvc
     con usuarios simulados siguen teniendo alcance total.
  2. Si no, y el token es `AutenticacionEscuela` → su alcance.
  3. En cualquier otro caso → `NINGUNO` (falla cerrado).

  Se descarta un bean `@RequestScope`: no funciona fuera de una petición (tareas, futuros SSE de la decisión 14) y
  duplica lo que ya lleva el `SecurityContext`.
- Listados (entregas 6 y 7, no la 5): el servicio pide `AlcanceActual.obtener()`. Con `total` no añade nada. Si no,
  añade el predicado:
  - **Grupos:** `g.sedeId IN :sedes OR g.id IN :grupos`.
  - **Alumnos (D9):** `a.sedePrincipalId IN :sedes OR EXISTS (SELECT 1 FROM Grupo g JOIN g.alumnos x WHERE x = a AND g.sedeId IN :sedes)`.
    Para el profesor, `… g.id IN :grupos`.
  - **Turnos:** por `t.grupo`.
  - **Preinscripciones:** por `p.grupo`. Sin grupo → no visible con alcance limitado.

  Son JPQL sobre entidades `@TenantId`, así que el filtro de escuela se añade solo. En la 5c se dejan escritos como
  constructores reutilizables (`PredicadosAlcance`) con tests de repositorio, sin enchufarlos aún a ningún listado.

---

## 4. Bean `@acceso` (5c)

### 4.1 Inventario de comprobaciones por recurso

Las «tres copias» son métodos privados idénticos que delegan en `AlumnoAccessControlService.canAccessAlumno`:
- `controllers/AlumnoController.java:2445-2448`, usado en `:245, 310, 323, 414, 663, 673, 682, 694, 734, 888, 912, 936, 965, 993`
  (14 llamadas);
- `controllers/ConvocatoriaController.java:144-147`, usado en `:75`;
- `controllers/GrupoController.java:134-137`, usado en `:123`.

En total, 16 llamadas, todas con el mismo patrón: `if (!usuarioPuedeAccederAlumno(id)) return 403 sin cuerpo`, en
endpoints con `@PreAuthorize(MANAGER‖ADMIN‖USER)`. La lógica está en `AlumnoAccessControlServiceImpl.java:28-54`:
gestión → `true`; USER → `AccesoFamiliaService.puedeAcceder`. Las demás comprobaciones por rol (no por recurso) están
en la tabla 2.3. Hoy no existe ninguna comprobación por grupo, turno, preinscripción ni producto del alumno.

### 4.2 API

```java
@Component("acceso")
public class AccesoRecursos {
    public boolean alumno(Long alumnoId, String operacion)
    public boolean grupo(Long grupoId, String operacion)
    public boolean turno(Long turnoId, String operacion)
    public boolean preinscripcion(String referencia, String operacion)   // las rutas usan {referencia}
    public boolean productoAlumno(Long productoAlumnoId, String operacion)
}
enum OperacionAcceso { LEER, EDITAR }   // la entrega 7 añade GRADO, SALUD, ASISTENCIA si hace falta (D5)
```

- `operacion` es un `String` en SpEL (`'LEER'`) y se convierte al enum. Si es desconocida → `false` y log de aviso.
  Con un id `null` → `false`.
- **Alcance total →** `true` sin consultar. Tiene el mismo coste que hoy.
- **Familia (USER):**
  - `alumno(id, LEER)` → `AccesoFamiliaService.puedeAcceder`, exactamente como hoy.
  - Cualquier otra combinación → `false`. Hoy la familia solo pasa por id de alumno.
- **RESPONSABLE_SEDE:** el recurso está en `sedes` (alumno por D9; grupo por `sede_id`; turno y preinscripción por su
  grupo). `productoAlumno` → `false` (D4: sin tesorería).
- **PROFESOR:**
  - `LEER` → el recurso está en `grupos` (alumno: alguno de sus grupos).
  - `EDITAR` → `false`.
- **Varios roles:** se concede si lo concede cualquiera de ellos.
- **Diseño interno, necesario para el arnés:** toda lectura de datos va detrás de un puerto `ConsultaAccesoRecursos`
  (implementación JPQL tenant-safe, más `AccesoFamiliaService`). La lógica de rol × operación no toca datos.
- `AlumnoAccessControlService` pasa a delegar en `acceso.alumno(id, LEER)` o desaparece. Sus tests
  (`AlumnoAccessControlServiceImplTest`) se trasladan.

### 4.3 Migración

1. **5c-1 (sin cambio de comportamiento):** crear el bean y sustituir las tres copias privadas por
   `acceso.alumno(id, OperacionAcceso.LEER)` desde el controlador. Respuesta idéntica (403 sin cuerpo) y matriz sin
   cambios.
2. **5c-2 (decisión D5-4):** pasar las 16 rutas a
   `@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_USER') and @acceso.alumno(#alumnoId, 'LEER')")`
   y quitar el `if`.
   - `#alumnoId` funciona: el proyecto compila con `-parameters`. Lo hereda de `spring-boot-starter-parent` 3.5.9, y
     lo demuestran los `@PathVariable` sin nombre que ya funcionan.
   - Cambio visible: el 403 pasa a venir de Spring Security (`sendError(403)` → `/error`, con cuerpo JSON) en vez de
     vacío. El frontend solo mira el estado (`error.interceptor.ts:51-59`).
   - No hay `@ControllerAdvice` que capture `Exception` (`exceptions/GlobalExceptionHandler.java`), así que no
     acabará en 500.
3. **Entregas 6 y 7:** los endpoints que se abren usan `hasAnyAuthority(…, 'ROLE_RESPONSABLE_SEDE') and @acceso.x(#id, 'OP')`.
   Las reglas de URL de `SecurityConfiguration` añaden la autoridad solo en esas rutas.

### 4.4 Ampliación del arnés de la matriz (5c-2)

Hoy `EvaluadorAutorizacion.comprobarExpresionesSoportadas`
(`seguridad/EvaluadorAutorizacion.java:98-117`) lanza un error con cualquier `#`, `@`, `returnObject` o
`filterObject`, y la invocación simulada pasa argumentos nulos (`:243-286`). Propuesta:

1. `Decision` (`seguridad/Decision.java`) gana **`ALCANCE`**: la seguridad declarativa deja pasar, pero solo si el
   recurso está en el alcance de la sesión.
2. Un `@TestConfiguration` en `MatrizPermisosTest` sustituye (`@Primary`) el puerto `ConsultaAccesoRecursos` por un
   **registrador**:
   - devuelve `true`;
   - marca en un `ThreadLocal` que se consultó.

   La lógica real de rol × operación del bean se evalúa de verdad, porque solo se sustituye el acceso a datos.
   Coste: un contexto de Spring más en caché para esta clase.
3. `decisionMetodo` (`:170-228`): pone a cero el registrador antes de invocar. Si el resultado es PERMITE y el
   registrador se consultó → `ALCANCE`; si no se consultó → `PERMITE`.
   - Con alcance total, el bean corta sin consultar: PROPIETARIO y GESTOR siguen en `PERMITE`.
   - Una denegación por rol u operación sigue siendo `DENIEGA`.
4. La salvaguarda solo admite `@acceso.<método>(...)` y `#` como argumento de esas llamadas. Se comprueba quitando de
   la expresión las llamadas `@acceso\.\w+\([^()]*\)` y exigiendo que no quede `@`, `#`, `returnObject` ni
   `filterObject`. Cualquier otro bean o argumento sigue dando error.
   `EvaluadorAutorizacionSalvaguardaTest` gana casos para lo admitido y lo rechazado.
5. `RolMatriz` (`seguridad/RolMatriz.java:20-42`) crea para RESPONSABLE_SEDE y PROFESOR un `AutenticacionEscuela`
   con un alcance sintético (`sedes={1}` / `grupos={1}`). Así el bean llega al puerto y se clasifica como `ALCANCE`.
   Con un alcance vacío se denegaría sin consultar y daría un DENIEGA falso.
6. Para la familia, el puerto incluye la comprobación de `AccesoFamiliaService` y el registrador también la intercepta.
   Con 5c-2, la casilla FAMILIA de esas 16 líneas pasa de `PERMITE` a `ALCANCE`.
7. Nuevo control: `GET /api/alumnos/{alumnoId}/grupos` →
   - FAMILIA `ALCANCE`;
   - PROPIETARIO y GESTOR `PERMITE`;
   - ANONIMO y SOLO_PLATAFORMA `DENIEGA`.

---

## 5. Cambios en la matriz

- `RolMatriz` añade `RESPONSABLE_SEDE("ROLE_RESPONSABLE_SEDE")` y `PROFESOR("ROLE_PROFESOR")` **al final** de la
  lista. `GESTOR` se queda con `ROLE_MANAGER`: ya es el rol ADMINISTRADOR («Gestor»). Corregir el Javadoc
  (`:14-19`) y la tabla de columnas de `matriz-endpoints.md` (`:27-33`).
- `RegistroMatrizPermisos` lee las columnas por `RolMatriz.values()`, así que hay que **regenerar** todo el registro
  (`-Dmatriz.regenerar=true`). Hay que hacerlo **después** de que entre el hotfix de HEAD (que ya estará regenerado).
- **Valores esperados tras la 5b:** RESPONSABLE_SEDE = PROFESOR = SOLO_PLATAFORMA en cada línea, salvo
  `/api/plataforma/**`, que es DENIEGA para ellos. En el registro actual son 50 PERMITE:
  - las públicas;
  - `/api/auth/organizacion(es)` con `isAuthenticated()`;
  - `POST /api/mail/enviar`, `/error` y los HEAD de la anomalía 3.

  Las cifras cambiarán con el hotfix de HEAD. Con la opción B de D5-1, además GET y HEAD `/api/auth/roles` y
  `/api/auth/user` pasan a PERMITE.
- **Nuevo test de invariante (5b-1):** `rolesNuevosNoPasanDondeNoPasaUnaSesionSinRolDeEscuela`. Para cada línea,
  RESPONSABLE_SEDE y PROFESOR solo pueden ser PERMITE donde SOLO_PLATAFORMA es PERMITE y la ruta no es
  `/api/plataforma/**`, más una lista blanca explícita (vacía con la opción A). Las entregas 6 y 7 la sustituyen
  por la política declarada.
- **Prueba de Taemoi idéntico:** las columnas ANONIMO, FAMILIA, GESTOR, PROPIETARIO y SOLO_PLATAFORMA no cambian ni
  en una casilla. Para la revisión: `awk '!/^#/{print $1,$2,$3,$4,$5,$6,$7}'` antes y después, y `diff` vacío.
- En 5c-2 aparecen las casillas `ALCANCE`. Hay que documentarlo en `matriz-endpoints.md` («Cómo leer el registro» y
  «Fuera de alcance», que deja de excluir el alcance por sede o grupo a nivel declarativo).

---

## 6. Condición previa en producción

Solo recuentos, sin nombres. Ejecutar antes de desplegar la 5b:

```sql
-- 1. Membresías de los roles nuevos, por rol y estado
SELECT rol, activa, COUNT(*) AS membresias, COUNT(DISTINCT usuario_id) AS usuarios
FROM membresia
WHERE rol IN ('RESPONSABLE_SEDE', 'PROFESOR')
GROUP BY rol, activa;

-- 2. (usuario, escuela) que perderían acceso de gestión: rol nuevo activo sin PROPIETARIO/ADMINISTRADOR activo
--    en la misma escuela; y de ellos, cuántos se quedarían sin poder entrar (sin FAMILIA activa)
SELECT COUNT(*) AS pierden_gestion,
       SUM(NOT EXISTS (SELECT 1 FROM membresia f
                       WHERE f.usuario_id = x.usuario_id AND f.organizacion_id = x.organizacion_id
                         AND f.activa = TRUE AND f.rol = 'FAMILIA')) AS sin_familia
FROM (SELECT DISTINCT m.usuario_id, m.organizacion_id
      FROM membresia m
      WHERE m.activa = TRUE AND m.rol IN ('RESPONSABLE_SEDE', 'PROFESOR')
        AND NOT EXISTS (SELECT 1 FROM membresia g
                        WHERE g.usuario_id = m.usuario_id AND g.organizacion_id = m.organizacion_id
                          AND g.activa = TRUE AND g.rol IN ('PROPIETARIO', 'ADMINISTRADOR'))) x;

-- 3. Datos de alcance ya presentes (deberían ser 0: nada los escribe todavía)
SELECT (SELECT COUNT(*) FROM membresia_sede) AS membresia_sede,
       (SELECT COUNT(*) FROM asignacion_profesor) AS asignacion_profesor;
```

Lo esperado es 0 en todo. Ningún código ni migración crea esos roles (`desdeAutoridad` solo da los tres antiguos), y
`precheck-v31.sql` (`membresias_responsable_o_profesor`) ya dio 0. Si sale algún valor distinto de 0, solo puede
venir de SQL manual. Decide el propietario (D5-6):
- **(a)** convertir cada membresía en ADMINISTRADOR antes del despliegue, con SQL fuera del repositorio (conserva
  exactamente el acceso de hoy);
- **(b)** desactivarla (si era de prueba);
- **(c)** aceptar que pierda la gestión hasta las entregas 6, 7 y 9.

Recomendación: (a) si es una persona que hoy gestiona; (b) si es de prueba.

---

## 7. Plan por commits

| Commit | Contenido | Ficheros | Tests | Riesgo | Verificación | Nivel |
|---|---|---|---|---|---|---|
| **5b-1** `feat(seguridad): autoridades propias para responsable de sede y profesor` | §2.1, §2.2 filas 1-7, puerta según D5-1, columnas de la matriz e invariante | `entities/Roles.java`, `entities/RolMembresia.java`, `services/impl/MembresiaServiceImpl.java`, `organizacion/IdentificadorSesionJwt.java`, `controllers/AdminController.java`, `services/impl/AuthenticationServiceImpl.java`, `config/OAuth2AuthenticationSuccessHandler.java`, `repositories/AlumnoRepository.java` (D5-3), `seguridad/RolMatriz.java`, `seguridad/MatrizPermisosTest.java`, `matriz-permisos.txt`, `matriz-endpoints.md` | `MembresiaServiceImplTest`: `:57-58` pasa a las autoridades nuevas; `:164-180` conserva PROFESOR, crea ADMINISTRADOR y guarda 4. Nuevos: PUT roles con `ROLE_PROFESOR` → 400; reemplazar no toca RESP/PROF; `IdentificadorSesionJwtTest` (solo PROFESOR → sin sesión con A; PROFESOR+FAMILIA exige alumno; PROFESOR+ADMINISTRADOR → MANAGER+PROFESOR); login con mensaje nuevo; `/api/auth/roles` de una sesión mixta incluye `ROLE_PROFESOR`; IT de familia (`ACCESO_FAMILIA`) con PROFESOR | Medio: autenticación. Lo mitiga que ningún dato de producción tenga esos roles (§6) | Batería completa (`-DexcludedGroups=migracion`); diff de la matriz = solo columnas nuevas; precondición §6 = 0 | careful |
| **5b-1f** `feat(configuracion): nombres de los roles nuevos en la pantalla de roles` (y, solo con D5-1 = B, página sin acceso) | §2.2 fila 8; con B, la ruta `/sin-acceso` y `resolverRutaInicioPorRoles` | `configuracion-sistema.component.ts` (+ spec); con B: `app.routes.ts`, `authentication.service.ts`, componente nuevo, `AuthenticationController.java:247,265` | Specs de componente y de `resolverRutaInicioPorRoles` | Bajo (A) / medio (B, UI) | `ng test`; con B, login manual de una cuenta de prueba | medium (con B, decisiones de UI antes) |
| **5b-2** `feat(seguridad): alcance de la sesión por sede y grupo` | §3: `AlcanceSesion`, `AlcanceRepository` nativo, cálculo condicional en `verificar`, `SesionVerificada.alcance`, token `AutenticacionEscuela`, `AlcanceActual` | `organizacion/AlcanceSesion.java` (nuevo), `organizacion/SesionVerificada.java`, `organizacion/IdentificadorSesionJwt.java`, `config/JwtAuthenticationFilter.java`, `repositories/AlcanceRepository.java` (nuevo), `organizacion/AlcanceActual.java` (nuevo) | Unitarios: ADMIN/MANAGER → TOTAL **sin** llamar al repositorio (`verify(never())`); FAMILIA → NINGUNO; RESP → sedes; PROF → grupos; mixto. IT MySQL de la consulta: sede o grupo de **otra** escuela insertado a mano en `membresia_sede`/`asignacion_profesor` → no aparece; membresía inactiva → no cuenta. `OrganizacionRequestFilterTest` y `JwtAuthenticationFilter` con el token nuevo; `AlcanceActual` falla cerrado con otro tipo de token | Medio: toca la ruta de cada petición. Rendimiento: ninguna consulta nueva para Taemoi | Batería completa; matriz sin cambios | careful |
| **5c-1** `feat(seguridad): bean de acceso por recurso` | §4.2 y §4.3 paso 1: `AccesoRecursos` (`@acceso`), `OperacionAcceso`, puerto `ConsultaAccesoRecursos` + implementación JPQL, mapeo de solo lectura `Alumno.sedePrincipalId`, `PredicadosAlcance`; las 3 copias pasan a usar el bean | `seguridad/AccesoRecursos.java` (nuevo; paquete a elegir por el implementador), `entities/Alumno.java`, `controllers/AlumnoController.java`, `ConvocatoriaController.java`, `GrupoController.java`, `services/impl/AlumnoAccessControlServiceImpl.java` | Unitarios del bean por rol × operación × recurso; TOTAL sin consulta; id nulo, operación desconocida y sesión anónima → false. IT de la implementación JPQL: D9 (sede principal **o** grupo), turno y preinscripción por grupo, otra escuela invisible. `AlumnoControllerTest`, `GrupoControllerTest` y `AlumnoAccessControlServiceImplTest` adaptados. Las 22 rutas de familia en WebMvc con el mismo resultado | Medio: centraliza el acceso de familia | Batería completa; matriz sin cambios | careful |
| **5c-2** `test(seguridad): la matriz evalúa @acceso` + migración de las 16 rutas a `@PreAuthorize` (si D5-4 = sí) | §4.3 paso 2 y §4.4 | `seguridad/Decision.java`, `EvaluadorAutorizacion.java`, `EvaluadorAutorizacionSalvaguardaTest.java`, `RolMatriz.java`, `MatrizPermisosTest.java` (+ `@TestConfiguration`), `RegistroMatrizPermisos.java` (valor ALCANCE), `matriz-permisos.txt`, `matriz-endpoints.md`, los 3 controladores | Salvaguarda: se admite `@acceso.x(#id,'OP')`; se rechazan `@otroBean`, `#x` suelto y `returnObject`. Control nuevo (§4.4.7). Mutación a mano como la del 16/10: quitar el `and @acceso…` de una ruta → la casilla FAMILIA pasa de ALCANCE a PERMITE y B falla | Medio: si el arnés no es fiel, da una falsa seguridad | Diff de la matriz = solo FAMILIA PERMITE→ALCANCE en esas 16 líneas (y sus HEAD) | careful (heavy si el arnés se complica) |

**Orden:**
1. 5b-1 y 5b-1f, que van juntos en el mismo despliegue.
2. 5b-2.
3. 5c-1.
4. 5c-2.

La 5b-2 puede ir en paralelo a la 5b-1 si se fusiona después. Cada commit compila y deja la batería en verde.

---

## 8. Decisiones del propietario

| # | Decisión | Recomendación |
|---|---|---|
| D5-1 | Qué pasa cuando entra alguien que solo es responsable de sede o profesor, hasta la entrega 9 | **A:** no puede entrar, con un mensaje propio del tipo «Tu rol en esta escuela todavía no tiene acceso». El texto exacto lo decide el propietario. Alternativa B: entra a una página mínima con «Cerrar sesión»; exige abrir `/api/auth/roles` y `/api/auth/user` y una pantalla nueva. |
| D5-2 | Cuentas mixtas (profesor + familia) | Entran como familia (necesitan un alumno activo, como cualquier familia). Ser profesor no da nada hasta la entrega 7. |
| D5-3 | Si ser responsable o profesor cuenta como «gestión» en la regla de familia (`ACCESO_FAMILIA`, filas `MIGRACION` de otro email, escuelas nuevas) | **No:** solo PROPIETARIO y ADMINISTRADOR. Sin efecto en datos actuales. |
| D5-4 | Pasar las 16 comprobaciones de familia a `@PreAuthorize("… and @acceso.alumno(#alumnoId,'LEER')")` en la 5c | **Sí.** Valida el arnés con rutas reales antes de las entregas 6 y 7. Único cambio visible: el 403 lleva el cuerpo JSON estándar de error en vez de ir vacío. |
| D5-5 | Si una sede inactiva sigue en el alcance de su responsable | **Sí**, para el histórico. Para quitarla, se quita la `membresia_sede`. |
| D5-6 | Si la precondición §6 no da 0 | Convertir a ADMINISTRADOR (persona real) o desactivar (prueba), con SQL fuera del repositorio. |

## 9. Preguntas abiertas

- **Sesión «solo plataforma» sin cabecera:** hoy queda sin cabecera ni botón de cerrar sesión (§2.4). Fallo existente,
  fuera de esta entrega. Conviene una tarea aparte.
- **Paquete del bean:** `seguridad` no existe en `main` (solo en los tests). Lo elige el implementador:
  `organizacion/` o uno nuevo `acceso/`.
