# Fase 3 · E8b: diseño del panel de gestión

## 1. Estado

- Solo diseño. Por la decisión 228, la E8b se diseña antes de construir y el propietario elige la dirección.
  No hay código, migración ni despliegue.
- Las tres direcciones y su comparación están en la página «Dirección E8b»: https://claude.ai/artifact/VssXLaUu8JqKeb3ra4KyMf.
- Las capturas no van al repositorio; están en la página.

## 2. Método

- `impeccable` en cuatro pasos: `init` (contexto en `PRODUCT.md`, sin entrevista porque el propietario no estaba),
  `audit` y `critique` del panel actual, y `new-work` para tres direcciones.
- Stack local (Angular 4200, API 8080) con datos ficticios: 52 alumnos, 3 sedes, 16 grupos. Commit auditado `20431339`.
- Cada dirección se montó sobre las pantallas reales (Inicio, Alumnos, Tesorería) con CSS y JS inyectados por
  Playwright, usando las mismas llamadas a la API que Angular. Capturas a 375 y 1280 px, más una «otra escuela»
  para probar el tema.
- Los componentes Angular de las direcciones no existen: los filtros, menús y paginaciones de las maquetas no funcionan.

## 3. Auditoría del panel actual

Técnica: 9/20 (pobre). Accesibilidad 2, rendimiento 3, responsive 2, tema 1, integridad 1.

Notas heurísticas por pantalla (sobre 40):

| Pantalla | Nota | Pantalla | Nota |
|---|---|---|---|
| Inicio | 20 | Convocatorias | 16 |
| Alumnos | 16 | Caja de sede | 28 |
| Ficha del alumno | 16 | Mis grupos | 28 |
| Grupos | 17 | Mi caja | 30 |
| Tesorería | 23 | | |

Hallazgos principales:

1. **El inicio no cuenta lo que decide.** Banner con degradado, 4 cifras sueltas y 13 accesos que repiten la
   cabecera. Sin clases de hoy, plazas ni deudas.
2. **La deuda de un alumno no se ve.** Hay que bajar 3,9 pantallas en escritorio y 6,6 en móvil; la ficha mide
   9.502 px a 375 y «Productos asignados» está en y = 5.391.
3. **Contraste y objetivos.** Blanco sobre `#4CAF50` 2,78:1, sobre `#007BFF` 3,98:1, sobre `#F44336` 3,68:1.
   58 objetivos de menos de 24 px en Grupos (iconos de turno de 12×13 px). Foco invisible en la cabecera de cada
   convocatoria (`listado-convocatorias.component.html:69`).
4. **Sin sistema.** 11 colores de botón, 21 radios y 26 sombras en 9 pantallas. `$primary-color` en 15 ficheros con
   3 valores. Ninguna variable CSS de tema; `organizacion.color_primario` no lo lee nadie.
5. **Formato y textos.** Sin `LOCALE_ID` global: «1,010.00 EUR» (`tesoreria-cobros.component.html:88-103`) y
   `toFixed(2)` en el inicio (`vista-principal-admin.component.ts:424`). Errata «1 eventos estan ocultos en la web
   publica» (`vista-principal-admin.component.ts:397`).
6. **Alumnos de 6 en 6.** El tamaño de página es `tarjetasPorFila × 2` (`listado-alumnos.component.ts:178-188`):
   con 800 alumnos, 134 páginas en escritorio y 400 en móvil. El contador cuenta la página
   (`listado-alumnos.component.html:88`).
7. **Lo que ya funciona.** Mi caja, Mis grupos y Caja de sede: una columna, 44 px, cifras tabulares, es-ES, una
   acción por fila y hoja de confirmación (`mis-grupos/_profesor.scss:1-22`). Es la referencia.

Rendimiento: inicial de 1,50 MB en bruto (273,6 kB por la red), en el aviso del presupuesto (`angular.json:72-76`).
Una librería de gráficas no cabe sin quitar `mat.all-component-themes` o Bootstrap entero.

