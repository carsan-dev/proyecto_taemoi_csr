# Entrega 7 (fase 3) — el profesor con sus grupos y alumnos

Diseño para planificar · 20/10/2026 · base `feature/multi-escuela` en `16923850`. Solo diseño: no hay código ni
migraciones escritas. Rutas relativas a `src-api/src/main/java/com/taemoi/project/` (backend),
`src-frontend/src/app/` (frontend) y `src-api/src/main/resources/db/migration/` (migraciones). Parte de:
`fase3-sedes-permisos.md` §3 (matriz), §7 (D2, D5, decisiones 180 y 184) y §8 (pasos 7, 8 y 9),
`fase3-6c-solicitudes.md` (patrón de registro de acciones y de rutas del profesor) y `matriz-endpoints.md`.

Abreviaturas: P = PROPIETARIO (`ROLE_ADMIN`), G = GESTOR (`ROLE_MANAGER`), R = RESPONSABLE_SEDE
(`ROLE_RESPONSABLE_SEDE`), Pr = PROFESOR (`ROLE_PROFESOR`), F = FAMILIA (`ROLE_USER`). Los caminos A–G que cambian un
grado son los del inventario (§2.2). «Al constructor» = lo decide quien lo implemente. Las decisiones técnicas del
diseñador (§4.3) están aceptadas; el constructor puede cambiarlas solo donde se indica.

**Resumen.** El profesor entra en «Mis grupos»: ve sus grupos, sus alumnos con deporte, grado y apto, una ficha
reducida con el teléfono de contacto, la salud a petición (cada consulta queda registrada) y la hoja de asistencia
del grupo. Gestiona los grados **solo con los pases**: marca apto, corrige la fecha de grado y crea el cargo del pase;
el grado sube cuando se cobra, como en gestión. Todo va por rutas propias bajo `/api/profesor/**`, filtradas por sus
grupos; ninguna ruta de gestión se abre al profesor. Además, propietario y gestor asignan profesores a un grupo desde
su ficha. Sin migración.

---

## 1. Intención y porqué

**Sierra Sur** (un club, seis sedes) trabaja con profesores que dan clase en grupos concretos y no están delante del
dueño. Hoy el profesor solo tiene «Mi caja» (6b y 6c): cobra en efectivo, vende y pide encargos, pero no ve su lista
de alumnos, no puede llamar a un tutor si un niño se lesiona, no sabe si un alumno tiene una alergia y no puede
preparar el examen. Todo eso lo hace hoy el propietario o el gestor por él.

**Moiskimdo** (organización 1, producción, 800+ alumnos) no tiene profesores con cuenta. La E7 no debe cambiarle
nada salvo una sección vacía en la ficha del grupo (decisión 208).

La idea: **el profesor ve y gestiona lo de sus grupos, y nada más.** Ve lo justo para dar clase (sin NIF, email,
dirección, tarifa ni dinero), y lo que toca al dinero (el pase de grado) pasa por el mismo cobro que en gestión.

No hay ningún incidente real que lo motive. Es preparación para Sierra Sur, y cierra el paso 7 de
`fase3-sedes-permisos.md` §8 con la D5 concretada (decisión 207).

## 2. Estado actual (resumen del inventario)

### 2.1 Lo que tiene hoy el profesor

| Qué | Dónde | Estado |
|---|---|---|
| Sesión solo-profesor (180, 184) | `config/SesionSoloProfesorAuthorizationManager.java:132-147` (lista blanca: `/api/caja/profesor/**`, lo imprescindible de sesión, `/api/plataforma/**`) | Todo lo demás, 403 en la capa de URL |
| Alcance por grupos | `AlcanceSesion.grupos()`, calculado en cada petición por `IdentificadorSesionJwt` con `repositories/AlcanceRepository.ALCANCE` (filtra por `organizacion_id` en las tres tablas y exige membresía PROFESOR activa) | No distingue titular de ayudante |
| Alumnos de sus grupos | `GET /api/caja/profesor/alumnos` → `CajaService.alumnosDeMisGrupos` (`CajaService.java:196-224`): JPQL por `alcance.grupos()`, activos, DTO sin datos sensibles | Es el patrón a copiar |
| `@acceso` del profesor | `AccesoRecursos.alumnoDeMisGrupos:209`, `cargoDeMisGrupos:195`, `cobroPropioDelProfesor:220` (solo `ROLE_PROFESOR`, `comoProfesor` exige `!alcance.total()` y grupos no vacíos). `AccesoRecursos.alumno/grupo/turno(id,'LEER'):145-180` tienen rama de profesor pero **también** de responsable de sede y nadie los usa | Ver §9, riesgo 2 |
| Cobrar un cargo que concede grado | `POST /api/caja/profesor/cargos/{cargoId}/cobrar` → `ProductoAlumnoServiceImpl.aplicarEfectosDelCambioDePago:811-836` → `activarGradoPorRecompensaPagada:1010-1042` (camino E) | Ya abierto: el grado sube al cobrar |

