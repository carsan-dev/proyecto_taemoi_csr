# Fase 4 · Web por plantilla: plan

Estado: propuesta (07/11/2026), sin código. Base: `develop` en 473605a1, Flyway 32.2 en el repo (V33 la reserva
`fase4-dominio-plan.md`). Cubre las piezas del roadmap «Plantilla con bloques editables (textos, imágenes, colores y
orden)», «Horarios, tarifas, eventos, sedes y preinscripción salen de la gestión» y «Textos legales y SEO por escuela».

Fuente de verdad de la interfaz: los bocetos de `docs/multi-escuela/fase4-web/` (4 temas × 7 páginas y
`boceto-panel-tema-web.html`). Este plan no añade interfaz: lo que los bocetos no enseñan va al §9.

Regla de oro: **la web pública de Moiskimdo (organización 1) no cambia.** Ni `index.html`, ni sus rutas, ni sus
componentes, ni el JSON de sus endpoints públicos.

## 1. Lo ya decidido

| Fuente | Qué dice |
|---|---|
| `modelo-datos.md:36-46` (decisión 12) | Plantilla con bloques editables (textos, imágenes, colores, orden), no editor libre. Horarios, tarifas, eventos, sedes y preinscripción salen de la gestión. Dos niveles: plantilla (Sierra Sur) y web a medida (Taemoi/Moiskimdo, que «sigue dentro de la aplicación» hasta separarse). |
| Roadmap (decisiones 450, 469) | Cuatro temas aprobados, cada uno bocetado entero: «La línea comarcal», «El chándal del club», «Una sede por pantalla», «El estándar del sector». |
| 464 | Sin aviso de cookies: la plantilla solo usa almacenamiento técnico (lo dice `boceto-*-legales.html`, sección Cookies). Sin GA4. |
| 466 | Hora, lugar y sede del evento, opcionales; si faltan, no se pinta nada. |
| 467 / 475 | Pantalla «Web · Tema» en el panel nuevo (perfil propietario): elige tema, vista previa móvil/escritorio por página con datos reales, publicar con confirmación. Logo y color, del equipo de Taemoi (136). |
| 470 | Plazas: cada estado con su forma (punto, medio punto, tachado, reloj). |
| 393 / 476 | La preinscripción elige primero la sede. |
| 136 | Logo y color, solo el superadmin («Logo y color»). |
| `fase4-dominio-plan.md` | Escuela anónima por Host (pieza 1), `GET /api/publico/escuela` con `tema` (pieza 4), nginx por host (pieza 6). Esta web depende de las piezas 1, 3 y 6 de ese plan. |

## 2. Estado actual

### 2.1 Qué pide cada página y qué hay en la API

Rutas públicas hoy (`SecurityConfiguration.java`): `GET /api/catalogo/publico` (:265), `POST /api/preinscripciones` y
`GET /api/preinscripciones/public/**` (:269-271), `GET /api/turnos/dto` y `/limite` (:411-412), eventos con imagen,
fotos y documentos (:434-443), `POST /api/mail/**` (:581). Sedes (:229), tarifas por sede (:545-548) y marca
(:257-258) exigen sesión.

