-- Comprobación posterior de V31_15 (pasar lista, decisión 324). Solo lectura y SOLO RECUENTOS: no devuelve nombres, fechas,
-- emails ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_31_15                 1   V31_15 aplicada con éxito
--   ultima_es_31_15              1   es la última versión aplicada
--   tablas                       2   clase_asistencia y asistencia_alumno existen
--   claves_ajenas                8   las de las dos tablas, con su regla de borrado
--   unicas                       2   UNIQUE (turno_id, fecha) y UNIQUE (clase_id, alumno_id)
--   clases_con_lista             0   V31_15 no rellena nada. Crece cuando se pasa lista.
--   filas_de_asistencia          0   ídem.
--   turnos_con_horario_raro      0   turnos con una hora vacía o de más de 5 caracteres: no se les puede pasar lista
--                                    (400). Si sale más de 0, avisa: hay que corregir esos turnos.
-- Informativa:
--   turnos                           total de turnos; no cambia con V31_15
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.15' AND success = 1)
    AS flyway_31_15,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '31.15')
    AS ultima_es_31_15,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('clase_asistencia', 'asistencia_alumno'))
    AS tablas,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND (
      (TABLE_NAME = 'clase_asistencia' AND DELETE_RULE = 'NO ACTION' AND REFERENCED_TABLE_NAME = 'organizacion')
      OR (TABLE_NAME = 'clase_asistencia' AND DELETE_RULE = 'CASCADE' AND REFERENCED_TABLE_NAME = 'grupo')
      OR (TABLE_NAME = 'clase_asistencia' AND DELETE_RULE = 'SET NULL' AND REFERENCED_TABLE_NAME IN ('turno', 'usuario'))
      OR (TABLE_NAME = 'asistencia_alumno' AND DELETE_RULE = 'NO ACTION' AND REFERENCED_TABLE_NAME = 'organizacion')
      OR (TABLE_NAME = 'asistencia_alumno' AND DELETE_RULE = 'CASCADE'
          AND REFERENCED_TABLE_NAME IN ('clase_asistencia', 'alumno'))))
    AS claves_ajenas,
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'UNIQUE'
      AND CONSTRAINT_NAME IN ('uk_clase_asistencia_turno_fecha', 'uk_asistencia_alumno_clase_alumno'))
    AS unicas,
  (SELECT COUNT(*) FROM clase_asistencia)
    AS clases_con_lista,
  (SELECT COUNT(*) FROM asistencia_alumno)
    AS filas_de_asistencia,
  (SELECT COUNT(*) FROM turno
    WHERE hora_inicio IS NULL OR hora_fin IS NULL OR hora_inicio = '' OR hora_fin = ''
       OR CHAR_LENGTH(hora_inicio) > 5 OR CHAR_LENGTH(hora_fin) > 5)
    AS turnos_con_horario_raro,
  (SELECT COUNT(*) FROM turno)
    AS turnos;
