-- Comprobación posterior de V31_8 (solicitudes al propietario y devolución enlazada, pieza 6c-2). Solo lectura y SOLO
-- RECUENTOS: no devuelve nombres, motivos, conceptos, importes ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   tabla_solicitud                          1   existe solicitud
--   filas_solicitud                          0   V31_8 no escribe filas. Crece cuando la gestión pide (6c-2) o el
--                                                profesor encarga (6c-5); las demás comprobaciones siguen valiendo.
--   columnas_virtuales_solicitud             4   cobro_, entrega_, movimiento_ y cargo_pendiente_id (VIRTUAL)
--   uk_solicitud                             4   una clave única por cada una («una pendiente por recurso»)
--   fk_solicitud                            13   organización, sede, 4 de usuario, cobro, entrega, movimiento,
--                                                cargo, producto, alumno y movimiento de devolución
--   fk_solicitud_set_null                    8   sede, las 4 de usuario, cargo, producto y alumno (ON DELETE SET NULL)
--   fk_solicitud_sin_accion                  5   organización, cobro, entrega, movimiento y movimiento de devolución
--                                                (NO ACTION o RESTRICT)
--   columnas_movimiento_caja                 4   cobro_id, producto_alumno_id, metodo, cobro_devuelto_activo_id
--   metodo_no_nulo_efectivo                  1   movimiento_caja.metodo es NOT NULL con DEFAULT 'EFECTIVO'
--   uk_cobro_devuelto_activo                 1   una DEVOLUCION ACTIVA por cobro
--   fk_movimiento_caja                       8   las 6 de V31_5 + cobro + cargo
--   fk_movimiento_caja_set_null              4   alumno, creado por, anulado por (V31_5) y cargo (V31_8)
--   fk_movimiento_caja_sin_accion            4   organización, sede, entrega (V31_5) y cobro (V31_8)
--   flyway_31_8                              1   V31_8 aplicada con éxito
--   movimientos_no_efectivo                  0   todas las filas quedan EFECTIVO
--   sedes_con_saldo_distinto_solo_efectivo   0   sedes cuyo Σ de movimientos ACTIVOS cambia si se cuentan solo los
--                                                EFECTIVO (D6c-13): con 0, el saldo de caja de cada sede es el mismo
--                                                antes y después
-- Informativas (0 justo tras desplegar):
--   movimientos_con_cobro                        devoluciones o errores enlazados a un cobro (crecen con la 6c-4 y la 6c-6)
--   solicitudes_pendientes                       tipos 1 a 3 esperando al propietario (crecen con la 6c-2)
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud')
    AS tabla_solicitud,
  (SELECT COUNT(*) FROM solicitud) AS filas_solicitud,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud' AND EXTRA = 'VIRTUAL GENERATED'
      AND COLUMN_NAME IN ('cobro_pendiente_id', 'entrega_pendiente_id', 'movimiento_pendiente_id',
                          'cargo_pendiente_id'))
    AS columnas_virtuales_solicitud,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud' AND NON_UNIQUE = 0
      AND INDEX_NAME IN ('uk_solicitud_cobro_pendiente', 'uk_solicitud_entrega_pendiente',
                         'uk_solicitud_movimiento_pendiente', 'uk_solicitud_cargo_pendiente'))
    AS uk_solicitud,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud')
    AS fk_solicitud,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud' AND DELETE_RULE = 'SET NULL')
    AS fk_solicitud_set_null,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'solicitud' AND DELETE_RULE IN ('NO ACTION', 'RESTRICT'))
    AS fk_solicitud_sin_accion,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimiento_caja'
      AND COLUMN_NAME IN ('cobro_id', 'producto_alumno_id', 'metodo', 'cobro_devuelto_activo_id'))
    AS columnas_movimiento_caja,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimiento_caja' AND COLUMN_NAME = 'metodo'
      AND IS_NULLABLE = 'NO' AND COLUMN_DEFAULT = 'EFECTIVO')
    AS metodo_no_nulo_efectivo,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'movimiento_caja' AND NON_UNIQUE = 0
      AND INDEX_NAME = 'uk_movimiento_caja_cobro_devuelto_activo')
    AS uk_cobro_devuelto_activo,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimiento_caja')
    AS fk_movimiento_caja,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimiento_caja' AND DELETE_RULE = 'SET NULL')
    AS fk_movimiento_caja_set_null,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimiento_caja'
      AND DELETE_RULE IN ('NO ACTION', 'RESTRICT'))
    AS fk_movimiento_caja_sin_accion,
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.8' AND success = 1)
    AS flyway_31_8,
  (SELECT COUNT(*) FROM movimiento_caja WHERE metodo <> 'EFECTIVO')
    AS movimientos_no_efectivo,
  (SELECT COUNT(*) FROM (
     SELECT m.sede_id FROM movimiento_caja m WHERE m.estado = 'ACTIVO'
     GROUP BY m.sede_id
     HAVING SUM(m.importe) <> SUM(CASE WHEN m.metodo = 'EFECTIVO' THEN m.importe ELSE 0 END)) distintas)
    AS sedes_con_saldo_distinto_solo_efectivo,
  (SELECT COUNT(*) FROM movimiento_caja WHERE cobro_id IS NOT NULL)
    AS movimientos_con_cobro,
  (SELECT COUNT(*) FROM solicitud WHERE estado = 'PENDIENTE')
    AS solicitudes_pendientes;
