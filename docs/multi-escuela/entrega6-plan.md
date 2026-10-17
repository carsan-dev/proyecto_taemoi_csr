# Entrega 6 (fase 3) — abrir la gestión al responsable de sede: alumnos, grupos y turnos; preinscripciones y convocatorias; tesorería por sede

Plan de implementación · 17/10/2026 · base `feature/multi-escuela` en `5ebff680` (mismo árbol que `develop` `00552aee`,
en producción). Solo lectura: no hay código escrito. Rutas relativas a `src-api/src/main/java/com/taemoi/project/`
(backend), `src-api/src/test/java/com/taemoi/project/` (tests) y `src-api/src/main/resources/db/migration/`
(migraciones). Diseño de partida: `fase3-sedes-permisos.md` §3, §4, §7 (D4 del 16/10, D9) y §8 fila 6;
`entrega5-plan.md` §3.4 y §4.3 paso 3; red de seguridad: `matriz-endpoints.md`.

**Resumen.** La entrega 6 abre al responsable de sede (`ROLE_RESPONSABLE_SEDE`) la gestión de SUS sedes en tres PR:
6-1 alumnos, grupos y turnos; 6-2 preinscripciones y convocatorias; 6-3 tesorería por sede (D4). Cada ruta abierta
añade la autoridad a la regla de URL y al `@PreAuthorize`, con `@acceso` para los recursos por id y
`PredicadosAlcance` para los listados. Para Moiskimdo (organización 1, una sede) no cambia nada visible: el
propietario y el gestor tienen alcance total (`Specification.unrestricted()`, sin consulta extra), la familia
conserva sus 16 rutas y la matriz lo demuestra porque solo cambia la columna RESPONSABLE_SEDE.

Tres hallazgos cambian el plan general:
1. **Nadie escribe `alumno.sede_principal_id`** (`entities/Alumno.java:169-170`, `insertable = false, updatable = false`).
   En Moiskimdo, los alumnos dados de alta después del despliegue de V31 tienen la sede principal a NULL. Antes
   de abrir nada por sede hay que escribirla en el alta y rellenar los NULL de las escuelas con una sede (V31_2).
2. **La tesorería no sabe de qué sede es un cargo.** `producto_alumno` no tiene sede y la regla D9 (sede principal
   **o** grupo) no sirve para imputar: un alumno con grupos en dos sedes aparecería en los totales de las dos.
   La 6-3 necesita una columna nueva, `producto_alumno.sede_id` (V31_3). Es una ampliación: no hace falta V32.
3. **El bean `@acceso` concede si cualquier rol concede.** Si una ruta con `ROLE_USER` en `hasAnyAuthority` usa
   `@acceso.alumno(#id,'LEER')`, una cuenta FAMILIA + PROFESOR leería por su rama de profesor antes de la
   entrega 7 (lo que D5-2 prohíbe). La E6 necesita métodos del bean que solo evalúen alcance total o sedes
   (§2.1).

---

## 1. Estado actual

| Pieza | Dónde | Qué hace hoy |
|---|---|---|
| Bean `@acceso` | `acceso/AccesoRecursos.java:35-153` | `alumno`, `grupo`, `turno`, `preinscripcion` resuelven RESPONSABLE_SEDE por sedes (sin mirar la operación) y PROFESOR por grupos (solo `LEER`). `productoAlumno` solo concede con alcance total (`:99-102`, comentario «D4: sin tesorería», ya superado). `alumnoComoFamilia` (`:54-56`) es lo único que usan hoy las rutas. |
| Puerto de datos | `acceso/ConsultaAccesoRecursos.java`, `ConsultaAccesoRecursosJpa.java:256-313` | `exists(Specification)` por id; sin escuela activa, id nulo o conjunto vacío → `false` sin consultar. |
| Predicados | `acceso/PredicadosAlcance.java` | Grupos por `g.sedeId`; alumnos D9 (`a.sedePrincipalId IN :sedes OR EXISTS grupo de esas sedes`); turnos y preinscripciones por `x.grupo`. **Ningún listado los usa todavía.** |
| Alcance | `organizacion/AlcanceActual.java:25-37` | ADMIN o MANAGER → `TOTAL`; si no, el alcance del token `AutenticacionEscuela`; si no, `NINGUNO`. |
| Puerta de entrada | `entities/RolMembresia.java:80-96` | Solo RESPONSABLE_SEDE o PROFESOR → sin sesión. **Una cuenta FAMILIA + RESPONSABLE_SEDE sí entra** (por `ROLE_USER`) y, tras la E6, tendrá acceso de API a su sede sin pantallas (D6-15). |
| Matriz | `seguridad/MatrizPermisosTest.java:259-285` | `ROLES_NUEVOS_LISTA_BLANCA = Set.of()`: RESPONSABLE_SEDE y PROFESOR deben decidir como SOLO_PLATAFORMA. La E6 la sustituye por una política declarada (§2.2). |
| Sede del grupo | `entities/Grupo.java:57-58`, `services/impl/GrupoServiceImpl.java:150-158`, `services/SedeService.java:26-29` | `sede_id` NULL admitido hasta V32. Al crear, la única sede activa; con varias, NULL. **Ningún DTO lleva sede** (`grep -i sede dtos/` vacío). |
| Sede del alumno | `entities/Alumno.java:169-170` | Solo lectura. V31 rellenó las escuelas con una sede; las altas posteriores quedan a NULL. |
| Reglas de URL | `config/SecurityConfiguration.java:108-288` | Reglas amplias (`GET /api/alumnos/**`, `POST /api/grupos/**`…) con ADMIN/MANAGER. Varias rutas solo tienen la regla de URL, sin `@PreAuthorize` (anomalía 10: `GET /api/alumnos/aptos`, `GET /api/catalogo`, `GET /api/admin/configuracion/limite-turno`, `/imagenes/**`). |

**Consecuencia para la seguridad por URL.** Añadir `ROLE_RESPONSABLE_SEDE` a una regla amplia abre la capa de URL
de todas sus rutas. Las que no tienen `@PreAuthorize` quedarían abiertas sin filtro (p. ej. `GET /api/alumnos/aptos`
caería en `GET /api/alumnos/**`). Se añaden reglas concretas por ruta antes de la amplia y, además, `@PreAuthorize`
en las rutas de la anomalía 10 que estén bajo un patrón que se toque (§3, commit 6-1a).

---

## 2. Piezas comunes

### 2.1 Métodos del bean solo por sede (6-1a)

Las rutas que se abren en la E6 no deben activar la rama de profesor (entrega 7) ni ampliar lo que ve una cuenta
mixta. Propuesta: métodos nuevos que conceden con alcance total o por las sedes de RESPONSABLE_SEDE, y nada más.

```java
public boolean alumnoEnSede(Long alumnoId, String operacion)       // D9 para LEER; EDITAR según D6-3
public boolean grupoEnSede(Long grupoId, String operacion)
public boolean turnoEnSede(Long turnoId, String operacion)
public boolean preinscripcionEnSede(String referencia, String operacion)
public boolean productoAlumnoEnSede(Long productoAlumnoId, String operacion)        // 6-3, por producto_alumno.sede_id
public boolean alumnoConvocatoriaEnSede(Long alumnoConvocatoriaId, String operacion) // 6-2, por su alumno
public boolean alumnoComoFamiliaOSede(Long alumnoId)   // rutas compartidas con la familia (LEER)
```

- Internamente reutilizan `decidir(...)` con `familia = null` y `porGrupos = null` (salvo `alumnoComoFamiliaOSede`,
  que pasa la regla de familia de hoy y la de sedes).
- Los métodos actuales (`alumno`, `grupo`…) se quedan para la entrega 7. Su Javadoc debe decir que activan la rama
  de profesor.
- Con D6-3 = A, `EDITAR` de alumno pasa a mirar **solo** la sede principal (puerto nuevo
  `alumnoConSedePrincipalEn(alumnoId, sedes)`); `LEER` sigue siendo D9. El bean decide la operación; el puerto no.
