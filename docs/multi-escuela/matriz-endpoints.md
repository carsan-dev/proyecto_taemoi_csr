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
| `control*` | el arnés deja de reproducir casos conocidos por HTTP real (p. ej. `/api/plataforma/errores`) |

## Cómo leer el registro

```
# METODO RUTA                              ANONIMO FAMILIA GESTOR  PROPIETARIO SOLO_PLATAFORMA
GET      /api/alumnos/{alumnoId}/grupos    DENIEGA PERMITE PERMITE PERMITE     DENIEGA   # AlumnoController#obtenerGruposDeAlumno
```

| Columna | Sesión | Autoridades |
|---|---|---|
| ANONIMO | sin sesión | (token anónimo) |
| FAMILIA | FAMILIA | `ROLE_USER` |
| GESTOR | ADMINISTRADOR, RESPONSABLE_SEDE, PROFESOR | `ROLE_MANAGER` |
| PROPIETARIO | PROPIETARIO | `ROLE_ADMIN` |
| SOLO_PLATAFORMA | admin. de plataforma sin membresía | solo `ROLE_ADMIN_PLATAFORMA` |

- `PERMITE` = la seguridad declarativa deja llegar la petición al controlador. No garantiza un 200: el
  controlador o el servicio pueden seguir negando (p. ej. `usuarioPuedeAccederAlumno` para la familia, o
  `AccesoArchivos` para ficheros, que responde 404).
- `RUTA` es el patrón tal cual del controlador. Para los dos manejadores comodín (`/imagenes/**`,
  `/documentos/**`) cada línea es una **ruta de ejemplo** concreta, una por regla de URL distinta. Las rutas
  de `MUESTRAS_COMODIN` son obligatorias: si falta la línea de alguna, en GET o en HEAD, el test A falla.
- **HEAD.** Spring MVC atiende HEAD con los manejadores GET, pero Spring Security ve el método HEAD, y casi
  todas las reglas de URL son solo GET. Por eso cada GET tiene también su línea `HEAD`, con su propia decisión.
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
   La invocación lleva argumentos nulos y no devuelve nada. Por eso, si una expresión de
   `@PreAuthorize`/`@PostAuthorize`/`@PreFilter`/`@PostFilter` usa argumentos (`#`), beans (`@`) o
   `returnObject`/`filterObject`, el test da ERROR nombrando el endpoint y pide ampliar el arnés, en vez de
   decidir (`EvaluadorAutorizacionSalvaguardaTest`). Esto pasará con el bean `@acceso` de la fase 3.
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

**Fuera de alcance, a propósito.** Estas comprobaciones no se evalúan:

- CSRF (solo afecta a métodos no GET fuera de `/api/**`, y el único manejador así es `/error`).
- `OPTIONS`: Spring MVC lo responde de forma implícita y la URL lo deja pasar en `/api/**`.
- Las comprobaciones programáticas (propiedad de la ficha, escuela activa, fila `Imagen`/`Documento`).
- El alcance por sede o grupo.

## Anomalías encontradas (no corregidas)

Decisiones en orden A/F/G/P/SP = ANONIMO/FAMILIA/GESTOR/PROPIETARIO/SOLO_PLATAFORMA; P = PERMITE, D = DENIEGA.

### Graves

1. **GESTOR lo puede casi todo, y GESTOR incluye RESPONSABLE_SEDE y PROFESOR.** Hay 188 líneas no públicas
   con G=P. Ninguna se limita por sede o grupo, y muchas son las que §3 niega al profesor. Ejemplos:
   - `GET /api/tesoreria/movimientos`, `GET /api/informes/deudas`, `POST /api/productos-alumno/mensualidades/general`
     (D/D/P/P/D): §3 «Tesorería»: PROFESOR —.
   - `GET|PUT /api/alumnos/{alumnoId}/salud`, `DELETE /api/alumnos/{id}`, `POST /api/alumnos/crear` (D/D/P/P/D):
     §3: PROFESOR — en alta/edición/baja y solo sus grupos en salud.
   - `POST /api/preinscripciones/{referencia}/finalizar`, `POST /api/eventos/crear` (D/D/P/P/D): PROFESOR — en §3.

   Es el hueco que cerrarán `ROLE_RESPONSABLE_SEDE` y `ROLE_PROFESOR`. Cuando lleguen, la columna GESTOR se
   desdoblará.
