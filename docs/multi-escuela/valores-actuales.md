# Valores actuales en código (fase 0)

Inventario exacto de los valores que hoy están fijados en el código Java y que la fase de
multi-escuela trasladará a tablas (ver `modelo-datos.md`, «Qué sustituye a cada pieza de código»).
Sirve de fuente para los datos semilla de Flyway.

Todos los valores de este documento están fijados por tests de caracterización en
`src-api/src/test/java/com/taemoi/project/caracterizacion/`. Si un test falla tras un cambio,
el comportamiento ha cambiado.

Las rutas se abrevian: `config/…`, `utils/…`, `entities/…` y `services/impl/…` cuelgan de
`src-api/src/main/java/com/taemoi/project/`. Las líneas corresponden a la rama
`feature/multi-escuela` en el commit `ee6f74b0`.

## 1. Grados (`TipoGrado`)

Fuente: `entities/TipoGrado.java:9-27`. El orden es el de declaración del enum (`ordinal`), que
también usa `TipoGrado.siguiente()` (`entities/TipoGrado.java:149-157`).

| Orden | Código | Nombre | Producto de examen | Producto de recompensa |
|---|---|---|---|---|
| 0 | `BLANCO` | Blanco | — | — |
| 1 | `BLANCO_AMARILLO` | Blanco-Amarillo | DERECHO A EXAMEN BLANCO/AMARILLO | DERECHO CAMBIO A BLANCO-AMARILLO RECOMPENSA |
| 2 | `AMARILLO` | Amarillo | DERECHO A EXAMEN AMARILLO | DERECHO DE CAMBIO A AMARILLO POR RECOMPENSA |
| 3 | `AMARILLO_NARANJA` | Amarillo-Naranja | DERECHO A EXAMEN AMARILLO/NARANJA | DERECHO DE CAMBIO A AMARILLO-NARANJA POR RECOMPENSA |
| 4 | `NARANJA` | Naranja | DERECHO A EXAMEN NARANJA | DERECHO DE CAMBIO A NARANJA POR RECOMPENSA |
| 5 | `NARANJA_VERDE` | Naranja-Verde | DERECHO A EXAMEN NARANJA/VERDE | DERECHO DE CAMBIO A NARANJA-VERDE POR RECOMPENSA |
| 6 | `VERDE` | Verde | DERECHO A EXAMEN VERDE | DERECHO DE CAMBIO A VERDE RECOMPENSA |
| 7 | `VERDE_AZUL` | Verde-Azul | DERECHO A EXAMEN VERDE/AZUL | DERECHO DE CAMBIO A VERDE-AZUL POR RECOMPENSA |
| 8 | `AZUL` | Azul | DERECHO A EXAMEN AZUL | DERECHO DE CAMBIO A AZUL POR RECOMPENSA |
| 9 | `AZUL_ROJO` | Azul-Rojo | DERECHO A EXAMEN AZUL/ROJO | DERECHO DE CAMBIO A AZUL-ROJO POR RECOMPENSA |
| 10 | `ROJO` | Rojo | DERECHO A EXAMEN ROJO | DERECHO DE CAMBIO A ROJO POR RECOMPENSA |
| 11 | `ROJO_NEGRO_1_PUM` | Rojo-Negro-1-Pum | DERECHO A EXAMEN ROJO/NEGRO 1º PUM | — |
| 12 | `ROJO_NEGRO_2_PUM` | Rojo-Negro-2-Pum | DERECHO A EXAMEN ROJO/NEGRO 2º PUM | — |
| 13 | `ROJO_NEGRO_3_PUM` | Rojo-Negro-3-Pum | — | — |
| 14 | `NEGRO_1_DAN` | Negro-1-Dan | DERECHO A EXAMEN NEGRO 1º DAN | — |
| 15 | `NEGRO_2_DAN` | Negro-2-Dan | DERECHO A EXAMEN NEGRO 2º DAN | — |
| 16 | `NEGRO_3_DAN` | Negro-3-Dan | DERECHO A EXAMEN NEGRO 3º DAN | — |
| 17 | `NEGRO_4_DAN` | Negro-4-Dan | — | — |
| 18 | `NEGRO_5_DAN` | Negro-5-Dan | — | — |

Observaciones literales que deben conservarse al sembrar:

