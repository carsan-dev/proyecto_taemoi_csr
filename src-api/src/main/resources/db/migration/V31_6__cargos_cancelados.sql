-- Cargos cancelados (pieza 6b-2c, docs/multi-escuela/entrega6-plan.md §7).
--
-- SOLO AÑADE a producto_alumno tres columnas NULL (sin relleno: ningún cargo nace cancelado), un índice y una FK:
--   cancelado_en              cuándo se canceló (NULL = cargo vigente). Cancelado => no pagado y sin cobro ACTIVO.
--   cancelado_por_usuario_id  quién lo canceló; FK a usuario ON DELETE SET NULL (borrar una cuenta no falla).
--   motivo_cancelacion        por qué (obligatorio al cancelar; lo exige el servicio).
-- El código anterior sigue funcionando sobre este esquema (no lee las columnas e inserta sin ellas: quedan NULL), así
-- que no lleva una guarda que detenga la migración; sí guardas de existencia (information_schema) en cada cambio, como
-- V31_3 y V31_4. Va como V31_6 porque V32 está reservada para la contracción multi-escuela.
--
-- Coste en producción (mysql:8.0): cada ADD COLUMN NULL va en su PROPIA sentencia, que MySQL 8.0 hace INSTANT (solo
-- metadatos, sin copiar filas). El índice es INPLACE sin bloquear escrituras. La FK se añade con foreign_key_checks = 0
-- en la sesión: con 1, MySQL solo admite ADD FOREIGN KEY con el algoritmo COPY (reconstruye la tabla y bloquea las
-- escrituras); con 0 la hace INPLACE (solo metadatos; el índice ya existe). Saltarse la validación es seguro porque la
-- columna está entera a NULL: no puede haber huérfanos. Aun así se cuentan antes: si hubiera alguno (solo posible si
-- alguien escribió la columna a mano), se añade con foreign_key_checks = 1 para que MySQL la valide (y falle).
-- foreign_key_checks es de la SESIÓN de Flyway: se restaura a 1 justo después (y la sesión se cierra al terminar).
--
-- Repetible: cada paso vuelve a consultar information_schema; tras un fallo a medias, `flyway repair` y volver a
-- desplegar crea solo lo que falte. Repetirla no cambia ninguna fila.
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

-- ============================================================================
-- 2. Índice de la FK (INPLACE, sin bloquear escrituras)
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

-- ============================================================================
-- 3. FK a usuario (ON DELETE SET NULL), sin reconstruir la tabla
-- ============================================================================
SET @huerfanos = (
    SELECT COUNT(*) FROM producto_alumno pa
    WHERE pa.cancelado_por_usuario_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.id = pa.cancelado_por_usuario_id)
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

SET SESSION foreign_key_checks = IF(@fk_checks_antes IS NULL, 1, @fk_checks_antes);