### 2.2 Caminos que cambian un grado hoy

Todos acaban en `AlumnoDeporteServiceImpl.actualizarGradoPorCodigo:383-402` (grado, `fecha_grado` = hoy, recalcula
apto). Ninguno manda correos ni toca tarifas; no hay historial de grados ni triggers.

| # | Camino | Dinero | E7 |
|---|---|---|---|
| A | `PUT /api/alumnos/{id}/deportes/{deporte}/grado` (cambio directo) | No | **No se abre** (207) |
| B | `PUT .../fecha-grado`: fecha y recalcula apto (`AlumnoDeporteServiceImpl:417-437`) | No | Ruta propia |
| C | `PUT .../apto-examen`: solo el flag (`AlumnoDeporteServiceImpl:404-414`) | No | Ruta propia |
| D | `POST .../pase-examen` y `.../pase-recompensa` → `AlumnoServiceImpl.pasarGradoInterno:2274-2328`: exige deporte con grados, apto y grado actual, `exigirSedeActiva` (400), producto de grado en catálogo; **crea un `ProductoAlumno` pendiente** y no cambia el grado | Crea cargo | Ruta propia |
| E | Cobrar ese cargo (tesorería o Mi caja) → el grado sube | Cobro | Ya existe |
| F | Pago del examen en la convocatoria | Sí | No se abre |
| G | `PUT /api/convocatorias/{id}/actualizar-grados` (masivo) | No | No se abre |

**Dos pases seguidos (comprobado).** `pasarGradoInterno` no bloquea filas ni mira si ya hay un cargo de pase
pendiente: dos pulsaciones crean **dos cargos** pendientes (el apto sigue a `true` porque el grado no cambia hasta
cobrar). En gestión solo lo frena el diálogo de confirmación de `editar-alumno.component.ts:4639-4758`, que no
desactiva el botón. Si se cobran los dos, el grado se fija dos veces al mismo código (el producto concede un grado
concreto) y se cobra dos veces.

### 2.3 Lo que falta

- **Filtro de servicio fail-open.** `acceso/GestionPorSede.restriccion():62-78` solo restringe con
  `ROLE_RESPONSABLE_SEDE`; con solo `ROLE_PROFESOR` devuelve «sin restricción». Abrir al profesor cualquier listado
  de gestión le daría toda la escuela.
- **DTO con datos de más.** `AlumnoDTO` (NIF, dirección, email, teléfonos, observaciones, tarifa) y `TutorDTO` (NIF,
  email) no sirven.
- **Asistencia por nombre.** `PDFService.generarListadoAsistencia(year, month, List<String> grupos, deporte)`
  (`PDFService.java:49`, impl. `PDFServiceImpl:1196`) recibe cadenas de día/nombre de grupo, no ids.
- **Catálogo cerrado.** `/api/catalogo` y `GET /api/grados` dan DENIEGA al profesor (`SecurityConfiguration:221-223`).
- **Salud.** `GET /api/alumnos/{alumnoId}/salud` es de gestión. `AlumnoSaludService.obtener:45-52` ya registra cada
  lectura (módulo `salud`) en la misma transacción.
- **Asignación de profesores.** Tabla `asignacion_profesor` (V31): PK `(usuario_id, grupo_id)` (un rol por usuario y
  grupo), FK con `ON DELETE CASCADE` a usuario y grupo, **sin `@TenantId`** (`entities/AsignacionProfesor.java`).
  Sin API ni pantalla. La membresía (`membresia`, V20) tiene `uk_membresia_usuario_organizacion_rol` y campo
  `activa`; PROFESOR no se asigna desde la API de roles (`RolMembresia.desdeAutoridad`).
- **Frontend.** Tras el login el solo-profesor aterriza en `/mi-caja` (`authentication.service.ts:378`); su único
  enlace es «Mi caja» (`header.component.html:501-506, 604-606, 733-736, 830-832`, bloques de `tieneRolProfesor`).

## 3. Objetivos, no-objetivos y anti-objetivos

**Objetivos**
1. Que el profesor vea sus grupos, sus alumnos (deporte, grado, apto) y una ficha reducida con teléfonos de
   contacto.
2. Que consulte la salud de un alumno a petición, con cada consulta registrada.
3. Que descargue la hoja de asistencia de un grupo suyo para un mes.
4. Que gestione los grados de sus alumnos con apto, fecha de grado y los dos pases (207).
5. Que propietario y gestor asignen y quiten profesores de un grupo (208).

