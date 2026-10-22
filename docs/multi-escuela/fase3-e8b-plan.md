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

Orden: **8b-1 → 8b-I → 8b-T → 8b-N → 8b-2 → 8b-3a … 8b-3e → 8b-3f … 8b-3k → 8b-Z**. 8b-I y 8b-T se pueden hacer
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
construya, con el interruptor encendido se ve con la cabecera nueva y su contenido de hoy. Fuera de las siete quedan
los formularios de alta y edición que no nombra la 259 (alta de alumno, crear y editar grupo y turno, gestionar alumnos
del grupo) y las pantallas de plataforma: siguen con su contenido de hoy dentro de la cabecera nueva; el boceto de la
ronda 3 los lista para que el propietario confirme si entran.

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

**Riesgos:** perder una acción poco usada (el inventario manda); el formulario de edición; la salud (si aparece, con las
mismas reglas de lectura y registro de hoy).

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

| Hoy | En la pantalla nueva |
|---|---|
| Título «Grupos y turnos» | Título con el total de grupos y turnos |
| Nuevo turno | Arriba a la derecha |
| Nuevo grupo | Botón principal |
| Alumnos por tipo de grupo (abre la lista) | Fila de botones con «ver alumnos» |
| Nombre del grupo | Cabecera de la tarjeta |
| Rango de edad | Dato «Edad» de la tarjeta |
| Turnos: número y lista (día y hora) | Horario resumido a la vista; lista al desplegar «Turnos» |
| Editar turno | Lápiz en cada turno (desplegado) |
| Quitar turno del grupo | Cruz en cada turno (desplegado) |
| Añadir turno (turnos sin grupo) | Desplegable dentro de «Turnos» |
| Botón «Rango edad» | Pie de la tarjeta |
| Botón «Alumnos» | Pie de la tarjeta |
| Botón «Editar» | Pie y nombre de la tarjeta |
| Botón «Eliminar» | Papelera al pie |
| Turnos sin grupo asignado (con «Editar») | Bloque al final |
| Diálogo con los alumnos de un tipo | Sin cambios |
| Añadido: Alumnos de cada grupo y de cada disciplina | Dato «Alumnos» y total por disciplina |
| Añadido: Filtro por disciplina | Segmentos con su número |

### Tesorería (C)

| Hoy | En la pantalla nueva |
|---|---|
| Título «Tesorería y cobros» y subtítulo | «Tesorería» y subtítulo |
| Actualizado (fecha y hora) | En el subtítulo |
| Filtro mes | Desplegable |
| Filtro año (o todos los años) | Desplegable |
| Filtro deporte | Desplegable |
| Filtro estado | Pestañas Pendientes | Cobrados | Todos |
| Solo alumnos activos | Casilla |
| Buscar (alumno, concepto o categoría) | Campo |
| Aplicar filtros | Botón |
| Restablecer | Botón |
| Importe pendiente y nº de cobros | Resumen |
| Importe cobrado, nº y devueltos | Resumen |
| Total generado y movimientos | Resumen |
| Alumnos con deuda y periodo | Resumen |
| Título de la tabla, deporte y periodo | Cabecera de la lista |
| Certificados (diálogo por alumno y año) | Menú «Exportar» |
| Ver PDF | Menú «Exportar» |
| Exportar PDF | Menú «Exportar» |
| Exportar CSV | Menú «Exportar» |
| Alumno (enlace a la ficha) | Línea 1 |
| Deporte | Bajo el alumno |
| Concepto | Columna |
| Categoría | Bajo el alumno |
| Fecha de cargo | «Cargado …» bajo el alumno |
| Importe | A la derecha |
| Estado (pagado, pendiente, devuelto) | Pastilla |
| Fecha de pago | En la pastilla «Pagado el …» |
| Notas (texto) | Bajo el concepto y menú «⋯» |
| Marcar pagado | «Cobrar» en la fila |
| Cambiar fecha de pago (pagados) | Menú «⋯» |
| Revertir o pedir revertir (pagados) | Menú «⋯» |
| Mostrando X de Y movimientos | Pie de la lista |
| Paginación (con más de una página) | Pie de la lista |
| Aviso «No hay cobros para los filtros» | Igual que hoy |
| Añadido: Barra cobrado / pendiente y pendiente por sede | Barra y pendiente por sede |

### Convocatorias (C)

