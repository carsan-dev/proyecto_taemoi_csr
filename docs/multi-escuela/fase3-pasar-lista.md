# Pasar lista (decisión 324)

Boceto: artifact `PBbgzg4a9WCGPjp3JhkNmz`. Backend en la rama `feat/pasar-lista`; el PDF mensual sigue como respaldo.

## Modelo (V31_15, solo tablas nuevas)

- `clase_asistencia`: un turno y una fecha con lista guardada. UNIQUE (`turno_id`, `fecha`). Horario copiado al guardar; registro y última modificación. Borrar el grupo la borra; borrar el turno deja `turno_id` NULL y conserva el histórico.
- `asistencia_alumno`: una fila por CADA alumno de la lista, presente o no. UNIQUE (`clase_id`, `alumno_id`). Borrar o anonimizar al alumno quita sus filas.
- Clase sin fila en `clase_asistencia`: «sin lista», no cuenta.

## Reglas

- **La lista es del turno, no del grupo.** Lista actual: alumnos del turno ∩ del grupo ∩ activos en el deporte del grupo ∩ con fecha de nacimiento; por nombre y apellidos (el criterio del PDF). Un alumno del turno del lunes no sale en la lista del miércoles, no suma falta ese día y su detalle solo trae sus lunes. `Clase.alumnos` y `Clase.total` son los de esa lista.
- La lista de una clase es lista actual ∪ alumnos con fila guardada: quien causa baja sigue en la lista ya guardada.
- PUT: reemplaza la lista, idempotente, gana la última escritura. Una sola fila de auditoría.
- PUT, por orden: turno de otro grupo 404; fecha no válida, de otro día de la semana o futura 400; profesor antes de hoy − 14 días 400; `presentes` ausente o con null 400; turno con hora no válida 400; lista guardada con otro grupo (turno movido) 409; turno sin alumnos 400 (no crea clase); id fuera de la lista 400. «Hoy»: Europe/Madrid.
- GET de una clase: turno de otro grupo 404; fecha que no cae en el turno 400 salvo que tenga lista guardada. El límite de 14 días es solo del PUT.
- Acceso: grupo ajeno 403. Gestión con alcance total: grupo de otra escuela o inexistente 404. Errores: `{"mensaje": "..."}`. Año fuera de 1900-2999 o mes fuera de 1-12: 400.

## API

Bajo `/api/profesor/grupos/{grupoId}` y `/api/grupos/{grupoId}`, con las mismas formas. Fechas `yyyy-MM-dd`, horas `HH:mm`.

`Clase`: `presentes` y `total` son `null` sin lista. `pasada`: ya terminó (hoy, con `horaFin` alcanzada). `alumnos`: tamaño de la lista actual; 0, turno sin alumnos.

```json
{"turnoId": 9, "fecha": "2026-10-26", "horaInicio": "18:00", "horaFin": "19:00", "pasada": true, "presentes": 7, "total": 9, "alumnos": 9}
```

1. `GET /clases`: `hoy` por hora; `recientes` de hoy − 14 días a hoy, de más nueva a más antigua y por hora; `proxima`, la primera que no ha empezado, o `null`. Un turno con día desconocido se descarta; una clase de turno borrado no sale aquí.

```json
{"hoy": [Clase], "recientes": [Clase], "proxima": {"fecha": "2026-10-27", "horaInicio": "17:00"}}
```

2. `GET /clases/{turnoId}/{fecha}`: `deportes` con la forma de `GET /api/profesor/grupos/{grupoId}/alumnos`.

```json
{"clase": Clase, "alumnos": [{"id": 12, "nombre": "A", "apellidos": "B", "presente": true, "deportes": [{"deporte": "TAEKWONDO", "deporteNombre": "Taekwondo", "grado": {"codigo": "AMARILLO", "nombre": "Amarillo"}, "fechaGrado": "2025-01-10", "apto": false, "delGrupo": true}]}]}
```

3. `PUT /clases/{turnoId}/{fecha}`: cuerpo `{"presentes": [12, 15]}`; devuelve `Clase`.

4. `GET /asistencia/resumen?year&month`: alumnos con alguna fila ese mes en clases del grupo; también quien ya salió del grupo. `clases`: sus filas; `presentes`: las que tienen presente.

```json
[{"alumnoId": 12, "presentes": 3, "clases": 4}]
```

