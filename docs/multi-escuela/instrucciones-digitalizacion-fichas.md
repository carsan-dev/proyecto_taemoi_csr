# Instrucciones: herramienta local para digitalizar fichas de alumnos

Tarea para un agente de código (Codex): **programar** una herramienta que convierta las fotos de las
fichas en papel de «Taekwondo Sierra Sur» en un CSV. La herramienta la ejecuta una persona en su
propio equipo. El agente **no** procesa fichas reales.

## Privacidad (obligatorio)

- Las fichas reales contienen datos personales de menores y **datos de salud** (categoría especial
  del RGPD). Ningún dato puede salir del equipo.
- **No abras, listes, leas ni proceses nada** en `C:\Users\croly\Documents\datos-clientes\`. Esa
  carpeta no forma parte de esta tarea.
- Todo el reconocimiento se hace con un modelo local servido por Ollama en `http://localhost:11434`.
  Sin llamadas a servicios externos ni telemetría.
- Para desarrollar y probar usa solo **fichas sintéticas** que generes tú (ver «Pruebas»).

## Dónde va el código

- `scripts/digitalizar-fichas/` en este repositorio. Python 3.11 o superior, con dependencias en
  `requirements.txt` (por ejemplo `opencv-python`, `pillow`, `requests`, `pytest`).
- `README.md` en esa carpeta con la instalación (Ollama y `ollama pull qwen2.5vl:7b`) y el comando de uso.
- `.gitignore` en esa carpeta que ignore `salida/` y cualquier imagen o CSV fuera de `pruebas/`.

## Uso previsto

```
python digitalizar.py --fotos <carpeta_fotos> --salida <carpeta_salida> [--lote 20]
```

- Genera `<carpeta_salida>/alumnos.csv` con la cabecera **exacta** de
  `docs/multi-escuela/plantilla-alumnos.csv`: separador `;`, UTF-8 con BOM, una fila por ficha.
- Genera `<carpeta_salida>/informe-revision.md` (ver el final de este documento).
- Es reanudable: si una foto ya tiene fila en `alumnos.csv` (por `archivo_origen`), se salta.
- Si una foto no parece una ficha, no crea fila y la anota en el informe.

## Cómo funciona

1. **Enderezar.** Detecta los bordes de la hoja y corrige perspectiva y rotación: son fotos de móvil
   inclinadas. Si no detecta la hoja, sigue con la imagen original y marca `foto_no_enderezada`.
2. **Reducir.** Antes de procesar, redimensiona a un máximo de 1920 px de ancho (la GPU tiene 8 GB).
3. **Recortar por campo.** La ficha siempre es la misma plantilla. Define en `plantilla_regiones.json`
   la caja de cada campo en coordenadas relativas (0–1) de la hoja enderezada, calibradas sobre la
   ficha en blanco.
4. **Leer cada recorte por separado** con `qwen2.5vl:7b`, temperatura 0, pidiendo solo el texto
   escrito a mano, sin el texto impreso del formulario, o la palabra `ILEGIBLE`. Un recorte por
   petición reduce las invenciones del modelo.
5. **Casillas sin modelo.** `firma` y `tiene_foto` se deciden por densidad de tinta en su recuadro.
6. **Normalizar y validar** cada campo con las reglas de este documento.
7. **Marcar para revisar** cualquier campo `ILEGIBLE`, vacío cuando no debería o fuera de formato.

El modelo puede devolver texto plausible pero inventado. Ningún resultado es fiable sin revisión
humana, y el informe debe decirlo explícitamente.

## Pruebas

- El usuario aportará una foto de la ficha **en blanco** en `pruebas/plantilla-blanco.jpg`.
- Genera al menos 10 fichas sintéticas sobre esa plantilla, con datos inventados en una fuente
  manuscrita libre y con rotaciones y perspectivas leves. Guarda junto a ellas su CSV esperado.
- Siembra errores a propósito (letra de DNI incorrecta, fecha imposible, un duplicado) y comprueba
  que la herramienta los marca.
- Tests con `pytest` para normalización y validación, ejecutables sin GPU.
- Mide y documenta en el README el porcentaje de campos acertados con las fichas sintéticas.

## Regla de oro

**La herramienta no inventa nada.** Si un campo está vacío en el papel, queda vacío. Si no se lee bien,
se escribe lo que se lea, con `?` en cada carácter ilegible, y la fila se marca para revisar.

## Columnas