- La salvaguarda de la matriz (`EvaluadorAutorizacion`) ya admite cualquier `@acceso.<método>(#p, 'LIT')`. No
  admite `#dto.campo`: los ids que llegan en el cuerpo se comprueban en el servicio (§2.3).

### 2.2 Política de la matriz (6-1a)

Se sustituye `ROLES_NUEVOS_LISTA_BLANCA` por dos invariantes:

1. **PROFESOR** sigue decidiendo como SOLO_PLATAFORMA en todas las líneas (no cambia hasta la entrega 7).
2. **RESPONSABLE_SEDE** solo puede ser:
   - `DENIEGA`;
   - `ALCANCE` (el `@PreAuthorize` consultó `@acceso`);
   - `PERMITE` solo si la línea está en una lista declarada `RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO`. Cada entrada
     lleva el nombre del test que prueba el filtro (listados y rutas con ids en el cuerpo), o es una ruta pública o
     de sesión que ya era `PERMITE` para SOLO_PLATAFORMA.

**Prueba de Moiskimdo idéntico:** las columnas ANONIMO, FAMILIA, GESTOR, PROPIETARIO, SOLO_PLATAFORMA y PROFESOR no
cambian en ninguna casilla (`awk '!/^#/{print $1,$2,$3,$4,$5,$6,$7,$9}'` antes y después, `diff` vacío). El diff de la
matriz es la lista de lo que se abre.

### 2.3 Patrón por tipo de ruta

| Tipo | Expresión | Matriz (RESP) |
|---|---|---|
| Por id en la ruta | `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_RESPONSABLE_SEDE') and @acceso.grupoEnSede(#id,'EDITAR')` | ALCANCE |
| Compartida con la familia | `hasAnyAuthority('ROLE_ADMIN','ROLE_MANAGER','ROLE_USER','ROLE_RESPONSABLE_SEDE') and @acceso.alumnoComoFamiliaOSede(#alumnoId)` | ALCANCE |
| Listado | `hasAnyAuthority(…,'ROLE_RESPONSABLE_SEDE')`; el servicio añade `PredicadosAlcance.x(alcanceActual.obtener())` | PERMITE (lista declarada) |
| Ids en el cuerpo (lista de alumnos, `TurnoDTO.grupoId`, certificados) | `hasAnyAuthority(…)`; el servicio llama a `AccesoRecursos` para cada id y responde 403 si alguno no está | PERMITE (lista declarada) |
| Efecto global (generar mensualidades de toda la escuela, borrar convocatoria…) | sin cambios: solo ADMIN/MANAGER | DENIEGA |

- Con alcance total, los listados no añaden predicado (`PredicadosAlcance.lectura` → `Specification.unrestricted()`):
  Moiskimdo hace exactamente las mismas consultas.
- Las denegaciones por id siguen el formato de la 5c-2: 403 de Spring Security con `{"mensaje":"Acceso denegado."}`.
  Las de servicio (ids en el cuerpo) lanzan `AccessDeniedException` para dar la misma respuesta. Hay que comprobar
  que `GlobalExceptionHandler` no la convierte en 500 y añadir un test.

---

## 3. Inventario por bloque

Leyenda «Hoy» (columnas de la matriz A/F/G/P/SP): `G+P` = solo gestor y propietario; `F·alc` = familia por
`@acceso.alumnoComoFamilia`. «Sede del recurso» = de dónde sale la sede. «¿Existe?» = si esa relación existe y se
escribe hoy. ⚠ = sede ambigua o inexistente.

### 3.1 Alumnos (`controllers/AlumnoController.java`, `AlumnoSaludController`, `TutorController`, `ConsentimientoController`)

| Rutas | Hoy | Operación del responsable (propuesta) | Sede del recurso | ¿Existe? |
|---|---|---|---|---|
| `GET /api/alumnos` (listado paginado y sin paginar) | G+P | LEER, filtrado | D9: `alumno.sede_principal_id` o `alumno_grupo → grupo.sede_id` | Sí. `buildAlumnoSpecification` (`AlumnoServiceImpl.java:2335`) ya es una `Specification`: basta con `.and(PredicadosAlcance.alumnos(alcance))`. ⚠ Alumno sin sede principal y sin grupo: invisible para todo responsable (D6-1). |
| `GET /api/alumnos/ids` | G+P | LEER, mismo filtro y orden | Igual | **No filtra:** JPQL `buscarIdsEnOrganizacionActiva` (`AlumnoRepository.java:272-273`) solo por escuela. Pasarlo a Criteria con `select(id)` + predicado + `ORDER BY id`, sin cargar entidades. |
| `GET /api/alumnos/count`, `/deportes/distribucion`, `/con-deportes` | G+P | LEER, filtrado (panel) | Igual | Consultas propias por escuela (`countAlumnos`, `AlumnoDeporteService.obtenerDistribucionActivaPorDeporte`): añadir el predicado. `/con-deportes` usa `obtenerAlumnosFiltrados` (`AlumnoController.java:1899`), ya con `Specification`. |
| `GET /api/alumnos/aptos`, `/aptos/deporte`, `/aptos/convocatoria`, `/aptos/{id}` | G+P (`/aptos` sin `@PreAuthorize`, anomalía 10) | LEER, filtrado; `/aptos/{id}` por id | D9 | Consultas JPQL propias (`findAlumnosAptosParaExamen`…). **Antes** de tocar `GET /api/alumnos/**`, poner `@PreAuthorize` a `/aptos`. |
| `GET /api/alumnos/{id}`, `/{id}/completo` | G+P | LEER (`alumnoEnSede`) | D9 | Sí |
| `POST /api/alumnos/crear` | G+P | EDITAR: alta en una de sus sedes | Sede del cuerpo (campo nuevo `sedePrincipalId`) | ⚠ **No**: nadie escribe `sede_principal_id`. Con varias sedes, el alta queda sin sede (D6-2). |
| `PUT /{id}`, `/{id}/observaciones`, `/{id}/fecha-alta-inicial`, `DELETE /{id}/imagen`, `PUT /{id}/baja`, `/{id}/alta` | G+P | EDITAR | D6-3 (sede principal o D9) | Sí (D9); la sede principal puede ser NULL ⚠ |
| `DELETE /api/alumnos/{id}` | G+P | Recomendado: no (D6-4) | — | — |
| `/{id}/deportes` y `/{id}/deportes/{deporte}/*` (1 GET de familia, 1 POST, 1 DELETE y 23 PUT/POST: grado, fechas, licencia, peso, categoría, competidor, apto, pase de examen y recompensa, tarifa y cuantía) | G+P (el GET, F·alc) | EDITAR (§3 «Convocatorias, aptos, actualizar grado: N·S»). `tipo-tarifa` y `cuantia-tarifa` son tesorería (D6-14) | Alumno | Sí |
| `POST/DELETE /{alumnoId}/grupos/{grupoId}`, `/{alumnoId}/turnos/{turnoId}` | G+P | EDITAR alumno **y** grupo/turno en sus sedes (D6-6) | Alumno y grupo/turno | Sí. ⚠ Asignar a un grupo de su sede a un alumno de otra sede amplía su alcance (D6-6). |
| `GET/PUT /{alumnoId}/salud` | G+P | LEER/EDITAR (§3 «Salud: N·S», D4) | Alumno | Sí. La lectura se audita igual. |
| `/{alumnoId}/tutores` (4 rutas), `/{alumnoId}/consentimientos` (2) | G+P | LEER/EDITAR | Alumno | Sí |
| `POST /{alumnoId}/documentos`, `DELETE …/{documentoId}` | G+P | EDITAR | Alumno | Sí |
| 16 rutas de familia: `GET /{alumnoId}/imagen`, `/grupos`, `/turnos`, `/deportes`, `/documentos`, `…/descargar` | F·alc + G+P | LEER (`alumnoComoFamiliaOSede`) | Alumno | Sí. La foto del listado va por `/api/alumnos/{id}/imagen` (`AlumnoController.java:1911-1916`), así que `/imagenes/**` y `AccesoArchivos.esPersonal` no se tocan. |
| Portal familia: `…/material-examen*` (4), `…/reto-diario*` (4) | F·alc + G+P | **No** (§3 «Portal familia: RESP —») | — | Siguen con `alumnoComoFamilia` |
| `GET /api/grupos/{grupoId}/alumnos/{alumnoId}/turnos` | F·alc + G+P | LEER (`alumnoComoFamiliaOSede` y `grupoEnSede`) | Alumno y grupo | Sí |
| `GET /api/informes/asistencia` (PDF por grupo) | G+P | LEER, solo sus grupos | `grupo.sede_id` | ⚠ El grupo se pasa **por nombre** (`PDFController.java:268-273`) y `grupo.nombre` no es único (V32 lo hará único por sede, no por escuela). Hay que resolver el nombre dentro de los grupos de su alcance, o pasar a ids. |

