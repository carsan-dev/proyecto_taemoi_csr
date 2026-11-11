# W12 · Editor visual de la web y campos nuevos de sede y evento: desglose

Base: `develop` en 6b6f1f17 (Flyway 32.8: la última es `V32_8__rallas_examen.sql`; ninguna rama remota trae V32_9).
Fuentes: `fase4-web-plan.md` §8 (fila W12), §9 n.º 3, 4 y 11, claves nuevas bajo el §9, §10 (n.º 11 y pendiente de
imágenes); roadmap F4-B; boceto aprobado `fase4-web/boceto-panel-contenido-web-comarcal.html` (d517, d525: manda en la
interfaz y en el catálogo de claves; el patrón de edición sale de `boceto-panel-contenido-web-visual.html`).
Las preguntas que dejaba abiertas el primer desglose las cerró el propietario (d517-d530): ver el §3.

## 1. Qué hay hoy y qué falta

### 1.1 Lo que ya existe (W1-W7 y la API de W2)

| Pieza | Dónde | Estado |
|---|---|---|
| Tablas `web_escuela`, `web_bloque` (`clave` 60, `texto` TEXT, `imagen_id`, `orden`; única por escuela y clave) | `src-api/src/main/resources/db/migration/V32_5__web_escuela.sql`; `entities/WebEscuela.java`, `entities/WebBloque.java` (`@TenantId` vía `EntidadOrganizacion`) | Sin `oculto`. Sin repositorio de escritura de bloques: solo `WebPublicaConsultas.bloques()` (lectura) |
| Columnas de sede y evento | `V32_6__web_sede_evento.sql`: `sede.slug/lugar/indicaciones_acceso/foto_imagen_id/foto2_imagen_id`, `evento.hora/lugar/sede_id` | En producción |
| API de esos campos | `PUT /api/sedes/{id}` acepta `slug`, `lugar`, `indicacionesAcceso` si llegan (`SedeRequest.CAMPOS_WEB`, `SedeController.editar`); `PUT/DELETE /api/sedes/{id}/fotos/{1|2}` (`SedeFotoService`, carpeta pública `org-<id>/web/sedes/`, auditado); `POST /api/eventos/crear` y `PUT /api/eventos/{id}` aceptan `hora`, `lugar`, `sedeId` (`EventoService.CAMPOS_WEB`, `DatosEventoNoValidosException` → 400) | Hecha. **El panel no los envía ni los enseña** (`panel-nuevo/sedes/*`, `panel-nuevo/eventos/evento-formulario.*`: ninguna referencia a `lugar`, `slug`, `hora`, `sedeId`) |
| Lectura pública | `GET /api/publico/web` (`WebPublicaController`, `WebPublicaService`, `WebPublicaDTO.Bloque(clave, texto, imagenUrl, orden)`); vista previa con `vistaPrevia=true` y ROLE_ADMIN/ROLE_MANAGER; 404 para la 1 (legado) | Hecha. ETag y `max-age=60` en lo anónimo |
| Publicar tema | `GET /api/escuela/web`, `PUT /api/escuela/web/publicacion` (`EscuelaWebController`, solo ROLE_ADMIN, 409 Moiskimdo; `PublicacionWebService` con auditoría) | Hecha (W3) |
| Imágenes públicas | `RutasArchivos.esImagenPublica`: lista blanca `eventos/…` y `org-<id>/web/sedes/…`; `CARPETA_WEB = "web"` | El resto de `web/` es privado: las fotos de bloques no tienen carpeta pública (pendiente del §10) |
| Plantilla (frontend) | `src-frontend/src/app/web-plantilla/`: rutas, `comun/bloques.ts` (`BloquesWeb`: texto por defecto si no hay clave, vacío no se pinta, `profesores()`, `galeria()`, `ordenarSecciones`), tema **«La línea comarcal»** (`temas/linea-comarcal/`, único construido), `textos.ts` (claves con su texto por defecto), `vista-previa.ts` | Ya lee las claves `menu.*`, `cabecera.boton`, `horarios.*`, `pie.*`… con su texto por defecto. No conoce `oculto`. Muchos textos siguen fijos (`TEXTOS_FIJOS_LINEA_COMARCAL`, `TEXTOS_PAGINAS_LINEA_COMARCAL`: «los hará editables W12») |
| Panel «Web · Tema» | `panel-nuevo/web/tema/*` (iframe del mismo origen `/<página>?vista-previa=<tema>`), ruta `web-tema` en `app.routes.ts:556` (`adminOnlyGuard`, `sinLegadoTaemoiGuard`, `salirSinGuardarGuard`), entrada «Tema» con `condicion: 'webPlantilla'` en `panel-nuevo/navegacion/menu-panel.ts:161` | Hecha (W7). Patrón a copiar para «Contenido» |
| Tests | `WebPublicaIntegrationTest`, `WebPublicacionIntegrationTest`, `SedeFotosIntegrationTest`, `EventoDatosWebIntegrationTest`, `WebEscuelaV32_5MigracionIntegrationTest`, dorados en `src-api/src/test/resources/dorado/`, `seguridad/matriz-permisos.txt` | Base para los nuevos |

