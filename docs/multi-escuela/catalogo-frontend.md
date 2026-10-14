# Fase C: catálogo en el frontend

Objetivo: el frontend deja de usar listas fijas de disciplinas, grados, tarifas y categorías y lee el
catálogo de la organización activa. Taemoi (organización 1, `comportamiento_legado_taemoi = TRUE`)
ve exactamente la misma interfaz y los mismos valores. Cada diferencia que se mantiene para Taemoi
queda en un único módulo (`legado-taemoi.ts`) y es una decisión pendiente del propietario (§5).

Rutas abreviadas: FE = `src-frontend/src/app`, API = `src-api/src/main/java/com/taemoi/project`.

## 1. Contrato de los endpoints

Dos lecturas nuevas en un `CatalogoController`. `/api/grados` y `/api/grados/disponibles/{fecha}` no
cambian (`GradoJsonTest` sigue fijando `{id, tipoGrado}`); el catálogo usa DTO propios (records), nunca
serializa entidades.

| Endpoint | Acceso | Uso |
|---|---|---|
| `GET /api/catalogo` | Autenticado (`ROLE_USER`, `ROLE_MANAGER`, `ROLE_ADMIN`) | Toda la aplicación tras iniciar sesión |
| `GET /api/catalogo/publico` | `permitAll` (añadir a `SecurityConfiguration`) | Preinscripción pública |

Organización: la que resuelve `OrganizacionRequestFilter` (claim `org` del JWT; anónimo → organización
por defecto). Los repositorios ya filtran por `@TenantId`; el controlador no recibe `organizacionId`.

`GET /api/catalogo` (todas las disciplinas, también inactivas, para poder etiquetar inscripciones
antiguas; el frontend filtra `activa` en los desplegables):

```json
{
  "organizacion": { "id": 1, "comportamientoLegadoTaemoi": true },
  "disciplinas": [{
    "codigo": "KICKBOXING", "nombre": "Kickboxing", "orden": 2, "activa": true,
    "edadAdulto": 15, "permiteCompeticion": true, "requiereLicencia": true,
    "sistemaGradosId": 2, "tieneMaterialExamen": true
  }],
  "sistemasGrados": [{
    "id": 2, "nombre": "Kickboxing",
    "grados": [{ "codigo": "ROJO", "nombre": "MARRÓN", "orden": 11, "aplicaA": "TODOS",
                 "color1": "#8B4513", "color2": null, "requiereExamen": true }]
  }],
  "categorias": [{ "codigo": "...", "nombre": "Infantil", "disciplina": "TAEKWONDO",
                   "edadMin": null, "edadMax": 9, "orden": 1 }],
  "tarifas": [{ "codigo": "PADRES_HIJOS", "nombre": "Padres e Hijos", "disciplina": "TAEKWONDO",
                "importe": 0.00, "importeAdulto": 28.00, "importeMenor": 26.00, "orden": 7, "activa": true,
                "requiereRolFamiliar": true, "requiereGrupoFamiliar": false }]
}
```

- Orden: disciplinas por `orden, id`; sistemas por `id`; grados por `orden, id` (sin los de `orden` nulo,
  como `ProgresionGradosService`); categorías por `orden, id`; tarifas por `orden, id` (`tarifa.orden`, V30).
- Disciplinas y tarifas: todas, también las inactivas (`activa`). `disciplina` de una tarifa o categoría:
  código de la única disciplina que admite, o `null` (cualquiera / general).
- Grados: `color1` = mitad inferior (primer color del código), `color2` = mitad superior, `null` = un color
  (V30); las franjas de PUM/DAN se derivan del código.
- `tieneMaterialExamen` = la disciplina tiene sistema de grados y algún grado con `bloque_temario`.
- `requiereRolFamiliar` / `requiereGrupoFamiliar`: de `TarifasService` (no se replica la regla en el FE).
- `tarifas` solo para `ROLE_MANAGER`/`ROLE_ADMIN`; `ROLE_USER` recibe `[]` (decisión 14).
- No se exponen `meses_minimos_*`, `bloque_temario`, productos ni `equivalente_adulto_id`.

