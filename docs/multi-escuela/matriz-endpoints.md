# Matriz de permisos por endpoint

Red de seguridad de **caracterización**: fija qué rol puede llamar a cada endpoint HOY, para que los cambios de
la fase 3 (autoridades nuevas que fallan cerradas para responsable de sede y profesor, ver
`fase3-sedes-permisos.md` §3-§4) no abran ni cierren nada sin que se vea en la revisión.

- Registro: `src-api/src/test/resources/seguridad/matriz-permisos.txt` (360 líneas; 120 son HEAD, ver más abajo).
- Test: `src-api/src/test/java/com/taemoi/project/seguridad/MatrizPermisosTest.java` (etiquetas `integracion` y
  `matriz-permisos`; corre en la batería normal `-DexcludedGroups=migracion`).
- Arnés: `EvaluadorAutorizacion.java` en el mismo paquete.

## Qué comprueba

| Test | Falla cuando |
|---|---|
| A. `cadaEndpointTieneExactamenteUnaPoliticaYNoSobraNinguna` | un manejador de `RequestMappingHandlerMapping` (método × patrón) no tiene línea, una línea no corresponde a ningún manejador, o hay líneas repetidas |
| B. `laDecisionRealCoincideConLaDeclaradaParaCadaRol` | para alguna línea y rol, la decisión real (regla de URL **y** `@PreAuthorize`) no es la declarada |
| `rolesNuevosSoloPasanPorAlcanceOPorUnFiltroDeclarado` | PROFESOR decide distinto de SOLO_PLATAFORMA en alguna línea (o no deniega en `/api/plataforma/**`); o RESPONSABLE_SEDE es `PERMITE` en una línea que no está en `RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO` (listados y cuerpos que filtra el servicio, cada uno con el test que lo prueba) ni era ya `PERMITE` para SOLO_PLATAFORMA; o la lista declara una línea que no es `PERMITE` o cita un test que no existe (entrega 6, entrega6-plan.md §2.2) |
| `control*` | el arnés deja de reproducir casos conocidos por HTTP real (p. ej. `/api/plataforma/errores`) |

## Cómo leer el registro

```
# METODO RUTA                              ANONIMO FAMILIA GESTOR  PROPIETARIO SOLO_PLATAFORMA RESPONSABLE_SEDE PROFESOR
GET      /api/alumnos/{alumnoId}/grupos    DENIEGA ALCANCE PERMITE PERMITE     DENIEGA         DENIEGA          DENIEGA  # AlumnoController#obtenerGruposDeAlumno
```

| Columna | Sesión | Autoridades |
|---|---|---|
| ANONIMO | sin sesión | (token anónimo) |
| FAMILIA | FAMILIA | `ROLE_USER` |
| GESTOR | ADMINISTRADOR | `ROLE_MANAGER` |
| PROPIETARIO | PROPIETARIO | `ROLE_ADMIN` |
| SOLO_PLATAFORMA | admin. de plataforma sin membresía | solo `ROLE_ADMIN_PLATAFORMA` |
| RESPONSABLE_SEDE | RESPONSABLE_SEDE | `ROLE_RESPONSABLE_SEDE` |
| PROFESOR | PROFESOR | `ROLE_PROFESOR` |