### 1.2 Lo que pide el boceto y no existe

1. **Modelo de bloques**: `oculto` por bloque; orden de secciones en el que una movible puede saltar las fijas
   (Disciplinas, Próximos eventos, Tarifas «se pueden saltar al subir o bajar», boceto `FIJOS`); bloques repetibles
   «Texto con foto»; títulos y frases de cada sección; texto alternativo obligatorio en cada foto (máx. 150);
   galería hasta 6 fotos con su orden; profesores con permiso de publicación.
   Choque detectado: hoy `BloquesWeb.orden('galeria')` toma el menor `orden` de `galeria.<n>`, que también ordena las
   fotos dentro de la galería: hace falta una fila de cabecera por sección para separar ambos órdenes.
2. **API de escritura** (solo propietario, §9 n.º 3): leer el contenido editable, guardarlo de una vez («Guardar»
   publica todo o nada: «Tu web sigue como estaba»), subir fotos a una carpeta pública, validar en el servidor lo
   mismo que el boceto (obligatorios, máximos, 6 fotos, permiso), auditar, 409 para Moiskimdo.
3. **Tema**: pintar `oculto`, el orden nuevo, «Texto con foto» y las claves aún fijas de la portada.
4. **Panel**: pantalla «Web · Contenido» (migas «Web /», h1 «Contenido», línea de estado, «Ver en la web»,
   Móvil | Escritorio, marco «Vista previa de tu web · Inicio», barra «N cambios sin guardar · Descartar / Guardar»,
   avisos, diálogo de confirmación, hoja inferior < 768 px) y la capa de edición dentro del iframe (textos en el
   sitio con chip «nombre · obligatorio/opcional · n / máx», candados «Se cambia en X», barra de bloque, menús de foto
   y de «Añadir bloque»).
5. **Campos nuevos de sede y evento en el panel**: formularios de Sedes (slug, lugar, indicaciones de acceso, fotos
   1 y 2) y de Eventos (hora, lugar, sede). El boceto los da por hechos (candado «Lugar y dirección → Sedes»,
   «Evento: nombre, lugar y hora → Eventos») pero no los dibuja.

Nota: la fila W12 del §8 dice «Migración: no». `oculto` exige una (V32_9): W12a va con `desplegar_migrar`.

## 2. Piezas, en orden (cada una se fusiona y se despliega sola)

Orden: W12a → W12b → W12c → W12c-bis → W12d → W12e → W12f. W12g es independiente (su interfaz está en el
boceto aprobado, d524) y puede ir en cualquier momento. Entrada de menú «Contenido» visible a partir de W12d (antes,
la ruta existe pero no se enlaza: una pantalla que no guarda no debe verse; decisión 341: lo que se despliega se ve).

