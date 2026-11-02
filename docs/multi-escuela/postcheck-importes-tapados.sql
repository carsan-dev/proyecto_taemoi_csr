-- Comprobación posterior de V31_18 (modo privado de importes, decisión 419). Solo lectura y SOLO RECUENTOS: no devuelve
-- nombres, emails ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_31_18                  1   V31_18 aplicada con éxito
--   ultima_es_31_18               1   es la última versión aplicada
--   columna_importes_tapados      1   usuario.importes_tapados existe: tinyint(1) NOT NULL DEFAULT 0
--   cuentas_con_importes_tapados  0   V31_18 no rellena nada. Crece cuando las cuentas activan el modo privado.
-- Informativa:
--   cuentas                           total de cuentas; no cambia con V31_18
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.18' AND success = 1)
    AS flyway_31_18,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '31.18')
    AS ultima_es_31_18,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'importes_tapados'
      AND COLUMN_TYPE = 'tinyint(1)' AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = '0')
    AS columna_importes_tapados,
  (SELECT COUNT(*) FROM usuario WHERE importes_tapados)
    AS cuentas_con_importes_tapados,
  (SELECT COUNT(*) FROM usuario)
    AS cuentas;
