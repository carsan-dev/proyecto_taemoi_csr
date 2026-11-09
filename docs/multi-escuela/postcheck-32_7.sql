-- Comprobación posterior de V32_7 (mascota del panel por escuela). Solo lectura. La primera consulta son recuentos; la
-- segunda lista cada escuela con su id, su nombre y su mascota (sin datos personales: solo el nombre de la escuela).
--
-- Primera consulta. Deben ser (justo tras desplegar):
--   flyway_32_7                  1   V32_7 aplicada con éxito
--   ultima_es_32_7               1   es la última versión aplicada
--   columna_mascota              1   organizacion.mascota existe: varchar(16) NOT NULL DEFAULT 'TAMI'
--   check_mascota                1   existe ck_organizacion_mascota y está activa
--   organizacion_1_maki          1   Moiskimdo (organización 1) lleva MAKI
--   otras_en_maki                0   ninguna otra escuela lleva MAKI. Crecerá cuando se pueda elegir mascota.
-- Informativa:
--   escuelas                         total de escuelas; no cambia con V32_7
--
-- Segunda consulta: id, nombre y mascota de cada escuela. La 1 en MAKI; el resto en TAMI.
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32.7' AND success = 1)
    AS flyway_32_7,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '32.7')
    AS ultima_es_32_7,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion' AND COLUMN_NAME = 'mascota'
      AND COLUMN_TYPE = 'varchar(16)' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = 'TAMI')
    AS columna_mascota,
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion'
      AND CONSTRAINT_NAME = 'ck_organizacion_mascota' AND CONSTRAINT_TYPE = 'CHECK' AND ENFORCED = 'YES')
    AS check_mascota,
  (SELECT COUNT(*) FROM organizacion WHERE id = 1 AND mascota = 'MAKI')
    AS organizacion_1_maki,
  (SELECT COUNT(*) FROM organizacion WHERE id <> 1 AND mascota = 'MAKI')
    AS otras_en_maki,
  (SELECT COUNT(*) FROM organizacion)
    AS escuelas;

SELECT id, nombre, mascota FROM organizacion ORDER BY id;