5. `GET /alumnos/{alumnoId}/asistencia?year&month`: el alumno debe ser del grupo (404). `dias` por fecha y hora: las fechas de SUS turnos hasta hoy, más sus filas de turnos borrados o que ya no son suyos. `presentes` y `clases` coinciden con el resumen; `SIN_LISTA` no cuenta.

```json
{"presentes": 1, "clases": 2, "dias": [{"fecha": "2026-10-19", "horaInicio": "18:00", "estado": "PRESENTE | FALTA | SIN_LISTA"}]}
```

## Motivos y festivos (decisiones 455-463)

Modelo en V32_2 (solo amplía): `asistencia_alumno.motivo_falta` (VARCHAR(20) NULL), `festivo` y `festivo_sede`.
Postcheck: `postcheck-asistencia-motivos-festivos.sql`.

### Reglas

- **Motivo de falta**, opcional: `INJUSTIFICADA`, `MEDICA`, `FAMILIAR` u `OTRA_JUSTIFICADA`. Solo en faltas; un presente
  no lleva motivo. Sin motivo, la falta es como antes. «Médica» es el único dato de salud de pasar lista (aceptado por el
  propietario) y no lleva detalle.
- **Festivo**: días sin clase en una o varias sedes de la escuela. `DIA` (un día), `RANGO` (de una fecha a otra, ambas
  incluidas, como mucho 366 días) o `ANUAL` (mismo día y mes cada año; el 29 de febrero no se admite). Nombre opcional
  de hasta 60 caracteres (recortado; vacío = sin nombre).
- **Regla única**: `FestivoService.festivosEn(sedes, desde, hasta)`, por sede y fecha, en bloque. La sede de una clase es
  la del grupo del turno. Si en la misma sede y fecha caen varios, gana el más concreto (DIA, RANGO, ANUAL) y, a
  igualdad, el más antiguo.
- **Manda la lista**: una clase festiva que ya tiene lista guardada sigue normal (se ve, se corrige y sale con sus marcas
  en el PDF). Nunca se borra ninguna lista.
- Una clase festiva sin lista sigue saliendo en `hoy` y `recientes` (con `presentes` y `total` a `null`) y su lista se
  lee; no se puede guardar (409).
- **Permisos**: propietario y gestor, todas las sedes de su escuela; el responsable de sede ve los festivos que tocan
  alguna de sus sedes y crea, cambia o quita solo los que tienen TODAS sus sedes entre las suyas (el resto, `editable:
  false`, 403 al cambiarlo). El profesor no gestiona festivos: los ve en su lista de clases.
- PDF marcado (gestión y profesor): falta con motivo, su letra (`I`, `M`, `F`, `J`); falta sin motivo «–»; presente
  «X»; sin lista, vacía; clase festiva (festivo y sin lista), columna gris (#ECEEF1) con «Festivo» en vertical («Fest.»
  con un solo alumno; con más de 40 alumnos, celdas grises sin texto, porque la rejilla puede pasar de página).
  Leyenda en su propia línea bajo el contexto: «X: asistió · –: faltó · I: injustificada · M: médica · F: familiar · J:
  otra justificada · Gris: festivo · Vacía: sin lista». La hoja sin marcar no cambia y no consulta festivos.

### PUT de una clase, orden de los errores

Turno de otro grupo 404; fecha no válida, de otro día de la semana o futura 400; profesor antes de hoy − 14 días 400;
`presentes` ausente o con null 400; turno con hora no válida 400; lista guardada con otro grupo 409; turno sin alumnos
400; id de `presentes` fuera de la lista 400; alumno de `motivos` fuera de la lista 400; motivo desconocido 400; motivo
de un alumno presente 400; **clase festiva sin lista guardada 409** `{"mensaje": "Ese día es festivo en <sede>."}`.
Con lista guardada, el PUT se permite. El PUT reemplaza la lista entera: sin `motivos`, todas las faltas quedan sin
motivo. La auditoría del 2xx sigue sin ids de alumnos ni motivos; en los 4xx, `presentes` y `motivos` se guardan
redactados.

### Cambios en la API de pasar lista

- `Clase` + `festivo`: `{"id", "nombre", "tipo": "DIA | RANGO | ANUAL", "fechaDesde", "fechaHasta", "sedes": n,
  "deUnDiaYUnaSede": bool}` o `null`. Solo si la clase es festiva y no tiene lista guardada. `fechaDesde` y
  `fechaHasta`, las del festivo (ANUAL: la fecha de esta clase en las dos); `sedes`, cuántas sedes cubre.