### W12a · API del contenido de la web + `oculto` (careful)

- **Por qué careful**: abre una carpeta nueva de ficheros públicos (lista blanca de seguridad), escribe datos
  personales de profesores en una web pública y reemplaza el contenido entero en una transacción.
- **DB**: `V32_9__web_contenido.sql`, solo amplía e idempotente con `information_schema` (como V32_6):
  `web_bloque.oculto BOOLEAN NOT NULL DEFAULT FALSE`; constancia del permiso de cada profesor en su fila
  `profesor.<n>.nombre` (`web_bloque.permiso_origen` `PANEL|ALTA`, `permiso_por`, `permiso_en`; d521, d526); tabla
  `web_contenido` (versión del contenido por escuela, d522; sin fila = versión 0); `sede.foto_alt` y
  `sede.foto2_alt` VARCHAR(150) NULL (d523). Postcheck `docs/multi-escuela/postcheck-v32-9.sql`. →
  `desplegar_migrar` + postcheck.
- **Dónde se guarda cada cosa** (elecciones de W12a):
  - Constancia del permiso: **columnas** de `web_bloque`, no claves. Lo público se construye campo a campo y nunca
    las lee; una clave iría en la lista genérica de bloques y dependería de un filtro en cada salida pública.
    `ALTA` («Permiso dado en el alta», sin usuario) la escribe la migración de datos del alta.
  - Versión: tabla `web_contenido` y no columna de `web_escuela`, porque se puede guardar contenido antes de
    publicar (estado «nueva») y `web_escuela.tema` es obligatorio.
  - `oculto`: el anónimo recibe de una sección oculta solo su fila de cabecera (con `oculto: true`, sin texto ni
    imagen) y ninguna de sus filas de contenido; la vista previa de la gestión recibe todas, marcadas. Sin la cabecera,
    el tema caería en sus textos por defecto y pintaría la sección.
- **Catálogo de claves (`ClavesWeb`)**: patrón + máximo + tipo (texto, imagen, cabecera, sedes del profesor) +
  obligatorio, copiados de `TX` y `CAMPOS` del boceto. Cabeceras (sin texto; llevan `orden` y `oculto`):
  `presentacion`, `profesores`, `galeria`, `sedes` (Fotos de las sedes), `texto_foto.<n>` y las fijas `disciplinas`,
  `eventos`, `tarifas` (solo `orden`; no se ocultan). Campos de bloque: `<bloque>.rotulo` (24), `<bloque>.titulo`
  (60), `presentacion.texto` (1200), `texto_foto.<n>.texto` (600), fotos `presentacion.foto`,
  `texto_foto.<n>.foto`, `portada.foto`, `galeria.<n>` (su `orden` = puesto en la galería, máx. 6),
  `profesor.<n>.nombre (60, obl.)|cargo (40)|foto|sedes` (ids de sedes de la escuela, d520), `portada.titular (60,
  obl.)|frase (160)`, `disciplina.<codigo>.descripcion (600)|fila<n>.rotulo (24)|fila<n>.texto (120)|foto` (código
  de una disciplina de la escuela) y todos los textos de las 7 páginas del `TX` del boceto (d519), salvo los errores
  del formulario de preinscripción (d527). En las de imagen, `texto` = descripción (obligatoria, máx. 150). Si un
  texto es igual al del tema, el frontend no lo manda (al cambiar de tema cada uno conserva sus textos por defecto).
