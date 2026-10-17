-- Sede principal de los alumnos dados de alta después de V31 (entrega 6-1b, docs/multi-escuela/entrega6-plan.md
-- §4.1 y §4.5).
--
-- SOLO DATOS: no cambia el esquema. Hasta la entrega 6 nadie escribía alumno.sede_principal_id, así que las altas
-- posteriores a V31 quedaron a NULL. Es el MISMO UPDATE que V31 §4: en las escuelas con UNA sola sede, los alumnos
-- sin sede principal reciben esa sede.
--   - Idempotente: solo toca filas con sede_principal_id IS NULL; repetirlo no cambia nada.
--   - No toca las filas ya rellenas ni las escuelas con varias sedes (o sin ninguna).
--   - El código anterior sigue funcionando (la columna ya existía).
-- Comprobación posterior: docs/multi-escuela/postcheck-v31_2.sql. Va como V31_2 porque V32 está reservada para la
-- contracción multi-escuela.
UPDATE alumno a
JOIN (SELECT organizacion_id, MIN(id) AS sede_id
      FROM sede
      GROUP BY organizacion_id
      HAVING COUNT(*) = 1) unica
  ON unica.organizacion_id = a.organizacion_id
SET a.sede_principal_id = unica.sede_id
WHERE a.sede_principal_id IS NULL;
