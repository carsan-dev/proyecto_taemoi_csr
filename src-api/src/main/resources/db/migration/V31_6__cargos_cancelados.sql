-- Cargos cancelados (pieza 6b-2c, docs/multi-escuela/entrega6-plan.md §7) y origen de la venta del profesor
-- (decisión 172).
--
-- SOLO AÑADE a producto_alumno cuatro columnas NULL, dos índices y dos FK:
--   cancelado_en              cuándo se canceló (NULL = cargo vigente). Cancelado => no pagado y sin cobro ACTIVO.
--   cancelado_por_usuario_id  quién lo canceló. FK a usuario ON DELETE SET NULL (borrar una cuenta no falla).
--   motivo_cancelacion        por qué (obligatorio al cancelar, lo exige el servicio).
--   vendido_por_usuario_id    el profesor que apuntó y cobró el cargo como venta del catálogo (6b-2). NULL en todo lo
--                             demás (asignación de la gestión, mensualidades, licencias, exámenes, reservas…). FK a
--                             usuario ON DELETE SET NULL, como cobro.cobrado_por_usuario_id (V31_5). Es lo que mira el
--                             deshacer del profesor con «no hubo venta», en lugar de deducirlo de las fechas.
-- Las columnas de cancelación quedan a NULL (ningún cargo nace cancelado). vendido_por_usuario_id se rellena UNA vez
-- (paso 5) en las ventas del profesor hechas desde la 6b-2, a partir del registro de auditoría de cada venta.
-- El código anterior sigue funcionando sobre este esquema (no lee las columnas e inserta sin ellas: quedan NULL), así
-- que no lleva una guarda que detenga la migración. Sí guardas de existencia (information_schema) en cada cambio, como
-- V31_3 y V31_4. Va como V31_6 porque V32 está reservada para la contracción multi-escuela.
--
-- Coste en producción (mysql:8.0): cada ADD COLUMN NULL va en su PROPIA sentencia, que MySQL 8.0 hace INSTANT (solo
-- metadatos, sin copiar filas). Los índices son INPLACE sin bloquear escrituras. Las FK se añaden con
-- foreign_key_checks = 0 en la sesión: con 1, MySQL solo admite ADD FOREIGN KEY con el algoritmo COPY (reconstruye la
-- tabla y bloquea las escrituras), con 0 la hace INPLACE (solo metadatos, el índice ya existe). Saltarse la validación es
-- seguro porque las columnas están enteras a NULL (el relleno va DESPUÉS de las FK): no puede haber huérfanos. Aun así se
-- cuentan antes: si hubiera alguno (solo posible si alguien escribió una columna a mano), se añaden con
-- foreign_key_checks = 1 para que MySQL las valide (y falle). foreign_key_checks es de la SESIÓN de Flyway: se restaura
-- justo después (y la sesión se cierra al terminar). El relleno toca solo las ventas del profesor (pocas filas).
--
-- Repetible: cada paso vuelve a consultar information_schema y el relleno solo escribe filas con
-- vendido_por_usuario_id NULL. Tras un fallo a medias, `flyway repair` y volver a desplegar crea solo lo que falte.
-- Comprobación posterior (solo recuentos): docs/multi-escuela/postcheck-v31_6.sql.

SET @schema_name = DATABASE();

-- ============================================================================
-- 1. Columnas (una sentencia cada una: INSTANT)
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno' AND COLUMN_NAME = 'cancelado_en'),
        'SELECT ''producto_alumno.cancelado_en already exists''',
        'ALTER TABLE producto_alumno ADD COLUMN cancelado_en DATETIME(6) NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND COLUMN_NAME = 'cancelado_por_usuario_id'),
        'SELECT ''producto_alumno.cancelado_por_usuario_id already exists''',
        'ALTER TABLE producto_alumno ADD COLUMN cancelado_por_usuario_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND COLUMN_NAME = 'motivo_cancelacion'),
        'SELECT ''producto_alumno.motivo_cancelacion already exists''',
        'ALTER TABLE producto_alumno ADD COLUMN motivo_cancelacion VARCHAR(500) NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND COLUMN_NAME = 'vendido_por_usuario_id'),
        'SELECT ''producto_alumno.vendido_por_usuario_id already exists''',
        'ALTER TABLE producto_alumno ADD COLUMN vendido_por_usuario_id BIGINT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 2. Índices de las FK (INPLACE, sin bloquear escrituras)