- **Endpoints** (`/api/escuela/web/**` ya deja pasar a la gestión; `@PreAuthorize ROLE_ADMIN` de la clase, como W3;
  Moiskimdo 409):
  - `GET /api/escuela/web/contenido`: `version` y todas las filas (también ocultas) con `imagenId`, `imagenUrl`,
    `orden`, `oculto` y, en `profesor.<n>.nombre`, la constancia (`origen`, nombre de quien lo marcó, fecha).
    Funciona sin fila en `web_escuela`.
  - `PUT /api/escuela/web/contenido` (`{version, bloques}`): reemplazo completo en una transacción con la escuela
    bloqueada. Versión distinta → 409 sin escribir nada y sin datos de nadie (d522, d530). 400 con
    `{mensaje, errores:{clave: texto}}` si algo no vale (claves, máximos, obligatorios, ≤ 6 fotos, imagen solo de la
    carpeta pública de bloques de la escuela, permiso de cada profesor sin constancia). Las imágenes de bloques que
    dejan de usarse se borran (fila y, tras confirmar, fichero). Una fila de auditoría por guardado, solo recuentos y
    números de profesor; la ruta en `AuditoriaRequestFilter.EscrituraConRegistroPropio`.
  - `POST /api/escuela/web/imagenes` (multipart `archivo`): `org-<id>/web/bloques/` con la conversión de las fotos de
    sede (JPEG/PNG/WebP, 60 Mpx, HEIC rechazado, sin metadatos); 201 `{imagenId, url}`. No toca bloques.
  - `RutasArchivos.CARPETA_WEB_BLOQUES = "web/bloques"`, pública con las reglas de las fotos de sede.
- **Sedes**: `fotoAlt` y `foto2Alt` en `PUT /api/sedes/{id}` (`SedeRequest.CAMPOS_WEB`: si no llegan, se conservan),
  en `SedeDTO` y en la sede de `/api/publico/web`. Vacío = el texto por defecto del tema.
- **Pendiente tras W12a**: tarea diaria que borre las subidas nunca guardadas (`web/bloques` sin bloque y con más de
  24 h); el tema lee aún la descripción de la portada de `portada.alt` (W12b la toma de `portada.foto`).
- **Moiskimdo igual**: sin filas en `web_bloque` ni en `web_contenido`; endpoints nuevos 409; columnas con su valor
  por defecto.

### W12b · El tema pinta el modelo del editor (medium)

- **Por qué medium**: solo la plantilla pública, perezosa y en hosts de plantilla; lógica pura con tests.
- **Frontend** (`web-plantilla/`): `BloquesWeb` con `oculto(seccion)` y orden por la fila de cabecera (si no hay,
  el comportamiento de hoy); `ordenarSecciones` con la regla del boceto (una lista única en la que las fijas
  conservan su orden relativo y las movibles pueden quedar entre ellas); sección oculta o vacía, fuera;
  «Texto con foto» (repetible) en «La línea comarcal» con el diseño de la presentación sin cifras, como hace el
  boceto con `clubHtml` (d518); claves de título/frase por sección (`presentacion.titulo` y
  `profesores.titulo` ya están; añadir `*.apoyo`, `galeria.titulo`, `horarios.apoyo`, `portada.boton_preinscripcion`,
  `tarifas.punto3`, `tarifas.boton_ver`, `eventos.apoyo`, `eventos.enlace_todos`, `contacto.apoyo`,
  `disciplina.<codigo>.*`); `profesor.<n>.foto` y `galeria.<n>` con alt del bloque.
- **API/DB**: no. **Migración**: no.
- **Tests**: Karma de `bloques.spec.ts` y `portada.component.spec.ts` (oculta, vacía, orden con saltos sobre fijas,
  galería ordenada aparte de su sección, «Texto con foto» 0/1/3); specs nuevos en el `include` de `angular.json`.
  Revisión en navegador contra `boceto-linea-comarcal.html`.
- **Boceto**: reglas de `htmlWeb()`, `estaVacio()`, `FIJOS` del editor; aspecto del tema existente.
- **Moiskimdo igual**: chunk de la plantilla, solo con la meta `taemoi-web`. **Tamaño**: ~400-600 líneas.

### W12c · Pantalla «Web · Contenido»: armazón y vista previa con candados (medium)

