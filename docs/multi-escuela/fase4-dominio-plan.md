# Fase 4 · Escuela por dominio: plan

Estado: propuesta (07/11/2026), sin código. Base: `develop` en 473605a1, Flyway 32.2 en el repo.
Cubre tres piezas pendientes de la fase 4 y dos huecos de la decisión 394:

- «Plantilla … escuela por dominio o subdominio».
- «Registro, Google y preinscripción pública en la escuela del dominio» (decisión 10, pendiente).
- «Nginx, certificados y Google para más de un dominio».
- 394: enlaces de correo por escuela y logo del login por escuela.

Regla de oro: **Moiskimdo (organización 1, moiskimdo.es) no nota nada.** Cada pieza lleva un test que lo
demuestra.

## 1. Lo ya decidido (buscado en `docs/multi-escuela/`)

| Fuente | Qué dice |
|---|---|
| `modelo-datos.md:36-46` (decisión 12) | Web pública por plantilla, «escuela resuelta por dominio o subdominio». La gestión y el inicio de sesión son siempre de la aplicación común. La web a medida (nivel b) consume una API pública con CORS por dominio. |
| `roadmap.md:324` (decisión 10, pendiente) | Las peticiones anónimas (registro, vuelta de Google) van siempre a Taemoi; ligado a la resolución por dominio. |
| `fase3-e7-plan.md:493`, `fase3-e8-plan.md:630`, `fase3-e8-miembros.md:413` | `app.frontend.base-url` única: «pendiente conocido del dominio único». |
| `fase3-e8c-inventario.md:46`, `catalogo-frontend.md:66` | Sin resolución por dominio; la marca pública dependerá del dominio. |

No hay nada decidido sobre nombres de dominio, DNS ni certificados. Los bocetos de `fase4-web/` no mencionan dominios.

## 2. Estado actual

### 2.1 Resolución de la escuela

| Hecho | Evidencia |
|---|---|
| Punto único: `OrganizacionRequestFilter.resolverOrganizacion`. JWT válido → su escuela; sin JWT → `app.organizacion.por-defecto` (1). | `src-api/.../organizacion/OrganizacionRequestFilter.java:68-78` |
| Se registra antes de Spring Security (orden −110); `ForwardedHeaderFilter` (estrategia `framework`) corre antes. | `OrganizacionWebConfig.java` (`ORDEN`), `application-production.properties:83` |
| Por defecto = 1, configurable. | `application.properties:110` (`APP_ORGANIZACION_POR_DEFECTO`), `ResolutorOrganizacion.java:51` |
| Alumno y Preinscripción llevan `@TenantId`: registro, alta de cuenta con Google y preinscripción pública buscan y escriben **en la escuela activa**, hoy siempre la 1. | `entities/EntidadOrganizacion.java:23`, `Alumno.java:43`, `Preinscripcion.java:17`, `RegistroServiceImpl.java:92`, `OAuth2UserServiceImpl.java:117-122` |
| Login de un usuario existente: si tiene varias escuelas, la última usada o la de menor id. No mira el dominio. | `MembresiaServiceImpl.java:74-95` |
| Formulario de contacto: buzón de la escuela activa. | `EmailController.java:36` |
| Datos públicos (`/api/eventos`, `/api/catalogo/publico`, `/api/turnos/dto`, `/api/preinscripciones/public/**`): anónimos → escuela 1. | `SecurityConfiguration.java:265-271, 411-443` |

Consecuencia: si la escuela anónima pasa a salir del Host, registro, Google (alta), preinscripción, contacto y datos
públicos cambian de escuela solos, sin tocar sus servicios.

### 2.2 Modelo

- `organizacion` (V18): `slug` único (60), `web_url` (sin uso en el código), `logo_imagen_id`, `color_primario`,
  `email_contacto`, `email_remitente_nombre`. Ninguna columna de dominio. `entities/Organizacion.java:24-87`,
  `db/migration/V18__organizacion.sql:18`.
