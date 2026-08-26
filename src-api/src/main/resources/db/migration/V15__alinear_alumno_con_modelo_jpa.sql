-- Add Alumno columns that predate the Flyway baseline but were never included
-- in the versioned schema. Every operation is guarded because production
-- databases created previously by Hibernate may already contain them.

SET @schema_name = DATABASE();

SET @sql = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'telefono2'
        ),
        'SELECT ''alumno.telefono2 already exists''',
        'ALTER TABLE alumno ADD COLUMN telefono2 INT NULL DEFAULT NULL'
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
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'observaciones'
        ),
        'SELECT ''alumno.observaciones already exists''',
        'ALTER TABLE alumno ADD COLUMN observaciones LONGTEXT NULL'
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
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'fecha_reto_diario_completado'
        ),
        'SELECT ''alumno.fecha_reto_diario_completado already exists''',
        'ALTER TABLE alumno ADD COLUMN fecha_reto_diario_completado DATE NULL DEFAULT NULL'
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
              AND TABLE_NAME = 'alumno'
              AND COLUMN_NAME = 'racha_reto_diario'
        ),
        'SELECT ''alumno.racha_reto_diario already exists''',
        'ALTER TABLE alumno ADD COLUMN racha_reto_diario INT NULL DEFAULT 0'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