| Dato del boceto | Existe | Dónde | Falta |
|---|---|---|---|
| Nombre, logo, color de la escuela | Sí (privado) | `Organizacion.java` (`nombre`, `logoImagenId`, `colorPrimario`); `EscuelaMarcaController` con sesión | Lectura anónima (pieza 4 del plan de dominio) |
| Sede: nombre, dirección, teléfono | Sí (privado) | `Sede.java:22-37` | Lectura pública |
| Sede: «lugar» (Pabellón municipal), «acceso» (por dónde se entra), foto y foto2, slug para la URL | No | — | Columnas nuevas |
| Grupo por sede: nombre, edades, días, horas | Sí | `Grupo.java:61-68` (`sedeId`, obligatoria desde V32_1), `Turno` | Endpoint por sede (los públicos no llevan sede: `PreinscripcionService.java:119-120`) |
| Plazas «Quedan N / Últimas N / Completo / Lista de espera» | Casi | `TurnoPublicoDTO.java:42-46` (`ocupacionEfectiva`, `aforoEfectivo`, `completo`); `EstadoPreinscripcion.EN_LISTA_ESPERA`; `PreinscripcionService.java:110,142` | Recuento de la lista de espera por turno; regla del grupo (§4.3) |
| Profesores: nombre, grado («Cinturón negro 5.º dan»), sedes, foto | No | `AsignacionProfesor.java:29-40` (usuario y grupo, sin grado ni foto) | Bloque editable (§9, n.º 5) |
| Tarifas: general, familiar (adulto/menor), hermanos; tarifa por sede con fecha; licencia y examen | Sí (privado) | `Tarifa.java` (`importe`, `importeAdulto`, `importeMenor`, `orden`, `activa`), `TarifaSede.java:26-44` (`vigenteDesde`), `Producto` (`tipo`, `precio`) | Lectura pública (§9, n.º 6) |
| Eventos: título, fecha, foto, descripción, documentos, galería | Sí | `Evento.java:30-59`, `EventoFoto`, rutas públicas de eventos | — |
| Evento: hora, lugar, sede (opcionales, 466) | No | — | Columnas nuevas |
| Normas de la actividad | Sí | Plantilla de preinscripción por disciplina (`PreinscripcionService.java:117`, V17) | — |
| Preinscripción por sede y turnos | Sí | `PreinscripcionRequest` (`deporte`, `turnoIds`; una sede por solicitud, D6-7) | Sede en los endpoints públicos (393) |
| Firma «con mi nombre» (casilla) o a mano | Solo a mano | `PreinscripcionRequest.java:25` (`firmaBase64` `@NotBlank`) | Firma por nombre (§9, n.º 7) |
| Legales: titular, CIF, domicilio, correo, teléfono | Sí (privado) | `Organizacion.java` (`nombreLegal`, `cif`, `direccion`, `localidad`, `provincia`, `codigoPostal`, `emailContacto`, `telefono`) | Lectura pública solo para Legales |
| Contacto: correo y teléfono (sin formulario) | Sí | `emailContacto`, `telefono`, `Sede.telefono` | — |
| Textos de portada, presentación, disciplinas (texto y ficha), galería de la portada | No | — | Bloques editables |
| Tema elegido y web publicada | No | — | Tabla nueva |

### 2.2 Frontend

| Hecho | Evidencia |
|---|---|
| Un solo build, sin SSR ni prerender; `index.html` es de Moiskimdo: GA4 con consentimiento (:1-15), título, meta, `canonical`, OG (:17-61) y tres JSON-LD (:62, :196, :275). | `src-frontend/src/index.html`, `angular.json:28` |
| Las rutas públicas de Moiskimdo ocupan las que necesita la plantilla: `''` (:83, portada en el bundle inicial), `eventos`, `eventos/:eventoId`, `tarifas`, `preinscripcion`, `aviso-legal`, `politica-*`, `contacto` (:143-181) y el `**` público (:575). | `app/app.routes.ts` |
| La cáscara pinta cabecera, pie, botón de subir y aviso de cookies de Moiskimdo en toda ruta pública (`modo() === 'hoy'`). GA4 con el id de Moiskimdo. | `skeleton.component.html:5-6,21-26`; `skeleton.component.ts:82,109-116` |
| `SeoService` reescribe título, meta, `canonical` y JSON-LD de Moiskimdo en **cada** navegación; sin `data.seo`, los valores por defecto de Moiskimdo. | `seo.service.ts:21,87-123`; `seo.constants.ts:9` |
| `robots.txt` y `sitemap.xml` estáticos de Moiskimdo. | `src-frontend/src/robots.txt`, `sitemap.xml` |
| nginx: `try_files … /index.html`; CSP con `frame-ancestors 'self'` y Google Fonts permitido: un iframe del mismo origen funciona. | `nginx-production.conf:181`; `nginx-cabeceras-seguridad.conf:21,40` |
| Panel: «Ver la web» en Escuela › Web. | `panel-nuevo/navegacion/menu-panel.ts:146` |
| Presupuesto: aviso a 1.500.000 bytes (`angular.json:82-86`); referencia 1.499.939: **61 bytes de margen**. | `AGENTS.md` |
| Cada tema de los bocetos carga dos familias de Google Fonts propias (Big Shoulders + Hanken; Saira + Atkinson; Instrument Sans + Serif; Archivo + Source Sans). | `boceto-*.html` |

## 3. Arquitectura recomendada

### 3.1 Una funcionalidad perezosa con capa de temas

```
src-frontend/src/app/web-plantilla/
  web-plantilla.routes.ts        rutas hijas; cada tema, su chunk
  datos/web-publica.service.ts   una petición por página, signals, caché en memoria
  datos/modelos.ts               SitioWeb, SedeWeb, GrupoWeb, Plazas, TarifaWeb, EventoWeb, Legal
  comun/                         lógica compartida sin estilo: plazas, días, próxima clase, enlace a mapas,
                                 colores derivados (contraste AA de los bocetos), SEO por escuela, formulario
                                 de preinscripción (validación y envío)
  temas/linea-comarcal/          7 páginas + estilos + fuentes del tema
  temas/chandal/
  temas/sede-por-pantalla/
  temas/estandar/
```