**No-objetivos**
- La E8 completa: miembros, sedes, permisos por cargo, `GET /api/auth/permisos`. Sigue después.
- Pantallas del responsable de sede (E9). Las rutas de asignación le dan DENIEGA hasta entonces.
- Inscribir en convocatorias, pagar exámenes o actualizar grados en bloque (caminos F y G).
- Historial de grados. El registro de acciones con ids lo mitiga (§7).
- Distinguir titular de ayudante en permisos.
- Abrir el catálogo al profesor.
- Revertir el grado al deshacer un cobro (comportamiento actual, §9).

**Anti-objetivos**
- Que el profesor cambie un grado sin pasar por el cobro (207).
- Que el profesor vea alumnos que no son de sus grupos, por id o en listados.
- Que el profesor reciba NIF, email, dirección, observaciones, tarifa o datos de cobro.
- Que una lectura de salud no quede registrada, o que la salud salga en un listado, un PDF o una exportación.
- Que Moiskimdo cambie de comportamiento o que una casilla existente de la matriz cambie.

## 4. Decisiones

### 4.1 Del propietario

- **Decisión 207 (20/10/2026, concreta la D5).** El profesor gestiona los grados de sus alumnos **solo con los
  pases**: marca «apto», corrige la fecha de grado y hace el pase con derecho de examen o por recompensa. El pase crea
  el cargo pendiente y el grado sube al cobrarlo, como en gestión; el profesor ya puede cobrarlo en Mi caja. **No**
  tiene «Cambiar grado» directo (`PUT .../grado`): ningún grado sube sin pasar por el cobro.
- **Decisión 208 (20/10/2026, adelanta lo mínimo de la E8).** En la ficha del grupo, propietario y gestor ven sus
  profesores y pueden añadir una cuenta existente (por email) como titular o ayudante, o quitarla. Si la cuenta no
  tiene membresía PROFESOR en la escuela, se le crea. Quitar la asignación **no** borra la membresía.
- **Decisión 209 (20/10/2026).** Boceto aprobado (§5) con las decisiones del diseñador (§4.2).
- **Siguen vigentes:** D5 (sin cobros nuevos fuera de Mi caja, sin editar fichas); 180 y 184 (sesión solo-profesor
  con lista blanca en `SesionSoloProfesorAuthorizationManager`); D2 (el registro de acciones del propietario muestra
  altas, cambios y bajas 2xx y las lecturas de salud).

### 4.2 Del diseñador (aceptadas con el boceto)

- **D7-1. Ficha no editable.** El profesor no cambia datos del alumno ni de los tutores.
- **D7-2. Contacto.** Teléfono(s) del alumno y de los tutores, con nombre y parentesco, y «Llamar» (`tel:`). Nada
  más de contacto.
- **D7-3. Salud a petición.** No aparece en listados. En la ficha, plegada: «Ver datos de salud · Tu consulta queda
  registrada». Sin edición, sin exportar, sin salud en el PDF.
- **D7-4. Titular y ayudante, mismos permisos en la E7.** `AlcanceRepository` no los distingue; el rol se muestra.
- **D7-5. Asistencia.** El PDF de siempre, solo de ese grupo y del mes elegido.
- **D7-6. Pases con confirmación.** Texto: «Crea el cargo; el grado sube al cobrarlo en Mi caja.»
- **D7-7. Navegación.** Pestañas superiores «Mis grupos | Mi caja». Tras el login, la sesión solo-profesor entra en
  «Mis grupos» (hoy `/mi-caja`). Las cuentas mixtas ven los enlaces donde hoy ven «Mi caja».
- **D7-8. Asignación en la ficha del grupo.** Sección «Profesores»: filas con nombre, email, chip titular/ayudante y
  quitar con confirmación; alta con email, rol y «Añadir».

### 4.3 Del diseñador, técnicas (aceptadas)

Ninguna cambia lo que ve Moiskimdo. Las marcadas «revisable» puede cambiarlas el constructor si encuentra algo más
simple que no amplíe lo que ve o hace el profesor; si lo hace, lo anota en la PR.

