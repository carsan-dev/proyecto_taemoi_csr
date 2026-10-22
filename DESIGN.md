---
name: Panel de gestión Taemoi (E8b, dirección C «La escuela al frente»)
description: Panel sobrio y ordenado donde la escuela manda en la cabecera y todo lo demás trabaja como Mi caja y Mis grupos.
colors:
  marca-primario: "#1B2B2E"
  marca-sobre-primario: "#FFFFFF"
  marca-sobre-primario-2: "#C6CACB"
  marca-primario-tinta: "#1B2B2E"
  marca-primario-suave: "#E8EAEA"
  marca-por-defecto: "#1A2230"
  acento: "#01707F"
  acento-borde: "#4595A3"
  acento-suave: "#D5F5FC"
  atencion: "#A14F0B"
  atencion-suave: "#FCF2E6"
  fondo: "#F5F5F2"
  superficie: "#FFFFFF"
  superficie-2: "#FAFAF8"
  linea: "#E4E4DF"
  linea-fuerte: "#CFCFC8"
  borde-control: "#868D97"
  tinta: "#1A2230"
  tinta-2: "#5D6573"
  tinta-3: "#7A8290"
  peligro: "#B42318"
  bien: "#2F6B3A"
  disc-1: "#3D75BF"
  disc-2: "#A04916"
  disc-3: "#00A390"
  disc-4: "#894081"
  serie-ocupacion: "#2B6CB0"
  serie-pista: "#ECEEF1"
  cinto-borde: "#7A8290"
typography:
  titulo-pantalla:
    fontFamily: "'Montserrat Panel', Montserrat, system-ui, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.01em"
  titulo-bloque:
    fontFamily: "'Montserrat Panel', Montserrat, system-ui, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 700
    lineHeight: 1.25
  cifra:
    fontFamily: "'Montserrat Panel', Montserrat, system-ui, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 700
    lineHeight: 1.1
  nombre-carnet:
    fontFamily: "'Montserrat Panel', Montserrat, system-ui, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.02em"
  cuerpo:
    fontFamily: "'Public Sans', 'Segoe UI', system-ui, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.45
  secundario:
    fontFamily: "'Public Sans', 'Segoe UI', system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.35
  etiqueta:
    fontFamily: "'Public Sans', 'Segoe UI', system-ui, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 600
    lineHeight: 1.2
rounded:
  s: "6px"
  m: "8px"
  carnet: "14px"
  pastilla: "999px"
spacing:
  xs: "4px"
  s: "8px"
  m: "16px"
  l: "24px"
  xl: "32px"
components:
  boton-primario:
    backgroundColor: "{colors.marca-primario-tinta}"
    textColor: "{colors.marca-sobre-primario}"
    rounded: "{rounded.m}"
    height: "40px"
    padding: "0 16px"
  boton-secundario:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.acento}"
    rounded: "{rounded.m}"
    height: "40px"
    padding: "0 16px"
  boton-texto:
    textColor: "{colors.acento}"
    rounded: "{rounded.m}"
    height: "34px"
    padding: "0 10px"
  cabecera:
    backgroundColor: "{colors.marca-primario}"
    textColor: "{colors.marca-sobre-primario}"
    height: "64px"
  barra-inferior:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.tinta-2}"
    height: "64px"
  bloque:
    backgroundColor: "{colors.superficie}"
    rounded: "{rounded.m}"
    padding: "18px 20px 16px"
  fila-dos-lineas:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.tinta}"
    padding: "10px 12px 10px 16px"
  pestana:
    textColor: "{colors.tinta-2}"
    height: "44px"
    padding: "0 14px"
  pestana-activa:
    textColor: "{colors.acento}"
    height: "44px"
  pastilla-pendiente:
    backgroundColor: "{colors.atencion-suave}"
    textColor: "{colors.atencion}"
    rounded: "{rounded.pastilla}"
    height: "24px"
  carnet:
    backgroundColor: "{colors.superficie}"
    rounded: "{rounded.carnet}"
    padding: "30px 26px 22px"
---

<!-- Escrito a partir de los bocetos aprobados de la dirección C (rondas 1 y 2, 21/10/2026). Cuando 8b-1 y 8b-2 estén
construidas, se vuelve a pasar `impeccable document` sobre el código para fijar lo que de verdad se haya enviado. -->

# Design System: Panel de gestión Taemoi

## Overview

