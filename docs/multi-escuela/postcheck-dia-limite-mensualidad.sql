-- Comprobación posterior de V32_3 (día límite de pago de la mensualidad por escuela, pieza 394). Solo lectura y SOLO
-- RECUENTOS: no devuelve nombres, datos legales ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_32_3                  1   V32_3 aplicada con éxito
--   ultima_es_32_3               1   es la última versión aplicada
--   columna_dia_limite           1   organizacion.dia_limite_mensualidad existe: tinyint NOT NULL DEFAULT 5
--   check_dia_limite             1   existe ck_organizacion_dia_limite y está activa
--   organizacion_1_en_5          1   Moiskimdo (organización 1) sigue con el día 5
--   escuelas_fuera_de_5          0   V32_3 deja todas en 5. Crece cuando una escuela cambia su día.
-- Informativa:
--   escuelas                         total de escuelas; no cambia con V32_3
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32.3' AND success = 1)
    AS flyway_32_3,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '32.3')
    AS ultima_es_32_3,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion' AND COLUMN_NAME = 'dia_limite_mensualidad'
      AND COLUMN_TYPE = 'tinyint' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = '5')
    AS columna_dia_limite,
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion'
      AND CONSTRAINT_NAME = 'ck_organizacion_dia_limite' AND CONSTRAINT_TYPE = 'CHECK' AND ENFORCED = 'YES')
    AS check_dia_limite,
  (SELECT COUNT(*) FROM organizacion WHERE id = 1 AND dia_limite_mensualidad = 5)
    AS organizacion_1_en_5,
  (SELECT COUNT(*) FROM organizacion WHERE dia_limite_mensualidad <> 5)
    AS escuelas_fuera_de_5,
  (SELECT COUNT(*) FROM organizacion)
    AS escuelas;
