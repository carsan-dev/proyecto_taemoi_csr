-- Solo lectura (30/10/2026): volumen e índices de la caja de la organización 1. Sin escrituras ni datos personales.
SET @org := 1;
SET @sede := (SELECT sede_id FROM cobro WHERE organizacion_id = @org GROUP BY sede_id ORDER BY COUNT(*) DESC LIMIT 1);
SET @cobrador := (SELECT cobrado_por_usuario_id FROM cobro WHERE organizacion_id = @org AND origen = 'PROFESOR' GROUP BY cobrado_por_usuario_id ORDER BY COUNT(*) DESC LIMIT 1);
SELECT @sede AS sede_con_mas_cobros, @cobrador AS cobrador_con_mas_cobros;
SELECT 'cobro' AS tabla, COUNT(*) AS filas FROM cobro UNION ALL SELECT 'entrega_caja', COUNT(*) FROM entrega_caja UNION ALL SELECT 'movimiento_caja', COUNT(*) FROM movimiento_caja UNION ALL SELECT 'solicitud', COUNT(*) FROM solicitud UNION ALL SELECT 'producto_alumno', COUNT(*) FROM producto_alumno;
SELECT sede_id, COUNT(*) AS cobros_sin_entregar, COUNT(DISTINCT cobrado_por_usuario_id) AS cobradores FROM cobro WHERE organizacion_id = @org AND origen = 'PROFESOR' AND estado = 'ACTIVO' AND entrega_caja_id IS NULL GROUP BY sede_id;
SELECT sede_id, DATE_FORMAT(fecha, '%Y-%m') AS mes, COUNT(*) AS movimientos FROM movimiento_caja WHERE organizacion_id = @org GROUP BY sede_id, mes ORDER BY movimientos DESC LIMIT 5;
SELECT COUNT(*) AS cargos_sin_pagar FROM producto_alumno WHERE organizacion_id = @org AND (pagado IS NULL OR pagado = FALSE) AND cancelado_en IS NULL;
SHOW INDEX FROM cobro; SHOW INDEX FROM entrega_caja; SHOW INDEX FROM movimiento_caja;
EXPLAIN SELECT c.id FROM cobro c WHERE c.organizacion_id = @org AND c.sede_id = @sede AND c.origen = 'PROFESOR' AND c.estado = 'ACTIVO' AND c.entrega_caja_id IS NULL ORDER BY c.cobrado_por_usuario_id, c.fecha_cobro, c.id;
EXPLAIN SELECT m.id FROM movimiento_caja m WHERE m.organizacion_id = @org AND m.sede_id = @sede AND m.fecha >= '2026-10-01' AND m.fecha < '2026-11-01' ORDER BY m.fecha DESC, m.id DESC;
EXPLAIN SELECT e.id FROM entrega_caja e WHERE e.organizacion_id = @org AND e.sede_id = @sede ORDER BY e.fecha DESC, e.id DESC LIMIT 25;
EXPLAIN SELECT c.id FROM cobro c WHERE c.organizacion_id = @org AND c.cobrado_por_usuario_id = @cobrador AND c.origen = 'PROFESOR' AND c.estado = 'ACTIVO' AND c.entrega_caja_id IS NULL ORDER BY c.fecha_cobro, c.id;