### 3.2 Grupos y turnos (`GrupoController`, `TurnoController`)

| Rutas | Hoy | Operación del responsable | Sede del recurso | ¿Existe? |
|---|---|---|---|---|
| `GET /api/grupos` | G+P | LEER, filtrado | `grupo.sede_id` | Sí. `obtenerTodosLosGrupos` hace `findAll()` (`GrupoServiceImpl.java:90`): pasar a `findAll(PredicadosAlcance.grupos(alcance))`. ⚠ Grupo con `sede_id` NULL (escuela con varias sedes): solo gestión. |
| `GET /api/grupos/conteo-alumnos`, `/tipo/{tipo}/alumnos` | G+P | Recomendado: cerrado en la E6 | — | Lógica de Taemoi con tipos fijos y clave por nombre (`GrupoServiceImpl.java:262-284`). El panel del responsable llega en la E9. |
| `GET /api/grupos/{id}`, `/{grupoId}/turnos` | G+P | LEER (`grupoEnSede`) | `grupo.sede_id` | Sí |
| `POST /api/grupos` | G+P | EDITAR en una de sus sedes (D6-5) | Sede del cuerpo | ⚠ **No**: `GrupoConAlumnosDTO` no lleva sede; `asignarSedePorDefecto` solo funciona con una sede activa. Hace falta `sedeId` en el DTO (entrada y salida). |
| `PUT /api/grupos/{id}`, `DELETE /api/grupos/{id}` | G+P | EDITAR; si cambia la sede, la de destino también debe ser suya | `grupo.sede_id` | Sí. Al borrar, sus turnos quedan sin grupo (`GrupoServiceImpl.java:167-175`) y salen del alcance. |
| `POST /api/grupos/{grupoId}/alumnos` (lista en el cuerpo), `POST/DELETE /{grupoId}/alumnos/{alumnoId}` | G+P | EDITAR grupo y cada alumno en su alcance (D6-6) | Grupo y alumno | Sí |
| `POST/DELETE /api/grupos/{grupoId}/turnos/{turnoId}` | G+P | EDITAR grupo y turno | Grupo y `turno.grupo` | ⚠ Un turno sin grupo no tiene sede: solo gestión puede asignarlo. |
| `GET /api/turnos` | G+P | LEER, filtrado | `turno.grupo → grupo.sede_id` | `listarTurnosDTOCompleto` hace `findAll()` (`TurnoServiceImpl.java:97`). ⚠ Turnos sin grupo: invisibles. |
| `GET /api/turnos/dto`, `/limite` | Públicos (anomalía 4) | Sin cambios | — | — |
| `GET/PUT/DELETE /api/turnos/{turnoId}` | G+P | LEER/EDITAR (`turnoEnSede`) | `turno.grupo` | Sí |
| `POST /api/turnos/crear` (sin grupo) | G+P | **No** (un turno sin grupo no tiene sede) | — | ⚠ |
| `POST /api/turnos/crear-asignando-grupo` | G+P | EDITAR: el servicio comprueba `turnoDTO.grupoId` | Grupo del cuerpo | Sí (comprobación en servicio, §2.3) |

`preinscripcionService.promocionarTodas()` se sigue llamando tras bajas y cambios de turno. Recorre toda la escuela,
pero es un efecto del sistema (lista de espera) y no expone datos al responsable.

### 3.3 Preinscripciones y convocatorias

| Rutas | Hoy | Operación del responsable | Sede del recurso | ¿Existe? |
|---|---|---|---|---|
| `GET /api/preinscripciones` | G+P | LEER, filtrado | `preinscripcion.grupo → grupo.sede_id` | `listar` ya construye una `Specification` (`PreinscripcionService.java:105`): añadir `PredicadosAlcance.preinscripciones`. ⚠ Sin grupo (anteriores a V4 o con solo `turno_id`): invisibles. |
| `GET /{referencia}`, `/documento-firmado`, `/firma` | G+P | LEER (`preinscripcionEnSede`) | Igual | Sí |
| `POST /{referencia}/cancelar`, `/finalizar`, `/reenviar`, `/reenviar-cambio-turnos`, `/reenviar-finalizacion` | G+P | EDITAR | Igual | Sí. ⚠ `finalizar` crea o actualiza un alumno: su sede principal debería ser la del grupo (no se escribe hoy; depende de la 6-1). |
| `PUT /{referencia}/turnos` | G+P | EDITAR; los turnos nuevos (en el cuerpo) también deben ser de sus sedes | Igual | Comprobación en servicio |
| `DELETE /{referencia}` | Solo P | Sin cambios | — | — |
| `/plantillas/**` | P (y el GET con anomalía 7) | **No** (§3: solo propietario) | — | — |
| `POST /api/preinscripciones`, `/public/**`, `/{referencia}/documento` | Públicos | Sin cambios | — | ⚠ Ver «Preguntas abiertas»: el formulario público lista los grupos de todas las sedes sin decir cuál es cuál. |
| **Preinscripción multisede** | — | — | — | ⚠ Una solicitud puede llevar turnos de varios grupos (uno por día, `PreinscripcionService.java:151, 201`). `p.grupo` es el grupo del primer turno (`:93, :141`) y `PredicadosAlcance` mira solo ese. Si los grupos son de sedes distintas, la otra sede no la ve (D6-7). |
| `GET /api/convocatorias`, `/{id}` | G+P | LEER (D6-8) | ⚠ **No existe**: `convocatoria` solo tiene `disciplina_id` (`Convocatoria.java:39-42`). Es común a la escuela. | — |
| `GET /api/convocatorias/{id}/alumnos`, `/{id}/reporte`, `/{id}/informe-pdf` | G+P | LEER, solo sus alumnos (filtrado en servicio) | `alumno_convocatoria.alumno_id` → D9 | Sí, por alumno |
| `GET /api/convocatorias/alumnos/{alumnoId}` | F·alc + G+P | LEER (`alumnoComoFamiliaOSede`) | Alumno | Sí |
| `POST /api/convocatorias`, `DELETE /{id}`, `PUT /{id}/actualizar-grados` | G+P | Recomendado: no (D6-8). `actualizar-grados` sube de grado a **todos** los alumnos de la convocatoria (`ConvocatoriaServiceImpl.java:177-212`) y borrar elimina sus cargos (`:153-174`). | Toda la escuela | — |
| `PUT /api/convocatorias/alumno/{alumnoConvocatoriaId}` | G+P | EDITAR (`alumnoConvocatoriaEnSede`); **también es tesorería**: marca el examen como pagado y sincroniza el `producto_alumno` (`:215-260`) | `alumno_convocatoria.alumno` | Sí, por alumno |
| `POST/DELETE /api/alumnos/{convocatoriaId}/alumno/{alumnoId}` | G+P | EDITAR alumno (D6-8); crea o borra el cargo del examen | Alumno | Sí |

### 3.4 Tesorería: cargos, cobros, productos, caja y totales