`GET /api/catalogo/publico`: `{ "disciplinas": [{ "codigo", "nombre", "orden" }] }`, solo activas, de la
organización de la petición (anónimo → la organización por defecto; con sesión, la de la sesión).

Cabeceras: `Cache-Control: private, no-cache` y `Vary: Cookie` en los dos (la respuesta depende de la
sesión, y la pública dependerá del dominio cuando la escuela se resuelva por dominio), con ETag
(`ShallowEtagHeaderFilter` limitado a `/api/catalogo` y `/api/catalogo/*`, `CatalogoWebConfig`) para
respuestas 304. La caché real es la memoria del frontend.

Implementado: `CatalogoController`, `CatalogoService(Impl)`, DTO `CatalogoDTO` y `CatalogoPublicoDTO`
(records). `SecurityConfiguration`: `GET /api/catalogo/publico` → `permitAll`; `/api/catalogo` y
`/api/catalogo/**` → `authenticated` (anónimo: 401). Pruebas: `CatalogoControllerIntegrationTest`.

## 2. Diseño del frontend

Carpeta `FE/features/catalogo/` (mismo patrón que `features/grado`, `features/preinscripcion`):

- `catalogo.models.ts`: interfaces del JSON.
- `catalogo.service.ts` (`providedIn: 'root'`): estado en `signal<EstadoCatalogo>`
  (`{ estado: 'vacio' | 'cargando' | 'listo' | 'error', datos?, error? }`) y `cargar(): Observable<Catalogo>`
  con la petición en curso compartida (`shareReplay(1)` + `finalize`, igual que
  `AuthenticationService.obtenerAlumnosAsociados` y `EndpointsService`). Expone lecturas síncronas
  (`disciplina(codigo)`, `disciplinasActivas()`, `etiquetaDisciplina(codigo)`, `gradosSeleccionables(codigo)`,
  `grado(disciplina, codigo)`, `tarifasDe(disciplina)`, `categoriasDe(disciplina)`, `esLegadoTaemoi()`)
  como `computed`.
- `catalogo-reglas.ts`: funciones puras sin Angular (se prueban sin TestBed): `esMenor(fecha, edadAdulto)`,
  `siguienteGrado(grados, codigo, esMenor)` (réplica de `ProgresionGradosServiceImpl.siguiente`),
  `categoriaPorEdad(categorias, edad)`, `importeTarifa(tarifa, rolFamiliar)` (réplica de
  `TarifasServiceImpl.calcularImporte`), `claseCssDisciplina(codigo)` (minúsculas, `_` → `-`).
- `legado-taemoi.ts`: todas las excepciones visibles de Taemoi (etiquetas, paletas, listas de tarifas
  por pantalla, formateadores de nombre de grado). Solo se aplican si `comportamientoLegadoTaemoi`.
- `catalogo.resolver.ts`: `ResolveFn` en las rutas autenticadas (admin, manager, usuario) que espera a
  `cargar()`. Los componentes leen el catálogo de forma síncrona, como hoy leen las constantes.

Carga e invalidación:

- Al iniciar sesión (`usuarioLogueadoCambio` → `true`) se vacía y se precarga; al cerrar sesión o en
  `limpiarEstadoLocal()` (→ `false`) se vacía. `invalidar()` público para el futuro cambio de escuela
  (`POST /api/auth/organizacion`, que hoy el FE no llama): tras él, `invalidar()` + `cargar()`.
- La preinscripción pública llama a `/api/catalogo/publico` desde el componente (ya carga grupos y
  normas de forma asíncrona) y muestra «Cargando…» en el desplegable de actividad.
- Error: el resolver reintenta una vez; si falla, la ruta se abre con un aviso y desplegables vacíos.
  No hay respaldo a las constantes de Taemoi: mostrar valores de Taemoi a otra escuela es peor que un
  error (decisión 15). Es el único cambio para Taemoi y solo ocurre si falla la petición.