| # | Decisión | Por qué | Pieza |
|---|---|---|---|
| D7-9 | Rutas propias bajo `/api/profesor/**`. **No** se abre ninguna ruta de gestión al profesor | `GestionPorSede.restriccion()` es fail-open para él (§2.3). Con un prefijo nuevo basta una entrada en la lista blanca y una condición en `MatrizPermisosTest` | E7-1 |
| D7-10 | Regla de URL `/api/profesor/**` → `hasAuthority('ROLE_PROFESOR')`; cada método con `@PreAuthorize` que exige `ROLE_PROFESOR` y **no** `ROLE_ADMIN` ni `ROLE_MANAGER` (o `@acceso.*DeMisGrupos`, que ya exige `!alcance.total()`). Cuentas mixtas F+Pr y R+Pr las usan, limitadas a sus grupos; P y G no (DENIEGA): tienen las de gestión | Coherente con `comoProfesor`; P o G con asignación verían su escuela entera de todos modos | E7-1 |
| D7-11 | El filtro de **todas** las rutas del profesor es `alcance.grupos()` y nada más: ni `PredicadosAlcance.alumnos(alcance)` ni `AccesoRecursos.alumno/grupo(id,'LEER')`, que también conceden por sede. `@acceso` nuevo `grupoDeMisGrupos(grupoId)` (copia de `alumnoDeMisGrupos`) | En la sesión 184 (R+Pr) la autoridad de responsable no debe abrir nada (180/184); esos predicados la dejarían pasar por sede | E7-1 |
| D7-12 | DTO propios en un fichero `ProfesorDTOs` (records): grupo (id, nombre, sede, rol, nº de alumnos activos), alumno de lista (id, nombre, apellidos, por deporte: código, grado con nombre, apto), ficha (nombre, apellidos, edad, grupos que comparte con el profesor, contacto, por deporte: grado, nombre de grado, `fechaGrado`, apto, si admite pases y si el siguiente grado pide elegir rojo bordado). Nunca NIF, email, dirección, observaciones, tarifa, cobros ni fecha de nacimiento completa | El catálogo sigue cerrado (§2.3): la respuesta trae lo necesario para pintar | E7-1 |
| D7-13 | Solo alumnos **activos** en listados y ficha (como `alumnosDeMisGrupos`); un alumno dado de baja da 404 | El profesor da clase a los activos | E7-1 |
| D7-14 | Salud: `GET /api/profesor/alumnos/{alumnoId}/salud` reutiliza `AlumnoSaludService.obtener` (registra la lectura, módulo `salud`, en la misma transacción) y responde con `Cache-Control: no-store` como `AlumnoSaludController`. Revisable: un `obtenerComoProfesor` que use la ruta del profesor en el endpoint registrado | No hace falta otro mecanismo de registro | E7-2 |
| D7-15 | Asistencia: `GET /api/profesor/grupos/{grupoId}/asistencia?year&month` comprueba que el grupo es suyo, resuelve **sus** turnos y llama a una sobrecarga nueva de `PDFService` por ids de turno (o de grupo). La firma por `String` no cambia ni se usa | Hoy el PDF recorre por nombre de grupo y por sede; filtrar por nombre podría mezclar grupos homónimos | E7-2 |
| D7-16 | Grados: `PUT .../apto`, `PUT .../fecha-grado`, `POST .../pase-examen` y `POST .../pase-recompensa` bajo `/api/profesor/alumnos/{alumnoId}/deportes/{deporte}/`, que llaman a los **mismos** servicios que gestión (`actualizarAptoParaExamen`, `actualizarFechaGrado`, `pasarGradoConDerechoExamen`, `pasarGradoPorRecompensa`): mismas validaciones y mismos mensajes (400) | 207: el profesor no es más permisivo que gestión | E7-3 |
| D7-17 | Doble pase: la ruta del profesor da **409** «Ya hay un pase pendiente de cobro para este deporte» si el `alumno_deporte` tiene un cargo de pase sin pagar ni cancelar, comprobado con la fila de `alumno_deporte` bloqueada. Gestión no cambia | Hoy dos pases crean dos cargos (§2.2); el profesor no puede ser más permisivo y en su caso el error es más probable (móvil, cobertura) | E7-3 |
| D7-18 | Registro de acciones explícito `RegistroAccionesProfesor` (módulo `profesor`), copia de `RegistroAccionesCaja`, en la misma transacción y solo con ids (§7) | D2 y el patrón de la 6c | E7-3, E7-4 |
| D7-19 | Asignación: `GET/POST /api/grupos/{grupoId}/profesores` y `DELETE /api/grupos/{grupoId}/profesores/{usuarioId}`, solo P y G (R DENIEGA hasta la E9). El grupo se lee acotado por escuela (`@TenantId`): de otra escuela, 404. Toda consulta a `asignacion_profesor` y `membresia` lleva `organizacion_id` explícito | `AsignacionProfesor` y `Membresia` no son `EntidadOrganizacion` | E7-4 |
| D7-20 | `POST {email, rol}` busca la cuenta con `UsuarioRepository.findByEmailIgnoreCase`; sin cuenta → 404 «No hay ninguna cuenta con ese email. Que se registre primero.». Si ya está asignada, cambia el rol (PK `usuario_id, grupo_id`). Si no hay membresía PROFESOR en la escuela, la crea activa | 208 | E7-4 |
| D7-21 | `DELETE` borra solo la fila de `asignacion_profesor`; la membresía queda. Un profesor sin grupos sigue entrando y ve «Aún no tienes grupos asignados» | 208 y 180 (sesión sin grupos) | E7-4 |
| D7-22 | Sin migración. `asignacion_profesor` y `membresia` ya tienen todo lo necesario | La PK y la clave única ya imponen las reglas | Todas |

