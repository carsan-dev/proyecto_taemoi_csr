-- Comprobación posterior de V31_6 (cargos cancelados, pieza 6b-2c). Solo lectura y SOLO RECUENTOS: no devuelve nombres,
-- correos, DNI, importes ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   columnas_cancelacion          3   cancelado_en, cancelado_por_usuario_id y motivo_cancelacion existen
--   columnas_nulables             3   las tres admiten NULL
--   idx_cancelado_por             1   índice idx_producto_alumno_cancelado_por
--   fk_cancelado_por              1   FK fk_producto_alumno_cancelado_por a usuario
--   fk_cancelado_por_set_null     1   esa FK es ON DELETE SET NULL
--   flyway_31_6                   1   V31_6 aplicada con éxito
--   cargos_cancelados             0   V31_6 no escribe datos. Tras las primeras cancelaciones crece; las demás siguen valiendo.
-- Informativas (deben ser 0 siempre, también tras cancelar):
--   cancelados_pagados            cargos cancelados con pagado = 1 (cancelado => no pagado)
--   cancelados_con_cobro_activo   cargos cancelados con un cobro ACTIVO
--   cancelados_sin_motivo         cargos cancelados sin motivo
--   datos_sin_cancelar            cargos vigentes (cancelado_en NULL) con motivo o con quién canceló
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
      AND COLUMN_NAME IN ('cancelado_en', 'cancelado_por_usuario_id', 'motivo_cancelacion'))
    AS columnas_cancelacion,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno' AND IS_NULLABLE = 'YES'
      AND COLUMN_NAME IN ('cancelado_en', 'cancelado_por_usuario_id', 'motivo_cancelacion'))
    AS columnas_nulables,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
      AND INDEX_NAME = 'idx_producto_alumno_cancelado_por')
    AS idx_cancelado_por,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
      AND CONSTRAINT_NAME = 'fk_producto_alumno_cancelado_por')
    AS fk_cancelado_por,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
      AND CONSTRAINT_NAME = 'fk_producto_alumno_cancelado_por' AND DELETE_RULE = 'SET NULL')
    AS fk_cancelado_por_set_null,
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.6' AND success = 1)
    AS flyway_31_6,
  (SELECT COUNT(*) FROM producto_alumno WHERE cancelado_en IS NOT NULL)
    AS cargos_cancelados,
  (SELECT COUNT(*) FROM producto_alumno WHERE cancelado_en IS NOT NULL AND pagado = 1)
    AS cancelados_pagados,
  (SELECT COUNT(*) FROM producto_alumno pa JOIN cobro c ON c.producto_alumno_id = pa.id
    WHERE pa.cancelado_en IS NOT NULL AND c.estado = 'ACTIVO')
    AS cancelados_con_cobro_activo,
  (SELECT COUNT(*) FROM producto_alumno
    WHERE cancelado_en IS NOT NULL AND (motivo_cancelacion IS NULL OR TRIM(motivo_cancelacion) = ''))
    AS cancelados_sin_motivo,
  (SELECT COUNT(*) FROM producto_alumno
    WHERE cancelado_en IS NULL AND (motivo_cancelacion IS NOT NULL OR cancelado_por_usuario_id IS NOT NULL))
    AS datos_sin_cancelar;