- Los datos y la lógica son comunes; cada tema solo pone plantilla HTML, estilos y movimiento (los bocetos comparten
  el mismo modelo de datos: `SEDES`, `PROFES`, `TARIFAS`, `TARIFAS_SEDE`, `PRODUCTOS`, `EVENTOS`, `NORMAS`).
- Las páginas: portada, sede, preinscripción, tarifas, eventos, evento y legales. Las variantes de una sola sede
  (`?sedes=1` en los bocetos) salen de los datos, no de otra página.
- Rutas propuestas: `/`, `/sedes/:slug`, `/preinscripcion` (`?sede=&grupo=&dias=`), `/tarifas`, `/eventos`,
  `/eventos/:id`, `/legales` (anclas `#aviso-legal`, `#privacidad`, `#cookies`, `#normas`).

### 3.2 Cómo se elige la web sin tocar la de Moiskimdo

1. **nginx decide** (ya sabe el host): los bloques de escuelas con plantilla sirven `index-plantilla.html` en el
   `try_files`; moiskimdo.es sigue con `index.html`. Depende de la pieza 6 del plan de dominio.
2. `index-plantilla.html`: cabeza neutra (charset, viewport, `<meta name="taemoi-web" content="plantilla">`), sin GA4,
   sin JSON-LD ni meta de Moiskimdo. Se genera tras `ng build` con un script que copia las etiquetas de los bundles
   del `index.html` compilado a una plantilla propia (`src/index-plantilla.html`); el `index.html` de Moiskimdo no se
   toca. En local, una configuración `plantilla` de `angular.json` con `index: src/index-plantilla.html`.
3. `app.routes.ts`: un grupo **delante** de las rutas públicas, `{ path: '', canMatch: [esWebPlantilla],
   loadChildren: () => import('./web-plantilla/web-plantilla.routes') }`. `esWebPlantilla` mira la meta de forma
   síncrona (sin petición: la portada de Moiskimdo no espera nada). Sin la meta, el grupo no casa y todo sigue como hoy.
4. Las rutas del panel, el login y demás no están en el grupo: caen a las de siempre en cualquier host.
5. En un host de plantilla, las rutas solo de Moiskimdo (`/taekwondo`, `/horarios`, `/aviso-legal`, …) van a la
   página equivalente de la plantilla o a su 404 (ver §9, n.º 10).
6. Cáscara: `data.webPlantilla` en las rutas del grupo → `modo()` devuelve `'plantilla'`: solo `<ng-content>`, sin
   cabecera, pie ni aviso de cookies; sin GA4 (no se llama a `acceptCookies`). Moiskimdo nunca lleva ese dato.
7. `SeoService.applyRouteSeo`: sale sin hacer nada si la ruta lleva `data.webPlantilla`; la plantilla pone su SEO.

Alternativa descartada: una segunda aplicación Angular (proyecto aparte en `angular.json`) servida por nginx en las
rutas públicas de los hosts de plantilla. Pro: cero bytes en el bundle de Moiskimdo. Contra: dos builds, dos
servidores en local, nginx con rutas repartidas entre dos aplicaciones y código compartido por rutas relativas. Solo
compensa si el propietario no acepta los pocos cientos de bytes del §7.

### 3.3 Vista previa del panel

- «Web · Tema» incrusta la página real en un `<iframe>` del mismo origen:
  `/<página>?vista-previa=<tema>` (el boceto hace lo mismo con `?color=`).
- `vista-previa` solo lo atiende la plantilla si la API lo confirma: `GET /api/publico/web?vistaPrevia=true` exige
  sesión de gestión (la cookie viaja sola, mismo origen); devuelve los datos aunque la web no esté publicada.
  Sin sesión, se ignora y se ve lo publicado. La vista previa lleva `noindex`.
- El tema elegido y no publicado vive en la pantalla (estado «Cambios sin publicar» del boceto; «Descartar» vuelve
  al publicado). Publicar = `PUT` del tema. Sin borrador guardado (§9, n.º 2).
- Requiere la cookie de sesión en el host de la escuela (pieza 3 del plan de dominio); en local, `localhost`.

## 4. Modelo de datos (ampliar, sin contraer)

### 4.1 Tablas nuevas (`V34__web_escuela.sql`, o la siguiente libre)

