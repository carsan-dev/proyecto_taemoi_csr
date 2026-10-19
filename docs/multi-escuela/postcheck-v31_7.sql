-- Comprobación posterior de V31_7 (marca «Lo puede vender el profesor», pieza 6b-3b-1, decisión 182). Solo lectura y
-- SOLO RECUENTOS: no devuelve nombres, conceptos, importes ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   columna_vendible_profesor   1   producto.vendible_profesor existe
--   tipo_booleano               1   es tinyint(1) (BOOLEAN en MySQL)
--   no_nula                     1   NOT NULL
--   default_falso               1   DEFAULT 0
--   flyway_31_7                 1   V31_7 aplicada con éxito
--   productos_marcados          0   V31_7 no marca nada. Crece cuando la gestión marca productos en su ficha.
-- Informativa:
--   productos                       total de productos (todas las escuelas); no cambia con V31_7
SELECT
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto' AND COLUMN_NAME = 'vendible_profesor')
    AS columna_vendible_profesor,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto' AND COLUMN_NAME = 'vendible_profesor'
      AND COLUMN_TYPE = 'tinyint(1)')
    AS tipo_booleano,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto' AND COLUMN_NAME = 'vendible_profesor'
      AND IS_NULLABLE = 'NO')
    AS no_nula,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto' AND COLUMN_NAME = 'vendible_profesor'
      AND COLUMN_DEFAULT = '0')
    AS default_falso,
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.7' AND success = 1)
    AS flyway_31_7,
  (SELECT COUNT(*) FROM producto WHERE vendible_profesor = TRUE)
    AS productos_marcados,
  (SELECT COUNT(*) FROM producto)
    AS productos;