Desde la entrega 5b, RESPONSABLE_SEDE y PROFESOR tienen autoridad propia. PROFESOR sigue decidiendo lo mismo que
SOLO_PLATAFORMA salvo en `/api/plataforma/**`, que le deniega (hasta la entrega 7). Desde la entrega 6-1,
RESPONSABLE_SEDE gestiona alumnos, grupos y turnos de sus sedes: `ALCANCE` en las rutas por id (`@acceso.alumnoEnSede`,
`grupoEnSede`, `turnoEnSede`, `alumnoComoFamiliaOSede`, que nunca activan la rama de profesor) y `PERMITE` solo en los
25 listados y altas que filtra o comprueba el servicio (`RESPONSABLE_SEDE_FILTRADO_EN_SERVICIO`). Lo comprueba
`rolesNuevosSoloPasanPorAlcanceOPorUnFiltroDeclarado`. Solos tampoco abren sesión (`RolMembresia.abreSesion`, D6-15).
Las columnas nuevas van al final; la 6-1 no cambió ninguna casilla de las otras seis columnas.
Desviación anotada del plan (§3.1): `GET /api/grupos/{grupoId}/alumnos/{alumnoId}/turnos` solo exige
`@acceso.alumnoComoFamiliaOSede(#alumnoId)` (no también `grupoEnSede`): por D6-16 el responsable lee todos los grupos
(y sus turnos) de un alumno de su alcance, también los de otras sedes; la expresión de la familia no cambia.

Entrega 6-2 (preinscripciones y convocatorias, §3.3, D6-7 y D6-8): 29 casillas nuevas, todas en la columna
RESPONSABLE_SEDE (las otras seis no cambian; plantillas, `DELETE /api/preinscripciones/{referencia}` y las rutas
públicas siguen igual, también sus anomalías).
- `ALCANCE` (17): `@acceso.preinscripcionEnSede(#referencia, …)` en el detalle, `documento-firmado`, `firma` (GET y
  HEAD), `cancelar`, `finalizar`, `reenviar*` y `PUT …/turnos` (por el grupo de la solicitud);
  `@acceso.alumnoConvocatoriaEnSede(#alumnoConvocatoriaId,'EDITAR')` en `PUT /api/convocatorias/alumno/{…}` (pago del
  examen, sede principal del alumno, D6-3); `@acceso.alumnoEnSede(#alumnoId,'EDITAR')` en `POST/DELETE
  /api/alumnos/{convocatoriaId}/alumno/{alumnoId}`; `@acceso.alumnoComoFamiliaOSede` en
  `GET/HEAD /api/convocatorias/alumnos/{alumnoId}` (la familia, igual que antes).
- `PERMITE` declarado (12, GET y HEAD): `/api/preinscripciones` (el servicio añade `preinscripcionesDeSusSedes`),
  `/api/convocatorias` y `/{id}` (comunes a la escuela, D6-8), `/{id}/alumnos`, `/{id}/reporte` e `/{id}/informe-pdf`
  (solo sus alumnos, D9). Crear y borrar convocatorias y `actualizar-grados` siguen en `DENIEGA`.
En el servicio, además: `PUT …/turnos` y `finalizar` exigen que todos los turnos sean de sus sedes; vincular exige
una ficha de su sede principal y nunca cambia el email de una ficha con cuenta (403); vincular no quita grupos ni
turnos de otras sedes; los candidatos (`alumnosCoincidentes`) son solo los que puede vincular (su sede principal). D6-7
(todos los turnos de la misma sede) se aplica al alta pública y a `PUT …/turnos` para todos los roles.

- `PERMITE` = la seguridad declarativa deja llegar la petición al controlador sin mirar el recurso. No garantiza
  un 200: el controlador o el servicio pueden seguir negando (p. ej. `AccesoArchivos` para ficheros, que
  responde 404).
- `ALCANCE` (desde la 5c-2) = la seguridad declarativa deja pasar **solo si el recurso está en el alcance o el
  acceso de la sesión**: el `@PreAuthorize` llama a `@acceso` y este consultó datos para decidir. Hoy son las 16
  rutas de familia con alumno (`@acceso.alumnoComoFamilia(#alumnoId)`, D5-2 y D5-4) y sus HEAD, en la columna
  FAMILIA. PROPIETARIO y GESTOR siguen en `PERMITE` porque, con alcance total, el bean concede sin consultar. Una
  denegación por rol u operación sigue siendo `DENIEGA`. Qué recursos concretos entran en el alcance no lo fija
  la matriz (ver «Fuera de alcance»).