## 3. Sustitución por lista y por duplicado

«Legado» = se mantiene el valor actual vía `legado-taemoi.ts` cuando el flag es `TRUE`.

| Origen actual | Catálogo | Respaldo / legado | ¿Taemoi igual? |
|---|---|---|---|
| `enums/deporte.ts` `DeportesArray` | `disciplinasActivas()` por `orden` | — | Sí (mismo orden y códigos) |
| `getDeporteLabel`, `DeporteLabels` | `disciplina.nombre` | Legado: etiquetas actuales (D2) | Sí |
| `vista-principal-admin.ts:509-536` etiquetas/emojis/colores | `nombre`; emoji y color no existen en el catálogo | Legado: mapa actual; resto: `⭐` / `#6c757d` (ya son los valores por defecto) (D2, D12) | Sí |
| `tesoreria-cobros.ts:52-58,:419` | `TODOS` + disciplinas + `GENERAL` (pseudovalores se quedan) | Legado: etiquetas actuales | Sí |
| `preinscripcion.component.ts:10` | `/api/catalogo/publico` | — (sus etiquetas ya son las de la BD) | Sí |
| Valor por defecto `'TAEKWONDO'` (crear/editar-alumno, `grado.service.ts:31`, `endpoints.service.ts:1028`, `horarios.ts:299`, `listado-convocatorias.ts:91`) | Primera disciplina activa por `orden` | — | Sí (TAEKWONDO tiene orden 1) |
| Clases CSS por disciplina (`editar-alumno.html:138-141,1525-1528`, `listado-convocatorias.html:76`) | `claseCssDisciplina(codigo)` | Estilo neutro para códigos sin clase | Sí |
| `enums/tipo-grado.ts` (13 códigos obsoletos) | Tipo `string`; valores del catálogo | Se borra | Sí (solo se usa como tipo; `editar-alumno.ts:76` filtraba con él hasta recibir `/disponibles`) |
| Subconjunto KB (`crear-alumno.ts:193-205,:383-395`, `editar-alumno.ts:1429-1441`, `preinscripciones-admin.ts:104`) | `gradosSeleccionables(d)` = grados del sistema con `aplicaA ≠ NINGUNO`, por `orden` | — | Sí (KB `TODOS` = los 11 del subconjunto; TKD = los 19) |
| Pilates/DPF sin grado (`deportesSinGrado` `editar-alumno.ts:122`, `crear-alumno.ts:210,:275`, `listado-alumnos.ts:839`, `vista-principal-user.ts:1549`, `.html:691`, `crear-alumno.html:270`) | `sistemaGradosId === null` | — | Sí |
| Convocatorias solo con grados (`editar-alumno.html:579`, `listado-convocatorias.ts:90,185`) | `sistemaGradosId !== null` | Opciones con el código, como hoy | Sí |
| Licencia (`crear-alumno.html:401,444`, `listado-alumnos.ts:620,851`) | `requiereLicencia` | — | Sí |
| Competidor (`editar-alumno.html:894`) | `permiteCompeticion` | — | Sí |
| Material de examen (`materiales-examen-user.ts:712`) | `tieneMaterialExamen` | — | Sí |
| `utilities/categoria-por-edad.ts` `EDAD_ADULTO_*`, `esMenor` | `disciplina.edadAdulto` + `esMenor(fecha, edadAdulto)` | Sin disciplina: 14 (`EDAD_ADULTO_SIN_DISCIPLINA`) | Sí (§4, fila 5) |
| `calcularCategoriaPorEdad` | `categoriasDe(d)` + `categoriaPorEdad` (por `nombre`) | Legado: disciplina con `permiteCompeticion` sin categorías propias usa las de TAEKWONDO (D6) | Sí |
| `utilities/grado-progresion.ts` (3 mapas) | `siguienteGrado(grados, codigo, esMenor(fecha, edadAdulto))` | Disciplina sin sistema: `null` | Sí (comprobado transición a transición) |
| `esSiguienteGradoRojo` (cinturón bordado) | — | Solo con legado (D11) | Sí |
| `materiales-examen-user.ts:68-86` etiquetas | `grado.nombre` | Legado: etiquetas actuales (D3) | Sí |
| `grado-colors.ts` `getGradoNombreParaDeporte` (ROJO → MARRÓN) | `grado.nombre` | Legado: código y «MARRÓN» como hoy (D3) | Sí |
| `grado-colors.ts` `getGradoColors`/`getGradoTextStyle` | `color1` (base, mitad inferior), `color2` (mitad superior; nulo = color único) | Si `color1` es nulo: paleta actual de ese archivo | Sí (en la BD son nulos) |
| `vista-principal-user.ts:2745-2870` cinturón, KB ROJO `#8B4513`, franjas PUM/DAN, etiquetas «MARRON» | Igual que la fila anterior; franjas por patrón de código `_<n>_(PUM\|DAN)` | Si `color1` es nulo: paleta propia de esa vista; etiquetas legado (D3, D4) | Sí |
| `enums/tipo-tarifa.ts` | `tarifasDe(d)` = tarifas activas con `disciplina` nula o igual | — | Ver fila siguiente |
| Listas de tarifas por pantalla: `crear-alumno.ts:225-255,:646-680`, `editar-alumno.ts:1540-1560`, `editar-alumno.html:431-439,:759-767`, `preinscripciones-admin.ts:101` | `tarifasDe(d)`, orden del catálogo, etiqueta según la pantalla (código o nombre, como hoy) | Legado: subconjunto y orden de cada pantalla (D7) | Sí |
| `constants/tarifa.constants.ts` `obtenerCuantiaTarifaEstandar` | `importeTarifa(tarifa, rol)` | Tarifa desconocida: `0` (como hoy) | Sí (importes iguales, §4) |
| `preinscripciones-admin.ts:105` `cuantiaEstandar` | `importeTarifa` | — | Sí |
| `preinscripciones-admin.ts:102` tarifa inicial (`<18` → INFANTIL/ADULTO) | — | Legado: regla actual; resto: sin preselección (D9) | Sí |
| `editar-alumno.ts:2103` `asignarCuantiaTarifa` | — | Código muerto (sin llamadas): se borra (D1) | Sí |
| Regla `<18` de tutor (`preinscripcion.component.ts:29`, `preinscripciones-admin.ts:99`) | No es catálogo: mayoría de edad legal, igual que `PreinscripcionService` (líneas 89, 190) | Constante `EDAD_MAYORIA_LEGAL = 18` compartida | Sí |
| Botón «derecho a examen» solo KB / «recompensa» solo no-KB (`editar-alumno.html:615,622`) | Sin campo en el catálogo | Legado: regla actual; resto: «recompensa» (D10) | Sí |
| `horarios.ts:298` color de turno → disciplina | Sin campo | Mapa actual; respaldo: primera disciplina | Sí |
| Páginas públicas `taekwondo`, `kickboxing`, `pilates`, `defensa-personal-femenina` | Fuera de la fase C: contenido de Taemoi | — | Sí (sin cambios) |

