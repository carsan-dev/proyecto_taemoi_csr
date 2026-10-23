# Entrega 8b (rediseño del panel de gestión): plan de implementación

> **Ejecutores:** agentes savvy-* orquestados por el router. Cada pieza 8b-n es un agente en su rama `feature/e8b-<pieza>`,
> con revisión independiente (`savvy-reviewer`) antes de la PR. Las piezas con permisos, migración, dinero o mucha
> superficie (8b-I, 8b-T, 8b-3b, 8b-3d, 8b-3g a 8b-3k, 8b-Z) las ejecuta `savvy-careful` con revisión en opus; el resto, `savvy-medium`
> (en opus las que llevan backend o gráfica). Los pasos usan casillas (`- [ ]`). Cada implementador amplía su sección
> con la lista detallada de tests al lanzarse, como en la E7 y la E8.

**Objetivo:** el panel de gestión con la dirección C («La escuela al frente») detrás de un interruptor por usuario:
cabecera del color y el logo de cada escuela, navegación de 6 entradas con barra inferior en el móvil, Inicio I1,
Alumnos, ficha F1 «Carnet», Grupos, Tesorería y Convocatorias y, por la decisión 259, las siete pantallas de gestión
restantes (Eventos, Productos, Preinscripciones, Solicitudes, Caja de sede, Configuración y Registro de acciones, con la
dirección ya elegida en la ronda 3, decisión 261), sin perder nada de lo que se ve hoy (decisiones 232 y 236 a 262).
Moiskimdo no nota nada mientras el interruptor esté apagado.

**Arquitectura:** las pantallas nuevas viven en `F/panel-nuevo/`, se cargan aparte (carga diferida) y se enrutan con un
`canMatch` que mira el interruptor: con él apagado, el router ni descarga la pantalla nueva y sigue a la ruta de hoy.
Los tokens, las fuentes y la base viven en una hoja global aparte (`panel-nuevo.css`, `inject: false`) que se carga solo
al entrar en el panel nuevo y declara todo bajo `.pn`, nunca en `:root`. El interruptor se guarda por escuela y usuario
en una tabla nueva (V31_10) y llega al frontend dentro de `GET /api/auth/permisos`. La marca de la escuela (color con su
regla de contraste y logo) se lee con `GET /api/escuela/marca` y la escribe solo el superadmin.

**Tecnología:** Angular 20 (standalone, Karma/Jasmine, Playwright para capturas), Spring Boot + JPA/Hibernate (MySQL 8,
Flyway).

**Spec y diseño:** `DESIGN.md` (raíz), `PRODUCT.md`, `docs/multi-escuela/fase3-e8b-diseno.md` y las decisiones 232 a
262 del propietario (las de esta entrega, en §25). Los inventarios «nada se pierde» de cada pantalla están en el
**Anexo A**: son la lista de aceptación de cada pieza de pantalla.

Rutas abreviadas: `J/` = `src-api/src/main/java/com/taemoi/project/`, `T/` = `src-api/src/test/java/com/taemoi/project/`,
`F/` = `src-frontend/src/app/`, `M/` = `src-api/src/main/resources/db/migration/`.

## Restricciones globales

- **TDD.** Cada pieza escribe primero los tests que fallan, los ve fallar y luego implementa.
- **Moiskimdo intacta con el interruptor apagado.** Ninguna pieza cambia lo que ve hoy un usuario sin el interruptor,
  salvo lo que este plan nombra y el propietario aprueba (8b-1 no cambia nada visible; 8b-I añade una entrada al menú
  del propietario, decisión 256). Las capturas «apagado» de cada PR son idénticas a las de antes.
- **Solo el panel** (decisión 240). Familias, web pública y login no cambian. La sesión solo-profesor («Mis grupos» y
  «Mi caja») queda fuera del interruptor hasta 8b-Z (decisión 260). Con el interruptor encendido, las rutas públicas y
  de familia siguen con la cabecera de hoy.
- **Presupuesto.** El aviso del bundle inicial salta en **1.500.000 bytes** (`"1.5mb"` en `angular.json:75`;
  `@angular/build` 20.3.34 usa `BYTES_IN_KILOBYTE = 1000`; el plan de la E8 decía 1.572.864 por error). Referencia:
  **1.499.939 bytes** en `3b59a8cb` (61 de margen), medido por el hotfix 2 con `node_modules` propios (`npm ci`); ver
  §2. No se sube el presupuesto. 8b-1 libera 54.011 bytes (§2); ninguna otra
  pieza puede subir el inicial más de 4.000 bytes, y cada PR anota el total exacto.
- **Carga diferida.** Toda pantalla nueva, la cabecera nueva y la hoja `panel-nuevo.css` se cargan aparte. El código
  que va al inicial se limita al guard, al servicio del interruptor y a la rama de la cabecera en `skeleton`.
- **Roles con nombre visible nuevo (decisión 262).** En las pantallas nuevas los roles se muestran como «Familia»,
  «Gestión» y «Administración». Solo cambia el texto: los roles, los permisos y los valores internos (`ROLE_USER`,
  `ROLE_MANAGER`, `ROLE_ADMIN`) no cambian, ni lo que se envía a la API. Correspondencia: `ROLE_USER` = «Familia»,
  `ROLE_MANAGER` = «Gestión», `ROLE_ADMIN` = «Administración»; por la decisión 263, `ROLE_PROFESOR` = «Profesorado» y
  `ROLE_RESPONSABLE_SEDE` = «Responsable de sede». El texto visible sale de un solo ayudante de las
  pantallas nuevas, con su spec. Las pantallas de hoy no se tocan (hoy: «Usuario», «Manager», «Admin»).
- **Formato es-ES.** Importes, fechas y plurales con los ayudantes de `F/panel-nuevo/formato/` (8b-1). `LOCALE_ID
  'es-ES'` solo en las rutas nuevas hasta 8b-Z (decisión 258). Nunca `toFixed` para mostrar importes.
- Aislamiento por escuela: tablas nuevas como `EntidadOrganizacion` (`@TenantId`); `membresia`, `asignacion_profesor` y
  `membresia_sede` NO lo son: toda consulta sobre ellas lleva `organizacion_id` explícito. Consultas nativas, siempre
  con `organizacion_id` explícito.
- Matriz: cada ruta nueva lleva su línea (y su `HEAD` si es `GET`) en
  `src-api/src/test/resources/seguridad/matriz-permisos.txt` y su nota en `docs/multi-escuela/matriz-endpoints.md`.
  **Ninguna casilla existente cambia; el diff de la matriz solo añade líneas.**
- Registro de acciones: solo ids, enumeraciones y booleanos; nunca email, nombre, cargo, dirección, teléfono ni el
  valor del color. Una fila por escritura: una ruta entra en `AuditoriaRequestFilter.EscrituraConRegistroPropio` solo si
  todos sus caminos 2xx registran, con su caso en `AuditoriaRequestFilterRegistroPropioTest` y en un IT contra MySQL.
- Dorados de Moiskimdo (`MoiskimdoTesoreriaIdenticaIntegrationTest` y el JSON dorado de alumnos) intactos y en verde.
  Ningún DTO existente de alumnos o tesorería cambia de forma: los datos nuevos salen por rutas nuevas.
- IT que comparten MySQL: asserts independientes del orden; datos inventados; lo forzado por SQL se deshace en
  `finally`.
- Karma solo ejecuta los specs de `src-frontend/tsconfig.spec.json` («files») **y** de `angular.json` (test
  «include»): cada spec nuevo va en las dos listas.
- Baterías en primer plano: `mvn test -DexcludedGroups=migracion` y `mvn test -Dgroups=migracion` en `src-api`;
  `npm test -- --watch=false --browsers=ChromeHeadless` y `npm run build:production` **sin aviso de presupuesto**. Si se
  cuelga un proceso, se limpia al cerrar.
- **Capturas.** Cada PR de pantalla adjunta antes y después a **375, 768 y 1280 px** con datos ficticios (nunca
  nombres, emails, teléfonos, DNI ni salud reales), con el interruptor apagado (igual que hoy) y encendido. Se trabaja
  con las skills `impeccable` (modo Operate, `DESIGN.md` como sistema) y `frontend-design`; la gráfica de ocupación,
  también con `dataviz`. Antes de la PR, `C:/Users/croly/.claude/skills/impeccable/scripts/impeccable detect --json`
  sobre los ficheros de UI cambiados.
- **Nada se pierde** (decisión 232): la PR de cada pantalla marca, fila a fila, su tabla del Anexo A.
- **WCAG 2.2 AA** (decisión 243): contraste medido de cada pareja nueva en la PR, foco visible, objetivos ≥ 24 px
  (44 px en móvil), sin desplazamiento horizontal a 375, teclado completo en menús, pestañas y filas desplegables.
- Finales de línea: ficheros nuevos en LF; antes de cada commit, `git diff --stat` igual a
  `git diff --ignore-cr-at-eol --stat`, y `tr -cd '\r' < fichero | wc -c` = 0 en los nuevos.
- Commits convencionales en español, sin `Co-Authored-By` ni atribución a IA.

## Puntos de revisión

Los casos que más pueden morder. Cada uno tiene su test en la pieza que lo posee.

1. **Apagado = hoy.**
   - 8b-I: `disenoNuevoGuard` falla cerrado (error o sin permisos → ruta de hoy) y con él apagado no se descarga ningún
     chunk de `panel-nuevo` (spec con el router y `loadComponent` espiado).
   - Cada pieza de pantalla: capturas «apagado» idénticas a las de antes.
2. **El interruptor no cruza escuelas.**
   - 8b-I: `disenoNuevo_noCruzaEscuelas` (propietario de A no lee ni cambia el de un usuario solo de B: 404),
     `disenoNuevo_soloPropietarioAsigna` (gestor 403), `disenoNuevo_soloGestionDeLaEscuela` (familia o profesor sin
     gestión: 400), `miDiseno_sinPermiso_403`.
3. **Registro solo con ids y una fila por escritura.**
   - 8b-I y 8b-T: `registroSoloConIds` (sin color, nombre ni email), casos en
     `AuditoriaRequestFilterRegistroPropioTest` y en el IT; el cambio de color se registra en la escuela DESTINO, como el
     logo (E7-7a).
4. **Regla de contraste.**
   - 8b-T: `ReglaContrasteTest` con la tabla de §4 (Moiskimdo `#1B2B2E` → blanco 14,66:1; verde `#3E9B4F` → tinta
     4,56:1, razones truncadas como en §4; un amarillo → tinta; un gris medio que no llega con ninguno → primario oscurecido) y
     `tintaSiempreAlMenos4_5SobreBlanco` sobre una rejilla de colores.
5. **El superadmin es el único que escribe la marca.**
   - 8b-T: `marca_soloPlataforma` (propietario, gestor y familia 403) y la matriz.
6. **Presupuesto.**
   - 8b-1: el inicial baja al menos 54.000 bytes frente a `3b59a8cb` medido en el mismo entorno (`npm ci`); se espera
     ≈ 1.445.900. Cada pieza: el total en la PR y sin aviso.
7. **Pendiente vencido sin romper la tesorería de hoy.**
   - 8b-2: `pendienteVencido_coincideConMovimientosNoPagadosVencidos`, `pendienteVencido_noCruzaEscuelas`,
     `pendienteVencido_desglosesSumanElTotal`; `MoiskimdoTesoreriaIdenticaIntegrationTest` sin tocar.
8. **Cobrar desde las pantallas nuevas = cobrar hoy.**
   - 8b-3a, 8b-3b, 8b-3d, 8b-3i y 8b-3j: «Cobrar», deshacer, recibir entrega y apuntar movimiento llaman al mismo
     endpoint, con el mismo cuerpo y la misma confirmación que hoy (spec con `HttpTestingController`); el grado sigue
     subiendo solo al cobrar el derecho de examen.
9. **Nada se pierde.** Cada PR de pantalla con su tabla del Anexo A marcada fila a fila; las de 8b-3f a 8b-3k, con el
   inventario de la variante elegida en la ronda 3 (decisión 261, RB3 hecha).
10. **Las rutas duplicadas conservan sus guards.**
    - 8b-3k: `configuracion-sistema` y `auditoriaSistema` nuevas con `roleGuard` y `adminOnlyGuard` (un gestor encendido
      no entra); 8b-3i: acciones por rol iguales que hoy; 8b-3g: cuerpo de productos idéntico campo a campo.

---

## 1. Mapa de piezas

Orden: **8b-1 → 8b-I → 8b-T → 8b-N → 8b-2 → 8b-3a … 8b-3e → 8b-3f … 8b-3k → 8b-B4 → 8b-3l … 8b-3n → 8b-M → 8b-Z**. 8b-I y 8b-T se pueden hacer
en paralelo tras 8b-1; 8b-N depende de las dos; las pantallas dependen de 8b-N y no entre sí (3b enlaza desde 3a, pero
la ruta de la ficha existe ya); **8b-B3** (boceto de la ronda 3, solo diseño) está **hecha**: el propietario eligió las
variantes (decisión 261) y 8b-3f a 8b-3k pueden empezar cuando 8b-N esté lista (decisión 228: se diseñó antes de
construir); 8b-Z, de la aprobación del propietario (R6). Cada pieza se despliega apagada al fusionarse (decisión 255,
§24).

Los nombres de esta tabla fijan los contratos entre piezas. Quien construye puede renombrarlos, pero lo anota en la PR
y actualiza este mapa.