- Última migración: `V32_2__asistencia_motivos_festivos.sql`. Producción en Flyway 32.1 (continuación del 05/11).

### 2.3 Frontend

- Un solo build, sin SSR. `apiUrl: '/api'` en producción (`src-frontend/src/environments/environment.prod.ts`): cada
  dominio llama a su propio `/api`, así que el Host llega a la API sin cambiar nada.
- Google: `vista-login.component.ts:238-243` navega a `/oauth2/authorization/google` relativo (mismo host).
- Logo del login: SVG fijo en `vista-login.component.html:12`. La marca (`/api/escuela/marca`, `/logo`) exige sesión de
  gestión, profesor o responsable (`SecurityConfiguration.java:257-258`, `EscuelaMarcaController.java:34-59`).
- Web pública de Moiskimdo fija en el build: `index.html` (título, `canonical`, `og:url`, JSON-LD con moiskimdo.es,
  líneas 19-66), cabecera y pie (`header.component.html:11,39`, `footer.component.html:10`), legales
  (`aviso-legal.component.html:4,17`), `evento-detalle.component.ts:81` y `no-encontrado.ts:23` (`canonical` fijo),
  GA4 con el id de Moiskimdo (`skeleton.configurar-cookies.spec.ts:22` lo nombra). Esto es de la pieza de la
  plantilla, no de este plan, pero la condiciona (§5, pieza 7).

### 2.4 Despliegue

| Hecho | Evidencia |
|---|---|
| nginx dentro del contenedor frontend, puertos 80/443. API sin puerto publicado (solo red interna). | `docker-compose.production.yml` (servicios `frontend`, `backend`) |
| Bloques: `:80` `server_name _` → redirige a https; `:443` `www.moiskimdo.es` → 301 a `moiskimdo.es`; `:443` `moiskimdo.es` con todas las `location`. Sin `default_server` en 443: un Host desconocido cae en el primero (www → moiskimdo.es). | `nginx/nginx-production.conf:89-136` |
| Certificado único `live/moiskimdo.es`, emitido por HTTP-01 (webroot `/var/www/certbot`); certbot renueva cada 12 h; `nginx-cert-reloader` recarga. | `nginx-production.conf:101,118,135`, `nginx/nginx-certbot.conf`, compose `certbot` |
| `X-Forwarded-Host $host` solo en 7 de 10 `proxy_pass`; falta en `/api/auth/signin` (`nginx-production.conf:367-384`) y otras dos. Ahí pasa el que mande el cliente y Spring (`framework`) lo cree. | `grep -c` sobre el fichero |
| Sin `proxy_cache` (solo un comentario en :458). | `nginx-production.conf` |
| CORS: un único origen `cors.allowed.origin` en `/api/**`, `/imagenes/**`, `/documentos/**`. | `WebConfig.java:26-58`, `application-production.properties:47` |
| Cookie JWT y `rememberMe` con `Domain` sacado de `app.base.url` (moiskimdo.es) y `SameSite=Lax`. En otro dominio el navegador **rechaza** esa cookie: el login no funcionaría. | `AuthenticationController.java:172-183, 505-545, 516`; `OAuth2AuthenticationSuccessHandler.java:98-120, 171-193` |
| Google: `redirect-uri` fija por variable `GOOGLE_REDIRECT_URI`; tras el login redirige a `cors.allowed.origin`. Empezar en otro dominio y volver a moiskimdo.es pierde la petición de autorización (otra cookie de sesión). | `application.properties:16`, `OAuth2AuthenticationSuccessHandler.java:47-48,122-134` |
| CSP: `connect-src 'self'` (API en el mismo origen). | `src-frontend/nginx-cabeceras-seguridad.conf:36-40` |

### 2.5 URL absolutas hacia el frontend o la API