```sql
CREATE TABLE web_escuela (
  organizacion_id BIGINT PRIMARY KEY,
  tema VARCHAR(30) NOT NULL,                 -- linea-comarcal | chandal | sede-por-pantalla | estandar
  publicada BOOLEAN NOT NULL DEFAULT FALSE,
  publicada_en DATETIME NULL,
  publicada_por BIGINT NULL,
  actualizada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_web_escuela_org FOREIGN KEY (organizacion_id) REFERENCES organizacion (id)
);
CREATE TABLE web_bloque (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organizacion_id BIGINT NOT NULL,
  clave VARCHAR(60) NOT NULL,                -- portada.titular, portada.frase, portada.foto, presentacion.texto,
                                             -- disciplina.<codigo>.texto, …, galeria.<n>, profesor.<n>
  texto TEXT NULL,
  imagen_id BIGINT NULL,
  orden INT NOT NULL DEFAULT 0,
  CONSTRAINT uk_web_bloque UNIQUE (organizacion_id, clave),
  CONSTRAINT fk_web_bloque_org FOREIGN KEY (organizacion_id) REFERENCES organizacion (id),
  CONSTRAINT fk_web_bloque_img FOREIGN KEY (imagen_id) REFERENCES imagen (id)
);
```

- Ninguna fila para la organización 1: Moiskimdo no tiene web de plantilla y sus datos no cambian.
- Tabla aparte y no columnas en `organizacion`: `organizacion` es global y su marca la escribe solo el superadmin
  (136); el tema lo publica el propietario (467). Permisos y auditoría separados.
- `web_bloque` con `@TenantId`, como el resto de datos de negocio.

### 4.2 Columnas nuevas (`V35__web_sede_evento.sql`)

| Tabla | Columna | Uso |
|---|---|---|
| `sede` | `slug VARCHAR(60) NULL`, único por escuela | `/sedes/:slug` |
| `sede` | `lugar VARCHAR(120) NULL` | «Pabellón cubierto municipal» |
| `sede` | `indicaciones_acceso VARCHAR(300) NULL` | «Entrada por la calle lateral…» |
| `sede` | `foto_imagen_id`, `foto2_imagen_id BIGINT NULL` | fotos de la sede |
| `evento` | `hora TIME NULL`, `lugar VARCHAR(160) NULL`, `sede_id BIGINT NULL` | decisión 466 |

Todas nulas: nada cambia para quien no las rellena. Postcheck: recuentos de `sede` y `evento` iguales y columnas nuevas
a NULL en la organización 1.

### 4.3 Reglas calculadas (sin columnas)

- Plazas de un turno: `libres = aforoEfectivo − ocupacionEfectiva` (incluye solicitudes, como hoy).
  `libres ≥ 1` → «Quedan N» (sin «Últimas N»: regla de los bocetos de la 450; el `n <= 2` del de «La línea
  comarcal» se quita al construirlo); `0` y nadie en espera → «Lleno»; `0` con alguien en espera → «Lista de
  espera». Del grupo: el peor de sus turnos; en la preinscripción, por día.
- Datos de la presentación (n.º de sedes, edad de inicio, mensualidad, grupos por sede): de sedes, grupos y tarifas.
- Temporada («curso 2026-2027»): `TemporadaService.actual()`.

## 5. Piezas de la API

| Endpoint | Acceso | Contenido |
|---|---|---|
| `GET /api/publico/web` | anónimo; `vistaPrevia=true` exige P o G | escuela (nombre, color, URL del logo), tema, bloques, sedes con grupos, días, horas y plazas, tarifas (general, por sede vigentes y futuras), licencia y examen, 3 próximos eventos, contacto. 404 si la escuela no tiene fila en `web_escuela` o no está publicada (salvo vista previa). `ETag` + `Cache-Control: public, max-age=60`. |
| `GET /api/publico/web/eventos` | anónimo | `{proximos, anteriores}`, como mucho 60 en cada lista, con hora, lugar y sede si existen. El detalle es `GET /api/publico/web/eventos/{id}` (campos públicos e ids de documentos y fotos), no `GET /api/eventos/{id}`; los ficheros, por los endpoints anónimos de siempre, aislados por escuela. |
| `GET /api/publico/web/legal` | anónimo | titular (`nombreLegal`, `cif`, domicilio, correo, teléfono), normas por disciplina, fecha de actualización. Solo aquí sale el CIF (lo exige el art. 10 LSSI); `/api/publico/escuela` sigue sin él. |
| `GET /api/escuela/web`, `PUT /api/escuela/web/publicacion` | propietario (P) | tema publicado y estado; publicar con un tema. Auditado. |
| `GET/PUT /api/escuela/web/bloques` | P (y G si §9, n.º 3) | bloques; imágenes con la subida que ya existe. Necesita boceto (§9, n.º 4). |
| `GET /robots.txt`, `GET /sitemap.xml` | anónimo, por Host | solo en los bloques nginx de plantilla; Moiskimdo sigue con sus ficheros estáticos. |
| `GET /api/preinscripciones/public/grupos|turnos/{deporte}` | anónimo | **añadir** `sedeId` y `sede` (393). Campo nuevo: revisar los tests dorados de JSON. |
| `POST /api/preinscripciones` | anónimo | firma por nombre si se decide (§9, n.º 7). |