Un panel de trabajo, no un escaparate. La escuela se nota en una sola banda: la cabecera sólida de su color con su logo
a 46 px; debajo, un fondo neutro y quieto donde todo se trabaja como ya funcionan Mi caja y Mis grupos (una columna en
el móvil, objetivos de 44 px, cifras tabulares, una acción por fila y hoja de confirmación). El tono es el de un buen
maestro: ordenado, directo, sin alardes.

Alcance (decisión 240): solo el panel de gestión. El área de familias y la web pública no cambian, y ningún token se
declara en `:root`: todo vive bajo la raíz del panel nuevo. Solo modo claro (decisión 241), con todos los colores en
variables. Se diseña y revisa a 375, 768 y 1280 px (decisión 237). Objetivo WCAG 2.2 AA (decisión 243).

**The Una Escuela Rule.** La marca ocupa la cabecera y el botón principal; el resto del panel la lleva solo como un
acento medido (decisión 273, variante B; pieza 8b-C): entrada actual de los menús y de la barra inferior, pestaña
activa, controles elegidos (filtros, segmento, pastillas, página actual), secundarios, botones de texto, enlaces,
iconos de las entradas del Inicio, avatar de iniciales y foco. Siguen fijos el fondo, la cabecera y el botón principal,
los estados (`bien`, `atencion`, `peligro`), las disciplinas, los cinturones, la ocupación y el diálogo rojo de peligro.

**The Nada Se Pierde Rule.** Cada pantalla nueva conserva todo lo que muestra y permite la de hoy (decisión 232); el
inventario de su pieza es la lista de aceptación.

Movimiento: solo el que responde a una acción (abrir un menú, desplegar una fila, cambiar de pestaña), 150 ms con
`ease-out` en color y fondo. Sin entradas animadas. Con `prefers-reduced-motion`, ninguna transición.

## Colors

Estrategia contenida: neutros cálidos casi blancos, la tinta casi negra y un único color de escuela; el ocre cálido
solo para lo pendiente.

### Primary

- **Color de la escuela** (`marca-primario`): fondo de la cabecera (la página actual de la paginación pasa al acento en 8b-C). En
  Moiskimdo, el oscuro de su logo (decisión 247; blanco encima 14,66:1). Una escuela sin color guardado usa
  `marca-por-defecto`.
- **Texto sobre el color** (`marca-sobre-primario`, `marca-sobre-primario-2`): nombres y entradas de la cabecera; el
  secundario, para las entradas no activas (8,87:1 en Moiskimdo).
- **Color de la escuela como tinta** (`marca-primario-tinta`): botón principal. Es el
  primario oscurecido hasta 4,5:1 sobre blanco si hace falta; en Moiskimdo coincide con el primario.
- **Suave** (`marca-primario-suave`): fondo neutro al pasar el ratón por menús, filas y botones de icono.
- **Acento** (`acento`, `acento-borde`, `acento-suave`; decisión 273, 8b-C): el color de la escuela medido para el resto
  del panel, calculado en `acento-escuela.ts` y escrito bajo `.pn` (nunca en `:root`). Con P de tono H y croma C en
  OKLCH: `acento-suave` es H con L 0,95 y C 0,035; `acento`, H con C máx(C, 0,09) recortado a sRGB y la L que baja desde
  0,50 hasta dar ≥ 4,5:1 sobre blanco y sobre `acento-suave` (texto, enlaces, iconos); `acento-borde`, H con C 0,08 y la
  L más alta que dé ≥ 3:1 sobre blanco, el fondo y `acento-suave` (bordes de control, anillo de foco). Un color sin tono
  (C < 0,01) se queda con `marca-primario-tinta`, `marca-primario-suave` y `borde-control`. Moiskimdo: `#01707F` (5,79:1
  sobre blanco, 5,30 sobre el fondo, 5,04 sobre el suave), `#4595A3` (3,45, 3,16 y 3,00) y `#D5F5FC`. Sin marca, el de
  `#1A2230`: `#466396`, `#6F8ABB` y `#E6EFFF`.
- **Derivados.** El secundario y el suave se derivan del primario mezclando en sRGB: el secundario es el texto con el
  25 % del primario (menos si no llega a 4,5:1) y el suave, el 10 % del primario sobre blanco. Los valores de arriba
  son los de Moiskimdo (`#1B2B2E`), calculados por `ReglaContraste`.
- **Tema por defecto** (escuela sin color, decisión 257; también el de `panel-nuevo.scss` mientras no llega la marca):
  primario `#1A2230`, texto `#FFFFFF` (15,96:1), secundario `#C6C8CB` (9,52:1), tinta `#1A2230` y suave
  `#E8E9EA`.

