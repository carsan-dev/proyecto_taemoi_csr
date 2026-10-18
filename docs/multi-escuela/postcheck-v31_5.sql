-- Comprobación posterior de V31_5 (registro de cobros y tablas de caja, pieza 6b-1). Solo lectura y SOLO RECUENTOS:
-- no devuelve nombres, correos, DNI, importes ni identificadores.
--
-- Deben ser (justo tras desplegar):
--   tablas                       3   existen cobro, entrega_caja y movimiento_caja
--   filas_cobro                  0   V31_5 no escribe datos (sin relleno: los cargos ya pagados no tienen cobro). Tras
--                                    los primeros cobros crece; las demás comprobaciones siguen valiendo.
--   filas_entrega_caja           0   nadie crea entregas hasta la 6b-2
--   filas_movimiento_caja        0   nadie crea movimientos hasta la 6b-2
--   columnas_clave_cobro         7   producto_alumno_id, sede_id, importe, metodo, fecha_cobro, estado,
--                                    producto_alumno_activo_id
--   uk_cobro_activo_por_cargo    1   clave única de «un cobro ACTIVO por cargo»
--   indices_cobro                3   idx_cobro_sede_estado, idx_cobro_producto_alumno, idx_cobro_entrega_caja
--   fk_cobro                     7   organización, cargo, sede, alumno, cobrado por, anulado por, entrega
--   fk_entrega_caja              5   organización, sede, entregado por, recibido por, anulada por
--   fk_movimiento_caja           6   organización, sede, alumno, entrega, creado por, anulado por
--   flyway_31_5                  1   V31_5 aplicada con éxito
-- Informativas (deben ser 0 siempre, también tras cobrar):
--   cargos_con_varios_activos         cargos con más de un cobro ACTIVO (lo impide la clave única)
--   cobros_activos_de_cargo_no_pagado cobros ACTIVOS cuyo cargo no está pagado
--   cobros_con_sede_distinta          cobros con una sede distinta de la de su cargo
--   cobros_de_otra_escuela            cobros de una organización distinta de la de su cargo
SELECT
  (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('cobro', 'entrega_caja', 'movimiento_caja'))
    AS tablas,
  (SELECT COUNT(*) FROM cobro) AS filas_cobro,
  (SELECT COUNT(*) FROM entrega_caja) AS filas_entrega_caja,
  (SELECT COUNT(*) FROM movimiento_caja) AS filas_movimiento_caja,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cobro'
      AND COLUMN_NAME IN ('producto_alumno_id', 'sede_id', 'importe', 'metodo', 'fecha_cobro', 'estado',
                          'producto_alumno_activo_id'))
    AS columnas_clave_cobro,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cobro' AND INDEX_NAME = 'uk_cobro_activo_por_cargo'
      AND NON_UNIQUE = 0)
    AS uk_cobro_activo_por_cargo,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'cobro'
      AND INDEX_NAME IN ('idx_cobro_sede_estado', 'idx_cobro_producto_alumno', 'idx_cobro_entrega_caja'))
    AS indices_cobro,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'cobro')
    AS fk_cobro,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'entrega_caja')
    AS fk_entrega_caja,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'movimiento_caja')
    AS fk_movimiento_caja,
  (SELECT COUNT(*) FROM flyway_schema_history WHERE version = '31.5' AND success = 1)
    AS flyway_31_5,
  (SELECT COUNT(*) FROM (SELECT producto_alumno_id FROM cobro
    WHERE estado = 'ACTIVO' AND producto_alumno_id IS NOT NULL
    GROUP BY producto_alumno_id HAVING COUNT(*) > 1) varios)
    AS cargos_con_varios_activos,
  (SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
    WHERE c.estado = 'ACTIVO' AND (pa.pagado IS NULL OR pa.pagado = 0))
    AS cobros_activos_de_cargo_no_pagado,
  (SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
    WHERE NOT (c.sede_id <=> pa.sede_id))
    AS cobros_con_sede_distinta,
  (SELECT COUNT(*) FROM cobro c JOIN producto_alumno pa ON pa.id = c.producto_alumno_id
    WHERE c.organizacion_id <> pa.organizacion_id)
    AS cobros_de_otra_escuela;