| Rutas | Hoy | Operación del responsable | Sede del recurso | ¿Existe? |
|---|---|---|---|---|
| `GET /api/tesoreria/resumen`, `/movimientos`, `/export/csv`, `/export/pdf` | G+P | LEER, solo sus sedes; propietario y gestor, todo con desglose por sede (D6-12) | Sede del cargo (D6-9) | ⚠ **No existe**: `producto_alumno` no tiene sede. Derivarla del alumno (`COALESCE(pa.alumno, pa.alumnoDeporte.alumno).sedePrincipalId`) se mueve si el alumno cambia de sede y deja fuera a los que no tienen sede principal. |
| `GET /api/tesoreria/anios-disponibles` | G+P | LEER, sin filtro (solo años) | — | — |
| `POST /api/tesoreria/certificados-cobros` (ids en el cuerpo) | G+P | EDITAR: cada alumno en su alcance | Alumno | Comprobación en servicio |
| `GET /api/productos-alumno/alumno/{alumnoId}` | G+P | LEER | Alumno | Sí |
| `POST /api/productos-alumno/alumno/{alumnoId}/producto/{productoId}` (y `/deporte/{deporte}`), `/{alumnoId}/reservar-plaza*`, `/{alumnoId}/renovar-licencia`, `/mensualidades/individual*` y `/licencias/individual` (`alumnoId` como `@RequestParam`) | G+P | EDITAR (crear cargo a un alumno suyo) | Alumno; el cargo nace con la sede del alumno | Sí, por alumno; `#alumnoId` funciona también como `@RequestParam` |
| `PUT /api/productos-alumno/{id}` (cobrar), `DELETE /{id}` | G+P | EDITAR (`productoAlumnoEnSede`). Revertir un cobro sigue siendo solo de `ROLE_ADMIN` (`ProductoAlumnoServiceImpl.java:180-184, 220-232`). | Sede del cargo | ⚠ Necesita `producto_alumno.sede_id` (D6-9) |
| `POST /api/productos-alumno/mensualidades/general`, `/deporte`, `/multi-deporte`, `/licencias/general` | G+P | Recomendado: no (D6-10). Recorren **todas** las inscripciones activas (`ProductoAlumnoServiceImpl.java:378-428`). | Toda la escuela | — |
| `GET /api/productos`, `/todos`, `/{id}` (catálogo) | G+P | LEER (D6-11) | ⚠ **No existe**: `Producto` no tiene sede (común a la escuela) | — |
| `POST/PUT/DELETE /api/productos…` | G+P | No | — | — |
| `GET /api/catalogo` | F+G+P (anomalía 5; sin `@PreAuthorize`) | LEER con tarifas | — | `CatalogoController.esGestion` (`:33, 57-67`) oculta las tarifas a quien no es gestión: hay que incluir al responsable (§3 «Catálogo: lectura») |
| `GET /api/informes/deudas`, `/deudas/csv`, `/listado-mensualidad-mensual` | G+P | LEER, solo sus sedes (D6-13) | Sede del cargo | ⚠ Como el resumen |
| Resto de `/api/informes/*` (mensualidades, productos, reservas, licencias, grados, competidores, promociones, DPF) | G+P | No en la E6 (D6-13) | — | — |
| «Caja» | — | Totales cobrados por sede y período | Sede del cargo y `fecha_pago` | ⚠ **No existe** ninguna entidad de caja; hoy «caja» es el resumen filtrado. La caja física de la 6b es nueva (§7). |

---

## 4. PR propuestas

### 4.1 PR 6-1 — alumnos, grupos y turnos

| Commit | Contenido | Ficheros | Migración |
|---|---|---|---|
| **6-1a** `feat(seguridad): métodos de acceso por sede y política de la matriz` | §2.1 y §2.2. `@PreAuthorize` en `GET /api/alumnos/aptos`. Sin abrir rutas. | `acceso/AccesoRecursos.java`, `ConsultaAccesoRecursos.java`, `ConsultaAccesoRecursosJpa.java`, `controllers/AlumnoController.java` (aptos), `seguridad/MatrizPermisosTest.java`, `matriz-permisos.txt` (solo si cambia aptos: no debería), `matriz-endpoints.md` | No |
| **6-1b** `feat(sedes): sede principal del alumno y sede del grupo en la API` | Alta de alumno escribe `sede_principal_id` (D6-2): `Alumno.sedePrincipalId` pasa a `insertable = true` y **sigue** `updatable = false`. Los cambios posteriores van por un `UPDATE` JPQL explícito del repositorio, para que ningún `save` de una entidad a medio construir lo ponga a NULL. Una sede activa → `SedeService.sedePorDefecto()`. `GrupoConAlumnosDTO` y `GrupoResponseDTO` ganan `sedeId` (entrada y salida, campo aditivo). Validación: la sede es de la escuela activa. | `entities/Alumno.java`, `repositories/AlumnoRepository.java`, `services/impl/AlumnoServiceImpl.java`, `dtos/AlumnoDTO.java`, `dtos/response/GrupoConAlumnosDTO.java`, `GrupoResponseDTO.java`, `services/impl/GrupoServiceImpl.java`, `services/SedeService.java` | **V31_2** `V31_2__sede_principal_alumnos_sin_sede.sql`: el mismo `UPDATE` idempotente que V31 §4 (escuelas con una sola sede, `sede_principal_id IS NULL`). En Moiskimdo rellena las altas posteriores a V31. Sin cambios de esquema. |
| **6-1c** `feat(alumnos): el responsable de sede gestiona los alumnos de sus sedes` | §3.1: reglas de URL concretas, `@PreAuthorize` con `alumnoEnSede`/`alumnoComoFamiliaOSede`, listados filtrados (`obtenerAlumnosFiltrados`, `ids`, `count`, `distribucion`, `con-deportes`, `aptos*`), alta en su sede, asignaciones con doble comprobación. | `AlumnoController.java`, `AlumnoSaludController`, `TutorController`, `ConsentimientoController`, `GrupoController.java` (la ruta de familia), `services/impl/AlumnoServiceImpl.java`, `AlumnoDeporteServiceImpl.java`, `repositories/AlumnoRepository.java`, `config/SecurityConfiguration.java`, matriz | No |
| **6-1d** `feat(grupos): el responsable de sede gestiona grupos y turnos de sus sedes` | §3.2 y el PDF de asistencia | `GrupoController.java`, `TurnoController.java`, `GrupoServiceImpl.java`, `TurnoServiceImpl.java`, `PDFController.java`/`PDFServiceImpl.java` (asistencia), `SecurityConfiguration.java`, matriz | No |

**Tests.**
- `AccesoRecursosTest`: los métodos nuevos por rol × operación. RESP+PROFESOR y FAMILIA+PROFESOR **no** conceden
  por grupos; TOTAL sin consultar; EDITAR por sede principal (D6-3).
- IT nuevo `ResponsableSedeAlumnosIntegrationTest` (MySQL), con dos sedes A y B:
  - un responsable de A lista solo alumnos de A (por sede principal y por grupo) y recibe 403 en el GET, PUT y
    borrado de un alumno de B;
  - un alumno sin sede principal en un grupo de A es visible y su edición sigue D6-3;
  - `/ids` devuelve los mismos ids y en el mismo orden que el listado sin paginar;
  - el alta con una sede de B → 403/400; con varias sedes propias y sin sede → 400;
  - un grupo de otra escuela no se ve aunque su id esté en `membresia_sede` (defensa de §5 «Integridad»).
- IT `MoiskimdoGestionIdenticaIntegrationTest`: con ADMIN y MANAGER, mismos ids, orden y totales en
  listado, `/ids`, `count`, `distribucion`, `aptos`, grupos y turnos. Los predicados con TOTAL devuelven
  `Specification.unrestricted()` (unitario en `PredicadosAlcance`).
- Alta en una escuela con una sede → `sede_principal_id` = esa sede; con varias y gestión sin sede → NULL (D6-2 A).
- Migración V31_2 en MySQL de prueba: dos veces (idempotente); una escuela con dos sedes no se toca.
- `RutasFamiliaTaemoiIntegrationTest`, `AccesoRecursosAlcanceIntegrationTest`, `SecurityAuthorizationWebMvcTest`
  sin regresiones. El 403 de servicio no se convierte en 500.