Nunca en lo público: alumnos, ocupación con nombres, DNI, firmante, buzones internos ni `aforo` de otras escuelas.
Todo lo anónimo sale de la escuela del Host (pieza 1 del plan de dominio); hasta entonces, de la 1, que no tiene fila
→ 404: Moiskimdo no ve nada nuevo.

## 6. Piezas del frontend, SEO y legales

| Pieza | Qué |
|---|---|
| Armazón | grupo de rutas con `canMatch`, `modo 'plantilla'` en la cáscara, salida temprana en `SeoService`, `index-plantilla.html` y su script, configuración `plantilla` para `ng serve`. |
| Datos | `WebPublicaService` (una petición por página), modelos, reglas de §4.3, colores derivados con contraste AA (la función `derivarColores` de cada boceto, con sus tests). |
| Temas | cada uno en su chunk; fuentes del tema por `<link>` al activarse (CSP ya permite Google Fonts) o servidas desde `assets` si el propietario lo prefiere. Movimiento solo sin `prefers-reduced-motion`, como dicen los bocetos. |
| SEO por escuela | título, descripción, `canonical` (host principal), OG y JSON-LD (`SportsActivityLocation` por sede, `Event` por evento) en cada página; favicon con el logo de la escuela. Google ejecuta JS; las vistas previas de WhatsApp y redes, no: ven la cabeza neutra de `index-plantilla.html` (§9, n.º 9). |
| Legales | página única con aviso legal, privacidad, cookies y normas; texto fijo de la plantilla con los datos de la escuela (boceto `*-legales`). Sin aviso de cookies (464): la plantilla solo usa `sessionStorage` (borrador de la preinscripción) y `localStorage` (última sede), como dice la tabla del boceto. |
| Preinscripción | formulario común (validación de los bocetos: DNI/NIE, teléfono +34, menor con responsable, normas, firma) con piel por tema; sede primero; lista de espera con su posición en la confirmación (ya lo devuelve `POST`). |
| Panel «Web · Tema» | ruta perezosa del panel nuevo en Escuela › Web, solo propietario; elegir tema, móvil/escritorio, página, publicar con diálogo (boceto `boceto-panel-tema-web.html`, armazón de 8b-D). |

Todo trabajo visual carga la skill `impeccable` y se revisa en navegador contra el boceto HTML. Los specs nuevos van
también a la lista `include` de Karma en `angular.json` (si el total no sube, no se ejecutan).

## 7. Bundle inicial

- Al bundle inicial solo llegan: la entrada del grupo con `canMatch` y `loadChildren`, la ruta perezosa del panel,
  la rama `'plantilla'` de la cáscara y la salida de `SeoService`. Estimado: 300-600 bytes.
- (Cifra antigua de 61 bytes de margen; el 07/11 se midieron unos 188 KB y se decidió el login perezoso: ver §9, n.º 1.)
  Medir en el worktree tras `npm ci` antes y después de cada pieza.
- Todo lo demás, perezoso: temas, datos, formulario, fuentes, pantalla del panel. Ningún import estático desde
  `app.routes.ts` hacia `web-plantilla/`.

## 8. Piezas en orden (cada una se fusiona sola)