- **Frontend**: ruta perezosa `web-contenido` en `app.routes.ts` (mismos guards que `web-tema`), sin entrada de menú
  todavía. Componente `panel-nuevo/web/contenido/`: migas, h1 «Contenido» con foco, línea de estado («Publicada con
  «tema»» / «Tu web aún no está publicada. Lo que guardes aquí saldrá cuando la publiques en Tema.»), ayuda
  («Pulsa un texto o una foto para cambiarlo. …»), «Ver en la web» (pestaña nueva), Móvil | Escritorio (390/1200 px,
  escala si no cabe, «Escritorio, 1200 px · al N %»), marco con el iframe, estados cargando y error (los de W7).
- **Iframe en modo editor**: `/?vista-previa=<tema>&editor=1`. La plantilla solo activa el editor si la API confirma
  la vista previa de gestión; la capa de edición es un chunk perezoso aparte (no entra en los chunks del tema).
  Puente `postMessage` con comprobación de origen: el panel guarda el borrador y lo manda; el iframe pinta con sus
  datos públicos y los bloques del borrador, y devuelve acciones. Enlaces de la web fuera del tabulador.
- **En esta pieza**: envoltorios de bloque y de partes fijas con su nombre («Su sitio lo pone el tema…»),
  selección, contorno punteado y aviso flotante «X · Se cambia en Y» con enlace (o «Lo cambia el equipo de Taemoi»)
  y enlace oculto por tipo de dato para el teclado; los enlaces llevan a la pantalla del panel (Sedes, Grupos,
  Eventos, Tesorería · Productos, Datos de la escuela). Tabla de candados de «La línea comarcal» (d517).
- **Tests**: Karma de estados (publicada, nueva, cargando, error), escala, puente (mensajes de otro origen
  ignorados), candados con su destino; revisión en navegador contra el boceto (móvil y escritorio).
- **Boceto**: cabecera, `herramientas`, `marco`, `dato()`, `envolverFija()`, `mostrarCandado()`.
- **Moiskimdo igual**: `sinLegadoTaemoiGuard` y sin entrada. **Bundle**: solo la ruta perezosa. **Tamaño**:
  ~900-1.200 líneas.

### W12c-bis · Selector de página y «Ver el caso» (medium)

- **Frontend**: selector «Página» con las 7 páginas (d519) y, en «Una sede» y «Un evento», segundo selector con las
  sedes y los eventos de la vista previa de gestión (sedes sin página, desactivadas); `?pagina=` en la dirección, voz
  «Vista previa de X.», sin «· N cambios» (W12d). Candados y partes fijas de las otras 6 páginas en la capa (atributos
  `data-dato` neutros en el tema; destino nuevo Preinscripciones · Plantillas). «Ver el caso» (d528): menú por página
  (hoja inferior en < 768 px), franja del caso activo y «Volver a datos reales»; el caso viaja por el puente (lista
  cerrada) y la capa lo simula en memoria. Fuera: «Sin clases esta semana», los casos y el paso de la preinscripción.
- **Tests**: Karma de selector, URL, voz, candados por página, menú de casos, teclado, puente y que nada va a la API.
- **Boceto**: `PAGINAS`, `SUBS`, `pintarSelector`, `CASOS`, `abrirMenuCasos`, `ponerCaso`, `dato()` de cada página.
- **Moiskimdo igual**: misma ruta y guards de W12c; bundle inicial sin cambios. **Tamaño**: ~1.900 líneas con tests.

### W12d · Textos en el sitio y guardar (medium; careful en la revisión del guardado)

- **Frontend**: textos editables (`contenteditable="plaintext-only"`, rol `button` → `textbox`, Intro/F2 entra,
  Escape sale, Intro termina en una línea), chip con nombre, «obligatorio/opcional» y «n / máx» («· máximo» al
  tope, aviso por voz cada 2,5 s), pegar recortado, misma clave repetida sincronizada (el error solo en la primera).
  Barra inferior «N cambios sin guardar · Al guardar se publican en tu web.» (en «nueva», sin la segunda frase),
  Descartar y Guardar (ocupado «Guardando…»); resumen «Faltan N datos para guardar» con enlaces al dato; avisos
  «Cambios guardados. Ya se ven en tu web.» / «… Saldrán en tu web cuando la publiques.» / error «No se pudieron
  guardar los cambios. Tu web sigue como estaba.» con Reintentar; diálogo «Tienes N cambios sin guardar» al salir
  (también desde un candado) y `beforeunload`; `salirSinGuardarGuard`. Errores 400 del servidor al mismo resumen.