- `BLANCO_AMARILLO` usa «DERECHO CAMBIO A … RECOMPENSA» (sin «DE» ni «POR»).
- `VERDE` usa «DERECHO DE CAMBIO A VERDE RECOMPENSA» (sin «POR»).
- Los PUM y DAN usan el carácter `º` (U+00BA).

Productos adicionales para el rojo, en `services/impl/AlumnoServiceImpl.java:98-101`, elegidos en
`resolverConceptoProducto` (`AlumnoServiceImpl.java:1964-1972`) cuando el siguiente grado es `ROJO`:

| Caso | Concepto |
|---|---|
| Examen, rojo normal | DERECHO A EXAMEN ROJO |
| Examen, rojo bordado | DERECHO A EXAMEN ROJO BORDADO |
| Recompensa, rojo normal | DERECHO DE CAMBIO A ROJO POR RECOMPENSA |
| Recompensa, rojo bordado | DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA |

### Resolución de grado desde un concepto

`fromProductoRecompensa` y `fromProductoExamen` (`entities/TipoGrado.java:92-124`) recorren el enum
en orden y devuelven el primer grado cuyo producto está **contenido** en el concepto (sin
distinguir mayúsculas). Se usan al pagar un producto para activar el grado
(`services/impl/ProductoAlumnoServiceImpl.java:223-246`). Resultado actual:

| Concepto | Grado devuelto |
|---|---|
| DERECHO A EXAMEN AMARILLO/NARANJA | `AMARILLO` |
| DERECHO A EXAMEN NARANJA/VERDE | `NARANJA` |
| DERECHO A EXAMEN VERDE/AZUL | `VERDE` |
| DERECHO A EXAMEN AZUL/ROJO | `AZUL` |
| DERECHO A EXAMEN ROJO/NEGRO 1º PUM | `ROJO` |
| DERECHO A EXAMEN ROJO/NEGRO 2º PUM | `ROJO` |
| DERECHO A EXAMEN ROJO BORDADO | `ROJO` |
| DERECHO DE CAMBIO A ROJO BORDADO POR RECOMPENSA | `null` (no activa grado) |
| Resto de productos de la tabla anterior | El grado de su fila |

Los grados intermedios y los PUM resuelven al grado de color anterior porque su concepto contiene
el concepto de ese grado. Es el comportamiento vigente, no necesariamente el deseado; al pasar a
`grado.producto_*_id` conviene decidir si se corrige.

`fromNombre` (`entities/TipoGrado.java:135-142`) compara con la columna «Nombre» sin distinguir
mayúsculas; el código del enum (`BLANCO_AMARILLO`) no se acepta y lanza `IllegalArgumentException`.

## 2. Progresión de grados

Fuente: `config/GradeProgressionConfig.java`. Menores Taekwondo en líneas 112-129, mayores
Taekwondo en 135-154, Kickboxing en 160-179 (el mismo mapa se asigna a menores y adultos en 46-50).
`esMenor` se calcula con `FechaUtils.esMenor` (sección 4), salvo en
`MaterialExamenServiceImpl.java:231-233`, que usa `DeporteEdadUtils`.

| Grado actual | Taekwondo menores | Taekwondo adultos | Kickboxing (menores y adultos) |
|---|---|---|---|
| `BLANCO` | `BLANCO_AMARILLO` | `AMARILLO` | `AMARILLO` |
| `BLANCO_AMARILLO` | `AMARILLO` | `AMARILLO` | `AMARILLO` |
| `AMARILLO` | `AMARILLO_NARANJA` | `NARANJA` | `NARANJA` |
| `AMARILLO_NARANJA` | `NARANJA` | `NARANJA` | `NARANJA` |
| `NARANJA` | `NARANJA_VERDE` | `VERDE` | `VERDE` |
| `NARANJA_VERDE` | `VERDE` | `VERDE` | `VERDE` |
| `VERDE` | `VERDE_AZUL` | `AZUL` | `AZUL` |
| `VERDE_AZUL` | `AZUL` | `AZUL` | `AZUL` |
| `AZUL` | `AZUL_ROJO` | `ROJO` | `ROJO` |
| `AZUL_ROJO` | `ROJO` | `ROJO` | `ROJO` |
| `ROJO` | `ROJO_NEGRO_1_PUM` | `NEGRO_1_DAN` | `NEGRO_1_DAN` |
| `ROJO_NEGRO_1_PUM` | `ROJO_NEGRO_2_PUM` | — | — |
| `ROJO_NEGRO_2_PUM` | `ROJO_NEGRO_3_PUM` | — | — |
| `ROJO_NEGRO_3_PUM` | — | — | — |
| `NEGRO_1_DAN` | — | `NEGRO_2_DAN` | `NEGRO_2_DAN` |
| `NEGRO_2_DAN` | — | `NEGRO_3_DAN` | `NEGRO_3_DAN` |
| `NEGRO_3_DAN` | — | `NEGRO_4_DAN` | `NEGRO_4_DAN` |
| `NEGRO_4_DAN` | — | `NEGRO_5_DAN` | `NEGRO_5_DAN` |
| `NEGRO_5_DAN` | — | — | — |