| # | Pieza | Nivel | Migración | Boceto que implementa | Moiskimdo igual porque… | Tests |
|---|---|---|---|---|---|---|
| W1 | Modelo `web_escuela` + `web_bloque`, entidades, `GET /api/publico/web` y `/legal`, plazas por turno y grupo | careful | V34 → `desplegar_migrar` + postcheck | datos de todos | la 1 no tiene fila: 404; endpoints existentes intactos | unitarios de plazas (bordes 0/1/2/3, espera); integración: anónimo de otra escuela ve sus datos y nunca campos privados; org 1 → 404; vista previa sin sesión → 404; `-Dgroups=migracion`; batería completa |
| W2 | Columnas de sede y evento, expuestas en la API pública y en los DTO del panel (solo lectura) | medium | V35 → `desplegar_migrar` + postcheck | `*-sede`, `*-evento`, `*-eventos` | columnas nulas; JSON del panel con campos nuevos nulos | dorados de JSON actualizados; slug único por escuela |
| W3 | `GET/PUT /api/escuela/web` (publicar tema), auditoría | medium | no | panel (publicar) | Moiskimdo sin entrada en el menú (§9, n.º 8) | permisos P/G/R/profesor; tema fuera de la lista → 400; auditoría |
| W4 | Armazón frontend: `canMatch`, cáscara, `SeoService`, `index-plantilla.html` + script, `WebPublicaService`, SEO por escuela | careful | no | — | sin la meta no casa nada nuevo | Karma: sin meta, `''` → `EscaparatePrincipalComponent` y cabecera de hoy; con meta, ruta de plantilla, sin GA4 ni aviso; `SeoService` no toca nada en plantilla; medición del bundle |
| W5 | Primer tema completo: portada, sede, tarifas, eventos, evento, legales | medium | no | `boceto-<tema>{,-sede,-tarifas,-eventos,-evento,-legales}` | perezoso, solo en hosts de plantilla | Karma por página con datos de prueba (1 y 6 sedes, sin eventos, sin profesores); revisión en navegador contra el boceto (móvil y escritorio) |
| W6 | Preinscripción de la plantilla: `sedeId` en los públicos (393) + formulario común + piel del primer tema | careful | no | `boceto-<tema>-preinscripcion` | campos nuevos en el JSON; flujo de Moiskimdo igual | dorados de `/public/grupos|turnos`; integración: turnos de dos sedes → 400 (D6-7); lista de espera y posición; Karma de validación; prueba en la app real con copia de la base |
| W7 | Panel «Web · Tema» con vista previa en iframe | medium | no | `boceto-panel-tema-web.html` | entrada oculta para Moiskimdo | Karma: estados publicado/cambios/primera/cargando/error; diálogo; foco; revisión en navegador |
| W8-W10 | Los otros tres temas (uno por PR, con su preinscripción) | medium | no | sus 7 bocetos | ídem W5 | ídem W5 |
| W11 | nginx de plantilla (`index-plantilla.html`, `/robots.txt` y `/sitemap.xml` a la API) | careful (despliegue) | no | — | `nginx -T` del bloque moiskimdo.es igual | local con `docker-compose.pruebas.yml` y host de pruebas; `curl` de robots y sitemap |
| W12 | Editor visual de bloques y campos nuevos de sede y evento en el panel. Depende de W4-W7: la vista previa reutiliza los componentes de la plantilla | medium | no | `boceto-panel-contenido-web-visual.html` (aprobado el 07/11) | solo pantallas nuevas | — |
| W13 | (opcional) Meta en servidor para vistas previas de redes | medium | no | — | solo hosts de plantilla | — |

Dependencias: W1-W3 no esperan al dominio (404 para la 1). W4 en adelante se prueban en local con la configuración
`plantilla`. Poner la web en producción para Sierra Sur exige las piezas 1, 3 y 6 del plan de dominio + W11.
Contenido para el alta mientras no exista W12: ver §9, n.º 4.
W12 depende de W4-W7 (la vista previa del editor reutiliza los componentes de la plantilla).

## 9. Lo que los bocetos enseñan y no está decidido