- Matriz: diff solo en la columna RESPONSABLE_SEDE (§2.2).

**Riesgo: alto.** Toca la autorización de unas 90 rutas y la consulta de los listados de Moiskimdo. Lo mitigan la
matriz (las columnas de hoy no cambian), el IT de equivalencia y que ningún usuario de producción tiene el rol.
Nivel: careful, con revisión opus.

### 4.2 PR 6-2 — preinscripciones y convocatorias

| Commit | Contenido | Ficheros | Migración |
|---|---|---|---|
| **6-2a** `feat(preinscripciones): el responsable de sede gestiona las de sus sedes` | §3.3 (preinscripciones): `preinscripcionEnSede`, listado filtrado, `PUT …/turnos` con turnos nuevos de sus sedes, `finalizar` escribe la sede principal del alumno nuevo (la del grupo) con el método de la 6-1b. Con D6-7 = A: en escuelas con varias sedes, el alta pública exige que todos los turnos sean de la misma sede (en Moiskimdo no se aplica). | `controllers/PreinscripcionController.java`, `services/PreinscripcionService.java`, `config/SecurityConfiguration.java` (regla concreta antes de `/api/preinscripciones/**`; las plantillas siguen solo ADMIN), matriz | No |
| **6-2b** `feat(convocatorias): convocatorias comunes con los alumnos de cada sede` | Según D6-8 A: lectura de la lista, alumnos y reporte filtrados por alcance, apuntar o quitar a sus alumnos, `PUT /alumno/{id}` por `alumnoConvocatoriaEnSede`. Crear, borrar y actualizar grados, solo gestión. `informe-pdf` filtrado. | `ConvocatoriaController.java`, `ConvocatoriaServiceImpl.java`, `AlumnoController.java` (las dos rutas de convocatoria), `PDFServiceImpl.java` (informe), `ConsultaAccesoRecursos*` (alumno de una `alumno_convocatoria`), matriz | No (con D6-8 B: V31_x `convocatoria.sede_id`) |

**Tests.**
- IT con dos sedes: listado, detalle, cancelar y finalizar solo los de su sede. Una preinscripción sin grupo es
  invisible para el responsable. `PUT turnos` hacia un turno de otra sede → 403. El alumno que crea `finalizar`
  lleva la sede del grupo.
- Alta pública multisede rechazada en una escuela con dos sedes y aceptada en Moiskimdo (D6-7).
- Convocatoria con alumnos de A y B: el responsable de A ve y marca solo los suyos; `actualizar-grados` → 403.
- Correos de cancelación, finalización y cambio de turnos sin cambios (tests existentes en verde).
- Matriz: diff solo en RESPONSABLE_SEDE.

**Riesgo: medio.** El flujo público de preinscripción solo cambia para escuelas con varias sedes. Nivel: careful.

### 4.3 PR 6-3 — tesorería por sede

| Commit | Contenido | Ficheros | Migración |
|---|---|---|---|
| **6-3a** `feat(tesoreria): sede del cargo` | Columna `producto_alumno.sede_id` (D6-9 A). `ProductoAlumno.sedeId` se fija en `@PrePersist` desde la sede principal del alumno (`alumno` o `alumnoDeporte.alumno`) si viene vacía. Así cubre los 17 puntos que crean cargos (`new ProductoAlumno()`: 14 en `ProductoAlumnoServiceImpl`, 3 en `AlumnoServiceImpl`). La sede no cambia al cambiar al alumno de sede. | `entities/ProductoAlumno.java`, `repositories/ProductoAlumnoRepository.java` | **V31_3** `V31_3__sede_de_los_cargos.sql`: `ADD COLUMN sede_id BIGINT NULL`, índice, FK `ON DELETE SET NULL`. Relleno: escuelas con una sede → esa sede; con varias → la sede principal del alumno del cargo si la tiene. Guarda de existencia con `information_schema`, como V31. Ampliación pura: el código anterior la ignora. |
| **6-3b** `feat(tesoreria): el responsable de sede cobra y consulta lo de sus sedes` | `productoAlumnoEnSede`; rutas por alumno con `alumnoEnSede(#alumnoId,'EDITAR')`; las 8 consultas de `ProductoAlumnoRepository` usadas por `TesoreriaServiceImpl` ganan `AND (:todasLasSedes = TRUE OR pa.sedeId IN :sedes)`. Con una lista nunca vacía: Hibernate 6 no admite `:lista IS NULL`. Certificados con cada alumno en su alcance; `CatalogoController.esGestion` incluye al responsable; productos en lectura; informes de deudas y listado mensual filtrados (D6-13). | `TesoreriaController.java`, `TesoreriaServiceImpl.java`, `ProductoAlumnoRepository.java`, `ProductoAlumnoController.java`, `ProductoAlumnoServiceImpl.java`, `ProductoController.java`, `CatalogoController.java`, `PDFController.java`/`PDFServiceImpl.java`, `SecurityConfiguration.java`, matriz | No |
| **6-3c** `feat(tesoreria): totales por sede` | `TesoreriaResumenDTO` gana `porSede: [{sedeId, nombre, totales…}]` (aditivo) y `GET /resumen`/`/movimientos` aceptan `sedeId` opcional (propietario y gestor: cualquiera; responsable: solo las suyas). Cargos sin sede → fila «Sin sede». Agregado con `GROUP BY pa.sedeId`, sin cargar entidades. | `TesoreriaServiceImpl.java`, `dtos/response/TesoreriaResumenDTO.java`, `ProductoAlumnoRepository.java` | No |

**Tests.**
- Migración V31_3 en MySQL de prueba: dos veces, tras un fallo a medias y con una escuela de dos sedes (cargo de
  un alumno sin sede principal → NULL).
- `@PrePersist`: un cargo nuevo por cada vía (mensualidad general, individual, licencia, reserva, examen, alta)
  lleva la sede del alumno.
- IT con dos sedes: el resumen y los movimientos del responsable de A no incluyen cargos de B. `PUT` de un cargo de
  B → 403. Revertir un cobro → 403 (no es propietario). Generación general → 403 (D6-10 A). Certificados con un
  alumno de B → 403.
- `TesoreriaServiceImplTest` y un IT de Moiskimdo: con ADMIN y MANAGER, mismos contadores, importes, paginación y
  CSV que antes. `porSede` tiene una sola fila cuyo total es el general.
- Matriz: diff solo en RESPONSABLE_SEDE.

**Riesgo: alto (dinero).** Ocho consultas casi gemelas (lista, recuento, sumas): si se olvida el filtro en una, el
recuento y la lista no cuadran o se filtran importes de otra sede. Mitigación: un test por consulta con datos de
dos sedes. Nivel: careful, con revisión opus.

### 4.4 (Opcional) PR 6-4 — tarifa por sede

Solo si D6-14 = B o se programa aparte antes de diciembre de 2026: tabla `tarifa_sede (organizacion_id, tarifa_id,
sede_id, importe, importe_adulto, importe_menor, vigente_desde)`, solo para mensualidades. Migración **V31_4**.
Al generar la mensualidad, si la cuantía de la inscripción es la de la tarifa base y la sede del alumno tiene una
excepción vigente en ese mes, se cobra la de la sede; una cuantía personalizada (descuentos) no se toca. Esa
interacción con `alumno_deporte.cuantia_tarifa` (copiada al asignar la tarifa, `TarifasServiceImpl.java:117`,
`ProductoAlumnoServiceImpl.java:401`) es la razón para no meterlo en la 6-3.

### 4.5 Migraciones

| Versión | PR | Tipo | Notas |
|---|---|---|---|
| V31_2 | 6-1b | Solo datos (UPDATE idempotente) | Escuelas con una sede |
| V31_3 | 6-3a | Ampliación (columna NULL, índice, FK) + relleno | El código anterior sigue funcionando |
| V31_4 | 6-4 (opcional) | Tabla nueva | Solo si se decide |