- `RUTA` es el patrón tal cual del controlador. Para los dos manejadores comodín (`/imagenes/**`,
  `/documentos/**`) cada línea es una **ruta de ejemplo** concreta, una por regla de URL distinta. Las rutas
  de `MUESTRAS_COMODIN` son obligatorias: si falta la línea de alguna, en GET o en HEAD, el test A falla.
- **HEAD.** Spring MVC atiende HEAD con los manejadores GET, y desde el hotfix #26 (16/10/2026)
  `HeadComoGetAuthorizationManager` autoriza HEAD exactamente como GET. Cada GET tiene también su línea `HEAD`,
  y el registro fija que decide igual que su GET.
- Un mapeo sin método (solo `/error`) se expande a GET, HEAD, POST, PUT, PATCH y DELETE.
- El comentario final es informativo; el test no lo lee.

## Cómo actualizarlo

- **Endpoint nuevo.** El test A falla y en el mensaje aparece la línea que hay que añadir, con la decisión
  actual calculada. Antes de pegarla, compárala con §3. Si no es la que debería ser, corrige la seguridad
  (`SecurityConfiguration` / `@PreAuthorize`), no la línea.
- **Cambio de permisos intencionado.** El test B lista las casillas que cambian (`declarado X, real Y`, con el
  detalle de cada capa, `url=` y `método=`). Edita esas casillas. El diff del registro es el resumen del cambio
  para la revisión.
- **Endpoint borrado o con otra ruta.** El test A señala la línea obsoleta. Bórrala.
- **Comodines.** Para cubrir otra subruta de `/imagenes/**` o `/documentos/**`, añade una línea con una ruta
  concreta. Si la subruta tiene una regla de URL propia, añádela también a `MUESTRAS_COMODIN` para que sea
  obligatoria.
- **Columna nueva** (p. ej. `ROLE_PROFESOR`). Añade el valor a `RolMatriz`, regenera y revisa el diff entero.
- **Regenerar** (sobrescribe el fichero desde el comportamiento actual):
  `cd src-api && ./mvnw test -Dtest=MatrizPermisosTest -Dmatriz.regenerar=true`. Escribe además
  `target/matriz-permisos-detalle.tsv` con la decisión de cada capa y el `@PreAuthorize` de cada línea. Sin la
  propiedad, `regenerarRegistro` aparece como omitido.

## Por qué el arnés es fiel y no tiene efectos

No copia las reglas: evalúa las piezas reales del contexto de la aplicación (`@SpringBootTest`, mismo contexto
en caché que el resto de ITs).

1. **Capa URL.** Recorre las cadenas de `FilterChainProxy.getFilterChains()` en su orden y se queda con la
   primera que casa con la petición, igual que en producción. De esa cadena usa el `AuthorizationManager` de su
   `AuthorizationFilter`: las `requestMatchers` en su orden, hasta `anyRequest().authenticated()`. La petición
   se construye como en MockMvc (`MockMvcRequestBuilders…buildRequest`), con el método real, también HEAD. Un
   resultado `null` cuenta como concedido, igual que en `AuthorizationFilter`.