«—» significa que `obtenerSiguienteGrado` devuelve `null`. Para `PILATES` y
`DEFENSA_PERSONAL_FEMENINA` lanza `IllegalArgumentException` (`GradeProgressionConfig.java:70-73`).
Con deporte o grado nulos devuelve `null` sin lanzar.

`obtenerGradosAnteriores` (`GradeProgressionConfig.java:83-106`) devuelve las claves del mapa, en
orden de inserción, anteriores al grado actual. Las claves de adultos y Kickboxing incluyen los
grados intermedios (por ejemplo, adulto en `NEGRO_1_DAN` → de `BLANCO` a `ROJO`, 11 grados). Si el
grado no es clave del mapa (un DAN en menores, `ROJO_NEGRO_3_PUM`, un PUM en adultos), devuelve
todas las claves.

## 3. Meses mínimos entre exámenes

Fuente: `config/ExamEligibilityConfig.java`. Menores en líneas 68-96, adultos en 102-121.

| Grado actual | Menores (meses) | Adultos (meses) |
|---|---|---|
| `BLANCO` | 2 | 3 |
| `BLANCO_AMARILLO` | 2 | — |
| `AMARILLO` | 3 | 5 |
| `AMARILLO_NARANJA` | 3 | — |
| `NARANJA` | 4 | 6 |
| `NARANJA_VERDE` | 4 | — |
| `VERDE` | 6 | 8 |
| `VERDE_AZUL` | 6 | — |
| `AZUL` | 8 | 10 |
| `AZUL_ROJO` | 10 | — |
| `ROJO` | 12 | 12 |
| `ROJO_NEGRO_1_PUM` | 24 | — |
| `ROJO_NEGRO_2_PUM` | 36 | — |
| `ROJO_NEGRO_3_PUM` | — | — |
| `NEGRO_1_DAN` | — | 24 |
| `NEGRO_2_DAN` | — | 36 |
| `NEGRO_3_DAN` | — | 48 |
| `NEGRO_4_DAN` | — | 60 |
| `NEGRO_5_DAN` | — | — |

«—» significa que `obtenerMesesRequeridos` devuelve `Long.MAX_VALUE` (no apto). No distingue
disciplina: la misma tabla se aplica a Taekwondo y Kickboxing.

Qué tabla se aplica depende del punto de llamada; hay dos reglas distintas:

| Punto de llamada | Regla «menor» |
|---|---|
| `ExamEligibilityConfig.obtenerMesesRequeridos(edad, grado)` (`ExamEligibilityConfig.java:48-62`), usado por `AlumnoServiceImpl.java:2251-2253` para alumnos sin `AlumnoDeporte` | `edad < 13`, con `edad = FechaUtils.calcularEdad` (años cumplidos a hoy) |
| `AlumnoDeporteServiceImpl.java:739-744` (camino multideporte) | `FechaUtils.esMenor(fechaNacimiento, deporte)` (sección 4) |

Por tanto, un alumno de 13 años usa la tabla de adultos en el primer camino y puede usar la de
menores en el segundo.

## 4. Edad adulta por disciplina

Fuentes: `utils/FechaUtils.java:16-22` y `84-90`; `utils/DeporteEdadUtils.java:11-12` y `35-40`.
Constantes duplicadas sin uso en `services/impl/ProductoAlumnoServiceImpl.java:61` y `:63`
(`EDAD_LIMITE_INFANTIL_TAEKWONDO = 14`, `EDAD_LIMITE_INFANTIL_KICKBOXING = 15`).