### Secondary

- **Atención** (`atencion`, `atencion-suave`): importes pendientes, pastilla «Pendiente», marca de «Hoy» y la estrella
  de disciplina principal. 5,77:1 sobre blanco. Común a todas las escuelas.

### Tertiary

- **Disciplinas** (`disc-1` a `disc-4`, orden fijo del catálogo: taekwondo, kickboxing, pilates, defensa personal
  femenina). Validadas como paleta categórica: todas ≥ 3:1 sobre blanco y diferencia mínima para daltonismo 15,6.
  Siempre con su nombre al lado: nunca el color solo.
- **Ocupación** (`serie-ocupacion`): rampa secuencial de un solo tono para el mapa día × franja, en cuatro pasos fijos
  (8b-2, validados con dataviz): `#95B6D8` (menos del 25 %), `#6091C4` (25-49 %), el color pleno `#2B6CB0` (50-74 %) y
  `#1C4672` (75 % o más). Cifra en tinta sobre los dos claros (7,57 y 4,82:1) y en blanco sobre los oscuros (5,41 y
  9,68:1); el paso claro llega a 2,11:1 contra el bloque. «Sin clase», trama sin color. `serie-pista`: plazas libres.
- **Cinturones**: el color real de cada grado, con `cinto-borde` alrededor (3,87:1 sobre blanco), para que el
  cinturón blanco o amarillo claro se vea (WCAG 1.4.11).

### Neutral

- `fondo` para la página, `superficie` para bloques, filas y menús, `superficie-2` para cabeceras de tabla y fila
  abierta.
- `linea` separa filas y bloques; `linea-fuerte` solo decora. Los campos y controles cuya forma hay que ver llevan
  `borde-control` (3,35:1 sobre blanco, 3,07:1 sobre el fondo).
- `tinta` para el texto (14,62:1 sobre el fondo), `tinta-2` para el secundario (5,38:1), `tinta-3` solo para iconos y
  texto grande.
- Estados: `peligro` (6,57:1) y `bien` (6,39:1), siempre con icono y palabra.

### Named Rules

**The Contraste Automático Rule.** El sistema, no el superadmin, decide el texto sobre el color de la escuela al
guardarlo: blanco si da ≥ 4,5:1; si no, `tinta` si da ≥ 4,5:1; si ninguno llega, se oscurece el primario en OKLCH
conservando tono y croma hasta que el blanco llegue. `marca-primario-tinta` es el primario oscurecido hasta 4,5:1 sobre
blanco. El valor tal cual se guarda solo para el logo y los rellenos que lleguen a 3:1.

**The Un Pendiente Rule.** El ocre significa «se debe dinero o algo vence». No se usa para decorar ni como segundo
color de marca.

## Typography

Dos familias con papeles separados. Títulos y cifras grandes en Montserrat 600/700 (continuidad con Moiskimdo); texto,
tablas, controles y datos en Public Sans 400 a 700, con cifras tabulares (`font-variant-numeric: tabular-nums`) en
importes, contadores y horas.

Las dos se sirven desde `assets/fonts` y se declaran solo en la hoja del panel nuevo. Montserrat del panel se declara
con otro nombre de familia («Montserrat Panel») para que su peso 600 no cambie el texto de la web pública ni de las
pantallas de hoy, que cargan Montserrat 400/700 con `* { font-family: Montserrat }`.

### Hierarchy

- Título de pantalla 24 px (22 px en móvil), bloque 17 px, cifra destacada 28 px, nombre del carnet 36 px.
- Cuerpo y tablas 15 px; secundario 14 px; etiquetas y cabeceras de tabla 13 px en 600.
- Frases en minúscula inicial, sin mayúsculas sostenidas en etiquetas ni rótulos sobre los títulos.

### Named Rules

**The Euro Español Rule.** Importes con `Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR',
useGrouping: 'always' })`: «1.010,00 €», alineados a la derecha. Fechas y meses en es-ES. Plurales calculados.

## Layout

- Página de 1280 px como máximo, márgenes de 24 px (16 px en móvil), bloques separados 20 px.
- Escritorio: rejilla de dos columnas en el inicio (contenido principal y una columna de 380 px). Tableta: una
  columna con bloques a ancho completo. Móvil (375): una columna, barra inferior fija.
