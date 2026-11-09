-- Comprobación posterior de V32_8 (rallas hacia el examen). Solo lectura y solo recuentos: sin datos personales.
--
-- Deben ser (justo tras desplegar):
--   flyway_32_8                  1   V32_8 aplicada con éxito
--   ultima_es_32_8               1   es la última versión aplicada
--   columna_medias_rallas        1   alumno_deporte.medias_rallas existe: tinyint NOT NULL DEFAULT 0
--   check_medias_rallas          1   existe ck_alumno_deporte_medias_rallas y está activa
--   columna_rallas_activas       1   disciplina.rallas_activas existe: tinyint(1) NOT NULL DEFAULT 0
--   inscripciones_con_rallas     0   ningún alumno tiene rallas. Crecerá cuando una escuela las active y las dé.
--   fuera_de_rango               0   ninguna inscripción fuera de 0 a 10 (el CHECK lo impide)
--   disciplinas_con_rallas       0   ninguna disciplina las tiene activas. Crecerá cuando una escuela las active.
-- Informativas:
--   inscripciones                    total de alumno_deporte; no cambia con V32_8
--   disciplinas                      total de disciplinas de todas las escuelas; no cambia con V32_8
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32.8' AND success = 1)
    AS flyway_32_8,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '32.8')
    AS ultima_es_32_8,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_deporte' AND COLUMN_NAME = 'medias_rallas'
      AND COLUMN_TYPE = 'tinyint' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = '0')
    AS columna_medias_rallas,
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno_deporte'
      AND CONSTRAINT_NAME = 'ck_alumno_deporte_medias_rallas' AND CONSTRAINT_TYPE = 'CHECK' AND ENFORCED = 'YES')
    AS check_medias_rallas,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'disciplina' AND COLUMN_NAME = 'rallas_activas'
      AND COLUMN_TYPE = 'tinyint(1)' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = '0')
    AS columna_rallas_activas,
  (SELECT COUNT(*) FROM alumno_deporte WHERE medias_rallas <> 0)
    AS inscripciones_con_rallas,
  (SELECT COUNT(*) FROM alumno_deporte WHERE medias_rallas < 0 OR medias_rallas > 10)
    AS fuera_de_rango,
  (SELECT COUNT(*) FROM disciplina WHERE rallas_activas = TRUE)
    AS disciplinas_con_rallas,
  (SELECT COUNT(*) FROM alumno_deporte)
    AS inscripciones,
  (SELECT COUNT(*) FROM disciplina)
    AS disciplinas;
