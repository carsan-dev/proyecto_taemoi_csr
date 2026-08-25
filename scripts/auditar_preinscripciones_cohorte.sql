-- Auditoría de preinscripciones activas elegidas con edad cumplida en vez de cohorte.
-- Solo lectura: no modifica turnos ni solicitudes.
WITH seleccion_incompatible AS (
  SELECT
    p.id AS preinscripcion_id,
    p.referencia,
    p.nombre,
    p.apellidos,
    p.fecha_nacimiento,
    p.temporada,
    p.deporte,
    p.estado,
    CAST(SUBSTRING(p.temporada, 1, 4) AS UNSIGNED) - YEAR(p.fecha_nacimiento) AS edad_cohorte,
    t.id AS turno_id,
    t.dia_semana,
    t.hora_inicio,
    t.hora_fin,
    g.id AS grupo_id,
    g.nombre AS grupo,
    COALESCE(g.rango_edad_min, 0) AS rango_edad_min,
    COALESCE(g.rango_edad_max, 99) AS rango_edad_max
  FROM preinscripcion p
  JOIN preinscripcion_turno pt ON pt.preinscripcion_id = p.id
  JOIN turno t ON t.id = pt.turno_id
  JOIN grupo g ON g.id = t.grupo_id
  WHERE p.estado <> 'CANCELADA'
    AND p.temporada REGEXP '^[0-9]{4}-[0-9]{4}$'
)
SELECT
  referencia,
  CONCAT(nombre, ' ', apellidos) AS alumno,
  fecha_nacimiento,
  temporada,
  deporte,
  estado,
  edad_cohorte,
  grupo_id,
  grupo,
  turno_id,
  dia_semana,
  hora_inicio,
  hora_fin,
  rango_edad_min,
  rango_edad_max
FROM seleccion_incompatible
WHERE edad_cohorte NOT BETWEEN rango_edad_min AND rango_edad_max
ORDER BY temporada DESC, referencia, dia_semana, hora_inicio;