- Cabecera fija de 64 px (56 px en móvil); barra inferior de 64 px más la zona segura, por debajo de 1024 px.
- Pestañas fijas bajo la cabecera en la ficha.
- Ninguna tabla obliga a desplazarse en horizontal: por debajo de 768 px pasa a filas de dos líneas o tarjetas.
- Objetivos táctiles de 44 px en móvil (40 px en escritorio, nunca menos de 24 px, WCAG 2.5.8).

## Elevation & Depth

Plano. Bloques, filas y tablas se separan con un filete de 1 px (`linea`) sobre el fondo, sin sombra. Solo flotan
los menús desplegables, los avisos, la barra inferior y las capas modales: una sombra para menús y avisos
(`0 6px 20px -6px` de la tinta al 22 %, más `0 2px 4px` al 6 %) y otra hacia arriba para la barra. El carnet lleva una
sombra mínima de tarjeta (`0 1px 2px` al 5 % y `0 3px 10px -4px` al 10 %).

**Barra inferior** (token `--pn-sombra-barra`): `0 -6px 20px -8px` de la tinta al 22 %, más `0 -2px 4px` al 6 %, ambas hacia arriba.

**Diálogo** (token `--pn-sombra-dialogo`, decisión 271, 8b-D1): `0 24px 48px -12px` de la tinta al 28 %, más `0 4px 10px`
al 8 %. La llevan el diálogo centrado y el panel lateral, sobre el velo (`--pn-velo`, la tinta al 40 %). La hoja inferior
lleva la misma, vuelta hacia arriba (`--pn-sombra-hoja`: `0 -12px 32px -8px` de la tinta al 28 %), como la barra.

**The Tres Sombras Rule.** Hay tres sombras en todo el panel (menú, carnet y diálogo, decisión 271) más sus versiones
hacia arriba para la barra inferior y la hoja. Una cuarta pide una decisión, no un retoque.

## Shapes

Esquinas suavemente curvadas de 8 px en bloques, botones y campos; 6 px en elementos interiores (miniaturas, menús
internos, tablas pequeñas); 14 px solo en el carnet; pastillas y contadores redondos. Fotos de alumno en fila,
redondas de 40 px; foto del carnet 4:5 con 12 px.

## Components

### Buttons

- **Primario**: uno por pantalla, el color de la escuela como tinta con texto blanco, 40 px de alto (44 px en
  móvil). Hover: el mismo color mezclado con un 14 % de negro.
- **Secundario**: blanco con borde `acento-borde` y texto `acento`; hover `acento-suave`.
- **Texto**: sin fondo, para acciones de fila («Cobrar», «Ver alumnos»), en `acento`; hover `acento-suave`. Las de
  `peligro` y `bien` conservan su color y el hover neutro.
- **Elegido** (filtro activo, segmento, pastilla, página actual): `acento-suave` con borde `acento-borde` y texto
  `acento`.
- **Icono**: 34 px, solo con `aria-label`; en móvil ningún botón de ancho completo lleva solo un icono.
- Exportar, informes y acciones raras van en un botón con menú («Exportar», «Informes», «⋯»).

### Cards / Containers

- **Bloque**: superficie blanca, filete y 8 px; título en Montserrat 17 px y, si hace falta, una nota en 13 px.
  No hay tarjetas de una sola cifra.
- **Tarjeta de grupo** (Grupos): bloque con nombre, alumnos, edad y horario a la vista; acciones al pie; turnos que se
  despliegan dentro.

### Inputs / Fields

Campos de 42 px con `borde-control`, 8 px de radio y etiqueta visible encima. Foco (8b-C): anillo de 3 px de
`acento-borde` con 2 px de separación (por dentro en menús y pestañas, con `acento-suave` debajo en las entradas de
menú). Buscador con icono dentro y botón de borrar.

### Navigation

- **Cabecera**: color de la escuela, logo de 46 px y nombre de la escuela a la izquierda; seis entradas con icono y
  nombre (Inicio, Alumnos, Grupos, Tesorería, Solicitudes, Escuela), cuatro con desplegable; cuenta con iniciales y
  nombre visible a la derecha. Entrada actual con fondo claro del texto al 14 % y `aria-current="page"`.
- **Barra inferior** (por debajo de 1024 px): Inicio, Alumnos, Tesorería y Más, con icono y nombre; la actual en el
  `acento` con una marca de 3 px arriba; en la hoja «Más» y en los desplegables de la cabecera, la entrada actual va en
  `acento-suave` con texto e icono en `acento`. Contador de solicitudes en ocre.