| Disciplina | Edad adulta |
|---|---|
| `TAEKWONDO` | 14 |
| `KICKBOXING` | 15 |
| `PILATES` | 14 (regla por defecto) |
| `DEFENSA_PERSONAL_FEMENINA` | 14 (regla por defecto) |

Regla (`FechaUtils.esMenor`, `FechaUtils.java:35-54`; `DeporteEdadUtils.esMenorParaDeporte`,
`DeporteEdadUtils.java:24-33`): el alumno es adulto si cumple la edad adulta en el año natural
en curso o antes. Equivale a `año_nacimiento + edad_adulta <= año_actual`. El día del cumpleaños no
influye dentro del año.

| Nacimiento (año actual = A) | Taekwondo | Kickboxing |
|---|---|---|
| 1 ene A-15 … 31 dic A-15 | adulto | adulto |
| 1 ene A-14 … 31 dic A-14 | adulto | menor |
| 1 ene A-13 … 31 dic A-13 | menor | menor |

Variante con año de referencia `FechaUtils.esMenor(fecha, deporte, anio)` (`FechaUtils.java:65-76`):
`año_nacimiento + edad_adulta > anio` → menor. Se usa para licencias
(`ProductoAlumnoServiceImpl.java:839-865`).

Fecha de nacimiento nula: `FechaUtils` devuelve menor (`true`); `DeporteEdadUtils` devuelve adulto
(`false`). Las dos utilidades divergen en este caso.

## 5. Bloques de material de examen

Fuente: `config/ExamMaterialBlockConfig.java:31-62`. Mismo mapa para Taekwondo y Kickboxing;
`PILATES` y `DEFENSA_PERSONAL_FEMENINA` tienen mapa vacío (devuelven `null`).

| Grado actual | Bloque |
|---|---|
| `BLANCO`, `BLANCO_AMARILLO` | `b01_inicio_a_amarillo` |
| `AMARILLO`, `AMARILLO_NARANJA` | `b02_amarillo_a_naranja` |
| `NARANJA`, `NARANJA_VERDE` | `b03_naranja_a_verde` |
| `VERDE`, `VERDE_AZUL` | `b04_verde_a_azul` |
| `AZUL`, `AZUL_ROJO` | `b05_azul_a_rojo` |
| `ROJO` | `b06_rojo_a_negro_1` |
| `ROJO_NEGRO_1_PUM`, `NEGRO_1_DAN` | `b07_negro_1_a_negro_2` |
| `ROJO_NEGRO_2_PUM`, `NEGRO_2_DAN` | `b08_negro_2_a_negro_3` |
| `ROJO_NEGRO_3_PUM`, `NEGRO_3_DAN` | `b09_negro_3_a_negro_4` |
| `NEGRO_4_DAN` | `b10_negro_4_a_negro_5` |
| `NEGRO_5_DAN` | `b11_negro_5_a_maximo` |

## 6. Tarifas

Fuente: `config/TarifaConfig.java:43-68` (cuantías) y `95-117` (regla por rol). Disciplinas
admitidas por tarifa en `services/PreinscripcionService.java:146` (`validarDatosDeporte`).

| Tarifa | Cuantía (€) | Disciplina admitida |
|---|---|---|
| `ADULTO` | 30.0 | Taekwondo |
| `ADULTO_GRUPO` | 20.0 | Taekwondo |
| `FAMILIAR` | 0.0 | Taekwondo |
| `INFANTIL` | 28.0 | Taekwondo |
| `INFANTIL_GRUPO` | 20.0 | Taekwondo |
| `HERMANOS` | 26.0 | Taekwondo |
| `PADRES_HIJOS` | 0.0 sin rol; ver tabla siguiente | Taekwondo |
| `KICKBOXING` | 30.0 | Kickboxing |
| `PILATES` | 30.0 | Pilates |
| `DEFENSA_PERSONAL_FEMENINA` | 25.0 | Defensa personal femenina |

Regla de rol (`obtenerCuantiaConRol`), solo para `PADRES_HIJOS`; el resto de tarifas ignora el rol:

| Rol familiar | Cuantía `PADRES_HIJOS` (€) |
|---|---|
| `PADRE` | 28.0 |
| `MADRE` | 28.0 |
| `HIJO` | 26.0 |
| `HIJA` | 26.0 |
| `NINGUNO` | 0.0 |
| `null` | 0.0 |

Reglas familiares observadas:

- `PADRES_HIJOS`: precio por miembro según su rol; no hay cálculo conjunto de la unidad familiar.
  La preinscripción exige rol distinto de `NINGUNO` (`PreinscripcionService.java:146`).
- `HERMANOS`: 26.0 por miembro, fijo, sin depender del rol ni del número de hermanos. La
  preinscripción exige `grupoFamiliar` no vacío (`PreinscripcionService.java:146`); el grupo no se
  usa para el importe.
- `FAMILIAR`: 0.0; sin regla adicional en `TarifaConfig`.
- La cuantía calculada solo se aplica si el DTO no trae una cuantía explícita
  (`AlumnoServiceImpl.java:371-373` y `682-684`); la preinscripción guarda la cuantía recibida.
- Tarifa nula: `IllegalArgumentException`.

## 7. Productos buscados por nombre

| Concepto | Precio fijado en código | Uso | Fuente |
|---|---|---|---|
| MENSUALIDAD | 28.0 (semilla) | Producto base de mensualidades; concepto final «MENSUALIDAD <MES> <AÑO>» | `config/InicializadorDatos.java:273`; búsquedas en `ProductoAlumnoServiceImpl.java:353`, `433`, `504`, `1056`, `1125` y `AlumnoServiceImpl.java:2288`; formato en `utils/MensualidadUtils.java:16-24` |
| LICENCIA FEDERATIVA | 0.0 (semilla) | Producto genérico de licencia | `InicializadorDatos.java:274` |
| RESERVA DE PLAZA | 10.0 (semilla) | Reserva de plaza | `InicializadorDatos.java:275`; búsquedas en `ProductoAlumnoServiceImpl.java:278`, `326` |
| TARIFA COMPETIDOR TAEKWONDO | 20.0 (semilla y `PRECIO_TARIFA_COMPETIDOR`) | Competidor Taekwondo | `InicializadorDatos.java:276`; `ProductoAlumnoServiceImpl.java:46`, `48`, `1208-1215` |
| TARIFA COMPETIDOR KICKBOXING | 20.0 (semilla y `PRECIO_TARIFA_COMPETIDOR`) | Competidor Kickboxing | `InicializadorDatos.java:277`; `ProductoAlumnoServiceImpl.java:47`, `48`, `1208-1215` |
| LICENCIA FEDERATIVA TAEKWONDO ADULTO | 46.0 (respaldo) | Licencia TKD adulto, enero-agosto | `ProductoAlumnoServiceImpl.java:49`, `839-865`, `867-878` |
| LICENCIA FEDERATIVA TAEKWONDO INFANTIL | 35.0 (respaldo) | Licencia TKD menor, enero-agosto | `ProductoAlumnoServiceImpl.java:50` |
| LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD | 30.0 (respaldo) | Licencia TKD con discapacidad, enero-agosto | `ProductoAlumnoServiceImpl.java:51` |
| PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO ADULTO | 35.0 (respaldo) | Desde septiembre | `ProductoAlumnoServiceImpl.java:54-55` |
| PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO INFANTIL | 20.0 (respaldo) | Desde septiembre | `ProductoAlumnoServiceImpl.java:56-57` |
| PARTE PROPORCIONAL LICENCIA FEDERATIVA TAEKWONDO DISCAPACIDAD | 20.0 (respaldo) | Desde septiembre | `ProductoAlumnoServiceImpl.java:58-59` |
| LICENCIA FEDERATIVA KICKBOXING ADULTO | ver nota | Licencia KB adulto (sin variante proporcional) | `ProductoAlumnoServiceImpl.java:52` |
| LICENCIA FEDERATIVA KICKBOXING INFANTIL | ver nota | Licencia KB menor (sin variante proporcional) | `ProductoAlumnoServiceImpl.java:53` |
| Productos de examen y recompensa | — | Ver sección 1 | `entities/TipoGrado.java:9-27`, `AlumnoServiceImpl.java:98-101` |