| Sitio | Uso | Base |
|---|---|---|
| `PasswordResetServiceImpl.java:40,80` | enlace de restablecer | `app.frontend.base-url` |
| `RegistroServiceImpl.java:53,116` | confirmar registro | ídem |
| `RecordatorioRachaServiceImpl.java:47,241-243` | enlace del recordatorio; **`https://moiskimdo.es` fijo** de reserva | ídem |
| `InvitacionCorreoListener.java:35,48` | invitación a la escuela | ídem |
| `PreinscripcionService.java:96,292` | aviso al buzón (`/preinscripciones`) | ídem |
| `PreinscripcionFinalizacionEmailService.java:38,93` | finalizar preinscripción | ídem |
| `OAuth2AuthenticationSuccessHandler.java:122,127,132` | destino tras Google (redirección) | `cors.allowed.origin` |
| `UrlArchivoSerializer.java:23,35-46` (7 usos) | URL de imágenes y documentos en JSON | `app.base.url` |
| `AlumnoController.java:1983,1992`, `EventoController.java:412`, `EventoFotoController.java:182` | foto del alumno, imagen y fotos del evento | `app.base.url` |
| `RespuestaFotoAlumno.java:94-97` | orígenes permitidos al redirigir una foto | `app.base.url` + `app.frontend.base-url` |
| `EmailController.java:59` | logo de Moiskimdo en un servidor externo, fijo en el correo de contacto; sin remitente de escuela (:70) | fijo |

No hay QR. Los PDF no llevan URL a la aplicación (no se encontró ninguna).

Problema que no estaba en la lista de la 394: con `app.base.url` fijo, el panel de otra escuela pediría sus fotos
y documentos a `https://moiskimdo.es/...`, sin su cookie (otro sitio) → 401/403 o la escuela 1.

## 3. Diseño recomendado

### 3.1 Resolución por Host

1. Tabla nueva `organizacion_dominio` (global, sin `@TenantId`): `host` único, `organizacion_id`, `principal`.
   Una escuela puede tener subdominio de plataforma y dominio propio; el `principal` da los enlaces de correo.
2. Semilla: `moiskimdo.es` → 1, principal. `www.moiskimdo.es` no hace falta (nginx lo redirige antes).
3. `ResolutorEscuelaPorHost`: `request.getServerName()` (ya corregido por `ForwardedHeaderFilter`), en minúsculas, sin
   puerto ni punto final; búsqueda exacta en caché (recarga corta o al cambiar la tabla). Host desconocido →
   `app.organizacion.por-defecto` (local, tests, IP, `/healthz`: igual que hoy).
4. `OrganizacionRequestFilter.resolverOrganizacion`: sin sesión → escuela del Host (antes: la 1). Con sesión → la de la
   sesión, como hoy. Guarda la escuela del Host en un atributo de la petición para el login y las URL.
5. Login (contraseña y Google) en un Host que **no** es el de la escuela por defecto: si el usuario puede entrar en la
   escuela del Host, se elige esa; si no, la regla de hoy. En moiskimdo.es la regla no cambia (multimembresía igual).

Alternativas descartadas:

| Alternativa | Pros | Contras |
|---|---|---|
| Columna `organizacion.dominio` | Una migración mínima | Un solo host por escuela: subdominio + dominio propio no caben |
| Reutilizar `organizacion.web_url` | Ya existe | Es la web del club (nivel b, a medida), no el host de la aplicación; mezcla dos conceptos |
| Resolver por `slug` en el subdominio con regex | Sin tabla para subdominios | Los dominios propios necesitan tabla igual; dos caminos |
| Escuela por parámetro (`?escuela=slug`) o ruta (`/e/slug`) | Sin DNS ni certificados | Enlaces y web pública feos, SEO malo, contradice la decisión 12 |
| Dominio común `app.<plataforma>` para todos los logins | Un solo certificado y un solo URI de Google | El login no sabe la escuela sin parámetro; logo por escuela pierde sentido; cambio para Moiskimdo |

### 3.2 Seguridad