## 4. Las tres direcciones

Comunes a las tres: importes es-ES con `useGrouping: 'always'` («1.010,00 €»), sin degradados ni banner de
bienvenida, un solo botón principal por pantalla, contraste AA medido, botones con texto en 375 px y tema por
escuela con variables.

### A · Hana · «Dojo claro»

- **Idea.** Fondo hueso, superficies blancas, un solo color de marca para lo que se toca y un cálido solo para lo
  pendiente. El inicio abre con una frase de estado del día con enlaces, y sigue con «Hoy» (clases con barra de
  plazas y «Lista») y «Por cobrar» (lista con «Cobrar»).
- **Qué mejora.** Inicio que cuenta el día; alumnos en tabla de 20 filas (8 en la primera pantalla del móvil, antes
  0); cabecera blanca con 7 accesos con nombre y «Más»; en móvil, barra inferior Inicio, Alumnos, Cobros y Más;
  exportar sin colores.
- **Riesgos.** El cambio de tono más grande (cabecera oscura a blanca). «Tesorería» pasa a «Cobros». «Cobrar» en el
  inicio lleva a Cobros: cobrar en el sitio pide decidir confirmación y deshacer.
- **Tokens.** `--marca-primario #0D47A1` (8,63:1), `--acento-atencion #A3500D` (5,66:1).
- **Fuente.** Source Sans 3.
- **Datos.** `turnos/resumen`, `grupos/resumen`, `tesoreria/resumen`, `tesoreria/movimientos?pagado=false`,
  `alumnos/deportes/distribucion`, `alumnos/aptos`, `eventos/admin/todos`.
- **Faltan.** Aforo por grupo (las barras usan 16 de muestra, rotulado), asistencia, sede de cada clase.

### B · Dul · «Libro de registro»

- **Idea.** Densidad ordenada: filetes de 1 px como cuadrícula, tablas que mandan, cifras alineadas. Barra lateral
  con nombres agrupada en Escuela, Dinero, Exámenes y Ajustes.
- **Qué mejora.** Inicio con cuatro formas: cobros de 6 meses (columnas cobrado/pendiente), ocupación día × franja
  (mapa de calor), alumnos por disciplina (barras por estado) y «Por resolver». Alumnos en filas de 40 px, 17 a la
  vista en 1280. Tesorería cabe a 1280 y exportar va en un menú.
- **Riesgos.** La barra lateral es el mayor cambio de navegación. La Mono pone el cero con barra en los importes.
  El mapa de calor cuenta inscritos, no plazas. Su pendiente suma todos los meses (48 cargos, 1.274,00 €) frente a
  octubre en A y C (35, 1.010,00 €).
- **Tokens.** `--marca-primario #0D47A1`, `--acento-atencion #CF4800` (4,59:1). Escala de gráficas con
  `color-mix(in oklab, …)` desde el primario.
- **Fuentes.** Atkinson Hyperlegible Next y Atkinson Hyperlegible Mono (solo datos).
- **Datos.** `tesoreria/resumen?ano&mes` de mayo a octubre, `grupos/resumen`, `alumnos?size=1000&incluirInactivos=true`,
  `tesoreria/movimientos?pagado=false`, `turnos/resumen`, `solicitudes/contador`.
- **Faltan.** Asistencia, aforo por grupo, sede del alumno en el DTO (el chip «Sede» es maqueta).

### C · Set · «La escuela al frente»

- **Idea.** La evolución más cercana al panel actual: cabecera sólida del color de la escuela con su logo a 46 px,
  menú de 6 entradas con icono y nombre (4 desplegables), y todo trabajado como Mi caja y Mis grupos.
- **Qué mejora.** Inicio con «Esta semana» por disciplina, «Hoy» con la siguiente clase resaltada y un solo bloque
  «Pendiente de cobro» con desglose por sede. Alumnos en tabla de 25 con columna «pendiente» y «Cobrar» en la fila.
  Cobros con pestañas Pendientes | Cobrados. Sin el pie público en el panel (unos 1.300 px menos en móvil).