## 4. Divergencias entre el frontend y las semillas (organización 1)

Semillas: V23 (disciplinas, grados), V24 (tarifas), V28 (rangos de categorías). Acción: **L** = legado
para Taemoi en `legado-taemoi.ts`; **U** = se puede unificar sin cambio visible; **P** = decisión del
propietario.

| # | Valor | Frontend | BD | Acción |
|---|---|---|---|---|
| 1 | Nombre DPF | `DeporteLabels` y tesorería: «Defensa Personal Femenina»; admin: «Defensa Personal» | «Defensa personal femenina» (también PDF y preinscripción pública) | L + P (D2) |
| 2 | Resto de nombres de disciplina | Taekwondo, Kickboxing, Pilates | Iguales | U |
| 3 | Importes de tarifa | FAMILIAR 0, PADRES_HIJOS 0 (28 padre/madre, 26 hijo/hija), ADULTO_GRUPO 20, INFANTIL_GRUPO 20, HERMANOS 26, INFANTIL 28, ADULTO 30, KICKBOXING 30, PILATES 30, DPF 25 | Iguales (`importe`, `importe_adulto`, `importe_menor`) | U |
| 4 | Nombres de tarifa | `editar-alumno.html` usa los mismos nombres; crear-alumno y preinscripciones-admin muestran el código | `nombre` = los de `editar-alumno.html` | L (cada pantalla conserva código o nombre) + P (D8) |
| 5 | Edad adulta | TKD 14, KB 15, resto 14; regla «menor si edad < E−1, o edad = E−1 y no cumple E este año» | 14 / 15 / 14 / 14; regla del backend `año_nacimiento + E > año_actual`. Equivalentes (fecha nula → menor en ambos) | U |
| 6 | Rangos de categoría | ≤9 Infantil, 10-11 Precadete, 12-13 Cadete, 14-16 Junior, ≥17 Senior, en **todas** las disciplinas | Mismos rangos, solo en TAEKWONDO. El backend acepta por nombre la categoría que envía el FE para un competidor de KB | L + P (D6) |
| 7 | Progresión TKD menores/adultos y KB | 3 mapas | `orden` + `aplica_a` (KB: intermedios y PUM `NINGUNO`, PUM tras NEGRO_5_DAN). Mismo resultado en todas las transiciones, incluidos PUM en adultos, DAN en menores y último grado | U |
| 8 | Grados seleccionables | KB: 11 códigos fijos; TKD: 19 | KB `aplica_a ≠ NINGUNO` = los 11; TKD = 19, mismo orden | U |
| 9 | `TipoGrado` | 13 códigos (incluye `ROJO_NEGRO`, `NEGRO`, que no existen) | 19 códigos | U (se borra el enum) |
| 10 | Nombre de grado | Código en bruto (`BLANCO_AMARILLO`), «MARRÓN» (KB ROJO, `grado-colors.ts`), «MARRON» y «MARRON/NEGRO 1 PUM» (vista usuario), «NEGRO 1º PUM» para `ROJO_NEGRO_1_PUM` (materiales) | «Blanco-Amarillo», «Rojo-Negro-1-Pum», «Negro-1-Dan»; KB ROJO «MARRÓN» (mayúsculas, el resto no) | L + P (D3) |
| 11 | Color KB ROJO | Rojo `#FF0000` en `grado-colors.ts`; marrón `#8B4513` en vista usuario | `color_1`/`color_2` nulos («decisión visual pendiente», V23) | L + P (D4, D5) |
| 12 | Tarifas por pantalla | crear-alumno: orden del enum (ADULTO, ADULTO_GRUPO, FAMILIAR, INFANTIL, INFANTIL_GRUPO, HERMANOS, PADRES_HIJOS) y otro orden en `:646`/`editar-alumno.ts:1540` (…, PADRES_HIJOS, FAMILIAR); `editar-alumno.html`: 8 tarifas sin INFANTIL_GRUPO ni FAMILIAR y sin filtrar por disciplina; preinscripciones-admin: INFANTIL, ADULTO, INFANTIL_GRUPO, ADULTO_GRUPO, FAMILIAR, HERMANOS, PADRES_HIJOS | Orden por `id`: FAMILIAR, PADRES_HIJOS, ADULTO_GRUPO, INFANTIL_GRUPO, HERMANOS, INFANTIL, ADULTO, KICKBOXING, PILATES, DPF; las 7 primeras con disciplina TAEKWONDO | L + P (D7) |
| 13 | Tarifa inicial en preinscripciones-admin | `<18` → INFANTIL, si no ADULTO (solo TKD) | `edad_adulto` TKD = 14 | L + P (D9) |
| 14 | `asignarCuantiaTarifa` | Sin PILATES ni DPF (lanza error) | Tiene las 10 | U (código muerto, se borra) |
| 15 | Regla `<18` de tutor | Mayoría legal | Igual en `PreinscripcionService` | U (no es catálogo) |