Ninguna pieza necesita V32 ni contraer nada. `sede_id` de grupo y de cargo y `sede_principal_id` siguen admitiendo
NULL. Pasarlos a NOT NULL, si se decide, va con la contracción planificada (entrega 10). Siguen la convención de
V31_1: número decimal para no ocupar V32.

### 4.6 Orden

1. 6-1 (6-1a → 6-1b → 6-1c → 6-1d). 6-1b es requisito de 6-2a (sede del alumno de `finalizar`) y de 6-3a (la sede
   del cargo sale de la sede principal).
2. 6-2 y 6-3 pueden ir en paralelo después de la 6-1. 6-3 depende de las decisiones D6-9 a D6-13.
3. Despliegue: V31_2 y V31_3 son ampliaciones sin guarda. Pueden salir en la misma versión o en versiones
   distintas, con la aplicación parada como siempre.

---

## 5. Rendimiento (ciclo EAGER Alumno–Grupo–Turno)

- **Filtrar por alcance no lo empeora.** Los predicados son subconsultas en el `WHERE` (`EXISTS`/`IN` sobre
  `alumno_grupo` y `grupo`) y no cargan entidades. Para el responsable reducen las filas. Con alcance total no se
  añade nada. Las comprobaciones por id usan `exists(Specification)` (`SELECT 1 … LIMIT 1`), sin cargar entidades.
- **Lo que sí lo empeoraría**, y el plan evita:
  - nuevas consultas que devuelvan `Alumno`, `Grupo` o `Turno` completos para contar o sumar. Usar proyecciones,
    `select(id)` o agregados (`/ids`, totales por sede);
  - comprobaciones por elemento en bucles. Para listas en el cuerpo, una sola consulta `id IN :ids AND predicado`
    que compare el recuento.
- Las consultas de tesorería ya hacen `JOIN FETCH pa.alumno`, que dispara la carga EAGER de grupos y turnos de cada
  alumno. Eso pasa hoy. Filtrar por `pa.sede_id` (columna e índice propios) no añade joins.
- **Recomendación:** no bloquear la E6 por la fase 2 de rendimiento. Si la fase 2 pasa `Alumno.grupos` y
  `Grupo.alumnos` a LAZY, los predicados no cambian: son subconsultas sobre la relación, no fetch. Conviene, eso sí,
  hacer la fase 2 antes de la E9 (pantallas del responsable), porque los DTO que hoy dependen de la carga EAGER
  (`AlumnoConGruposDTO`, `GrupoConAlumnosDTO`) se tocarán allí de todos modos.
- Dato a vigilar: `AlumnoConGruposDTO` serializa **todos** los grupos del alumno, también los de otra sede. Un
  responsable vería el nombre de un grupo de otra sede de un alumno compartido. Fuga menor; se puede filtrar en el
  DTO en la 6-1c o aceptar (pregunta abierta).

---

## 6. Riesgos y cómo se verifica que Moiskimdo no nota nada

| Riesgo | Verificación |
|---|---|
| Un cambio de `@PreAuthorize` o de URL altera lo que puede hacer propietario, gestor o familia | Matriz: las columnas ANONIMO, FAMILIA, GESTOR, PROPIETARIO, SOLO_PLATAFORMA y PROFESOR no cambian (`diff` vacío, §2.2) |
| Añadir la autoridad a una regla de URL amplia abre rutas sin `@PreAuthorize` | Reglas concretas por ruta + `@PreAuthorize` en las rutas de la anomalía 10 afectadas; la política de la matriz exige ALCANCE o lista declarada |
| Los listados de gestión cambian (filas u orden) | IT de equivalencia con ADMIN/MANAGER; unitario: con TOTAL, el predicado es `unrestricted()` |
| Cuenta mixta FAMILIA + PROFESOR o RESP + PROFESOR obtiene acceso de profesor antes de la E7 | Métodos del bean solo por sede (§2.1) y tests de cuentas mixtas |
| Un `save` pone a NULL la sede principal | `updatable = false` y escritura solo por `UPDATE` explícito; test que actualiza la ficha y conserva la sede |
| El alta de alumnos o grupos de Moiskimdo empieza a fallar por la sede | Con una sede activa, la sede se pone sola (como en los grupos hoy); test de alta sin `sedePrincipalId` en una escuela con una sede |
| Totales de tesorería distintos tras V31_3 | Postcheck: cargos de la organización 1 con `sede_id` NULL = 0, y suma por sede = suma total. IT con ADMIN: mismos totales antes y después |
| JSON nuevo rompe el frontend | Los campos son aditivos (`sedeId`, `porSede`); el frontend ignora lo que no usa. Revisar los `spec` que comparan objetos enteros |
| Revertir cobros | Sin cambios: solo `ROLE_ADMIN` (`ProductoAlumnoServiceImpl.java:180-184`) |

**Comprobación previa en producción (solo recuentos, sin nombres), antes de desplegar la 6-1:**

```sql
-- 1. Alumnos sin sede principal por escuela y estado (Moiskimdo: altas posteriores a V31; V31_2 las rellena)
SELECT organizacion_id, activo, COUNT(*) FROM alumno WHERE sede_principal_id IS NULL GROUP BY organizacion_id, activo;
-- 2. Grupos sin sede y turnos sin grupo, por escuela
SELECT organizacion_id, COUNT(*) FROM grupo WHERE sede_id IS NULL GROUP BY organizacion_id;
SELECT organizacion_id, COUNT(*) FROM turno WHERE grupo_id IS NULL GROUP BY organizacion_id;
-- 3. Preinscripciones sin grupo, por estado
SELECT estado, COUNT(*) FROM preinscripcion WHERE grupo_id IS NULL GROUP BY estado;
-- 4. Preinscripciones con turnos de más de un grupo (y de más de una sede)
SELECT COUNT(*) FROM (SELECT pt.preinscripcion_id FROM preinscripcion_turno pt JOIN turno t ON t.id = pt.turno_id
  GROUP BY pt.preinscripcion_id HAVING COUNT(DISTINCT t.grupo_id) > 1) x;
-- 5. Cargos sin alumno resoluble o con alumno incoherente (antes de la 6-3)
SELECT COUNT(*) FROM producto_alumno pa LEFT JOIN alumno_deporte ad ON ad.id = pa.alumno_deporte_id
  WHERE pa.alumno_id IS NULL AND ad.alumno_id IS NULL;
SELECT COUNT(*) FROM producto_alumno pa JOIN alumno_deporte ad ON ad.id = pa.alumno_deporte_id
  WHERE pa.alumno_id IS NOT NULL AND pa.alumno_id <> ad.alumno_id;
-- 6. Datos de alcance (deben seguir en 0)
SELECT (SELECT COUNT(*) FROM membresia WHERE rol IN ('RESPONSABLE_SEDE','PROFESOR')) AS membresias,
       (SELECT COUNT(*) FROM membresia_sede) AS membresia_sede;
```

Postchecks: tras V31_2, la consulta 1 para la organización 1 da 0; tras V31_3, `SELECT COUNT(*) FROM producto_alumno
WHERE organizacion_id = 1 AND sede_id IS NULL` = 0. Se añaden a `checklist-despliegue.md` como `precheck-e6.sql` y
`postcheck-v31_3.sql` (sin datos reales).

---

## 7. Tesorería hoy (para la 6b)

**No hay entidades separadas de cargo y pago.** Todo vive en `producto_alumno` (`entities/ProductoAlumno.java`,
`@TenantId`):
- campos: `concepto`, `fecha_asignacion`, `cantidad`, `precio`, `pagado` (Boolean; NULL cuenta como pendiente),
  `fecha_pago`, `notas`;
- relaciones: `producto_id` (catálogo `Producto`: `tipo` `TipoProducto`, `codigo`, `disciplina`, `grado`, común a la
  escuela), `alumno_id` (marcado como obsoleto pero se sigue escribiendo) y `alumno_deporte_id`.

Una fila es a la vez el cargo y su cobro.