- **Sin pie público** (decisión 259): el panel nuevo no lleva el pie de la web; los enlaces legales y «Configurar
  cookies» van en «Más» y en el menú de cuenta.
- **Pestañas**: texto y subrayado de 3 px en `acento` en la activa, `role="tablist"` y teclado con
  flechas. Tesorería: Pendientes | Cobrados | Todos.

### Fila de dos líneas (Alumnos, Tesorería)

Foto de 40 px a la izquierda; línea 1 con nombre (enlace), expediente y edad; línea 2 con disciplinas (cinturón de
16 × 8 px y grado), tarifa, antigüedad y licencia en 14 px; a la derecha, el importe pendiente con «Cobrar» o
«Al día». La fila se despliega con el detalle y sus acciones. 25 por página, con cambio a tarjetas.

### Carnet (ficha del alumno, F1)

Foto 4:5 de 240 × 300 en escritorio y 184 × 230 en móvil (nunca menor que hoy), recortada por arriba. Nombre a 36 px,
expediente, edad, alta y estado. Franja superior de 10 px con el color del cinturón de cada disciplina, cada tramo con
`cinto-borde`. A la derecha (debajo en móvil), pendiente con «Cobrar», grado y licencia: se ven sin bajar. Acciones
del alumno al pie del carnet; debajo, pestañas Deportes, Pagos, Datos y contacto, Documentos y notas.

### Ocupación de la semana (Inicio, I1)

Mapa de calor día × franja: columnas de lunes a sábado, filas por hora de inicio. Cada celda muestra
«inscritos/plazas» en texto (el color nunca va solo), con los cuatro pasos de `serie-ocupacion` (menos del 25 %,
25-49 %, 50-74 %, 75 % o más) y una trama a 135° para «sin clase». La columna de hoy lleva «Hoy» en ocre. Leyenda
debajo, título con el total («N plazas ocupadas de M») y la nota «Inscritos frente al límite común de 36 por
turno», que desaparece cuando exista el aforo por sede (E9). Cada celda lleva su descripción completa al pasar
el ratón; el mapa no se lee en voz alta ni recibe el foco: los lectores de pantalla leen la tabla equivalente, con la
misma descripción por celda (8b-2).

### Pendiente de cobro (Inicio, I1)

Total de todo lo vencido en ocre a 28 px, cargos y alumnos debajo; desglose por tipo, por mes y por sede en filas de
importe alineado a la derecha; los cinco cargos más antiguos con «Cobrar».

### Diálogo y hoja (8b-D1, decisión 271)

Sustituyen a SweetAlert2 en las pantallas nuevas (decisión 269); las de hoy y las de familias siguen con él hasta 8b-Z.
`ConfirmacionService.confirmar(opciones)` devuelve una `Promise` con `{confirmado, valor}`.

- `<dialog>` con `showModal()`, centrado y de 440 px, radio 8, sombra de diálogo, sobre el velo. Título en Montserrat
  700 de 18 px (`--pn-titulo-dialogo`), X de cerrar de 34 px, frase de consecuencia en `tinta-2` (el nombre, en negrita
  y en `tinta`), campos opcionales y pie con «Cancelar» (secundario) a la izquierda y la acción a la derecha.
- Cobro: el concepto en etiqueta y el importe en la cifra ocre de 28 px entre dos filetes; el método, un grupo de
  pastillas `role=radiogroup` (32 px, borde de control; la elegida, `acento-suave` con borde `acento-borde`, texto
  `acento` y la marca de verificación) con «Sin indicar» por defecto. Flechas, Inicio y Fin mueven la elección.
- Esc, el clic fuera y la X cancelan sin hacer nada (decisiones 235 y 248). El foco queda contenido y vuelve al
  disparador al cerrar. Foco inicial: en la acción; en los destructivos, en «Cancelar», con la acción en `peligro`.
- Con la acción en vuelo, el diálogo sigue abierto con el botón ocupado («Cobrando…») y no se puede cancelar: ni Esc,
  ni el velo, ni la X, ni «Cancelar»; si el navegador lo cierra igualmente (un segundo Esc), se vuelve a abrir.
- Por debajo de 768 px es una hoja inferior: pegada abajo, radio 14 arriba, asa de 36 × 4, botones y pastillas de
  44 px y los botones a ancho completo, con la acción arriba.

### Avisos (8b-D1)