| Hoy | En la pantalla nueva |
|---|---|
| Título «Gestión de convocatorias» | «Convocatorias» y subtítulo |
| Crear convocatoria | Botón principal |
| Filtro por deporte | Pestañas con nº |
| Lista: disciplina y fecha de examen | Bloques |
| Abrir una convocatoria | «Ver alumnos» |
| Eliminar convocatoria | Papelera en el bloque |
| Alumnos inscritos, pagados y pendientes | Cifras del bloque |
| Ver PDF | Menú «PDF» al pie |
| Descargar PDF (con la fecha) | Menú «PDF» al pie |
| Actualizar grados de todos los alumnos | Botón principal al pie |
| Alumno (iniciales y nombre) | Lista |
| Grado actual con su color | Con su cinturón |
| Grado siguiente con su color | Con su cinturón |
| Cuantía editable | Campo |
| Pagado / pendiente (se cambia) | Conmutador Pagado | Pendiente |
| Quitar alumno de la convocatoria | Cruz |
| Aviso «No hay alumnos inscritos» | Igual que hoy |
| Seleccionar alumno apto para examen | Desplegable al pie |
| Agregar alumno | Botón al pie |
| Cargar más alumnos | Botón al pie |
| Paginación de convocatorias | Bajo los bloques |
| Aviso sin convocatorias con «Crear primera convocatoria» | Igual que hoy |

### Eventos (P1: lista densa ordenable)

Pieza 8b-3f. Rutas de hoy: `eventosListar`. Componentes de hoy: `listado-eventos`.

| Hoy | En la pantalla nueva |
|---|---|
| Título «Gestión de eventos» | «Eventos» con total y visibles |
| Guardar cambios de orden (aparece al reordenar) | Aviso bajo el título que pasa a «Guardar orden» |
| Mostrar u ocultar la vista previa de la web | «Ver cómo queda en la web» |
| Crear nuevo evento | Botón principal |
| Imagen (se amplía) | Miniatura en la fila |
| Título del evento | Fila |
| Fecha con su estado (próximo, hoy, pasado) | Columna de fecha con su estado |
| Visible u oculto | Interruptor «Visible en la web» |
| Descripción | Bajo el título, dos líneas |
| Subir, bajar y arrastrar para ordenar | Asa y flechas |
| Ocultar o mostrar | Interruptor |
| Editar | Botón en la fila |
| Eliminar | Menú «⋯» |
| Aviso sin eventos y estado de carga | Igual que hoy |

### Editar evento (P2: vista previa de la web al lado)

Pieza 8b-3f. Rutas de hoy: `eventosCrear`, `eventosEditar/:id`. Componentes de hoy: `crear-evento`, `editar-evento`.

| Hoy | En la pantalla nueva |
|---|---|
| Título «Editar evento» y subtítulo | Igual, con enlace «Eventos» |
| Título del evento (obligatorio, máximo) y sus errores | Campo con ayuda y error debajo |
| Descripción (obligatoria) y sus errores | Campo con ayuda y error |
| Fecha del evento (opcional) y su ayuda | Campo |
| Visible en la web (al crear) | Interruptor y vista previa |
| Imagen: subir, formatos, vista previa y quitar | Sección «Imagen» |
| Documentos guardados: descargar y eliminar | Sección «Documentos» |
| Documentos por subir: nombre, tamaño, quitar y «Subir N» | Sección «Documentos» |
| Aviso de que los documentos son públicos | Ayuda de «Documentos» |
| Cancelar | Barra fija de guardar |
| Actualizar evento (desactivado con errores, «Guardando…») | «Guardar cambios» en la barra fija |
| Estado de carga | Igual que hoy |
| Añadido: Resumen de errores con enlaces a cada campo | Arriba |
| Añadido: Vista previa de la tarjeta pública | Al lado del formulario |

### Productos (P2: agrupados por tipo)

Pieza 8b-3g. Rutas de hoy: `productosListar`, `productosCrear`, `productosEditar/:id`. Componentes de hoy: `listado-productos`, `crear-producto`, `editar-producto`.

| Hoy | En la pantalla nueva |
|---|---|
| Título «Gestión de productos» | «Productos» y para qué sirven |
| Crear producto | Botón principal |
| Dar a Mi caja (seleccionados) | Barra fija al pie |
| Quitar de Mi caja (seleccionados) | Barra fija al pie |
| Eliminar seleccionados | Barra fija al pie |
| Filtro por concepto | Buscador |
| Filtro por tipo | Desplegable |
| Filtro Mi caja | Desplegable |
| Limpiar filtros | Botón |
| Total de productos | Junto a los filtros |
| Chips de filtros activos con quitar | Bajo los filtros |
| Seleccionar todos y cada uno | Casilla en cada producto |
| Ordenar por ID, concepto o precio | Menú de cada grupo |
| ID | «nº» en la fila |
| Concepto (enlace a editar) | Fila |
| Precio | A la derecha |
| Mi caja (interruptor) | Interruptor «Mi caja» |
| Editar | Menú «⋯» |
| Eliminar | Menú «⋯» |
| Paginación | Grupos enteros, sin páginas |
| Aviso sin productos o sin coincidencias | Igual que hoy |
| Añadido: Tipo de cada producto | Grupos por tipo |

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