2. **Capa método.** Toma los `AuthorizationAdvisor` que Spring aplicó al proxy real del controlador (llegan
   envueltos en `MethodSecurityAdvisorRegistrar.AdvisorWrapper`) y los invoca en su orden sobre una invocación
   simulada. El `proceed()` final **no llama al método**: solo registra que la seguridad lo habría dejado pasar.
   Ningún controlador se ejecuta y no se toca ningún dato. Si un método tiene `@PreAuthorize` pero el arnés no
   encuentra su interceptor, el test lanza una excepción en vez de dar un PERMITE falso.
   La invocación no devuelve nada y lleva argumentos nulos, salvo los parámetros que una expresión pasa a
   `@acceso`, que reciben un valor de ejemplo no nulo (`1` o `"1"`; con un id nulo el bean deniega y daría un
   DENIEGA falso). Solo se admiten expresiones de roles y llamadas `@acceso.<método>(...)` cuyos argumentos son
   `#parámetro` del método o literales `'TEXTO'`. Cualquier otro bean, un `#x` fuera de esas llamadas, un
   `#x` que no es parámetro, `returnObject` o `filterObject` dan ERROR nombrando el endpoint y piden ampliar el
   arnés, en vez de decidir (`EvaluadorAutorizacionSalvaguardaTest`).
   **`@acceso` (5c-2).** Una `@TestConfiguration` de `MatrizPermisosTest` sustituye (`@Primary`) SOLO el puerto de
   datos `ConsultaAccesoRecursos` por `RegistradorConsultaAcceso`: responde `true` y anota en un `ThreadLocal` que
   se consultó. La lógica de rol × operación del bean se evalúa de verdad. Si la capa de método concede y el
   registrador se consultó, la casilla es `ALCANCE`; si concede sin consultar, `PERMITE`. RESPONSABLE_SEDE y
   PROFESOR llevan un `AutenticacionEscuela` con alcance de ejemplo (sede 1 / grupo 1) para que el bean llegue al
   puerto. Si el registrador no está instalado, el arnés no arranca.
3. **Controles.** Los tests `control*` reproducen casos comprobados por HTTP real en
   `SecurityAuthorizationWebMvcTest` y `PlataformaYAuditoriaPropietarioIntegrationTest`. Uno de ellos exige que
   exista un endpoint que la URL deja pasar y que solo `@PreAuthorize` deniega. Así se demuestra que se aplican
   las dos capas. Otro control comprueba que las peticiones HEAD casan con las reglas `HttpMethod.HEAD`
   explícitas.
4. **Mutación (hecha a mano el 16/10/2026 y revertida).** Se cambiaron a la vez tres cosas:
   `GET /api/turnos/dto` pasó de `permitAll` a ROLE_ADMIN; se añadió `ROLE_USER` al `@PreAuthorize` de
   `GradoController#obtenerTodosLosGrados`; y se renombró la línea de `/healthz`. Los tests fallaron con las
   5 casillas cambiadas (url=DENIEGA en turnos/dto; FAMILIA PERMITE en `/api/grados`), con `/healthz` sin
   política y con la línea obsoleta.
   Segunda mutación, tras modelar HEAD: la regla `HEAD /api/eventos/{eventoId}/imagen` (`SecurityConfiguration`,
   línea 235) pasó de `permitAll` a `denyAll`. En la misma ejecución se borraron del registro las líneas
   `GET /imagenes/alumnos/1.jpg` y `HEAD /imagenes/org-1/alumnos/1.jpg`. Fallaron tres tests:
   - B, en las 5 casillas de esa línea HEAD;
   - el control de HEAD;
   - A, que pidió las dos rutas de ejemplo borradas.

   Se revirtió todo.

   Tercera mutación, en la 5c-2: se quitó `and @acceso.alumnoComoFamilia(#alumnoId)` de
   `AlumnoController#obtenerGruposDeAlumno`. Fallaron B (FAMILIA de `GET` y `HEAD /api/alumnos/{alumnoId}/grupos`:
   declarado ALCANCE, real PERMITE) y el control de esa ruta. Se revirtió.

**Fuera de alcance, a propósito.** Estas comprobaciones no se evalúan:

- CSRF (solo afecta a métodos no GET fuera de `/api/**`, y el único manejador así es `/error`).
- `OPTIONS`: Spring MVC lo responde de forma implícita y la URL lo deja pasar en `/api/**`.
- Las comprobaciones programáticas (propiedad de la ficha, escuela activa, fila `Imagen`/`Documento`).
- Qué recursos concretos están en el alcance de una sesión (sede, grupo o alumno de la familia). La matriz solo
  distingue `ALCANCE` (la decisión depende de esos datos) de `PERMITE` y `DENIEGA`; los datos los prueban
  `AccesoRecursosAlcanceIntegrationTest`, `RutasFamiliaTaemoiIntegrationTest` y los tests de `AccesoRecursos`.

