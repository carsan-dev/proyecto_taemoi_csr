-- Comprobación posterior de V32_4 (escuela por dominio, pieza 1: tabla organizacion_dominio). Solo lectura y SOLO
-- RECUENTOS: no devuelve hosts, nombres ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   flyway_32_4                       1   V32_4 aplicada con éxito
--   tabla                             1   organizacion_dominio existe
--   clave_ajena                       1   organizacion_dominio.organizacion_id → organizacion(id)
--   host_unico                        1   índice único uk_organizacion_dominio_host sobre host
--   moiskimdo_principal_en_1          1   la semilla: moiskimdo.es → organización 1, principal
--   hosts_no_normalizados             0   SIEMPRE 0: host en minúsculas, sin puerto, espacios ni punto final
--   escuelas_con_varios_principales   0   SIEMPRE 0: como mucho un dominio principal por escuela
--   escuelas_sin_principal            0   SIEMPRE 0: una escuela con dominios tiene uno principal
-- Informativa:
--   dominios                          1   total de filas; crece al dar de alta el dominio de una escuela
SELECT
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '32.4' AND success = 1)
    AS flyway_32_4,
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion_dominio')
    AS tabla,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion_dominio'
      AND REFERENCED_TABLE_NAME = 'organizacion')
    AS clave_ajena,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'organizacion_dominio'
      AND INDEX_NAME = 'uk_organizacion_dominio_host' AND NON_UNIQUE = 0 AND COLUMN_NAME = 'host')
    AS host_unico,
  (SELECT COUNT(*) FROM organizacion_dominio
    WHERE organizacion_id = 1 AND host = 'moiskimdo.es' AND principal = TRUE)
    AS moiskimdo_principal_en_1,
  (SELECT COUNT(*) FROM organizacion_dominio
    WHERE BINARY host <> BINARY LOWER(TRIM(TRAILING '.' FROM TRIM(host)))
       OR host LIKE '%:%' OR host LIKE '% %' OR host = '')
    AS hosts_no_normalizados,
  (SELECT COUNT(*) FROM (SELECT organizacion_id FROM organizacion_dominio WHERE principal = TRUE
                         GROUP BY organizacion_id HAVING COUNT(*) > 1) varios)
    AS escuelas_con_varios_principales,
  (SELECT COUNT(*) FROM (SELECT organizacion_id FROM organizacion_dominio
                         GROUP BY organizacion_id HAVING SUM(principal) = 0) ninguno)
    AS escuelas_sin_principal,
  (SELECT COUNT(*) FROM organizacion_dominio)
    AS dominios;
