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