| # | Asunto | Necesita decisión · recomendado |
|---|---|---|
| 1 | Presupuesto del bundle (61 bytes de margen, cifra antigua) | **Decidido el 07/11: login perezoso** (reserva de la 260). Medido ese día: `develop` tenía 1.312.275 bytes de inicial (unos 188 KB de margen; los 61 bytes eran una cifra antigua); con el login perezoso, 1.201.594 (PR #237). W4 ya no espera. |
| 2 | ¿Se guarda el tema elegido sin publicar? | No: vive en la pantalla; «Descartar» vuelve al publicado. Pro: sin estado de borrador en la base. Contra: se pierde al salir. |
| 3 | ¿Quién publica y edita la web? | Solo el propietario (lo dice el boceto). Pro: coherente con 467. Contra: el gestor tiene que pedírselo. |
| 4 | Edición de bloques (textos, imágenes, profesores, galería) | **Decidido el 07/11: editor visual** del boceto `fase4-web/boceto-panel-contenido-web-visual.html` (sustituye al de formulario por bloques `boceto-panel-contenido-web.html`). Se edita sobre la vista previa: textos e imágenes en el sitio; subir, bajar, ocultar, añadir y quitar bloques de los tipos del tema (no maquetación libre); portada y partes fijas en su sitio. Candado solo en el dato que es de otra pantalla (sedes, grupos, importes, eventos, contacto y nombre legal) y en lo que cambia el equipo de Taemoi (logo y color, nombres de escuela y de disciplina). Todo el texto de la web es editable. Lo guardado se publica al momento (n.º 2). Hasta W12, el contenido del alta de Sierra Sur va por migración de datos revisada en la PR. Claves nuevas de `web_bloque` y campo `oculto`: ver debajo de la tabla. |
| 5 | Profesores en la web (nombre, grado, foto, sedes) | Bloque editable, no sacado de `asignacion_profesor`: nada personal sale solo. Pro: consentimiento explícito. Contra: duplica nombres. |
| 6 | Qué tarifas salen en la web | Todas las activas, más licencia y examen («desde» el menor precio de examen). Pro: sin columna nueva. Contra: si una tarifa es interna, sale. Alternativa: columna `visible_web`. |
| 7 | Firma «con mi nombre» (casilla) | Aceptar: el PDF dice «Firmado por nombre y casilla el <fecha>» en lugar de la imagen; `firmaBase64` opcional solo con `firmaPorNombre=true`. Pro: lo pide el boceto aprobado. Contra: validez a revisar por el propietario (dato legal). |
| 8 | «Web · Tema» para Moiskimdo | Oculta (tiene web propia, nivel b). Pro: no puede despublicar su web por error. Contra: ninguna mientras no se separe. |
| 9 | Vistas previas en WhatsApp y redes | Primero cabeza neutra con título y descripción de la escuela puestos por nginx (`sub_filter` en su bloque); W13 (meta por página en servidor) si hace falta. Pro: sin servidor de HTML al principio. Contra: el enlace de un evento se comparte sin su foto. |
| 10 | 404 y web aún no publicada | **Aprobado el 07/11** el boceto `fase4-web/boceto-login-404-sin-publicar.html`: 404 con la cabecera y el pie del tema; sin publicar, el host enseña solo el login. El login es común a todas las escuelas; solo cambia la marca. |
| 11 | Orden de los bloques («orden» de la decisión 12) | **Sustituida el 07/11** por el editor visual (n.º 4): el orden deja de ser fijo por tema; lo cambia la escuela con Subir y Bajar y se guarda en la columna `orden`. La portada y las partes fijas (cabecera, pie) siguen en su sitio; los bloques vacíos no se pintan. |
| 12 | ¿Hacen falta los cuatro temas para salir? | No: sale con el que elija Sierra Sur; la pantalla «Web · Tema» enseña solo los temas construidos hasta tener los cuatro. Pro: alta antes. Contra: menos opciones al principio. |
| 13 | Orden de los eventos (decisión 278, pendiente) | Próximos del más cercano al más lejano; anteriores del más reciente al más antiguo (lo que pintan los bocetos). |
| 14 | Fecha «Última actualización» de Legales | La de la última publicación (`web_escuela.publicada_en`). Pro: sin columna. Contra: no cambia si solo cambian los datos del titular. |
| 15 | Fuentes de cada tema | Google Fonts por `<link>` al cargar el tema. Pro: ya permitido por la CSP. Contra: petición a Google sin consentimiento; alternativa, servirlas desde `assets`. |

Claves nuevas de `web_bloque` que pide el editor visual (las fija W12; hoy llevan el texto del tema por defecto):
`menu.<horarios|sedes|tarifas|eventos|contacto>`, `cabecera.boton`, `portada.boton_horarios`,
`portada.boton_preinscripcion`; `horarios.titulo`, `horarios.apoyo`, `horarios.ver_sede`, `horarios.como_llegar`,
`horarios.lleno`, `horarios.espera`, `horarios.boton`, `horarios.boton_espera`; `sedes.proxima_rotulo`, `sedes.ver_sede`;
`disciplinas.titulo`, `disciplinas.apoyo`, `disciplina.<codigo>.descripcion`, `disciplina.<codigo>.fila<n>.rotulo`,
`disciplina.<codigo>.fila<n>.texto`, `disciplina.<codigo>.foto`; `eventos.titulo`, `eventos.apoyo`, `eventos.enlace_todos`,
`eventos.enlace_ver`; `tarifas.titulo`, `tarifas.punto<n>`, `tarifas.boton_preinscripcion`, `tarifas.boton_ver`;
`contacto.titulo`, `contacto.apoyo`, `contacto.correo_rotulo`, `contacto.telefono_rotulo`, `contacto.sedes_titulo`;
`pie.aviso_legal`, `pie.privacidad`, `pie.cookies`, `pie.creditos`.
Además: un campo `oculto` por bloque (lo fija W12), y claves para el título y la frase de cada sección y para los
bloques «Texto con foto» (repetibles).

Pendiente del propietario: rediseño del login al acabar la fase 4 (el login común de n.º 10 queda como está hasta entonces).

## 10. Decisiones tomadas en W1

Tomadas por el implementador de W1 (la opción más conservadora donde el plan o la d482 no lo fijan); revisables.

| # | Asunto | Decisión |
|---|---|---|
| 1 | Versión de la migración | `V32_5__web_escuela.sql`, no V34: hay V32_3 (rama `feat/f4-394-datos-escuela`) y V32_4 (`feat/f4-dominio-p1`) abiertas. W1 debe fusionarse **después** de ellas (Flyway sin `outOfOrder`). |
| 2 | Vista previa | `vistaPrevia=true` solo con sesión de propietario o gestor (ROLE_ADMIN o ROLE_MANAGER) de la escuela: ve los datos aunque no haya fila o no esté publicada (§5, «salvo vista previa»; la necesita la primera publicación de W7). Con otra sesión o anónimo, el parámetro se ignora. |
| 3 | Moiskimdo y escuelas inactivas | 404 siempre, también en vista previa (marca `comportamiento_legado_taemoi` de la 1, además de no tener fila). |
| 4 | Caché | Anónimo: `ETag` + `Cache-Control: public, max-age=60`. Con sesión o con `vistaPrevia` en la URL: `no-store`, porque la escuela sale de la sesión y no de la URL. Siempre `Vary: Cookie, Authorization` (revisión de W1). |
| 5 | Plazas | Con alguna libre, «Quedan N» aunque haya lista de espera. El grupo toma el peor turno: lista de espera, luego lleno, luego el que menos libres tiene. La espera se cuenta por `preinscripcion_turno` (las solicitudes antiguas por `turno_id`/`grupo_id` solo existen en Moiskimdo). |
| 6 | Grupos que salen | De sedes activas, de disciplinas activas, visibles en la preinscripción (misma regla: sin los de competición de taekwondo) y con al menos un turno. Orden de la preinscripción. |
| 7 | Sede | Nombre, dirección, localidad, código postal y teléfono. Ni correo, ni aforo, ni código de club. |
| 8 | Tarifas por sede | Por tarifa activa y sede activa: la vigente hoy y las futuras. |
| 9 | Licencia y examen | Licencia: la `productoLicencia` de cada disciplina activa (no todos los productos `LICENCIA`). Examen: el menor precio mayor que 0 de los productos `EXAMEN` generales o de una disciplina activa. Las tarifas de una disciplina inactiva tampoco salen (revisión de W1). |
| 10 | Eventos en `/api/publico/web` | Los 3 próximos visibles con fecha de hoy o posterior (zona de la escuela), del más cercano al más lejano; sin fecha, fuera. La lista completa (`/api/publico/web/eventos`, anteriores del más reciente al más antiguo) no es de W1. |
| 11 | Bloques | Lista genérica (`clave`, `texto`, `imagenUrl`, `orden`). Los profesores van en claves `profesor.<n>.*`; su forma exacta se fija en W5/W12. La URL de la imagen solo sale si la imagen es de la misma escuela. |
| 12 | Logo | Solo `logoVersion` (id de la imagen). La URL pública es `/api/publico/escuela/logo` de la pieza 4 del plan de dominio; W1 no la crea. |
| 13 | Legales | Titular con CIF y domicilio (solo aquí), normas = plantilla de preinscripción activa de cada disciplina activa (ya pública), `actualizadaEn` = fecha de `publicada_en` (`null` en una vista previa sin publicar). |
| 14 | `publicada_por` | Sin clave ajena a `usuario`, como en el §4.1: con ella habría que clasificarla en la cancelación de invitaciones (`CancelarInvitacionIntegrationTest`), fuera de W1. |

Pendientes que deja W1: las imágenes de los bloques se sirven por `/imagenes/**`, que hoy solo es público para la carpeta
de eventos (`RutasArchivos.esImagenPublica`): W12 (o el alta por migración de datos) debe guardarlas en una ruta pública
o servirlas por un endpoint. Los 404 de estas rutas los apunta la auditoría como cualquier 404 (comportamiento de
siempre del filtro).