| Columna | Origen en la ficha | Formato y reglas |
|---|---|---|
| `archivo_origen` | Nombre del fichero de la foto | Tal cual, con extensión |
| `revisar` | Calculado | `si` si cualquier regla de este documento lo pide; si no, `no` |
| `motivo_revision` | Calculado | Motivos breves separados por comas, p. ej. `dni_letra_invalida,nombre_dudoso` |
| `escuela` | Cabecera | `Taekwondo Sierra Sur` |
| `numero_manuscrito` | Número escrito a mano en la ficha (p. ej. al pie) | Tal cual. Vacío si no hay |
| `anio_entrada` | «AÑO DE ENTRADA» | 4 dígitos. Si pone 2 dígitos (`23`), escribe `2023` y marca `anio_dos_digitos` |
| `anio_ficha` | «ACTUAL» (año en que se rellenó la ficha) | 4 dígitos, mismas reglas |
| `nombre` | «NOMBRE Y APELLIDOS» | Ver «Separar nombre y apellidos» |
| `apellidos` | «NOMBRE Y APELLIDOS» | Ídem |
| `domicilio` | «DOMICILIO» | Tal cual (calle, número, piso) |
| `ciudad` | «CIUDAD» | Tal cual |
| `codigo_postal` | «C.P.» | 5 dígitos. Si no, marca `cp_invalido` |
| `fecha_nacimiento` | «FECHA DE NACIMIENTO» | `AAAA-MM-DD`. Año de 2 dígitos o fecha imposible: marca `fecha_dudosa` |
| `dni` | «D.N.I.» | Sin espacios ni guiones, en mayúsculas. Ver «Validar DNI/NIE» |
| `telefono_1` | «NÚMEROS TLF.» (primero) | Solo dígitos. 9 dígitos que empiecen por 6, 7, 8 o 9; si no, marca `telefono_invalido` |
| `telefono_2` | «NÚMEROS TLF.» (segundo, si hay) | Ídem |
| `tutor_1_nombre` | «NOMBRE DEL PADRE» | Tal cual |
| `tutor_1_parentesco` | Calculado | `padre` si hay nombre; vacío si no |
| `tutor_2_nombre` | «NOMBRE DE LA MADRE» | Tal cual |
| `tutor_2_parentesco` | Calculado | `madre` si hay nombre; vacío si no |
| `salud_lesion_enfermedad` | «¿Has tenido / tiene su hijo alguna lesión o enfermedad?» | `si`, `no` o vacío |
| `salud_descripcion` | «DESCRIBA CUÁL» | Tal cual |
| `salud_impide_actividad` | «¿Te/le imposibilita hacer alguna actividad física?» | `si`, `no` o vacío. Si hay texto además del sí/no, va a `salud_impide_detalle` |
| `salud_impide_detalle` | Ídem | Tal cual |
| `alergias` | «¿Tiene alguna alergia o alimento que no pueda consumir?» | Tal cual. `no` si pone no o «ninguna» |
| `firma` | Recuadros de firma | `mayor`, `tutor`, `ambas` o `ninguna` |
| `tiene_foto` | Recuadro «FOTO» | `si` si hay una foto pegada; `no` si está vacío |

**No transcribas** (no hacen falta y el RGPD obliga a no guardarlos): lugar de nacimiento,
centro donde estudia y profesiones de los padres.

## Separar nombre y apellidos

- 3 palabras o más: las dos últimas son los apellidos y el resto es el nombre
  («María José García López» → nombre `María José`, apellidos `García López`).
- Partículas de apellidos compuestos («de», «del», «de la», «y») van con el apellido que las sigue.
- 2 palabras: nombre y un solo apellido; marca `un_apellido`.
- Si dudas, aplica la regla y marca `nombre_dudoso`.
- Escribe con mayúscula inicial («GARCÍA» → `García`) y conserva las tildes.

## Validar DNI/NIE

- DNI: 8 dígitos y una letra. La letra es `TRWAGMYFPDXBNJZSQVHLCKE[número % 23]`.
- NIE: `X`, `Y` o `Z`, 7 dígitos y una letra. Sustituye X=0, Y=1, Z=2 y aplica la misma fórmula.
- Si la letra no cuadra, conserva lo transcrito y marca `dni_letra_invalida`.
- Los menores pueden no tener DNI: vacío no es un error.

## Comprobaciones al terminar cada lote

La herramienta aplica estas comprobaciones al terminar cada lote y marca:

- `anio_entrada` posterior a `anio_ficha` → `anio_entrada_posterior`.
- Edad al rellenar la ficha (`anio_ficha` − año de nacimiento) fuera de 3–90 → `edad_improbable`.
- DNI repetido, o mismo nombre + apellidos + fecha de nacimiento → `posible_duplicado` en ambas filas.
- Menor de edad al rellenar la ficha con `firma` = `mayor` → `firma_incoherente`.
- Cabecera del CSV distinta de la plantilla → error que detiene el proceso.

## Informe de revisión

`salida/informe-revision.md` contiene solo:

- Total de fotos procesadas, filas creadas y filas con `revisar = si`.
- Recuento por motivo de revisión.
- Lista de `archivo_origen` con `revisar = si` y sus motivos, **sin datos personales**.
- Fotos descartadas y por qué (no es una ficha, ilegible del todo, duplicada).

El cliente revisa las filas marcadas comparándolas con la foto original antes de importar nada.