-- Comprobación posterior de V31_17 (tipo de discapacidad en alumno_salud). Solo lectura y SOLO RECUENTOS: no devuelve
-- identificadores ni contenido de salud.
--
-- Deben ser (justo tras desplegar):
--   flyway_31_17              1   V31_17 aplicada con éxito
--   ultima_es_31_17           1   es la última versión aplicada
--   columnas_nuevas           2   discapacidad_tipos y discapacidad_otra existen
--   filas_alumno_salud        N   filas de alumno_salud: las mismas que antes de desplegar
--   filas_con_tipos           0   V31_17 no rellena nada. Crece cuando se guardan tipos.
--   alumnos_con_discapacidad  N   alumnos con tiene_discapacidad = 1: los mismos que antes de desplegar
--   tipos_sin_discapacidad    0   filas con tipos o texto de «Otra» de un alumno sin discapacidad. Debe seguir en 0 siempre.
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.17' AND success = 1)
    AS flyway_31_17,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '31.17')
    AS ultima_es_31_17,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_salud'
      AND COLUMN_NAME IN ('discapacidad_tipos', 'discapacidad_otra'))
    AS columnas_nuevas,
  (SELECT COUNT(*) FROM alumno_salud)
    AS filas_alumno_salud,
  (SELECT COUNT(*) FROM alumno_salud WHERE discapacidad_tipos IS NOT NULL)
    AS filas_con_tipos,
  (SELECT COUNT(*) FROM alumno WHERE tiene_discapacidad = 1)
    AS alumnos_con_discapacidad,
  (SELECT COUNT(*) FROM alumno_salud s JOIN alumno a ON a.id = s.alumno_id
    WHERE (s.discapacidad_tipos IS NOT NULL OR s.discapacidad_otra IS NOT NULL)
      AND (a.tiene_discapacidad IS NULL OR a.tiene_discapacidad <> 1))
    AS tipos_sin_discapacidad;