- **Menú**: entrada «Contenido» bajo Web (`condicion: 'webPlantilla'`) en `menu-panel.ts` y sus specs.
- **Tests**: Karma de edición por teclado, máximos y pegado, cálculo de cambios, validación, guardado ok/400/500,
  descartar, salir con cambios. Prueba en la app real con copia de la base y `taemoi-pruebas`.
- **Boceto**: `texto()`, `entrarEdicion/salirEdicion`, `mostrarChip`, `pintarResumen`, `pintarBarra`, guardar,
  descartar, `salir()`; estados `contenido`, `errores`, `guardando`, `guardado`, `error`.
- **Moiskimdo igual**: entrada oculta por `webPlantilla`. **Tamaño**: ~900-1.100 líneas.

### W12e · Bloques: subir, bajar, ocultar, añadir y quitar (medium)

- **Frontend**: barra del bloque (Subir/Bajar deshabilitados en los extremos; Ocultar/Mostrar; Quitar solo en
  «Texto con foto» y en los añadidos), pastillas «Sin guardar», «Oculto en la web · Mostrar», «Vacío: no sale en la
  web»; portada sin acciones («Siempre arriba…»); huecos «Añadir bloque» entre bloques con menú de tipos (los únicos
  ya presentes, deshabilitados: «Ya está en tu web. Si no lo quieres, ocúltalo.»); confirmación «¿Quitar «X» de tu
  web?»; hoja inferior < 768 px con «Puesto i de n · …» y Listo; anuncios por voz; foco tras cada acción.
- **Tests**: Karma de mover sobre fijas, ocultar/mostrar, añadir en un puesto, quitar con confirmación, hoja en
  móvil, foco. **Boceto**: `barraHtml`, `mover`, `alternarOculto`, `quitarBloque`, `anadirBloque`,
  `abrirMenuAnadir`, `abrirHoja`. **Tamaño**: ~600-800 líneas.

### W12f · Fotos, galería y profesores (medium; careful en la revisión: datos personales)

- **Frontend**: capa de foto (añadir/cambiar), subida con progreso, «Cancelar subida», fallos «Demasiado grande» y
  «Formato no admitido» con «Elegir otra foto» («La foto de antes sigue puesta.»), «Nueva, sin guardar»; menú de foto
  (Cambiar, Describir, Volver a la anterior, Mover antes/después en la galería, Quitar; en profesores «Sale con sus
  iniciales»); diálogo «describe la foto» (150, «Sin nombres de alumnos.»), que se abre al terminar la subida;
  galería «n de 6 fotos» y añadir; profesores: añadir tarjeta, monograma, «Quitar» con confirmación («Su ficha en el
  equipo no cambia»), casilla «Tengo su permiso para publicar su nombre y su foto (obligatorio)» en los nuevos.
  Guardar deshabilitado mientras sube algo.
- **API**: usa `POST /api/escuela/web/imagenes` de W12a (progreso con `reportProgress`).
- **Tests**: Karma de cada estado de subida, menú por tipo de foto, límite de 6, permiso obligatorio, alt
  obligatoria. **Boceto**: `fotoCapa`, `subidaHtml`, `abrirMenuFoto`, `abrirDescribir`, `galeriaHtml`,
  `profeHtml`; `?subida=`. **Tamaño**: ~1.000-1.300 líneas.

### W12g · Campos de sede y evento en el panel (medium)