## Anomalías encontradas

Tres de las marcadas RESUELTA se corrigieron en el hotfix #26 (16/10/2026) y la 1 en la entrega 5b-1; el resto
sigue abierto.

Decisiones en orden A/F/G/P/SP = ANONIMO/FAMILIA/GESTOR/PROPIETARIO/SOLO_PLATAFORMA; P = PERMITE, D = DENIEGA.

### Graves

1. **RESUELTA en parte (entrega 5b-1): GESTOR incluía RESPONSABLE_SEDE y PROFESOR.** Desde la 5b-1 esos roles
   tienen autoridad propia (`ROLE_RESPONSABLE_SEDE`, `ROLE_PROFESOR`) y columna propia, que falla cerrada en
   todas las líneas. GESTOR es ya solo ADMINISTRADOR. **Sigue abierto:** ADMINISTRADOR lo puede casi todo y sin
   límite por sede (188 líneas no públicas con G=P). Estos ejemplos los niega §3 al profesor, que ya no pasa:
   - `GET /api/tesoreria/movimientos`, `GET /api/informes/deudas`, `POST /api/productos-alumno/mensualidades/general`
     (D/D/P/P/D): §3 «Tesorería»: PROFESOR —.
   - `GET|PUT /api/alumnos/{alumnoId}/salud`, `DELETE /api/alumnos/{id}`, `POST /api/alumnos/crear` (D/D/P/P/D):
     §3: PROFESOR — en alta/edición/baja y solo sus grupos en salud.
   - `POST /api/preinscripciones/{referencia}/finalizar`, `POST /api/eventos/crear` (D/D/P/P/D): PROFESOR — en §3.

   Las entregas 6 y 7 abrirán a RESPONSABLE_SEDE y PROFESOR solo lo que §3 les da, con alcance por sede o grupo.