-- ============================================================================
SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND INDEX_NAME = 'idx_producto_alumno_cancelado_por'),
        'SELECT ''idx_producto_alumno_cancelado_por already exists''',
        'ALTER TABLE producto_alumno ADD INDEX idx_producto_alumno_cancelado_por (cancelado_por_usuario_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND INDEX_NAME = 'idx_producto_alumno_vendido_por'),
        'SELECT ''idx_producto_alumno_vendido_por already exists''',
        'ALTER TABLE producto_alumno ADD INDEX idx_producto_alumno_vendido_por (vendido_por_usuario_id)'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 3. FK a usuario (ON DELETE SET NULL), sin reconstruir la tabla
-- ============================================================================
SET @huerfanos = (
    SELECT COUNT(*) FROM producto_alumno pa
    WHERE (pa.cancelado_por_usuario_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.id = pa.cancelado_por_usuario_id))
       OR (pa.vendido_por_usuario_id IS NOT NULL
           AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.id = pa.vendido_por_usuario_id))
);
SET @fk_checks_antes = @@SESSION.foreign_key_checks;
SET SESSION foreign_key_checks = IF(@huerfanos = 0, 0, 1);

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND CONSTRAINT_NAME = 'fk_producto_alumno_cancelado_por' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
        'SELECT ''fk_producto_alumno_cancelado_por already exists''',
        'ALTER TABLE producto_alumno ADD CONSTRAINT fk_producto_alumno_cancelado_por FOREIGN KEY (cancelado_por_usuario_id) REFERENCES usuario(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = @schema_name AND TABLE_NAME = 'producto_alumno'
                  AND CONSTRAINT_NAME = 'fk_producto_alumno_vendido_por' AND CONSTRAINT_TYPE = 'FOREIGN KEY'),
        'SELECT ''fk_producto_alumno_vendido_por already exists''',
        'ALTER TABLE producto_alumno ADD CONSTRAINT fk_producto_alumno_vendido_por FOREIGN KEY (vendido_por_usuario_id) REFERENCES usuario(id) ON DELETE SET NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================================
-- 4. Restaurar foreign_key_checks (el relleno de abajo ya lo valida la FK)
-- ============================================================================
SET SESSION foreign_key_checks = IF(@fk_checks_antes IS NULL, 1, @fk_checks_antes);

-- ============================================================================
-- 5. Relleno ÚNICO de vendido_por_usuario_id desde el registro de auditoría (decisión 172)
-- ============================================================================
-- Las ventas del profesor hechas en producción desde la 6b-2 no guardaron quién vendió. Cada una dejó, en la MISMA
-- transacción que el cargo y el cobro (RegistroAccionesCaja -> AuditoriaService#registrarAccesoSensible, propagación
-- MANDATORY y sin capturar errores: sin fila de auditoría no hay venta), una fila en auditoria_evento:
--   modulo 'caja', accion 'CREATE', endpoint '/api/caja/profesor/alumnos/{alumno}/productos/{producto}/cobrar',
--   recurso_id = id del cobro y resumen 'CAJA COBRO_PROFESOR_PRODUCTO sede=#S cobro=#C cargo=#G producto=#P alumno=#A'.
-- El resumen es lo que la distingue: el registro HTTP genérico de esa misma ruta escribe otro resumen
-- ('CREATE caja #N -> 201', con recurso_id = último número de la ruta), y cobrar un cargo pendiente
-- (COBRO_PROFESOR) va por '/api/caja/profesor/cargos/{cargo}/cobrar'. Este UPDATE marca UNA vez cada cargo cuyo cobro
-- tenga esa fila (misma escuela, recurso_id = el cobro, y el resumen nombra ese cobro Y ese cargo) con quien cobró.
-- Después solo la venta del profesor fija la columna. La auditoría se conserva 12 meses (audit.retention.months):
-- las ventas de la 6b-2 son posteriores. Busca por el índice de modulo ('caja': pocas filas).
-- Repetible: solo escribe filas con vendido_por_usuario_id NULL.
UPDATE producto_alumno pa
JOIN cobro c ON c.producto_alumno_id = pa.id
SET pa.vendido_por_usuario_id = c.cobrado_por_usuario_id
WHERE pa.vendido_por_usuario_id IS NULL
  AND c.origen = 'PROFESOR'
  AND c.cobrado_por_usuario_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM auditoria_evento ae
      WHERE ae.modulo = 'caja'
        AND ae.accion = 'CREATE'
        AND ae.organizacion_id = pa.organizacion_id
        AND ae.recurso_id = c.id
        AND ae.endpoint LIKE '/api/caja/profesor/alumnos/%/productos/%/cobrar'
        AND ae.resumen LIKE CONCAT('CAJA COBRO!_PROFESOR!_PRODUCTO sede=#% cobro=#', c.id, ' cargo=#', pa.id, ' %') ESCAPE '!');