## 5. Decisiones del propietario

En las tablas, «Dn» es la decisión n de esta lista.

1. **`asignarCuantiaTarifa` (lanza con PILATES/DPF)**: no tiene llamadas. Recomendación: borrarlo en la
   rebanada de tarifas; no cambia nada visible.
2. **Nombre de DPF**: tres variantes. Recomendación: elegir una y escribirla en `disciplina.nombre` de
   Taemoi (afecta también al PDF); hasta entonces, legado.
3. **Nombres de grado**: el FE deriva etiquetas del código; la BD tiene `nombre` con otra forma.
   Recomendación: unificar a `grado.nombre` editando los nombres de Taemoi al formato que se quiera
   ver (p. ej. «NEGRO 1º DAN»); hasta entonces, legado. Otras escuelas usan `nombre` desde el principio.
4. **KB ROJO**: rojo o marrón, «MARRÓN» o «MARRON». Recomendación: marrón `#8B4513` y «MARRÓN» en todas
   las pantallas.
5. **Colores en la BD**: recomendación: dejar `color_1/color_2` nulos en Taemoi (paleta actual) hasta
   que el propietario quiera editarlos; las escuelas nuevas los rellenan al crear su sistema de grados.
6. **Categorías de KB**: hoy los competidores de KB reciben categorías de TKD por el FE. Recomendación:
   confirmar si KB compite por esas categorías; si sí, crear filas de `categoria` para KB (datos, desde
   la gestión o un script) y quitar el legado; si no, quitar el relleno para KB.