Notas:

- Mes de corte de licencia: septiembre (`MES_CORTE_LICENCIA = 9`,
  `ProductoAlumnoServiceImpl.java:64`). Formato de fechas del concepto: `dd/MM/yyyy` (`:65`).
- Precio de respaldo (`calcularPrecioLicenciaFallback`, `ProductoAlumnoServiceImpl.java:867-878`),
  usado cuando no se encuentra el producto: discapacidad 30.0 / 20.0; menor 35.0 / 20.0; adulto
  46.0 / 35.0 (antes / desde septiembre). No distingue Kickboxing: aplica la tabla de menor/adulto
  con la edad adulta de Kickboxing.
- Si falta el producto de tarifa competidor, se crea con precio 20.0
  (`ProductoAlumnoServiceImpl.java:1245-1252`).
- Prefijos y subcadenas con significado:
  - «DERECHO A EXAMEN» o «PASE DE GRADO POR RECOMPENSA» marcan derecho a examen
    (`ProductoAlumnoServiceImpl.java:123-124`, `197-198`, `257`). Ningún producto de `TipoGrado`
    contiene «PASE DE GRADO POR RECOMPENSA».
  - «ALTA LICENCIA FEDERATIVA <mes-año>» (`:757`), «RENOVACION <producto> <MES> <AÑO>» (`:799`),
    filtro «LICENCIA FEDERATIVA» (`:953`).
  - Tesorería, conceptos certificables: empiezan por MENSUALIDAD, TARIFA COMPETIDOR, RESERVA DE
    PLAZA, MATRICULA o MATRÍCULA (`TesoreriaServiceImpl.java:407-416`). Con periodo mensual:
    MENSUALIDAD y TARIFA COMPETIDOR (`:774-778`).
  - Tesorería, categoría (`TesoreriaServiceImpl.java:905-927`): MENSUALIDAD → `MENSUALIDAD`;
    TARIFA COMPETIDOR → `TARIFA_COMPETIDOR`; patrón `\s*MATR[IÍ]CULA\b` (`:63`) → `MATRICULA`;
    contiene LICENCIA → `LICENCIA`; RESERVA DE PLAZA → `RESERVA_PLAZA`; contiene EXAMEN o
    RECOMPENSA → `EXAMEN`; resto → `OTRO`.
  - Tesorería, disciplina deducida del concepto (`TesoreriaServiceImpl.java:881-903`): contiene
    TAEKWONDO, KICKBOXING, PILATES, DEFENSA o PERSONAL, en ese orden; si no, `GENERAL`.
- `TesoreriaServiceImpl.java:64-67` contiene constantes con los datos legales del club y de su
  responsable para el certificado de cobros: [dato personal omitido]. Pasan a columnas de
  `organizacion`.

## 8. Nombre visible del grado por disciplina

Fuente: `utils/GradoUtils.java:23-39` (sobrecarga con `TipoGrado`) y `49-60` (sobrecarga con
`String`).

| Disciplina | Regla |
|---|---|
| `KICKBOXING` | `ROJO` se muestra como «MARRÓN». El resto, igual que la columna «Nombre» de la sección 1. |
| `TAEKWONDO`, `PILATES`, `DEFENSA_PERSONAL_FEMENINA`, `null` | Columna «Nombre» de la sección 1. |

Detalles: la sobrecarga con `String` traduce solo el literal «rojo» (sin distinguir mayúsculas);
«Azul-Rojo» y los PUM no se traducen en Kickboxing. Grado nulo o cadena vacía → «N/A».

## 9. Anexo: categorías por edad

Fuera del encargo principal, se recoge porque `InicializadorDatos` las siembra
(`InicializadorDatos.java:245-253`).

| Categoría | Rango declarado (`entities/TipoCategoria.java:4-5`) | Regla aplicada (`AlumnoServiceImpl.java:1869-1884`) |
|---|---|---|
| Infantil | 8-9 | edad <= 9 |
| Precadete | 10-11 | 10-11 |
| Cadete | 12-13 | 12-13 |
| Junior | 14-16 | 14-16 |
| Senior | 17 o más | edad >= 17 |

La regla aplicada incluye en Infantil a los menores de 8 años; el rango declarado no.