- `GET /clases/{turnoId}/{fecha}` + `sede: {"id", "nombre"}` (la del grupo; `null` si no tuviera); cada alumno +
  `motivo` (`null` si estuvo, si la falta no tiene motivo o si no hay fila).
- `PUT /clases/{turnoId}/{fecha}`: `{"presentes": [12], "motivos": {"15": "MEDICA"}}`; `motivos` opcional (alumno →
  motivo; un valor `null` es «sin motivo»).
- `GET /alumnos/{alumnoId}/asistencia`: `estado` + `FESTIVO` (un día de sus turnos festivo, sin lista guardada de esa
  clase y sin fila suya; no cuenta en `clases`); cada día + `motivo` (solo en `FALTA` con motivo).

### API de festivos: `/api/festivos`

Propietario, gestor y responsable de sede; profesor, familia y sesión solo-profesor 403. Errores `{"mensaje": "..."}`:
400 datos, 403 sedes de otra escuela o fuera del alcance del responsable (o festivo que no puede cambiar), 404 festivo
de otra escuela o inexistente. Fechas `yyyy-MM-dd`. Las escrituras quedan en `auditoria_evento` (filtro genérico).

```json
{"id": 3, "tipo": "RANGO", "fechaDesde": "2026-12-22", "fechaHasta": "2027-01-07", "mes": null, "dia": null,
 "proximaFecha": "2026-12-22", "nombre": "Navidad", "sedes": [{"id": 1, "nombre": "Casariche"}],
 "todasLasSedes": false, "editable": true}
```

`proximaFecha`: DIA y RANGO, `fechaDesde`; ANUAL, su próxima aparición desde hoy, incluido. `todasLasSedes`: sus sedes
cubren todas las sedes ACTIVAS de la escuela. `editable`: para quien pregunta.

1. `GET /api/festivos?sedeId=&pasados=false`: los de hoy en adelante (`fechaHasta` ≥ hoy) y todos los ANUAL; con
   `pasados=true`, los de `fechaHasta` < hoy, sin los ANUAL. Por `proximaFecha` (ascendente) e id. `sedeId` de otra
   escuela 400; del responsable, una suya o 403.
2. `POST /api/festivos` con `{"tipo", "fechaDesde"?, "fechaHasta"?, "mes"?, "dia"?, "nombre"?, "sedeIds": [..]}` → 201
   `Festivo`. DIA usa `fechaDesde` (`fechaHasta`, si llega, igual); RANGO, las dos; ANUAL, `mes` y `dia`; lo que no
   corresponde al tipo se ignora. `sedeIds` vacío 400; repetidas, una vez.
3. `PUT /api/festivos/{id}`, mismo cuerpo → 200 `Festivo` (reemplaza forma y sedes). Orden: 404; 403 si el responsable
   no puede cambiarlo; 400 datos; 403 sedes nuevas fuera de su alcance.
4. `DELETE /api/festivos/{id}` → 204. 404; 403 si el responsable no puede quitarlo.
5. `POST /api/festivos/impacto`, mismo cuerpo + `excluirFestivoId`? (debe existir en la escuela, 404) → `{"clases",
   "conLista", "conListaDetalle": [{"sedeId", "sede", "fecha", "horaInicio", "grupo"}], "clasesDetalle": [{"sedeId",
   "sede", "fecha", "horaInicio", "horaFin", "grupo", "conLista": bool, "presentes": n|null, "total": n|null}]}`.
   Cada detalle, como mucho 20, por fecha, hora, sede y grupo: `conListaDetalle`, las que tienen lista;
   `clasesDetalle`, todas las afectadas, con las cifras de su lista si la tienen (`null` sin ella). Clases: cada turno de los grupos de esas sedes en cada fecha que cae en su día (ANUAL:
   solo su próxima aparición), sin las que ya son festivo por OTRO festivo (el `excluirFestivoId`, el que se edita, no
   cuenta). `conLista`: las que ya tienen lista guardada y seguirán normales. No guarda nada.

Mensajes de validación: «Elige si el festivo es un día, un periodo o cada año.», «Indica la fecha.», «Indica la fecha
de inicio y la de fin.», «La fecha no es válida.», «Un festivo de un día empieza y acaba el mismo día.», «La fecha de
fin debe ser posterior a la de inicio.», «Un periodo no puede pasar de 366 días.», «Indica el día y el mes.», «El mes no
es válido.», «Ese día no existe en ese mes.», «Elige otro día: el 29 de febrero no existe todos los años.», «El nombre
no puede pasar de 60 caracteres.», «Elige al menos una sede.».