- **Cargos.** Se crean en 17 sitios (`new ProductoAlumno()`):
  - mensualidades: general, por deporte, multideporte e individual (`ProductoAlumnoServiceImpl.java:378-…`);
  - tarifa de competidor, licencias (general e individual), renovación de licencia y reserva de plaza;
  - asignación manual de un producto del catálogo;
  - derecho a examen al apuntar a una convocatoria;
  - mensualidad del alta (`AlumnoServiceImpl.asignarMensualidadGeneralSiCorresponde`).

  El precio de la mensualidad es `alumno_deporte.cuantia_tarifa`, copiado de `tarifa` al asignarla y editable por
  alumno. El concepto lleva el mes y el año en texto (`MensualidadUtils`), y la tesorería deduce el período de ese
  texto cuando no hay fecha.
- **Cobros.** `PUT /api/productos-alumno/{id}` con `pagado = true` (`ProductoAlumnoServiceImpl.java:157-218`):
  - fija `fecha_pago` (la enviada o la de hoy);
  - sincroniza `alumno_convocatoria` (cuantía y pagado) y `alumno.tiene_derecho_examen`;
  - si el producto concede grado, promociona (`activarGradoPorRecompensaPagada`).

  Revertir un cobro solo lo puede `ROLE_ADMIN`. **No se guarda quién cobró, ni el método de pago, ni pagos
  parciales, ni el historial.** Solo queda la petición en `AuditoriaEvento`.
- **Duplicado de convocatorias.** `alumno_convocatoria` tiene su propio `pagado`, `fecha_pago` y `cuantia_examen`.
  `PUT /api/convocatorias/alumno/{id}` marca el pago y lo copia al `producto_alumno`; el `PUT` del cargo lo copia en
  sentido contrario (`ProductoAlumnoServiceImpl.java:202-207`, `ConvocatoriaServiceImpl.java:215-260`). Hay dos
  puertas de cobro para el mismo examen.
- **Lectura.** `TesoreriaServiceImpl` (1.219 líneas) sobre 8 consultas JPQL de `ProductoAlumnoRepository`:
  - `findMovimientosTesoreriaPaginados` + `countQuery`, `…PeriodoBase`, `…Filtrados`, `…ByIds`;
  - `contarMovimientosTesoreria`, `sumarImporteMovimientosTesoreria`, `contarAlumnosConPendientesTesoreria`;
  - años disponibles.

  El alumno se resuelve con `COALESCE(pa.alumno, pa.alumnoDeporte.alumno)`. El «resumen» son contadores y sumas
  (pagado y pendiente) del filtro. Aparte están los certificados de cobros (PDF por alumno y año) y los informes de
  `PDFController` (deudas, mensualidades…).
- **Caja.** No existe: no hay apertura ni cierre, ni entregas de dinero.

**Lo que la 6b necesitará y la E6 deja preparado o no bloquea:**
1. Sede del cargo: la da `producto_alumno.sede_id` (6-3a).
2. Un registro de cobro con usuario que cobra, método (efectivo, transferencia…), importe, fecha y estado de
   entrega. Recomendación para la 6b: tabla propia `cobro` (una fila por cobro, FK al cargo) en vez de más columnas
   en `producto_alumno`. `pagado` y `fecha_pago` se mantienen como resumen derivado para no romper las consultas de
   arriba.
3. Tabla `entrega_caja` (sede, quien entrega, quien recibe, importe declarado y recibido, diferencia, fecha) y la
   relación cobro → entrega.
4. Permiso del profesor para marcar como cobrado en efectivo (D4) por un método nuevo del bean sobre su grupo, sin
   abrir el `PUT` general.

La E6 no debe: añadir significado nuevo a `pagado`, calcular «caja» a partir solo de `fecha_pago` en código nuevo
que la 6b tenga que deshacer, ni dar al responsable la reversión de cobros.

---

## 8. Decisiones del propietario

**Tomadas el 17/10/2026:** D6-1 a D6-15, todas con la opción A (la recomendada). Precisiones:

- D6-7: si un alumno quiere entrenar en dos sedes, se preinscribe en una y la gestión le añade después el grupo de la
  otra (hoy solo cabe una preinscripción por persona, deporte y temporada).
- D6-9: la sede del cargo se guarda en todas las escuelas, Moiskimdo incluida (V31_3 rellena el histórico), por si
  abre más sedes en el futuro.
- D6-16 (nueva, de §9): la ficha del alumno muestra al responsable todos sus grupos, también los de otras sedes, en
  lectura; solo puede cambiar los de sus sedes.
- D6-17 (nueva, de §9): en una sede inactiva no se crean grupos, turnos ni cargos (error de validación); se consulta
  el histórico y se pueden cobrar los cargos que ya existían.
- D6-18 (nueva, de la revisión de la 6-1): no se da de alta un alumno en una sede inactiva, ni la gestión ni el
  responsable (400 de validación, como D6-17). Moiskimdo no cambia (su sede está activa).
- D6-19 (nueva, de la revisión de la 6-2): finalizar una preinscripción VINCULANDO a un alumno existente también se
  bloquea con 400 si la sede del grupo de la solicitud está inactiva, para todos los roles (como crear, D6-18).
  Moiskimdo no cambia.
- Nota de la 6-2 (coherente con D6-6): si el responsable finaliza con CREAR_NUEVO y el DNI de la solicitud ya es de
  una ficha de otra sede, la respuesta es 400 («Ya existe una ficha con el DNI/NIE de la preinscripción.») y la
  solicitud queda pendiente para la gestión: el responsable no ve esa ficha como candidata (solo las de su sede
  principal, D6-3) ni puede vincularla.
- D6-20 (de la revisión de la 6-3): en un cargo YA COBRADO el responsable solo puede cambiar las notas; precio,
  cantidad, concepto, fechas o deshacer el pago → 403 (cualquier campo que llegue con un valor distinto del guardado;
  reenviar el cargo entero con otras notas vale). La gestión, igual que antes. Un cargo sin `alumno_id` (solo
  inscripción) da 400 al responsable al editarlo o, si da derecho a examen, al borrarlo: el código de cobro de siempre
  no admite esos cargos (para la gestión, sin cambios; ver el informe de la 6-3).
- D6-21: los cargos los cobra y borra la sede DEL CARGO (D6-9; tras un cambio de sede, la sede antigua cobra los
  cargos antiguos aunque ya no vea la ficha); ver los cargos de un alumno, crearle cargos sueltos, cambiar su tarifa o
  cuantía y pedir su certificado, por su SEDE PRINCIPAL (D6-3). La lista de cargos de un alumno solo enseña los de sus
  sedes.
- D6-22: el responsable no borra cargos cobrados (403; equivale a revertir el cobro, que solo hace `ROLE_ADMIN`).
- D6-23: las generaciones masivas (solo gestión) se saltan a los alumnos de sedes inactivas (D6-17) sin detenerse, y
  la respuesta añade `alumnosOmitidosSedeInactiva` (alumnos distintos saltados). En Moiskimdo es 0 y el resto de la
  respuesta no cambia.
- D6-24: el certificado de cobros de un alumno (de su sede principal) incluye todos sus cobros, de cualquier sede.
- D6-25: el informe de deudas del responsable va por la sede del cargo; el listado de mensualidad mensual, por la sede
  principal actual del alumno.
- D6-26: la exportación CSV/PDF de movimientos de tesorería (`/api/tesoreria/export/*`) se abre al responsable
  filtrada por sus sedes: es parte de la tesorería (D4), no un informe de D6-13. Mismo formato que para la gestión.
- Nota de la 6-3 sobre el resumen: en `porSede`, `alumnosConPendientes` de cada fila cuenta alumnos distintos de esa
  sede; un alumno con pendientes en dos sedes cuenta en las dos, así que la suma de las filas puede superar el total.
  La interfaz NO debe sumarlo (los demás campos de las filas sí suman el total).
