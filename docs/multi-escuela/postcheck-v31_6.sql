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
--   cargos_cancelados             0   V31_6 no cancela nada. Tras las primeras cancelaciones crece; las demás siguen valiendo.
--   columna_vendido_por           1   vendido_por_usuario_id existe y admite NULL (decisión 172)
--   idx_vendido_por               1   índice idx_producto_alumno_vendido_por
--   fk_vendido_por                1   FK fk_producto_alumno_vendido_por a usuario
--   fk_vendido_por_set_null       1   esa FK es ON DELETE SET NULL
-- Informativas (deben ser 0 siempre, también tras cancelar):
--   cancelados_pagados            cargos cancelados con pagado = 1 (cancelado => no pagado)
--   cancelados_con_cobro_activo   cargos cancelados con un cobro ACTIVO
--   cancelados_sin_motivo         cargos cancelados sin motivo
--   datos_sin_cancelar            cargos vigentes (cancelado_en NULL) con motivo o con quién canceló
--   ventas_sin_cobro_del_vendedor cargos con vendedor sin un cobro de origen PROFESOR de ese vendedor
--   ventas_auditadas_sin_marcar   cargos con venta auditada (como abajo) sin vendido_por_usuario_id
-- Deben ser IGUALES (justo tras desplegar: el relleno sale de la auditoría; después las dos crecen con cada venta,
-- salvo que la retención de 12 meses borre auditoría antigua):
--   ventas_del_profesor           cargos con vendido_por_usuario_id
--   ventas_auditadas              cargos con un cobro PROFESOR y su fila COBRO_PROFESOR_PRODUCTO en auditoria_evento
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
    AS datos_sin_cancelar,
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno' AND IS_NULLABLE = 'YES'
      AND COLUMN_NAME = 'vendido_por_usuario_id')
    AS columna_vendido_por,
  (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
      AND INDEX_NAME = 'idx_producto_alumno_vendido_por')
    AS idx_vendido_por,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
      AND CONSTRAINT_NAME = 'fk_producto_alumno_vendido_por')
    AS fk_vendido_por,
  (SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'producto_alumno'
      AND CONSTRAINT_NAME = 'fk_producto_alumno_vendido_por' AND DELETE_RULE = 'SET NULL')
    AS fk_vendido_por_set_null,
  (SELECT COUNT(*) FROM producto_alumno WHERE vendido_por_usuario_id IS NOT NULL)
    AS ventas_del_profesor,
  (SELECT COUNT(*) FROM producto_alumno pa
    WHERE pa.vendido_por_usuario_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM cobro c WHERE c.producto_alumno_id = pa.id AND c.origen = 'PROFESOR'
                        AND c.cobrado_por_usuario_id = pa.vendido_por_usuario_id))
    AS ventas_sin_cobro_del_vendedor,
  (SELECT COUNT(DISTINCT pa.id) FROM producto_alumno pa JOIN cobro c ON c.producto_alumno_id = pa.id
    WHERE c.origen = 'PROFESOR' AND c.cobrado_por_usuario_id IS NOT NULL
      AND EXISTS (SELECT 1 FROM auditoria_evento ae
                  WHERE ae.modulo = 'caja' AND ae.accion = 'CREATE' AND ae.organizacion_id = pa.organizacion_id
                    AND ae.recurso_id = c.id
                    AND ae.endpoint LIKE '/api/caja/profesor/alumnos/%/productos/%/cobrar'
                    AND ae.resumen LIKE CONCAT('CAJA COBRO!_PROFESOR!_PRODUCTO sede=#% cobro=#', c.id, ' cargo=#',
                                               pa.id, ' %') ESCAPE '!'))
    AS ventas_auditadas,
  (SELECT COUNT(DISTINCT pa.id) FROM producto_alumno pa JOIN cobro c ON c.producto_alumno_id = pa.id
    WHERE pa.vendido_por_usuario_id IS NULL AND c.origen = 'PROFESOR' AND c.cobrado_por_usuario_id IS NOT NULL
      AND EXISTS (SELECT 1 FROM auditoria_evento ae
                  WHERE ae.modulo = 'caja' AND ae.accion = 'CREATE' AND ae.organizacion_id = pa.organizacion_id
                    AND ae.recurso_id = c.id
                    AND ae.endpoint LIKE '/api/caja/profesor/alumnos/%/productos/%/cobrar'
                    AND ae.resumen LIKE CONCAT('CAJA COBRO!_PROFESOR!_PRODUCTO sede=#% cobro=#', c.id, ' cargo=#',
                                               pa.id, ' %') ESCAPE '!'))
    AS ventas_auditadas_sin_marcar;