| Pieza | Objetivo | Migración | Depende de | Tier / ejecutor | Commit (orientativo) |
|---|---|---|---|---|---|
| **8b-1** | Sitio en el presupuesto (fuera Material), `panel-nuevo.css` con tokens de `DESIGN.md`, fuentes propias, ayudantes es-ES | No | — | medium; `savvy-medium` | `feat(panel): base del panel nuevo con tokens, fuentes y formato es-ES (8b-1)` |
| **8b-I** | Interruptor por escuela y usuario: tabla, API, `disenoNuevo` en permisos, `disenoNuevoGuard`, pantalla del propietario | **Sí, V31_10** | 8b-1 | careful; `savvy-careful`, revisión opus | `feat(panel): interruptor del diseño nuevo por usuario (8b-I)` |
| **8b-T** | Marca por escuela: regla de contraste, `GET /api/escuela/marca` y `/logo`, escritura del superadmin con registro, nombre visible | No (columnas de V18) | 8b-1 | careful; `savvy-careful`, revisión opus | `feat(marca): color y logo de la escuela en el panel (8b-T)` |
| **8b-N** | Cabecera C, menú de 6 entradas, barra inferior, cuenta con «Volver al diseño anterior», panel sin el pie público | No | 8b-I, 8b-T | medium; `savvy-medium` en opus | `feat(panel): navegación del panel nuevo (8b-N)` |
| **8b-2** | Inicio I1: ocupación día × franja, «Hoy», pendiente vencido con desglose, accesos | No | 8b-N | medium; `savvy-medium` en opus | `feat(panel): inicio nuevo con ocupación y pendiente vencido (8b-2)` |
| **8b-3a** | Alumnos: filas de dos líneas, 25 por página, cambio a tarjetas, pendiente por alumno | No | 8b-N | medium; `savvy-medium` en opus | `feat(panel): listado de alumnos nuevo (8b-3a)` |
| **8b-3b** | Ficha F1 «Carnet» con pestañas | No | 8b-N | careful; `savvy-careful` | `feat(panel): ficha del alumno con carnet (8b-3b)` |
| **8b-3c** | Grupos: estructura de C con tarjetas de A | No | 8b-N | medium; `savvy-medium` | `feat(panel): grupos nuevos (8b-3c)` |
| **8b-3d** | Tesorería C: pestañas, filas de dos líneas con «Cobrar», exportar en menú | No | 8b-N | careful; `savvy-careful`, revisión opus | `feat(panel): tesorería nueva (8b-3d)` |
| **8b-3e** | Convocatorias C | No | 8b-N | medium; `savvy-medium` en opus | `feat(panel): convocatorias nuevas (8b-3e)` |
| **8b-B3** (hecha, https://claude.ai/artifact/5ynw1nWRYKQDJ8LdVqfq1w) | Boceto ronda 3: las siete pantallas restantes, hoy frente a propuestas en el mundo de C, con inventario; aprobación del propietario (decisión 261) | No (solo diseño, sin código) | 8b-1 (tokens) | — ; diseño con `impeccable` | `docs(e8b): bocetos de la ronda 3` |
| **8b-3f** | Eventos: listado (con su orden por arrastre), crear y editar | No | 8b-N, 8b-B3 | medium; `savvy-medium` | `feat(panel): eventos nuevos (8b-3f)` |
| **8b-3g** | Productos: listado, crear y editar (precios del catálogo) | No | 8b-N, 8b-B3 | careful (dinero); `savvy-careful` | `feat(panel): productos nuevos (8b-3g)` |
| **8b-3h** | Preinscripciones: bandeja, lista de espera y paso a alumno | No | 8b-N, 8b-B3 | careful (datos de menores, altas); `savvy-careful` | `feat(panel): preinscripciones nuevas (8b-3h)` |
| **8b-3i** | Solicitudes: bandeja de la 6c (deshacer cobros, devoluciones, encargos) | No | 8b-N, 8b-B3 | careful (dinero y permisos); `savvy-careful`, revisión opus | `feat(panel): solicitudes nuevas (8b-3i)` |
| **8b-3j** | Caja de sede: efectivo de los profesores, entregas y movimientos | No | 8b-N, 8b-B3 | careful (dinero); `savvy-careful`, revisión opus | `feat(panel): caja de sede nueva (8b-3j)` |
| **8b-3k** | Escuela: Configuración (límite por turno y Gestión de Roles) y Registro de acciones | No | 8b-N, 8b-B3 | careful (permisos y datos sensibles); `savvy-careful`, revisión opus | `feat(panel): configuración y registro de acciones nuevos (8b-3k)` |
| **8b-B4** | Boceto de la ronda 4: alta de alumno, crear y editar grupo y turno, alumnos del grupo y productos del alumno (hoy frente a una o dos propuestas en el mundo de C, con inventario «nada se pierde») | No | 8b-3k | solo diseño; el orquestador con `impeccable` y `frontend-design` | `docs(e8b): boceto de la ronda 4 (8b-B4)` |
| **8b-3l** | Alta de alumno | No | 8b-B4 | careful (datos de menores, altas y cargos iniciales); `savvy-careful`, revisión opus | `feat(panel): alta de alumno nueva (8b-3l)` |
| **8b-3m** | Grupos: crear y editar grupo y turno, y gestionar los alumnos del grupo | No | 8b-B4, 8b-3c | medium; `savvy-medium`, revisión sonnet | `feat(panel): formularios de grupos y turnos nuevos (8b-3m)` |
| **8b-3n** | Productos de un alumno: asignar, editar (fecha, cantidad, precio), cobrar y quitar | No | 8b-B4, 8b-3b | careful (dinero); `savvy-careful`, revisión opus | `feat(panel): productos del alumno nuevos (8b-3n)` |
| **8b-Z** | Retirada de lo viejo (tras aprobación): rutas de hoy fuera, `LOCALE_ID` global, interruptor retirado | No (la tabla se queda; su borrado iría con la contracción) | todas y R6 | careful; `savvy-careful`, revisión opus | `refactor(panel): retirar las pantallas anteriores al rediseño (8b-Z)` |

**Migración:** solo V31_10 (8b-I). El `#1B2B2E` de Moiskimdo lo pone el superadmin desde «Logo y color», sin migración
de datos (decisión 257). Numeración: la última es `V31_9__usuario_contrasena_opcional.sql`; Flyway compara por partes
numéricas (31.10 > 31.9), así que V31_10 va detrás. **No se usa V32.x:** V32 está reservada para la contracción con guarda
(`fase3-sedes-permisos.md` §2) y una V32_1 aplicada antes que ella dejaría V32 «por detrás» y Flyway la rechazaría
(fuera de orden) cuando llegue.

**Alcance ampliado (decisión 259):** las siete pantallas de gestión que no tenían boceto (Eventos, Productos,
Preinscripciones, Solicitudes, Caja de sede, Configuración y Registro de acciones) entran en la E8b, tras su boceto
(8b-B3). Agrupación: solo Configuración y Registro de acciones van juntas (8b-3k): son pequeñas (≈ 29 y 24 kB de
plantilla y código), las dos solo del propietario (`adminOnlyGuard`) y piden la misma revisión de permisos. Eventos y
Productos, aunque pequeñas, van separadas porque su riesgo es distinto (Productos toca precios). Hasta que cada una se
construya, con el interruptor encendido se ve con la cabecera nueva y su contenido de hoy. Los formularios que la 259 no
nombraba (alta de alumno, crear y editar grupo y turno, gestionar los alumnos del grupo y gestionar los productos de un
alumno) entran también (decisión 282, 23/10): boceto de la ronda 4 (8b-B4) y piezas 8b-3l a 8b-3n tras 8b-3k. Siguen
fuera las pantallas de plataforma (superadmin) y, por la 260, Mis grupos y Mi caja hasta 8b-Z; hasta entonces, con el
interruptor encendido, se ve con la cabecera nueva y su contenido de hoy.

---

## 2. Presupuesto del bundle inicial

**Referencia: 1.499.939 bytes en `3b59a8cb` (61 de margen)**, medido por el hotfix 2 con `node_modules` propios
(`npm ci`). El desglose de abajo es de un build propio de la planificación con `--stats-json` sobre el mismo commit, que
dio **1.499.220** (719 bytes menos): se hizo con una unión al `node_modules` del checkout principal, que está en otra rama
(`feature/e8-6`). El `package-lock.json` es el mismo, así que la causa probable es que ese `node_modules` no es una
instalación limpia de ese lock (versiones transitivas o restos de instalaciones anteriores), no el código. Las partes se
leen como proporciones; las cifras que cuentan se miden siempre con `npm ci` en el worktree de la pieza.

| Parte del inicial | Bytes | Qué es |
|---|---:|---|
| `styles-*.css` | 486.086 | Bootstrap CSS 230.835 · `styles.scss` 167.263 · Bootstrap Icons CSS 84.764 · Montserrat en línea 3.206 |
| ↳ dentro de `styles.scss` | | estilos compartidos de gestión 107.328 (`admin-shared-styles` + `admin-mobile-forms`, globales bajo `body:has(.admin-ui)`) · tema de Angular Material 53.758 · resto ~6.200 |
| `main-*.js` | 368.987 | portada pública eager 141.805 (con su CSS de 65.075) · login eager 57.830 (con su CSS de 24.412) · cabecera y pie 138.892 · resto |
| chunks iniciales | 529.113 | `@angular/core` 149.312, router 78.759, SweetAlert2 78.742, common 60.579, forms 50.773, rxjs 28.883… |
| `scripts-*.js` | 80.449 | `bootstrap.bundle.min.js` |
| `polyfills-*.js` | 34.585 | zone.js |
| **Total** | **1.499.220** (referencia: 1.499.939) | aviso en 1.500.000 |

De dónde sale el sitio, de menos a más riesgo:

1. **Quitar el tema de Angular Material de `styles.scss`** (`@include mat.core()` y `mat.all-component-themes`, más el
   `@use` y `define-theme`): **−54.011 bytes**, medido con un build real en el mismo entorno (el CSS pasa de 486.086 a
   432.075 y el inicial de 1.499.220 a 1.445.209); sobre la referencia se espera **≈ 1.445.900**, unos 54.000 de
   margen. Ningún fichero importa `@angular/material` (0 coincidencias) y ninguna plantilla ni hoja usa `--mat-`,
   `.mat-` ni `mdc-`; el CDK (`A11yModule`, `DragDropModule`) carga sus propios estilos. No cambia nada visible. **Se aplica en 8b-1.**
2. **Login con carga diferida** (`app.routes.ts:175`, hoy eager «para mejor UX»): −57.830 bytes. No cambia nada
   visible, pero añade una descarga al abrir `/login`. **Reserva**: solo si hiciera falta (decisión 260).
3. **Estilos compartidos de gestión fuera del inicial** (107.328 bytes): a una hoja `inject: false` cargada al entrar en
   el panel. Riesgo de parpadeo y de orden de la cascada en las pantallas de hoy. **No antes de 8b-Z.** Con la decisión 259
   casi todas las pantallas de gestión pasan al sistema nuevo: en 8b-Z esta hoja puede salir del inicial entera (o
   quedarse solo para los formularios que sigan como hoy).
4. Recortar Bootstrap Icons (84.764) o Bootstrap (230.835) con purga: muchas clases `bi-*` se componen en tiempo de
   ejecución. **No.**
5. La portada pública eager (141.805) no se toca: es la web pública y se prerenderiza.

Lo que pide la base común **en el inicial**: el servicio del interruptor y su guard, la rama de la cabecera en
`skeleton` y las entradas de ruta con `canMatch` (estimado 3-5 kB). Todo lo demás va aparte: tokens, fuentes y base en
`panel-nuevo.css` (`inject: false`, 0 bytes en el inicial); `registerLocaleData` del español (1.495 bytes) y los
ayudantes de formato, en el chunk compartido del panel nuevo; la cabecera nueva, en un `@defer`.

Desorden que el sistema nuevo deja atrás (no se borra en la E8b salvo en 8b-Z): 11 colores de botón, 21 radios y 26
sombras en 9 pantallas, `$primary-color` en 15 ficheros con 3 valores (auditoría de `fase3-e8b-diseno.md` §3).

---

## 3. Diseño del interruptor (8b-I)

- **Dónde se guarda.** Tabla nueva `diseno_panel_usuario` (V31_10): `id`, `organizacion_id` (FK, `@TenantId`),
  `usuario_id` (FK a `usuario`), `permitido` y `activo` (BOOLEAN NOT NULL), `cambiado_por_usuario_id` (NULL),
  `cambiado_en` (DATETIME(6)); clave única `(organizacion_id, usuario_id)`. Es `EntidadOrganizacion`: Hibernate filtra
  por la escuela activa. Ni `usuario` (es global: cruzaría escuelas) ni `membresia` (una fila por rol: se duplicaría)
  sirven. Ampliación pura: tabla vacía que el código anterior ignora.
- **Para todos** (antes de retirar lo viejo): clave `panel.diseno_por_defecto` = `NUEVO` en `configuracion_organizacion`
  (ya existe, clave/valor por escuela; sin migración). Sin la clave, `VIEJO`.
- **Regla efectiva** (`DisenoPanelService.activoPara(usuarioId)`): con fila y `permitido`, su `activo`; si no, el valor
  por defecto de la escuela.
- **Quién lo cambia.**
  - El propietario (`ROLE_ADMIN`) da o quita el permiso a cualquier miembro de **gestión** de su escuela (membresía
    PROPIETARIO o ADMINISTRADOR activa, consultada con `organizacion_id` explícito), también a sí mismo; al darlo, queda
    activo. Usuario sin gestión en esta escuela: 400; de otra escuela o inexistente: 404. El gestor no asigna (403).
  - Cada usuario con permiso (o con el valor por defecto `NUEVO`) puede encenderlo o apagarlo **para sí mismo** desde
    su menú de cuenta (decisión 256); a otros, nunca.
  - El valor por defecto de la escuela («Para todo el equipo»), solo el propietario (decisión 256). Se construye en
    8b-I, oculto en la pantalla hasta R5 (decisión 258).
  - La pantalla de asignación se abre desde la entrada «Diseño nuevo» del menú de cuenta del panel de hoy, visible solo
    para el propietario (decisión 256).
  - Nunca entre escuelas: todo con la escuela activa de la sesión.
- **Rutas** (regla de URL nueva `/api/panel/**` → `ROLE_ADMIN` o `ROLE_MANAGER`, fuera de la lista blanca
  solo-profesor; `@PreAuthorize` por método):
  - `GET /api/panel/diseno-nuevo` (propietario): `{porDefecto, usuarios[{usuarioId, permitido, activo}]}`; la pantalla
    lo cruza con `GET /api/miembros` para los nombres.
  - `PUT /api/panel/diseno-nuevo/{usuarioId}` `{permitido}` (propietario).
  - `PUT /api/panel/diseno-nuevo/por-defecto` `{nuevo}` (propietario).
  - `PUT /api/panel/mi-diseno` `{activo}` (gestión con permiso; sin permiso, 403).
  - Registro: módulo `panel`, acciones `DISENO_PERMITIR`, `DISENO_QUITAR`, `DISENO_ACTIVAR`, `DISENO_DESACTIVAR`,
    `DISENO_POR_DEFECTO` con `usuarioId` y booleanos. Las cuatro escrituras, en `EscrituraConRegistroPropio`.
- **Cómo lo lee el frontend al arrancar.** `PermisosSesionDTO` añade `disenoNuevo {activo, permitido}` (sin ruta nueva:
  `GET /api/auth/permisos` ya existe y se comparte con `shareReplay`). Hoy el frontend solo pide permisos con
  `ROLE_PROFESOR`; pasa a pedirlos también con `ROLE_ADMIN` o `ROLE_MANAGER`, una vez por sesión. Error → apagado.
- **Cómo se enruta sin cargar las dos.** Cada pantalla rediseñada tiene dos entradas con la misma ruta: primero la nueva
  con `canMatch: [disenoNuevoGuard]` y `loadComponent` de `panel-nuevo`; después la de hoy, sin tocar. `canMatch` se
  evalúa **antes** de descargar: con `false`, el router pasa a la siguiente entrada y solo baja el chunk de hoy. El
  guard, además, espera a `panel-nuevo.css` (carga única del `<link>`): sin hoja, `false` y pantalla de hoy (sin
  parpadeo ni pantalla a medias). Al cambiar el interruptor propio: `reiniciar()` de permisos y recarga de la página.
- **Cabecera.** `skeleton` muestra `<app-header>` de hoy salvo con el interruptor encendido **y** una ruta de gestión;
  entonces, `@defer (on immediate)` de la cabecera nueva, con un hueco de 64 px mientras llega. Mientras no se sepa el
  interruptor (primera carga en una ruta de gestión), la cabecera de hoy, salvo que el navegador recuerde que estaba
  encendido: entonces el hueco. Por qué: con el hueco para todos, quien lo tiene apagado (casi todos hasta R5) veía un
  hueco de 64 px y luego la cabecera de hoy de 70 px; así, apagado = hoy desde el primer píxel, y solo quien lo usa ve
  el hueco en vez de la cabecera vieja. El recordatorio es un booleano global en `localStorage` (`panelNuevo`), sin
  email ni usuario (sería un dato personal): se escribe o se borra solo con una respuesta válida de permisos (un
  error no lo toca), se borra al cerrar sesión y, si `localStorage` falla, no hay recordatorio. El interruptor se vuelve
  a leer en cada navegación a una ruta del panel (los permisos están en caché; tras `reiniciar()`, una petición).

## 4. Diseño del tema por escuela (8b-T)

- **Datos.** `organizacion.color_primario` (CHAR(7), NULL) y `logo_imagen_id` existen desde V18; nadie los expone al
  panel. Hoy el logo del panel es fijo (`assets/media/Logo_escuela.svg`, `header.component.html:11, 32, 226, 596, 655`).
- **Regla de contraste** (`J/marca/ReglaContraste.java`, pura y sin Spring; la aplica el sistema, no el superadmin):
  - Texto sobre el primario: blanco si da ≥ 4,5:1; si no, tinta `#1A2230` si da ≥ 4,5:1; si ninguno llega, el
    primario se oscurece en OKLCH (misma tonalidad y croma, luz −0,01 por paso) hasta que el blanco llegue.
  - Secundario de cabecera: mezcla del texto con el primario que dé ≥ 4,5:1 sobre el primario.
  - `primarioTinta`: el primario oscurecido hasta ≥ 4,5:1 sobre blanco (si ya llega, él mismo); `primarioSuave`: el
    primario al 10 % sobre blanco, con la tinta ≥ 4,5:1 encima.
  - Sin color guardado: `#1A2230` (decisión 257).
  - Se guarda el valor tal cual; lo derivado se calcula al leer (determinista, sin columnas nuevas).
  - Razones de contraste: se muestran truncadas a dos decimales (Moiskimdo 14,66:1 con blanco; el verde `#3E9B4F`
    4,56:1 con la tinta), nunca redondeadas: así una pareja que no llega no puede aparecer como «4,50:1». La decisión
    se toma con el valor exacto (≥ 4,5).
- **Lectura** (`GET /api/escuela/marca`, gestión de la escuela activa; responsable de sede, `DENIEGA` hasta la E9):
  `{nombre, logo: 'SUBIDO' | 'LEGADO' | 'NINGUNO', logoVersion, tema {primario, sobrePrimario, sobrePrimario2,
  primarioTinta, primarioSuave, ajustado}}`. `GET /api/escuela/logo`: PNG del logo subido de la escuela activa
  (`Cache-Control: private, max-age=300`, `ETag` con el id de la imagen); 404 sin él. `LEGADO` (Moiskimdo sin logo
  subido) usa el SVG de siempre de `assets`.
- **Nombre visible del usuario**: `PermisosSesionDTO.nombreVisible` («Nombre A.» con la inicial del primer apellido),
  para el botón de cuenta; solo el de la propia sesión, sin registro.
- **Escritura** (solo superadmin, decisiones 135, 136 y 212): `PUT /api/plataforma/escuelas/{organizacionId}/marca`
  `{colorPrimario: '#RRGGBB' | null}` → `EscuelaPlataformaDTO` (que añade `colorPrimario`) más el tema calculado.
  Formato inválido, 400 con `{"mensaje"}`; escuela inexistente, 404. Se registra en la escuela DESTINO (como
  `LOGO_ESCUELA_CAMBIAR`), con `organizacionId` y `quitado` (booleano), sin el valor.
- **Pantalla del superadmin**: la de logos (`F/componentes/endpoints/plataforma-logos/`) pasa a «Logo y color»: campo
  de color por escuela con vista previa de la cabecera (logo, nombre y texto calculado), las razones de contraste y
  «Guardar color» / «Quitar color».
- **Moiskimdo `#1B2B2E`** (decisión 247): lo pone el superadmin desde esa pantalla tras desplegar 8b-T, sin migración
  de datos (decisión 257); queda en el registro y se deshace igual. Con el interruptor apagado nadie lee el color:
  Moiskimdo no nota nada.
- **Frontend**: `MarcaEscuelaPanelService` (en el chunk de `panel-nuevo`) pide la marca una vez por sesión y escribe
  los `--marca-*` en el elemento `.pn`; nunca en `:root`.

---

## 5. Pieza 8b-1: base del panel nuevo

Rama `feature/e8b-1`. Solo frontend, sin migración: atajo `desplegar`. **No cambia nada visible.**

**Ficheros:**
- Modificar: `src-frontend/src/styles.scss` (fuera `@use "@angular/material"`, `mat.core()`, `define-theme` y
  `all-component-themes`), `src-frontend/angular.json` (entrada de estilos `{ "input": "src/panel-nuevo.scss",
  "bundleName": "panel-nuevo", "inject": false }`; los specs nuevos en «include»), `src-frontend/tsconfig.spec.json`.
- Crear: `src-frontend/src/panel-nuevo.scss` (tokens de `DESIGN.md` bajo `:where(.pn)`, base de tipografía y foco,
  `@font-face`), `src-frontend/src/assets/fonts/` (Public Sans variable y Montserrat 600/700 en woff2, latín y latín
  extendido, con sus licencias OFL; Montserrat del panel con el nombre de familia «Montserrat Panel»).
- Crear: `F/panel-nuevo/formato/formato-es.ts` (`eur`, `numero`, `fecha`, `mes`, `plural`; `registerLocaleData(es,
  'es-ES')`), `F/panel-nuevo/formato/eur.pipe.ts`, `F/panel-nuevo/formato/plural.pipe.ts`,
  `F/panel-nuevo/estilos-panel.service.ts` (`cargar(): Promise<void>`: un solo `<link>`, resuelve en `load`, rechaza en
  `error`).

**Tareas TDD:**
- [ ] Specs que fallan: `formato-es.spec.ts` (`eur(1010)` = «1.010,00 €» con espacio duro; `eur(0)`; negativos;
  `numero(1044)` = «1.044»; `plural(1, 'clase', 'clases')` = «1 clase» y 7 → «7 clases»; mes «octubre de 2026»),
  `estilos-panel.service.spec.ts` (un `<link>` aunque se llame dos veces; resuelve con `load`; rechaza con `error` y deja
  reintentar). Verlos fallar.
- [ ] Implementar hasta verde.
- [ ] `npm ci` y `npm run build:production` en el worktree de la pieza (nunca con el `node_modules` de otra rama): el
  inicial baja al menos 54.000 bytes frente a `3b59a8cb` medido igual, `panel-nuevo.css` fuera del inicial, sin aviso.

**Riesgos:** algo que dependiera del tema de Material sin nombrarlo (búsqueda vacía hoy; la comprobación es visual);
estilos del CDK (`cdkTrapFocus` en Mi caja y Caja de sede, `LiveAnnouncer`): comprobar a mano que siguen igual; el CSP de
producción tiene `font-src 'self'` (`nginx/nginx-production.conf:145`): por eso las fuentes nuevas se sirven desde
`assets`.

**Verificación:** capturas antes/después a 375, 768 y 1280 de la portada, login, área de familias (`/userpage`) y panel
(`/adminpage`, `/alumnosListar`, `/tesoreriaCobros`, `/mi-caja`) idénticas (diff de píxeles o explicación de cada
diferencia); batería de Karma en verde.

**Notas para la PR:** cifras del presupuesto antes y después; `@angular/material` sigue en `package.json` (sin uso; se
quita en 8b-Z si se quiere); los tokens aún no los usa ninguna pantalla; `DESIGN.md` es la fuente y cualquier cambio de
token pasa antes por él.

**Notas de la base (ronda 2 de 8b-1):**
- `panel-nuevo.css` sale **sin hash** (`inject: false`) y nginx servía todo `*.css` con `expires 1y` + `immutable`: un cambio
  de tokens habría quedado cacheado un año. Los tres confs (`src-frontend/default.conf`, `nginx/nginx-production.conf` y
  `nginx/nginx-production-http-only.conf`) llevan un `location = /panel-nuevo.css` con `Cache-Control: no-cache` (se revalida
  con ETag y Last-Modified) y con `include /etc/nginx/cabeceras-seguridad.conf`, porque un `add_header` dentro de un location
  anula los del server. Un location nuevo con `add_header` debe incluirlo también.
- `styles.scss` declara `* { font-family: Montserrat }`, que corta la herencia desde `.pn`. `panel-nuevo.scss` lleva
  `:where(.pn) :where(*) { font-family: inherit }` (especificidad 0, hoja cargada después): los descendientes heredan la fuente
  del panel. Los iconos `.bi` no se ven afectados (su fuente va en `.bi::before`).
- Los importes y números agrupan los miles a mano (`agruparMiles`) porque `useGrouping: 'always'` necesita Firefox 116 y
  se admite Firefox 104. `--pn-sombra-barra` está ya en `DESIGN.md`.

## 6. Pieza 8b-I: interruptor por usuario

Rama `feature/e8b-i`. **Con migración V31_10**: atajo `desplegar_migrar`. Diseño en §3.

**Ficheros:**
- Crear: `M/V31_10__diseno_panel_usuario.sql` (solo amplía; cabecera que lo explica como V31_8), `J/entities/DisenoPanelUsuario.java`
  (`EntidadOrganizacion`), `J/repositories/DisenoPanelUsuarioRepository.java`, `J/services/DisenoPanelService.java`,
  `J/controllers/DisenoPanelController.java`, `J/dtos/request/DisenoPanelRequests.java`,
  `J/dtos/response/DisenoPanelDTO.java`.
- Modificar: `J/dtos/response/PermisosSesionDTO.java` y `J/services/PermisosSesionService.java` (`disenoNuevo`),
  `J/config/SecurityConfiguration.java` (`/api/panel/**`), `J/config/AuditoriaRequestFilter.java`
  (`EscrituraConRegistroPropio`), la matriz (5 líneas) y `docs/multi-escuela/matriz-endpoints.md`.
- Frontend: modificar `F/servicios/authentication/permisos-sesion.service.ts` (tipo) y quien decide pedir permisos
  (también con gestión); crear `F/panel-nuevo/diseno-nuevo.guard.ts`, `F/panel-nuevo/diseno-panel.service.ts`,
  `F/panel-nuevo/diseno-nuevo/diseno-nuevo.component.*` (pantalla del propietario, ruta `panel/diseno-nuevo`, carga
  diferida, capacidad `DAR_GESTION`), y la entrada «Diseño nuevo» en el menú de cuenta del propietario de la cabecera de
  hoy, visible solo para el propietario (decisión 256).

**Tareas TDD:**
- [ ] Tests que fallan: IT `DisenoPanelIntegrationTest` (dos escuelas `EscuelaConSedesDePrueba`, propietario, gestor,
  familia, profesor y una cuenta con gestión en las dos): `propietarioPermiteYQuitaAGestor`,
  `permitirLoDejaActivo`, `disenoNuevo_soloPropietarioAsigna`, `disenoNuevo_soloGestionDeLaEscuela`,
  `disenoNuevo_noCruzaEscuelas` (la cuenta de las dos escuelas: activo en A no lo activa en B),
  `miDiseno_sinPermiso_403`, `miDiseno_apagaYEnciendeSoloElPropio`, `porDefectoNuevo_sinFilaActivo`,
  `porDefectoNuevo_filaApagadaManda`, `registroSoloConIds` (una fila por escritura), `permisos_incluyeDisenoNuevo`
  (apagado por defecto). `DisenoPanelServiceTest` (regla efectiva). Migración (`-Dgroups=migracion`):
  `v31_10_soloAmplia` (tabla vacía, nada más cambia). Matriz y `AuditoriaRequestFilterRegistroPropioTest`.
- [ ] Specs que fallan: `diseno-nuevo.guard.spec.ts` (encendido → `true` tras cargar la hoja; apagado, error de
  permisos o error de la hoja → `false`), `rutas-panel-nuevo.spec.ts` (con el guard en `false` no se llama al
  `loadComponent` nuevo), `diseno-nuevo.component.spec.ts` (lista cruzada con miembros; un clic, una petición; los
  errores muestran el `mensaje`), `permisos-sesion.service.spec.ts` (gestión también pide).
- [ ] Implementar hasta verde; baterías completas; build sin aviso (el inicial, en la PR).

**Riesgos:** permisos (quién asigna a quién); consulta de membresía sin `organizacion_id`; parpadeo de la cabecera en
la primera carga; una petición más (`/api/auth/permisos`) por sesión de gestión.

**Verificación:** con datos ficticios, el propietario se activa a sí mismo y a un gestor; otro gestor sigue igual;
apagado, capturas idénticas a hoy.

**Notas para la PR:** V31_10 y por qué no V32.x (§1); el gestor no asigna (decisión 236: «el propietario y quien él
elija»); el control «Para todo el equipo» está construido pero oculto hasta R5 (decisión 258); con el
interruptor encendido, hasta 8b-N no cambia nada visible salvo la pantalla del propietario. Regla efectiva (ronda 2
de la revisión): con fila y (`permitido` o el valor por defecto NUEVO), su `activo`; si no, el valor por defecto;
`permitido` solo lo da el propietario y `mi-diseno` solo escribe `activo`. **Límite conocido:** la pantalla pide los
nombres con `GET /api/miembros?rol=PROPIETARIO|ADMINISTRADOR&size=100` (una página por rol); una escuela con más de 100
propietarios o gestores vería «Cuenta n.º …» en los que pasen de ahí.

## 7. Pieza 8b-T: tema por escuela

Rama `feature/e8b-t`. Sin migración (decisión 257): atajo `desplegar`. Diseño en §4.

**Ficheros:**
- Crear: `J/marca/ReglaContraste.java`, `J/marca/TemaEscuela.java`, `J/marca/MarcaPanelService.java`,
  `J/controllers/EscuelaMarcaController.java` (`/api/escuela/marca`, `/api/escuela/logo`),
  `J/dtos/response/MarcaPanelDTO.java`.
- Modificar: `J/plataforma/PlataformaController.java` (`PUT …/marca`), `J/plataforma/EscuelaPlataformaDTO.java`
  (`colorPrimario`), `J/plataforma/ErroresPlataformaRepository.java` (la lista de escuelas lee el color),
  `J/services/PermisosSesionService.java` y su DTO (`nombreVisible`), `J/config/AuditoriaRequestFilter.java`, matriz (5
  líneas) y `matriz-endpoints.md`.
- Frontend: `F/componentes/endpoints/plataforma-logos/` («Logo y color»), `F/servicios/plataforma/logo-escuela.service.ts`
  (`guardarColor`), `F/interfaces/escuela-logo.ts`; crear `F/panel-nuevo/marca-escuela-panel.service.ts`.

**Tareas TDD:**
- [ ] Tests que fallan: `ReglaContrasteTest` (tabla de §4 y rejilla de 4.096 colores: el texto y la tinta siempre ≥ 4,5:1),
  IT `MarcaEscuelaPanelIntegrationTest` (marca de la escuela activa; otra escuela no se ve; Moiskimdo sin logo →
  `LEGADO`; sin color → `#1A2230`; logo con `ETag`; `marca_soloPlataforma` en el PUT; formato inválido 400; registro en
  la escuela destino sin el valor; `permisos_nombreVisible`), matriz y registro propio.
- [ ] Specs que fallan: `marca-escuela-panel.service.spec.ts` (una petición por sesión; escribe las variables en `.pn`,
  nunca en `documentElement`), `plataforma-logos.component.spec.ts` (guardar y quitar color; el error en la fila).
- [ ] Implementar hasta verde; baterías completas; build sin aviso.

**Riesgos:** el algoritmo OKLCH (redondeos: la prueba de rejilla lo cubre); caché del logo tras cambiarlo
(`logoVersion` en la URL); que algo de hoy lea `color_primario` (no lo lee nadie).

**Verificación:** superadmin con datos ficticios pone `#1B2B2E` y un verde claro a otra escuela; vista previa con texto
blanco y oscuro; apagado, nada cambia.

**Notas para la PR:** el valor guardado y lo derivado; por qué no se registra el color; el de Moiskimdo lo pone el superadmin
desde «Logo y color» tras desplegar (decisión 257).

## 8. Pieza 8b-N: navegación del panel nuevo

Rama `feature/e8b-n`. Solo frontend, sin migración: atajo `desplegar`.

**Objetivo:** con el interruptor encendido y en rutas de gestión, la cabecera C (color y logo de la escuela, 6 entradas
con icono y nombre: Inicio, Alumnos, Grupos, Tesorería, Solicitudes, Escuela; 4 con desplegable; cuenta con iniciales y
nombre visible), la barra inferior por debajo de 1024 px (Inicio, Alumnos, Tesorería, Más) y el panel sin el pie de la
web pública (decisión 259): los enlaces legales y «Configurar cookies» pasan a «Más» (barra inferior) y al menú de
cuenta, y «Configurar cookies» abre el mismo aviso que hoy (`skeleton`, `showCookieBanner`). Las pantallas aún no
rediseñadas se ven dentro, tal cual.

**Ficheros:** crear `F/panel-nuevo/navegacion/cabecera-panel.component.*`, `barra-inferior.component.*`,
`menu-panel.ts` (las entradas de hoy, agrupadas; ninguna se pierde); modificar `F/componentes/vistas/layout/skeleton/`
(rama con `@defer`, clase `.pn`, pie fuera del panel nuevo).

**Tareas TDD:**
- [ ] Specs que fallan: `menu-panel.spec.ts` (todas las entradas de gestión de la cabecera de hoy están en el menú
  nuevo, con las mismas rutas y las mismas condiciones de rol y capacidad: lista generada de la cabecera de hoy),
  `cabecera-panel.component.spec.ts` (`aria-current`, menús con teclado y Esc, contador de solicitudes, «Volver al
  diseño anterior» llama a `PUT /api/panel/mi-diseno`), `skeleton.component.spec.ts` (apagado → cabecera y pie de hoy;
  encendido en ruta pública o de familia → los de hoy; encendido en gestión → la nueva y sin pie).
- [ ] Implementar; capturas a 375, 768 y 1280 de la cabecera, los desplegables y la barra inferior.

**Primera carga (ronda 2):** mientras no se sabe el interruptor, la cabecera de hoy; el hueco, solo con el recordatorio
de encendido de este navegador (§3 «Cabecera»). Specs: sin recordatorio → hoy; con recordatorio y sin respuesta →
hueco; con recordatorio y respuesta apagado → hoy y se borra; respuesta encendido → se guarda; error → ni se escribe
ni se borra; `localStorage` que lanza → no rompe; cerrar sesión → se borra; navegar a una ruta del panel → se vuelve a
leer.

**Riesgos:** 13 entradas quedan a dos clics (hoy 11 a uno): revisarlo con el gestor en R3; el contador de solicitudes y
el SSE de avisos deben seguir funcionando en la cabecera nueva.

**Notas para la PR:** mapa entrada de hoy → sitio nuevo; el nombre del botón de cuenta (`nombreVisible`); los enlaces
legales del pie, dónde quedan.

**Notas de 8b-T para esta pieza:**
- (a) `MarcaEscuelaPanelService` reinicia su caché solo al cambiar el email de la sesión; si llega un selector de
  escuela, debe reiniciarse también al cambiar de escuela.
- (b) Si falta el fichero del logo, `GET /api/escuela/marca` dice `SUBIDO` y `/api/escuela/logo` da 404: la cabecera
  necesita plan B, el nombre de la escuela sin logo.
- (c) El `<img>` del logo depende de la cookie de sesión (`jwt`): solo funciona con la API en el mismo sitio.
- (d) La paridad Java/TS de la regla de contraste (`ReglaContraste.java` y `regla-contraste.ts`) está medida con 0
  diferencias en la rejilla de 4.096 colores y en 645.278 colores de todo el espacio sRGB (uno de cada 26); si se toca
  una, se tocan las dos (los vectores compartidos de sus tests lo vigilan).

## 9. Pieza 8b-2: Inicio I1

Rama `feature/e8b-2`. Backend y frontend, sin migración: atajo `desplegar`.

**Objetivo:** I1 sobre la base C (decisión 252), sin más añadidos: título «Inicio» con la fecha y «Actualizar ahora»;
**Ocupación de la semana** (día × franja, inscritos frente al límite común de 36 por turno, rotulado; decisión 245);
**Hoy** (clases del día, la siguiente resaltada); **Pendiente de cobro** (todo lo vencido con desglose por tipo, mes y
sede y los cinco más antiguos con «Cobrar»; decisión 242); **Accesos** con cifra; «La escuela en cifras», eventos
activos y «Operaciones y accesos» debajo. Inventario: Anexo A, Inicio.

**Ficheros:**
- Backend: `GET /api/tesoreria/pendiente-vencido` en `J/controllers/TesoreriaController.java` (mismo `@PreAuthorize` que
  `/resumen` y el mismo alcance por sede), `J/services/TesoreriaService.java` (o un `PendienteVencidoService` aparte, sin
  tocar los métodos que cubre el dorado), `J/dtos/response/PendienteVencidoDTO.java` (`total`, `cargos`, `alumnos`,
  `porTipo[]`, `porMes[]`, `porSede[]`, `masAntiguos[5]`), matriz (2 líneas).
- Frontend: `F/panel-nuevo/inicio/` (componente, `ocupacion.ts` puro con su spec, bloques), ruta `adminpage` con
  `canMatch`.
- Datos que ya existen: `grupos/resumen`, `turnos/resumen`, `turnos/limite`, `alumnos/aptos`, `eventos/admin/todos`.

**Tareas TDD:**
- [x] Tests que fallan: IT `PendienteVencidoIntegrationTest` (`coincideConMovimientosNoPagadosVencidos`,
  `desglosesSumanElTotal`, `noCruzaEscuelas`, `cargoFuturoNoCuenta`, `masAntiguosOrdenados`,
  `responsableSede_denegadoHastaE9`), matriz.
- [x] Specs que fallan: `ocupacion.spec.ts` (franjas ordenadas, celdas sin clase, capacidad = turnos × límite, pasos
  0/25/50/75 %, el texto de cada celda), `inicio.component.spec.ts` (tabla equivalente de la ocupación; plurales;
  importes es-ES; «Cobrar» con la misma llamada que hoy).
- [x] Implementar; capturas apagado/encendido a 375, 768 y 1280 (y 1024).

**Regla de «vencido» (decisión 267, Europe/Madrid, bean `Clock` de `RelojConfig`):** pendiente = `pagado` FALSE o NULL y
`cancelado_en` NULL, de un alumno activo (como la tesorería), solo categorías `MENSUALIDAD` y `EXAMEN` de la tesorería.
Mensualidad: su periodo, como la tesorería, es el año y el mes del concepto y, el que falte, el de la asignación;
vencida si es anterior al mes en curso o si es
el mes en curso y hoy es día 6 o posterior (`PendienteVencidoService.DIA_LIMITE_MENSUALIDAD = 5`, de las normas de
Moiskimdo; ajuste por escuela en la E9). Derecho de examen: vencido si `convocatoria.fecha_convocatoria` <= hoy; sin
convocatoria, la fecha de asignación. El responsable de sede recibe 403 hasta la E9 (a diferencia de `/resumen`).

**Tests (lista detallada):**
- [x] IT `PendienteVencidoIntegrationTest` (MySQL, dos escuelas sintéticas): `pendienteVencido_coincideConMovimientosNoPagadosVencidos`
  (mismo universo y categorías que `GET /api/tesoreria/movimientos?pagado=false`), `pendienteVencido_desglosesSumanElTotal`
  (por tipo, mes y sede suman importe y cargos; meses ordenados), `pendienteVencido_noCruzaEscuelas`,
  `pendienteVencido_cargoFuturoNoCuenta` (meses posteriores y examen futuro), `pendienteVencido_masAntiguosOrdenados`
  (por la fecha en que vencieron), `pendienteVencido_mensualidadMesEnCurso_dia5_noCuenta`,
  `pendienteVencido_mensualidadMesEnCurso_dia6_cuenta`, `pendienteVencido_derechoExamenElDiaDeLaConvocatoria_cuenta` y
  `pendienteVencido_responsableSede_denegadoHastaE9` (GET y HEAD 403; familia 403; gestor 200).
- [x] `MatrizPermisosTest`: 2 líneas nuevas (GET y HEAD `/api/tesoreria/pendiente-vencido`), ninguna casilla cambia.
- [x] `ocupacion.spec.ts`: franjas ordenadas y en «HH:mm»; lunes a sábado y domingo solo con clase; celda sin clase;
  capacidad = turnos × límite; grupo de dos días en ambos; pasos 0/25/50/75 %; texto y descripción de cada celda;
  totales; sin turnos; `claveDia`; `clasesDeHoy` con la siguiente resaltada y día sin clases.
- [x] `inicio.component.spec.ts` (`HttpTestingController`): pide los datos de hoy y el pendiente; tabla equivalente de la
  ocupación con la nota del límite común y del grupo de dos días; plurales; «Hoy» con la siguiente; importes es-ES y
  desgloses; «Cobrar» con el mismo diálogo, el mismo `PUT /api/productos-alumno/{id}` y el mismo cuerpo (con método y
  sin él; cancelar no envía nada) y recarga del pendiente; nada vencido; accesos con cifra; la escuela en cifras.
- [x] `formato-es.spec.ts`: `fechaLarga`. `app.routes.spec.ts`: dos entradas `adminpage`, la nueva con
  `disenoNuevoGuard` y `roleGuard` delante de la de hoy.
- [x] El diálogo «Confirmar cobro» sale de `tesoreria-cobros/confirmar-cobro.ts`, compartido con la tesorería de hoy
  (sus specs de la decisión 148 siguen igual).
- [x] Ronda 2 de revisión: el IT suma un cargo con `pagado` NULL, uno de alumno por su inscripción (`alumno_id` NULL) y
  uno con año sin mes (enero de 2027: no vence); reloj como bean simulado con `@MockitoBean Clock`. Specs del Inicio:
  plurales en singular y con miles, límite en 0 o con error (la ocupación muestra su error y «Actualizar ahora» lo
  reintenta) y «Ir a Tesorería».

**Pendiente para 8b-3d:** la tesorería de hoy no admite «todos los meses» por parámetro (sin `mes` usa el mes en curso);
el enlace del bloque es «Ir a Tesorería» hasta que la Tesorería nueva pueda abrir lo vencido de todos los meses.

**Riesgos:** la ocupación se ve clara con 36 plazas (va rotulada; será real con el aforo por sede, E9); un grupo con
dos días cuenta sus alumnos en ambos (no hay inscritos por turno: rotularlo); el pendiente vencido suma todos los
meses, distinto del aviso de hoy (mes en curso): el título lo dice.

**Notas para la PR:** de dónde sale cada cifra; la diferencia entre «pendiente vencido» y el aviso de hoy; mapa de
colores de la ocupación validado (`dataviz`).

## 10. Pieza 8b-3a: Alumnos

Rama `feature/e8b-3a`. Backend pequeño y frontend, sin migración: atajo `desplegar`.

**Objetivo:** listado C (decisión 253): filas de dos líneas, 25 por página con paginación del servidor, cambio a
tarjetas, buscador y filtros en una barra, «Dar de alta» como único primario, «Informes» en un menú, pendiente con
«Cobrar» o «Al día», fila desplegable con el detalle por deporte y sus acciones; las tres herramientas del pie
(mensualidades y licencias, asistencia, mensualidades) siguen. Inventario: Anexo A, Alumnos.

**Ficheros:** backend `GET /api/tesoreria/pendientes-por-alumno?alumnoIds=…` (máximo 25 ids, solo de la escuela activa;
importe y número de cargos vencidos; el DTO de alumnos no cambia), matriz (2 líneas); frontend `F/panel-nuevo/alumnos/`,
ruta `alumnosListar` con `canMatch`.

**Tareas TDD:** IT `PendientesPorAlumnoIntegrationTest` (`soloEscuelaActiva`, `idsAjenosSeIgnoran`, `masDe25_400`,
`coincideConPendienteVencido`); specs del listado (25 por página, contador del total y no de la página, filtros a
la API, cambio filas/tarjetas recordado, «Cobrar» igual que hoy, teclado en la fila desplegable). Capturas.

**Tests (lista detallada):**
- [x] IT `PendientesPorAlumnoIntegrationTest` (MySQL, dos escuelas sintéticas, `Clock` simulado el 06/03/2026):
  `pendientesPorAlumno_soloEscuelaActiva` (importe, cargos y detalle con `vencidoDesde`; el cargo de una inscripción se
  atribuye al alumno; la otra escuela no ve nada), `pendientesPorAlumno_idsAjenosSeIgnoran`,
  `pendientesPorAlumno_masDe25_400` (26 → 400, 25 → 200, sin ids → 400),
  `pendientesPorAlumno_coincideConPendienteVencido` (la suma por alumno y los cargos son los de `/pendiente-vencido`) y
  `pendientesPorAlumno_responsableSede_403` (GET y HEAD 403; familia 403; gestor 200). La regla sale de
  `PendienteVencidoService` (la consulta, `PendienteVencidoRepository.CANDIDATOS`, compartida); sus 9 casos siguen verdes.
- [x] `MatrizPermisosTest`: 2 líneas nuevas (GET y HEAD `/api/tesoreria/pendientes-por-alumno`), ninguna casilla cambia.
- [x] `alumno-vista.spec.ts`: iniciales, antigüedad y tiempo con el grado con plurales (singular y plural), principal
  primero como hoy y estado de la licencia como hoy.
- [x] `alumnos.component.spec.ts` (`HttpTestingController`): 25 por página y una sola llamada de pendientes con los ids
  de la página; contador del total del servidor y rango «1–25 de N» con miles; plurales en singular y en plural; filtros
  a la API (`nombre` con espera, `deporte`, `incluirInactivos`, `aptoParaExamen`, `page`); «Filas | Tarjetas» recordado
  en localStorage y sin él; «Cobrar» con un cargo (mismo diálogo, mismo `PUT /api/productos-alumno/{id}` y cuerpo, con
  método, sin método y cancelado) y con varios (panel con cada cargo, foco en el primero, Esc lo cierra y devuelve el
  foco); refresco del pendiente de ese alumno; fila desplegable (botón nativo con `aria-expanded`/`aria-controls`,
  detalle por deporte y acciones); «Marcar principal» con la ruta de hoy; menú «Informes» con teclado, el diálogo de
  tipos de hoy y las tres herramientas del pie; error con «Reintentar».
- [x] `app.routes.spec.ts`: dos entradas `alumnosListar`, la nueva con `disenoNuevoGuard` y la misma data, guards y
  resolve que la de hoy; apagado, no se llama al `loadComponent` nuevo.
- [x] Las herramientas del pie (`HerramientasAlumnosComponent`), el diálogo «Generar informe» (`informes-alumnos.ts`) y
  «Dar de baja» / «Dar de alta» (`bajas-altas-alumno.ts`) salen del listado de hoy sin cambios para que el nuevo los use
  sin copiarlos; `informes-preview.spec.ts` sigue igual salvo que asistencia y listado de mensualidades se prueban en
  el componente del pie.
- [x] Capturas apagado (antes y después idénticas en bytes, también con el pie desplegado) y encendido (normal, fila
  desplegada, «Cobrar» con varios cargos, tarjetas e «Informes») a 375, 768, 1024 y 1280.

**Riesgos:** 800+ alumnos: búsqueda en el servidor; editar, vista de familia y dar de baja quedan a un clic.

**Notas para la PR:** tabla del Anexo A marcada; el contador cuenta el total (hoy cuenta la página).

## 11. Pieza 8b-3b: Ficha del alumno F1 «Carnet»

Rama `feature/e8b-3b`. Solo frontend, sin migración: atajo `desplegar`. Tier careful: la pantalla más grande (hoy
203 kB de chunk, 9.502 px a 375) y con más acciones.

**Objetivo:** carnet arriba (foto 240 × 300 en escritorio y 184 × 230 en móvil, nunca menor que hoy; nombre grande,
expediente, edad, alta y estado; franja con el color del cinturón de cada disciplina con `cinto-borde`, que **corrige
el cinturón blanco poco visible**; pendiente con «Cobrar», grado y licencia sin bajar; acciones del alumno al pie),
y debajo pestañas Deportes, Pagos, Datos y contacto, Documentos y notas (decisión 254). Diálogos de hoy sin cambios.
Inventario: Anexo A, Ficha.

**Ficheros:** `F/panel-nuevo/ficha/` (carnet, pestañas, una sub-vista por pestaña que reutiliza los servicios y
formularios de `editar-alumno` sin duplicar su lógica), ruta `alumnosEditar/:id` con `canMatch`.

**Tareas TDD:** specs del carnet (franja con borde en cinturones claros: contraste del borde ≥ 3:1; pendiente y
«Cobrar»; licencia), de las pestañas (`role="tablist"`, flechas, pestaña en la URL para volver a ella), y de cada acción
del inventario contra el mismo endpoint que hoy (agregar deporte, principal, baja/alta, grado, documentos, guardar y
cancelar). Capturas con «Pagos a la vista».

**Tests (lista detallada):**
- [x] Reutilización sin copiar: `FichaAlumnoComponent` **hereda** de `EditarAlumnoComponent` (mismas cargas, guardados,
  validaciones, diálogos SweetAlert y navegación entre alumnos) y solo cambia la plantilla; `aplicarAlumnoCargado` pasa
  de `private` a `protected` (el JS emitido no cambia). Los diálogos en línea de hoy (convocatorias, quitar de
  convocatoria, WhatsApp, agregar deporte, actualizar grado) salen a `DialogosAlumnoComponent` y la foto ampliada a
  `FotoAmpliadaComponent`, con su CSS movido tal cual; la ficha de hoy los usa (`[ficha]="this"`, anfitrión con
  `display: contents`). «Cobrar» del listado nuevo sale a `panel-nuevo/cobro/cobro-cargo.ts` (`cobrarCargo`,
  `teclaPanelCargos`) y el cinturón a `fondoCinturon` (`alumno-vista.ts`); `alumnos.component.spec.ts` sin cambios y
  en verde.
- [x] `dialogos-alumno.component.spec.ts`: los cinco diálogos con sus ids, cerrados y sin pintar sin alumno; sus
  botones llaman a los métodos de la ficha; la foto ampliada es el `<dialog id="imageModal">` de hoy.
- [x] `ficha-vista.spec.ts`: contraste WCAG (21:1, simétrico); `cinto-borde` ≥ 3:1 frente a superficie (3,87),
  superficie-2 (3,71) y fondo (3,55), y el blanco y el amarillo sin borde por debajo de 3:1; resumen del pendiente
  («3 cargos, el más antiguo vencido desde 06/09/2026», «1 cargo, vencido desde…», miles); línea del grado («desde
  hoy»); `fechaParaCampo` (ISO, milisegundos de la API, `Date`, vacío).
- [x] `ficha-alumno.component.spec.ts` (`HttpTestingController`, `RouterTestingHarness`), carnet: nombre en dos
  líneas como `h1`, expediente, edad con plural, «Alumno desde» (sin alta propia, la más antigua de sus disciplinas) y
  estado; una línea por disciplina activa con cinturón, «principal», grado y tiempo; franja con un tramo por disciplina
  con grado y cada tramo y muestra con borde `cinto-borde` ≥ 3:1 frente al carnet (estilos calculados); pendiente con
  importe es-ES y «Cobrar»: varios cargos → panel modal (foco en el primero, Esc lo cierra y devuelve el foco), cada
  «Cobrar» con «Confirmar cobro» y el `PUT /productos-alumno/{id}` de la tesorería (`{pagado, metodoPago}`) y relectura
  del pendiente; un cargo → el diálogo directo, cancelado no envía, sin método no envía `metodoPago`; «Al día» y sin
  insignia con 0; licencia de la principal con su pastilla; foto 240 × 300 que se amplía con el diálogo de hoy,
  «Cambiar foto» y «Quitar».
- [x] Barra: «Alumnos» enlaza al listado, «1 de N» con miles, anterior y siguiente, «Incluir inactivos» pide los ids
  con inactivos.
- [x] Pestañas: `tablist` con cuatro `tab` (`aria-selected`, `aria-controls`, `tabindex` itinerante), insignia de
  Pagos con su `aria-label`; flechas, Inicio y Fin mueven pestaña y foco; `?pestana=` en la dirección, se vuelve a
  ella y se conserva al pasar al alumno siguiente; una desconocida abre Deportes.
- [x] Cada acción, **comparada con la ficha de hoy** (mismas escrituras —método, URL y cuerpo— y mismos diálogos
  SweetAlert, con las mismas respuestas del usuario), y con su endpoint: agregar deporte (diálogo de hoy), marcar
  principal, desactivar, activar, eliminar deporte, dar de baja (cabecera de Deportes y «⋯»), eliminar alumno (dos
  confirmaciones), dar de alta, actualizar grado (diálogo de hoy), apto para examen, agregar a convocatoria, pasar por
  recompensa, añadir derecho de examen, fecha de grado, tarifa, licencia, alta (con «Aplicar cambios»), competidor,
  cancelar sin enviar, quitar y añadir grupo y turno, quitar y cambiar foto, subir y eliminar documento, descargar, ver
  y «Ver 1 más», WhatsApp (carnet y Documentos), guardar observaciones, «Editar datos» + guardar (mismo `PUT
  /alumnos/{id}` con `FormData`), errores y cancelar, «Cobrar» de la tabla de Pagos (diálogo y `PUT` de la ficha de
  hoy), paginación de pagos, gestionar productos, reservar plaza y «Vista de familia».
- [x] `app.routes.spec.ts`: dos entradas `alumnosEditar/:id`, la nueva con `disenoNuevoGuard` y la misma data, guards y
  resolve que la de hoy; apagado, no se llama al `loadComponent` nuevo; `alumnosEditar` sin id sigue solo con la de hoy.
- [x] Capturas apagado antes (`bc910a12`) y después idénticas en bytes a 375, 768, 1024 y 1280, también con la foto
  ampliada, «Agregar deporte», «Actualizar grado» y WhatsApp abiertos; encendido con las cuatro pestañas, el cinturón
  blanco, el panel de «Cobrar» de varios cargos, «Editar datos», «⋯» y los diálogos de hoy.
- [x] Ronda 2 (revisión): al pasar de 7 a 9 sin respuesta del pendiente de 9 no quedan importe, «Cobrar», panel ni
  insignia del 7, y una respuesta que no es del alumno que se ve se descarta (en rojo sin la corrección); el listado
  nuevo no tenía esa carrera (oculta las filas al cambiar de página o de filtro y cancela los pendientes en vuelo):
  specs que lo fijan. Buscador de hoy (`appSearchableSelect`) en tarifa, rol familiar, categoría, añadir turno y
  añadir grupo, con su lista flotante en `.pn` (`searchDropdownClass`, sin efecto en las pantallas de hoy) y spec de
  que filtra. El listado escribe y lee en la dirección `nombre`, `deporte`, `inactivos`, `aptoParaExamen`, `pagina` y
  `vista` (sin repetir cargas); «← Alumnos» vuelve con ellos si se vino del listado y sin ellos si no. Cada
  `aria-controls` de las pestañas apunta a un panel que existe; la insignia dice «cargos vencidos». Grado del carnet
  con mayúscula inicial con `gradoFila`/`gradoParaFrase` de `alumno-vista.ts`, compartidos con el listado. Capturas
  apagado de la ficha y del listado otra vez idénticas en bytes.

**Riesgos:** perder una acción poco usada (el inventario manda); el formulario de edición; la salud (si aparece, con las
mismas reglas de lectura y registro de hoy). La ficha de hoy muestra un dato de salud, «Discapacidad» (Sí/No, editable con
«Editar»): la nueva lo muestra y lo edita en «Datos y contacto» con el mismo DTO y el mismo guardado (`PUT /alumnos/{id}`),
así que le aplica las mismas reglas de lectura y de registro que hoy, sin registro aparte.

**Notas para la PR:** tabla del Anexo A marcada; qué quedó a un clic; cinturones medidos.

## 12. Pieza 8b-3c: Grupos

Rama `feature/e8b-3c`. Solo frontend, sin migración: atajo `desplegar`.

**Objetivo:** estructura de C (alumnos por tipo de grupo, filtro por disciplina con su número, grupos por disciplina
con su total) con cada grupo como tarjeta de A (alumnos, edad y horario a la vista; «Alumnos», «Editar», «Rango de
edad» y eliminar al pie; turnos desplegables con editar, quitar y «Añadir turno»); turnos sin grupo al final. Iconos de
turno de 32 px como mínimo (hoy 12 × 13). Inventario: Anexo A, Grupos.

**Ficheros:** `F/panel-nuevo/grupos/`, ruta `gruposListar` con `canMatch`.

**Tareas TDD:** specs (filtro, despliegue con `aria-expanded`, objetivos ≥ 24 px, cada acción contra el endpoint de hoy).
Capturas (16 tarjetas en móvil, con y sin filtro).

**Notas para la PR:** tabla del Anexo A marcada.

**Implementación (rama `feature/e8b-3c`, desde `develop` en `ea233fab`):**
- Nuevos en `F/panel-nuevo/grupos/`: `grupos.component.*` (`GruposComponent`, `app-grupos`), `grupos-vista.ts`
  (`rangoEdad`, `resumenHorario`, `textoTurno`, `seccionesPorDisciplina`) y `alumnos-tipo-panel.component.*`
  (`pn-alumnos-tipo`, hereda de `GrupoAlumnosModalComponent`: buscador con 300 ms, «solo activos», 8 por página y paso a
  la ficha; solo cambian plantilla, carga y cierre). `LecturasPanelService`: `resumenGrupos`, `resumenTurnos`,
  `conteoAlumnosPorGrupo` y `alumnosPorTipo`, la misma petición que hoy con `ERROR_HANDLED_LOCALLY`. Ruta `gruposListar`
  con `canMatch: [disenoNuevoGuard]` delante de la de hoy.
- Escrituras con los métodos de hoy de `EndpointsService` (mismo método, ruta y cuerpo): añadir turno (`POST
  /grupos/{id}/turnos/{turnoId}`, cuerpo nulo), quitar turno (`DELETE`), rango de edad (`PUT /grupos/{id}` con `{id,
  nombre, deporte, rangoEdadMin, rangoEdadMax}`) y eliminar (`DELETE /grupos/{id}` y relectura de grupos y turnos).
  Confirmaciones con `ConfirmacionService` (quitar y eliminar destructivas, textos de hoy); rango con `DialogoComponent`
  (dos campos numéricos y la validación de hoy en línea); avisos con `AvisosService`; sin SweetAlert2 ni cargador global.
- [x] Specs en rojo antes del código (`Can't resolve './grupos.component'`, `'./grupos-vista'`; `resumenGrupos` no existe
  en `LecturasPanelService`), luego verdes: `grupos-vista.spec.ts`, `grupos.component.spec.ts` (filtro con números,
  selección y teclado; `aria-expanded`/`aria-controls`; objetivos ≥ 24 px e iconos de turno ≥ 32 px; cada acción con
  método, ruta y cuerpo, y nada al cancelar; panel de alumnos por tipo; esqueleto, error por lectura y vacío),
  `lecturas-panel.service.spec.ts` y `app.routes.spec.ts` (dos entradas; apagado no descarga el chunk).
- **Turnos sin grupo:** la pantalla de hoy filtra `!grupoId && !grupoNombre`, pero la API manda «Sin grupo» en
  `grupoNombre`, así que hoy nunca ofrece ninguno (lo fija `listado-grupos.resumen.spec.ts`). La nueva filtra solo por
  `!grupoId`: «Añadir turno» y el bloque del final funcionan. Al quitar un turno pasa al bloque sin recargar (hoy solo
  desaparecía de la tarjeta). Mensajes de validación del rango con tildes («La edad mínima…»; hoy sin ellas).
- **Notas para la PR:** bundle inicial 1.455.687 bytes (+158 frente a 1.455.529, la entrada de la ruta); chunk
  `grupos-component` 52,35 kB; karma 1804 SUCCESS; capturas apagado antes (`ea233fab`) y después idénticas en bytes, 9/9
  (carga, alumnos por tipo y rango a 375, 768 y 1280); encendido a 375, 768 y 1280 (16 tarjetas, con filtro, una
  desplegada, alumnos por tipo y rango de edad); objetivos medidos: 44 px mínimo a 375, sin desplazamiento horizontal.

## 13. Pieza 8b-3d: Tesorería

Rama `feature/e8b-3d`. Solo frontend, sin migración: atajo `desplegar`. Tier careful: dinero.

**Objetivo:** Tesorería C (decisión 246, el nombre se queda): filtros en una línea, resumen con barra cobrado/pendiente
y pendiente por sede, pestañas Pendientes | Cobrados | Todos en lugar del filtro «Estado», filas de dos líneas con
«Cobrar», notas, cambiar fecha de pago y revertir en «⋯», exportar (informe, PDF, CSV, certificados) en un menú;
importes es-ES. Inventario: Anexo A, Tesorería.

**Ficheros:** `F/panel-nuevo/tesoreria/`, ruta `tesoreriaCobros` con `canMatch`.

**Tareas TDD:** specs (pestaña → `pagado` en la API; cobrar, cambiar fecha y revertir o pedir revertir con el mismo
endpoint, cuerpo y confirmación que hoy; «Mostrando X de Y»; exportaciones con los mismos parámetros que hoy). Capturas.

**Riesgos:** revertir y pedir revertir (solicitudes de la 6c) según el rol; el dorado de Moiskimdo no cambia porque no
hay backend nuevo.

**Notas para la PR:** tabla del Anexo A marcada; mismas peticiones que la pantalla de hoy (lista).

**Implementación (rama `feature/e8b-3d`, desde `develop` en `4442751b`):**
- Nuevos en `F/panel-nuevo/tesoreria/`: `tesoreria.component.*` (`TesoreriaComponent`, `app-tesoreria`) y `tesoreria-vista.ts`
  (`PESTANAS`, `pagadoDeEstado`, `nombreExportacion`, `etiquetaPeriodo`, `fechaHora`, `fechaPagoParaCampo`, `barraCobro`).
  `LecturasPanelService`: `tesoreriaResumen`, `tesoreriaMovimientos`, `tesoreriaAnios` y `alumnosSinPaginar` (la misma
  petición que hoy con `ERROR_HANDLED_LOCALLY`) y `paramsTesoreria` (copia de `construirParamsTesoreria`, privado en
  `EndpointsService`, que no se toca). `DialogoComponent`: opción `focoEnCancelar` (destructivo con campos: el foco va a
  «Cancelar» y no al campo; sin ella, igual que antes). Ruta `tesoreriaCobros` con `canMatch: [disenoNuevoGuard]` delante
  de la de hoy.
- **Mismas peticiones** que `TesoreriaCobrosComponent`: `GET /tesoreria/resumen`, `/movimientos` (25 por página),
  `/anios-disponibles` y `/alumnos?incluirInactivos=false` con los mismos parámetros; `PUT /productos-alumno/{id}` con
  `{pagado: true[, metodoPago]}` («Cobrar», `CobroCargoService`), `{notas}`, `{fechaPago}` (mediodía del día elegido) y
  `{pagado: false, motivoCambio}`; `POST /solicitudes` `{tipo: 'REVERTIR_COBRO', productoAlumnoId, motivo}`;
  `GET /tesoreria/export/pdf|csv` con el estado de la pestaña y los nombres de hoy
  (`tesoreria_<año>_<mes>_<deporte>_<estado>_<alumnos>.csv|pdf`); `POST /tesoreria/certificados-cobros` `{alumnoIds, ano}`
  con `certificado_cobros_<año>.pdf` o `certificados_cobros_<año>.zip`. Textos de confirmación, validaciones y avisos de
  hoy; el 403 dice «Solo administradores pueden revertir cobros».
- Diálogos con `ConfirmacionService` («Editar notas», «Editar fecha de pago», «Revertir cobro» destructivo con el foco en
  «Cancelar», «Pedir al propietario que revierta este cobro» con datos, nota y motivo obligatorio en línea); certificados
  en `pn-panel-lateral` (bloqueado mientras genera); «Ver PDF» en `pn-visor-pdf` con `InformePdfPanelService`; avisos con
  `AvisosService`; esqueletos a los 300 ms, `pn-bloque-error` con «Reintentar» y `pn-bloque-vacio`. Sin SweetAlert2 ni
  cargador global. Menús «Exportar» y «⋯» con el patrón de botón de menú (`teclado-menu.ts`); pestañas `tablist` con
  flechas, Inicio y Fin. Barra apilada con `serie-ocupacion` (cobrado) y `atencion` (pendiente), validada con el script de
  dataviz (ΔE CVD 22,3; ≥ 3:1), texto equivalente en `aria-label` y leyenda con nombre, importe y %.
- **Pendiente por sede:** no; `TesoreriaResumen` no trae desglose por sede y la pieza no toca el backend.
- **Cambios de texto a propósito:** «Pagados» pasa a la pestaña «Cobrados» y «Pedir al propietario» a «Pedir revertir»
  (el diseño); «Movimientos de cobro» en minúscula; deporte y periodo con «·»; plurales calculados («1 cobro
  pendiente»); importes es-ES con €; «Debes seleccionar un año» con eñe (hoy «ano»; el botón está deshabilitado en ese
  caso). Tras una escritura se relee sin esqueleto (hoy, con el esqueleto de toda la pantalla).
- [x] Specs en rojo antes del código (`Can't resolve './tesoreria.component'`, `'./tesoreria-vista'`; `tesoreriaResumen` no
  existe en `LecturasPanelService`; `focoEnCancelar` no existe), luego verdes: `tesoreria-vista.spec.ts`,
  `tesoreria.component.spec.ts` (pestaña → `pagado`; filtros, «Aplicar», «Restablecer» y parámetros de la dirección;
  búsqueda a los 300 ms; resumen, plurales y barra; filas, pastillas, «Mostrando X de Y» y paginación; cobrar, notas,
  fecha, revertir y pedir revertir con método, ruta y cuerpo, nada al cancelar y sin doble envío; menús con teclado;
  exportaciones y certificados con parámetros y nombres; esqueleto, error y vacío), `lecturas-panel.service.spec.ts`,
  `confirmacion.service.spec.ts` y `app.routes.spec.ts` (dos entradas; apagado no descarga el chunk).
- **Notas para la PR:** bundle inicial 1.455.851 bytes (+164 frente a 1.455.687, la entrada de la ruta); chunk
  `tesoreria-component` 67,15 kB; karma 1862 SUCCESS; capturas apagado antes (`4442751b`) y después idénticas en bytes,
  15/15 (carga, cobrados, certificados, revertir y cobrar a 375, 768 y 1280); encendido a 375, 768 y 1280 (Pendientes,
  Cobrados, «⋯» abierto, «Exportar» abierto, revertir, cambiar fecha, certificados y vacío); objetivos de 44 px a 375
  salvo el enlace del alumno (24, como en Alumnos), sin desplazamiento horizontal.

## 14. Pieza 8b-3e: Convocatorias

Rama `feature/e8b-3e`. Solo frontend, sin migración: atajo `desplegar`.

**Objetivo:** Convocatorias C: un bloque por convocatoria con sus cifras, pestañas por disciplina con su número, alumnos
en lista con conmutador Pagado | Pendiente, PDF en un menú, «Actualizar grados de todos» como primario al pie.
Inventario: Anexo A, Convocatorias.

**Ficheros:** `F/panel-nuevo/convocatorias/`, ruta `convocatoriasListar` con `canMatch`.

**Tareas TDD:** specs (conmutador con la misma llamada que hoy; «Actualizar grados» con confirmación; foco visible en la
cabecera de cada convocatoria, que hoy no lo tiene; el diálogo «Selecciona el tipo de producto» con elección explícita
y un cancelar que no decide nada). Capturas.

**Notas para la PR:** tabla del Anexo A marcada; el fallo de «Por Antigüedad» (`listado-convocatorias.component.ts:516-523`)
ya se corrigió en la pantalla de hoy (hotfix 2); la nueva conserva ese comportamiento corregido.

**Implementación (rama `feature/e8b-3e`, desde `develop` en `2672b8fa`):**
- Nuevos en `F/panel-nuevo/convocatorias/`: `convocatorias.component.*` (`ConvocatoriasComponent`, `app-convocatorias`) y
  `convocatorias-vista.ts` (`cifrasDe`, `textoCifra`, `nombreInforme`, `paginar`, `totalPaginas`, `paginasVisibles`,
  `iniciales`, `diaParaCampo`, `fechaDeCampo`, `cuantiaDeCampo`, `cuantiaParaCampo`, `pestanasDisciplina`).
  `LecturasPanelService`: `convocatorias`, `alumnosDeConvocatoria` y `aptosParaConvocatoria` (la misma petición que
  `obtenerConvocatorias`, `obtenerAlumnosDeConvocatoria` y `obtenerAlumnosElegiblesParaConvocatoria`, con
  `ERROR_HANDLED_LOCALLY`); «Cargar más alumnos» usa `alumnos` (la de `obtenerAlumnos`). Ruta `convocatoriasListar` con
  `canMatch: [disenoNuevoGuard]` delante de la de hoy (misma data, `roleGuard` y resolve).
- **Mismas escrituras** que `ListadoConvocatoriasComponent`, con los métodos de hoy de `EndpointsService`: conmutador y
  cuantía, `PUT /convocatorias/alumno/{id}` con `{cuantiaExamen, pagado}` (sin `metodoPago`, como hoy); agregar,
  `POST /alumnos/{convocatoria}/alumno/{alumno}` con `{porRecompensa, rojoBordado}`; quitar, `DELETE` a esa ruta;
  «Actualizar grados de todos», `PUT /convocatorias/{id}/actualizar-grados` con `{}`; crear, `POST /convocatorias` con
  `{fechaConvocatoria: medianoche local del día, deporte}`; eliminar, `DELETE /convocatorias/{id}`; PDF,
  `GET /convocatorias/{id}/informe-pdf` con `informe_convocatoria_<deporte>_<dd_mm_aaaa>.pdf` y el título de hoy. Sin
  comprobaciones de rol en la pantalla, como hoy (la ruta sigue con `ROUTE_ROLE_SETS.management`). Página y disciplina
  se recuerdan en `sessionStorage` con la clave de hoy (`listadoConvocatoriasEstado`).
- Diálogos con `ConfirmacionService`: «Quitar alumno» y «Eliminar convocatoria» destructivos con `focoEnCancelar`;
  «Actualizar grados» con `ejecutar` (ocupado mientras va, sin doble envío); «Crear convocatoria» con disciplina y fecha
  (la validación de hoy en línea) y su confirmación; «Selecciona el tipo de producto» y «Selecciona el grado rojo» con
  pastillas. Avisos con `AvisosService`; «Ver PDF» en `pn-visor-pdf` con `InformePdfPanelService`; menú «PDF» con el
  patrón de botón de menú (`teclado-menu.ts`); pestañas `tablist` con flechas, Inicio y Fin; esqueletos a los 300 ms,
  `pn-bloque-error` con «Reintentar» (lista, cifras de un bloque y alumnos del bloque) y `pn-bloque-vacio`. Sin
  SweetAlert2 ni cargador global. Cabecera de cada convocatoria: un `<button>` con `aria-expanded`/`aria-controls` y
  anillo de foco de 3 px (`acento-borde`), que hoy no tiene. Conmutador: dos botones con `aria-pressed` en un `group`.
- **«Selecciona el tipo de producto»:** hoy, desde el hotfix 2 (decisión 235), cerrar ya no asigna nada: «Por
  Recompensa» y «Por Antigüedad» son botones propios y Esc, el clic fuera, la X y «Cancelar» no llaman al backend (el
  fallo de origen era «Por Antigüedad» como botón de cancelar). La nueva conserva eso y además pide la elección
  explícita: dos pastillas sin ninguna elegida al abrir, «Agregar» deshabilitado hasta elegir y el foco en «Cancelar»;
  Enter ya no puede decidir por defecto.
- **Lecturas de más (mismo endpoint y parámetros):** los números de las pestañas piden `GET /convocatorias?deporte=` de
  cada disciplina con grados a la vez (hoy, solo la elegida; cambiar de pestaña ya no vuelve a pedir la lista) y las
  cifras de cada bloque piden `GET /convocatorias/{id}/alumnos` de los bloques a la vista, 5 como mucho (hoy, solo al
  abrir). Una convocatoria recién creada empieza en cero sin pedir nada.
- **Cambios a propósito:** título «Convocatorias» con subtítulo (hoy «Gestión de Convocatorias»); tras cualquier
  escritura se relee la lista con el bloque abierto (hoy `seleccionarConvocatoria` alternaba y el bloque se cerraba);
  «Actualizar grados de todos» confirma siempre («Se actualizará el grado de N alumnos inscritos.» si todos han pagado;
  hoy iba directo); los diálogos llevan título propio («Quitar alumno», «Eliminar convocatoria», «Actualizar grados»,
  «Crear convocatoria»; hoy «¿Estás seguro?») con la frase de hoy; la fecha al eliminar en es-ES («14/11/2026»; hoy
  `AAAA-MM-DD`); la disciplina por su nombre en «Crear» y su confirmación (hoy el código); la fecha por defecto es el día
  local (hoy `toISOString`, que entre las 00:00 y las 02:00 daba el día anterior); la convocatoria creada va a la lista
  de su disciplina (hoy a la que estuviera a la vista) y se recalculan las páginas al eliminar; «Selecciona cinturón
  rojo» pasa a «Selecciona el grado rojo» («Selecciona el tipo de grado rojo.»; mismas opciones y «Rojo (sin bordar)»
  elegida); «Por recompensa» y «Por antigüedad» en minúscula; cuantía en un campo decimal es-ES («25,00 €», acepta coma;
  lo que no es un número no se envía y el campo vuelve atrás); «No se pudieron obtener las convocatorias» (hoy «No se
  pudo»); plurales calculados («1 inscrito», «1 pendiente»). «Cargar más alumnos» hace lo de hoy: pide la página
  siguiente del listado (10), que la pantalla no usa, y vuelve a leer los aptos.
- [x] Specs en rojo antes del código (`Can't resolve './convocatorias.component'` y `'./convocatorias-vista'`;
  `convocatorias`, `alumnosDeConvocatoria` y `aptosParaConvocatoria` no existen en `LecturasPanelService`), luego verdes:
  `convocatorias-vista.spec.ts` (10), `convocatorias.component.spec.ts` (40: carga y parámetros; cabecera; pestañas con
  número, cambio sin relectura y teclado; bloques, cifras y su error; foco visible y `aria-expanded`; «Ver alumnos»;
  lista con grados y color; conmutador, cuantía, quitar, agregar, tipo de producto y grado rojo con método, ruta y
  cuerpo, nada al cancelar y sin doble envío; «Cargar más alumnos»; menú «PDF» con teclado, nombre y visor;
  «Actualizar grados»; crear y eliminar; paginación y `sessionStorage`; esqueleto, error y vacíos),
  `lecturas-panel.service.spec.ts` y `app.routes.spec.ts` (dos entradas; apagado no descarga el chunk).
- **Notas para la PR:** bundle inicial 1.456.023 bytes (+172 frente a 1.455.851, la entrada de la ruta; sin
  cambio tras la revisión); chunk `convocatorias-component` 57,71 kB (12,03 kB por la red; 56,43 kB antes de la revisión);
  karma 1930 SUCCESS; capturas apagado antes (`2672b8fa`) y
  después idénticas en bytes, 12/12 (carga, abierta, crear y eliminar a 375, 768 y 1280); encendido a 375, 768 y 1280
  (lista, alumnos abiertos, menú «PDF», actualizar grados, tipo de producto, quitar y vacío); objetivos de 44 px como
  mínimo a 375, sin desplazamiento horizontal en ningún ancho; `impeccable detect` sin hallazgos.
- **Correcciones de la revisión:** tras guardar, el inscrito pasa a llevar la cuantía y el pagado enviados, de modo que
  el siguiente PUT no revierte lo recién guardado mientras llega la relectura (cuatro pruebas); la lectura de cada
  disciplina tiene su propio `catchError` (esa pestaña va sin número; solo si fallan todas, bloque de error); una
  respuesta tardía de las cifras no pisa las de la relectura del bloque abierto (generación por bloque); fuera el
  getter `cifrasAbierta`; el `|` de «Pagado | Pendiente» escapado en el Anexo A.

## 15. Pieza 8b-B3: boceto de la ronda 3 (solo diseño)

Rama `feature/e8b-b3`. Sin código de la aplicación, sin migración y sin despliegue. Decisiones 228 (se diseña antes de
construir) y 259 (las siete pantallas entran en la E8b).

**Estado: hecha.** Boceto publicado en https://claude.ai/artifact/5ynw1nWRYKQDJ8LdVqfq1w; el propietario eligió una variante por pantalla (decisión 261, RB3 hecha) y
los inventarios elegidos están copiados al Anexo A.

**Objetivo:** las siete pantallas de gestión que no tenían boceto, cada una **hoy frente a una o dos propuestas en el
mundo de C** (`DESIGN.md`: cabecera de la escuela, filas de dos líneas, pestañas, un primario por pantalla, acciones
raras en «⋯»), a 375, 768 y 1280 px y con datos ficticios:
- Eventos: listado con orden y vista previa, crear y editar.
- Productos: listado con filtros, crear y editar.
- Preinscripciones: bandeja, filtros, ficha con historial, firma, lista de espera, asignación, paso a alumno,
  plantillas PDF y normas.
- Solicitudes: bandeja de la 6c con su recorrido.
- Caja de sede: resumen, efectivo en mano de los profesores, entregas con correcciones y movimientos.
- Configuración: límite por turno y Gestión de Roles.
- Registro de acciones: filtros, tabla y detalle con parámetros y cuerpo.

**Método** (como las rondas 1 y 2): `impeccable` en modo Operate sobre `DESIGN.md` (extensión de un mundo ya fijado,
sin torneo de direcciones) y `frontend-design`; maquetas montadas sobre la pantalla real con el stack local y datos
ficticios; página de comparación con selector de pantalla, versión y ancho, «Qué cambia», «Riesgo» e **inventario**
por pantalla (cada cosa de hoy y dónde queda; «Añadido» para lo nuevo). La página lista también los formularios que la
259 no nombra (alta de alumno, crear y editar grupo y turno, gestionar alumnos del grupo) para que el propietario diga
si entran.

**Entrega:** la página para el propietario y, en el repositorio, `docs/multi-escuela/fase3-e8b-ronda3.md` (método,
propuestas, riesgos, datos que faltan y preguntas) más los inventarios elegidos, copiados al Anexo A de este plan.
Capturas fuera del repositorio.

**Riesgos:** Preinscripciones es la pantalla más grande del panel (≈ 100 kB de plantilla y código) y mezcla bandeja y
configuración (plantillas y normas): puede pedir dividirse en dos. Caja de sede y Solicitudes ya siguen la línea de
Mi caja (Caja de sede, 28/40 en la auditoría): el boceto conserva lo que funciona en vez de rehacerlo.

**Notas para la PR:** cada inventario completo (nada se pierde); qué queda a un clic; contraste medido de lo nuevo.

## 16. Pieza 8b-3f: Eventos

Rama `feature/e8b-3f`. Solo frontend, sin migración: atajo `desplegar`. Tier medium: contenido de la web pública, sin
dinero ni permisos nuevos. RB3 hecha (decisión 261).

**Objetivo:** listado, crear y editar eventos con el orden (subir, bajar y arrastrar), la vista previa, la visibilidad
en la web y las imágenes de hoy. Inventario: Anexo A, Eventos y Editar evento.

**Variantes elegidas (decisión 261).**
- **Listado: P1, lista densa ordenable.** Una fila por evento: asa y flechas de orden al principio, miniatura, título,
  descripción en dos líneas, fecha con su estado e interruptor «Visible en la web»; «Editar» a la vista y «Eliminar» en
  «⋯». Un aviso bajo el título explica que aparecerá «Guardar orden» al reordenar.
- **Editar y crear: P2, vista previa al lado.** Formulario en una columna con tres secciones (información, imagen,
  documentos), etiquetas «obligatorio» u «opcional», resumen de errores arriba con enlaces a cada campo y barra fija
  abajo con «Cancelar» y «Guardar cambios» (desactivado con errores). Al lado, la vista previa de la tarjeta pública,
  que cambia mientras se escribe y dice si el evento está visible u oculto.
- **Vista previa a 375 y 768 px:** el boceto la baja al final del formulario, donde deja de verse a la vez que los campos
  (riesgo anotado en 8b-B3). No fija una solución móvil: **a confirmar en R4b**. Desde 1280 px va al lado.

**Requisito explícito: arreglar el móvil del listado P1.** El propietario lo vio descolocado a 375 px. El boceto no
trae la maqueta móvil corregida: la pieza la resuelve sin cambiar el contenido de la fila (Anexo A) y la presenta en
R4b. El implementador compara su captura a 375 con la de P1 en el boceto (https://claude.ai/artifact/5ynw1nWRYKQDJ8LdVqfq1w) antes de dar la pieza por
buena. Criterios de aceptación a 375 px (y 360 como mínimo útil):
- Una sola columna, sin desplazamiento horizontal de la página ni de la fila (`scrollWidth` igual a `clientWidth`).
- Ningún elemento de la fila se solapa con otro ni queda cortado: miniatura, título, descripción (dos líneas con
  elipsis), fecha con su estado, interruptor «Visible en la web», «Editar» y «⋯» caben, apilados si hace falta.
- El control de orden (asa y flechas subir y bajar) se maneja con el dedo, con objetivos de 44 px, y con teclado
  (flechas con foco en el asa o en los botones, anunciado con `LiveAnnouncer`). Arrastrar no es la única vía.
- El aviso «Guardar orden» y la acción de guardar se ven sin tapar filas ni la barra inferior de 8b-N.
- Cadenas largas (título de 80 caracteres, sin espacios) no desbordan; con el texto al 200 % sigue sin desplazamiento.

**Ficheros:** `F/panel-nuevo/eventos/` (listado y un formulario compartido de crear y editar); rutas `eventosListar`,
`eventosCrear` y `eventosEditar/:id` con `canMatch`.

**Tareas TDD:** specs (el orden manda la misma petición que hoy, con teclado además de arrastre; publicar u ocultar con
la misma llamada; subida de imagen con las mismas validaciones, HEIC rechazado con su mensaje; vista previa con el foco
atrapado y Esc; a 375 sin desplazamiento horizontal en el listado). Capturas del listado a 375 antes y después del
arreglo.

**Riesgos:** la web pública muestra estos eventos: el formulario no puede cambiar lo que se guarda.

**Notas para la PR:** inventario de la ronda 3 marcado; mismas peticiones que hoy (lista).

**Implementación (rama `feature/e8b-3f`, desde `feature/e8b-3e` en `39e15337`; la PR #102 aún sin fusionar):**
- Nuevos en `F/panel-nuevo/eventos/`: `eventos.component.*` (`EventosPanelComponent`, `app-eventos-panel`, listado P1),
  `evento-formulario.component.*` (`EventoFormularioComponent`, `app-evento-formulario`, crear y editar P2),
  `eventos-capa.component.*` (`pn-eventos-capa`: capa grande para «Ver cómo queda en la web» y la imagen ampliada, con
  `showModal()`, foco contenido, Esc, velo y vuelta al disparador) y `eventos-vista.ts` (`estadoFecha`, `etiquetaEstado`,
  `fechaCorta`, `fechaTitulo`, `fechaParaCampo`, `urlImagen`, `moverEn`, `nombreDescarga`, `tamanoArchivo`,
  `cuerpoCrear`, `cuerpoEditar`, `mensajesError`, `resumenEventos`). `LecturasPanelService`: `eventosAdmin` y `evento`
  (la misma petición que `obtenerTodosLosEventos` y `obtenerEventoPorId`, con `ERROR_HANDLED_LOCALLY`). Rutas
  `eventosListar`, `eventosCrear` y `eventosEditar/:id` con `canMatch: [disenoNuevoGuard]` delante de las de hoy (misma
  data, `roleGuard` y resolve).
- **Mismas peticiones** que `ListadoEventosComponent`, `CrearEventoComponent` y `EditarEventoComponent`, con los métodos
  de hoy de `EndpointsService`: orden, `PUT /eventos/orden` con los ids en el orden de la lista (arrastre, flechas y
  teclado mandan el mismo cuerpo) y, después, la relectura `GET /eventos/admin/todos`; visible, `PUT
  /eventos/{id}/toggle-visibilidad` sin cuerpo; eliminar, `DELETE /eventos/{id}` con credenciales y la misma limpieza de
  la caché de eventos públicos y de `eventosSubject` (`EndpointsService.eliminarEvento` se suscribe solo y no deja saber
  si fue bien: la pantalla usa el nuevo `eliminarEventoConRespuesta(id)`, el mismo DELETE devuelto como observable, y
  `eliminarEvento` queda intacto); crear, `POST /eventos/crear` multipart con
  `nuevo` = `{titulo, descripcion, fechaEvento o null, visible}` y `file` con su nombre solo si hay imagen y, cuando
  responde, un `POST /eventos/{id}/documentos` (`archivo`) por documento, a la vez; editar, `PUT /eventos/{id}` con
  `eventoEditado` = `{titulo, descripcion, fechaEvento o null, fotoEvento: null}` y `file` (el archivo o `"null"`);
  imagen, `DELETE /eventos/{id}/imagen`; documentos, `POST /eventos/{id}/documentos` por documento al pulsar «Subir N
  documentos», `DELETE /eventos/{id}/documentos/{doc}` y `GET .../descargar?download=true` (blob; en iOS, la URL directa)
  con el nombre de hoy. Validaciones de hoy (`prepararFicheroParaSubida` con `SUBIDA_IMAGEN` y `SUBIDA_DOCUMENTO`: HEIC
  con `MOTIVO_HEIC`, formatos, 50 MB), `required` y máximos 100 y 500 sin recortar. Nada se envía al cancelar; sin doble
  envío (orden, interruptor, guardar, subir y eliminar imagen).
- Diálogos con `ConfirmacionService` («Eliminar evento», «Eliminar documento» y «Eliminar imagen», destructivos con
  `focoEnCancelar`); avisos con `AvisosService`; esqueletos a los 300 ms, `pn-bloque-error` con «Reintentar» (listado y
  evento) y `pn-bloque-vacio` con «Crear nuevo evento». Sin SweetAlert2 ni cargador global. «⋯» con el patrón de botón de
  menú (`teclado-menu.ts`). Orden: asa (`cdkDragHandle`, la fila arrastrada dentro de la lista) y botones subir y bajar;
  flechas arriba y abajo con el foco en el asa o en los botones mueven la fila, el foco sigue en el mismo mando y se
  anuncia con `LiveAnnouncer` («Exhibición, posición 2 de 5.»; en un extremo, «… ya es la primera.»). La región del
  anunciador se oculta con su regla en la hoja de la pantalla (la aplicación no carga `a11y-prebuilt`).
- **Solución del móvil del listado:** la fila se compone con consultas de contenedor sobre la lista (no del viewport). Por
  debajo de 960 px de lista, tres bandas: miniatura con la fecha y su estado y «⋯» arriba, título y descripción (dos
  líneas con elipsis) debajo, e interruptor y «Editar» en una banda propia a lo ancho; el asa y las flechas, en una
  columna de 44 px a la izquierda. Títulos con `overflow-wrap: anywhere`. El aviso «Guardar orden» queda fijo bajo la
  cabecera mientras se baja (las filas dejan su margen al moverse con el teclado) y no toca la barra inferior. Desde
  960 px, una línea por evento. Criterios a 375 y 360: una columna; `scrollWidth` igual a `clientWidth` en la página y en
  cada fila (también con 80 caracteres sin espacios y con el texto al 200 %); sin solapes ni cortes entre las piezas de la
  fila; objetivos de 44 px; orden con el dedo, con teclado y anunciado; «Guardar orden» a la vista sin tapar la barra.
- **Cambios a propósito:** el interruptor publica u oculta al momento (hoy, «Mostrar»/«Ocultar» quedaba pendiente hasta
  «Guardar cambios», que mandaba orden y visibilidad juntos; la llamada es la misma; **cambio a propósito decidido por el
  propietario el 23/10**: publica u oculta al momento y «Ver cómo queda en la web» muestra lo ya publicado más el orden
  sin guardar) y el aviso pasa a «Guardar orden»
  solo con el orden; «Eliminar imagen» pide confirmación (hoy borraba sin preguntar; se borra al momento, como hoy); los
  errores de imagen y documentos van en línea bajo su sección (hoy, SweetAlert2); sin foto, la miniatura no se amplía
  (hoy avisaba «Imagen no disponible»); el aviso de borrado sale cuando responde el servidor (hoy, antes); la acción de
  crear dice «Crear evento» y la de editar «Guardar cambios» (hoy «Crear Evento» y «Actualizar Evento»); títulos
  «Eventos», «Crear nuevo evento» y «Editar evento»; «Orden guardado.» (hoy «Cambios guardados»); la visibilidad avisa
  «… ya se ve en la web.» o «… ya no se ve en la web.»; con errores, la acción va con `aria-disabled` y su clic no envía:
  enseña el resumen y le da el foco; tamaños «17 bytes», «1,5 KB»; «máx. 50 MB» con espacio.
- [x] Specs en rojo antes del código (`Can't resolve './eventos-vista'`, `'./eventos.component'`,
  `'./evento-formulario.component'`; `eventosAdmin` y `evento` no existen en `LecturasPanelService`), luego verdes:
  `eventos-vista.spec.ts` (12), `eventos.component.spec.ts` (21: carga y cabecera; filas; aviso y «Guardar orden»;
  subir y bajar; teclado en el asa y en los botones con `LiveAnnouncer`; arrastre y teclado con la misma petición;
  sin doble envío y error; interruptor con la llamada de hoy, sin doble envío y error; «⋯» con teclado; eliminar con
  foco en «Cancelar», cancelar sin envío y error; capas con foco contenido y Esc; esqueleto, error y vacío; a 375 px sin
  desplazamiento horizontal con un título de 80 caracteres), `evento-formulario.component.spec.ts` (23: secciones y
  etiquetas; resumen y botón desactivado; máximos; crear con multipart, documentos después y sin doble envío; errores;
  cancelar sin envío; imagen HEIC, formato, tamaño y quitar; editar con GET, PUT y «file» "null" o el archivo; eliminar
  imagen; documentos subir N, quitar, descargar y eliminar; vista previa al escribir y visible u oculto; esqueleto y
  error), `lecturas-panel.service.spec.ts` y `app.routes.spec.ts` (dos entradas por ruta; apagado no descarga el chunk).
- **Notas para la PR:** bundle inicial 1.457.342 bytes (+501 frente a 1.456.841 en `39e15337`, las tres entradas de
  ruta; los dos medidos con las dependencias del `package-lock.json`: con el `node_modules` anterior, la base daba
  1.456.023); chunks `eventos-component` 40,25 kB (8,80 kB por la red) y `evento-formulario-component` 45,35 kB (9,99
  kB); karma 1994 SUCCESS; capturas apagado antes (`39e15337`) y después idénticas en bytes, 9/9 (listado, crear y editar
  a 375, 768 y 1280; el listado de hoy a 768 y 1280 sale en dos o tres variantes por tiempos de carga con cualquiera de
  los dos builds, y se compara la misma variante); encendido a 360, 375, 768 y 1280 (listado, reordenado con «Guardar
  orden», «⋯» abierto, «Ver cómo queda en la web», eliminar, vacío, título largo, texto al 200 %, crear con errores y
  editar con imagen y documentos); objetivos de 44 px como mínimo a 360 y 375, sin desplazamiento horizontal ni solapes
  en ningún ancho. `impeccable detect`: solo «img sin src» en los `[src]` enlazados (falso positivo).
- **Correcciones de la revisión (commit `fix(panel): correcciones de la revisión de Eventos (8b-3f)`):** (1) test
  inestable: un `.swal2-container` (toast de 3 s) dejado por specs viejos rompía la primera aserción «sin Swal» que
  corría después (1 de cada ~5 corridas); `Swal.close()` más la retirada de `.swal2-container` al inicio del
  `beforeEach` de `eventos`, `evento-formulario` y `tesoreria`, y, de raíz, en el `afterEach` de CrearAlumno
  (disciplinas), CajaSede, ficha de producto (vendible por el profesor), ListadoGrupos (resumen) y EditarAlumno. (2)
  borrar evento ya no usa el método privado por corchetes ni `HttpClient`: `EndpointsService.eliminarEventoConRespuesta`
  (spec: la caché se limpia y la siguiente `obtenerEventos` vuelve a pedir `GET /eventos`; el evento sale de
  `eventos$`; un error no toca nada). (3) las escrituras (crear, actualizar, interruptor, orden, borrar, imagen,
  documentos) ya no llevan `takeUntil(destroy$)`: salir a mitad de un guardado no aborta la petición, sube los
  documentos y limpia la caché; lo que toca la vista o navega tras destruir se protege (no relee ni navega). Las lecturas
  y la descarga lo mantienen. Specs en rojo con el código viejo (6 fallos) y verdes. (4) specs que faltaban: error al
  eliminar imagen y documento, error de descarga, subida parcial al editar.
- **Notas de la corrección:** karma 2006 SUCCESS tres veces seguidas; bundle inicial 1.457.342 bytes (sin cambio);
  chunks `eventos-component` 39.937 bytes (39,94 kB; antes 40,25 kB) y `evento-formulario-component` 45.322 bytes (45,32
  kB; antes 45,35 kB).
- **Pregunta abierta (R4b):** a 375 y 768 px la vista previa va al final del formulario, como el boceto, y deja de verse
  a la vez que los campos. A confirmar por el propietario.

## 17. Pieza 8b-3g: Productos

Rama `feature/e8b-3g`. Solo frontend, sin migración: atajo `desplegar`. **Tier careful** (sube de medium): el catálogo
fija los precios que luego se cargan a los alumnos y los productos que puede vender el profesor (V31_7).

**Objetivo:** listado con filtros, crear y editar productos; importes es-ES al leer y en los campos, sin cambiar el
valor que se envía. Inventario: Anexo A, Productos.

**Variante elegida (decisión 261): P2, agrupados por tipo.** Productos en grupos por tipo (mensualidad, licencia,
reserva, competidor, derechos de examen), cada grupo con su número y sin páginas. Fila corta: casilla, concepto, «nº»,
precio a la derecha, interruptor «Mi caja» y «⋯» (Editar, Eliminar). Barra fija al pie con los seleccionados («Dar a Mi
caja», «Quitar de Mi caja», «Eliminar»). Ordenar por ID, concepto o precio pasa a un menú de cada grupo. Los derechos
de examen son un grupo largo (riesgo anotado en 8b-B3).

**Ficheros:** `F/panel-nuevo/productos/`; rutas `productosListar`, `productosCrear` y `productosEditar/:id` con
`canMatch`.

**Tareas TDD:** specs (el precio escrito «1.010,50» se envía como `1010.5`, igual que hoy «1010.50»; el cuerpo de crear y
editar es idéntico al de hoy campo a campo; borrar con confirmación y la misma llamada; «vendible por el profesor» igual
que hoy). Capturas.

**Riesgos:** la conversión del importe escrito en formato español (el campo de hoy usa punto decimal); un campo que
deje de enviarse.

**Notas para la PR:** inventario marcado; tabla de cuerpos enviados hoy frente a la pantalla nueva.

**Implementación (rama `feature/e8b-3g`, desde `develop` en `2e95a191`):**
- Nuevos en `F/panel-nuevo/productos/`: `productos.component.*` (`ProductosComponent`, `app-productos`, listado P2),
  `producto-formulario.component.*` (`ProductoFormularioComponent`, `app-producto-formulario`, crear y editar) y
  `productos-vista.ts` (`TIPOS_PRODUCTO`, `TAMANO_LECTURA`, `leerImporte`, `importeParaCampo`, `validarPrecio`,
  `mensajesError`, `cuerpoProducto`, `OPCIONES_ORDEN`, `ORDEN_POR_DEFECTO`, `ordenar`, `etiquetaOrden`, `mismoOrden`,
  `filtrosActivos`, `miCajaParaApi`, `textoTotal`). `LecturasPanelService`: `productos` y `producto` (la misma petición
  que `obtenerTodosLosProductosPaginado` y `obtenerProductoPorId`, con `ERROR_HANDLED_LOCALLY`). Rutas
  `productosListar`, `productosCrear` y `productosEditar/:id` con `canMatch: [disenoNuevoGuard]` delante de las de hoy
  (misma data, `roleGuard` y resolve). `EndpointsService` no cambia.
- **Lectura y grupos.** `GET /productos` no trae el tipo (`Producto.tipo` es `@JsonIgnore`) y la pieza no toca la API:
  se pide una lectura por tipo con la petición de hoy y su filtro `tipo` (`page=1&size=200&orderBy=concepto&order=asc&tipo=…`,
  más `concepto` y `vendibleProfesor` si hay filtro) y las páginas siguientes si las hay, para enseñar cada grupo
  entero. Con «Tipo» elegido, solo esa lectura. Los grupos siguen la columna `tipo` (la misma del filtro de hoy); los
  siete tipos de la API tienen grupo (Mensualidades, Licencias, Reservas de plaza, Competidor, Derechos de examen,
  Recompensas y Otros productos) y los vacíos no salen. El total es la suma de los grupos. El orden de cada grupo
  (concepto de la A a la Z por defecto, como hoy) se hace en el navegador, sin volver a pedir nada.
- **Mismas escrituras que hoy** (con los métodos de `EndpointsService`), campo a campo:

  | Escritura | Hoy | Pantalla nueva |
  |---|---|---|
  | Crear | `POST /productos` con `productoForm.value` = `{concepto, precio, vendibleProfesor}`; el precio, número del campo `type="number"` | `POST /productos` con `{concepto, precio, vendibleProfesor}` en ese orden; el precio, `leerImporte` del texto es-ES (el mismo número); el concepto sin recortar, como hoy |
  | Editar | `PUT /productos/{id}` con `{concepto, precio, vendibleProfesor}` (el `patchValue` solo rellena esos tres) | `PUT /productos/{id}` con el mismo cuerpo |
  | Mi caja de uno | `PATCH /productos/{id}/vendible-profesor` con `{vendibleProfesor}`, al momento; el valor, el de la respuesta | Igual, al momento; el interruptor ocupado, sin doble envío; el valor, el de la respuesta |
  | Mi caja en lote | `PATCH /productos/vendible-profesor` con `{ids, vendibleProfesor}` (los seleccionados de la página) | Igual, con los seleccionados de todos los grupos en el orden de la pantalla |
  | Eliminar uno | `DELETE /productos/{id}` tras SweetAlert2 | Igual, tras el diálogo destructivo (foco en «Cancelar», ocupado «Eliminando…») |
  | Eliminar en lote | Un `DELETE /productos/{id}` tras otro, del último al primero; un fallo no para el resto | Igual (`eliminarEnSerie`, con el diálogo ocupado) |

  Specs: crear y editar de la pantalla nueva se comparan con `CrearProductoComponent` y `EditarProductoComponent` de
  verdad (método, ruta, credenciales y JSON enviado) en cinco y seis casos; Mi caja y eliminar, con
  `HttpTestingController`.
- **Importes.** Se leen y se escriben en es-ES (`eur` en la fila; «1.010,50» en el campo, con todos los decimales
  guardados: 12,345 sale «12,345», nunca redondeado). El campo es de texto (`inputmode="decimal"`) y `leerImporte` da el
  mismo `Number` que hoy daba el campo con punto: «1.010,50», «1010,50» y «1010.50» → `1010.5`; «25» → `25`; «25,5» →
  `25.5`; «1.5» → `1.5` (el punto decimal de hoy sigue valiendo: no es 15); «0.500» → `0.5`; «1.234.567» → `1234567`;
  «,5» y «.5» → `0.5`; espacios por fuera y «€» al final se ignoran. **Ambiguo:** un solo punto seguido de tres cifras
  sin cero delante («1.234», «10.000») puede ser miles (es-ES) o decimales (el campo de hoy): no se adivina y el error
  pide escribirlo sin punto (1234) o con coma (1,234). Vacío → «El precio es obligatorio» (como hoy, no se envía);
  negativo → «El precio debe ser mayor o igual a 0» (como hoy); texto, «1,010.50», «12,», «1 000», «+5» o «1e3» →
  «Escribe el precio con cifras, por ejemplo 25 o 1.010,50». Al salir del campo, un precio válido se reescribe en es-ES
  («1010.5» → «1.010,50»). Más de dos decimales se aceptan, como hoy.
- Diálogos con `ConfirmacionService` (`ejecutar`): «Eliminar producto» y «Eliminar N productos», destructivos con
  `focoEnCancelar`; nada al cancelar; ocupados mientras van, sin doble envío. Avisos con `AvisosService`; esqueletos a
  los 300 ms, `pn-bloque-error` con «Reintentar» (listado y producto) y `pn-bloque-vacio` (sin productos, con «Crear
  producto»; sin coincidencias, con «Limpiar filtros»). Sin SweetAlert2 ni cargador global. «⋯» y el orden de cada grupo
  con el patrón de botón de menú (`teclado-menu.ts`). Escrituras sin `takeUntil` (salir no las cancela; tras destruir no
  se relee ni se navega); lecturas canceladas al salir.
- **Barra de seleccionados:** fija al pie (encima de la barra inferior de 8b-N por debajo de 1024 px) con «N productos
  seleccionados», «Quitar selección», «Dar a Mi caja», «Quitar de Mi caja» y «Eliminar»; a la derecha deja el sitio del
  botón flotante «volver arriba» de la aplicación, que si no tapaba «Eliminar». Casillas de 44 px (el control nativo
  cubre la zona y la caja se dibuja con los tokens); «Seleccionar los N de …» en la cabecera de cada grupo, con estado
  mixto.
- **Cambios a propósito:** una lectura por tipo (siete) en vez de una página de 10, y sin páginas; el orden de cada
  grupo en el navegador (sin mayúsculas ni acentos y con los números en su orden; hoy, la colación de MySQL); filtros y
  orden recordados con la clave `panelProductosEstado` (hoy `listadoProductosEstado`, con la página); búsqueda a los
  300 ms (hoy 500); tras eliminar se relee sin esqueleto; diálogos con título propio («Eliminar producto», «Eliminar N
  productos»; hoy «¿Estás seguro?» y «Estas seguro?») y la frase de hoy con tildes; el lote avisa «N productos
  eliminados.» y un error con los que fallaron (hoy un aviso por fallo y luego «Productos eliminados correctamente»
  aunque fallaran); sin selección no hay barra (hoy «No hay productos seleccionados»); avisos con punto final; opciones
  «Mi caja: todos», «Mi caja: sí» y «Mi caja: no»; chip del concepto con «»; títulos «Productos», «Crear producto» y
  «Editar producto» (hoy «Gestión de Productos», «Crear Nuevo Producto» y «Editar Producto»); acciones «Crear producto»
  y «Guardar cambios» (hoy «Crear Producto» y «Actualizar Producto»). En el formulario: precio en texto es-ES, un error
  por causa (hoy uno común), resumen de errores con enlaces, la acción con `aria-disabled` (su clic enseña el resumen),
  «Guardando…» sin doble envío, el mensaje del servidor si lo trae (el 400 de renombrar con cargos; hoy genérico), los
  botones de la barra no quitan el foco al campo al pulsarlos (el error que aparecía al salir del campo movía el botón y
  el clic se perdía) y, si falla la lectura al editar, bloque con «Reintentar» (hoy aviso y vuelta al listado).
- [x] Specs en rojo antes del código (`Can't resolve './productos-vista'`, `'./productos.component'` y
  `'./producto-formulario.component'`; `productos` y `producto` no existen en `LecturasPanelService`; después, el de los
  botones de la barra, con `defaultPrevented` falso), luego verdes: `productos-vista.spec.ts` (59: conversión de
  importes caso a caso, ida y vuelta, validación, mensajes, cuerpo, tipos, orden y chips), `productos.component.spec.ts`
  (25: lecturas por tipo y páginas; cabecera; grupos y fila; menús con teclado; orden por grupo; buscador, tipo, Mi
  caja, chips y limpiar; sesión; vacíos, esqueleto y error; Mi caja de uno y en lote con método, ruta y cuerpo, sin
  doble envío y errores; selección y barra; eliminar uno y en lote, cancelar y errores; escrituras que no se cancelan; a
  375 sin desplazamiento horizontal), `producto-formulario.component.spec.ts` (15: crear y editar comparados con las
  pantallas de hoy; conversión en el campo; errores y resumen; sin doble envío; errores del servidor; cancelar; carga y
  «Reintentar»; foco al pulsar), `lecturas-panel.service.spec.ts` y `app.routes.spec.ts` (dos entradas por ruta;
  apagado no descarga el chunk).
- **Notas para la PR:** bundle inicial 1.458.416 bytes (+748 frente a 1.457.668 en `2e95a191`, los dos medidos con las
  dependencias del `package-lock.json`: las tres entradas de ruta; ningún fichero inicial contiene código de
  `productos`); chunks `productos-component` 47.724 bytes y `producto-formulario-component` 21.903 bytes; karma 2131
  SUCCESS dos veces; capturas apagado antes (`2e95a191`) y después idénticas en bytes, 9/9 (listado, crear y editar a
  375, 768 y 1280; en una toma, editar a 375 difirió en 3 × 1 px junto al cursor, y tres repeticiones con cada build
  salieron idénticas a la de antes); encendido a 375, 768 y 1280 (listado agrupado, seleccionados con la barra, menú de
  orden abierto, «⋯» abierto, filtros con chips, eliminar, vacío, crear con errores y editar); objetivos de 44 px como
  mínimo a 375 (40 en escritorio; 24 en los enlaces del resumen de errores), sin desplazamiento horizontal ni solapes en
  ningún ancho y la barra sin tapar la inferior; `impeccable detect` sin hallazgos.
- **Preguntas abiertas:** (1) «1.234» se pide aclarar: ¿leerlo como miles (es-ES) o como hoy (1,234)? (2) ¿Limitar el
  precio a dos decimales? Hoy se aceptan más y se conservan.
- **Correcciones de la revisión (mismo commit de arreglos):** (a) las páginas de cada grupo se piden con `orderBy=id`
  (único; en paralelo, el orden por concepto podía repetir u omitir filas en el corte) y el orden visible se hace en el
  navegador; (b) «Dar/Quitar de Mi caja» con más de 200 seleccionados no envía nada y avisa «Selecciona como máximo 200
  productos a la vez.» (todo o nada de la API); 200 sí se envía; (c) el botón global «volver arriba» del `skeleton` solo
  se monta con el diseño de hoy (así no tapa la barra inferior ni las barras fijas del panel nuevo, Eventos incluido);
  se borró su regla sin efecto en `skeleton.component.scss` y la barra de selección vuelve a `padding` de 16 px;
  (d) `productosEditar/abc` (id no numérico o no positivo) enseña `pn-bloque-error` «No se encontró el producto» sin
  «Reintentar» (nuevo `@Input() reintentable`, por defecto `true`) y no deja guardar, en vez de caer en crear.
  Cierra las preguntas (3) y (4) de antes. Karma 2134 SUCCESS; bundle inicial 26 bytes menos que antes de la corrección.

## 18. Pieza 8b-3h: Preinscripciones

Rama `feature/e8b-3h`. Solo frontend, sin migración: atajo `desplegar`. **Tier careful:** datos de menores, firmas,
borrado definitivo y paso a ficha de alumno (altas). Si 8b-B3 la divide en bandeja y configuración (plantillas PDF y
normas), van en dos PR de esta misma pieza.

**Objetivo:** la variante elegida con todo lo de hoy: filtros, ficha con historial, firma presentada y
solicitud firmada, observaciones, modificar turnos, lista de espera, asignación administrativa, destino de la ficha
(nueva o existente), finalizar, cancelar, eliminar definitivamente, reenviar aviso, correo definitivo de acceso,
plantillas PDF (previsualizar y publicar versión) y normas. Inventario: Anexo A, Preinscripciones.

**Variante elegida (decisión 261): P1, lista con detalle al lado.** Lista a la izquierda (nombre, estado, referencia,
disciplina, hace cuánto llegó, marca «Con observación») y detalle a la derecha, siempre visible en escritorio: datos,
horario, observación resaltada, avisos por correo con intentos, «Firma y ficha» y acciones al pie («Revisar y finalizar
alta» como primario, «Reenviar solicitud original», «Más» con cancelar y eliminar definitivamente). En el móvil, la
lista y, al tocar, el detalle. Las pestañas Solicitudes y Plantillas PDF, y los filtros, se quedan como hoy. La lista
estrecha puede recortar nombres largos (riesgo anotado en 8b-B3). El boceto no divide la pantalla en dos PR; si el
implementador la parte por tamaño, lo anota (ver arriba).

**Ficheros:** `F/panel-nuevo/preinscripciones/`; ruta `preinscripciones` con `canMatch`.

**Tareas TDD:** specs (cada acción del inventario contra el mismo endpoint, cuerpo y confirmación que hoy; «Eliminar
definitivamente» con confirmación explícita y sin acción por defecto; el paso a ficha elige nueva o existente igual que
hoy; ningún dato personal en la URL). Capturas con datos inventados.

**Riesgos:** perder una acción poco usada; el borrado definitivo; la vista de la firma (documento con datos personales).

**Notas para la PR:** inventario marcado; mapa acción de hoy → sitio nuevo.

## 19. Pieza 8b-3i: Solicitudes

Rama `feature/e8b-3i`. Solo frontend, sin migración: atajo `desplegar`. **Tier careful, revisión opus:** decide sobre
dinero (deshacer cobros, devoluciones enlazadas y encargos de la 6c) y cada acción depende del rol.

**Objetivo:** bandeja conservando lo que ya funciona (recorrido del encargo, aceptar, retirar, encargado, recibido,
reintentar) y el contador de la cabecera. Inventario: Anexo A, Solicitudes.

**Variante elegida (decisión 261): P1, acciones en la fila.** La idea de hoy en el lenguaje de C: quién, cuándo y sede
a la izquierda; qué, importe y motivo en el centro; acciones a la derecha, todo a la vista. Segmentos con su número
como pestañas; debajo de las pendientes, los encargos en curso con su recorrido Pedido, Encargado, Recibido y
Entregado. Es un cambio pequeño: no hay panel de detalle ni explicación de «qué pasa al aceptar» (eso era P2).

**Ficheros:** `F/panel-nuevo/solicitudes/`; ruta `solicitudes` con `canMatch`.

**Tareas TDD:** specs (cada acción con el mismo endpoint y la misma confirmación; las acciones que el rol no puede hacer
no se muestran, con los criterios de hoy para propietario y gestor; el contador se actualiza igual tras aceptar o
retirar; un 409 muestra el `mensaje`). Capturas por rol.

**Riesgos:** ofrecer una acción a quien no puede hacerla (el backend la deniega, pero la interfaz no debe enseñarla).

**Notas para la PR:** inventario marcado; tabla acción × rol de hoy frente a la nueva.

## 20. Pieza 8b-3j: Caja de sede

Rama `feature/e8b-3j`. Solo frontend, sin migración: atajo `desplegar`. **Tier careful, revisión opus:** efectivo,
entregas de los profesores y movimientos por sede.

**Objetivo:** conservar lo que hoy funciona: resumen de la caja, efectivo en mano de cada profesor, recibir entrega
(con selección de cobros y correcciones), entregas y movimientos, apuntar movimiento y selector de sede con varias
sedes. Inventario: Anexo A, Caja de sede.

**Variante elegida (decisión 261): P2, pestañas Por recibir, Entregas y Movimientos.** Resumen arriba (saldo de caja,
pendiente de entregar, recibido en el periodo, diferencias) con sede y periodo bajo el título, y debajo tres pestañas
con su número. Abre en «Por recibir» (efectivo en mano de cada profesor y «Recibir entrega», que se abre bajo el
profesor con el cálculo de lo que falta y la nota obligatoria si no cuadra); «Entregas» y «Movimientos» quedan a un
clic, este último con «Apuntar movimiento».

**Ficheros:** `F/panel-nuevo/caja-sede/`; ruta `cajaSede` con `canMatch`.

**Tareas TDD:** specs (recibir entrega manda los mismos cobros y correcciones que hoy; apuntar movimiento con el mismo
cuerpo; saldos en es-ES sin redondeos distintos de los de hoy; hoja de confirmación con el foco atrapado, como hoy con
`cdkTrapFocus`; cambiar de sede recarga el resumen de esa sede). Capturas con dos sedes.

**Riesgos:** un importe mostrado distinto del guardado; la selección de cobros de una entrega.

**Notas para la PR:** inventario marcado; mismas peticiones que hoy (lista) y prueba manual en la escuela de pruebas.

## 21. Pieza 8b-3k: Configuración y Registro de acciones

Rama `feature/e8b-3k`. Solo frontend, sin migración: atajo `desplegar`. **Tier careful, revisión opus:** las dos son
solo del propietario (`adminOnlyGuard`); Configuración cambia roles (Gestión de Roles, `PUT
/api/admin/users/{id}/roles`, con el 409 de último propietario de la decisión 221) y el límite por turno, y el
Registro de acciones enseña datos sensibles. Van juntas por pequeñas y por compartir guard y revisión (§1).

**Objetivo:** Configuración: límite de alumnos por turno y Gestión de Roles (búsqueda, roles actuales, asignar roles).
Registro de acciones: filtros, cronología y detalle (parámetros y cuerpo, tal como ya llegan redactados del backend).
Inventario: Anexo A, Configuración y Registro de acciones.

**Variantes elegidas (decisión 261).**
- **Configuración: dos distribuciones según el ancho, con el mismo contenido y las mismas acciones.** Por debajo de
  768 px, **P1**: secciones seguidas, «Alumnos por turno» (valor actual, nuevo valor, «Modificado», Guardar y
  restaurar; el error bajo el campo y Guardar desactivado) y «Roles y permisos» con una fila por cuenta. Desde 768 px
  (tablet y escritorio), **P2**: índice de ajustes a la izquierda (Alumnos por turno, Roles y permisos, Avisos legales
  y cookies) y, a la derecha, la sección elegida; cada cuenta en dos líneas (nombre y roles actuales arriba; casillas
  y Guardar abajo). Un solo componente con dos plantillas por ancho, sin lógica duplicada; los tests comprueban las
  mismas acciones en 375 y en 768. Con dos ajustes, el índice aporta poco hasta que haya más (riesgo anotado en 8b-B3).
- **Registro de acciones: P2, cronología por días con detalle.** Días como título y, por acción, hora, pastilla del
  tipo, qué, módulo y «Cuenta N» (el identificador, sin nombre ni email); el detalle (incluidos los datos técnicos
  desplegables) en un panel al lado. Los filtros (desde, hasta, acción, módulo, persona y buscar) se pliegan en un botón
  «Filtros»: quedan a un clic (riesgo anotado en 8b-B3).

**Gestión de Roles y nombres visibles (decisión 262).** Las casillas y las pastillas de roles muestran «Familia»,
«Gestión» y «Administración». Correspondencia con los valores internos, que no cambian ni cambia lo que se envía a
`PUT /api/admin/users/{id}/roles`: `ROLE_USER` = «Familia», `ROLE_MANAGER` = «Gestión», `ROLE_ADMIN` =
«Administración» (hoy «Usuario», «Manager» y «Admin» en las casillas, y «Usuario», «Manager» y «Administrador» en las
pastillas: `configuracion-sistema.component.html:168, 177, 186` y `.ts:403-418`). Los permisos no cambian. El spec
comprueba el texto visible y que el cuerpo enviado sigue llevando los `ROLE_*`. Otros roles que hoy muestra el
componente (`ROLE_PROFESOR`, `ROLE_RESPONSABLE_SEDE`) se muestran como «Profesorado» y «Responsable de sede» (decisión
263); solo cambia el texto, igual que en la 262.

**Ficheros:** `F/panel-nuevo/escuela/configuracion/` y `F/panel-nuevo/escuela/registro/`; rutas `configuracion-sistema`
y `auditoriaSistema` con `canMatch` **y** los mismos `roleGuard` y `adminOnlyGuard` de hoy.

**Tareas TDD:** specs (los textos «Familia», «Gestión» y «Administración» con los `ROLE_*` intactos en el cuerpo; las
dos distribuciones de Configuración ofrecen las mismas acciones; las rutas nuevas conservan `adminOnlyGuard`: un gestor con el interruptor encendido no entra;
cambiar roles manda el mismo cuerpo y muestra el 409 de último propietario con su `mensaje`; el límite se guarda con la
misma llamada; el detalle del registro no muestra nada que hoy no muestre ni pone datos en la URL). Capturas.

**Riesgos:** perder un guard al duplicar la ruta; ofrecer un rol que la sesión no puede dar.

**Notas para la PR:** inventario marcado; guards de cada ruta nueva frente a los de hoy.

## 21 bis. Pieza 8b-D1: diálogos, avisos y carga

Rama `feature/e8b-d1` (sale de `feature/e8b-3b`). Solo frontend, sin migración: atajo `desplegar`. Boceto aprobado tal
cual como especificación visual (decisión 271): `Main.dc.html` (diálogo «Cobrar»), `Baja.dc.html` (destructivo),
`HojaMovil.dc.html`, `Avisos.dc.html`, `AvisosMovil.dc.html`, `Carga.dc.html` e `InformesPanel.dc.html`.

**Objetivo:** el diálogo de confirmación, la hoja inferior, los avisos, el panel lateral y los estados de carga
propios del panel nuevo (decisión 269), aplicados a Inicio (8b-2), Alumnos (8b-3a) y el carnet de la ficha (8b-3b).
Las pantallas de hoy y las de familias siguen con SweetAlert2 hasta 8b-Z (decisión 240). En el listado nuevo, las tres
herramientas del pie salen del pie y se abren desde «Informes» en el panel lateral (hoja inferior en el móvil), con su
formulario redibujado y los mismos campos y acciones (decisión 270). «Cobrar» llama al mismo endpoint con el mismo
cuerpo, con una confirmación equivalente (punto de revisión 8).

**Ficheros:**
- Nuevos en `F/panel-nuevo/`: `capas/capa-modal.ts` (foco contenido, clic en el velo, vuelta al disparador, ancho de
  hoja, `PilaCapas`) y `capas/_capas.scss`; `dialogo/` (`DialogoComponent`, `ConfirmacionService`); `avisos/`
  (`AvisosService`, `AvisosComponent`); `carga/` (`EsqueletoComponent`, `BotonOcupadoComponent`, `BloqueErrorComponent`,
  `BloqueVacioComponent`); `panel-lateral/` (`PanelLateralComponent`); `cobro/cobro-cargo.ts` (`CobroCargoService`, que
  sustituye a `cobrarCargo`); `alumnos/bajas-altas-panel.ts`; `alumnos/herramientas/` (`HerramientaPanelComponent`,
  `CampoMesAnoComponent`, `InformePdfPanelService`); `formato/formato-es.ts` (`frasePlural`).
- Tokens en `panel-nuevo.scss`: `--pn-sombra-dialogo`, `--pn-sombra-hoja`, `--pn-velo`, `--pn-titulo-dialogo`.
- De hoy, sin cambio visible ni de comportamiento: `HerramientasAlumnosComponent` (avisos, errores, confirmaciones del
  409 y cargador global por métodos `protected` que hacen lo de siempre), `InformePdfService` (`avisar` protegido, con el
  mismo SweetAlert2), `SearchableSelectDirective` (`searchDropdownEnDialogo`, falso por defecto) y `confirmar-cobro.ts`
  (exporta `METODOS_PAGO_COBRO`).

**Tareas TDD:**
- [x] Specs en rojo antes del código: con la implementación apartada, los cinco specs nuevos no compilan
  (`Can't resolve './confirmacion.service'`, `'./avisos.service'`, `'./esqueleto.component'`,
  `'./panel-lateral.component'`, `CobroCargoService` no exportado); `herramienta-panel.component.spec.ts`, igual
  (`Can't resolve './herramienta-panel.component'`); con las pantallas aún con SweetAlert2, 33 casos en rojo de Inicio,
  Alumnos, ficha y herramientas.
- [x] `confirmacion.service.spec.ts`: `<dialog>` modal en el body dentro de un `.pn`; título, frase, concepto, importe
  y pastillas `role=radiogroup` con «Sin indicar» por defecto y tabindex itinerante; «Cancelar» a la izquierda y foco
  en la acción; destructivo con la acción en peligro y el foco en «Cancelar»; Esc, el evento `cancel`, el clic en el
  velo, la X y «Cancelar» cancelan (un clic dentro no); flechas, Inicio y Fin en las pastillas; foco contenido con Tab
  y Mayús+Tab; vuelta al disparador; hoja por debajo de 768 px (asa, acción arriba, 44 px, radio 14 arriba); con
  `ejecutar`, botón ocupado («Cobrando…», mismo ancho, 16 px, `aria-busy`) y Esc sin efecto hasta que termina; error
  devuelto a quien lo abrió; campo requerido.
- [x] `avisos.service.spec.ts`: éxito e información a los 5 s con barra de tiempo; error con `role=alert` y sin cierre
  solo; como mucho 3; acción opcional; pausa con el ratón; dentro de la capa modal de arriba.
- [x] `carga.spec.ts`: esqueleto a los 300 ms (no antes, y nada si la carga acaba antes), con su `role=status` y la
  forma oculta; sin pulso con `prefers-reduced-motion`; botón ocupado; bloque de error con «Reintentar»; vacío con su
  acción.
- [x] `panel-lateral.component.spec.ts`: modal de 480 px a la derecha bajo la cabecera de 64 px, pie fijo, hoja por
  debajo de 768 px; Esc, X y velo cierran con el foco de vuelta; foco contenido.
- [x] `cobro-cargo.spec.ts` (`HttpTestingController`): el mismo `PUT /api/productos-alumno/{id}` con
  `cambiosDeCobro(metodo)`, con método y sin él; cancelar no envía nada; botón «Cobrando…»; aviso «Cobro registrado»;
  «Deshacer» solo para quien hoy puede «Revertir» (el propietario), con la misma llamada que «Revertir» de la tesorería
  de hoy (`{pagado: false, motivoCambio: null}`); errores con los textos de hoy; sin doble cobro.
- [x] `herramienta-panel.component.spec.ts`: cada herramienta, comparada con el componente de hoy (mismas peticiones
  —método, ruta, parámetros y cuerpo— y mismos nombres de PDF): asistencia y listado de mensualidades con «Ver PDF» y
  «Descargar PDF»; mensualidades generales y por deporte; mensualidad individual con el 409 en el diálogo nuevo y el
  reintento forzado; licencias generales e individuales; un PDF que falla avisa dentro del panel y no con SweetAlert2.
- [x] `alumnos.component.spec.ts`, `inicio.component.spec.ts` y `ficha-alumno.component.spec.ts`: cada sustitución con
  la misma llamada y el mismo cuerpo, y nada al cancelar (listado arriba); `formato-es.spec.ts`: `frasePlural`.
- [x] Ruta apagada: `app.routes.spec.ts` y `rutas-panel-nuevo.spec.ts` siguen en verde (no se llama al
  `loadComponent` nuevo) y ningún fichero del inicial contiene los componentes nuevos (build de producción).
- [x] Capturas apagado antes y después idénticas en bytes, también con el pie de herramientas desplegado y con cada
  herramienta abierta; encendido a 375, 768, 1024 y 1280.

**Ronda 2 (revisión devuelta, 22/10/2026).** Specs en rojo antes del código: 21 casos (con las exportaciones nuevas
como esbozo vacío) en `capa-modal`, `carga`, `confirmacion.service`, `avisos.service`, `panel-lateral`,
`herramienta-panel`, `formato-es` y el interceptor; después, la batería completa en verde (1.649).
- [x] Bloqueante: `herramienta-panel.component.spec.ts`: con «Cargar mensualidades», «Cargar licencias» o un PDF en
  vuelo, el panel NO se cierra con «Cancelar», la X, Esc, el evento `cancel` ni el velo, y un segundo clic no lanza
  otro `POST`; tras la respuesta, `asignado` llega una vez y el panel se cierra. `panel-lateral.component.spec.ts`:
  con `bloqueado`, la X deshabilitada, dos Esc y dos `cancel` con `preventDefault` y sin emitir, el velo tampoco; si el
  navegador cierra el `<dialog>`, se vuelve a abrir como modal; sin bloquear, su `close` emite `cerrar` una vez.
- [x] `capa-modal.spec.ts` (nuevo, en `tsconfig.spec.json` y `angular.json`): `cerrarSweetAlertAbierto` cierra el
  SweetAlert2 con `swal-error-http` en una pantalla nueva (entrada con `disenoNuevoGuard`) y no cierra otro (una
  confirmación, «Sesión expirada») ni, en una pantalla de hoy, el del error. `error.interceptor.spec.ts`: su
  SweetAlert2 lleva `customClass: { popup: 'swal-error-http' }`. Avisos y diálogo, lo mismo con el router de verdad.
- [x] `confirmacion.service.spec.ts` y `carga.spec.ts`: el botón ocupado lleva `aria-disabled` (no `disabled`), el foco
  se queda en él y un segundo clic no llega; dos Esc y dos `cancel` sin efecto; si el navegador cierra el `<dialog>` con
  la acción en vuelo, se vuelve a abrir y el resultado llega al terminar; sin nada en vuelo, ese cierre cancela.
- [x] `formato-es.spec.ts`: `nombreMes` (0 a 11, mayúscula inicial, vacío fuera de rango); `pn-campo-mes-ano` lo usa.
- [x] `avisos.service.spec.ts`: las regiones vivas `role=status` y `role=alert` existen vacías al crear la región y
  reciben el texto a los 100 ms, por tipo, y siguen ahí vacías sin avisos; barra de tiempo sin animación con
  `prefers-reduced-motion`. `herramienta-panel.component.spec.ts`: el «Aviso» de un PDF parcial sale como información
  con el mismo texto y el error como error.
- [x] Capturas: apagado antes (`80d8bbf4`) y después idénticas en bytes, 28/28 (con 52 alumnos ficticios); encendido,
  solo lo que cambia (`encendido-ocupado` y `encendido-panel-envuelo`) a 375, 768, 1024 y 1280, con dos Esc en
  Chrome sin cerrar el diálogo ni el panel y un solo `POST` retenido en el navegador.
- La frase de la baja sigue siendo «desde hoy» (exacta: `fechaBaja` = hoy); ni el plan ni DESIGN.md citaban la del
  boceto.

**Inventario de lo sustituido** (dónde estaba → qué lo sustituye; por qué: decisión 269, y un SweetAlert2 o el cargador
global quedarían detrás de una capa modal nueva, sin verse ni poder cerrarse):

| Pantalla | Qué | Antes | Ahora |
|---|---|---|---|
| Inicio | «Cobrar» de los cinco más antiguos | `Swal.fire(opcionesConfirmarCobro)` + toasts SweetAlert2 | Diálogo «Cobrar mensualidad» / «… derecho de examen» con pastillas, botón ocupado y aviso |
| Inicio | Carga de Hoy, pendiente, ocupación, cifras y eventos | Textos «Cargando…» | Esqueletos con la forma de cada bloque |
| Inicio | Errores de Hoy, pendiente, ocupación, cifras y del límite | Textos «Prueba con «Actualizar ahora»» (el del límite, `role=status`) | Bloque de error con «Reintentar» por bloque |
| Alumnos | «Cobrar» de uno o varios cargos | `cobrarCargo` con SweetAlert2 y toasts | `CobroCargoService`: diálogo «Cobrar cargo», aviso y «Deshacer» del propietario |
| Alumnos | «Dar de baja» | `BajasAltasAlumno.darDeBaja`: SweetAlert2 «¿Estás seguro?» | Diálogo destructivo «¿Dar de baja a …?» con el foco en «Cancelar» |
| Alumnos | «Dar de alta» | SweetAlert2 informativo y selector con buscador | Aviso de información; diálogo con pastillas («Todos los deportes» primero) |
| Alumnos | «Marcar principal» y refresco del pendiente | Toasts SweetAlert2 | Avisos |
| Alumnos | Carga, error y vacío del listado | Esqueleto fijo, error y vacío propios | `pn-esqueleto` (300 ms, pulso), `pn-bloque-error`, `pn-bloque-vacio` «Ningún alumno coincide…» con «Quitar filtros» |
| Alumnos | Herramientas del pie | `<details>` en el pie con toasts, «Error» y 409 de SweetAlert2, cargador global y spinner de Bootstrap | Panel lateral desde «Informes» con avisos, diálogo del 409 y botón ocupado (lógica heredada sin copiar) |
| Alumnos | Errores de «Ver/Descargar PDF» y «Generar informe» | `InformePdfService` con SweetAlert2 | `InformePdfPanelService`: avisos |
| Ficha (carnet) | «Cobrar» | `cobrarCargo` con SweetAlert2 | `CobroCargoService` (diálogo y avisos) |
| Ficha (carnet) | Pendiente cargando | Hueco fijo y «Leyendo el pendiente…» | Esqueleto (importe y línea) a los 300 ms |

**Pendiente para 8b-D2:** los diálogos que la ficha hereda de `EditarAlumnoComponent` (pestañas Deportes, Pagos, Datos y
contacto, Documentos y notas, y el «⋯» del carnet) siguen con SweetAlert2. El listado nuevo y la ficha no saben si un
cargo es mensualidad o derecho de examen (el DTO de `pendientes-por-alumno` no lo trae): su diálogo dice «Cobrar
cargo»; el Inicio, que sí lo sabe, «Cobrar mensualidad» o «Cobrar derecho de examen».

**Además:** el interceptor de errores de hoy abre un SweetAlert2 con cada error HTTP; en las pantallas de hoy el aviso
propio (otro SweetAlert2) lo sustituía. En el panel nuevo, un aviso de error o un diálogo que responde a un error lo
cierran igual (`cerrarSweetAlertAbierto`), solo el del interceptor (clase `swal-error-http`) y solo en una pantalla
nueva. Dentro de una capa modal, los avisos no tapan sus botones (con el panel
lateral, a su izquierda; en una hoja, arriba). La nota «límite común de N por turno» de la ocupación ya no sale mientras
carga ni con error (decía «de 0»). En el móvil, pastillas y acciones de aviso de 44 px (el boceto las dibuja de 40 y 32)
por la regla de 44 a 375.

**Riesgos:** un SweetAlert2 que alguien abra con una capa modal nueva delante queda detrás (las herramientas y los
informes del listado ya no lo usan); la lista flotante del buscador de hoy va dentro del `<dialog>` solo donde se pide;
los errores de carga de los bloques siguen pasando también por el SweetAlert2 del interceptor (como en 8b-2 y 8b-3a).

**Notas para la PR:** inventario de arriba; bundle inicial 1.456.347 bytes tras la ronda 2 (1.456.078 en la ronda 1,
+1.612 frente a 1.454.466; la ronda 2 suma 269, la clase del interceptor); contrastes de las parejas nuevas; capturas
apagado idénticas.

## 21 ter. Pieza 8b-C: color de la escuela

Rama `feature/e8b-c` (sale de `feature/e8b-d1`). Solo frontend, sin migración: atajo `desplegar`. Boceto aprobado,
variante «B · acento medido» (decisión 273): `AlumnosB.dc.html`, `InicioB.dc.html`, `MovilB.dc.html`,
`SierraB.dc.html` y `Paleta.dc.html`, con el `acento-borde` corregido (el del boceto, `#51A0AE`, daba 2,75:1 sobre el
fondo).

**Objetivo:** el color de la escuela llega a más sitios del panel nuevo sin teñir el fondo: un acento medido, derivado
del primario, que cumple siempre los contrastes. La cabecera, el botón principal, los estados, las disciplinas, los
cinturones, la ocupación y el diálogo rojo de peligro no cambian; las pantallas de hoy, tampoco.

**Regla (decisión 273):** P con tono H y croma C en OKLCH.
- `acento-suave`: H, L 0,95 y C 0,035.
- `acento`: H, C máx(C, 0,09) recortado a sRGB y L bajando desde 0,50 (pasos de 0,0025) hasta ≥ 4,5:1 sobre blanco y
  sobre `acento-suave`.
- `acento-borde`: H, C 0,08 y la L más alta que dé ≥ 3:1 sobre blanco, sobre el fondo (`#F5F5F2`) y sobre
  `acento-suave`.
- C < 0,01: `marca-primario-tinta` y `marca-primario-suave` del tema, y el `acento-borde` con la misma búsqueda de L y
  C = 0 (≥ 3:1 sobre blanco, el fondo y ese suave; `borde-control` daba 2,99:1 sobre `#F2F2F2`). `#808080`: `#8C8C8C`.
- Un primario que no es `#RRGGBB` (`''`, `'abc'`, `'#abc'`, `'#GGGGGG'`, `null`): el acento por defecto.

| Escuela | acento | acento-suave | acento-borde |
|---|---|---|---|
| Moiskimdo `#1B2B2E` | `#01707F` (5,79:1 blanco; 5,04 suave) | `#D5F5FC` | `#4595A3` (3,45 blanco; 3,16 fondo; 3,00 suave) |
| Sierra Sur de prueba `#3E9B4F` | `#0E782D` (5,61; 4,89) | `#E0F5E1` | `#67956B` (3,44; 3,15; 3,00) |
| Por defecto `#1A2230` | `#466396` (6,02; 5,20) | `#E6EFFF` | `#6F8ABB` (3,48; 3,18; 3,01) |

**Ficheros:**
- Nuevos en `F/panel-nuevo/`: `acento-escuela.ts` (OKLCH ↔ sRGB y contraste WCAG, en TypeScript puro; `acentoDe`) y
  `acento-escuela.directive.ts` (`AcentoEscuelaActual`, `escribirAcento`, `seguirAcento` y la directiva `pnAcento`).
- `MarcaEscuelaPanelService.aplicarEn` calcula el acento al aplicar la marca, escribe el tema y el acento en el `.pn`
  de la cabecera y deja los dos (`{tema, acento}`) en `AcentoEscuelaActual`; con otra sesión se vacía. Inicio, Alumnos y
  la ficha llevan `pnAcento` en su `.pn`; el diálogo y los avisos, `seguirAcento()` (sin `hostDirectives`, que añadía
  1.106 bytes al bundle inicial). Los dos escriben con `escribirTema` las ocho variables (ronda 2: antes solo el acento,
  y el botón principal de cada pantalla salía con `#1A2230`, contra la decisión 239). `tema-escuela.ts` (sin Angular)
  guarda `TemaMarca` y los nombres de las variables, para que el servicio y la directiva no se importen entre sí.
- `panel-nuevo.scss`: `--pn-acento`, `--pn-acento-borde` y `--pn-acento-suave` bajo `.pn` con los de `#1A2230`, y el
  foco de 3 px en `acento-borde` con 2 px de separación. Ningún token en `:root`.
- Acento en: entrada actual de los desplegables del menú y de la hoja «Más», entrada actual de la barra inferior,
  pestaña activa, filtros activos («Inactivos», «Solo aptos»), «Filas | Tarjetas», pastillas elegidas (método de pago y
  herramientas), página actual, secundarios («Informes», «Cobrar», «Cambiar foto», «Agregar deporte», «Cancelar»),
  botones de texto, enlaces («Ir a Tesorería», «Ver todos», «Ver grupo», la acción de los avisos), iconos de las
  entradas del Inicio y avatar de iniciales.
- Sin acento a propósito: «Diseño nuevo» (se abre también con el panel de hoy, sin marca: mantiene su foco de 2 px) y
  el diálogo destructivo (vuelve a los neutros de `#1A2230` con su foco de antes: `marca-por-defecto` y el nuevo
  `marca-suave-por-defecto`, fijos bajo `.pn`). Las acciones `peligro` y `bien` conservan el fondo neutro al pasar.
- Listas flotantes del buscador (ronda 2): la de las herramientas va dentro del `<dialog>` de Alumnos y pierde su
  `pn` propio (hereda el tema); la de la ficha va al `body` y lo lleva copiado con el input nuevo
  `searchDropdownVariables` de `SearchableSelectDirective` (vacío por defecto: las pantallas de hoy no cambian). La
  opción elegida, en `acento` sobre `acento-suave`.

**Tareas TDD:**
- [x] Specs en rojo antes del código: `acento-escuela.spec.ts` y los casos nuevos de `marca-escuela-panel.service.spec.ts`
  no compilaban (`Property 'acento' does not exist on type 'MarcaEscuelaPanelService'`); los de Alumnos, Inicio y
  ficha fallaban por los estilos (3 FAILED, 117 SUCCESS: filtro `rgb(232, 233, 234)` en vez de `rgb(213, 245, 252)`,
  pestaña sin subrayado, pastilla elegida rellena, etc.).
- [x] `acento-escuela.spec.ts`: tabla de casos (Moiskimdo, Sierra, `#1A2230`, gris puro, amarillo, rojo y azul claro);
  el acento por defecto; el tono se conserva; rejilla de más de 400 colores que cumple siempre 4,5:1 y 3:1.
- [x] `marca-escuela-panel.service.spec.ts`: el acento se escribe en el `.pn` (nunca en `documentElement` ni `body`);
  un `.pn` con `pnAcento` lo recibe sin pedir la marca otra vez, solo el acento, y lo pierde al cambiar de sesión.
- [x] `alumnos.component.spec.ts`: estilos calculados del filtro activo, «Informes», «Cobrar» de fila, el segmento
  elegido, la página actual, el avatar, el principal sin cambios y, en el diálogo «Cobrar», la pastilla elegida y
  «Cancelar».
- [x] `ficha-alumno.component.spec.ts`: pestaña activa (texto y subrayado), «Cambiar foto» y «Agregar deporte».
- [x] `inicio.component.spec.ts`: «Ir a Tesorería», iconos de «Accesos» y «Cobrar».
- [x] Ronda 2, en rojo antes del código (12 FAILED, 1664 SUCCESS: `--pn-marca-primario` vacío en la pantalla, principal
  `rgb(26, 34, 48)` en vez de `rgb(27, 43, 46)`, `#NANNANNAN` con una entrada no válida, borde gris 2,99:1, lista con
  `pn` propio, `NG0303` sin `searchDropdownVariables`, etc.):
  - `marca-escuela-panel.service.spec.ts`: la pantalla con `pnAcento` recibe las cinco `--pn-marca-*` de Sierra
    (`#3E9B4F`) y las pierde al cambiar de sesión; `escribirTema` escribe las ocho y con `null` las quita, y se niega
    fuera de un `.pn`; `AcentoEscuelaActual` guarda `{tema, acento}`.
  - `alumnos.component.spec.ts`: el principal con la tinta de Moiskimdo (`rgb(27, 43, 46)`); `ficha-alumno.component.spec.ts`:
    el `.pn` de la ficha con el tema y las listas flotantes de Deportes (en el `body`) con el tema copiado y la opción
    elegida en el acento.
  - `acento-escuela.spec.ts`: entradas no válidas (`''`, `'abc'`, `'#abc'`, `'#GGGGGG'`, `'#12345'`, `'#1234567'`, con
    espacio, `null`, `undefined`) dan el acento por defecto; caso gris: `#8C8C8C`, y ≥ 3:1 sobre blanco, el fondo y su
    suave en cuatro grises.
  - `confirmacion.service.spec.ts`: el diálogo normal con el tema de Sierra (principal `#24853A`, «Cancelar» en el
    acento); el de peligro en neutros (texto `#1A2230`, borde `linea-fuerte`, hover `#E8E9EA` y foco en la tinta neutra).
  - `avisos.service.spec.ts`: la acción del aviso en el acento de la escuela.
  - `diseno-nuevo.component.spec.ts`: «Diseño nuevo» sin variables de la escuela y en la tinta neutra con una marca aplicada.
  - `herramienta-panel.component.spec.ts`: foco de 3 px en `acento-borde` con 2 px de separación en el buscador; su
    lista, dentro del `<dialog>`, sin `pn` y con la opción elegida en el acento de la pantalla.
  - `searchable-select.directive.spec.ts`: `searchDropdownVariables` copia, cambia y quita las variables; sin él, nada.

**Contrastes nuevos (WCAG 2.2 AA):** acento sobre blanco, fondo, `superficie-2` y `acento-suave` ≥ 4,5:1 (Moiskimdo
5,79, 5,30, 5,54 y 5,04); `acento-borde` sobre blanco, fondo, `superficie-2` y `acento-suave` ≥ 3:1 (3,45, 3,16, 3,30 y
3,00). Sobre el suave neutro el anillo daba 2,85:1: las entradas de los menús con el foco pasan a `acento-suave`.

**Notas para la PR:** bundle inicial 1.456.347 bytes, igual que en `ffc88711`; capturas apagado antes y después
idénticas (16/16); encendido con Moiskimdo y con Sierra de prueba a 375, 768, 1024 y 1280.
Ronda 2: bundle inicial 1.456.347 bytes (sin cambio); karma 1676 SUCCESS; apagado antes (`f14da53e`) y después 16/16
idénticas en bytes; encendido con Moiskimdo y Sierra de prueba (Inicio, Alumnos, ficha, «Cobrar» y la lista del
buscador abierta) a 375, 768, 1024 y 1280: «Nuevo alumno» y «Marcar como pagado» con la tinta de la escuela.

## 21 quater. Pieza 8b-D2 (a): «Generar informe», visor de PDF y errores de carga

Rama `feature/e8b-d2a` (sale de `develop` en `0c0dd45b`). Solo frontend, sin migración: atajo `desplegar`. Sin boceto
nuevo: piezas de 8b-D1 (capas, panel lateral, botón ocupado, esqueleto y avisos) y tokens de DESIGN.md.

**Objetivo:** lo que 8b-D1 dejó con SweetAlert2 o el cargador global en el listado nuevo y la cabecera: el diálogo
«Generar informe», el visor de PDF, «Sesión cerrada con éxito», el SweetAlert2 del interceptor sobre los errores de
carga que ya enseña un bloque y el cargador global de los PDF. Las pantallas de hoy no cambian.

**Ficheros:**
- Nuevos en `F/panel-nuevo/`: `alumnos/herramientas/generar-informe-panel.component.*` (sección «Generar informe» del
  panel lateral), `alumnos/herramientas/informes-alumnos-panel.ts` (`InformesAlumnosPanel`), `visor-pdf/` (`VisorPdfComponent`)
  y `carga/lecturas-panel.service.ts` (`contextoErrorLocal`, `LecturasPanelService`).
- De hoy, sin cambio visible ni de comportamiento: `informe-modal/categorias-informe.ts` (los grupos y descripciones
  salen de `InformeModalComponent`, que los usa igual), `informes-alumnos.ts` (miembros `protected`, `generarReservasPlaza`,
  `descargarDeudas` y el aviso del fallo de temporadas como parámetro, por defecto el toast de hoy) e
  `InformePdfService` (`mostrarCarga`/`ocultarCarga` protegidos con el `LoadingService` de siempre).
- `EndpointsService` no se toca (CRLF): `LecturasPanelService` repite la misma petición de `obtenerAlumnos` y
  `obtenerLimiteTurno` con el contexto (spec que las compara).

**Tareas TDD:**
- [x] En rojo antes del código: los dos specs nuevos no compilaban (`Cannot find module './generar-informe-panel.component'`,
  `'./visor-pdf.component'`); con esbozos vacíos, 27 FAILED y 131 SUCCESS en los specs tocados. El caso del interceptor
  (401 con `ERROR_HANDLED_LOCALLY`) pasó ya en rojo: el token no salta la sesión caducada.
- [x] `generar-informe-panel.component.spec.ts`: grupos, orden, nombres y descripciones iguales a los de
  `InformeModalComponent`; cada tipo con «Ver PDF» y «Descargar PDF» hace la misma petición, con el mismo título y nombre
  de archivo, que `InformesAlumnos` de hoy; `soloActivos=false`; deudas («Descargar PDF» y «Descargar CSV» frente al
  SweetAlert2 «PDF/CSV» de hoy) y reservas de plaza (temporada en la fila, la actual por defecto, frente al SweetAlert2 de
  hoy) sin abrir SweetAlert2; sin temporadas, botones deshabilitados; el fallo al leerlas, aviso; botón ocupado, una
  petición a la vez y panel bloqueado (Esc, `cancel`, X y «Cerrar»); error en un aviso dentro del panel; cerrar no envía
  nada; nunca `LoadingService`.
- [x] `visor-pdf.component.spec.ts`: `<dialog>` modal en el `.pn`, en `PilaCapas` y con el scroll bloqueado; cabecera
  de 56 px con título, «Descargar PDF» y X (foco inicial en la X, como hoy); `min(1040px, 100vw - 48px)` por
  `100dvh - 48px` y hoja a pantalla completa por debajo de 768 px; descarga con el nombre de hoy y la URL liberada; varios
  documentos y aviso parcial; Esc (primero sale de la expansión), `cancel`, X y velo cierran, un clic dentro no; cierre
  del navegador una vez; foco contenido y de vuelta al disparador; esqueleto a los 300 ms.
- [x] `alumnos.component.spec.ts`: «Generar informe…» abre el panel (no `app-informe-modal`) y «Ver PDF» abre el visor
  encima con el foco de vuelta a su botón; `inicio`, `alumnos`, `ficha` y `lecturas-panel`: el contexto en las lecturas
  de bloque y solo en ellas; `error.interceptor.spec.ts`: el 401 sigue cerrando la sesión; `cabecera-panel`: cerrar sesión
  sin SweetAlert2 ni avisos.

**Inventario** (por qué: decisión 269; un SweetAlert2 o el cargador global quedarían detrás de una capa modal):

| Pantalla | Qué | Antes | Ahora |
|---|---|---|---|
| Alumnos | «Generar informe…» | `app-informe-modal` (tipos con radio, «Solo alumnos activos», «Ver PDF» y «Descargar PDF» abajo) | Sección «Generar informe» del panel lateral (hoja en el móvil): los mismos grupos y filas, cada una con sus botones y botón ocupado |
| Alumnos | Formato de las deudas al descargar | SweetAlert2 «PDF / CSV» | «Descargar PDF» y «Descargar CSV» en su fila |
| Alumnos | Temporada de reservas de plaza | SweetAlert2 con selector (y «Cargando temporadas» / «Sin temporadas») | Campo «Temporada» en su fila; sin temporadas, la frase y los botones deshabilitados |
| Alumnos | Fallo al leer las temporadas | Toast de SweetAlert2 | Aviso de error con el mismo texto |
| Alumnos | Visor de PDF (informes y herramientas) | `app-informe-pdf-modal` | `pn-visor-pdf` con el mismo visor, documentos, aviso y descarga |
| Alumnos | PDF en curso (informes y herramientas) | Cargador global (`LoadingService`) | Botón ocupado; panel bloqueado |
| Cabecera | «Sesión cerrada con éxito» | SweetAlert2 al llegar a la web | Nada: la web pública no monta los avisos del panel nuevo |
| Inicio | Fallo del límite y del pendiente | Bloque de error + SweetAlert2 del interceptor | Solo el bloque de error |
| Alumnos | Fallo del listado | Bloque de error + SweetAlert2 del interceptor | Solo el bloque de error |
| Ficha (carnet) | Fallo del pendiente | «No se pudo leer el pendiente.» + SweetAlert2 del interceptor | Solo el texto con «Reintentar» |

**Además:** el visor no crea URL de objeto: hoy tampoco (pdf.js lee el Blob; la descarga crea y libera la suya al
momento); al cerrarlo se destruye la tarea de pdf.js como hoy. La barra del visor de hoy lleva los tokens del panel solo
dentro de `.pn-visor`. Siguen con el interceptor las lecturas cuyo fallo no enseña un bloque: grupos, turnos y totales
del Inicio (solo hay bloque de error si fallan los tres), aptos, distribución, el total de activos del listado y los
pendientes de su página («—»).

**Notas para la PR:** inventario de arriba; bundle inicial 1.455.529 bytes (suma de los ficheros iniciales del build de
producción; 1.456.347 en 8b-C): ningún fichero nuevo en el inicial; karma 1705 SUCCESS; capturas encendido a 375 y
1280 (panel, financieros, ocupado y visor).

## 21 quinquies. Pieza 8b-D2 (b): diálogos heredados de la ficha

Rama `feature/e8b-d2b` (sale de `develop` en `542fe10e`). Solo frontend, sin migración: atajo `desplegar`. Sin boceto
nuevo: piezas de 8b-D1 (diálogo, avisos, panel lateral, botón ocupado, `cerrarSweetAlertAbierto`) y tokens de DESIGN.md.

**Objetivo:** lo que 8b-D1 dejó pendiente en la ficha nueva (decisión 269): los SweetAlert2 que hereda de
`EditarAlumnoComponent` (111 `Swal.fire`, 1 `showValidationMessage` y 23 toasts), los diálogos propios de `DialogosAlumnoComponent` y la foto ampliada.
Con el interruptor apagado, la ficha de hoy no cambia: mismas opciones de SweetAlert2, mismo flujo, mismas peticiones.

**Ficheros:**
- Nuevo `editar-alumno/dialogos-ficha.port.ts`: `DIALOGOS_FICHA` (`InjectionToken`, `providedIn: 'root'`) con
  `fire(opciones, id?)`, `close`, `showValidationMessage`, `toastExito` y `toastError`; por defecto
  `DIALOGOS_SWEETALERT`, que pasa el mismo objeto a `Swal.fire` e ignora `id`.
- `editar-alumno.component.ts` (CRLF, sin reescribir) y `productos-alumno.component.ts` (finales mixtos, conservados):
  sustitución mecánica de `Swal.fire(`, `Swal.showValidationMessage(`, `showSuccessToast(` y `showErrorToast(` por el
  puerto; ningún import de `sweetalert2` ni de `toast.util`. Seis llamadas llevan `id`: `baja-o-eliminar`,
  `alta-o-eliminar`, `eliminar-deporte`, `desactivar-o-eliminar-deporte`, `eliminar-alumno` y `alta-deporte`. Productos lee
  el puerto de su `Injector` con `DIALOGOS_SWEETALERT` por defecto (un spec la construye fuera de contexto).
- Nuevos en `F/panel-nuevo/ficha/dialogos/`: `dialogos-ficha-panel.ts` (`DialogosFichaPanel`, el puerto del panel),
  `dialogos-alumno-panel.component.*` (`pn-dialogos-alumno`) y `foto-ampliada-panel.component.*` (`pn-foto-ampliada`).
- `FichaAlumnoComponent`: `providers: [{ provide: DIALOGOS_FICHA, useClass: DialogosFichaPanel }]`, pinta
  `pn-dialogos-alumno` y `pn-foto-ampliada` dentro de su `.pn` y sobrescribe `abrirModal`/`cerrarModal`.
- `DialogoComponent` (8b-D1), con opciones nuevas y opcionales: `bloques` (párrafos y listas), `campos` (pastillas,
  desplegable, texto, número, fecha, área, email; error en línea con `aria-invalid` y `aria-describedby`),
  `accionSecundaria` (la «denegar»), `comprobar` (validación síncrona o promesa con el botón ocupado) y `nombre`/`generico`
  (`data-dialogo`, `data-generico`). Sin ellas, igual que antes (su spec, en verde).

**Tareas TDD:**
- [x] En rojo antes del código: los tres specs nuevos y el de la ficha no compilaban (`Can't resolve
  './dialogos-ficha.port'`, `'./dialogos-ficha-panel'`, `'./dialogos-alumno-panel.component'`,
  `'./foto-ampliada-panel.component'`).
- [x] `dialogos-ficha.port.spec.ts`: el puerto por defecto llama a `Swal.fire` con el objeto idéntico (`toBe`) y un solo
  argumento, por categoría de la ficha de hoy (aviso, error, confirmación, denegar con html, selector, fecha, formulario
  html con `preConfirm` y `showValidationMessage`, toast) y en productos; `EditarAlumnoComponent` y
  `ProductosAlumnoComponent` no nombran `sweetalert2` ni `toast.util` en su código emitido; el puerto sí.
- [x] `dialogos-ficha-panel.spec.ts` (nunca `Swal.fire` ni `Toast.fire`): avisos de éxito, información y error (con la
  regla del texto); confirmación con foco en la acción; destructivo con foco en «Cancelar»; denegar como segunda acción
  (en peligro si borra); select de hasta 6 opciones en pastillas y de más en desplegable; radio; fecha y área con su
  validación en línea; formulario html por id con `preConfirm` leyendo el DOM; acción larga con botón ocupado y Esc,
  `cancel` y «Cancelar» sin efecto; tipo desconocido como confirmación genérica.
- [x] `dialogos-alumno-panel.component.spec.ts`: convocatorias, quitar de convocatoria, WhatsApp y agregar deporte en el
  panel lateral (hoja por debajo de 768 px) con los métodos de la ficha; «Actualizar grado» en el diálogo; caja de luz
  (modal, `min(90vw, 960px)` × `90dvh`, X de 44 px con el foco, Esc, X y velo, foco de vuelta).
- [x] `ficha-alumno.component.spec.ts`: las comparaciones con la ficha de hoy siguen igual (opciones del puerto = las de
  SweetAlert2 de hoy, mismos toasts y escrituras); agregar deporte, actualizar grado, WhatsApp y foto con las capas
  nuevas; bloque «diálogos en el panel nuevo» sin simular el puerto: la ficha nueva da `DialogosFichaPanel` y la de hoy
  `DIALOGOS_SWEETALERT`; eliminar documento, dar de baja y eliminar alumno desde «⋯», reservar plaza (pastillas y dos
  confirmaciones), fecha de convocatoria y error de convocatorias, con las mismas peticiones que hoy y sin SweetAlert2.

**Inventario** (por qué: decisión 269; detalle por llamada en el informe de la pieza):

| Grupo de diálogos (ficha de hoy) | Llamadas | En el panel nuevo |
|---|---|---|
| Éxitos (con o sin temporizador) | 34 | Aviso de éxito con la frase (sin frase, el título) |
| Información («Información», «No hay deportes disponibles») | 3 | Aviso de información |
| Errores (45) y advertencias (5) sin «Cancelar» | 50 | Aviso de error (`role=alert`, sin cierre solo); título no genérico delante: «No se pudo subir el documento. …» |
| Toasts de `toast.util` | 23 | `toastExito` / `toastError` → avisos |
| Confirmaciones (activar, desactivar, marcar pagado, recompensa, derecho de examen, reservas ×3, actualizar datos) | 9 | `ConfirmacionService` con título, frase y botones de hoy, foco en la acción |
| Destructivas (eliminar documento, quitar de convocatoria, eliminar deporte, eliminar alumno) | 4 | Diálogo de peligro, foco en «Cancelar» |
| Con «denegar» (baja o eliminar, alta o eliminar, desactivar o eliminar deporte, ¿abonada? ×2, teléfono) | 6 | Segunda acción del diálogo; en peligro (botón de texto) si borra; a 500 px para que quepan los tres |
| Selector (¿para qué deporte?, convocatoria con «Crear nueva») y radio (cinturón rojo) | 3 | Pastillas hasta 6 opciones; desplegable con más; error en línea |
| Fecha (nueva convocatoria) | 1 | Campo de fecha con su etiqueta; error en línea con el foco |
| Formulario html (dar de alta: `#deporte-select` + `preConfirm` y su `showValidationMessage`) | 1 + 1 | Pastillas con los mismos valores y un `input` oculto con el id: el `preConfirm` de hoy lee lo mismo; `showValidationMessage` en línea |
| `DialogosAlumnoComponent`: convocatorias, quitar de convocatoria, WhatsApp, agregar deporte | 4 | `pn-panel-lateral` (hoja en el móvil) con los mismos métodos |
| `DialogosAlumnoComponent`: actualizar grado | 1 | Diálogo con pastillas o desplegable y fecha |
| `FotoAmpliadaComponent` | 1 | Caja de luz sobre la capa modal |

**Genéricos y sin traducir:** ninguna llamada de la ficha cae en la confirmación genérica (solo `input` de tipos que el
diálogo no pinta: file, range, checkbox…; con spec). `didOpen` (el buscador `attachSwalSelectSearch` del alta) no se
ejecuta en el panel. `close()` del panel no hace nada (la ficha no lo llama). La página de productos del alumno
(`alumnos/:id/productos`) es de hoy en los dos diseños: usa el puerto por defecto y sigue con SweetAlert2, igual que
`pedirAlPropietario`.

**Además:** «Actualizar grado» se cierra al aceptar (hoy el modal sigue abierto hasta la respuesta); si falla, el aviso de
error lo dice y se vuelve a abrir con el botón. Su frase dice el nombre del grado («Naranja»; hoy, el código «NARANJA»).
Un aviso o diálogo del panel que responde a un error cierra el SweetAlert2 del interceptor, como en 8b-D1.

**Notas para la PR:** inventario de arriba; bundle inicial 1.455.529 bytes (igual que en 8b-D2 (a)): ningún fichero
nuevo en el inicial; karma 1766 SUCCESS; capturas apagado antes (`542fe10e`) y después idénticas en bytes, 16/16 (carga,
baja, error, selector, fecha, formulario, toast y foto a 375 y 1280); encendido a 375 y 1280 (destructivo, pastillas y su
error, formulario, fecha, aviso de error, agregar deporte, actualizar grado y foto).

## 20 ter. Pieza 8b-B4 y piezas 8b-3l a 8b-3n (decisión 282)

Decisión del propietario del 23/10: los formularios que quedaron fuera de la 259 entran en la E8b. Van tras 8b-3k y antes de 8b-M.

- **8b-B4, boceto de la ronda 4** (solo diseño, sin código ni despliegue, como 8b-B3): alta de alumno, crear y editar
  grupo y turno, gestionar los alumnos del grupo y gestionar los productos de un alumno, cada una hoy frente a una o dos
  propuestas en el mundo de C, a 375, 768 y 1280 px, con datos ficticios e inventario por pantalla. El propietario elige
  una variante por pantalla y los inventarios elegidos se copian al Anexo A.
- **8b-3l, alta de alumno:** mismo cuerpo de alta que hoy campo a campo, cargos iniciales y documentos iguales; datos de
  menores.
- **8b-3m, formularios de grupos:** crear y editar grupo y turno y la pantalla de alumnos del grupo, con las mismas
  llamadas que hoy; enlaza con Grupos (8b-3c).
- **8b-3n, productos de un alumno:** la pantalla «Gestionar productos» que hoy abre la ficha (pestaña Pagos): asignar,
  editar fecha de asignación, cantidad y precio, cobrar con el mismo diálogo y llamada que Tesorería, quitar; mismos
  cuerpos que hoy.
- Siguen fuera: pantallas de plataforma (superadmin) y, por la 260, Mis grupos y Mi caja hasta 8b-Z.

## 21 sexies. Pieza 8b-M: mascota en el panel (decisiones 274 y 275)

Rama `feature/e8b-m`, tras 8b-3k y antes de 8b-Z. Solo frontend, sin migración: atajo `desplegar`. Fuente:
`mascota-app/` (gota de esfuerzo: poses en SVG, entre ellas los 8 estados añadidos el 23/10 —agradecer, animar, buscar, confirmar, esperar, invitar, leer y prepararse—, compacta de 32 px, monocroma y `LEEME.txt` con las reglas de uso; los renders 3D de `mascota-app/renders-3d/` no van al repositorio).

**Objetivo:** `pn-mascota` (pose como entrada) con los SVG inline; la cinta, el gorro y el confeti toman el acento de
8b-C (`--school-accent` enlazado al token). Animaciones CSS cortas: aparecer, saludar, celebrar, dudar, descansar y
cumpleaños (de `gota-cumpleanos-inicio` a `gota-cumpleanos`); con `prefers-reduced-motion`, la pose quieta. Decorativa
(`aria-hidden`) si el texto contiguo dice lo mismo. Nada en el bundle inicial.

**Dónde:** estados vacíos de las pantallas nuevas, cierre de tareas largas (mes cobrado entero, mensualidades
generadas) y errores sin conexión o página no encontrada. Nunca en dinero, bajas ni errores serios.

**Fuera:** «primera visita a una pantalla» (hay que decidir si se recuerda en el navegador o en el servidor), área de
familias, web pública y cumpleaños de las familias (otras superficies, su propia entrega). PNG o GIF solo para
correos y redes, exportados de los mismos SVG.

## 22. Pieza 8b-Z: retirada de lo viejo

Solo cuando el propietario lo apruebe (R6), tras un periodo con el valor por defecto `NUEVO`. Rama `feature/e8b-z`.
Sin migración: la tabla del interruptor se queda (su borrado, con la contracción).

**Objetivo:** las rutas rediseñadas pasan a tener una sola entrada (la nueva); se borran los componentes de hoy que ya
no usa nadie, el guard y la rama de `skeleton`; `LOCALE_ID 'es-ES'` global (decisión 258); el menú de cuenta pierde
«Diseño nuevo» y «Volver al diseño anterior»; los estilos compartidos de gestión salen del inicial si ya no los usa
ninguna pantalla (con la 259 solo quedarían los formularios que sigan como hoy). La sesión solo-profesor sigue fuera del
interruptor hasta aquí (decisión 260); qué le toca se decide en R6.

**Tareas TDD:** specs de rutas (una sola entrada), batería completa, presupuesto (anotar lo que baja), capturas de
todas las pantallas de gestión a 375, 768 y 1280.

**Riesgos:** borrar algo que aún usa una pantalla no rediseñada (búsqueda de usos antes de cada borrado); el cambio
global de formato en pantallas no rediseñadas.

---

## 23. Revisiones del propietario

- **R1 (tras 8b-1):** capturas idénticas y cifras del presupuesto. Nada que ver en la aplicación.
- **R2 (tras 8b-I y 8b-T desplegadas):** el superadmin pone el color de Moiskimdo; el propietario se activa el diseño
  nuevo y ve su pantalla de asignación. Nadie más nota nada.
- **R3 (tras 8b-N):** navegación con datos reales, en el móvil y en el escritorio; el gestor que el propietario elija
  prueba las 13 entradas a dos clics.
- **R4 (tras cada pantalla, 8b-2 a 8b-3e):** con datos reales a 375, 768 y 1280; la tabla del Anexo A como lista.
- **RB3 (ronda 3, antes de 8b-3f): hecha (21/10/2026).** El propietario eligió, pantalla a pantalla, la propuesta de
  8b-B3 (decisión 261; boceto en https://claude.ai/artifact/5ynw1nWRYKQDJ8LdVqfq1w) y los inventarios elegidos están en el Anexo A.
- **R4b (tras cada pantalla, 8b-3f a 8b-3k):** como R4.
- **R5 (antes de «para todos»):** el propietario pone el valor por defecto `NUEVO` y lo vigila unos días.
- **R6 (antes de 8b-Z):** aprobación explícita para retirar lo viejo.

## 24. Despliegue (decisión 255)

Cada pieza se despliega **apagada** según se fusiona: nadie la ve hasta que el propietario se la active, y él la prueba
con datos reales (decisión 236). Orden, cada una con su línea en `checklist-despliegue.md`:

- 8b-1 sola (cambia el CSS global de todos, aunque no se vea): atajo `desplegar`; comprobar portada, login, familias y
  panel en el móvil.
- 8b-I con **V31_10** → atajo `desplegar_migrar`; después, solo lectura: Flyway en `31.10` y
  `SELECT COUNT(*) FROM diseno_panel_usuario` = 0. El código anterior funciona sobre este esquema (tabla que no conoce).
- 8b-T (sin migración): `desplegar`; el superadmin pone `#1B2B2E` en «Logo y color» (decisión 257).
- 8b-N, 8b-2 y 8b-3a a 8b-3e: `desplegar`, apagadas.
- 8b-B3 no se despliega (solo diseño); con RB3 hecha, 8b-3f a 8b-3k: `desplegar`, apagadas. Las de dinero o permisos
  (8b-3g, 8b-3i, 8b-3j, 8b-3k) llevan en su línea una prueba manual en la escuela de pruebas con el interruptor
  encendido (cobrar o deshacer un cargo ficticio; cambiar un rol ficticio) antes de activársela a nadie.
- 8b-Z: `desplegar`, con capturas de todas las pantallas de gestión.

Vuelta atrás: hasta 8b-Z, apagar el interruptor (o quitar el valor por defecto) devuelve a cada usuario a lo de hoy sin
desplegar.

## 25. Decisiones tomadas

El propietario respondió las preguntas abiertas de la primera versión de este plan (21/10/2026):

- **255** (pregunta 1). Despliegue por piezas, apagadas según se fusionan (§24).
- **256** (preguntas 2, 3 y 4). Solo el propietario activa el diseño nuevo a otros y el valor para todos; cada usuario
  con permiso puede volver al anterior solo para sí mismo; la entrada «Diseño nuevo» va en el menú de cuenta del panel
  de hoy, visible solo para el propietario.
- **257** (preguntas 5 y 6). El `#1B2B2E` de Moiskimdo lo pone el superadmin desde «Logo y color», sin migración de
  datos; una escuela sin color usa `#1A2230`.
- **258** (preguntas 7 y 8). `LOCALE_ID 'es-ES'` solo en las pantallas nuevas hasta 8b-Z; «Para todo el equipo» se
  construye en 8b-I, oculto hasta R5.
- **259** (preguntas 9 y 10). El panel nuevo no lleva el pie de la web pública: los enlaces legales y «Configurar
  cookies» van en «Más» y en el menú de cuenta. **Cambio de alcance:** las pantallas de gestión no rediseñadas
  (Eventos, Productos, Preinscripciones, Solicitudes, Caja de sede, Configuración y Registro de acciones) entran en la
  E8b, con boceto previo (8b-B3) y piezas 8b-3f a 8b-3k.
- **260** (preguntas 11 y 13). El login diferido queda solo como reserva de presupuesto; la sesión solo-profesor queda
  fuera del interruptor hasta 8b-Z.
- Pregunta 12 (el diálogo de Convocatorias que asignaba «Por Antigüedad» al cancelar): resuelta en el hotfix 2.

Ronda 3 de bocetos (21/10/2026), tras publicar https://claude.ai/artifact/5ynw1nWRYKQDJ8LdVqfq1w:

- **261** (12:00). «Eventos: P1 (lista densa ordenable), arreglando el móvil, que se ve descolocado. Editar evento: P2
  (vista previa de la web al lado). Productos: P2 (agrupados por tipo). Preinscripciones: P1 (lista con detalle al
  lado). Solicitudes: P1 (acciones en la fila). Caja de sede: P2 (pestañas Por recibir, Entregas y Movimientos).
  Configuración: P1 en móvil (secciones seguidas) y P2 en tablet y escritorio (índice de ajustes a la izquierda).
  Registro de acciones: P2 (cronología por días con detalle). Con esto la dirección de todas las pantallas de la E8b
  está cerrada.» Marca RB3 y 8b-B3 como hechas.
- **262** (12:02). «En las pantallas nuevas los roles se muestran como "Familia", "Gestión" y "Administración". Solo
  cambia el texto visible; los roles, permisos y valores internos no cambian.»
- **263** (14:38). En las pantallas nuevas `ROLE_PROFESOR` se muestra como «Profesorado» y `ROLE_RESPONSABLE_SEDE` como
  «Responsable de sede». Solo texto, en el mismo ayudante que la 262.

---

## Peticiones del propietario del 23/10 (sin pieza asignada)

Recogidas en el roadmap como decisiones pendientes 278 a 281. Se planifican como piezas propias cuando se decidan los puntos abiertos; ninguna entra en las pantallas 8b-3h a 8b-3k.

1. **Eventos con orden automático por fecha (278).** Primero los próximos, del más cercano a hoy al más lejano. Cuando un evento se realiza pasa solo detrás del último próximo, como el realizado más reciente; los realizados van del más reciente al más antiguo. Por decidir: si el orden manual de hoy (subir, bajar, arrastrar y «Guardar orden», 8b-3f) desaparece o solo desempata eventos de la misma fecha o sin fecha, y si el orden se calcula en la API (web pública y panel) o solo en la web. Toca la web pública: mismas pruebas de apagado que 8b-3f.
2. **Recibo de pago (279).** Al marcar pagado un producto, opción de generar un recibo en PDF con el nombre y el logo de la escuela (8b-T); también desde Tesorería, la ficha y pantallas parecidas para un cobro ya hecho. Por decidir: numeración del recibo, datos de la escuela que salen (razón social, NIF, dirección), si el recibo se guarda o se genera al vuelo y quién puede generarlo. Toca dinero y backend (PDF): tier careful.
3. **Mensualidades sin Pilates (280).** Al asignar mensualidades generales y cargas parecidas no se asigna mensualidad a Pilates, que está en la app solo para la ocupación por plazas. Propuesta para la plataforma multideporte (PRODUCT.md): un ajuste por disciplina «no genera mensualidad» en vez de excluir Pilates en el código, con Pilates marcado en Moiskimdo. Toca dinero: tier careful, con migración si el ajuste va en base de datos.
4. **Reparto con el profesor titular (281).** En Moiskimdo, Defensa Personal Femenina reparte lo cobrado entre el propietario y el profesor titular por porcentaje; casi siempre cobra el propietario y le paga su parte, y a veces cobra el profesor. Hoy la app no lo modela: se registra todo como ingreso de la escuela y la parte se paga fuera; si cobra el profesor, desde «Mi caja», entregando todo (o anotando su parte en la entrega, que la caja ve como descuadre). Propuesta: un porcentaje del profesor titular por disciplina o grupo; la app calcula su parte de cada cobro, la descuenta en sus entregas cuando cobra él y dice cuánto se le debe cuando cobra el propietario. Por decidir: porcentaje por disciplina, grupo o profesor; sobre qué cargos (mensualidades, matrícula, exámenes); cómo se liquida y se registra el pago al profesor. Toca dinero y base de datos: migración y tier careful.

## Anexo A: inventarios «nada se pierde» (decisión 232)

Dónde queda en la pantalla nueva cada cosa que se ve hoy, según los bocetos aprobados (ronda 1: Alumnos, Tesorería y
Convocatorias de C; ronda 2: Inicio I1, Ficha F1 y Grupos; ronda 3: Eventos, Editar evento, Productos,
Preinscripciones, Solicitudes, Caja de sede, Configuración y Registro de acciones, con la variante elegida en la decisión
261). «Añadido» es lo nuevo. Cada PR de pantalla lo copia y lo marca fila a fila.

Los inventarios de la ronda 3 salen de la página de comparación de 8b-B3
(https://claude.ai/artifact/5ynw1nWRYKQDJ8LdVqfq1w), columna de la variante elegida, sin quitar ninguna fila. Donde la
página dice «Igual» sobre la variante, aquí se copia el texto de la otra variante o «Igual que hoy», para que cada
tabla se entienda sola. Configuración lleva dos columnas porque elige una variante por ancho.

### Inicio (I1)

| Hoy | En la pantalla nueva |
|---|---|
| Título con la fecha de hoy | «Inicio» y la fecha |
| Saludo con el nombre del usuario | Botón de cuenta de la cabecera |
| Botón «Actualizar ahora» | Junto al título |
| Alerta: cobros pendientes del mes | «Pendiente de cobro»: todo lo vencido, por tipo, mes y sede |
| Alerta: alumnos aptos para examen | «Accesos»: aptos para examen |
| Alerta: eventos sin publicar | «Accesos»: eventos sin publicar |
| Otras alertas según el estado | Lista en «La escuela en cifras» |
| Tarjeta total de alumnos (activos e inactivos) | «La escuela en cifras» |
| Tarjeta grupos y turnos | «La escuela en cifras» |
| Tarjeta eventos y visibles | «La escuela en cifras» |
| Tarjeta aptos para examen | «La escuela en cifras» |
| Alumnos por disciplina | Tabla en «La escuela en cifras» |
| Porcentaje y total de inscripciones | Tabla en «La escuela en cifras» |
| Eventos activos y «Ver todos» | Bloque «Eventos activos» |
| Operaciones prioritarias (6) | «Operaciones y accesos» |
| Acceso rápido (7) | «Operaciones y accesos» |
| Menú: accesos, menú lateral y cuenta | 6 entradas con desplegables; barra inferior por debajo de 1024 px |
| Añadido: Ocupación de la semana (día × franja) | Arriba a la izquierda |
| Añadido: Clases de hoy | «Hoy» |

### Alumnos (C)

| Hoy | En la pantalla nueva |
|---|---|
| Título «Gestión de alumnos» | «Alumnos» con el total |
| Botón «Nuevo alumno» | Botón principal, arriba |
| «Generar informe» (diálogo con tipos de informe) | Menú «Informes», primera opción |
| Buscador por nombre o apellidos | Barra de filtros |
| Filtro por deporte | Desplegable |
| Mostrar u ocultar inactivos | Botón «Inactivos» |
| Solo aptos para examen | Botón «Solo aptos» |
| Contador de alumnos | «N alumnos» y subtítulo |
| Cambio de vista tarjetas / tabla | «Filas | Tarjetas» |
| Foto | Inicio de la fila |
| Nombre y apellidos (enlace a la ficha) | Línea 1 |
| Nº de expediente | Línea 1 |
| Edad | Línea 1 |
| Nº de deportes | Cifra en círculo, línea 2 |
| Tarifa principal | Línea 2 |
| Antigüedad principal | Línea 2 |
| Licencia (con, sin, caducada, no aplica) | Pastilla, línea 2 |
| Disciplinas con su color (vista tabla) | Línea 2 con cinturón y grado |
| Detalle: deporte y estrella de principal (marcar como principal) | Fila desplegable |
| Detalle: grado con su color y tiempo con el grado | Fila desplegable |
| Detalle: tarifa | Fila desplegable |
| Detalle: fecha de alta | Fila desplegable |
| Detalle: antigüedad | Fila desplegable |
| Detalle: categoría | Fila desplegable |
| Fecha de baja (con inactivos) | Línea 1 |
| Editar | Nombre enlazado y «Editar ficha» en la fila abierta |
| Vista usuario | Fila abierta |
| Dar de baja / dar de alta | Fila abierta |
| Paginación (hoy 6 por página, 2 en el móvil) | 25 por página |
| Gestión de mensualidades y licencias (plegado) | Pie y menú «Informes» |
| Listado de asistencia (PDF por días y grupos) | Pie y menú «Informes» |
| Listado de mensualidades (PDF) | Pie y menú «Informes» |
| Añadido: Pendiente de cobro del alumno | A la derecha, con «Cobrar» |

### Ficha del alumno (F1 «Carnet»)

| Hoy | En la pantalla nueva |
|---|---|
| Título «Perfil del alumno» y subtítulo | El nombre del alumno en el carnet hace de título; el subtítulo genérico se quita |
| Volver | «Alumnos» en la barra |
| Mostrar todos / solo activos | Casilla «Incluir inactivos» en la barra |
| Foto (se amplía al pulsar) | Carnet, 240×300 (184×230 en el móvil) |
| Disciplinas del alumno | Franja de cinturones y lista con grado |
| Nombre, expediente y estado | Carnet |
| Editar imagen / eliminar imagen | «Cambiar foto» y «Quitar» bajo la foto |
| Paginación entre alumnos | «1 de N» con anterior y siguiente en la barra |
| Deportes: una pestaña por deporte (principal e inactivo) | Pestaña «Deportes», un bloque por disciplina con su marca |
| Agregar deporte, dar de baja o de alta | Cabecera de «Deportes» |
| Marcar principal, activar, desactivar y eliminar deporte | Cabecera de cada disciplina |
| Estadísticas (alta, alta inicial, antigüedad, estado, baja) | Caja «Alta y antigüedad» |
| Tarifa (tipo, cuantía, rol y grupo familiar) | Caja «Tarifa» |
| Grado (actualizar, fecha, apto, convocatorias, recompensa, derecho de examen) | Caja «Grado»; grado y tiempo también en el carnet |
| Licencia federativa (tiene, número, fecha) | Caja «Licencia» y junto al carnet |
| Competidor (categoría, peso y fechas) | Caja «Competidor» |
| Grupos y turnos (quitar, añadir grupo) | Caja «Grupos y turnos» |
| Información personal (nombre, apellidos, nacimiento, DNI, dirección, discapacidad, «Editar») | Pestaña «Datos y contacto» |
| Productos y pagos (tabla, cobrar, paginación, «Gestionar productos», «Reservar plaza») | Pestaña «Pagos»; pendiente con «Cobrar» en el carnet |
| Contacto (email, teléfonos, responsable legal y su DNI) | Pestaña «Datos y contacto» |
| Información adicional (deportes activos, baja general, autorización web, observaciones y guardar) | Pestaña «Documentos y notas» |
| Documentos (ver, descargar, eliminar, ver más, subir, WhatsApp) | Pestaña «Documentos y notas» |
| Guardar o cancelar cambios al editar | Igual que hoy |
| Diálogos: convocatorias, WhatsApp, agregar deporte, actualizar grado | Sin cambios |
| Añadido: Pendiente, grado y licencia sin bajar | Carnet |
| Añadido: Acciones del alumno juntas (editar, vista de familia, WhatsApp, dar de baja y eliminar en «⋯») | Pie del carnet |

### Grupos (estructura de C con tarjetas de A)

| Hoy | En la pantalla nueva | 8b-3c |
|---|---|---|
| Título «Grupos y turnos» | Título con el total de grupos y turnos | Hecho |
| Nuevo turno | Arriba a la derecha | Hecho |
| Nuevo grupo | Botón principal | Hecho |
| Alumnos por tipo de grupo (abre la lista) | Fila de botones con «ver alumnos» | Hecho |
| Nombre del grupo | Cabecera de la tarjeta | Hecho |
| Rango de edad | Dato «Edad» de la tarjeta | Hecho |
| Turnos: número y lista (día y hora) | Horario resumido a la vista; lista al desplegar «Turnos» | Hecho |
| Editar turno | Lápiz en cada turno (desplegado) | Hecho |
| Quitar turno del grupo | Cruz en cada turno (desplegado) | Hecho |
| Añadir turno (turnos sin grupo) | Desplegable dentro de «Turnos» | Hecho (desplegable + «Añadir», ocupado) |
| Botón «Rango edad» | Pie de la tarjeta | Hecho |
| Botón «Alumnos» | Pie de la tarjeta | Hecho |
| Botón «Editar» | Pie y nombre de la tarjeta | Hecho |
| Botón «Eliminar» | Papelera al pie | Hecho |
| Turnos sin grupo asignado (con «Editar») | Bloque al final | Hecho (ver nota de §12: hoy nunca sale) |
| Diálogo con los alumnos de un tipo | Sin cambios | Hecho: panel lateral (hoja en el móvil) con la lógica del de hoy heredada |
| Añadido: Alumnos de cada grupo y de cada disciplina | Dato «Alumnos» y total por disciplina | Hecho |
| Añadido: Filtro por disciplina | Segmentos con su número | Hecho |

### Tesorería (C)

| Hoy | En la pantalla nueva | 8b-3d |
|---|---|---|
| Título «Tesorería y cobros» y subtítulo | «Tesorería» y subtítulo | Hecho |
| Actualizado (fecha y hora) | En el subtítulo | Hecho |
| Filtro mes | Desplegable | Hecho |
| Filtro año (o todos los años) | Desplegable | Hecho (con «Todos» el mes se deshabilita, como hoy) |
| Filtro deporte | Desplegable | Hecho |
| Filtro estado | Pestañas Pendientes | Cobrados | Todos | Hecho (`pagado` false / true / sin él; `?estado=` de la dirección elige la pestaña) |
| Solo alumnos activos | Casilla | Hecho |
| Buscar (alumno, concepto o categoría) | Campo | Hecho (a los 300 ms, solo movimientos, como hoy) |
| Aplicar filtros | Botón | Hecho («Aplicar») |
| Restablecer | Botón | Hecho |
| Importe pendiente y nº de cobros | Resumen | Hecho |
| Importe cobrado, nº y devueltos | Resumen | Hecho |
| Total generado y movimientos | Resumen | Hecho |
| Alumnos con deuda y periodo | Resumen | Hecho |
| Título de la tabla, deporte y periodo | Cabecera de la lista | Hecho |
| Certificados (diálogo por alumno y año) | Menú «Exportar» | Hecho: panel lateral (hoja en el móvil) |
| Ver PDF | Menú «Exportar» | Hecho: `pn-visor-pdf` |
| Exportar PDF | Menú «Exportar» | Hecho |
| Exportar CSV | Menú «Exportar» | Hecho |
| Alumno (enlace a la ficha) | Línea 1 | Hecho |
| Deporte | Bajo el alumno | Hecho |
| Concepto | Columna | Hecho |
| Categoría | Bajo el alumno | Hecho |
| Fecha de cargo | «Cargado …» bajo el alumno | Hecho |
| Importe | A la derecha | Hecho |
| Estado (pagado, pendiente, devuelto) | Pastilla | Hecho |
| Fecha de pago | En la pastilla «Pagado el …» | Hecho |
| Notas (texto) | Bajo el concepto y menú «⋯» | Hecho |
| Marcar pagado | «Cobrar» en la fila | Hecho (`CobroCargoService`) |
| Cambiar fecha de pago (pagados) | Menú «⋯» | Hecho |
| Revertir o pedir revertir (pagados) | Menú «⋯» | Hecho («Revertir» el propietario, «Pedir revertir» la gestión; ninguno con devuelto, como hoy) |
| Mostrando X de Y movimientos | Pie de la lista | Hecho |
| Paginación (con más de una página) | Pie de la lista | Hecho |
| Aviso «No hay cobros para los filtros» | Igual que hoy | Hecho (`pn-bloque-vacio` con «Restablecer») |
| Añadido: Barra cobrado / pendiente y pendiente por sede | Barra y pendiente por sede | Barra hecha; pendiente por sede no: la API no lo trae (sin backend) |

### Convocatorias (C)

| Hoy | En la pantalla nueva | 8b-3e |
|---|---|---|
| Título «Gestión de convocatorias» | «Convocatorias» y subtítulo | Hecho |
| Crear convocatoria | Botón principal | Hecho (disciplina y fecha, y la confirmación de hoy) |
| Filtro por deporte | Pestañas con nº | Hecho (`tablist` con flechas, Inicio y Fin; disciplinas con grados) |
| Lista: disciplina y fecha de examen | Bloques | Hecho |
| Abrir una convocatoria | «Ver alumnos» | Hecho (la cabecera entera es el botón, con foco visible) |
| Eliminar convocatoria | Papelera en el bloque | Hecho (destructivo, foco en «Cancelar») |
| Alumnos inscritos, pagados y pendientes | Cifras del bloque | Hecho (también con el bloque cerrado) |
| Ver PDF | Menú «PDF» al pie | Hecho: `pn-visor-pdf` |
| Descargar PDF (con la fecha) | Menú «PDF» al pie | Hecho (la fecha en la opción y en el nombre de hoy) |
| Actualizar grados de todos los alumnos | Botón principal al pie | Hecho (confirma siempre) |
| Alumno (iniciales y nombre) | Lista | Hecho |
| Grado actual con su color | Con su cinturón | Hecho (muestra del color y nombre del grado) |
| Grado siguiente con su color | Con su cinturón | Hecho |
| Cuantía editable | Campo | Hecho (decimal es-ES) |
| Pagado / pendiente (se cambia) | Conmutador Pagado \| Pendiente | Hecho (la misma llamada que hoy) |
| Quitar alumno de la convocatoria | Cruz | Hecho (destructivo, foco en «Cancelar») |
| Aviso «No hay alumnos inscritos» | Igual que hoy | Hecho (`pn-bloque-vacio`) |
| Seleccionar alumno apto para examen | Desplegable al pie | Hecho (con buscador) |
| Agregar alumno | Botón al pie | Hecho (tipo de producto con elección explícita y grado rojo) |
| Cargar más alumnos | Botón al pie | Hecho |
| Paginación de convocatorias | Bajo los bloques | Hecho (5 por página, como hoy) |
| Aviso sin convocatorias con «Crear primera convocatoria» | Igual que hoy | Hecho (`pn-bloque-vacio`) |

### Eventos (P1: lista densa ordenable)

Pieza 8b-3f. Rutas de hoy: `eventosListar`. Componentes de hoy: `listado-eventos`.

| Hoy | En la pantalla nueva | 8b-3f |
|---|---|---|
| Título «Gestión de eventos» | «Eventos» con total y visibles | Hecho (con total y visibles) |
| Guardar cambios de orden (aparece al reordenar) | Aviso bajo el título que pasa a «Guardar orden» | Hecho (fijo bajo la cabecera al bajar) |
| Mostrar u ocultar la vista previa de la web | «Ver cómo queda en la web» | Hecho (capa con la vista pública: visibles en el orden actual) |
| Crear nuevo evento | Botón principal | Hecho |
| Imagen (se amplía) | Miniatura en la fila | Hecho (la miniatura se amplía en una capa con Esc) |
| Título del evento | Fila | Hecho |
| Fecha con su estado (próximo, hoy, pasado) | Columna de fecha con su estado | Hecho (Próximo, Hoy, Realizado y Sin fecha) |
| Visible u oculto | Interruptor «Visible en la web» | Hecho |
| Descripción | Bajo el título, dos líneas | Hecho |
| Subir, bajar y arrastrar para ordenar | Asa y flechas | Hecho (también con teclado, anunciado) |
| Ocultar o mostrar | Interruptor | Hecho (al momento, con la misma llamada) |
| Editar | Botón en la fila | Hecho |
| Eliminar | Menú «⋯» | Hecho (destructivo, foco en «Cancelar») |
| Aviso sin eventos y estado de carga | Igual que hoy | Hecho (`pn-bloque-vacio` y esqueleto) |

### Editar evento (P2: vista previa de la web al lado)

Pieza 8b-3f. Rutas de hoy: `eventosCrear`, `eventosEditar/:id`. Componentes de hoy: `crear-evento`, `editar-evento`.

| Hoy | En la pantalla nueva | 8b-3f |
|---|---|---|
| Título «Editar evento» y subtítulo | Igual, con enlace «Eventos» | Hecho («Crear nuevo evento» al crear) |
| Título del evento (obligatorio, máximo) y sus errores | Campo con ayuda y error debajo | Hecho (con contador) |
| Descripción (obligatoria) y sus errores | Campo con ayuda y error | Hecho (con contador) |
| Fecha del evento (opcional) y su ayuda | Campo | Hecho |
| Visible en la web (al crear) | Interruptor y vista previa | Hecho |
| Imagen: subir, formatos, vista previa y quitar | Sección «Imagen» | Hecho (HEIC, formato y tamaño en línea; eliminar con confirmación) |
| Documentos guardados: descargar y eliminar | Sección «Documentos» | Hecho (eliminar destructivo, foco en «Cancelar») |
| Documentos por subir: nombre, tamaño, quitar y «Subir N» | Sección «Documentos» | Hecho (al crear, se suben tras crear, como hoy) |
| Aviso de que los documentos son públicos | Ayuda de «Documentos» | Hecho |
| Cancelar | Barra fija de guardar | Hecho (no envía nada) |
| Actualizar evento (desactivado con errores, «Guardando…») | «Guardar cambios» en la barra fija | Hecho («Guardar cambios»; «Crear evento» al crear) |
| Estado de carga | Igual que hoy | Hecho (esqueleto y error con «Reintentar») |
| Añadido: Resumen de errores con enlaces a cada campo | Arriba | Hecho |
| Añadido: Vista previa de la tarjeta pública | Al lado del formulario | Hecho (al lado desde 1280 px; al final por debajo) |

### Productos (P2: agrupados por tipo)

Pieza 8b-3g. Rutas de hoy: `productosListar`, `productosCrear`, `productosEditar/:id`. Componentes de hoy: `listado-productos`, `crear-producto`, `editar-producto`.

| Hoy | En la pantalla nueva | 8b-3g |
|---|---|---|
| Título «Gestión de productos» | «Productos» y para qué sirven | Hecho |
| Crear producto | Botón principal | Hecho |
| Dar a Mi caja (seleccionados) | Barra fija al pie | Hecho (el PATCH de hoy con los ids) |
| Quitar de Mi caja (seleccionados) | Barra fija al pie | Hecho |
| Eliminar seleccionados | Barra fija al pie | Hecho (uno tras otro, como hoy; destructivo, foco en «Cancelar») |
| Filtro por concepto | Buscador | Hecho (a los 300 ms) |
| Filtro por tipo | Desplegable | Hecho |
| Filtro Mi caja | Desplegable | Hecho («Mi caja: todos \| sí \| no») |
| Limpiar filtros | Botón | Hecho |
| Total de productos | Junto a los filtros | Hecho |
| Chips de filtros activos con quitar | Bajo los filtros | Hecho |
| Seleccionar todos y cada uno | Casilla en cada producto | Hecho (y «Seleccionar los N» en cada grupo) |
| Ordenar por ID, concepto o precio | Menú de cada grupo | Hecho (seis opciones, con teclado) |
| ID | «nº» en la fila | Hecho |
| Concepto (enlace a editar) | Fila | Hecho |
| Precio | A la derecha | Hecho (es-ES) |
| Mi caja (interruptor) | Interruptor «Mi caja» | Hecho (al momento, como hoy) |
| Editar | Menú «⋯» | Hecho |
| Eliminar | Menú «⋯» | Hecho (destructivo, foco en «Cancelar») |
| Paginación | Grupos enteros, sin páginas | Hecho (las páginas de la API se leen todas) |
| Aviso sin productos o sin coincidencias | Igual que hoy | Hecho (`pn-bloque-vacio`) |
| Añadido: Tipo de cada producto | Grupos por tipo | Hecho (siete grupos; los vacíos no salen) |

### Preinscripciones (P1: lista con detalle al lado)

Pieza 8b-3h. Rutas de hoy: `preinscripciones`. Componentes de hoy: `preinscripciones-admin`.

| Hoy | En la pantalla nueva |
|---|---|
| Pestañas Solicitudes y Plantillas PDF | Igual que hoy |
| Título y «Actualizar» | Igual que hoy |
| Filtro temporada | Desplegable |
| Filtro deporte | Desplegable |
| Filtro estado | Desplegable |
| Buscar por DNI, referencia o nombre | Buscador |
| Filtrar | Botón |
| Referencia y DNI (o «Sin DNI») | Lista |
| Alumno o alumna | Lista |
| «Con observación» | Marca en la lista |
| Deporte | Lista |
| Temporada | Detalle |
| Estado (y puesto en la lista de espera) | Pastilla en la lista |
| Email inicial, aviso de cambio y confirmación final | Detalle: «Avisos por correo» con intentos |
| Paginación «1–6 de 6» | Bajo la lista |
| Detalle: DNI, teléfonos, discapacidad, deporte | Panel al lado |
| Horario solicitado y asignación vigente | Detalle «Horario» |
| Cambiar la asignación de turnos (cancelar y guardar) | «Cambiar la asignación» |
| Observación sobre el horario | Resaltada en el detalle |
| Firma presentada y descargar la solicitud firmada | Detalle «Firma y ficha» |
| Fichas de alumno compatibles | Detalle «Firma y ficha» |
| Revisar y finalizar alta (con su diálogo completo) | Botón principal del detalle |
| Reenviar solicitud original | Botón del detalle |
| Cancelar solicitud y eliminar definitivamente | Menú «Más» del detalle |
| Plantillas PDF (deporte, previsualizar, campos, historial) | Pestaña, sin cambios |
| Añadido: Hace cuánto llegó | En la lista |

### Solicitudes (P1: acciones en la fila)

Pieza 8b-3i. Rutas de hoy: `solicitudes`. Componentes de hoy: `solicitudes-bandeja`.

| Hoy | En la pantalla nueva |
|---|---|
| Título y subtítulo | Igual que hoy |
| Actualizar | Botón |
| Segmentos con su número | Pestañas |
| Tipo de solicitud | Pastilla |
| Estado y «Devolución pendiente» | Pastillas |
| Quién la pidió y cuándo | Bloque izquierdo |
| Sede | Bloque izquierdo |
| Sobre qué (concepto y alumno) | Columna central |
| Importe | Columna central |
| Motivo o nota | Cita bajo «sobre qué» |
| Recorrido del encargo | Barra de pasos |
| Resuelta por y cuándo; respuesta o motivo del rechazo | En la fila (Resueltas) |
| Aceptar (con el verbo de cada tipo) | Botón principal de la fila |
| Denegar o rechazar | Botón de la fila |
| Retirar (las propias) | En la fila |
| Marcar encargado, marcar recibido, registrar devolución | En la fila |
| Error con «Reintentar» y vacíos de cada segmento | Igual que hoy |
| (Solo en P2, no entra: «Qué pasa al aceptar») | — |

### Caja de sede (P2: pestañas Por recibir, Entregas y Movimientos)

Pieza 8b-3j. Rutas de hoy: `cajaSede`. Componentes de hoy: `caja-sede`.

| Hoy | En la pantalla nueva |
|---|---|
| Título y subtítulo | Igual que hoy |
| Sede | Desplegable bajo el título |
| Periodo | Desplegable bajo el título |
| Saldo de caja | Resumen |
| Pendiente de entregar | Resumen |
| Recibido en el periodo | Resumen |
| Diferencias | Resumen |
| Efectivo en mano: profesor, cobros, importe, desde | Pestaña «Por recibir» |
| Recibir entrega: cobros, debería entregar, has contado, faltan o sobran, nota obligatoria, cancelar y recibir | Igual, en «Por recibir» |
| Entregas: fecha, de, recibió, contado, diferencia, nota | Pestaña «Entregas» |
| Entregas anuladas tachadas con motivo y correcciones | Igual que hoy |
| Anular o pedir anular una entrega | En la fila |
| Mostrando X de Y entregas | Pie de la pestaña |
| Movimientos: fecha, tipo, alumno, importe, nota, anulados | Pestaña «Movimientos» |
| Apuntar movimiento (tipo, importe, alumno, nota) | Botón de la pestaña |
| Anular o pedir anular un movimiento | En la fila |
| Errores con «Reintentar», sin sedes y vacíos | Igual que hoy |

### Configuración (P1 en móvil, P2 en tablet y escritorio)

Pieza 8b-3k. Rutas de hoy: `configuracion-sistema`. Componentes de hoy: `configuracion-sistema`.

| Hoy | En móvil (menos de 768 px, P1) | En tablet y escritorio (desde 768 px, P2) |
|---|---|---|
| Título y subtítulo | «Configuración» | Igual que en móvil |
| Límite de alumnos por turno y su ayuda | Sección «Alumnos por turno» | Igual, desde el índice |
| Valor actual, nuevo valor y «Modificado» | Al lado del campo | Igual que en móvil |
| Guardar cambios y restaurar | Bajo el campo | Igual que en móvil |
| Aviso de que afecta a la web de horarios | Texto de la sección | Igual que en móvil |
| Error si el límite es menor que 1 | Error bajo el campo; Guardar desactivado | Igual que en móvil |
| Buscador de usuarios | Cabecera de «Roles y permisos» | Igual que en móvil |
| Usuario (iniciales y nombre) y email | Fila | Dos líneas por cuenta |
| Roles actuales | Pastillas | Pastillas |
| Asignar roles (usuario, manager, admin) | Casillas Familia, Gestión y Administración | Igual que en móvil |
| Guardar y restablecer por usuario | En la fila | Igual que en móvil |
| Paginación de usuarios y aviso sin usuarios | Igual que hoy | Igual que en móvil |
| Añadido: Avisos legales y cookies | Menú de la cuenta | Índice y menú de la cuenta |

### Registro de acciones (P2: cronología por días con detalle)

Pieza 8b-3k. Rutas de hoy: `auditoriaSistema`. Componentes de hoy: `auditoria-sistema`.

| Hoy | En la pantalla nueva |
|---|---|
| Título y subtítulo | Igual, con el total |
| Actualizar ahora | Botón |
| Filtro desde | Botón «Filtros» |
| Filtro hasta | Botón «Filtros» |
| Filtro acción | Botón «Filtros» |
| Filtro módulo (fijo en salud con su ayuda) | Botón «Filtros» |
| Filtro persona | Botón «Filtros» |
| Buscar en el resumen | Botón «Filtros» |
| Aplicar filtros y restablecer | Botón «Filtros» |
| Total de acciones | Subtítulo |
| Cuándo | Día como título y hora |
| Acción | Pastilla |
| Qué | Texto principal |
| Módulo | Bajo el texto |
| Quién | Bajo el texto, «Cuenta N» |
| Ver el detalle | Panel al lado |
| Detalle: qué, módulo, ficha afectada, fecha, persona | En el panel |
| Datos técnicos: método, estado, endpoint, query params, payload | Desplegable del detalle |
| Paginación | Pie de la cronología |
| Aviso sin acciones en el periodo | Igual que hoy |