- **Riesgos.** «Esta semana» se ve casi vacía: compara con 36 plazas comunes. 13 entradas quedan a dos clics (hoy 11
  están a uno). Con un color de escuela claro, la cabecera lleva texto oscuro.
- **Tokens.** `--marca-primario #1B385F` (blanco 11,8:1), `--marca-primario-tinta`, `--marca-primario-suave`,
  ocre de atención `#A14F0B` (5,8:1); colores de disciplina `--disc-1..4` derivados en OKLCH.
- **Fuentes.** Montserrat 600/700 en títulos y Public Sans en texto y datos.
- **Datos.** `grupos/resumen`, `turnos/resumen`, `turnos/limite`, `tesoreria/resumen`,
  `tesoreria/movimientos?pagado=false`, `alumnos/aptos`, `eventos/admin/todos`, `alumnos?size=25`.
- **Faltan.** Asistencia, aforo por grupo, apuntados por turno, nombre visible del usuario y marca de la escuela en la API.

### Azul de Moiskimdo

Las direcciones no usaron el mismo azul: A y B `#0D47A1` (taekwondo), C `#1B385F`. La auditoría propone `#2B6CB0` /
`#1F4D86` (banners de Mi caja, Mis grupos y Caja de sede; 5,42:1 y 7,37:1 con blanco). Queda como pregunta 9.

### Recomendación del director

C como base, por ser lo que menos sorprende a Moiskimdo y extender Mi caja (30/40). En el inicio del móvil, la frase
de estado y la lista «Hoy» de A. En escritorio, las gráficas de cobros de 6 meses y de ocupación de B en lugar de
«Esta semana» de C, que se ve vacía; ninguna de las dos necesita un aforo que no existe.

## 5. Tema por escuela

- Variables comunes: `--marca-primario`, `--marca-sobre-primario`, `--marca-primario-suave` y `--acento-atencion`
  (más `--marca-nombre` y `--marca-logo`). Hover, líneas y escalas se derivan con `color-mix` u OKLCH.
- Regla de contraste automática: si el color de la escuela no da 4,5:1, se ajusta su uso, no la norma.
  Texto sobre el primario: blanco si da 4,5:1; si no, tinta oscura; si ninguno llega, se oscurece el primario
  conservando el tono. Ejemplos medidos: verde `#2E9B5E` 3,52:1 pasa a `#1F6B45` 6,47:1 (A); verde `#3E9B4F` con
  blanco 3,5:1, con tinta `#16202C` 4,7:1 (C). El valor bruto queda para el logo y los rellenos (≥ 3:1).
- Lo edita solo el superadmin (decisiones 135 y 136: colores y logo por organización). La regla se aplica al
  guardar, no la decide el superadmin.
- Moiskimdo arranca con su paleta actual: hoy `organizacion.color_primario` es NULL en la org 1, así que el valor
  por defecto de la org 1 debe reproducir exactamente lo que ve hoy.

## 6. Supuestos

De `PRODUCT.md`:

- Supuesto: el propietario alterna entre móvil (durante y entre clases) y escritorio (altas, tesorería).
- Supuesto: el gestor trabaja sobre todo en escritorio, en sesiones largas.
- Supuesto: el responsable de sede (E9) hará gestión acotada a sus sedes.
- Supuesto: las familias usan casi siempre el móvil y de forma ocasional.
- Supuesto: éxito es también que el profesor resuelva su clase desde el móvil sin pedir nada a la oficina.
- Supuesto: el uso en móvil es de pie, entre clases y a menudo con una mano.
- Supuesto: objetivo WCAG 2.2 AA en panel, área de familias y web pública.
- Supuesto: el tema por escuela no puede romper el contraste.
- Supuesto: personalidad de buen maestro (ordenado, directo, respetuoso) y voz de frases cortas sin anglicismos.

De las direcciones:

- Supuesto: el panel se rediseña sin tocar el área de familias ni la web pública (las tres).
- Supuesto: aforo de 16 por clase en las barras de A, solo como muestra.
- Supuesto: «pendiente» es lo del mes en curso en A y C, y todo lo no cobrado en B.
- Supuesto: «Cobros» como nombre de la sección en A y C; B mantiene «Tesorería».
- Supuesto: el concepto del cargo se muestra en minúsculas y sin repetir la disciplina; la BD no cambia (B y C).
- Supuesto: se mantienen las rutas actuales y los nombres de menú, con redirección si alguno cambia.

## 7. Preguntas para el propietario

1. ¿Qué pantalla abre cada rol al entrar (resumen, agenda del día, Mis grupos) y qué debe verse sin desplazarse?
2. ¿El gestor trabaja sobre todo en escritorio, o también en el móvil?
3. ¿Hasta dónde llega el color de la escuela en el panel: solo acentos y logo, o también cabecera y gráficas?
4. ¿Cuándo debe Moiskimdo ver el panel nuevo: a la vez que las demás escuelas, después, o solo si lo activas?
5. ¿El área de familias y la web pública entran en la E8b o se quedan como están por ahora?
6. ¿Quieres modo oscuro o basta con el claro?
7. ¿Qué cuenta como «pago pendiente» en el resumen: mensualidades vencidas, derechos de examen o ambos?
8. ¿Se asume WCAG 2.2 AA como requisito o hay otra norma que cumplir (federación, administración)?
9. ¿Cuál es el azul de la marca de Moiskimdo (`#0D47A1`, `#1B385F` o `#2B6CB0` / `#1F4D86`)?
10. ¿Se guarda la asistencia (pasar lista en la aplicación) para poder contarla?
11. ¿Aforo por grupo, o el límite común de 36 por turno?
12. ¿«Cobros» o «Tesorería» como nombre de la sección?

## 8. Datos que faltan y su coste

| Dato | Hoy | Qué haría falta | Coste |
|---|---|---|---|
| Asistencia por disciplina | No hay tabla; solo la hoja en PDF (`ProfesorController.java:83`) | Tabla de presencia por turno, alumno y fecha; API y permisos; pantalla de pasar lista | Alto: funcionalidad nueva con migración, no rediseño |
| Plazas por grupo | Límite común `configuracion_organizacion.limite_turno` = 36 por turno; `sede.aforo_turno` se guarda pero `AforoPreinscripcionService` no lo usa | Decidir aforo por grupo (columna nueva) o usar el de la sede en el cálculo | Medio: migración si es por grupo; bajo si se usa el de la sede |
| Sede en el DTO de alumnos | No viene | Añadir la sede (o sedes) al DTO del listado | Bajo: sin migración |
| Marca de la escuela en la API | `color_primario` y `logo_imagen_id` existen (V18) pero ningún DTO del panel los expone; nombre y logo fijos en `header.component.html:228`; el saludo usa el usuario técnico | Endpoint o campo en la sesión con nombre visible, logo y colores; nombre visible del usuario | Bajo-medio: sin migración para leer; la edición por el superadmin entra en 8b-T |

## 9. Siguiente paso

1. El propietario elige dirección (o mezcla) y responde las preguntas.
2. **8b-1** Sistema de tokens: variables CSS de color, radio, sombra y tipo, con la org 1 igual a hoy.
3. **8b-T** Tema por escuela: lectura de la marca en la API, regla de contraste al guardar, edición del superadmin.
4. **8b-2** Inicio nuevo con los datos que existen.
5. **8b-3** Pantallas (Alumnos, Cobros, Grupos, ficha), una por PR.

Cada PR lleva capturas antes y después a 375 y 1280 px.

## 10. Seguimiento aparte

Fallo, no de diseño: en Convocatorias, el diálogo «Selecciona el tipo de producto» usa «Por Antigüedad» como botón
de cancelar, así que Esc o un clic fuera asignan el producto por antigüedad (`listado-convocatorias.component.ts:516-523`).
Pide una elección explícita y un cancelar que no decida nada.
