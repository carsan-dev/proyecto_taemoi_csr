-- Comprobación posterior de V31_14 (perfil de la cuenta, decisión 322). Solo lectura y SOLO RECUENTOS: no devuelve
-- nombres, fechas, emails ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_31_14                 1   V31_14 aplicada con éxito
--   ultima_es_31_14              1   es la última versión aplicada
--   columna_nombre_preferido     1   usuario.nombre_preferido existe: varchar(40) NULL en utf8mb4
--   columna_cumple_dia           1   usuario.cumple_dia existe: tinyint NULL
--   columna_cumple_mes           1   usuario.cumple_mes existe: tinyint NULL
--   check_cumple                 1   existe ck_usuario_cumple y está activa
--   cuentas_con_perfil           0   V31_14 no rellena nada. Crece cuando las cuentas guardan su perfil.
-- Informativa:
--   cuentas                          total de cuentas; no cambia con V31_14
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.14' AND success = 1)
    AS flyway_31_14,
  (SELECT COUNT(*) FROM (SELECT version FROM flyway_schema_history WHERE success = 1
                         ORDER BY installed_rank DESC LIMIT 1) ultima
    WHERE ultima.version = '31.14')
    AS ultima_es_31_14,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'nombre_preferido'
      AND COLUMN_TYPE = 'varchar(40)' AND IS_NULLABLE = 'YES' AND CHARACTER_SET_NAME = 'utf8mb4')
    AS columna_nombre_preferido,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'cumple_dia'
      AND COLUMN_TYPE = 'tinyint' AND IS_NULLABLE = 'YES')
    AS columna_cumple_dia,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND COLUMN_NAME = 'cumple_mes'
      AND COLUMN_TYPE = 'tinyint' AND IS_NULLABLE = 'YES')
    AS columna_cumple_mes,
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'usuario' AND CONSTRAINT_NAME = 'ck_usuario_cumple'
      AND CONSTRAINT_TYPE = 'CHECK' AND ENFORCED = 'YES')
    AS check_cumple,
  (SELECT COUNT(*) FROM usuario
    WHERE nombre_preferido IS NOT NULL OR cumple_dia IS NOT NULL OR cumple_mes IS NOT NULL)
    AS cuentas_con_perfil,
  (SELECT COUNT(*) FROM usuario)
    AS cuentas;