## 5. Pantallas (boceto aprobado)

Lo visual (estilos, iconos, chips, colores) es de quien construya el frontend, con las skills `impeccable` y
`frontend-design`. Este apartado fija qué hay, no cómo se ve. Móvil primero (375 px); en escritorio, el mismo
contenido con más aire.

### 5.1 Profesor · Mis grupos

```
┌─────────────────────────────────────┐
│  [ Mis grupos ]   Mi caja           │  ← pestañas superiores (D7-7)
├─────────────────────────────────────┤
│  Mis grupos                         │
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Infantil lunes y miércoles  > │  │
│  │ Sede Centro · Titular         │  │
│  │ 18 alumnos                    │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Adultos martes              > │  │
│  │ Sede Norte · Ayudante         │  │
│  │ 9 alumnos                     │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘

Vacío:
│  Aún no tienes grupos asignados.    │
│  Pídeselo a tu escuela.             │
```

### 5.2 Un grupo

```
┌─────────────────────────────────────┐
│  < Volver                           │
│  Infantil lunes y miércoles         │
│                                     │
│  [ Hoja de asistencia ] [ Oct 2026 ▾]│  ← mes elegido; descarga el PDF
│                                     │
│  ( Todos 18 )  ( Aptos 4 )          │  ← filtro
│                                     │
│  ┌───────────────────────────────┐  │
│  │ Ana Pérez Ruiz              > │  │
│  │ Taekwondo · Amarillo  [apto]  │  │
│  ├───────────────────────────────┤  │
│  │ Luis Gómez Sanz             > │  │
│  │ Taekwondo · Blanco            │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

Sin datos de salud en la lista. Un alumno con varios deportes muestra una línea por deporte (al constructor).

### 5.3 Ficha reducida

```
┌─────────────────────────────────────┐
│  < Volver                           │
│  Ana Pérez Ruiz                     │
│  9 años · Infantil lunes y miérc.   │
│                                     │
│  Contacto                           │
│  María Ruiz · Madre                 │
│  600 000 000            [ Llamar ]  │  ← tel:
│  Ana (alumna)                       │
│  600 000 001            [ Llamar ]  │
│                                     │
│  Taekwondo                          │
│  Amarillo · desde 12/03/2026 [✎]    │  ← fecha de grado editable
│  [apto ✓]                           │  ← chip conmutable
│  [ Pase con derecho de examen ]     │
│  [ Pase por recompensa ]            │
│                                     │
│  ▸ Ver datos de salud               │
│    Tu consulta queda registrada     │  ← plegada; al abrir, GET .../salud
└─────────────────────────────────────┘

Confirmación del pase:
┌─────────────────────────────────────┐
│  Pase con derecho de examen         │
│  Taekwondo: Amarillo → Naranja      │
│  Crea el cargo; el grado sube al    │
│  cobrarlo en Mi caja.               │
│        [ Cancelar ]  [ Crear cargo ]│
└─────────────────────────────────────┘
```

- El botón del pase se desactiva mientras la petición está en curso (y D7-17 cubre la doble pulsación en el
  servidor).
- Los pases solo salen si el alumno está apto en ese deporte y el deporte tiene grados; si no, el motivo (al
  constructor).
- El 409 de D7-17 y los 400 del servicio se muestran con el mensaje del backend.
- Cuando el siguiente grado es rojo y pide elegir bordado (como en gestión), ver §11, pregunta 3.

### 5.4 Gestión · ficha del grupo, sección «Profesores»

```
┌─────────────────────────────────────┐
│  Profesores                         │
│  ┌───────────────────────────────┐  │
│  │ Carlos Díaz                   │  │
│  │ carlos@ejemplo.test           │  │
│  │ [Titular]           [ Quitar ]│  │  ← quitar con confirmación
│  ├───────────────────────────────┤  │
│  │ Lucía Martín                  │  │
│  │ lucia@ejemplo.test            │  │
│  │ [Ayudante]          [ Quitar ]│  │
│  └───────────────────────────────┘  │
│                                     │
│  Añadir profesor                    │
│  Email [_____________________]      │
│  Rol   ( Titular ▾ )                │
│                        [ Añadir ]   │
└─────────────────────────────────────┘

