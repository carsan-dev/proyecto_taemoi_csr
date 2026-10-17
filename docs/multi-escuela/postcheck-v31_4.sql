-- Comprobación posterior de V31_4 (tarifa por sede, entrega 6-4). Solo lectura y SOLO RECUENTOS: no devuelve
-- nombres, importes ni identificadores.
--
-- Deben ser:
--   tabla_tarifa_sede            1   la tabla existe
--   filas_tarifa_sede            0   recién migrada no tiene filas (V31_4 no escribe datos; las crea después la API,
--                                    POST /api/tarifas-sede)
--   flyway_31_4                  1   V31_4 aplicada con éxito
-- Informativas (tras crear excepciones con la API; deben ser 0):
--   filas_con_sede_de_otra_escuela      excepciones cuya sede es de otra organización (integridad)
--   filas_con_tarifa_de_otra_escuela    excepciones cuya tarifa es de otra organización (integridad)
--   filas_sin_dia_1                     excepciones con vigente_desde distinto del día 1 del mes
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tarifa_sede')
    AS tabla_tarifa_sede,
  (SELECT COUNT(*) FROM tarifa_sede)
    AS filas_tarifa_sede,
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.4' AND success = 1)
    AS flyway_31_4,
  (SELECT COUNT(*) FROM tarifa_sede ts JOIN sede s ON s.id = ts.sede_id
    WHERE s.organizacion_id <> ts.organizacion_id)
    AS filas_con_sede_de_otra_escuela,
  (SELECT COUNT(*) FROM tarifa_sede ts JOIN tarifa t ON t.id = ts.tarifa_id
    WHERE t.organizacion_id <> ts.organizacion_id)
    AS filas_con_tarifa_de_otra_escuela,
  (SELECT COUNT(*) FROM tarifa_sede WHERE DAYOFMONTH(vigente_desde) <> 1)
    AS filas_sin_dia_1;