- **Host falso**: nginx solo atiende los `server_name` declarados; un `default_server` en 443 y 80 devuelve 444 a
  cualquier otro Host. La API no tiene puerto publicado. Añadir `X-Forwarded-Host $host` en **todas** las
  `location` con `proxy_pass` (hoy falta en 3, p. ej. `/api/auth/signin`), mejor con un `include` común. La API solo
  acepta hosts de la tabla (lista blanca); nunca construye URL con un Host que no esté en ella.
- **Cookies**: en hosts que no sean moiskimdo.es, cookie JWT **solo del host** (sin `Domain`). En moiskimdo.es, igual
  que hoy. El dominio de plataforma **no** debe ser un subdominio de moiskimdo.es: la cookie `Domain=moiskimdo.es`
  viajaría a `escuela.moiskimdo.es` y la sesión de Moiskimdo mandaría sobre el Host (el filtro prioriza el JWT).
  Subdominios hermanos de la plataforma son el mismo «sitio» para `SameSite=Lax`: no servir nunca HTML de terceros en
  ellos.
- **Sesión y Host distintos** (solo posible si alguien copia una cookie): manda la sesión, como hoy; se registra aviso.
- **Google**: `redirect-uri` con plantilla `{baseUrl}/login/oauth2/code/{registrationId}` (Spring la rellena con el
  Host corregido). Para moiskimdo.es da la misma URI que hoy. Cada host nuevo se da de alta en Google Cloud (URI de
  redirección y origen JS; Google no admite comodines). Redirección tras Google al origen del **propio Host**, solo si
  está en la tabla; si no, al de la escuela por defecto (evita la redirección abierta).
- **CORS**: la aplicación es del mismo origen en cada dominio y no necesita CORS. No ampliar `cors.allowed.origin`
  ahora; la lista por escuela llega con la web a medida (decisión 443). Probar que un `POST` del mismo origen en un
  host nuevo no lo bloquea CORS (Spring compara con el Host corregido).
- **URL de correo**: siempre del dominio principal de la escuela del destinatario, nunca del Host de la petición
  directamente (un Host aceptado por nginx ya está en la tabla, pero así se cubre también el trabajo programado).

## 4. Cambios

### 4.1 Datos (ampliar, sin contraer)

`V33__organizacion_dominio.sql` (o la siguiente libre):

```sql
CREATE TABLE organizacion_dominio (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  organizacion_id BIGINT NOT NULL,
  host VARCHAR(253) NOT NULL,
  principal BOOLEAN NOT NULL DEFAULT FALSE,
  creado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_organizacion_dominio_host UNIQUE (host),
  CONSTRAINT fk_organizacion_dominio_org FOREIGN KEY (organizacion_id) REFERENCES organizacion (id)
);
INSERT INTO organizacion_dominio (organizacion_id, host, principal) VALUES (1, 'moiskimdo.es', TRUE);
```

Un principal por escuela: se valida en la aplicación (MySQL 8 no tiene índice único parcial). Postcheck: 1 fila,
escuela 1, `moiskimdo.es`, principal. Con migración → `desplegar_migrar` + postcheck.

### 4.2 API

- `ResolutorEscuelaPorHost` + cambio en `OrganizacionRequestFilter` (§3.1).
- `UrlsEscuela`: `baseFrontend(organizacionId)` = `https://` + host principal; sin dominio → `app.frontend.base-url`.
  `baseArchivosPeticion()` = origen del Host si está en la tabla; si no, `app.base.url`.
- Sustituir la base en los 6 correos y quitar el fijo de `RecordatorioRachaServiceImpl.java:242`. Restablecer
  contraseña: la escuela del Host si el usuario es miembro; si no, la última usada; si no, la por defecto.
- `UrlArchivoSerializer`, `AlumnoController:1983,1992`, `EventoController:412`, `EventoFotoController:182` y los
  orígenes de `RespuestaFotoAlumno:97` con `baseArchivosPeticion()`.