Vacío: «Este grupo no tiene profesores asignados.» (texto al constructor)
Email sin cuenta: «No hay ninguna cuenta con ese email. Que se registre primero.»
```

Añadir con un email ya asignado cambia su rol. Hoy no hay una única «ficha del grupo»: existen `gruposEditar/:id`
(`componentes/endpoints/editar-grupo/`) y `gestionarAlumnos/:id` (`listado-grupos/gestionar-alumnos/`). Dónde va la
sección es la pregunta 1 de §11.

## 6. Permisos por rol y por ruta

### 6.1 Tabla rol × acción

`G` = solo sus grupos; `—` = no.

| Acción | P | G (gestor) | R | Pr | F |
|---|---|---|---|---|---|
| Ver mis grupos, alumnos y ficha reducida | — (tiene gestión) | — (tiene gestión) | — | G | — |
| Ver salud de un alumno | gestión | gestión | gestión (API) | G, registrada | — |
| Hoja de asistencia de un grupo | gestión | gestión | gestión | G | — |
| Apto, fecha de grado, pases | gestión | gestión | gestión | G | — |
| Cambiar grado directo (`PUT .../grado`) | gestión | gestión | gestión | **—** (207) | — |
| Cobrar el cargo del pase | tesorería | tesorería | tesorería | Mi caja (ya existe) | — |
| Ver / asignar / quitar profesores de un grupo | Sí | Sí | — (hasta la E9) | — | — |

Cuenta con varios roles: F+Pr y R+Pr usan las rutas del profesor limitadas a sus grupos. P+Pr y G+Pr usan las de
gestión (D7-10).

### 6.2 Rutas nuevas (nombres al constructor)

Columnas de la matriz: ANONIMO, FAMILIA, GESTOR, PROPIETARIO, SOLO_PLATAFORMA, RESPONSABLE_SEDE, PROFESOR. Cada `GET`
lleva además su línea `HEAD` igual.

**Profesor** (`/api/profesor/**`; regla de URL `ROLE_PROFESOR`; en la lista blanca de la sesión solo-profesor):

| Ruta | Expresión | Matriz (PROFESOR; resto DENIEGA) |
|---|---|---|
| `GET /api/profesor/grupos` | `hasAuthority('ROLE_PROFESOR')` sin ADMIN/MANAGER; el servicio filtra por `alcance.grupos()` | PERMITE (filtro declarado) |
| `GET /api/profesor/grupos/{grupoId}/alumnos` | `@acceso.grupoDeMisGrupos(#grupoId)` | ALCANCE |
| `GET /api/profesor/grupos/{grupoId}/asistencia?year&month` | `@acceso.grupoDeMisGrupos(#grupoId)` | ALCANCE |
| `GET /api/profesor/alumnos/{alumnoId}` | `@acceso.alumnoDeMisGrupos(#alumnoId)` | ALCANCE |
| `GET /api/profesor/alumnos/{alumnoId}/salud` | `@acceso.alumnoDeMisGrupos(#alumnoId)` | ALCANCE |
| `PUT /api/profesor/alumnos/{alumnoId}/deportes/{deporte}/apto` `{aptoParaExamen}` | `@acceso.alumnoDeMisGrupos(#alumnoId)` | ALCANCE |
| `PUT /api/profesor/alumnos/{alumnoId}/deportes/{deporte}/fecha-grado` `{fechaGrado}` | ídem | ALCANCE |
| `POST /api/profesor/alumnos/{alumnoId}/deportes/{deporte}/pase-examen` `{rojoBordado}` | ídem | ALCANCE |
| `POST /api/profesor/alumnos/{alumnoId}/deportes/{deporte}/pase-recompensa` `{rojoBordado}` | ídem | ALCANCE |

**Gestión** (bajo la regla de URL existente de `/api/grupos/**`; el `@PreAuthorize` cierra a R):

| Ruta | Expresión | Matriz |
|---|---|---|
| `GET /api/grupos/{grupoId}/profesores` | `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER')` | GESTOR y PROPIETARIO PERMITE; resto DENIEGA |
| `POST /api/grupos/{grupoId}/profesores` `{email, rol: TITULAR\|AYUDANTE}` | ídem | ídem |
| `DELETE /api/grupos/{grupoId}/profesores/{usuarioId}` | ídem | ídem |

**Ninguna casilla existente cambia.** Las rutas de gestión siguen dando DENIEGA al PROFESOR; `/api/catalogo`,
`/api/grados` y `PUT /api/alumnos/{id}/deportes/{deporte}/grado` también.

### 6.3 Las tres capas a la vez

1. Lista blanca: una entrada `rutas.matcher("/api/profesor/**")` en
   `config/SesionSoloProfesorAuthorizationManager.listaBlanca()`.
2. Regla de URL en `config/SecurityConfiguration.java`, junto a la de `/api/caja/profesor/**` (l. 489), y antes del
   `denyAll` final.
3. `MatrizPermisosTest`: la condición `listaBlanca` de `sesionSoloProfesorLaCierraLaCapaDeUrlFueraDeSuListaBlanca`
   acepta el prefijo `/api/profesor/`; cada línea del profesor se declara en `PROFESOR_CAJA_6B2` o en un mapa
   análogo (`PROFESOR_E7`) con el test que la prueba.

## 7. Registro de acciones

- **Automático:** `config/AuditoriaRequestFilter` ya registra todas las peticiones `/api/**` con usuario, endpoint y
  payload saneado; las lecturas 2xx de salud no se duplican.
- **Salud:** cada lectura la registra `AlumnoSaludService.obtener` (módulo `salud`) y la ve el propietario en
  «Consultas de salud» (D2). Sin cambios.
- **Explícito, `RegistroAccionesProfesor`** (módulo `profesor`, copia de `RegistroAccionesCaja`, sobre
  `AuditoriaService.registrarAccesoSensible`, `@Transactional(MANDATORY)`: en la misma transacción que el cambio).
  Resumen **solo con ids** y valores de enumeración; nunca texto libre, nombres ni fechas de nacimiento.

| Acción | Resumen (ejemplo) | Pieza |
|---|---|---|
| `APTO_CAMBIADO` | `PROFESOR APTO_CAMBIADO alumno=#12 alumnoDeporte=#30 apto=SI` | E7-3 |
| `FECHA_GRADO_CAMBIADA` | `PROFESOR FECHA_GRADO_CAMBIADA alumno=#12 alumnoDeporte=#30` | E7-3 |
| `PASE_EXAMEN` | `PROFESOR PASE_EXAMEN alumno=#12 alumnoDeporte=#30 gradoActual=#5 gradoSiguiente=#6 cargo=#88` | E7-3 |
| `PASE_RECOMPENSA` | ídem | E7-3 |
| `ASIGNACION_CREADA` / `ASIGNACION_ROL_CAMBIADO` / `ASIGNACION_QUITADA` | `PROFESOR ASIGNACION_CREADA grupo=#4 usuario=#21 rol=TITULAR` | E7-4 |
| `MEMBRESIA_PROFESOR_CREADA` | `PROFESOR MEMBRESIA_PROFESOR_CREADA usuario=#21` | E7-4 |

El grado nuevo de verdad (camino E, al cobrar) ya queda en el registro de caja del cobro. Con `PASE_*` y el cobro se
reconstruye quién hizo qué sin historial de grados.

## 8. Qué cambia para Moiskimdo

| # | Cambio | Para quién | Estado |
|---|---|---|---|
| 1 | Sección «Profesores» (vacía) en la ficha del grupo, con alta por email | P y G | Aceptado (208) |
| 2 | Rutas `/api/profesor/**` | — | Sin efecto (no hay profesores) |
| 3 | Líneas nuevas en la matriz | — | Invisible; ninguna casilla existente cambia |

Lo que sigue igual: todo comportamiento existente, incluidos los pases y el cambio de grado de gestión (D7-17 solo
toca la ruta del profesor), la tesorería y los listados. Los dorados (`MoiskimdoTesoreriaIdenticaIntegrationTest` y
el JSON dorado de alumnos) pasan sin cambios.

## 9. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | `GestionPorSede.restriccion()` es fail-open para el profesor: abrir una ruta de gestión le da toda la escuela | No se abre ninguna (D7-9); `profesorNoEntraEnRutasDeGestion_403` y la matriz sin casillas cambiadas |
| 2 | En la sesión 184 (R+Pr), un predicado que concede por sede (`PredicadosAlcance.alumnos`, `AccesoRecursos.alumno/grupo`) abriría las sedes del responsable | Filtro solo por `alcance.grupos()` y `@acceso.*DeMisGrupos` (D7-11); test con una cuenta R+Pr |
| 3 | Fuga de datos por DTO (NIF, email, dirección, tarifa, cobros) | DTO propios (D7-12); test que serializa la ficha y comprueba que esas claves no están |
| 4 | Salud sin registrar, cacheada o exportada | Reutilizar `AlumnoSaludService.obtener`; `no-store`; sin salud en listados ni PDF; test de evento y cabecera |
| 5 | Pases que crean cargo (dinero) y doble pulsación | Mismos servicios que gestión; 409 si ya hay un pase pendiente (D7-17) con la fila bloqueada; botón desactivado; test de dos pases seguidos |
| 6 | Cobrar en Mi caja sube el grado (camino E) | Es el comportamiento buscado (207); ya existe y tiene tests de la 6b; el pase lo explica en la confirmación |
| 7 | Deshacer el cobro (Mi caja A4 o tesorería A1) **no** revierte el grado ni lo recalcula (`ProductoAlumnoServiceImpl:828-836`) | Comportamiento actual: se documenta y no se cambia. El arreglo es la fecha de grado o el cambio directo de gestión |
| 8 | `asignacion_profesor` y `membresia` sin `@TenantId` | `organizacion_id` explícito en cada consulta; grupo leído por el repositorio acotado (404 si es de otra escuela); `asignarEnOtraEscuela_404` |
| 9 | Lista blanca, regla de URL y matriz en tres sitios | Un solo prefijo (D7-9); `sesionSoloProfesorFueraDeListaBlanca_403` y `sesionSoloProfesorLaCierraLaCapaDeUrlFueraDeSuListaBlanca` en verde |
| 10 | El PDF de asistencia filtra por nombre de grupo (String) | Sobrecarga por ids de turno del grupo (D7-15); `pdfAsistenciaSoloAlumnosDelGrupo` |
| 11 | Catálogo cerrado: la UI no puede resolver nombres de grado | Las respuestas traen nombre de grado y siguiente grado (D7-12) |
| 12 | Sin historial de grados | Registro con ids (§7) |
| 13 | El alcance se recalcula en cada petición (`IdentificadorSesionJwt`) | Quitar una asignación vale en la siguiente petición; test que quita y comprueba 403 |
| 14 | Corregir la fecha de grado recalcula el apto y puede pisar el apto marcado a mano | Comportamiento de gestión (`actualizarFechaGrado`); la pantalla repinta el apto devuelto |
| 15 | Un alumno con varios deportes: el profesor podría hacer pases en un deporte que no da | Pregunta 2 de §11 |

## 10. Troceado en piezas

| Pieza | Contenido | Depende de | Riesgo |
|---|---|---|---|
| **E7-1** backend | Base `/api/profesor/**`: regla de URL, lista blanca, `MatrizPermisosTest`, `@acceso.grupoDeMisGrupos`; `GET /api/profesor/grupos`, `.../grupos/{grupoId}/alumnos`, `.../alumnos/{alumnoId}` (ficha reducida). Solo lectura | — | Alto (permisos y datos personales); opus |
| **E7-2** backend | Salud (`GET`) y PDF de asistencia del grupo | E7-1 | Alto (salud); opus |
| **E7-3** backend | Apto, fecha de grado y pases (D7-16, D7-17) + `RegistroAccionesProfesor` | E7-2 (comparten controlador, regla y matriz) | Alto (crea cargos); opus |
| **E7-4** backend | Asignación de profesores (208) + registro | E7-1 | Alto (permisos, sin `@TenantId`); opus |
| **E7-5** frontend | Mis grupos, grupo, ficha, salud, pases, asistencia; pestañas «Mis grupos \| Mi caja»; aterrizaje tras el login; enlaces de las cuentas mixtas | E7-3 | Medio |
| **E7-6** frontend | Sección «Profesores» en la ficha del grupo | E7-4 | Medio (visible para Moiskimdo) |

Sin migración. Plan detallado en `fase3-e7-plan.md`.

## 11. Preguntas del diseño, ya cerradas

Cerradas por el diseñador el 20/10/2026 con el plan: 1, 2, 3 y 4 según la recomendación; 5, no (gestión no cambia y
Moiskimdo sigue igual). El propietario puede cambiar cualquiera al aprobar el plan.

1. **Dónde va la sección «Profesores».** Hay dos pantallas de grupo: el formulario `gruposEditar/:id` y la lista de
   alumnos `gestionarAlumnos/:id`. Recomendación: `gruposEditar/:id`, donde ya están la sede y los datos del grupo.
2. **Pases en deportes que el profesor no da.** `grupo` tiene `disciplina`. Recomendación: apto, fecha y pases solo
   en las disciplinas de los grupos que el alumno comparte con el profesor (grupos sin disciplina: cualquier deporte
   del alumno). Sin decisión, E7-3 aplica la recomendación y lo anota en la PR.
3. **Rojo bordado.** El pase a rojo (Taemoi) pide elegir «Rojo» o «Rojo bordado» en gestión; el boceto no lo
   incluye. Recomendación: la misma elección dentro de la confirmación del pase, solo cuando la ficha lo indique.
4. **Membresía PROFESOR inactiva.** Si la cuenta tiene la membresía PROFESOR desactivada, ¿asignarla la reactiva o
   da 409? Recomendación: 409 «La cuenta tiene el rol de profesor desactivado en esta escuela», porque reactivar es
   de la E8 (miembros).
5. **Doble pase en gestión.** D7-17 solo protege la ruta del profesor. ¿Se aplica también a gestión? Cambiaría el
   comportamiento de Moiskimdo; fuera de la E7 salvo que se decida.
