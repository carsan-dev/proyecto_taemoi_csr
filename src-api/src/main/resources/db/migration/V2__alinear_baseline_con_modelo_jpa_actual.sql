-- Align the V1 baseline with columns already expected by the current JPA model.
-- This migration is intentionally additive and guarded so it is safe on
-- existing databases that may already contain these columns.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'documento'
              AND COLUMN_NAME = 'evento_id'
        ),
        'SELECT ''documento.evento_id already exists''',
        'ALTER TABLE documento ADD COLUMN evento_id BIGINT NULL DEFAULT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        NOT EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'documento'
              AND COLUMN_NAME = 'evento_id'
        ),
        'SELECT ''documento.evento_id missing''',
        IF(
            EXISTS (
                SELECT 1
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @schema_name
                  AND TABLE_NAME = 'documento'
                  AND COLUMN_NAME = 'evento_id'
            ),
            'SELECT ''documento.evento_id already indexed''',
            'ALTER TABLE documento ADD INDEX idx_documento_evento_id (evento_id)'
        )
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'grupo'
              AND COLUMN_NAME = 'rango_edad_min'
        ),
        'SELECT ''grupo.rango_edad_min already exists''',
        'ALTER TABLE grupo ADD COLUMN rango_edad_min INT NULL DEFAULT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'grupo'
              AND COLUMN_NAME = 'rango_edad_max'
        ),
        'SELECT ''grupo.rango_edad_max already exists''',
        'ALTER TABLE grupo ADD COLUMN rango_edad_max INT NULL DEFAULT NULL'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