7. **Listas de tarifas por pantalla**: recomendación: unificar a «tarifas que admite la disciplina, en
   orden del catálogo» en todas las pantallas (corrige que `editar-alumno` ofrezca tarifas de otra
   disciplina y oculte INFANTIL_GRUPO/FAMILIAR); hasta entonces, legado.
8. **Etiqueta de tarifa (código o nombre)**: recomendación: `nombre` en todas las pantallas.
9. **Tarifa inicial en preinscripciones-admin**: recomendación: mantener `<18` en Taemoi (es su
   práctica de cobro) y no preseleccionar en otras escuelas.
10. **«Derecho a examen» solo en KB**: recomendación: legado en Taemoi; otras escuelas ven
    «recompensa». Si una escuela nueva lo necesita, un campo de disciplina (migración futura).
11. **Cinturón rojo bordado**: recomendación: solo con legado (depende de productos de Taemoi).
12. **Emoji y color por disciplina en el panel de administración**: recomendación: legado para los
    códigos de Taemoi y valor neutro para el resto; sin columna nueva por ahora.
13. **Páginas públicas por disciplina**: siguen siendo contenido de Taemoi; la web por escuela es otra fase.
14. **Tarifas para `ROLE_USER`**: recomendación: no enviarlas (no las usa ninguna vista de usuario).
15. **Fallo al cargar el catálogo**: recomendación: aviso y desplegables vacíos, nunca constantes de Taemoi.

### Decisiones tomadas (14/10/2026)

- D2: «Defensa Personal Femenina» en todas las pantallas, el PDF y `disciplina.nombre` de Taemoi.
- D3: todas las pantallas usan `grado.nombre`; los de Taemoi pasan al formato «BLANCO-AMARILLO»,
  «ROJO-NEGRO 1º PUM», «NEGRO 1º DAN». Sustituye la decisión 5 del informe (listados cortos).
- D4: KB ROJO = marrón `#8B4513` y «MARRÓN» en todas las pantallas.
- D5: los colores de Taemoi se guardan en `color_1/color_2` (paleta actual, KB ROJO marrón).
- D6: cada disciplina tiene sus propias categorías. Las de KB (y de otros deportes) aún no se conocen:
  Taemoi conserva el relleno con las de TKD como legado; las escuelas nuevas solo asignan categoría si
  la disciplina tiene categorías propias.