2. **`POST /api/mail/enviar`: P/P/P/P/P**, sin `@PreAuthorize`. Desde el anonimato se hace que el servidor
   envíe un correo a una dirección fija de Taemoi (`EmailController`). El texto del usuario se concatena sin
   escapar en el HTML. En multi-escuela, el formulario de cualquier escuela llega a Taemoi. No hay límite de
   frecuencia en la capa de URL ni en el controlador (no se comprobó en otras capas).
3. **HEAD se salta las reglas de URL que son solo GET.** Spring MVC ejecuta el manejador GET (sin cuerpo en la
   respuesta), pero la URL decide por el método HEAD. Casi todas las reglas son `HttpMethod.GET`, así que HEAD
   suele caer en `anyRequest().authenticated()`. De 120 líneas HEAD, 17 deciden distinto que su GET.

   Más abiertas que el GET: 3 líneas, 5 casillas. En ellas el handler se ejecuta y la respuesta revela
   existencia y tamaño:

   | Ruta | GET (A/F/G/P/SP) | HEAD (A/F/G/P/SP) | Por qué |
   |---|---|---|---|
   | `/api/alumnos/aptos` | D/D/P/P/D | **D/P/P/P/P** | sin `@PreAuthorize`; la regla `GET /api/alumnos/**` no cubre HEAD |
   | `/api/eventos/{eventoId}/documentos` | D/D/P/P/D | **D/P/P/P/P** | sin `@PreAuthorize` |
   | `/api/alumnos/{id}/completo` | D/D/P/P/D | **D/P/P/P/D** | su `@PreAuthorize` incluye `ROLE_USER` (anomalía 9) |

   Más cerradas que el GET: 14 líneas. Son rutas públicas en GET que en HEAD piden sesión (anónimo D):
   - `/api/catalogo/publico`, `/api/eventos`, `/api/eventos/{eventoId}` y `…/documentos/{documentoId}/descargar`;
   - `/api/turnos/dto`, `/api/turnos/limite` y `/healthz`;
   - `/api/preinscripciones/public/*` (4 rutas) y `/api/preinscripciones/{referencia}/documento`;
   - los dos ejemplos de documentos de eventos (`/documentos/Documentos_Eventos/1.pdf`,
     `/documentos/org-1/eventos/1.pdf`).

   Las de preinscripciones y documentos de eventos además niegan a FAMILIA y SOLO_PLATAFORMA, porque las
   reglas `/api/preinscripciones/**` y `/documentos/**` no distinguen método. No es fuga, pero rompe los HEAD
   de clientes y proxies.

   El resto de HEAD coincide con su GET. Hay dos motivos: o la regla no distingue método (`/imagenes/**`,
   `/api/admin/**`, `/api/plataforma/**`, `/api/catalogo`), o el `@PreAuthorize` repite la restricción. La
   corrección en producción va en un hotfix aparte. El registro describe el comportamiento de la base
   `344cc683`.

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

En GET y en el resto de métodos declarados manda la regla más estricta, así que no hay fuga. Con HEAD sí la
hay: ver la anomalía 3, que afecta a la 9 (`/completo`) y a las rutas de la 10 sin `@PreAuthorize`. El riesgo
general está en editar una sola de las dos capas.

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
   - `GET /api/alumnos/aptos`;
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
  eventos no visibles. **Excepción**: `GET /api/eventos/{eventoId}/imagen` (P×5) no comprueba la visibilidad
  en `EventoController#obtenerImagenEvento`. Cualquiera puede leer, por id, la imagen de un evento oculto.
- `/documentos/Documentos_Eventos/**`, `/documentos/org-*/eventos/**` e `/imagenes/**` salvo alumnos (P×5):
  `AccesoArchivos` comprueba escuela y fila.
- Latente: el `permitAll` de `GET /api/eventos/{eventoId}` casa con cualquier GET de un solo segmento bajo
  `/api/eventos`. Un futuro `GET /api/eventos/borradores` nacería público. La matriz lo detectaría, porque
  pediría su línea con P en ANONIMO.