`AvisosService` con `exito`, `error` e `info`, y una acción opcional («Deshacer», «Reintentar», «Descargar»).

- Abajo a la derecha, de 360 px, 3 como mucho (el cuarto saca al más antiguo); con la barra inferior (por debajo de
  1024 px), encima de ella; por debajo de 768 px, a lo ancho con 16 px a cada lado y la acción y la X de 44 px.
- Superficie con filete y la sombra de los menús; icono de 20 px del color de su estado (`bien`, `peligro`, `tinta-2`)
  y la acción como enlace subrayado en el color de la escuela como tinta.
- Éxito e información se cierran a los 5 s con una barra de tiempo de 2 px (`linea-fuerte`), que se para con el ratón
  encima o el foco dentro (quieta con `prefers-reduced-motion`); el error no se cierra solo. Los lectores de pantalla
  los oyen por dos regiones vivas ocultas que nacen vacías con la de avisos, `role=status` y, la del error,
  `role=alert`; el texto llega 100 ms después de verse la tarjeta.
- Con una capa modal abierta, los avisos se pintan dentro de ella (fuera quedarían inertes), sin tapar sus botones:
  con el panel lateral, a su izquierda; en una hoja inferior, arriba. Un aviso de error cierra el SweetAlert2 que haya
  abierto el interceptor de errores de hoy (clase `swal-error-http`, sin estilos), como lo sustituía el aviso de las
  pantallas de hoy; solo ese y solo en una pantalla nueva.

### Carga (8b-D1)

- **Esqueleto** (`pn-esqueleto`): la forma del contenido que sustituye, en `serie-pista`, con un pulso de opacidad
  (1 a 0,55 en 1,6 s); aparece a los 300 ms (una carga más corta no parpadea) y se queda quieto con
  `prefers-reduced-motion`. La forma va con `aria-hidden` y a los lectores les llega «Cargando…» en un `role=status`.
- **Botón ocupado** (`button[pnOcupado]`): mismo ancho, indicador de 16 px, `aria-disabled` y `aria-busy` (no
  `disabled`: el foco se queda en el botón) sin atender clics, y el texto de la acción en curso («Cobrando…»,
  «Generando…»); en el primario, el texto pasa al secundario sobre el color.
- **Error de bloque** (`pn-bloque-error`): icono de peligro, qué falló, cómo seguir y «Reintentar», que vuelve a pedir
  solo ese bloque.
- **Vacío** (`pn-bloque-vacio`): qué no hay, por qué y la acción que lo arregla («Quitar filtros»), en `role=status`.

### Panel lateral (8b-D1, decisión 270)

`pn-panel-lateral`: `<dialog>` modal de 480 px a la derecha, bajo la cabecera de 64 px, con filete a la izquierda y la
sombra de diálogo sobre el velo. Cabecera con título, descripción y X; cuerpo con su propio desplazamiento; pie fijo
con «Cancelar» (botón de texto) a la izquierda y las acciones a la derecha. Por debajo de 768 px, hoja inferior. Esc, el
clic fuera y la X lo cierran y el foco vuelve a quien lo abrió. Con `bloqueado` (un envío en vuelo) no se cierra por
ninguna vía: la X y «Cancelar» van deshabilitados y, si el navegador lo cierra, se vuelve a abrir. Lo usan las
herramientas de «Informes» del listado.

## Do's and Don'ts

### Do:

- **Do** declarar los tokens bajo la raíz del panel nuevo y leer el color de la escuela de la API.
- **Do** poner nombre al lado de todo color con significado (disciplina, estado, cinturón, ocupación).
- **Do** medir el contraste de cada pareja nueva y anotarlo en la PR.
- **Do** reutilizar la hoja de confirmación y las filas de Mi caja y Mis grupos.

### Don't:

- **Don't** usar degradados, banners de bienvenida ni filas de tarjetas pastel con un solo número (anti-referencias
  de PRODUCT.md).
- **Don't** pintar gráficas, estados o disciplinas con el color de la escuela.
- **Don't** declarar nada en `:root` ni cargar fuentes o pesos nuevos con el nombre «Montserrat»: cambiaría la web
  pública y las pantallas de hoy.
- **Don't** usar clases de botón de Bootstrap ni de Angular Material en las pantallas nuevas.
- **Don't** añadir un segundo botón principal, un color de botón nuevo, un radio nuevo ni una sombra nueva sin
  cambiar antes este documento.
- **Don't** mostrar importes con `toFixed` ni con el formato inglés («1,010.00 EUR»).