- Cookies host-only fuera de moiskimdo.es (`AuthenticationController.resolverDominioCookie`, :524; ídem en
  `OAuth2AuthenticationSuccessHandler`, :171).
- `OAuth2AuthenticationSuccessHandler`: destino y errores al origen del Host (lista blanca).
- `EmailController:59,70`: logo y remitente de la escuela (`MarcaEscuela`) en el correo de contacto.
- Público: `GET /api/publico/escuela` (nombre, origen del logo `SUBIDO|LEGADO|NINGUNO`, versión, tema) y
  `GET /api/publico/escuela/logo` (PNG, `ETag`, `Cache-Control: public, max-age=300`), ambos de la escuela del Host,
  `permitAll`, reutilizando `MarcaPanelService`. Nunca datos privados (ni CIF, ni buzón, ni firmante).

### 4.3 Frontend

- Login: lee `/api/publico/escuela`. `LEGADO` → el SVG de siempre (Moiskimdo idéntico). `SUBIDO` → el PNG público.
  `NINGUNO` → **necesita boceto** (¿nombre en texto?).
- Nada de base URL en el frontend: ya es relativo (`/api`, `/oauth2/...`).

### 4.4 Despliegue

- nginx: sacar las `location` comunes a un `include` (`app-locations.conf`); `moiskimdo.es` lo incluye (configuración
  efectiva idéntica, se comprueba con `nginx -T` antes y después). Bloque nuevo por escuela (o uno con varios
  `server_name`) con su certificado. `default_server` 444 en 80 y 443. `X-Forwarded-Host` en todas las `location`.
- Certificados: uno por host con HTTP-01 por el webroot que ya existe (`certbot certonly --webroot -w /var/www/certbot
  -d <host>`); el bucle de renovación y `nginx-cert-reloader` ya lo cubren. Comodín (`*.<plataforma>`) solo con DNS-01 y
  credenciales de la API del DNS en el servidor: más adelante, si hay muchas escuelas.
- Primero el DNS (registro A al servidor) y el certificado, después el bloque de nginx: si no, nginx no arranca sin el
  `.pem`.
- Google Cloud: URI de redirección `https://<host>/login/oauth2/code/google` y origen JS `https://<host>` por host;
  dominios autorizados de la pantalla de consentimiento (puede pedir verificar el dominio en Search Console).
- `.env` del servidor: `GOOGLE_REDIRECT_URI={baseUrl}/login/oauth2/code/{registrationId}` (o quitarla y poner ese valor
  por defecto en `application-production.properties`). El propietario la cambia; el fichero de plantilla está protegido.

## 5. Piezas en orden (cada una se fusiona sola)