2. **RESUELTA (hotfix #26, 16/10/2026): `POST /api/mail/enviar` con HTML sin escapar.** Sigue siendo P/P/P/P/P
   y sin `@PreAuthorize` (el formulario de contacto es público por diseño), pero el texto del usuario ya se
   escapa antes de entrar en el HTML del correo. Queda abierto que en multi-escuela el formulario de cualquier
   escuela llega a Taemoi y que no hay límite de frecuencia en la capa de URL ni en el controlador.
3. **RESUELTA (hotfix #26, 16/10/2026): HEAD se saltaba las reglas de URL que son solo GET.** Antes, 17 de las
   120 líneas HEAD decidían distinto que su GET (3 más abiertas, 14 más cerradas). Ahora
   `HeadComoGetAuthorizationManager` autoriza HEAD como GET, se quitaron las reglas HEAD explícitas de
   `SecurityConfiguration` y en el registro las 120 líneas HEAD coinciden con su GET. Esto también cierra la
   exposición de `/api/alumnos/aptos`, `/api/eventos/{eventoId}/documentos` y `/api/alumnos/{id}/completo`
   por HEAD.

### Medias

4. **`GET /api/turnos/dto` y `GET /api/turnos/limite`: P/P/P/P/P**, sin `@PreAuthorize`. Un anónimo puede leer:
   - la lista de turnos, que son datos de la escuela;
   - el límite de turno, que en `/api/admin/configuracion/limite-turno` es solo del PROPIETARIO (D/D/D/P/D).

   §3 no tiene columna pública para «Grupos y turnos: ver». No se comprobó a qué escuela resuelve una
   petición anónima.
5. **`GET /api/catalogo`: D/P/P/P/D.** La familia lee el catálogo completo, y §3 dice FAMILIA — en «Catálogo».
   Según el comentario de `SecurityConfiguration`, las tarifas se ocultan en el controlador.
6. **El portal familia está abierto a GESTOR.** Afecta a `GET …/material-examen*`, `GET …/reto-diario*` y
   `PUT /api/alumnos/{alumnoId}/reto-diario/completar` (D/P/P/P/D). §3 «Portal familia» dice RESP. SEDE — y
   PROFESOR —. Además, cualquier gestor puede completar el reto diario de un alumno, una escritura propia de
   la familia.

### Incoherencias entre la regla de URL y `@PreAuthorize`

Manda la regla más estricta, así que no hay fuga. Con HEAD tampoco desde el hotfix #26 (anomalía 3, resuelta).
El riesgo general está en editar una sola de las dos capas.

7. **`GET /api/preinscripciones/plantillas/{deporte}` y `POST …/plantillas/{deporte}/preview`: D/D/D/P/D.** El
   `@PreAuthorize` admite ADMIN y MANAGER, pero la URL solo ADMIN. §3 dice que las plantillas son solo del
   PROPIETARIO, así que la anotación es la equivocada.
8. **`DELETE /api/preinscripciones/{referencia}`: D/D/D/P/D.** La regla de URL `/api/preinscripciones/**`
   admite MANAGER; el método solo ADMIN.
9. **`GET /api/alumnos/{id}/completo`: D/D/P/P/D.** El `@PreAuthorize` incluye `ROLE_USER`; la familia solo
   queda fuera porque `GET /api/alumnos/**` no la admite. Si se reordenan las reglas, la familia vería la
   ficha completa.
10. **Protegidos solo por la URL, sin `@PreAuthorize`:**
   - `GET /api/admin/configuracion/limite-turno` (el PUT sí lo tiene);
   - ~~`GET /api/alumnos/aptos`~~ (RESUELTA en la entrega 6-1: lleva `@PreAuthorize`);
   - `GET /api/eventos/{eventoId}/documentos`;
   - `GET /api/catalogo`;
   - `/imagenes/**` y `/documentos/**`.
11. **Protegidos solo por `@PreAuthorize`; la URL cae en `anyRequest().authenticated()`:**
    - `GET /api/grados` y `GET /api/grados/disponibles/{fechaNacimiento}` (D/D/P/P/D);
    - `GET /api/auth/organizaciones` y `POST /api/auth/organizacion` (`isAuthenticated()`, D/P/P/P/P).

    Si se pierde la anotación de los primeros, se abren a toda sesión, incluida SOLO_PLATAFORMA.
12. **SOLO_PLATAFORMA no puede leer `GET /api/auth/user`, `/api/auth/roles`, `/api/auth/user/alumnos` ni
    `…/recordatorios-racha-email` (D/P/P/P/D).** La URL lo deja pasar y el método exige rol de escuela. Puede
    ser intencionado, pero `/api/auth/user` es el propio perfil: conviene confirmarlo con el frontend.

### Públicos por diseño con guarda en el controlador (para revisar, no son fallos)

- `GET /api/preinscripciones/{referencia}/documento` (P×5): exige el `token` de la URL.
- `GET /api/eventos/{eventoId}` y `…/documentos/{documentoId}/descargar` (P×5): el controlador oculta los
  eventos no visibles. `GET /api/eventos/{eventoId}/imagen` (P×5) también lo comprueba desde el hotfix #26
  (16/10/2026): **RESUELTA**, la imagen de un evento oculto responde 404 a quien no gestiona eventos. La
  comprobación está en `EventoController`; la regla de URL no cambia.
- `/documentos/Documentos_Eventos/**`, `/documentos/org-*/eventos/**` e `/imagenes/**` salvo alumnos (P×5):
  `AccesoArchivos` comprueba escuela y fila.
- Latente: el `permitAll` de `GET /api/eventos/{eventoId}` casa con cualquier GET de un solo segmento bajo
  `/api/eventos`. Un futuro `GET /api/eventos/borradores` nacería público. La matriz lo detectaría, porque
  pediría su línea con P en ANONIMO.