- D7: tarifas que admite la disciplina, en orden del catálogo, en todas las pantallas.
- D8: nombre de la tarifa en todas las pantallas.
- Orden de tarifas: nueva columna `tarifa.orden`; Taemoi con el orden de crear-alumno.
- D1, D9–D15: según la recomendación.
- Consecuencia: hace falta una migración de datos (V30: nombres y colores de grado, nombre de DPF,
  `tarifa.orden`). Las sedes pasan a V31.

## 6. Migraciones

V30 (catálogo visual, decisiones del 14/10/2026): nombres y colores de grado de Taemoi, nombre de DPF y
`tarifa.orden` (ver `modelo-datos.md`). Las sedes pasan a V31. Emoji/color de disciplina y la regla de
«derecho a examen» siguen en el legado; si el propietario decide columnas nuevas
(`disciplina.icono/color`, una bandera de examen), irán en una migración posterior.

## 7. Rebanadas de implementación

Cada rebanada se revisa por separado y deja a Taemoi igual. Datos de prueba: un fixture JSON con la
respuesta de `/api/catalogo` para la organización 1 (generado por la prueba de la rebanada 1) y otro
de segunda escuela coherente con `scripts/pruebas/segunda-escuela.sql`.

| # | Rebanada | Nivel | Pruebas |
|---|---|---|---|
| 0 | Pruebas de caracterización de las 7 listas en `FE` (otro agente, en curso) | — | Fijan los valores actuales; deben seguir verdes en todas las rebanadas |
| 1 | Backend: `CatalogoController`, DTO, `/api/catalogo` y `/api/catalogo/publico`, reglas de `SecurityConfiguration`, cabeceras | careful (seguridad y tenant) | MockMvc por rol (USER sin tarifas, anónimo solo en `/publico`, anónimo → org 1), aislamiento org 2, JSON de org 1 contra las semillas, `GradoJsonTest` intacto |
| 2 | FE núcleo: modelos, `CatalogoService`, `catalogo-reglas.ts`, `legado-taemoi.ts`, resolver, invalidación | medium | Unitarias de reglas con el fixture de Taemoi comparadas con las utilidades actuales (todas las edades −1..90 y todos los grados); servicio con `HttpTestingController` (petición compartida, vaciado en login/logout, estado de error) |
| 3 | Utilidades: `categoria-por-edad`, `grado-progresion`, `grado-colors`, `tarifa.constants` delegan en el catálogo con la misma firma (parámetro de disciplina opcional) | medium | Las de caracterización sin cambios + casos de segunda escuela |
| 4 | Disciplinas en componentes: etiquetas, listas, valor por defecto, clases CSS, banderas (licencia, competidor, sin grado, convocatorias, material) | medium | Specs de componente con los dos fixtures; búsqueda en CI de literales de disciplina fuera de `legado-taemoi.ts` |
| 5 | Grados: subconjuntos KB, borrado de `TipoGrado`, nombres y colores (legado frente a catálogo) | medium | Specs de `vista-principal-user` (`getBeltVisual`) y de etiquetas con los dos fixtures |
| 6 | Tarifas: listas por pantalla, importes, `preinscripciones-admin`, borrado de `asignarCuantiaTarifa` | careful (importes) | Caracterización previa de cada lista de pantalla; importe por tarifa y rol con los dos fixtures |
| 7 | Preinscripción pública con `/api/catalogo/publico` | light | Spec del componente: opciones, enlace `?deporte=`, estado de carga |
| 8 | Limpieza: borrar `enums/deporte.ts`, `tipo-tarifa.ts`, `TARIFAS_ESTANDAR` cuando no tengan usos | light | Compilación, lint y suite completa |

Comprobación manual al final: Taemoi y la segunda escuela con `docker-compose.pruebas.yml`, recorriendo
alta y edición de alumno, preinscripción, tesorería, panel de administración y vista de usuario.