| # | Pieza | Nivel | Migración | Moiskimdo idéntico porque… | Tests |
|---|---|---|---|---|---|
| 1 | Tabla `organizacion_dominio`, resolutor por Host, filtro | careful | V33 → `desplegar_migrar` | su única fila es moiskimdo.es → 1, lo mismo que la por defecto | Unitarios del resolutor (mayúsculas, puerto, desconocido → 1); filtro: anónimo con host X → X, con moiskimdo.es → 1, sesión gana al Host; integración: preinscripción y registro con `Host: x.test` caen en X y con `moiskimdo.es` en 1; `-Dgroups=migracion`; batería completa (tests dorados de JSON) |
| 2 | Enlaces de correo por escuela (`UrlsEscuela.baseFrontend`) | medium | no | base de la escuela 1 = `https://moiskimdo.es` = `app.frontend.base-url` | Por cada correo: escuela 1 da el texto exacto de hoy (comparación literal) y escuela 2 su host; sin dominio → la base global |
| 3 | Sesión, Google y archivos por Host: cookies host-only, destino de Google, `redirect-uri` con plantilla, URL de imágenes, preferencia de escuela del Host en el login | careful | no | en moiskimdo.es `Domain`, destino, URI y URL de archivos salen iguales; la regla de login no cambia en el host por defecto | `Set-Cookie` literal de hoy con Host moiskimdo.es; host X sin `Domain`; redirección abierta con Host fuera de la tabla → origen por defecto; JSON de alumno y evento idéntico para moiskimdo.es; multimembresía en host X elige X |
| 4 | Marca pública por Host (`/api/publico/escuela`, `/logo`) + correo de contacto con la marca | light/medium | no | Moiskimdo devuelve `LEGADO` (no hay PNG); el correo de contacto de la 1 sale igual | Anónimo con host X recibe su nombre y logo; nunca campos privados; 404 sin logo; `ETag`/304 |
| 5 | Logo del login por escuela | light (necesita boceto para `NINGUNO`) | no | `LEGADO` pinta el mismo SVG | Karma: `LEGADO` → SVG; `SUBIDO` → `/api/publico/escuela/logo`; presupuesto del bundle |
| 6 | nginx `include` + `default_server` 444 + `X-Forwarded-Host` + bloque del primer host + certificado + Google Console | careful (despliegue) | no | `nginx -T` del bloque moiskimdo.es igual salvo el `include` | En local con `docker-compose.pruebas.yml` y hosts en `/etc/hosts`; curl con Host desconocido → 444; prueba manual de login con contraseña y Google en el host nuevo con la copia de la base |
| 7 | Alta del dominio de una escuela | light | migración de datos por escuela | no toca la fila 1 | Postcheck de la fila. Pantalla en «Nueva escuela»: **necesita boceto** |
| — | Dependencia: web por plantilla | — | — | — | `index.html`, cabecera, pie, legales, `canonical` y GA4 son de Moiskimdo (§2.3): para otros hosts hace falta otro `index` o servirlos por escuela. Va en la pieza de la plantilla |

Piezas 1-4 son solo de API y se pueden desplegar antes de tener dominio: en producción no cambian nada hasta que
exista una segunda fila en `organizacion_dominio`. Conviene probar cada una en la app real (local con copia de la base
y un host de pruebas en `/etc/hosts`) antes del merge.

## 6. Decisiones del propietario (una pregunta cada vez)

1. **¿Qué dominio de plataforma?** Recomendado: uno propio y distinto de moiskimdo.es (p. ej. `taemoi.es`).
   Pro: cookies y marca separadas de Moiskimdo. Contra: comprar y mantener un dominio más.
2. **¿Quién lo compra y dónde está su DNS?** Recomendado: lo compra el propietario y su DNS va a un proveedor con API
   (p. ej. Cloudflare, gratis). Pro: comodín por DNS-01 posible más adelante. Contra: otro proveedor; moiskimdo.es sigue
   en GoDaddy (`aviso-legal.component.html:17`) sin tocarse.
3. **¿Sierra Sur arranca con subdominio de plataforma o con dominio propio?** Recomendado: subdominio
   (`sierrasur.<plataforma>`) para el alta; su dominio propio después, si lo tiene, como segundo host. Pro: no depende
   del club. Contra: cambiar de URL más tarde (se redirige con 301).
4. **¿Login y panel de Sierra Sur en su propio host o en uno común (`app.<plataforma>`)?** Recomendado: en su host, el
   mismo build (la «aplicación común» de la decisión 12 es la misma aplicación, no el mismo dominio). Pro: logo y
   Google por escuela sin parámetros. Contra: un URI de Google y un certificado por host.
5. **¿Certificado por host (HTTP-01) o comodín (DNS-01)?** Recomendado: por host mientras haya pocas escuelas.
   Pro: sin credenciales del DNS en el servidor. Contra: un paso de certbot en cada alta.
6. **¿El dominio se da de alta por migración de datos hasta que haya pantalla?** Recomendado: sí. Pro: revisado en la
   PR, sin SQL suelto. Contra: un despliegue con migración por escuela.
7. **Login de una escuela sin logo subido**: necesita boceto (¿nombre en texto, logo de la plataforma?).