- **Frontend** (API hecha en W2): en Sedes, `slug`, `lugar`, `indicacionesAcceso` (se envían en el `PUT`; si no
  llegan, el servidor los conserva) y fotos 1 y 2 con `PUT/DELETE /api/sedes/{id}/fotos/{n}`; en el formulario de
  Eventos, `hora`, `lugar` y `sede`. Solo para escuelas de plantilla (con `legado` el formulario queda como hoy).
- **Boceto**: grupo «En la web» de los formularios (d524); incluye `fotoAlt`/`foto2Alt` (d523). **Tests**: Karma de envío con y sin campos, 400 de slug repetido o
  sede de otra escuela, Moiskimdo sin campos. **Tamaño**: ~600-900 líneas.

## 3. Decisiones del propietario (d517-d530)

Cierran las ocho preguntas que dejaba abiertas el primer desglose. El boceto aprobado es
`fase4-web/boceto-panel-contenido-web-comarcal.html`.

| # | Decisión | Cierra |
|---|---|---|
| d517 | Boceto nuevo del editor sobre «La línea comarcal» (el patrón de edición sale del boceto visual del 07/11). | n.º 2 |
| d518 | Cinco tipos de bloque: Presentación, Profesores, Galería, Fotos de las sedes y Texto con foto (este, repetible). | n.º 3 |
| d519 | Se editan las 7 páginas (Inicio con cabecera y pie, una sede, tarifas, eventos, un evento, legales y preinscripción), con selector de página. | n.º 4 |
| d520 | Cada profesor lleva sus sedes guardadas en el bloque (`profesor.<n>.sedes`), nunca sacadas de `asignacion_profesor`. | n.º 5 |
| d521 | La constancia del permiso (quién lo marcó y cuándo) se guarda con el bloque y solo se ve en el editor; nunca en la API pública. | n.º 6 |
| d522 | Si otra persona guardó antes: 409, no se pisa nada y hay que recargar. | n.º 7 |
| d523 | Texto alternativo editable para cada una de las dos fotos de sede; se cambia solo en Sedes. | n.º 8 |
| d524 | Los campos de la web de Sedes y Eventos (grupo «En la web») van en el mismo boceto. | n.º 1 |
| d525 | Boceto aprobado. | — |
| d526 | Profesores del alta: «Permiso dado en el alta», con su fecha. | n.º 6 |
| d527 | Los mensajes de error del formulario de preinscripción no son editables (no tienen clave). | n.º 4 |
| d528 | Botón «Ver el caso». | — |
| d529 | Fila del tema en la vista previa de Eventos. | — |
| d530 | El aviso de conflicto solo dice «otra persona» (ni nombre ni hora de quien guardó). | n.º 7 |
| d532 | El selector de página (d519) y «Ver el caso» (d528) van en pieza propia, W12c-bis, antes de W12d (11/11). | — |

Con d519 y d518, el alcance de W12c-W12f crece respecto a la estimación del §2: selector de página, bloques «Fotos de
las sedes» y «Texto con foto», sedes del profesor y estado de conflicto. W12g (d524) ya no espera boceto.

## 4. Notas para los briefs

- Toda pieza visual: skill `impeccable`, tokens de `DESIGN.md`/`PRODUCT.md`, revisión en navegador contra el boceto
  HTML; specs nuevos también en el `include` de Karma de `angular.json`.
- Medir el bundle inicial tras `npm ci` en el worktree antes y después de W12c y W12d (aviso en 1.500.000 bytes);
  ningún import estático hacia `web-plantilla/` ni `panel-nuevo/web/contenido/` desde `app.routes.ts`.
- «Ya se ven en tu web»: los visitantes anónimos pueden ver la versión anterior hasta 60 s (`max-age=60`); la
  sesión del propietario ve lo nuevo al momento (`no-store`).
- Sierra Sur sale con su contenido por migración de datos revisada (§9 n.º 4) hasta W12d; el editor debe leer esas
  filas tal cual (claves de W1/W5).
- Maven: `./mvnw -o verify` completo en W12a (dorados de JSON); logs fuera del repo.