- La sede de un cargo nuevo es la sede principal del alumno al crearlo (`ProductoAlumno @PrePersist`, sin consultas
  a la base de datos); sin sede principal, el cargo queda sin sede («Sin sede», solo gestión). Riesgo vigilado: en
  una escuela de UNA sola sede todo alumno tiene sede principal (alta, 6-1; finalización de preinscripción, 6-2; y el
  relleno de V31_2), así que no debería haber cargos sin sede; si apareciera un alumno sin sede principal, sus cargos
  nuevos quedarían sin sede y lo detectan `postcheck-v31_2.sql` (`alumnos_sin_sede_escuelas_una_sede`) y
  `postcheck-v31_3.sql` (`cargos_sin_sede_escuelas_una_sede`). Se corrige asignando la sede principal y repitiendo
  el relleno de V31_3 (idempotente).

| # | Decisión | Opciones (recomendada primero) | Por qué | Impacto en Moiskimdo |
|---|---|---|---|---|
| D6-1 | Qué ve el responsable de un alumno **sin sede principal** | **A.** Regla D9 tal cual: lo ve si está en un grupo de sus sedes; si no, nadie salvo gestión. B. Invisible hasta que tenga sede principal. C. Lo ven todos los responsables. | A es lo ya decidido (D9) e implementado en `PredicadosAlcance`; C rompe el aislamiento entre sedes | Ninguno (no hay responsables; V31_2 rellena las sedes) |
| D6-2 | Si el responsable da de alta alumnos y en qué sede caen | **A.** Sí, la sede principal es obligatoria y debe ser suya (automática si tiene una). Gestión: con una sede activa, automática; con varias, opcional hasta la E9 (queda NULL como hoy). B. Obligatoria también para gestión con varias sedes (400 sin sede). C. El responsable no da altas. | A no rompe el alta del propietario de Sierra Sur antes de tener el selector (E9); B sí | Ninguno visible: el alta pasa a guardar «Umbrete» |
| D6-3 | Quién **edita** un alumno visible desde dos sedes (sede principal en A, grupo en B) | **A.** Leer, por D9 (las dos); editar ficha, deportes, salud, documentos y cobros, solo el responsable de la sede principal; sin sede principal, solo lectura. B. Editar también por D9 (como el bean hoy). | Un único dueño evita dos responsables cobrando o cambiando la misma ficha | Ninguno |
| D6-4 | Bajas y borrado definitivo | **A.** El responsable puede dar de baja y de alta; borrar definitivamente, solo gestión. B. También borrar. | El borrado arrastra cargos, documentos y vínculos; es irreversible | Ninguno |
| D6-5 | Quién da de alta grupos y turnos | **A.** El responsable crea, edita y borra grupos y turnos de sus sedes (sede obligatoria entre las suyas; turno siempre con grupo). B. Solo gestión crea; el responsable asigna alumnos y cambia horarios. | A es §3 («crear, editar, asignar alumnos: N·S») | El JSON de grupos gana `sedeId` (aditivo, invisible) |
| D6-6 | Asignar a un grupo de su sede a un alumno de otra sede o sin sede | **A.** No: solo alumnos que ya están en su alcance; mover a un alumno entre sedes es de gestión. B. Sí (amplía su alcance). | B dejaría a un responsable «adoptar» alumnos ajenos por id | Ninguno |
| D6-7 | Preinscripción con turnos de **varias sedes** | **A.** En escuelas con varias sedes, todos los turnos de una solicitud deben ser de la misma sede (validación nueva en el alta pública). B. Se queda como hoy: la ve el responsable del grupo del primer turno. C. La ven los dos responsables y solo la finaliza gestión. | Con A la sede de la solicitud es única y `p.grupo` basta | Ninguno (una sede: la validación no se aplica) |
| D6-8 | Convocatorias | **A.** Comunes a la escuela: el responsable ve la lista y, dentro, solo sus alumnos; apunta o quita a los suyos y marca el pago del examen; crear, borrar y «actualizar grados», solo gestión. B. Convocatoria por sede (`convocatoria.sede_id`, migración V31_x). C. Sin convocatorias para el responsable en la E6. | El examen es un acto del club; «actualizar grados» y borrar afectan a todos los alumnos | Ninguno |
| D6-9 | A qué sede se **imputa un cargo** | **A.** Columna `producto_alumno.sede_id` fijada al crear el cargo (sede principal del alumno en ese momento), con relleno (V31_3). B. Derivarla en cada consulta de la sede principal actual del alumno (sin migración). | Con B, el histórico se mueve al cambiar de sede, los alumnos sin sede quedan fuera y los totales no cuadran con lo cobrado. A también sirve de base a la caja (6b). | Ninguno: todos los cargos van a «Umbrete» |
| D6-10 | Generación **masiva** de mensualidades y licencias | **A.** Solo propietario y gestor (tarifa común, una sola generación al mes); el responsable genera las individuales y cobra. B. El responsable genera «las de mi sede». | Evita generaciones solapadas y meses generados a medias por sede | Ninguno |
| D6-11 | Catálogo de productos | **A.** Común a la escuela; el responsable lo lee (con tarifas) y no lo edita. B. Productos por sede (`producto.sede_id` NULL = común; migración). | Sierra Sur tiene tarifas comunes; B se puede añadir después sin romper A | `/api/catalogo` sin cambios para gestión |
| D6-12 | Totales | **A.** Propietario y gestor ven el resumen con desglose por sede y un filtro opcional por sede; con una sola sede, la interfaz no muestra el desglose (E9). El responsable ve solo sus sedes. B. Sin desglose en el backend hasta la E9. | A deja la API lista; el desglose es aditivo | Ninguno visible (una fila «Umbrete» que la pantalla no pinta) |
| D6-13 | Informes PDF y CSV para el responsable | **A.** Abrir solo deudas (PDF y CSV) y el listado de mensualidad mensual, filtrados por sede; el resto sigue de gestión hasta que se pida. B. Abrirlos todos, filtrados. | Cada informe tiene su propia consulta y se filtra uno a uno. La exportación de movimientos de tesorería no es un informe de D6-13: se abre por D6-26 | Ninguno |
| D6-14 | Tarifa por sede (una sede a 30 € desde 2027) | **A.** Fuera de la E6: PR 6-4 propia (`tarifa_sede` con fecha de inicio, solo mensualidades, V31_4) antes de generar las mensualidades de enero de 2027; mientras tanto vale la cuantía por alumno. B. Dentro de la 6-3. C. Solo cuantía manual por alumno, sin tabla. | Interactúa con las cuantías personalizadas (descuentos) y con la generación mensual; mezclarlo con la 6-3 sube el riesgo de la PR de dinero | Ninguno |
| D6-15 | Puerta de entrada de una cuenta que solo es responsable de sede | **A.** Sigue cerrada hasta la E9 (no hay pantallas); las cuentas FAMILIA + RESPONSABLE_SEDE ya entran y tendrán acceso de API a su sede. B. Abrirla en la E6 con la página mínima de D5-1 B. | Sin pantallas no aporta nada; la API se prueba con tests | Ninguno |

## 9. Preguntas abiertas

- **Formulario público multisede.** `/api/preinscripciones/public/grupos|turnos/{deporte}` lista los grupos de todas
  las sedes sin nombre de sede, y el resumen guardado («(nombre del grupo)») tampoco la lleva. Para Sierra Sur hace
  falta antes de abrir su web (fase 4 / E9). Fuera de la E6.
- **Aforo por sede.** `sede.aforo_turno` existe pero `AforoPreinscripcionService` usa solo `limite_turno`. Fuera de
  la E6 (entrega 8).
- **Nombres de grupo repetidos.** `contarAlumnosPorGrupo` y el PDF de asistencia trabajan por nombre; V32 hará único
  (`sede_id`, `nombre`), no (`organizacion_id`, `nombre`). Conviene pasar esos dos a ids en la E9.
- **Grupos de otra sede en la ficha.** `AlumnoConGruposDTO` enseña todos los grupos del alumno (§5): ¿se filtran
  para el responsable o se acepta?
- **Sede inactiva.** Por D5-5 el responsable sigue viendo su histórico. ¿Puede crear cargos o grupos en una sede
  inactiva? Recomendación: no (validación de escritura), sin decisión formal.
